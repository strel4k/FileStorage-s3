package com.crudapp.filestorage.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FileUpdateRequest(@NotBlank @Size(min = 1, max = 255) String name) {
}
