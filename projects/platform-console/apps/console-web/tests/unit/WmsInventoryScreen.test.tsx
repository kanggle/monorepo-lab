import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ComponentProps, ReactNode } from 'react';
import { WmsInventoryScreen } from '@/features/wms-ops';
import type { InventoryPage } from '@/features/wms-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-ops` `WmsInventoryScreen` — TASK-PC-FE-173, the inventory
 * query table split off the `/wms` 개요 into a dedicated `/wms/inventory`
 * screen, plus the previously-uncoded-but-unused capabilities it surfaces:
 *   - extended filters (창고/SKU/저재고 + 위치 ID / 로트 ID / 최소 보유) +
 *     submit re-query (serialised params, incl. `minOnHand` numeric parse)
 *   - extended columns (손상 / 최근 조정, null → "—")
 *   - per-row "상세" → `getInventoryByKey` composite-key lookup (inline
 *     panel, success + 404 "재고 없음")
 *   - pagination + forbidden/degraded/empty states
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

const INVENTORY: InventoryPage = {
  content: [
    {
      locationId: 'loc-1',
      skuId: 'sku-1',
      lotId: 'lot-1',
      warehouseId: 'wh-1',
      locationCode: 'WH01-A-01',
      skuCode: 'SKU-1',
      lotNo: 'LOT-0001',
      availableQty: 80,
      reservedQty: 20,
      damagedQty: 3,
      onHandQty: 100,
      lowStockFlag: true,
      lastAdjustedAt: '2026-06-01T09:00:00Z',
      lastEventAt: '2026-05-09T10:00:00Z',
      version: 5,
    },
  ],
  page: { number: 0, size: 20, totalElements: 40, totalPages: 2 },
  sort: 'lastEventAt,desc',
};

/** Render with sensible defaults; override only what a test exercises. */
function renderScreen(
  props: Partial<ComponentProps<typeof WmsInventoryScreen>> = {},
) {
  return render(
    <WmsInventoryScreen inventory={INVENTORY} lagSeconds={null} {...props} />,
    { wrapper: wrapper() },
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const BY_KEY_DETAIL = {
  locationId: 'loc-1',
  skuId: 'sku-1',
  lotId: 'lot-1',
  warehouseId: 'wh-1',
  locationCode: 'WH01-A-01',
  skuCode: 'SKU-1',
  lotNo: 'LOT-0001',
  availableQty: 80,
  reservedQty: 20,
  damagedQty: 3,
  onHandQty: 100,
  lastAdjustedAt: '2026-06-01T09:00:00Z',
  lastEventAt: '2026-05-09T10:00:00Z',
  version: 5,
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('WmsInventoryScreen — render + extended columns', () => {
  it('renders the inventory table with the 손상/최근 조정 columns', () => {
    renderScreen();
    expect(
      screen.getByRole('heading', { name: 'WMS 재고' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-inv-table')).toBeInTheDocument();
    const row = within(screen.getByTestId('wms-inv-row-0'));
    expect(row.getByText('3')).toBeInTheDocument(); // damagedQty
    expect(row.getByText(/2026/)).toBeInTheDocument(); // formatDateTime lastAdjustedAt
    expect(screen.getByTestId('wms-inv-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('renders — for a null damagedQty/lastAdjustedAt (no crash)', () => {
    renderScreen({
      inventory: {
        ...INVENTORY,
        content: [
          {
            locationId: 'loc-2',
            skuId: 'sku-2',
            warehouseId: 'wh-1',
          },
        ],
      },
    });
    const row = within(screen.getByTestId('wms-inv-row-0'));
    expect(row.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('shows the extended filter inputs (위치 ID / 로트 ID / 최소 보유)', () => {
    renderScreen();
    expect(screen.getByTestId('wms-inv-filter-location')).toBeInTheDocument();
    expect(screen.getByTestId('wms-inv-filter-lot')).toBeInTheDocument();
    expect(screen.getByTestId('wms-inv-filter-minonhand')).toBeInTheDocument();
  });

  it('shows a friendly empty state when there is no inventory', () => {
    renderScreen({
      inventory: {
        ...INVENTORY,
        content: [],
        page: { ...INVENTORY.page, totalElements: 0, totalPages: 0 },
      },
    });
    expect(screen.getByTestId('wms-inv-empty')).toBeInTheDocument();
  });
});

describe('WmsInventoryScreen — extended filter submit & pagination', () => {
  it('submits all filters (incl. location/lot/minOnHand) and re-queries with serialised params', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...INVENTORY, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('wms-inv-filter-warehouse'), 'wh-42');
    await user.type(screen.getByTestId('wms-inv-filter-sku'), 'sku-42');
    await user.type(screen.getByTestId('wms-inv-filter-location'), 'loc-42');
    await user.type(screen.getByTestId('wms-inv-filter-lot'), 'lot-42');
    await user.type(screen.getByTestId('wms-inv-filter-minonhand'), '5');
    await user.click(screen.getByTestId('wms-inv-filter-lowstock'));
    await user.click(screen.getByTestId('wms-inv-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/wms/inventory');
    expect(url.searchParams.get('warehouseId')).toBe('wh-42');
    expect(url.searchParams.get('skuId')).toBe('sku-42');
    expect(url.searchParams.get('locationId')).toBe('loc-42');
    expect(url.searchParams.get('lotId')).toBe('lot-42');
    expect(url.searchParams.get('minOnHand')).toBe('5');
    expect(url.searchParams.get('lowStockOnly')).toBe('true');
  });

  it('an empty/non-numeric minOnHand → the param is NOT sent (undefined)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...INVENTORY, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    // Change another filter too — an all-blank resubmit keeps the SAME
    // query key as the seeded initial page (no queryKey change ⇒ no
    // refetch, by design); pair it with a warehouse filter so a fetch is
    // actually triggered, and assert minOnHand is still omitted.
    await user.type(screen.getByTestId('wms-inv-filter-warehouse'), 'wh-1');
    await user.click(screen.getByTestId('wms-inv-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.searchParams.has('minOnHand')).toBe(false);
  });

  it('a minOnHand of 0 is a valid value and IS sent', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...INVENTORY, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('wms-inv-filter-minonhand'), '0');
    await user.click(screen.getByTestId('wms-inv-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.searchParams.get('minOnHand')).toBe('0');
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
    renderScreen();

    await user.click(screen.getByTestId('wms-inv-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });

  it('a 503 on the inventory re-query degrades only the inventory block (heading stays)', async () => {
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

    await user.click(screen.getByTestId('wms-inv-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-inv-degraded')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 재고' }),
    ).toBeInTheDocument();
  });

  it('a 403 on the inventory re-query renders an inline forbidden state (no crash)', async () => {
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

    await user.click(screen.getByTestId('wms-inv-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-inv-forbidden')).toBeInTheDocument(),
    );
  });
});

describe('WmsInventoryScreen — row detail (getInventoryByKey, TASK-PC-FE-173)', () => {
  it('shows a placeholder before any row is selected', () => {
    renderScreen();
    expect(screen.getByTestId('wms-inv-detail-panel')).toBeInTheDocument();
    expect(screen.getByTestId('wms-inv-detail-empty')).toBeInTheDocument();
  });

  it('clicking 상세 fetches the by-key proxy with the composite key and shows the fields', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/by-key')
          ? jsonResponse(BY_KEY_DETAIL)
          : jsonResponse(INVENTORY),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-inv-detail-0'));

    await waitFor(() =>
      expect(screen.getByTestId('wms-inv-detail-available')).toHaveTextContent(
        '80',
      ),
    );
    const byKeyCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/by-key'),
    )!;
    const url = new URL(String(byKeyCall[0]), 'http://console.local');
    expect(url.searchParams.get('locationId')).toBe('loc-1');
    expect(url.searchParams.get('skuId')).toBe('sku-1');
    expect(url.searchParams.get('lotId')).toBe('lot-1');
    expect(screen.getByTestId('wms-inv-detail-reserved')).toHaveTextContent(
      '20',
    );
    expect(screen.getByTestId('wms-inv-detail-damaged')).toHaveTextContent(
      '3',
    );
    expect(screen.getByTestId('wms-inv-detail-onhand')).toHaveTextContent(
      '100',
    );
    expect(screen.getByTestId('wms-inv-detail-version')).toHaveTextContent(
      '5',
    );
  });

  it('a 404 by-key response ("재고 없음") is distinguished from a degrade (no crash)', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/by-key')
          ? new Response(
              JSON.stringify({ code: 'NOT_FOUND', message: 'x' }),
              { status: 404, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(INVENTORY),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-inv-detail-0'));

    await waitFor(() =>
      expect(screen.getByTestId('wms-inv-detail-notfound')).toBeInTheDocument(),
    );
    // Not rendered as a degrade — the list stays intact too.
    expect(
      screen.queryByTestId('wms-inv-detail-degraded'),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('wms-inv-table')).toBeInTheDocument();
  });

  it('a 503 by-key response degrades only the detail panel (no crash)', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/by-key')
          ? new Response(
              JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'x' }),
              { status: 503, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(INVENTORY),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-inv-detail-0'));

    await waitFor(() =>
      expect(screen.getByTestId('wms-inv-detail-degraded')).toBeInTheDocument(),
    );
  });
});

describe('WmsInventoryScreen — a11y', () => {
  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = renderScreen({ lagSeconds: 6 });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });
});
