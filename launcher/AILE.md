# Aile ekranı (family screen fork)

This fork replaces dumbtv home's grid with one fixed family screen, built for a Fire TV Stick 4K
(1st gen, Fire OS 6.7) used by a grandmother and small children:

- **Canlı TV**: live Turkish channels, each opened in Kodi as a stream.
- **Çocuklar için**: one big tile that opens a YouTube channel or live stream in SmartTube.
- **Ev**: the Blink cameras and Alexa, when installed.
- **Uygulamalar**: Netflix, Prime Video, Disney+, YouTube (SmartTube) and Kodi, each shown with
  the app's own TV banner.
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

Any key left out keeps its built-in value:

```json
{
  "kanallar": [
    {"ad": "TRT 1", "url": "https://tv-trt1.medya.trt.com.tr/master.m3u8"},
    {"ad": "Kanal D", "url": "https://demiroren.daioncdn.net/kanald/kanald.m3u8?app=kanald_web&ce=3"}
  ],
  "cocuk": {"ad": "Ms Rachel", "alt": "Canlı yayın · YouTube", "url": "https://www.youtube.com/watch?v=VIDEO_ID", "canli": true},
  "ev": [{"ad": "Kameralar", "alt": "Blink", "paketler": ["package.name"]}],
  "uygulamalar": [{"ad": "Netflix", "paketler": ["com.netflix.ninja"]}],
  "surfshark": ["com.surfshark.vpnclient.android"],
  "oynatici": "org.xbmc.kodi"
}
```
