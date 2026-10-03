import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-751 AC-3 / Failure Scenario 1 — a customer operator's registry lists `fan` with
 * `tenants: []` (admin-service R3 filter), so the fan pages resolve «not eligible» and make
 * NO fan call; a platform operator's registry lists `fan-platform` → eligible.
 */
const getCatalog = vi.fn();
vi.mock('@/features/catalog', () => ({ getCatalog: () => getCatalog() }));
vi.mock('next/navigation', () => ({
  redirect: (to: string) => {
    throw new Error(`redirect:${to}`);
  },
}));
const listAgencies = vi.fn();
vi.mock('@/features/fan-directory/api/fan-api', () => ({
  listAgencies: (...a: unknown[]) => listAgencies(...a),
  getAgency: vi.fn(),
  listArtists: vi.fn(),
  getArtist: vi.fn(),
  getArtistGroup: vi.fn(),
}));

import { resolveFanEligibility } from '@/app/(console)/fan/_eligibility';
import { getAgenciesSectionState } from '@/features/fan-directory/api/fan-state';
import { ApiError } from '@/shared/api/errors';

const product = (tenants: string[]) => ({
  productKey: 'fan',
  displayName: 'Fan Platform',
  available: true,
  tenants,
  baseRoute: '/fan',
});

beforeEach(() => {
  getCatalog.mockReset();
  listAgencies.mockReset();
});

describe('fan eligibility', () => {
  it('customer operator (fan.tenants = []) → not eligible, and the section makes NO fan call', async () => {
    getCatalog.mockResolvedValue({ products: [product([])], degraded: false });
    const e = await resolveFanEligibility();
    expect(e).toEqual({ eligible: false, registryDegraded: false });
    const state = await getAgenciesSectionState(e.eligible);
    expect(state.notEligible).toBe(true);
    expect(listAgencies).not.toHaveBeenCalled();
  });

  it('platform operator (fan.tenants = [fan-platform]) → eligible', async () => {
    getCatalog.mockResolvedValue({ products: [product(['fan-platform'])], degraded: false });
    expect(await resolveFanEligibility()).toEqual({ eligible: true, registryDegraded: false });
  });

  it('a registry without the fan product (pre-751 producer) → not eligible', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: false });
    expect((await resolveFanEligibility()).eligible).toBe(false);
  });

  it('degraded registry → registryDegraded (cannot prove ineligibility)', async () => {
    getCatalog.mockResolvedValue({ products: [], degraded: true });
    expect(await resolveFanEligibility()).toEqual({ eligible: false, registryDegraded: true });
  });

  it('a 403 from the fan gateway (e.g. TENANT_FORBIDDEN) → forbidden note, not a crash', async () => {
    listAgencies.mockRejectedValue(new ApiError(403, 'TENANT_FORBIDDEN', 'not permitted'));
    const state = await getAgenciesSectionState(true);
    expect(state.forbidden).toBe(true);
    expect(state.data).toBeNull();
  });
});
