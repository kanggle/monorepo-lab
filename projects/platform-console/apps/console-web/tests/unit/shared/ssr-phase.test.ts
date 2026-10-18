import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, it, expect, vi, beforeEach } from 'vitest';

/**
 * TASK-PC-FE-341 — `console_ssr_phase`: one structured line per server-side
 * phase of the overview render. Pure timing: value / error pass through
 * unchanged, and the line carries no token, id, email, URL or body (R1).
 */

const logs: { level: string; event: string; fields: Record<string, unknown> }[] = [];
vi.mock('@/shared/lib/logger', () => ({
  logger: {
    info: (event: string, fields: Record<string, unknown>) =>
      logs.push({ level: 'info', event, fields }),
    warn: (event: string, fields: Record<string, unknown>) =>
      logs.push({ level: 'warn', event, fields }),
    error: (event: string, fields: Record<string, unknown>) =>
      logs.push({ level: 'error', event, fields }),
    debug: () => undefined,
  },
}));

import { timedPhase } from '@/shared/lib/ssr-phase';

beforeEach(() => {
  logs.length = 0;
});

describe('timedPhase', () => {
  it('returns the value unchanged and logs one info line with the documented fields', async () => {
    const value = { token: 'SECRET-TOKEN', email: 'op@example.com' };
    const out = await timedPhase('/dashboards/overview', 'registry', async () => value);

    expect(out).toBe(value);
    expect(logs).toHaveLength(1);
    expect(logs[0].level).toBe('info');
    expect(logs[0].event).toBe('console_ssr_phase');
    expect(Object.keys(logs[0].fields).sort()).toEqual(
      ['latencyMs', 'phase', 'route', 'startedAt', 'status'].sort(),
    );
    expect(logs[0].fields).toMatchObject({
      route: '/dashboards/overview',
      phase: 'registry',
      status: 'ok',
    });
    expect(typeof logs[0].fields.latencyMs).toBe('number');
    expect(Number.isNaN(Date.parse(String(logs[0].fields.startedAt)))).toBe(false);
    // R1 — nothing from the resolved value reaches the line.
    const line = JSON.stringify(logs[0]);
    expect(line).not.toContain('SECRET-TOKEN');
    expect(line).not.toContain('op@example.com');
  });

  it('measures elapsed time', async () => {
    vi.useFakeTimers();
    try {
      const p = timedPhase('r', 'slow', () => new Promise<void>((r) => setTimeout(r, 250)));
      await vi.advanceTimersByTimeAsync(250);
      await p;
      expect(logs[0].fields.latencyMs).toBeGreaterThanOrEqual(250);
    } finally {
      vi.useRealTimers();
    }
  });

  it('rethrows the very same error, logging warn with httpStatus + errorName but not the message', async () => {
    const err = Object.assign(new Error('Bearer abc.def.ghi for acct-123'), {
      name: 'ApiError',
      status: 503,
    });
    await expect(
      timedPhase('(console)/layout', 'me_roles', async () => {
        throw err;
      }),
    ).rejects.toBe(err);

    expect(logs).toHaveLength(1);
    expect(logs[0].level).toBe('warn');
    expect(logs[0].fields).toMatchObject({
      status: 'error',
      httpStatus: 503,
      errorName: 'ApiError',
      phase: 'me_roles',
    });
    const line = JSON.stringify(logs[0]);
    expect(line).not.toContain('abc.def.ghi');
    expect(line).not.toContain('acct-123');
  });

  it("classifies Next's redirect() as status 'redirect', not an error", async () => {
    const redirectErr = Object.assign(new Error('NEXT_REDIRECT'), {
      digest: 'NEXT_REDIRECT;replace;/login?error=session_expired;307;',
    });
    await expect(
      timedPhase('/dashboards/overview', 'page_total', async () => {
        throw redirectErr;
      }),
    ).rejects.toBe(redirectErr);

    expect(logs[0].level).toBe('info');
    expect(logs[0].fields.status).toBe('redirect');
    expect(JSON.stringify(logs[0])).not.toContain('session_expired');
  });
});

describe('call sites', () => {
  const root = path.resolve(__dirname, '../../../src/app/(console)');
  const layout = readFileSync(path.join(root, 'layout.tsx'), 'utf8');
  const page = readFileSync(path.join(root, 'dashboards/overview/page.tsx'), 'utf8');

  it('the shell layout times its data fetches and its own total', () => {
    for (const phase of [
      'layout_total',
      'demo_stop_check',
      'me_roles',
      'me_domain_access',
      'registry',
      'org_nodes',
    ]) {
      expect(layout).toContain(`'${phase}'`);
    }
  });

  it('the overview page times its three fetches and its own total', () => {
    for (const phase of ['page_total', 'overview_state', 'registry', 'domain_health']) {
      expect(page).toContain(`'${phase}'`);
    }
  });
});
