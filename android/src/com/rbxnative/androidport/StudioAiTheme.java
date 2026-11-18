package com.rbxnative.androidport;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.StateSet;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Spinner;
import android.widget.TextView;

/**
 * GPUI Studio Default theme tokens mirrored for the Java-only AI screen.
 * Source: pinned upstream rbx_studio/assets/themes/default/theme.json and
 * rbx_studio/src/launcher/ui.rs. Not an invented blue Android theme.
 *
 * The original editor's controls use FLAT surfaces, subtle one-pixel lines
 * and 5px radii. Avoid faux bevels, gradients, or PNG textures: the actual
 * shell doesn't use them for button surfaces.
 */
final class StudioAiTheme {
    private StudioAiTheme() {}

    static final int GROUND = Color.rgb(10, 10, 11);      // black #0A0A0B
    static final int DOCK = Color.rgb(18, 18, 19);        // dock #121213
    static final int FIELD = Color.rgb(25, 26, 28);       // field_select #191A1C
    static final int BORDER = Color.argb(15,255,255,255); // border #FFFFFF0F
    static final int BORDER_STRONG = Color.argb(28,255,255,255);
    static final int TEXT = Color.rgb(226,226,230);       // #E2E2E6
    static final int TEXT_MUTED = Color.rgb(143,143,151); // #8F8F97
    static final int TEXT_DISABLED = Color.rgb(92,92,99);
    static final int ACCENT = Color.rgb(108,127,219);     // check_on #6C7FDB
    static final int ACCENT_HOVER = Color.rgb(115,133,221);
    static final int ACCENT_FOCUS = Color.argb(140,108,127,219);
    static final int SECONDARY_HOVER = Color.rgb(32,33,35);
    static final int RED = Color.rgb(224,108,108);

    static int dp(Context c,float units) {
        return Math.round(units * c.getResources().getDisplayMetrics().density);
    }

    static GradientDrawable surface(Context c,int fill,float radius,int line) {
        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.RECTANGLE);
        shape.setColor(fill);
        shape.setCornerRadius(dp(c,radius));
        if (line != Color.TRANSPARENT)
            shape.setStroke(Math.max(1, dp(c,1)),line);
        return shape;
    }

    static GradientDrawable panel(Context c) {
        return surface(c, DOCK, 8, BORDER);
    }

    private static StateListDrawable states(Context c,int normal,int active,
            int stroke,int activeStroke,int radius) {
        StateListDrawable all = new StateListDrawable();
        all.addState(new int[]{-android.R.attr.state_enabled},
                surface(c,FIELD,radius,BORDER));
        all.addState(new int[]{android.R.attr.state_pressed},
                surface(c,active,radius,activeStroke));
        all.addState(new int[]{android.R.attr.state_focused},
                surface(c,active,radius,ACCENT_FOCUS));
        all.addState(new int[]{android.R.attr.state_hovered},
                surface(c,active,radius,activeStroke));
        all.addState(StateSet.WILD_CARD, surface(c,normal,radius,stroke));
        return all;
    }

    /** Every AI action shares the same real Studio surface and interaction. */
    static void button(Context c,Button button,boolean primary,boolean danger) {
        int normal = primary ? ACCENT : danger ? RED : FIELD;
        int active = primary ? ACCENT_HOVER : danger ? Color.rgb(191,93,93)
                : SECONDARY_HOVER;
        int stroke = primary ? ACCENT : danger ? RED : BORDER_STRONG;
        int selectedStroke = primary ? ACCENT_HOVER : danger ? RED : ACCENT_FOCUS;
        button.setBackground(states(c,normal,active,stroke,selectedStroke,5));
        button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled},StateSet.WILD_CARD},
                new int[]{TEXT_DISABLED, (primary || danger) ? GROUND : TEXT}));
        button.setTypeface(Typeface.create("sans-serif-medium",
                (primary || danger) ? Typeface.BOLD : Typeface.NORMAL));
        button.setTextSize(12.5f);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(c,36));
        button.setMinimumHeight(dp(c,36));
        button.setMinWidth(dp(c,44));
        button.setMinimumWidth(dp(c,44));
        button.setPadding(dp(c,12),0,dp(c,12),0);
        button.setIncludeFontPadding(false);
        button.setElevation(0);
        button.setStateListAnimator(null);
        // Same compact Lucide-like line-glyph rhythm as the GPUI launcher:
        // a 14dp monochrome glyph, 6dp gap, never a new logo.
        String label = button.getText().toString().toLowerCase(java.util.Locale.ROOT);
        String glyph = label.startsWith("voltar") ? "back" :
                label.startsWith("salvar chave") ? "key" :
                label.startsWith("remover chave") ? "trash" :
                label.startsWith("consultar modelos") ? "refresh" :
                label.startsWith("como conectar") ? "help" :
                label.startsWith("enviar") ? "send" : null;
        if (glyph != null) {
            Glyph icon = new Glyph(c,glyph,(primary || danger) ? GROUND : TEXT);
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(icon,null,null,null);
            button.setCompoundDrawablePadding(dp(c,6));
        }
    }

    static void checkbox(Context c,CheckBox check) {
        check.setButtonTintList(new ColorStateList(new int[][]{
                new int[]{android.R.attr.state_checked},StateSet.WILD_CARD},
                new int[]{ACCENT,TEXT_MUTED}));
        check.setTextColor(TEXT);
        check.setTextSize(12.5f);
        check.setMinHeight(dp(c,30));
        check.setPadding(0,dp(c,2),0,dp(c,2));
    }

    static void field(Context c,android.widget.EditText edit) {
        edit.setTextColor(TEXT);
        edit.setHintTextColor(TEXT_DISABLED);
        edit.setTextSize(12.5f);
        edit.setBackground(states(c,FIELD,FIELD,BORDER_STRONG,ACCENT_FOCUS,5));
        edit.setMinHeight(dp(c,34));
        edit.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6));
    }

    static void spinner(Context c,Spinner spinner) {
        spinner.setBackground(new DropArrow(c));
        spinner.setMinimumHeight(dp(c,34));
        spinner.setMinimumWidth(dp(c,92));
        spinner.setPadding(dp(c,10),0,dp(c,26),0);
        spinner.setPopupBackgroundDrawable(surface(c,FIELD,5,BORDER_STRONG));
    }

    static void body(TextView text,boolean title) {
        text.setTextColor(TEXT);
        text.setTextSize(title ? 13.5f : 12f);
        text.setIncludeFontPadding(false);
    }


    /** Small original vector line icons, never upscaled Android system icons. */
    private static final class Glyph extends Drawable {
        private final Context context;
        private final String kind;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int tint;
        Glyph(Context c,String name,int color) {
            context=c;kind=name;tint=color;
        }
        @Override public int getIntrinsicWidth() { return dp(context,14); }
        @Override public int getIntrinsicHeight() { return dp(context,14); }
        @Override public void draw(Canvas c) {
            c.save();
            c.translate(getBounds().left,getBounds().top);
            float scale=Math.min(getBounds().width(),getBounds().height())/24f;
            c.scale(scale,scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(tint);
            Path p=new Path();
            switch(kind) {
                case "back":
                    p.moveTo(13,5);p.lineTo(6,12);p.lineTo(13,19);
                    c.drawPath(p,paint);c.drawLine(6,12,21,12,paint);break;
                case "key":
                    c.drawCircle(7.5f,9.5f,4.2f,paint);
                    c.drawLine(10.6f,12.7f,20,22,paint);
                    c.drawLine(15.5f,17.6f,18,15.1f,paint);
                    c.drawLine(18,20,20.4f,17.6f,paint);break;
                case "trash":
                    c.drawLine(4,7,20,7,paint);
                    c.drawLine(9,4,15,4,paint);
                    c.drawRoundRect(new RectF(6,7,18,21),1,1,paint);
                    c.drawLine(10,11,10,17,paint);
                    c.drawLine(14,11,14,17,paint);break;
                case "refresh":
                    c.drawArc(new RectF(4,4,20,20),210,235,false,paint);
                    p.moveTo(18,3);p.lineTo(20,9);p.lineTo(14,9);
                    c.drawPath(p,paint);break;
                case "help":
                    c.drawCircle(12,12,9,paint);
                    c.drawArc(new RectF(8,6,16,14),195,250,false,paint);
                    c.drawLine(12,14,12,16,paint);
                    c.drawPoint(12,19,paint);break;
                case "send":
                    p.moveTo(4,4);p.lineTo(20,12);p.lineTo(4,20);
                    p.lineTo(8,12);p.close();
                    c.drawPath(p,paint);c.drawLine(8,12,20,12,paint);break;
            }
            c.restore();
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha);invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter);invalidateSelf();
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }

    /** One real chevron, not the platform-themed Spinner arrow. */
    private static final class DropArrow extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Context context;
        DropArrow(Context c) { context = c; }
        @Override public void draw(Canvas canvas) {
            RectF rect = new RectF(getBounds());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(FIELD);
            canvas.drawRoundRect(rect, dp(context,5), dp(context,5), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1,dp(context,1)));
            paint.setColor(BORDER_STRONG);
            canvas.drawRoundRect(new RectF(rect.left+0.5f,rect.top+0.5f,
                    rect.right-0.5f,rect.bottom-0.5f),
                    dp(context,5),dp(context,5),paint);
            paint.setColor(TEXT_MUTED);
            paint.setStrokeWidth(dp(context,1.5f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            float cx=rect.right-dp(context,15);
            float cy=rect.centerY();
            Path p = new Path();
            p.moveTo(cx-dp(context,4),cy-dp(context,2));
            p.lineTo(cx,cy+dp(context,2));
            p.lineTo(cx+dp(context,4),cy-dp(context,2));
            canvas.drawPath(p,paint);
        }
        @Override public void setAlpha(int value) { paint.setAlpha(value); invalidateSelf(); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            paint.setColorFilter(filter); invalidateSelf();
        }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}
