package ru.alfahackaton.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.alfahackaton.api.models.FnsDto;

import java.time.Duration;
import java.util.Map;

/**
 * Клиентский сервис интеграции с межведомственной службой ФНС.
 * Выполняет вызов с установленным таймаутом и безопасной деградацией при сбоях.
 */
@Service
public class FnsClientService {

    private static final Logger log = LoggerFactory.getLogger(FnsClientService.class);

    private final RestClient restClient;
    private final String fnsUrl;

    public FnsClientService(
            RestClient.Builder restClientBuilder,
            @Value("${fns.service.url:http://localhost:8080/api/v1/stubs/fns/check}") String fnsUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(1000));
        requestFactory.setReadTimeout(Duration.ofMillis(1000));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        this.fnsUrl = fnsUrl;
    }

    /**
     * Запрос сведений о регистрации, статусе и блокировках счетов налогоплательщика.
     *
     * @param inn ИНН организации или индивидуального предпринимателя
     * @return FnsDto при успешном ответе, либо null при недоступности сервиса или отсутствии данных
     */
    public FnsDto getData(String inn) {
        if (inn == null || inn.isBlank()) return null;

        try {
            return restClient.post()
                    .uri(fnsUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("inn", inn))
                    .retrieve()
                    .body(FnsDto.class);
        } catch (Exception e) {
            log.warn("Не удалось получить сведения из ФНС для ИНН {}: {}", inn, e.getMessage());
            return null;
        }
    }
}
