import {
  test,
  expect,
  type BrowserContext,
} from '@playwright/test';
import { loginAsMultiOperator } from '../fixtures/login';
import { ADMIN_BASE, OPERATOR_COOKIE, STORAGE_STATE } from '../fixtures/admin-helpers';
import { switchTenant } from '../fixtures/console-helpers';

/**
 * TASK-MONO-207 — ADR-MONO-023 § 3.3 D2 cross-service plane-separation proof.
 *
 * The runtime capstone the account-service step-3 IT (TASK-BE-344) explicitly
 * DEFERRED: the cross-service half of the D2 invariant. The IT proved the
 * entitlement-plane half in-process (suspend drops the domain from both read
 * paths, row preserved, reversible); IAM-plane preservation was argued
 * structurally. This spec closes the loop end-to-end on the full federation
 * stack (auth + account + admin + console-web + 5 domains):
 *
 *   an entitlement SUSPEND (entitlement plane, account_db) is reflected in a
 *   RE-ISSUED operator token's signed `entitled_domains` claim, WHILE the
 *   operator's `operator_tenant_assignment` row (IAM plane, admin_db) stays
 *   byte-unchanged — GCP billing↔IAM parity (suspend ≠ unbind; resume needs no
 *   re-grant).
 *
 * Logic chain exercised:
 *   mutate (D3 surface → D2 delegation): PATCH /api/admin/subscriptions/...
 *     gated by `subscription.manage` (BE-343, V0032 on SUPER_ADMIN) authorizes
 *     in the IAM plane, then delegates the entitlement write to account-service
 *     (BE-342). The exact D2/D3 path, not a DB poke.
 *   re-issue: the operator switches tenant → the assume-tenant RFC 8693 exchange
 *     re-mints the domain-facing token with `entitled_domains` re-read from
 *     account-service at issuance. EMPIRICAL (verified in run 27255988866): two
 *     CONSECUTIVE switches to the same tenant reuse the same assumed token (the
 *     SAS token-exchange returns the existing token for an identical
 *     subject+audience), so a same-tenant re-switch would NOT pick up the change.
 *     The re-mint therefore switches AWAY (globex-corp) and BACK to initech-corp,
 *     forcing a genuinely fresh initech exchange that re-reads entitled_domains.
 *   IAM-plane survival: the switch back STILL returns 200 — the assume-tenant D2
 *     assignment gate would 403 if the binding were touched. Entitlement
 *     vanished; assignment survived. RESUME restores via the same row.
 *
 * Assertion channel: the SIGNED `entitled_domains` claim decoded directly from
 * the `console_assumed_token` HttpOnly cookie set by the real server-side
 * exchange (claim → card is already proven by MONO-154 / MONO-158, so the cards
 * are not re-asserted here — avoids producer-health flakiness).
 *
 * Isolation: a DEDICATED tenant `initech-corp` ([finance,wms], Flyway-dev V9002
 * + seed.sql § 14 assignment) referenced by NO other spec, so the runtime
 * suspend/resume cannot race-break the fullyParallel acme/globex specs; finance
 * is always restored in `finally`, and the per-attempt baseline resume makes the
 * suite-default retries safe (a leftover SUSPENDED only yields a benign 409
 * self-transition WARN, never a cross-attempt failure).
 *
 * Verification channel: federation-hardening-e2e is nightly + workflow_dispatch
 * (NOT PR-triggered) — verified via `gh workflow run federation-hardening-e2e.yml`.
 */

// Override the suite-wide SUPER_ADMIN storageState — the operator logs in fresh.
test.use({ storageState: { cookies: [], origins: [] } });

/** session.ts cookie name (HttpOnly — readable via BrowserContext.cookies()). */
const ASSUMED_COOKIE = 'console_assumed_token'; // the re-scoped domain-facing token

const TENANT = 'initech-corp';
const INTERMEDIATE = 'globex-corp'; // switch-away target to force a fresh exchange
const SUSPEND_DOMAIN = 'finance'; // the entitlement toggled by the proof
const CONTROL_DOMAIN = 'wms'; // must stay entitled (only the target drops)

async function readCookie(
  ctx: BrowserContext,
  name: string,
): Promise<string | undefined> {
  const all = await ctx.cookies();
  return all.find((c) => c.name === name)?.value;
}

/** Decode a JWT payload (no verification — the token was just minted by the real
 *  exchange; we only read its claims). */
function decodeJwtPayload(jwt: string): Record<string, unknown> {
  const payload = jwt.split('.')[1];
  return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8'));
}

/** The current assumed (re-scoped) token's claims, with a diagnostic log of the
 *  re-scope target + issued-at + entitled set (so a stale-token vs stale-data
 *  failure is distinguishable in the run log). */
async function assumedClaims(
  ctx: BrowserContext,
  label: string,
): Promise<Record<string, unknown>> {
  const tok = await readCookie(ctx, ASSUMED_COOKIE);
  expect(
    tok,
    'assumed token cookie must be present after an active-tenant switch',
  ).toBeTruthy();
  const claims = decodeJwtPayload(tok!);
  // eslint-disable-next-line no-console
  console.log(
    `[MONO-207] ${label}: tenant_id=${claims.tenant_id} iat=${claims.iat} entitled=${JSON.stringify(claims.entitled_domains)}`,
  );
  return claims;
}

async function entitledOf(
  ctx: BrowserContext,
  label: string,
): Promise<string[]> {
  const claims = await assumedClaims(ctx, label);
  expect(claims.tenant_id, 'assumed token re-scoped to the selected tenant').toBe(
    TENANT,
  );
  return (claims.entitled_domains as string[] | undefined) ?? [];
}

/** Re-mint the initech assumed token via a genuinely fresh exchange: switch AWAY
 *  to globex then BACK to initech (so the second initech exchange cannot reuse a
 *  same-audience idempotent result). */
async function remintInitech(ctx: BrowserContext): Promise<void> {
  await switchTenant(ctx, INTERMEDIATE);
  await switchTenant(ctx, TENANT);
}

/** Mutate a subscription through the admin RBAC surface (D3 → D2 delegation),
 *  with the proven /api/admin/** header set. Logs + asserts the producer 200. */
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
  const bodyText = await res.text();
  // eslint-disable-next-line no-console
  console.log(
    `[MONO-207] setStatus ${tenant}/${domain}→${status}: http=${res.status()} body=${bodyText}`,
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
    // Reuses the persisted global-setup session (no in-test login → no
    // tracing.start collision); the exchanged operator token is in its cookies.
    const adminCtx = await browser.newContext({ storageState: STORAGE_STATE });
    // Operator context: the multi-operator, assigned to initech-corp (seed § 14).
    const opCtx = await browser.newContext({
      storageState: { cookies: [], origins: [] },
    });

    let operatorToken = '';
    try {
      const tok = await readCookie(adminCtx, OPERATOR_COOKIE);
      expect(
        tok,
        'the exchanged SUPER_ADMIN operator token (the /api/admin/** credential) must be present in the persisted global-setup session',
      ).toBeTruthy();
      operatorToken = tok!;

      await loginAsMultiOperator(opCtx);

      // Defensive baseline — a prior aborted run may have left finance SUSPENDED.
      await tryResume(adminCtx, operatorToken);

      // ── A. baseline: switch to initech → entitled_domains = [finance, wms] ──
      await switchTenant(opCtx, TENANT);
      expect(
        await entitledOf(opCtx, 'A/baseline'),
        'baseline: both subscriptions entitled in the freshly minted token',
      ).toEqual(expect.arrayContaining([SUSPEND_DOMAIN, CONTROL_DOMAIN]));

      // ── B. SUSPEND finance via the admin RBAC surface (D3 authz → D2 write) ─
      await setStatus(adminCtx, operatorToken, TENANT, SUSPEND_DOMAIN, 'SUSPENDED');

      // ── C. re-issue (away→back) → token RE-MINTED ──────────────────────────
      // The switch back STILL returns 200 → the IAM-plane operator_tenant_assignment
      // row is intact (the assume-tenant D2 gate would 403 if touched). The
      // discriminator:
      await remintInitech(opCtx);
      const afterSuspend = await entitledOf(opCtx, 'C/after-suspend');
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
      await remintInitech(opCtx);
      expect(
        await entitledOf(opCtx, 'D/after-resume'),
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
