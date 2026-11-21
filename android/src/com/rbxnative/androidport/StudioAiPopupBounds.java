package com.rbxnative.androidport;

/** Coordinates constrained to the visible window, including IME and split screen. */
final class StudioAiPopupBounds {
  final int x, y, width, height;

  private StudioAiPopupBounds(int x, int y, int w, int h) {
    this.x = x;
    this.y = y;
    width = w;
    height = h;
  }

  static StudioAiPopupBounds place(
      int left,
      int top,
      int right,
      int bottom,
      int anchorLeft,
      int anchorTop,
      int anchorBottom,
      int wantedWidth,
      int wantedHeight,
      int margin,
      int gap,
      boolean above) {
    int l = left + margin,
        t = top + margin,
        r = Math.max(l + 1, right - margin),
        b = Math.max(t + 1, bottom - margin);
    int w = Math.max(1, Math.min(wantedWidth, r - l));
    int upper = Math.max(0, Math.min(b, anchorTop - gap) - t),
        lower = Math.max(0, b - Math.max(t, anchorBottom + gap));
    boolean up = above ? upper > 0 : lower < Math.min(wantedHeight, upper);
    int room = up ? upper : lower;
    if (room < 1) room = b - t;
    int h = Math.max(1, Math.min(wantedHeight, room));
    return new StudioAiPopupBounds(
        Math.max(l, Math.min(anchorLeft, r - w)),
        Math.max(t, Math.min(up ? anchorTop - gap - h : anchorBottom + gap, b - h)),
        w,
        h);
  }
}
