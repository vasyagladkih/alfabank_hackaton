package ru.alfahackaton.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {
    // Deliberately public, isolated test fixture; never a deployable credential.
    private static final String KEY = "TEST_ONLY_ALFA_JWT_KEY_0123456789_not_for_deployment";
    private final JwtTokenProvider provider = new JwtTokenProvider(KEY, 3_600_000);
    private final ObjectMapper mapper = new ObjectMapper();

    private Map<String, Object> claims() {
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("sub", "1"); c.put("username", "client_good"); c.put("role", "CLIENT");
        c.put("iat", now); c.put("exp", now + 3600);
        c.put("iss", "alfa-credit-demo"); c.put("aud", "alfa-credit-ui");
        return c;
    }

    private String signed(String header, Map<String, Object> claims) throws Exception {
        return signedRaw(header, mapper.writeValueAsString(claims));
    }

    private String signedRaw(String header, String claims) throws Exception {
        var enc = Base64.getUrlEncoder().withoutPadding();
        String body = enc.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
                + enc.encodeToString(claims.getBytes(StandardCharsets.UTF_8));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return body + "." + enc.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private String signed(Map<String, Object> claims) throws Exception {
        return signed("{\"alg\":\"HS256\",\"typ\":\"JWT\"}", claims);
    }

    @Test void generatedTokenIsValid() {
        String token = provider.generateToken(1L, "client_good", "CLIENT", "0000000000");
        assertTrue(provider.validateToken(token));
        assertEquals("1", provider.getClaims(token).get("sub"));
    }

    @Test void rejectsTamperedSignature() {
        String token = provider.generateToken(1L, "client_good", "CLIENT", null);
        assertFalse(provider.validateToken(token.substring(0, token.length() - 2) + "zz"));
    }

    @Test void rejectsMissingExpiration() throws Exception {
        var c = claims(); c.remove("exp"); assertFalse(provider.validateToken(signed(c)));
    }

    @Test void rejectsMissingIssuedAt() throws Exception {
        var c = claims(); c.remove("iat"); assertFalse(provider.validateToken(signed(c)));
    }

    @Test void rejectsExpiredAndFutureIssuedTokens() throws Exception {
        var c = claims(); c.put("exp", System.currentTimeMillis() / 1000 - 1);
        assertFalse(provider.validateToken(signed(c)));
        c = claims(); c.put("iat", System.currentTimeMillis() / 1000 + 60);
        assertFalse(provider.validateToken(signed(c)));
    }

    @Test void rejectsWrongAlgorithmAndTypeEvenWhenSignatureMatches() throws Exception {
        assertFalse(provider.validateToken(signed("{\"alg\":\"none\",\"typ\":\"JWT\"}", claims())));
        assertFalse(provider.validateToken(signed("{\"alg\":\"HS256\",\"typ\":\"NOT_JWT\"}", claims())));
    }

    @Test void rejectsDuplicateHeaderFields() throws Exception {
        assertFalse(provider.validateToken(signed("{\"alg\":\"none\",\"alg\":\"HS256\",\"typ\":\"JWT\"}", claims())));
    }

    @Test void rejectsTrailingJsonAfterHeaderOrClaims() throws Exception {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = mapper.writeValueAsString(claims());
        assertFalse(provider.validateToken(signedRaw(header + " {}", payload)));
        assertFalse(provider.validateToken(signedRaw(header, payload + " {}")));
    }

    @Test void validatesOptionalNotBeforeClaim() throws Exception {
        var c = claims();
        c.put("nbf", System.currentTimeMillis() / 1000 + 60);
        assertFalse(provider.validateToken(signed(c)));
        c.put("nbf", "not-a-time");
        assertFalse(provider.validateToken(signed(c)));
        c.put("nbf", System.currentTimeMillis() / 1000 - 1);
        assertTrue(provider.validateToken(signed(c)));
    }

    @Test void rejectsMalformedSubjectAndUnknownRole() throws Exception {
        for (Object sub : new Object[]{"0", "-1", "abc", "9223372036854775808", 1}) {
            var c = claims(); c.put("sub", sub); assertFalse(provider.validateToken(signed(c)));
        }
        var c = claims(); c.put("role", "ADMIN"); assertFalse(provider.validateToken(signed(c)));
    }

    @Test void rejectsWrongIssuerAudienceAndFractionalTime() throws Exception {
        var c = claims(); c.put("iss", "another-service"); assertFalse(provider.validateToken(signed(c)));
        c = claims(); c.put("aud", "another-client"); assertFalse(provider.validateToken(signed(c)));
        c = claims(); c.put("exp", System.currentTimeMillis() / 1000 + 3600.5);
        assertFalse(provider.validateToken(signed(c)));
    }

    @Test void rejectsMissingUsernameAndExcessiveLifetime() throws Exception {
        var c = claims(); c.remove("username"); assertFalse(provider.validateToken(signed(c)));
        c = claims(); c.put("exp", System.currentTimeMillis() / 1000 + 864000);
        assertFalse(provider.validateToken(signed(c)));
    }

    @Test void rejectsMalformedOrOversizedTokenWithoutThrowing() {
        for (String token : new String[]{"", "x.y.z", "a.b.c.d", "x".repeat(9000)}) {
            assertFalse(provider.validateToken(token));
        }
        assertFalse(provider.validateToken(null));
    }

    @Test void rejectsEmptyShortAndRedactedKeyAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("", 3600000));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("short", 3600000));
        assertThrows(IllegalArgumentException.class, () -> new JwtTokenProvider("REPLACE_WITH_LOCAL_DEMO_KEY_NOT_A_REAL_SECRET", 3600000));
    }
}
