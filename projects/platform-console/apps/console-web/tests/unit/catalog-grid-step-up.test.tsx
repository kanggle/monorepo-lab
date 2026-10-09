import { describe, it, expect, vi, afterEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import { CatalogGrid } from '@/features/catalog';
import { ApiError } from '@/shared/api/errors';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-MONO-771 S3 (§ 2.7) — a catalog tenant row is a switch surface, and for a
 * single-tenant operator the ONLY one (the top switcher is a static label then).
 * A `403 MFA_REQUIRED` from that switch must surface the step-up offer here too.
 */
const { hookState } = vi.hoisted(() => ({
  hookState: { isError: false, error: null as unknown },
}));
vi.mock('@/shared/api/use-tenant-switch', () => ({
  useTenantSwitch: () => ({ mutate: vi.fn(), isPending: false, ...hookState }),
}));
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const PRODUCTS: RegistryProduct[] = [
  { productKey: 'wms', displayName: 'WMS', available: true, tenants: ['acme'], baseRoute: '/wms' },
];

afterEach(() => {
  cleanup();
  hookState.isError = false;
  hookState.error = null;
});

describe('CatalogGrid — step-up offer (§ 2.7)', () => {
  it('🔴 MFA_REQUIRED switch error → the step-up offer is shown', () => {
    hookState.isError = true;
    hookState.error = new ApiError(403, 'MFA_REQUIRED', 'x');
    render(<CatalogGrid products={PRODUCTS} />);
    expect(screen.getByTestId('tenant-step-up')).toBeInTheDocument();
  });

  it('🔵 control — another switch error shows no step-up offer (unchanged)', () => {
    hookState.isError = true;
    hookState.error = new ApiError(403, 'TENANT_FORBIDDEN', 'x');
    render(<CatalogGrid products={PRODUCTS} />);
    expect(screen.queryByTestId('tenant-step-up')).toBeNull();
  });

  it('🔵 control — no error, no offer', () => {
    render(<CatalogGrid products={PRODUCTS} />);
    expect(screen.queryByTestId('tenant-step-up')).toBeNull();
  });
});
