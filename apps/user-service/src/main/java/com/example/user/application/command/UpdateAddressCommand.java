package com.example.user.application.command;

import java.util.UUID;

public record UpdateAddressCommand(
        UUID userId,
        UUID addressId,
        String label,
        String recipientName,
        String phone,
        String zipCode,
        String address1,
        String address2,
        Boolean isDefault
) {}
