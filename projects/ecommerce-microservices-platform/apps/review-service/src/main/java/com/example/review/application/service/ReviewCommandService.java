package com.example.review.application.service;

import com.example.review.application.command.CreateReviewCommand;
import com.example.review.application.command.UpdateReviewCommand;
import com.example.review.application.port.PurchaseVerificationPort;
import com.example.review.application.result.CreateReviewResult;
import com.example.review.application.result.UpdateReviewResult;
import com.example.review.domain.event.ReviewCreatedPayload;
import com.example.review.domain.event.ReviewDeletedPayload;
import com.example.review.domain.event.ReviewEvent;
import com.example.review.domain.event.ReviewEventPublisher;
import com.example.review.domain.event.ReviewUpdatedPayload;
import com.example.review.domain.exception.ProductNotPurchasedException;
import com.example.review.domain.exception.ReviewAccessDeniedException;
import com.example.review.domain.exception.ReviewAlreadyExistsException;
import com.example.review.domain.exception.ReviewNotFoundException;
import com.example.review.domain.model.Review;
import com.example.review.domain.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewCommandService {

    private final ReviewRepository reviewRepository;
    private final PurchaseVerificationPort purchaseVerificationPort;
    private final ReviewEventPublisher reviewEventPublisher;
    private final Clock clock;

    @Transactional
    public CreateReviewResult createReview(CreateReviewCommand command) {
        if (reviewRepository.existsByUserIdAndProductId(command.userId(), command.productId())) {
            throw new ReviewAlreadyExistsException(command.userId(), command.productId());
        }

        boolean purchased = purchaseVerificationPort.hasUserPurchasedProduct(
                command.userId(), command.productId());
        if (!purchased) {
            throw new ProductNotPurchasedException(command.userId(), command.productId());
        }

        Review review = Review.create(
                command.userId(),
                command.productId(),
                command.productName(),
                command.rating(),
                command.title(),
                command.content(),
                clock
        );

        reviewRepository.save(review);

        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                review.getId().toString(),
                review.getProductId().toString(),
                review.getUserId().toString(),
                review.getRatingValue(),
                review.getCreatedAt().toString()
        );
        reviewEventPublisher.publish(ReviewEvent.created(payload, review.getTenantId(), clock));

        return new CreateReviewResult(review.getId());
    }

    @Transactional
    public UpdateReviewResult updateReview(UpdateReviewCommand command) {
        Review review = reviewRepository.findActiveById(command.reviewId())
                .orElseThrow(() -> new ReviewNotFoundException(command.reviewId()));

        if (!review.isOwnedBy(command.userId())) {
            throw new ReviewAccessDeniedException(command.userId(), command.reviewId());
        }

        review.update(command.rating(), command.title(), command.content(), clock);
        reviewRepository.save(review);

        ReviewUpdatedPayload payload = new ReviewUpdatedPayload(
                review.getId().toString(),
                review.getProductId().toString(),
                review.getUserId().toString(),
                review.getRatingValue(),
                review.getUpdatedAt().toString()
        );
        reviewEventPublisher.publish(ReviewEvent.updated(payload, review.getTenantId(), clock));

        return new UpdateReviewResult(review.getId());
    }

    @Transactional
    public void deleteReview(UUID userId, UUID reviewId) {
        Review review = reviewRepository.findActiveById(reviewId)
                .orElseThrow(() -> new ReviewNotFoundException(reviewId));

        if (!review.isOwnedBy(userId)) {
            throw new ReviewAccessDeniedException(userId, reviewId);
        }

        review.softDelete(clock);
        reviewRepository.save(review);

        ReviewDeletedPayload payload = new ReviewDeletedPayload(
                review.getId().toString(),
                review.getProductId().toString(),
                review.getUserId().toString(),
                review.getUpdatedAt().toString()
        );
        reviewEventPublisher.publish(ReviewEvent.deleted(payload, review.getTenantId(), clock));
    }
}
