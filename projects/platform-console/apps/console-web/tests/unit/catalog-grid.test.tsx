import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import type { ReactNode } from 'react';
import { CatalogGrid } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-PC-FE-065 — the catalog ALWAYS shows the full product list (no tenant
 * filter); clicking a tenant sets it active (mutate) and, on success, navigates
 * to that product's domain ops (router.push to resolveConsoleRoute). The switch
 * hook + router are mocked; mutate invokes its onSuccess so we can assert the
 * navigation ordering.
 */
const { mutateMock, pushMock } = vi.hoisted(() => ({
  mutateMock: vi.fn(),
  pushMock: vi.fn(),
}));
vi.mock('@/features/tenant', () => ({
  useTenantSwitch: () => ({ mutate: mutateMock, isPending: false, isError: false }),
}));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: pushMock }) }));
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

beforeEach(() => {
  // mutate(tenant, { onSuccess }) → run onSuccess (the switch "lands").
  mutateMock.mockImplementation((_tenant: string, opts?: { onSuccess?: () => void }) =>
    opts?.onSuccess?.(),
  );
});
afterEach(() => {
  cleanup();
  mutateMock.mockReset();
  pushMock.mockReset();
});

const p = (productKey: RegistryProduct['productKey'], tenants: string[]): RegistryProduct => ({
  productKey,
  displayName: productKey.toUpperCase(),
  available: true,
  tenants,
  baseRoute: `/${productKey}`,
});

const PRODUCTS = [p('wms', ['acme', 'globex']), p('scm', ['globex']), p('finance', ['acme'])];

describe('CatalogGrid — always full list + tenant navigates to domain ops (TASK-PC-FE-065)', () => {
  it('renders ALL products (no filter banner / no clear)', () => {
    render(<CatalogGrid products={PRODUCTS} />);
    expect(screen.getByTestId('tile-wms')).toBeInTheDocument();
    expect(screen.getByTestId('tile-scm')).toBeInTheDocument();
    expect(screen.getByTestId('tile-finance')).toBeInTheDocument();
    expect(screen.queryByTestId('catalog-tenant-filter')).toBeNull();
    expect(screen.queryByTestId('catalog-filter-clear')).toBeNull();
  });

  it('clicking a tenant sets it active and navigates to that product\'s route; the list does NOT shrink', () => {
    render(<CatalogGrid products={PRODUCTS} />);
    fireEvent.click(screen.getByTestId('tile-wms-tenant-acme'));

    expect(mutateMock).toHaveBeenCalledWith('acme', expect.objectContaining({ onSuccess: expect.any(Function) }));
    expect(pushMock).toHaveBeenCalledWith('/wms'); // resolveConsoleRoute(wms baseRoute)
    // No filtering — every product still shown.
    expect(screen.getByTestId('tile-scm')).toBeInTheDocument();
    expect(screen.getByTestId('tile-finance')).toBeInTheDocument();
  });

  it('header dot + per-tenant dot come from healthByDomain (same product tone)', () => {
    render(<CatalogGrid products={PRODUCTS} healthByDomain={{ wms: 'healthy' }} />);
    expect(screen.getByTestId('tile-wms-status')).toHaveAttribute('data-tone', 'healthy');
    expect(screen.getByTestId('tile-wms-tenant-acme-status')).toHaveAttribute('data-tone', 'healthy');
    // no health for scm → no dots
    expect(screen.queryByTestId('tile-scm-status')).toBeNull();
    expect(screen.queryByTestId('tile-scm-tenant-globex-status')).toBeNull();
  });
});
