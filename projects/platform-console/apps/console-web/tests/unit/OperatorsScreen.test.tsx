import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorsScreen } from '@/features/operators';
import type { OperatorPage } from '@/features/operators';
import { runAxe } from '../a11y/axe-helper';

// PC-FE-179 added a debounced account-existence pre-flight in CreateOperatorForm
// (a GET /api/accounts lookup on email+tenant), which TASK-MONO-334 turned into a
// submit GATE (create is enabled only when the email resolves to an existing tenant
// account). That side-effecting fetch is out of scope for OperatorsScreen behaviour
// tests and, when it races the create flow, lands as the first `fetch` call. Stub it
// to a no-network **exists (true)** so the create-flow tests can reach the confirm
// dialog; those tests `await` the OIDC-ok note before submitting. The gate's own
// absent/unavailable behaviour is covered by CreateOperatorForm.test.
//
// A PLAIN function (not vi.fn) on purpose: the 400ms debounce timer can fire
// after a test's teardown, and clearAllMocks/clearMocks would wipe a vi.fn's
// mockResolvedValue → the late call returns undefined → `undefined.then(...)`
// throws inside the timer callback = an unhandled error (vitest exits non-zero
// even with all tests "passing"). A plain arrow is immune to mock resets.
vi.mock('@/features/operators/api/account-existence', () => ({
  checkAccountExistsForTenant: () => Promise.resolve(true),
}));

/**
 * `features/operators` component behaviour (TASK-PC-FE-004):
 *   - list render + status filter + pagination
 *   - create-form password-policy mirror + roles multi-select; the create
 *     is reason+confirm-gated (no one-click) with ELEVATED copy
 *   - `*` platform tenant NOT offered to a non-platform operator
 *   - edit-roles empty-array (remove all) → strong confirm copy
 *   - change-status (suspend) reason+confirm-gated
 *   - self change-password: new == confirm + policy
 *   - reason-gated mutations: no reason ⇒ the proxy is NOT called
 *   - 403 PERMISSION_DENIED (non-SUPER_ADMIN) inline; tenant-scope-denied
 *     + email-conflict inline; 503 degrade
 *   - WCAG AA: the confirm dialog is axe-clean and keyboard-dismissable
 *
 * Client calls the same-origin `/api/operators/**` proxy via `fetch`
 * (mocked).
 */

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const PAGE: OperatorPage = {
  content: [
    {
      operatorId: 'op-1',
      email: 'alice@x.com',
      displayName: 'Alice',
      status: 'ACTIVE',
      roles: ['SUPPORT_LOCK'],
      createdAt: '2026-01-01',
    },
    {
      operatorId: 'op-2',
      email: 'bob@x.com',
      displayName: 'Bob',
      status: 'SUSPENDED',
      roles: ['SUPER_ADMIN', 'FUTURE_ROLE_V2'],
      createdAt: '2026-01-02',
    },
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

describe('OperatorsScreen — list, role tolerance, pagination', () => {
  it('renders the server-provided page with a row per operator', () => {
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );
    expect(screen.getByTestId('operators-table')).toBeInTheDocument();
    expect(screen.getByText('alice@x.com')).toBeInTheDocument();
    // Unknown/future role still renders as a generic chip (no crash).
    expect(
      screen.getByTestId('operator-role-chip-FUTURE_ROLE_V2'),
    ).toBeInTheDocument();
    // SUPER_ADMIN chip is visually distinct (elevated).
    expect(
      screen.getByTestId('operator-role-chip-SUPER_ADMIN'),
    ).toHaveAttribute('data-elevated', 'true');
    expect(screen.getByTestId('operators-pageinfo')).toHaveTextContent(
      '1 / 2 페이지',
    );
  });

  it('status filter triggers a re-query through the proxy', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...PAGE, content: [] }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.selectOptions(
      screen.getByTestId('operators-status-filter'),
      'SUSPENDED',
    );
    await user.click(screen.getByTestId('operators-filter-submit'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('status=SUSPENDED'),
        expect.anything(),
      ),
    );
  });

  it('paginates to the next page (re-query via the proxy)', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(jsonResponse({ ...PAGE, page: 1 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('operators-next'));
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        expect.stringContaining('page=1'),
        expect.anything(),
      ),
    );
  });
});

describe('OperatorsScreen — create form (policy mirror, tenant *, gate)', () => {
  it('does NOT offer the * platform tenant to a non-platform operator', () => {
    render(
      <OperatorsScreen
        initial={PAGE}
        tenantOptions={['wms', 'scm']}
        isPlatformOperator={false}
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.queryByTestId('create-operator-tenant-platform'),
    ).not.toBeInTheDocument();
  });

  it('offers the * platform tenant ONLY for a platform-scope operator', () => {
    render(
      <OperatorsScreen
        initial={PAGE}
        tenantOptions={['wms']}
        isPlatformOperator
      />,
      { wrapper: wrapper() },
    );
    expect(
      screen.getByTestId('create-operator-tenant-platform'),
    ).toBeInTheDocument();
  });

  it('blocks submit on a weak password (client policy mirror) — proxy NOT called', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.type(
      screen.getByTestId('create-operator-email'),
      'new@x.com',
    );
    await user.type(
      screen.getByTestId('create-operator-displayName'),
      'New Op',
    );
    await user.type(
      screen.getByTestId('create-operator-password'),
      'weak', // < 10 chars, no digit/special
    );
    await user.selectOptions(
      screen.getByTestId('create-operator-tenant'),
      'wms',
    );
    await user.click(screen.getByTestId('create-operator-submit'));

    expect(
      screen.getByTestId('create-operator-password-error'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('operator-confirm-dialog'),
    ).not.toBeInTheDocument();
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('a valid create is reason+confirm-gated (no one-click) with ELEVATED copy', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.type(
      screen.getByTestId('create-operator-email'),
      'new@x.com',
    );
    await user.type(
      screen.getByTestId('create-operator-displayName'),
      'New Op',
    );
    await user.type(
      screen.getByTestId('create-operator-password'),
      'Str0ng!pass1',
    );
    await user.click(screen.getByTestId('create-operator-role-SUPER_ADMIN'));
    await user.selectOptions(
      screen.getByTestId('create-operator-tenant'),
      'wms',
    );
    // TASK-MONO-334: submit is account-gated — wait for the OIDC-ok state before
    // clicking (the mocked probe resolves to exists).
    await screen.findByTestId('create-operator-account-ok');
    await user.click(screen.getByTestId('create-operator-submit'));

    // The confirm dialog opens — the producer call has NOT fired yet.
    const dialog = screen.getByTestId('operator-confirm-dialog');
    expect(fetchMock).not.toHaveBeenCalled();
    const submit = within(dialog).getByTestId('operator-confirm-submit');
    expect(submit).toBeDisabled(); // no reason yet

    fetchMock.mockResolvedValue(
      jsonResponse(
        {
          operatorId: 'op-9',
          email: 'new@x.com',
          displayName: 'New Op',
          status: 'ACTIVE',
          roles: ['SUPER_ADMIN'],
          createdAt: 'x',
          auditId: 'a',
          tenantId: 'wms',
        },
        201,
      ),
    );
    await user.type(
      within(dialog).getByTestId('operator-confirm-reason'),
      'onboarding a platform admin',
    );
    expect(submit).toBeEnabled();
    await user.click(submit);

    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/operators',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.reason).toBe('onboarding a platform admin');
    expect(body.idempotencyKey).toBeTruthy();
    expect(body.password).toBe('Str0ng!pass1');
  });
});

describe('OperatorsScreen — edit-roles strong confirm (remove all)', () => {
  it('an empty role selection shows the remove-all warning and is reason-gated', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('action-edit-roles-op-1'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    // op-1 seeded with SUPPORT_LOCK → uncheck it = empty (remove all).
    await user.click(within(dialog).getByTestId('edit-roles-SUPPORT_LOCK'));
    expect(
      within(dialog).getByTestId('edit-roles-remove-all-warning'),
    ).toBeInTheDocument();

    const submit = within(dialog).getByTestId('operator-confirm-submit');
    expect(submit).toBeDisabled(); // reason still required
    await user.click(submit);
    expect(fetchMock).not.toHaveBeenCalled();

    fetchMock.mockResolvedValue(
      jsonResponse({ operatorId: 'op-1', roles: [], auditId: 'a' }),
    );
    await user.type(
      within(dialog).getByTestId('operator-confirm-reason'),
      'offboarding operator',
    );
    await user.click(submit);
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/operators/op-1/roles',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.roles).toEqual([]);
    expect(body.reason).toBe('offboarding operator');
    expect(body.idempotencyKey).toBeUndefined();
  });
});

describe('OperatorsScreen — change-status suspend (reason+confirm)', () => {
  it('suspends an active operator only after a reason is entered', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('action-status-op-1'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    const submit = within(dialog).getByTestId('operator-confirm-submit');
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(fetchMock).not.toHaveBeenCalled();

    fetchMock.mockResolvedValue(
      jsonResponse({
        operatorId: 'op-1',
        previousStatus: 'ACTIVE',
        currentStatus: 'SUSPENDED',
        auditId: 'a',
      }),
    );
    await user.type(
      within(dialog).getByTestId('operator-confirm-reason'),
      'security incident',
    );
    await user.click(submit);
    await waitFor(() =>
      expect(fetchMock).toHaveBeenCalledWith(
        '/api/operators/op-1/status',
        expect.objectContaining({ method: 'POST' }),
      ),
    );
    const body = JSON.parse(
      (fetchMock.mock.calls[0][1] as RequestInit).body as string,
    );
    expect(body.status).toBe('SUSPENDED');
    expect(body.idempotencyKey).toBeUndefined();
  });
});

// TASK-PC-FE-045: self change-password / my-profile moved to /account
// (AccountSelfService). See AccountSelfService.test.tsx. OperatorsScreen now
// asserts these self forms are ABSENT (it is 남 관리 only).
describe('OperatorsScreen — self-service forms moved to /account (TASK-PC-FE-045)', () => {
  it('no longer renders the self change-password or my-profile forms', () => {
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );
    expect(screen.queryByTestId('change-password-form')).not.toBeInTheDocument();
    expect(screen.queryByTestId('my-profile-form')).not.toBeInTheDocument();
    // 남 관리 surface stays.
    expect(screen.getByTestId('create-operator-form')).toBeInTheDocument();
    expect(screen.getByTestId('operators-table')).toBeInTheDocument();
  });
});

describe('OperatorsScreen — permission / degrade UX', () => {
  it('a 403 PERMISSION_DENIED on re-query → inline "not permitted" (no crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'PERMISSION_DENIED' }, 403)),
    );
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('operators-next'));
    await waitFor(() =>
      expect(
        screen.getByTestId('operators-permission-denied'),
      ).toBeInTheDocument(),
    );
    // The section heading is still present — not a blank crash.
    expect(
      screen.getByRole('heading', { name: '운영자 관리' }),
    ).toBeInTheDocument();
  });

  it('an email-conflict surfaces inline in the create dialog (no crash)', async () => {
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.type(
      screen.getByTestId('create-operator-email'),
      'dup@x.com',
    );
    await user.type(
      screen.getByTestId('create-operator-displayName'),
      'Dup',
    );
    await user.type(
      screen.getByTestId('create-operator-password'),
      'Str0ng!pass1',
    );
    await user.selectOptions(
      screen.getByTestId('create-operator-tenant'),
      'wms',
    );
    // TASK-MONO-334: submit is account-gated — wait for the OIDC-ok state first.
    await screen.findByTestId('create-operator-account-ok');
    await user.click(screen.getByTestId('create-operator-submit'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    fetchMock.mockResolvedValue(
      jsonResponse({ code: 'OPERATOR_EMAIL_CONFLICT' }, 409),
    );
    await user.type(
      within(dialog).getByTestId('operator-confirm-reason'),
      'onboarding',
    );
    await user.click(
      within(dialog).getByTestId('operator-confirm-submit'),
    );
    await waitFor(() =>
      expect(
        within(screen.getByTestId('operator-confirm-dialog')).getByTestId(
          'operator-confirm-error',
        ),
      ).toBeInTheDocument(),
    );
  });

  it('a 503 on re-query degrades the operators section (no blank crash)', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(jsonResponse({ code: 'DOWNSTREAM_ERROR' }, 503)),
    );
    const user = userEvent.setup();
    render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );

    await user.click(screen.getByTestId('operators-next'));
    await waitFor(() =>
      expect(
        screen.getAllByTestId('operators-degraded').length,
      ).toBeGreaterThan(0),
    );
    expect(
      screen.getByRole('heading', { name: '운영자 관리' }),
    ).toBeInTheDocument();
  });
});

describe('OperatorsScreen — accessibility (WCAG AA)', () => {
  it('the confirm dialog is axe-clean and keyboard-dismissable', async () => {
    const user = userEvent.setup();
    const { container } = render(
      <OperatorsScreen initial={PAGE} tenantOptions={['wms']} />,
      { wrapper: wrapper() },
    );
    await user.click(screen.getByTestId('action-status-op-1'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    expect(dialog).toHaveAttribute('role', 'dialog');
    expect(dialog).toHaveAttribute('aria-modal', 'true');

    const violations = await runAxe(container);
    expect(violations).toEqual([]);

    await user.keyboard('{Escape}');
    expect(
      screen.queryByTestId('operator-confirm-dialog'),
    ).not.toBeInTheDocument();
  });
});
