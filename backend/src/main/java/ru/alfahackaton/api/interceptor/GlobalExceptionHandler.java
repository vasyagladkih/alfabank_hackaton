package ru.alfahackaton.api.interceptor;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import ru.alfahackaton.dto.ErrorResponse;

import java.time.OffsetDateTime;
import java.time.ZoneId;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException e) {
        ErrorResponse error = new ErrorResponse("FORBIDDEN", e.getMessage(), OffsetDateTime.now(ZoneId.systemDefault()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<ErrorResponse> handleIllegalArgument(RuntimeException e) {
        ErrorResponse error = new ErrorResponse("BAD_REQUEST", e.getMessage(), OffsetDateTime.now(ZoneId.systemDefault()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        ErrorResponse error = new ErrorResponse("VALIDATION_ERROR", "Некорректные параметры запроса", OffsetDateTime.now(ZoneId.systemDefault()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        ErrorResponse error = new ErrorResponse("INTERNAL_ERROR", e.getMessage(), OffsetDateTime.now(ZoneId.systemDefault()));
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
