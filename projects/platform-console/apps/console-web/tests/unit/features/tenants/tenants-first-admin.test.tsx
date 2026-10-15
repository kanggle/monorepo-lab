import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantDetail } from '@/features/tenants';
import { TenantsTable } from '@/features/tenants/components/TenantsTable';
import { TenantSchema, TenantPageSchema } from '@/features/tenants/api/types';

/**
 * TASK-MONO-773 S4 (owner decision 2026-10-15 UTC — S1 open question 7: the console `/tenants` surface widens in
 * the SAME PR as IAM's `firstAdmin`-required create). IAM contract: admin-api.md § Tenant Lifecycle — `status`
 * gains `PENDING` («관리자 대기») and every tenant response carries a `firstAdmin` summary
 * (`INVITED` · `EXPIRED` · `NONE` · `ACCEPTED`, or `null`). Console copy: console-integration-contract.md § 2.6.4
 * «`/tenants` (SUPER_ADMIN list)» — a `PENDING` row reads «관리자 대기» + the state suffix (`EXPIRED` → «· 초대
 * 만료», `NONE` → «· 초대 없음», `ACCEPTED` → «· 활성화 재시도»).
 *
 * 🔵 Deploy-order measurement (admin-api § POST /api/admin/tenants «호환»): Vercel ships the console before the
 * backend AMI, so the new console reads OLD responses (no `PENDING`, no `firstAdmin`) for a while — the
 * «old response» cases below pin that it still parses and renders.
 */

const get = vi.fn();
const patch = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    post: vi.fn(),
    patch: (...a: unknown[]) => patch(...a),
  },
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const BASE = {
  displayName: 'ACME',
  tenantType: 'B2B_ENTERPRISE',
  createdAt: '2026-10-15T00:00:00Z',
  updatedAt: '2026-10-15T00:00:00Z',
};

function pending(tenantId: string, state: string | null) {
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

/** An OLD (pre-S4 backend) row: no PENDING, no firstAdmin key at all. */
const OLD_ROW = { ...BASE, tenantId: 'old-corp', status: 'ACTIVE' };

beforeEach(() => {
  get.mockReset();
  patch.mockReset();
});

describe('schemas — firstAdmin summary · old responses', () => {
  it('a PENDING tenant keeps its firstAdmin summary through TenantSchema', () => {
    const parsed = TenantSchema.parse(pending('acme', 'EXPIRED')) as Record<string, unknown>;
    expect(parsed.status).toBe('PENDING');
    expect(parsed.firstAdmin).toEqual({
      state: 'EXPIRED',
      email: 'owner@acme.example',
      invitationId: 'inv-acme',
      expiresAt: '2026-10-22T00:00:00Z',
    });
  });

  it('a page with a PENDING row keeps the summary; a null summary stays null', () => {
    const page = TenantPageSchema.parse({
      items: [pending('acme', 'INVITED'), { ...OLD_ROW, firstAdmin: null }],
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    });
    const items = page.items as Array<Record<string, unknown>>;
    expect((items[0].firstAdmin as Record<string, unknown>).state).toBe('INVITED');
    expect(items[1].firstAdmin).toBeNull();
  });

  it('🔵 deploy-order: an OLD response (no PENDING, no firstAdmin key) still parses', () => {
    expect(() => TenantSchema.parse(OLD_ROW)).not.toThrow();
    expect(() =>
      TenantPageSchema.parse({ items: [OLD_ROW], page: 0, size: 20, totalElements: 1, totalPages: 1 }),
    ).not.toThrow();
  });
});

function renderTable(rows: unknown[]) {
  return render(
    <TenantsTable
      statusFilter=""
      onStatusFilterChange={vi.fn()}
      tenantTypeFilter=""
      onTenantTypeFilterChange={vi.fn()}
      onSubmitFilter={vi.fn()}
      isListError={false}
      rows={rows as never}
      page={{ items: rows as never, page: 0, size: 20, totalElements: rows.length, totalPages: 1 }}
      currentPage={0}
      onPrevPage={vi.fn()}
      onNextPage={vi.fn()}
    />,
  );
}

function statusCell(tenantId: string): HTMLElement {
  const cell = screen.queryByTestId(`tenants-row-status-${tenantId}`);
  expect(cell, `the status cell of ${tenantId} (testid tenants-row-status-${tenantId})`).not.toBeNull();
  return cell as HTMLElement;
}

describe('TenantsTable — «관리자 대기» rows (§ 2.6.4)', () => {
  it.each([
    ['INVITED', '관리자 대기'],
    ['EXPIRED', '관리자 대기 · 초대 만료'],
    ['NONE', '관리자 대기 · 초대 없음'],
    ['ACCEPTED', '관리자 대기 · 활성화 재시도'],
  ])('PENDING + firstAdmin.state=%s reads «%s»', (state, label) => {
    renderTable([pending('acme', state)]);
    expect(statusCell('acme')).toHaveTextContent(label);
    expect(statusCell('acme').textContent?.trim()).toBe(label);
    expect(screen.queryByText('PENDING')).toBeNull();
  });

  it('a PENDING row with a null summary still reads «관리자 대기»', () => {
    renderTable([pending('acme', null)]);
    expect(statusCell('acme').textContent?.trim()).toBe('관리자 대기');
  });

  it('🔵 deploy-order (control): an OLD row (no firstAdmin key) renders as today — name, id and ACTIVE', () => {
    renderTable([OLD_ROW]);
    const row = screen.getByTestId('tenants-row-old-corp');
    expect(row).toHaveTextContent('ACME');
    expect(row).toHaveTextContent('old-corp');
    expect(row).toHaveTextContent('ACTIVE');
    expect(row).not.toHaveTextContent('관리자 대기');
  });

  it('the status filter offers PENDING as «관리자 대기»', () => {
    renderTable([]);
    const select = screen.getByTestId('tenants-filter-status') as HTMLSelectElement;
    const pendingOption = Array.from(select.options).find((o) => o.value === 'PENDING');
    expect(pendingOption, 'a PENDING option in the status filter').toBeDefined();
    expect(pendingOption?.textContent).toBe('관리자 대기');
  });
});

function renderDetail(tenant: Record<string, unknown>) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TenantDetail tenant={tenant as never} />
    </QueryClientProvider>,
  );
}

describe('TenantDetail — a PENDING tenant', () => {
  it('shows «관리자 대기 · 초대 만료» as its status', () => {
    renderDetail(pending('acme', 'EXPIRED'));
    expect(screen.getByTestId('tenant-detail-status')).toHaveTextContent('관리자 대기 · 초대 만료');
  });

  it('renaming it PATCHes displayName only — never status=PENDING (IAM answers 400 to that)', async () => {
    const tenant = pending('acme', 'INVITED');
    patch.mockResolvedValue({ ...tenant, displayName: 'ACME KR' });
    const user = userEvent.setup();
    renderDetail(tenant);

    await user.click(screen.getByTestId('tenant-detail-edit'));
    const nameInput = screen.getByTestId('tenant-form-display-name');
    await user.clear(nameInput);
    await user.type(nameInput, 'ACME KR');
    await user.click(screen.getByTestId('tenant-edit-submit'));
    await user.type(screen.getByTestId('tenant-confirm-reason'), '법인명 변경');
    await user.click(screen.getByTestId('tenant-confirm-submit'));

    await waitFor(() => expect(patch).toHaveBeenCalled());
    const body = patch.mock.calls[0][1] as Record<string, unknown>;
    expect(body.displayName).toBe('ACME KR');
    expect(body).not.toHaveProperty('status');
  });
});
