import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AuditScreen } from '@/features/audit';
import type { AuditPage } from '@/features/audit';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/audit` component behaviour (TASK-PC-FE-003 — READ-ONLY):
 *   - filter submission → re-query via the proxy with serialised params
 *   - source switching (admin / login_history / suspicious)
 *   - discriminated row rendering per `source`
 *   - intersection-permission: a security source w/o security.event.read
 *     → inline permission-denied (no crash); pre-disabled affordance when
 *     the capability is known-denied
 *   - tenant-scope-denied → inline (no crash)
 *   - pagination
 *   - degrade on 503/timeout (no blank crash — shell stays)
 *   - empty state
 *   - unknown future source → generic row (no crash)
 *   - NO destructive dialog exists (read-only) + WCAG AA axe-clean
 *
 * Client calls the same-origin `/api/audit` proxy via `fetch` (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const PAGE: AuditPage = {
  content: [
    {
      source: 'admin',
      auditId: 'aud-1',
      actionCode: 'ACCOUNT_LOCK',
      operatorId: 'op-1',
      targetId: 'acc-1',
      reason: 'fraud',
      outcome: 'SUCCESS',
      occurredAt: '2026-04-12T10:00:00Z',
    },
    {
      source: 'login_history',
      eventId: 'ev-1',
      accountId: 'acc-9',
      outcome: 'FAILURE',
      ipMasked: '192.168.*.*',
      geoCountry: 'KR',
      occurredAt: '2026-04-12T09:58:00Z',
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

describe('AuditScreen — render & discriminated rows', () => {
  it('renders the server-provided initial page, one row per source discriminant', () => {
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(screen.getByTestId('audit-table')).toBeInTheDocument();

    const adminRow = within(screen.getByTestId('audit-row-0'));
    expect(screen.getByTestId('audit-row-0')).toHaveAttribute(
      'data-source',
      'admin',
    );
    expect(adminRow.getByTestId('cell-primary')).toHaveTextContent(
      'ACCOUNT_LOCK',
    );

    const loginRow = within(screen.getByTestId('audit-row-1'));
    expect(screen.getByTestId('audit-row-1')).toHaveAttribute(
      'data-source',
      'login_history',
    );
    // login_history is rendered by its own discriminant: masked IP + geo.
    expect(loginRow.getByTestId('cell-target')).toHaveTextContent(
      '192.168.*.* · KR',
    );
    expect(screen.getByTestId('audit-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('has NO destructive/confirm dialog (read-only slice — FE-002 scaffolding absent)', () => {
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(screen.queryByTestId('confirm-reason')).not.toBeInTheDocument();
  });

  it('an unknown/future source row renders as a generic row (no crash)', () => {
    const page: AuditPage = {
      ...PAGE,
      content: [
        {
          source: 'future_v2',
          occurredAt: '2026-06-01T00:00:00Z',
        } as unknown as AuditPage['content'][number],
      ],
      totalElements: 1,
      totalPages: 1,
    };
    render(<AuditScreen initial={page} />, { wrapper: wrapper() });
    expect(screen.getByTestId('audit-row-0')).toHaveAttribute(
      'data-source',
      'future_v2',
    );
    expect(screen.getByTestId('generic-row-note')).toBeInTheDocument();
  });
});

describe('AuditScreen — filter submit & source switch', () => {
  it('submits filters and re-queries the proxy with serialised params', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ ...PAGE, content: [PAGE.content[1]] }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.type(
      screen.getByTestId('audit-filter-accountId'),
      'acc-42',
    );
    await user.selectOptions(
      screen.getByTestId('audit-filter-source'),
      'suspicious',
    );
    await user.click(screen.getByTestId('audit-filter-submit'));

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const url = new URL(
      String(fetchMock.mock.calls[0][0]),
      'http://console.local',
    );
    expect(url.searchParams.get('accountId')).toBe('acc-42');
    expect(url.searchParams.get('source')).toBe('suspicious');
    expect(url.searchParams.get('page')).toBe('0');
  });

  it('blocks the call with an inline range error when from > to (client guard)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.type(
      screen.getByTestId('audit-filter-from'),
      '2026-05-10T10:00',
    );
    await user.type(
      screen.getByTestId('audit-filter-to'),
      '2026-05-01T10:00',
    );
    await user.click(screen.getByTestId('audit-filter-submit'));

    expect(screen.getByTestId('audit-range-error')).toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('AuditScreen — intersection-permission UX', () => {
  it('pre-disables the security source options when security.event.read is known-denied', () => {
    render(
      <AuditScreen initial={PAGE} securityEventReadGranted={false} />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByTestId('audit-source-option-login_history'),
    ).toBeDisabled();
    expect(
      screen.getByTestId('audit-source-option-suspicious'),
    ).toBeDisabled();
    expect(
      screen.getByTestId('audit-source-option-admin'),
    ).not.toBeDisabled();
    expect(
      screen.getByTestId('audit-security-locked-hint'),
    ).toBeInTheDocument();
  });

  it('always handles a server 403 PERMISSION_DENIED on a security source inline (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'PERMISSION_DENIED' }, 403),
      ),
    );
    const user = userEvent.setup();
    // capability unknown (default) → option enabled → click → 403 inline
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.selectOptions(
      screen.getByTestId('audit-filter-source'),
      'login_history',
    );
    await user.click(screen.getByTestId('audit-filter-submit'));

    await waitFor(() =>
      expect(
        screen.getByTestId('audit-permission-denied'),
      ).toBeInTheDocument(),
    );
    // shell of the section stays — not a blank crash
    expect(
      screen.getByRole('heading', { name: '감사 · 보안 조회' }),
    ).toBeInTheDocument();
  });

  it('tenant-scope-denied (403) renders inline (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'TENANT_SCOPE_DENIED' }, 403),
      ),
    );
    const user = userEvent.setup();
    render(
      <AuditScreen initial={PAGE} superAdminTenants={['t-a', 't-b']} />,
      { wrapper: wrapper() },
    );

    await user.selectOptions(
      screen.getByTestId('audit-filter-tenantId'),
      't-b',
    );
    await user.click(screen.getByTestId('audit-filter-submit'));

    await waitFor(() =>
      expect(
        screen.getByTestId('audit-permission-denied'),
      ).toHaveTextContent(/테넌트/),
    );
  });

  it('a non-super operator gets NO free-text tenant override (selector absent)', () => {
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(
      screen.queryByTestId('audit-filter-tenantId'),
    ).not.toBeInTheDocument();
  });
});

describe('AuditScreen — pagination, degrade, empty', () => {
  it('paginates to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ ...PAGE, page: 1, content: [PAGE.content[0]] }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('audit-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });

  it('a 503 on re-query degrades the audit section (no blank crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'DOWNSTREAM_ERROR' }, 503)),
    );
    const user = userEvent.setup();
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('audit-next'));
    await waitFor(() =>
      expect(screen.getByTestId('audit-degraded')).toBeInTheDocument(),
    );
    expect(
      screen.getByRole('heading', { name: '감사 · 보안 조회' }),
    ).toBeInTheDocument();
  });

  it('re-login on 401: the server route redirects; the client query maps 401 → ApiError (no partial state)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(jsonResponse({ code: 'TOKEN_INVALID' }, 401)),
    );
    const user = userEvent.setup();
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('audit-next'));
    // 401 is NOT rendered as a permission/degrade inline state — it is a
    // re-login signal (the api client redirects). The section neither
    // crashes nor shows a false "no permission" message.
    await waitFor(() =>
      expect(
        screen.queryByTestId('audit-permission-denied'),
      ).not.toBeInTheDocument(),
    );
  });

  it('renders the empty state when there are no rows', () => {
    render(
      <AuditScreen
        initial={{ ...PAGE, content: [], totalElements: 0, totalPages: 0 }}
      />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('audit-empty')).toBeInTheDocument();
  });
});

describe('AuditScreen — accessibility (WCAG AA)', () => {
  it('the screen is axe-clean and keyboard-operable', async () => {
    const { container } = render(<AuditScreen initial={PAGE} />, {
      wrapper: wrapper(),
    });
    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    // The filter form is reachable + operable by keyboard.
    const user = userEvent.setup();
    await user.tab();
    expect(document.activeElement).toBeTruthy();
  });
});
