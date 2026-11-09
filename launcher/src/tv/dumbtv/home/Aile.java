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
import android.widget.ScrollView;
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
    /** The programme line under each channel name, in channel order, refreshed each minute. */
    private final List<TextView> onAir = new ArrayList<>();
    private final List<AileAyar.Kanal> onAirKanal = new ArrayList<>();
    private final List<TextView> pills = new ArrayList<>();
    /** Channel list rows, refreshed each minute with what is on. */
    private final List<Satir> satirlar = new ArrayList<>();
    /** Set by Keeper when HOME was pressed: the next resume goes back to the main screen. */
    static volatile boolean homePressed;
    static final int ANA = 0, KANALLAR = 1, FILMLER = 2;
    private int screen = ANA;
    /** The main-screen tile that opened the current list, focused again on Back. */
    private int fromTile = 0;
    private Kart liveTile, kidsTile, filmTile;
    private Detay detay;
    private AileAyar ayar;
    /** aile.json's time when the screen was built; a newer one is read on the next resume. */
    private long localStamp;
    private FrameLayout root;
    private Gokyuzu sky;
    private TextView clock, date;
    private View first;
    /** Shown only when no VPN is up: the DNS filter did not come back after Surfshark. */
    private TextView filterPill;
    private TextView surfPill;

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

    /** (Re)builds the current screen, after a start, a new channel list or an app change. */
    void fill() {
        localStamp = AileAyar.localStamp(home);
        ayar = AileAyar.load(home);
        if (root.getChildCount() > 1) root.removeViewAt(1);
        glassTiles.clear();
        onAir.clear();
        onAirKanal.clear();
        pills.clear();
        satirlar.clear();
        first = null;
        filterPill = null;
        surfPill = null;
        date = null;
        detay = null;
        liveTile = kidsTile = filmTile = null;

        View content = screen == KANALLAR ? kanallarScreen()
                : screen == FILMLER ? filmlerScreen() : anaScreen();
        root.addView(content, new FrameLayout.LayoutParams(MATCH, MATCH));
        tick();
    }

    /** Switches screen and puts the focus where the remote expects it. */
    private void show(int s) {
        screen = s;
        fill();
        root.post(this::focusFirst);
    }

    /** Back on a list goes to the main screen, onto the tile that opened it. */
    boolean back() {
        if (screen == ANA) return false;
        int from = fromTile;
        fromTile = 0;
        screen = ANA;
        fill();
        Kart t = from == 2 ? filmTile : liveTile;
        root.post(() -> {
            if (t != null) t.requestFocus();
        });
        return true;
    }

    /** HOME always lands on the main screen, on Canlı TV. */
    void goHome() {
        fromTile = 0;
        if (screen != ANA) show(ANA);
        else focusFirst();
    }

    int screen() {
        return screen;
    }

    private LinearLayout.LayoutParams spaced(float top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = px(top);
        return lp;
    }

    private LinearLayout column(float bottom) {
        LinearLayout column = new LinearLayout(home);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(px(96), px(56), px(96), px(bottom));
        column.setClipChildren(false);
        column.setClipToPadding(false);
        return column;
    }

    // ---- main screen -------------------------------------------------------------------------

    private View anaScreen() {
        LinearLayout column = column(54);
        column.addView(header(), new LinearLayout.LayoutParams(MATCH, px(80)));

        LinearLayout hero = row();
        hero.addView(liveTile(), new LinearLayout.LayoutParams(0, MATCH, 1.55f));
        LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(0, MATCH, 1f);
        kl.leftMargin = px(32);
        hero.addView(kidsTile(), kl);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(0, MATCH, 1f);
        fl.leftMargin = px(32);
        hero.addView(filmTile(), fl);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(MATCH, px(560));
        hl.topMargin = px(48);
        column.addView(hero, hl);

        View apps = apps();
        if (apps != null) column.addView(apps, spaced(44));
        return column;
    }

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

        String filter = launchable(new String[] {"dnsfilter.android"});
        if (filter != null) {
            filterPill = pill("Filtre kapalı", Ikon.KALKAN, v -> openApp(filter));
            filterPill.setVisibility(View.GONE);
            LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(WRAP, px(56));
            fl.rightMargin = px(16);
            h.addView(filterPill, fl);
        }
        String surf = launchable(ayar.surfshark);
        if (surf != null) {
            surfPill = pill("Surfshark", Ikon.KALKAN, v -> openSurfshark(surf));
            h.addView(surfPill);
        }
        // Kodi is used rarely now that the channels open it themselves.
        String kodi = launchable(new String[] {ayar.player});
        if (kodi != null) {
            TextView k = pill("Kodi", Ikon.OYNAT, v -> openApp(kodi));
            LinearLayout.LayoutParams kl = new LinearLayout.LayoutParams(WRAP, px(56));
            kl.leftMargin = px(16);
            h.addView(k, kl);
        }
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

    /** "Canlı TV": what is on now on the first four channels; opens the channel list. */
    private View liveTile() {
        Kart t = new Kart(home, Kart.GLASS, 0, px(32), px(6));
        t.setFocusScale(1.04f);
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(px(48), px(44), px(48), px(44));
        inner.addView(Kart.badge(home, t, px(22), px(14), px(6), 0), new LinearLayout.LayoutParams(WRAP, WRAP));
        TextView title = text(72, 0xFFFFFFFF, BOLD);
        title.setText("Canlı TV");
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(WRAP, WRAP);
        tl.topMargin = px(18);
        inner.addView(title, tl);
        t.setTitle(title);
        TextView sub = text(28, 0xFFC9CEDA, SEMI);
        sub.setText(subtitle());
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, WRAP);
        sl.topMargin = px(18);
        inner.addView(sub, sl);
        t.setSub(sub);
        inner.addView(new View(home), new LinearLayout.LayoutParams(1, 0, 1));
        int n = 0;
        for (AileAyar.Kanal k : visibleChannels()) {
            if (n == 4) break;
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.addView(logoChip(k, 48, 12, t), new LinearLayout.LayoutParams(px(48), px(48)));
            TextView name = text(28, 0xFFFFFFFF, BOLD);
            name.setText(k.ad);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(130), WRAP);
            nl.leftMargin = px(18);
            line.addView(name, nl);
            t.setTitle(name);
            TextView prog = text(28, 0xFFC9CEDA, SEMI);
            prog.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.addView(prog, new LinearLayout.LayoutParams(0, WRAP, 1));
            t.setSub(prog);
            onAir.add(prog);
            onAirKanal.add(k);
            LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(MATCH, WRAP);
            if (n > 0) ll.topMargin = px(12);
            inner.addView(line, ll);
            n++;
        }
        t.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.setContentDescription("Canlı TV");
        t.setOnClickListener(v -> {
            fromTile = 0;
            show(KANALLAR);
        });
        glassTiles.add(t);
        liveTile = t;
        first = t;
        return t;
    }

    private String subtitle() {
        boolean uk = false, film = false;
        for (AileAyar.Kanal k : visibleChannels()) {
            uk |= k.grup.startsWith("İngiliz");
            film |= k.grup.startsWith("Film");
        }
        return "Türk" + (uk ? " ve İngiliz" : "") + " kanalları" + (film ? ", film kanalları" : "");
    }

    /**
     * Ms Rachel: a picture filling the whole tile (the live stream's own YouTube cover, or one the
     * family chose with "kapak"), with the name over its lower part unless "yazi" is false.
     */
    private View kidsTile() {
        AileAyar.Link c = ayar.cocuk;
        Kart t = new Kart(home, Kart.SOLID, 0xFFF4C64E, px(32), px(6));
        t.setFocusScale(1.04f);
        // The whole picture, never cropped; the space around it is a blurred, dimmed copy of it.
        ImageView back = new ImageView(home);
        back.setScaleType(ImageView.ScaleType.CENTER_CROP);
        t.addView(back, new FrameLayout.LayoutParams(MATCH, MATCH));
        View dim = new View(home);
        dim.setBackgroundColor(0x59000000);
        dim.setVisibility(View.GONE);
        t.addView(dim, new FrameLayout.LayoutParams(MATCH, MATCH));
        ImageView img = new ImageView(home);
        img.setScaleType(ImageView.ScaleType.FIT_CENTER);
        t.addView(img, new FrameLayout.LayoutParams(MATCH, MATCH));
        String id = AileAyar.youtubeId(c.url);
        // The family's picture first; the stream's own covers if it cannot be had.
        List<String> urls = new ArrayList<>();
        if (c.kapak != null) urls.add(c.kapak);
        if (id != null) {
            urls.add("https://i.ytimg.com/vi/" + id + "/maxresdefault_live.jpg");
            urls.add("https://i.ytimg.com/vi/" + id + "/maxresdefault.jpg");
            urls.add("https://i.ytimg.com/vi/" + id + "/hqdefault_live.jpg");
        }
        Resim.load(img, urls.toArray(new String[0]), px(470), px(560), 6 * 60 * 60 * 1000L, b -> {
            back.setImageBitmap(Resim.bulanik(b));
            dim.setVisibility(View.VISIBLE);
        });

        if (c.yazi) {
            // A shade from the bottom, so white text reads on any picture.
            View shade = new View(home);
            shade.setBackground(new android.graphics.drawable.GradientDrawable(
                    android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                    new int[] {0xD9000000, 0x80000000, 0x00000000}));
            t.addView(shade, new FrameLayout.LayoutParams(MATCH, px(300), Gravity.BOTTOM));
            LinearLayout words = new LinearLayout(home);
            words.setOrientation(LinearLayout.VERTICAL);
            words.setPadding(px(40), 0, px(40), px(40));
            TextView forKids = text(24, 0xFFF4C64E, BOLD);
            forKids.setText("Çocuklar için");
            words.addView(forKids);
            TextView name = text(60, 0xFFFFFFFF, BOLD);
            name.setText(c.ad);
            shadow(name);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(WRAP, WRAP);
            nl.topMargin = px(10);
            words.addView(name, nl);
            if (c.alt != null && !c.alt.isEmpty()) {
                TextView alt = text(26, 0xFFE4E7EE, SEMI);
                alt.setText(c.alt);
                LinearLayout.LayoutParams al = new LinearLayout.LayoutParams(WRAP, WRAP);
                al.topMargin = px(10);
                words.addView(alt, al);
            }
            t.addView(words, new FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM));
        }
        if (c.canli) {
            FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP | Gravity.START);
            bl.leftMargin = px(28);
            bl.topMargin = px(28);
            t.addView(Kart.badge(home, null, px(22), px(14), px(6), 0xCC1A1A1A), bl);
        }
        t.setContentDescription(c.ad + (c.canli ? ", canlı" : ""));
        t.setOnClickListener(v -> openLink(c));
        kidsTile = t;
        return t;
    }

    /** "Eski Türk Filmleri": a film strip in Yeşilçam colours; opens the film list. */
    private View filmTile() {
        Kart t = new Kart(home, Kart.SOLID, 0xFF3B1D17, px(32), px(6));
        t.setFocusScale(1.04f);
        LinearLayout inner = new LinearLayout(home);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.addView(new Serit(home, px(26), px(16), px(50)), new LinearLayout.LayoutParams(MATCH, px(40)));
        LinearLayout words = new LinearLayout(home);
        words.setOrientation(LinearLayout.VERTICAL);
        words.setGravity(Gravity.CENTER_VERTICAL);
        words.setPadding(px(44), 0, px(44), 0);
        TextView y = text(40, 0xFFE9B872, Typeface.create("serif", Typeface.BOLD_ITALIC));
        y.setText("Yeşilçam");
        words.addView(y);
        TextView name = text(56, 0xFFF7EBDD, BOLD);
        name.setSingleLine(false);
        name.setMaxLines(2);
        name.setText("Eski Türk Filmleri");
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(WRAP, WRAP);
        nl.topMargin = px(14);
        words.addView(name, nl);
        TextView sub = text(26, 0xFFD9C4B0, SEMI);
        sub.setSingleLine(false);
        sub.setMaxLines(3);
        sub.setLineSpacing(px(8), 1f);
        int count = Filmler.all(home).size();
        sub.setText("Kemal Sunal, Şener Şen, Hababam Sınıfı ve dahası."
                + (count > 0 ? " " + count + " film, her ay yenilenir." : " Her ay yenilenir."));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(WRAP, WRAP);
        sl.topMargin = px(14);
        words.addView(sub, sl);
        inner.addView(words, new LinearLayout.LayoutParams(MATCH, 0, 1));
        inner.addView(new Serit(home, px(26), px(16), px(50)), new LinearLayout.LayoutParams(MATCH, px(40)));
        t.addView(inner, new FrameLayout.LayoutParams(MATCH, MATCH));
        t.setContentDescription("Eski Türk Filmleri");
        t.setOnClickListener(v -> {
            fromTile = 2;
            show(FILMLER);
        });
        filmTile = t;
        return t;
    }

    // ---- apps --------------------------------------------------------------------------------

    private View apps() {
        LinearLayout tiles = row();
        tiles.setClipChildren(false);
        List<AileAyar.Uygulama> shown = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        for (AileAyar.Uygulama u : ayar.uygulamalar) {
            if (shown.size() == 6) break;
            String pkg = launchable(u.paketler);
            if (pkg == null) continue;
            shown.add(u);
            pkgs.add(pkg);
        }
        // Five 320 x 180 banners fill the 1728 wide column; a sixth shrinks them all to fit.
        float gap = shown.size() > 5 ? 24 : 32;
        float w = shown.size() > 5 ? (1728 - gap * (shown.size() - 1)) / shown.size() : 320;
        float h = w * 9 / 16;
        int n = 0;
        for (int k = 0; k < shown.size(); k++) {
            AileAyar.Uygulama u = shown.get(k);
            String pkg = pkgs.get(k);
            Drawable banner = u.logo != null ? Kart.banner(home, u.logo) : null;
            if (banner == null) banner = Kart.banner(home, pkg);
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
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(px(w), px(h));
            if (n > 0) lp.leftMargin = px(gap);
            tiles.addView(t, lp);
            n++;
        }
        if (n == 0) return null;
        LinearLayout s = section("Uygulamalar");
        s.addView(tiles, spaced(16));
        return s;
    }

    // ---- channel list ------------------------------------------------------------------------

    /** Channels that can be opened here: an app channel needs its app installed. */
    private List<AileAyar.Kanal> visibleChannels() {
        List<AileAyar.Kanal> out = new ArrayList<>();
        for (AileAyar.Kanal k : ayar.kanallar) {
            if (k.app() && launchable(k.paket) == null) continue;
            out.add(k);
        }
        return out;
    }

    private View kanallarScreen() {
        LinearLayout column = column(0);
        column.addView(listHeader("Canlı TV", null), new LinearLayout.LayoutParams(MATCH, px(80)));

        LinearLayout body = row();
        ScrollView scroll = new ScrollView(home);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(px(12), px(8), px(28), px(60));
        LinearLayout list = new LinearLayout(home);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        scroll.addView(list, new FrameLayout.LayoutParams(MATCH, WRAP));

        String group = null;
        for (AileAyar.Kanal k : visibleChannels()) {
            if (!k.grup.equals(group)) {
                group = k.grup;
                TextView g = text(28, 0xFFD7DBE6, SEMI);
                g.setText(group);
                shadow(g);
                LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(WRAP, px(36));
                gl.topMargin = px(list.getChildCount() == 0 ? 4 : 28);
                gl.leftMargin = px(8);
                gl.bottomMargin = px(4);
                list.addView(g, gl);
            }
            Satir s = new Satir(k);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, px(92));
            rl.topMargin = px(12);
            list.addView(s.kart, rl);
            satirlar.add(s);
            if (first == null) first = s.kart;
        }
        body.addView(scroll, new LinearLayout.LayoutParams(px(1068), MATCH));

        detay = new Detay();
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(px(648), px(860));
        dl.leftMargin = px(12);
        dl.topMargin = px(20);
        body.addView(detay.panel, dl);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(MATCH, 0, 1);
        bl.topMargin = px(28);
        column.addView(body, bl);
        if (!satirlar.isEmpty()) detay.show(satirlar.get(0).k);
        return column;
    }

    private View listHeader(String title, String right) {
        LinearLayout h = row();
        h.setGravity(Gravity.CENTER_VERTICAL);
        ImageView back = new ImageView(home);
        Ikon i = new Ikon(Ikon.GERI);
        back.setImageDrawable(i);
        h.addView(back, new LinearLayout.LayoutParams(px(44), px(44)));
        TextView t = text(60, 0xFFF5F5F7, BOLD);
        t.setText(title);
        shadow(t);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1);
        tl.leftMargin = px(20);
        h.addView(t, tl);
        clock = text(48, 0xFFF5F5F7, SEMI);
        shadow(clock);
        h.addView(clock);
        return h;
    }

    /** A channel: a stream in Kodi or SmartTube, or the broadcaster's own app. */
    private void openKanal(AileAyar.Kanal k) {
        if (!k.app()) {
            openStream(k);
            return;
        }
        String pkg = launchable(k.paket);
        if (pkg == null) {
            Toast.makeText(home, k.uygulama + " yüklü değil", Toast.LENGTH_LONG).show();
            return;
        }
        // The live page's address opens the channel itself where the app knows it.
        if (k.url != null && !k.url.isEmpty()) {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(k.url)).setPackage(pkg);
            if (home.getPackageManager().resolveActivity(i, 0) != null) {
                home.launch(i, pkg);
                return;
            }
        }
        openApp(pkg);
    }

    /** One channel row: logo, name, what is on, its hours and how far it has run. */
    private final class Satir {
        final AileAyar.Kanal k;
        final Kart kart;
        final TextView prog, time;
        final Cizgi bar;

        Satir(AileAyar.Kanal k) {
            this.k = k;
            kart = new Kart(home, Kart.GLASS, 0, px(20), px(6));
            kart.setFocusScale(1.02f);
                        FrameLayout frame = new FrameLayout(home);
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(px(16), 0, px(32), 0);
            line.addView(logoChip(k, 60, 14, kart), new LinearLayout.LayoutParams(px(60), px(60)));
            TextView name = text(34, 0xFFFFFFFF, BOLD);
            name.setText(k.ad);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(px(300), WRAP);
            nl.leftMargin = px(24);
            line.addView(name, nl);
            kart.setTitle(name);
            prog = text(28, 0xFFC9CEDA, SEMI);
            prog.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(0, WRAP, 1);
            pl.leftMargin = px(16);
            line.addView(prog, pl);
            kart.setSub(prog);
            time = text(24, 0xFFC9CEDA, SEMI);
            LinearLayout.LayoutParams ml = new LinearLayout.LayoutParams(WRAP, WRAP);
            ml.leftMargin = px(20);
            line.addView(time, ml);
            kart.setSub(time);
            frame.addView(line, new FrameLayout.LayoutParams(MATCH, MATCH));
            bar = new Cizgi(home);
            FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(MATCH, px(4), Gravity.BOTTOM);
            cl.leftMargin = px(100);
            cl.rightMargin = px(32);
            cl.bottomMargin = px(12);
            frame.addView(bar, cl);
            kart.addView(frame, new FrameLayout.LayoutParams(MATCH, MATCH));
            kart.setContentDescription(k.ad);
            kart.setOnClickListener(v -> openKanal(k));
            // Rows grow from their left edge, so the list keeps a straight left side.
            kart.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                v.setPivotX(0);
                v.setPivotY((b - t) / 2f);
            });
            kart.setOdak(has -> {
                bar.setDark(has);
                if (has && detay != null) detay.show(k);
            });
            glassTiles.add(kart);
            refresh(System.currentTimeMillis());
        }

        void refresh(long now) {
            Rehber.Program p = Rehber.current(home, key(k), now);
            if (p != null) {
                prog.setText(p.title);
                time.setText(hhmm(p.start) + "–" + hhmm(p.stop));
                bar.setFraction(done(p, now));
                bar.setVisibility(View.VISIBLE);
            } else {
                prog.setText(idle(k));
                time.setText("");
                bar.setVisibility(View.GONE);
            }
        }
    }

    /** The big card on the right: the focused channel, now and next. */
    private final class Detay {
        final LinearLayout panel;
        final ImageView logo;
        final android.graphics.drawable.GradientDrawable logoBg, bg;
        final TextView name, now, time, nextLabel, next, nextTime, hint;
        final Cizgi bar;
        AileAyar.Kanal shown;

        Detay() {
            panel = new LinearLayout(home);
            panel.setOrientation(LinearLayout.VERTICAL);
            panel.setPadding(px(48), px(48), px(48), px(48));
            bg = Ui.round(Gokyuzu.glass(sky.phase()), px(32));
            bg.setStroke(1, 0x29FFFFFF);
            panel.setBackground(bg);
            LinearLayout top = row();
            logoBg = Ui.round(0xEBFFFFFF, px(24));
            FrameLayout chip = new FrameLayout(home);
            chip.setBackground(logoBg);
            logo = new ImageView(home);
            logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(MATCH, MATCH);
            il.setMargins(px(14), px(14), px(14), px(14));
            chip.addView(logo, il);
            top.addView(chip, new LinearLayout.LayoutParams(px(120), px(120)));
            top.addView(new View(home), new LinearLayout.LayoutParams(0, 1, 1));
            top.addView(Kart.badge(home, null, px(22), px(14), px(6), 0x59000000));
            panel.addView(top);
            name = text(72, 0xFFFFFFFF, BOLD);
            name.setSingleLine(false);
            name.setMaxLines(2);
            panel.addView(name, spaced(20));
            panel.addView(label("ŞİMDİ"), spaced(36));
            now = text(48, 0xFFFFFFFF, BOLD);
            now.setSingleLine(false);
            now.setMaxLines(2);
            now.setEllipsize(android.text.TextUtils.TruncateAt.END);
            panel.addView(now, spaced(14));
            time = text(30, 0xFFD7DBE6, SEMI);
            panel.addView(time, spaced(14));
            bar = new Cizgi(home);
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(MATCH, px(10));
            bl.topMargin = px(14);
            panel.addView(bar, bl);
            View rule = new View(home);
            rule.setBackgroundColor(0x29FFFFFF);
            LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(MATCH, 1);
            rl.topMargin = px(36);
            panel.addView(rule, rl);
            nextLabel = label("SONRA");
            panel.addView(nextLabel, spaced(36));
            next = text(36, 0xFFFFFFFF, SEMI);
            next.setSingleLine(false);
            next.setMaxLines(2);
            next.setEllipsize(android.text.TextUtils.TruncateAt.END);
            panel.addView(next, spaced(10));
            nextTime = text(28, 0xFFD7DBE6, SEMI);
            panel.addView(nextTime, spaced(10));
            panel.addView(new View(home), new LinearLayout.LayoutParams(1, 0, 1));
            hint = text(26, 0xFFD7DBE6, SEMI);
            Ikon play = new Ikon(Ikon.OYNAT);
            play.setTint(0xFFD7DBE6);
            play.setBounds(0, 0, px(30), px(30));
            hint.setCompoundDrawables(play, null, null, null);
            hint.setCompoundDrawablePadding(px(14));
            hint.setGravity(Gravity.CENTER_VERTICAL);
            panel.addView(hint);
        }

        void setGlass(int color) {
            bg.setColor(color);
        }

        private TextView label(String s) {
            TextView t = text(24, 0xFFC9CEDA, BOLD);
            t.setLetterSpacing(0.08f);
            t.setText(s);
            return t;
        }

        void show(AileAyar.Kanal k) {
            shown = k;
            name.setText(k.ad);
            logo.setImageDrawable(null);
            logoBg.setColor(k.logoKoyu ? 0xFF14171F : 0xEBFFFFFF);
            if (k.logo != null) Resim.load(logo, new String[] {k.logo}, px(92), px(92), Resim.AY);
            hint.setText(k.app() ? "Tamam'a bas: " + k.uygulama + " uygulamasında açılır"
                    : AileAyar.isYoutube(k.url) ? "Tamam'a bas: YouTube'da reklamsız açılır"
                    : "Tamam'a bas: Kodi'de açılır");
            refresh(System.currentTimeMillis());
        }

        void refresh(long nowMs) {
            if (shown == null) return;
            Rehber.Program p = Rehber.current(home, key(shown), nowMs);
            Rehber.Program n = Rehber.next(home, key(shown), nowMs);
            if (p != null) {
                now.setText(p.title);
                time.setText(hhmm(p.start) + " – " + hhmm(p.stop));
                time.setVisibility(View.VISIBLE);
                bar.setFraction(done(p, nowMs));
                bar.setVisibility(View.VISIBLE);
            } else {
                now.setText(idle(shown));
                time.setVisibility(View.GONE);
                bar.setVisibility(View.GONE);
            }
            boolean hasNext = n != null;
            nextLabel.setVisibility(hasNext ? View.VISIBLE : View.GONE);
            next.setVisibility(hasNext ? View.VISIBLE : View.GONE);
            nextTime.setVisibility(hasNext ? View.VISIBLE : View.GONE);
            if (hasNext) {
                next.setText(n.title);
                nextTime.setText(hhmm(n.start));
            }
        }
    }

    /** The light (or, for a white logo, dark) square a channel logo sits on. */
    private View logoChip(AileAyar.Kanal k, float size, float radius, Kart owner) {
        FrameLayout chip = new FrameLayout(home);
        android.graphics.drawable.GradientDrawable bg = Ui.round(k.logoKoyu ? 0xFF14171F : 0xEBFFFFFF, px(radius));
        chip.setBackground(bg);
        if (owner != null) owner.addChip(bg);
        if (k.logo != null) {
            ImageView img = new ImageView(home);
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            FrameLayout.LayoutParams il = new FrameLayout.LayoutParams(MATCH, MATCH);
            int m = px(size * 0.12f);
            il.setMargins(m, m, m, m);
            chip.addView(img, il);
            Resim.load(img, new String[] {k.logo}, px(size), px(size), Resim.AY);
        } else {
            TextView t = text(size * 0.3f, k.logoKoyu ? 0xFFFFFFFF : 0xFF3A3F4A, BOLD);
            t.setText(initials(k.ad));
            t.setGravity(Gravity.CENTER);
            chip.addView(t, new FrameLayout.LayoutParams(MATCH, MATCH));
        }
        return chip;
    }

    private static String initials(String name) {
        StringBuilder b = new StringBuilder();
        for (String w : name.split("\\s+")) {
            if (!w.isEmpty() && b.length() < 2) b.append(Character.toUpperCase(w.charAt(0)));
        }
        return b.toString();
    }

    private static String key(AileAyar.Kanal k) {
        return k.rehber != null ? k.rehber : k.ad;
    }

    /** What a row says when the guide has nothing for the channel. */
    private static String idle(AileAyar.Kanal k) {
        if (k.app()) return k.uygulama + " uygulamasında açılır";
        if (k.grup.startsWith("Film")) return "24 saat film";
        return "Canlı yayın";
    }

    /** How much of the programme has run, 0 to 1. */
    private static float done(Rehber.Program p, long nowMs) {
        return (float) ((nowMs / 1000.0 - p.start) / Math.max(1, p.stop - p.start));
    }

    private static String hhmm(long unixSeconds) {
        return new SimpleDateFormat("HH:mm", TR).format(new Date(unixSeconds * 1000));
    }

    // ---- films -------------------------------------------------------------------------------

    private static final int[] TONES = {0xFF4A2A22, 0xFF3D2A3A, 0xFF2E3440, 0xFF4A3A22, 0xFF3A2A1E, 0xFF2F3A2E};

    private View filmlerScreen() {
        FrameLayout frame = new FrameLayout(home);
        frame.setBackgroundColor(0xFF1E0F0C);
        LinearLayout column = column(0);
        frame.addView(column, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout h = row();
        h.setGravity(Gravity.BOTTOM);
        ImageView back = new ImageView(home);
        Ikon i = new Ikon(Ikon.GERI);
        i.setTint(0xFFF7EBDD);
        back.setImageDrawable(i);
        LinearLayout.LayoutParams bkl = new LinearLayout.LayoutParams(px(44), px(44));
        bkl.bottomMargin = px(8);
        h.addView(back, bkl);
        LinearLayout titles = new LinearLayout(home);
        titles.setOrientation(LinearLayout.VERTICAL);
        TextView y = text(34, 0xFFE9B872, Typeface.create("serif", Typeface.BOLD_ITALIC));
        y.setText("Yeşilçam");
        titles.addView(y);
        TextView t = text(60, 0xFFF7EBDD, BOLD);
        t.setText("Eski Türk Filmleri");
        titles.addView(t, spaced(8));
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, WRAP, 1);
        tl.leftMargin = px(20);
        h.addView(titles, tl);
        List<Filmler.Film> films = Filmler.all(home);
        TextView right = text(26, 0xFFD9C4B0, SEMI);
        right.setSingleLine(false);
        right.setGravity(Gravity.END);
        right.setLineSpacing(px(8), 1f);
        right.setText("Arzu Film, Erler Film ve Sistem Film'in\nresmi YouTube kanallarından"
                + (films.isEmpty() ? "" : " · " + films.size() + " film"));
        h.addView(right);
        column.addView(h, new LinearLayout.LayoutParams(MATCH, px(108)));
        clock = null;

        if (films.isEmpty()) {
            TextView wait = text(36, 0xFFD9C4B0, SEMI);
            wait.setText("Film listesi yükleniyor…");
            column.addView(wait, spaced(80));
            return frame;
        }
        ScrollView scroll = new ScrollView(home);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(px(12), px(24), px(12), px(80));
        LinearLayout grid = new LinearLayout(home);
        grid.setOrientation(LinearLayout.VERTICAL);
        grid.setClipChildren(false);
        scroll.addView(grid, new FrameLayout.LayoutParams(MATCH, WRAP));
        float cardW = (1704 - 3 * 32) / 4f;
        LinearLayout line = null;
        for (int n = 0; n < films.size(); n++) {
            if (n % 4 == 0) {
                line = row();
                LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(MATCH, WRAP);
                if (n > 0) ll.topMargin = px(40);
                grid.addView(line, ll);
            }
            View card = filmCard(films.get(n), n, cardW);
            LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(px(cardW), WRAP);
            if (n % 4 > 0) cl.leftMargin = px(32);
            line.addView(card, cl);
            if (first == null) first = card;
        }
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(MATCH, 0, 1);
        sl.topMargin = px(16);
        column.addView(scroll, sl);
        return frame;
    }

    private View filmCard(Filmler.Film f, int index, float w) {
        LinearLayout card = new LinearLayout(home);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipChildren(false);
        FrameLayout pic = new FrameLayout(home);
        android.graphics.drawable.GradientDrawable bg = Ui.round(TONES[index % TONES.length], px(20));
        pic.setBackground(bg);
        pic.setClipToOutline(true);
        ImageView img = new ImageView(home);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        pic.addView(img, new FrameLayout.LayoutParams(MATCH, MATCH));
        Resim.load(img, new String[] {f.kapak()}, px(w), px(228), Resim.AY);
        TextView len = text(20, 0xFFFFFFFF, SEMI);
        len.setText(f.sure());
        len.setPadding(px(10), px(4), px(10), px(4));
        len.setBackground(Ui.round(0xB3000000, px(8)));
        FrameLayout.LayoutParams lenl = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.END);
        lenl.setMargins(0, 0, px(14), px(14));
        pic.addView(len, lenl);
        android.graphics.drawable.GradientDrawable ring = new android.graphics.drawable.GradientDrawable();
        ring.setCornerRadius(px(20));
        ring.setStroke(px(6), 0xFFFFFFFF);
        card.addView(pic, new LinearLayout.LayoutParams(MATCH, px(228)));
        TextView name = text(30, 0xFFF7EBDD, BOLD);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setText(f.ad);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(MATCH, WRAP);
        nl.topMargin = px(16);
        nl.leftMargin = px(4);
        card.addView(name, nl);
        TextView cast = text(22, 0xFFD9C4B0, SEMI);
        cast.setEllipsize(android.text.TextUtils.TruncateAt.END);
        cast.setText(f.alt);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(MATCH, WRAP);
        cl.topMargin = px(6);
        cl.leftMargin = px(4);
        card.addView(cast, cl);
        card.setContentDescription(f.ad + ", " + f.sure());
        card.setOnFocusChangeListener((v, has) -> {
            pic.setForeground(has ? ring : null);
            card.setPivotX(px(w) / 2f);
            card.setPivotY(0);
            card.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(150).start();
            name.setTextColor(has ? 0xFFFFFFFF : 0xFFF7EBDD);
        });
        card.setOnClickListener(v -> openLink(new AileAyar.Link(f.ad, null, f.link(), AileAyar.YOUTUBE, false)));
        return card;
    }

    /** A thin progress line: how much of the programme has run. */
    static final class Cizgi extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private float fraction;
        private boolean dark;

        Cizgi(android.content.Context c) {
            super(c);
        }

        void setFraction(float f) {
            fraction = Math.max(0, Math.min(1, f));
            invalidate();
        }

        void setDark(boolean d) {
            dark = d;
            invalidate();
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            float h = getHeight(), rad = h / 2;
            r.set(0, 0, getWidth(), h);
            paint.setColor(dark ? 0x260E1014 : 0x29FFFFFF);
            c.drawRoundRect(r, rad, rad, paint);
            r.set(0, 0, getWidth() * fraction, h);
            paint.setColor(dark ? 0xFF0E1014 : 0xFFFFFFFF);
            c.drawRoundRect(r, rad, rad, paint);
        }
    }

    /** The perforated edge of a strip of film. */
    static final class Serit extends View {
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF r = new android.graphics.RectF();
        private final int holeW, holeH, step;

        Serit(android.content.Context c, int holeW, int holeH, int step) {
            super(c);
            this.holeW = holeW;
            this.holeH = holeH;
            this.step = step;
        }

        @Override
        protected void onDraw(android.graphics.Canvas c) {
            c.drawColor(0xFF24110D);
            paint.setColor(0xFF5A3A2E);
            float top = (getHeight() - holeH) / 2f;
            for (int x = step / 3; x + holeW < getWidth(); x += step) {
                r.set(x, top, x + holeW, top + holeH);
                c.drawRoundRect(r, holeH / 5f, holeH / 5f, paint);
            }
        }
    }

    // ---- actions -----------------------------------------------------------------------------

    private void openStream(AileAyar.Kanal k) {
        if (AileAyar.isYoutube(k.url)) {
            openLink(new AileAyar.Link(k.ad, null, k.url, AileAyar.YOUTUBE, true));
            return;
        }
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

    /**
     * Surfshark refuses to connect while the filter's VPN is up, so the filter lends it the slot
     * first. It comes back by itself once Surfshark is turned off (see Filter.wake).
     */
    private void openSurfshark(String pkg) {
        if (Filter.lend(home)) afterFilter(pkg, 0);
        else openApp(pkg);
    }

    /** Opens Surfshark once the filter's VPN is down, as Surfshark checks for one at start. */
    private void afterFilter(String pkg, int tries) {
        if (!Filter.vpnUp(home) || tries >= 24) {
            root.postDelayed(() -> openApp(pkg), 300);
            return;
        }
        root.postDelayed(() -> afterFilter(pkg, tries + 1), 250);
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

    /**
     * Back on the screen. By HOME: the main screen, on Canlı TV. From an app opened in a list
     * (a channel, a film): the list, where the remote left it, for the next one.
     */
    void resume() {
        if (homePressed) {
            homePressed = false;
            fromTile = 0;
            if (screen != ANA) {
                screen = ANA;
                fill();
            }
        }
        // An aile.json pushed with adb shows on the next HOME.
        if (AileAyar.localStamp(home) != localStamp) fill();
        tick();
        if (screen == ANA || home.getCurrentFocus() == null) focusFirst();
        Uzak.refresh(home, ayar.uzak, this::uzakChanged);
        Rehber.refresh(home, Rehber.URL, this::showOnAir);
        Filmler.refresh(home, this::filmsChanged);
        // The filter is woken on resume and takes a moment to raise its VPN.
        for (long at : new long[] {4000, 10000, 20000}) root.postDelayed(this::checkFilter, at);
    }

    private void checkFilter() {
        boolean up = Filter.vpnUp(home);
        boolean lent = Filter.lent(home);
        if (filterPill != null) filterPill.setVisibility(!up && !lent ? View.VISIBLE : View.GONE);
        if (surfPill != null) surfPill.setText(up && lent ? "Surfshark açık" : "Surfshark");
    }

    /** Puts the programme on air now on the Canlı TV tile, the channel rows and the panel. */
    private void showOnAir() {
        long now = System.currentTimeMillis();
        for (int i = 0; i < onAir.size(); i++) {
            AileAyar.Kanal k = onAirKanal.get(i);
            String title = Rehber.now(home, key(k), now);
            onAir.get(i).setText(title != null ? title : idle(k));
        }
        for (Satir s : satirlar) s.refresh(now);
        if (detay != null) detay.refresh(now);
    }

    /** A new channel list arrived: rebuild, keeping the remote's place. */
    private void uzakChanged() {
        rebuild();
    }

    /** A new film list arrived: the tile's count and, if open, the list itself. */
    private void filmsChanged() {
        if (screen != KANALLAR) rebuild();
    }

    /** Builds the screen again and puts the focus back on the same row or tile. */
    void rebuild() {
        View f = home.getCurrentFocus();
        int at = -1;
        if (screen == KANALLAR) {
            for (int i = 0; i < satirlar.size(); i++) if (satirlar.get(i).kart == f) at = i;
        }
        boolean onFilms = screen == ANA && f != null && f == filmTile;
        fill();
        if (at >= 0 && at < satirlar.size()) satirlar.get(at).kart.requestFocus();
        else if (onFilms && filmTile != null) filmTile.requestFocus();
        else focusFirst();
    }

    void focusFirst() {
        if (first != null) first.requestFocus();
    }

    /** Every minute: clock, date and, when the hour of the day changes it, the backdrop. */
    void tick() {
        Date now = new Date();
        if (clock != null) clock.setText(new SimpleDateFormat("HH:mm", TR).format(now));
        if (date != null) date.setText(new SimpleDateFormat("d MMMM EEEE", TR).format(now));
        int before = sky.phase();
        sky.setPhase(Gunes.phase(Calendar.getInstance()));
        int g = Gokyuzu.glass(sky.phase());
        for (Kart t : glassTiles) t.setGlass(g);
        if (sky.phase() != before) {
            for (TextView p : pills) paintPill(p, p.isFocused());
            if (detay != null) detay.setGlass(g);
        }
        // A TV left on this screen still picks up a fixed channel (at most every 10 minutes).
        if (ayar != null) Uzak.refresh(home, ayar.uzak, this::uzakChanged);
        Rehber.refresh(home, Rehber.URL, this::showOnAir);
        showOnAir();
        // While Surfshark has the slot, see each minute whether it is done with it.
        if (Filter.lent(home)) Filter.wake(home);
        checkFilter();
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
