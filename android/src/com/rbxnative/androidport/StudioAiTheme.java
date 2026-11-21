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

/** Android controls mirror the default mobile Studio chrome in mobile_style.rs. */
final class StudioAiTheme {
    private StudioAiTheme() {}

    static final int GROUND = 0xFF0B1322;
    static final int DOCK = 0xFF19263C;
    static final int DOCK_BOTTOM = 0xFF101A2B;
    static final int CONTROL_TOP = 0xFF283B56;
    static final int CONTROL_BOTTOM = 0xFF19273F;
    static final int FIELD = 0xFF142338;
    static final int BORDER = 0xFF3C526E;
    static final int BORDER_STRONG = BORDER;
    static final int TEXT = 0xFFF2F7FF;
    static final int TEXT_MUTED = 0xFFADBED2;
    static final int TEXT_DISABLED = 0xFF7D91AB;
    static final int ACCENT = 0xFF42DBEF;
    static final int PRIMARY_TOP = 0xFF59E6F7;
    static final int PRIMARY_BOTTOM = 0xFF24BBD8;
    static final int PRIMARY_INK = 0xFF082437;
    static final int PRESSED = 0xFF1B3E52;
    static final int RED = 0xFFFFADAD;
    private static final int CONTROL_RADIUS = 12;
    private static final int SURFACE_RADIUS = 18;
    private static final int ROW_RADIUS = 10;

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
        return gradient(c, DOCK, DOCK_BOTTOM, SURFACE_RADIUS, BORDER);
    }

    private static GradientDrawable gradient(Context c, int top, int bottom, int radius, int line) {
        GradientDrawable drawable = surface(c, top, radius, line);
        drawable.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        drawable.setColors(new int[]{top, bottom});
        return drawable;
    }

    private static StateListDrawable states(Context c, Drawable normal, int active) {
        StateListDrawable all = new StateListDrawable();
        all.addState(new int[]{-android.R.attr.state_enabled}, surface(c, FIELD, CONTROL_RADIUS, BORDER));
        all.addState(new int[]{android.R.attr.state_pressed}, surface(c, active, CONTROL_RADIUS, ACCENT));
        all.addState(new int[]{android.R.attr.state_focused}, surface(c, active, CONTROL_RADIUS, ACCENT));
        all.addState(new int[]{android.R.attr.state_hovered}, surface(c, active, CONTROL_RADIUS, ACCENT));
        all.addState(StateSet.WILD_CARD, normal);
        return all;
    }

    static void button(Context c,Button button,boolean primary,boolean danger) {
        Drawable normal = primary ? gradient(c, PRIMARY_TOP, PRIMARY_BOTTOM, CONTROL_RADIUS, PRIMARY_TOP)
                : gradient(c, CONTROL_TOP, CONTROL_BOTTOM, CONTROL_RADIUS, danger ? RED : BORDER);
        button.setBackgroundTintList(null);
        button.setBackground(states(c, normal, primary ? PRIMARY_TOP : PRESSED));
        button.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled},StateSet.WILD_CARD},
                new int[]{TEXT_DISABLED, primary ? PRIMARY_INK : danger ? RED : TEXT}));
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
            Glyph icon = new Glyph(c,glyph,primary ? PRIMARY_INK : TEXT);
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
        edit.setBackgroundTintList(null);
        edit.setBackground(states(c, surface(c, FIELD, CONTROL_RADIUS, BORDER), FIELD));
        edit.setMinHeight(dp(c,34));
        edit.setPadding(dp(c,10),dp(c,6),dp(c,10),dp(c,6));
    }

    static void spinner(Context c,Spinner spinner) {
        spinner.setBackground(new DropArrow(c));
        spinner.setMinimumHeight(dp(c,34));
        spinner.setMinimumWidth(dp(c,92));
        spinner.setPadding(dp(c,10),0,dp(c,26),0);
        spinner.setPopupBackgroundDrawable(surface(c,FIELD,ROW_RADIUS,BORDER_STRONG));
    }

    static void body(TextView text,boolean title) {
        text.setTextColor(TEXT);
        text.setTextSize(title ? 13.5f : 12f);
        text.setIncludeFontPadding(false);
    }


    static void icon(Context c, Button button, String kind, boolean primary) {
        int color = primary ? PRIMARY_INK : TEXT;
        Drawable foreground = button.getForeground();
        if (foreground instanceof Glyph) {
            Glyph shown = (Glyph)foreground;
            if (shown.kind.equals(kind) && shown.tint == color) return;
        }
        button.setCompoundDrawables(null, null, null, null);
        button.setText("");
        button.setForeground(new Glyph(c, kind, color));
        button.setForegroundGravity(Gravity.CENTER);
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
            float edge = Math.min(dp(context,18), Math.min(getBounds().width(),getBounds().height()));
            c.translate(getBounds().centerX() - edge / 2f, getBounds().centerY() - edge / 2f);
            float scale=edge/24f;
            c.scale(scale,scale);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1.9f);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(tint);
            Path p=new Path();
            switch(kind) {
                case "menu":
                    for (int y : new int[]{6,12,18}) c.drawLine(4,y,20,y,paint);
                    break;
                case "close":
                    c.drawLine(6,6,18,18,paint); c.drawLine(18,6,6,18,paint);
                    break;
                case "plus":
                    c.drawLine(5,12,19,12,paint); c.drawLine(12,5,12,19,paint);
                    break;
                case "minimize":
                    c.drawLine(5,12,19,12,paint);
                    break;
                case "expand":
                    p.moveTo(14,4); p.lineTo(20,4); p.lineTo(20,10);
                    p.moveTo(10,20); p.lineTo(4,20); p.lineTo(4,14);
                    p.moveTo(20,4); p.lineTo(4,20);
                    c.drawPath(p,paint); break;
                case "up":
                    p.moveTo(5,11); p.lineTo(12,4); p.lineTo(19,11);
                    c.drawPath(p,paint); c.drawLine(12,4,12,20,paint); break;
                case "stop":
                    c.drawRoundRect(new RectF(6,6,18,18),2,2,paint); break;
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
            canvas.drawRoundRect(rect, dp(context,CONTROL_RADIUS), dp(context,CONTROL_RADIUS), paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1,dp(context,1)));
            paint.setColor(BORDER_STRONG);
            canvas.drawRoundRect(new RectF(rect.left+0.5f,rect.top+0.5f,
                    rect.right-0.5f,rect.bottom-0.5f),
                    dp(context,CONTROL_RADIUS),dp(context,CONTROL_RADIUS),paint);
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
