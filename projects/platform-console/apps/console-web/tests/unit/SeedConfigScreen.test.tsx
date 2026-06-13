import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { SeedConfigScreen } from '@/features/scm-config';
import { runAxe } from '../a11y/axe-helper';

vi.mock('next/navigation', () => ({
  usePathname: () => '/scm/config',
}));

/**
 * `features/scm-config` component behaviour (TASK-PC-FE-080 — SKU-code-driven
 * inspect + confirm-gated upsert):
 *   - SKU lookup gates both forms (no fetch until a SKU is looked up — no list);
 *   - GET 404 → "not configured yet → create" state (NOT an error toast);
 *   - confirm-gated PUT (no one-click), full-row body, success invalidates read;
 *   - VALIDATION_ERROR (422) → inline; entered values preserved;
 *   - the config-invariant ("affects FUTURE evaluation only") is surfaced;
 *   - the screen issues ONLY policies/sku-supplier-map GET/PUT (no
 *     suggestion/PO/dispatch call);
 *   - WCAG AA axe-clean.
 *
 * Client calls the same-origin `/api/scm/demand-planning/**` proxy via `fetch`
 * (mocked, routed by URL).
 */

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
function errResponse(code: string, status: number) {
  return new Response(JSON.stringify({ code, message: 'e' }), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** A fetch router keyed by URL substring; both reads default to not-configured
 *  unless overridden. Records every call for the "no suggestion/PO call"
 *  assertion. */
function routedFetch(
  overrides: {
    policy?: () => Response;
    map?: () => Response;
    policyPut?: () => Response;
    mapPut?: () => Response;
  } = {},
) {
  const calls: { url: string; method: string; body?: string }[] = [];
  const fn = vi.fn((url: string, init?: RequestInit) => {
    const u = String(url);
    const method = (init?.method ?? 'GET').toUpperCase();
    calls.push({
      url: u,
      method,
      body: typeof init?.body === 'string' ? init.body : undefined,
    });
    if (u.includes('/policies/')) {
      if (method === 'PUT') {
        return Promise.resolve(
          overrides.policyPut
            ? overrides.policyPut()
            : jsonResponse({ reorderPoint: 10, safetyStock: 5, reorderQty: 100 }),
        );
      }
      return Promise.resolve(
        overrides.policy
          ? overrides.policy()
          : jsonResponse({ found: false }),
      );
    }
    if (u.includes('/sku-supplier-map/')) {
      if (method === 'PUT') {
        return Promise.resolve(
          overrides.mapPut
            ? overrides.mapPut()
            : jsonResponse({
                supplierId: 'sup-1',
                defaultOrderQty: 100,
                leadTimeDays: 7,
                currency: 'KRW',
              }),
        );
      }
      return Promise.resolve(
        overrides.map ? overrides.map() : jsonResponse({ found: false }),
      );
    }
    return Promise.resolve(jsonResponse({}));
  });
  return { fn, calls };
}

beforeEach(() => {
  vi.unstubAllGlobals();
});

async function lookupSku(sku = 'SKU-APPLE-001') {
  const user = userEvent.setup();
  await user.type(screen.getByTestId('cfg-sku-input'), sku);
  await user.click(screen.getByTestId('cfg-sku-lookup'));
  return user;
}

describe('SeedConfigScreen — SKU lookup gating + config-invariant surfaced', () => {
  it('makes NO fetch until a SKU is looked up (no list route)', () => {
    const { fn } = routedFetch();
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    expect(screen.getByTestId('cfg-no-sku')).toBeInTheDocument();
    expect(fn).not.toHaveBeenCalled();
  });

  it('surfaces the "affects FUTURE evaluation only" affordance', () => {
    const { fn } = routedFetch();
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    expect(screen.getByTestId('cfg-future-only-note')).toHaveTextContent(
      /이후\(미래\) 보충 추천 평가에만/,
    );
    // The link back to the 보충 screen is present (closes the FE-077 gap).
    expect(screen.getByTestId('cfg-replenishment-link')).toHaveAttribute(
      'href',
      '/scm/replenishment',
    );
  });
});

describe('SeedConfigScreen — 404-as-empty-state', () => {
  it('a not-configured SKU renders the "not configured yet → create" state for BOTH forms (not an error)', async () => {
    const { fn } = routedFetch();
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    await lookupSku();

    await waitFor(() =>
      expect(screen.getByTestId('policy-not-configured')).toBeInTheDocument(),
    );
    expect(screen.getByTestId('map-not-configured')).toBeInTheDocument();
    // The create-mode buttons read "생성" not "저장".
    expect(screen.getByTestId('policy-save')).toHaveTextContent('생성');
    expect(screen.getByTestId('map-save')).toHaveTextContent('생성');
  });

  it('a configured SKU hydrates the policy form from the fetched row', async () => {
    const { fn } = routedFetch({
      policy: () =>
        jsonResponse({
          found: true,
          value: { reorderPoint: 30, safetyStock: 12, reorderQty: 250 },
        }),
    });
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    await lookupSku();

    await waitFor(() =>
      expect(screen.getByTestId('policy-reorderPoint')).toHaveValue(30),
    );
    expect(screen.getByTestId('policy-reorderQty')).toHaveValue(250);
    expect(screen.getByTestId('policy-save')).toHaveTextContent('저장');
  });
});

describe('SeedConfigScreen — confirm-gated upsert + full-row body + invalidation', () => {
  it('policy upsert is confirm-gated; on confirm PUTs the FULL-row body + NO invented headers + invalidates', async () => {
    const { fn, calls } = routedFetch();
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    const user = await lookupSku();

    await waitFor(() =>
      expect(screen.getByTestId('policy-not-configured')).toBeInTheDocument(),
    );

    await user.type(screen.getByTestId('policy-reorderPoint'), '10');
    await user.type(screen.getByTestId('policy-safetyStock'), '5');
    await user.type(screen.getByTestId('policy-reorderQty'), '100');
    await user.click(screen.getByTestId('policy-save'));

    // CONFIRM step required — the PUT has NOT fired yet.
    expect(screen.getByTestId('config-confirm-dialog')).toBeInTheDocument();
    expect(calls.some((c) => c.method === 'PUT')).toBe(false);

    await user.click(screen.getByTestId('config-confirm-submit'));

    await waitFor(() => {
      const put = calls.find(
        (c) => c.method === 'PUT' && c.url.includes('/policies/'),
      );
      expect(put).toBeTruthy();
      expect(JSON.parse(put!.body!)).toEqual({
        reorderPoint: 10,
        safetyStock: 5,
        reorderQty: 100,
      });
    });

    // The PUT triggers a re-GET (invalidation) of THIS sku's policy.
    await waitFor(() => {
      const policyGets = calls.filter(
        (c) => c.method === 'GET' && c.url.includes('/policies/'),
      );
      expect(policyGets.length).toBeGreaterThanOrEqual(2);
    });
  });

  it('the upsert request carries NO Idempotency-Key and NO X-Operator-Reason header', async () => {
    const headerSpy: Record<string, string>[] = [];
    const fn = vi.fn((url: string, init?: RequestInit) => {
      const u = String(url);
      if ((init?.method ?? 'GET').toUpperCase() === 'PUT') {
        // apiClient uses a Headers object; capture it.
        const h = init!.headers as Headers;
        const obj: Record<string, string> = {};
        h.forEach((v, k) => (obj[k] = v));
        headerSpy.push(obj);
        return Promise.resolve(
          jsonResponse({ reorderPoint: 10, safetyStock: 5, reorderQty: 100 }),
        );
      }
      return Promise.resolve(jsonResponse({ found: false }));
    });
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    const user = await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('policy-not-configured')).toBeInTheDocument(),
    );
    await user.type(screen.getByTestId('policy-reorderPoint'), '10');
    await user.type(screen.getByTestId('policy-safetyStock'), '5');
    await user.type(screen.getByTestId('policy-reorderQty'), '100');
    await user.click(screen.getByTestId('policy-save'));
    await user.click(screen.getByTestId('config-confirm-submit'));

    await waitFor(() => expect(headerSpy.length).toBeGreaterThan(0));
    for (const h of headerSpy) {
      expect(h['idempotency-key']).toBeUndefined();
      expect(h['x-operator-reason']).toBeUndefined();
    }
  });
});

describe('SeedConfigScreen — validation inline + values preserved', () => {
  it('a negative qty does NOT open the confirm dialog and shows an inline field error (no PUT)', async () => {
    const { fn, calls } = routedFetch();
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    const user = await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('policy-not-configured')).toBeInTheDocument(),
    );

    await user.type(screen.getByTestId('policy-reorderPoint'), '5');
    await user.type(screen.getByTestId('policy-safetyStock'), '2');
    await user.type(screen.getByTestId('policy-reorderQty'), '0'); // positive() fails
    await user.click(screen.getByTestId('policy-save'));

    expect(screen.queryByTestId('config-confirm-dialog')).toBeNull();
    expect(screen.getByTestId('policy-reorderQty-error')).toBeInTheDocument();
    // Entered values preserved.
    expect(screen.getByTestId('policy-reorderPoint')).toHaveValue(5);
    expect(calls.some((c) => c.method === 'PUT')).toBe(false);
  });

  it('a producer VALIDATION_ERROR (422) on PUT → inline submit error; entered values preserved', async () => {
    const { fn } = routedFetch({
      policyPut: () => errResponse('VALIDATION_ERROR', 422),
    });
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    const user = await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('policy-not-configured')).toBeInTheDocument(),
    );
    await user.type(screen.getByTestId('policy-reorderPoint'), '10');
    await user.type(screen.getByTestId('policy-safetyStock'), '5');
    await user.type(screen.getByTestId('policy-reorderQty'), '100');
    await user.click(screen.getByTestId('policy-save'));
    await user.click(screen.getByTestId('config-confirm-submit'));

    await waitFor(() =>
      expect(screen.getByTestId('policy-submit-error')).toBeInTheDocument(),
    );
    // Entered values preserved after the failed submit.
    expect(screen.getByTestId('policy-reorderQty')).toHaveValue(100);
  });
});

describe('SeedConfigScreen — config invariant: only seed GET/PUT, no suggestion/PO/dispatch', () => {
  it('across lookup + both upserts, ONLY policies/sku-supplier-map routes are called', async () => {
    const { fn, calls } = routedFetch();
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    const user = await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('map-not-configured')).toBeInTheDocument(),
    );

    await user.type(screen.getByTestId('map-supplierId'), 'sup-1');
    await user.type(screen.getByTestId('map-defaultOrderQty'), '100');
    await user.type(screen.getByTestId('map-leadTimeDays'), '7');
    await user.click(screen.getByTestId('map-save'));
    await user.click(screen.getByTestId('config-confirm-submit'));

    await waitFor(() =>
      expect(calls.some((c) => c.method === 'PUT')).toBe(true),
    );
    for (const c of calls) {
      expect(c.url).toMatch(/\/(policies|sku-supplier-map)\//);
      expect(c.url).not.toMatch(
        /\/suggestions|\/approve|\/dismiss|\/submit|\/confirm|\/cancel|\/dispatch/,
      );
    }
  });
});

describe('SeedConfigScreen — resilience surfaces', () => {
  it('a 403 on the policy read renders an inline "not scoped" state (section degrades, no crash)', async () => {
    const { fn } = routedFetch({
      policy: () => errResponse('TENANT_FORBIDDEN', 403),
    });
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('policy-forbidden')).toBeInTheDocument(),
    );
  });

  it('a 503 on the supplier-map read degrades only that sub-section', async () => {
    const { fn } = routedFetch({
      map: () => errResponse('SERVICE_UNAVAILABLE', 503),
    });
    vi.stubGlobal('fetch', fn);
    render(<SeedConfigScreen />, { wrapper: wrapper() });
    await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('map-degraded')).toBeInTheDocument(),
    );
  });
});

describe('SeedConfigScreen — a11y', () => {
  it('the screen with both forms is axe-clean (WCAG AA)', async () => {
    const { fn } = routedFetch();
    vi.stubGlobal('fetch', fn);
    const { container } = render(<SeedConfigScreen />, { wrapper: wrapper() });
    await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('policy-not-configured')).toBeInTheDocument(),
    );
    expect(await runAxe(container)).toEqual([]);
  });

  it('the confirm dialog is axe-clean and Escape cancels it', async () => {
    const { fn } = routedFetch();
    vi.stubGlobal('fetch', fn);
    const { container } = render(<SeedConfigScreen />, { wrapper: wrapper() });
    const user = await lookupSku();
    await waitFor(() =>
      expect(screen.getByTestId('policy-not-configured')).toBeInTheDocument(),
    );
    await user.type(screen.getByTestId('policy-reorderPoint'), '10');
    await user.type(screen.getByTestId('policy-safetyStock'), '5');
    await user.type(screen.getByTestId('policy-reorderQty'), '100');
    await user.click(screen.getByTestId('policy-save'));

    expect(screen.getByTestId('config-confirm-dialog')).toBeInTheDocument();
    expect(await runAxe(container)).toEqual([]);

    await user.keyboard('{Escape}');
    await waitFor(() =>
      expect(screen.queryByTestId('config-confirm-dialog')).toBeNull(),
    );
  });
});
