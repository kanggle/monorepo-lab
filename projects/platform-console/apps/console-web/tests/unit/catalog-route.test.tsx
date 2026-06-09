import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ServiceCatalog, resolveConsoleRoute } from '@/features/catalog';
import type { CatalogState, RegistryProduct } from '@/shared/api/registry-types';

/**
 * Regression (TASK-PC-FE-002 AC): the catalog `gap` tile must route to the
 * accounts operator surface — `iam.baseRoute` resolves to `/accounts`.
 * Other products keep their registry `baseRoute` unchanged (data-driven,
 * additive — no FE-001/FE-002a regression).
 *
 * The grid is interactive (TASK-PC-FE-064 — `CatalogGrid` → `useTenantSwitch`),
 * so tile renders are wrapped with a QueryClientProvider + mocked router/link.
 */
vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: vi.fn(), push: vi.fn(), replace: vi.fn() }),
}));
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

function renderCatalog(catalog: CatalogState) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <ServiceCatalog catalog={catalog} />
    </QueryClientProvider>,
  );
}
const gap: RegistryProduct = {
  productKey: 'iam',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['wms'],
  baseRoute: '/iam',
};
const wms: RegistryProduct = {
  productKey: 'wms',
  displayName: 'WMS',
  available: true,
  tenants: ['wms'],
  baseRoute: '/wms',
};

describe('catalog → accounts route resolution', () => {
  it('resolveConsoleRoute maps iam to the accounts surface', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('resolveConsoleRoute leaves other products on their registry baseRoute', () => {
    expect(resolveConsoleRoute(wms)).toBe('/wms');
  });

  it('the IAM catalog tile links to /accounts (not /iam)', () => {
    renderCatalog({ products: [gap], degraded: false });
    const link = screen.getByRole('link', {
      name: /Global Account Platform/,
    });
    expect(link).toHaveAttribute('href', '/accounts');
  });

  it('a non-IAM tile is unaffected (FE-001 behaviour preserved)', () => {
    renderCatalog({ products: [wms], degraded: false });
    expect(screen.getByRole('link', { name: /WMS/ })).toHaveAttribute(
      'href',
      '/wms',
    );
  });
});
