package com.example.review.application.port;

import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import com.example.review.application.result.MyReviewListResult;

import java.util.UUID;

public interface ReviewQueryPort {

    ReviewListResult findByProductId(UUID productId, int page, int size, String sort);

    ReviewSummaryResult getSummaryByProductId(UUID productId);

    MyReviewListResult findByUserId(UUID userId, int page, int size);
}
