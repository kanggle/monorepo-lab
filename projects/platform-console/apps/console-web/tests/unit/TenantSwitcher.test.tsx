import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantSwitcher } from '@/features/tenant';

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
  });
});
