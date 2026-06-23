import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import type { ReactNode } from 'react';
import { ApiError } from '@/shared/api/errors';
import type { CatalogState } from '@/shared/api/registry-types';
import type {
  DomainHealth,
  DomainHealthState,
} from '@/features/domain-health';

/**
 * TASK-MONO-241 — the `/ecommerce` drill-in route (ADR-MONO-030 Step 4 facet
 * a-후속). Closes the "catalog ecommerce tile (`baseRoute=/ecommerce`) click →
 * 404" gap MONO-240 left open. v1 surfaces the ecommerce domain-health card +
 * a "준비중" note; STRICTLY READ-ONLY; degrade-safe like scm/wms.
 *
 * Asserts:
 *   - eligible → renders the ecommerce section + the health card + the
 *     "상세 운영 표면 준비중" note.
 *   - registry degraded → degraded note (never blocks on an unproven negative).
 *   - not eligible → "no ecommerce-scoped access" note (does not crash).
 *   - registry 401 → redirect('/login').
 *   - health bff unavailable → section still renders + health-unavailable note
 *     (degrade-safe; the section never blanks the shell).
 */

const { redirectMock } = vi.hoisted(() => ({ redirectMock: vi.fn() }));
vi.mock('next/navigation', () => ({
  redirect: (path: string) => {
    redirectMock(path);
    throw new Error(`REDIRECT:${path}`);
  },
}));
vi.mock('next/link', () => ({
  default: ({ children, href }: { children: ReactNode; href: string }) => (
    <a href={href}>{children}</a>
  ),
}));

const { getCatalogMock, getDomainHealthStateMock } = vi.hoisted(() => ({
  getCatalogMock: vi.fn(),
  getDomainHealthStateMock: vi.fn(),
}));
vi.mock('@/features/catalog', () => ({
  getCatalog: getCatalogMock,
}));
vi.mock('@/features/domain-health', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('@/features/domain-health')>();
  return {
    ...actual,
    getDomainHealthState: getDomainHealthStateMock,
  };
});

import EcommercePage from '@/app/(console)/ecommerce/page';

const ELIGIBLE_CATALOG: CatalogState = {
  degraded: false,
  products: [
    {
      productKey: 'ecommerce',
      displayName: 'E-Commerce',
      available: true,
      tenants: ['acme-corp'],
      baseRoute: '/ecommerce',
    },
  ],
};

const HEALTH: DomainHealth = {
  asOf: '2026-06-13T00:00:00Z',
  cards: [
    { domain: 'iam', status: 'ok', data: { status: 'UP' } },
    { domain: 'wms', status: 'ok', data: { status: 'UP' } },
    { domain: 'scm', status: 'ok', data: { status: 'UP' } },
    { domain: 'finance', status: 'ok', data: { status: 'UP' } },
    { domain: 'erp', status: 'ok', data: { status: 'UP' } },
    { domain: 'ecommerce', status: 'ok', data: { status: 'UP' } },
  ],
};

function healthState(over: Partial<DomainHealthState>): DomainHealthState {
  return {
    health: HEALTH,
    noTenant: false,
    unauthorized: false,
    bffUnavailable: false,
    ...over,
  };
}

beforeEach(() => {
  vi.clearAllMocks();
  cleanup();
});

async function renderPage() {
  render(await EcommercePage());
}

describe('EcommercePage (TASK-MONO-241 /ecommerce drill-in)', () => {
  it('eligible → renders the ecommerce section, the health card, and the 준비중 note', async () => {
    getCatalogMock.mockResolvedValue(ELIGIBLE_CATALOG);
    getDomainHealthStateMock.mockResolvedValue(healthState({}));

    await renderPage();

    expect(screen.getByTestId('ecommerce-section')).toBeInTheDocument();
    expect(
      screen.getByRole('heading', { name: 'E-Commerce 운영' }),
    ).toBeInTheDocument();
    // The 6th domain-health card renders inside the section.
    expect(
      screen.getByTestId('domain-health-card-ecommerce'),
    ).toBeInTheDocument();
    // The deferred-ops note.
    expect(
      screen.getByTestId('ecommerce-ops-coming-soon'),
    ).toBeInTheDocument();
  });

  it('registry degraded → degraded note (never blocks on an unproven negative)', async () => {
    getCatalogMock.mockResolvedValue({ products: [], degraded: true });
    // TASK-PC-FE-118 — health is now fired speculatively up-front (concurrently
    // with the catalog eligibility pre-flight) to remove the SSR waterfall, so
    // it MAY be called on this gated branch. The decisive assertion is that the
    // gated render OUTPUT is unaffected by the speculative call.
    getDomainHealthStateMock.mockResolvedValue(healthState({}));

    await renderPage();

    expect(screen.getByTestId('ecommerce-degraded')).toBeInTheDocument();
    // The speculative health result does not leak into the degraded render.
    expect(screen.queryByTestId('domain-health-card-ecommerce')).toBeNull();
  });

  it('not eligible (no ecommerce product) → not-eligible note, does not crash', async () => {
    getCatalogMock.mockResolvedValue({ products: [], degraded: false });

    await renderPage();

    expect(screen.getByTestId('ecommerce-not-eligible')).toBeInTheDocument();
  });

  it('registry 401 → redirect to /login (no partial authed state)', async () => {
    getCatalogMock.mockRejectedValue(
      new ApiError(401, 'TOKEN_INVALID', 'expired'),
    );

    await expect(renderPage()).rejects.toThrow('REDIRECT:/login');
    expect(redirectMock).toHaveBeenCalledWith('/login');
  });

  it('health bff unavailable → section renders + health-unavailable note (degrade-safe)', async () => {
    getCatalogMock.mockResolvedValue(ELIGIBLE_CATALOG);
    getDomainHealthStateMock.mockResolvedValue(
      healthState({ health: null, bffUnavailable: true }),
    );

    await renderPage();

    expect(screen.getByTestId('ecommerce-section')).toBeInTheDocument();
    expect(
      screen.getByTestId('ecommerce-health-unavailable'),
    ).toBeInTheDocument();
    // The shell + 준비중 note still render — degrade never blanks the section.
    expect(
      screen.getByTestId('ecommerce-ops-coming-soon'),
    ).toBeInTheDocument();
  });
});
