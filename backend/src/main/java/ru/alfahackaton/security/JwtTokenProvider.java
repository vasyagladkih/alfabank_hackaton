package ru.alfahackaton.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Small HS256 adapter for demo sessions; unsigned placeholder tokens are never accepted. */
@Component
public class JwtTokenProvider {
    private static final String ISSUER = "alfa-credit-demo";
    private static final String AUDIENCE = "alfa-credit-ui";
    private static final Set<String> ROLES = Set.of("CLIENT", "EMPLOYEE");
    private final byte[] secret;
    private final long expirationSeconds;
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public JwtTokenProvider(@Value("${jwt.secret}") String secret,
                            @Value("${jwt.expiration-ms:86400000}") long expirationMs) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32
                || secret.startsWith("REPLACE_WITH_") || secret.startsWith("CHANGE_ME")) {
            throw new IllegalArgumentException("JWT_SECRET must be explicitly configured with at least 32 bytes; placeholders are not accepted");
        }
        if (expirationMs < 1000 || expirationMs > 86_400_000) {
            throw new IllegalArgumentException("JWT lifetime must be between one second and 24 hours");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.expirationSeconds = expirationMs / 1000;
    }

    public String generateToken(Long userId, String username, String role, String inn) {
        if (userId == null || userId <= 0 || username == null || username.isBlank()
                || username.length() > 64 || !ROLES.contains(role)) {
            throw new IllegalArgumentException("Invalid demo identity");
        }
        try {
            long now = Instant.now().getEpochSecond();
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sub", userId.toString()); payload.put("username", username); payload.put("role", role);
            payload.put("iat", now); payload.put("exp", now + expirationSeconds);
            payload.put("iss", ISSUER); payload.put("aud", AUDIENCE);
            if (inn != null) payload.put("inn", inn);
            String body = encode(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")))
                    + "." + encode(mapper.writeValueAsBytes(payload));
            return body + "." + encode(sign(body));
        } catch (Exception cause) {
            throw new IllegalStateException("Unable to issue demo session", cause);
        }
    }

    /** Validates signature, JOSE header, identity, issuer/audience and expiration exactly once. */
    public Optional<Map<String, Object>> verify(String token) {
        try {
            if (token == null || token.length() > 8192) return Optional.empty();
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) return Optional.empty();
            for (String part : parts) if (!part.matches("[A-Za-z0-9_-]+")) return Optional.empty();
            byte[] signature = Base64.getUrlDecoder().decode(parts[2]);
            if (signature.length != 32 || !encode(signature).equals(parts[2])
                    || !MessageDigest.isEqual(signature, sign(parts[0] + "." + parts[1]))) return Optional.empty();
            Map<String, Object> header = decode(parts[0]);
            if (header.size() != 2 || !"HS256".equals(header.get("alg")) || !"JWT".equals(header.get("typ"))) return Optional.empty();
            Map<String, Object> claims = decode(parts[1]);
            if (!(claims.get("sub") instanceof String sub) || !sub.matches("[1-9][0-9]{0,18}") || Long.parseLong(sub) <= 0) return Optional.empty();
            if (!(claims.get("role") instanceof String role) || !ROLES.contains(role)) return Optional.empty();
            if (!(claims.get("username") instanceof String username) || username.isBlank() || username.length() > 64) return Optional.empty();
            if (!ISSUER.equals(claims.get("iss")) || !AUDIENCE.equals(claims.get("aud"))) return Optional.empty();
            Long issued = integerClaim(claims.get("iat"));
            Long expires = integerClaim(claims.get("exp"));
            long now = Instant.now().getEpochSecond();
            if (issued == null || expires == null || issued < 0 || issued > now + 30
                    || expires <= now || expires <= issued || expires - issued > expirationSeconds) return Optional.empty();
            if (claims.containsKey("nbf")) {
                Long notBefore = integerClaim(claims.get("nbf"));
                if (notBefore == null || notBefore < 0 || notBefore > now || notBefore >= expires) return Optional.empty();
            }
            return Optional.of(Map.copyOf(claims));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    public boolean validateToken(String token) { return verify(token).isPresent(); }

    public Map<String, Object> getClaims(String token) {
        return verify(token).orElseThrow(() -> new IllegalArgumentException("Invalid JWT token"));
    }

    private static Long integerClaim(Object value) {
        return value instanceof Integer || value instanceof Long ? ((Number) value).longValue() : null;
    }

    private Map<String, Object> decode(String part) throws Exception {
        return mapper.readValue(Base64.getUrlDecoder().decode(part), new TypeReference<>() {});
    }

    private byte[] sign(String content) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(content.getBytes(StandardCharsets.UTF_8));
    }

    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
