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
    private static final long EVERY = 30 * 60 * 1000L;

    static final class Dizi {
        final String ad, kanal, id, bolum;
        final int dk;

        Dizi(String ad, String kanal, String id, String bolum, int dk) {
            this.ad = ad;
            this.kanal = kanal;
            this.id = id;
            this.bolum = bolum;
            this.dk = dk;
        }

        /** "146. Bölüm · Show TV". */
        String alt() {
            if (bolum.isEmpty()) return kanal;
            return kanal.isEmpty() ? bolum : bolum + " · " + kanal;
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
        if (busy || (now - last >= 0 && now - last < (empty ? 5 * 60 * 1000L : EVERY))) return;
        busy = true;
        p.edit().putLong(LAST, now).apply();
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
                out.add(new Dizi(o.optString("ad", ""), o.optString("kanal", ""), id, o.optString("bolum", ""), o.optInt("dk", 0)));
            }
        } catch (JSONException e) {
            Keeper.note("diziler bozuk: " + e.getMessage());
        }
        return out;
    }
}
