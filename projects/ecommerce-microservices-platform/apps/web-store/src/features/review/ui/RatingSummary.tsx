'use client';

import { useReviewSummary } from '../model/use-review-summary';
import { Skeleton } from '@/shared/ui/Skeleton';

interface RatingSummaryProps {
  productId: string;
}

const RATING_LABELS: Record<number, string> = {
  5: '5점',
  4: '4점',
  3: '3점',
  2: '2점',
  1: '1점',
};

export function RatingSummary({ productId }: RatingSummaryProps) {
  const { data, isLoading, isError } = useReviewSummary(productId);

  if (isLoading) {
    return (
      <div style={{ padding: 'var(--space-4)' }}>
        <Skeleton width="120px" height="32px" />
        <div style={{ marginTop: 'var(--space-3)', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} width="100%" height="16px" />
          ))}
        </div>
      </div>
    );
  }

  if (isError || !data) {
    return null;
  }

  const maxCount = Math.max(
    ...Object.values(data.ratingDistribution),
    1,
  );

  return (
    <div
      style={{
        padding: 'var(--space-6)',
        background: 'var(--color-bg-secondary, #f9fafb)',
        borderRadius: 'var(--radius-lg)',
        border: '1px solid var(--color-border-light)',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'baseline', gap: 'var(--space-2)' }}>
        <span
          style={{
            fontSize: 'var(--font-size-2xl, 1.5rem)',
            fontWeight: 'var(--font-weight-bold)',
          }}
        >
          {data.averageRating.toFixed(1)}
        </span>
        <span style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-text-secondary)' }}>
          / 5.0 ({data.totalReviews}개 리뷰)
        </span>
      </div>

      <div
        style={{
          marginTop: 'var(--space-4)',
          display: 'flex',
          flexDirection: 'column',
          gap: 'var(--space-2)',
        }}
      >
        {[5, 4, 3, 2, 1].map((rating) => {
          const count = data.ratingDistribution[String(rating)] ?? 0;
          const percentage = maxCount > 0 ? (count / maxCount) * 100 : 0;

          return (
            <div
              key={rating}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: 'var(--space-2)',
              }}
            >
              <span
                style={{
                  width: '32px',
                  fontSize: 'var(--font-size-sm)',
                  color: 'var(--color-text-secondary)',
                  textAlign: 'right',
                }}
              >
                {RATING_LABELS[rating]}
              </span>
              <div
                style={{
                  flex: 1,
                  height: '8px',
                  background: 'var(--color-border-light)',
                  borderRadius: 'var(--radius-full, 9999px)',
                  overflow: 'hidden',
                }}
              >
                <div
                  data-testid={`rating-bar-${rating}`}
                  style={{
                    height: '100%',
                    width: `${percentage}%`,
                    background: 'var(--color-primary)',
                    borderRadius: 'var(--radius-full, 9999px)',
                    transition: 'width 0.3s ease',
                  }}
                />
              </div>
              <span
                style={{
                  width: '28px',
                  fontSize: 'var(--font-size-xs, 0.75rem)',
                  color: 'var(--color-text-secondary)',
                  textAlign: 'right',
                }}
              >
                {count}
              </span>
            </div>
          );
        })}
      </div>
    </div>
  );
}
