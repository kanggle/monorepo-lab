import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createElement, type ReactNode } from 'react';

/**
 * `useOperatorOverview()` (TASK-PC-FE-011) — React Query hook for the
 * client-side explicit retry on the operator-overview screen.
 *
 * Pinned behaviour (matches the BFF read-only / bounded-fan-out
 * invariants in § 2.4.9):
 *   - seeded with `initialData` → does NOT auto-fetch on mount (one
 *     overview load per page render; no auto-poll storm into the 6
 *     producers via the BFF).
 *   - explicit `refetch()` triggers exactly ONE fetch.
 *   - no `refetchInterval` / no `refetchOnWindowFocus` / no
 *     `refetchOnReconnect` (the audit-respecting bounded fan-out into
 *     the 6 producers via the BFF).
 *   - `retry: false` (failure does NOT auto-retry — operator decides).
 */

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    ACCOUNTS_TIMEOUT_MS: 50,
    AUDIT_TIMEOUT_MS: 50,
    OPERATORS_TIMEOUT_MS: 50,
    WMS_ADMIN_BASE_URL: 'http://wms.local/api/v1/admin',
    WMS_TIMEOUT_MS: 50,
    SCM_GATEWAY_BASE_URL: 'http://scm.local',
    SCM_TIMEOUT_MS: 50,
    FINANCE_BASE_URL: 'http://finance.local',
    FINANCE_TIMEOUT_MS: 50,
    ERP_BASE_URL: 'http://erp.local',
    ERP_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import {
  useOperatorOverview,
} from '@/features/operator-overview';
import type { OperatorOverview } from '@/features/operator-overview';

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const ENVELOPE: OperatorOverview = {
  asOf: '2026-05-20T10:30:00Z',
  cards: [
    { domain: 'iam', status: 'ok', data: { totalElements: 1 } },
    { domain: 'wms', status: 'ok', data: {} },
    { domain: 'scm', status: 'ok', data: {} },
    { domain: 'finance', status: 'ok', data: {} },
    { domain: 'erp', status: 'ok', data: {} },
    { domain: 'ecommerce', status: 'ok', data: { totalElements: 42 } },
  ],
};

function makeWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) =>
    createElement(QueryClientProvider, { client: qc }, children);
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('useOperatorOverview — explicit retry only (no auto-refresh)', () => {
  it('with seeded initialData → does NOT auto-fetch on mount', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const { result } = renderHook(() => useOperatorOverview(ENVELOPE), {
      wrapper: makeWrapper(),
    });

    // Wait a tick to give react-query a chance to schedule a fetch.
    await new Promise((r) => setTimeout(r, 20));
    expect(fetchMock).not.toHaveBeenCalled();
    expect(result.current.data).toEqual(ENVELOPE);
  });

  it('explicit refetch() → fires exactly ONE fetch (no storm)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(ENVELOPE));
    vi.stubGlobal('fetch', fetchMock);

    const { result } = renderHook(() => useOperatorOverview(ENVELOPE), {
      wrapper: makeWrapper(),
    });

    await act(async () => {
      await result.current.refetch();
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(String(fetchMock.mock.calls[0][0])).toBe(
      'http://console.local/api/console/dashboards/operator-overview',
    );
  });

  it('on failure → does NOT auto-retry (retry: false; operator decides)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValueOnce(jsonResponse({ code: 'BAD_GATEWAY' }, 502));
    vi.stubGlobal('fetch', fetchMock);

    const { result } = renderHook(() => useOperatorOverview(), {
      wrapper: makeWrapper(),
    });

    await waitFor(() => expect(result.current.isError).toBe(true));
    // exactly ONE attempt despite the failure
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
