package com.rbxnative.androidport;

import java.util.*;
import org.json.*;

/** AI Visual Builder: explicit editable UI trees compiled to the same native DOM edits as Explorer. */
final class StudioAiVisualBuilder {
    private static final Set<String> CLASSES=new HashSet<>(Arrays.asList("Frame","ScrollingFrame","TextLabel","TextButton","TextBox","ImageLabel","ImageButton","ViewportFrame","UIListLayout","UIGridLayout","UIPadding","UIScale","UIAspectRatioConstraint","UISizeConstraint","UITextSizeConstraint","UICorner","UIStroke","UIGradient"));
    private final JSONArray operations=new JSONArray(),issues=new JSONArray();
    private final JSONObject theme;
    private final String root;
    private final StringBuilder bindings=new StringBuilder();
    private final List<Node> nodes=new ArrayList<>();
    private static final class Node {String path,parent,kind;JSONObject props;Node(String p,String parent,String k,JSONObject props){path=p;this.parent=parent;kind=k;this.props=props;}}
    private StudioAiVisualBuilder(JSONObject spec)throws Exception{
        if(!spec.getString("parent").equals("StarterGui"))throw new StudioAiAgent.Refusal("GUI persistente precisa de StarterGui.");
        root="StarterGui."+name(spec);theme=spec.optJSONObject("theme")==null?new JSONObject():spec.getJSONObject("theme");
        add("ScreenGui","StarterGui",name(spec),new JSONObject().put("ResetOnSpawn",false));
        walk(spec.getJSONArray("elements"),root,0);
        if(bindings.length()>0)operations.put(new JSONObject().put("op","create").put("parent",root).put("class","LocalScript").put("name","InteractionBindings").put("source",bindings.toString()));
        if(operations.length()>256||operations.toString().length()>240000)throw new StudioAiAgent.Refusal("Interface acima do orçamento; divida em componentes.");
    }
    static JSONArray compile(JSONObject spec)throws Exception{return new StudioAiVisualBuilder(spec).operations;}
    static JSONObject elementSchema()throws Exception{
        JSONObject element=null;
        for(int i=0;i<5;i++) {
            JSONObject fields=new JSONObject().put("class",new JSONObject().put("type","string").put("enum",new JSONArray(CLASSES)))
                .put("name",new JSONObject().put("type","string"))
                .put("properties",new JSONObject().put("type","object").put("additionalProperties",true))
                .put("on_activate",new JSONObject().put("type","string"));
            if(element!=null)fields.put("children",new JSONObject().put("type","array").put("description","Componentes filhos reais deste objeto. Use esta árvore para painéis, botões e seus modificadores; irmãos não são filhos.").put("items",element));
            element=new JSONObject().put("type","object").put("properties",fields).put("required",new JSONArray(Arrays.asList("class","name")));
        }
        return element;
    }
    static JSONObject review(JSONObject spec)throws Exception{
        StudioAiVisualBuilder b=new StudioAiVisualBuilder(spec);
        for(int[] viewport:new int[][]{{360,800},{800,360},{1080,2400}})b.layout(viewport[0],viewport[1]);
        return new JSONObject().put("issues",b.issues).put("operations",b.operations.length())
            .put("scope","Verificação geométrica de parâmetros declarados. Não é captura, medição de fonte, execução de callbacks nem avaliação visual. Layouts automáticos e constraints precisam de renderização real.");
    }
    private void walk(JSONArray elements,String parent,int depth)throws Exception{
        if(depth>12||elements.length()>64)throw new StudioAiAgent.Refusal("Hierarquia de GUI acima do limite.");
        Set<String> names=new HashSet<>();
        for(int i=0;i<elements.length();i++){
            JSONObject e=elements.getJSONObject(i);String name=name(e),kind=e.getString("class");
            for(Iterator<String> keys=e.keys();keys.hasNext();){String key=keys.next();if(!Arrays.asList("class","name","properties","on_activate","children").contains(key))throw new StudioAiAgent.Refusal("Campo de componente desconhecido: "+key+". Use class/name/properties/on_activate/children.");}
            if(e.has("children")&&!(e.opt("children") instanceof JSONArray))throw new StudioAiAgent.Refusal("children deve ser uma lista de componentes JSON.");
            if(e.has("properties")&&!(e.opt("properties") instanceof JSONObject))throw new StudioAiAgent.Refusal("properties deve ser objeto JSON.");
            if(name.equals("InteractionBindings")||!names.add(name)||!CLASSES.contains(kind))throw new StudioAiAgent.Refusal("Classe ou nome de componente inválido: "+name);
            if(kind.startsWith("UI")) {
                String parentClass = parent.equals(root) ? "ScreenGui" : "";
                for(Node node:nodes)if(node.path.equals(parent)){parentClass=node.kind;break;}
                if(parentClass.equals("ScreenGui")||parentClass.startsWith("UI"))
                    throw new StudioAiAgent.Refusal(kind+" precisa ser filho de um GuiObject (Frame, botão ou label). Declare-o em children do componente; não como irmão do painel.");
            }
            JSONObject props=e.optJSONObject("properties")==null?new JSONObject():new JSONObject(e.getJSONObject("properties").toString());
            String path=parent+"."+name;
            if(kind.startsWith("Text")){
                if(!props.has("TextSize"))props.put("TextSize",theme.optInt("text_size",16));
                if(!props.has("TextColor3")&&theme.has("text"))props.put("TextColor3",theme.getString("text"));
                if(!props.has("Font")&&theme.has("font"))props.put("Font",theme.getString("font"));
            }
            if((kind.equals("Frame")||kind.endsWith("Button"))&&!props.has("BackgroundColor3")&&theme.has(kind.endsWith("Button")?"accent":"surface"))
                props.put("BackgroundColor3",theme.getString(kind.endsWith("Button")?"accent":"surface"));
            add(kind,parent,name,props);nodes.add(new Node(path,parent,kind,props));
            String code=e.optString("on_activate");
            if(!code.isEmpty()){
                if(!kind.equals("TextButton")&&!kind.equals("ImageButton"))throw new StudioAiAgent.Refusal("on_activate requer TextButton ou ImageButton.");
                if(code.length()>3000)throw new StudioAiAgent.Refusal("Callback longo; use um ModuleScript separado.");
                StringBuilder expression=new StringBuilder("script.Parent");
                for(String segment:path.substring(root.length()+1).split("\\."))expression.append(":WaitForChild(\"").append(quote(segment)).append("\")");
                bindings.append(expression).append(".Activated:Connect(function()\n").append(code).append("\nend)\n");
                if(bindings.length()>16000)throw new StudioAiAgent.Refusal("Mova a lógica de interface para módulos reutilizáveis.");
            } else if(kind.endsWith("Button"))issues.put(path+": nenhum comportamento foi conectado por este builder; confira bindings externos antes de concluir.");
            if(e.optJSONArray("children")!=null)walk(e.getJSONArray("children"),path,depth+1);
        }
    }
    private void add(String kind,String parent,String name,JSONObject props)throws Exception{
        operations.put(new JSONObject().put("op","create").put("class",kind).put("parent",parent).put("name",name).put("properties",props));
    }
    private void layout(int width,int height)throws Exception{
        Map<String,double[]> boxes=new HashMap<>();boxes.put(root,new double[]{0,0,width,height});
        Set<String> autoParents=new HashSet<>();
        for(Node n:nodes)if(n.kind.equals("UIGridLayout")||n.kind.equals("UIListLayout"))autoParents.add(n.parent);
        Map<String,List<double[]>> siblings=new HashMap<>();
        for(Node n:nodes){
            if(n.kind.startsWith("UI"))continue;
            double[] p=boxes.get(n.parent);if(p==null)continue;
            if(autoParents.contains(n.parent)){issues.put(n.path+": posição depende de layout automático; conferir renderização "+width+"×"+height);continue;}
            double[] size=n.props.has("Size")?numbers(n.props.optString("Size"),4):null;
            double[] pos=n.props.has("Position")?numbers(n.props.optString("Position"),4):new double[]{0,0,0,0};
            double[] anchor=n.props.has("AnchorPoint")?numbers(n.props.optString("AnchorPoint"),2):new double[]{0,0};
            if(size==null||pos==null||anchor==null){issues.put(n.path+": geometria não avaliada "+width+"×"+height);continue;}
            double w=p[2]*size[0]+size[1],h=p[3]*size[2]+size[3];
            double x=p[0]+p[2]*pos[0]+pos[1]-w*anchor[0],y=p[1]+p[3]*pos[2]+pos[3]-h*anchor[1];
            double[] b={x,y,w,h};boxes.put(n.path,b);
            if(w<=0||h<=0||x<0||y<0||x+w>width+1||y+h>height+1)issues.put(n.path+": limites fora da tela em "+width+"×"+height);
            if(n.kind.endsWith("Button")&&(w<44||h<44))issues.put(n.path+": alvo de toque menor que 44 px em "+width+"×"+height);
            if(n.kind.startsWith("Text")&&n.props.optDouble("TextSize",16)<14)issues.put(n.path+": texto menor que 14 px.");
            if(n.kind.startsWith("Text")&&n.props.has("TextColor3")&&n.props.has("BackgroundColor3")&&n.props.optDouble("BackgroundTransparency",0)==0){
                double[] a=numbers(n.props.optString("TextColor3"),3),c=numbers(n.props.optString("BackgroundColor3"),3);
                if(a!=null&&c!=null){double la=luminance(a,n.props.optString("TextColor3").startsWith("Color3.new(")),lc=luminance(c,n.props.optString("BackgroundColor3").startsWith("Color3.new(")),contrast=(Math.max(la,lc)+.05)/(Math.min(la,lc)+.05);if(contrast<4.5)issues.put(n.path+": contraste calculado "+String.format(Locale.ROOT,"%.2f",contrast)+"; verificar tamanho e legibilidade.");}
            }
            if(n.kind.endsWith("Button")&&Boolean.FALSE!=n.props.opt("Visible")){
                List<double[]> previous=siblings.computeIfAbsent(n.parent,k->new ArrayList<>());
                for(double[] q:previous)if(x<q[0]+q[2]&&x+w>q[0]&&y<q[1]+q[3]&&y+h>q[1])issues.put(n.path+": interseção com outro botão no mesmo pai em "+width+"×"+height);
                previous.add(b);
            }
            if(issues.length()>200)break;
        }
    }
    private static double[] numbers(String text,int count){
        String s=text.trim();int open=s.indexOf('(');if(open>=0&&s.endsWith(")"))s=s.substring(open+1,s.length()-1);
        String[] parts=s.split(",");if(parts.length!=count)return null;double[] n=new double[count];
        try{for(int i=0;i<count;i++){n[i]=Double.parseDouble(parts[i].trim());if(!Double.isFinite(n[i]))return null;}return n;}catch(Exception invalid){return null;}
    }
    private static double luminance(double[] rgb,boolean normalized){double sum=0;double[] weights={.2126,.7152,.0722};for(int i=0;i<3;i++){double c=Math.max(0,Math.min(1,normalized?rgb[i]:rgb[i]/255));sum+=weights[i]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return sum;}
    private static String name(JSONObject e)throws Exception{
        String n=e.getString("name");if(n.isEmpty()||n.length()>80||n.indexOf('.')>=0||n.chars().anyMatch(Character::isISOControl))throw new StudioAiAgent.Refusal("Nome de componente inválido.");return n;
    }
    private static String quote(String s){return s.replace("\\","\\\\").replace("\"","\\\"");}
}
