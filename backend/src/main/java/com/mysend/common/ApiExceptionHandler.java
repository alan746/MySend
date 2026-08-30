package com.mysend.common;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private final MeterRegistry registry;

    public ApiExceptionHandler(MeterRegistry registry) {
        this.registry = registry;
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiProblem> handleApiException(ApiException exception) {
        recordProblem(exception.code(), exception.status());
        return ResponseEntity.status(exception.status()).body(new ApiProblem(
                exception.code(),
                exception.getMessage(),
                Map.of(),
                Instant.now()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> handleValidation(MethodArgumentNotValidException exception) {
        recordProblem("VALIDATION_FAILED", HttpStatus.BAD_REQUEST);
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(new ApiProblem(
                "VALIDATION_FAILED",
                "Please check the highlighted fields",
                fields,
                Instant.now()
        ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiProblem> handleUploadSize(MaxUploadSizeExceededException exception) {
        recordProblem("UPLOAD_TOO_LARGE", HttpStatus.PAYLOAD_TOO_LARGE);
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiProblem(
                "UPLOAD_TOO_LARGE",
                "The upload is larger than the server request limit",
                Map.of(),
                Instant.now()
        ));
    }

    private void recordProblem(String code, HttpStatus status) {
        Counter.builder("mysend.api.problems")
                .tag("code", code)
                .tag("status", Integer.toString(status.value()))
                .register(registry)
                .increment();
    }

    public record ApiProblem(
            String code,
            String message,
            Map<String, String> fields,
            Instant timestamp
    ) {
    }
}
