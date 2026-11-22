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

  /** Insets of the safe screen rectangle relative to an already measured host.
   * A host resized above IME receives zero extra bottom padding. */
  static int[] safePadding(int x,int y,int w,int h,int left,int top,int right,int bottom){
    if(w<=0||h<=0||right<=left||bottom<=top||right<=x||bottom<=y||left>=x+w||top>=y+h)return new int[4];
    int l=Math.max(0,left-x),t=Math.max(0,top-y),r=Math.max(0,x+w-right),b=Math.max(0,y+h-bottom);
    return new int[]{Math.min(l,w-1),Math.min(t,h-1),Math.min(r,Math.max(0,w-l-1)),Math.min(b,Math.max(0,h-t-1))};
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
