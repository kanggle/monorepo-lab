package com.example.user.application.command;

import java.util.UUID;

public record CreateAddressCommand(
        UUID userId,
        String label,
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2,
        boolean isDefault
) {}
