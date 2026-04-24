package com.example.review.interfaces.dto;

import com.example.review.application.result.UpdateReviewResult;

public record UpdateReviewResponse(String reviewId) {

    public static UpdateReviewResponse from(UpdateReviewResult result) {
        return new UpdateReviewResponse(result.reviewId().toString());
    }
}
