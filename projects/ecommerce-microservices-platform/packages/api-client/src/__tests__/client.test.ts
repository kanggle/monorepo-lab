import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { ApiClient } from '../client';

// callable mock instance를 생성하여 this.instance(originalRequest) 재시도를 지원한다
vi.mock('axios', () => {
  const instanceFn = vi.fn();
  const mockInstance = Object.assign(instanceFn, {
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
    interceptors: {
      request: { use: vi.fn() },
      response: { use: vi.fn() },
    },
  });
  return {
    default: {
      create: vi.fn(() => mockInstance),
    },
  };
});

function getMockInstance() {
  return (axios.create as ReturnType<typeof vi.fn>).mock.results[0]?.value;
}

function getRequestInterceptor() {
  const mock = getMockInstance();
  return mock.interceptors.request.use.mock.calls[0][0];
}

function getResponseInterceptors() {
  const mock = getMockInstance();
  const [onFulfilled, onRejected] =
    mock.interceptors.response.use.mock.calls[0];
  return { onFulfilled, onRejected };
}

describe('ApiClient', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  describe('생성', () => {
    it('baseURL과 기본 헤더로 axios 인스턴스를 생성한다', () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });

      expect(axios.create).toHaveBeenCalledWith({
        baseURL: 'http://localhost:8080',
        headers: { 'Content-Type': 'application/json' },
        timeout: 10000,
      });
    });

    it('requestTimeoutMs 설정이 axios timeout에 반영된다', () => {
      new ApiClient({ baseURL: 'http://localhost:8080', requestTimeoutMs: 5000 });

      expect(axios.create).toHaveBeenCalledWith({
        baseURL: 'http://localhost:8080',
        headers: { 'Content-Type': 'application/json' },
        timeout: 5000,
      });
    });

    it('요청 인터셉터를 등록한다', () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const mock = getMockInstance();
      expect(mock.interceptors.request.use).toHaveBeenCalledTimes(1);
    });

    it('응답 인터셉터를 등록한다', () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const mock = getMockInstance();
      expect(mock.interceptors.response.use).toHaveBeenCalledTimes(1);
    });
  });

  describe('JWT 토큰 주입', () => {
    it('보호된 경로에 Authorization 헤더를 추가한다', () => {
      const getAccessToken = vi.fn(() => 'test-token');
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
      });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/orders',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBe('Bearer test-token');
    });

    it('공개 경로 /api/auth/signup에는 토큰을 주입하지 않는다', () => {
      const getAccessToken = vi.fn(() => 'test-token');
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
      });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/auth/signup',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBeUndefined();
    });

    it('공개 경로 /api/auth/login에는 토큰을 주입하지 않는다', () => {
      const getAccessToken = vi.fn(() => 'test-token');
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
      });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/auth/login',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBeUndefined();
    });

    it('공개 경로 /api/auth/refresh에는 토큰을 주입하지 않는다', () => {
      const getAccessToken = vi.fn(() => 'test-token');
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
      });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/auth/refresh',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBeUndefined();
    });

    it('공개 경로 /api/products에는 토큰을 주입하지 않는다', () => {
      const getAccessToken = vi.fn(() => 'test-token');
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
      });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/products',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBeUndefined();
    });

    it('공개 경로 /api/search에는 토큰을 주입하지 않는다', () => {
      const getAccessToken = vi.fn(() => 'test-token');
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
      });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/search/products',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBeUndefined();
    });

    it('토큰이 null이면 헤더를 추가하지 않는다', () => {
      const getAccessToken = vi.fn(() => null);
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
      });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/orders',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBeUndefined();
    });

    it('publicPaths 설정으로 공개 경로를 커스텀할 수 있다', () => {
      const getAccessToken = vi.fn(() => 'test-token');
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getAccessToken,
        publicPaths: ['/api/custom-public'],
      });

      const interceptor = getRequestInterceptor();

      // 커스텀 공개 경로에는 토큰 주입 안 됨
      const publicConfig = {
        url: '/api/custom-public/test',
        headers: {} as Record<string, string>,
      };
      const publicResult = interceptor(publicConfig);
      expect(publicResult.headers.Authorization).toBeUndefined();

      // 기본 공개 경로는 더 이상 공개 아님
      const defaultConfig = {
        url: '/api/products',
        headers: {} as Record<string, string>,
      };
      const defaultResult = interceptor(defaultConfig);
      expect(defaultResult.headers.Authorization).toBe('Bearer test-token');
    });

    it('getAccessToken이 없으면 헤더를 추가하지 않는다', () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });

      const interceptor = getRequestInterceptor();
      const config = {
        url: '/api/orders',
        headers: {} as Record<string, string>,
      };
      const result = interceptor(config);

      expect(result.headers.Authorization).toBeUndefined();
    });
  });

  describe('에러 핸들링', () => {
    it('서버 에러 응답을 ApiErrorResponse로 변환한다', async () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const { onRejected } = getResponseInterceptors();

      const error = {
        response: {
          status: 400,
          data: {
            code: 'VALIDATION_ERROR',
            message: 'Invalid input',
            timestamp: '2026-01-01T00:00:00Z',
          },
        },
        config: { url: '/api/orders', _retry: false },
      };

      await expect(onRejected(error)).rejects.toEqual({
        code: 'VALIDATION_ERROR',
        message: 'Invalid input',
        timestamp: '2026-01-01T00:00:00Z',
      });
    });

    it('네트워크 에러 시 NETWORK_ERROR로 변환한다', async () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const { onRejected } = getResponseInterceptors();

      const error = {
        response: undefined,
        message: 'Network Error',
        config: { url: '/api/orders', _retry: false },
      };

      await expect(onRejected(error)).rejects.toMatchObject({
        code: 'NETWORK_ERROR',
        message: 'Network Error',
      });
    });

    it('네트워크 에러 시 timestamp를 포함한다', async () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const { onRejected } = getResponseInterceptors();

      const error = {
        response: undefined,
        message: 'Network Error',
        config: { url: '/api/orders', _retry: false },
      };

      try {
        await onRejected(error);
      } catch (e: unknown) {
        const apiError = e as { timestamp: string };
        expect(apiError.timestamp).toBeDefined();
        expect(typeof apiError.timestamp).toBe('string');
      }
    });

    it('응답 본문이 없으면 NETWORK_ERROR 폴백을 사용한다', async () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const { onRejected } = getResponseInterceptors();

      const error = {
        response: { status: 500, data: null },
        message: 'Internal Server Error',
        config: { url: '/api/orders', _retry: false },
      };

      await expect(onRejected(error)).rejects.toMatchObject({
        code: 'NETWORK_ERROR',
        message: 'Internal Server Error',
      });
    });

    it('응답 본문이 undefined이면 NETWORK_ERROR 폴백을 사용한다', async () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const { onRejected } = getResponseInterceptors();

      const error = {
        response: { status: 502, data: undefined },
        message: 'Bad Gateway',
        config: { url: '/api/orders', _retry: false },
      };

      await expect(onRejected(error)).rejects.toMatchObject({
        code: 'NETWORK_ERROR',
        message: 'Bad Gateway',
      });
    });

    it('에러 메시지도 없으면 기본 메시지를 사용한다', async () => {
      new ApiClient({ baseURL: 'http://localhost:8080' });
      const { onRejected } = getResponseInterceptors();

      const error = {
        response: undefined,
        message: undefined,
        config: { url: '/api/orders', _retry: false },
      };

      await expect(onRejected(error)).rejects.toMatchObject({
        code: 'NETWORK_ERROR',
        message: 'Network error occurred',
      });
    });
  });

  describe('토큰 갱신', () => {
    it('401 응답 시 토큰 갱신을 시도한다', async () => {
      const getRefreshToken = vi.fn(() => 'refresh-token');
      const onTokenRefreshed = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken,
        onTokenRefreshed,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      mock.post.mockResolvedValueOnce({
        data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
      });

      const originalRequest = {
        url: '/api/orders',
        headers: {} as Record<string, string>,
        _retry: false,
      };

      // callable mock이므로 retry 호출 시 성공 응답 반환
      mock.mockResolvedValueOnce({ data: { orderId: '123' } });

      const error = {
        response: { status: 401 },
        config: originalRequest,
      };

      await onRejected(error);

      expect(mock.post).toHaveBeenCalledWith('/api/auth/refresh', {
        refreshToken: 'refresh-token',
      });
    });

    it('토큰 갱신 성공 후 원래 요청을 새 토큰으로 재시도한다', async () => {
      const getRefreshToken = vi.fn(() => 'refresh-token');
      const onTokenRefreshed = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken,
        onTokenRefreshed,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      mock.post.mockResolvedValueOnce({
        data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
      });

      const originalRequest = {
        url: '/api/orders',
        headers: {} as Record<string, string>,
        _retry: false,
      };

      mock.mockResolvedValueOnce({ data: { orderId: '123' } });

      const error = {
        response: { status: 401 },
        config: originalRequest,
      };

      const result = await onRejected(error);

      // 재시도된 요청에 새 토큰이 설정되었는지 확인
      expect(originalRequest.headers.Authorization).toBe('Bearer new-access');
      expect(originalRequest._retry).toBe(true);
      // instance(originalRequest)가 호출되었는지 확인
      expect(mock).toHaveBeenCalledWith(originalRequest);
      expect(result).toEqual({ data: { orderId: '123' } });
    });

    it('토큰 갱신 성공 시 onTokenRefreshed 콜백을 호출한다', async () => {
      const getRefreshToken = vi.fn(() => 'refresh-token');
      const onTokenRefreshed = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken,
        onTokenRefreshed,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      mock.post.mockResolvedValueOnce({
        data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
      });
      mock.mockResolvedValueOnce({ data: 'ok' });

      const error = {
        response: { status: 401 },
        config: {
          url: '/api/orders',
          headers: {} as Record<string, string>,
          _retry: false,
        },
      };

      await onRejected(error);

      expect(onTokenRefreshed).toHaveBeenCalledWith(
        'new-access',
        'new-refresh',
      );
    });

    it('동시 401 발생 시 하나의 갱신만 실행되고 대기 요청이 새 토큰을 받는다', async () => {
      const getRefreshToken = vi.fn(() => 'refresh-token');
      const onTokenRefreshed = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken,
        onTokenRefreshed,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      // refresh는 약간 지연되어 반환
      let resolveRefresh!: (value: unknown) => void;
      const refreshPromise = new Promise((resolve) => {
        resolveRefresh = resolve;
      });
      mock.post.mockReturnValueOnce(refreshPromise);

      // retry 호출마다 다른 응답 반환
      mock
        .mockResolvedValueOnce({ data: { orderId: '1' } }) // 첫 번째 요청 retry
        .mockResolvedValueOnce({ data: { orderId: '2' } }) // 두 번째 요청 retry (subscriber)
        .mockResolvedValueOnce({ data: { orderId: '3' } }); // 세 번째 요청 retry (subscriber)

      const makeError = (url: string) => ({
        response: { status: 401 },
        config: {
          url,
          headers: {} as Record<string, string>,
          _retry: false,
        },
      });

      // 첫 번째 401 → refresh 시작
      const p1 = onRejected(makeError('/api/orders'));

      // 두 번째, 세 번째 401 → isRefreshing이므로 subscriber 큐에 등록
      const p2 = onRejected(makeError('/api/orders/2'));
      const p3 = onRejected(makeError('/api/orders/3'));

      // refresh 완료
      resolveRefresh({
        data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
      });

      const [r1, r2, r3] = await Promise.all([p1, p2, p3]);

      // refresh는 한 번만 호출됨
      expect(mock.post).toHaveBeenCalledTimes(1);

      // 모든 요청이 성공적으로 재시도됨 (subscriber가 먼저 호출되므로 순서가 다름)
      const results = [r1, r2, r3].map(
        (r) => (r as { data: { orderId: string } }).data.orderId,
      );
      expect(results).toHaveLength(3);
      expect(new Set(results)).toEqual(new Set(['1', '2', '3']));

      // subscriber 요청의 헤더에 새 토큰이 설정됨
      expect(onTokenRefreshed).toHaveBeenCalledWith(
        'new-access',
        'new-refresh',
      );
    });

    it('refresh token이 없으면 onAuthError를 호출한다', async () => {
      const onAuthError = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken: () => null,
        onAuthError,
      });

      const { onRejected } = getResponseInterceptors();

      const error = {
        response: { status: 401 },
        config: { url: '/api/orders', _retry: false, headers: {} },
      };

      try {
        await onRejected(error);
      } catch {
        // Expected
      }

      expect(onAuthError).toHaveBeenCalled();
    });

    it('토큰 갱신 실패 시 onAuthError 호출과 함께 원래 에러를 반환한다', async () => {
      const onAuthError = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken: () => 'expired-refresh-token',
        onAuthError,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      // refresh 요청 자체가 실패
      mock.post.mockRejectedValueOnce(new Error('Refresh failed'));

      const originalError = {
        response: { status: 401 },
        config: {
          url: '/api/orders',
          headers: {} as Record<string, string>,
          _retry: false,
        },
      };

      await expect(onRejected(originalError)).rejects.toBe(originalError);
      expect(onAuthError).toHaveBeenCalledTimes(1);
    });

    it('네트워크 오류로 갱신 요청이 실패해도 onAuthError를 호출한다', async () => {
      const onAuthError = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken: () => 'refresh-token',
        onAuthError,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      mock.post.mockRejectedValueOnce(new Error('Network Error'));

      const error = {
        response: { status: 401 },
        config: {
          url: '/api/orders',
          headers: {} as Record<string, string>,
          _retry: false,
        },
      };

      await expect(onRejected(error)).rejects.toBe(error);
      expect(onAuthError).toHaveBeenCalled();
    });

    it('갱신 실패 후 isRefreshing 플래그가 초기화된다', async () => {
      const onAuthError = vi.fn();
      const getRefreshToken = vi.fn(() => 'refresh-token');
      const onTokenRefreshed = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken,
        onTokenRefreshed,
        onAuthError,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      // 첫 번째 시도: refresh 실패
      mock.post.mockRejectedValueOnce(new Error('Refresh failed'));

      const error1 = {
        response: { status: 401 },
        config: {
          url: '/api/orders',
          headers: {} as Record<string, string>,
          _retry: false,
        },
      };

      try {
        await onRejected(error1);
      } catch {
        // Expected
      }

      // 두 번째 시도: refresh 성공 (isRefreshing이 초기화되었으므로 새로운 갱신 시도)
      mock.post.mockResolvedValueOnce({
        data: { accessToken: 'new-access-2', refreshToken: 'new-refresh-2' },
      });
      mock.mockResolvedValueOnce({ data: 'success' });

      const error2 = {
        response: { status: 401 },
        config: {
          url: '/api/orders',
          headers: {} as Record<string, string>,
          _retry: false,
        },
      };

      const result = await onRejected(error2);
      expect(result).toEqual({ data: 'success' });
      expect(mock.post).toHaveBeenCalledTimes(2);
    });

    it('토큰 갱신이 10초 이상 걸리면 timeout으로 실패하고 isRefreshing이 복원된다', async () => {
      vi.useFakeTimers();

      const onAuthError = vi.fn();
      const getRefreshToken = vi.fn(() => 'refresh-token');
      const onTokenRefreshed = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken,
        onTokenRefreshed,
        onAuthError,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      // refresh 요청이 resolve되지 않는 pending 상태
      mock.post.mockReturnValueOnce(new Promise(() => {}));

      const error = {
        response: { status: 401 },
        config: {
          url: '/api/orders',
          headers: {} as Record<string, string>,
          _retry: false,
        },
      };

      const resultPromise = onRejected(error);

      // 10초 경과 → timeout
      vi.advanceTimersByTime(10000);

      await expect(resultPromise).rejects.toBe(error);
      expect(onAuthError).toHaveBeenCalledTimes(1);
      expect(onTokenRefreshed).not.toHaveBeenCalled();

      // isRefreshing이 false로 복원되었는지 확인:
      // 새로운 401 에러가 subscriber 큐가 아닌 새 갱신 시도로 처리되어야 함
      mock.post.mockResolvedValueOnce({
        data: { accessToken: 'new-access', refreshToken: 'new-refresh' },
      });
      mock.mockResolvedValueOnce({ data: 'success' });

      const error2 = {
        response: { status: 401 },
        config: {
          url: '/api/orders',
          headers: {} as Record<string, string>,
          _retry: false,
        },
      };

      const result = await onRejected(error2);
      expect(result).toEqual({ data: 'success' });
      expect(mock.post).toHaveBeenCalledTimes(2);

      vi.useRealTimers();
    });

    it('토큰 갱신 timeout 시 대기 중인 subscriber도 정리된다', async () => {
      vi.useFakeTimers();

      const onAuthError = vi.fn();

      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken: () => 'refresh-token',
        onAuthError,
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      // refresh 요청이 영원히 pending
      mock.post.mockReturnValueOnce(new Promise(() => {}));

      const makeError = (url: string) => ({
        response: { status: 401 },
        config: {
          url,
          headers: {} as Record<string, string>,
          _retry: false,
        },
      });

      // 첫 번째 401 → refresh 시작
      const p1 = onRejected(makeError('/api/orders'));
      // 두 번째 401 → subscriber 큐에 등록
      const p2 = onRejected(makeError('/api/orders/2'));

      // timeout 발생
      vi.advanceTimersByTime(10000);

      await expect(p1).rejects.toBeDefined();
      expect(onAuthError).toHaveBeenCalledTimes(1);

      vi.useRealTimers();
    });

    it('공개 경로의 401은 토큰 갱신을 시도하지 않는다', async () => {
      new ApiClient({
        baseURL: 'http://localhost:8080',
        getRefreshToken: () => 'refresh-token',
      });

      const mock = getMockInstance();
      const { onRejected } = getResponseInterceptors();

      const error = {
        response: {
          status: 401,
          data: {
            code: 'INVALID_CREDENTIALS',
            message: 'Bad credentials',
            timestamp: '2026-01-01T00:00:00Z',
          },
        },
        config: { url: '/api/auth/login', _retry: false },
      };

      await expect(onRejected(error)).rejects.toEqual({
        code: 'INVALID_CREDENTIALS',
        message: 'Bad credentials',
        timestamp: '2026-01-01T00:00:00Z',
      });

      expect(mock.post).not.toHaveBeenCalled();
    });
  });

  describe('HTTP 메서드', () => {
    it('get은 응답 데이터를 반환한다', async () => {
      const client = new ApiClient({ baseURL: 'http://localhost:8080' });
      const mock = getMockInstance();
      mock.get.mockResolvedValueOnce({ data: { id: '1' } });

      const result = await client.get('/api/test');
      expect(result).toEqual({ id: '1' });
      expect(mock.get).toHaveBeenCalledWith('/api/test', undefined);
    });

    it('post는 응답 데이터를 반환한다', async () => {
      const client = new ApiClient({ baseURL: 'http://localhost:8080' });
      const mock = getMockInstance();
      mock.post.mockResolvedValueOnce({ data: { id: '1' } });

      const result = await client.post('/api/test', { name: 'test' });
      expect(result).toEqual({ id: '1' });
      expect(mock.post).toHaveBeenCalledWith(
        '/api/test',
        { name: 'test' },
        undefined,
      );
    });

    it('patch는 응답 데이터를 반환한다', async () => {
      const client = new ApiClient({ baseURL: 'http://localhost:8080' });
      const mock = getMockInstance();
      mock.patch.mockResolvedValueOnce({ data: { id: '1' } });

      const result = await client.patch('/api/test', { name: 'updated' });
      expect(result).toEqual({ id: '1' });
      expect(mock.patch).toHaveBeenCalledWith(
        '/api/test',
        { name: 'updated' },
        undefined,
      );
    });

    it('delete는 응답 데이터를 반환한다', async () => {
      const client = new ApiClient({ baseURL: 'http://localhost:8080' });
      const mock = getMockInstance();
      mock.delete.mockResolvedValueOnce({ data: null });

      const result = await client.delete('/api/test');
      expect(result).toBeNull();
      expect(mock.delete).toHaveBeenCalledWith('/api/test', undefined);
    });
  });
});
