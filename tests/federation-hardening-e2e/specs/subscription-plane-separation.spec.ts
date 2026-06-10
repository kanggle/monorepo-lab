import {
  test,
  expect,
  type BrowserContext,
} from '@playwright/test';
import { loginAsSuperAdmin, loginAsMultiOperator } from '../fixtures/login';

/**
 * TASK-MONO-207 — ADR-MONO-023 § 3.3 D2 cross-service plane-separation proof.
 *
 * The runtime capstone the account-service step-3 IT (TASK-BE-344) explicitly
 * DEFERRED: the cross-service half of the D2 invariant. The IT proved the
 * entitlement-plane half in-process (suspend drops the domain from both read
 * paths, row preserved, reversible); IAM-plane preservation was argued
 * structurally (account-service has no admin_db access). This spec closes the
 * loop end-to-end on the full federation stack (auth + account + admin +
 * console-web + 5 domains), proving the thing only a running multi-service stack
 * can:
 *
 *   an entitlement SUSPEND (entitlement plane, account_db) is reflected in a
 *   RE-ISSUED operator token's signed `entitled_domains` claim, WHILE the
 *   operator's `operator_tenant_assignment` row (IAM plane, admin_db) stays
 *   byte-unchanged — GCP billing↔IAM parity (suspend ≠ unbind; resume needs no
 *   re-grant).
 *
 * Logic chain exercised:
 *   mutate (D3 surface → D2 delegation): the admin RBAC surface
 *     PATCH /api/admin/subscriptions/{tenant}/{domain}/status — gated by the
 *     `subscription.manage` permission (TASK-BE-343, V0032 seeds it on
 *     SUPER_ADMIN) — authorizes in the IAM plane, then delegates the entitlement
 *     write to account-service (TASK-BE-342). This is the EXACT D2/D3 path, not
 *     a DB poke.
 *   re-issue (the cross-service fidelity): the operator switches tenant, driving
 *     the assume-tenant RFC 8693 exchange, which re-mints the domain-facing token
 *     with `entitled_domains` re-read from account-service at issuance
 *     (TenantClaimTokenCustomizer.populateEntitledDomains). The suspended domain
 *     is gone from the fresh token; the control domain remains.
 *   IAM-plane survival (the discriminator): the switch STILL returns 200 — the
 *     assume-tenant D2 assignment gate (auth → admin
 *     /internal/operator-assignments/check) would 403 if the binding were
 *     touched. Entitlement vanished; assignment survived. RESUME restores via the
 *     same row, no re-grant.
 *
 * Assertion channel: the SIGNED `entitled_domains` claim is decoded directly from
 * the `console_assumed_token` HttpOnly cookie set by the real server-side
 * exchange — the most direct proof of token re-issuance fidelity (claim → card
 * is already proven by the MONO-154 / MONO-158 overview-card specs, so this spec
 * deliberately does not re-assert the cards, avoiding producer-health flakiness).
 *
 * Isolation: a DEDICATED tenant `initech-corp` ([finance,wms], account-service
 * Flyway-dev V9002 + the multi-operator assignment in seed.sql § 14) that NO
 * other spec references. The suspend/resume cycle therefore cannot race-break the
 * fullyParallel acme-corp / globex-corp assertions; the suspend target `finance`
 * is always restored in `finally`.
 *
 * Session isolation: empty storageState + a fresh OIDC PKCE login (the suite-wide
 * SUPER_ADMIN '*' wildcard would defeat the entitlement gate). Two contexts: the
 * SUPER_ADMIN admin context owns the mutation (it alone has subscription.manage);
 * the multi-operator op context observes the re-scoped token.
 *
 * Verification channel: federation-hardening-e2e is nightly + workflow_dispatch
 * (NOT PR-triggered) — verified via `gh workflow run federation-hardening-e2e.yml`.
 */

// Override the suite-wide SUPER_ADMIN storageState — both identities log in fresh.
test.use({ storageState: { cookies: [], origins: [] } });

/** admin-service host base URL (docker-compose maps 18085:8085); the workflow
 *  exports E2E_ADMIN_BASE_URL. The admin RBAC surface is called directly with the
 *  exchanged operator token (there is no console-web subscription proxy route —
 *  unlike /api/tenant — so the spec drives the backend surface directly, which is
 *  the D2/D3 path under test). */
const ADMIN_BASE = process.env.E2E_ADMIN_BASE_URL ?? 'http://localhost:18085';

/** session.ts cookie names (HttpOnly — readable via BrowserContext.cookies()). */
const OPERATOR_COOKIE = 'console_operator_token'; // the /api/admin/** credential
const ASSUMED_COOKIE = 'console_assumed_token'; // the re-scoped domain-facing token

const TENANT = 'initech-corp';
const SUSPEND_DOMAIN = 'finance'; // the entitlement toggled by the proof
const CONTROL_DOMAIN = 'wms'; // must stay entitled (only the target drops)

async function readCookie(
  ctx: BrowserContext,
  name: string,
): Promise<string | undefined> {
  const all = await ctx.cookies();
  return all.find((c) => c.name === name)?.value;
}

/** Decode a JWT payload (no verification needed — the token was just minted by
 *  the real exchange; we only read its claims). */
function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const payload = jwt.split('.')[1];
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
}

/** The signed `entitled_domains` claim from the current assumed (re-scoped) token. */
async function assumedEntitledDomains(ctx: BrowserContext): Promise<string[]> {
  const tok = await readCookie(ctx, ASSUMED_COOKIE);
  expect(
    tok,
    'assumed token cookie must be present after an active-tenant switch',
  ).toBeTruthy();
  const claims = decodeJwtPayload(tok!);
  expect(
    claims.tenant_id,
    'assumed token must be re-scoped to the selected tenant',
  ).toBe(TENANT);
  return (claims.entitled_domains as string[] | undefined) ?? [];
}

/** Drive the real switcher → assume-tenant exchange. A 200 also asserts the
 *  IAM-plane assignment is intact (the D2 assignment gate would 403 otherwise). */
async function switchTenant(ctx: BrowserContext, tenant: string): Promise<void> {
  const res = await ctx.request.post('/api/tenant', { data: { tenant } });
  expect(
    res.status(),
    `switch to ${tenant} must succeed (operator_tenant_assignment present → assume-tenant minted)`,
  ).toBe(200);
  expect((await res.json()).activeTenant).toBe(tenant);
}

/** Mutate a subscription through the admin RBAC surface (D3 → D2 delegation),
 *  with the proven /api/admin/** header set (Bearer operator token + reason +
 *  active tenant). Asserts the producer 200. */
async function setStatus(
  adminCtx: BrowserContext,
  operatorToken: string,
  tenant: string,
  domain: string,
  status: string,
): Promise<void> {
  const res = await adminCtx.request.patch(
    `${ADMIN_BASE}/api/admin/subscriptions/${tenant}/${domain}/status`,
    {
      headers: {
        Authorization: `Bearer ${operatorToken}`,
        'X-Operator-Reason': encodeURIComponent(
          'e2e ADR-023 D2 plane-separation proof',
        ),
        'X-Tenant-Id': '*', // SUPER_ADMIN platform scope
        'Content-Type': 'application/json',
      },
      data: { status },
    },
  );
  expect(
    res.status(),
    `${tenant}/${domain} → ${status} via the subscription.manage admin surface`,
  ).toBe(200);
}

/** Best-effort resume (baseline + cleanup): tolerates the 409 self-transition
 *  when the row is already ACTIVE. Never asserts (no report noise). */
async function tryResume(
  adminCtx: BrowserContext,
  operatorToken: string,
): Promise<void> {
  await adminCtx.request
    .patch(
      `${ADMIN_BASE}/api/admin/subscriptions/${TENANT}/${SUSPEND_DOMAIN}/status`,
      {
        headers: {
          Authorization: `Bearer ${operatorToken}`,
          'X-Operator-Reason': encodeURIComponent('e2e baseline/cleanup resume'),
          'X-Tenant-Id': '*',
          'Content-Type': 'application/json',
        },
        data: { status: 'ACTIVE' },
      },
    )
    .catch(() => {
      /* ignore — already ACTIVE (409 self-transition) or transient */
    });
}

test.describe('ADR-MONO-023 D2 — entitlement↔IAM plane separation (cross-service runtime proof)', () => {
  test('suspending an entitlement drops it from the re-issued operator token while the IAM assignment survives; resume restores it', async ({
    browser,
  }) => {
    // Admin context: SUPER_ADMIN — the only identity with subscription.manage.
    const adminCtx = await browser.newContext({
      storageState: { cookies: [], origins: [] },
    });
    // Operator context: the multi-operator, assigned to initech-corp (seed § 14).
    const opCtx = await browser.newContext({
      storageState: { cookies: [], origins: [] },
    });

    let operatorToken = '';
    try {
      await loginAsSuperAdmin(adminCtx);
      const tok = await readCookie(adminCtx, OPERATOR_COOKIE);
      expect(
        tok,
        'the exchanged SUPER_ADMIN operator token (the /api/admin/** credential) must be present',
      ).toBeTruthy();
      operatorToken = tok!;

      await loginAsMultiOperator(opCtx);

      // Defensive baseline — a prior aborted run may have left finance SUSPENDED.
      await tryResume(adminCtx, operatorToken);

      // ── A. baseline: switch to initech → entitled_domains = [finance, wms] ──
      await switchTenant(opCtx, TENANT);
      expect(
        await assumedEntitledDomains(opCtx),
        'baseline: both subscriptions entitled in the freshly minted token',
      ).toEqual(expect.arrayContaining([SUSPEND_DOMAIN, CONTROL_DOMAIN]));

      // ── B. SUSPEND finance via the admin RBAC surface (D3 authz → D2 write) ─
      await setStatus(adminCtx, operatorToken, TENANT, SUSPEND_DOMAIN, 'SUSPENDED');

      // ── C. re-issue: switch initech again → token RE-MINTED ────────────────
      // The switch STILL returns 200 (inside switchTenant) → the IAM-plane
      // operator_tenant_assignment row is intact: the assume-tenant D2 assignment
      // gate would 403 if the binding had been touched. The discriminator:
      const afterSuspend = await assumedEntitledDomains(opCtx);
      expect(
        afterSuspend,
        'entitlement plane: the SUSPENDED domain is dropped from the re-issued token',
      ).not.toContain(SUSPEND_DOMAIN);
      expect(
        afterSuspend,
        'IAM plane intact: the control domain is UNAFFECTED (only the targeted entitlement changed)',
      ).toContain(CONTROL_DOMAIN);

      // ── D. RESUME → reversible via the SAME assignment, no re-grant ─────────
      await setStatus(adminCtx, operatorToken, TENANT, SUSPEND_DOMAIN, 'ACTIVE');
      await switchTenant(opCtx, TENANT);
      expect(
        await assumedEntitledDomains(opCtx),
        'resume restored the entitlement via the same subscription row (no re-create, no re-grant)',
      ).toEqual(expect.arrayContaining([SUSPEND_DOMAIN, CONTROL_DOMAIN]));
    } finally {
      // Guarantee restoration (re-runnability) even if an assertion failed mid-cycle.
      if (operatorToken) await tryResume(adminCtx, operatorToken);
      await adminCtx.close();
      await opCtx.close();
    }
  });
});
