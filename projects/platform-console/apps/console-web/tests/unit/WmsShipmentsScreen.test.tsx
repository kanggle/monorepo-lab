import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ComponentProps, ReactNode } from 'react';
import { WmsShipmentsScreen } from '@/features/wms-ops';
import type { ShipmentPage } from '@/features/wms-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-ops` 택배/출고 read section (TASK-PC-FE-175 — moved off the
 * `/wms` 개요 onto the `/wms/outbound` 출고 page; these cases moved verbatim
 * from `WmsOpsScreen.test.tsx`):
 *   - shipments / 택배 read table + carrier/warehouse filters + pagination
 *     (carrier code / tracking no, READ-only, no mutation affordance)
 *   - nullable carrier/tracking render '—' (confirmed without a carrier)
 *   - friendly empty state (distinct from a degraded notice)
 *   - tolerant of an unknown/future field on a row (no throw)
 *   - a 503 on a re-query degrades the section only; a 403 → inline forbidden
 *   - read-model-lag hint banner when lagSeconds is present
 *   - WCAG AA axe-clean
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

function renderScreen(
  props: Partial<ComponentProps<typeof WmsShipmentsScreen>> = {},
) {
  return render(
    <WmsShipmentsScreen shipments={SHIPMENTS} lagSeconds={null} {...props} />,
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

describe('WmsShipmentsScreen — render & read table', () => {
  it('renders the shipments / 택배 table with carrier code + tracking no', () => {
    renderScreen();
    expect(
      screen.getByRole('heading', { name: '택배 / 출고' }),
    ).toBeInTheDocument();
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
      shipments: {
        ...SHIPMENTS,
        content: [],
        page: { ...SHIPMENTS.page, totalElements: 0, totalPages: 0 },
      },
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

  it('the shipments surface is read-only (no edit affordance on read rows)', () => {
    renderScreen();
    const shipRow = within(screen.getByTestId('wms-ship-row-0'));
    expect(shipRow.queryByRole('button')).not.toBeInTheDocument();
  });

  it('shows the read-model-lag hint banner when lagSeconds is present', () => {
    renderScreen({ lagSeconds: 8 });
    expect(screen.getByTestId('wms-ship-lag-hint')).toHaveTextContent(/8초/);
    // The table still renders (eventual-consistency honesty, not an error).
    expect(screen.getByTestId('wms-ship-table')).toBeInTheDocument();
  });

  it('does NOT show the lag hint when lagSeconds is null', () => {
    renderScreen();
    expect(screen.queryByTestId('wms-ship-lag-hint')).not.toBeInTheDocument();
  });
});

describe('WmsShipmentsScreen — filter & pagination', () => {
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

  it('a 503 on the shipments re-query degrades only this section (form stays)', async () => {
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
    // The filter form (section shell) still renders — only the table degraded.
    expect(screen.getByTestId('wms-ship-filter-submit')).toBeInTheDocument();
  });

  it('a 403 on the shipments re-query renders an inline forbidden state (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'FORBIDDEN', message: 'x' }), {
          status: 403,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-ship-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-ship-forbidden')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: '택배 / 출고' }),
    ).toBeInTheDocument();
  });
});

describe('WmsShipmentsScreen — a11y', () => {
  it('the section is axe-clean (WCAG AA)', async () => {
    const { container } = renderScreen({ lagSeconds: 6 });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
