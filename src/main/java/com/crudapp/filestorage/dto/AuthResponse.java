package com.crudapp.filestorage.dto;

import java.util.List;

public record AuthResponse(
        String token,
        String tokenType,
        long   expiresInSeconds,
        Integer userId,
        String username,
        List<String> roles
) {}
