package com.rbxnative.androidport;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Small vector controls for the Android adapter; no image/texture assets. */
final class StudioIcon extends Drawable {
    private final String kind;
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    StudioIcon(String kind){this.kind=kind;p.setColor(0xffdbe4f0);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.65f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    void line(Canvas c,float...v){for(int i=0;i+3<v.length;i+=4)c.drawLine(v[i],v[i+1],v[i+2],v[i+3],p);}
    @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);
        Path path=new Path();
        switch(kind){
            case "project":path.moveTo(3,6);path.lineTo(10,6);path.lineTo(12,9);path.lineTo(21,9);path.lineTo(21,20);path.lineTo(3,20);path.close();c.drawPath(path,p);line(c,3,10,21,10);break;
            case "insert":line(c,12,4,12,20,4,12,20,12);break;
            case "select":path.moveTo(5,3);path.lineTo(7,21);path.lineTo(12,15);path.lineTo(20,13);path.close();c.drawPath(path,p);break;
            case "move":line(c,12,3,12,21,3,12,21,12,12,3,9,6,12,3,15,6,12,21,9,18,12,21,15,18,3,12,6,9,3,12,6,15,21,12,18,9,21,12,18,15);break;
            case "rotate":c.drawArc(4,4,20,20,-75,290,false,p);line(c,18,3,20,8,20,8,15,8);break;
            case "scale":c.drawRect(4,11,13,20,p);line(c,12,12,21,3,15,3,21,3,21,3,21,9);break;
            case "undo":case "redo":if(kind.equals("redo")){c.translate(24,0);c.scale(-1,1);}path.moveTo(4,9);path.cubicTo(19,4,24,15,17,20);c.drawPath(path,p);line(c,4,9,9,4,4,9,9,14);break;
            case "save":path.moveTo(4,3);path.lineTo(17,3);path.lineTo(21,7);path.lineTo(21,21);path.lineTo(4,21);path.close();c.drawPath(path,p);c.drawRect(8,3,16,9,p);c.drawRect(8,14,17,21,p);break;
            case "more":p.setStyle(Paint.Style.FILL);for(int y=5;y<=19;y+=7)c.drawCircle(12,y,1.5f,p);p.setStyle(Paint.Style.STROKE);break;
            case "explorer":line(c,5,5,5,19,5,12,12,12,5,19,12,19);c.drawRect(3,3,8,7,p);c.drawRect(13,9,20,14,p);c.drawRect(13,17,20,22,p);break;
            case "properties":line(c,4,6,20,6,4,12,20,12,4,18,20,18);c.drawCircle(9,6,2,p);c.drawCircle(16,12,2,p);c.drawCircle(8,18,2,p);break;
            case "script":line(c,8,6,3,12,3,12,8,18,16,6,21,12,21,12,16,18,14,4,10,20);break;
            case "output":c.drawRoundRect(3,4,21,20,2,2,p);line(c,6,8,10,12,10,12,6,16,13,16,17,16);break;
            case "focus":line(c,4,9,4,4,4,4,9,4,15,4,20,4,20,4,20,9,4,15,4,20,4,20,9,20,15,20,20,20,20,20,20,15);c.drawCircle(12,12,3,p);break;
            case "jump":line(c,12,4,12,20,5,11,12,4,12,4,19,11);break;
            case "close":line(c,6,6,18,18,18,6,6,18);break;
        }
        c.restore();}
    @Override public void setAlpha(int a){p.setAlpha(a);}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
