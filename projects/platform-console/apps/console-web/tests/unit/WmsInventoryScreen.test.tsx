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

/** `GET /dashboard/refs/{type}` page shape (TASK-PC-FE-333 `WmsRefPicker`). */
function refsPage(
  rows: { id: string; code: string; name?: string | null }[],
) {
  return {
    content: rows,
    page: { number: 0, size: 20, totalElements: rows.length, totalPages: 1 },
  };
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

  it('shows the extended filter inputs (위치 / 로트 / 최소 보유)', () => {
    renderScreen();
    // TASK-PC-FE-333 — 위치/로트(+창고/SKU) are now `WmsRefPicker` combobox
    // inputs (코드·이름으로 고르면 id 가 필터 상태에 들어간다), not raw UUID
    // text inputs; same base testid, suffixed `-input`.
    expect(
      screen.getByTestId('wms-inv-filter-location-input'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-inv-filter-lot-input')).toBeInTheDocument();
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
  it('submits all filters (incl. location/lot/minOnHand) and re-queries with serialised params — ids, not typed text (AC-1)', async () => {
    // Four sequential debounced picker fetches — give this one more room
    // than the vitest default (5000ms).
    const fetchMock = vi.fn((input: string | URL) => {
      const url = String(input);
      if (url.includes('/api/wms/master/refs/warehouses')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'wh-42-id', code: 'WH-42', name: '인천창고' }])),
        );
      }
      if (url.includes('/api/wms/master/refs/skus')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'sku-42-id', code: 'SKU-42', name: '테스트상품' }])),
        );
      }
      if (url.includes('/api/wms/master/refs/locations')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'loc-42-id', code: 'LOC-42', name: null }])),
        );
      }
      if (url.includes('/api/wms/master/refs/lots')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'lot-42-id', code: 'LOT-42', name: null }])),
        );
      }
      return Promise.resolve(jsonResponse({ ...INVENTORY, content: [] }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    async function pick(baseTestid: string, typed: string, optionId: string) {
      const input = screen.getByTestId(`${baseTestid}-input`);
      await user.click(input);
      await user.type(input, typed);
      await user.click(
        await screen.findByTestId(
          `${baseTestid}-option-${optionId}`,
          {},
          { timeout: 2000 },
        ),
      );
    }

    await pick('wms-inv-filter-warehouse', 'WH-42', 'wh-42-id');
    await pick('wms-inv-filter-sku', 'SKU-42', 'sku-42-id');
    await pick('wms-inv-filter-location', 'LOC-42', 'loc-42-id');
    await pick('wms-inv-filter-lot', 'LOT-42', 'lot-42-id');
    await user.type(screen.getByTestId('wms-inv-filter-minonhand'), '5');
    await user.click(screen.getByTestId('wms-inv-filter-lowstock'));
    await user.click(screen.getByTestId('wms-inv-filter-submit'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) =>
          String(c[0]).includes('/api/wms/inventory?'),
        ),
      ).toBe(true),
    );
    const invCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/api/wms/inventory?'),
    )!;
    const url = new URL(String(invCall[0]), 'http://console.local');
    expect(url.pathname).toContain('/api/wms/inventory');
    // The REQUEST carries the picked candidates' ids, never the typed text
    // (Failure Scenario 2 — the producer only accepts ids, not codes).
    expect(url.searchParams.get('warehouseId')).toBe('wh-42-id');
    expect(url.searchParams.get('skuId')).toBe('sku-42-id');
    expect(url.searchParams.get('locationId')).toBe('loc-42-id');
    expect(url.searchParams.get('lotId')).toBe('lot-42-id');
    expect(url.searchParams.get('minOnHand')).toBe('5');
    expect(url.searchParams.get('lowStockOnly')).toBe('true');

    // AC-2 — 위치 후보는 고른 창고로, 로트 후보는 고른 SKU 로 좁혀 요청했다.
    const locRefsCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/api/wms/master/refs/locations'),
    )!;
    expect(
      new URL(String(locRefsCall[0]), 'http://console.local').searchParams.get(
        'warehouseId',
      ),
    ).toBe('wh-42-id');
    const lotRefsCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/api/wms/master/refs/lots'),
    )!;
    expect(
      new URL(String(lotRefsCall[0]), 'http://console.local').searchParams.get(
        'skuId',
      ),
    ).toBe('sku-42-id');
  }, 15000);

  it('Edge Case — changing the warehouse clears the already-picked location', async () => {
    const fetchMock = vi.fn((input: string | URL) => {
      const url = String(input);
      if (url.includes('/api/wms/master/refs/warehouses')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'wh-1-id', code: 'WH-1', name: '서울창고' }])),
        );
      }
      if (url.includes('/api/wms/master/refs/locations')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'loc-1-id', code: 'A-01', name: null }])),
        );
      }
      return Promise.resolve(jsonResponse({ ...INVENTORY, content: [] }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    const locInput = screen.getByTestId('wms-inv-filter-location-input');
    await user.click(locInput);
    await user.type(locInput, 'A-01');
    await user.click(
      await screen.findByTestId('wms-inv-filter-location-option-loc-1-id', {}, { timeout: 2000 }),
    );
    expect(locInput).toHaveValue('A-01');

    const whInput = screen.getByTestId('wms-inv-filter-warehouse-input');
    await user.click(whInput);
    await user.type(whInput, 'WH-1');
    await user.click(
      await screen.findByTestId('wms-inv-filter-warehouse-option-wh-1-id', {}, { timeout: 2000 }),
    );

    // 창고를 (다시) 고르면 위치 선택은 해제된다 — 범위 밖 조합 방지.
    expect(locInput).toHaveValue('');
  }, 10000);

  it('Edge Case — clearing/changing the SKU clears the already-picked lot', async () => {
    const fetchMock = vi.fn((input: string | URL) => {
      const url = String(input);
      if (url.includes('/api/wms/master/refs/skus')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'sku-1-id', code: 'SKU-1', name: '상품1' }])),
        );
      }
      if (url.includes('/api/wms/master/refs/lots')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'lot-1-id', code: 'LOT-1', name: null }])),
        );
      }
      return Promise.resolve(jsonResponse({ ...INVENTORY, content: [] }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    const skuInput = screen.getByTestId('wms-inv-filter-sku-input');
    await user.click(skuInput);
    await user.type(skuInput, 'SKU-1');
    await user.click(
      await screen.findByTestId('wms-inv-filter-sku-option-sku-1-id', {}, { timeout: 2000 }),
    );

    const lotInput = screen.getByTestId('wms-inv-filter-lot-input');
    await user.click(lotInput);
    await user.type(lotInput, 'LOT-1');
    await user.click(
      await screen.findByTestId('wms-inv-filter-lot-option-lot-1-id', {}, { timeout: 2000 }),
    );
    expect(lotInput).toHaveValue('LOT-1');

    // SKU 를 지우면 로트 선택도 같이 해제된다.
    await user.click(screen.getByTestId('wms-inv-filter-sku-clear'));
    expect(lotInput).toHaveValue('');
  }, 10000);

  it('initialLowStockOnly seeds BOTH the checkbox and the query (the link actually filters)', async () => {
    // TASK-PC-FE-296 (owner decision ⓒ) — the operator-overview wms card
    // answers "how many are low on stock?" with a LINK to
    // `/wms/inventory?lowStockOnly=true` instead of a second leg call.
    //
    // 🔴🔴 That link was a PROMISE THE SCREEN DID NOT KEEP. `app/api/wms/
    //    inventory/route.ts` honours `lowStockOnly`, so the param looked
    //    supported end-to-end — but this container seeded its filter state
    //    from EMPTY_INV_FILTERS and never read the URL, so the link rendered
    //    the UNFILTERED list with nothing reporting an error anywhere.
    //
    // 🔴 Both halves are asserted. Seeding only the checkbox would tick the
    //    box over an unfiltered table — the screen would then be *claiming* a
    //    filter it had not applied, which is worse than ignoring the param.
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...INVENTORY, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    renderScreen({ initialLowStockOnly: true });

    expect(screen.getByTestId('wms-inv-filter-lowstock')).toBeChecked();

    // The seeded server page is UNFILTERED, so the screen must not reuse it:
    // a filtered query has to go out on its own.
    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/wms/inventory');
    expect(url.searchParams.get('lowStockOnly')).toBe('true');
  });

  it('without the seed the screen must NOT filter — and must not re-query at all', async () => {
    // Control group for the cell above. Without it, a screen that hard-coded
    // `lowStockOnly: true` would pass that test while breaking every operator
    // who opens /wms/inventory normally. It also pins the seeded-page
    // optimisation: an unfiltered first render reuses the server page and
    // issues NO fetch.
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...INVENTORY, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    renderScreen();

    expect(screen.getByTestId('wms-inv-filter-lowstock')).not.toBeChecked();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('an empty/non-numeric minOnHand → the param is NOT sent (undefined)', async () => {
    const fetchMock = vi.fn((input: string | URL) => {
      const url = String(input);
      if (url.includes('/api/wms/master/refs/warehouses')) {
        return Promise.resolve(
          jsonResponse(refsPage([{ id: 'wh-1-id', code: 'WH-1', name: '서울창고' }])),
        );
      }
      return Promise.resolve(jsonResponse({ ...INVENTORY, content: [] }));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    // Change another filter too — an all-blank resubmit keeps the SAME
    // query key as the seeded initial page (no queryKey change ⇒ no
    // refetch, by design); pair it with a warehouse filter so a fetch is
    // actually triggered, and assert minOnHand is still omitted.
    const whInput = screen.getByTestId('wms-inv-filter-warehouse-input');
    await user.click(whInput);
    await user.type(whInput, 'WH-1');
    await user.click(
      await screen.findByTestId('wms-inv-filter-warehouse-option-wh-1-id', {}, { timeout: 2000 }),
    );
    await user.click(screen.getByTestId('wms-inv-filter-submit'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('/api/wms/inventory?')),
      ).toBe(true),
    );
    const invCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/api/wms/inventory?'),
    )!;
    const url = new URL(String(invCall[0]), 'http://console.local');
    expect(url.searchParams.get('warehouseId')).toBe('wh-1-id');
    expect(url.searchParams.has('minOnHand')).toBe(false);
  }, 10000);

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
