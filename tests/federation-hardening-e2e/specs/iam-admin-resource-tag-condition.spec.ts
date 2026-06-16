import { test, expect, type BrowserContext, type APIResponse } from '@playwright/test';
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
 * TASK-MONO-228 — ADR-MONO-029 § D6 step 4 iam admin RESOURCE_TAG access-condition
 * federation runtime proof (the DETERMINISTIC one — unlike ADR-028's global-clock
 * TIME_WINDOW, which could not be federation-proven without breaking suite-level
 * net-zero).
 *
 * Completes the closed access-condition enum {SOURCE_IP ✓, TIME_WINDOW ✓,
 * RESOURCE_TAG} end-to-end. With the shared admin-service configured with
 * ADMIN_ACCESS_RESOURCE_TAG_FORBIDDEN=protected (docker-compose), the gate is
 * keyed on the TARGET resource's tags — so it bites exactly one seeded operator
 * and is net-zero for every other (no global side effect):
 *
 *   • gated:      a role mutation on `rt-protected-target` (tags='protected') →
 *                 403 ACCESS_CONDITION_UNMET.
 *   • unaffected: the SAME mutation on `rt-untagged-target` (no tags) → 200.
 *
 * The discriminant is the operator's tag alone. SOURCE_IP is also configured (the
 * MONO-221 allowlist), and these requests carry no X-Forwarded-For → their private
 * docker remote address satisfies SOURCE_IP, so the gated 403 is specifically the
 * RESOURCE_TAG condition firing while SOURCE_IP passes — proving the AND-only
 * 3-condition composition on the live stack. The gate is orthogonal to RBAC, so it
 * bites even SUPER_ADMIN ('*').
 *
 * Isolation: `rt-protected-target` / `rt-untagged-target` (seed.sql § 17, scoped to
 * the dedicated ip-pilot-corp tenant) are referenced by NO other spec and never log
 * in; the role mutation is idempotent (re-sets SUPPORT_READONLY), so no teardown is
 * needed and the suite-default retries are safe.
 *
 * Verification channel: federation-hardening-e2e is nightly + workflow_dispatch
 * (NOT PR-triggered) — verified via `gh workflow run federation-hardening-e2e.yml`.
 */

const PROTECTED_TARGET = 'rt-protected-target'; // tags='protected' → mutation gated
const UNTAGGED_TARGET = 'rt-untagged-target'; // no tags → mutation allowed
const BENIGN_ROLES = ['SUPPORT_READONLY']; // idempotent re-set (the seeded baseline role)

function patchRoles(ctx: BrowserContext, token: string, operatorId: string): Promise<APIResponse> {
  return send(() => ctx.request.patch(`${ADMIN_BASE}/api/admin/operators/${operatorId}/roles`, {
    headers: headers(token, 'e2e ADR-029 step4 resource-tag'),
    data: { roles: BENIGN_ROLES },
  }));
}

test.describe('ADR-MONO-029 step 4 — iam admin RESOURCE_TAG access condition (federation runtime proof)', () => {
  test.describe.configure({ mode: 'serial' });

  // Outbox warm-up gate (MONO-207/210/221 pattern): block on an in-policy role
  // mutation (on the UNTAGGED target, which the RESOURCE_TAG gate allows) until the
  // admin_db outbox is writable, so the real assertions run against a warm stack.
  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    await warmUpAdminOutbox(browser, {
      logPrefix: 'MONO-228',
      accept: [200],
      probe: (ctx, tok) => patchRoles(ctx, tok, UNTAGGED_TARGET),
    });
  });

  test('gated: a mutation on a `protected`-tagged operator → 403 ACCESS_CONDITION_UNMET', async ({ browser }) => {
    test.setTimeout(120_000);
    const ctx = await browser.newContext({ storageState: STORAGE_STATE });
    try {
      const token = await operatorToken(ctx, 'SUPER_ADMIN(global-setup)');
      const gated = await patchRoles(ctx, token, PROTECTED_TARGET);
      expect(gated.status(), 'role mutation on a protected operator is gated by RESOURCE_TAG').toBe(403);
      expect(await codeOf(gated), 'denial is the access-condition gate (RESOURCE_TAG), not RBAC').toBe('ACCESS_CONDITION_UNMET');
    } finally {
      await ctx.close();
    }
  });

  test('unaffected: the SAME mutation on an untagged operator → 200 (per-resource net-zero)', async ({ browser }) => {
    test.setTimeout(120_000);
    const ctx = await browser.newContext({ storageState: STORAGE_STATE });
    try {
      const token = await operatorToken(ctx, 'SUPER_ADMIN(global-setup)');
      const ok = await patchRoles(ctx, token, UNTAGGED_TARGET);
      expect(ok.status(), 'role mutation on an untagged operator proceeds (the tag is the only discriminant)').toBe(200);
    } finally {
      await ctx.close();
    }
  });
});
