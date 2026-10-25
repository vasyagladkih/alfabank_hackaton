package ru.alfahackaton.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.alfahackaton.api.models.FnsDto;
import ru.alfahackaton.persistance.entity.ApplicationFnsSnapshotEntity;
import ru.alfahackaton.persistance.repository.ApplicationFnsSnapshotRepository;

import java.time.OffsetDateTime;

/**
 * Изолированный сервис обогащения заявки сведениями из ФНС.
 * Сохраняет снимок в собственную таблицу application_fns_snapshots.
 */
@Service
public class FnsEnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(FnsEnrichmentService.class);

    private final FnsClientService fnsClientService;
    private final ApplicationFnsSnapshotRepository fnsSnapshotRepository;

    public FnsEnrichmentService(
            FnsClientService fnsClientService,
            ApplicationFnsSnapshotRepository fnsSnapshotRepository
    ) {
        this.fnsClientService = fnsClientService;
        this.fnsSnapshotRepository = fnsSnapshotRepository;
    }

    /**
     * Запрос данных ФНС и сохранение снимка в БД.
     *
     * @param applicationId идентификатор заявки
     * @param inn           ИНН организации
     * @return сохраненный снимок ФНС либо null при технической недоступности
     */
    @Transactional
    public ApplicationFnsSnapshotEntity enrich(Long applicationId, String inn) {
        log.info("Запуск обогащения ФНС по заявке #{} (ИНН {})", applicationId, inn);
        FnsDto dto = fnsClientService.getData(inn);
        if (dto == null) {
            log.warn("Сервис ФНС не вернул данные для заявки #{}", applicationId);
            return null;
        }

        ApplicationFnsSnapshotEntity entity = new ApplicationFnsSnapshotEntity(
                null,
                applicationId,
                inn,
                dto.companyName(),
                dto.stateStatus(),
                dto.hasTaxBlocks(),
                dto.businessAgeMonths(),
                OffsetDateTime.now()
        );

        return fnsSnapshotRepository.save(entity);
    }
}
