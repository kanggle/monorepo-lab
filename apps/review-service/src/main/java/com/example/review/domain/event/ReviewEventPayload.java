package com.example.review.domain.event;

public sealed interface ReviewEventPayload
        permits ReviewCreatedPayload, ReviewUpdatedPayload, ReviewDeletedPayload {

    String reviewId();
}
