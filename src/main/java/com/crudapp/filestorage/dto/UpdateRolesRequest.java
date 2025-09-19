package com.crudapp.filestorage.dto;

import java.util.List;

public record UpdateRolesRequest(
        List<String> roles
) {
}
