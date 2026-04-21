'use client';

import { Skeleton } from '@/shared/ui/Skeleton';

interface ReviewListSkeletonProps {
  count?: number;
  gap?: string;
}

export function ReviewListSkeleton({ count = 3, gap = 'var(--space-4)' }: ReviewListSkeletonProps) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap }}>
      {Array.from({ length: count }).map((_, i) => (
        <div
          key={i}
          style={{
            padding: 'var(--space-4)',
            border: '1px solid var(--color-border-light)',
            borderRadius: 'var(--radius-md)',
          }}
        >
          <Skeleton width="40%" height="14px" />
          <div style={{ marginTop: 'var(--space-2)' }}>
            <Skeleton width="80%" height="14px" />
          </div>
        </div>
      ))}
    </div>
  );
}
