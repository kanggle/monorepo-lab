import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, within, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { DomainPermissionSetsScreen } from '@/features/domain-permission-sets';
import type { DomainRole } from '@/features/domain-permission-sets';

/**
 * `DomainPermissionSetsScreen` — the 업무 권한 세트 surface (TASK-PC-FE-338 /
 * ADR-MONO-083 D9). `apiClient` is mocked so mutations are asserted without a
 * real backend (mirrors `OperatorGroupsScreen.test.tsx` conventions: draft →
 * reason+confirm dialog → mutate). A seeded render fires no extra list GET.
 */

const get = vi.fn();
const post = vi.fn();
const patch = vi.fn();
const del = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    post: (...a: unknown[]) => post(...a),
    patch: (...a: unknown[]) => patch(...a),
    delete: (...a: unknown[]) => del(...a),
  },
}));

const ROLES: DomainRole[] = [
  { domainKey: 'wms', role: 'INVENTORY_READ', access: 'READ', fineGrained: true, label: '재고 조회' },
  { domainKey: 'wms', role: 'INVENTORY_WRITE', access: 'WRITE', fineGrained: true, label: '재고 변경(조정 · 이동)' },
  { domainKey: 'scm', role: 'SCM_OPERATOR', access: 'OPERATE', fineGrained: false, label: 'SCM 전체 업무(세분 미지원)' },
];

const PRESET = {
  setId: 'preset-1',
  tenantId: null,
  systemKey: 'wms-inventory-read',
  readOnly: true,
  name: 'wms-inventory-read',
  description: null,
  roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
  domains: ['wms'],
  grantCount: 2,
  groupCount: 0,
  createdAt: '2026-10-14T09:00:00Z',
  updatedAt: '2026-10-14T09:00:00Z',
};

const TENANT_SET = {
  setId: 'set-1',
  tenantId: 'acme-corp',
  systemKey: null,
  readOnly: false,
  name: '재고팀',
  description: '재고 조회 · 변경',
  roles: [{ domainKey: 'wms', role: 'INVENTORY_READ' }],
  domains: ['wms'],
  grantCount: 0,
  groupCount: 0,
  createdAt: '2026-10-14T09:00:00Z',
  updatedAt: '2026-10-14T09:00:00Z',
};

function renderScreen(subscribedDomains: string[] | null = ['wms']) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <DomainPermissionSetsScreen
        roles={ROLES}
        initialSets={[PRESET, TENANT_SET]}
        subscribedDomains={subscribedDomains}
      />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  patch.mockReset();
  del.mockReset();
});

describe('DomainPermissionSetsScreen — render (seeded)', () => {
  it('renders the seeded presets + tenant sets without an extra fetch', () => {
    renderScreen();
    expect(screen.getByTestId('domain-set-preset-preset-1')).toBeInTheDocument();
    expect(screen.getByTestId('domain-set-tenant-set-1')).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });

  it('carries the «적용 예정» status-honesty note, never «즉시» anywhere on screen', () => {
    const { container } = renderScreen();
    const note = screen.getByTestId('domain-set-applies-later-note');
    expect(note).toHaveTextContent('적용 예정');
    expect(note).toHaveTextContent('아직 실제 업무 권한을 좁히지 않습니다');
    expect(container.textContent).not.toMatch(/즉시/);
  });
});

describe('DomainPermissionSetsScreen — presets are read-only', () => {
  it('a preset carries the «시스템» badge and NO edit/delete button, only «복제해서 새 세트»', () => {
    renderScreen();
    const row = screen.getByTestId('domain-set-preset-preset-1');
    expect(within(row).getByTestId('domain-set-preset-preset-1-badge')).toHaveTextContent('시스템');
    expect(within(row).getByTestId('domain-set-preset-preset-1-clone')).toBeInTheDocument();
    expect(within(row).queryByText('수정')).toBeNull();
    expect(within(row).queryByText('삭제')).toBeNull();
  });

  it('«복제해서 새 세트» opens the CREATE form pre-filled with the preset\'s roles', async () => {
    const user = userEvent.setup();
    renderScreen();
    await user.click(screen.getByTestId('domain-set-preset-preset-1-clone'));

    expect(screen.getByTestId('domain-set-create-form')).toBeInTheDocument();
    const checkbox = screen.getByTestId(
      'domain-set-form-role-wms-INVENTORY_READ',
    ) as HTMLInputElement;
    expect(checkbox.checked).toBe(true);
  });
});

describe('DomainPermissionSetsScreen — fineGrained=false label (ADR-083 D3)', () => {
  it('renders the producer label VERBATIM for a coarse (fineGrained=false) role', async () => {
    const user = userEvent.setup();
    renderScreen();
    await user.click(screen.getByTestId('domain-set-add'));

    const label = screen.getByText('SCM 전체 업무(세분 미지원)');
    expect(label).toBeInTheDocument();
    // never split / rephrased as narrower — the raw producer string appears whole.
    expect(label.textContent).toBe('SCM 전체 업무(세분 미지원)');
  });
});

describe('DomainPermissionSetsScreen — subscription-aware role picker (D9 nav rule reused)', () => {
  it('a domain the active tenant has not subscribed to is disabled with «구독 없음»', async () => {
    const user = userEvent.setup();
    renderScreen(['wms']); // scm NOT subscribed
    await user.click(screen.getByTestId('domain-set-add'));

    expect(screen.getByTestId('domain-set-form-domain-scm-unsubscribed')).toHaveTextContent('구독 없음');
    const scmCheckbox = screen.getByTestId(
      'domain-set-form-role-scm-SCM_OPERATOR',
    ) as HTMLInputElement;
    expect(scmCheckbox).toBeDisabled();
    // the subscribed domain is NOT badged / disabled.
    expect(screen.queryByTestId('domain-set-form-domain-wms-unsubscribed')).toBeNull();
    expect(
      (screen.getByTestId('domain-set-form-role-wms-INVENTORY_READ') as HTMLInputElement).disabled,
    ).toBe(false);
  });

  it('subscription data unknown (null) ⇒ NO domain is disabled — "don\'t know ⇒ don\'t disable"', async () => {
    const user = userEvent.setup();
    renderScreen(null);
    await user.click(screen.getByTestId('domain-set-add'));

    expect(screen.queryByTestId('domain-set-form-domain-scm-unsubscribed')).toBeNull();
    expect(
      (screen.getByTestId('domain-set-form-role-scm-SCM_OPERATOR') as HTMLInputElement).disabled,
    ).toBe(false);
  });
});

describe('DomainPermissionSetsScreen — create (Idempotency-Key required)', () => {
  it('opens the reason+confirm dialog on a valid draft, then POSTs WITH an Idempotency-Key', async () => {
    post.mockResolvedValue(TENANT_SET);
    const user = userEvent.setup();
    renderScreen();
    await user.click(screen.getByTestId('domain-set-add'));

    await user.type(screen.getByTestId('domain-set-form-name'), '신규 세트');
    await user.click(screen.getByTestId('domain-set-form-role-wms-INVENTORY_READ'));
    await user.click(screen.getByTestId('domain-set-form-submit'));

    const submit = screen.getByTestId('domain-set-reason-submit');
    expect(submit).toBeDisabled(); // no reason yet
    await user.type(screen.getByTestId('domain-set-reason-input'), '재고팀 신설');
    expect(submit).not.toBeDisabled();
    await user.click(submit);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/api/domain-permission-sets',
        expect.objectContaining({
          name: '신규 세트',
          reason: '재고팀 신설',
          idempotencyKey: expect.any(String),
        }),
      ),
    );
  });

  it('409 DOMAIN_PERMISSION_SET_READ_ONLY → the exact contract copy inline', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    post.mockRejectedValue(new ApiError(409, 'DOMAIN_PERMISSION_SET_READ_ONLY', 'x'));
    const user = userEvent.setup();
    renderScreen();
    await user.click(screen.getByTestId('domain-set-add'));
    await user.type(screen.getByTestId('domain-set-form-name'), '신규 세트');
    await user.click(screen.getByTestId('domain-set-form-role-wms-INVENTORY_READ'));
    await user.click(screen.getByTestId('domain-set-form-submit'));
    await user.type(screen.getByTestId('domain-set-reason-input'), '시도');
    await user.click(screen.getByTestId('domain-set-reason-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('domain-set-reason-error')).toHaveTextContent(
        '시스템 세트는 바꿀 수 없습니다 — 복제해서 새 세트를 만드세요',
      ),
    );
  });

  it('422 DOMAIN_NOT_SUBSCRIBED → the exact contract copy inline', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    post.mockRejectedValue(new ApiError(422, 'DOMAIN_NOT_SUBSCRIBED', 'x'));
    const user = userEvent.setup();
    renderScreen();
    await user.click(screen.getByTestId('domain-set-add'));
    await user.type(screen.getByTestId('domain-set-form-name'), '신규 세트');
    await user.click(screen.getByTestId('domain-set-form-role-wms-INVENTORY_READ'));
    await user.click(screen.getByTestId('domain-set-form-submit'));
    await user.type(screen.getByTestId('domain-set-reason-input'), '시도');
    await user.click(screen.getByTestId('domain-set-reason-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('domain-set-reason-error')).toHaveTextContent(
        '이 테넌트가 구독하지 않은 도메인이 들어 있습니다',
      ),
    );
  });
});

describe('DomainPermissionSetsScreen — edit (NO Idempotency-Key, latency copy)', () => {
  it('PATCHes WITHOUT an idempotencyKey field, and the confirm carries the 30분 latency copy (never 즉시)', async () => {
    patch.mockResolvedValue({ ...TENANT_SET, name: '재고팀 (개편)' });
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('domain-set-tenant-set-1-edit'));
    expect(screen.getByTestId('domain-set-edit-form')).toBeInTheDocument();
    await user.click(screen.getByTestId('domain-set-form-submit'));

    const dialog = screen.getByTestId('domain-set-reason-dialog');
    expect(dialog).toHaveTextContent('최대 30분');
    expect(dialog.textContent).not.toMatch(/즉시/);

    await user.type(screen.getByTestId('domain-set-reason-input'), '역할 조정');
    await user.click(screen.getByTestId('domain-set-reason-submit'));

    await waitFor(() => expect(patch).toHaveBeenCalledTimes(1));
    const [, body] = patch.mock.calls[0];
    expect(body.reason).toBe('역할 조정');
    expect(Object.keys(body)).not.toContain('idempotencyKey');
  });

  it('409 DOMAIN_PERMISSION_SET_IN_USE → the exact contract copy inline', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    patch.mockRejectedValue(new ApiError(409, 'DOMAIN_PERMISSION_SET_IN_USE', 'x'));
    const user = userEvent.setup();
    renderScreen();
    await user.click(screen.getByTestId('domain-set-tenant-set-1-edit'));
    await user.click(screen.getByTestId('domain-set-form-submit'));
    await user.type(screen.getByTestId('domain-set-reason-input'), '시도');
    await user.click(screen.getByTestId('domain-set-reason-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('domain-set-reason-error')).toHaveTextContent(
        '아직 배정돼 있는 세트입니다 — 먼저 회수하세요',
      ),
    );
  });
});

describe('DomainPermissionSetsScreen — delete (NO Idempotency-Key, latency copy)', () => {
  it('DELETEs with the reason in the body ONLY (no idempotencyKey, no query string)', async () => {
    del.mockResolvedValue(undefined);
    const user = userEvent.setup();
    renderScreen();

    await user.click(screen.getByTestId('domain-set-tenant-set-1-delete'));
    const dialog = screen.getByTestId('domain-set-reason-dialog');
    expect(dialog).toHaveTextContent('최대 30분');
    expect(dialog.textContent).not.toMatch(/즉시/);

    await user.type(screen.getByTestId('domain-set-reason-input'), '폐지');
    await user.click(screen.getByTestId('domain-set-reason-submit'));

    await waitFor(() =>
      expect(del).toHaveBeenCalledWith(
        '/api/domain-permission-sets/set-1',
        { body: { reason: '폐지' } },
      ),
    );
  });
});
