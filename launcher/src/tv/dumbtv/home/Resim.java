package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pictures from the internet for the family screen: channel logos, the live stream's cover and
 * film covers. Each is fetched once, kept on the device (so the screen shows them offline and at
 * once on the next start) and decoded no larger than the view needs, which matters on a
 * 1.5 GB stick. A picture that cannot be had leaves the view as it was.
 */
final class Resim {
    /** Logos change rarely; a live stream's cover follows the stream. */
    static final long GUN = 24 * 60 * 60 * 1000L, AY = 30 * GUN;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final String AGENT = "dumbtv-aile/1.0 (https://github.com/s4birli/dumbtv)";

    private static final LruCache<String, Bitmap> MEMORY = new LruCache<String, Bitmap>(16 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap b) {
            return b.getByteCount();
        }
    };
    private static final ExecutorService WORK = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Resim() {}

    /**
     * Shows the first of {@code urls} that can be had in {@code view}, sized for {@code wPx} x
     * {@code hPx}; the later ones are fallbacks (a live stream's large cover is not always there).
     * {@code maxAge}: how old a kept copy may be before it is fetched again (the old copy still
     * shows meanwhile, and stays if the fetch fails).
     */
    static void load(ImageView view, String[] urls, int wPx, int hPx, long maxAge) {
        load(view, urls, wPx, hPx, maxAge, null);
    }

    /** Told on the main thread when a picture is shown, for a backdrop made from it. */
    interface Hazir {
        void hazir(Bitmap b);
    }

    static void load(ImageView view, String[] urls, int wPx, int hPx, long maxAge, Hazir done) {
        java.util.List<String> ok = new java.util.ArrayList<>();
        for (String u : urls) {
            if (u != null && (u.startsWith("https://") || u.startsWith("http://"))) ok.add(u);
        }
        if (ok.isEmpty()) return;
        String tag = android.text.TextUtils.join(" ", ok);
        view.setTag(tag);
        for (String url : ok) {
            Bitmap hit = MEMORY.get(url + "@" + wPx + "x" + hPx);
            if (hit != null) {
                view.setImageBitmap(hit);
                if (done != null) done.hazir(hit);
                return;
            }
        }
        Context app = view.getContext().getApplicationContext();
        WORK.execute(() -> {
            for (String url : ok) {
                String key = url + "@" + wPx + "x" + hPx;
                File f = file(app, url);
                boolean fresh = f.isFile() && System.currentTimeMillis() - f.lastModified() < maxAge;
                Bitmap b = f.isFile() ? decode(f, wPx, hPx) : null;
                if (b != null) show(view, tag, key, b, done);
                if (fresh && b != null) return;
                if (fetch(url, f)) {
                    Bitmap nb = decode(f, wPx, hPx);
                    if (nb != null) {
                        show(view, tag, key, nb, done);
                        return;
                    }
                }
                // A kept copy, even an old one, beats a fallback.
                if (b != null) return;
            }
        });
    }

    private static void show(ImageView view, String tag, String key, Bitmap b, Hazir done) {
        MEMORY.put(key, b);
        MAIN.post(() -> {
            if (!tag.equals(view.getTag())) return;
            view.setImageBitmap(b);
            if (done != null) done.hazir(b);
        });
    }

    /** A soft, blurred copy: shrunk to a few pixels and drawn large, for behind a picture. */
    static Bitmap bulanik(Bitmap b) {
        int w = 16, h = Math.max(1, Math.round(16f * b.getHeight() / Math.max(1, b.getWidth())));
        Bitmap tiny = Bitmap.createScaledBitmap(b, w, h, true);
        return Bitmap.createScaledBitmap(tiny, w * 4, h * 4, true);
    }

    private static File file(Context c, String url) {
        File dir = new File(c.getFilesDir(), "resim");
        if (!dir.isDirectory()) dir.mkdirs();
        return new File(dir, sha1(url));
    }

    private static boolean fetch(String url, File f) {
        HttpURLConnection http = null;
        try {
            http = (HttpURLConnection) new URL(url).openConnection();
            http.setConnectTimeout(8000);
            http.setReadTimeout(15000);
            http.setRequestProperty("User-Agent", AGENT);
            http.setInstanceFollowRedirects(true);
            if (http.getResponseCode() != 200) return false;
            String type = http.getContentType();
            if (type != null && !type.startsWith("image/")) return false;
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream in = http.getInputStream()) {
                byte[] buf = new byte[16384];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > MAX_BYTES) return false;
                }
            }
            File tmp = new File(f.getPath() + ".tmp");
            try (FileOutputStream fo = new FileOutputStream(tmp)) {
                fo.write(out.toByteArray());
            }
            return tmp.renameTo(f);
        } catch (IOException | RuntimeException e) {
            Keeper.note("resim alinamadi: " + url + " " + e);
            return false;
        } finally {
            if (http != null) http.disconnect();
        }
    }

    /** Decodes at the smallest power-of-two step that still covers the view. */
    private static Bitmap decode(File f, int wPx, int hPx) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getPath(), o);
            if (o.outWidth <= 0) return null;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= wPx && o.outHeight / (sample * 2) >= hPx) sample *= 2;
            BitmapFactory.Options d = new BitmapFactory.Options();
            d.inSampleSize = sample;
            return BitmapFactory.decodeFile(f.getPath(), d);
        } catch (RuntimeException | OutOfMemoryError e) {
            return null;
        }
    }

    private static String sha1(String s) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(s.getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x));
            return b.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }
}
