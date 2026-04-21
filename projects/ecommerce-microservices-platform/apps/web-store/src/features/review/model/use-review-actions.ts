'use client';

import { useState } from 'react';
import { useUpdateReview } from './use-update-review';
import { useDeleteReview } from './use-delete-review';
import type { ReviewItem, MyReviewItem } from '@repo/types';

type EditableReview = Pick<ReviewItem | MyReviewItem, 'reviewId' | 'rating' | 'title' | 'content'>;

export function useReviewActions<T extends EditableReview>() {
  const [editingReview, setEditingReview] = useState<T | null>(null);
  const updateReview = useUpdateReview();
  const deleteReview = useDeleteReview();

  async function handleUpdate(formData: { rating: number; title: string; content: string }) {
    if (!editingReview) return;
    await updateReview.mutateAsync({
      reviewId: editingReview.reviewId,
      data: {
        rating: formData.rating,
        title: formData.title,
        content: formData.content,
      },
    });
    setEditingReview(null);
  }

  function handleDelete(reviewId: string) {
    if (window.confirm('리뷰를 삭제하시겠습니까?')) {
      deleteReview.mutate(reviewId);
    }
  }

  return {
    editingReview,
    setEditingReview,
    handleUpdate,
    handleDelete,
    isUpdatePending: updateReview.isPending,
  };
}
