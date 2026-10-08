import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';

/**
 * TASK-MONO-777 — «이메일로 찾기» in the HR link dialog (console contract § 2.4.8
 * «Account selection»; IAM `GET /api/admin/operators/lookup`).
 *   - AC-1  e-mail → pick ONE result → the account-id field is filled → propose
 *           POSTs that id (rendered DOM, real api-client over a mocked fetch);
 *   - AC-2  «no such e-mail» and «out of scope» render the SAME sentence in the
 *           SAME element — compared in ONE test;
 *   - AC-3  a 403 / 401 from the lookup never logs out: no `/api/auth/refresh`
 *           call, the dialog stays, typed input still works;
 *   - Edge  several hits show their tenant; an already-linked account →
 *           the producer's 409 `account_already_linked` copy.
 */

vi.mock('next/navigation', () => ({
  useRouter: () => ({ replace: vi.fn() }),
  usePathname: () => '/erp/masters',
  useSearchParams: () => new URLSearchParams(),
}));

import { EmployeeAccountLinkDialog } from '@/features/erp-ops';
import type { Employee } from '@/features/erp-ops';
import {
  ACCOUNT_LOOKUP_FAILED_MESSAGE,
  ACCOUNT_LOOKUP_NOT_FOUND_MESSAGE,
} from '@/features/erp-ops/components/account-link-error';

function wrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={qc}>{children}</QueryClientProvider>
  );
}
function jsonResponse(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function errorResponse(code: string, status: number, details?: unknown) {
  return new Response(
    JSON.stringify({ code, message: 'e', ...(details ? { details } : {}) }),
    { status, headers: { 'Content-Type': 'application/json' } },
  );
}

const EMPTY_PAGE = { data: [], meta: { page: 0, size: 20, totalElements: 0 } };
const EMPLOYEE: Employee = {
  id: 'emp-unlinked',
  employeeNumber: 'E-0002',
  name: '박미연결',
  status: 'ACTIVE',
  employmentStatus: 'EMPLOYED',
  effectivePeriod: { effectiveFrom: '2026-01-01', effectiveTo: null },
};
const DEMO_SUB = '0199de70-0000-7000-8000-00000000ad03';

type Route = (url: string, init?: RequestInit) => Response | undefined;

/** fetch mock: lookup answers come from `lookup(email)`; everything else is EMPTY_PAGE. */
function installFetch(lookup: (email: string) => Response, extra?: Route) {
  const fetchMock = vi.fn((input: string | URL, init?: RequestInit) => {
    const url = String(input);
    const custom = extra?.(url, init);
    if (custom) return Promise.resolve(custom);
    if (url.startsWith('/api/operators/lookup')) {
      const email = new URL(url, 'http://x').searchParams.get('email') ?? '';
      return Promise.resolve(lookup(email));
    }
    return Promise.resolve(jsonResponse(EMPTY_PAGE));
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

async function search(user: ReturnType<typeof userEvent.setup>, email: string) {
  const input = screen.getByTestId('erp-account-link-lookup-email');
  await user.clear(input);
  await user.type(input, email);
  await user.click(screen.getByTestId('erp-account-link-lookup-submit'));
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

describe('TASK-MONO-777 — «이메일로 찾기»', () => {
  it('AC-1 — e-mail → pick the result → the account-id field is filled → propose POSTs that id', async () => {
    const user = userEvent.setup();
    const fetchMock = installFetch(
      (email) =>
        email === 'demo@demo.com'
          ? jsonResponse({
              content: [{ accountId: DEMO_SUB, displayName: 'Demo Operator', tenantId: 'demo-corp' }],
            })
          : jsonResponse({ content: [] }),
      (url, init) =>
        init?.method === 'POST'
          ? jsonResponse({
              data: {
                id: 'prop-1',
                employeeId: EMPLOYEE.id,
                accountId: DEMO_SUB,
                status: 'PENDING',
                proposedBy: 'acc-hr',
                proposedAt: '2026-10-08T00:00:00Z',
              },
            })
          : undefined,
    );
    render(<EmployeeAccountLinkDialog employee={EMPLOYEE} onClose={() => {}} />, {
      wrapper: wrapper(),
    });

    await search(user, 'demo@demo.com');
    const hit = await screen.findByTestId('erp-account-link-lookup-result-0');
    expect(hit.textContent).toContain('Demo Operator');
    expect(hit.textContent).toContain('demo-corp 테넌트');
    expect(screen.getByTestId('erp-account-link-account-id')).toHaveValue('');

    await user.click(hit);
    expect(screen.getByTestId('erp-account-link-account-id')).toHaveValue(DEMO_SUB);
    expect(hit).toHaveAttribute('aria-pressed', 'true');

    await user.click(screen.getByTestId('erp-account-link-propose'));
    await screen.findByTestId('erp-account-link-propose-done');
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST');
    expect(String(post?.[0])).toBe(`/api/erp/masterdata/employees/${EMPLOYEE.id}/account-link-proposals`);
    expect(JSON.parse(String(post?.[1]?.body)).accountId).toBe(DEMO_SUB);
    // the lookup went through the same-origin proxy with the typed e-mail
    expect(
      fetchMock.mock.calls.some(([u]) => String(u) === '/api/operators/lookup?email=demo%40demo.com'),
    ).toBe(true);
  });

  it('🔴 AC-2 — «no such e-mail» and «out of scope» read the SAME sentence in the SAME element', async () => {
    const user = userEvent.setup();
    installFetch((email) => {
      if (email === 'nobody@demo.com') return jsonResponse({ content: [] }); // unknown e-mail
      if (email === 'outsider@other.example') return jsonResponse({ content: [] }); // producer: out of scope ⇒ same empty list
      if (email === 'leak@other.example') return errorResponse('TENANT_SCOPE_DENIED', 403); // defence in depth
      return jsonResponse({ content: [] });
    });
    render(<EmployeeAccountLinkDialog employee={EMPLOYEE} onClose={() => {}} />, {
      wrapper: wrapper(),
    });

    const seen: { html: string; text: string }[] = [];
    for (const email of ['nobody@demo.com', 'outsider@other.example', 'leak@other.example']) {
      await search(user, email);
      const msg = await screen.findByTestId('erp-account-link-lookup-message');
      await waitFor(() => expect(msg.textContent).toBe(ACCOUNT_LOOKUP_NOT_FOUND_MESSAGE));
      seen.push({ html: msg.outerHTML, text: msg.textContent ?? '' });
      expect(screen.queryByTestId('erp-account-link-lookup-results')).not.toBeInTheDocument();
    }
    expect(seen[1]).toEqual(seen[0]);
    expect(seen[2]).toEqual(seen[0]);
  });

  it('🔴 AC-3 — a 403 or 401 from the lookup never logs out (no refresh, dialog stays, typed input works)', async () => {
    const user = userEvent.setup();
    const fetchMock = installFetch((email) =>
      email === 'forbidden@demo.com'
        ? errorResponse('PERMISSION_DENIED', 403)
        : errorResponse('TOKEN_INVALID', 401),
    );
    render(<EmployeeAccountLinkDialog employee={EMPLOYEE} onClose={() => {}} />, {
      wrapper: wrapper(),
    });

    for (const email of ['forbidden@demo.com', 'expired@demo.com']) {
      await search(user, email);
      const msg = await screen.findByTestId('erp-account-link-lookup-message');
      await waitFor(() => expect(msg.textContent).toBe(ACCOUNT_LOOKUP_FAILED_MESSAGE));
    }
    // the client's logout path is refresh → /login; it never started
    expect(fetchMock.mock.calls.some(([u]) => String(u).includes('/api/auth/refresh'))).toBe(false);
    expect(screen.getByTestId('erp-account-link-dialog')).toBeInTheDocument();
    // typed fallback still usable
    await user.type(screen.getByTestId('erp-account-link-account-id'), 'acc-typed');
    expect(screen.getByTestId('erp-account-link-propose')).toBeEnabled();
  });

  it('Edge — several hits show their tenant; the platform tenant reads «플랫폼»', async () => {
    const user = userEvent.setup();
    installFetch(() =>
      jsonResponse({
        content: [
          { accountId: 'acc-home', displayName: '김인사', tenantId: 'demo-corp' },
          { accountId: 'acc-assigned', displayName: '김인사', tenantId: 'partner-co' },
          { accountId: 'acc-platform', displayName: null, tenantId: '*' },
        ],
      }),
    );
    render(<EmployeeAccountLinkDialog employee={EMPLOYEE} onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await search(user, 'kim@demo.com');
    await screen.findByTestId('erp-account-link-lookup-result-2');
    expect(screen.getByTestId('erp-account-link-lookup-result-0').textContent).toContain('demo-corp 테넌트');
    expect(screen.getByTestId('erp-account-link-lookup-result-1').textContent).toContain('partner-co 테넌트');
    expect(screen.getByTestId('erp-account-link-lookup-result-2').textContent).toContain('플랫폼 테넌트');
    await user.click(screen.getByTestId('erp-account-link-lookup-result-1'));
    expect(screen.getByTestId('erp-account-link-account-id')).toHaveValue('acc-assigned');
  });

  it('Edge — picking an already-linked account → the producer 409 account_already_linked copy', async () => {
    const user = userEvent.setup();
    installFetch(
      () => jsonResponse({ content: [{ accountId: 'acc-taken', displayName: '이연결', tenantId: 'demo-corp' }] }),
      (url, init) =>
        init?.method === 'POST'
          ? errorResponse('EMPLOYEE_LINK_CONFLICT', 409, { cause: 'account_already_linked' })
          : undefined,
    );
    render(<EmployeeAccountLinkDialog employee={EMPLOYEE} onClose={() => {}} />, {
      wrapper: wrapper(),
    });
    await search(user, 'taken@demo.com');
    await user.click(await screen.findByTestId('erp-account-link-lookup-result-0'));
    await user.click(screen.getByTestId('erp-account-link-propose'));
    expect((await screen.findByTestId('erp-account-link-propose-error')).textContent).toBe(
      '이 계정은 이미 이 회사의 다른 직원과 연결되어 있습니다.',
    );
  });
});
