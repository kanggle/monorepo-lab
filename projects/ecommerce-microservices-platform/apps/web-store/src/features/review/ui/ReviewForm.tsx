'use client';

import { useState } from 'react';
import { StarRating } from './StarRating';
import { getErrorMessage, isApiError } from '@repo/types/guards';

interface ReviewFormProps {
  initialRating?: number;
  initialTitle?: string;
  initialContent?: string;
  onSubmit: (data: { rating: number; title: string; content: string }) => Promise<void>;
  onCancel?: () => void;
  submitLabel?: string;
  isPending?: boolean;
}

export function ReviewForm({
  initialRating = 0,
  initialTitle = '',
  initialContent = '',
  onSubmit,
  onCancel,
  submitLabel = '리뷰 작성',
  isPending = false,
}: ReviewFormProps) {
  const [rating, setRating] = useState(initialRating);
  const [title, setTitle] = useState(initialTitle);
  const [content, setContent] = useState(initialContent);
  const [error, setError] = useState('');

  const isValid = rating >= 1 && rating <= 5 && title.trim().length > 0 && content.trim().length > 0;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!isValid) return;

    setError('');
    try {
      await onSubmit({ rating, title: title.trim(), content: content.trim() });
    } catch (err) {
      if (isApiError(err)) {
        const errorMessages: Record<string, string> = {
          PRODUCT_NOT_PURCHASED: '구매한 상품에만 리뷰를 작성할 수 있습니다.',
          REVIEW_ALREADY_EXISTS: '이미 이 상품에 리뷰를 작성했습니다.',
        };
        setError(errorMessages[err.code] ?? getErrorMessage(err, '리뷰 저장에 실패했습니다.'));
      } else {
        setError(getErrorMessage(err, '리뷰 저장에 실패했습니다.'));
      }
    }
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      <div>
        <label className="label">별점</label>
        <StarRating rating={rating} onChange={setRating} size="md" />
        {rating === 0 && (
          <p style={{ fontSize: 'var(--font-size-xs, 0.75rem)', color: 'var(--color-text-secondary)', marginTop: 'var(--space-1)' }}>
            별점을 선택해주세요
          </p>
        )}
      </div>

      <div>
        <label htmlFor="review-title" className="label">제목</label>
        <input
          id="review-title"
          type="text"
          className="input"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="리뷰 제목을 입력해주세요"
          maxLength={100}
        />
      </div>

      <div>
        <label htmlFor="review-content" className="label">내용</label>
        <textarea
          id="review-content"
          className="input"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder="리뷰 내용을 입력해주세요"
          rows={4}
          maxLength={2000}
          style={{ resize: 'vertical', minHeight: '100px' }}
        />
      </div>

      {error && (
        <p style={{ color: 'var(--color-danger, #ef4444)', fontSize: 'var(--font-size-sm)' }}>
          {error}
        </p>
      )}

      <div style={{ display: 'flex', gap: 'var(--space-2)', justifyContent: 'flex-end' }}>
        {onCancel && (
          <button type="button" className="btn" onClick={onCancel}>
            취소
          </button>
        )}
        <button
          type="submit"
          className="btn btn-primary"
          disabled={!isValid || isPending}
        >
          {isPending ? '저장 중...' : submitLabel}
        </button>
      </div>
    </form>
  );
}
