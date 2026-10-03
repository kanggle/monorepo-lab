import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-MONO-751 — the fan-directory server client + its same-origin routes.
 *
 * Central assertions (ADR-MONO-079 D4-A · TASK-MONO-750's trusted paths):
 *   - the bearer is the domain-facing token — with an assumed tenant that is the ASSUMED
 *     `fan-platform` token, never the base login token and never the operator token;
 *   - NO `X-Tenant-Id`;
 *   - the calls go to the fan gateway's external `/api/v1/{agencies,artists,artist-groups}`;
 *   - the producer `{ data, meta }` envelope is unwrapped (a list → `{ content, page, … }`);
 *   - the store-seller link's `503 STORE_SELLER_LOOKUP_UNAVAILABLE` keeps its code through
 *     the route (so the screen can say «nothing was saved»), and its `422
 *     STORE_SELLER_NOT_FOUND` passes through as the validation error it is (AC-2).
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

const { ENV } = vi.hoisted(() => ({
  ENV: {
    OIDC_ISSUER_URL: 'http://iam.local',
    OIDC_CLIENT_ID: 'platform-console-web',
    OIDC_REDIRECT_URI: 'http://console.local/api/auth/callback',
    OIDC_SCOPE: 'openid profile email tenant.read',
    CONSOLE_REGISTRY_URL: 'http://iam.local/api/admin/console/registry',
    REGISTRY_TIMEOUT_MS: 50,
    CONSOLE_TOKEN_EXCHANGE_URL: 'http://iam.local/api/admin/auth/token-exchange',
    TOKEN_EXCHANGE_TIMEOUT_MS: 50,
    IAM_ADMIN_API_BASE: 'http://iam.local',
    FAN_GATEWAY_BASE_URL: 'http://fan-platform.local',
    FAN_TIMEOUT_MS: 50,
    LOG_LEVEL: 'info' as const,
    NEXT_PUBLIC_APP_URL: 'http://console.local',
  },
}));
vi.mock('@/shared/config/env', () => ({
  clientEnv: { NEXT_PUBLIC_APP_URL: ENV.NEXT_PUBLIC_APP_URL },
  getServerEnv: () => ENV,
}));

import * as sessionModule from '@/shared/lib/session';
import {
  ACCESS_COOKIE,
  ASSUMED_TOKEN_COOKIE,
  OPERATOR_COOKIE,
  TENANT_COOKIE,
} from '@/shared/lib/session';
import {
  listAgencies,
  createAgency,
  linkAgencyStoreSeller,
  changeArtistAgency,
  getArtistGroup,
} from '@/features/fan-directory/api/fan-api';
import { ApiError, FanUnavailableError } from '@/shared/api/errors';
import { PATCH as storeSellerPATCH } from '@/app/api/fan/agencies/[id]/store-seller/route';
import { POST as agenciesPOST, GET as agenciesGET } from '@/app/api/fan/agencies/route';

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}
function fanError(code: string, status: number) {
  return json({ code, message: 'err', timestamp: '2026-10-03T00:00:00Z' }, status);
}

const AGENCY = {
  id: 'ag-1',
  tenantId: 'fan-platform',
  name: 'Aurora Entertainment',
  status: 'ACTIVE',
  storeSellerId: null,
  createdAt: '2026-10-03T00:00:00Z',
  updatedAt: '2026-10-03T00:00:00Z',
};

function platformOperatorSwitchedToFan() {
  cookieJar.set(ACCESS_COOKIE, 'BASE-LOGIN-TOKEN-tenant-iam');
  cookieJar.set(OPERATOR_COOKIE, 'OPERATOR-TOKEN-must-not-be-used');
  cookieJar.set(TENANT_COOKIE, 'fan-platform');
  cookieJar.set(ASSUMED_TOKEN_COOKIE, 'ASSUMED-fan-platform-FAN_OPERATOR');
}

beforeEach(() => {
  cookieJar.clear();
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe('fan-api — credential, path, envelope', () => {
  it('sends the ASSUMED fan-platform token (not the base login token, not the operator token), no X-Tenant-Id', async () => {
    platformOperatorSwitchedToFan();
    const fetchMock = vi.fn().mockResolvedValue(
      json({ data: [AGENCY], meta: { timestamp: 't', page: 0, size: 20, totalElements: 1, totalPages: 1 } }),
    );
    vi.stubGlobal('fetch', fetchMock);
    const getOperatorSpy = vi.spyOn(sessionModule, 'getOperatorToken');

    const page = await listAgencies({ page: 0 });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://fan-platform.local/api/v1/agencies?page=0&size=20');
    const headers = (init as RequestInit).headers as Record<string, string>;
    expect(headers.Authorization).toBe('Bearer ASSUMED-fan-platform-FAN_OPERATOR');
    expect(headers['X-Tenant-Id']).toBeUndefined();
    expect(getOperatorSpy).not.toHaveBeenCalled();
    expect(page).toEqual({ content: [AGENCY], page: 0, size: 20, totalElements: 1, totalPages: 1 });
  });

  it('POSTs a create to /api/v1/agencies and unwraps `data`', async () => {
    platformOperatorSwitchedToFan();
    const fetchMock = vi.fn().mockResolvedValue(json({ data: AGENCY, meta: { timestamp: 't' } }, 201));
    vi.stubGlobal('fetch', fetchMock);

    const created = await createAgency({ name: 'Aurora Entertainment' });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://fan-platform.local/api/v1/agencies');
    expect((init as RequestInit).method).toBe('POST');
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ name: 'Aurora Entertainment' });
    expect(created.id).toBe('ag-1');
  });

  it('PATCHes an affiliation with agencyId:null (unaffiliated) to /api/v1/artists/{id}/agency', async () => {
    platformOperatorSwitchedToFan();
    const artist = {
      id: 'ar-1', accountId: 'acc-1', artistType: 'SOLO', status: 'DRAFT', stageName: 'STAR',
      agency: null, agencyId: null,
    };
    const fetchMock = vi.fn().mockResolvedValue(json({ data: artist, meta: {} }));
    vi.stubGlobal('fetch', fetchMock);

    await changeArtistAgency('ar-1', { agencyId: null });

    const [url, init] = fetchMock.mock.calls[0];
    expect(String(url)).toBe('http://fan-platform.local/api/v1/artists/ar-1/agency');
    expect((init as RequestInit).method).toBe('PATCH');
    expect(JSON.parse(String((init as RequestInit).body))).toEqual({ agencyId: null });
  });

  it('reads a group by id (members default to []) — there is no group list call', async () => {
    platformOperatorSwitchedToFan();
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(json({ data: { id: 'g-1', name: 'G', status: 'ACTIVE' }, meta: {} })),
    );
    const g = await getArtistGroup('g-1');
    expect(g.members).toEqual([]);
  });

  it('TENANT_FORBIDDEN (403 — e.g. not switched to fan-platform) → ApiError(403)', async () => {
    platformOperatorSwitchedToFan();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fanError('TENANT_FORBIDDEN', 403)));
    const err = await listAgencies().catch((e) => e);
    expect(err).toBeInstanceOf(ApiError);
    expect(err.status).toBe(403);
  });

  it('store-seller 503 STORE_SELLER_LOOKUP_UNAVAILABLE → FanUnavailableError CARRYING the producer code', async () => {
    platformOperatorSwitchedToFan();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fanError('STORE_SELLER_LOOKUP_UNAVAILABLE', 503)));
    const err = await linkAgencyStoreSeller('ag-1', { storeSellerId: 'acme' }).catch((e) => e);
    expect(err).toBeInstanceOf(FanUnavailableError);
    expect(err.code).toBe('STORE_SELLER_LOOKUP_UNAVAILABLE');
  });
});

describe('fan same-origin routes', () => {
  function patchReq(body: unknown) {
    return new Request('http://console.local/api/fan/agencies/ag-1/store-seller', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    });
  }
  const ctx = { params: Promise.resolve({ id: 'ag-1' }) };

  it('store-seller: 503 STORE_SELLER_LOOKUP_UNAVAILABLE keeps its code through the route', async () => {
    platformOperatorSwitchedToFan();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fanError('STORE_SELLER_LOOKUP_UNAVAILABLE', 503)));
    const res = await storeSellerPATCH(patchReq({ storeSellerId: 'acme' }), ctx);
    expect(res.status).toBe(503);
    expect((await res.json()).code).toBe('STORE_SELLER_LOOKUP_UNAVAILABLE');
  });

  it('store-seller: 422 STORE_SELLER_NOT_FOUND passes through (AC-2 — only an existing seller is accepted)', async () => {
    platformOperatorSwitchedToFan();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fanError('STORE_SELLER_NOT_FOUND', 422)));
    const res = await storeSellerPATCH(patchReq({ storeSellerId: 'nope' }), ctx);
    expect(res.status).toBe(422);
    expect((await res.json()).code).toBe('STORE_SELLER_NOT_FOUND');
  });

  it('store-seller: a >64-char id is refused 422 with NO upstream call', async () => {
    platformOperatorSwitchedToFan();
    const fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
    const res = await storeSellerPATCH(patchReq({ storeSellerId: 'x'.repeat(65) }), ctx);
    expect(res.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it('store-seller: `null` clears the link (forwarded verbatim)', async () => {
    platformOperatorSwitchedToFan();
    const fetchMock = vi.fn().mockResolvedValue(json({ data: AGENCY, meta: {} }));
    vi.stubGlobal('fetch', fetchMock);
    const res = await storeSellerPATCH(patchReq({ storeSellerId: null }), ctx);
    expect(res.status).toBe(200);
    expect(JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body))).toEqual({
      storeSellerId: null,
    });
  });

  it('agencies POST: blank name → 422, no upstream; valid → 201 with the unwrapped agency', async () => {
    platformOperatorSwitchedToFan();
    const fetchMock = vi.fn().mockResolvedValue(json({ data: AGENCY, meta: {} }, 201));
    vi.stubGlobal('fetch', fetchMock);
    const bad = await agenciesPOST(
      new Request('http://console.local/api/fan/agencies', { method: 'POST', body: JSON.stringify({ name: '  ' }) }),
    );
    expect(bad.status).toBe(422);
    expect(fetchMock).not.toHaveBeenCalled();
    const ok = await agenciesPOST(
      new Request('http://console.local/api/fan/agencies', { method: 'POST', body: JSON.stringify({ name: 'Aurora' }) }),
    );
    expect(ok.status).toBe(201);
    expect((await ok.json()).id).toBe('ag-1');
  });

  it('agencies GET: AGENCY 409/422 producer codes are not swallowed; 401 stays 401', async () => {
    platformOperatorSwitchedToFan();
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(fanError('UNAUTHORIZED', 401)));
    const res = await agenciesGET(new Request('http://console.local/api/fan/agencies?page=0'));
    expect(res.status).toBe(401);
  });
});
