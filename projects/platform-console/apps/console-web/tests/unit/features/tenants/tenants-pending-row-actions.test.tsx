import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantsScreen } from '@/features/tenants';
import { ApiError, messageForCode } from '@/shared/api/errors';

/**
 * TASK-MONO-773 S6 — owner decision 2026-10-16 UTC: the `/tenants` «관리자 대기» row actions
 * (console-integration-contract § 2.6.4 «`/tenants` (SUPER_ADMIN list)»), exactly as the contract states:
 *
 *   - **다시 초대** — `INVITED` / `EXPIRED` to the SAME address → that invitation's `:resend`
 *     (`POST /api/operator-invitations/{id}/resend`); a NEW address or `NONE` →
 *     `POST /api/tenants/{tenantId}/first-admin-invitation` (→ IAM `POST /api/admin/tenants/{id}/first-admin-invitation`).
 *   - **정리** — `PATCH status=SUSPENDED`, behind the in-page confirm dialog «초대를 취소하고 테넌트를 정지합니다 —
 *     되돌릴 수 없습니다» (never `window.confirm`).
 *   - **활성화 마치기** — a `PENDING` row whose `firstAdmin.state = ACCEPTED` offers this INSTEAD
 *     (`PATCH status=ACTIVE` — the IAM recovery path).
 *
 * Every action needs an operator reason (the general `/api/admin/*` rule) — the dialog captures it.
 */

const get = vi.fn();
const post = vi.fn();
const patch = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    post: (...a: unknown[]) => post(...a),
    patch: (...a: unknown[]) => patch(...a),
  },
}));

vi.mock('next/link', () => ({
  default: ({
    href,
    children,
    ...rest
  }: { href: string; children: React.ReactNode } & Record<string, unknown>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

const BASE = {
  displayName: 'ACME',
  tenantType: 'B2B_ENTERPRISE',
  createdAt: '2026-10-15T00:00:00Z',
  updatedAt: '2026-10-15T00:00:00Z',
};

function pendingRow(tenantId: string, state: string | null) {
  return {
    ...BASE,
    tenantId,
    status: 'PENDING',
    firstAdmin:
      state === null
        ? null
        : {
            state,
            email: state === 'NONE' ? null : `owner@${tenantId}.example`,
            invitationId: state === 'NONE' ? null : `inv-${tenantId}`,
            expiresAt: state === 'NONE' ? null : '2026-10-22T00:00:00Z',
          },
  };
}

const ROWS = [
  pendingRow('invited-co', 'INVITED'),
  pendingRow('expired-co', 'EXPIRED'),
  pendingRow('none-co', 'NONE'),
  pendingRow('nullsum-co', null),
  pendingRow('accepted-co', 'ACCEPTED'),
  { ...BASE, tenantId: 'active-co', status: 'ACTIVE', firstAdmin: null },
  { ...BASE, tenantId: 'suspended-co', status: 'SUSPENDED', firstAdmin: null },
];

function renderList() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TenantsScreen
        initial={{ items: ROWS, page: 0, size: 20, totalElements: ROWS.length, totalPages: 1 }}
      />
    </QueryClientProvider>,
  );
}

const TENANT_ANSWER = (tenantId: string, status: string) => ({
  ...BASE,
  tenantId,
  status,
  firstAdmin: null,
});

let confirmSpy: { mockRestore: () => void; mock: { calls: unknown[][] } };

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  patch.mockReset();
  confirmSpy = vi.spyOn(window, 'confirm').mockImplementation(() => true);
});
afterEach(() => confirmSpy.mockRestore());

/** Present-or-assertion lookup (a missing control fails as an assertion, not a query throw). */
function control(id: string): HTMLElement {
  const el = screen.queryByTestId(id);
  expect(el, `control ${id} is rendered`).not.toBeNull();
  return el as HTMLElement;
}

describe('which rows offer which actions', () => {
  it.each(['invited-co', 'expired-co', 'none-co', 'nullsum-co'])(
    '🔴 %s (PENDING, not accepted) → 다시 초대 + 정리, no 활성화 마치기',
    (id) => {
      renderList();
      expect(screen.queryByTestId(`tenants-row-reinvite-${id}`)).not.toBeNull();
      expect(screen.queryByTestId(`tenants-row-cleanup-${id}`)).not.toBeNull();
      expect(screen.queryByTestId(`tenants-row-finish-activation-${id}`)).toBeNull();
    },
  );

  it('🔴 PENDING + ACCEPTED → 활성화 마치기 INSTEAD (no 다시 초대 / 정리)', () => {
    renderList();
    expect(screen.queryByTestId('tenants-row-finish-activation-accepted-co')).not.toBeNull();
    expect(screen.queryByTestId('tenants-row-reinvite-accepted-co')).toBeNull();
    expect(screen.queryByTestId('tenants-row-cleanup-accepted-co')).toBeNull();
  });

  it.each(['active-co', 'suspended-co'])('🔵 control — %s offers none of the three', (id) => {
    renderList();
    const row = screen.getByTestId(`tenants-row-${id}`);
    expect(within(row).queryByRole('button')).toBeNull();
  });
});

describe('다시 초대', () => {
  it('🔴 INVITED · same address → that invitation\'s :resend (reason only) — never a new invitation', async () => {
    post.mockResolvedValue({ invitationId: 'inv-invited-co', status: 'PENDING' });
    get.mockResolvedValue({ items: ROWS, page: 0, size: 20, totalElements: ROWS.length, totalPages: 1 });
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-reinvite-invited-co'));
    expect((control('tenant-reinvite-email') as HTMLInputElement).value).toBe('owner@invited-co.example');
    await user.type(control('tenant-reinvite-reason'), '메일 재발송');
    await user.click(control('tenant-reinvite-submit'));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe('/api/operator-invitations/inv-invited-co/resend');
    expect(post.mock.calls[0][1]).toEqual({ reason: '메일 재발송' });
  });

  it('🔴 EXPIRED · a NEW address → POST /api/tenants/{id}/first-admin-invitation with email · name · reason · key', async () => {
    post.mockResolvedValue({ invitationId: 'inv-new', firstAdmin: true });
    get.mockResolvedValue({ items: ROWS, page: 0, size: 20, totalElements: ROWS.length, totalPages: 1 });
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-reinvite-expired-co'));
    const email = control('tenant-reinvite-email');
    await user.clear(email);
    await user.type(email, 'new-owner@expired-co.example');
    await user.type(control('tenant-reinvite-display-name'), '김대표');
    await user.type(control('tenant-reinvite-reason'), '담당자 변경');
    await user.click(control('tenant-reinvite-submit'));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe('/api/tenants/expired-co/first-admin-invitation');
    const body = post.mock.calls[0][1] as Record<string, unknown>;
    expect(body).toMatchObject({
      email: 'new-owner@expired-co.example',
      displayName: '김대표',
      reason: '담당자 변경',
    });
    expect(typeof body.idempotencyKey).toBe('string');
    expect((body.idempotencyKey as string).length).toBeGreaterThan(0);
  });

  it('🔴 NONE (no invitation left) → POST first-admin-invitation; submit stays disabled until email + name are valid', async () => {
    post.mockResolvedValue({ invitationId: 'inv-none', firstAdmin: true });
    get.mockResolvedValue({ items: ROWS, page: 0, size: 20, totalElements: ROWS.length, totalPages: 1 });
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-reinvite-none-co'));
    await user.type(control('tenant-reinvite-reason'), '첫 초대');
    expect(control('tenant-reinvite-submit')).toBeDisabled();
    await user.type(control('tenant-reinvite-email'), 'owner@none-co.example');
    await user.type(control('tenant-reinvite-display-name'), '홍길동');
    expect(control('tenant-reinvite-submit')).not.toBeDisabled();
    await user.click(control('tenant-reinvite-submit'));

    await waitFor(() => expect(post).toHaveBeenCalledTimes(1));
    expect(post.mock.calls[0][0]).toBe('/api/tenants/none-co/first-admin-invitation');
  });

  it('🔴 409 OPERATOR_INVITATION_ALREADY_PENDING → the Korean copy in the dialog', async () => {
    post.mockRejectedValue(new ApiError(409, 'OPERATOR_INVITATION_ALREADY_PENDING', 'already pending'));
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-reinvite-expired-co'));
    const email = control('tenant-reinvite-email');
    await user.clear(email);
    await user.type(email, 'other@expired-co.example');
    await user.type(control('tenant-reinvite-display-name'), '김대표');
    await user.type(control('tenant-reinvite-reason'), '변경');
    await user.click(control('tenant-reinvite-submit'));

    const err = await screen.findByTestId('tenant-reinvite-error');
    expect(err).toHaveTextContent(messageForCode('OPERATOR_INVITATION_ALREADY_PENDING'));
  });
});

describe('정리', () => {
  it('🔴 in-page confirm dialog with the contract sentence → PATCH status=SUSPENDED + reason; never window.confirm', async () => {
    patch.mockResolvedValue(TENANT_ANSWER('invited-co', 'SUSPENDED'));
    get.mockResolvedValue({ items: ROWS, page: 0, size: 20, totalElements: ROWS.length, totalPages: 1 });
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-cleanup-invited-co'));
    expect(control('tenant-confirm-dialog')).toHaveTextContent('초대를 취소하고 테넌트를 정지합니다 — 되돌릴 수 없습니다');
    await user.type(control('tenant-confirm-reason'), '수락 없음');
    await user.click(control('tenant-confirm-submit'));

    await waitFor(() => expect(patch).toHaveBeenCalledTimes(1));
    expect(patch.mock.calls[0][0]).toBe('/api/tenants/invited-co');
    expect(patch.mock.calls[0][1]).toEqual({ status: 'SUSPENDED', reason: '수락 없음' });
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('🔴 409 TENANT_NOT_PENDING → the Korean copy in the dialog', async () => {
    patch.mockRejectedValue(new ApiError(409, 'TENANT_NOT_PENDING', 'not pending'));
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-cleanup-expired-co'));
    await user.type(control('tenant-confirm-reason'), '정리');
    await user.click(control('tenant-confirm-submit'));

    const err = await screen.findByTestId('tenant-confirm-error');
    expect(err).toHaveTextContent(messageForCode('TENANT_NOT_PENDING'));
  });
});

describe('활성화 마치기', () => {
  it('🔴 ACCEPTED → PATCH status=ACTIVE + reason', async () => {
    patch.mockResolvedValue(TENANT_ANSWER('accepted-co', 'ACTIVE'));
    get.mockResolvedValue({ items: ROWS, page: 0, size: 20, totalElements: ROWS.length, totalPages: 1 });
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-finish-activation-accepted-co'));
    await user.type(control('tenant-confirm-reason'), '활성화 재시도');
    await user.click(control('tenant-confirm-submit'));

    await waitFor(() => expect(patch).toHaveBeenCalledTimes(1));
    expect(patch.mock.calls[0][0]).toBe('/api/tenants/accepted-co');
    expect(patch.mock.calls[0][1]).toEqual({ status: 'ACTIVE', reason: '활성화 재시도' });
    expect(confirmSpy).not.toHaveBeenCalled();
  });

  it('🔴 409 TENANT_FIRST_ADMIN_PENDING → the Korean copy in the dialog', async () => {
    patch.mockRejectedValue(new ApiError(409, 'TENANT_FIRST_ADMIN_PENDING', 'first admin pending'));
    const user = userEvent.setup();
    renderList();

    await user.click(control('tenants-row-finish-activation-accepted-co'));
    await user.type(control('tenant-confirm-reason'), '활성화');
    await user.click(control('tenant-confirm-submit'));

    const err = await screen.findByTestId('tenant-confirm-error');
    expect(err).toHaveTextContent(messageForCode('TENANT_FIRST_ADMIN_PENDING'));
  });
});

describe('error copy (shared/api/errors.ts)', () => {
  it.each(['TENANT_NOT_PENDING', 'TENANT_FIRST_ADMIN_PENDING', 'OPERATOR_INVITATION_ALREADY_PENDING'])(
    '🔴 %s has its own Korean message (not the generic fallback)',
    (code) => {
      const m = messageForCode(code);
      expect(m).not.toBe('알 수 없는 오류가 발생했습니다.');
      expect(m).toMatch(/[가-힣]/);
    },
  );
});
