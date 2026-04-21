import axios, {
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import type { ApiErrorResponse } from '@repo/types';

export interface ApiClientConfig {
  baseURL: string;
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

    this.instance.interceptors.request.use(
      (reqConfig: InternalAxiosRequestConfig) => {
        const url = reqConfig.url ?? '';
        if (!this.isPublicPath(url) && this.config.getAccessToken) {
          const token = this.config.getAccessToken();
          if (token) {
            reqConfig.headers.Authorization = `Bearer ${token}`;
          }
        }
        return reqConfig;
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
