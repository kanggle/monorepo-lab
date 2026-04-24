'use client';

import { useState } from 'react';
import { useProductReviews } from '../model/use-product-reviews';
import { useCreateReview } from '../model/use-create-review';
import { useReviewActions } from '../model/use-review-actions';
import { useAuth } from '@/features/auth';
import { ErrorMessage, EmptyState } from '@repo/ui';
import { ReviewForm } from './ReviewForm';
import { RatingSummary } from './RatingSummary';
import { ReviewCard } from './ReviewCard';
import { Pagination } from './Pagination';
import { ReviewListSkeleton } from './ReviewListSkeleton';
import styles from './ReviewList.module.css';
import type { ReviewItem } from '@repo/types';

interface ReviewListProps {
  productId: string;
}

const PAGE_SIZE = 10;

export function ReviewList({ productId }: ReviewListProps) {
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);

  const { data, isLoading, isError, refetch } = useProductReviews(productId, page, PAGE_SIZE);
  const createReview = useCreateReview();

  const { user, isAuthenticated } = useAuth();

  const { editingReview, setEditingReview, handleUpdate, handleDelete, isUpdatePending } =
    useReviewActions<ReviewItem>();

  const reviews = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));

  async function handleCreate(formData: { rating: number; title: string; content: string }) {
    await createReview.mutateAsync({
      productId,
      rating: formData.rating,
      title: formData.title,
      content: formData.content,
    });
    setShowForm(false);
  }

  function handlePageChange(newPage: number) {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  }

  return (
    <div className={styles.wrapper}>
      <h2
        style={{
          fontSize: 'var(--font-size-lg, 1.125rem)',
          fontWeight: 'var(--font-weight-semibold)',
          marginBottom: 'var(--space-4)',
        }}
      >
        상품 리뷰
      </h2>

      <RatingSummary productId={productId} />

      <div style={{ marginTop: 'var(--space-6)' }}>
        {isAuthenticated && !showForm && !editingReview && (
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => setShowForm(true)}
            style={{ marginBottom: 'var(--space-4)' }}
          >
            리뷰 작성
          </button>
        )}

        {showForm && (
          <div
            style={{
              padding: 'var(--space-4)',
              border: '1px solid var(--color-border-light)',
              borderRadius: 'var(--radius-md)',
              marginBottom: 'var(--space-4)',
            }}
          >
            <h3
              style={{
                fontSize: 'var(--font-size-md, 1rem)',
                fontWeight: 'var(--font-weight-semibold)',
                marginBottom: 'var(--space-3)',
              }}
            >
              리뷰 작성
            </h3>
            <ReviewForm
              onSubmit={handleCreate}
              onCancel={() => setShowForm(false)}
              submitLabel="리뷰 작성"
              isPending={createReview.isPending}
            />
          </div>
        )}

        {isLoading && <ReviewListSkeleton count={3} />}

        {isError && (
          <ErrorMessage
            message="리뷰를 불러오는데 실패했습니다."
            onRetry={() => refetch()}
          />
        )}

        {!isLoading && !isError && reviews.length === 0 && (
          <EmptyState message="아직 리뷰가 없습니다." />
        )}

        {!isLoading && !isError && reviews.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
            {reviews.map((review) => (
              <ReviewCard
                key={review.reviewId}
                reviewId={review.reviewId}
                rating={review.rating}
                title={review.title}
                content={review.content}
                createdAt={review.createdAt}
                isEditing={editingReview?.reviewId === review.reviewId}
                showActions={!!(user && user.userId === review.userId)}
                isUpdatePending={isUpdatePending}
                onEdit={() => setEditingReview(review)}
                onDelete={() => handleDelete(review.reviewId)}
                onUpdate={handleUpdate}
                onCancelEdit={() => setEditingReview(null)}
              />
            ))}
          </div>
        )}

        {!isLoading && !isError && totalElements > PAGE_SIZE && (
          <Pagination
            page={page}
            totalPages={totalPages}
            onPageChange={handlePageChange}
            ariaLabel="리뷰 페이지네이션"
            style={{ marginTop: 'var(--space-6)' }}
          />
        )}
      </div>
    </div>
  );
}
