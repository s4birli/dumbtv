# Aile ekranı (family screen fork)

This fork replaces dumbtv home's grid with one fixed family screen, built for a Fire TV Stick 4K
(1st gen, Fire OS 6.7) used by a grandmother and small children:

The main screen has three big tiles and a row of apps:

- **Canlı TV**: what is on now on the first four channels. It opens the channel list, grouped
  as Türk kanalları (TRT 1, Kanal D, ATV, Show TV, NOW, TV8), İngiliz kanalları (BBC One and
  Two, ITV1, Channel 4, Channel 5) and Film kanalları (24 saat: MovieSphere, MyTime Movies,
  Artflix Classics). Each row shows its logo, the programme on air with its hours and how far
  it has run; the panel on the right shows now and next for the focused channel. A stream opens
  in Kodi, a YouTube live link in SmartTube, and a UK channel in its broadcaster's own app (BBC
  iPlayer, ITVX, Channel 4, 5), whose row only shows once that app is installed.
- **Ms Rachel**: the children's live stream in SmartTube, with the stream's own YouTube cover.
- **Eski Türk Filmleri**: full Yeşilçam comedies from the production companies' own YouTube
  channels (Arzu Film, Erler Film, Sistem Film), most watched first, opened in SmartTube.
- **Uygulamalar**: Netflix, Prime Video, Disney+, YouTube (SmartTube) and İnternet (Silk, Fire
  TV's own browser), each shown with the app's own TV banner.
- **Header**: clock and date, then Surfshark, Kodi and Ayarlar, which are used rarely.

Back on a list returns to the main screen; HOME always does. Coming back from a channel or a
film opened in a list returns to that list, where the remote left it.

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
and the children's tile can change this way, never which apps the screen launches. A channel
has `ad`, `url` and optionally `grup` (its heading in the list), `logo` (a picture address),
`"logoZemin": "koyu"` for a white logo, `rehber` (its name in the guide), and, for a channel
watched in an app, `uygulama` and `paket` (the app's package names; the first installed wins).
The children's tile takes an optional `kapak`: a picture address that fills the tile instead
of the stream's own cover (which comes back if the picture cannot be had), and `"yazi": false`
to show the picture without the name over it. Both also work in `aile.json` on the device, which
is read again on the next HOME after it changes.

## Eski Türk Filmleri

`launcher/filmler/build.py` lists the full films (an hour or longer, before 1995) that Arzu Film,
Erler Film and Sistem Film publish on their own YouTube channels, keeps the comedies (a comedy
cast or a known comedy title), drops trailers, clips and compilations, and orders them by views.
The `aile-filmler` workflow runs it on the 3rd of each month and publishes `aile-filmler.json`
on the `filmler` branch; the TV reads it twice a day and keeps the last copy. If YouTube turns
GitHub's runner away the run fails and the TV keeps the old list; run the script on a Mac then
(`pip install yt-dlp`) and push the file to the `filmler` branch by hand.

## What is on now

The Canlı TV tile and the channel list show the programme on air. `launcher/rehber/build.py` takes
the day's schedule from Türksat Kablo's public EPG files, and the `aile-rehber` GitHub Actions
workflow runs it every two hours (and on any push that changes the channel list) and publishes
`aile-rehber.json` on the `rehber` branch. The TV reads that once an hour and keeps the last
copy. Each programme carries its start and end, so a stale copy shows nothing rather than a
wrong title. A channel whose guide name differs from its tile name sets `"rehber"` in
`aile-kanallar.json`. GitHub turns off scheduled workflows in a public repository after 60 days
without activity; re-enable it (and aile-filmler) in the Actions tab if the programmes disappear.

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
