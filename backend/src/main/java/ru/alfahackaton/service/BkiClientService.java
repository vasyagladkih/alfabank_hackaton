package ru.alfahackaton.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import ru.alfahackaton.api.models.BkiDto;

import java.time.Duration;
import java.util.Map;

/**
 * Клиентский сервис интеграции с Бюро кредитных историй (БКИ).
 * Выполняет вызов с установленным таймаутом и безопасной деградацией при сбоях.
 */
@Service
public class BkiClientService {

    private static final Logger log = LoggerFactory.getLogger(BkiClientService.class);

    private final RestClient restClient;
    private final String bkiUrl;

    public BkiClientService(
            RestClient.Builder restClientBuilder,
            @Value("${bki.service.url:http://localhost:8080/api/v1/stubs/bki/report}") String bkiUrl
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(1000));
        requestFactory.setReadTimeout(Duration.ofMillis(1000));

        this.restClient = restClientBuilder
                .requestFactory(requestFactory)
                .build();
        this.bkiUrl = bkiUrl;
    }

    /**
     * Запрос кредитного отчета и скорингового балла заемщика.
     *
     * @param inn ИНН организации или индивидуального предпринимателя
     * @return BkiDto при успешном ответе, либо null при недоступности сервиса или отсутствии данных
     */
    public BkiDto getData(String inn) {
        if (inn == null || inn.isBlank()) return null;
        try {
            return restClient.post()
                    .uri(bkiUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("inn", inn))
                    .retrieve()
                    .body(BkiDto.class);
        } catch (Exception e) {
            log.warn("Не удалось получить кредитный отчет из БКИ для ИНН {}: {}", inn, e.getMessage());
            return null;
        }
    }
}
