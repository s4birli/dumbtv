#!/usr/bin/env python3
"""Builds the "Eski Türk Filmleri" list for the family screen.

Only full films that their rights holders publish themselves on YouTube: the channels below are
the production companies' own, verified channels. Only comedies: a comedy cast or a known
comedy title. Series episodes, trailers, clips and compilations are left out, and so is
anything shorter than an hour. The most watched come first.

Needs yt-dlp (pip install yt-dlp). Titles are asked for in Turkish, or YouTube answers with
English translations ("Hababam Class"); a second, untranslated pass gives the view counts,
which the Turkish one garbles. Usage: build.py out.json
"""
import json
import re
import sys
import time

from yt_dlp import YoutubeDL

CHANNELS = [
    ("Arzu Film", "UCUALYmnkEmx8vlxIbpr2gmw"),
    ("Erler Film", "UCUX_iQoFXw2nYzIuuYdeWhg"),
    ("Sistem Film", "UCL_hdvwQcxxVwoBOj0f6qTA"),
]
# Comedies whose title names no comedian (mostly Arzu Film's).
TITLES = ["hababam", "kibar feyzo", "tosun paşa", "süt kardeşler", "şaban oğlu şaban",
          "çöpçüler kralı", "köyden indim şehire", "bizim aile", "gülen gözler", "neşeli günler",
          "banker bilo", "erkek güzeli sefil bilo", "salak milyoner", "şekerpare", "mavi boncuk",
          "canım kardeşim", "oh olsun", "ah nerede", "aile şerefi", "milyarder", "sev kardeşim",
          "arabesk", "delisin"]
COMEDY = ["kemal sunal", "şener şen", "müjdat gezen", "adile naşit", "zeki alasya",
          "metin akpınar", "ilyas salman", "levent kırca", "halit akçatepe", "turist ömer",
          "ali poyrazoğlu", "perran kutman", "ayşen gruda", "hababam",
          "şaban", "gırgıriye", "komedi"]
SKIP = ["bölüm", "fragman", "derleme", "sahne", "klip", "kamera arkası", "belgesel",
        "trailer", "özel", "en komik", "full movie", "anları", "anlar ", "tüm ", "tanıtım"]
LIMIT = 80
MIN_SECONDS = 60 * 60


def lower(s):
    return s.replace("I", "ı").replace("İ", "i").lower()


def clean(title):
    """'Talih Kuşu | Kemal Sunal, Yasemin Yalçın | Türk Filmi | Full HD' -> name, cast, year."""
    year = None
    m = re.search(r"\((19\d\d|20\d\d)\)", title)
    if m:
        year = int(m.group(1))
    parts = [p.strip() for p in re.split(r"\s+\|\s+|\s+-\s+", title) if p.strip()]
    name = parts[0] if parts else title
    cast = None
    # "Köyden İndim Şehire Yeşilçam Filmi (Kemal Sunal, Zeki Alasya)": the cast in brackets.
    m0 = re.search(r"\(([^()]*,[^()]*)\)", name)
    if m0:
        cast = m0.group(1).replace(" ve ", ", ")
        name = name.replace(m0.group(0), "")
    name = re.sub(r"(?i)yeşilçam( aile)? filmi|\(\s*\)", "", name)
    name = re.sub(r"\((19\d\d|20\d\d)\)", "", name)
    name = re.sub(r"(?i)\b(türk filmi|tek parça|full hd|full|restorasyonlu|restored|4k|ultra hd|hd)\b", "", name)
    name = re.sub(r"\s{2,}", " ", name).strip(" -|")
    for p in parts[1:]:
        if cast:
            break
        lp = lower(p)
        if any(w in lp for w in ("türk filmi", "full", "hd", "tek parça", "restor", "izle")):
            m2 = re.search(r"\(([^)]+)\)", p)
            if m2 and "&" in m2.group(1):
                cast = m2.group(1).replace(" & ", ", ")
            continue
        if "," in p or "&" in p:
            cast = p.replace(" & ", ", ")
            break
    if cast is None:
        m3 = re.search(r"\(([^()]*&[^()]*)\)", title)
        if m3:
            cast = m3.group(1).replace(" & ", ", ")
    if cast:
        cast = re.sub(r"(?i)4k|yeşilçam( aile)? filmi|\(siyah beyaz\)|türk filmi|full hd|\(\s*\)", "", cast)
        cast = re.sub(r"[()]", "", cast)
        cast = re.sub(r"^\s*(19|20)\d\d\s+", "", cast)
        cast = ", ".join(c.strip() for c in cast.split(",") if c.strip())
    return name, cast or None, year


def films(channel_id, turkish=True):
    opts = {"extract_flat": True, "quiet": True, "no_warnings": True}
    if turkish:
        opts["extractor_args"] = {"youtube": {"lang": ["tr"]}}
    with YoutubeDL(opts) as ydl:
        info = ydl.extract_info(f"https://www.youtube.com/channel/{channel_id}/videos",
                                download=False)
    return info.get("entries") or []


def views(channel_id):
    """Video id -> view count, from the untranslated listing (0 when that fails)."""
    try:
        return {e["id"]: e.get("view_count") or 0 for e in films(channel_id, turkish=False)}
    except Exception as e:  # the order then falls back to the channel's own
        print(f"views {channel_id}: {e}", file=sys.stderr)
        return {}


def main():
    out = {}
    for label, cid in CHANNELS:
        try:
            entries = films(cid)
        except Exception as e:  # one channel failing keeps the others
            print(f"{label}: {e}", file=sys.stderr)
            continue
        counts = views(cid)
        kept = 0
        for e in entries:
            title = e.get("title") or ""
            secs = e.get("duration") or 0
            lt = lower(title)
            if secs < MIN_SECONDS or any(w in lt for w in SKIP):
                continue
            if not any(w in lt for w in COMEDY + TITLES):
                continue
            name, cast, year = clean(title)
            if year and year >= 1995:
                continue
            key = re.sub(r"[^0-9a-zçğıöşü]", "", lower(name))
            if not key:
                continue
            film = {"ad": name, "alt": cast or label, "id": e["id"],
                    "dk": round(secs / 60), "izlenme": counts.get(e["id"], 0)}
            # The same film uploaded twice: keep the most watched copy.
            if key in out and out[key]["izlenme"] >= film["izlenme"]:
                continue
            kept += key not in out
            out[key] = film
        print(f"{label}: {kept} films", file=sys.stderr)
    out = list(out.values())
    if len(out) < 10:
        raise SystemExit("too few films; keeping the old list")
    out.sort(key=lambda f: -f["izlenme"])
    out = out[:LIMIT]
    for f in out:
        del f["izlenme"]
    with open(sys.argv[1], "w", encoding="utf-8") as fh:
        json.dump({"olusturuldu": int(time.time()), "filmler": out}, fh, ensure_ascii=False,
                  indent=0, separators=(",", ":"))


if __name__ == "__main__":
    main()
