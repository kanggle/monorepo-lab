import { apiClient } from '@/shared/config/api';
import { createReviewApi } from '@repo/api-client';
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

const reviewApi = createReviewApi(apiClient);

export async function getProductReviews(
  productId: string,
  params?: ReviewListParams,
): Promise<ReviewListResponse> {
  return reviewApi.getProductReviews(productId, params);
}

export async function getProductReviewSummary(
  productId: string,
): Promise<ReviewSummary> {
  return reviewApi.getProductReviewSummary(productId);
}

export async function getMyReviews(
  params?: ReviewListParams,
): Promise<MyReviewListResponse> {
  return reviewApi.getMyReviews(params);
}

export async function createReview(
  data: CreateReviewRequest,
): Promise<CreateReviewResponse> {
  return reviewApi.createReview(data);
}

export async function updateReview(
  reviewId: string,
  data: UpdateReviewRequest,
): Promise<UpdateReviewResponse> {
  return reviewApi.updateReview(reviewId, data);
}

export async function deleteReview(reviewId: string): Promise<void> {
  return reviewApi.deleteReview(reviewId);
}
