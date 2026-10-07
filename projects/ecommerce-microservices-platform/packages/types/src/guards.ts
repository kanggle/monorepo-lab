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
  // 주문 시 쿠폰 적용 결과 (TASK-INT-026) — 주문은 만들어지지 않았다.
  COUPON_NOT_FOUND: '쿠폰을 찾을 수 없습니다. 쿠폰을 다시 선택해 주세요.',
  COUPON_ALREADY_USED: '이미 사용한 쿠폰입니다. 다른 쿠폰을 선택해 주세요.',
  COUPON_EXPIRED: '만료된 쿠폰입니다. 다른 쿠폰을 선택해 주세요.',
  COUPON_NOT_OWNED: '이 계정에서 사용할 수 없는 쿠폰입니다.',
  COUPON_NOT_APPLICABLE: '이 주문에는 선택한 쿠폰을 적용할 수 없습니다.',
  COUPON_SERVICE_UNAVAILABLE: '쿠폰을 확인하지 못해 주문을 만들지 않았습니다. 잠시 후 다시 시도해 주세요.',
  // 공용 — 로그인/서버 상태 (TASK-FE-107 이전엔 쓰는 곳이 없었다. 둘 다 기존 코드에 영향 없음).
  UNAUTHORIZED: '로그인이 필요합니다. 다시 로그인한 뒤 시도해 주세요.',
  SERVICE_UNAVAILABLE: '잠시 후 다시 시도해 주세요.',
  // 셀러 초대 수락 (TASK-FE-107, product-api.md:376-415). SELLER_INVITATION_EMAIL_NOT_VERIFIED
  // 는 인증 메일 화면 링크가 함께 필요해 화면에서 별도로 처리한다 — 이 표에 넣지 않는다.
  SELLER_INVITATION_NOT_FOUND: '유효하지 않은 초대 코드입니다. 코드를 다시 확인해 주세요.',
  SELLER_INVITATION_ALREADY_USED: '이미 다른 계정이 사용한 초대입니다.',
  SELLER_INVITATION_EXPIRED: '초대가 만료되었습니다. 초대한 운영자에게 새 초대를 요청해 주세요.',
  SELLER_INVITATION_EMAIL_MISMATCH: '이 초대는 다른 이메일 주소로 발급되었습니다. 초대받은 이메일 계정으로 로그인해 주세요.',
  SELLER_NOT_ACTIVE: '초대한 셀러가 더 이상 활성 상태가 아닙니다.',
  SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE: '이 계정으로는 셀러 구성원이 될 수 없습니다.',
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
