# Aile ekranı (family screen fork)

This fork replaces dumbtv home's grid with one fixed family screen, built for a Fire TV Stick 4K
(1st gen, Fire OS 6.7) used by a grandmother and small children:

- **Canlı TV**: live Turkish channels, each opened in Kodi as a stream.
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

## Changing a channel without a new build

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
  "oynatici": "org.xbmc.kodi"
}
```

## Surfshark notes

- Do not revoke the filter's VPN instead (another app calling `VpnService.prepare`): the filter
  then keeps a half-stopped process whose boot receiver only opens its screen, so it never comes
  back by itself, and it pops its screen over Surfshark twice.
- `all.sh` sets `always_on_vpn_app`. Fire OS 6 drops it at the next boot, but until then the
  filter is the always-on VPN and every other VPN is refused. Reboot once after `all.sh`.
