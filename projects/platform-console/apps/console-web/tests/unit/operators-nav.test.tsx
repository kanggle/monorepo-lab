import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { OperatorsScreen } from '@/features/operators';
import type { OperatorPage } from '@/features/operators';

/**
 * Regression (TASK-PC-FE-004 AC): the `/operators` surface is an in-console
 * NAV destination — it must NOT change the catalog `gap.baseRoute`
 * contract (FE-002 stays: gap → /accounts). The operators screen mounts
 * server-side as an in-console destination.
 */

const gap: RegistryProduct = {
  productKey: 'gap',
  displayName: 'Global Account Platform',
  available: true,
  tenants: ['wms'],
  baseRoute: '/gap',
};

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}

const PAGE: OperatorPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

describe('operators nav — does not disturb the catalog gap.baseRoute (FE-002)', () => {
  it('gap still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('the operators screen mounts as an in-console destination', () => {
    render(<OperatorsScreen initial={PAGE} tenantOptions={['wms']} />, {
      wrapper: wrapper(),
    });
    expect(
      screen.getByRole('heading', { name: '운영자 관리' }),
    ).toBeInTheDocument();
  });
});
