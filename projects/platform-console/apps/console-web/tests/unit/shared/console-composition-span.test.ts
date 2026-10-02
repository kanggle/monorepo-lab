import { describe, it, expect, vi } from 'vitest';

/**
 * Per-leg trace span (TASK-PC-FE-302 — contract § 2.4.9 Observability: «per-leg
 * span carries domain + route attributes», previously the retired BFF's
 * `bff.fanout.leg`). The federation trace spec gates the joined tree in a real
 * stack; this cell pins the span's name, attributes and closure without one.
 */

const spans: {
  name: string;
  attributes: Record<string, unknown>;
  set: Record<string, unknown>;
  ended: boolean;
}[] = [];

vi.mock('@opentelemetry/api', () => ({
  SpanStatusCode: { ERROR: 2 },
  trace: {
    getTracer: () => ({
      startActiveSpan: (
        name: string,
        opts: { attributes: Record<string, unknown> },
        fn: (span: unknown) => unknown,
      ) => {
        const rec = { name, attributes: opts.attributes, set: {} as Record<string, unknown>, ended: false };
        spans.push(rec);
        return fn({
          setAttribute: (k: string, v: unknown) => {
            rec.set[k] = v;
          },
          setStatus: () => undefined,
          end: () => {
            rec.ended = true;
          },
        });
      },
    }),
  },
}));

vi.mock('@/shared/config/env', () => ({ getServerEnv: () => ({}) }));
vi.mock('@/shared/lib/logger', () => ({
  logger: { info: () => undefined, warn: () => undefined, error: () => undefined },
}));

import { compose, type LegSpec } from '@/shared/composition/console-composition';

describe('per-leg span', () => {
  it('one ended span per leg, tagged composition.domain / composition.route / outcome', async () => {
    const specs: LegSpec[] = [
      { domain: 'iam', kind: 'data', url: 'http://iam.local/a', headers: {} },
      { domain: 'scm', kind: 'data', url: 'http://scm.local/b', headers: {} },
    ];
    const r = await compose(specs, {
      route: 'operator-overview',
      requestId: 'r',
      fetchLeg: async (url) =>
        url.includes('scm')
          ? new Response('{}', { status: 500 })
          : new Response('{"ok":1}', { status: 200 }),
    });
    expect(r.unauthorized).toBe(false);
    expect(spans).toHaveLength(2);
    for (const s of spans) {
      expect(s.name).toBe('console.composition.leg');
      expect(s.attributes['composition.route']).toBe('operator-overview');
      expect(s.ended).toBe(true);
    }
    const by = Object.fromEntries(spans.map((s) => [s.attributes['composition.domain'], s.set]));
    expect(by.iam['composition.outcome']).toBe('ok');
    expect(by.scm['composition.outcome']).toBe('degraded');
  });
});
