package tv.dumbtv.home;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The family home screen (fork of dumbtv home): live Turkish channels, a children's tile, the
 * house's cameras and Alexa, and the apps, over an Istanbul backdrop that follows the time of
 * day. Sizes are those of the approved 1920x1080 design, scaled to the screen.
 *
 * Home keeps everything that makes HOME work (Keeper, Guard, the DNS filter restart) and hands
 * the screen itself to this class.
 */
final class Aile {
    /** This fork always shows the family screen. */
    static final boolean ON = true;

    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface SEMI = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    private static final Locale TR = new Locale("tr", "TR");

    private final Home home;
    private final float unit;
    private final List<Kart> glassTiles = new ArrayList<>();
    private final List<TextView> pills = new ArrayList<>();
    private AileAyar ayar;
    private FrameLayout root;
    private Gokyuzu sky;
    private TextView clock, date;
    private Kart first;

    Aile(Home home) {
        this.home = home;
        DisplayMetrics m = home.getResources().getDisplayMetrics();
        int w = Math.max(m.widthPixels, m.heightPixels), h = Math.min(m.widthPixels, m.heightPixels);
        unit = Math.min(w / 1920f, h / 1080f);
    }

    private int px(float design) {
        return Math.round(design * unit);
    }

    View build() {
        root = new FrameLayout(home);
        root.setBackgroundColor(0xFF0E1014);
        sky = new Gokyuzu(home);
        root.addView(sky, new FrameLayout.LayoutParams(MATCH, MATCH));
        fill();
        return root;
    }

    /** (Re)builds the tiles, after a start or when an app is installed or removed. */
    void fill() {
        ayar = AileAyar.load(home);
        if (root.getChildCount() > 1) root.removeViewAt(1);
        glassTiles.clear();
        pills.clear();
        first = null;

        LinearLayout column = new LinearLayout(home);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(px(96), px(56), px(96), px(54));
        column.setClipChildren(false);
        column.setClipToPadding(false);
        root.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        column.addView(header(), new LinearLayout.LayoutParams(MATCH, px(80)));
        column.addView(channels(), spaced(44));
        column.addView(middleRow(), spaced(44));
        View apps = apps();
        if (apps != null) column.addView(apps, spaced(44));
        tick();
    }

    private LinearLayout.LayoutParams spaced(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = px(top);
        return lp;
    }

    // ---- header ---------------------------------------------------------------------------

    private View header() {
        LinearLayout h = row();
        h.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout when = row();
        when.setGravity(Gravity.BOTTOM);
        clock = text(64, 0xFFF5F5F7, SEMI);
        shadow(clock);
        date = text(30, 0xFFE4E7EE, SEMI);
        shadow(date);
        when.addView(clock);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(WRAP, WRAP);
        dl.leftMargin = px(28);
        dl.bottomMargin = px(6);
        when.addView(date, dl);
        h.addView(when, new LinearLayout.LayoutParams(0, WRAP, 1));

        String surf = launchable(ayar.surfshark);
        if (surf != null) h.addView(pill("Surfshark", Ikon.KALKAN, v -> openApp(surf)));
        TextView settings = pill("Ayarlar", Ikon.AYAR, v -> home.openSettings());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, px(56));
        sl.leftMargin = px(16);
        h.addView(settings, sl);
        return h;
    }

    private TextView pill(String label, int icon, View.OnClickListener click) {
        TextView p = text(26, 0xFFE4E7EE, SEMI);
        p.setText(label);
        p.setGravity(Gravity.CENTER_VERTICAL);
        p.setPadding(px(26), 0, px(26), 0);
        p.setFocusable(true);
        p.setClickable(true);
        p.setOnClickListener(click);
        Ikon i = new Ikon(icon);
        i.setBounds(0, 0, px(26), px(26));
        p.setCompoundDrawables(i, null, null, null);
        p.setCompoundDrawablePadding(px(12));
        p.setOnFocusChangeListener((v, has) -> paintPill(p, has));
        pills.add(p);
        paintPill(p, false);
        p.setLayoutParams(new LinearLayout.LayoutParams(WRAP, px(56)));
        return p;
    }

    private void paintPill(TextView p, boolean focused) {
        android.graphics.drawable.GradientDrawable bg = Ui.round(focused ? 0xFFFFFFFF
                : Gokyuzu.glass(sky.phase()), px(28));
        bg.setStroke(1, focused ? 0xFFFFFFFF : 0x29FFFFFF);
        p.setBackground(bg);
        int ink = focused ? 0xFF0E1014 : 0xFFE4E7EE;
        p.setTextColor(ink);
        Drawable d = p.getCompoundDrawables()[0];
        if (d != null) d.setTint(ink);
    }

    // ---- live channels -----------------------------------------------------------------------

    private View channels() {
        LinearLayout s = section("Canlı TV");
        LinearLayout tiles = row();
        tiles.setClipChildren(false);
        for (int i = 0; i < ayar.kanallar.size() && i < 4; i++) {
            AileAyar.Kanal k = ayar.kanallar.get(i);
            Kart t = new Kart(home, Kart.GLASS, 0, px(24), px(6));
            LinearLayout inner = new LinearLayout(home);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setPadding(px(32), px(28), px(32), px(28));
            LinearLayout b = Kart.badge(home, t, px(22), px(14), px(6), 0);
            inner.addView(b, new LinearLayout.LayoutParams(WRAP, WRAP));
            inner.addView(new View(home), new LinearLayout.LayoutParams(1, 0, 1));
            TextView name = text(46, 0xFFFFFFFF, BOLD);
            name.setText(k.ad);
            inner.addView(name);
            t.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
            t.setTitle(name);
            t.setContentDescription(k.ad + ", canlı");
            t.setOnClickListener(v -> openStream(k));
            glassTiles.add(t);
            if (first == null) first = t;
            tiles.addView(t, tileParams(408, 230, i));
        }
        s.addView(tiles, spaced(16));
        return s;
    }

    // ---- children + home row -------------------------------------------------------------------

    private View middleRow() {
        LinearLayout r = row();
        r.setClipChildren(false);

        LinearLayout kids = section("Çocuklar için");
        AileAyar.Link c = ayar.cocuk;
        Kart t = new Kart(home, Kart.SOLID, 0xFFF4C64E, px(24), px(6));
        LinearLayout inner = row();
        inner.setGravity(Gravity.CENTER_VERTICAL);
        inner.setPadding(px(32), px(26), px(32), px(26));
        LinearLayout words = new LinearLayout(home);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView name = text(46, 0xFF1A1A1A, BOLD);
        name.setText(c.ad);
        words.addView(name);
        if (c.alt != null && !c.alt.isEmpty()) {
            TextView alt = text(26, 0xFF3A3A3A, SEMI);
            alt.setText(c.alt);
            LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(WRAP, WRAP);
            al.topMargin = px(8);
            words.addView(alt, al);
        }
        inner.addView(words, new LinearLayout.LayoutParams(0, WRAP, 1));
        if (c.canli) inner.addView(Kart.badge(home, null, px(22), px(14), px(6), 0xFF1A1A1A));
        t.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.setContentDescription(c.ad);
        t.setOnClickListener(v -> openLink(c));
        kids.addView(t, sized(848, 180, 16, 0));
        r.addView(kids, new LinearLayout.LayoutParams(px(848), WRAP));

        List<View> homeTiles = new ArrayList<>();
        for (AileAyar.Uygulama u : ayar.ev) {
            String pkg = launchable(u.paketler);
            if (pkg == null) continue;
            int icon = u.ad.toLowerCase(TR).contains("alexa") ? Ikon.MIKROFON : Ikon.KAMERA;
            homeTiles.add(glassTile(u.ad, u.alt, icon, 38, v -> openApp(pkg)));
        }
        if (!homeTiles.isEmpty()) {
            LinearLayout ev = section("Ev");
            LinearLayout tiles = row();
            tiles.setClipChildren(false);
            for (int i = 0; i < homeTiles.size() && i < 2; i++) tiles.addView(homeTiles.get(i), tileParams(408, 180, i));
            ev.addView(tiles, spaced(16));
            LinearLayout.LayoutParams el = new LinearLayout.LayoutParams(WRAP, WRAP);
            el.leftMargin = px(32);
            r.addView(ev, el);
        }
        return r;
    }

    private Kart glassTile(String title, String subtitle, int icon, float titleSize, View.OnClickListener click) {
        Kart t = new Kart(home, Kart.GLASS, 0, px(24), px(6));
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(px(32), px(26), px(32), px(26));
        ImageView i = new ImageView(home);
        i.setImageDrawable(new Ikon(icon));
        inner.addView(i, new LinearLayout.LayoutParams(px(40), px(40)));
        inner.addView(new View(home), new LinearLayout.LayoutParams(1, 0, 1));
        TextView name = text(titleSize, 0xFFFFFFFF, BOLD);
        name.setText(title);
        inner.addView(name);
        t.setTitle(name);
        t.setIcon(i);
        if (subtitle != null) {
            TextView sub = text(24, 0xFFC9CEDA, SEMI);
            sub.setText(subtitle);
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, WRAP);
            sl.topMargin = px(4);
            inner.addView(sub, sl);
            t.setSub(sub);
        }
        t.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.setContentDescription(title);
        t.setOnClickListener(click);
        glassTiles.add(t);
        return t;
    }

    // ---- apps --------------------------------------------------------------------------------

    private View apps() {
        LinearLayout tiles = row();
        tiles.setClipChildren(false);
        int n = 0;
        for (AileAyar.Uygulama u : ayar.uygulamalar) {
            if (n == 5) break;
            String pkg = launchable(u.paketler);
            if (pkg == null) continue;
            Drawable banner = Kart.banner(home, pkg);
            Kart t;
            if (banner != null) {
                t = new Kart(home, Kart.BANNER, 0, px(24), px(6));
                ImageView img = new ImageView(home);
                img.setImageDrawable(banner);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                t.addView(img, new FrameLayout.LayoutParams(MATCH, MATCH));
            } else {
                t = new Kart(home, Kart.GLASS, 0, px(24), px(6));
                TextView name = text(32, 0xFFFFFFFF, SEMI);
                name.setText(u.ad);
                name.setGravity(Gravity.CENTER);
                t.addView(name, new FrameLayout.LayoutParams(MATCH, MATCH));
                t.setTitle(name);
                glassTiles.add(t);
            }
            t.setContentDescription(u.ad);
            t.setOnClickListener(v -> openApp(pkg));
            tiles.addView(t, tileParams(320, 180, n));
            n++;
        }
        if (n == 0) return null;
        LinearLayout s = section("Uygulamalar");
        s.addView(tiles, spaced(16));
        return s;
    }

    // ---- actions -----------------------------------------------------------------------------

    private void openStream(AileAyar.Kanal k) {
        Intent i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(k.url), "video/*");
        if (has(ayar.player)) i.setPackage(ayar.player);
        else {
            Toast.makeText(home, "Kanalları açmak için Kodi gerekli", Toast.LENGTH_LONG).show();
            return;
        }
        home.launch(i, null);
    }

    private void openLink(AileAyar.Link l) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(l.url));
        String pkg = installed(l.paketler);
        if (pkg != null) i.setPackage(pkg);
        home.launch(i, null);
    }

    private void openApp(String pkg) {
        PackageManager pm = home.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i == null) {
            Toast.makeText(home, "Açılamadı", Toast.LENGTH_SHORT).show();
            return;
        }
        home.launch(i, pkg);
    }

    // ---- lifecycle from Home -------------------------------------------------------------------

    /** Back on the screen, by HOME or from an app: always start on the first channel. */
    void resume() {
        tick();
        focusFirst();
    }

    void focusFirst() {
        if (first != null) first.requestFocus();
    }

    /** Every minute: clock, date and, when the hour of the day changes it, the backdrop. */
    void tick() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", TR).format(now));
        date.setText(new SimpleDateFormat("d MMMM EEEE", TR).format(now));
        int before = sky.phase();
        sky.setPhase(Gunes.phase(Calendar.getInstance()));
        int g = Gokyuzu.glass(sky.phase());
        for (Kart t : glassTiles) t.setGlass(g);
        if (sky.phase() != before) {
            for (TextView p : pills) paintPill(p, p.isFocused());
        }
    }

    // ---- small builders ----------------------------------------------------------------------

    private LinearLayout section(String title) {
        LinearLayout s = new LinearLayout(home);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setClipChildren(false);
        TextView t = text(34, 0xFFF5F5F7, SEMI);
        t.setText(title);
        shadow(t);
        s.addView(t, new LinearLayout.LayoutParams(WRAP, px(40)));
        return s;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(home);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        return r;
    }

    private LinearLayout.LayoutParams tileParams(float w, float h, int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(w), px(h));
        if (index > 0) lp.leftMargin = px(32);
        return lp;
    }

    private LinearLayout.LayoutParams sized(float w, float h, float top, float left) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(w), px(h));
        lp.topMargin = px(top);
        lp.leftMargin = px(left);
        return lp;
    }

    private TextView text(float designPx, int color, Typeface face) {
        TextView v = new TextView(home);
        v.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(designPx));
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        v.setSingleLine(true);
        return v;
    }

    private void shadow(TextView t) {
        t.setShadowLayer(px(14), 0, px(2), 0x73000000);
    }

    private boolean has(String pkg) {
        try {
            home.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** The first of {@code pkgs} that is installed and enabled, or null. */
    private String installed(String[] pkgs) {
        if (pkgs == null) return null;
        PackageManager pm = home.getPackageManager();
        for (String p : pkgs) {
            try {
                if (pm.getApplicationInfo(p, 0).enabled) return p;
            } catch (PackageManager.NameNotFoundException ignored) {
                // try the next one
            }
        }
        return null;
    }

    /**
     * The first of {@code pkgs} that can be opened from a home screen, or null. Fire OS's own
     * Alexa (com.amazon.vizzini) is installed but has no screen to open, so its tile stays away.
     */
    private String launchable(String[] pkgs) {
        if (pkgs == null) return null;
        PackageManager pm = home.getPackageManager();
        for (String p : pkgs) {
            if (installed(new String[] {p}) == null) continue;
            if (pm.getLeanbackLaunchIntentForPackage(p) != null
                    || pm.getLaunchIntentForPackage(p) != null) return p;
        }
        return null;
    }
}
