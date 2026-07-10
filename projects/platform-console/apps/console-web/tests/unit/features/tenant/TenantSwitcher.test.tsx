import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantSwitcher } from '@/features/tenant';

/**
 * `TenantSwitcher` org-node grouping (TASK-PC-FE-237 / ADR-047). With
 * companies present the switcher renders display-only `<optgroup>` blocks; an
 * ungrouped tenant is still a selectable `<option>`. With no companies the
 * markup is the flat list (byte-identical degrade path).
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: vi.fn(), push: vi.fn(), replace: vi.fn() }),
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe('TenantSwitcher — org-node grouping', () => {
  it('renders <optgroup> company blocks and keeps ungrouped tenants selectable', () => {
    const { container } = wrap(
      <TenantSwitcher
        tenants={['acme-a', 'acme-b', 'solo', '*']}
        activeTenant="acme-a"
        companies={[
          { orgNodeId: 'c1', name: 'Acme', tenants: ['acme-a', 'acme-b'] },
        ]}
      />,
    );
    // Company optgroup present.
    expect(container.querySelector('optgroup[label="Acme"]')).not.toBeNull();
    // Ungrouped tenants live under a plain '그룹 없음' group and stay selectable
    // (an ungrouped tenant must never disappear — D7).
    expect(container.querySelector('optgroup[label="그룹 없음"]')).not.toBeNull();
    expect(screen.getByRole('option', { name: 'solo' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '*' })).toBeInTheDocument();
    // The grouped leaves are still options too.
    expect(screen.getByRole('option', { name: 'acme-a' })).toBeInTheDocument();
  });

  it('with no companies renders the flat list (degrade path — no optgroup)', () => {
    const { container } = wrap(
      <TenantSwitcher
        tenants={['acme-a', 'globex-b']}
        activeTenant="acme-a"
        companies={[]}
      />,
    );
    expect(container.querySelector('optgroup')).toBeNull();
    expect(screen.getByRole('option', { name: 'acme-a' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'globex-b' })).toBeInTheDocument();
  });
});
