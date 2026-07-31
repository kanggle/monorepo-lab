package com.example.review.application.port;

import com.example.common.page.PageResult;
import com.example.review.application.result.MyReviewItem;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;

import java.util.UUID;

public interface ReviewQueryPort {

    ReviewListResult findByProductId(UUID productId, int page, int size, String sort);

    ReviewSummaryResult getSummaryByProductId(UUID productId);

    PageResult<MyReviewItem> findByUserId(UUID userId, int page, int size);
}
