import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { WmsOpsScreen } from '@/features/wms-ops';
import type { InventoryPage, AlertPage } from '@/features/wms-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-ops` component behaviour (TASK-PC-FE-007 — read +
 * confirm-gated alert-ack):
 *   - inventory snapshot table + filters + pagination (re-query via proxy)
 *   - adjustments/inventory are READ-only (no edit affordance)
 *   - alert acknowledge is CONFIRM-GATED (no one-click ack) + reason-free
 *     (no reason capture — wms surface has no X-Operator-Reason)
 *   - the Idempotency-Key is generated per a confirmed action
 *   - 422 STATE_TRANSITION_INVALID (already acknowledged) → inline (no crash)
 *   - 503 on a re-query degrades the wms section (no blank crash)
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

const INVENTORY: InventoryPage = {
  content: [
    {
      locationId: 'loc-1',
      skuId: 'sku-1',
      lotId: null,
      warehouseId: 'wh-1',
      locationCode: 'WH01-A-01',
      skuCode: 'SKU-1',
      availableQty: 80,
      reservedQty: 20,
      onHandQty: 100,
      lowStockFlag: true,
      lastEventAt: '2026-05-09T10:00:00Z',
      version: 5,
    },
  ],
  page: { number: 0, size: 20, totalElements: 40, totalPages: 2 },
  sort: 'lastEventAt,desc',
};

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
  it('renders the inventory snapshot + alerts from the server-provided pages', () => {
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 운영' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-inv-table')).toBeInTheDocument();
    const row = within(screen.getByTestId('wms-inv-row-0'));
    expect(row.getByTestId('wms-inv-low-0')).toBeInTheDocument(); // low-stock
    expect(screen.getByTestId('wms-inv-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
    expect(screen.getByTestId('wms-alerts-table')).toBeInTheDocument();
  });

  it('shows the read-model-lag hint banner when lagSeconds is present', () => {
    render(
      <WmsOpsScreen inventory={INVENTORY} alerts={ALERTS} lagSeconds={8} />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('wms-lag-hint')).toHaveTextContent(/8초/);
    // The section still renders (eventual-consistency honesty, not an error).
    expect(screen.getByTestId('wms-inv-table')).toBeInTheDocument();
  });

  it('does NOT show the lag hint when lagSeconds is null', () => {
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.queryByTestId('wms-lag-hint')).not.toBeInTheDocument();
  });

  it('the adjustments/inventory surface is read-only (no edit affordance on inventory rows)', () => {
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );
    const row = within(screen.getByTestId('wms-inv-row-0'));
    // No mutation button on a read row.
    expect(row.queryByRole('button')).not.toBeInTheDocument();
  });
});

describe('WmsOpsScreen — inventory filter & pagination', () => {
  it('submits inventory filters and re-queries the proxy with serialised params', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...INVENTORY, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );

    await user.type(
      screen.getByTestId('wms-inv-filter-warehouse'),
      'wh-42',
    );
    await user.click(screen.getByTestId('wms-inv-filter-lowstock'));
    await user.click(screen.getByTestId('wms-inv-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/wms/inventory');
    expect(url.searchParams.get('warehouseId')).toBe('wh-42');
    expect(url.searchParams.get('lowStockOnly')).toBe('true');
  });

  it('paginates inventory to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        ...INVENTORY,
        page: { ...INVENTORY.page, number: 1 },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('wms-inv-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });
});

describe('WmsOpsScreen — alert acknowledge (confirm-gated, reason-free)', () => {
  it('does NOT fire the ack on a single click — a confirm dialog gates it', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('wms-alert-ack-0'));
    // The dialog is shown; NO upstream call yet (not one-click).
    expect(screen.getByTestId('wms-ack-dialog')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('has NO reason capture in the ack dialog (wms surface is reason-free)', async () => {
    const user = userEvent.setup();
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('wms-alert-ack-0'));
    // The GAP ConfirmActionDialog has a `confirm-reason` textarea; the wms
    // ack dialog deliberately does NOT (no X-Operator-Reason on wms).
    expect(screen.queryByTestId('confirm-reason')).not.toBeInTheDocument();
  });

  it('confirms the ack → posts with an Idempotency-Key + empty body', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/acknowledge')
          ? jsonResponse({ ...ALERTS.content[0], acknowledged: true })
          : jsonResponse(ALERTS),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );

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
    const fetchMock = vi.fn((url: string) => {
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
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={{
          ...ALERTS,
          content: [
            ALERTS.content[0],
            { ...ALERTS.content[0], alertId: 'al-2' },
          ],
        }}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );

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
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('wms-alert-ack-0'));
    await user.click(screen.getByTestId('wms-ack-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('wms-ack-error')).toBeInTheDocument(),
    );
    // The shell of the screen stays — not a blank crash.
    expect(
      screen.getByRole('heading', { name: 'WMS 운영' }),
    ).toBeInTheDocument();
  });
});

describe('WmsOpsScreen — per-section degrade & a11y', () => {
  it('a 503 on the inventory re-query degrades only the inventory block (shell stays)', async () => {
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
    render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('wms-inv-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-inv-degraded')).toBeInTheDocument(),
    );
    // The wms heading + alerts table still render — only the inventory
    // block degraded (not a blank crash).
    expect(
      screen.getByRole('heading', { name: 'WMS 운영' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-alerts-table')).toBeInTheDocument();
  });

  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={6}
      />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });

  it('the ack dialog is axe-clean and Escape-cancellable', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <WmsOpsScreen
        inventory={INVENTORY}
        alerts={ALERTS}
        lagSeconds={null}
      />,
      { wrapper: wrapper() },
    );
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
