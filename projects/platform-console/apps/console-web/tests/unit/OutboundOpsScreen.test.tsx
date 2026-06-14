import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OutboundOpsScreen } from '@/features/wms-outbound-ops';
import type { OutboundOrderPage } from '@/features/wms-outbound-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-outbound-ops` component behaviour (TASK-PC-FE-057 — list +
 * drill + confirm-gated Pick/Pack/Ship):
 *   - orders table (status filter + pagination, re-query via proxy)
 *   - order drill (lines + saga state) on demand
 *   - Pick/Pack/Ship are CONFIRM-GATED (no one-click advance) + status/saga
 *     gated (Pick disabled unless PICKING+RESERVED, etc.)
 *   - 422 inline (no crash) / 503 degrade / 409 refetch + retry-prompt
 *   - WCAG AA axe-clean + Escape cancels dialog
 *
 * Client calls the same-origin `/api/wms/outbound/**` proxy via `fetch`
 * (mocked, routed by URL).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function order(over: Record<string, unknown> = {}) {
  return {
    orderId: 'o-1',
    orderNo: 'ORD-1',
    status: 'PICKING',
    sagaState: 'RESERVED',
    lineCount: 1,
    createdAt: '2026-06-08T10:00:00Z',
    ...over,
  };
}

const ORDERS: OutboundOrderPage = {
  content: [order()],
  page: { number: 0, size: 20, totalElements: 40, totalPages: 2 },
  sort: 'updatedAt,desc',
};

function drillEnvelope(status: string, saga: string, version = 3) {
  return {
    detail: {
      orderId: 'o-1',
      orderNo: 'ORD-1',
      status,
      sagaState: saga,
      lines: [
        { orderLineId: 'ol-1', lineNo: 1, skuId: 'sku-1', lotId: null, qtyOrdered: 50 },
      ],
      version,
    },
    saga: { sagaId: 'sg-1', orderId: 'o-1', state: saga },
  };
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('OutboundOpsScreen — orders table + filter + pagination', () => {
  it('renders the seeded orders from the server-provided page', () => {
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });
    expect(
      screen.getByRole('heading', { name: 'WMS 출고 운영' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('outbound-table')).toBeInTheDocument();
    expect(screen.getByTestId('outbound-row-status-0')).toHaveTextContent(
      'PICKING',
    );
    expect(screen.getByTestId('outbound-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('submits the status filter and re-queries the proxy', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...ORDERS, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.selectOptions(screen.getByTestId('outbound-status-filter'), 'PACKED');
    await user.click(screen.getByTestId('outbound-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(String(fetchMock.mock.calls[0][0]), 'http://console.local');
    expect(url.pathname).toContain('/api/wms/outbound');
    expect(url.searchParams.get('status')).toBe('PACKED');
  });

  it('paginates to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ ...ORDERS, page: { ...ORDERS.page, number: 1 } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });
});

describe('OutboundOpsScreen — order drill + action gating', () => {
  it('drills into an order showing lines + saga state', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('PICKING', 'RESERVED')),
      ),
    );
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-drill')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('outbound-drill-status')).toHaveTextContent(
      'PICKING',
    );
    expect(screen.getByTestId('outbound-drill-saga')).toHaveTextContent(
      'RESERVED',
    );
    expect(screen.getByTestId('outbound-line-0')).toBeInTheDocument();
  });

  it('Pick is ENABLED only for PICKING + RESERVED; Pack/Ship disabled there', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('PICKING', 'RESERVED')),
      ),
    );
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pick')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('outbound-action-pick')).toBeEnabled();
    expect(screen.getByTestId('outbound-action-pack')).toBeDisabled();
    expect(screen.getByTestId('outbound-action-ship')).toBeDisabled();
  });

  it('Pick is DISABLED when PICKING but saga not yet RESERVED (REQUESTED)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('PICKING', 'REQUESTED')),
      ),
    );
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pick')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('outbound-action-pick')).toBeDisabled();
    expect(screen.getByTestId('outbound-pick-blocked-hint')).toHaveTextContent(
      'REQUESTED',
    );
  });

  it('Pack is ENABLED for PICKED (Pick/Ship disabled there)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('PICKED', 'PICKING_CONFIRMED')),
      ),
    );
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pack')).toBeEnabled(),
    );
    expect(screen.getByTestId('outbound-action-pick')).toBeDisabled();
    expect(screen.getByTestId('outbound-action-ship')).toBeDisabled();
  });

  it('Ship is ENABLED for PACKED (Pick/Pack disabled there)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('PACKED', 'PACKING_CONFIRMED')),
      ),
    );
    const user = userEvent.setup();
    render(
      <OutboundOpsScreen
        orders={{ ...ORDERS, content: [order({ status: 'PACKED' })] }}
      />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-ship')).toBeEnabled(),
    );
    expect(screen.getByTestId('outbound-action-pick')).toBeDisabled();
    expect(screen.getByTestId('outbound-action-pack')).toBeDisabled();
  });
});

describe('OutboundOpsScreen — confirm-gated actions', () => {
  it('Pick does NOT fire on a single click — a confirm dialog gates it', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse(drillEnvelope('PICKING', 'RESERVED')),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pick')).toBeEnabled(),
    );
    const callsBefore = fetchMock.mock.calls.length;
    await user.click(screen.getByTestId('outbound-action-pick'));
    // Dialog shown; NO new (mutation) call yet.
    expect(screen.getByTestId('outbound-action-dialog')).toBeInTheDocument();
    expect(fetchMock.mock.calls.length).toBe(callsBefore);
  });

  it('has NO reason capture in the action dialog (outbound surface is reason-free)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(drillEnvelope('PICKING', 'RESERVED'))),
    );
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pick')).toBeEnabled(),
    );
    await user.click(screen.getByTestId('outbound-action-pick'));
    expect(screen.queryByTestId('confirm-reason')).not.toBeInTheDocument();
  });

  it('confirming Pick posts to the pick proxy with an Idempotency-Key', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/pick')
          ? jsonResponse({ orderStatus: 'PICKED' })
          : jsonResponse(drillEnvelope('PICKING', 'RESERVED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pick')).toBeEnabled(),
    );
    await user.click(screen.getByTestId('outbound-action-pick'));
    await user.click(screen.getByTestId('outbound-action-confirm'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('/pick')),
      ).toBe(true),
    );
    const pickCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/pick'),
    )!;
    const init = pickCall[1] as RequestInit;
    expect(init.method).toBe('POST');
    const body = JSON.parse(init.body as string);
    expect(body.idempotencyKey).toBeTruthy();
    expect(body.reason).toBeUndefined();
  });

  it('422 STATE_TRANSITION_INVALID → inline error (no crash)', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/pick')
          ? new Response(
              JSON.stringify({ code: 'STATE_TRANSITION_INVALID', message: 'x' }),
              { status: 422, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(drillEnvelope('PICKING', 'RESERVED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pick')).toBeEnabled(),
    );
    await user.click(screen.getByTestId('outbound-action-pick'));
    await user.click(screen.getByTestId('outbound-action-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-error')).toBeInTheDocument(),
    );
    // The shell stays — not a blank crash.
    expect(
      screen.getByRole('heading', { name: 'WMS 출고 운영' }),
    ).toBeInTheDocument();
  });

  it('409 CONFLICT → refetch + retry-prompt (no silent auto-retry)', async () => {
    let drillCalls = 0;
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (String(url).includes('/ship')) {
        return Promise.resolve(
          new Response(JSON.stringify({ code: 'CONFLICT', message: 'stale' }), {
            status: 409,
            headers: { 'Content-Type': 'application/json' },
          }),
        );
      }
      drillCalls += 1;
      return Promise.resolve(jsonResponse(drillEnvelope('PACKED', 'PACKING_CONFIRMED')));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OutboundOpsScreen
        orders={{ ...ORDERS, content: [order({ status: 'PACKED' })] }}
      />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-ship')).toBeEnabled(),
    );
    const drillCallsBeforeConflict = drillCalls;
    await user.click(screen.getByTestId('outbound-action-ship'));
    await user.click(screen.getByTestId('outbound-action-confirm'));

    // The conflict prompt surfaces AND a refetch was issued (drill re-read).
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-conflict')).toBeInTheDocument(),
    );
    await waitFor(() => expect(drillCalls).toBeGreaterThan(drillCallsBeforeConflict));
    // The dialog stays open (no silent retry, no auto-close) — operator must retry.
    expect(screen.getByTestId('outbound-action-dialog')).toBeInTheDocument();
  });
});

describe('OutboundOpsScreen — degrade & a11y', () => {
  it('a 503 on the orders re-query degrades the section (shell stays)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'x' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    render(<OutboundOpsScreen orders={ORDERS} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('outbound-next'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-degraded')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 출고 운영' }),
    ).toBeInTheDocument();
  });

  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = render(<OutboundOpsScreen orders={ORDERS} />, {
      wrapper: wrapper(),
    });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });

  it('the action dialog is axe-clean and Escape-cancellable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(drillEnvelope('PICKING', 'RESERVED'))),
    );
    const user = userEvent.setup();
    const { container } = render(<OutboundOpsScreen orders={ORDERS} />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-pick')).toBeEnabled(),
    );
    await user.click(screen.getByTestId('outbound-action-pick'));
    expect(screen.getByTestId('outbound-action-dialog')).toBeInTheDocument();
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
    await user.keyboard('{Escape}');
    await waitFor(() =>
      expect(screen.queryByTestId('outbound-action-dialog')).not.toBeInTheDocument(),
    );
  });
});

describe('OutboundOpsScreen — cancel action (TASK-PC-FE-085)', () => {
  async function drillInto(
    user: ReturnType<typeof userEvent.setup>,
    seedStatus: string,
  ) {
    render(
      <OutboundOpsScreen
        orders={{ ...ORDERS, content: [order({ status: seedStatus })] }}
      />,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-drill')).toBeInTheDocument(),
    );
  }

  it('shows the cancel button for a cancellable order (PICKING)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(drillEnvelope('PICKING', 'RESERVED'))),
    );
    const user = userEvent.setup();
    await drillInto(user, 'PICKING');
    expect(
      screen.getByTestId('outbound-action-cancel-order'),
    ).toBeInTheDocument();
    // Pre-pick (PICKING) → no admin note.
    expect(
      screen.queryByTestId('outbound-cancel-admin-note'),
    ).not.toBeInTheDocument();
  });

  it('hides the cancel button for a SHIPPED (terminal) order', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('SHIPPED', 'SHIPPED_NOT_NOTIFIED')),
      ),
    );
    const user = userEvent.setup();
    await drillInto(user, 'SHIPPED');
    expect(
      screen.queryByTestId('outbound-action-cancel-order'),
    ).not.toBeInTheDocument();
  });

  it('shows the OUTBOUND_ADMIN hint for a post-pick (PACKED) cancel', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('PACKED', 'PACKING_CONFIRMED')),
      ),
    );
    const user = userEvent.setup();
    await drillInto(user, 'PACKED');
    expect(screen.getByTestId('outbound-action-cancel-order')).toBeInTheDocument();
    expect(screen.getByTestId('outbound-cancel-admin-note')).toBeInTheDocument();
  });

  it('requires a reason (3..500) — confirm disabled until valid, no producer call', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse(drillEnvelope('PICKING', 'RESERVED')));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'PICKING');

    await user.click(screen.getByTestId('outbound-action-cancel-order'));
    expect(screen.getByTestId('outbound-cancel-dialog')).toBeInTheDocument();
    const confirm = screen.getByTestId('outbound-cancel-confirm');
    expect(confirm).toBeDisabled(); // empty reason
    await user.type(screen.getByTestId('outbound-cancel-reason'), 'ab');
    expect(confirm).toBeDisabled(); // 2 chars < 3
    await user.type(screen.getByTestId('outbound-cancel-reason'), 'c');
    expect(confirm).toBeEnabled(); // 3 chars
    // No cancel producer call was fabricated (only the drill read happened).
    expect(
      fetchMock.mock.calls.some((c) => String(c[0]).includes('/cancel')),
    ).toBe(false);
  });

  it('confirming cancel posts { reason } + Idempotency-Key to the cancel proxy', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/cancel')
          ? jsonResponse({
              orderId: 'o-1',
              status: 'CANCELLED',
              sagaState: 'CANCELLATION_REQUESTED',
            })
          : jsonResponse(drillEnvelope('PICKING', 'RESERVED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'PICKING');

    await user.click(screen.getByTestId('outbound-action-cancel-order'));
    await user.type(
      screen.getByTestId('outbound-cancel-reason'),
      '고객 주문 취소 요청',
    );
    await user.click(screen.getByTestId('outbound-cancel-confirm'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('/cancel')),
      ).toBe(true),
    );
    const call = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/cancel'),
    )!;
    const init = call[1] as RequestInit;
    expect(init.method).toBe('POST');
    const body = JSON.parse(init.body as string);
    expect(body.reason).toBe('고객 주문 취소 요청');
    expect(body.idempotencyKey).toBeTruthy();
  });

  it('422 ORDER_ALREADY_SHIPPED → inline cancel error (no crash)', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/cancel')
          ? new Response(
              JSON.stringify({ code: 'ORDER_ALREADY_SHIPPED', message: 'x' }),
              { status: 422, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(drillEnvelope('PACKED', 'PACKING_CONFIRMED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'PACKED');

    await user.click(screen.getByTestId('outbound-action-cancel-order'));
    await user.type(screen.getByTestId('outbound-cancel-reason'), '취소 사유');
    await user.click(screen.getByTestId('outbound-cancel-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('outbound-cancel-error')).toHaveTextContent(
        '이미 출고된',
      ),
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 출고 운영' }),
    ).toBeInTheDocument();
  });

  it('403 FORBIDDEN (post-pick, no admin) → inline permission message', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/cancel')
          ? new Response(JSON.stringify({ code: 'FORBIDDEN', message: 'x' }), {
              status: 403,
              headers: { 'Content-Type': 'application/json' },
            })
          : jsonResponse(drillEnvelope('PACKED', 'PACKING_CONFIRMED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'PACKED');

    await user.click(screen.getByTestId('outbound-action-cancel-order'));
    await user.type(screen.getByTestId('outbound-cancel-reason'), '취소 사유');
    await user.click(screen.getByTestId('outbound-cancel-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('outbound-cancel-error')).toHaveTextContent(
        '권한',
      ),
    );
  });

  it('async cancel: after success the CANCELLATION_REQUESTED release-pending hint shows', async () => {
    let drillCalls = 0;
    const fetchMock = vi.fn((url: string) => {
      if (String(url).includes('/cancel')) {
        return Promise.resolve(
          jsonResponse({
            orderId: 'o-1',
            status: 'CANCELLED',
            sagaState: 'CANCELLATION_REQUESTED',
          }),
        );
      }
      drillCalls += 1;
      // First drill = PICKING; the post-cancel invalidation refetch = CANCELLED
      // with the saga still releasing inventory (async).
      return Promise.resolve(
        drillCalls === 1
          ? jsonResponse(drillEnvelope('PICKING', 'RESERVED'))
          : jsonResponse(drillEnvelope('CANCELLED', 'CANCELLATION_REQUESTED')),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'PICKING');

    await user.click(screen.getByTestId('outbound-action-cancel-order'));
    await user.type(screen.getByTestId('outbound-cancel-reason'), '취소 사유');
    await user.click(screen.getByTestId('outbound-cancel-confirm'));

    await waitFor(() =>
      expect(
        screen.getByTestId('outbound-cancel-pending-hint'),
      ).toBeInTheDocument(),
    );
  });
});

describe('OutboundOpsScreen — TMS retry action (TASK-PC-FE-087)', () => {
  async function drillInto(
    user: ReturnType<typeof userEvent.setup>,
    seedStatus: string,
  ) {
    render(
      <OutboundOpsScreen
        orders={{ ...ORDERS, content: [order({ status: seedStatus })] }}
      />,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('outbound-drill-0'));
    await waitFor(() =>
      expect(screen.getByTestId('outbound-drill')).toBeInTheDocument(),
    );
  }

  it('shows the TMS retry action ONLY for SHIPPED + SHIPPED_NOT_NOTIFIED (with admin note)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('SHIPPED', 'SHIPPED_NOT_NOTIFIED')),
      ),
    );
    const user = userEvent.setup();
    await drillInto(user, 'SHIPPED');
    expect(screen.getByTestId('outbound-action-retry-tms')).toBeInTheDocument();
    expect(screen.getByTestId('outbound-retry-admin-note')).toBeInTheDocument();
    // It is the recovery action — the cancel button is absent for SHIPPED.
    expect(
      screen.queryByTestId('outbound-action-cancel-order'),
    ).not.toBeInTheDocument();
  });

  it('hides the TMS retry action for a healthy SHIPPED order (saga COMPLETED)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse(drillEnvelope('SHIPPED', 'COMPLETED'))),
    );
    const user = userEvent.setup();
    await drillInto(user, 'SHIPPED');
    expect(
      screen.queryByTestId('outbound-action-retry-tms'),
    ).not.toBeInTheDocument();
  });

  it('hides the TMS retry action for a non-SHIPPED order (PACKED)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(drillEnvelope('PACKED', 'PACKING_CONFIRMED')),
      ),
    );
    const user = userEvent.setup();
    await drillInto(user, 'PACKED');
    expect(
      screen.queryByTestId('outbound-action-retry-tms'),
    ).not.toBeInTheDocument();
  });

  it('confirming retry posts (reason-free) to the retry-tms proxy with an Idempotency-Key', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/retry-tms')
          ? jsonResponse({ tmsStatus: 'NOTIFIED', sagaState: 'COMPLETED' })
          : jsonResponse(drillEnvelope('SHIPPED', 'SHIPPED_NOT_NOTIFIED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'SHIPPED');

    await user.click(screen.getByTestId('outbound-action-retry-tms'));
    expect(screen.getByTestId('outbound-action-dialog')).toBeInTheDocument();
    await user.click(screen.getByTestId('outbound-action-confirm'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('/retry-tms')),
      ).toBe(true),
    );
    const call = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/retry-tms'),
    )!;
    const init = call[1] as RequestInit;
    expect(init.method).toBe('POST');
    const body = JSON.parse(init.body as string);
    expect(body.idempotencyKey).toBeTruthy();
    // Reason-free — the retry surface carries no reason.
    expect(body.reason).toBeUndefined();
  });

  it('404 SHIPMENT_NOT_FOUND → inline error (no crash)', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/retry-tms')
          ? new Response(
              JSON.stringify({ code: 'SHIPMENT_NOT_FOUND', message: 'x' }),
              { status: 404, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(drillEnvelope('SHIPPED', 'SHIPPED_NOT_NOTIFIED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'SHIPPED');

    await user.click(screen.getByTestId('outbound-action-retry-tms'));
    await user.click(screen.getByTestId('outbound-action-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-error')).toHaveTextContent(
        '출고 건을 찾을 수 없습니다',
      ),
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 출고 운영' }),
    ).toBeInTheDocument();
  });

  it('403 FORBIDDEN (non-admin) → inline permission message', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/retry-tms')
          ? new Response(JSON.stringify({ code: 'FORBIDDEN', message: 'x' }), {
              status: 403,
              headers: { 'Content-Type': 'application/json' },
            })
          : jsonResponse(drillEnvelope('SHIPPED', 'SHIPPED_NOT_NOTIFIED')),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    await drillInto(user, 'SHIPPED');

    await user.click(screen.getByTestId('outbound-action-retry-tms'));
    await user.click(screen.getByTestId('outbound-action-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('outbound-action-error')).toHaveTextContent(
        '권한',
      ),
    );
  });
});
