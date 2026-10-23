package ru.alfahackaton.persistance;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Explicit private Jackson instance: no dependency on Spring's Jackson 3 HTTP mapper. */
public final class JsonDocuments {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);

    private JsonDocuments() {}

    public static String canonicalize(String json) {
        try {
            Object parsed = MAPPER.readValue(json, Object.class);
            if (!(parsed instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("Evidence JSON must be an object");
            }
            return MAPPER.writeValueAsString(normalize(parsed));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid evidence JSON", e);
        }
    }

    public static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> sorted = new TreeMap<>();
            values.forEach((key, item) -> sorted.put((String) key, normalize(item)));
            return sorted;
        }
        if (value instanceof List<?> values) {
            return values.stream().map(JsonDocuments::normalize).toList();
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.stripTrailingZeros();
        }
        return value;
    }
}
