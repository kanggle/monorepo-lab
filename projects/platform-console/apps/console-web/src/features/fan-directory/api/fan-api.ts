import { z } from 'zod';
import { FanUnavailableError } from '@/shared/api/errors';
import {
  callFlatEnvelopeGateway,
  type FlatEnvelopeGatewayProfile,
} from '@/shared/api/flat-envelope-gateway';
import { clampPageSize } from '@/shared/lib/pagination';
import {
  AgencySchema,
  ArtistSchema,
  ArtistGroupSchema,
  envelopeSchema,
  listEnvelopeSchema,
  FAN_DEFAULT_PAGE_SIZE,
  FAN_MAX_PAGE_SIZE,
  type Agency,
  type Artist,
  type ArtistGroup,
  type Paged,
  type FanListParams,
  type ArtistListParams,
  type CreateAgencyBody,
  type RenameAgencyBody,
  type LinkStoreSellerBody,
  type CreateArtistBody,
  type UpdateArtistBody,
  type ArtistStatusBody,
  type ChangeAffiliationBody,
  type CreateGroupBody,
} from './fan-types';

/**
 * Server-side fan-DIRECTORY client (TASK-MONO-751 — ADR-MONO-079 D4-A): agencies, artists
 * and artist groups in fan artist-service, reached through the fan-platform gateway
 * (`/api/v1/**` → `/api/**`).
 *
 * ── AUTH MODEL ──────────────────────────────────────────────────────────────
 *
 * The shared FLAT-envelope core attaches `getDomainFacingToken()` — for a platform operator
 * who switched to `fan-platform`, that is the ASSUMED token (`tenant_id=fan-platform`,
 * `roles=[FAN_OPERATOR]`, derived from the tenant's `fan` subscription — TASK-MONO-750).
 * artist-service admits it by `tenant_id` EQUALITY on the directory resources only. NEVER
 * the IAM operator token; NO `X-Tenant-Id`.
 *
 * 🔴 «전환 뒤에 묻는다» (ticket Failure Scenario 1): this client must not be reached before
 * the switch. The `(console)/fan` layout's `DomainTenantGate productKey="fan"` holds the
 * section until a tenant is assumed AND that tenant is one the `fan` product serves; the
 * base token (`tenant_id=iam`) would otherwise be refused `403 TENANT_FORBIDDEN`.
 *
 * ── SAMPLE VISITOR ──────────────────────────────────────────────────────────
 *
 * Every call goes through `callFlatEnvelopeGateway`, which asks `sampleGate(` before any
 * token read (ADR-MONO-074 A2). The `fan` surface is `pending` in the sample coverage
 * ledger → a sample visitor gets `503 SAMPLE_NOT_READY` → the section's degraded note. (A
 * sample visitor never reaches it in practice: the sample registry lists no `fan` tenant,
 * so the nav entry is hidden.)
 *
 * ── ERRORS ──────────────────────────────────────────────────────────────────
 *
 * FLAT envelope `{ code, message, details?, timestamp }` (artist-api § Error). 401 →
 * whole-session re-login; 403/404/409/422 → inline `ApiError`; 503 / timeout / network →
 * {@link FanUnavailableError} carrying the producer `code` — so the agency screen can tell
 * `STORE_SELLER_LOOKUP_UNAVAILABLE` (the link was refused, nothing saved) from «fan is down».
 */

const FAN_PROFILE: FlatEnvelopeGatewayProfile = {
  logPrefix: 'fan',
  requestFailedLabel: 'fan request failed',
  resolveDefaults: (env) => ({
    baseUrl: env.FAN_GATEWAY_BASE_URL,
    timeoutMs: env.FAN_TIMEOUT_MS,
  }),
  makeUnavailable: (reason, code, message) =>
    new FanUnavailableError(reason, code, message),
  isUnavailable: (err) => err instanceof FanUnavailableError,
  messages: {
    degraded: 'fan directory unavailable',
    timeout: 'fan directory call timed out',
    network: 'fan directory call failed',
  },
};

const clampSize = (size?: number): number =>
  clampPageSize(size, FAN_DEFAULT_PAGE_SIZE, FAN_MAX_PAGE_SIZE);

function pageQs(params: FanListParams): URLSearchParams {
  const qs = new URLSearchParams();
  qs.set('page', String(Math.max(0, params.page ?? 0)));
  qs.set('size', String(clampSize(params.size)));
  return qs;
}

async function call<T>(
  method: string,
  path: string,
  logPath: string,
  parse: (json: unknown) => T,
  body?: unknown,
): Promise<T> {
  const { raw } = await callFlatEnvelopeGateway(
    { method, path, logPath, body },
    parse,
    FAN_PROFILE,
  );
  return raw;
}

function one<T extends z.ZodTypeAny>(schema: T) {
  return (json: unknown): z.infer<T> => envelopeSchema(schema).parse(json).data;
}

function many<T extends z.ZodTypeAny>(schema: T) {
  return (json: unknown): Paged<z.infer<T>> => {
    const env = listEnvelopeSchema(schema).parse(json);
    return { content: env.data, ...env.meta };
  };
}

const id = (v: string) => encodeURIComponent(v);

// ===========================================================================
// AGENCIES (artist-api § Agencies)
// ===========================================================================

export function listAgencies(params: FanListParams = {}): Promise<Paged<Agency>> {
  return call('GET', `/api/v1/agencies?${pageQs(params)}`, '/api/v1/agencies', many(AgencySchema));
}

export function getAgency(agencyId: string): Promise<Agency> {
  return call('GET', `/api/v1/agencies/${id(agencyId)}`, '/api/v1/agencies/{id}', one(AgencySchema));
}

export function createAgency(body: CreateAgencyBody): Promise<Agency> {
  return call('POST', '/api/v1/agencies', '/api/v1/agencies', one(AgencySchema), body);
}

export function renameAgency(agencyId: string, body: RenameAgencyBody): Promise<Agency> {
  return call('PATCH', `/api/v1/agencies/${id(agencyId)}`, '/api/v1/agencies/{id}', one(AgencySchema), body);
}

export function archiveAgency(agencyId: string): Promise<Agency> {
  return call(
    'PATCH',
    `/api/v1/agencies/${id(agencyId)}/status`,
    '/api/v1/agencies/{id}/status',
    one(AgencySchema),
    { status: 'ARCHIVED' },
  );
}

export function linkAgencyStoreSeller(agencyId: string, body: LinkStoreSellerBody): Promise<Agency> {
  return call(
    'PATCH',
    `/api/v1/agencies/${id(agencyId)}/store-seller`,
    '/api/v1/agencies/{id}/store-seller',
    one(AgencySchema),
    body,
  );
}

// ===========================================================================
// ARTISTS (artist-api § Artists)
// ===========================================================================

/** Directory search — 🔴 the producer returns PUBLISHED artists only (a DRAFT is reached by id). */
export function listArtists(params: ArtistListParams = {}): Promise<Paged<Artist>> {
  const qs = pageQs(params);
  if (params.q && params.q.trim() !== '') qs.set('q', params.q.trim());
  return call('GET', `/api/v1/artists?${qs}`, '/api/v1/artists', many(ArtistSchema));
}

export function getArtist(artistId: string): Promise<Artist> {
  return call('GET', `/api/v1/artists/${id(artistId)}`, '/api/v1/artists/{id}', one(ArtistSchema));
}

export function createArtist(body: CreateArtistBody): Promise<Artist> {
  return call('POST', '/api/v1/artists', '/api/v1/artists', one(ArtistSchema), body);
}

export function updateArtist(artistId: string, body: UpdateArtistBody): Promise<Artist> {
  return call('PATCH', `/api/v1/artists/${id(artistId)}`, '/api/v1/artists/{id}', one(ArtistSchema), body);
}

export function changeArtistStatus(artistId: string, body: ArtistStatusBody): Promise<Artist> {
  return call(
    'PATCH',
    `/api/v1/artists/${id(artistId)}/status`,
    '/api/v1/artists/{id}/status',
    one(ArtistSchema),
    body,
  );
}

export function changeArtistAgency(artistId: string, body: ChangeAffiliationBody): Promise<Artist> {
  return call(
    'PATCH',
    `/api/v1/artists/${id(artistId)}/agency`,
    '/api/v1/artists/{id}/agency',
    one(ArtistSchema),
    body,
  );
}

// ===========================================================================
// ARTIST GROUPS (artist-api § Artist groups) — the producer has NO list endpoint;
// a group is reached by id (the create answer carries it).
// ===========================================================================

export function getArtistGroup(groupId: string): Promise<ArtistGroup> {
  return call(
    'GET',
    `/api/v1/artist-groups/${id(groupId)}`,
    '/api/v1/artist-groups/{id}',
    one(ArtistGroupSchema),
  );
}

export function createArtistGroup(body: CreateGroupBody): Promise<ArtistGroup> {
  return call('POST', '/api/v1/artist-groups', '/api/v1/artist-groups', one(ArtistGroupSchema), body);
}

export function changeArtistGroupAgency(
  groupId: string,
  body: ChangeAffiliationBody,
): Promise<ArtistGroup> {
  return call(
    'PATCH',
    `/api/v1/artist-groups/${id(groupId)}/agency`,
    '/api/v1/artist-groups/{id}/agency',
    one(ArtistGroupSchema),
    body,
  );
}
