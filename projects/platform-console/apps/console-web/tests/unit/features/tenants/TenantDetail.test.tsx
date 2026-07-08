import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantDetail } from '@/features/tenants';

/**
 * `TenantDetail` — the tenant detail + inline-edit surface (TASK-PC-FE-226).
 * `apiClient` is mocked; the edit flow is draft (`TenantForm`) → reason+confirm
 * (`TenantConfirmDialog`) → PATCH, mirroring the create flow's two-step gate.
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

const TENANT = {
  tenantId: 'acme-corp',
  displayName: 'ACME Corp',
  tenantType: 'B2B_ENTERPRISE',
  status: 'ACTIVE',
  createdAt: '2026-04-01T00:00:00Z',
  updatedAt: '2026-04-01T00:00:00Z',
};

function renderDetail() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TenantDetail tenant={TENANT} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockReset();
  patch.mockReset();
});

describe('TenantDetail — render (seeded)', () => {
  it('renders the dl fields in the 명칭→상태→식별자→날짜 convention order', () => {
    renderDetail();
    expect(screen.getByTestId('tenant-detail-display-name')).toHaveTextContent('ACME Corp');
    expect(screen.getByTestId('tenant-detail-status')).toHaveTextContent('ACTIVE');
    expect(screen.getByTestId('tenant-detail-tenant-id')).toHaveTextContent('acme-corp');
    expect(screen.getByTestId('tenant-detail-tenant-type')).toHaveTextContent('B2B');
    expect(screen.getByTestId('tenant-detail-created-at')).toBeInTheDocument();
  });
});

describe('TenantDetail — edit', () => {
  it('opens the edit form, then the reason+confirm dialog, then PATCHes on confirm', async () => {
    patch.mockResolvedValue({ ...TENANT, displayName: 'ACME Corp KR', status: 'ACTIVE' });
    const user = userEvent.setup();
    renderDetail();

    await user.click(screen.getByTestId('tenant-detail-edit'));
    const nameInput = screen.getByTestId('tenant-form-display-name');
    await user.clear(nameInput);
    await user.type(nameInput, 'ACME Corp KR');
    await user.click(screen.getByTestId('tenant-edit-submit'));

    const submit = screen.getByTestId('tenant-confirm-submit');
    expect(submit).toBeDisabled();
    await user.type(screen.getByTestId('tenant-confirm-reason'), '법인명 변경');
    await user.click(submit);

    await waitFor(() =>
      expect(patch).toHaveBeenCalledWith('/api/tenants/acme-corp', {
        displayName: 'ACME Corp KR',
        status: 'ACTIVE',
        reason: '법인명 변경',
      }),
    );
  });

  it('SUSPENDED selection shows the lockout warning copy', async () => {
    const user = userEvent.setup();
    renderDetail();
    await user.click(screen.getByTestId('tenant-detail-edit'));
    await user.selectOptions(screen.getByTestId('tenant-form-status'), 'SUSPENDED');
    expect(screen.getByTestId('tenant-form-suspend-warning')).toBeInTheDocument();
  });

  it('surfaces a 403 TENANT_SCOPE_DENIED inline (forbidden, not a fake degrade)', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    patch.mockRejectedValue(new ApiError(403, 'TENANT_SCOPE_DENIED', 'no'));
    const user = userEvent.setup();
    renderDetail();

    await user.click(screen.getByTestId('tenant-detail-edit'));
    await user.click(screen.getByTestId('tenant-edit-submit'));
    await user.type(screen.getByTestId('tenant-confirm-reason'), '시도');
    await user.click(screen.getByTestId('tenant-confirm-submit'));

    expect(await screen.findByTestId('tenant-confirm-error')).toHaveTextContent(
      '해당 테넌트에 대한 조회 권한이 없습니다.',
    );
  });
});
