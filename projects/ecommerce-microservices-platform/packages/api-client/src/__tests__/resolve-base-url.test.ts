/**
 * `ApiClientConfig.resolveBaseURL` — 요청 시점 baseURL 재해석 (TASK-MONO-580).
 *
 * 🔴 이 훅의 계약에서 중요한 것은 "덮어쓴다" 가 아니라 **"언제 덮어쓰지 않는가"** 다.
 *    훅이 없거나 · null 을 내거나 · 던지면 **생성 시점 baseURL 이 그대로 남아야** 한다.
 *    해석 실패의 안전한 쪽이 "요청 실패" 가 되면, 데모 컨트롤 플레인이 잠깐 죽는 것이
 *    스토어 전체를 죽이게 된다.
 */

import { describe, it, expect, vi, beforeEach } from 'vitest';
import axios from 'axios';
import { ApiClient } from '../client';

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
  return { default: { create: vi.fn(() => mockInstance) } };
});

function requestInterceptor() {
  const mock = (axios.create as ReturnType<typeof vi.fn>).mock.results[0]?.value;
  return mock.interceptors.request.use.mock.calls[0][0];
}

function reqConfig() {
  return {
    url: '/api/products',
    baseURL: 'http://built-in:8080',
    headers: {},
  } as never;
}

beforeEach(() => {
  vi.clearAllMocks();
  (axios.create as ReturnType<typeof vi.fn>).mock.results.length = 0;
});

describe('resolveBaseURL', () => {
  it('값을 내면 그 요청의 baseURL 을 덮어쓴다', async () => {
    new ApiClient({
      baseURL: 'http://built-in:8080',
      resolveBaseURL: async () => 'http://ecommerce.13-125-1-2.sslip.io',
    });

    const out = await requestInterceptor()(reqConfig());
    expect(out.baseURL).toBe('http://ecommerce.13-125-1-2.sslip.io');
  });

  it('🔴 null 을 내면 생성 시점 baseURL 이 남는다 (해석 실패 = 기존 동작)', async () => {
    new ApiClient({
      baseURL: 'http://built-in:8080',
      resolveBaseURL: async () => null,
    });

    const out = await requestInterceptor()(reqConfig());
    expect(out.baseURL).toBe('http://built-in:8080');
  });

  it('🔴 던져도 요청이 죽지 않고 생성 시점 baseURL 이 남는다', async () => {
    new ApiClient({
      baseURL: 'http://built-in:8080',
      resolveBaseURL: async () => {
        throw new Error('control plane down');
      },
    });

    const out = await requestInterceptor()(reqConfig());
    expect(out.baseURL).toBe('http://built-in:8080');
  });

  it('🔵 훅을 안 넘기면 예전과 완전히 같다 (추가일 뿐이다)', async () => {
    new ApiClient({ baseURL: 'http://built-in:8080' });

    const out = await requestInterceptor()(reqConfig());
    expect(out.baseURL).toBe('http://built-in:8080');
  });

  it('🔵 baseURL 재해석이 토큰 부착을 방해하지 않는다 (두 관심사가 같은 인터셉터에 산다)', async () => {
    new ApiClient({
      baseURL: 'http://built-in:8080',
      resolveBaseURL: async () => 'http://resolved',
      getAccessToken: () => 'tok',
    });

    // `/api/products` 는 기본 public path 라 토큰이 안 붙는다 — 비공개 경로로 확인한다.
    const out = await requestInterceptor()({
      url: '/api/orders',
      baseURL: 'http://built-in:8080',
      headers: {},
    } as never);

    expect(out.baseURL).toBe('http://resolved');
    expect(out.headers.Authorization).toBe('Bearer tok');
  });
});
