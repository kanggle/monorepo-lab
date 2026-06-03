import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantSwitcher } from '@/features/tenant';

// TASK-PC-FE-040: the switch now calls router.refresh() on success to re-run
// the current route's server components with the re-scoped (assumed) token, so
// the viewed page re-applies the new tenant's entitlement gate in place. Mock
// next/navigation's useRouter (no App Router context in jsdom) + spy refresh.
const { refreshMock } = vi.hoisted(() => ({ refreshMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: refreshMock, push: vi.fn(), replace: vi.fn() }),
}));

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>{ui}</QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.unstubAllGlobals();
  refreshMock.mockClear();
});

describe('TenantSwitcher (multi-tenant)', () => {
  it('renders nothing for a zero-tenant operator (graceful degrade)', () => {
    const { container } = wrap(
      <TenantSwitcher tenants={[]} activeTenant={null} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it('renders a static read-only label for a single-tenant operator (no-op)', () => {
    wrap(<TenantSwitcher tenants={['wms']} activeTenant="wms" />);
    expect(screen.getByTestId('tenant-single')).toHaveTextContent('wms');
    expect(screen.queryByTestId('tenant-select')).not.toBeInTheDocument();
  });

  it('renders an UNSELECTED placeholder when no active tenant (no silent default to tenants[0]) — TASK-PC-FE-036', () => {
    wrap(
      <TenantSwitcher
        tenants={['acme-corp', 'globex-corp']}
        activeTenant={null}
      />,
    );
    const select = screen.getByTestId('tenant-select') as HTMLSelectElement;
    // The bug: defaulting to tenants[0] showed a tenant as "selected" while the
    // server had no active-tenant cookie → overviews gated. Honest fix = the
    // select sits on the empty placeholder, NOT on the first tenant.
    expect(select.value).toBe('');
    expect(select.value).not.toBe('acme-corp');
    expect(screen.getByRole('option', { name: '테넌트 선택…' })).toBeDisabled();
  });

  it('selects the active tenant when one is set (no placeholder)', () => {
    wrap(
      <TenantSwitcher
        tenants={['acme-corp', 'globex-corp']}
        activeTenant="globex-corp"
      />,
    );
    const select = screen.getByTestId('tenant-select') as HTMLSelectElement;
    expect(select.value).toBe('globex-corp');
    expect(
      screen.queryByRole('option', { name: '테넌트 선택…' }),
    ).not.toBeInTheDocument();
  });

  it('switches the active tenant via the /api/tenant route on change', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ ok: true, activeTenant: 'scm' }), {
        status: 200,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    vi.stubGlobal('fetch', fetchMock);

    wrap(
      <TenantSwitcher
        tenants={['fan-platform', 'scm', 'wms']}
        activeTenant="fan-platform"
      />,
    );
    await userEvent.selectOptions(
      screen.getByTestId('tenant-select'),
      'scm',
    );

    await waitFor(() => expect(fetchMock).toHaveBeenCalled());
    const [url, init] = fetchMock.mock.calls[0];
    expect(url).toBe('/api/tenant');
    expect((init as RequestInit).method).toBe('POST');
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      tenant: 'scm',
    });

    // TASK-PC-FE-040: on a successful switch the current route's server
    // components are refreshed so the viewed page re-applies the new tenant's
    // entitlement gate in place.
    await waitFor(() => expect(refreshMock).toHaveBeenCalled());
  });

  it('surfaces an error when the switch is rejected (cross-tenant 403)', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({ code: 'TENANT_FORBIDDEN', message: 'no' }),
        { status: 403, headers: { 'Content-Type': 'application/json' } },
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    wrap(
      <TenantSwitcher tenants={['wms', 'scm']} activeTenant="wms" />,
    );
    await userEvent.selectOptions(screen.getByTestId('tenant-select'), 'scm');

    expect(await screen.findByRole('alert')).toHaveTextContent('전환 실패');
    // A rejected switch must NOT refresh the view (no re-apply on a failed
    // assume-tenant — fail-closed; the operator stays on the prior tenant).
    expect(refreshMock).not.toHaveBeenCalled();
  });
});
