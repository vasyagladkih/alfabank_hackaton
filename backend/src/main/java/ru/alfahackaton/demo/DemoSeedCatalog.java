package ru.alfahackaton.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import ru.alfahackaton.persistance.JsonDocuments;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed fixture catalog, independent of the risk engine and its later implementation package. */
public record DemoSeedCatalog(String schemaVersion, LocalDate asOf, List<DemoScenario> scenarios,
                               Map<String, String> profileScenarios) {
    private static final ObjectMapper JSON = new ObjectMapper();

    public static DemoSeedCatalog load() {
        try (var stream = DemoSeedCatalog.class.getResourceAsStream("/demo/scenarios-v1.json")) {
            if (stream == null) throw new IllegalStateException("Synthetic demo fixture is missing");
            JsonNode document = JSON.readTree(stream);
            if (!document.path("demoOnly").asBoolean(false)) throw new IllegalArgumentException("Demo marker is required");
            String version = document.required("schemaVersion").asText();
            LocalDate asOf = LocalDate.parse(document.required("asOf").asText());
            List<DemoScenario> scenarios = new ArrayList<>();
            Set<String> codes = new HashSet<>();
            for (JsonNode row : document.required("scenarios")) {
                String code = row.required("code").asText();
                String owner = row.required("owner").asText();
                if (!code.matches("[a-z0-9-]+") || !codes.add(code)
                        || !Set.of("client_good", "client_bad").contains(owner)) {
                    throw new IllegalArgumentException("Invalid or duplicate synthetic scenario identity");
                }
                long amount = row.required("amountRub").longValue();
                int months = row.required("termMonths").intValue();
                if (amount < 100000 || amount > 50000000 || months < 1 || months > 60) {
                    throw new IllegalArgumentException("Synthetic request is outside the public API contract");
                }
                if (!asOf.toString().equals(row.required("inputs").required("asOf").asText())) {
                    throw new IllegalArgumentException("Fixture valuation dates disagree");
                }
                ObjectNode snapshot = JSON.createObjectNode();
                snapshot.put("demoOnly", true);
                snapshot.put("scenarioCode", code);
                snapshot.put("scenarioTitle", row.required("title").asText());
                snapshot.put("companyName", owner.equals("client_good") ? "ООО \"Ромашка\"" : "ООО \"Однодневка\"");
                snapshot.put("inn", owner.equals("client_good") ? "7701234567" : "7702999999");
                snapshot.put("purpose", row.required("purpose").asText());
                snapshot.set("inputs", row.required("inputs").deepCopy());
                snapshot.putObject("request").put("amountRub", amount).put("termMonths", months);
                scenarios.add(new DemoScenario(code, row.required("title").asText(), owner, amount, months,
                        row.required("purpose").asText(), row.required("expectedOutcome").asText(),
                        row.required("expectedPath").asText(), JsonDocuments.canonicalize(snapshot.toString())));
            }
            if (scenarios.isEmpty()) throw new IllegalArgumentException("Synthetic catalog must not be empty");
            Map<String, String> profiles = Map.of(
                    "client_good", document.required("profileScenarios").required("client_good").asText(),
                    "client_bad", document.required("profileScenarios").required("client_bad").asText());
            if (!codes.containsAll(profiles.values())) throw new IllegalArgumentException("Default profile fixture is missing");
            return new DemoSeedCatalog(version, asOf, List.copyOf(scenarios), profiles);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read deterministic synthetic fixture", e);
        }
    }
}
