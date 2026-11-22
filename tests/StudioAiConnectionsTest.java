package com.rbxnative.androidport;
import java.util.*;
import org.json.*;

/** Registry/quotas are deterministic contracts. This does not emulate Android Keystore or live failover. */
public final class StudioAiConnectionsTest {
    static void yes(boolean b){if(!b)throw new AssertionError();}
    static final class Store implements StudioAiConnections.Store{
        final Map<String,String> values=new HashMap<>();
        public String read(String k){return values.get(k);}public void write(String k,String v){values.put(k,v);}public void remove(String k){values.remove(k);}
    }
    public static void main(String[] args)throws Exception{
        Store store=new Store();StudioAiConnections a=new StudioAiConnections(store);
        String one=a.add(1,"Gemini — Chave 1","test-invalid-secret-one"),two=a.add(1,"Gemini — Chave 2","test-invalid-secret-two"),third=a.add(3,"OpenRouter","test-invalid-secret-three");
        a.update(one,"Principal",null,true,false,false,"project-a");a.update(two,"Reserva",null,true,true,true,"project-a");a.update(third,"Outro provedor",null,true,true,false,"");
        a.model(two,"gemini-test");a.model(third,"free-model:free");
        a.catalog(two,new JSONArray().put(new JSONObject().put("id","gemini-test").put("tier",2)));
        a.catalog(third,new JSONArray().put(new JSONObject().put("id","free-model:free").put("tier",3)));
        a.select(one);
        yes(a.eligible(one,429,false,0,new HashSet<>()).size()==1); // Same project quota must not rotate to key 2.
        yes(a.eligible(one,503,false,0,new HashSet<>()).size()==2);
        a.update(two,"Reserva",null,true,true,false,"project-a");yes(a.eligible(one,503,false,0,new HashSet<>()).size()==1);
        a.update(two,"Reserva",null,true,true,true,"project-a");
        JSONObject detached=a.get(two);detached.put("model","tampered");yes(a.get(two).getString("model").equals("gemini-test"));
        yes(a.eligible(one,503,true,0,new HashSet<>()).isEmpty()); // No invented vision capability.
        a.update(two,"Reserva",null,true,true,true,"project-b");yes(a.eligible(one,429,false,0,new HashSet<>()).size()==2);
        yes(!store.values.get(StudioAiConnections.INDEX).contains("test-invalid-secret"));
        StudioAiConnections b=new StudioAiConnections(store);yes(b.list(1).size()==2&&b.selected(1).getString("id").equals(one));
        yes(b.secret(two).equals("test-invalid-secret-two"));
        b.status(two,"HTTP 429",10000);yes(b.eligible(one,503,false,1,new HashSet<>()).size()==1);
        b.remove(two);yes(b.list(1).size()==1&&!store.values.containsKey(StudioAiConnections.KEYS+two));
        StudioAiUsage u=new StudioAiUsage();Map<String,List<String>> headers=new HashMap<>();
        headers.put("X-RateLimit-Remaining-Requests",Arrays.asList("75"));headers.put("x-ratelimit-limit-requests",Arrays.asList("100"));headers.put("x-ratelimit-reset-requests",Arrays.asList("1m2s"));
        u.record(0,200,new JSONObject("{\"usage\":{\"input_tokens\":10,\"output_tokens\":5}}"),headers,1000);
        yes(u.hasQuota(1001)&&u.fraction(1001)==750&&!u.hasQuota(64000));
        u.record(1,200,new JSONObject("{\"usageMetadata\":{\"promptTokenCount\":20,\"candidatesTokenCount\":7}}"),Collections.emptyMap(),2000);
        yes(!u.hasQuota(2001)&&u.inputTokens==30&&u.outputTokens==12&&u.calls==2);
        yes(StudioAiUsage.duration("1m2.5s")==62500&&StudioAiUsage.duration("NaNs")==0);
        yes(StudioAiUsage.retryDelay(Collections.emptyMap(),new JSONObject("{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\",\"retryDelay\":\"4.5s\"}]}}"),0)==4500);
        System.out.println("Multi-key registry, persistence, quota isolation, vision guard and observed usage contracts passed.");
    }
}
