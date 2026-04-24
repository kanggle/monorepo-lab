package com.example.product.presentation.controller;

import com.example.product.application.service.ProductImageService;
import com.example.product.domain.model.ProductImage;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.presentation.dto.ImageListResponse;
import com.example.product.presentation.dto.ImageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products/{productId}/images")
@RequiredArgsConstructor
public class ProductImageController {

    private final ProductImageService productImageService;
    private final MediaUrlResolver mediaUrlResolver;

    @GetMapping
    public ImageListResponse listImages(@PathVariable UUID productId) {
        List<ProductImage> images = productImageService.getImages(productId);
        List<ImageResponse> responseList = images.stream()
                .map(img -> ImageResponse.from(img, mediaUrlResolver.resolve(img.getObjectKey())))
                .toList();
        return new ImageListResponse(responseList);
    }
}
