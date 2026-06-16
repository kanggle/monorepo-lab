import path from 'node:path';
import {
  expect,
  type APIResponse,
  type Browser,
  type BrowserContext,
} from '@playwright/test';

/**
 * TASK-MONO-280 — shared admin-surface helpers for the federation-hardening-e2e
 * admin RBAC / access-condition specs.
 *
 * Extracted (behavior-preserving) from the byte-identical private copies that
 * lived in tenant-admin-delegation.spec.ts (MONO-210),
 * iam-admin-source-ip-condition.spec.ts (MONO-221) and
 * iam-admin-resource-tag-condition.spec.ts (MONO-228); the constants are also
 * shared with subscription-plane-separation.spec.ts (MONO-207). The per-spec
 * request builders that embed the endpoint / reason / params stay in each spec
 * and call the shared `send` / `headers` from here — see each spec's header for
 * its authority-model rationale.
 */

/** admin-service host base URL (docker-compose maps 18085:8085); the
 *  federation-hardening-e2e.yml workflow exports E2E_ADMIN_BASE_URL. The admin
 *  RBAC surface is called directly with the exchanged operator token. */
export const ADMIN_BASE = process.env.E2E_ADMIN_BASE_URL ?? 'http://localhost:18085';

/** The persisted global-setup SUPER_ADMIN session (playwright.config storageState):
 *  loaded by an admin context (no in-test login → no tracing.start collision);
 *  carries the exchanged operator token. */
export const STORAGE_STATE = path.join(__dirname, '.storage-state.json');

/** session.ts cookie name (HttpOnly — readable via BrowserContext.cookies()). */
export const OPERATOR_COOKIE = 'console_operator_token'; // the /api/admin/** credential

/** Read + assert the exchanged `console_operator_token` cookie from a context. */
export async function operatorToken(ctx: BrowserContext, label: string): Promise<string> {
  const all = await ctx.cookies();
  const tok = all.find((c) => c.name === OPERATOR_COOKIE)?.value;
  expect(tok, `${label}: console_operator_token cookie must be present after login`).toBeTruthy();
  return tok!;
}

/** Base admin-surface headers. `sourceIp` (when set) is injected as the
 *  X-Forwarded-For first hop, which RequiresPermissionAspect resolves as the
 *  request source IP (used by the SOURCE_IP access-condition spec). */
export function headers(token: string, reason: string, sourceIp?: string): Record<string, string> {
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

/** Extract the `code` field from a JSON error envelope (undefined if not JSON). */
export async function codeOf(res: APIResponse): Promise<string | undefined> {
  try {
    return ((await res.json()) as { code?: string }).code;
  } catch {
    return undefined;
  }
}

/**
 * Re-issue a request while it returns a transient infra 500/503. The admin audit
 * write emits a row into the admin_db `outbox` table; the outbox poller takes a
 * range PESSIMISTIC_WRITE lock (no SKIP LOCKED) and, while Kafka is still warming
 * up on a cold federation stack, can hold it long enough to make the audit INSERT
 * time out (1205 → PessimisticLockingFailureException → 500). The admin mutations
 * are transactional (a failed write rolls back) + idempotent, so re-issuing once
 * the poller releases the lock is safe and deterministic. A 403 is NOT retried
 * (the gate denial is deterministic, not transient). `warmUpAdminOutbox` also
 * front-loads the wait, so under warm conditions this retry is a no-op.
 */
export async function send(fn: () => Promise<APIResponse>): Promise<APIResponse> {
  let res = await fn();
  for (let i = 0; i < 5 && (res.status() === 500 || res.status() === 503); i++) {
    await new Promise((r) => setTimeout(r, 3000));
    res = await fn();
  }
  return res;
}

/**
 * Outbox warm-up gate (the MONO-207/210/221/228 pattern). A cold federation
 * stack's admin_db `outbox` poller can hold its range PESSIMISTIC_WRITE lock long
 * enough (while Kafka warms up) to make audit→outbox INSERTs time out (→ 500).
 * Block, using the SUPER_ADMIN platform credential, until an admin write `probe`
 * (which exercises the audit→outbox path) returns an `accept`-listed status — so
 * the real assertions run against a warm, writable stack. Call from `beforeAll`
 * (set the test timeout there). `cleanup` (when given) runs before the assert so a
 * throwaway warm-up write is undone. Loops up to 12 times, 4s between attempts.
 */
export async function warmUpAdminOutbox(
  browser: Browser,
  opts: {
    probe: (ctx: BrowserContext, token: string) => Promise<APIResponse>;
    accept: number[];
    logPrefix: string;
    cleanup?: (ctx: BrowserContext, token: string) => Promise<void>;
  },
): Promise<void> {
  const ctx = await browser.newContext({ storageState: STORAGE_STATE });
  try {
    const tok = await operatorToken(ctx, 'warm-up SUPER_ADMIN');
    let warm = false;
    for (let i = 0; i < 12 && !warm; i++) {
      const res = await opts.probe(ctx, tok);
      if (opts.accept.includes(res.status())) {
        warm = true;
      } else {
        // eslint-disable-next-line no-console
        console.log(`[${opts.logPrefix}] outbox warm-up attempt ${i + 1}: probe http=${res.status()}`);
        await new Promise((r) => setTimeout(r, 4000));
      }
    }
    if (opts.cleanup) await opts.cleanup(ctx, tok);
    expect(warm, 'admin outbox must become writable (Kafka/poller warm) before the proof').toBe(true);
  } finally {
    await ctx.close();
  }
}
