package com.rbxnative.androidport;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
import org.json.JSONObject;

/** UI-test APK only. Real Android Keystore/backend; synthetic secrets, no API calls. */
public final class StudioAiStoreProbe extends Activity {
    private void require(boolean condition){if(!condition)throw new IllegalStateException("Store assertion failed");}
    private StudioAiConnections registry()throws Exception{
        return new StudioAiConnections(new StudioAiConnections.Store(){
            public String read(String service)throws Exception{return StudioAiStoredDocument.read(StudioServices.credential(StudioAiStoreProbe.this,"read",service,"",""),2000000);}
            public void write(String service,String value)throws Exception{StudioServices.credential(StudioAiStoreProbe.this,"write",service,value,"");}
            public void remove(String service)throws Exception{StudioServices.credential(StudioAiStoreProbe.this,"delete",service,"","");}
        });
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);String phase=getIntent().getStringExtra("phase"),result;
        try{
            StudioAiConnections a=registry();android.content.SharedPreferences ids=getSharedPreferences("r10-store-probe",MODE_PRIVATE);
            if("write".equals(phase)){
                require(a.list(1).isEmpty());String first=a.add(1,"Primary","test-secret-device-one"),second=a.add(1,"Backup","test-secret-device-two");
                a.model(first,"test-model-one");a.model(second,"test-model-two");a.update(first,"Device Primary",null,true,false,false,"device-project");a.select(first);
                require(ids.edit().putString("first",first).putString("second",second).commit());
                StudioServices.credential(this,"write","r10.test.task-state","{\"task_id\":\"device-task\",\"cursor\":7}","");
            }else if("read".equals(phase)){
                String first=ids.getString("first",""),second=ids.getString("second","");require(a.list(1).size()==2);require(a.selected(1).getString("id").equals(first));
                require(a.secret(first).equals("test-secret-device-one")&&a.secret(second).equals("test-secret-device-two"));
                require(a.get(second).getString("model").equals("test-model-two"));
                JSONObject task=new JSONObject(StudioAiStoredDocument.read(StudioServices.credential(this,"read","r10.test.task-state","","")));require(task.getInt("cursor")==7);
                a.update(second,"Edited backup",null,true,true,false,"device-project");a.prioritize(second);a.select(second);a.remove(first);
                StudioAiConnections reopened=registry();require(reopened.list(1).size()==1);require(reopened.selected(1).getString("id").equals(second));
                require(StudioServices.credential(this,"read",StudioAiConnections.KEYS+first,"","")==null);
                JSONObject copy=reopened.get(second);copy.put("name","tampered");require(reopened.get(second).getString("name").equals("Edited backup"));
            }else throw new IllegalArgumentException("Unknown test phase");
            for(Object packed:getSharedPreferences("studio-credentials-v1",MODE_PRIVATE).getAll().values())require(!String.valueOf(packed).contains("test-secret-device"));
            result="RBX_STORE_"+phase.toUpperCase(java.util.Locale.ROOT)+"_PASS";
        }catch(Exception failure){result="RBX_STORE_FAIL "+failure.getClass().getSimpleName();}
        TextView label=new TextView(this);label.setText(result);label.setTextSize(22);label.setPadding(24,24,24,24);setContentView(label);
    }
}
