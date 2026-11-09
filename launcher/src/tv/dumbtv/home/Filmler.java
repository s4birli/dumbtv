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
 * The "Eski Türk Filmleri" list: full films their rights holders publish on their own YouTube
 * channels. A GitHub Actions job (.github/workflows/aile-filmler.yml) rebuilds it every month and
 * publishes aile-filmler.json on the "filmler" branch; the TV reads it at most twice a day and
 * keeps the last copy.
 */
final class Filmler {
    static final String URL = "https://raw.githubusercontent.com/s4birli/dumbtv/filmler/aile-filmler.json";
    private static final String CACHE = "aile-filmler.json";
    private static final String PREFS = "aile_filmler";
    private static final String LAST = "last_try";
    private static final long EVERY = 12 * 60 * 60 * 1000L;

    static final class Film {
        final String ad, alt, id;
        final int dk;

        Film(String ad, String alt, String id, int dk) {
            this.ad = ad;
            this.alt = alt;
            this.id = id;
            this.dk = dk;
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

    private static List<Film> list;
    private static volatile boolean busy;

    private Filmler() {}

    static List<Film> all(Context c) {
        if (list == null) list = parse(readCache(c));
        return list;
    }

    static void refresh(Context c, Runnable changed) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = p.getLong(LAST, 0);
        boolean empty = all(c).isEmpty();
        if (busy || (now - last >= 0 && now - last < (empty ? 10 * 60 * 1000L : EVERY))) return;
        busy = true;
        p.edit().putLong(LAST, now).apply();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                String body = Uzak.fetch(URL);
                List<Film> fresh = parse(body);
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
                Keeper.note("filmler alinamadi: " + e);
            } finally {
                busy = false;
            }
        }, "aile-filmler").start();
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

    private static List<Film> parse(String body) {
        if (body == null) return Collections.emptyList();
        List<Film> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONObject(body).getJSONArray("filmler");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String id = o.optString("id", "");
                // A YouTube video id: 11 letters, digits, - and _. Anything else is not opened.
                if (!id.matches("[A-Za-z0-9_-]{11}")) continue;
                out.add(new Film(o.optString("ad", ""), o.optString("alt", ""), id, o.optInt("dk", 0)));
            }
        } catch (JSONException e) {
            Keeper.note("filmler bozuk: " + e.getMessage());
        }
        return out;
    }
}
