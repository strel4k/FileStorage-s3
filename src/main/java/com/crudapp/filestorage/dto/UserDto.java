package com.crudapp.filestorage.dto;

import java.util.List;

public record UserDto(
        Integer id,
        String username,
        List<String> roles
) {}