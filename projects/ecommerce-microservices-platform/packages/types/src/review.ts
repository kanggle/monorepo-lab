// Review types based on specs/contracts/http/review-api.md

export interface CreateReviewRequest {
  productId: string;
  rating: number;
  title: string;
  content: string;
}

export interface CreateReviewResponse {
  reviewId: string;
}

export interface UpdateReviewRequest {
  rating: number;
  title: string;
  content: string;
}

export interface UpdateReviewResponse {
  reviewId: string;
}

export interface ReviewItem {
  reviewId: string;
  userId: string;
  rating: number;
  title: string;
  content: string;
  createdAt: string;
  updatedAt: string;
}

export interface ReviewListResponse {
  content: ReviewItem[];
  page: number;
  size: number;
  totalElements: number;
  averageRating: number;
  totalReviews: number;
}

export interface ReviewSummary {
  productId: string;
  averageRating: number;
  totalReviews: number;
  ratingDistribution: Record<string, number>;
}

export interface MyReviewItem {
  reviewId: string;
  productId: string;
  productName: string;
  rating: number;
  title: string;
  content: string;
  createdAt: string;
}

export interface MyReviewListResponse {
  content: MyReviewItem[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ReviewListParams {
  page?: number;
  size?: number;
  sort?: string;
}
