import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { ReplenishmentScreen } from '@/features/scm-replenishment';
import type { SuggestionPage } from '@/features/scm-replenishment';
import { runAxe } from '../a11y/axe-helper';

vi.mock('next/navigation', () => ({
  usePathname: () => '/scm/replenishment',
}));

/**
 * `features/scm-replenishment` component behaviour (TASK-PC-FE-077 — list +
 * confirm-gated approve/dismiss):
 *   - suggestion table (status/skuCode filter + pagination + triggerAvailableQty)
 *   - approve/dismiss CONFIRM-GATED (no one-click) + state-disabled buttons
 *   - optional note/reason in the BODY; NO Idempotency-Key / X-Operator-Reason
 *   - approve success surfaces the DRAFT poId/poStatus + a Procurement
 *     affordance; the screen issues NO PO submit/confirm/cancel call
 *   - idempotent re-approve (existing poId) → success; SKU_SUPPLIER_UNMAPPED /
 *     INVALID_SUGGESTION_STATE → inline; 503 degrade; mutation invalidation
 *   - WCAG AA axe-clean + Escape cancels dialog
 *
 * Client calls the same-origin `/api/scm/demand-planning/**` proxy via `fetch`
 * (mocked, routed by URL).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

function suggestion(over: Record<string, unknown> = {}) {
  return {
    id: 's-1',
    skuCode: 'SKU-APPLE-001',
    warehouseId: 'wh-1',
    supplierId: 'sup-1',
    suggestedQty: 100,
    status: 'SUGGESTED',
    source: 'ALERT',
    triggerAvailableQty: 5,
    materializedPoId: null,
    createdAt: '2026-06-11T10:05:00Z',
    ...over,
  };
}

const PAGE: SuggestionPage = {
  content: [suggestion()],
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

describe('ReplenishmentScreen — list + filter + pagination + trigger qty', () => {
  it('renders the seeded suggestions incl. the triggerAvailableQty (why it was suggested)', () => {
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });
    expect(
      screen.getByRole('heading', { name: /SCM 보충 운영/ }),
    ).toBeInTheDocument();
    expect(screen.getByTestId('repl-table')).toBeInTheDocument();
    expect(screen.getByTestId('repl-row-status-0')).toHaveTextContent(
      'SUGGESTED',
    );
    expect(screen.getByTestId('repl-row-trigger-0')).toHaveTextContent('5');
    expect(screen.getByTestId('repl-pageinfo')).toHaveTextContent('1 / 2 페이지');
  });

  it('submits the status filter and re-queries the proxy', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...PAGE, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.selectOptions(screen.getByTestId('repl-filter-status'), 'DISMISSED');
    await user.click(screen.getByTestId('repl-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(String(fetchMock.mock.calls[0][0]), 'http://console.local');
    expect(url.pathname).toContain('/api/scm/demand-planning/suggestions');
    expect(url.searchParams.get('status')).toBe('DISMISSED');
  });

  it('paginates to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...PAGE, page: 1 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });

  it('disables approve/dismiss on a non-actionable producer state (MATERIALIZED / DISMISSED)', () => {
    render(
      <ReplenishmentScreen
        suggestions={{
          ...PAGE,
          content: [
            suggestion({ status: 'MATERIALIZED', materializedPoId: 'po-9' }),
          ],
        }}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('repl-approve-0')).toBeDisabled();
    expect(screen.getByTestId('repl-dismiss-0')).toBeDisabled();
  });
});

describe('ReplenishmentScreen — confirm-gated actions + body reason', () => {
  it('approve does NOT fire on a single click — a confirm dialog gates it', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-approve-0'));
    // Dialog shown; NO mutation call yet.
    expect(screen.getByTestId('replenishment-action-dialog')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('confirming approve POSTs to the approve proxy with the OPTIONAL note in the BODY (no Idempotency-Key/reason header)', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/approve')
          ? jsonResponse({ id: 's-1', status: 'MATERIALIZED', poId: 'po-1', poStatus: 'DRAFT' })
          : jsonResponse(PAGE),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-approve-0'));
    await user.type(screen.getByTestId('replenishment-action-note'), '보충 승인');
    await user.click(screen.getByTestId('replenishment-action-confirm'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('/approve')),
      ).toBe(true),
    );
    const approveCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/approve'),
    )!;
    const init = approveCall[1] as RequestInit;
    expect(init.method).toBe('POST');
    const body = JSON.parse(init.body as string);
    expect(body.note).toBe('보충 승인');
    // The reason is in the BODY, never invented headers.
    expect(body.reason).toBeUndefined();
    const h = init.headers as Headers;
    // apiClient sends a Headers object — assert the forbidden headers are absent.
    expect(h.get?.('Idempotency-Key') ?? undefined).toBeFalsy();
    expect(h.get?.('X-Operator-Reason') ?? undefined).toBeFalsy();
  });

  it('confirming dismiss POSTs to the dismiss proxy (reason in body)', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/dismiss')
          ? jsonResponse({ id: 's-1', status: 'DISMISSED' })
          : jsonResponse(PAGE),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-dismiss-0'));
    await user.type(screen.getByTestId('replenishment-action-note'), '중복');
    await user.click(screen.getByTestId('replenishment-action-confirm'));

    await waitFor(() =>
      expect(
        fetchMock.mock.calls.some((c) => String(c[0]).includes('/dismiss')),
      ).toBe(true),
    );
    const dismissCall = fetchMock.mock.calls.find((c) =>
      String(c[0]).includes('/dismiss'),
    )!;
    const body = JSON.parse((dismissCall[1] as RequestInit).body as string);
    expect(body.reason).toBe('중복');
  });
});

describe('ReplenishmentScreen — DRAFT-PO invariant + NO procurement submit call', () => {
  it('approve success surfaces the DRAFT poId/poStatus + a Procurement affordance; NO submit/confirm/cancel call is made', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/approve')
          ? jsonResponse({ id: 's-1', status: 'MATERIALIZED', poId: 'po-draft-1', poStatus: 'DRAFT' })
          : jsonResponse(PAGE),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-approve-0'));
    await user.click(screen.getByTestId('replenishment-action-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('repl-approved-draft')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('repl-approved-poid')).toHaveTextContent('po-draft-1');
    expect(screen.getByTestId('repl-approved-postatus')).toHaveTextContent('DRAFT');
    // The explicit "submit in Procurement" affordance links to the FE-008 PO surface.
    expect(screen.getByTestId('repl-procurement-link')).toHaveAttribute('href', '/scm');
    // The screen issued NO procurement submit/confirm/cancel call.
    for (const call of fetchMock.mock.calls) {
      expect(String(call[0])).not.toMatch(/\/submit|\/confirm|\/cancel/);
    }
  });
});

describe('ReplenishmentScreen — idempotency / state handling', () => {
  it('idempotent re-approve (existing poId via 200) is handled as success (no duplicate-PO error toast)', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/approve')
          ? jsonResponse({ id: 's-1', status: 'MATERIALIZED', poId: 'po-existing', poStatus: 'DRAFT' })
          : jsonResponse(PAGE),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-approve-0'));
    await user.click(screen.getByTestId('replenishment-action-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('repl-approved-poid')).toHaveTextContent('po-existing'),
    );
    // No error surfaced (the idempotent 200 is a success, not a duplicate toast).
    expect(screen.queryByTestId('replenishment-action-error')).not.toBeInTheDocument();
    // Exactly one approve call (no duplicate retry).
    expect(
      fetchMock.mock.calls.filter((c) => String(c[0]).includes('/approve')),
    ).toHaveLength(1);
  });

  it('SKU_SUPPLIER_UNMAPPED (422) → inline error; the dialog stays open (suggestion not optimistically transitioned)', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/approve')
          ? new Response(
              JSON.stringify({ code: 'SKU_SUPPLIER_UNMAPPED', message: 'x' }),
              { status: 422, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(PAGE),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-approve-0'));
    await user.click(screen.getByTestId('replenishment-action-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('replenishment-action-error')).toBeInTheDocument(),
    );
    // No DRAFT-PO success banner (the suggestion stays SUGGESTED).
    expect(screen.queryByTestId('repl-approved-draft')).not.toBeInTheDocument();
    // The shell stays — not a blank crash.
    expect(screen.getByRole('heading', { name: /SCM 보충 운영/ })).toBeInTheDocument();
  });

  it('INVALID_SUGGESTION_STATE (422) on dismiss → inline error (no crash)', async () => {
    const fetchMock = vi.fn((url: string, _init?: RequestInit) =>
      Promise.resolve(
        String(url).includes('/dismiss')
          ? new Response(
              JSON.stringify({ code: 'INVALID_SUGGESTION_STATE', message: 'x' }),
              { status: 422, headers: { 'Content-Type': 'application/json' } },
            )
          : jsonResponse(PAGE),
      ),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-dismiss-0'));
    await user.click(screen.getByTestId('replenishment-action-confirm'));

    await waitFor(() =>
      expect(screen.getByTestId('replenishment-action-error')).toBeInTheDocument(),
    );
    expect(screen.getByRole('heading', { name: /SCM 보충 운영/ })).toBeInTheDocument();
  });
});

describe('ReplenishmentScreen — invalidation + degrade + a11y', () => {
  it('a successful approve invalidates + refetches the suggestions list', async () => {
    let listCalls = 0;
    const fetchMock = vi.fn((url: string, _init?: RequestInit) => {
      if (String(url).includes('/approve')) {
        return Promise.resolve(
          jsonResponse({ id: 's-1', status: 'MATERIALIZED', poId: 'po-1', poStatus: 'DRAFT' }),
        );
      }
      listCalls += 1;
      return Promise.resolve(jsonResponse(PAGE));
    });
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-approve-0'));
    await user.click(screen.getByTestId('replenishment-action-confirm'));

    // The list query is invalidated on approve success → a refetch fires.
    await waitFor(() => expect(listCalls).toBeGreaterThan(0));
  });

  it('a 503 on the list re-query degrades the section (shell stays)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ code: 'SERVICE_UNAVAILABLE', message: 'x' }), {
          status: 503,
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    const user = userEvent.setup();
    render(<ReplenishmentScreen suggestions={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('repl-next'));
    await waitFor(() =>
      expect(screen.getByTestId('repl-degraded')).toBeInTheDocument(),
    );
    expect(screen.getByRole('heading', { name: /SCM 보충 운영/ })).toBeInTheDocument();
  });

  it('the screen is axe-clean and keyboard-operable (WCAG AA)', async () => {
    const { container } = render(<ReplenishmentScreen suggestions={PAGE} />, {
      wrapper: wrapper(),
    });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });

  it('the action dialog is axe-clean and Escape-cancellable', async () => {
    const user = userEvent.setup();
    const { container } = render(<ReplenishmentScreen suggestions={PAGE} />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('repl-approve-0'));
    expect(screen.getByTestId('replenishment-action-dialog')).toBeInTheDocument();
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
    await user.keyboard('{Escape}');
    await waitFor(() =>
      expect(
        screen.queryByTestId('replenishment-action-dialog'),
      ).not.toBeInTheDocument(),
    );
  });
});
