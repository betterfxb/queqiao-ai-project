package com.queqiao.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 轻量 JWT（HS256）实现——不引第三方依赖，减少供应链面。
 * 结构：base64url(header).base64url(payload).base64url(hmacSha256)
 */
@Component
public class JwtUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Base64.Encoder ENC = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DEC = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    @Value("${queqiao.jwt-secret:queqiao-jwt-secret-2026-change-in-prod}")
    private String secret;

    @Value("${queqiao.jwt-expire-minutes:120}")
    private long expireMinutes;

    /** 签发令牌 */
    public String sign(String userId, String username, String role) {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> payload = new HashMap<>();
        payload.put("sub", userId);
        payload.put("name", username);
        payload.put("role", role);
        payload.put("iat", now);
        payload.put("exp", now + expireMinutes * 60);
        String h = ENC.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String p;
        try {
            p = ENC.encodeToString(MAPPER.writeValueAsBytes(payload));
        } catch (Exception e) {
            throw new IllegalStateException("token 生成失败", e);
        }
        String sig = hmac(h + "." + p);
        return h + "." + p + "." + sig;
    }

    /** 校验并解析；失败返回 null */
    @SuppressWarnings("unchecked")
    public Map<String, Object> verify(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String expect = hmac(parts[0] + "." + parts[1]);
        if (!constantTimeEquals(expect, parts[2])) return null;
        try {
            Map<String, Object> payload = MAPPER.readValue(DEC.decode(parts[1]), Map.class);
            Object exp = payload.get("exp");
            long expSec = exp instanceof Number n ? n.longValue() : 0;
            if (expSec < System.currentTimeMillis() / 1000) return null;
            return payload;
        } catch (Exception e) {
            return null;
        }
    }

    public long expireSeconds() {
        return expireMinutes * 60;
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return ENC.encodeToString(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 计算失败", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        if (ba.length != bb.length) return false;
        int r = 0;
        for (int i = 0; i < ba.length; i++) r |= ba[i] ^ bb[i];
        return r == 0;
    }

}
