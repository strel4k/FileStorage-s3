package com.crudapp.filestorage.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public Mono<ResponseEntity<ApiError>> onRse(ResponseStatusException ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ApiError body = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                ex.getReason(),
                exchange.getRequest().getPath().value(),
                OffsetDateTime.now()
        );
        return Mono.just(ResponseEntity.status(status).body(body));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiError>> onAny(Exception ex, ServerWebExchange exchange) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ApiError body = new ApiError(
                status.value(),
                status.getReasonPhrase(),
                ex.getMessage(),
                exchange.getRequest().getPath().value(),
                OffsetDateTime.now()
        );
        return Mono.just(ResponseEntity.status(status).body(body));
    }
}
