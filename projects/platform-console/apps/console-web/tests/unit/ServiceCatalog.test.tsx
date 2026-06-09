import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ServiceCatalog } from '@/features/catalog';
import type { CatalogState, RegistryProduct } from '@/shared/api/registry-types';

/**
 * The catalog grid is now interactive (TASK-PC-FE-064 — per-product health dot
 * + tenant filter / active-tenant select via `CatalogGrid` → `useTenantSwitch`),
 * so renders are wrapped with a QueryClientProvider and next/navigation's
 * useRouter is mocked. The data-driven assertions are unchanged.
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
  tenants: ['fan-platform', 'wms', 'scm'],
  baseRoute: '/iam',
};
const erp: RegistryProduct = {
  productKey: 'erp',
  displayName: 'Enterprise Resource Planning',
  available: false,
  tenants: [],
  baseRoute: '/erp',
};

describe('ServiceCatalog (data-driven)', () => {
  it('renders strictly from the registry response — tile per product', () => {
    renderCatalog({ products: [gap, erp], degraded: false });
    expect(screen.getByTestId('tile-iam')).toBeInTheDocument();
    expect(screen.getByTestId('tile-erp')).toBeInTheDocument();
    expect(screen.getByText('Global Account Platform')).toBeInTheDocument();
  });

  it('renders an available product as a navigable link to its console route', () => {
    // TASK-PC-FE-002: the IAM tile resolves to /accounts via resolveConsoleRoute
    // (the registry baseRoute is the logical prefix). The product header is the
    // link; tenants are separate buttons (TASK-PC-FE-064).
    renderCatalog({ products: [gap], degraded: false });
    const link = screen.getByRole('link', { name: /Global Account Platform/ });
    expect(link).toHaveAttribute('href', '/accounts');
  });

  it('renders available:false as a non-interactive "coming soon" tile', () => {
    renderCatalog({ products: [erp], degraded: false });
    expect(screen.getByText('Coming soon')).toBeInTheDocument();
    expect(
      screen.queryByRole('link', { name: /Enterprise Resource Planning/ }),
    ).not.toBeInTheDocument();
    expect(screen.getByTestId('tile-erp')).toHaveAttribute(
      'aria-disabled',
      'true',
    );
  });

  it('flipping available flips the tile with no code change (data-driven proof)', () => {
    const { rerender } = renderCatalog({
      products: [{ ...erp, available: false }],
      degraded: false,
    });
    expect(screen.getByText('Coming soon')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();

    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    rerender(
      <QueryClientProvider client={qc}>
        <ServiceCatalog
          catalog={{
            products: [
              { ...erp, available: true, displayName: 'ERP', baseRoute: '/erp' },
            ],
            degraded: false,
          }}
        />
      </QueryClientProvider>,
    );
    expect(screen.queryByText('Coming soon')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ERP/ })).toHaveAttribute(
      'href',
      '/erp',
    );
  });

  it('shows a degraded notice but still renders the shell (no blank crash)', () => {
    renderCatalog({ products: [], degraded: true });
    expect(screen.getByTestId('catalog-degraded')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '서비스' })).toBeInTheDocument();
  });

  it('renders an empty-state when registry is reachable but empty', () => {
    renderCatalog({ products: [], degraded: false });
    expect(screen.getByTestId('catalog-empty')).toBeInTheDocument();
  });
});
