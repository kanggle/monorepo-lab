package com.wms.admin.api.role.dto;

import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateRoleRequest(
        @Size(max = 100) String name,
        @Size(max = 500) String description,
        List<String> permissionsJson) {
}
