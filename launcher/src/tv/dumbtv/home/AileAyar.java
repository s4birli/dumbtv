package tv.dumbtv.home;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * What the family screen shows. Built-in defaults, overridden by an optional
 * {@code aile.json} in the app's files folder, so a stream address that changes is fixed with
 * one {@code adb push} instead of a new build:
 * {@code /sdcard/Android/data/tv.dumbtv.home/files/aile.json}. A key left out keeps its default.
 */
final class AileAyar {
    static final String FILE = "aile.json";
    /**
     * The channel list fetched from the internet (see {@link Uzak}): editing this file in the
     * repository fixes a channel on the TV within minutes. aile.json can point elsewhere with
     * "uzak", or switch it off with "uzak": "".
     */
    static final String UZAK = "https://raw.githubusercontent.com/s4birli/dumbtv/aile/launcher/aile-kanallar.json";
    static final String KODI = "org.xbmc.kodi";
    /** Apps that open a YouTube link, best first: SmartTube has no ads. */
    static final String[] YOUTUBE = {"org.smarttube.stable", "org.smarttube.beta", "com.amazon.firetv.youtube"};

    /**
     * A live channel: a stream address opened in {@link #player}, or a YouTube live link opened
     * in {@link #YOUTUBE}.
     */
    static final class Kanal {
        final String ad, url;
        /** The channel's name in the TV guide when it differs from {@link #ad}; else null. */
        String rehber;
        /** The list heading it sits under. */
        String grup = "Türk kanalları";
        /** A picture of the channel's logo, and whether that logo is light (shown on dark). */
        String logo;
        boolean logoKoyu;
        /** For a channel watched in its broadcaster's app: the app (first installed wins). */
        String[] paket;
        String uygulama;

        Kanal(String ad, String url) {
            this.ad = ad;
            this.url = url;
        }

        boolean app() {
            return paket != null && paket.length > 0;
        }

        Kanal grup(String g) {
            grup = g;
            return this;
        }

        Kanal logo(String l, boolean koyu) {
            logo = l;
            logoKoyu = koyu;
            return this;
        }

        Kanal app(String name, String... pkgs) {
            uygulama = name;
            paket = pkgs;
            return this;
        }
    }

    /** A link tile: a web address opened in a given app (a YouTube channel in SmartTube). */
    static final class Link {
        final String ad, alt, url;
        final String[] paketler;
        final boolean canli;
        /** A picture for the tile chosen by the family ("kapak"); else the video's own cover. */
        String kapak;
        /** "yazi": false shows the picture alone, without the name over it. */
        boolean yazi = true;

        Link(String ad, String alt, String url, String[] paketler, boolean canli) {
            this.ad = ad;
            this.alt = alt;
            this.url = url;
            this.paketler = paketler;
            this.canli = canli;
        }
    }

    /**
     * An app slot: the first installed package of {@link #paketler} fills it. {@link #logo}, when
     * set, names another installed app whose TV banner the tile shows instead of its own.
     */
    static final class Uygulama {
        final String ad, alt;
        final String[] paketler;
        String logo;

        Uygulama(String ad, String alt, String[] paketler) {
            this.ad = ad;
            this.alt = alt;
            this.paketler = paketler;
        }

        Uygulama logo(String pkg) {
            logo = pkg;
            return this;
        }
    }

    final List<Kanal> kanallar = new ArrayList<>();
    Link cocuk;
    final List<Uygulama> ev = new ArrayList<>();
    final List<Uygulama> uygulamalar = new ArrayList<>();
    String[] surfshark;
    String player = KODI;
    String uzak = UZAK;

    private AileAyar() {}

    static boolean isYoutube(String url) {
        return url.contains("youtube.com/") || url.contains("youtu.be/");
    }

    /** The 11 character video id of a YouTube link, or null. */
    static String youtubeId(String url) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?:v=|youtu\\.be/|/live/|/embed/)([A-Za-z0-9_-]{11})").matcher(url);
        return m.find() ? m.group(1) : null;
    }

    static AileAyar defaults() {
        AileAyar a = new AileAyar();
        // Built-in copy of launcher/aile-kanallar.json, for the first start before it is fetched.
        String tr = "Türk kanalları", uk = "İngiliz kanalları", film = "Film kanalları (24 saat)";
        String wm = "https://upload.wikimedia.org/wikipedia/";
        a.kanallar.add(new Kanal("TRT 1", "https://tv-trt1.medya.trt.com.tr/master.m3u8")
                .logo(wm + "commons/thumb/8/85/TRT_1_logo_%282021-%29.svg/960px-TRT_1_logo_%282021-%29.svg.png", false));
        a.kanallar.add(new Kanal("Kanal D", "https://demiroren.daioncdn.net/kanald/kanald.m3u8?app=kanald_web&ce=3")
                .logo("https://static.kanald.com.tr/images/kanald-logo-250x60.png", true));
        // 720p: the 1080p playlist of this CDN times out about every other request.
        a.kanallar.add(new Kanal("ATV", "https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_720p.m3u8")
                .logo(wm + "commons/thumb/4/45/Atv_logo_2010.svg/960px-Atv_logo_2010.svg.png", false));
        // Show has no public stream address that works; the family uses this YouTube live link.
        Kanal show = new Kanal("Show TV", "https://www.youtube.com/watch?v=WxeHEn5N1vk")
                .logo(wm + "commons/thumb/c/cb/Show_TV_logo.svg/960px-Show_TV_logo.svg.png", false);
        show.rehber = "Show";
        a.kanallar.add(show);
        // Star TV's own 24-hour YouTube stream, in SmartTube.
        a.kanallar.add(new Kanal("Star TV", "https://www.youtube.com/watch?v=82O6yOy_XwE").logo("https://yt3.googleusercontent.com/RZMwSCEwuSYV3R0X10cizuB4Pf_FOPPJrI-JnKytQoAe6gdScle1iWxj-5ApcILVT2hFZmCa0w=s240-c-k-c0x00ffffff-no-rj", false));
        a.kanallar.add(new Kanal("NOW", "https://uycyyuuzyh.turknet.ercdn.net/nphindgytw/nowtv/nowtv.m3u8")
                .logo(wm + "commons/thumb/d/db/NOW_TV_%28Turkey%29_wordmark-red.svg/960px-NOW_TV_%28Turkey%29_wordmark-red.svg.png", false));
        a.kanallar.add(new Kanal("TV8", "https://tv8.daioncdn.net/tv8/tv8.m3u8?app=7ddc255a-ef47-4e81-ab14-c0e5f2949788&ce=3")
                .logo(wm + "tr/thumb/6/68/Tv8_Yeni_Logo.png/960px-Tv8_Yeni_Logo.png", false));
        // The BBC's own UK streams (they answer only in the UK), in Kodi. BBC One is the South
        // edition, Reading's local news.
        String bbc = "https://vs-hls-push%s-uk-live.akamaized.net/x=4/i=urn:bbc:pips:service:%s/pc_hd_abr_v2.m3u8";
        a.kanallar.add(new Kanal("BBC One", String.format(bbc, "b", "bbc_one_south")).grup(uk)
                .logo(wm + "commons/thumb/8/8b/BBC_One_logo_2021.svg/960px-BBC_One_logo_2021.svg.png", false));
        a.kanallar.add(new Kanal("BBC Two", String.format(bbc, "", "bbc_two_hd")).grup(uk)
                .logo(wm + "commons/thumb/1/15/BBC_Two_logo_2021.svg/960px-BBC_Two_logo_2021.svg.png", false));
        a.kanallar.add(new Kanal("BBC Three", String.format(bbc, "b", "bbc_three_hd")).grup(uk)
                .logo(wm + "commons/thumb/7/76/BBC_Three_2022.svg/960px-BBC_Three_2022.svg.png", false));
        a.kanallar.add(new Kanal("BBC Four", String.format(bbc, "b", "bbc_four_hd")).grup(uk)
                .logo(wm + "commons/thumb/6/6d/BBC_Four_logo_2021.svg/960px-BBC_Four_logo_2021.svg.png", false));
        a.kanallar.add(new Kanal("BBC News", String.format(bbc, "", "bbc_news_channel_hd")).grup(uk)
                .logo(wm + "commons/thumb/6/62/BBC_News_2019.svg/960px-BBC_News_2019.svg.png", false));
        a.kanallar.add(new Kanal("ITV1", "").grup(uk).app("ITVX", "air.ITVMobilePlayer", "com.itv.itvx")
                .logo(wm + "commons/thumb/e/e8/ITV1_Logo_2022.svg/960px-ITV1_Logo_2022.svg.png", false));
        a.kanallar.add(new Kanal("Channel 4", "").grup(uk).app("Channel 4", "com.channel4.ondemand")
                .logo("https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/512/512/skychb1621.png", true));
        a.kanallar.add(new Kanal("Channel 5", "").grup(uk).app("5", "com.channel5.my5", "uk.co.channel5.my5")
                .logo("https://d2n0069hmnqmmx.cloudfront.net/epgdata/1.0/newchanlogos/512/512/skychb1800.png", true));
        a.kanallar.add(new Kanal("E4", "").grup(uk).app("Channel 4", "com.channel4.ondemand"));
        // Free channels the broadcasters themselves stream (BBC, and FAST channels on Amagi
        // and Wurl, the feeds of Samsung TV Plus, LG Channels and Rakuten TV).
        String kids = "Çocuk kanalları", fun = "Eğlence ve belgesel";
        a.kanallar.add(new Kanal("CBeebies", String.format(bbc, "b", "cbeebies_hd")).grup(kids)
                .logo(wm + "commons/thumb/b/b5/CBeebies_2023.svg/960px-CBeebies_2023.svg.png", true));
        a.kanallar.add(new Kanal("CBBC", "https://vs-hls-pushb-uk-live.akamaized.net/x=4/i=urn:bbc:pips:service:cbbc_hd/pc_hd_abr_v2.m3u8").grup(kids)
                .logo("https://upload.wikimedia.org/wikipedia/commons/thumb/3/35/CBBC_%282023%29.svg/960px-CBBC_%282023%29.svg.png", true));
        a.kanallar.add(new Kanal("Tiny Pop", "https://amg01753-narrativeuk-amg01753c1-lg-gb-1830.playouts.now.amagi.tv/playlist/amg01753-narrativeuk-tinypop-lggb/playlist.m3u8").grup(kids)
                .logo("https://upload.wikimedia.org/wikipedia/commons/0/02/New_Tiny_Pop_Logo_2018.png", false));
        a.kanallar.add(new Kanal("Pop", "https://amg01753-narrativeentert-popkids-lggb-xyy5k.amagi.tv/ts-eu-w1-n2/playlist/amg01753-narrativeentert-popkids-lggb/playlist.m3u8").grup(kids));
        a.kanallar.add(new Kanal("Moonbug Kids", "https://moonbug-rokuus.amagi.tv/playlist.m3u8").grup(kids)
                .logo("https://upload.wikimedia.org/wikipedia/commons/thumb/7/73/Moonbug_Kids.svg/960px-Moonbug_Kids.svg.png", false));
        a.kanallar.add(new Kanal("Mr Bean çizgi", "https://amg00627-amg00627c23-samsung-au-4110.playouts.now.amagi.tv/playlist.m3u8").grup(kids)
                .logo("https://d2yv6y0n3qsl5c.cloudfront.net/MBEANP/MrBeanLiveAction-LG_LOGO_800x200.png", true));
        a.kanallar.add(new Kanal("Mr Bean", "https://amg00627-amg00627c39-samsung-gb-5569.playouts.now.amagi.tv/playlist.m3u8").grup(fun)
                .logo("https://d2yv6y0n3qsl5c.cloudfront.net/MBEANP/MrBeanLiveAction-LG_LOGO_800x200.png", true));
        a.kanallar.add(new Kanal("Graham Norton", "https://amg00654-itv-amg00654c35-rakuten-gb-7598.playouts.now.amagi.tv/playlist.m3u8").grup(fun)
                .logo("https://image.tmdb.org/t/p/w500/xZkWYamzlT6nFu9MI2Rr6NkjedX.png", true));
        a.kanallar.add(new Kanal("Hell's Kitchen", "https://amg00654-itv-amg00654c1-samsung-au-1072.playouts.now.amagi.tv/itv-hellskitchen-samsung/playlist.m3u8").grup(fun));
        a.kanallar.add(new Kanal("Homes Under the Hammer", "https://all3media-homes-under-the-hammer-1-au.samsung.wurl.tv/playlist.m3u8").grup(fun));
        a.kanallar.add(new Kanal("NatureTime", "https://amg01515-amg01515c43-samsung-gb-9038.playouts.now.amagi.tv/playlist.m3u8").grup(fun));
        a.kanallar.add(new Kanal("Tastemade", "https://tastemade-tdint-rakuten.amagi.tv/playlist.m3u8").grup(fun)
                .logo("https://upload.wikimedia.org/wikipedia/commons/thumb/b/bc/TastemadeLogo.svg/960px-TastemadeLogo.svg.png", false));
        a.kanallar.add(new Kanal("The Pet Collective", "https://the-pet-collective-international-gb.samsung.wurl.tv/playlist.m3u8").grup(fun));
        a.kanallar.add(new Kanal("MovieSphere", "https://moviesphereuk-samsunguk.amagi.tv/playlist.m3u8").grup(film));
        a.kanallar.add(new Kanal("MyTime Movies", "https://appletree-mytimeuk-rakuten.amagi.tv/playlist.m3u8").grup(film));
        a.kanallar.add(new Kanal("GREAT! movies", "https://amg01753-narrativeuk-amg01753c3-lg-gb-1833.playouts.now.amagi.tv/playlist/amg01753-narrativeuk-greatmovies-lggb/playlist.m3u8").grup(film));
        a.kanallar.add(new Kanal("Artflix Classics", "https://amogonetworx-artflix-1-nl.samsung.wurl.tv/playlist.m3u8").grup(film));
        // The live stream the family watches. A live video's id can change when the stream
        // restarts: then put the new link in aile.json ("cocuk": {"url": ...}).
        a.cocuk = new Link("Ms Rachel", "Canlı yayın · YouTube",
                "https://www.youtube.com/watch?v=jBvzOfT8_44",
                YOUTUBE, true);
        a.ev.add(new Uygulama("Kameralar", "Blink", new String[] {
            "com.immediasemi.android.blink", "com.immediasemi.blink"}));
        a.ev.add(new Uygulama("Alexa", "Sesli asistan", new String[] {
            "com.amazon.alexa.multimodal.lyra", "com.amazon.dee.app", "com.amazon.vizzini"}));
        a.uygulamalar.add(new Uygulama("Netflix", null, new String[] {"com.netflix.ninja"}));
        a.uygulamalar.add(new Uygulama("Prime Video", null, new String[] {"com.amazon.firebat", "com.amazon.avod"}));
        a.uygulamalar.add(new Uygulama("Disney+", null, new String[] {"com.disney.disneyplus"}));
        // SmartTube opens it, but the tile shows the official app's YouTube banner, which the
        // family knows. The official app stays installed for that (and as a fallback).
        a.uygulamalar.add(new Uygulama("YouTube", null, YOUTUBE).logo("com.amazon.firetv.youtube"));
        // Fire TV's own browser: Chrome has no Fire TV build, and its phone build needs a mouse.
        a.uygulamalar.add(new Uygulama("İnternet", null, new String[] {"com.amazon.cloud9"}));
        a.surfshark = new String[] {"com.surfshark.vpnclient.android"};
        return a;
    }

    /**
     * Defaults, then the last channel list fetched from the internet, then whatever aile.json on
     * the device overrides. A broken file is ignored, not fatal.
     */
    /** When aile.json on the device last changed (0 when there is none), to notice an edit. */
    static long localStamp(Context c) {
        File dir = c.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, FILE);
        return f != null && f.isFile() ? f.lastModified() : 0;
    }

    static AileAyar load(Context c) {
        AileAyar a = defaults();
        File cache = new File(c.getFilesDir(), Uzak.CACHE);
        if (cache.isFile()) {
            try {
                a.applyUzak(new JSONObject(read(cache)));
            } catch (IOException | JSONException e) {
                Keeper.note("uzak ayar ignored: " + e.getMessage());
            }
        }
        File dir = c.getExternalFilesDir(null);
        File f = dir == null ? null : new File(dir, FILE);
        if (f == null || !f.isFile()) return a;
        try {
            a.apply(new JSONObject(read(f)));
        } catch (IOException | JSONException e) {
            Keeper.note("aile.json ignored: " + e.getMessage());
        }
        return a;
    }

    /**
     * The fetched list may change only the channels and the children's tile. A channel may name
     * its broadcaster's app; it shows only once that app is installed. Broken entries are
     * skipped; an empty list keeps the current one.
     */
    private void applyUzak(JSONObject o) throws JSONException {
        if (o.has("kanallar")) {
            List<Kanal> list = new ArrayList<>();
            JSONArray arr = o.getJSONArray("kanallar");
            for (int i = 0; i < arr.length(); i++) {
                Kanal kanal = kanal(arr.optJSONObject(i));
                if (kanal != null) list.add(kanal);
            }
            if (!list.isEmpty()) {
                kanallar.clear();
                kanallar.addAll(list);
            }
        }
        JSONObject k = o.optJSONObject("cocuk");
        if (k != null) {
            String url = k.optString("url", cocuk.url).trim();
            if (web(url)) {
                Link old = cocuk;
                cocuk = new Link(k.optString("ad", old.ad), k.optString("alt", old.alt), url,
                        old.paketler, k.optBoolean("canli", old.canli));
                String yeni = k.optString("kapak", "").trim();
                cocuk.kapak = web(yeni) ? yeni : old.kapak;
                cocuk.yazi = k.optBoolean("yazi", old.yazi);
            }
        }
    }

    private static boolean web(String url) {
        return url.startsWith("https://") || url.startsWith("http://");
    }

    /**
     * One channel of a list. A stream or YouTube channel needs its web address; a channel
     * watched in its broadcaster's app needs the app's package names (its address, a page of
     * that app, is optional). Anything else is skipped.
     */
    private static Kanal kanal(JSONObject k) throws JSONException {
        if (k == null) return null;
        String ad = k.optString("ad", "").trim(), url = k.optString("url", "").trim();
        JSONArray paket = k.optJSONArray("paket");
        boolean app = paket != null && paket.length() > 0;
        if (ad.isEmpty() || (!app && !web(url)) || (!url.isEmpty() && !web(url))) return null;
        Kanal kanal = new Kanal(ad, url);
        String r = k.optString("rehber", "").trim();
        if (!r.isEmpty()) kanal.rehber = r;
        String g = k.optString("grup", "").trim();
        if (!g.isEmpty()) kanal.grup = g;
        String logo = k.optString("logo", "").trim();
        if (web(logo)) kanal.logo = logo;
        kanal.logoKoyu = "koyu".equals(k.optString("logoZemin", ""));
        if (app) {
            kanal.paket = strings(paket);
            kanal.uygulama = k.optString("uygulama", ad);
        }
        return kanal;
    }

    private void apply(JSONObject o) throws JSONException {
        if (o.has("kanallar")) {
            kanallar.clear();
            JSONArray arr = o.getJSONArray("kanallar");
            for (int i = 0; i < arr.length(); i++) {
                Kanal kanal = kanal(arr.optJSONObject(i));
                if (kanal != null) kanallar.add(kanal);
            }
        }
        if (o.has("cocuk")) {
            JSONObject k = o.getJSONObject("cocuk");
            Link old = cocuk;
            cocuk = new Link(k.optString("ad", old.ad), k.optString("alt", old.alt),
                    k.optString("url", old.url),
                    k.has("paketler") ? strings(k.getJSONArray("paketler")) : old.paketler,
                    k.optBoolean("canli", old.canli));
            String kapak = k.optString("kapak", "").trim();
            cocuk.kapak = web(kapak) ? kapak : old.kapak;
            cocuk.yazi = k.optBoolean("yazi", old.yazi);
        }
        if (o.has("ev")) {
            ev.clear();
            ev.addAll(apps(o.getJSONArray("ev")));
        }
        if (o.has("uygulamalar")) {
            uygulamalar.clear();
            uygulamalar.addAll(apps(o.getJSONArray("uygulamalar")));
        }
        if (o.has("surfshark")) surfshark = strings(o.getJSONArray("surfshark"));
        player = o.optString("oynatici", player);
        uzak = o.optString("uzak", uzak);
    }

    private static List<Uygulama> apps(JSONArray arr) throws JSONException {
        List<Uygulama> out = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject u = arr.getJSONObject(i);
            String alt = u.optString("alt", "");
            String logo = u.optString("logo", "");
            out.add(new Uygulama(u.getString("ad"), alt.isEmpty() ? null : alt,
                    strings(u.getJSONArray("paketler"))).logo(logo.isEmpty() ? null : logo));
        }
        return out;
    }

    private static String[] strings(JSONArray arr) throws JSONException {
        String[] s = new String[arr.length()];
        for (int i = 0; i < s.length; i++) s[i] = arr.getString(i);
        return s;
    }

    static String read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) Math.min(f.length(), 64 * 1024)];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            return new String(buf, 0, n, StandardCharsets.UTF_8);
        }
    }
}
