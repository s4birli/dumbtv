package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The family screen's backdrop: an Istanbul skyline over the water, drawn in code for one of
 * {@link Gunes}' four times of day. Coordinates are those of the 1920x1080 design and scaled
 * to the view, so nothing is decoded from disk and nothing is fetched.
 */
final class Gokyuzu extends View {
    private static final float W = 1920, H = 1080;
    private static final float[] SKY_STOPS = {0f, 0.35f, 0.56f, 0.6f, 0.61f, 1f};
    /** x, y, radius, alpha (0-1) of each star. */
    private static final float[][] STARS = {
        {140, 70, 1.6f, .7f}, {310, 150, 1.2f, .5f}, {520, 60, 1.8f, .6f}, {760, 120, 1.3f, .5f},
        {880, 40, 1.6f, .7f}, {1010, 170, 1.2f, .4f}, {1320, 60, 1.5f, .6f}, {1420, 190, 1.2f, .5f},
        {1610, 40, 1.8f, .7f}, {1760, 150, 1.3f, .5f}, {1860, 260, 1.4f, .5f}, {60, 300, 1.2f, .4f},
        {420, 330, 1.4f, .4f}, {1240, 310, 1.2f, .4f}, {1700, 380, 1.3f, .35f}
    };
    private static final float[][] LIGHTS = {
        {196, 626}, {222, 638}, {778, 630}, {900, 560}, {1000, 636}, {1584, 632}, {1722, 624}
    };

    /** One time of day: sky stops, orb (sun or moon), skyline colour and the overlay levels. */
    private static final class Look {
        final int[] sky;
        final float orbX, orbY, orbR;
        final int orb;
        final float halo, stars, lights, reflection, dim;
        final int skyline;

        Look(int[] sky, float orbX, float orbY, float orbR, int orb, float halo, float stars,
                int skyline, float lights, float reflection, float dim) {
            this.sky = sky;
            this.orbX = orbX;
            this.orbY = orbY;
            this.orbR = orbR;
            this.orb = orb;
            this.halo = halo;
            this.stars = stars;
            this.skyline = skyline;
            this.lights = lights;
            this.reflection = reflection;
            this.dim = dim;
        }
    }

    private static final Look[] LOOKS = new Look[4];
    static {
        LOOKS[Gunes.SABAH] = new Look(new int[] {0xFF3B5A8C, 0xFF7C95C4, 0xFFF2B8A0, 0xFFF7D2A6, 0xFF4A5F86, 0xFF2A3858},
                300, 560, 46, 0xFFFFE3B0, .18f, 0f, 0xFF2C3A5C, .15f, .35f, .38f);
        LOOKS[Gunes.GUNDUZ] = new Look(new int[] {0xFF2F6FB3, 0xFF5C9AD6, 0xFFA9CDEB, 0xFFCFE4F2, 0xFF3F79A8, 0xFF1F4B73},
                1250, 120, 40, 0xFFFFF6D8, .2f, 0f, 0xFF3A4D66, 0f, .2f, .42f);
        LOOKS[Gunes.AKSAM] = new Look(new int[] {0xFF1F2350, 0xFF5B3F78, 0xFFE07A5F, 0xFFF2A65A, 0xFF3A2F55, 0xFF1A1630},
                1600, 612, 54, 0xFFFFB36B, .18f, .25f, 0xFF1A1530, .55f, .4f, .12f);
        LOOKS[Gunes.GECE] = new Look(new int[] {0xFF0A1226, 0xFF15204A, 0xFF2C3364, 0xFF433F70, 0xFF0B1129, 0xFF070A18},
                1150, 98, 38, 0xFFF3EBD3, .06f, 1f, 0xFF0B1129, .6f, .18f, 0f);
    }

    /** Unfocused glass tile colour that reads on each backdrop. */
    static int glass(int phase) {
        switch (phase) {
            case Gunes.SABAH: return 0x6B080C18;
            case Gunes.GUNDUZ: return 0x73080C18;
            case Gunes.AKSAM: return 0x59080C18;
            default: return 0x1AFFFFFF;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path skyline = new Path();
    private final RectF rect = new RectF();
    private int phase = -1;
    private Shader skyShader, scrimShader;

    Gokyuzu(Context c) {
        super(c);
        buildSkyline();
        scrimShader = new LinearGradient(0, 0, 0, H,
                new int[] {0x00080B16, 0x1A080B16, 0xD1080B16}, new float[] {0f, .6f, 1f},
                Shader.TileMode.CLAMP);
        setPhase(Gunes.GECE);
    }

    void setPhase(int p) {
        if (p == phase || p < 0 || p > 3) return;
        phase = p;
        skyShader = new LinearGradient(0, 0, 0, H, LOOKS[p].sky, SKY_STOPS, Shader.TileMode.CLAMP);
        invalidate();
    }

    int phase() {
        return phase;
    }

    private void buildSkyline() {
        Path p = skyline;
        float[] ridge = {0, 660, 0, 628, 120, 620, 260, 626, 420, 614, 560, 622, 700, 616, 860, 620,
            1000, 612, 1180, 622, 1320, 616, 1480, 624, 1640, 614, 1800, 622, 1920, 616, 1920, 660};
        p.moveTo(ridge[0], ridge[1]);
        for (int i = 2; i < ridge.length; i += 2) p.lineTo(ridge[i], ridge[i + 1]);
        p.close();
        float[][] blocks = {{180, 604, 60, 56}, {252, 614, 40, 46}, {440, 610, 200, 50},
            {760, 612, 70, 48}, {884, 536, 32, 124}, {980, 620, 90, 40}, {1080, 612, 50, 48},
            {1290, 622, 140, 38}, {1560, 616, 80, 44}, {1700, 606, 60, 54}};
        for (float[] b : blocks) p.addRect(b[0], b[1], b[0] + b[2], b[1] + b[3], Path.Direction.CW);
        dome(p, 540, 614, 80);
        dome(p, 456, 628, 36);
        dome(p, 624, 628, 36);
        dome(p, 1360, 626, 60);
        minaret(p, 404, 512, 10, 148, 34);
        minaret(p, 430, 540, 9, 120, 28);
        minaret(p, 641, 540, 9, 120, 28);
        minaret(p, 666, 512, 10, 148, 34);
        minaret(p, 1268, 534, 9, 126, 30);
        minaret(p, 1443, 534, 9, 126, 30);
        // The tower's cone.
        p.moveTo(878, 538);
        p.lineTo(900, 486);
        p.lineTo(922, 538);
        p.close();
    }

    private void dome(Path p, float cx, float baseY, float r) {
        rect.set(cx - r, baseY - r, cx + r, baseY + r);
        p.arcTo(rect, 180, 180, true);
        p.close();
    }

    private static void minaret(Path p, float x, float top, float w, float h, float spire) {
        p.addRect(x, top, x + w, top + h, Path.Direction.CW);
        p.moveTo(x - 1, top);
        p.lineTo(x + w / 2, top - spire);
        p.lineTo(x + w + 1, top);
        p.close();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Look l = LOOKS[phase];
        canvas.save();
        canvas.scale(getWidth() / W, getHeight() / H);

        paint.setShader(skyShader);
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(null);

        if (l.stars > 0) {
            for (float[] s : STARS) {
                paint.setColor(0xFFFFFFFF);
                paint.setAlpha(Math.round(255 * s[3] * l.stars));
                canvas.drawCircle(s[0], s[1], s[2], paint);
            }
        }

        float haloR = l.orbR * 2.9f;
        int haloColor = (Math.round(255 * l.halo) << 24) | (l.orb & 0xFFFFFF);
        paint.setColor(0xFFFFFFFF);
        paint.setShader(new RadialGradient(l.orbX, l.orbY, haloR,
                new int[] {haloColor, haloColor & 0xFFFFFF}, new float[] {0.35f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(l.orbX, l.orbY, haloR, paint);
        paint.setShader(null);
        paint.setColor(l.orb);
        paint.setAlpha(242);
        canvas.drawCircle(l.orbX, l.orbY, l.orbR, paint);

        paint.setColor(l.skyline);
        canvas.drawPath(skyline, paint);

        if (l.lights > 0) {
            paint.setColor(0xFFF6C870);
            paint.setAlpha(Math.round(255 * l.lights));
            for (float[] p : LIGHTS) canvas.drawCircle(p[0], p[1], 2.1f, paint);
        }

        if (l.dim > 0) {
            paint.setColor(0xFF080C18);
            paint.setAlpha(Math.round(255 * l.dim));
            canvas.drawRect(0, 0, W, H, paint);
        }
        paint.setColor(0xFFFFFFFF);
        paint.setShader(scrimShader);
        canvas.drawRect(0, 0, W, H, paint);
        paint.setShader(null);
        canvas.restore();
    }
}
