package ru.alfahackaton.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider {

    private final String secret;
    private final long expirationMs;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JwtTokenProvider(
            @Value("${jwt.secret:alfa-hackaton-2026-super-secret-key-with-minimum-256-bits-length-security}") String secret,
            @Value("${jwt.expiration-ms:86400000}") long expirationMs
    ) {
        this.secret = secret;
        this.expirationMs = expirationMs;
    }

    public String generateToken(Long userId, String username, String role, String inn) {
        try {
            Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
            long now = System.currentTimeMillis();

            Map<String, Object> payload = new HashMap<>();
            payload.put("sub", String.valueOf(userId));
            payload.put("username", username);
            payload.put("role", role);
            if (inn != null) {
                payload.put("inn", inn);
            }
            payload.put("iat", now / 1000);
            payload.put("exp", (now + expirationMs) / 1000);

            String headerEncoded = base64UrlEncode(objectMapper.writeValueAsBytes(header));
            String payloadEncoded = base64UrlEncode(objectMapper.writeValueAsBytes(payload));

            String content = headerEncoded + "." + payloadEncoded;
            String signatureEncoded = sign(content);

            return content + "." + signatureEncoded;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate JWT", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) return false;
            String content = parts[0] + "." + parts[1];
            String expectedSignature = sign(content);

            if (!MessageDigest.isEqual(parts[2].getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
                return false;
            }

            Map<String, Object> claims = parsePayload(parts[1]);
            Number exp = (Number) claims.get("exp");
            return exp == null || exp.longValue() >= System.currentTimeMillis() / 1000;
        } catch (Exception e) {
            return false;
        }
    }

    public Map<String, Object> getClaims(String token) {
        try {
            String[] parts = token.split("\\.");
            return parsePayload(parts[1]);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JWT token", e);
        }
    }

    private Map<String, Object> parsePayload(String payloadEncoded) throws Exception {
        byte[] bytes = Base64.getUrlDecoder().decode(payloadEncoded);
        return objectMapper.readValue(bytes, new TypeReference<>() {});
    }

    private String sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(secretKeySpec);
        byte[] hmac = mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
        return base64UrlEncode(hmac);
    }

    private String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
