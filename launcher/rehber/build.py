#!/usr/bin/env python3
"""Builds the family screen's TV guide: what each channel shows now and next.

Reads the channel names from aile-kanallar.json, takes today's and tomorrow's schedule from
Türksat Kablo's public EPG files, and writes a small JSON the TV reads once an hour:

    {"olusturuldu": 1794233400, "kanallar": {"trt1": [[start, stop, "title"], ...], ...}}

Times are Unix seconds. Keys are channel names lowercased with everything but letters and
digits removed ("TV 8" and "TV8" both become "tv8"), which is how the TV matches its tiles.
A channel can name its guide entry explicitly with "rehber" in aile-kanallar.json.

Türksat's server sends its certificate without the intermediate, which browsers fetch on their
own but Python does not, so the intermediate is fetched from the address in the certificate.
Usage: build.py aile-kanallar.json out.json
"""
import json
import re
import ssl
import subprocess
import sys
import tempfile
import time
import urllib.request
from datetime import datetime, timedelta, timezone

HOST = "www.turksatkablo.com.tr"
URL = "https://" + HOST + "/userUpload/EPG/{day}.json"
IST = timezone(timedelta(hours=3))  # Turkey has kept UTC+3 all year since 2016
BEHIND = 3 * 3600   # keep programmes that ended up to 3 hours ago
AHEAD = 36 * 3600   # and those starting in the next 36 hours


def key(name):
    return re.sub(r"[^0-9a-zçğıöşü]", "", name.lower())


def context():
    ctx = ssl.create_default_context()
    try:
        urllib.request.urlopen("https://" + HOST + "/", context=ctx, timeout=20).close()
        return ctx
    except ssl.SSLError:
        pass
    except urllib.error.URLError as e:
        if not isinstance(e.reason, ssl.SSLError):
            return ctx
    leaf = ssl.get_server_certificate((HOST, 443))
    with tempfile.NamedTemporaryFile("w", suffix=".pem") as f:
        f.write(leaf)
        f.flush()
        text = subprocess.run(["openssl", "x509", "-noout", "-text", "-in", f.name],
                              capture_output=True, text=True, check=True).stdout
    m = re.search(r"CA Issuers - URI:(\S+)", text)
    if not m:
        raise SystemExit("no CA Issuers address in the certificate")
    raw = urllib.request.urlopen(m.group(1), timeout=20).read()
    pem = raw.decode() if raw.startswith(b"-----BEGIN") else ssl.DER_cert_to_PEM_cert(raw)
    ctx = ssl.create_default_context()
    ctx.load_verify_locations(cadata=pem)
    ctx.verify_flags |= ssl.VERIFY_X509_PARTIAL_CHAIN
    return ctx


def day(ctx, date):
    req = urllib.request.Request(URL.format(day=date.day), headers={"User-Agent": "aile-rehber"})
    with urllib.request.urlopen(req, context=ctx, timeout=60) as r:
        return json.load(r)


def programmes(entries, date):
    """Türksat lists one day from 00:00 Istanbul time; times past midnight roll over."""
    out, base, prev = [], datetime(date.year, date.month, date.day, tzinfo=IST), None
    shift = 0
    for p in entries:
        title = (p.get("b") or "").strip()
        try:
            sh, sm = map(int, p["c"].split(":"))
            eh, em = map(int, p["d"].split(":"))
        except (KeyError, ValueError):
            continue
        start = base + timedelta(days=shift, hours=sh, minutes=sm)
        if prev is not None and start < prev:
            shift += 1
            start += timedelta(days=1)
        stop = base + timedelta(days=shift, hours=eh, minutes=em)
        if stop <= start:
            stop += timedelta(days=1)
        prev = start
        if title and title != "-":
            out.append([int(start.timestamp()), int(stop.timestamp()), title])
    return out


def main():
    cfg = json.load(open(sys.argv[1], encoding="utf-8"))
    wanted = {}
    for k in cfg.get("kanallar", []):
        wanted[key(k.get("rehber") or k["ad"])] = k["ad"]
    ctx = context()
    now = int(time.time())
    today = datetime.now(IST).date()
    guide = {w: [] for w in wanted}
    for d in (today, today + timedelta(days=1)):
        try:
            data = day(ctx, d)
        except Exception as e:  # tomorrow's file may not be there yet
            print(f"{d}: {e}", file=sys.stderr)
            if d == today:
                raise
            continue
        seen = set()
        for ch in data.get("k", []):
            k = key(ch.get("n", ""))
            if k in guide and k not in seen:
                seen.add(k)
                guide[k] += programmes(ch.get("p", []), d)
    for k, items in guide.items():
        items.sort()
        dedup = []
        for it in items:
            if dedup and it[0] == dedup[-1][0]:
                continue
            if it[1] > now - BEHIND and it[0] < now + AHEAD:
                dedup.append(it)
        guide[k] = dedup
        print(f"{wanted[k]}: {len(dedup)} programmes", file=sys.stderr)
    if not any(guide.values()):
        raise SystemExit("no programmes found")
    with open(sys.argv[2], "w", encoding="utf-8") as f:
        json.dump({"olusturuldu": now, "kaynak": HOST, "kanallar": guide}, f,
                  ensure_ascii=False, separators=(",", ":"))


if __name__ == "__main__":
    main()
