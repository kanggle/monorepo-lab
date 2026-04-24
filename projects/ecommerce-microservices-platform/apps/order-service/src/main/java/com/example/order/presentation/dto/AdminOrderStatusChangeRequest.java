package com.example.order.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminOrderStatusChangeRequest(
        @NotBlank(message = "status is required") String status
) {}
