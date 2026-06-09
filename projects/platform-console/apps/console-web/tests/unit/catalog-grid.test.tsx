import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import type { ReactNode } from 'react';
import { CatalogGrid } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-PC-FE-065/067 — the catalog ALWAYS shows the full product list (no tenant
 * filter); clicking a tenant sets it active (mutate) and, on success, performs a
 * HARD navigation to that product's domain ops (window.location.assign to
 * resolveConsoleRoute, NOT router.push — see CatalogGrid for why: the active
 * tenant is an httpOnly cookie only the server layout reads, and Next.js does
 * not re-render a shared layout on SPA navigation, so a full load is required to
 * refresh the top switcher). The switch hook is mocked; mutate invokes its
 * onSuccess so we can assert the navigation; window.location is stubbed.
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

const assignMock = vi.fn();
let originalLocation: Location;

beforeEach(() => {
  // mutate(tenant, { onSuccess }) → run onSuccess (the switch "lands").
  mutateMock.mockImplementation((_tenant: string, opts?: { onSuccess?: () => void }) =>
    opts?.onSuccess?.(),
  );
  // jsdom's window.location is read-only and assign() is a no-op; replace it
  // with a stub so the hard navigation is observable.
  originalLocation = window.location;
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { assign: assignMock },
  });
});
afterEach(() => {
  cleanup();
  mutateMock.mockReset();
  assignMock.mockReset();
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: originalLocation,
  });
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

  it('clicking a tenant sets it active and HARD-navigates to that product\'s route; the list does NOT shrink', () => {
    render(<CatalogGrid products={PRODUCTS} />);
    fireEvent.click(screen.getByTestId('tile-wms-tenant-acme'));

    expect(mutateMock).toHaveBeenCalledWith('acme', expect.objectContaining({ onSuccess: expect.any(Function) }));
    // Hard navigation (full load) — NOT SPA router.push — so the server layout
    // re-renders the top switcher against the new httpOnly active-tenant cookie.
    expect(assignMock).toHaveBeenCalledWith('/wms'); // resolveConsoleRoute(wms baseRoute)
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
