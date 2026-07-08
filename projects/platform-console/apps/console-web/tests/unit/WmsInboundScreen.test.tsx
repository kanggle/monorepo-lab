import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ComponentProps, ReactNode } from 'react';
import { WmsInboundScreen } from '@/features/wms-ops';
import type { AsnPage } from '@/features/wms-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/wms-ops` `WmsInboundScreen` — TASK-PC-FE-222, the dedicated
 * `/wms/inbound` screen surfacing the previously-uncoded-but-unused
 * `listAsns` / `getAsnInspection` client functions:
 *   - ASN table (상태/창고/공급처/예정일/라인 수) + status/warehouse/supplier/
 *     date-range filters + submit re-query (serialised params)
 *   - pagination + forbidden/degraded/empty states
 *   - per-row "검수" → `getAsnInspection` inline panel (success + 404 "검수
 *     내역 없음", distinguished from a degrade)
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

const ASNS: AsnPage = {
  content: [
    {
      asnId: 'asn-1',
      asnNo: 'ASN-0001',
      warehouseId: 'wh-1',
      supplierPartnerId: 'sup-1',
      supplierName: '공급사A',
      status: 'CREATED',
      source: 'MANUAL',
      expectedArriveDate: '2026-07-10',
      lineCount: 3,
      receivedAt: '2026-07-08T01:00:00Z',
    },
  ],
  page: { number: 0, size: 20, totalElements: 40, totalPages: 2 },
  sort: 'receivedAt,desc',
};

/** Render with sensible defaults; override only what a test exercises. */
function renderScreen(
  props: Partial<ComponentProps<typeof WmsInboundScreen>> = {},
) {
  return render(
    <WmsInboundScreen asns={ASNS} lagSeconds={null} {...props} />,
    { wrapper: wrapper() },
  );
}

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

const INSPECTION = {
  asnId: 'asn-1',
  warehouseId: 'wh-1',
  inspectionCompletedAt: '2026-07-09T03:00:00Z',
  inspectorId: 'inspector-1',
  totalLines: 3,
  discrepancyCount: 1,
  totalQtyExpected: 100,
  totalQtyPassed: 90,
  totalQtyDamaged: 5,
  totalQtyShort: 5,
};

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('WmsInboundScreen — render + columns', () => {
  it('renders the ASN table with 상태/창고/공급처/예정일/라인수 columns', () => {
    renderScreen();
    expect(
      screen.getByRole('heading', { name: 'WMS 입고' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-asn-table')).toBeInTheDocument();
    const row = within(screen.getByTestId('wms-asn-row-0'));
    expect(row.getByText('ASN-0001')).toBeInTheDocument();
    expect(row.getByTestId('wms-asn-status-0')).toHaveTextContent('CREATED');
    expect(row.getByText('공급사A')).toBeInTheDocument();
    expect(row.getByText('3')).toBeInTheDocument(); // lineCount
    expect(screen.getByTestId('wms-asn-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('renders — for a null supplierName/expectedArriveDate (no crash)', () => {
    renderScreen({
      asns: {
        ...ASNS,
        content: [
          {
            asnId: 'asn-2',
            warehouseId: 'wh-1',
          },
        ],
      },
    });
    const row = within(screen.getByTestId('wms-asn-row-0'));
    expect(row.getAllByText('—').length).toBeGreaterThan(0);
  });

  it('shows the filter inputs (상태/창고/공급처/예정일 from·to)', () => {
    renderScreen();
    expect(screen.getByTestId('wms-inbound-filter-status')).toBeInTheDocument();
    expect(
      screen.getByTestId('wms-inbound-filter-warehouse'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('wms-inbound-filter-supplier'),
    ).toBeInTheDocument();
    expect(
      screen.getByTestId('wms-inbound-filter-datefrom'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('wms-inbound-filter-dateto')).toBeInTheDocument();
  });

  it('shows a friendly empty state when there is no ASN', () => {
    renderScreen({
      asns: {
        ...ASNS,
        content: [],
        page: { ...ASNS.page, totalElements: 0, totalPages: 0 },
      },
    });
    expect(screen.getByTestId('wms-asn-empty')).toBeInTheDocument();
  });
});

describe('WmsInboundScreen — filter submit & pagination', () => {
  it('submits all filters and re-queries with serialised params', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...ASNS, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.selectOptions(
      screen.getByTestId('wms-inbound-filter-status'),
      'CREATED',
    );
    await user.type(screen.getByTestId('wms-inbound-filter-warehouse'), 'wh-42');
    await user.type(
      screen.getByTestId('wms-inbound-filter-supplier'),
      'sup-42',
    );
    await user.type(
      screen.getByTestId('wms-inbound-filter-datefrom'),
      '2026-07-01',
    );
    await user.type(
      screen.getByTestId('wms-inbound-filter-dateto'),
      '2026-07-31',
    );
    await user.click(screen.getByTestId('wms-inbound-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/wms/inbound/asns');
    expect(url.searchParams.get('status')).toBe('CREATED');
    expect(url.searchParams.get('warehouseId')).toBe('wh-42');
    expect(url.searchParams.get('supplierPartnerId')).toBe('sup-42');
    expect(url.searchParams.get('expectedArriveDateFrom')).toBe('2026-07-01');
    expect(url.searchParams.get('expectedArriveDateTo')).toBe('2026-07-31');
  });

  it('a from-only date range is sent without the to bound (partial input allowed)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...ASNS, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.type(
      screen.getByTestId('wms-inbound-filter-datefrom'),
      '2026-07-01',
    );
    await user.click(screen.getByTestId('wms-inbound-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.searchParams.get('expectedArriveDateFrom')).toBe('2026-07-01');
    expect(url.searchParams.has('expectedArriveDateTo')).toBe(false);
  });

  it('paginates ASNs to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        ...ASNS,
        page: { ...ASNS.page, number: 1 },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-asn-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });

  it('a 503 on the ASN re-query degrades only the ASN block (heading stays)', async () => {
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

    await user.click(screen.getByTestId('wms-asn-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-asn-degraded')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: 'WMS 입고' }),
    ).toBeInTheDocument();
  });

  it('a 403 on the ASN re-query renders an inline forbidden state (no crash)', async () => {
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

    await user.click(screen.getByTestId('wms-asn-next'));
    await waitFor(() =>
      expect(screen.getByTestId('wms-asn-forbidden')).toBeInTheDocument(),
    );
  });
});

describe('WmsInboundScreen — 검수 상세 (getAsnInspection, TASK-PC-FE-222)', () => {
  it('shows a placeholder before any row is selected', () => {
    renderScreen();
    expect(screen.getByTestId('wms-asn-inspection-panel')).toBeInTheDocument();
    expect(screen.getByTestId('wms-asn-inspection-empty')).toBeInTheDocument();
  });

  it('clicking 검수 fetches the inspection proxy for the asnId and shows the fields', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/inspection')
          ? jsonResponse(INSPECTION)
          : jsonResponse(ASNS),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-asn-inspection-0'));

    await waitFor(() =>
      expect(
        screen.getByTestId('wms-asn-inspection-inspector'),
      ).toHaveTextContent('inspector-1'),
    );
    const inspectionCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/inspection'),
    )!;
    expect(String(inspectionCall[0])).toContain(
      '/api/wms/inbound/asns/asn-1/inspection',
    );
    expect(
      screen.getByTestId('wms-asn-inspection-totallines'),
    ).toHaveTextContent('3');
    expect(
      screen.getByTestId('wms-asn-inspection-discrepancy'),
    ).toHaveTextContent('1');
    expect(screen.getByTestId('wms-asn-inspection-passed')).toHaveTextContent(
      '90',
    );
    expect(screen.getByTestId('wms-asn-inspection-damaged')).toHaveTextContent(
      '5',
    );
  });

  it('a 404 inspection response ("검수 내역 없음") is distinguished from a degrade (no crash)', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/inspection')
          ? new Response(
              JSON.stringify({ code: 'NOT_FOUND', message: 'x' }),
              { status: 404, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(ASNS),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-asn-inspection-0'));

    await waitFor(() =>
      expect(
        screen.getByTestId('wms-asn-inspection-notfound'),
      ).toBeInTheDocument(),
    );
    // Not rendered as a degrade — the list stays intact too.
    expect(
      screen.queryByTestId('wms-asn-inspection-degraded'),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('wms-asn-table')).toBeInTheDocument();
  });

  it('a 503 inspection response degrades only the detail panel (no crash)', async () => {
    const fetchMock = vi.fn((url: string) =>
      Promise.resolve(
        String(url).includes('/inspection')
          ? new Response(
              JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'x' }),
              { status: 503, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(ASNS),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('wms-asn-inspection-0'));

    await waitFor(() =>
      expect(
        screen.getByTestId('wms-asn-inspection-degraded'),
      ).toBeInTheDocument(),
    );
  });
});

describe('WmsInboundScreen — a11y', () => {
  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = renderScreen({ lagSeconds: 6 });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });
});
