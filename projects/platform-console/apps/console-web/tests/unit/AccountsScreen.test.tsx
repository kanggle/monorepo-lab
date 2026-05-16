import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { AccountsScreen } from '@/features/accounts';
import type { AccountPage } from '@/features/accounts';
import { runAxe } from '../a11y/axe-helper';

/**
 * `features/accounts` component behaviour (TASK-PC-FE-002):
 *   - search pagination + detail render
 *   - every destructive op is reason-gated: no reason ⇒ the proxy is NOT
 *     called (the producer call cannot fire)
 *   - gdpr-delete double-confirm: reason alone is insufficient — a typed
 *     `DELETE` confirmation is also required
 *   - bulk-lock: multi-select + per-account outcome rendering
 *   - export: server download (no PII into client state)
 *   - 503 ⇒ the accounts section degrades (no blank crash)
 *   - WCAG AA: the confirm dialog is axe-clean
 *
 * Client calls the same-origin `/api/accounts/**` proxy via `fetch` (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const PAGE: AccountPage = {
  content: [
    { id: 'acc-1', email: 'alice@x.com', status: 'ACTIVE', createdAt: '2026-01-01' },
    { id: 'acc-2', email: 'bob@x.com', status: 'LOCKED', createdAt: '2026-01-02' },
  ],
  totalElements: 40,
  page: 0,
  size: 20,
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

describe('AccountsScreen — render & detail', () => {
  it('renders the server-provided initial page with a row per account', () => {
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(screen.getByTestId('accounts-table')).toBeInTheDocument();
    expect(screen.getByTestId('account-row-acc-1')).toBeInTheDocument();
    expect(screen.getByText('alice@x.com')).toBeInTheDocument();
    const lockedRow = within(screen.getByTestId('account-row-acc-2'));
    expect(lockedRow.getByTestId('account-status')).toHaveTextContent('LOCKED');
    expect(screen.getByTestId('accounts-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('paginates to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({ ...PAGE, page: 1, content: [PAGE.content[0]] }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('accounts-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('/api/accounts?page=1'),
        expect.anything(),
      ),
    );
  });
});

describe('AccountsScreen — destructive ops are reason-gated', () => {
  it('lock: confirm is disabled and the proxy is NOT called until a reason is entered', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('action-lock-acc-1'));
    const dialog = screen.getByTestId('confirm-dialog');
    const submit = within(dialog).getByTestId('confirm-submit');

    // No reason yet → confirm disabled, clicking it does nothing.
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(fetchMock).not.toHaveBeenCalled();

    // Enter a reason → confirm enables → the proxy fires with the reason.
    fetchMock.mockResolvedValue(
      jsonResponse({
        accountId: 'acc-1',
        previousStatus: 'ACTIVE',
        currentStatus: 'LOCKED',
        operatorId: 'op',
        lockedAt: 'x',
        auditId: 'a',
      }),
    );
    await user.type(within(dialog).getByTestId('confirm-reason'), 'abuse report');
    expect(submit).toBeEnabled();
    await user.click(submit);

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/accounts/acc-1/lock',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.reason).toBe('abuse report');
    expect(body.idempotencyKey).toBeTruthy();
  });

  it('gdpr-delete double-confirms: reason alone is NOT enough — a typed DELETE is required', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('action-gdpr-acc-1'));
    const dialog = screen.getByTestId('confirm-dialog');
    const submit = within(dialog).getByTestId('confirm-submit');

    await user.type(
      within(dialog).getByTestId('confirm-reason'),
      'gdpr erasure request',
    );
    // Reason present but the typed confirmation is missing → still blocked.
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(fetchMock).not.toHaveBeenCalled();

    // Wrong phrase → still blocked.
    await user.type(within(dialog).getByTestId('confirm-typed'), 'delete');
    expect(submit).toBeDisabled();

    // Exact phrase → enabled → fires.
    await user.clear(within(dialog).getByTestId('confirm-typed'));
    await user.type(within(dialog).getByTestId('confirm-typed'), 'DELETE');
    fetchMock.mockResolvedValue(
      jsonResponse({ accountId: 'acc-1', status: 'DELETED', maskedAt: 'x', auditId: 'a' }),
    );
    expect(submit).toBeEnabled();
    await user.click(submit);
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/accounts/acc-1/gdpr-delete',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
  });

  it('cancel closes the dialog without calling the proxy', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('action-revoke-acc-1'));
    expect(screen.getByTestId('confirm-dialog')).toBeInTheDocument();
    await user.click(screen.getByTestId('confirm-cancel'));
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });
});

describe('AccountsScreen — bulk-lock multi-select & per-account outcomes', () => {
  it('selects multiple accounts and renders each per-account outcome', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      jsonResponse({
        results: [
          { accountId: 'acc-1', outcome: 'LOCKED' },
          {
            accountId: 'acc-2',
            outcome: 'ALREADY_LOCKED',
            error: { code: 'STATE_TRANSITION_INVALID', message: 'x' },
          },
        ],
      }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('account-select-acc-1'));
    await user.click(screen.getByTestId('account-select-acc-2'));
    await user.click(screen.getByTestId('accounts-bulk-lock-trigger'));

    const dialog = screen.getByTestId('confirm-dialog');
    await user.type(
      within(dialog).getByTestId('confirm-reason'),
      'security incident lockdown',
    );
    await user.click(within(dialog).getByTestId('confirm-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('bulk-result')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('bulk-result-acc-1')).toHaveTextContent('LOCKED');
    expect(screen.getByTestId('bulk-result-acc-2')).toHaveTextContent(
      'ALREADY_LOCKED',
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.accountIds).toEqual(['acc-1', 'acc-2']);
  });
});

describe('AccountsScreen — export & degrade', () => {
  it('export navigates to the server download route with the audit reason (no PII in state)', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { assign } as unknown as Location);
    vi.stubGlobal('prompt', vi.fn().mockReturnValue('subject access request'));
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('action-export-acc-1'));
    expect(assign).toHaveBeenCalledWith(
      expect.stringMatching(
        /^\/api\/accounts\/acc-1\/export\?reason=subject%20access%20request$/,
      ),
    );
  });

  it('export does nothing if the operator cancels the reason prompt', async () => {
    const assign = vi.fn();
    vi.stubGlobal('location', { assign } as unknown as Location);
    vi.stubGlobal('prompt', vi.fn().mockReturnValue(null));
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('action-export-acc-2'));
    expect(assign).not.toHaveBeenCalled();
  });

  it('a 503 on re-query degrades the accounts section (no blank crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse({ code: 'DOWNSTREAM_ERROR' }, 503),
      ),
    );
    const user = userEvent.setup();
    render(<AccountsScreen initial={PAGE} />, { wrapper: wrapper() });

    await user.click(screen.getByTestId('accounts-next'));
    await waitFor(() =>
      expect(screen.getByTestId('accounts-degraded')).toBeInTheDocument(),
    );
    // The heading (shell of this section) is still present — not blank.
    expect(
      screen.getByRole('heading', { name: '계정 운영' }),
    ).toBeInTheDocument();
  });
});

describe('AccountsScreen — accessibility (WCAG AA)', () => {
  it('the confirm dialog is axe-clean and keyboard-dismissable', async () => {
    const user = userEvent.setup();
    const { container } = render(<AccountsScreen initial={PAGE} />, {
      wrapper: wrapper(),
    });
    await user.click(screen.getByTestId('action-lock-acc-1'));
    const dialog = screen.getByTestId('confirm-dialog');
    expect(dialog).toHaveAttribute('role', 'dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');

    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    // Escape cancels (keyboard-operable).
    await user.keyboard('{Escape}');
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
  });
});
