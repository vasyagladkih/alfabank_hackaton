package ru.alfahackaton.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.alfahackaton.api.models.BkiDto;
import ru.alfahackaton.persistance.entity.ApplicationBkiSnapshotEntity;
import ru.alfahackaton.persistance.repository.ApplicationBkiSnapshotRepository;

import java.time.OffsetDateTime;

/**
 * Изолированный сервис обогащения заявки кредитным отчетом из БКИ.
 * Вычисляет риск-бакет просрочки и сохраняет снимок в application_bki_snapshots.
 */
@Service
public class BkiEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(BkiEnrichmentService.class);

    private final BkiClientService bkiClientService;
    private final ApplicationBkiSnapshotRepository bkiSnapshotRepository;

    public BkiEnrichmentService(
            BkiClientService bkiClientService,
            ApplicationBkiSnapshotRepository bkiSnapshotRepository
    ) {
        this.bkiClientService = bkiClientService;
        this.bkiSnapshotRepository = bkiSnapshotRepository;
    }

    /**
     * Запрос данных БКИ и сохранение снимка в БД.
     *
     * @param applicationId идентификатор заявки
     * @param inn           ИНН организации
     * @return сохраненный снимок БКИ либо null при технической недоступности
     */
    public ApplicationBkiSnapshotEntity enrich(Long applicationId, String inn) {
        log.info("Запуск обогащения БКИ по заявке #{} (ИНН {})", applicationId, inn);
        BkiDto dto = bkiClientService.getData(inn);
        if (dto == null) {
            log.warn("Сервис БКИ не вернул данные для заявки #{}", applicationId);
            return null;
        }

        String riskBucket = determineRiskBucket(dto.currentOverdueDays(), dto.cntOverdue90());

        ApplicationBkiSnapshotEntity entity = new ApplicationBkiSnapshotEntity(
                null,
                applicationId,
                inn,
                dto.bkiScore(),
                dto.monthlyPaymentRub(),
                dto.currentOverdueDays(),
                dto.cntOverdue90(),
                riskBucket,
                OffsetDateTime.now()
        );

        return bkiSnapshotRepository.save(entity);
    }

    private String determineRiskBucket(Integer overdueDays, Integer cntOverdue90) {
        if (cntOverdue90 != null && cntOverdue90 > 0) return "DEFAULT (90+ DPD)";
        if (overdueDays == null || overdueDays == 0) return "CURRENT (0 DPD)";
        if (overdueDays <= 30) return "WATCHLIST (1-30 DPD)";
        if (overdueDays <= 60) return "SUBSTANDARD (31-60 DPD)";
        if (overdueDays <= 90) return "DOUBTFUL (61-90 DPD)";
        return "DEFAULT (90+ DPD)";
    }
}
