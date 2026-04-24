import type { ApiErrorResponse } from './common';

export function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as Record<string, unknown>).code === 'string' &&
    typeof (value as Record<string, unknown>).message === 'string' &&
    typeof (value as Record<string, unknown>).timestamp === 'string'
  );
}

export function isApiError(value: unknown): value is ApiErrorResponse {
  return (
    typeof value === 'object' &&
    value !== null &&
    'code' in value &&
    typeof (value as ApiErrorResponse).code === 'string'
  );
}

export const ERROR_MESSAGES: Record<string, string> = {
  VALIDATION_ERROR: '입력값을 확인해주세요.',
  NETWORK_ERROR: '네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.',
  INVALID_CREDENTIALS: '이메일 또는 비밀번호가 올바르지 않습니다.',
  EMAIL_ALREADY_EXISTS: '이미 사용 중인 이메일입니다.',
  INSUFFICIENT_STOCK: '재고가 부족한 상품이 있습니다.',
  ADDRESS_LIMIT_EXCEEDED: '배송지는 최대 10개까지 등록 가능합니다.',
  ADDRESS_NOT_FOUND: '이미 삭제된 배송지입니다.',
  DEFAULT_ADDRESS_CANNOT_BE_DELETED: '기본 배송지는 삭제할 수 없습니다.',
  USER_PROFILE_NOT_FOUND: '프로필을 찾을 수 없습니다.',
  WISHLIST_LIMIT_EXCEEDED: '위시리스트는 최대 100개까지 추가할 수 있습니다.',
};

export function getErrorMessage(error: unknown, fallback: string): string {
  if (isApiErrorResponse(error)) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  if (
    typeof error === 'object' &&
    error !== null &&
    'message' in error &&
    typeof (error as Record<string, unknown>).message === 'string'
  ) {
    return (error as Record<string, unknown>).message as string;
  }
  return fallback;
}
