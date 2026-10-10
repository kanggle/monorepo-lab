import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { OperatorsScreen } from '@/features/operators';
import type { OperatorPage } from '@/features/operators';
import type { OperatorInvitation } from '@/features/operators/api/invitation-types';

/**
 * TASK-MONO-772 S5 — the operators screen's «초대» surface
 * (console-integration-contract § 2.4.3 rows 11–14 + the «등록 → 초대» bullet).
 *
 *   - invite form validation (no producer call on an invalid draft)
 *   - invite → reason + confirm (contract copy) → POST with reason +
 *     idempotency key, NO password, tenant = the ACTIVE tenant
 *   - SENT → a plain notice; FAILED_TRANSIENT → a WARNING (role=status, never
 *     role=alert) with «다시 보내기» — the invitation exists
 *   - pending list: rows, «만료됨», delivery labels, inviter name
 *   - cancel / resend: reason-gated, reason ONLY (no idempotency key), list
 *     refetched after; a stale answer (409 NOT_PENDING / 404 NOT_FOUND)
 *     closes the dialog, says so, and refetches
 *   - error mapping: ALREADY_PENDING (+ jump to that row's resend),
 *     EMAIL_CONFLICT, ROLE_GRANT_FORBIDDEN, list 503 / 403 section-only
 *   - tenant switch: the key carries the tenant → a new tenant reads its own
 *     list; invalidating the `['operators']` root (what `useTenantSwitch`
 *     does) refetches it
 *   - 🔴 no token / link is ever rendered, even if a producer leaked one
 */

const PAGE: OperatorPage = {
  content: [
    {
      operatorId: 'op-admin',
      email: 'admin@acme.example',
      displayName: '관리자 김',
      status: 'ACTIVE',
      roles: ['TENANT_ADMIN'],
      createdAt: '2026-01-01',
    },
  ],
  totalElements: 1,
  page: 0,
  size: 20,
  totalPages: 1,
};

function inv(over: Partial<OperatorInvitation> = {}): OperatorInvitation {
  return {
    invitationId: 'inv-1',
    tenantId: 'acme-corp',
    email: 'new.person@example.com',
    displayName: '새 직원',
    roles: ['SUPPORT_LOCK'],
    status: 'PENDING',
    expired: false,
    expiresAt: '2026-10-18T00:00:00Z',
    createdAt: '2026-10-11T00:00:00Z',
    invitedBy: 'op-admin',
    delivery: { status: 'SENT', attemptedAt: '2026-10-11T00:00:01Z' },
    acceptedAt: null,
    acceptedOperatorId: null,
    cancelledAt: null,
    ...over,
  };
}

function page(rows: OperatorInvitation[]) {
  return {
    content: rows,
    totalElements: rows.length,
    page: 0,
    size: 20,
    totalPages: rows.length ? 1 : 0,
  };
}

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

type Handler = (url: string, init: RequestInit) => Response;

/**
 * Route-aware fetch stub. `list` answers every invitations GET (may change
 * between calls); `post` answers every POST. Records each call.
 */
function stubFetch(opts: { list?: () => Response; post?: Handler }) {
  const fetchMock = vi.fn(async (url: string, init: RequestInit = {}) => {
    const u = String(url);
    const method = (init.method ?? 'GET').toUpperCase();
    if (method === 'GET' && u.startsWith('/api/operator-invitations')) {
      return opts.list ? opts.list() : json(page([]));
    }
    if (opts.post) return opts.post(u, init);
    return json({ code: 'UNEXPECTED' }, 500);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function listCalls(fetchMock: ReturnType<typeof stubFetch>) {
  return fetchMock.mock.calls.filter(
    ([u, i]) =>
      String(u).startsWith('/api/operator-invitations') &&
      ((i as RequestInit | undefined)?.method ?? 'GET') === 'GET',
  );
}

function postCalls(fetchMock: ReturnType<typeof stubFetch>) {
  return fetchMock.mock.calls.filter(
    ([, i]) => (i as RequestInit | undefined)?.method === 'POST',
  );
}

function bodyOf(call: unknown[]) {
  return JSON.parse((call[1] as RequestInit).body as string);
}

function renderScreen(
  props: Partial<Parameters<typeof OperatorsScreen>[0]> = {},
  qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  }),
) {
  const Wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
  const utils = render(
    <OperatorsScreen
      initial={PAGE}
      activeTenant="acme-corp"
      grantableRoles={['TENANT_ADMIN', 'SUPPORT_LOCK']}
      selfOperatorId="op-self"
      {...props}
    />,
    { wrapper: Wrapper },
  );
  return { ...utils, qc, Wrapper };
}

async function fillInvite(user: ReturnType<typeof userEvent.setup>) {
  await user.type(
    screen.getByTestId('invite-operator-email'),
    'New.Person@Example.com',
  );
  await user.type(screen.getByTestId('invite-operator-displayName'), '새 직원');
  await user.click(screen.getByTestId('invite-operator-role-SUPPORT_LOCK'));
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('«초대» replaces «등록» for company operators', () => {
  it('a non-platform operator sees the invite form and NO direct create form', async () => {
    stubFetch({});
    renderScreen({ isPlatformOperator: false });
    expect(screen.getByTestId('invite-operator-form')).toBeInTheDocument();
    expect(screen.getByTestId('invite-operator-tenant')).toHaveTextContent(
      'acme-corp',
    );
    expect(screen.queryByTestId('create-operator-form')).not.toBeInTheDocument();
    // No tenant picker and no password field on an invitation.
    const form = screen.getByTestId('invite-operator-form');
    expect(within(form).queryByRole('combobox')).not.toBeInTheDocument();
    expect(form.querySelector('input[type="password"]')).toBeNull();
    await screen.findByTestId('operator-invitations-empty');
  });

  it('the active tenant `*` is never an invitation target (no invite form)', async () => {
    stubFetch({});
    renderScreen({ activeTenant: '*', isPlatformOperator: true });
    expect(screen.queryByTestId('invite-operator-form')).not.toBeInTheDocument();
    // The platform create form is the path for `*`.
    expect(screen.getByTestId('create-operator-form')).toBeInTheDocument();
  });
});

describe('invite form validation', () => {
  it('an invalid draft shows field errors and never opens the dialog or calls the producer', async () => {
    const fetchMock = stubFetch({});
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('invite-operator-email'), 'not-an-email');
    await user.click(screen.getByTestId('invite-operator-submit'));

    expect(screen.getByTestId('invite-operator-email-error')).toBeInTheDocument();
    expect(
      screen.getByTestId('invite-operator-displayName-error'),
    ).toBeInTheDocument();
    expect(screen.getByTestId('invite-operator-roles-error')).toBeInTheDocument();
    expect(screen.queryByTestId('operator-confirm-dialog')).not.toBeInTheDocument();
    expect(postCalls(fetchMock)).toHaveLength(0);
  });

  it('roles are pre-filtered to the caller\'s grantable set', () => {
    stubFetch({});
    renderScreen({ grantableRoles: ['SUPPORT_LOCK'] });
    expect(
      screen.getByTestId('invite-operator-role-SUPPORT_LOCK'),
    ).toBeInTheDocument();
    expect(
      screen.queryByTestId('invite-operator-role-SUPER_ADMIN'),
    ).not.toBeInTheDocument();
  });
});

describe('invite → confirm → POST', () => {
  it('SENT: reason-gated, carries reason + idempotency key + active tenant, NO password; then a plain notice and a refetched list', async () => {
    let rows: OperatorInvitation[] = [];
    const fetchMock = stubFetch({
      list: () => json(page(rows)),
      post: () => {
        rows = [inv()];
        return json({ ...inv(), auditId: 'audit-1' }, 201);
      },
    });
    const user = userEvent.setup();
    renderScreen();
    await screen.findByTestId('operator-invitations-empty');

    await fillInvite(user);
    await user.click(screen.getByTestId('invite-operator-submit'));

    const dialog = screen.getByTestId('operator-confirm-dialog');
    // Contract copy: what happens next.
    expect(dialog).toHaveTextContent('초대 메일이');
    expect(dialog).toHaveTextContent('인증한');
    expect(dialog).toHaveTextContent('7일 안에');
    const submit = within(dialog).getByTestId('operator-confirm-submit');
    expect(submit).toBeDisabled();
    expect(postCalls(fetchMock)).toHaveLength(0);

    await user.type(
      within(dialog).getByTestId('operator-confirm-reason'),
      '신규 입사자 운영 권한',
    );
    await user.click(submit);

    await waitFor(() => expect(postCalls(fetchMock)).toHaveLength(1));
    const [url] = postCalls(fetchMock)[0];
    expect(url).toBe('/api/operator-invitations');
    const body = bodyOf(postCalls(fetchMock)[0]);
    expect(body).toEqual({
      email: 'new.person@example.com',
      displayName: '새 직원',
      roles: ['SUPPORT_LOCK'],
      tenantId: 'acme-corp',
      reason: '신규 입사자 운영 권한',
      idempotencyKey: expect.any(String),
    });
    expect(body).not.toHaveProperty('password');

    const notice = await screen.findByTestId('invitation-delivery-notice');
    expect(notice).toHaveAttribute('data-delivery-status', 'SENT');
    expect(notice).toHaveTextContent('초대 메일을 보냈습니다');
    expect(
      screen.queryByTestId('invitation-delivery-notice-resend'),
    ).not.toBeInTheDocument();
    // List refetched after the mutation and now shows the row.
    await screen.findByTestId('invitation-row-inv-1');
    expect(listCalls(fetchMock).length).toBeGreaterThanOrEqual(2);
    // The form cleared for the next invite.
    expect(screen.getByTestId('invite-operator-email')).toHaveValue('');
  });

  it('FAILED_TRANSIENT: a visible WARNING (not an error) with «다시 보내기», which resends with reason ONLY', async () => {
    const failed = inv({
      delivery: { status: 'FAILED_TRANSIENT', attemptedAt: '2026-10-11T00:00:01Z' },
    });
    let rows: OperatorInvitation[] = [];
    const fetchMock = stubFetch({
      list: () => json(page(rows)),
      post: (u) => {
        if (u === '/api/operator-invitations') {
          rows = [failed];
          return json({ ...failed, auditId: 'a' }, 201);
        }
        if (u === '/api/operator-invitations/inv-1/resend') {
          const resent = inv({
            delivery: { status: 'SENT', attemptedAt: '2026-10-11T00:05:00Z' },
          });
          rows = [resent];
          return json(resent);
        }
        return json({ code: 'UNEXPECTED' }, 500);
      },
    });
    const user = userEvent.setup();
    renderScreen();

    await fillInvite(user);
    await user.click(screen.getByTestId('invite-operator-submit'));
    await user.type(screen.getByTestId('operator-confirm-reason'), '입사');
    await user.click(screen.getByTestId('operator-confirm-submit'));

    const notice = await screen.findByTestId('invitation-delivery-notice');
    expect(notice).toHaveAttribute('data-delivery-status', 'FAILED_TRANSIENT');
    expect(notice).toHaveAttribute('role', 'status');
    expect(notice).toHaveTextContent('초대를 만들었습니다');
    expect(notice).toHaveTextContent('메일을 보내지 못했습니다');
    // Not an error: no alert anywhere, the dialog closed, the row is listed.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByTestId('operator-confirm-dialog')).not.toBeInTheDocument();
    const deliveryCell = await screen.findByTestId('invitation-delivery-inv-1');
    expect(deliveryCell).toHaveTextContent('보내지 못함 — 다시 보내기');

    await user.click(screen.getByTestId('invitation-delivery-notice-resend'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    expect(dialog).toHaveTextContent('전에 보낸 링크는 더 이상 쓸 수 없습니다');
    await user.type(within(dialog).getByTestId('operator-confirm-reason'), '재발송');
    await user.click(within(dialog).getByTestId('operator-confirm-submit'));

    await waitFor(() => expect(postCalls(fetchMock)).toHaveLength(2));
    const resendCall = postCalls(fetchMock)[1];
    expect(resendCall[0]).toBe('/api/operator-invitations/inv-1/resend');
    expect(bodyOf(resendCall)).toEqual({ reason: '재발송' }); // NO idempotency key
    await waitFor(() =>
      expect(screen.getByTestId('invitation-delivery-notice')).toHaveAttribute(
        'data-delivery-status',
        'SENT',
      ),
    );
  });

  it('a retry of the SAME confirmed invite reuses its idempotency key', async () => {
    let n = 0;
    const fetchMock = stubFetch({
      post: () => {
        n += 1;
        return n === 1
          ? json({ code: 'DOWNSTREAM_ERROR' }, 503)
          : json({ ...inv(), auditId: 'a' }, 201);
      },
    });
    const user = userEvent.setup();
    renderScreen();
    await fillInvite(user);
    await user.click(screen.getByTestId('invite-operator-submit'));
    await user.type(screen.getByTestId('operator-confirm-reason'), '입사');
    await user.click(screen.getByTestId('operator-confirm-submit'));

    // 503 → inline in the dialog (only this action failed), dialog stays open.
    await screen.findByTestId('operator-confirm-error');
    expect(screen.getByTestId('operator-confirm-error')).toHaveTextContent(
      '초대 서비스가 일시적으로 응답하지 않습니다',
    );
    await user.click(screen.getByTestId('operator-confirm-submit'));
    await waitFor(() => expect(postCalls(fetchMock)).toHaveLength(2));
    expect(bodyOf(postCalls(fetchMock)[0]).idempotencyKey).toBe(
      bodyOf(postCalls(fetchMock)[1]).idempotencyKey,
    );
  });
});

describe('pending invitations list', () => {
  it('renders rows with roles, inviter name, expiry and delivery status; an expired row reads «만료됨»', async () => {
    stubFetch({
      list: () =>
        json(
          page([
            inv(),
            inv({
              invitationId: 'inv-2',
              email: 'expired@example.com',
              expired: true,
              invitedBy: 'op-self',
              delivery: {
                status: 'FAILED_PERMANENT',
                attemptedAt: '2026-10-01T00:00:00Z',
              },
            }),
            inv({ invitationId: 'inv-3', email: 'pending@example.com', delivery: null }),
          ]),
        ),
    });
    renderScreen();

    const row1 = await screen.findByTestId('invitation-row-inv-1');
    expect(row1).toHaveTextContent('new.person@example.com');
    expect(row1).toHaveTextContent('SUPPORT_LOCK');
    expect(row1).toHaveTextContent('관리자 김'); // inviter display name, not email
    expect(screen.getByTestId('invitation-delivery-inv-1')).toHaveTextContent('보냄');

    expect(screen.getByTestId('invitation-expired-inv-2')).toHaveTextContent('만료됨');
    expect(screen.getByTestId('invitation-row-inv-2')).toHaveTextContent('나');
    expect(screen.getByTestId('invitation-delivery-inv-2')).toHaveTextContent(
      '이 주소로는 보낼 수 없음',
    );
    // The expired row still offers resend / cancel.
    expect(screen.getByTestId('invitation-resend-inv-2')).toBeEnabled();
    expect(screen.getByTestId('invitation-cancel-inv-2')).toBeEnabled();

    expect(screen.getByTestId('invitation-delivery-inv-3')).toHaveTextContent('발송 대기');
  });

  it('reads the ACTIVE tenant, PENDING by default', async () => {
    const fetchMock = stubFetch({});
    renderScreen();
    await waitFor(() => expect(listCalls(fetchMock)).toHaveLength(1));
    const url = new URL(String(listCalls(fetchMock)[0][0]), 'http://x');
    expect(url.searchParams.get('tenantId')).toBe('acme-corp');
    expect(url.searchParams.get('status')).toBe('PENDING');
  });

  it('a 503 on the list degrades ONLY the invitations section (operators table intact)', async () => {
    stubFetch({ list: () => json({ code: 'DOWNSTREAM_ERROR' }, 503) });
    renderScreen();
    await screen.findByTestId('operator-invitations-degraded');
    expect(screen.getByTestId('operators-table')).toBeInTheDocument();
    expect(screen.getByTestId('invite-operator-form')).toBeInTheDocument();
  });

  it('a 403 on the list is an inline «not permitted» note (no crash)', async () => {
    stubFetch({ list: () => json({ code: 'TENANT_SCOPE_DENIED' }, 403) });
    renderScreen();
    await screen.findByTestId('operator-invitations-permission-denied');
    expect(screen.getByTestId('operators-table')).toBeInTheDocument();
  });
});

describe('cancel / resend (reason-gated, reason ONLY)', () => {
  it('cancel: no call without a reason; then POST {reason} and the list is refetched', async () => {
    let rows = [inv()];
    const fetchMock = stubFetch({
      list: () => json(page(rows)),
      post: (u) => {
        if (u === '/api/operator-invitations/inv-1/cancel') {
          rows = [];
          return json(inv({ status: 'CANCELLED', cancelledAt: '2026-10-11T01:00:00Z' }));
        }
        return json({ code: 'UNEXPECTED' }, 500);
      },
    });
    const user = userEvent.setup();
    renderScreen();

    await user.click(await screen.findByTestId('invitation-cancel-inv-1'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    expect(dialog).toHaveTextContent('new.person@example.com');
    const submit = within(dialog).getByTestId('operator-confirm-submit');
    expect(submit).toBeDisabled();
    await user.click(submit);
    expect(postCalls(fetchMock)).toHaveLength(0);

    const before = listCalls(fetchMock).length;
    await user.type(within(dialog).getByTestId('operator-confirm-reason'), '오입력');
    await user.click(submit);

    await waitFor(() => expect(postCalls(fetchMock)).toHaveLength(1));
    expect(postCalls(fetchMock)[0][0]).toBe('/api/operator-invitations/inv-1/cancel');
    expect(bodyOf(postCalls(fetchMock)[0])).toEqual({ reason: '오입력' });
    await screen.findByTestId('operator-invitations-empty');
    expect(listCalls(fetchMock).length).toBeGreaterThan(before);
  });

  it('stale answer (409 OPERATOR_INVITATION_NOT_PENDING) → dialog closes, says so, list refetched', async () => {
    let rows = [inv()];
    const fetchMock = stubFetch({
      list: () => json(page(rows)),
      post: () => {
        rows = []; // accepted meanwhile
        return json({ code: 'OPERATOR_INVITATION_NOT_PENDING' }, 409);
      },
    });
    const user = userEvent.setup();
    renderScreen();

    await user.click(await screen.findByTestId('invitation-resend-inv-1'));
    await user.type(screen.getByTestId('operator-confirm-reason'), '재발송');
    await user.click(screen.getByTestId('operator-confirm-submit'));

    const notice = await screen.findByTestId('operator-invitations-notice');
    expect(notice).toHaveTextContent('수락되었거나 취소되었습니다');
    expect(screen.queryByTestId('operator-confirm-dialog')).not.toBeInTheDocument();
    await screen.findByTestId('operator-invitations-empty');
    expect(listCalls(fetchMock).length).toBeGreaterThanOrEqual(2);
  });

  it('stale answer (404 OPERATOR_INVITATION_NOT_FOUND) → the same refresh path', async () => {
    let rows = [inv()];
    stubFetch({
      list: () => json(page(rows)),
      post: () => {
        rows = [];
        return json({ code: 'OPERATOR_INVITATION_NOT_FOUND' }, 404);
      },
    });
    const user = userEvent.setup();
    renderScreen();
    await user.click(await screen.findByTestId('invitation-cancel-inv-1'));
    await user.type(screen.getByTestId('operator-confirm-reason'), '정리');
    await user.click(screen.getByTestId('operator-confirm-submit'));
    expect(
      await screen.findByTestId('operator-invitations-notice'),
    ).toHaveTextContent('찾을 수 없습니다');
    await screen.findByTestId('operator-invitations-empty');
  });

  it('ROLE_GRANT_FORBIDDEN on resend stays in the dialog with the no-escalation copy', async () => {
    stubFetch({
      list: () => json(page([inv()])),
      post: () => json({ code: 'ROLE_GRANT_FORBIDDEN' }, 403),
    });
    const user = userEvent.setup();
    renderScreen();
    await user.click(await screen.findByTestId('invitation-resend-inv-1'));
    await user.type(screen.getByTestId('operator-confirm-reason'), '재발송');
    await user.click(screen.getByTestId('operator-confirm-submit'));
    const err = await screen.findByTestId('operator-confirm-error');
    expect(err).toHaveTextContent('부여할 수 없는 역할');
    expect(screen.getByTestId('operator-confirm-dialog')).toBeInTheDocument();
  });
});

describe('invite error mapping', () => {
  async function inviteAnd(post: Handler, list = () => json(page([]))) {
    stubFetch({ list, post });
    const user = userEvent.setup();
    renderScreen();
    await fillInvite(user);
    await user.click(screen.getByTestId('invite-operator-submit'));
    await user.type(screen.getByTestId('operator-confirm-reason'), '입사');
    await user.click(screen.getByTestId('operator-confirm-submit'));
    return user;
  }

  it('409 OPERATOR_INVITATION_ALREADY_PENDING → form message + jump to THAT row\'s resend', async () => {
    const twin = inv({ invitationId: 'inv-twin' });
    const user = await inviteAnd(
      () => json({ code: 'OPERATOR_INVITATION_ALREADY_PENDING' }, 409),
      () => json(page([twin])),
    );
    const err = await screen.findByTestId('invite-operator-server-error');
    expect(err).toHaveTextContent('이미 대기 중인 초대가 있습니다');
    expect(screen.queryByTestId('operator-confirm-dialog')).not.toBeInTheDocument();

    await user.click(screen.getByTestId('invite-operator-jump-to-pending'));
    const dialog = screen.getByTestId('operator-confirm-dialog');
    expect(dialog).toHaveTextContent('운영자 초대 다시 보내기');
    expect(dialog).toHaveTextContent('new.person@example.com');
    expect(screen.getByTestId('invitation-row-inv-twin')).toHaveAttribute(
      'data-highlighted',
      'true',
    );
  });

  it('409 OPERATOR_EMAIL_CONFLICT → «이미 이 테넌트의 운영자입니다»', async () => {
    await inviteAnd(() => json({ code: 'OPERATOR_EMAIL_CONFLICT' }, 409));
    expect(
      await screen.findByTestId('invite-operator-server-error'),
    ).toHaveTextContent('이미 이 테넌트의 운영자입니다');
    expect(
      screen.queryByTestId('invite-operator-jump-to-pending'),
    ).not.toBeInTheDocument();
  });

  it('403 TENANT_SCOPE_DENIED → invitation-specific copy in the dialog', async () => {
    await inviteAnd(() => json({ code: 'TENANT_SCOPE_DENIED' }, 403));
    expect(await screen.findByTestId('operator-confirm-error')).toHaveTextContent(
      '이 테넌트에 운영자를 초대할 권한이 없습니다',
    );
  });

  it('400 ROLE_NOT_FOUND → the shared copy (as today)', async () => {
    await inviteAnd(() => json({ code: 'ROLE_NOT_FOUND' }, 400));
    expect(await screen.findByTestId('operator-confirm-error')).toHaveTextContent(
      '존재하지 않는 역할',
    );
  });
});

describe('tenant switch — the list is tenant-scoped (TASK-MONO-780 lesson)', () => {
  it('a new active tenant reads ITS list (key carries the tenant), never the previous one\'s rows', async () => {
    const fetchMock = stubFetch({
      list: () => {
        const last = listCalls(fetchMock).at(-1);
        const t = new URL(String(last?.[0]), 'http://x').searchParams.get('tenantId');
        return json(
          page(
            t === 'acme-corp'
              ? [inv()]
              : [inv({ invitationId: 'inv-globex', tenantId: 'globex', email: 'g@example.com' })],
          ),
        );
      },
    });
    const { rerender } = renderScreen();
    await screen.findByTestId('invitation-row-inv-1');

    // In-place rerender (RTL re-applies the same `wrapper`) — the SAME mounted
    // query observer sees a new tenant. Only a tenant-bearing key makes it
    // read again; a key without the tenant would keep showing acme's rows
    // (bite-checked: dropping the tenant from `invitationsKey` turns this red).
    rerender(
      <OperatorsScreen
        initial={PAGE}
        activeTenant="globex"
        grantableRoles={['SUPPORT_LOCK']}
      />,
    );
    await screen.findByTestId('invitation-row-inv-globex');
    expect(screen.queryByTestId('invitation-row-inv-1')).not.toBeInTheDocument();
    const tenants = listCalls(fetchMock).map((c) =>
      new URL(String(c[0]), 'http://x').searchParams.get('tenantId'),
    );
    expect(tenants).toContain('globex');
  });

  it('invalidating the `[\'operators\']` root (what useTenantSwitch does) refetches the mounted list', async () => {
    const fetchMock = stubFetch({});
    const { qc } = renderScreen();
    await waitFor(() => expect(listCalls(fetchMock)).toHaveLength(1));
    await qc.invalidateQueries({ queryKey: ['operators'] });
    await waitFor(() => expect(listCalls(fetchMock)).toHaveLength(2));
  });
});

describe('🔴 no token / link is ever rendered', () => {
  it('even if a producer response leaked `token` / `link` fields, the screen shows neither', async () => {
    const SECRET = 'tok_SuperSecret123';
    const leaked = {
      ...inv(),
      token: SECRET,
      link: `https://auth.example/operator-invitations/accept?token=${SECRET}`,
      auditId: 'a',
    };
    stubFetch({
      list: () => json(page([leaked as unknown as OperatorInvitation])),
      post: () => json(leaked, 201),
    });
    const user = userEvent.setup();
    const { container } = renderScreen();
    await screen.findByTestId('invitation-row-inv-1');

    await fillInvite(user);
    await user.click(screen.getByTestId('invite-operator-submit'));
    await user.type(screen.getByTestId('operator-confirm-reason'), '입사');
    await user.click(screen.getByTestId('operator-confirm-submit'));
    await screen.findByTestId('invitation-delivery-notice');

    expect(container.innerHTML).not.toContain(SECRET);
    expect(container.innerHTML).not.toContain('operator-invitations/accept');
    expect(screen.queryByText(/링크 복사/)).not.toBeInTheDocument();
  });
});
