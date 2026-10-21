import { describe, expect, it } from 'vitest';
import path from 'node:path';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { chartMagnitude } from '@/shared/lib/chart-scale';

/**
 * TASK-PC-FE-359 — the F5 chart-geometry exception and its guard.
 *
 * Spec: `console-web/architecture.md` § Integration Rules «F5 의 유일한 예외»
 * + contract § 2.4.7 «Sole exception» (owner decision 2026-10-22 KST): ONE
 * function — `shared/lib/chart-scale.ts` `chartMagnitude` — may turn a money /
 * rate decimal string into a `Number`, for chart geometry only.
 *
 * Why a new guard rather than only the four existing ones
 * (`ledger-api` / `finance-api` / `ledger-account-api` / `ledger-statement-api`):
 * those flag a coercion call only on a line that ALSO names `amount` or
 * `exchangeRate`. `Number(q.rate)` or `Number(lot.carryingBaseMinor)` names
 * neither, so it passed all four (measured in the ticket's AC-0). This guard
 * is stricter and simpler: on the F5 surfaces NO executable line calls
 * `Number(` / `parseFloat(` / `parseInt(` at all — measured 0 before this
 * ticket — and the allow-list is exactly one file holding exactly one call.
 */

const SRC = path.resolve(__dirname, '../../src');

/** The F5 surfaces: the finance / ledger features + every `shared/` module the
 *  F5 primitives and ledger reads were promoted into (TASK-PC-FE-259). */
const F5_ROOTS = [
  'features/finance-ops',
  'features/ledger-ops',
  'features/finance-overview',
  'shared/lib/money.ts',
  'shared/lib/chart-scale.ts',
  'shared/api/ledger-types',
  'shared/api/ledger-client.ts',
  'shared/api/ledger-overview-read.ts',
  'shared/api/finance-accounts-read.ts',
  'shared/api/finance-accounts-types.ts',
];

/** The allow-list — exactly one file (repo-relative to `src/`, `/`-separated). */
const F5_ALLOW_LIST = ['shared/lib/chart-scale.ts'] as const;

const COERCION = /\b(?:Number|parseFloat|parseInt)\s*\(/g;

/** Drops comments so documentation that names the forbidden calls is not
 *  flagged (block-comment lines, `//` tails, inline and JSX `/* … *\/`). */
function executablePart(line: string): string {
  const t = line.trim();
  if (t.startsWith('*') || t.startsWith('//') || t.startsWith('/*')) return '';
  return line.replace(/\/\*.*?\*\//g, '').replace(/\/\/.*$/, '');
}

/** Coercion calls on executable lines of one file's text. */
function coercionLines(rel: string, text: string): string[] {
  const out: string[] = [];
  text.split(/\r?\n/).forEach((line, i) => {
    const code = executablePart(line);
    const hits = code.match(COERCION);
    hits?.forEach(() => out.push(`${rel}:${i + 1}: ${line.trim()}`));
  });
  return out;
}

/** The guard's verdict over a set of `{rel, text}` files. */
function f5Offenders(files: readonly { rel: string; text: string }[]): string[] {
  return files
    .filter((f) => !(F5_ALLOW_LIST as readonly string[]).includes(f.rel))
    .flatMap((f) => coercionLines(f.rel, f.text));
}

function walk(abs: string): string[] {
  if (!statSync(abs).isDirectory()) return [abs];
  return readdirSync(abs).flatMap((n) => walk(path.join(abs, n)));
}

function scannedFiles(): { rel: string; text: string }[] {
  return F5_ROOTS.flatMap((r) => walk(path.join(SRC, r)))
    .filter((f) => f.endsWith('.ts') || f.endsWith('.tsx'))
    .map((f) => ({
      rel: path.relative(SRC, f).replace(/\\/g, '/'),
      text: readFileSync(f, 'utf8'),
    }));
}

describe('F5 guard — the chart-geometry exception is one file, one call (TASK-PC-FE-359)', () => {
  const files = scannedFiles();

  it('scans the F5 surfaces, including every allow-listed file (reachability)', () => {
    expect(files.length).toBeGreaterThan(50);
    const rels = files.map((f) => f.rel);
    for (const allowed of F5_ALLOW_LIST) expect(rels).toContain(allowed);
    expect(rels).toContain('features/ledger-ops/components/FxRateHistoryChart.tsx');
    expect(rels).toContain('shared/api/ledger-types/fx.ts');
  });

  it('no executable line outside the allow-list calls Number( / parseFloat( / parseInt(', () => {
    expect(f5Offenders(files)).toEqual([]);
  });

  it('the allow-listed file holds exactly ONE coercion call (the exception cannot grow inside it)', () => {
    const chart = files.find((f) => f.rel === 'shared/lib/chart-scale.ts')!;
    expect(coercionLines(chart.rel, chart.text)).toHaveLength(1);
  });

  it('bite — a money / rate coercion outside the allow-list is flagged, comments are not', () => {
    const planted = [
      'const y = Number(q.rate);',
      'const w = parseFloat(lot.carryingBaseMinor);',
      'const n = parseInt(money.amount, 10);',
      'return Number (line.exchangeRate) * 2;',
    ].join('\n');
    const docs = [
      '// NEVER Number(q.rate)',
      ' * no `Number(...)` / `parseFloat(...)` on amount',
      '{/* F5: rate is rendered as-is (string). NEVER Number()/parseFloat(). */}',
    ].join('\n');
    expect(
      f5Offenders([{ rel: 'features/ledger-ops/components/Planted.tsx', text: planted }]),
    ).toHaveLength(4);
    expect(
      f5Offenders([{ rel: 'features/ledger-ops/components/Docs.tsx', text: docs }]),
    ).toEqual([]);
    // The same planted text is accepted ONLY at the allow-listed path —
    // a sibling path in the same directory still bites.
    expect(f5Offenders([{ rel: 'shared/lib/chart-scale.ts', text: planted }])).toEqual([]);
    expect(
      f5Offenders([{ rel: 'shared/lib/chart-scale-extra.ts', text: planted }]),
    ).toHaveLength(4);
  });
});

describe('chartMagnitude — decimal string → geometry magnitude', () => {
  it('normal decimals, including the producer\'s 8-dp rate strings', () => {
    expect(chartMagnitude('1300.00000000')).toBe(1300);
    expect(chartMagnitude('1298.5')).toBe(1298.5);
    expect(chartMagnitude('0.00076923')).toBeCloseTo(0.00076923, 10);
    expect(chartMagnitude('.5')).toBe(0.5);
    expect(chartMagnitude(' 42 ')).toBe(42);
  });

  it('negative values stay negative (the caller decides how to draw them)', () => {
    expect(chartMagnitude('-5000')).toBe(-5000);
    expect(chartMagnitude('+7')).toBe(7);
  });

  it('exponent notation is accepted', () => {
    expect(chartMagnitude('1.3e3')).toBe(1300);
    expect(chartMagnitude('5E-2')).toBe(0.05);
  });

  it('empty / non-decimal / non-string → null (not drawn)', () => {
    expect(chartMagnitude('')).toBeNull();
    expect(chartMagnitude('   ')).toBeNull();
    expect(chartMagnitude('abc')).toBeNull();
    expect(chartMagnitude('1,300')).toBeNull();
    expect(chartMagnitude('12.3.4')).toBeNull();
    expect(chartMagnitude('Infinity')).toBeNull();
    expect(chartMagnitude('NaN')).toBeNull();
    expect(chartMagnitude('0x10')).toBeNull();
    expect(chartMagnitude(null)).toBeNull();
    expect(chartMagnitude(1300)).toBeNull();
  });

  it('very large values: finite → approximate magnitude; beyond double range → null', () => {
    expect(chartMagnitude('9007199254740993')).toBe(9007199254740992);
    expect(chartMagnitude('1e400')).toBeNull();
  });
});
