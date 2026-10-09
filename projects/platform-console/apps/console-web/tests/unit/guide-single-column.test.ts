import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-328 — every console guide screen (the global /guide and each domain
 * guide) lays its cards out ONE per row, at every width (owner decision 2026-10-09
 * UTC: «두 열씩 표기되는 거 한 열씩»). The guides are read top-to-bottom; a
 * two-column card grid made the reading order zig-zag.
 *
 * Population = every .tsx under `src/features/*-guide/` plus `src/shared/guide/`
 * (the shared cards the domain guides render). Any multi-column grid class there —
 * `grid-cols-2..9`, with or without a breakpoint prefix — turns this red.
 */
const SRC = path.resolve(__dirname, '..', '..', 'src');

function tsxUnder(dir: string): string[] {
  return readdirSync(dir).flatMap((n) => {
    const p = path.join(dir, n);
    if (statSync(p).isDirectory()) return tsxUnder(p);
    return p.endsWith('.tsx') ? [p] : [];
  });
}

const GUIDE_DIRS = [
  ...readdirSync(path.join(SRC, 'features'))
    .filter((n) => n.endsWith('-guide'))
    .map((n) => path.join(SRC, 'features', n)),
  path.join(SRC, 'shared', 'guide'),
];
const FILES = GUIDE_DIRS.flatMap(tsxUnder);
const MULTI_COLUMN = /\bgrid-cols-[2-9]\b/;

describe('console guides are single-column (TASK-PC-FE-328)', () => {
  it('the population is the seven guides + the shared guide cards (non-vacuous)', () => {
    const guides = GUIDE_DIRS.map((d) => path.basename(d));
    for (const g of ['global-guide', 'iam-guide', 'wms-guide', 'scm-guide', 'finance-guide', 'erp-guide', 'ecommerce-guide', 'guide']) {
      expect(guides).toContain(g);
    }
    expect(FILES.length).toBeGreaterThanOrEqual(10);
  });

  it('no guide component uses a multi-column grid', () => {
    const offenders = FILES.filter((f) => MULTI_COLUMN.test(readFileSync(f, 'utf8'))).map((f) =>
      path.relative(SRC, f),
    );
    expect(offenders).toEqual([]);
  });
});
