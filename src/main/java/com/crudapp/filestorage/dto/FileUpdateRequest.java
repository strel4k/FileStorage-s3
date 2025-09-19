package com.crudapp.filestorage.dto;

import jakarta.validation.constraints.NotBlank;

public record FileUpdatedRequest(@NotBlank String name) {
}
