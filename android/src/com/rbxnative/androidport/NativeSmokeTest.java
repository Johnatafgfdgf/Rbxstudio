package com.rbxnative.androidport;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.graphics.Rect;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import org.json.*;

/** Debug instrumentation: exercises JNI, real Surface presentation and I/O. */
public class NativeSmokeTest extends Instrumentation {
    private Method command, snapshot;
    private boolean software;
    @Override public void onCreate(Bundle args) { super.onCreate(args);software=args!=null&&"true".equals(args.getString("software"));start(); }
    View find(View v,String description){if(description.contentEquals(v.getContentDescription()==null?"":v.getContentDescription()))return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View r=find(g.getChildAt(i),description);if(r!=null)return r;}}return null;}
    void pointer(int action,float x,float y,long down){MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0);sendPointerSync(e);e.recycle();}
    double distance(JSONArray a,JSONArray b){double dx=a.optDouble(0)-b.optDouble(0),dz=a.optDouble(2)-b.optDouble(2);return Math.hypot(dx,dz);}
    JSONObject state() throws Exception {JSONObject s=new JSONObject((String)snapshot.invoke(null));if(s.has("fatal"))throw new Exception(s.optString("fatal"));return s;}
    void send(JSONObject value) throws Exception {command.invoke(null,value.toString());}
    JSONObject waitFor(String key,long timeout) throws Exception {
        long deadline=System.currentTimeMillis()+timeout;JSONObject value;
        do { value=state();if(value.has("fatal"))throw new Exception(value.optString("fatal"));if(value.has(key))return value;Thread.sleep(200); } while(System.currentTimeMillis()<deadline);
        throw new Exception("Timeout aguardando "+key+": "+value);
    }
    void waitRevision(long prior) throws Exception {
        long deadline=System.currentTimeMillis()+30000;
        do {if(state().optLong("revision")>prior)return;Thread.sleep(150);}while(System.currentTimeMillis()<deadline);
        throw new Exception("Comando JNI não alterou o documento");
    }
    JSONObject op(String name) throws Exception {return new JSONObject().put("op",name);}
    void requireForeground(String stage) throws Exception {
        long deadline=System.currentTimeMillis()+15000;String active="<sem janela acessível>";
        do {
            AccessibilityNodeInfo window=getUiAutomation().getRootInActiveWindow();
            if(window!=null){active=String.valueOf(window.getPackageName());if("com.rbxnative.androidport".equals(active))return;}
            Thread.sleep(200);
        }while(System.currentTimeMillis()<deadline);
        throw new Exception(stage+": uma janela externa ou ainda indisponível bloqueia a validação; janela ativa="+active);
    }
    @Override public void onStart() {
        Bundle result=new Bundle();Activity activity=null;
        try {
            command=MainActivity.class.getDeclaredMethod("nativeCommand",String.class);command.setAccessible(true);
            snapshot=MainActivity.class.getDeclaredMethod("nativeSnapshot");snapshot.setAccessible(true);
            getTargetContext().getSharedPreferences("MainActivity",0).edit().putInt("quality",software?5:10).putInt("resolution",640).commit();
            Intent intent=new Intent(getTargetContext(),MainActivity.class);intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity=startActivitySync(intent);
            JSONObject first=waitFor("tree",120000);
            long deadline=System.currentTimeMillis()+120000;
            while(state().optLong("presented_frames",0)<=0&&System.currentTimeMillis()<deadline)Thread.sleep(500);
            if(state().optLong("presented_frames",0)<=0)throw new Exception("Nenhum frame apresentado pela GPU");
            final MainActivity app=(MainActivity)activity;
            int builds=state().optInt("surface_builds");
            runOnMainSync(()->app.toggle("explorer"));Thread.sleep(600);
            runOnMainSync(()->app.toggle("properties"));Thread.sleep(600);
            runOnMainSync(()->app.toggle("properties"));Thread.sleep(600);
            if(state().optInt("surface_builds")!=builds)throw new Exception("Abrir painéis recriou o renderer");
            long revision=state().optLong("revision");
            send(op("insert").put("class","Part"));waitRevision(revision);
            long part=state().optLong("selection");if(part==0)throw new Exception("Part não selecionada");
            revision=state().optLong("revision");send(op("edit").put("id",part).put("name","Name").put("text","AndroidValidation"));waitRevision(revision);
            revision=state().optLong("revision");send(op("transform").put("kind","move").put("x",4).put("y",8).put("z",0));waitRevision(revision);
            revision=state().optLong("revision");send(op("transform").put("kind","rotate").put("x",0).put("y",30).put("z",0));waitRevision(revision);
            revision=state().optLong("revision");send(op("transform").put("kind","scale").put("x",2).put("y",2).put("z",2));waitRevision(revision);
            revision=state().optLong("revision");send(op("insert").put("class","Script"));waitRevision(revision);
            long script=state().optLong("selection");String source="-- Android validation\nprint('JNI Luau OK')\n";
            revision=state().optLong("revision");send(op("edit").put("id",script).put("name","Source").put("text",source));waitRevision(revision);
            if(!source.equals(state().optString("source")))throw new Exception("Source alterado indevidamente");
            revision=state().optLong("revision");send(op("run").put("source",source));waitRevision(revision);
            String guiFixture="local g=Instance.new('ScreenGui');g.Name='AndroidGuiFixture';g.IgnoreGuiInset=true;g.Parent=game:GetService('StarterGui');local b=Instance.new('TextButton');b.Name='AndroidClickFixture';b.Position=UDim2.fromOffset(80,100);b.Size=UDim2.fromOffset(160,80);b.Text='Android GUI test';b.BackgroundColor3=Color3.fromRGB(24,118,234);b.Parent=g;local s=Instance.new('LocalScript');s.Source=\"script.Parent.MouseButton1Click:Connect(function() print('Android GUI click OK') end)\";s.Parent=b";
            revision=state().optLong("revision");send(op("run").put("source",guiFixture));waitRevision(revision);
            send(op("save"));Thread.sleep(1000);
            File project=new File(getTargetContext().getFilesDir(),"projects/Main.rbxlx");if(!project.isFile()||project.length()==0)throw new Exception("Projeto não foi salvo");
            revision=state().optLong("revision");send(op("open").put("path",project.getAbsolutePath()));waitRevision(revision);
            boolean found=false;JSONArray tree=state().getJSONArray("tree");for(int i=0;i<tree.length();i++)if(tree.getJSONObject(i).optString("name").equals("AndroidValidation")){found=true;part=tree.getJSONObject(i).optLong("id");}
            if(!found)throw new Exception("Part perdida após reabertura");
            send(op("select").put("id",part));send(op("tool").put("tool","move"));send(op("focus"));Thread.sleep(2000);state();
            send(op("play"));
            long playDeadline=System.currentTimeMillis()+30000;
            while(!state().optBoolean("playing")&&System.currentTimeMillis()<playDeadline)Thread.sleep(200);
            if(!state().optBoolean("playing"))throw new Exception("Rook Play não iniciou");
            long readyPlayer=System.currentTimeMillis()+30000;
            while((state().optJSONObject("player")==null||!state().optJSONObject("player").has("position"))&&System.currentTimeMillis()<readyPlayer)Thread.sleep(200);
            JSONArray from=state().getJSONObject("player").getJSONArray("position");
            if(!"rook-engine::Session".equals(state().getJSONObject("runtime").optString("host")))throw new Exception("Play não usa a sessão original Rook");
            Rect pad=new Rect();long padDeadline=System.currentTimeMillis()+30000;
            do {runOnMainSync(()->{pad.setEmpty();View v=find(app.getWindow().getDecorView(),"Mover personagem: direcional analógico");if(v!=null&&v.isShown()&&v.hasWindowFocus())v.getGlobalVisibleRect(pad);});if(!pad.isEmpty())break;Thread.sleep(200);}while(System.currentTimeMillis()<padDeadline);
            if(pad.isEmpty())throw new Exception("Controle touch não ficou visível no Play");
            waitForIdleSync();requireForeground("Antes do toque físico");
            Rect viewport=new Rect();runOnMainSync(()->{View v=find(app.getWindow().getDecorView(),"Viewport 3D");if(v!=null)v.getGlobalVisibleRect(viewport);});
            if(viewport.isEmpty())throw new Exception("Viewport não está visível");
            JSONArray renderSize=state().getJSONArray("resolution");float gx=viewport.left+160f*viewport.width()/renderSize.getInt(0),gy=viewport.top+140f*viewport.height()/renderSize.getInt(1);
            if(renderSize.getInt(0)>=viewport.width())throw new Exception("O teste precisa exercitar resolução interna menor que a Surface física");
            long click=SystemClock.uptimeMillis();pointer(MotionEvent.ACTION_DOWN,gx,gy,click);pointer(MotionEvent.ACTION_UP,gx,gy,click);
            long guiDeadline=System.currentTimeMillis()+15000;while(!state().getJSONArray("logs").toString().contains("Android GUI click OK")&&System.currentTimeMillis()<guiDeadline)Thread.sleep(200);
            if(!state().getJSONArray("logs").toString().contains("Android GUI click OK"))throw new Exception("Toque físico não ativou o botão GUI do jogo na resolução interna configurada");
            Bitmap playImage=getUiAutomation().takeScreenshot();if(playImage==null)throw new Exception("Captura de Play indisponível");
            try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-play.png"))){playImage.compress(Bitmap.CompressFormat.PNG,100,output);}
            try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-play.json"))){output.write(state().toString(2).getBytes("UTF-8"));}
            Bitmap readable=playImage.copy(Bitmap.Config.ARGB_8888,false);int px=Math.round(viewport.left+92f*viewport.width()/renderSize.getInt(0)),py=Math.round(viewport.top+112f*viewport.height()/renderSize.getInt(1));int pixel=readable.getPixel(px,py);readable.recycle();
            if(Math.abs(android.graphics.Color.red(pixel)-24)>40||Math.abs(android.graphics.Color.green(pixel)-118)>40||Math.abs(android.graphics.Color.blue(pixel)-234)>40)throw new Exception("A GUI recebeu clique, mas o botão não apareceu na posição/cor esperada: "+Integer.toHexString(pixel));
            long touch=SystemClock.uptimeMillis();pointer(MotionEvent.ACTION_DOWN,pad.exactCenterX(),pad.exactCenterY(),touch);pointer(MotionEvent.ACTION_MOVE,pad.exactCenterX(),pad.exactCenterY()-pad.height()*.3f,touch);
            long walkDeadline=System.currentTimeMillis()+30000;
            while(distance(from,state().getJSONObject("player").getJSONArray("position"))<2&&System.currentTimeMillis()<walkDeadline)Thread.sleep(250);
            pointer(MotionEvent.ACTION_UP,pad.exactCenterX(),pad.exactCenterY()-pad.height()*.3f,touch);
            if(distance(from,state().getJSONObject("player").getJSONArray("position"))<2)throw new Exception("Direcional Android não moveu o personagem");
            runOnMainSync(()->{View jump=find(app.getWindow().getDecorView(),"Pular");if(jump!=null)jump.performClick();});
            Thread.sleep(1000);state();send(op("play"));
            playDeadline=System.currentTimeMillis()+30000;
            while(state().optBoolean("playing")&&System.currentTimeMillis()<playDeadline)Thread.sleep(200);
            if(state().optBoolean("playing"))throw new Exception("Play não restaurou o editor");
            waitForIdleSync();requireForeground("Após voltar à edição");
            Bitmap image=getUiAutomation().takeScreenshot();if(image==null)throw new Exception("Screenshot indisponível");
            try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-validation.png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}
            try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-validation.json"))){output.write(state().toString(2).getBytes("UTF-8"));}
            state();result.putString("result","JNI, GPU, panels without renderer reset, edit, Luau, Rook Session, physical GUI click at internal render resolution, real R6 touch movement, Play/Stop and save/reopen passed");finish(Activity.RESULT_OK,result);
        } catch(Throwable error) {
            try {
                Bitmap image=getUiAutomation().takeScreenshot();
                if(image!=null)try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-validation.png"))){image.compress(Bitmap.CompressFormat.PNG,100,output);}
                JSONObject failure=new JSONObject((String)snapshot.invoke(null));failure.put("test_error",error.toString());
                try(FileOutputStream output=new FileOutputStream(new File(getTargetContext().getFilesDir(),"android-validation.json"))){output.write(failure.toString(2).getBytes("UTF-8"));}
            }catch(Throwable capture){error.addSuppressed(capture);}
            result.putString("error",error.toString());finish(Activity.RESULT_CANCELED,result);
        }
    }
}
