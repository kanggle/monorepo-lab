package com.example.product.presentation.dto;

import java.util.List;

public record ImageListResponse(
        List<ImageResponse> images
) {}
