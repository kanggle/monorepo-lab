import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup, fireEvent } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ServiceTile } from '@/features/catalog/components/ServiceTile';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-PC-FE-063/064 — the catalog tile lists each available tenant on its own
 * line as a BUTTON (clicking selects/filters via the grid), and shows a single
 * domain-health status dot in the product HEADER (not per tenant). next/link is
 * mocked to a plain anchor so the tile renders without the app-router context;
 * ServiceTile itself has no hooks (handler is passed in).
 */
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

afterEach(cleanup);

const product = (tenants: string[]): RegistryProduct => ({
  productKey: 'wms',
  displayName: 'WMS',
  available: true,
  tenants,
  baseRoute: '/wms',
});

describe('ServiceTile tenant buttons + header status dot', () => {
  it('lists each tenant as a button; clicking calls onSelectTenant', () => {
    const onSelect = vi.fn();
    render(
      <ServiceTile product={product(['acme', 'globex'])} onSelectTenant={onSelect} />,
    );
    expect(screen.getByText('2개 테넌트 이용 가능')).toBeInTheDocument();
    const acme = screen.getByTestId('tile-wms-tenant-acme');
    expect(acme.tagName).toBe('BUTTON');
    fireEvent.click(acme);
    expect(onSelect).toHaveBeenCalledWith('acme');
  });

  it('renders the product header status dot from `tone`', () => {
    render(<ServiceTile product={product(['acme'])} tone="attention" />);
    const dot = screen.getByTestId('tile-wms-status');
    expect(dot).toHaveAttribute('data-tone', 'attention');
  });

  it('no tone → no status dot (health degrades gracefully)', () => {
    render(<ServiceTile product={product(['acme'])} />);
    expect(screen.queryByTestId('tile-wms-status')).toBeNull();
  });

  it('no tenants → "이용 가능", no tenant list', () => {
    render(<ServiceTile product={product([])} />);
    expect(screen.getByText('이용 가능')).toBeInTheDocument();
    expect(screen.queryByTestId('tile-wms-tenants')).toBeNull();
  });
});
