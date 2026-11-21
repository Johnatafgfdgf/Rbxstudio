package com.rbxnative.androidport;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Read-only official permission introspection. Only a sanitized summary leaves this boundary. */
final class StudioRobloxCapabilities {
    interface Transport {JSONObject introspect(String secret)throws Exception;}
    static JSONObject inspect(String key,String user)throws Exception{return check(key,user,secret->{
        HttpURLConnection c=(HttpURLConnection)new URL("https://apis.roblox.com/api-keys/v1/introspect").openConnection();
        try {
            c.setInstanceFollowRedirects(false);c.setUseCaches(false);c.setConnectTimeout(10000);c.setReadTimeout(15000);
            c.setRequestMethod("POST");c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");
            try(OutputStream out=c.getOutputStream()){out.write(new JSONObject().put("apiKey",secret).toString().getBytes(StandardCharsets.UTF_8));}
            if(c.getResponseCode()!=200)throw new IOException("Não foi possível confirmar permissões Roblox; use a Toolbox.");
            try(InputStream in=c.getInputStream()){return new JSONObject(new String(readBounded(in,65536),StandardCharsets.UTF_8));}
        }finally{c.disconnect();}
    });}
    static byte[] readBounded(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[4096];int count;
        while((count=in.read(buffer))!=-1){if(out.size()+count>limit)throw new IOException("Resposta excedeu o limite local.");out.write(buffer,0,count);}
        return out.toByteArray();
    }
    static JSONObject check(String key,String user,Transport transport)throws Exception{
        StudioRobloxImageUpload.requireKey(key);StudioRobloxImageUpload.requireCreator(user);
        return summarize(transport.introspect(key),user);
    }
    static JSONObject summarize(JSONObject raw,String user)throws Exception{
        boolean active=Boolean.TRUE.equals(raw.opt("enabled"))&&Boolean.FALSE.equals(raw.opt("expired")),write=false;
        JSONArray scopes=raw.optJSONArray("scopes");
        if(active&&scopes!=null)for(int i=0;i<scopes.length();i++){
            JSONObject s=scopes.optJSONObject(i);if(s==null||!s.optString("name").equals("asset"))continue;
            JSONArray ops=s.optJSONArray("operations"),owners=s.optJSONArray("userIds");boolean op=false,owner=false;
            if(ops!=null)for(int j=0;j<ops.length();j++)op|=ops.optString(j).equals("write");
            if(owners!=null)for(int j=0;j<owners.length();j++)owner|=owners.optString(j).equals("*")||owners.optString(j).equals(user);
            write|=op&&owner;
        }
        return new JSONObject().put("checked",true).put("active",active).put("asset_write",write)
            .put("creator_type","user").put("creator_id",user)
            .put("asset_strategy",write?"upload_requires_separate_approval":"toolbox")
            .put("reason","Permissões consultadas; nenhum asset publicado. Propriedade e moderação ainda são verificadas pela API.");
    }
}
