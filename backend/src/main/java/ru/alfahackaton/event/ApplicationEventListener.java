package ru.alfahackaton.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.alfahackaton.persistance.repository.ApplicationRepository;
import ru.alfahackaton.service.ApplicationService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
public class ApplicationEventListener {

    private final ApplicationService applicationService;
    private final ApplicationRepository applicationRepository;

    public ApplicationEventListener(ApplicationService applicationService, ApplicationRepository applicationRepository) {
        this.applicationService = applicationService;
        this.applicationRepository = applicationRepository;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onApplicationCreated(ApplicationCreatedEvent event) {
        log.info("Получено событие создания заявки #{}. Запуск скорингового конвейера...", event.applicationId());
        try {
            applicationService.processApplication(event.applicationId());
        } catch (Exception e) {
            log.error("Критическая ошибка при обработке заявки #{}: {}", event.applicationId(), e.getMessage(), e);
        }
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        log.info("Приложение готово к работе. Запуск самовосстановления зависших заявок в статусе PROCESSING...");
        try {
            List<Long> processingIds = applicationRepository.findProcessingApplicationIds();
            if (processingIds.isEmpty()) {
                log.info("Зависших заявок со статусом PROCESSING не обнаружено.");
                return;
            }
            log.info("Обнаружено {} зависших заявок. Инициирован асинхронный запуск скоринга...", processingIds.size());
            for (Long id : processingIds) {
                CompletableFuture.runAsync(() -> {
                    try {
                        log.info("Восстановление обработки заявки #{}...", id);
                        applicationService.processApplication(id);
                    } catch (Exception e) {
                        log.error("Критическая ошибка при восстановлении обработки заявки #{}: {}", id, e.getMessage(), e);
                    }
                });
            }
        } catch (Exception e) {
            log.error("Ошибка при восстановлении зависших заявок: {}", e.getMessage(), e);
        }
    }
}
