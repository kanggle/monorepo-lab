package com.example.product.domain.exception;

import java.util.UUID;

public class ImageLimitExceededException extends RuntimeException {

    public ImageLimitExceededException(UUID productId) {
        super("Maximum number of images reached for product: " + productId);
    }
}
