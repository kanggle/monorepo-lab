import type { cookies } from 'next/headers';
import { fetchRegistry } from '@/shared/api/registry-client';
import type { RegistryResponse } from '@/shared/api/registry-types';
import { exchangeForAssumedToken } from '@/shared/lib/assume-tenant-exchange';
import { readJwtClaim } from '@/shared/lib/jwt';
import { logger } from '@/shared/lib/logger';
import {
  ASSUMED_TOKEN_COOKIE,
  LAST_TENANT_COOKIE,
  TENANT_COOKIE,
  tokenCookieOpts,
} from '@/shared/lib/session';

/**
 * The active-tenant default at login and on idle refresh (TASK-PC-FE-292).
 *
 * 🔴🔴 The active tenant is NOT read from the IAM OIDC token. The console client's
 *    token carries `tenant_id` = the client's own operational slug (`iam` — not a
 *    customer tenant; `V0024` renamed it from `gap`), so the old default
 *    (TASK-PC-FE-036: «the token's tenant_id, minus `'*'`») set the active tenant
 *    to `iam` with NO assumed token, every domain call went out on the base token,
 *    the gateways answered 401 and the console said «세션 만료». This is also what
 *    `console-integration-contract.md` § 2.2 already said: *tenant scope is never
 *    derived from the IAM OIDC token*.
 *
 * The rule (owner decision 2026-09-16), over the tenants the registry says this
 * operator may select:
 *   ① the operator's last selection in this browser, if still selectable;
 *   ② else the only selectable tenant, if there is exactly one;
 *   ③ else none — the domain sections show «테넌트를 선택하세요»
 *      ({@link DomainTenantGate}).
 * The chosen tenant is always ASSUMED (the same RFC 8693 exchange `/api/tenant`
 * drives), so the tenant cookie never stands without its assumed token.
 *
 * Judged by relation («is it among the registry's choices?»), not by a list of
 * non-customer slugs — a list would break silently on the next rename.
 *
 * Callers: `/api/auth/callback` and `refreshSessionCookies` — one function, two
 * call sites, so login and the 30-minute idle refresh cannot drift apart.
 */

/** Tenants the operator may select = the distinct tenants of AVAILABLE
 *  products. The same set `/api/tenant` allow-checks a switch against. */
export function selectableTenants(registry: RegistryResponse): string[] {
  return [...new Set(registry.products.flatMap((p) => (p.available ? p.tenants : [])))];
}

/** ① remembered-and-selectable → ② the single selectable tenant → ③ null. */
export function chooseDefaultTenant(
  selectable: readonly string[],
  remembered: string | null,
): string | null {
  if (remembered && selectable.includes(remembered)) return remembered;
  return selectable.length === 1 ? selectable[0] : null;
}

export type NoTenantNoticeKind = 'zero' | 'select';

/**
 * TASK-PC-FE-301 — distinguishes "nothing to select" from "something to
 * select, none chosen", for a gate that already knows no tenant is currently
 * active/assumed (every gated screen's `noTenant` state / {@link
 * tenantSelectionRequired}).
 *
 * Owner decision ⓒ (2026-09-26 UTC): a 0-selectable operator (0 roles → 0
 * available products → 0 selectable tenants, the `viewer@demo.com` shape
 * measured live in the `TASK-MONO-730` AC-1 window) must be told they have no
 * reachable tenant, not "select one" — there is nothing to select.
 *
 * A registry failure is deliberately NOT read as zero (task Edge Case: 없음 ≠
 * 못 읽음) — any thrown/degraded result falls back to `'select'`, exactly
 * today's copy, so a registry blip never turns into a false "you have no
 * access" for every operator viewing a gated screen at that moment.
 */
export async function noTenantNoticeKind(): Promise<NoTenantNoticeKind> {
  try {
    const registry = await fetchRegistry();
    return selectableTenants(registry).length === 0 ? 'zero' : 'select';
  } catch {
    return 'select';
  }
}

/**
 * The last-tenant cookie value: `<operator sub>|<tenant>`. Keyed by the operator
 * so a selection made by one operator is never another operator's default in a
 * shared browser. Not a credential — a preference, re-validated against the
 * registry every time it is read.
 */
export function rememberTenantValue(sub: string, tenant: string): string {
  return `${sub}|${tenant}`;
}

function rememberedTenantFor(value: string | undefined, sub: string | null): string | null {
  if (!value || !sub) return null;
  const sep = value.lastIndexOf('|');
  if (sep <= 0 || value.slice(0, sep) !== sub) return null;
  return value.slice(sep + 1) || null;
}

/** The operator `sub` of a base IAM OIDC access token (decode only — see `jwt.ts`). */
export function operatorSubject(accessToken: string | null | undefined): string | null {
  if (!accessToken) return null;
  const sub = readJwtClaim(accessToken, 'sub');
  return typeof sub === 'string' && sub !== '' ? sub : null;
}

/** 30 days — the refresh-token lifetime; a preference outliving it has no session to apply to. */
const LAST_TENANT_MAX_AGE = 2_592_000;

type CookieStore = Awaited<ReturnType<typeof cookies>>;

/** Record the operator's selection for the next login (called by `/api/tenant`). */
export function rememberTenant(jar: CookieStore, accessToken: string, tenant: string): void {
  const sub = operatorSubject(accessToken);
  if (!sub) return;
  jar.set(LAST_TENANT_COOKIE, rememberTenantValue(sub, tenant), {
    ...tokenCookieOpts,
    maxAge: LAST_TENANT_MAX_AGE,
  });
}

/**
 * Choose and assume the default active tenant. Never fatal: on a degraded
 * registry, a refused or failed assume, or no choice, the session stays exactly
 * as it is (logged in, no tenant) and the function returns `null`.
 *
 * @param operatorToken the operator token the caller has JUST minted — passed
 *   explicitly because the request that carries this login does not carry that
 *   cookie yet.
 */
export async function establishDefaultTenant(
  jar: CookieStore,
  ctx: { accessToken: string; operatorToken: string; requestId: string; via: string },
): Promise<string | null> {
  const { accessToken, operatorToken, requestId, via } = ctx;

  let selectable: string[];
  try {
    selectable = selectableTenants(await fetchRegistry({ operatorToken }));
  } catch (err) {
    logger.warn('tenant_default_registry_unavailable', {
      requestId,
      via,
      err: err instanceof Error ? err.name : String(err),
    });
    return null;
  }

  const remembered = rememberedTenantFor(
    jar.get(LAST_TENANT_COOKIE)?.value,
    operatorSubject(accessToken),
  );
  const tenant = chooseDefaultTenant(selectable, remembered);
  if (!tenant) {
    logger.info('tenant_default_none', { requestId, via, selectableCount: selectable.length });
    return null;
  }

  try {
    const assumed = await exchangeForAssumedToken(accessToken, tenant);
    // Same shape as `/api/tenant`: the tenant cookie is a session cookie (it
    // survives idling, so the refresh takes the re-assume branch) and the
    // assumed token lives for its own `expiresIn`.
    jar.set(TENANT_COOKIE, tenant, tokenCookieOpts);
    jar.set(ASSUMED_TOKEN_COOKIE, assumed.accessToken, {
      ...tokenCookieOpts,
      maxAge: assumed.expiresIn,
    });
    logger.info('tenant_default_assumed', {
      requestId,
      via,
      tenant,
      source: tenant === remembered ? 'remembered' : 'single',
    });
    return tenant;
  } catch (err) {
    logger.warn('tenant_default_assume_failed', {
      requestId,
      via,
      tenant,
      err: err instanceof Error ? err.name : String(err),
    });
    return null;
  }
}
