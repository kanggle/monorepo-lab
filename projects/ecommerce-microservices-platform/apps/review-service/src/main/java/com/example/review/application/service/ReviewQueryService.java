package com.example.review.application.service;

import com.example.review.application.port.ReviewQueryPort;
import com.example.review.application.result.MyReviewListResult;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewQueryService {

    private final ReviewQueryPort reviewQueryPort;

    public ReviewListResult getProductReviews(UUID productId, int page, int size, String sort) {
        return reviewQueryPort.findByProductId(productId, page, size, sort);
    }

    public ReviewSummaryResult getProductReviewSummary(UUID productId) {
        return reviewQueryPort.getSummaryByProductId(productId);
    }

    public MyReviewListResult getMyReviews(UUID userId, int page, int size) {
        return reviewQueryPort.findByUserId(userId, page, size);
    }
}
