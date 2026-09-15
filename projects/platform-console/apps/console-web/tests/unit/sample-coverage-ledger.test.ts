/**
 * Sample coverage ledger ↔ the source tree (ADR-MONO-074 A9 — TASK-PC-FE-282 AC-9).
 *
 * «Which GET has sample data» must not grow empty cells silently. The inventory
 * is REBUILT from the code on every run and compared with the ledger in both
 * directions:
 *
 *   - surfaces: every gateway profile `logPrefix` literal (+ the ecommerce slice
 *     labels that compose `ecommerce_<event>`), the registry, and every GET
 *     console-bff proxy route. A new client profile without a ledger row → red.
 *   - screens: every `src/app/(console)/**\/page.tsx` route. A new screen without
 *     a ledger row → red. (ADR-MONO-074 § Consequences: «a new screen must bring
 *     its sample fixture — otherwise the A9 ledger goes red».)
 *
 * And the ledger's promises are checked against the router: `ready` → 200 from
 * a fixture, `pending` → 503 `SAMPLE_NOT_READY` in that core's envelope, any
 * write → 403 `SAMPLE_READ_ONLY`.
 */
import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import {
  SURFACE_COVERAGE,
  SCREEN_COVERAGE,
  screenStatusFor,
} from '@/shared/sample/coverage';
import { SAMPLE_FIXTURES, SAMPLE_FIXTURE_DOCUMENTS } from '@/shared/sample/fixtures';
import { sampleResponse } from '@/shared/sample/router';
import { SAMPLE_NOT_READY, SAMPLE_READ_ONLY } from '@/shared/sample/codes';

const SRC = path.resolve(__dirname, '..', '..', 'src');

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) return walk(full);
    return [full];
  });
}

function discoverSurfaces(): Set<string> {
  const found = new Set<string>();
  for (const file of walk(SRC).filter((f) => /\.(ts|tsx)$/.test(f))) {
    const src = readFileSync(file, 'utf8');
    for (const m of src.matchAll(/\blogPrefix:\s*'([a-z_]+)'/g)) found.add(m[1]);
    // ecommerce composes `ecommerce_<event>` (bare `ecommerce` when no event).
    if (/logPrefix:\s*label\.event\s*\?\s*`ecommerce_\$\{label\.event\}`\s*:\s*'ecommerce'/.test(src)) {
      found.add('ecommerce');
    }
    if (file.includes(`${path.sep}ecommerce-ops${path.sep}api${path.sep}`)) {
      for (const m of src.matchAll(/^\s*event:\s*'([a-z_]+)'/gm)) found.add(`ecommerce_${m[1]}`);
    }
    if (/export\s+async\s+function\s+fetchRegistry\s*\(/.test(src)) found.add('registry');
  }
  // console-bff proxy GET routes: `app/api/console/<group>/<name>/route.ts`.
  const consoleApi = path.join(SRC, 'app', 'api', 'console');
  for (const file of walk(consoleApi).filter((f) => f.endsWith('route.ts'))) {
    const src = readFileSync(file, 'utf8');
    if (!/export\s+async\s+function\s+GET\s*\(/.test(src)) continue;
    const parts = path.relative(consoleApi, path.dirname(file)).split(path.sep);
    found.add(parts[0] === 'notifications' ? `notifications-${parts[1]}` : parts[parts.length - 1]);
  }
  return found;
}

function discoverScreens(): Set<string> {
  const root = path.join(SRC, 'app', '(console)');
  return new Set(
    walk(root)
      .filter((f) => path.basename(f) === 'page.tsx')
      .map((f) => {
        const dir = path.relative(root, path.dirname(f)).split(path.sep).join('/');
        return dir === '' ? '/' : `/${dir}`;
      }),
  );
}

describe('surface ledger ↔ source inventory', () => {
  const discovered = discoverSurfaces();
  const ledger = new Set(SURFACE_COVERAGE.map((r) => r.surface));

  it('🔵 non-vacuity — the inventory found every core family', () => {
    for (const s of ['accounts', 'registry', 'wms', 'ecommerce_order', 'erp', 'scm', 'operator-overview']) {
      expect(discovered.has(s), s).toBe(true);
    }
    expect(discovered.size).toBeGreaterThanOrEqual(30);
  });

  it('🔴 every discovered surface has a ledger row (no silent empty cell)', () => {
    expect([...discovered].filter((s) => !ledger.has(s)).sort()).toEqual([]);
  });

  it('🔴 every ledger row still exists in the code (no stale row)', () => {
    expect([...ledger].filter((s) => !discovered.has(s)).sort()).toEqual([]);
  });

  it('ledger rows are unique', () => {
    expect(ledger.size).toBe(SURFACE_COVERAGE.length);
  });
});

describe('the ledger promises what the router does', () => {
  it.each(SURFACE_COVERAGE.map((r) => [`${r.core}:${r.surface} (${r.status})`, r] as const))(
    '%s',
    async (_label, row) => {
      const get = sampleResponse({ core: row.core, surface: row.surface, method: 'GET', path: '/' });
      if (row.status === 'ready') {
        expect(get.status).toBe(200);
      } else {
        expect(get.status).toBe(503);
        const body = (await get.json()) as { code?: string; error?: { code?: string } };
        const code = row.core === 'wms' ? body.error?.code : body.code;
        expect(code).toBe(SAMPLE_NOT_READY);
      }
      const write = sampleResponse({ core: row.core, surface: row.surface, method: 'PATCH', path: '/' });
      expect(write.status).toBe(403);
      const wbody = (await write.json()) as { code?: string; error?: { code?: string } };
      expect(row.core === 'wms' ? wbody.error?.code : wbody.code).toBe(SAMPLE_READ_ONLY);
    },
  );

  it('every fixture belongs to a ready row and is exposed to the label guard', () => {
    const ready = new Set(
      SURFACE_COVERAGE.filter((r) => r.status === 'ready').map((r) => `${r.core}:${r.surface}`),
    );
    expect(Object.keys(SAMPLE_FIXTURES).sort()).toEqual([...ready].sort());
    expect(Object.keys(SAMPLE_FIXTURE_DOCUMENTS).sort()).toEqual([...ready].sort());
  });
});

describe('screen ledger ↔ (console) pages', () => {
  const pages = discoverScreens();
  const ledger = new Set(Object.keys(SCREEN_COVERAGE));

  it('🔵 non-vacuity', () => {
    expect(pages.has('/dashboards/overview')).toBe(true);
    expect(pages.size).toBeGreaterThanOrEqual(60);
  });

  it('🔴 every (console) page has a screen ledger row', () => {
    expect([...pages].filter((p) => !ledger.has(p)).sort()).toEqual([]);
  });

  it('🔴 every screen ledger row is a real page', () => {
    expect([...ledger].filter((p) => !pages.has(p)).sort()).toEqual([]);
  });

  it('R3ⓐ — the dashboards are ready', () => {
    expect(SCREEN_COVERAGE['/dashboards/overview']).toBe('ready');
    expect(SCREEN_COVERAGE['/dashboards/health']).toBe('ready');
    expect(SCREEN_COVERAGE['/console']).toBe('ready');
  });

  it('dynamic segments and query strings resolve to their row; unknown paths err toward pending', () => {
    expect(screenStatusFor('/ecommerce/orders/ord-1')).toBe('pending');
    expect(screenStatusFor('/dashboards/overview?x=1')).toBe('ready');
    expect(screenStatusFor('/wms/guide/')).toBe('static');
    expect(screenStatusFor('/no-such-screen')).toBe('pending');
  });
});
