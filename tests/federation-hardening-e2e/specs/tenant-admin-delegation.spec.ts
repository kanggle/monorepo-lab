import { test, expect, type BrowserContext, type APIResponse } from '@playwright/test';
import {
  loginAsTenantAdmin,
  loginAsTenantBillingAdmin,
} from '../fixtures/login';
import {
  ADMIN_BASE,
  STORAGE_STATE,
  codeOf,
  headers,
  operatorToken,
  send,
  warmUpAdminOutbox,
} from '../fixtures/admin-helpers';

/**
 * TASK-MONO-210 — ADR-MONO-024 § 3.3 step 3 tenant-admin delegation runtime proof.
 *
 * The federation-stack capstone for the delegated-administration initiative
 * whose authority model was built in-process by step 1 (D2 target-tenant
 * confinement, TASK-BE-345), step 2a (the TENANT_ADMIN / TENANT_BILLING_ADMIN
 * roles, TASK-BE-346) and step 2b (the assign/unassign surface + grant-menu
 * no-escalation, TASK-BE-347). The unit/IT suites proved each guard against a
 * mocked or single-JVM operator; this spec proves the SAME guards bite end-to-end
 * on the live federation stack — a real OIDC login → real `console_operator_token`
 * exchange → the real `/api/admin/**` RBAC surface — for a NON-platform admin
 * whose authority is confined by its `admin_operator_roles` grant-row tenant_id.
 *
 * Two delegated administrators, both scoped to the DEDICATED `umbrella-corp`
 * tenant (account-service Flyway-dev V9003 + seed.sql § 15):
 *
 *   • TENANT_ADMIN (operator.manage + tenant.admin.delegate @ umbrella-corp)
 *       - assigns / scopes / unassigns a target operator within umbrella-corp → 2xx
 *       - cross-tenant assign to globex-corp → 403 TENANT_SCOPE_DENIED (D2)
 *       - grant-menu (PATCH .../roles):
 *           · peer-appoint TENANT_ADMIN (sub-delegation, ≤-own) → 200
 *           · escalate to SUPER_ADMIN → 403 ROLE_GRANT_FORBIDDEN (platform role)
 *           · cross-plane TENANT_BILLING_ADMIN → 403 ROLE_GRANT_FORBIDDEN
 *             (needs subscription.manage, which a TENANT_ADMIN does not hold)
 *
 *   • TENANT_BILLING_ADMIN (subscription.manage @ umbrella-corp)
 *       - suspend/resume umbrella-corp/finance via the subscription surface → 200
 *       - cross-tenant suspend on globex-corp → 403 TENANT_SCOPE_DENIED (D2);
 *         the guard rejects BEFORE delegating to account-service, so globex is
 *         never touched (parallel-safe with the globex specs).
 *
 *   • SUPER_ADMIN ('*') net-zero: unconstrained on the very same surface a
 *       TENANT_ADMIN is confined on (assign to ANY tenant → 201) — proving the
 *       confinement is grant-scope-driven, not a blanket new restriction.
 *
 * Isolation: `umbrella-corp` + the `deleg-target-umbrella` operator are referenced
 * by NO other spec; the target never logs in (no session), so re-roling /
 * assigning it cannot race the fullyParallel suite. Every mutation is restored in
 * `finally` (assignments deleted, roles reset, subscription resumed) so the
 * suite-default `retries: 2` is safe.
 *
 * Verification channel: federation-hardening-e2e is nightly + workflow_dispatch
 * (NOT PR-triggered) — verified via `gh workflow run federation-hardening-e2e.yml`.
 */

// Override the suite-wide SUPER_ADMIN storageState — each delegated admin logs in fresh.
test.use({ storageState: { cookies: [], origins: [] } });

const HOME = 'umbrella-corp'; // the delegated admins' own tenant
const FOREIGN = 'globex-corp'; // an out-of-scope tenant (exists; never mutated here)
const TARGET = 'deleg-target-umbrella'; // the operator the TENANT_ADMIN manages
const BILL_DOMAIN = 'finance'; // umbrella-corp's seeded subscription (V9003)

// ── admin-surface request helpers (absolute ADMIN_BASE URLs) ──────────────────

function assign(ctx: BrowserContext, token: string, operatorId: string, tenant: string): Promise<APIResponse> {
  return send(() => ctx.request.post(`${ADMIN_BASE}/api/admin/operators/${operatorId}/assignments/${tenant}`, {
    headers: headers(token, 'e2e ADR-024 step3 assign'),
  }));
}

function unassign(ctx: BrowserContext, token: string, operatorId: string, tenant: string): Promise<APIResponse> {
  return send(() => ctx.request.delete(`${ADMIN_BASE}/api/admin/operators/${operatorId}/assignments/${tenant}`, {
    headers: headers(token, 'e2e ADR-024 step3 unassign'),
  }));
}

function setOrgScope(ctx: BrowserContext, token: string, operatorId: string, tenant: string, scope: string[]): Promise<APIResponse> {
  return send(() => ctx.request.put(`${ADMIN_BASE}/api/admin/operators/${operatorId}/assignments/${tenant}/org-scope`, {
    headers: { ...headers(token, 'e2e ADR-024 step3 org-scope'), 'X-Tenant-Id': tenant },
    data: { orgScope: scope },
  }));
}

function patchRoles(ctx: BrowserContext, token: string, operatorId: string, roles: string[]): Promise<APIResponse> {
  return send(() => ctx.request.patch(`${ADMIN_BASE}/api/admin/operators/${operatorId}/roles`, {
    headers: headers(token, 'e2e ADR-024 step3 grant-menu'),
    data: { roles },
  }));
}

function changeSub(ctx: BrowserContext, token: string, tenant: string, domain: string, status: string): Promise<APIResponse> {
  return send(() => ctx.request.patch(`${ADMIN_BASE}/api/admin/subscriptions/${tenant}/${domain}/status`, {
    headers: headers(token, 'e2e ADR-024 step3 subscription'),
    data: { status },
  }));
}

/** Best-effort teardown — never asserts (tolerates 404/409/transient). */
async function quietUnassign(ctx: BrowserContext, token: string, tenant: string): Promise<void> {
  await unassign(ctx, token, TARGET, tenant).catch(() => {});
}
async function quietResume(ctx: BrowserContext, token: string): Promise<void> {
  await changeSub(ctx, token, HOME, BILL_DOMAIN, 'ACTIVE').catch(() => {});
}

test.describe('ADR-MONO-024 step 3 — tenant-admin delegation (federation runtime proof)', () => {
  // Each test does a full OIDC login PLUS a long chain of sequential admin-RBAC
  // calls; the default 30s budget is too tight on a loaded stack. Run serially so
  // this spec never adds more than one concurrent operator login/context to the
  // fullyParallel cohort, and give each test a generous budget.
  test.describe.configure({ mode: 'serial' });

  // Outbox warm-up gate: a cold federation stack's admin_db `outbox` poller can
  // hold its range PESSIMISTIC_WRITE lock long enough (while Kafka warms up) to
  // make audit→outbox INSERTs time out (→ 500). Block here, using the SUPER_ADMIN
  // platform credential, until an admin write (which exercises the audit→outbox
  // path) succeeds — so the real assertions below run against a warm, writable
  // stack and never observe the transient lock-timeout 500. `send()` already
  // retries the transient 500; this gate just front-loads the wait once per
  // worker instead of inside every test.
  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    // assign→unassign a throwaway (writes audit→outbox rows); 201/409 ⇒ writable.
    await warmUpAdminOutbox(browser, {
      logPrefix: 'MONO-210',
      accept: [201, 409],
      probe: (ctx, tok) => assign(ctx, tok, TARGET, FOREIGN),
      cleanup: (ctx, tok) => quietUnassign(ctx, tok, FOREIGN),
    });
  });

  test('TENANT_ADMIN administers its own tenant (assign/scope/grant) and is denied cross-tenant + escalating grants', async ({ browser }) => {
    test.setTimeout(200_000);
    const adminCtx = await browser.newContext({ storageState: STORAGE_STATE });
    const opCtx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    let superToken = '';
    try {
      superToken = await operatorToken(adminCtx, 'SUPER_ADMIN(global-setup)');
      // Defensive baseline — a prior aborted run may have left the target assigned.
      await quietUnassign(adminCtx, superToken, HOME);
      await quietUnassign(adminCtx, superToken, FOREIGN);

      await loginAsTenantAdmin(opCtx);
      const taToken = await operatorToken(opCtx, 'TENANT_ADMIN');

      // ── A. assign within its own tenant → 201 (operator.manage @ umbrella) ──
      const created = await assign(opCtx, taToken, TARGET, HOME);
      expect(created.status(), 'TENANT_ADMIN assigns target within its own tenant').toBe(201);

      // ── B. scope (org_scope) within its own tenant → 200 ───────────────────
      const scoped = await setOrgScope(opCtx, taToken, TARGET, HOME, ['dept-ops']);
      expect(scoped.status(), 'TENANT_ADMIN sets org_scope within its own tenant').toBe(200);

      // ── C. cross-tenant assign → 403 TENANT_SCOPE_DENIED (D2 confinement) ──
      const crossAssign = await assign(opCtx, taToken, TARGET, FOREIGN);
      expect(crossAssign.status(), 'TENANT_ADMIN denied assigning into a foreign tenant').toBe(403);
      expect(await codeOf(crossAssign)).toBe('TENANT_SCOPE_DENIED');

      // ── D. grant-menu: peer-appoint TENANT_ADMIN (sub-delegation, ≤-own) → 200
      const subDelegate = await patchRoles(opCtx, taToken, TARGET, ['TENANT_ADMIN']);
      expect(subDelegate.status(), 'TENANT_ADMIN may grant TENANT_ADMIN in-tenant (D4-B sub-delegation)').toBe(200);

      // ── E. grant-menu: escalate to SUPER_ADMIN → 403 ROLE_GRANT_FORBIDDEN ──
      const escalate = await patchRoles(opCtx, taToken, TARGET, ['SUPER_ADMIN']);
      expect(escalate.status(), 'TENANT_ADMIN denied granting the platform SUPER_ADMIN role').toBe(403);
      expect(await codeOf(escalate)).toBe('ROLE_GRANT_FORBIDDEN');

      // ── F. grant-menu: cross-plane TENANT_BILLING_ADMIN → 403 (lacks subscription.manage)
      const crossPlane = await patchRoles(opCtx, taToken, TARGET, ['TENANT_BILLING_ADMIN']);
      expect(crossPlane.status(), 'TENANT_ADMIN denied granting the billing role it does not hold (plane separation)').toBe(403);
      expect(await codeOf(crossPlane)).toBe('ROLE_GRANT_FORBIDDEN');

      // ── G. unassign within its own tenant → 204 ────────────────────────────
      const removed = await unassign(opCtx, taToken, TARGET, HOME);
      expect(removed.status(), 'TENANT_ADMIN unassigns target within its own tenant').toBe(204);
    } finally {
      if (superToken) {
        await quietUnassign(adminCtx, superToken, HOME);
        await quietUnassign(adminCtx, superToken, FOREIGN);
        // Restore the target's baseline role (SUPER_ADMIN can grant anything).
        await patchRoles(adminCtx, superToken, TARGET, ['SUPPORT_READONLY']).catch(() => {});
      }
      await adminCtx.close();
      await opCtx.close();
    }
  });

  test('TENANT_BILLING_ADMIN suspends/resumes its own subscription and is denied cross-tenant', async ({ browser }) => {
    test.setTimeout(120_000);
    const adminCtx = await browser.newContext({ storageState: STORAGE_STATE });
    const opCtx = await browser.newContext({ storageState: { cookies: [], origins: [] } });
    let superToken = '';
    try {
      superToken = await operatorToken(adminCtx, 'SUPER_ADMIN(global-setup)');
      // Defensive baseline — a prior aborted run may have left finance SUSPENDED.
      await quietResume(adminCtx, superToken);

      await loginAsTenantBillingAdmin(opCtx);
      const baToken = await operatorToken(opCtx, 'TENANT_BILLING_ADMIN');

      // ── A. suspend its own tenant's subscription → 200 (subscription.manage @ umbrella)
      const suspended = await changeSub(opCtx, baToken, HOME, BILL_DOMAIN, 'SUSPENDED');
      expect(suspended.status(), 'TENANT_BILLING_ADMIN suspends its own subscription').toBe(200);

      // ── B. resume → 200 (reversible) ───────────────────────────────────────
      const resumed = await changeSub(opCtx, baToken, HOME, BILL_DOMAIN, 'ACTIVE');
      expect(resumed.status(), 'TENANT_BILLING_ADMIN resumes its own subscription').toBe(200);

      // ── C. cross-tenant suspend → 403 TENANT_SCOPE_DENIED (guard rejects before
      //        delegating to account-service, so globex is never touched) ──────
      const crossSuspend = await changeSub(opCtx, baToken, FOREIGN, 'scm', 'SUSPENDED');
      expect(crossSuspend.status(), 'TENANT_BILLING_ADMIN denied mutating a foreign tenant').toBe(403);
      expect(await codeOf(crossSuspend)).toBe('TENANT_SCOPE_DENIED');
    } finally {
      if (superToken) await quietResume(adminCtx, superToken);
      await adminCtx.close();
      await opCtx.close();
    }
  });

  test('SUPER_ADMIN is unconstrained on the same surface (net-zero — confinement is grant-scope-driven)', async ({ browser }) => {
    test.setTimeout(90_000);
    const adminCtx = await browser.newContext({ storageState: STORAGE_STATE });
    let superToken = '';
    try {
      superToken = await operatorToken(adminCtx, 'SUPER_ADMIN(global-setup)');
      await quietUnassign(adminCtx, superToken, FOREIGN);

      // The exact cross-tenant assign a TENANT_ADMIN was denied (step C above)
      // succeeds for SUPER_ADMIN ('*' ∈ effectiveAdminScope) → 201.
      const created = await assign(adminCtx, superToken, TARGET, FOREIGN);
      expect(created.status(), 'SUPER_ADMIN assigns into ANY tenant (platform scope, net-zero)').toBe(201);

      const removed = await unassign(adminCtx, superToken, TARGET, FOREIGN);
      expect(removed.status(), 'SUPER_ADMIN unassigns the cross-tenant row').toBe(204);
    } finally {
      if (superToken) await quietUnassign(adminCtx, superToken, FOREIGN);
      await adminCtx.close();
    }
  });
});
