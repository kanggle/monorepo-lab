package com.example.product.domain.event;

public interface ProductEventPublisher {
    void publish(ProductEvent event);
}
