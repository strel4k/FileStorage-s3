package com.crudapp.filestorage.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Стандартная ошибка API")
public record ErrorResponse(
        @Schema(example = "400") int status,
        @Schema(example = "Bad Request") String error,
        @Schema(example = "Причина ошибки") String message,
        @Schema(example = "2025-09-16T13:25:10Z") String timestamp
) {
}
