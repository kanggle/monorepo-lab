import { describe, it, expect } from 'vitest';
import { isApiError, isApiErrorResponse, ERROR_MESSAGES, getErrorMessage } from '../guards';

describe('isApiError', () => {
  it('code 속성이 있는 객체에 대해 true를 반환한다', () => {
    expect(isApiError({ code: 'VALIDATION_ERROR', message: 'msg', timestamp: '2024-01-01' })).toBe(true);
  });

  it('code 속성만 있어도 true를 반환한다', () => {
    expect(isApiError({ code: 'ERR' })).toBe(true);
  });

  it('code가 문자열이 아니면 false를 반환한다', () => {
    expect(isApiError({ code: 123 })).toBe(false);
  });

  it('null에 대해 false를 반환한다', () => {
    expect(isApiError(null)).toBe(false);
  });

  it('undefined에 대해 false를 반환한다', () => {
    expect(isApiError(undefined)).toBe(false);
  });

  it('문자열에 대해 false를 반환한다', () => {
    expect(isApiError('error')).toBe(false);
  });

  it('빈 객체에 대해 false를 반환한다', () => {
    expect(isApiError({})).toBe(false);
  });
});

describe('isApiErrorResponse', () => {
  it('code, message, timestamp가 모두 문자열인 객체에 대해 true를 반환한다', () => {
    expect(isApiErrorResponse({ code: 'ERR', message: 'msg', timestamp: '2024-01-01' })).toBe(true);
  });

  it('timestamp가 없으면 false를 반환한다', () => {
    expect(isApiErrorResponse({ code: 'ERR', message: 'msg' })).toBe(false);
  });
});

describe('ERROR_MESSAGES', () => {
  it('VALIDATION_ERROR 키를 포함한다', () => {
    expect(ERROR_MESSAGES.VALIDATION_ERROR).toBe('입력값을 확인해주세요.');
  });

  it('NETWORK_ERROR 키를 포함한다', () => {
    expect(ERROR_MESSAGES.NETWORK_ERROR).toBe('네트워크 오류가 발생했습니다. 잠시 후 다시 시도해주세요.');
  });

  it('INVALID_CREDENTIALS 키를 포함한다', () => {
    expect(ERROR_MESSAGES.INVALID_CREDENTIALS).toBe('이메일 또는 비밀번호가 올바르지 않습니다.');
  });

  it('ADDRESS_LIMIT_EXCEEDED 키를 포함한다', () => {
    expect(ERROR_MESSAGES.ADDRESS_LIMIT_EXCEEDED).toBe('배송지는 최대 10개까지 등록 가능합니다.');
  });

  it('DEFAULT_ADDRESS_CANNOT_BE_DELETED 키를 포함한다', () => {
    expect(ERROR_MESSAGES.DEFAULT_ADDRESS_CANNOT_BE_DELETED).toBe('기본 배송지는 삭제할 수 없습니다.');
  });
});

describe('getErrorMessage', () => {
  it('ApiErrorResponse에서 메시지를 추출한다', () => {
    expect(getErrorMessage({ code: 'ERR', message: 'api error', timestamp: '2024-01-01' }, 'fallback')).toBe('api error');
  });

  it('Error 인스턴스에서 메시지를 추출한다', () => {
    expect(getErrorMessage(new Error('test error'), 'fallback')).toBe('test error');
  });

  it('알 수 없는 에러에 대해 fallback을 반환한다', () => {
    expect(getErrorMessage(42, 'fallback')).toBe('fallback');
  });
});
