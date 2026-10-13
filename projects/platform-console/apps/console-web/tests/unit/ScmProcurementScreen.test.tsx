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
 * TASK-PC-FE-337 — the «공급사 ID» filter is now a `<select>` of real
 * suppliers (`SupplierFilterField`, seeded by `shared/api/scm-suppliers-*`)
 * instead of a raw text input: it ALWAYS issues a mount-time
 * `/api/scm/procurement/suppliers` read (no seed, unlike the PO list — the
 * select needs options to render). Every test below therefore stubs fetch
 * with {@link routedFetch} so that read always resolves instead of hitting
 * the real network.
 *
 * Client calls the same-origin `/api/scm/po` + `/api/scm/procurement/suppliers`
 * proxies via `fetch` (mocked).
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

const SUPPLIERS_PAGE = {
  content: [
    { id: 'sup-1', code: 'SUP-0001', name: 'Acme Components', status: 'ACTIVE' },
    { id: 'sup-2', code: 'SUP-0002', name: 'Globex Parts', status: 'ACTIVE' },
  ],
  page: 0,
  size: 100,
  totalElements: 2,
};

function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Routes a mocked `fetch` by URL substring — the supplier-list call (always
 * fired by `SupplierFilterField` on mount) vs. the PO-list call (fired on
 * filter submit / pagination / the initial degrade re-query). Each call
 * returns a FRESH `Response` object (never a shared instance) — reusing one
 * `Response` across callers double-reads the same body stream, which is the
 * documented shared-Response double-read hazard.
 */
function routedFetch(
  overrides: { po?: () => Response; suppliers?: () => Response } = {},
) {
  return vi.fn((url: string) => {
    const u = String(url);
    if (u.includes('/api/scm/procurement/suppliers')) {
      return Promise.resolve(
        overrides.suppliers ? overrides.suppliers() : jsonResponse(SUPPLIERS_PAGE),
      );
    }
    return Promise.resolve(
      overrides.po ? overrides.po() : jsonResponse({ ...PO, content: [] }),
    );
  });
}

/** The PO-list calls among everything `fetchMock` recorded (excludes the
 *  mount-time supplier-list read). */
function poCalls(fetchMock: ReturnType<typeof vi.fn>) {
  return fetchMock.mock.calls.filter(([u]) =>
    String(u).includes('/api/scm/po'),
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('ScmProcurementScreen — render & read table (read-only)', () => {
  it('renders the PO list from the server page', () => {
    vi.stubGlobal('fetch', routedFetch());
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
    vi.stubGlobal('fetch', routedFetch());
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
    vi.stubGlobal('fetch', routedFetch());
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
    const fetchMock = routedFetch({
      po: () => jsonResponse({ ...PO, content: [] }),
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

    await user.selectOptions(
      screen.getByTestId('scm-po-filter-status'),
      'CONFIRMED',
    );
    await user.click(screen.getByTestId('scm-po-filter-submit'));

    await waitFor(() => expect(poCalls(fetchMock).length).toBeGreaterThan(0));
    const [poUrl] = poCalls(fetchMock).at(-1)!;
    const url = new URL(String(poUrl), 'http://console.local');
    expect(url.pathname).toContain('/api/scm/po');
    expect(url.searchParams.get('status')).toBe('CONFIRMED');
  });

  it('paginates the PO list to the next page (re-query via the proxy)', async () => {
    const fetchMock = routedFetch({
      po: () => jsonResponse({ ...PO, page: 1 }),
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('scm-po-next'));
    await waitFor(() =>
      expect(
        poCalls(fetchMock).some(([u]) => String(u).includes('page=1')),
      ).toBe(true),
    );
  });

  describe('ScmProcurementScreen — supplier filter (TASK-PC-FE-337)', () => {
    it('AC-2 — picking a supplier from the list sends supplierId=<id>; "전체" sends none', async () => {
      const fetchMock = routedFetch({
        po: () => jsonResponse({ ...PO, content: [] }),
      });
      vi.stubGlobal('fetch', fetchMock);
      const user = userEvent.setup();
      render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

      // The supplier list loads and the field renders as a <select> keyed
      // by supplier id (not the raw text input) — wait for the real
      // options (not just the "불러오는 중…" placeholder) to land.
      await waitFor(() => {
        const el = screen.getByTestId(
          'scm-po-filter-supplier',
        ) as HTMLSelectElement;
        expect(el.tagName).toBe('SELECT');
        expect(el.querySelector('option[value="sup-2"]')).toBeTruthy();
      });
      const select = screen.getByTestId(
        'scm-po-filter-supplier',
      ) as HTMLSelectElement;

      // Also pick a non-default STATUS alongside each submit — React Query
      // caches the PO list by the full filter key with a 30s staleTime; the
      // all-blank key is the one SEEDED at mount (`initialData`), so toggling
      // supplierId back to '' while every other filter is still blank would
      // revisit that exact cached key and legitimately serve from cache with
      // NO new fetch. Keeping `status` non-default throughout keeps every
      // submit below on a FRESH key so each one is observable on the wire.
      await user.selectOptions(
        screen.getByTestId('scm-po-filter-status'),
        'CONFIRMED',
      );
      await user.selectOptions(select, 'sup-2');
      await user.click(screen.getByTestId('scm-po-filter-submit'));

      await waitFor(() => expect(poCalls(fetchMock).length).toBeGreaterThan(0));
      let [poUrl] = poCalls(fetchMock).at(-1)!;
      expect(
        new URL(String(poUrl), 'http://console.local').searchParams.get(
          'supplierId',
        ),
      ).toBe('sup-2');

      // Back to "전체 공급사" (blank option) → no supplierId param at all.
      await user.selectOptions(select, '');
      await user.click(screen.getByTestId('scm-po-filter-submit'));
      await waitFor(() => expect(poCalls(fetchMock).length).toBeGreaterThan(1));
      [poUrl] = poCalls(fetchMock).at(-1)!;
      expect(
        new URL(String(poUrl), 'http://console.local').searchParams.has(
          'supplierId',
        ),
      ).toBe(false);
    });

    it('AC-3 — a supplier-list failure falls back to the SAME-testid free-text input (PO filter flow still completes)', async () => {
      const fetchMock = routedFetch({
        suppliers: () => jsonResponse({ code: 'SERVICE_UNAVAILABLE', message: 'x' }, 503),
        po: () => jsonResponse({ ...PO, content: [] }),
      });
      vi.stubGlobal('fetch', fetchMock);
      const user = userEvent.setup();
      render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

      await waitFor(() =>
        expect(
          screen.getByTestId('scm-po-filter-supplier-fallback'),
        ).toBeInTheDocument(),
      );
      const input = screen.getByTestId(
        'scm-po-filter-supplier',
      ) as HTMLInputElement;
      expect(input.tagName).toBe('INPUT');

      await user.type(input, 'SUP-0099');
      await user.click(screen.getByTestId('scm-po-filter-submit'));

      await waitFor(() => expect(poCalls(fetchMock).length).toBeGreaterThan(0));
      const [poUrl] = poCalls(fetchMock).at(-1)!;
      expect(
        new URL(String(poUrl), 'http://console.local').searchParams.get(
          'supplierId',
        ),
      ).toBe('SUP-0099');
    });

    it('AC-4 — the 공급사-코드 발주 hint is visible regardless of filter mode', () => {
      vi.stubGlobal('fetch', routedFetch());
      render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });
      expect(
        screen.getByTestId('scm-po-filter-supplier-hint'),
      ).toHaveTextContent(/보충 제안에서 만든 발주는 공급사 코드로 저장돼/);
    });

    it('«직접 입력» toggles the select to the same-testid text field (reaches a code-stored PO)', async () => {
      const fetchMock = routedFetch({
        po: () => jsonResponse({ ...PO, content: [] }),
      });
      vi.stubGlobal('fetch', fetchMock);
      const user = userEvent.setup();
      render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

      await waitFor(() =>
        expect(
          (screen.getByTestId('scm-po-filter-supplier') as HTMLElement)
            .tagName,
        ).toBe('SELECT'),
      );
      await user.click(screen.getByTestId('scm-po-filter-supplier-mode-manual'));
      const input = screen.getByTestId(
        'scm-po-filter-supplier',
      ) as HTMLInputElement;
      expect(input.tagName).toBe('INPUT');

      await user.type(input, 'SUP-0002');
      await user.click(screen.getByTestId('scm-po-filter-submit'));
      await waitFor(() => expect(poCalls(fetchMock).length).toBeGreaterThan(0));
      const [poUrl] = poCalls(fetchMock).at(-1)!;
      expect(
        new URL(String(poUrl), 'http://console.local').searchParams.get(
          'supplierId',
        ),
      ).toBe('SUP-0002');
    });

    it('switching back to «목록에서 선택» clears a typed code — no hidden filter behind «전체 공급사»', async () => {
      const fetchMock = routedFetch({
        po: () => jsonResponse({ ...PO, content: [] }),
      });
      vi.stubGlobal('fetch', fetchMock);
      const user = userEvent.setup();
      render(<ScmProcurementScreen poList={PO} />, { wrapper: wrapper() });

      await waitFor(() =>
        expect(
          (screen.getByTestId('scm-po-filter-supplier') as HTMLElement).tagName,
        ).toBe('SELECT'),
      );
      await user.click(screen.getByTestId('scm-po-filter-supplier-mode-manual'));
      await user.type(screen.getByTestId('scm-po-filter-supplier'), 'SUP-0002');
      await user.click(screen.getByTestId('scm-po-filter-supplier-mode-select'));

      const select = screen.getByTestId(
        'scm-po-filter-supplier',
      ) as HTMLSelectElement;
      expect(select.tagName).toBe('SELECT');
      expect(select.value).toBe('');

      // Fresh query key (non-default status) so the submit is observable.
      await user.selectOptions(
        screen.getByTestId('scm-po-filter-status'),
        'CONFIRMED',
      );
      await user.click(screen.getByTestId('scm-po-filter-submit'));
      await waitFor(() => expect(poCalls(fetchMock).length).toBeGreaterThan(0));
      const [poUrl] = poCalls(fetchMock).at(-1)!;
      expect(
        new URL(String(poUrl), 'http://console.local').searchParams.has(
          'supplierId',
        ),
      ).toBe(false);
    });
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
    vi.stubGlobal('fetch', routedFetch());
    const { container } = render(<ScmProcurementScreen poList={PO} />, {
      wrapper: wrapper(),
    });
    // A generous timeout — this `waitFor` resolves once the mount-time
    // supplier-list fetch settles, which has been observed to occasionally
    // exceed vitest's 5s default under a loaded test host (confirmed
    // transient by an isolated re-run, not a logic defect).
    await waitFor(
      () =>
        expect(
          (screen.getByTestId('scm-po-filter-supplier') as HTMLElement)
            .tagName,
        ).toBe('SELECT'),
      { timeout: 10_000 },
    );
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  }, 15_000);

  it('the PO detail dialog is axe-clean and Escape-cancellable', async () => {
    vi.stubGlobal('fetch', routedFetch());
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
