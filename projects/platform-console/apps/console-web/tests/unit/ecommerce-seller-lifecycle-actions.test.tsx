import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import {
  render,
  screen,
  fireEvent,
  waitFor,
  cleanup,
} from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { SellerDetail } from '@/features/ecommerce-ops/components/SellerDetail';
import type { SellerDetail as SellerDetailType } from '@/features/ecommerce-ops/api/seller-types';

/**
 * TASK-PC-FE-154 — seller lifecycle actions on the detail screen (ADR-MONO-042).
 * The action set is status-conditional; each action is confirm-gated and POSTs a
 * bodyless request to its `/api/ecommerce/sellers/{id}/{action}` proxy.
 */

function seed(status: string): SellerDetailType {
  return {
    sellerId: 'acme-corp',
    displayName: 'Acme Corporation',
    status,
    createdAt: '2026-06-14T00:00:00Z',
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** Routes GET detail → the seed; POST {action} → the configured status. */
function stubFetch(detail: SellerDetailType, postStatus = 204) {
  const fetchMock = vi.fn(async (input: unknown, init?: RequestInit) => {
    const url = String(input);
    const method = (init?.method ?? 'GET').toUpperCase();
    if (method === 'POST') return new Response(null, { status: postStatus });
    return jsonResponse(detail); // GET detail refetch
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function renderDetail(detail: SellerDetailType) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  return render(<SellerDetail seller={detail} />, { wrapper });
}

beforeEach(() => vi.unstubAllGlobals());
afterEach(() => cleanup());

describe('SellerDetail — status-conditional action visibility', () => {
  it('ACTIVE → 정지 + 폐점, no 프로비저닝', () => {
    stubFetch(seed('ACTIVE'));
    renderDetail(seed('ACTIVE'));
    expect(screen.getByTestId('seller-action-suspend')).toBeInTheDocument();
    expect(screen.getByTestId('seller-action-close')).toBeInTheDocument();
    expect(screen.queryByTestId('seller-action-provision')).toBeNull();
  });

  it('PENDING_PROVISIONING → 프로비저닝 only', () => {
    stubFetch(seed('PENDING_PROVISIONING'));
    renderDetail(seed('PENDING_PROVISIONING'));
    expect(screen.getByTestId('seller-action-provision')).toBeInTheDocument();
    expect(screen.queryByTestId('seller-action-suspend')).toBeNull();
    expect(screen.queryByTestId('seller-action-close')).toBeNull();
  });

  it('SUSPENDED → 폐점 only', () => {
    stubFetch(seed('SUSPENDED'));
    renderDetail(seed('SUSPENDED'));
    expect(screen.getByTestId('seller-action-close')).toBeInTheDocument();
    expect(screen.queryByTestId('seller-action-suspend')).toBeNull();
    expect(screen.queryByTestId('seller-action-provision')).toBeNull();
  });

  it('CLOSED (terminal) → no action buttons', () => {
    stubFetch(seed('CLOSED'));
    renderDetail(seed('CLOSED'));
    expect(screen.queryByTestId('seller-action-provision')).toBeNull();
    expect(screen.queryByTestId('seller-action-suspend')).toBeNull();
    expect(screen.queryByTestId('seller-action-close')).toBeNull();
  });

  it('unknown status renders a neutral badge and no actions (no crash)', () => {
    stubFetch(seed('FUTURE_STATE'));
    renderDetail(seed('FUTURE_STATE'));
    expect(screen.getByTestId('seller-detail-status')).toHaveTextContent(
      'FUTURE_STATE',
    );
    expect(screen.queryByTestId('seller-action-close')).toBeNull();
  });
});

describe('SellerDetail — confirm-gated action flow', () => {
  it('suspend: opens confirm → POSTs to /suspend → closes on 204', async () => {
    const fetchMock = stubFetch(seed('ACTIVE'), 204);
    renderDetail(seed('ACTIVE'));

    // Action button alone does NOT mutate (confirm gate).
    fireEvent.click(screen.getByTestId('seller-action-suspend'));
    expect(screen.getByTestId('ecommerce-confirm-dialog')).toBeInTheDocument();
    expect(
      fetchMock.mock.calls.filter(
        (c) => (c[1] as RequestInit)?.method === 'POST',
      ),
    ).toHaveLength(0);

    fireEvent.click(screen.getByTestId('ecommerce-confirm-confirm'));

    await waitFor(() => {
      const post = fetchMock.mock.calls.find(
        (c) => (c[1] as RequestInit)?.method === 'POST',
      );
      expect(post).toBeTruthy();
      expect(String(post![0])).toContain(
        '/api/ecommerce/sellers/acme-corp/suspend',
      );
    });
    // Dialog closes on success.
    await waitFor(() =>
      expect(screen.queryByTestId('ecommerce-confirm-dialog')).toBeNull(),
    );
  });

  it('surfaces an inline error and keeps the dialog open on 403', async () => {
    stubFetch(seed('ACTIVE'), 403);
    renderDetail(seed('ACTIVE'));

    fireEvent.click(screen.getByTestId('seller-action-close'));
    fireEvent.click(screen.getByTestId('ecommerce-confirm-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('ecommerce-confirm-error')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('ecommerce-confirm-dialog')).toBeInTheDocument();
  });
});
