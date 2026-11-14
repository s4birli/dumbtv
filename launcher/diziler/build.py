#!/usr/bin/env python3
"""Finds the newest full episode of each show on the family screen's "Diziler" list.

The shows and their official YouTube channels are listed under "diziler" in
launcher/aile-kanallar.json. For each channel this reads the newest uploads (a flat
listing, which YouTube answers without sign-in) and keeps the first one that is a full
episode: its title names the show, it runs 40 minutes or more, and it is not a trailer,
summary or compilation. When it went up on YouTube comes from the channel's own feed, or,
for a channel that has put up many clips since, from YouTube's "3 days ago" (day only, marked
"yaklasik"). A show that cannot be read this time keeps the episode it had in the previous list.

Needs yt-dlp (pip install yt-dlp). Usage: build.py aile-kanallar.json out.json [previous.json]
"""
import json
import re
import sys
import time
import urllib.request
import xml.etree.ElementTree as ET
from datetime import datetime

from yt_dlp import YoutubeDL

MIN_SECONDS = 40 * 60
LOOK = 30  # newest uploads to look through per channel
SKIP = ["fragman", "özet", "kolaj", "günlüğü", "tüm bölümler", "part", "ön izleme",
        "sahne", "kamera arkası", "tanıtım", "derleme", "#shorts"]


def lower(s):
    return s.replace("I", "ı").replace("İ", "i").lower()


def key(s):
    return re.sub(r"[^0-9a-zçğıöşü]", "", lower(s))


def channel_url(source):
    if source.startswith("UC"):
        return f"https://www.youtube.com/channel/{source}/videos"
    return f"https://www.youtube.com/{source.lstrip('/')}/videos"


def uploads(source):
    opts = {"extract_flat": True, "quiet": True, "no_warnings": True, "playlistend": LOOK,
            "extractor_args": {"youtube": {"lang": ["tr"]}}}
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(channel_url(source), download=False)
    return info.get("entries") or [], info.get("channel_id") or ""


def approximate(source, video_id):
    """The day the video went up, from YouTube's "3 days ago" in the untranslated listing; 0 if not there."""
    opts = {"extract_flat": True, "quiet": True, "no_warnings": True, "playlistend": LOOK,
            "extractor_args": {"youtubetab": {"approximate_date": [""]}}}
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(channel_url(source), download=False)
    for e in info.get("entries") or []:
        if e.get("id") == video_id:
            return int(e.get("timestamp") or 0)
    return 0


def published(channel_id, video_id):
    """When the video went up (unix seconds), from the channel's feed of its 15 newest; 0 if not there."""
    if not re.fullmatch(r"UC[A-Za-z0-9_-]{22}", channel_id):
        return 0
    req = urllib.request.Request(f"https://www.youtube.com/feeds/videos.xml?channel_id={channel_id}",
                                 headers={"User-Agent": "dumbtv-aile/1.0 (https://github.com/s4birli/dumbtv)"})
    with urllib.request.urlopen(req, timeout=15) as r:
        root = ET.fromstring(r.read())
    ns = {"a": "http://www.w3.org/2005/Atom", "yt": "http://www.youtube.com/xml/schemas/2015"}
    for e in root.findall("a:entry", ns):
        if e.findtext("yt:videoId", "", ns) == video_id:
            return int(datetime.fromisoformat(e.findtext("a:published", "", ns)).timestamp())
    return 0


def episode(title):
    """('146. Bölüm', '') for a series, ('', '13 Kasım Cuma') for a daily show dated in its title."""
    m = re.search(r"(\d+)\s*\.\s*bölüm", lower(title))
    if m:
        return f"{m.group(1)}. Bölüm", ""
    m = re.search(r"(\d{1,2} (?:Ocak|Şubat|Mart|Nisan|Mayıs|Haziran|Temmuz|Ağustos|Eylül|Ekim|"
                  r"Kasım|Aralık)) \d{4}( (?:Pazartesi|Salı|Çarşamba|Perşembe|Cuma|Cumartesi|"
                  r"Pazar))?", title)
    if m:
        return "", m.group(1) + (m.group(2) or "")
    return "", ""


def newest(show, old):
    name = key(show["ad"])
    entries, channel_id = uploads(show["kaynak"])
    for e in entries:
        title = (e.get("title") or "").strip()
        secs = e.get("duration") or 0
        vid = e.get("id") or ""
        if not re.fullmatch(r"[A-Za-z0-9_-]{11}", vid) or secs < MIN_SECONDS:
            continue
        if name not in key(title) or any(w in lower(title) for w in SKIP):
            continue
        same = old and old.get("id") == vid and old.get("yayin")
        when, rough = (old["yayin"], bool(old.get("yaklasik"))) if same else (0, False)
        if not same or rough:
            try:
                exact = published(channel_id, vid)
                if exact:
                    when, rough = exact, False
                elif not same:
                    when, rough = approximate(show["kaynak"], vid), True
            except Exception as ex:  # the episode still shows, without its date
                print(f"{show['ad']}: date: {ex}", file=sys.stderr)
        number, day = episode(title)
        ep = {"ad": show["ad"], "kanal": show.get("kanal", ""), "id": vid, "baslik": title,
              "bolum": number, "tarih": day, "dk": round(secs / 60), "yayin": when}
        if rough and when:
            ep["yaklasik"] = True
        return ep
    return None


def main():
    cfg = json.load(open(sys.argv[1], encoding="utf-8"))
    shows = [s for s in cfg.get("diziler", []) if s.get("ad") and s.get("kaynak")]
    old = {}
    if len(sys.argv) > 3:
        try:
            for d in json.load(open(sys.argv[3], encoding="utf-8")).get("diziler", []):
                old[d.get("ad")] = d
        except (OSError, ValueError):
            pass
    out, found = [], 0
    for show in shows:
        try:
            ep = newest(show, old.get(show["ad"]))
        except Exception as e:  # one channel failing keeps the others
            print(f"{show['ad']}: {e}", file=sys.stderr)
            ep = None
        if ep:
            found += 1
            print(f"{show['ad']}: {ep['baslik']}", file=sys.stderr)
        else:
            ep = old.get(show["ad"])
            print(f"{show['ad']}: kept {ep['baslik'] if ep else 'nothing'}", file=sys.stderr)
        if ep:
            out.append(ep)
    if found == 0:
        raise SystemExit("no episodes found; keeping the old list")
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump({"olusturuldu": int(time.time()), "diziler": out}, f, ensure_ascii=False, indent=1)


if __name__ == "__main__":
    main()
