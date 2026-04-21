package com.example.product.application.command;

public record VariantCommand(
        String optionName,
        int stock,
        long additionalPrice
) {}
