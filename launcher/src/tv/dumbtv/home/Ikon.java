package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** The family screen's four line icons (shield, gear, camera, microphone), drawn on a 24x24 grid. */
final class Ikon extends Drawable {
    static final int KALKAN = 0, AYAR = 1, KAMERA = 2, MIKROFON = 3;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF r = new RectF();

    Ikon(int kind) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(0xFFFFFFFF);
        switch (kind) {
            case KALKAN:
                path.moveTo(12, 3);
                path.lineTo(20, 6);
                path.lineTo(20, 12);
                path.cubicTo(20, 16.5f, 16.6f, 20.3f, 12, 21);
                path.cubicTo(7.4f, 20.3f, 4, 16.5f, 4, 12);
                path.lineTo(4, 6);
                path.close();
                break;
            case AYAR:
                path.addCircle(12, 12, 3, Path.Direction.CW);
                float[][] spokes = {{12, 2, 12, 5}, {12, 19, 12, 22}, {2, 12, 5, 12}, {19, 12, 22, 12},
                    {4.9f, 4.9f, 7, 7}, {17, 17, 19.1f, 19.1f}, {4.9f, 19.1f, 7, 17}, {17, 7, 19.1f, 4.9f}};
                for (float[] s : spokes) {
                    path.moveTo(s[0], s[1]);
                    path.lineTo(s[2], s[3]);
                }
                break;
            case KAMERA:
                path.moveTo(23, 7);
                path.lineTo(16, 12);
                path.lineTo(23, 17);
                path.close();
                r.set(1, 5, 16, 19);
                path.addRoundRect(r, 2, 2, Path.Direction.CW);
                break;
            default:
                r.set(9, 2, 15, 14);
                path.addRoundRect(r, 3, 3, Path.Direction.CW);
                r.set(5, 4, 19, 18);
                path.addArc(r, 0, 180);
                path.moveTo(12, 18);
                path.lineTo(12, 22);
                path.moveTo(8, 22);
                path.lineTo(16, 22);
                break;
        }
    }

    @Override
    public void draw(Canvas canvas) {
        android.graphics.Rect b = getBounds();
        canvas.save();
        canvas.translate(b.left, b.top);
        canvas.scale(b.width() / 24f, b.height() / 24f);
        canvas.drawPath(path, paint);
        canvas.restore();
    }

    @Override
    public void setTint(int color) {
        paint.setColor(color);
        invalidateSelf();
    }

    @Override
    public void setTintList(android.content.res.ColorStateList tint) {
        if (tint != null) setTint(tint.getDefaultColor());
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    @SuppressWarnings("deprecation") // abstract in Drawable on API 25, so it must be implemented
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
