package com.campusconnect.campus;

import com.campusconnect.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.TreeMap;

@Component
class CloudImages {
    private final String cloud,key,secret;
    private final RestClient client;
    CloudImages(@Value("${CLOUDINARY_CLOUD_NAME:}") String cloud,@Value("${CLOUDINARY_API_KEY:}") String key,
                @Value("${CLOUDINARY_API_SECRET:}") String secret) {
        this.cloud=cloud; this.key=key; this.secret=secret;
        var factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000); factory.setReadTimeout(20000);
        client=RestClient.builder().requestFactory(factory).baseUrl("https://api.cloudinary.com").build();
    }
    void configured() {
        if(cloud.isBlank() || key.isBlank() || secret.isBlank()) throw unavailable();
    }
    private ApiException unavailable() {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE,"Photo storage is unavailable. Retry the photo after the connection is configured.");
    }
    private JsonNode send(String id,byte[] bytes,String format) {
        configured();
        TreeMap<String,String> fields=new TreeMap<>();
        fields.put("timestamp",Long.toString(Instant.now().getEpochSecond())); fields.put("public_id",id);
        if(bytes!=null) fields.put("overwrite","false");
        else fields.put("invalidate","true");
        String canonical=String.join("&",fields.entrySet().stream().map(e->e.getKey()+"="+e.getValue()).toList());
        var form=new LinkedMultiValueMap<String,Object>(); fields.forEach(form::add); form.add("api_key",key);
        try {
            form.add("signature",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((canonical+secret).getBytes(StandardCharsets.UTF_8))));
        } catch(NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
        if(bytes!=null) form.add("file",new ByteArrayResource(bytes) { @Override public String getFilename() { return "item."+format; } });
        try {
            return client.post().uri("/v1_1/{cloud}/image/{op}",cloud,bytes==null?"destroy":"upload")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class);
        } catch(org.springframework.web.client.RestClientException ex) { throw unavailable(); }
    }
    String upload(String id,byte[] bytes,String format) {
        var result=send(id,bytes,format);
        if(result==null || !id.equals(result.path("public_id").asText()) || !result.path("secure_url").asText().startsWith("https://"))
            throw unavailable();
        return result.get("secure_url").asText();
    }
    void destroy(String id) {
        var result=send(id,null,null);
        if(result==null || !java.util.Set.of("ok","not found").contains(result.path("result").asText())) throw unavailable();
    }
}
