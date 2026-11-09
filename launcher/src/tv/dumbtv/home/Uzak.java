package tv.dumbtv.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Fetches the family screen's channel list from the internet, so a stream address that stops
 * working is fixed by editing one file in the git repository instead of touching the TV.
 *
 * Only the channels and the children's tile are taken from it (see {@link AileAyar#applyUzak}):
 * whoever can change that file can change which stream or YouTube link a tile opens, but not
 * which apps the screen launches. The last good copy is kept on the device, so the screen works
 * offline and a broken or missing file changes nothing.
 */
final class Uzak {
    /** The copy kept on the device, in the app's private files. */
    static final String CACHE = "aile-uzak.json";
    private static final String PREFS = "aile_uzak";
    private static final String LAST = "last_try";
    /** At most one fetch per this long: HOME, the minute tick and boots all ask. */
    private static final long EVERY = 10 * 60 * 1000L;
    private static final int MAX_BYTES = 64 * 1024;

    private static volatile boolean busy;

    private Uzak() {}

    /**
     * Fetches the file in the background if the last try is old enough, and runs {@code changed}
     * on the main thread when a new, valid version was saved.
     */
    static void refresh(Context c, String url, Runnable changed) {
        if (url == null || url.isEmpty() || !url.startsWith("https://")) return;
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        long last = p.getLong(LAST, 0);
        if (busy || (now - last >= 0 && now - last < EVERY)) return;
        busy = true;
        p.edit().putLong(LAST, now).apply();
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                String body = fetch(url);
                // Saved only when it parses: a half-written or broken file keeps the last copy.
                new JSONObject(body);
                File f = new File(app.getFilesDir(), CACHE);
                if (f.isFile() && body.equals(AileAyar.read(f))) {
                    Keeper.note("uzak ayar ayni");
                    return;
                }
                File tmp = new File(app.getFilesDir(), CACHE + ".tmp");
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(body.getBytes(StandardCharsets.UTF_8));
                }
                if (!tmp.renameTo(f)) return;
                Keeper.note("uzak ayar guncellendi");
                new Handler(Looper.getMainLooper()).post(changed);
            } catch (IOException | JSONException | RuntimeException e) {
                Keeper.note("uzak ayar alinamadi: " + e);
            } finally {
                busy = false;
            }
        }, "aile-uzak").start();
    }

    static String fetch(String url) throws IOException {
        HttpURLConnection http = (HttpURLConnection) new URL(url).openConnection();
        try {
            http.setConnectTimeout(8000);
            http.setReadTimeout(10000);
            http.setUseCaches(false);
            http.setInstanceFollowRedirects(true);
            int code = http.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code);
            try (InputStream in = http.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > MAX_BYTES) throw new IOException("too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            http.disconnect();
        }
    }
}
