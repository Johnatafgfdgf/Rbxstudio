package com.rbxnative.androidport;

import java.util.*;
import java.io.IOException;
import org.json.*;

/** Persisted connections. Secrets have separate encrypted entries and never enter metadata/handoffs. */
final class StudioAiConnections {
    interface Store {String read(String service)throws Exception;void write(String service,String value)throws Exception;void remove(String service)throws Exception;}
    static final String INDEX="rbxstudio.ai.connections.v1",KEYS="rbxstudio.ai.connection.";
    private final Store store;
    private JSONObject index;
    StudioAiConnections(Store store)throws Exception{
        this.store=store;String raw=store.read(INDEX);
        index=raw==null||raw.isEmpty()?new JSONObject().put("version",1).put("entries",new JSONArray()).put("selected",new JSONObject()):new JSONObject(raw);
        if(index.optInt("version")!=1||index.optJSONArray("entries")==null)throw new IOException("Registro de conexões incompatível; credenciais preservadas.");
    }
    synchronized List<JSONObject> list(int provider)throws Exception{
        List<JSONObject> result=new ArrayList<>();JSONArray entries=index.getJSONArray("entries");
        for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(e.getInt("provider")==provider)result.add(new JSONObject(e.toString()));}
        result.sort(Comparator.comparingInt(e->e.optInt("priority",100)));return result;
    }
    synchronized JSONObject selected(int provider)throws Exception{
        String id=index.getJSONObject("selected").optString(String.valueOf(provider));
        for(JSONObject e:list(provider))if(e.getString("id").equals(id)&&e.optBoolean("enabled"))return e;
        for(JSONObject e:list(provider))if(e.optBoolean("enabled"))return e;
        return null;
    }
    synchronized JSONObject get(String id)throws Exception{
        JSONArray entries=index.getJSONArray("entries");for(int i=0;i<entries.length();i++)if(entries.getJSONObject(i).getString("id").equals(id))return entries.getJSONObject(i);
        throw new IOException("Conexão não encontrada.");
    }
    synchronized String secret(String id)throws Exception{return store.read(KEYS+get(id).getString("id"));}
    synchronized String add(int provider,String name,String secret)throws Exception{
        if(index.getJSONArray("entries").length()>=32)throw new IOException("Limite local de 32 conexões atingido.");
        if(provider<0||provider>3)throw new IOException("Provedor inválido.");validateSecret(secret);name=validateName(name);
        String id=UUID.randomUUID().toString();JSONObject e=new JSONObject().put("id",id).put("provider",provider).put("name",name)
            .put("enabled",true).put("model","").put("priority",list(provider).size()).put("fallback",false).put("paid",false).put("quota_group","");
        store.write(KEYS+id,secret);index.getJSONArray("entries").put(e);select(id);return id;
    }
    synchronized void update(String id,String name,String secret,boolean enabled,boolean fallback,boolean paid,String quotaGroup)throws Exception{
        JSONObject e=get(id);name=validateName(name);
        if(quotaGroup==null||!quotaGroup.matches("[A-Za-z0-9_.:-]{0,100}"))throw new IOException("Grupo de cota inválido.");
        if(secret!=null&&!secret.isEmpty()){validateSecret(secret);store.write(KEYS+id,secret);e.remove("catalog");e.put("status","Chave alterada; teste novamente");}
        e.put("name",name).put("enabled",enabled).put("fallback",fallback).put("paid",paid).put("quota_group",quotaGroup);persist();
    }
    synchronized void select(String id)throws Exception{JSONObject e=get(id);if(!e.optBoolean("enabled"))throw new IOException("Ative a conexão primeiro.");index.getJSONObject("selected").put(String.valueOf(e.getInt("provider")),id);persist();}
    synchronized void remove(String id)throws Exception{
        get(id);store.remove(KEYS+id);JSONArray entries=index.getJSONArray("entries"),keep=new JSONArray();
        for(int i=0;i<entries.length();i++)if(!entries.getJSONObject(i).getString("id").equals(id))keep.put(entries.getJSONObject(i));
        index.put("entries",keep);persist();
    }
    synchronized void model(String id,String model)throws Exception{
        if(!model.matches("[a-zA-Z0-9_./:-]{1,180}"))throw new IOException("Modelo inválido.");get(id).put("model",model);persist();
    }
    synchronized void catalog(String id,JSONArray models)throws Exception{get(id).put("catalog",models).put("status","Catálogo consultado").put("tested_at",System.currentTimeMillis());persist();}
    synchronized void status(String id,String value,long cooldown)throws Exception{get(id).put("status",value).put("cooldown_until",cooldown);persist();}
    synchronized boolean migrated(int provider){return index.optBoolean("migrated_"+provider);}
    synchronized void migrated(int provider,boolean done)throws Exception{index.put("migrated_"+provider,done);persist();}
    synchronized void prioritize(String id)throws Exception{
        JSONObject e=get(id);for(JSONObject other:list(e.getInt("provider")))get(other.getString("id")).put("priority",other.optInt("priority")+1);e.put("priority",0);persist();
    }
    /** 429 does not switch to another key of the same/unknown quota scope. This is not a quota bypass. */
    synchronized List<JSONObject> eligible(String current,int status,boolean hasImages,long now,Set<String> tried)throws Exception{
        JSONObject active=get(current);List<JSONObject> result=new ArrayList<>();
        for(int p=0;p<4;p++)for(JSONObject e:list(p)){
            String id=e.getString("id"),model=e.optString("model");
            if(id.equals(current)||tried.contains(id)||!e.optBoolean("enabled")||!e.optBoolean("fallback")||e.optLong("cooldown_until")>now)continue;
            if(p==active.getInt("provider")&&status==429){String a=active.optString("quota_group"),b=e.optString("quota_group");if(a.isEmpty()||b.isEmpty()||a.equals(b))continue;}
            JSONArray catalog=e.optJSONArray("catalog");JSONObject item=null;
            if(catalog!=null)for(int i=0;i<catalog.length();i++)if(catalog.getJSONObject(i).optString("id").equals(model))item=catalog.getJSONObject(i);
            if(item==null||(!e.optBoolean("paid")&&item.optInt("tier")<2)||hasImages&&!item.optBoolean("vision"))continue;
            result.add(e);
        }
        result.sort(Comparator.comparingInt(e->e.optInt("priority",100)));return result;
    }
    private void persist()throws Exception{String data=index.toString();if(data.length()>2000000)throw new IOException("Catálogos acima do limite local; remova conexões não utilizadas.");store.write(INDEX,data);}
    private static void validateSecret(String secret)throws IOException{if(secret==null||secret.length()<8||secret.length()>4096||!secret.matches("[\\x21-\\x7e]+"))throw new IOException("Chave inválida; não inclua espaços ou quebras de linha.");}
    private static String validateName(String name)throws IOException{if(name==null||name.trim().isEmpty()||name.length()>64||name.chars().anyMatch(Character::isISOControl))throw new IOException("Nome da conexão inválido (1–64 caracteres).");return name.trim();}
}
