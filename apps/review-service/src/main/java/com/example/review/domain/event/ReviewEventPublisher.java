package com.example.review.domain.event;

public interface ReviewEventPublisher {
    void publish(ReviewEvent event);
}
