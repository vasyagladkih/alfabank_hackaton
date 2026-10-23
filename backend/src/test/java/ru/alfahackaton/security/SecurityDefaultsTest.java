package ru.alfahackaton.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import ru.alfahackaton.api.interceptor.GlobalExceptionHandler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SecurityDefaultsTest {
    @Test void corsDoesNotReflectAnUntrustedOrigin() {
        var config = new SecurityConfig(mock(JwtAuthenticationFilter.class));
        var cors = config.corsConfigurationSource().getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/client/applications"));
        assertNotNull(cors);
        assertNull(cors.checkOrigin("https://untrusted.invalid"));
        assertNotEquals(Boolean.TRUE, cors.getAllowCredentials());
    }

    @Test void unexpectedErrorsDoNotExposeInternalDetails() {
        var response = new GlobalExceptionHandler().handleGeneric(new RuntimeException("jdbc:postgresql://secret-host/private-table"));
        assertEquals(500, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Внутренняя ошибка сервиса. Повторите запрос позже.", response.getBody().getMessage());
    }

    @Test void invalidJsonHasSafeClientErrorStatus() {
        var invalid = new org.springframework.http.converter.HttpMessageNotReadableException(
                "Untrusted JSON content", new org.springframework.mock.http.MockHttpInputMessage(new byte[0]));
        var response = new GlobalExceptionHandler().handleMalformedRequest(invalid);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("BAD_REQUEST", response.getBody().getCode());
        assertFalse(response.getBody().getMessage().contains("Untrusted"));
    }
}
