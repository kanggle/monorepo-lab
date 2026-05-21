import { fetchRegistry } from '@/shared/api/registry-client';

/**
 * Server-only helper that resolves the calling operator's finance default
 * account id from the GAP registry response.
 *
 * <b>Server-only by construction</b>: this helper transitively imports
 * `next/headers` (via `fetchRegistry` → `getOperatorToken`), which throws
 * at runtime when called from a `'use client'` component or browser
 * bundle. The Next.js compiler additionally rejects `next/headers`
 * imports from client components at build time. This is the same
 * server-only enforcement the entire `shared/lib/session.ts` module
 * already relies on — no additional `import 'server-only'` is required
 * (the `server-only` npm shim is not a dependency of this app; the
 * runtime + compile-time guarantees from `next/headers` are the actual
 * enforcement).
 *
 * Source of truth: `productItem[finance].operatorContext.defaultAccountId`
 * on the GAP `GET /api/admin/console/registry` response (Phase 1 producer
 * surface — TASK-BE-304; consumer-side wiring — TASK-PC-FE-014; specs:
 * `console-integration-contract.md § 2.2` + `§ 2.4.9.1 Implementation
 * guidance — Option (a) activation`; producer:
 * `global-account-platform/specs/contracts/http/console-registry-api.md
 * § Per-operator profile attributes`).
 *
 * Returns `null` when:
 *   (i)   the registry omits `operatorContext` on the finance item (the
 *         producer omits when `admin_operators.finance_default_account_id`
 *         is null / blank after trim — Jackson `@JsonInclude(NON_NULL)`),
 *   (ii)  `operatorContext` is present but `defaultAccountId` is absent
 *         (future-extension carrier — other attributes only),
 *   (iii) the stored value is empty / whitespace after trim (defensive,
 *         the producer never emits this but the consumer is tolerant),
 *   (iv)  the registry response itself was unreachable (timeout / 401 /
 *         503) — same degraded posture as `getCatalog()` (the page never
 *         blank-crashes; the finance card falls through to the existing
 *         MISSING_PREREQUISITE path).
 *
 * Returns the **trimmed** value (always non-empty) when present.
 *
 * <b>SCOPE:</b> server-only — the import barrier (`server-only`) prevents
 * any `'use client'` file from bundling this helper. The value is
 * `internal`-classified operator profile data (finance F7 / `regulated.md`
 * R7 transitive discipline); it must NEVER be logged at INFO and NEVER
 * reach the browser bundle (Failure Scenario "header forwarded from
 * client component"). The only legitimate consumer is the server-side
 * dashboard proxy route which forwards it as the `X-Finance-Default-Account-Id`
 * request header to `console-bff`.
 */
export async function getFinanceDefaultAccountId(): Promise<string | null> {
  let registry;
  try {
    registry = await fetchRegistry();
  } catch {
    // Registry unreachable (401 / 503 / timeout). The session can still
    // be authenticated — the finance leg merely falls through to the
    // existing MISSING_PREREQUISITE path (header omitted is identical
    // to header set on an un-provisioned operator). The proxy route's
    // own auth gate (Authorization + X-Operator-Token cookies) handles
    // the unauthenticated case independently; this helper never throws.
    return null;
  }

  const financeItem = registry.products.find((p) => p.productKey === 'finance');
  const raw = financeItem?.operatorContext?.defaultAccountId;
  if (typeof raw !== 'string') return null;
  const trimmed = raw.trim();
  return trimmed.length > 0 ? trimmed : null;
}
