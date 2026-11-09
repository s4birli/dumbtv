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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * The TV guide: what each channel tile shows under its name. A GitHub Actions job
 * (.github/workflows/aile-rehber.yml) builds it from Türksat Kablo's public schedule every two
 * hours and publishes a small JSON on the "rehber" branch; the TV reads it once an hour and
 * keeps the last copy. Programmes carry their own start and end, so an old copy never shows a
 * wrong title: once it runs out, the tiles simply show none.
 */
final class Rehber {
    static final String URL = "https://raw.githubusercontent.com/s4birli/dumbtv/rehber/aile-rehber.json";
    private static final String CACHE = "aile-rehber.json";
    private static final String PREFS = "aile_rehber";
    private static final String LAST = "last_try";
    private static final long EVERY = 60 * 60 * 1000L;

    static final class Program {
        final long start, stop;
        final String title;

        Program(long start, long stop, String title) {
            this.start = start;
            this.stop = stop;
            this.title = title;
        }
    }

    private static Map<String, Program[]> guide;
    private static volatile boolean busy;

    private Rehber() {}

    /** "TV 8", "TV8" and "tv8" all match; the build script uses the same rule. */
    static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-zçğıöşü]", "");
    }

    /** The title on air now on {@code kanal}, or null when the guide has nothing for it. */
    static String now(Context c, String kanal, long nowMillis) {
        Program p = current(c, kanal, nowMillis);
        return p == null ? null : p.title;
    }

    /** The programme on air now, with its start and end (Unix seconds), or null. */
    static Program current(Context c, String kanal, long nowMillis) {
        if (guide == null) guide = parse(readCache(c));
        Program[] list = guide.get(key(kanal));
        if (list == null) return null;
        long now = nowMillis / 1000;
        for (Program p : list) {
            if (p.start <= now && now < p.stop) return p;
        }
        return null;
    }

    /** The first programme that starts after now, or null. */
    static Program next(Context c, String kanal, long nowMillis) {
        if (guide == null) guide = parse(readCache(c));
        Program[] list = guide.get(key(kanal));
        if (list == null) return null;
        long now = nowMillis / 1000;
        for (Program p : list) {
            if (p.start > now) return p;
        }
        return null;
    }

    /** Fetches a new guide in the background at most once an hour; {@code changed} on success. */
    static void refresh(Context c, String url, Runnable changed) {
        if (url == null || !url.startsWith("https://")) return;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = p.getLong(LAST, 0);
        if (busy || (now - last >= 0 && now - last < EVERY)) return;
        busy = true;
        p.edit().putLong(LAST, now).apply();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                String body = Uzak.fetch(url);
                Map<String, Program[]> fresh = parse(body);
                if (fresh.isEmpty()) return;
                File f = new File(app.getFilesDir(), CACHE);
                File tmp = new File(app.getFilesDir(), CACHE + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(f)) return;
                new Handler(Looper.getMainLooper()).post(() -> {
                    guide = fresh;
                    changed.run();
                });
            } catch (IOException | RuntimeException e) {
                Keeper.note("rehber alinamadi: " + e);
            } finally {
                busy = false;
            }
        }, "aile-rehber").start();
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

    private static Map<String, Program[]> parse(String body) {
        Map<String, Program[]> out = new HashMap<>();
        if (body == null) return out;
        try {
            JSONObject all = new JSONObject(body).getJSONObject("kanallar");
            for (Iterator<String> it = all.keys(); it.hasNext(); ) {
                String k = it.next();
                JSONArray arr = all.getJSONArray(k);
                Program[] list = new Program[arr.length()];
                int n = 0;
                for (int i = 0; i < arr.length(); i++) {
                    JSONArray p = arr.optJSONArray(i);
                    if (p == null || p.length() < 3) continue;
                    list[n++] = new Program(p.getLong(0), p.getLong(1), p.getString(2));
                }
                Program[] trimmed = new Program[n];
                System.arraycopy(list, 0, trimmed, 0, n);
                out.put(k, trimmed);
            }
        } catch (JSONException e) {
            Keeper.note("rehber bozuk: " + e.getMessage());
        }
        return out;
    }
}
