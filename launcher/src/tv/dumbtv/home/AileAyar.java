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

        Kanal(String ad, String url) {
            this.ad = ad;
            this.url = url;
        }
    }

    /** A link tile: a web address opened in a given app (a YouTube channel in SmartTube). */
    static final class Link {
        final String ad, alt, url;
        final String[] paketler;
        final boolean canli;

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

    static AileAyar defaults() {
        AileAyar a = new AileAyar();
        a.kanallar.add(new Kanal("TRT 1", "https://tv-trt1.medya.trt.com.tr/master.m3u8"));
        a.kanallar.add(new Kanal("Kanal D", "https://demiroren.daioncdn.net/kanald/kanald.m3u8?app=kanald_web&ce=3"));
        // 720p: the 1080p playlist of this CDN times out about every other request.
        a.kanallar.add(new Kanal("ATV", "https://rnttwmjcin.turknet.ercdn.net/lcpmvefbyo/atv/atv_720p.m3u8"));
        // Show has no public stream address that works; the family uses this YouTube live link.
        a.kanallar.add(new Kanal("Show", "https://www.youtube.com/watch?v=WxeHEn5N1vk"));
        a.kanallar.add(new Kanal("TV8", "https://tv8.daioncdn.net/tv8/tv8.m3u8?app=7ddc255a-ef47-4e81-ab14-c0e5f2949788&ce=3"));
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
        a.uygulamalar.add(new Uygulama("Kodi", null, new String[] {KODI}));
        // Fire TV's own browser: Chrome has no Fire TV build, and its phone build needs a mouse.
        a.uygulamalar.add(new Uygulama("İnternet", null, new String[] {"com.amazon.cloud9"}));
        a.surfshark = new String[] {"com.surfshark.vpnclient.android"};
        return a;
    }

    /**
     * Defaults, then the last channel list fetched from the internet, then whatever aile.json on
     * the device overrides. A broken file is ignored, not fatal.
     */
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
     * The fetched list may change only what a channel or the children's tile opens: never which
     * apps run. Entries without a web address are skipped; an empty list keeps the current one.
     */
    private void applyUzak(JSONObject o) throws JSONException {
        if (o.has("kanallar")) {
            List<Kanal> list = new ArrayList<>();
            JSONArray arr = o.getJSONArray("kanallar");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject k = arr.optJSONObject(i);
                if (k == null) continue;
                String ad = k.optString("ad", "").trim(), url = k.optString("url", "").trim();
                if (ad.isEmpty() || !web(url)) continue;
                list.add(new Kanal(ad, url));
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
                cocuk = new Link(k.optString("ad", cocuk.ad), k.optString("alt", cocuk.alt), url,
                        cocuk.paketler, k.optBoolean("canli", cocuk.canli));
            }
        }
    }

    private static boolean web(String url) {
        return url.startsWith("https://") || url.startsWith("http://");
    }

    private void apply(JSONObject o) throws JSONException {
        if (o.has("kanallar")) {
            kanallar.clear();
            JSONArray arr = o.getJSONArray("kanallar");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject k = arr.getJSONObject(i);
                kanallar.add(new Kanal(k.getString("ad"), k.getString("url")));
            }
        }
        if (o.has("cocuk")) {
            JSONObject k = o.getJSONObject("cocuk");
            cocuk = new Link(k.optString("ad", cocuk.ad), k.optString("alt", cocuk.alt),
                    k.optString("url", cocuk.url),
                    k.has("paketler") ? strings(k.getJSONArray("paketler")) : cocuk.paketler,
                    k.optBoolean("canli", cocuk.canli));
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
