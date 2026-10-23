package ru.alfahackaton.demo;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DemoSeedCatalogTest {
    @Test
    void catalogPreservesFixedClockAndCoversRequiredDecisionPaths() {
        var catalog = DemoSeedCatalog.load();
        assertEquals(LocalDate.parse("2026-10-23"), catalog.asOf());
        assertEquals(Set.of("APPROVED", "REJECTED", "MANUAL_REVIEW", "OFFER_PROPOSED"),
                catalog.scenarios().stream().map(DemoScenario::expectedOutcome).collect(Collectors.toSet()));
        assertEquals(catalog.scenarios().size(), catalog.scenarios().stream().map(DemoScenario::code).distinct().count());
        assertTrue(catalog.scenarios().stream().allMatch(s -> Set.of("client_good", "client_bad").contains(s.owner())));
        assertTrue(catalog.scenarios().stream().anyMatch(s -> s.code().equals("missing-data")));
        assertTrue(catalog.scenarios().stream().anyMatch(s -> s.code().equals("stale-data")));
        assertTrue(catalog.scenarios().stream().allMatch(s -> s.snapshotJson().contains("SYNTHETIC_INTERNAL")));
    }
}
