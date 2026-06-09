import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import type { ReactNode } from 'react';
import { CatalogGrid } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-PC-FE-064 — the catalog tenant filter + active-tenant select. The
 * active-tenant switch hook is mocked (the switch flow is covered elsewhere);
 * this asserts the filter behaviour + the header status dot.
 */
const { mutateMock } = vi.hoisted(() => ({ mutateMock: vi.fn() }));
vi.mock('@/features/tenant', () => ({
  useTenantSwitch: () => ({ mutate: mutateMock, isPending: false, isError: false }),
}));
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

afterEach(() => {
  cleanup();
  mutateMock.mockReset();
});

const p = (productKey: RegistryProduct['productKey'], tenants: string[]): RegistryProduct => ({
  productKey,
  displayName: productKey.toUpperCase(),
  available: true,
  tenants,
  baseRoute: `/${productKey}`,
});

// wms↔acme,globex / scm↔globex / finance↔acme
const PRODUCTS = [p('wms', ['acme', 'globex']), p('scm', ['globex']), p('finance', ['acme'])];

describe('CatalogGrid tenant filter + header dot (TASK-PC-FE-064)', () => {
  it('unfiltered (no active tenant) shows all products; header dot from healthByDomain', () => {
    render(<CatalogGrid products={PRODUCTS} healthByDomain={{ wms: 'healthy' }} />);
    expect(screen.getByTestId('tile-wms')).toBeInTheDocument();
    expect(screen.getByTestId('tile-scm')).toBeInTheDocument();
    expect(screen.getByTestId('tile-finance')).toBeInTheDocument();
    expect(screen.queryByTestId('catalog-tenant-filter')).toBeNull();
    // dot only where health is present
    expect(screen.getByTestId('tile-wms-status')).toHaveAttribute('data-tone', 'healthy');
    expect(screen.queryByTestId('tile-scm-status')).toBeNull();
  });

  it('clicking a tenant sets active (mutate) and filters to products including it', () => {
    render(<CatalogGrid products={PRODUCTS} />);
    fireEvent.click(screen.getByTestId('tile-wms-tenant-acme'));

    expect(mutateMock).toHaveBeenCalledWith('acme');
    // acme ∈ wms, finance — NOT scm(globex)
    expect(screen.getByTestId('tile-wms')).toBeInTheDocument();
    expect(screen.getByTestId('tile-finance')).toBeInTheDocument();
    expect(screen.queryByTestId('tile-scm')).toBeNull();
    expect(screen.getByTestId('catalog-tenant-filter')).toHaveTextContent('acme');
  });

  it('"전체 보기" clears the filter and shows all products again', () => {
    render(<CatalogGrid products={PRODUCTS} />);
    fireEvent.click(screen.getByTestId('tile-wms-tenant-acme'));
    expect(screen.queryByTestId('tile-scm')).toBeNull();

    fireEvent.click(screen.getByTestId('catalog-filter-clear'));
    expect(screen.queryByTestId('catalog-tenant-filter')).toBeNull();
    expect(screen.getByTestId('tile-scm')).toBeInTheDocument();
  });

  it('initialises the filter from the active tenant', () => {
    render(<CatalogGrid products={PRODUCTS} activeTenant="globex" />);
    // globex ∈ wms, scm — NOT finance(acme)
    expect(screen.getByTestId('tile-wms')).toBeInTheDocument();
    expect(screen.getByTestId('tile-scm')).toBeInTheDocument();
    expect(screen.queryByTestId('tile-finance')).toBeNull();
    expect(screen.getByTestId('catalog-tenant-filter')).toHaveTextContent('globex');
  });

  it('a filter matching no product shows the empty-filter note', () => {
    render(<CatalogGrid products={PRODUCTS} activeTenant="nobody" />);
    expect(screen.getByTestId('catalog-filter-empty')).toBeInTheDocument();
    expect(screen.queryByTestId('catalog-grid')).toBeNull();
  });
});
