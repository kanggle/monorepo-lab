import { describe, it, expect, vi, beforeEach } from 'vitest';

async function freshClient() {
  vi.resetModules();
  return (await import('@/shared/api/client')).apiClient;
}

describe('apiClient', () => {
  beforeEach(() => {
    vi.unstubAllGlobals();
    // Reset window.location to a mutable stub so we can observe redirects.
    Object.defineProperty(window, 'location', {
      value: { pathname: '/accounts', search: '', assign: vi.fn() },
      writable: true,
    });
  });

  it('retries once via /api/auth/refresh after a 401', async () => {
    const calls: Array<{ url: string; init: RequestInit }> = [];
    const fetchMock = vi.fn(async (url: RequestInfo | URL, init?: RequestInit) => {
      const u = String(url);
      calls.push({ url: u, init: init ?? {} });
      if (u.endsWith('/api/auth/refresh')) {
        return new Response(JSON.stringify({ ok: true }), { status: 200, headers: { 'Content-Type': 'application/json' } });
      }
      // First call returns 401, second returns 200.
      const callsToData = calls.filter((c) => c.url.includes('/api/admin/accounts'));
      if (callsToData.length === 1) {
        return new Response(JSON.stringify({ code: 'TOKEN_INVALID', message: 'expired' }), {
          status: 401,
          headers: { 'Content-Type': 'application/json' },
        });
      }
      return new Response(JSON.stringify({ content: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } });
    });
    vi.stubGlobal('fetch', fetchMock);

    const apiClient = await freshClient();
    const result = await apiClient.get<{ content: unknown[] }>('/api/admin/accounts?email=u@x.com');
    expect(result.content).toEqual([]);
    const refreshCall = calls.find((c) => c.url.endsWith('/api/auth/refresh'));
    expect(refreshCall).toBeDefined();
    // Original endpoint was called twice (once failed, once retried)
    expect(calls.filter((c) => c.url.includes('/api/admin/accounts'))).toHaveLength(2);
  });

  it('redirects to /login when refresh fails', async () => {
    const fetchMock = vi.fn(async (url: RequestInfo | URL) => {
      const u = String(url);
      if (u.endsWith('/api/auth/refresh')) {
        return new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
      }
      return new Response(JSON.stringify({ code: 'TOKEN_INVALID' }), { status: 401, headers: { 'Content-Type': 'application/json' } });
    });
    vi.stubGlobal('fetch', fetchMock);

    const apiClient = await freshClient();
    await expect(apiClient.get('/api/admin/accounts')).rejects.toMatchObject({ status: 401 });
    expect((window.location.assign as ReturnType<typeof vi.fn>)).toHaveBeenCalled();
    const arg = String((window.location.assign as ReturnType<typeof vi.fn>).mock.calls[0][0]);
    expect(arg).toContain('/login?redirect=');
  });
});
