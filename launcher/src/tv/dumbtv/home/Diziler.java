package tv.dumbtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The "Diziler" list: the newest full episode of each show the family follows, from the shows'
 * own YouTube channels. A GitHub Actions job (.github/workflows/aile-diziler.yml) looks every 30
 * minutes and publishes aile-diziler.json on the "diziler" branch; the TV reads it every 30
 * minutes and keeps the last copy.
 */
final class Diziler {
    static final String URL = "https://raw.githubusercontent.com/s4birli/dumbtv/diziler/aile-diziler.json";
    private static final String CACHE = "aile-diziler.json";
    private static final String PREFS = "aile_diziler";
    private static final String LAST = "last_try";
    /** The app version that last fetched: a new version fetches at once, its list may read more. */
    private static final String VERSION = "version";
    private static final long EVERY = 30 * 60 * 1000L;

    static final class Dizi {
        /** bolum: "146. Bölüm" for a series; tarih: "13 Kasım Cuma" for a daily show. */
        final String ad, kanal, id, bolum, tarih;
        final int dk;
        /** When it went up on YouTube, unix seconds (0: not known); yaklasik: the day only. */
        final long yayin;
        final boolean yaklasik;

        Dizi(String ad, String kanal, String id, String bolum, String tarih, int dk, long yayin,
                boolean yaklasik) {
            this.ad = ad;
            this.kanal = kanal;
            this.id = id;
            this.bolum = bolum;
            this.tarih = tarih;
            this.dk = dk;
            this.yayin = yayin;
            this.yaklasik = yaklasik;
        }

        /** "146. Bölüm · Show TV", or just the channel for a daily show. */
        String alt() {
            if (bolum.isEmpty()) return kanal;
            return kanal.isEmpty() ? bolum : bolum + " · " + kanal;
        }

        /** What the Diziler tile shows beside the name: the episode, or the day of a daily show. */
        String kisa() {
            return !bolum.isEmpty() ? bolum : tarih;
        }

        /** "Yayın: Dün 21:01", "Yayın: Çarşamba", "Yayın: 13 Kasım Cuma"; empty if not known. */
        String ne_zaman(long nowMs) {
            if (!tarih.isEmpty()) return "Yayın: " + tarih;
            if (yayin <= 0) return "";
            java.util.Calendar now = java.util.Calendar.getInstance();
            now.setTimeInMillis(nowMs);
            java.util.Calendar at = java.util.Calendar.getInstance();
            at.setTimeInMillis(yayin * 1000);
            int days = dayNumber(now) - dayNumber(at);
            java.util.Locale tr = new java.util.Locale("tr", "TR");
            String day = days <= 0 ? "Bugün" : days == 1 ? "Dün"
                    : days < 7 ? new java.text.SimpleDateFormat("EEEE", tr).format(at.getTime())
                    : new java.text.SimpleDateFormat("d MMMM", tr).format(at.getTime());
            if (!yaklasik && days < 7) {
                day += " " + new java.text.SimpleDateFormat("HH:mm", tr).format(at.getTime());
            }
            return "Yayın: " + day;
        }

        private static int dayNumber(java.util.Calendar c) {
            return c.get(java.util.Calendar.YEAR) * 400 + c.get(java.util.Calendar.DAY_OF_YEAR);
        }

        String link() {
            return "https://www.youtube.com/watch?v=" + id;
        }

        /** YouTube's own cover of the video, 480 x 360 (16:9 inside bars the card crops away). */
        String kapak() {
            return "https://i.ytimg.com/vi/" + id + "/hqdefault.jpg";
        }

        String sure() {
            return dk >= 60 ? (dk / 60) + " sa " + (dk % 60) + " dk" : dk + " dk";
        }
    }

    private static List<Dizi> list;
    private static volatile boolean busy;

    private Diziler() {}

    static List<Dizi> all(Context c) {
        if (list == null) list = parse(readCache(c));
        return list;
    }

    static void refresh(Context c, Runnable changed) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = p.getLong(LAST, 0);
        boolean empty = all(c).isEmpty();
        int version = versionCode(c);
        boolean upgraded = p.getInt(VERSION, 0) != version;
        if (busy || (!upgraded && now - last >= 0 && now - last < (empty ? 5 * 60 * 1000L : EVERY))) return;
        busy = true;
        p.edit().putLong(LAST, now).putInt(VERSION, version).apply();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                String body = Uzak.fetch(URL);
                List<Dizi> fresh = parse(body);
                if (fresh.isEmpty()) return;
                File f = new File(app.getFilesDir(), CACHE);
                File tmp = new File(app.getFilesDir(), CACHE + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(f)) return;
                new Handler(Looper.getMainLooper()).post(() -> {
                    list = fresh;
                    changed.run();
                });
            } catch (IOException | RuntimeException e) {
                Keeper.note("diziler alinamadi: " + e);
            } finally {
                busy = false;
            }
        }, "aile-diziler").start();
    }

    @SuppressWarnings("deprecation")
    private static int versionCode(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionCode;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private static String readCache(Context c) {
        File f = new File(c.getFilesDir(), CACHE);
        if (!f.isFile()) return null;
        try {
            return AileAyar.read(f);
        } catch (IOException e) {
            return null;
        }
    }

    private static List<Dizi> parse(String body) {
        if (body == null) return Collections.emptyList();
        List<Dizi> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(body).getJSONArray("diziler");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                // A YouTube video id: 11 letters, digits, - and _. Anything else is not opened.
                if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
                out.add(new Dizi(o.optString("ad", ""), o.optString("kanal", ""), id, o.optString("bolum", ""),
                        o.optString("tarih", ""), o.optInt("dk", 0), o.optLong("yayin", 0),
                        o.optBoolean("yaklasik", false)));
            }
        } catch (JSONException e) {
            Keeper.note("diziler bozuk: " + e.getMessage());
        }
        return out;
    }
}
