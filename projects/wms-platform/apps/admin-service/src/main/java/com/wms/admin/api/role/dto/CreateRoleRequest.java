package com.wms.admin.api.role.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateRoleRequest(
        @NotBlank @Size(max = 40) String roleCode,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description,
        @NotEmpty List<String> permissionsJson) {
}
