package com.rbxnative.androidport;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** The same complete vector paths used by the editor splash and app icon. */
final class RbxBrand extends View {
    private static final Path[] OUTLINES = paths(RbxGeometry.OUTLINE_PATHS);
    private static final Path[] FACES = paths(RbxGeometry.FACE_PATHS);
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int accent;

    RbxBrand(Context context, int accent) {
        super(context);
        this.accent = accent;
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        draw(canvas, paint, accent, getWidth(), getHeight());
    }

    static Bitmap menuImage(int accent) {
        Bitmap bitmap = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
        draw(new Canvas(bitmap), new Paint(Paint.ANTI_ALIAS_FLAG), accent, 128, 128);
        return bitmap;
    }

    private static void draw(Canvas canvas, Paint paint, int accent, int width, int height) {
        float side = Math.min(width, height);
        canvas.save();
        canvas.translate(width / 2f, height / 2f);
        canvas.scale(side / 1190f, side / 1190f);
        canvas.translate(-776f, -801f);
        for (int i = 0; i < OUTLINES.length; i++) {
            paint.setColor(accent);
            canvas.drawPath(OUTLINES[i], paint);
            paint.setColor(RbxGeometry.FACE);
            canvas.drawPath(FACES[i], paint);
        }
        canvas.restore();
    }

    private static Path[] paths(String[] data) {
        Path[] paths = new Path[data.length];
        for (int i = 0; i < data.length; i++) {
            Path path = new Path();
            path.setFillType(Path.FillType.EVEN_ODD);
            String[] parts = data[i].split(" ");
            for (int j = 0; j < parts.length;) {
                switch (parts[j++]) {
                    case "M": path.moveTo(number(parts[j++]), number(parts[j++])); break;
                    case "L": path.lineTo(number(parts[j++]), number(parts[j++])); break;
                    case "Q": path.quadTo(number(parts[j++]), number(parts[j++]),
                            number(parts[j++]), number(parts[j++])); break;
                    case "Z": path.close(); break;
                    case "": break;
                    default: throw new IllegalStateException("Invalid packaged logo path");
                }
            }
            paths[i] = path;
        }
        return paths;
    }

    private static float number(String text) { return Float.parseFloat(text); }
}
