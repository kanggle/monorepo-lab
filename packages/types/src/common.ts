// Common types shared across all domains

export interface ApiErrorResponse {
  code: string;
  message: string;
  timestamp: string;
}

export interface PaginationParams {
  page?: number;
  size?: number;
}

export interface PaginatedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
}
