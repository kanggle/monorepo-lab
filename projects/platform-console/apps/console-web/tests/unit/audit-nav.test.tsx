import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { resolveConsoleRoute } from '@/features/catalog';
import type { RegistryProduct } from '@/shared/api/registry-types';
import { AuditScreen } from '@/features/audit';
import type { AuditPage } from '@/features/audit';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * Regression (TASK-PC-FE-003 AC): the `/audit` surface is an in-console
 * NAV destination — it must NOT change the catalog `gap.baseRoute`
 * contract (FE-002 stays: gap → /accounts). The audit screen must mount
 * without any FE-002 mutation scaffolding (read-only).
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

const PAGE: AuditPage = {
  content: [],
  page: 0,
  size: 20,
  totalElements: 0,
  totalPages: 0,
};

describe('audit nav — does not disturb the catalog gap.baseRoute (FE-002)', () => {
  it('gap still resolves to /accounts (FE-002 contract unchanged)', () => {
    expect(resolveConsoleRoute(gap)).toBe('/accounts');
  });

  it('the audit screen mounts as an in-console destination (read-only, no confirm dialog)', () => {
    render(<AuditScreen initial={PAGE} />, { wrapper: wrapper() });
    expect(
      screen.getByRole('heading', { name: '감사 · 보안 조회' }),
    ).toBeInTheDocument();
    expect(screen.queryByTestId('confirm-dialog')).not.toBeInTheDocument();
  });
});
