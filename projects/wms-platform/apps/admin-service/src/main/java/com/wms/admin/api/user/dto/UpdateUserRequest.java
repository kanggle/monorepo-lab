package com.wms.admin.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateUserRequest(
        @Size(max = 200) String name,
        @Email @Size(max = 200) String email,
        @Size(max = 30) String phone,
        UUID defaultWarehouseId) {
}
