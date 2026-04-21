import { describe, it, expect } from 'vitest';
import { isApiErrorResponse, getErrorMessage } from '@repo/types/guards';

describe('isApiErrorResponse', () => {
  it('유효한 ApiErrorResponse 객체에 대해 true를 반환한다', () => {
    const value = { code: 'INVALID_CREDENTIALS', message: '인증 실패', timestamp: '2026-01-01T00:00:00Z' };
    expect(isApiErrorResponse(value)).toBe(true);
  });

  it('code가 없는 객체에 대해 false를 반환한다', () => {
    const value = { message: '에러', timestamp: '2026-01-01T00:00:00Z' };
    expect(isApiErrorResponse(value)).toBe(false);
  });

  it('message가 없는 객체에 대해 false를 반환한다', () => {
    const value = { code: 'ERROR', timestamp: '2026-01-01T00:00:00Z' };
    expect(isApiErrorResponse(value)).toBe(false);
  });

  it('timestamp가 없는 객체에 대해 false를 반환한다', () => {
    const value = { code: 'ERROR', message: '에러' };
    expect(isApiErrorResponse(value)).toBe(false);
  });

  it('null에 대해 false를 반환한다', () => {
    expect(isApiErrorResponse(null)).toBe(false);
  });

  it('undefined에 대해 false를 반환한다', () => {
    expect(isApiErrorResponse(undefined)).toBe(false);
  });

  it('문자열에 대해 false를 반환한다', () => {
    expect(isApiErrorResponse('error string')).toBe(false);
  });

  it('숫자에 대해 false를 반환한다', () => {
    expect(isApiErrorResponse(42)).toBe(false);
  });

  it('code가 문자열이 아닌 객체에 대해 false를 반환한다', () => {
    const value = { code: 123, message: '에러', timestamp: '2026-01-01T00:00:00Z' };
    expect(isApiErrorResponse(value)).toBe(false);
  });
});

describe('getErrorMessage', () => {
  const fallback = '기본 에러 메시지';

  it('ApiErrorResponse에서 message를 추출한다', () => {
    const error = { code: 'ERROR', message: 'API 에러', timestamp: '2026-01-01T00:00:00Z' };
    expect(getErrorMessage(error, fallback)).toBe('API 에러');
  });

  it('Error 인스턴스에서 message를 추출한다', () => {
    const error = new Error('일반 에러');
    expect(getErrorMessage(error, fallback)).toBe('일반 에러');
  });

  it('message 속성이 있는 일반 객체에서 message를 추출한다', () => {
    const error = { message: '객체 에러' };
    expect(getErrorMessage(error, fallback)).toBe('객체 에러');
  });

  it('인식할 수 없는 에러 형식일 때 fallback을 반환한다', () => {
    expect(getErrorMessage('문자열 에러', fallback)).toBe(fallback);
  });

  it('null일 때 fallback을 반환한다', () => {
    expect(getErrorMessage(null, fallback)).toBe(fallback);
  });

  it('undefined일 때 fallback을 반환한다', () => {
    expect(getErrorMessage(undefined, fallback)).toBe(fallback);
  });

  it('숫자일 때 fallback을 반환한다', () => {
    expect(getErrorMessage(42, fallback)).toBe(fallback);
  });

  it('message가 문자열이 아닌 객체일 때 fallback을 반환한다', () => {
    const error = { message: 123 };
    expect(getErrorMessage(error, fallback)).toBe(fallback);
  });
});
