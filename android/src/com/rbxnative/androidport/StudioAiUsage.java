package com.rbxnative.androidport;

import java.time.Instant;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Observed request-window quotas, not an estimate of account credits or daily balance. */
final class StudioAiUsage {
    long calls,inputTokens,outputTokens,remaining=-1,limit=-1,expiresAt;
    int lastStatus;
    synchronized void record(int provider,int status,JSONObject response,Map<String,List<String>> headers,long now) {
        calls++;lastStatus=status;
        JSONObject u=response==null?null:response.optJSONObject(provider==1?"usageMetadata":"usage");
        if(u!=null) {
            inputTokens+=Math.max(0,u.optLong(provider==1?"promptTokenCount":"input_tokens",u.optLong("prompt_tokens")));
            outputTokens+=Math.max(0,u.optLong(provider==1?"candidatesTokenCount":"output_tokens",u.optLong("completion_tokens")));
        }
        String prefix=provider==2?"anthropic-ratelimit-requests-":"x-ratelimit-";
        String r=header(headers,provider==2?prefix+"remaining":prefix+"remaining-requests");
        String l=header(headers,provider==2?prefix+"limit":prefix+"limit-requests");
        remaining=-1;limit=-1;expiresAt=0;
        try {
            long rn=Long.parseLong(r),ln=Long.parseLong(l);
            if(rn<0||ln<=0||rn>ln)return;
            String reset=header(headers,provider==2?prefix+"reset":prefix+"reset-requests");
            long expiry=provider==2?Instant.parse(reset).toEpochMilli():now+duration(reset);
            if(expiry<=now||expiry-now>86_400_000L)return;
            remaining=rn;limit=ln;expiresAt=expiry;
        } catch(Exception unknown) { /* No invented quota when headers are absent/inconsistent. */ }
    }
    synchronized boolean hasQuota(long now){return remaining>=0&&limit>0&&now<expiresAt;}
    synchronized int fraction(long now){return hasQuota(now)?(int)Math.round(1000.0*remaining/limit):0;}
    synchronized String label(long now){
        if(hasQuota(now))return remaining+"/"+limit+" solicitações na janela da API";
        return (lastStatus==429?"Limite atingido; saldo não informado":"Saldo não informado")+" · "+calls+" chamadas locais";
    }
    synchronized String details(long now){return label(now)+"\n"+inputTokens+" tokens de entrada · "+outputTokens+" de saída observados."
        +(hasQuota(now)?"\nRenovação informada pela API: "+Math.max(1,(expiresAt-now+999)/1000)+" s.":"")
        +"\nO contador local inclui erros e não inclui outros aplicativos. Não representa créditos nem saldo diário. Cotas podem ser compartilhadas entre chaves e modelos.";}
    static String header(Map<String,List<String>> headers,String name){
        if(headers!=null)for(Map.Entry<String,List<String>> e:headers.entrySet())
            if(e.getKey()!=null&&e.getKey().equalsIgnoreCase(name)&&e.getValue()!=null&&!e.getValue().isEmpty())return e.getValue().get(0);
        return "";
    }
    static long duration(String value){
        Matcher m=Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h)").matcher(value);double total=0;int end=0;
        while(m.find()) {if(m.start()!=end)return 0;end=m.end();double n=Double.parseDouble(m.group(1));
            total+=n*(m.group(2).equals("h")?3600000:m.group(2).equals("m")?60000:m.group(2).equals("s")?1000:1);}
        return end==value.length()&&Double.isFinite(total)&&total>0&&total<=86_400_000?(long)Math.ceil(total):0;
    }
    static long retryDelay(Map<String,List<String>> headers,JSONObject error,long now){
        long delay=0;String raw=header(headers,"retry-after");
        try {double n=Double.parseDouble(raw);if(Double.isFinite(n)&&n>0)delay=(long)Math.ceil(n*1000);}catch(Exception absent){
            try{delay=java.time.ZonedDateTime.parse(raw,java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now;}catch(Exception noDate){}
        }
        if(delay==0&&error!=null){JSONObject e=error.optJSONObject("error");JSONArray d=e==null?null:e.optJSONArray("details");
            if(d!=null)for(int i=0;i<d.length();i++){JSONObject v=d.optJSONObject(i);if(v!=null&&v.optString("@type").endsWith("google.rpc.RetryInfo"))delay=duration(v.optString("retryDelay"));}}
        return delay>0&&delay<=60000?delay:0;
    }
}
