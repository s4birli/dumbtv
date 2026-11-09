# Aile ekranı (family screen fork)

This fork replaces dumbtv home's grid with one fixed family screen, built for a Fire TV Stick 4K
(1st gen, Fire OS 6.7) used by a grandmother and small children:

- **Canlı TV**: live Turkish channels (TRT 1, Kanal D, ATV, Show, TV8), each opened in Kodi as a
  stream, or in SmartTube when the address is a YouTube live link.
- **Çocuklar için**: one big tile that opens a YouTube channel or live stream in SmartTube.
- **Ev**: the Blink cameras and Alexa, when installed.
- **Uygulamalar**: Netflix, Prime Video, Disney+, YouTube (SmartTube), Kodi and İnternet (Silk,
  Fire TV's own browser), each shown with the app's own TV banner. Five fit at 320 x 180; a sixth
  shrinks the row to fit.
- **Surfshark** (header pill): Android runs one VPN at a time, and Surfshark refuses to connect
  while the DNS filter's VPN is up ("Another VPN was detected"). The pill closes the filter the
  way its own CLOSE button does (its screen started with `SHOULD_FINISH`), waits for its VPN to
  go, then opens Surfshark. The filter stays off while Surfshark is connected and comes back on
  the next HOME press once Surfshark is turned off, or after 10 minutes if Surfshark never
  connected. Amazon's ads are not filtered while Surfshark is on.
- The backdrop is an Istanbul skyline that follows the time of day (morning, day, sunset,
  night), worked out on the device from Reading's sunrise and sunset (`Gunes.java`).

Everything that makes HOME work is unchanged: `Keeper`, `BootReceiver`, `Guard` and the DNS
filter restart. The screen itself is `Aile.java`; `Home.java` hands over to it when `Aile.ON`.

## Install (from a Mac with adb)

```sh
adb connect <tv-ip>:5555
adb uninstall tv.dumbtv.home          # only the first time: the release is signed with another key
./launcher/install.sh --apk dumbtv-home-aile.apk
```

Keep `launcher/release.keystore`: every later update must be signed with the same key.

## Fixing a channel from anywhere

The TV reads `launcher/aile-kanallar.json` from this branch on GitHub (raw.githubusercontent.com)
at most every 10 minutes, on HOME and while the screen is up, and keeps the last good copy for
when it is offline. Edit that file and push: the TV picks it up without adb. Only the channels
and the children's tile can change this way, never which apps the screen launches. Up to six
channels fit; more than four share the row.

## What is on now

Each channel tile shows the programme on air under its name. `launcher/rehber/build.py` takes
the day's schedule from Türksat Kablo's public EPG files, and the `aile-rehber` GitHub Actions
workflow runs it every two hours (and on any push that changes the channel list) and publishes
`aile-rehber.json` on the `rehber` branch. The TV reads that once an hour and keeps the last
copy. Each programme carries its start and end, so a stale copy shows nothing rather than a
wrong title. A channel whose guide name differs from its tile name sets `"rehber"` in
`aile-kanallar.json`. GitHub turns off scheduled workflows in a public repository after 60 days
without activity; re-enable it in the Actions tab if the line under the channels disappears.

## Changing a channel without a new build (on the device)

Stream addresses change. Put an `aile.json` next to the app and press HOME:

```sh
adb push aile.json /sdcard/Android/data/tv.dumbtv.home/files/aile.json
```

Any key left out keeps its built-in value. `logo` shows another installed app's TV banner on a
tile (the YouTube tile opens SmartTube but shows the official YouTube banner):

```json
{
  "kanallar": [
    {"ad": "TRT 1", "url": "https://tv-trt1.medya.trt.com.tr/master.m3u8"},
    {"ad": "Kanal D", "url": "https://demiroren.daioncdn.net/kanald/kanald.m3u8?app=kanald_web&ce=3"}
  ],
  "cocuk": {"ad": "Ms Rachel", "alt": "Canlı yayın · YouTube", "url": "https://www.youtube.com/watch?v=VIDEO_ID", "canli": true},
  "ev": [{"ad": "Kameralar", "alt": "Blink", "paketler": ["package.name"]}],
  "uygulamalar": [{"ad": "YouTube", "paketler": ["org.smarttube.stable"], "logo": "com.amazon.firetv.youtube"}],
  "surfshark": ["com.surfshark.vpnclient.android"],
  "oynatici": "org.xbmc.kodi",
  "uzak": "https://raw.githubusercontent.com/s4birli/dumbtv/aile/launcher/aile-kanallar.json"
}
```

## Surfshark notes

- Do not revoke the filter's VPN instead (another app calling `VpnService.prepare`): the filter
  then keeps a half-stopped process whose boot receiver only opens its screen, so it never comes
  back by itself, and it pops its screen over Surfshark twice.
- `all.sh` sets `always_on_vpn_app`. Fire OS 6 drops it at the next boot, but until then the
  filter is the always-on VPN and every other VPN is refused. Reboot once after `all.sh`.
