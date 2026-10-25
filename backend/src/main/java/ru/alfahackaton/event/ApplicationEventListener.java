package ru.alfahackaton.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import ru.alfahackaton.service.ApplicationService;

@Component
public class ApplicationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventListener.class);

    private final ApplicationService applicationService;

    public ApplicationEventListener(ApplicationService applicationService) {
        this.applicationService = applicationService;
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
}
