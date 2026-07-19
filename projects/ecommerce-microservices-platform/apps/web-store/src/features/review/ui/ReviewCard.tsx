'use client';

import { StarRating } from './StarRating';
import { ReviewForm } from './ReviewForm';
import { formatDateTime } from '@/shared/lib';

interface ReviewData {
  rating: number;
  title: string;
  content: string;
  createdAt: string;
}

interface ReviewCardActions {
  onEdit: () => void;
  onDelete: () => void;
  onUpdate: (formData: { rating: number; title: string; content: string }) => Promise<void>;
  onCancelEdit: () => void;
}

interface ReviewCardProps {
  review: ReviewData;
  isEditing: boolean;
  showActions: boolean;
  isUpdatePending: boolean;
  actions: ReviewCardActions;
  productLink?: React.ReactNode;
}

export function ReviewCard({
  review,
  isEditing,
  showActions,
  isUpdatePending,
  actions,
  productLink,
}: ReviewCardProps) {
  const { rating, title, content, createdAt } = review;
  const { onEdit, onDelete, onUpdate, onCancelEdit } = actions;
  if (isEditing) {
    return (
      <div
        style={{
          padding: 'var(--space-4)',
          border: '1px solid var(--color-primary)',
          borderRadius: 'var(--radius-md)',
        }}
      >
        <h3
          style={{
            fontSize: 'var(--font-size-md, 1rem)',
            fontWeight: 'var(--font-weight-semibold)',
            marginBottom: 'var(--space-3)',
          }}
        >
          리뷰 수정
        </h3>
        <ReviewForm
          initialRating={rating}
          initialTitle={title}
          initialContent={content}
          onSubmit={onUpdate}
          onCancel={onCancelEdit}
          submitLabel="리뷰 수정"
          isPending={isUpdatePending}
        />
      </div>
    );
  }

  return (
    <div
      style={{
        padding: 'var(--space-4)',
        border: '1px solid var(--color-border-light)',
        borderRadius: 'var(--radius-md)',
      }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          {productLink}
          <div style={productLink ? { marginTop: 'var(--space-1)' } : undefined}>
            <StarRating rating={rating} />
          </div>
          <h4
            style={{
              fontSize: 'var(--font-size-md, 1rem)',
              fontWeight: 'var(--font-weight-medium)',
              marginTop: 'var(--space-1)',
            }}
          >
            {title}
          </h4>
        </div>
        {showActions && (
          <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
            <button
              type="button"
              className="btn"
              onClick={onEdit}
              style={{ fontSize: 'var(--font-size-sm)' }}
            >
              수정
            </button>
            <button
              type="button"
              className="btn"
              onClick={onDelete}
              style={{ fontSize: 'var(--font-size-sm)', color: 'var(--color-danger, #ef4444)' }}
            >
              삭제
            </button>
          </div>
        )}
      </div>
      <p
        style={{
          marginTop: 'var(--space-2)',
          fontSize: 'var(--font-size-sm)',
          color: 'var(--color-text-primary)',
          lineHeight: 1.6,
          whiteSpace: 'pre-wrap',
          wordBreak: 'break-word',
        }}
      >
        {content}
      </p>
      <p
        style={{
          marginTop: 'var(--space-2)',
          fontSize: 'var(--font-size-xs, 0.75rem)',
          color: 'var(--color-text-secondary)',
        }}
      >
        {formatDateTime(createdAt)}
      </p>
    </div>
  );
}
