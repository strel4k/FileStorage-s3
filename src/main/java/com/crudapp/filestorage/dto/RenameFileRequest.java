package com.crudapp.filestorage.dto;

import jakarta.validation.constraints.NotBlank;

public record RenameFileRequest(@NotBlank String name) {
}
