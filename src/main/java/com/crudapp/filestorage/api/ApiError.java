package com.crudapp.filestorage.api;

import java.time.OffsetDateTime;

public record ApiError(
        int status,
        String error,
        String message,
        String path,
        OffsetDateTime timestamp
) {
}
