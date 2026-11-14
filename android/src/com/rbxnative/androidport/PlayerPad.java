package com.rbxnative.androidport;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** Real analog movement input for the original Rook Humanoid controller. */
final class PlayerPad extends View {
    interface Listener {void move(float x,float forward);}
    private final Listener listener;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float x,y;private int pointer=-1;
    PlayerPad(Context context,Listener listener){super(context);this.listener=listener;setContentDescription("Mover personagem: direcional analógico");setFocusable(true);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float r=Math.min(getWidth(),getHeight())*.37f,cx=getWidth()/2f,cy=getHeight()/2f;
        paint.setStyle(Paint.Style.FILL);paint.setColor(0x77303945);c.drawCircle(cx,cy,r,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(0x99c0cfdf);c.drawCircle(cx,cy,r,paint);
        paint.setStyle(Paint.Style.FILL);paint.setColor(pointer<0?0xaac4d0e0:0xee66b1ff);c.drawCircle(cx+x*r*.6f,cy+y*r*.6f,r*.37f,paint);
    }
    void release(){pointer=-1;x=y=0;listener.move(0,0);invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){int action=e.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN){pointer=e.getPointerId(0);getParent().requestDisallowInterceptTouchEvent(true);}
        int index=e.findPointerIndex(pointer);
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL||(action==MotionEvent.ACTION_POINTER_UP&&e.getPointerId(e.getActionIndex())==pointer)){release();return true;}
        if(index>=0){float r=Math.min(getWidth(),getHeight())*.37f;x=(e.getX(index)-getWidth()/2f)/r;y=(e.getY(index)-getHeight()/2f)/r;float length=(float)Math.hypot(x,y);if(length>1){x/=length;y/=length;}if(length<.12f){x=y=0;}listener.move(x,-y);invalidate();}
        return true;
    }
}
