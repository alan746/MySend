package com.mysend.common;

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

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiProblem> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.status()).body(new ApiProblem(
                exception.code(),
                exception.getMessage(),
                Map.of(),
                Instant.now()
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiProblem> handleValidation(MethodArgumentNotValidException exception) {
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
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(new ApiProblem(
                "UPLOAD_TOO_LARGE",
                "The upload is larger than the server request limit",
                Map.of(),
                Instant.now()
        ));
    }

    public record ApiProblem(
            String code,
            String message,
            Map<String, String> fields,
            Instant timestamp
    ) {
    }
}
