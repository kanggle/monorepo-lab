import path from 'node:path';
import { test, expect, type BrowserContext, type APIResponse } from '@playwright/test';

/**
 * TASK-MONO-221 — ADR-MONO-026 § D7 step 3 iam admin SOURCE_IP access-condition
 * federation runtime proof.
 *
 * The federation-stack capstone for the access-conditions initiative (axis ②
 * 2단계) whose authority model was built in-process by step 1 (the shared
 * contract `platform/access-conditions.md` + the shared `SourceIpCondition`
 * evaluator in libs/java-security, TASK-MONO-218) and step 2 (the admin-service
 * enforcement — the 4th authorization gate in `RequiresPermissionAspect`,
 * `AdminAccessConditionProperties` / `AccessConditionConfig`, 403
 * `ACCESS_CONDITION_UNMET`, net-zero/opt-in/fail-safe, TASK-BE-351). The unit/IT
 * suites proved each behaviour against MockMvc; this spec proves the SAME 4th gate
 * bites end-to-end on the live federation stack — a real `console_operator_token`
 * driving the real `/api/admin/**` mutation surface — with the shared admin-service
 * configured with an allowlist (`ADMIN_ACCESS_SOURCE_IP_CIDRS`, docker-compose).
 *
 * The SOURCE_IP condition is SERVICE-level (D3-B guard-config) and orthogonal to
 * RBAC (it runs only AFTER RBAC granted), so it gates even SUPER_ADMIN ('*'). The
 * proof therefore needs no new login helper / actor: it reuses the persisted
 * global-setup SUPER_ADMIN session and sets the perceived source IP per-request
 * via `X-Forwarded-For` (the aspect resolves the source IP from the raw
 * `X-Forwarded-For` first hop, falling back to the transport remote address —
 * `RequiresPermissionAspect.resolveSourceIp`). The mutation it gates is an
 * operator assign/unassign on a DEDICATED throwaway target.
 *
 *   • gated (out-of-range): assign `ip-pilot-target` → `ip-pilot-corp` with
 *       X-Forwarded-For = 203.0.113.7 (TEST-NET-3, outside every allowlisted
 *       range) → 403 ACCESS_CONDITION_UNMET; a follow-up read shows the row was
 *       NOT created (the RBAC-granted mutation was stopped by the gate).
 *   • unaffected (in-range): the SAME assign with X-Forwarded-For = 10.20.30.40
 *       (∈ 10.0.0.0/8) → 201; unassign → 204. Differs from the gated case ONLY by
 *       source IP — the gate does not disturb a legitimate, in-range operator.
 *   • mutation-only: a GET read with the out-of-range (blocked) X-Forwarded-For
 *       → 200 — reads are never gated (the restriction is mutation-only).
 *
 * Suite-level net-zero: the allowlist covers every RFC1918 + loopback range, so the
 * existing specs' admin mutations — which send no X-Forwarded-For and resolve to a
 * private docker-network remote address — stay in-range and are unaffected. The
 * whole workflow staying GREEN with the allowlist ON is itself the net-zero proof.
 *
 * Isolation: `ip-pilot-corp` (account-service Flyway-dev V9004) + the
 * `ip-pilot-target` operator (seed.sql § 16) are referenced by NO other spec; the
 * target never logs in (no session). Every mutation is restored in `finally`, so
 * the suite-default `retries: 2` is safe.
 *
 * Verification channel: federation-hardening-e2e is nightly + workflow_dispatch
 * (NOT PR-triggered) — verified via `gh workflow run federation-hardening-e2e.yml`.
 */

// This spec uses ONLY the persisted SUPER_ADMIN storageState (no fresh login) —
// keep the suite-wide storageState (do NOT override it like the per-operator specs).

/** admin-service host base URL (docker-compose maps 18085:8085); workflow exports
 *  E2E_ADMIN_BASE_URL. The admin RBAC surface is called directly with the
 *  exchanged operator token (mirror of the MONO-207/210 specs). */
const ADMIN_BASE = process.env.E2E_ADMIN_BASE_URL ?? 'http://localhost:18085';

/** The persisted global-setup SUPER_ADMIN session: its `console_operator_token`
 *  is the platform credential the proof drives the admin surface with. */
const STORAGE_STATE = path.join(__dirname, '../fixtures/.storage-state.json');

/** session.ts cookie name (HttpOnly — readable via BrowserContext.cookies()). */
const OPERATOR_COOKIE = 'console_operator_token'; // the /api/admin/** credential

const TARGET = 'ip-pilot-target'; // the dedicated throwaway operator (object, never actor)
const TENANT = 'ip-pilot-corp'; // the dedicated tenant the target is assigned to

/** X-Forwarded-For first hop OUTSIDE every allowlisted CIDR (RFC5737 TEST-NET-3,
 *  public documentation space — never private, never a real host→localhost src). */
const IP_OUT_OF_RANGE = '203.0.113.7';
/** X-Forwarded-For first hop INSIDE the allowlist (∈ 10.0.0.0/8). */
const IP_IN_RANGE = '10.20.30.40';

async function operatorToken(ctx: BrowserContext, label: string): Promise<string> {
  const all = await ctx.cookies();
  const tok = all.find((c) => c.name === OPERATOR_COOKIE)?.value;
  expect(tok, `${label}: console_operator_token cookie must be present after login`).toBeTruthy();
  return tok!;
}

/** Base admin-surface headers. `sourceIp` (when set) is injected as the
 *  X-Forwarded-For first hop, which the aspect resolves as the request source IP. */
function headers(token: string, reason: string, sourceIp?: string): Record<string, string> {
  const h: Record<string, string> = {
    Authorization: `Bearer ${token}`,
    'X-Operator-Reason': encodeURIComponent(reason),
    'Content-Type': 'application/json',
  };
  if (sourceIp) {
    h['X-Forwarded-For'] = sourceIp;
  }
  return h;
}

async function codeOf(res: APIResponse): Promise<string | undefined> {
  try {
    return ((await res.json()) as { code?: string }).code;
  } catch {
    return undefined;
  }
}

/**
 * Re-issue a request while it returns a transient infra 500/503 (the MONO-207/210
 * lesson: the admin_db `outbox` poller's range PESSIMISTIC_WRITE lock — no SKIP
 * LOCKED — can make the audit→outbox INSERT time out while Kafka warms up). The
 * admin mutations are transactional + idempotent, so re-issuing is safe. A 403 is
 * NOT retried (the gate denial is deterministic, not transient). The `beforeAll`
 * gate below also front-loads the warm-up so this is a no-op under warm conditions.
 */
async function send(fn: () => Promise<APIResponse>): Promise<APIResponse> {
  let res = await fn();
  for (let i = 0; i < 5 && (res.status() === 500 || res.status() === 503); i++) {
    await new Promise((r) => setTimeout(r, 3000));
    res = await fn();
  }
  return res;
}

function assign(ctx: BrowserContext, token: string, sourceIp?: string): Promise<APIResponse> {
  return send(() => ctx.request.post(`${ADMIN_BASE}/api/admin/operators/${TARGET}/assignments/${TENANT}`, {
    headers: headers(token, 'e2e ADR-026 step3 source-ip assign', sourceIp),
  }));
}

function unassign(ctx: BrowserContext, token: string, sourceIp?: string): Promise<APIResponse> {
  return send(() => ctx.request.delete(`${ADMIN_BASE}/api/admin/operators/${TARGET}/assignments/${TENANT}`, {
    headers: headers(token, 'e2e ADR-026 step3 source-ip unassign', sourceIp),
  }));
}

/** GET the target's assignment row(s) filtered to TENANT (X-Tenant-Id scoping).
 *  A read — never gated by the mutation-only SOURCE_IP condition. */
function listAssignments(ctx: BrowserContext, token: string, sourceIp?: string): Promise<APIResponse> {
  const h = headers(token, 'e2e ADR-026 step3 source-ip list', sourceIp);
  h['X-Tenant-Id'] = TENANT;
  return send(() => ctx.request.get(`${ADMIN_BASE}/api/admin/operators/${TARGET}/assignments`, { headers: h }));
}

async function assignmentCount(ctx: BrowserContext, token: string, sourceIp?: string): Promise<number> {
  const res = await listAssignments(ctx, token, sourceIp);
  expect(res.status(), 'list assignments (a read) is never gated by the SOURCE_IP condition').toBe(200);
  // Wire shape: OperatorAssignmentListResponse(assignments) — the JSON key is
  // `assignments` (BE-339), a list of 0 or 1 element scoped to X-Tenant-Id.
  const body = (await res.json()) as { assignments?: unknown[] };
  return Array.isArray(body.assignments) ? body.assignments.length : 0;
}

/** Best-effort teardown — never asserts (tolerates 404/transient). In-range so the
 *  cleanup itself is never gated. */
async function quietUnassign(ctx: BrowserContext, token: string): Promise<void> {
  await unassign(ctx, token, IP_IN_RANGE).catch(() => {});
}

test.describe('ADR-MONO-026 step 3 — iam admin SOURCE_IP access condition (federation runtime proof)', () => {
  // Each test drives a chain of admin-RBAC calls; run serially so the three tests
  // never race on the single (ip-pilot-target, ip-pilot-corp) assignment row.
  test.describe.configure({ mode: 'serial' });

  // Outbox warm-up gate (MONO-207/210 pattern): a cold federation stack's admin_db
  // `outbox` poller can hold its range PESSIMISTIC_WRITE lock long enough (while
  // Kafka warms up) to make audit→outbox INSERTs time out (→ 500). Block here,
  // using an IN-RANGE assign/unassign (so the gate itself never denies the warm-up),
  // until an admin write succeeds — so the real assertions run against a warm stack.
  test.beforeAll(async ({ browser }) => {
    test.setTimeout(240_000);
    const ctx = await browser.newContext({ storageState: STORAGE_STATE });
    try {
      const tok = await operatorToken(ctx, 'warm-up SUPER_ADMIN');
      let warm = false;
      for (let i = 0; i < 12 && !warm; i++) {
        // in-range assign (writes audit→outbox); 201/409 ⇒ writable.
        const res = await assign(ctx, tok, IP_IN_RANGE);
        if (res.status() === 201 || res.status() === 409) {
          warm = true;
        } else {
          // eslint-disable-next-line no-console
          console.log(`[MONO-221] outbox warm-up attempt ${i + 1}: assign http=${res.status()}`);
          await new Promise((r) => setTimeout(r, 4000));
        }
      }
      await quietUnassign(ctx, tok);
      expect(warm, 'admin outbox must become writable (Kafka/poller warm) before the proof').toBe(true);
    } finally {
      await ctx.close();
    }
  });

  test('gated: an RBAC-granted mutation from an out-of-range source IP → 403 ACCESS_CONDITION_UNMET, not executed', async ({ browser }) => {
    test.setTimeout(120_000);
    const ctx = await browser.newContext({ storageState: STORAGE_STATE });
    let token = '';
    try {
      token = await operatorToken(ctx, 'SUPER_ADMIN(global-setup)');
      // Defensive baseline — a prior aborted run may have left the row assigned.
      await quietUnassign(ctx, token);
      expect(await assignmentCount(ctx, token, IP_IN_RANGE),
        'baseline: target not assigned to ip-pilot-corp').toBe(0);

      // ── the 4th gate bites: out-of-range source IP → 403 ACCESS_CONDITION_UNMET ──
      const gated = await assign(ctx, token, IP_OUT_OF_RANGE);
      expect(gated.status(), 'out-of-range admin mutation is gated by the SOURCE_IP condition').toBe(403);
      expect(await codeOf(gated), 'denial code is the access-condition gate, not the RBAC gate').toBe('ACCESS_CONDITION_UNMET');

      // ── the RBAC-granted mutation did NOT execute (no assignment row created) ──
      expect(await assignmentCount(ctx, token, IP_IN_RANGE),
        'the gated mutation must not have created the assignment row').toBe(0);
    } finally {
      if (token) await quietUnassign(ctx, token);
      await ctx.close();
    }
  });

  test('unaffected: the SAME mutation from an in-range source IP → 201 / 204 (gate does not disturb a legitimate operator)', async ({ browser }) => {
    test.setTimeout(120_000);
    const ctx = await browser.newContext({ storageState: STORAGE_STATE });
    let token = '';
    try {
      token = await operatorToken(ctx, 'SUPER_ADMIN(global-setup)');
      await quietUnassign(ctx, token); // defensive baseline

      // ── in-range source IP → the gate is satisfied → the mutation proceeds ──
      const created = await assign(ctx, token, IP_IN_RANGE);
      expect(created.status(), 'in-range admin mutation proceeds (gate satisfied, RBAC granted)').toBe(201);

      expect(await assignmentCount(ctx, token, IP_IN_RANGE),
        'the in-range mutation created the assignment row').toBe(1);

      // ── reversible: unassign (in-range) → 204 ──
      const removed = await unassign(ctx, token, IP_IN_RANGE);
      expect(removed.status(), 'in-range unassign proceeds').toBe(204);
    } finally {
      if (token) await quietUnassign(ctx, token);
      await ctx.close();
    }
  });

  test('mutation-only: a GET read from the blocked (out-of-range) source IP → 200 (reads are never gated)', async ({ browser }) => {
    test.setTimeout(90_000);
    const ctx = await browser.newContext({ storageState: STORAGE_STATE });
    let token = '';
    try {
      token = await operatorToken(ctx, 'SUPER_ADMIN(global-setup)');

      // A GET, with the SAME out-of-range X-Forwarded-For that gated the mutation
      // above, is NOT gated — the SOURCE_IP condition is mutation-only.
      const read = await listAssignments(ctx, token, IP_OUT_OF_RANGE);
      expect(read.status(), 'a read from the blocked IP is never gated (mutation-only restriction)').toBe(200);
    } finally {
      await ctx.close();
    }
  });
});
