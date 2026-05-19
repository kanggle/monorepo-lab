import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ScmOpsScreen } from '@/features/scm-ops';
import type {
  PoPage,
  SnapshotResponse,
  StalenessResponse,
} from '@/features/scm-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/scm-ops` component behaviour (TASK-PC-FE-008 — STRICTLY
 * READ-ONLY procurement-PO + inventory-visibility):
 *   - PO list table + filters + pagination (re-query via proxy)
 *   - read-only PO detail dialog (NO submit/confirm/cancel/mutation)
 *   - inventory-visibility snapshot / per-SKU / staleness panels
 *   - EVERY inventory-visibility view renders the S5 warning prominently
 *   - the staleness panel shows STALE/UNREACHABLE nodes honestly
 *   - no mutation affordance anywhere (no confirm dialog, no PO write)
 *   - 503 on a re-query degrades the scm section (no blank crash)
 *   - WCAG AA axe-clean + keyboard-operable
 *
 * Client calls the same-origin `/api/scm/**` proxy via `fetch` (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const PO: PoPage = {
  content: [
    {
      id: 'po-1',
      poNumber: 'PO-A1B2',
      supplierId: 'sup-1',
      status: 'SUBMITTED',
      totalAmount: '125000.00',
      currency: 'KRW',
      createdAt: '2026-05-11T08:30:00Z',
      lines: [
        {
          id: 'l-1',
          lineNo: 1,
          sku: 'SKU-001',
          quantity: '10.0000',
          unitPrice: '12500.00',
          receivedQuantity: '0.0000',
        },
      ],
    },
  ],
  page: 0,
  size: 20,
  totalElements: 40,
  totalPages: 2,
};

const SNAP: SnapshotResponse = {
  data: {
    content: [
      {
        id: 's-1',
        nodeId: 'node-1',
        sku: 'SKU-001',
        quantity: 100,
        lastEventAt: '2026-05-01T10:00:00Z',
        version: 3,
        staleness: 'FRESH',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
  },
  meta: { warning: 'Not for procurement decisions (S5)' },
};

const STALE: StalenessResponse = {
  data: [
    {
      nodeId: 'node-1',
      stalenessStatus: 'UNREACHABLE',
      lastEventAt: null,
      lastCheckedAt: '2026-05-01T10:05:00Z',
    },
  ],
  meta: { warning: 'Not for procurement decisions (S5)' },
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

describe('ScmOpsScreen — render & read tables (read-only)', () => {
  it('renders the PO list + snapshot + staleness from the server pages', () => {
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByRole('heading', { name: 'SCM 운영' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('scm-po-table')).toBeInTheDocument();
    expect(screen.getByTestId('scm-po-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
    expect(screen.getByTestId('scm-snap-table')).toBeInTheDocument();
    expect(screen.getByTestId('scm-staleness-table')).toBeInTheDocument();
  });

  it('S5 meta.warning is rendered prominently on EVERY inventory-visibility view (never stripped)', () => {
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    const warnings = screen.getAllByTestId('scm-s5-warning');
    // snapshot + per-SKU + staleness — at least 3 prominent S5 surfaces.
    expect(warnings.length).toBeGreaterThanOrEqual(3);
    for (const w of warnings) {
      expect(w).toHaveAttribute('role', 'alert');
      expect(w).toHaveTextContent('Not for procurement decisions (S5)');
    }
  });

  it('the staleness panel shows an UNREACHABLE node honestly (not hidden)', () => {
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByTestId('scm-staleness-status-0'),
    ).toHaveTextContent('UNREACHABLE');
  });

  it('the PO/snapshot surface is READ-only (no mutation affordance, no confirm dialog)', () => {
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    // No submit/confirm/cancel/acknowledge buttons exist.
    expect(
      screen.queryByRole('button', { name: /제출|승인|취소|확인 처리/ }),
    ).not.toBeInTheDocument();
    // The only PO-row action is the read-only "상세" detail trigger.
    const row = within(screen.getByTestId('scm-po-row-0'));
    expect(row.getByTestId('scm-po-detail-0')).toHaveTextContent('상세');
  });
});

describe('ScmOpsScreen — PO detail (read-only dialog)', () => {
  it('opens a read-only PO detail dialog with NO mutation buttons', async () => {
    const user = userEvent.setup();
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('scm-po-detail-0'));
    expect(screen.getByTestId('scm-po-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('scm-po-status')).toHaveTextContent(
      'SUBMITTED',
    );
    expect(screen.getByTestId('scm-po-lines')).toBeInTheDocument();
    // The dialog has ONLY a close button — no submit/confirm/cancel.
    const dialog = within(screen.getByTestId('scm-po-dialog'));
    const buttons = dialog.getAllByRole('button');
    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveTextContent('닫기');
  });
});

describe('ScmOpsScreen — PO filter & pagination', () => {
  it('submits PO filters and re-queries the proxy with serialised params', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(
        jsonResponse({ ...PO, content: [] }),
      );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });

    await user.selectOptions(
      screen.getByTestId('scm-po-filter-status'),
      'CONFIRMED',
    );
    await user.click(screen.getByTestId('scm-po-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.pathname).toContain('/api/scm/po');
    expect(url.searchParams.get('status')).toBe('CONFIRMED');
  });

  it('paginates the PO list to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...PO, page: 1 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });

    await user.click(screen.getByTestId('scm-po-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });
});

describe('ScmOpsScreen — per-SKU breakdown (S5 surfaced on result)', () => {
  it('fetches a SKU breakdown and renders it WITH its own S5 warning', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        data: {
          sku: 'SKU-001',
          nodes: [{ nodeId: 'n-1', quantity: 100, staleness: 'FRESH' }],
          totalQuantity: 100,
        },
        meta: { warning: 'Not for procurement decisions (S5)' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });

    await user.type(screen.getByTestId('scm-sku-input'), 'SKU-001');
    await user.click(screen.getByTestId('scm-sku-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('scm-sku-result')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('scm-sku-table')).toBeInTheDocument();
    // The per-SKU result carries its OWN S5 warning — surfaced, not
    // stripped (the screen now has 4 S5 surfaces).
    expect(
      screen.getAllByTestId('scm-s5-warning').length,
    ).toBeGreaterThanOrEqual(4);
  });
});

describe('ScmOpsScreen — per-section degrade & a11y', () => {
  it('a 503 on the PO re-query degrades only the PO block (shell + S5 views stay)', async () => {
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
    render(<ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />, {
      wrapper: wrapper(),
    });

    await user.click(screen.getByTestId('scm-po-next'));
    await waitFor(() =>
      expect(screen.getByTestId('scm-po-degraded')).toBeInTheDocument(),
    );
    // The scm heading + inventory-visibility views (with S5) still render.
    expect(
      screen.getByRole('heading', { name: 'SCM 운영' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('scm-snap-table')).toBeInTheDocument();
    expect(
      screen.getAllByTestId('scm-s5-warning').length,
    ).toBeGreaterThan(0);
  });

  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = render(
      <ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />,
      { wrapper: wrapper() },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });

  it('the PO detail dialog is axe-clean and Escape-cancellable', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <ScmOpsScreen poList={PO} snapshot={SNAP} staleness={STALE} />,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('scm-po-detail-0'));
    expect(screen.getByTestId('scm-po-dialog')).toBeInTheDocument();
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
    await user.keyboard('{Escape}');
    await waitFor(() =>
      expect(screen.queryByTestId('scm-po-dialog')).not.toBeInTheDocument(),
    );
  });
});
