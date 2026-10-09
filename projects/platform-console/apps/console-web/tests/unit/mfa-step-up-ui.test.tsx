import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, waitFor, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { TenantSwitcher } from '@/features/tenant';
import { apiFetch } from '@/shared/api/client';
import { ApiError } from '@/shared/api/errors';

/**
 * TASK-MONO-771 S3 — the browser half of the step-up:
 *   - the switcher offers «2단계 인증 후 들어가기» on `403 MFA_REQUIRED` (§ 2.7),
 *     and keeps «전환 실패» for every other refusal;
 *   - the API client, after a `401`, navigates to the step-up route when
 *     `POST /api/auth/refresh` answers `403 MFA_REQUIRED` (§ 2.6.1), and to
 *     `/login` otherwise.
 */

const { refreshMock } = vi.hoisted(() => ({ refreshMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  useRouter: () => ({ refresh: refreshMock, push: vi.fn(), replace: vi.fn() }),
}));

const assignMock = vi.fn();
let originalLocation: Location;

function stubLocation(pathname: string, search = '') {
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: { assign: assignMock, pathname, search },
  });
}

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function wrap(ui: React.ReactElement) {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

beforeEach(() => {
  originalLocation = window.location;
  assignMock.mockReset();
  refreshMock.mockReset();
  vi.unstubAllGlobals();
});
afterEach(() => {
  cleanup();
  Object.defineProperty(window, 'location', {
    configurable: true,
    writable: true,
    value: originalLocation,
  });
});

describe('TenantSwitcher — step-up offer (§ 2.7)', () => {
  it('🔴 a 403 MFA_REQUIRED switch shows the offer; clicking it goes to /api/auth/step-up?redirect=<current path>', async () => {
    stubLocation('/wms/inventory', '?page=2');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(json({ code: 'MFA_REQUIRED', message: 'x' }, 403)),
    );
    wrap(<TenantSwitcher tenants={['acme-corp', 'globex-corp']} activeTenant="acme-corp" />);

    await userEvent.selectOptions(screen.getByTestId('tenant-select'), 'globex-corp');
    const offer = await screen.findByTestId('tenant-step-up');
    expect(offer).toHaveTextContent('2단계 인증 후 들어가기');
    expect(screen.queryByText('전환 실패')).toBeNull();
    // A refused switch never refreshes the view.
    expect(refreshMock).not.toHaveBeenCalled();

    await userEvent.click(offer);
    expect(assignMock).toHaveBeenCalledWith(
      `/api/auth/step-up?redirect=${encodeURIComponent('/wms/inventory?page=2')}`,
    );
  });

  it('🔵 control — TENANT_FORBIDDEN keeps «전환 실패» and offers no step-up', async () => {
    stubLocation('/wms');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(json({ code: 'TENANT_FORBIDDEN', message: 'no' }, 403)),
    );
    wrap(<TenantSwitcher tenants={['acme-corp', 'globex-corp']} activeTenant="acme-corp" />);

    await userEvent.selectOptions(screen.getByTestId('tenant-select'), 'globex-corp');
    expect(await screen.findByRole('alert')).toHaveTextContent('전환 실패');
    expect(screen.queryByTestId('tenant-step-up')).toBeNull();
  });
});

describe('apiFetch — refresh answered 403 MFA_REQUIRED (§ 2.6.1)', () => {
  it('🔴 navigates to the step-up route (not /login) and throws MFA_REQUIRED', async () => {
    stubLocation('/iam/accounts', '?q=a');
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        Promise.resolve(
          String(url) === '/api/auth/refresh'
            ? json({ code: 'MFA_REQUIRED', message: 'second factor required' }, 403)
            : json({ code: 'TOKEN_INVALID' }, 401),
        ),
      ),
    );

    const err = await apiFetch('/api/accounts').catch((e: unknown) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).code).toBe('MFA_REQUIRED');
    await waitFor(() =>
      expect(assignMock).toHaveBeenCalledWith(
        `/api/auth/step-up?redirect=${encodeURIComponent('/iam/accounts?q=a')}`,
      ),
    );
    expect(assignMock).not.toHaveBeenCalledWith(expect.stringContaining('/login'));
  });

  it('🔵 control — a refresh 401 still goes to /login?redirect=…', async () => {
    stubLocation('/iam/accounts');
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(json({ code: 'TOKEN_INVALID' }, 401)),
    );
    const err = await apiFetch('/api/accounts').catch((e: unknown) => e);
    expect((err as ApiError).code).toBe('TOKEN_INVALID');
    expect(assignMock).toHaveBeenCalledWith(
      `/login?redirect=${encodeURIComponent('/iam/accounts')}`,
    );
  });

  it('🔵 control — a refresh 403 with another code is a plain failure (→ /login)', async () => {
    stubLocation('/iam/accounts');
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string) =>
        Promise.resolve(
          String(url) === '/api/auth/refresh'
            ? json({ code: 'FORBIDDEN' }, 403)
            : json({ code: 'TOKEN_INVALID' }, 401),
        ),
      ),
    );
    await apiFetch('/api/accounts').catch(() => undefined);
    expect(assignMock).toHaveBeenCalledWith(
      `/login?redirect=${encodeURIComponent('/iam/accounts')}`,
    );
  });
});
