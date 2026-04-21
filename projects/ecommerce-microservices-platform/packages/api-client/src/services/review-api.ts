import type { ApiClient } from '../client';
import type {
  CreateReviewRequest,
  CreateReviewResponse,
  UpdateReviewRequest,
  UpdateReviewResponse,
  ReviewListResponse,
  ReviewSummary,
  MyReviewListResponse,
  ReviewListParams,
} from '@repo/types';

export function createReviewApi(client: ApiClient) {
  return {
    createReview: (data: CreateReviewRequest) =>
      client.post<CreateReviewResponse>('/api/reviews', data),

    getProductReviews: (productId: string, params?: ReviewListParams) =>
      client.get<ReviewListResponse>(`/api/reviews/products/${productId}`, { params }),

    getProductReviewSummary: (productId: string) =>
      client.get<ReviewSummary>(`/api/reviews/products/${productId}/summary`),

    getMyReviews: (params?: ReviewListParams) =>
      client.get<MyReviewListResponse>('/api/reviews/me', { params }),

    updateReview: (reviewId: string, data: UpdateReviewRequest) =>
      client.put<UpdateReviewResponse>(`/api/reviews/${reviewId}`, data),

    deleteReview: (reviewId: string) =>
      client.delete<void>(`/api/reviews/${reviewId}`),
  };
}
