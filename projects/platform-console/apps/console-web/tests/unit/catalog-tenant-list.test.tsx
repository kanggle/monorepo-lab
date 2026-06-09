import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ServiceTile } from '@/features/catalog/components/ServiceTile';
import type { RegistryProduct } from '@/shared/api/registry-types';

/**
 * TASK-PC-FE-063 — the catalog tile lists each available tenant one per line
 * under the "N개 테넌트 이용 가능" count. next/link is mocked to a plain anchor
 * so the tile renders without the app-router context.
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

describe('ServiceTile tenant list (TASK-PC-FE-063)', () => {
  it('lists each available tenant one per line under the count', () => {
    render(<ServiceTile product={product(['acme', 'globex'])} />);
    expect(screen.getByText('2개 테넌트 이용 가능')).toBeInTheDocument();
    const list = screen.getByTestId('tile-wms-tenants');
    expect(list.querySelectorAll('li')).toHaveLength(2);
    expect(screen.getByTestId('tile-wms-tenant-acme')).toHaveTextContent('acme');
    expect(screen.getByTestId('tile-wms-tenant-globex')).toHaveTextContent(
      'globex',
    );
  });

  it('a single tenant renders its one line', () => {
    render(<ServiceTile product={product(['acme'])} />);
    expect(screen.getByText('1개 테넌트 이용 가능')).toBeInTheDocument();
    expect(screen.getByTestId('tile-wms-tenants').querySelectorAll('li')).toHaveLength(1);
  });

  it('no tenants → "이용 가능", no tenant list', () => {
    render(<ServiceTile product={product([])} />);
    expect(screen.getByText('이용 가능')).toBeInTheDocument();
    expect(screen.queryByTestId('tile-wms-tenants')).toBeNull();
  });
});
