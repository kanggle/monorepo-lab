package com.example.product.domain.exception;

import java.util.UUID;

public class InvalidCategoryException extends RuntimeException {

    public InvalidCategoryException(UUID categoryId) {
        super("Category not found: " + categoryId);
    }
}
