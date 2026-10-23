package ru.alfahackaton.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="demo.processing.enabled", havingValue="true", matchIfMissing=true)
public class ScoringScheduler {
    private final ScoringProcessor processor;
    private final int batchSize;

    public ScoringScheduler(ScoringProcessor processor, @Value("${demo.processing.batch-size:8}") int batchSize) {
        if (batchSize < 1 || batchSize > 64) throw new IllegalArgumentException("Demo batch must contain 1–64 items");
        this.processor = processor;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString="${demo.processing.delay-ms:1000}", initialDelayString="${demo.processing.initial-delay-ms:2000}")
    public void tick() { processor.processBatch(batchSize); }
}
