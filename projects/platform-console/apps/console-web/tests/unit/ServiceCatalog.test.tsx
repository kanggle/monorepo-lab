import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ServiceCatalog } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';

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
    render(
      <ServiceCatalog catalog={{ products: [gap, erp], degraded: false }} />,
    );
    expect(screen.getByTestId('tile-iam')).toBeInTheDocument();
    expect(screen.getByTestId('tile-erp')).toBeInTheDocument();
    expect(screen.getByText('Global Account Platform')).toBeInTheDocument();
  });

  it('renders an available product as a navigable link to its console route', () => {
    // TASK-PC-FE-002: the IAM tile resolves to the Phase-2 operator surface
    // (`/accounts`) via `resolveConsoleRoute` — the registry `baseRoute` is
    // the logical prefix; which screen it lands on is console-internal.
    // (Dedicated coverage of the mapping lives in catalog-route.test.tsx;
    // non-IAM products still use their registry `baseRoute` unchanged.)
    render(<ServiceCatalog catalog={{ products: [gap], degraded: false }} />);
    const link = screen.getByRole('link', { name: /Global Account Platform/ });
    expect(link).toHaveAttribute('href', '/accounts');
  });

  it('renders available:false as a non-interactive "coming soon" tile', () => {
    render(<ServiceCatalog catalog={{ products: [erp], degraded: false }} />);
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
    const { rerender } = render(
      <ServiceCatalog
        catalog={{ products: [{ ...erp, available: false }], degraded: false }}
      />,
    );
    expect(screen.getByText('Coming soon')).toBeInTheDocument();
    expect(screen.queryByRole('link')).not.toBeInTheDocument();

    rerender(
      <ServiceCatalog
        catalog={{
          products: [
            { ...erp, available: true, displayName: 'ERP', baseRoute: '/erp' },
          ],
          degraded: false,
        }}
      />,
    );
    expect(screen.queryByText('Coming soon')).not.toBeInTheDocument();
    expect(screen.getByRole('link', { name: /ERP/ })).toHaveAttribute(
      'href',
      '/erp',
    );
  });

  it('shows a degraded notice but still renders the shell (no blank crash)', () => {
    render(<ServiceCatalog catalog={{ products: [], degraded: true }} />);
    expect(screen.getByTestId('catalog-degraded')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '서비스' })).toBeInTheDocument();
  });

  it('renders an empty-state when registry is reachable but empty', () => {
    render(<ServiceCatalog catalog={{ products: [], degraded: false }} />);
    expect(screen.getByTestId('catalog-empty')).toBeInTheDocument();
  });
});
