package com.wms.admin.api.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateUserRequest(
        @NotBlank @Size(max = 40) String userCode,
        @NotBlank @Email @Size(max = 200) String email,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 30) String phone,
        UUID defaultWarehouseId) {
}
