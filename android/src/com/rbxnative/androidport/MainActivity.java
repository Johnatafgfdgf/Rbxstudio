package com.rbxnative.androidport;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.Insets;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Android adaptation only. Scene/rendering/editing/file formats live in Rust. */
public class MainActivity extends Activity implements SurfaceHolder.Callback {
    private static native void nativeInit(String files, String cache,String contentApks);
    private static native void nativeSurface(Surface surface, int width, int height);
    private static native void nativeDetach();
    private static native void nativeCommand(String command);
    private static native String nativeSnapshot();

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private JSONObject state = new JSONObject();
    private LinearLayout root, top, treeRows, propertyRows;
    private FrameLayout viewport,work;
    private LinearLayout explorerPanel, propertiesPanel;
    private SurfaceView surface;
    private TextView status, treeTitle, propertiesTitle;
    private Button playButton;
    private LinearLayout bottom;
    private View help;
    private PlayerPad playerPad;
    private Button jumpButton,showUi;
    private boolean playingUi=false;
    private final Set<Long> expanded = new HashSet<>();
    private String panel = "", treeFilter = "", propertyFilter = "", lastTree = "", lastProperties = "";
    private final Map<String, Button> tools = new HashMap<>();
    private boolean ready = false, uiHidden = false, fatalShown = false;
    private float lastX,lastY,downX,downY,pinchDistance,midX,midY;
    private boolean multitouch = false, moved = false;
    private long downTime;
    private Uri pendingExport;
    private File exportFile;
    private TextView consoleView;
    private int bg = Color.rgb(23,27,33), raised = Color.rgb(35,41,50), blue = Color.rgb(37,113,198), text = Color.rgb(219,228,240);

    @Override public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        getWindow().setStatusBarColor(bg); getWindow().setNavigationBarColor(bg);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        try { System.loadLibrary("rbx_android"); ready = true; }
        catch (UnsatisfiedLinkError error) { TextView failure = label("Falha ao carregar o núcleo ARM64:\n"+error,16);setContentView(failure);return; }
        nativeInit(getFilesDir().getAbsolutePath(),getCacheDir().getAbsolutePath(),contentApks());
        android.content.SharedPreferences prefs=getPreferences(MODE_PRIVATE);
        command(obj("op","resolution","value",prefs.getInt("resolution",1280)));
        command(obj("op","quality","value",prefs.getInt("quality",10)));
        command(obj("op","frame_limit","value",prefs.getInt("frame_limit",60)));
        build();
        root.setOnApplyWindowInsetsListener((view,insets)->{safeArea(view,insets,false);return insets;});root.requestApplyInsets();
    }

    String contentApks(){
        JSONArray paths=new JSONArray();paths.put(new File(getFilesDir(),"resources").getAbsolutePath());
        try{ApplicationInfo app=getPackageManager().getApplicationInfo("com.roblox.client",0);if(app.sourceDir!=null)paths.put(app.sourceDir);if(app.splitSourceDirs!=null)for(String path:app.splitSourceDirs)paths.put(path);}catch(PackageManager.NameNotFoundException absent){}
        return paths.toString();
    }
    void safeArea(View view,WindowInsets insets,boolean keyboard){
        int left,right,top,bottom;
        if(Build.VERSION.SDK_INT>=30){int types=WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout();if(keyboard)types|=WindowInsets.Type.ime();Insets safe=insets.getInsets(types);left=safe.left;right=safe.right;top=safe.top;bottom=safe.bottom;}
        else{left=insets.getSystemWindowInsetLeft();right=insets.getSystemWindowInsetRight();top=insets.getSystemWindowInsetTop();bottom=insets.getSystemWindowInsetBottom();DisplayCutout c=insets.getDisplayCutout();if(c!=null){left=Math.max(left,c.getSafeInsetLeft());right=Math.max(right,c.getSafeInsetRight());top=Math.max(top,c.getSafeInsetTop());bottom=Math.max(bottom,c.getSafeInsetBottom());}}
        if(view.getPaddingLeft()!=left||view.getPaddingRight()!=right||view.getPaddingTop()!=top||view.getPaddingBottom()!=bottom)view.setPadding(left,top,right,bottom);
    }
    int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    TextView label(String value, int size) {
        TextView view = new TextView(this); view.setText(value);view.setTextSize(size);view.setTextColor(text);view.setGravity(Gravity.CENTER_VERTICAL);view.setPadding(dp(8),0,dp(8),0);return view;
    }
    Button button(String title, Runnable action) {
        Button b = new Button(this);b.setText(title);b.setAllCaps(false);b.setSingleLine(true);b.setTextSize(12);b.setTextColor(text);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(10),0,dp(10),0);b.setBackground(buttonBackground());b.setOnClickListener(v->action.run());b.setContentDescription(title);b.setTooltipText(title);return b;
    }
    GradientDrawable shape(int color,int border,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));if(border!=0)d.setStroke(dp(1),border);return d;}
    Drawable buttonBackground(){StateListDrawable d=new StateListDrawable();d.addState(new int[]{android.R.attr.state_pressed},shape(0xff35516e,0,5));d.addState(new int[]{android.R.attr.state_selected},shape(blue,0xff69b2ff,5));d.addState(new int[]{},shape(Color.TRANSPARENT,0,5));return d;}
    Button icon(String kind,String title,Runnable action){Button b=button("",action);b.setContentDescription(title);b.setTooltipText(title);StudioIcon image=new StudioIcon(kind);image.setBounds(0,0,dp(22),dp(22));b.setCompoundDrawables(image,null,null,null);b.setPadding(dp(10),0,dp(10),0);b.setGravity(Gravity.CENTER);return b;}
    void toolbarIcon(LinearLayout bar,String kind,String title,Runnable action){bar.addView(icon(kind,title,action),new LinearLayout.LayoutParams(dp(42),-1));}
    void divider(LinearLayout bar){View v=new View(this);v.setBackgroundColor(0xff414b58);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(1),dp(22));p.setMargins(dp(5),0,dp(5),0);bar.addView(v,p);}
    void addButton(LinearLayout bar,String title,Runnable action) {bar.addView(button(title,action),new LinearLayout.LayoutParams(-2,-1));}
    JSONObject obj(Object... values) {
        JSONObject j = new JSONObject();try { for(int i=0;i<values.length;i+=2)j.put((String)values[i],values[i+1]); }catch(JSONException e){error(e.toString());}return j;
    }
    void command(JSONObject c) { if(ready)nativeCommand(c.toString()); }
    void op(String name) {command(obj("op",name));}
    void error(String error) {new AlertDialog.Builder(this).setTitle("rbx-native").setMessage(error).setPositiveButton("OK",null).show();}

    void build() {
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(bg);setContentView(root);
        top=new LinearLayout(this);top.setGravity(Gravity.CENTER_VERTICAL);top.setBackgroundColor(raised);
        top.setPadding(dp(8),dp(3),dp(8),dp(3));
        HorizontalScrollView ribbon=new HorizontalScrollView(this);ribbon.setFillViewport(true);ribbon.setHorizontalScrollBarEnabled(false);ribbon.addView(top,new FrameLayout.LayoutParams(-1,-1));root.addView(ribbon,new LinearLayout.LayoutParams(-1,dp(48)));
        Button project=button("Projeto ▾",this::projectsMenu);project.setTypeface(null,Typeface.BOLD);top.addView(project,new LinearLayout.LayoutParams(dp(96),-1));
        toolbarIcon(top,"insert","Inserir objeto",this::insertMenu);divider(top);
        String[][] actions={{"select","Selecionar"},{"move","Mover"},{"rotate","Girar"},{"scale","Escalar"}};
        for(String[] action:actions) {Button b=icon(action[0],action[1],()->command(obj("op","tool","tool",action[0])));tools.put(action[0],b);top.addView(b,new LinearLayout.LayoutParams(dp(42),-1));}
        divider(top);toolbarIcon(top,"undo","Desfazer",()->op("undo"));toolbarIcon(top,"redo","Refazer",()->op("redo"));
        top.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));
        playButton=button("▶ Testar",()->op("play"));playButton.setTextColor(0xff83e4a3);top.addView(playButton,new LinearLayout.LayoutParams(dp(88),-1));
        toolbarIcon(top,"save","Salvar projeto",()->op("save"));toolbarIcon(top,"more","Mais ferramentas",this::moreMenu);
        work=new FrameLayout(this);work.setMotionEventSplittingEnabled(true);root.addView(work,new LinearLayout.LayoutParams(-1,0,1));
        viewport=new FrameLayout(this);viewport.setMotionEventSplittingEnabled(true);viewport.setBackgroundColor(Color.BLACK);work.addView(viewport,new FrameLayout.LayoutParams(-1,-1));
        surface=new SurfaceView(this);surface.getHolder().setFormat(android.graphics.PixelFormat.RGBA_8888);surface.getHolder().addCallback(this);viewport.addView(surface,new FrameLayout.LayoutParams(-1,-1));surface.setFocusable(true);surface.setFocusableInTouchMode(true);
        surface.setContentDescription("Viewport 3D");surface.setOnTouchListener((v,event)->touch(event));
        surface.setOnGenericMotionListener((v,event)-> { if(event.getAction()==MotionEvent.ACTION_SCROLL){command(obj("op","camera","kind","zoom","value",event.getAxisValue(MotionEvent.AXIS_VSCROLL)));return true;}return false; });
        TextView hint=label("1 dedo: orbitar    2 dedos: deslocar    Pinça: zoom",10);hint.setTextColor(0xffd3dfef);hint.setBackground(shape(0x99202731,0,5));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-2,dp(26),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);hp.bottomMargin=dp(10);viewport.addView(hint,hp);help=hint;
        playerPad=new PlayerPad(this,(x,y)->command(obj("op","player","x",x,"y",y)));FrameLayout.LayoutParams pad=new FrameLayout.LayoutParams(dp(144),dp(144),Gravity.BOTTOM|Gravity.LEFT);pad.setMargins(dp(12),0,0,dp(8));viewport.addView(playerPad,pad);playerPad.setVisibility(View.GONE);
        jumpButton=icon("jump","Pular",()->op("jump"));jumpButton.setBackground(shape(0xaa26384c,0xff8095b0,36));FrameLayout.LayoutParams jp=new FrameLayout.LayoutParams(dp(64),dp(64),Gravity.BOTTOM|Gravity.RIGHT);jp.setMargins(0,0,dp(28),dp(32));viewport.addView(jumpButton,jp);jumpButton.setVisibility(View.GONE);
        explorerPanel=makeExplorer();FrameLayout.LayoutParams ep=new FrameLayout.LayoutParams(dp(245),-1,Gravity.LEFT);work.addView(explorerPanel,ep);explorerPanel.setVisibility(View.GONE);explorerPanel.setElevation(dp(12));
        propertiesPanel=makeProperties();FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(dp(300),-1,Gravity.RIGHT);work.addView(propertiesPanel,pp);propertiesPanel.setVisibility(View.GONE);propertiesPanel.setElevation(dp(12));
        bottom=new LinearLayout(this);bottom.setGravity(Gravity.CENTER_VERTICAL);bottom.setBackgroundColor(raised);bottom.setPadding(dp(6),0,dp(6),0);root.addView(bottom,new LinearLayout.LayoutParams(-1,dp(38)));
        addButton(bottom,"Explorer",()->toggle("explorer"));addButton(bottom,"Properties",()->toggle("properties"));addButton(bottom,"Scripts",this::scripts);addButton(bottom,"Output",this::console);toolbarIcon(bottom,"focus","Focar seleção",()->op("focus"));
        status=label("Carregando núcleo Rust…",10);status.setSingleLine(true);status.setEllipsize(TextUtils.TruncateAt.END);bottom.addView(status,new LinearLayout.LayoutParams(0,-1,1));
    }

    LinearLayout side(String title) {
        LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setBackground(shape(0xf2171c24,0xff3c4655,0));p.setClickable(true);
        LinearLayout bar=new LinearLayout(this);bar.setGravity(Gravity.CENTER_VERTICAL);TextView t=label(title,12);t.setTypeface(null,Typeface.BOLD);t.setTooltipText("Arraste o título para ajustar a largura");
        t.setOnTouchListener(new View.OnTouchListener(){float start;int width;public boolean onTouch(View v,MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_DOWN){start=e.getRawX();width=p.getWidth();return true;}if(e.getActionMasked()==MotionEvent.ACTION_MOVE){ViewGroup.LayoutParams lp=p.getLayoutParams();int delta=Math.round(e.getRawX()-start);lp.width=Math.max(dp(205),Math.min(work.getWidth()*2/3,width+(title.equals("EXPLORER")?delta:-delta)));p.setLayoutParams(lp);return true;}return true;}});
        bar.addView(t,new LinearLayout.LayoutParams(0,-1,1));bar.addView(icon("close","Fechar painel",()->toggle(title.equals("EXPLORER")?"explorer":"properties")),new LinearLayout.LayoutParams(dp(40),-1));p.addView(bar,new LinearLayout.LayoutParams(-1,dp(40)));return p;
    }
    EditText search(String hint,Runnable changed) {
        EditText filter=new EditText(this);filter.setSingleLine();filter.setTextSize(12);filter.setTextColor(text);filter.setHintTextColor(Color.GRAY);filter.setHint(hint);filter.setPadding(dp(10),0,dp(8),0);
        filter.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){if(hint.equals("Pesquisar Instances"))treeFilter=s.toString();else propertyFilter=s.toString();changed.run();}public void afterTextChanged(Editable e){}});return filter;
    }
    LinearLayout makeExplorer() {
        LinearLayout p=side("EXPLORER");p.addView(search("Pesquisar Instances",this::renderTree),new LinearLayout.LayoutParams(-1,dp(38)));
        ScrollView scroll=new ScrollView(this);treeRows=new LinearLayout(this);treeRows.setOrientation(1);scroll.addView(treeRows);p.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return p;
    }
    LinearLayout makeProperties() {
        LinearLayout p=side("PROPERTIES");p.addView(search("Filtrar propriedades",this::renderProperties),new LinearLayout.LayoutParams(-1,dp(38)));
        LinearLayout quick=new LinearLayout(this);addButton(quick,"Transformar",this::transformDialog);addButton(quick,"Local / Global",()->op("local"));p.addView(quick,new LinearLayout.LayoutParams(-1,dp(36)));
        ScrollView scroll=new ScrollView(this);propertyRows=new LinearLayout(this);propertyRows.setOrientation(1);scroll.addView(propertyRows);p.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));return p;
    }
    void toggle(String name) {
        panel=panel.equals(name)?"":name;
        explorerPanel.setVisibility(panel.equals("explorer")?View.VISIBLE:View.GONE);propertiesPanel.setVisibility(panel.equals("properties")?View.VISIBLE:View.GONE);renderTree();renderProperties();
    }
    void renderTree() {
        if(treeRows==null||explorerPanel==null||explorerPanel.getVisibility()!=View.VISIBLE)return;treeRows.removeAllViews();JSONArray items=state.optJSONArray("tree");if(items==null)return;
        int hidden=-1;String filter=treeFilter.toLowerCase(Locale.ROOT);
        for(int i=0;i<items.length();i++) {
            JSONObject item=items.optJSONObject(i);if(item==null)continue;
            long id=item.optLong("id");int depth=item.optInt("depth");String name=item.optString("name"),clazz=item.optString("class");
            if(depth==0&&clazz.equals("Workspace")&&expanded.isEmpty())expanded.add(id);
            if(filter.isEmpty()) {if(hidden>=0&&depth>hidden)continue;hidden=-1;}
            else if(!(name+" "+clazz).toLowerCase(Locale.ROOT).contains(filter))continue;
            boolean children=i+1<items.length()&&items.optJSONObject(i+1).optInt("depth")>depth;
            LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(Math.min(depth,10)*12),0,0,0);row.setBackgroundColor(item.optBoolean("selected")?blue:Color.TRANSPARENT);
            Button arrow=button(children?(expanded.contains(id)?"▾":"▸"):"·",()->{if(expanded.contains(id))expanded.remove(id);else expanded.add(id);renderTree();});row.addView(arrow,new LinearLayout.LayoutParams(dp(28),-1));
            TextView title=label(name,12);row.addView(title,new LinearLayout.LayoutParams(0,-1,1));title.setOnClickListener(v->{command(obj("op","select","id",id));if(item.optBoolean("script"))openScript(item);});
            title.setOnLongClickListener(v->{command(obj("op","select","id",id));objectMenu(item);return true;});row.setContentDescription("Instance "+name);treeRows.addView(row,new LinearLayout.LayoutParams(-1,dp(36)));
            if(children&&!expanded.contains(id)&&filter.isEmpty())hidden=depth;
        }
    }
    void renderProperties() {
        if(propertyRows==null||propertiesPanel==null||propertiesPanel.getVisibility()!=View.VISIBLE)return;propertyRows.removeAllViews();JSONArray rows=state.optJSONArray("properties");
        if(rows==null||rows.length()==0){propertyRows.addView(label("Selecione uma Instance",12));return;}
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);if(row==null)continue;String name=row.optString("name"),value=row.optString("value");
            if(!name.toLowerCase(Locale.ROOT).contains(propertyFilter.toLowerCase(Locale.ROOT)))continue;
            LinearLayout line=new LinearLayout(this);line.setGravity(Gravity.CENTER_VERTICAL);line.setPadding(0,dp(3),0,dp(3));
            TextView key=label(name,11);line.addView(key,new LinearLayout.LayoutParams(dp(112),-2));TextView val=label(value,11);val.setMaxLines(2);line.addView(val,new LinearLayout.LayoutParams(0,-2,1));
            if(row.optBoolean("editable")) {val.setTextColor(Color.rgb(120,190,255));line.setOnClickListener(v->editProperty(name,row.optString("text",value)));}else val.setTextColor(Color.GRAY);
            propertyRows.addView(line,new LinearLayout.LayoutParams(-1,dp(42)));
        }
    }
    void editProperty(String name,String current) {
        EditText input=new EditText(this);input.setText(current);input.setTextColor(text);input.setSingleLine();
        new AlertDialog.Builder(this).setTitle(name).setView(input).setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",(d,w)->command(obj("op","edit","name",name,"text",input.getText().toString()))).show();
    }
    void transformDialog() {
        String[] kinds={"move","rotate","scale"},labels={"Mover em studs","Girar em graus","Alterar tamanho em studs"};
        new AlertDialog.Builder(this).setTitle("Transformação relativa").setItems(labels,(d,which)->{
            LinearLayout fields=new LinearLayout(this);String[] axes={"X","Y","Z"};EditText[] values=new EditText[3];
            for(int i=0;i<3;i++){values[i]=new EditText(this);values[i].setHint(axes[i]);values[i].setText("0");values[i].setInputType(8194|4096);fields.addView(values[i],new LinearLayout.LayoutParams(0,dp(50),1));}
            new AlertDialog.Builder(this).setTitle(labels[which]).setView(fields).setNegativeButton("Cancelar",null).setPositiveButton("Aplicar",(dialog,w)->{try {command(obj("op","transform","kind",kinds[which],"x",Double.parseDouble(values[0].getText().toString()),"y",Double.parseDouble(values[1].getText().toString()),"z",Double.parseDouble(values[2].getText().toString())));}catch(NumberFormatException e){error("Digite números válidos");}}).show();
        }).show();
    }
    void insertMenu() {
        String[] items={"Part","WedgePart","CornerWedgePart","Model","Folder","Script","LocalScript","ModuleScript","PointLight","SpotLight"};
        new AlertDialog.Builder(this).setTitle("Inserir Instance").setItems(items,(d,w)->command(obj("op","insert","class",items[w]))).show();
    }
    void objectMenu(JSONObject item) {
        String[] choices={"Properties","Renomear","Duplicar","Agrupar em Model","Excluir"};
        new AlertDialog.Builder(this).setTitle(item.optString("name")).setItems(choices,(d,w)->{switch(w){case 0:toggle("properties");break;case 1:editProperty("Name",item.optString("name"));break;case 2:op("duplicate");break;case 3:op("group");break;case 4:op("delete");break;}}).show();
    }
    void moreMenu() {
        String[] choices={"Explorer","Properties","Scripts","Output","Focar seleção","Transformação numérica","Duplicar","Agrupar","Excluir","Snap","Ocultar interface","Licenças e compatibilidade","Gráficos e desempenho","Diagnóstico desta sessão","Recursos locais","Animações importadas"};
        new AlertDialog.Builder(this).setItems(choices,(d,w)->{switch(w){case 0:toggle("explorer");break;case 1:toggle("properties");break;case 2:scripts();break;case 3:console();break;case 4:op("focus");break;case 5:transformDialog();break;case 6:op("duplicate");break;case 7:op("group");break;case 8:op("delete");break;case 9:snap();break;case 10:hideUi();break;case 11:licenses();break;case 12:graphicsQuality();break;case 13:diagnostics();break;case 14:resources();break;case 15:animations();break;}}).show();
    }
    void resources(){
        File dir=new File(getFilesDir(),"resources");String[] packs=dir.list((d,n)->n.toLowerCase(Locale.ROOT).endsWith(".apk")||n.toLowerCase(Locale.ROOT).endsWith(".zip"));
        String installed="não encontrado";try{if(new JSONArray(contentApks()).length()>1)installed="encontrado";}catch(JSONException e){}
        new AlertDialog.Builder(this).setTitle("Recursos locais").setMessage("Roblox instalado: "+installed+"\nPacotes locais: "+(packs==null?0:packs.length)+"\n\nCarrega os recursos disponíveis na instalação ou em um pacote ao qual você tem acesso. Assets privados continuam exigindo autenticação oficial.").setPositiveButton("Importar APK/ZIP",(d,w)->pickFile(14)).setNeutralButton("Recarregar",(d,w)->op("content_reload")).setNegativeButton("Fechar",null).show();
    }
    void animations(){
        if(!state.optBoolean("playing")){error("Inicie o Play após importar uma KeyframeSequence compatível com o rig.");return;}
        JSONArray tree=state.optJSONArray("tree");List<JSONObject> items=new ArrayList<>();List<String> names=new ArrayList<>();
        if(tree!=null)for(int i=0;i<tree.length();i++){JSONObject item=tree.optJSONObject(i);if(item!=null&&"KeyframeSequence".equals(item.optString("class"))){items.add(item);names.add(item.optString("name"));}}
        names.add("Parar animação");new AlertDialog.Builder(this).setTitle("KeyframeSequences · rig atual").setItems(names.toArray(new String[0]),(d,w)->{if(w==items.size())op("animation_stop");else command(obj("op","animation_play","id",items.get(w).optLong("id")));}).setNegativeButton("Fechar",null).show();
    }
    void setting(String name,int value){getPreferences(MODE_PRIVATE).edit().putInt(name,value).apply();command(obj("op",name,"value",value));}
    void graphicsQuality() {new AlertDialog.Builder(this).setTitle("Gráficos e desempenho").setItems(new String[]{"Qualidade de materiais e efeitos","Resolução do viewport","Limite de frames"},(d,w)->{
        if(w==0)new AlertDialog.Builder(this).setTitle("Qualidade original do rbx-native").setItems(new String[]{"5 · Leve, sombras 1024","8 · Materiais e reflexos","10 · Padrão","16 · Alta, MSAA","21 · Máxima"},(a,b)->setting("quality",new int[]{5,8,10,16,21}[b])).show();
        if(w==1)new AlertDialog.Builder(this).setTitle("Maior lado do viewport · UI mantém nitidez").setItems(new String[]{"960 px · Leve","1280 px · Padrão mobile","1600 px · Alta","Resolução nativa"},(a,b)->setting("resolution",new int[]{960,1280,1600,0}[b])).show();
        if(w==2)new AlertDialog.Builder(this).setTitle("Limite de frames").setItems(new String[]{"30 FPS · Menor consumo","60 FPS"},(a,b)->setting("frame_limit",new int[]{30,60}[b])).show();
    }).show();}
    void diagnostics(){String info="Build 0.3 · rbx-native + Rook Session\n\n"+Build.MANUFACTURER+" "+Build.MODEL+" · Android "+Build.VERSION.RELEASE+"\n"+state.optString("resolution")+" px · qualidade "+state.optInt("quality")+"\n"+String.format(Locale.ROOT,"%.1f FPS · %.1f ms no envio/renderização CPU",state.optDouble("fps"),state.optDouble("render_ms"))+"\nRenderizadores criados: "+state.optInt("surface_builds")+"\nLimite: "+state.optInt("frame_limit")+" FPS\nEstado: "+state.optString("status")+"\nPlayer: "+state.optString("player")+"\nRuntime: "+state.optString("runtime")+"\n\n"+logs();TextView t=label(info,12);t.setTextIsSelectable(true);t.setPadding(dp(16),dp(12),dp(16),dp(12));ScrollView scroll=new ScrollView(this);scroll.addView(t);new AlertDialog.Builder(this).setTitle("Diagnóstico · toque longo para copiar").setView(scroll).setPositiveButton("Fechar",null).show();}
    void snap() {new AlertDialog.Builder(this).setTitle("Snap em studs").setItems(new String[]{"Desligado","0.25","0.5","1","2","4"},(d,w)->command(obj("op","snap","value",new double[]{0,.25,.5,1,2,4}[w]))).show();}
    void hideUi() {
        uiHidden=!uiHidden;root.getChildAt(0).setVisibility(uiHidden?View.GONE:View.VISIBLE);bottom.setVisibility(uiHidden?View.GONE:View.VISIBLE);explorerPanel.setVisibility(View.GONE);propertiesPanel.setVisibility(View.GONE);panel="";
        if(uiHidden){showUi=button("Mostrar UI",this::hideUi);showUi.setBackground(shape(0xcc202b39,0xff53677f,5));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(96),dp(40),Gravity.TOP|Gravity.RIGHT);p.setMargins(0,dp(8),dp(8),0);viewport.addView(showUi,p);}else if(showUi!=null){viewport.removeView(showUi);showUi=null;}
    }

    void projectsMenu() {
        String[] choices={"Novo projeto","Abrir projeto local","Abrir arquivo RBXL / RBXLX","Importar modelo RBXM / RBXMX","Salvar","Exportar RBXLX","Exportar RBXL"};
        new AlertDialog.Builder(this).setTitle("Projeto").setItems(choices,(d,w)->{switch(w){case 0:newProject();break;case 1:openProject();break;case 2:pickFile(11);break;case 3:pickFile(13);break;case 4:op("save");break;case 5:export("rbxlx");break;case 6:export("rbxl");break;}}).show();
    }
    void pickFile(int request){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("*/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,request);}
    void newProject() {
        EditText name=new EditText(this);name.setSingleLine();name.setHint("Nome do projeto");
        new AlertDialog.Builder(this).setTitle("Novo projeto nativo").setView(name).setNegativeButton("Cancelar",null).setPositiveButton("Baseplate",(d,w)->{
            String safe=name.getText().toString().trim().replaceAll("[^\\p{L}\\p{N}_ -]","_");if(safe.isEmpty())safe="Projeto-"+System.currentTimeMillis();
            command(obj("op","project_new","path",new File(getFilesDir(),"projects/"+safe+".rbxlx").getAbsolutePath()));
        }).show();
    }
    void openProject() {
        File[] files=new File(getFilesDir(),"projects").listFiles((dir,name)->name.endsWith(".rbxlx")||name.endsWith(".rbxl"));
        if(files==null||files.length==0){error("Nenhum projeto salvo");return;}Arrays.sort(files,Comparator.comparing(File::getName));String[] names=Arrays.stream(files).map(File::getName).toArray(String[]::new);
        new AlertDialog.Builder(this).setTitle("Projetos locais").setItems(names,(d,w)->command(obj("op","project_open","path",files[w].getAbsolutePath()))).show();
    }
    void export(String extension) {
        exportFile=new File(getCacheDir(),"export."+extension);if(exportFile.exists()&&!exportFile.delete()){error("Não foi possível preparar a exportação");return;}command(obj("op","export","path",exportFile.getAbsolutePath()));
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("application/octet-stream");intent.putExtra(Intent.EXTRA_TITLE,"Project."+extension);startActivityForResult(intent,12);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||data.getData()==null)return;Uri uri=data.getData();
        if(request==14)io.execute(()->{
            String name="content.zip";
            try(android.database.Cursor cursor=getContentResolver().query(uri,new String[]{android.provider.OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}catch(Exception e){handler.post(()->error(e.toString()));return;}
            File pending=new File(getCacheDir(),"resource-import-"+System.nanoTime()+".tmp");
            try{try(InputStream input=getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(pending)){copy(input,output,1024L*1024*1024);}command(obj("op","content_import","path",pending.getAbsolutePath(),"name",name));}catch(Exception e){pending.delete();handler.post(()->error(e.toString()));}
        });
        if(request==11||request==13)io.execute(()->{
            File file=new File(getFilesDir(),"import-"+System.currentTimeMillis()+".rbxl");
            try(InputStream input=getContentResolver().openInputStream(uri);OutputStream output=new FileOutputStream(file)){copy(input,output);command(obj("op",request==13?"import_model":"open","path",file.getAbsolutePath()));}
            catch(Exception error){handler.post(()->error(error.toString()));}
        });
        if(request==12){File file=exportFile;io.execute(()->{try {long deadline=SystemClock.uptimeMillis()+10000;while(!file.exists()&&SystemClock.uptimeMillis()<deadline)Thread.sleep(100);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}try(InputStream input=new FileInputStream(file);OutputStream output=getContentResolver().openOutputStream(uri)){copy(input,output);handler.post(()->Toast.makeText(this,"Projeto exportado",Toast.LENGTH_SHORT).show());}catch(Exception error){handler.post(()->error(error.toString()));}});}
    }
    void copy(InputStream input,OutputStream output)throws IOException{copy(input,output,256L*1024*1024);}
    void copy(InputStream input,OutputStream output,long maximum)throws IOException{if(input==null||output==null)throw new IOException("Documento indisponível");byte[] buffer=new byte[65536];long count=0;int n;while((n=input.read(buffer))!=-1){count+=n;if(count>maximum)throw new IOException("Arquivo excede "+(maximum/(1024*1024))+" MB");output.write(buffer,0,n);}}

    void scripts() {
        JSONArray items=state.optJSONArray("tree");List<JSONObject> scripts=new ArrayList<>();if(items!=null)for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&item.optBoolean("script"))scripts.add(item);}
        List<String> names=new ArrayList<>();for(JSONObject s:scripts)names.add(s.optString("name")+" · "+s.optString("class"));names.add("＋ Criar Script");
        new AlertDialog.Builder(this).setTitle("Scripts Luau").setItems(names.toArray(new String[0]),(d,w)->{if(w==scripts.size()){command(obj("op","insert","class","Script"));handler.postDelayed(()->scripts(),500);}else {JSONObject s=scripts.get(w);command(obj("op","select","id",s.optLong("id")));handler.postDelayed(()->openScript(s),150);}}).show();
    }
    void openScript(JSONObject item) {
        openScriptReady(item,0);
    }
    void openScriptReady(JSONObject item,int attempt) {
        if(state.optLong("selection",-1)!=item.optLong("id")) {
            if(attempt>=12){error("O núcleo ainda não confirmou a seleção do script");return;}
            command(obj("op","select","id",item.optLong("id")));
            handler.postDelayed(()->openScriptReady(item,attempt+1),250);return;
        }
        final long id=item.optLong("id");Dialog dialog=new Dialog(this);LinearLayout body=new LinearLayout(this);body.setOrientation(1);body.setBackgroundColor(bg);
        LinearLayout bar=new LinearLayout(this);body.addView(bar,new LinearLayout.LayoutParams(-1,dp(44)));bar.addView(label(item.optString("name")+" · Luau",13),new LinearLayout.LayoutParams(0,-1,1));
        EditText code=new EditText(this);code.setGravity(Gravity.TOP|Gravity.LEFT);code.setTextColor(text);code.setTextSize(13);code.setTypeface(Typeface.MONOSPACE);code.setSingleLine(false);code.setHorizontallyScrolling(true);code.setPadding(dp(12),dp(8),dp(12),dp(8));code.setInputType(1|131072|524288);code.setText(state.optString("source",""));
        TextView diagnostic=label("Compilador Luau original · API parcial documentada",11);
        Runnable save=()->command(obj("op","edit","id",id,"name","Source","text",code.getText().toString()));
        addButton(bar,"Aplicar",save);addButton(bar,"Verificar",()->{save.run();command(obj("op","check","source",code.getText().toString()));diagnostic.setText("Resultado no Output");});
        addButton(bar,"Executar",()->{save.run();command(obj("op","run","source",code.getText().toString()));console();});addButton(bar,"Fechar",()->{save.run();dialog.dismiss();});
        body.addView(code,new LinearLayout.LayoutParams(-1,0,1));body.addView(diagnostic,new LinearLayout.LayoutParams(-1,dp(28)));
        LinearLayout keys=new LinearLayout(this);for(String key:new String[]{"    ","(",")","[","]","{","}","=","\"","'",":","end"})addButton(keys,key.equals("    ")?"Tab":key,()->{int start=Math.max(0,code.getSelectionStart()),end=Math.max(0,code.getSelectionEnd());code.getText().replace(Math.min(start,end),Math.max(start,end),key);});
        HorizontalScrollView keyboard=new HorizontalScrollView(this);keyboard.addView(keys);body.addView(keyboard,new LinearLayout.LayoutParams(-1,dp(40)));
        code.addTextChangedListener(new TextWatcher(){boolean painting=false;Runnable pending;public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable e){if(painting)return;painting=true;highlight(e);painting=false;if(pending!=null)handler.removeCallbacks(pending);pending=save;handler.postDelayed(pending,700);}});
        highlight(code.getText());body.setOnApplyWindowInsetsListener((v,insets)->{safeArea(v,insets,true);return insets;});dialog.setContentView(body);dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);dialog.setOnDismissListener(d->{save.run();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(code.getWindowToken(),0);});dialog.show();dialog.getWindow().setLayout(-1,-1);
    }
    void highlight(Editable code) {
        if(code.length()>150000)return;
        for(ForegroundColorSpan span:code.getSpans(0,code.length(),ForegroundColorSpan.class))code.removeSpan(span);
        String source=code.toString();Pattern token=Pattern.compile("--[^\\n]*|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|\\b(?:local|function|end|if|then|else|elseif|for|while|do|return|true|false|nil|and|or|not|repeat|until|in|break|continue|type|export)\\b|\\b[0-9]+(?:\\.[0-9]+)?\\b");Matcher m=token.matcher(source);
        while(m.find()){String t=m.group();int color=t.startsWith("--")?Color.rgb(110,160,100):t.startsWith("\"")||t.startsWith("'")?Color.rgb(205,160,120):Character.isDigit(t.charAt(0))?Color.rgb(180,205,160):Color.rgb(205,145,220);code.setSpan(new ForegroundColorSpan(color),m.start(),m.end(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
    }
    void console() {
        consoleView=label(logs(),12);consoleView.setTypeface(Typeface.MONOSPACE);consoleView.setTextIsSelectable(true);consoleView.setPadding(dp(14),dp(10),dp(14),dp(10));ScrollView scroll=new ScrollView(this);scroll.addView(consoleView);
        new AlertDialog.Builder(this).setTitle("Output · Luau / renderer / Rook").setView(scroll).setPositiveButton("Fechar",(d,w)->consoleView=null).show();
    }
    String logs() {JSONArray a=state.optJSONArray("logs");StringBuilder b=new StringBuilder();if(a!=null)for(int i=0;i<a.length();i++)b.append(a.optString(i)).append('\n');return b.length()==0?"Nenhuma mensagem":b.toString();}
    void licenses() {try(InputStream input=getAssets().open("THIRD_PARTY_NOTICES.txt")){ByteArrayOutputStream out=new ByteArrayOutputStream();copy(input,out);TextView t=label(out.toString("UTF-8"),11);t.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(t);new AlertDialog.Builder(this).setTitle("Código original e limites do port").setView(scroll).setPositiveButton("Fechar",null).show();}catch(Exception e){error(e.toString());}}

    boolean touch(MotionEvent event) {
        int action=event.getActionMasked();float x=event.getX(),y=event.getY();
        if(action==MotionEvent.ACTION_DOWN){surface.requestFocus();lastX=downX=x;lastY=downY=y;downTime=SystemClock.uptimeMillis();moved=false;multitouch=false;command(obj("op","touch","phase","down","x",x,"y",y));return true;}
        if(event.getPointerCount()>=2){float dx=event.getX(1)-x,dy=event.getY(1)-y,dist=(float)Math.hypot(dx,dy),mx=(event.getX(1)+x)/2,my=(event.getY(1)+y)/2;
            if(!multitouch||action==MotionEvent.ACTION_POINTER_DOWN){multitouch=true;pinchDistance=dist;midX=mx;midY=my;command(obj("op","touch","phase","up"));}
            else if(action==MotionEvent.ACTION_MOVE){if(pinchDistance>=1&&dist>=1)command(obj("op","camera","kind","zoom","value",Math.log(dist/pinchDistance)*3));command(obj("op","camera","kind","pan","dx",mx-midX,"dy",my-midY));pinchDistance=dist;midX=mx;midY=my;}
            return true;
        }
        if(action==MotionEvent.ACTION_MOVE&&!multitouch){if(Math.hypot(x-downX,y-downY)>dp(4))moved=true;command(obj("op","touch","phase","move","x",x,"y",y,"dx",x-lastX,"dy",y-lastY));lastX=x;lastY=y;return true;}
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){if(action==MotionEvent.ACTION_UP&&!moved&&!multitouch)command(obj("op","touch","phase","tap","x",x,"y",y));command(obj("op","touch","phase","up","x",x,"y",y));command(obj("op","camera","kind","release"));return true;}
        return true;
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if(getCurrentFocus() instanceof EditText)return super.dispatchKeyEvent(event);
        boolean down=event.getAction()==KeyEvent.ACTION_DOWN;
        if(event.isCtrlPressed()&&down){if(event.getKeyCode()==KeyEvent.KEYCODE_Z){op(event.isShiftPressed()?"redo":"undo");return true;}if(event.getKeyCode()==KeyEvent.KEYCODE_S){op("save");return true;}}
        String key=KeyEvent.keyCodeToString(event.getKeyCode()).replace("KEYCODE_","").toLowerCase(Locale.ROOT);
        if(state.optBoolean("playing")){
            if(Arrays.asList("w","a","s","d").contains(key)){command(obj("op","player_key","key",key,"down",down));return true;}
            if(event.getKeyCode()==KeyEvent.KEYCODE_SPACE){if(down&&event.getRepeatCount()==0)op("jump");return true;}
        }
        if(Arrays.asList("w","a","s","d","q","e").contains(key)){command(obj("op","camera","kind","key","key",key,"down",down));return true;}
        if(down&&event.getKeyCode()==KeyEvent.KEYCODE_F){op("focus");return true;}
        if(down&&event.getKeyCode()==KeyEvent.KEYCODE_DEL){op("delete");return true;}
        return super.dispatchKeyEvent(event);
    }
    private final Runnable poll=new Runnable(){public void run(){if(!ready)return;try{
        JSONObject next=new JSONObject(nativeSnapshot());state=next;
        if(state.has("fatal")&&!fatalShown){fatalShown=true;error(state.optString("fatal"));}
        String message=(state.optBoolean("idle")?"Em repouso":String.format(Locale.ROOT,"%.0f FPS",state.optDouble("fps",0)))+" · "+state.optString("status");
        if(!message.contentEquals(status.getText()))status.setText(message);
        boolean playing=state.optBoolean("playing");String caption=playing?"■ Parar":"▶ Testar";if(!caption.contentEquals(playButton.getText()))playButton.setText(caption);
        if(playing!=playingUi){playingUi=playing;playerPad.setVisibility(playing?View.VISIBLE:View.GONE);jumpButton.setVisibility(playing?View.VISIBLE:View.GONE);help.setVisibility(playing?View.GONE:View.VISIBLE);if(playing){panel="";explorerPanel.setVisibility(View.GONE);propertiesPanel.setVisibility(View.GONE);}else playerPad.release();}
        for(Map.Entry<String,Button> entry:tools.entrySet()){Button b=entry.getValue();boolean selected=state.optString("tool").equals(entry.getKey());if(b.isSelected()!=selected)b.setSelected(selected);if(b.isEnabled()==playing)b.setEnabled(!playing);b.setAlpha(playing?.35f:1f);}
        if(panel.equals("explorer")){String t=String.valueOf(state.optJSONArray("tree"));if(!lastTree.equals(t)){lastTree=t;renderTree();}}
        if(panel.equals("properties")){String p=String.valueOf(state.optJSONArray("properties"));if(!lastProperties.equals(p)){lastProperties=p;renderProperties();}}
        if(consoleView!=null)consoleView.setText(logs());
    }catch(Exception e){status.setText("Estado nativo: "+e);}handler.postDelayed(this,300);}};
    @Override protected void onResume(){super.onResume();if(ready){op("resume");handler.removeCallbacks(poll);handler.post(poll);}}
    @Override protected void onPause(){handler.removeCallbacks(poll);if(ready){if(playerPad!=null)playerPad.release();command(obj("op","camera","kind","release"));op("pause");if(!state.optBoolean("playing"))op("save");}super.onPause();}
    @Override public void surfaceCreated(SurfaceHolder holder){}
    @Override public void surfaceChanged(SurfaceHolder holder,int format,int width,int height){if(ready&&width>0&&height>0)nativeSurface(holder.getSurface(),width,height);}
    @Override public void surfaceDestroyed(SurfaceHolder holder){if(ready)nativeDetach();}
}
