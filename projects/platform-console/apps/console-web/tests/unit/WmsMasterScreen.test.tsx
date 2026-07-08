import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ComponentProps, ReactNode } from 'react';
import { WmsMasterScreen } from '@/features/wms-ops';
import type { RefPage } from '@/features/wms-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-ops` `WmsMasterScreen` — TASK-PC-FE-223, the dedicated
 * `/wms/master` screen surfacing the previously-uncoded-but-unused
 * `listRefs` client function (`GET /dashboard/refs/{type}`, § 1.7):
 *   - ref-type tab selector (창고/구역/로케이션/SKU/Lot/거래처) — switching
 *     re-queries that type's page-0
 *   - generic row table (코드/명칭/상태/최근 갱신) + `q`/`status` filters +
 *     submit re-query (serialised params)
 *   - pagination + forbidden/degraded/empty states
 *   - WCAG AA axe-clean
 *
 * Client calls the same-origin `/api/wms/master/refs/{type}` proxy via
 * `fetch` (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const REFS: RefPage = {
  content: [
    {
      id: 'loc-1',
      locationCode: 'WH01-A-01-01-01',
      warehouseId: 'wh-1',
      zoneId: 'zone-1',
      locationType: 'PICK',
      status: 'ACTIVE',
      lastEventAt: '2026-07-08T01:00:00Z',
      version: 3,
    },
  ],
  page: { number: 0, size: 20, totalElements: 40, totalPages: 2 },
  sort: 'lastEventAt,desc',
};

/** Render with sensible defaults; override only what a test exercises. */
function renderScreen(
  props: Partial<ComponentProps<typeof WmsMasterScreen>> = {},
) {
  return render(
    <WmsMasterScreen refs={REFS} refType="locations" lagSeconds={null} {...props} />,
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

describe('WmsMasterScreen — render + columns', () => {
  it('renders the 로케이션(default tab) table with 코드/상태/최근 갱신 columns', () => {
    renderScreen();
    expect(
      screen.getByRole('heading', { name: 'WMS 마스터' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-table')).toBeInTheDocument();
    const row = within(screen.getByTestId('wms-master-row-0'));
    expect(row.getByText('WH01-A-01-01-01')).toBeInTheDocument();
    expect(row.getByTestId('wms-master-status-0')).toHaveTextContent('ACTIVE');
    expect(screen.getByTestId('wms-master-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('renders — for an absent name (LocationRef rows have no `name`, no crash)', () => {
    renderScreen();
    const row = within(screen.getByTestId('wms-master-row-0'));
    expect(row.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('shows the ref-type tabs (창고/구역/로케이션/SKU/Lot/거래처)', () => {
    renderScreen();
    expect(screen.getByTestId('wms-master-tab-warehouses')).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-tab-zones')).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-tab-locations')).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-tab-skus')).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-tab-lots')).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-tab-partners')).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-tab-locations')).toHaveAttribute(
      'aria-selected',
      'true',
    );
  });

  it('shows the filter inputs (검색어/상태)', () => {
    renderScreen();
    expect(screen.getByTestId('wms-master-filter-q')).toBeInTheDocument();
    expect(screen.getByTestId('wms-master-filter-status')).toBeInTheDocument();
  });

  it('shows a friendly empty state when there is no ref row', () => {
    renderScreen({
      refs: {
        ...REFS,
        content: [],
        page: { ...REFS.page, totalElements: 0, totalPages: 0 },
      },
    });
    expect(screen.getByTestId('wms-master-empty')).toBeInTheDocument();
  });
});

describe('WmsMasterScreen — tab switch, filters & pagination', () => {
  it('switching to the 창고 tab re-queries that type (page-0, filters reset)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...REFS, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-master-tab-warehouses'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/wms/master/refs/warehouses');
    expect(url.searchParams.get('page')).toBe('0');
    expect(
      screen.getByTestId('wms-master-tab-warehouses'),
    ).toHaveAttribute('aria-selected', 'true');
  });

  it('submits q/status filters and re-queries with serialised params', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...REFS, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('wms-master-filter-q'), 'WH01');
    await user.type(screen.getByTestId('wms-master-filter-status'), 'ACTIVE');
    await user.click(screen.getByTestId('wms-master-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/wms/master/refs/locations');
    expect(url.searchParams.get('q')).toBe('WH01');
    expect(url.searchParams.get('status')).toBe('ACTIVE');
  });

  it('an empty q is never sent on the wire (task Edge Case)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...REFS, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('wms-master-filter-status'), 'ACTIVE');
    await user.click(screen.getByTestId('wms-master-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.searchParams.has('q')).toBe(false);
  });

  it('paginates to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        ...REFS,
        page: { ...REFS.page, number: 1 },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-master-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });

  it('a 503 on the re-query degrades only the master block (heading stays)', async () => {
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

    await user.click(screen.getByTestId('wms-master-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-master-degraded')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 마스터' }),
    ).toBeInTheDocument();
  });

  it('a 403 on the re-query renders an inline forbidden state (no crash)', async () => {
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

    await user.click(screen.getByTestId('wms-master-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-master-forbidden')).toBeInTheDocument(),
    );
  });
});

describe('WmsMasterScreen — a11y', () => {
  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = renderScreen({ lagSeconds: 6 });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });
});
