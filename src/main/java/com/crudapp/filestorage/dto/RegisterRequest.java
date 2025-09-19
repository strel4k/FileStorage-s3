package com.crudapp.filestorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {}

public record RegisterRequest(
        @NotBlank @Size(min=3,max=64) String username,
        @NotBlank @Size(min=6,max=100) String password
) {}

public record AuthResponse(
        String token,
        String tokenType,
        long   expiresInSeconds,
        Integer userId,
        String username,
        List<String> roles
) {}