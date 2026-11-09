package tv.dumbtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * One tile of the family screen. Three looks, as in the approved design:
 * GLASS (text on a see-through card that turns white when focused), SOLID (a coloured card that
 * gets a white ring) and BANNER (an app's own TV banner, also ringed). Focus also grows the tile
 * by 8 percent so it is seen from across the room.
 */
final class Kart extends FrameLayout {
    static final int GLASS = 0, SOLID = 1, BANNER = 2;
    private static final float FOCUS_SCALE = 1.08f;
    private static final int WHITE = 0xFFFFFFFF, INK = 0xFF0E1014;

    private final int look;
    private final float radius;
    private final GradientDrawable card = new GradientDrawable();
    private final GradientDrawable ring = new GradientDrawable();
    private GradientDrawable badgeBg;
    private TextView title, sub;
    private ImageView icon;
    private int glass = 0x1AFFFFFF;
    private final int solid;

    /** {@code px} converts a length of the 1920x1080 design to pixels on this screen. */
    Kart(Context c, int look, int solidColor, float radiusPx, float ringPx) {
        super(c);
        this.look = look;
        this.solid = solidColor;
        this.radius = radiusPx;
        setFocusable(true);
        setClickable(true);
        card.setCornerRadius(radiusPx);
        ring.setCornerRadius(radiusPx);
        ring.setColor(0);
        ring.setStroke(Math.round(ringPx), WHITE);
        setBackground(card);
        setClipToOutline(true);
        setOnFocusChangeListener((v, has) -> focus(has));
        paint(false);
    }

    void setTitle(TextView t) {
        title = t;
        paint(isFocused());
    }

    void setSub(TextView t) {
        sub = t;
        paint(isFocused());
    }

    void setIcon(ImageView i) {
        icon = i;
        paint(isFocused());
    }

    void setBadge(GradientDrawable bg) {
        badgeBg = bg;
        paint(isFocused());
    }

    /** The see-through colour for the current backdrop. */
    void setGlass(int color) {
        glass = color;
        paint(isFocused());
    }

    private void focus(boolean has) {
        animate().scaleX(has ? FOCUS_SCALE : 1f).scaleY(has ? FOCUS_SCALE : 1f)
                .translationZ(has ? radius : 0).setDuration(150).start();
        paint(has);
    }

    private void paint(boolean focused) {
        if (look == GLASS) {
            card.setColor(focused ? WHITE : glass);
            card.setStroke(1, focused ? WHITE : 0x29FFFFFF);
            setForeground(null);
            if (title != null) title.setTextColor(focused ? INK : WHITE);
            if (sub != null) sub.setTextColor(focused ? 0xFF3A3F4A : 0xFFC9CEDA);
            if (icon != null) icon.setImageTintList(ColorStateList.valueOf(focused ? INK : WHITE));
            if (badgeBg != null) badgeBg.setColor(focused ? INK : 0x59000000);
        } else {
            card.setColor(look == SOLID ? solid : 0xFF1E2229);
            setForeground(focused ? ring : null);
        }
    }

    /** A pill like the design's "CANLI" badge: red dot and white capitals. */
    static LinearLayout badge(Context c, Kart owner, float textPx, float padH, float padV, int fixedBg) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER_VERTICAL);
        b.setPadding(Math.round(padH), Math.round(padV), Math.round(padH), Math.round(padV));
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(textPx * 2);
        bg.setColor(fixedBg != 0 ? fixedBg : 0x59000000);
        b.setBackground(bg);
        View dot = new View(c);
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(0xFFFF453A);
        dot.setBackground(d);
        int ds = Math.round(textPx * 0.55f);
        LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(ds, ds);
        dp.rightMargin = Math.round(textPx * 0.45f);
        b.addView(dot, dp);
        TextView t = new TextView(c);
        t.setText("CANLI");
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, textPx);
        t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.05f);
        t.setTextColor(WHITE);
        t.setIncludeFontPadding(false);
        b.addView(t);
        if (fixedBg == 0 && owner != null) owner.setBadge(bg);
        return b;
    }

    /** The banner of an installed app, or null when it has none. */
    static Drawable banner(Context c, String pkg) {
        try {
            return c.getPackageManager().getApplicationBanner(pkg);
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }
}
