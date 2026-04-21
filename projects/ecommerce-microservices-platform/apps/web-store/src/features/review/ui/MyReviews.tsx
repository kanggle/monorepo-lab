'use client';

import { useState } from 'react';
import Link from 'next/link';
import { useMyReviews } from '../model/use-my-reviews';
import { useReviewActions } from '../model/use-review-actions';
import { ErrorMessage, EmptyState } from '@repo/ui';
import { ReviewCard } from './ReviewCard';
import { Pagination } from './Pagination';
import { ReviewListSkeleton } from './ReviewListSkeleton';
import type { MyReviewItem } from '@repo/types';

const PAGE_SIZE = 20;

export function MyReviews() {
  const [page, setPage] = useState(0);

  const { data, isLoading, isError, refetch } = useMyReviews(page, PAGE_SIZE);

  const { editingReview, setEditingReview, handleUpdate, handleDelete, isUpdatePending } =
    useReviewActions<MyReviewItem>();

  const reviews = data?.content ?? [];
  const totalElements = data?.totalElements ?? 0;
  const totalPages = Math.max(1, Math.ceil(totalElements / PAGE_SIZE));

  function handlePageChange(newPage: number) {
    if (newPage >= 0 && newPage < totalPages) {
      setPage(newPage);
    }
  }

  return (
    <div>
      <h1 className="page-title">내 리뷰</h1>

      {isLoading && <ReviewListSkeleton count={3} gap="var(--space-3)" />}

      {isError && (
        <ErrorMessage
          message="리뷰를 불러오는데 실패했습니다."
          onRetry={() => refetch()}
        />
      )}

      {!isLoading && !isError && reviews.length === 0 && (
        <EmptyState message="작성한 리뷰가 없습니다." />
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
              showActions={true}
              isUpdatePending={isUpdatePending}
              onEdit={() => setEditingReview(review)}
              onDelete={() => handleDelete(review.reviewId)}
              onUpdate={handleUpdate}
              onCancelEdit={() => setEditingReview(null)}
              productLink={
                <Link
                  href={`/products/${review.productId}`}
                  style={{
                    fontSize: 'var(--font-size-sm)',
                    color: 'var(--color-primary)',
                    textDecoration: 'none',
                  }}
                >
                  {review.productName}
                </Link>
              }
            />
          ))}
        </div>
      )}

      {!isLoading && !isError && totalElements > PAGE_SIZE && (
        <Pagination
          page={page}
          totalPages={totalPages}
          onPageChange={handlePageChange}
          ariaLabel="페이지네이션"
          style={{ marginTop: 'var(--space-8)' }}
        />
      )}
    </div>
  );
}
