package com.crudapp.filestorage.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String,Object>> handleRse(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(Map.of(
                        "status", e.getStatusCode().value(),
                        "error", e.getReason() != null ? e.getReason() : e.getMessage()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String,Object>> handleAny(Exception e) {
        log.error("Unhandled error", e);
        return ResponseEntity.status(500).body(Map.of(
                "status", 500,
                "error", "Internal Server Error"
        ));
    }
}
