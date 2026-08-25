import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiErrorResponse } from '@repo/types';

export interface ApiClientConfig {
  baseURL: string;
  /**
   * 요청마다 baseURL 을 **다시 정하고 싶을 때** (TASK-MONO-580 / ADR-MONO-067 D2).
   *
   * 🔴 왜 필요한가: `baseURL` 은 인스턴스 생성 시점에 한 번 굳는다. 그런데 데모 배포에서는
   * 백엔드 주소가 **부팅마다 바뀌므로** 굳은 값이 곧 썩는다. 이 훅이 있으면 요청 인터셉터가
   * 매 요청 그 결과로 덮어쓴다.
   *
   * 🔵 **추가일 뿐 기존 동작을 바꾸지 않는다** — 안 넘기면 예전과 완전히 같다.
   * `null`/`undefined` 를 돌려주면 그 요청은 생성 시점의 `baseURL` 을 쓴다. 즉 "해석 실패"
   * 의 안전한 쪽이 **기존 동작**이다.
   *
   * 🔴 여기서 던지지 마라 — 던지면 그 요청이 죽는다. 해석 실패는 `null` 로 표현한다.
   */
  resolveBaseURL?: () => Promise<string | null | undefined>;
  getAccessToken?: () => string | null;
  getRefreshToken?: () => string | null;
  onTokenRefreshed?: (accessToken: string, refreshToken: string) => void;
  onAuthError?: () => void;
  publicPaths?: string[];
  refreshTimeoutMs?: number;
  requestTimeoutMs?: number;
}

const DEFAULT_PUBLIC_PATHS = [
  '/api/auth/signup',
  '/api/auth/login',
  '/api/auth/refresh',
  '/api/products',
  '/api/search',
];

const DEFAULT_REFRESH_TIMEOUT_MS = 5000;
const DEFAULT_REQUEST_TIMEOUT_MS = 10000;

export class ApiClient {
  private instance: AxiosInstance;
  private config: ApiClientConfig;
  private isRefreshing = false;
  private refreshSubscribers: Array<(token: string) => void> = [];

  private publicPaths: string[];
  private refreshTimeoutMs: number;

  constructor(config: ApiClientConfig) {
    this.config = config;
    this.publicPaths = config.publicPaths ?? DEFAULT_PUBLIC_PATHS;
    this.refreshTimeoutMs = config.refreshTimeoutMs ?? DEFAULT_REFRESH_TIMEOUT_MS;
    this.instance = axios.create({
      baseURL: config.baseURL,
      headers: { 'Content-Type': 'application/json' },
      timeout: config.requestTimeoutMs ?? DEFAULT_REQUEST_TIMEOUT_MS,
    });

    // 토큰 부착 — 예전부터 있던 부분. 아래 두 경로가 **같은 함수**를 쓴다.
    const attachAuth = (
      reqConfig: InternalAxiosRequestConfig,
    ): InternalAxiosRequestConfig => {
      const url = reqConfig.url ?? '';
      if (!this.isPublicPath(url) && this.config.getAccessToken) {
        const token = this.config.getAccessToken();
        if (token) {
          reqConfig.headers.Authorization = `Bearer ${token}`;
        }
      }
      return reqConfig;
    };

    this.instance.interceptors.request.use(
      (reqConfig: InternalAxiosRequestConfig) => {
        // TASK-MONO-580 — 요청 시점 baseURL 재해석 (선택).
        //
        // 🔴🔴 **훅이 없으면 동기로 돌려준다.** 첫 판은 인터셉터 전체를 `async` 로 만들었고
        //    docblock 에는 *"안 넘기면 예전과 완전히 같다"* 고 적었다 — **거짓이었다.**
        //    `async` 는 훅과 무관하게 반환값을 Promise 로 바꾸므로, 인터셉터를 **직접 불러
        //    동기로 단언하던 기존 테스트 9개가 `TypeError` 로 죽었다**(CI 가 잡았다).
        //    "추가일 뿐" 이라는 주장은 **반환 모양까지** 같아야 참이다.
        //
        // 🔴 훅이 던지거나 null 을 내면 덮어쓰지 않는다 — 해석 실패의 안전한 쪽은
        //    "기존 동작" 이지 "요청 실패" 가 아니다.
        const resolve = this.config.resolveBaseURL;
        if (!resolve) return attachAuth(reqConfig);

        return resolve()
          .catch(() => null)
          .then((resolved) => {
            if (resolved) reqConfig.baseURL = resolved;
            return attachAuth(reqConfig);
          });
      },
    );

    this.instance.interceptors.response.use(
      (response) => response,
      async (error) => {
        const originalRequest = error.config as InternalAxiosRequestConfig & {
          _retry?: boolean;
        };

        if (
          error.response?.status === 401 &&
          !originalRequest._retry &&
          !this.isPublicPath(originalRequest.url ?? '')
        ) {
          if (this.isRefreshing) {
            return new Promise((resolve) => {
              this.refreshSubscribers.push((token: string) => {
                originalRequest.headers.Authorization = `Bearer ${token}`;
                resolve(this.instance(originalRequest));
              });
            });
          }

          originalRequest._retry = true;
          this.isRefreshing = true;

          try {
            const refreshToken = this.config.getRefreshToken?.();
            if (!refreshToken) {
              throw new Error('No refresh token');
            }

            const refreshPromise = this.instance.post('/api/auth/refresh', {
              refreshToken,
            });
            const timeoutPromise = new Promise<never>((_, reject) => {
              setTimeout(
                () => reject(new Error('Refresh token timeout')),
                this.refreshTimeoutMs,
              );
            });
            const response = await Promise.race([
              refreshPromise,
              timeoutPromise,
            ]);

            const { accessToken, refreshToken: newRefreshToken } =
              response.data;
            this.config.onTokenRefreshed?.(accessToken, newRefreshToken);

            this.refreshSubscribers.forEach((cb) => cb(accessToken));
            this.refreshSubscribers = [];

            originalRequest.headers.Authorization = `Bearer ${accessToken}`;
            return this.instance(originalRequest);
          } catch {
            this.refreshSubscribers = [];
            this.config.onAuthError?.();
            return Promise.reject(error);
          } finally {
            this.isRefreshing = false;
          }
        }

        const apiError: ApiErrorResponse = error.response?.data ?? {
          code: 'NETWORK_ERROR',
          message: error.message ?? 'Network error occurred',
          timestamp: new Date().toISOString(),
        };

        return Promise.reject(apiError);
      },
    );
  }

  private isPublicPath(url: string): boolean {
    return this.publicPaths.some((path) => url.startsWith(path));
  }

  async get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.instance.get<T>(url, config);
    return response.data;
  }

  async post<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<T> {
    const response = await this.instance.post<T>(url, data, config);
    return response.data;
  }

  async put<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<T> {
    const response = await this.instance.put<T>(url, data, config);
    return response.data;
  }

  async patch<T>(
    url: string,
    data?: unknown,
    config?: AxiosRequestConfig,
  ): Promise<T> {
    const response = await this.instance.patch<T>(url, data, config);
    return response.data;
  }

  async delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    const response = await this.instance.delete<T>(url, config);
    return response.data;
  }
}
