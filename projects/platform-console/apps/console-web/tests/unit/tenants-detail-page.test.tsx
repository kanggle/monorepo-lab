import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render } from '@testing-library/react';

/**
 * `/tenants/[tenantId]` SSR gating waterfall (TASK-PC-FE-226) — mirrors the
 * ecommerce seller-detail page test precedent: noTenant → permissionError →
 * notFound → degraded → happy.
 */

const getTenantDetailState = vi.fn();
vi.mock('@/features/tenants', () => ({
  getTenantDetailState: (a: unknown) => getTenantDetailState(a),
  TenantDetail: ({ tenant }: { tenant: { tenantId: string } }) => (
    <div data-testid="tenant-detail-screen" data-tenant-id={tenant.tenantId} />
  ),
}));
vi.mock('next/link', () => ({
  default: ({ href, children }: { href: string; children: React.ReactNode }) => (
    <a href={href}>{children}</a>
  ),
}));

const notFoundSpy = vi.fn(() => {
  throw new Error('NEXT_NOT_FOUND');
});
vi.mock('next/navigation', () => ({ notFound: () => notFoundSpy() }));

import TenantDetailPage from '@/app/(console)/tenants/[tenantId]/page';

const params = (tenantId: string) => ({ params: Promise.resolve({ tenantId }) });

beforeEach(() => {
  getTenantDetailState.mockReset();
  notFoundSpy.mockClear();
});

describe('TenantDetailPage — SSR gating', () => {
  it('renders the no-tenant gate', async () => {
    getTenantDetailState.mockResolvedValue({
      tenant: null,
      degraded: false,
      noTenant: true,
      permissionError: null,
      notFound: false,
    });
    const ui = await TenantDetailPage(params('acme-corp'));
    const { getByTestId } = render(ui);
    expect(getByTestId('tenant-detail-no-tenant')).toBeInTheDocument();
  });

  it('renders the permission-denied gate', async () => {
    getTenantDetailState.mockResolvedValue({
      tenant: null,
      degraded: false,
      noTenant: false,
      permissionError: { code: 'PERMISSION_DENIED', message: 'no' },
      notFound: false,
    });
    const ui = await TenantDetailPage(params('acme-corp'));
    const { getByTestId } = render(ui);
    expect(getByTestId('tenant-detail-permission-denied')).toBeInTheDocument();
  });

  it('calls notFound() on a missing tenant', async () => {
    getTenantDetailState.mockResolvedValue({
      tenant: null,
      degraded: false,
      noTenant: false,
      permissionError: null,
      notFound: true,
    });
    await expect(TenantDetailPage(params('missing'))).rejects.toThrow(
      'NEXT_NOT_FOUND',
    );
    expect(notFoundSpy).toHaveBeenCalledTimes(1);
  });

  it('renders the degraded notice on a 503/timeout', async () => {
    getTenantDetailState.mockResolvedValue({
      tenant: null,
      degraded: true,
      noTenant: false,
      permissionError: null,
      notFound: false,
    });
    const ui = await TenantDetailPage(params('acme-corp'));
    const { getByTestId } = render(ui);
    expect(getByTestId('tenant-detail-degraded')).toBeInTheDocument();
  });

  it('renders TenantDetail with the seeded tenant on the success path', async () => {
    getTenantDetailState.mockResolvedValue({
      tenant: { tenantId: 'acme-corp' },
      degraded: false,
      noTenant: false,
      permissionError: null,
      notFound: false,
    });
    const ui = await TenantDetailPage(params('acme-corp'));
    const { getByTestId } = render(ui);
    expect(getByTestId('tenant-detail-screen')).toHaveAttribute(
      'data-tenant-id',
      'acme-corp',
    );
  });
});
