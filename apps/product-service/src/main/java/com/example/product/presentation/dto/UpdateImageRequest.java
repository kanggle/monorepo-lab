package com.example.product.presentation.dto;

public record UpdateImageRequest(
        Integer sortOrder,
        Boolean isPrimary
) {}
