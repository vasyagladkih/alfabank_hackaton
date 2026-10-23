package ru.alfahackaton.service;

import tools.jackson.databind.json.JsonMapper;
import ru.alfahackaton.persistance.JsonDocuments;

/** Private explicit Boot4/Jackson3 codec for records and Java time; no HTTP-mapper bean assumptions. */
public final class RiskJson {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private RiskJson() {}
    public static String write(Object value) {
        return JsonDocuments.canonicalize(JSON.writeValueAsString(value));
    }
    public static <T> T read(String json, Class<T> type) {
        return JSON.readValue(json, type);
    }
}
