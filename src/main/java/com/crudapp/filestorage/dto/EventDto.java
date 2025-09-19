package com.crudapp.filestorage.dto;

import java.time.Instant;

public record EventDto(
        Integer id,
        Integer userId,
        Integer fileId,
        String status,
        String message,
        Instant createdAt
) {}