import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { gatewayFetch } from '@/shared/api/client';
import { ApiError } from '@/shared/api/errors';

describe('gatewayFetch', () => {
  const realFetch = global.fetch;

  beforeEach(() => {
    vi.stubEnv('NEXT_PUBLIC_GATEWAY_URL', 'http://test.gateway');
    vi.stubEnv('GATEWAY_URL_INTERNAL', 'http://test.gateway');
  });

  afterEach(() => {
    global.fetch = realFetch;
    vi.unstubAllEnvs();
    vi.restoreAllMocks();
  });

  it('attaches Authorization header when an access token is provided', async () => {
    const fetchMock = vi.fn(async (_url: string | URL | Request, init: RequestInit | undefined) => {
      expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer abc');
      return new Response(JSON.stringify({ data: { ok: true } }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    global.fetch = fetchMock as unknown as typeof fetch;

    const res = await gatewayFetch<{ ok: boolean }>('/api/v1/community/feed', {
      accessToken: 'abc',
    });
    expect(res.data.ok).toBe(true);
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it('omits Authorization when accessToken is null', async () => {
    const fetchMock = vi.fn(async (_url: string | URL | Request, init: RequestInit | undefined) => {
      expect((init?.headers as Record<string, string>).Authorization).toBeUndefined();
      return new Response(JSON.stringify({ data: {} }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      });
    });
    global.fetch = fetchMock as unknown as typeof fetch;
    await gatewayFetch('/api/v1/anything', { accessToken: null });
  });

  it('throws ApiError on non-2xx responses', async () => {
    global.fetch = vi.fn(
      async () =>
        new Response(JSON.stringify({ code: 'POST_NOT_FOUND', message: 'gone' }), {
          status: 404,
          headers: { 'Content-Type': 'application/json' },
        }),
    ) as unknown as typeof fetch;

    await expect(
      gatewayFetch('/api/v1/community/posts/x', { accessToken: 't' }),
    ).rejects.toBeInstanceOf(ApiError);
  });

  it('wraps network errors as ApiError(0, NETWORK_ERROR)', async () => {
    global.fetch = vi.fn(async () => {
      throw new Error('ECONNREFUSED');
    }) as unknown as typeof fetch;
    try {
      await gatewayFetch('/api/v1/community/feed', { accessToken: null });
      throw new Error('expected throw');
    } catch (err) {
      expect(err).toBeInstanceOf(ApiError);
      const apiErr = err as ApiError;
      expect(apiErr.status).toBe(0);
      expect(apiErr.code).toBe('NETWORK_ERROR');
    }
  });

  it('returns undefined data for 204 No Content', async () => {
    global.fetch = vi.fn(
      async () => new Response(null, { status: 204 }),
    ) as unknown as typeof fetch;
    const res = await gatewayFetch('/api/v1/community/follows/x', {
      accessToken: 't',
      method: 'DELETE',
    });
    expect(res.data).toBeUndefined();
  });
});
