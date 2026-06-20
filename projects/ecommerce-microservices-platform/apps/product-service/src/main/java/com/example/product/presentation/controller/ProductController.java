package com.example.product.presentation.controller;

import com.example.product.application.dto.ProductDetail;
import com.example.product.application.dto.ProductListResult;
import com.example.product.application.service.ProductImageService;
import com.example.product.application.service.QueryProductService;
import com.example.product.domain.model.ProductImage;
import com.example.product.domain.model.ProductStatus;
import com.example.product.domain.port.MediaUrlResolver;
import com.example.product.presentation.dto.ImageResponse;
import com.example.product.presentation.dto.ProductDetailResponse;
import com.example.product.presentation.dto.ProductListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private static final int MAX_PAGE_SIZE = 100;

    private final QueryProductService queryProductService;
    private final ProductImageService productImageService;
    private final MediaUrlResolver mediaUrlResolver;

    @GetMapping
    public ProductListResponse list(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);
        ProductListResult result = queryProductService.findAll(categoryId, status, name, page, cappedSize);
        return ProductListResponse.from(result);
    }

    @GetMapping("/{productId}")
    public ProductDetailResponse detail(@PathVariable UUID productId) {
        ProductDetail detail = queryProductService.findById(productId);
        java.util.List<ProductImage> images = productImageService.getImages(productId);
        java.util.List<ImageResponse> imageResponses = images.stream()
                .map(img -> ImageResponse.from(img, mediaUrlResolver.resolve(img.getObjectKey())))
                .toList();
        return ProductDetailResponse.from(detail, imageResponses);
    }
}
