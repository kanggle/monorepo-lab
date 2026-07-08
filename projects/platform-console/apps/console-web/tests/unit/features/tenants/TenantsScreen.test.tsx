import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantsScreen } from '@/features/tenants';

/**
 * `TenantsScreen` — the tenant-management list + create surface
 * (TASK-PC-FE-226). `apiClient` is mocked so the create mutation is asserted
 * without a real backend (mirrors `SubscriptionsScreen.test.tsx` /
 * `CreateOperatorForm.test.tsx` conventions: draft → reason+confirm dialog →
 * mutate).
 */

const get = vi.fn();
const post = vi.fn();
vi.mock('@/shared/api/client', () => ({
  apiClient: {
    get: (...a: unknown[]) => get(...a),
    post: (...a: unknown[]) => post(...a),
    patch: vi.fn(),
  },
}));

vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const INITIAL_PAGE = {
  items: [
    {
      tenantId: 'fan-platform',
      displayName: 'Fan Platform',
      tenantType: 'B2C_CONSUMER',
      status: 'ACTIVE',
      createdAt: '2026-04-01T00:00:00Z',
      updatedAt: '2026-04-01T00:00:00Z',
    },
  ],
  page: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
};

function renderScreen() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <TenantsScreen initial={INITIAL_PAGE} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
});

describe('TenantsScreen — render (seeded)', () => {
  it('renders the seeded list without an extra fetch', () => {
    renderScreen();
    expect(screen.getByTestId('tenants-table')).toBeInTheDocument();
    expect(screen.getByTestId('tenants-row-fan-platform')).toBeInTheDocument();
    expect(screen.getByText('Fan Platform')).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled();
  });
});

describe('TenantsScreen — create', () => {
  it('keeps submit disabled for an invalid tenantId format (fail-closed pre-gate)', async () => {
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('tenant-form-tenant-id'), 'Bad_ID!');
    await user.type(screen.getByTestId('tenant-form-display-name'), 'New Tenant');

    expect(screen.getByTestId('tenant-create-submit')).toBeDisabled();
    // A disabled submit never fires — the confirm dialog never opens.
    expect(screen.queryByTestId('tenant-confirm-submit')).not.toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('opens the reason+confirm dialog on a valid draft, then POSTs on confirm', async () => {
    post.mockResolvedValue({
      tenantId: 'new-tenant',
      displayName: 'New Tenant',
      tenantType: 'B2B_ENTERPRISE',
      status: 'ACTIVE',
      createdAt: '2026-07-08T00:00:00Z',
      updatedAt: '2026-07-08T00:00:00Z',
    });
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('tenant-form-tenant-id'), 'new-tenant');
    await user.type(screen.getByTestId('tenant-form-display-name'), 'New Tenant');
    await user.selectOptions(
      screen.getByTestId('tenant-form-tenant-type'),
      'B2B_ENTERPRISE',
    );
    await user.click(screen.getByTestId('tenant-create-submit'));

    const submit = screen.getByTestId('tenant-confirm-submit');
    expect(submit).toBeDisabled(); // no reason yet

    await user.type(screen.getByTestId('tenant-confirm-reason'), '신규 파트너 온보딩');
    expect(submit).not.toBeDisabled();
    await user.click(submit);

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(
        '/api/tenants',
        expect.objectContaining({
          tenantId: 'new-tenant',
          displayName: 'New Tenant',
          tenantType: 'B2B_ENTERPRISE',
          reason: '신규 파트너 온보딩',
          idempotencyKey: expect.any(String),
        }),
      ),
    );
  });

  it('surfaces a 409 TENANT_ALREADY_EXISTS inline without a fake success', async () => {
    const { ApiError } = await import('@/shared/api/errors');
    post.mockRejectedValue(new ApiError(409, 'TENANT_ALREADY_EXISTS', 'exists'));
    const user = userEvent.setup();
    renderScreen();

    await user.type(screen.getByTestId('tenant-form-tenant-id'), 'fan-platform');
    await user.type(screen.getByTestId('tenant-form-display-name'), 'Fan Platform 2');
    await user.click(screen.getByTestId('tenant-create-submit'));
    await user.type(screen.getByTestId('tenant-confirm-reason'), '재등록 시도');
    await user.click(screen.getByTestId('tenant-confirm-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('tenant-confirm-error')).toHaveTextContent(
        '이미 사용 중인 조직 ID',
      ),
    );
    // Dialog stays open on failure — no navigation / fake success.
    expect(screen.getByTestId('tenant-confirm-submit')).toBeInTheDocument();
  });
});
