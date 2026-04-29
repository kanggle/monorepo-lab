package com.example.review.interfaces.controller;

import com.example.review.application.command.CreateReviewCommand;
import com.example.review.application.command.UpdateReviewCommand;
import com.example.review.application.result.CreateReviewResult;
import com.example.review.application.result.MyReviewListResult;
import com.example.review.application.result.ReviewListResult;
import com.example.review.application.result.ReviewSummaryResult;
import com.example.review.application.result.UpdateReviewResult;
import com.example.review.application.service.ReviewCommandService;
import com.example.review.application.service.ReviewQueryService;
import com.example.review.interfaces.dto.CreateReviewRequest;
import com.example.review.interfaces.dto.CreateReviewResponse;
import com.example.review.interfaces.dto.MyReviewListResponse;
import com.example.review.interfaces.dto.ReviewListResponse;
import com.example.review.interfaces.dto.ReviewSummaryResponse;
import com.example.review.interfaces.dto.UpdateReviewRequest;
import com.example.review.interfaces.dto.UpdateReviewResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Set;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/reviews")
public class ReviewController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("createdAt", "rating");

    private final ReviewCommandService reviewCommandService;
    private final ReviewQueryService reviewQueryService;

    @PostMapping
    public ResponseEntity<CreateReviewResponse> createReview(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        CreateReviewCommand command = new CreateReviewCommand(
                UUID.fromString(userId),
                request.productId(),
                request.productName(),
                request.rating(),
                request.title(),
                request.content()
        );
        CreateReviewResult result = reviewCommandService.createReview(command);
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateReviewResponse.from(result));
    }

    @GetMapping("/products/{productId}")
    public ResponseEntity<ReviewListResponse> getProductReviews(
            @PathVariable UUID productId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        validateSortParam(sort);
        ReviewListResult result = reviewQueryService.getProductReviews(productId, sanitizePage(page), sanitizeSize(size), sort);
        return ResponseEntity.ok(ReviewListResponse.from(result));
    }

    @GetMapping("/products/{productId}/summary")
    public ResponseEntity<ReviewSummaryResponse> getProductReviewSummary(
            @PathVariable UUID productId
    ) {
        ReviewSummaryResult result = reviewQueryService.getProductReviewSummary(productId);
        return ResponseEntity.ok(ReviewSummaryResponse.from(result));
    }

    @GetMapping("/me")
    public ResponseEntity<MyReviewListResponse> getMyReviews(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        MyReviewListResult result = reviewQueryService.getMyReviews(UUID.fromString(userId), sanitizePage(page), sanitizeSize(size));
        return ResponseEntity.ok(MyReviewListResponse.from(result));
    }

    @PutMapping("/{reviewId}")
    public ResponseEntity<UpdateReviewResponse> updateReview(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @PathVariable UUID reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        UpdateReviewCommand command = new UpdateReviewCommand(
                UUID.fromString(userId),
                reviewId,
                request.rating(),
                request.title(),
                request.content()
        );
        UpdateReviewResult result = reviewCommandService.updateReview(command);
        return ResponseEntity.ok(UpdateReviewResponse.from(result));
    }

    private void validateSortParam(String sort) {
        String field = sort.split(",")[0].trim();
        if (!ALLOWED_SORT_FIELDS.contains(field)) {
            throw new IllegalArgumentException("Invalid sort field: " + field);
        }
    }

    private int sanitizePage(int page) {
        return Math.max(page, 0);
    }

    private int sanitizeSize(int size) {
        return size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    }

    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @RequestHeader("X-User-Id") @NotBlank(message = "X-User-Id header is required") String userId,
            @PathVariable UUID reviewId
    ) {
        reviewCommandService.deleteReview(UUID.fromString(userId), reviewId);
        return ResponseEntity.noContent().build();
    }
}
