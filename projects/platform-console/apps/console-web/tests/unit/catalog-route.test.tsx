import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { ServiceCatalog, resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * Regression (TASK-PC-FE-002 AC): the catalog `gap` tile must route to the
 * accounts operator surface — `iam.baseRoute` resolves to `/accounts`.
 * Other products keep their registry `baseRoute` unchanged (data-driven,
 * additive — no FE-001/FE-002a regression).
 */
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
    render(<ServiceCatalog catalog={{ products: [gap], degraded: false }} />);
    const link = screen.getByRole('link', {
      name: /Global Account Platform/,
    });
    expect(link).toHaveAttribute('href', '/accounts');
  });

  it('a non-IAM tile is unaffected (FE-001 behaviour preserved)', () => {
    render(<ServiceCatalog catalog={{ products: [wms], degraded: false }} />);
    expect(screen.getByRole('link', { name: /WMS/ })).toHaveAttribute(
      'href',
      '/wms',
    );
  });
});
