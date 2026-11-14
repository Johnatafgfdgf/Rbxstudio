package com.rbxnative.androidport;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.File;

final class PlayLoadingView extends FrameLayout {
    private final Bitmap preview;
    private final Paint filter = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect screen = new Rect();
    private final TextView stage;

    PlayLoadingView(Context context, File session, String title, int accent, Runnable cancel) {
        super(context);
        preview = BitmapFactory.decodeFile(new File(session, "preview.png").getPath());
        setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {accent, RbxGeometry.BACKGROUND}));
        setWillNotDraw(false);

        LinearLayout center = new LinearLayout(context);
        center.setOrientation(LinearLayout.VERTICAL);
        center.setGravity(Gravity.CENTER);
        int width = Math.max(dp(180), Math.min(dp(540), getResources().getDisplayMetrics().widthPixels - dp(72)));
        addView(center, new LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(16), dp(22), dp(16), dp(22));
        card.setBackground(panel(0xe9202231, 16));
        center.addView(card, new LinearLayout.LayoutParams(-1, -2));

        RbxBrand brand = new RbxBrand(context, accent);
        brand.setContentDescription("Rbx Studio");
        card.addView(brand, new LinearLayout.LayoutParams(dp(84), dp(76)));

        TextView name = text(title, 20, 0xfff1f1fb);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setMaxLines(2);
        name.setEllipsize(TextUtils.TruncateAt.END);
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));

        stage = text("Preparando o Play local…", 14, 0xffc9cada);
        stage.setAccessibilityLiveRegion(ACCESSIBILITY_LIVE_REGION_POLITE);
        LinearLayout.LayoutParams stageLayout = new LinearLayout.LayoutParams(-1, -2);
        stageLayout.topMargin = dp(7);
        card.addView(stage, stageLayout);

        ProgressBar progress = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(true);
        progress.setIndeterminateTintList(ColorStateList.valueOf(accent));
        LinearLayout.LayoutParams progressLayout = new LinearLayout.LayoutParams(dp(116), dp(7));
        progressLayout.topMargin = dp(16);
        card.addView(progress, progressLayout);

        Button close = new Button(context);
        close.setText("×");
        close.setTextSize(22);
        close.setTextColor(0xfff1f1fb);
        close.setContentDescription("Cancelar Play");
        close.setPadding(0, 0, 0, 0);
        close.setBackground(panel(0xd9202231, 12));
        close.setOnClickListener(view -> cancel.run());
        LayoutParams closeLayout = new LayoutParams(dp(48), dp(48), Gravity.TOP | Gravity.START);
        closeLayout.setMargins(dp(12), dp(12), dp(12), 0);
        addView(close, closeLayout);
    }

    void stage(String text) { stage.setText(text); }

    @Override protected void onDraw(Canvas canvas) {
        if (preview != null) {
            screen.set(0, 0, getWidth(), getHeight());
            canvas.drawBitmap(preview, null, screen, filter);
        }
        canvas.drawColor(0x960f101d);
    }

    private TextView text(String value, int sp, int color) {
        TextView view = new TextView(getContext());
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private GradientDrawable panel(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
