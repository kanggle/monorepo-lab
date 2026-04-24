import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  ACCESS_TOKEN_KEY,
  REFRESH_TOKEN_KEY,
  parseJwtPayload,
  getUserFromToken,
  saveTokens,
  clearTokens,
  getStoredAccessToken,
  getStoredRefreshToken,
  AUTH_ERROR_MESSAGES,
  AUTH_ERROR_KEYS,
  setAuthErrorMessages,
} from '../auth';

function createMockLocalStorage() {
  const storage: Record<string, string> = {};
  return {
    storage,
    mock: {
      getItem: vi.fn((key: string) => storage[key] ?? null),
      setItem: vi.fn((key: string, value: string) => { storage[key] = value; }),
      removeItem: vi.fn((key: string) => { delete storage[key]; }),
      clear: vi.fn(),
      length: 0,
      key: vi.fn(() => null),
    },
  };
}

describe('auth 유틸리티', () => {
  let storageMock: ReturnType<typeof createMockLocalStorage>;

  beforeEach(() => {
    storageMock = createMockLocalStorage();
    Object.defineProperty(globalThis, 'localStorage', {
      value: storageMock.mock,
      writable: true,
      configurable: true,
    });
    // Node 환경에서 window를 정의하여 SSR 분기를 통과시킨다
    if (typeof globalThis.window === 'undefined') {
      Object.defineProperty(globalThis, 'window', {
        value: globalThis,
        writable: true,
        configurable: true,
      });
    }
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  describe('토큰 키 상수', () => {
    it('ACCESS_TOKEN_KEY는 accessToken이다', () => {
      expect(ACCESS_TOKEN_KEY).toBe('accessToken');
    });

    it('REFRESH_TOKEN_KEY는 refreshToken이다', () => {
      expect(REFRESH_TOKEN_KEY).toBe('refreshToken');
    });
  });

  describe('parseJwtPayload', () => {
    it('유효한 JWT에서 페이로드를 파싱한다', () => {
      const payload = { sub: 'user-1', email: 'test@test.com', name: 'Tester' };
      const token = `header.${btoa(JSON.stringify(payload))}.signature`;

      const result = parseJwtPayload(token);

      expect(result).toEqual(payload);
    });

    it('base64url 인코딩된 토큰을 파싱한다', () => {
      const payload = { sub: 'user-1', email: 'test@test.com' };
      const base64 = btoa(JSON.stringify(payload))
        .replace(/\+/g, '-')
        .replace(/\//g, '_');
      const token = `header.${base64}.signature`;

      const result = parseJwtPayload(token);

      expect(result).toEqual(payload);
    });

    it('name이 없는 JWT도 파싱한다', () => {
      const payload = { sub: 'user-1', email: 'test@test.com' };
      const token = `header.${btoa(JSON.stringify(payload))}.signature`;

      const result = parseJwtPayload(token);

      expect(result).toEqual(payload);
      expect(result?.name).toBeUndefined();
    });

    it('점(.)이 하나뿐인 잘못된 토큰은 null을 반환한다', () => {
      expect(parseJwtPayload('invalid')).toBeNull();
    });

    it('빈 페이로드 부분은 null을 반환한다', () => {
      expect(parseJwtPayload('header..signature')).toBeNull();
    });

    it('유효하지 않은 JSON은 null을 반환한다', () => {
      const token = `header.${btoa('not-json')}.signature`;
      expect(parseJwtPayload(token)).toBeNull();
    });

    it('유효하지 않은 base64는 null을 반환한다', () => {
      expect(parseJwtPayload('header.!!!invalid!!!.signature')).toBeNull();
    });
  });

  describe('getUserFromToken', () => {
    it('localStorage에 유효한 토큰이 있으면 AuthUser를 반환한다', () => {
      const payload = { sub: 'user-1', email: 'test@test.com', name: 'Tester' };
      storageMock.storage[ACCESS_TOKEN_KEY] = `h.${btoa(JSON.stringify(payload))}.s`;

      const user = getUserFromToken();

      expect(user).toEqual({
        userId: 'user-1',
        email: 'test@test.com',
        name: 'Tester',
      });
    });

    it('name이 없는 토큰은 빈 문자열로 반환한다', () => {
      const payload = { sub: 'user-1', email: 'test@test.com' };
      storageMock.storage[ACCESS_TOKEN_KEY] = `h.${btoa(JSON.stringify(payload))}.s`;

      const user = getUserFromToken();

      expect(user?.name).toBe('');
    });

    it('토큰이 없으면 null을 반환한다', () => {
      expect(getUserFromToken()).toBeNull();
    });

    it('잘못된 토큰이면 null을 반환한다', () => {
      storageMock.storage[ACCESS_TOKEN_KEY] = 'invalid-token';
      expect(getUserFromToken()).toBeNull();
    });
  });

  describe('saveTokens', () => {
    it('액세스 토큰과 리프레시 토큰을 localStorage에 저장한다', () => {
      saveTokens('access-123', 'refresh-456');

      expect(storageMock.storage[ACCESS_TOKEN_KEY]).toBe('access-123');
      expect(storageMock.storage[REFRESH_TOKEN_KEY]).toBe('refresh-456');
    });
  });

  describe('clearTokens', () => {
    it('localStorage에서 토큰을 제거한다', () => {
      storageMock.storage[ACCESS_TOKEN_KEY] = 'access-123';
      storageMock.storage[REFRESH_TOKEN_KEY] = 'refresh-456';

      clearTokens();

      expect(storageMock.storage[ACCESS_TOKEN_KEY]).toBeUndefined();
      expect(storageMock.storage[REFRESH_TOKEN_KEY]).toBeUndefined();
    });
  });

  describe('getStoredAccessToken', () => {
    it('저장된 액세스 토큰을 반환한다', () => {
      storageMock.storage[ACCESS_TOKEN_KEY] = 'my-token';
      expect(getStoredAccessToken()).toBe('my-token');
    });

    it('토큰이 없으면 null을 반환한다', () => {
      expect(getStoredAccessToken()).toBeNull();
    });
  });

  describe('getStoredRefreshToken', () => {
    it('저장된 리프레시 토큰을 반환한다', () => {
      storageMock.storage[REFRESH_TOKEN_KEY] = 'my-refresh';
      expect(getStoredRefreshToken()).toBe('my-refresh');
    });

    it('토큰이 없으면 null을 반환한다', () => {
      expect(getStoredRefreshToken()).toBeNull();
    });
  });

  describe('AUTH_ERROR_MESSAGES', () => {
    it('INVALID_CREDENTIALS 메시지가 정의되어 있다', () => {
      expect(AUTH_ERROR_MESSAGES['INVALID_CREDENTIALS']).toBeDefined();
    });

    it('VALIDATION_ERROR 메시지가 정의되어 있다', () => {
      expect(AUTH_ERROR_MESSAGES['VALIDATION_ERROR']).toBeDefined();
    });

    it('NETWORK_ERROR 메시지가 정의되어 있다', () => {
      expect(AUTH_ERROR_MESSAGES['NETWORK_ERROR']).toBeDefined();
    });

    it('EMAIL_ALREADY_EXISTS 메시지가 정의되어 있다', () => {
      expect(AUTH_ERROR_MESSAGES['EMAIL_ALREADY_EXISTS']).toBeDefined();
    });

    it('INVALID_REFRESH_TOKEN 메시지가 정의되어 있다', () => {
      expect(AUTH_ERROR_MESSAGES['INVALID_REFRESH_TOKEN']).toBeDefined();
    });

    it('REFRESH_TOKEN_REVOKED 메시지가 정의되어 있다', () => {
      expect(AUTH_ERROR_MESSAGES['REFRESH_TOKEN_REVOKED']).toBeDefined();
    });
  });

  describe('AUTH_ERROR_KEYS', () => {
    it('모든 에러 키가 정의되어 있다', () => {
      expect(AUTH_ERROR_KEYS).toContain('INVALID_CREDENTIALS');
      expect(AUTH_ERROR_KEYS).toContain('VALIDATION_ERROR');
      expect(AUTH_ERROR_KEYS).toContain('NETWORK_ERROR');
      expect(AUTH_ERROR_KEYS).toContain('EMAIL_ALREADY_EXISTS');
      expect(AUTH_ERROR_KEYS).toContain('INVALID_REFRESH_TOKEN');
      expect(AUTH_ERROR_KEYS).toContain('REFRESH_TOKEN_REVOKED');
    });
  });

  describe('setAuthErrorMessages', () => {
    afterEach(() => {
      setAuthErrorMessages({});
    });

    it('에러 메시지를 오버라이드할 수 있다', () => {
      setAuthErrorMessages({ INVALID_CREDENTIALS: 'Custom message' });
      expect(AUTH_ERROR_MESSAGES['INVALID_CREDENTIALS']).toBe('Custom message');
    });

    it('오버라이드하지 않은 메시지는 기본값을 유지한다', () => {
      setAuthErrorMessages({ INVALID_CREDENTIALS: 'Custom' });
      expect(AUTH_ERROR_MESSAGES['VALIDATION_ERROR']).toBe('입력값을 확인해주세요.');
    });

    it('빈 객체로 호출하면 기본 메시지로 복원된다', () => {
      setAuthErrorMessages({ INVALID_CREDENTIALS: 'Custom' });
      setAuthErrorMessages({});
      expect(AUTH_ERROR_MESSAGES['INVALID_CREDENTIALS']).toBe('이메일 또는 비밀번호가 올바르지 않습니다.');
    });
  });
});
