import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ComponentProps, ReactNode } from 'react';
import { WmsOpsScreen } from '@/features/wms-ops';
import type { AlertPage, ShipmentPage } from '@/features/wms-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-ops` component behaviour:
 *   - shipments / 택배 read table + filters + pagination (TASK-PC-FE-079 —
 *     carrier code / tracking no, READ-only, no mutation affordance)
 *   - (the inventory snapshot table moved OFF this screen to the dedicated
 *     `/wms/inventory` route — TASK-PC-FE-173; see `WmsInventoryScreen.test.tsx`)
 *   - adjustments/shipments are READ-only (no edit affordance)
 *   - alert acknowledge is CONFIRM-GATED (no one-click ack) + reason-free
 *     (no reason capture — wms surface has no X-Operator-Reason)
 *   - the Idempotency-Key is generated per a confirmed action
 *   - 422 STATE_TRANSITION_INVALID (already acknowledged) → inline (no crash)
 *   - 503 on a re-query degrades that section only (no blank crash)
 *   - read-model-lag hint banner when lagSeconds is present
 *   - WCAG AA axe-clean + keyboard-operable
 *
 * Client calls the same-origin `/api/wms/**` proxy via `fetch` (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const ALERTS: AlertPage = {
  content: [
    {
      alertId: 'al-1',
      alertType: 'LOW_STOCK',
      warehouseId: 'wh-1',
      message: 'stock low',
      detectedAt: '2026-05-09T10:00:00Z',
      acknowledged: false,
    },
  ],
  page: { number: 0, size: 20, totalElements: 1, totalPages: 1 },
  sort: 'detectedAt,desc',
};

const SHIPMENTS: ShipmentPage = {
  content: [
    {
      shipmentId: 'sh-1',
      orderId: 'ord-1',
      orderNo: 'OUT-0001',
      warehouseId: 'wh-1',
      shipmentNo: 'SHP-0001',
      carrierCode: 'CJ-LOGISTICS',
      trackingNo: '1234567890',
      shippedAt: '2026-05-09T12:00:00Z',
      totalQty: 12,
    },
  ],
  page: { number: 0, size: 20, totalElements: 30, totalPages: 2 },
  sort: 'shippedAt,desc',
};

/** Render with sensible defaults; override only what a test exercises. */
function renderScreen(props: Partial<ComponentProps<typeof WmsOpsScreen>> = {}) {
  return render(
    <WmsOpsScreen
      alerts={ALERTS}
      shipments={SHIPMENTS}
      lagSeconds={null}
      {...props}
    />,
    { wrapper: wrapper() },
  );
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

describe('WmsOpsScreen — render & read tables', () => {
  it('renders the alerts table from the server-provided page', () => {
    renderScreen();
    expect(
      screen.getByRole('heading', { name: 'WMS 개요' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-alerts-table')).toBeInTheDocument();
  });

  it('renders the shipments / 택배 table with carrier code + tracking no', () => {
    renderScreen();
    expect(screen.getByTestId('wms-ship-table')).toBeInTheDocument();
    const row = within(screen.getByTestId('wms-ship-row-0'));
    expect(row.getByTestId('wms-ship-carrier-0')).toHaveTextContent(
      'CJ-LOGISTICS',
    );
    expect(row.getByText('1234567890')).toBeInTheDocument(); // tracking no
    expect(row.getByText('SHP-0001')).toBeInTheDocument(); // shipment no
    expect(screen.getByTestId('wms-ship-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('renders — for a nullable carrier / tracking no (confirmed without a carrier)', () => {
    renderScreen({
      shipments: {
        ...SHIPMENTS,
        content: [
          {
            shipmentId: 'sh-2',
            shipmentNo: 'SHP-0002',
            carrierCode: null,
            trackingNo: null,
          },
        ],
      },
    });
    const row = within(screen.getByTestId('wms-ship-row-0'));
    // Nullable carrier/tracking render as '—', not a crash.
    expect(row.queryByTestId('wms-ship-carrier-0')).not.toBeInTheDocument();
    expect(row.getByText('SHP-0002')).toBeInTheDocument();
  });

  it('shows a friendly empty state when there are no shipments', () => {
    renderScreen({
      shipments: { ...SHIPMENTS, content: [], page: { ...SHIPMENTS.page, totalElements: 0, totalPages: 0 } },
    });
    expect(screen.getByTestId('wms-ship-empty')).toBeInTheDocument();
    // Not a degraded notice — an empty list is a valid read.
    expect(screen.queryByTestId('wms-ship-degraded')).not.toBeInTheDocument();
  });

  it('tolerates an unknown/future field on a shipment row (no throw)', () => {
    renderScreen({
      shipments: {
        ...SHIPMENTS,
        content: [
          {
            shipmentId: 'sh-3',
            shipmentNo: 'SHP-0003',
            carrierCode: 'HANJIN',
            // a future field the console does not know about
            futureField: { nested: true },
          } as ShipmentPage['content'][number],
        ],
      },
    });
    expect(screen.getByTestId('wms-ship-row-0')).toBeInTheDocument();
  });

  it('shows the read-model-lag hint banner when lagSeconds is present', () => {
    renderScreen({ lagSeconds: 8 });
    expect(screen.getByTestId('wms-lag-hint')).toHaveTextContent(/8초/);
    // The section still renders (eventual-consistency honesty, not an error).
    expect(screen.getByTestId('wms-ship-table')).toBeInTheDocument();
  });

  it('does NOT show the lag hint when lagSeconds is null', () => {
    renderScreen();
    expect(screen.queryByTestId('wms-lag-hint')).not.toBeInTheDocument();
  });

  it('the adjustments/shipments surface is read-only (no edit affordance on read rows)', () => {
    renderScreen();
    const shipRow = within(screen.getByTestId('wms-ship-row-0'));
    expect(shipRow.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('WmsOpsScreen — shipments / 택배 filter & pagination', () => {
  it('submits the carrier-code filter and re-queries the shipments proxy', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...SHIPMENTS, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.type(
      screen.getByTestId('wms-ship-filter-carrier'),
      'CJ-LOGISTICS',
    );
    await user.click(screen.getByTestId('wms-ship-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/wms/shipments');
    expect(url.searchParams.get('carrierCode')).toBe('CJ-LOGISTICS');
  });

  it('paginates shipments to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        ...SHIPMENTS,
        page: { ...SHIPMENTS.page, number: 1 },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-ship-next'));
    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some(
          (c) =>
            String(c[0]).includes('/api/wms/shipments') &&
            String(c[0]).includes('page=1'),
        ),
      ).toBe(true),
    );
  });

  it('a 503 on the shipments re-query degrades only the shipments block (shell + other sections stay)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'x' }),
          { status: 503, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-ship-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-ship-degraded')).toBeInTheDocument(),
    );
    // The alerts table still renders — only shipments degraded.
    expect(screen.getByTestId('wms-alerts-table')).toBeInTheDocument();
  });

  it('a 403 on the shipments re-query renders an inline forbidden state (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: 'FORBIDDEN', message: 'x' }),
          { status: 403, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-ship-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-ship-forbidden')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 개요' }),
    ).toBeInTheDocument();
  });
});

describe('WmsOpsScreen — alert acknowledge (confirm-gated, reason-free)', () => {
  it('does NOT fire the ack on a single click — a confirm dialog gates it', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-alert-ack-0'));
    // The dialog is shown; NO upstream call yet (not one-click).
    expect(screen.getByTestId('wms-ack-dialog')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('has NO reason capture in the ack dialog (wms surface is reason-free)', async () => {
    const user = userEvent.setup();
    renderScreen();
    await user.click(screen.getByTestId('wms-alert-ack-0'));
    // The IAM ConfirmActionDialog has a `confirm-reason` textarea; the wms
    // ack dialog deliberately does NOT (no X-Operator-Reason on wms).
    expect(screen.queryByTestId('confirm-reason')).not.toBeInTheDocument();
  });

  it('confirms the ack → posts with an Idempotency-Key + empty body', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/acknowledge')
          ? jsonResponse({ ...ALERTS.content[0], acknowledged: true })
          : jsonResponse(ALERTS),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-alert-ack-0'));
    await user.click(screen.getByTestId('wms-ack-confirm'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]).includes('/acknowledge'),
        ),
      ).toBe(true),
    );
    const ackCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/acknowledge'),
    )!;
    const init = ackCall[1] as RequestInit;
    expect(init.method).toBe('POST');
    // The client sends ONLY { idempotencyKey } — no reason field.
    const body = JSON.parse(init.body as string);
    expect(body.idempotencyKey).toBeTruthy();
    expect(body.reason).toBeUndefined();
  });

  it('a fresh confirmed attempt regenerates the idempotency key', async () => {
    // Route by URL: ack POST → updated row; the post-success alerts
    // invalidation refetch → a valid AlertPage (so the section keeps
    // rendering — the refetch is CORRECT behaviour, not asserted-against).
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (String(url).includes('/acknowledge')) {
        return Promise.resolve(
          jsonResponse({ ...ALERTS.content[0], acknowledged: true }),
        );
      }
      return Promise.resolve(
        jsonResponse({
          ...ALERTS,
          content: [
            ALERTS.content[0],
            { ...ALERTS.content[0], alertId: 'al-2' },
          ],
        }),
      );
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen({
      alerts: {
        ...ALERTS,
        content: [
          ALERTS.content[0],
          { ...ALERTS.content[0], alertId: 'al-2' },
        ],
      },
    });

    const ackCalls = () =>
      fetchMock.mock.calls.filter((c) =>
        String(c[0]).includes('/acknowledge'),
      );

    await user.click(screen.getByTestId('wms-alert-ack-0'));
    await user.click(screen.getByTestId('wms-ack-confirm'));
    await waitFor(() => expect(ackCalls()).toHaveLength(1));

    await user.click(screen.getByTestId('wms-alert-ack-1'));
    await user.click(screen.getByTestId('wms-ack-confirm'));
    await waitFor(() => expect(ackCalls()).toHaveLength(2));

    const k0 = JSON.parse(
      (ackCalls()[0][1] as RequestInit).body as string,
    ).idempotencyKey;
    const k1 = JSON.parse(
      (ackCalls()[1][1] as RequestInit).body as string,
    ).idempotencyKey;
    // A NEW confirmed action (a new dialog open) → a fresh key (§ 2.4.5
    // stable-per-action / fresh-per-attempt).
    expect(k0).not.toBe(k1);
    expect(k0).toBeTruthy();
    expect(k1).toBeTruthy();
  });

  it('STATE_TRANSITION_INVALID (already acknowledged) → inline error (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({ code: 'STATE_TRANSITION_INVALID', message: 'x' }),
          { status: 422, headers: { 'Content-Type': 'application/json' } },
        ),
      ),
    );
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-alert-ack-0'));
    await user.click(screen.getByTestId('wms-ack-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('wms-ack-error')).toBeInTheDocument(),
    );
    // The shell of the screen stays — not a blank crash.
    expect(
      screen.getByRole('heading', { name: 'WMS 개요' }),
    ).toBeInTheDocument();
  });
});

describe('WmsOpsScreen — per-section degrade & a11y', () => {
  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = renderScreen({ lagSeconds: 6 });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });

  it('the ack dialog is axe-clean and Escape-cancellable', async () => {
    const user = userEvent.setup();
    const { container } = renderScreen();
    await user.click(screen.getByTestId('wms-alert-ack-0'));
    expect(screen.getByTestId('wms-ack-dialog')).toBeInTheDocument();
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
    await user.keyboard('{Escape}');
    await waitFor(() =>
      expect(screen.queryByTestId('wms-ack-dialog')).not.toBeInTheDocument(),
    );
  });
});
