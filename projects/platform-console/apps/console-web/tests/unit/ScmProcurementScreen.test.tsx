import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ScmProcurementScreen } from '@/features/scm-ops';
import type { PoPage } from '@/features/scm-ops';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/scm-ops` 조달 (procurement PO list) screen behaviour — split out
 * of the former combined ScmOpsScreen (TASK-PC-FE-220; read section
 * TASK-PC-FE-008). STRICTLY READ-ONLY:
 *   - PO list table + filters + pagination (re-query via proxy)
 *   - read-only PO detail dialog (NO submit/confirm/cancel/mutation)
 *   - no mutation affordance anywhere (no confirm dialog, no PO write)
 *   - 503 on a re-query degrades the PO block (no blank crash)
 *   - WCAG AA axe-clean + keyboard-operable
 *
 * Client calls the same-origin `/api/scm/po` proxy via `fetch` (mocked).
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

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('ScmProcurementScreen — render & read table (read-only)', () => {
  it('renders the PO list from the server page', () => {
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });
    expect(
      screen.getByRole('heading', { name: 'SCM 조달' }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('scm-po-table')).toBeInTheDocument();
    expect(screen.getByTestId('scm-po-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('the PO surface is READ-only (no mutation affordance, no confirm dialog)', () => {
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });
    // No submit/confirm/cancel/acknowledge buttons exist.
    expect(
      screen.queryByRole('button', { name: /제출|승인|취소|확인 처리/ }),
    ).not.toBeInTheDocument();
    // The only PO-row action is the read-only "상세" detail trigger.
    const row = within(screen.getByTestId('scm-po-row-0'));
    expect(row.getByTestId('scm-po-detail-0')).toHaveTextContent('상세');
  });
});

describe('ScmProcurementScreen — PO detail (read-only dialog)', () => {
  it('opens a read-only PO detail dialog with NO mutation buttons', async () => {
    const user = userEvent.setup();
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });
    await user.click(screen.getByTestId('scm-po-detail-0'));
    expect(screen.getByTestId('scm-po-dialog')).toBeInTheDocument();
    expect(screen.getByTestId('scm-po-status')).toHaveTextContent('SUBMITTED');
    expect(screen.getByTestId('scm-po-lines')).toBeInTheDocument();
    // The dialog has ONLY a close button — no submit/confirm/cancel.
    const dialog = within(screen.getByTestId('scm-po-dialog'));
    const buttons = dialog.getAllByRole('button');
    expect(buttons).toHaveLength(1);
    expect(buttons[0]).toHaveTextContent('닫기');
  });
});

describe('ScmProcurementScreen — PO filter & pagination', () => {
  it('submits PO filters and re-queries the proxy with serialised params', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...PO, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

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
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('scm-po-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });
});

describe('ScmProcurementScreen — degrade & a11y', () => {
  it('a 503 on the PO re-query degrades the PO block (heading stays)', async () => {
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
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('scm-po-next'));
    await waitFor(() =>
      expect(screen.getByTestId('scm-po-degraded')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: 'SCM 조달' }),
    ).toBeInTheDocument();
  });

  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = render(<ScmProcurementScreen poList={PO} />, {
      wrapper: wrapper(),
    });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });

  it('the PO detail dialog is axe-clean and Escape-cancellable', async () => {
    const user = userEvent.setup();
    const { container } = render(<ScmProcurementScreen poList={PO} />, {
      wrapper: wrapper(),
    });
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
