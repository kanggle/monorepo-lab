/**
 * 🔴🔴 `shared/sample/**` has no path to a backend (ADR-MONO-074 A3 —
 *      TASK-PC-FE-282 AC-4).
 *
 * The sample router is the thing an anonymous request is answered by. If it
 * could read the session, read the backend env, or call `fetch`, "anonymous
 * visitors cannot reach a backend" would rest on discipline instead of on
 * structure. So:
 *
 *   - every import in `shared/sample/**` is RELATIVE and resolves INSIDE
 *     `shared/sample/` (this rules out `@/shared/config/env`,
 *     `@/shared/lib/session`, `next/headers` and anything that imports them —
 *     not by a deny-list that a new alias could slip past, but by allowing
 *     nothing outside the directory);
 *   - no dynamic `import(` / `require(`;
 *   - no network primitive (`fetch(`, `XMLHttpRequest`, `sendBeacon`);
 *   - no `process.env`.
 */
import { describe, it, expect } from 'vitest';
import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';

const SAMPLE_DIR = path.resolve(__dirname, '..', '..', 'src', 'shared', 'sample');

function walk(dir: string): string[] {
  return readdirSync(dir).flatMap((name) => {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) return walk(full);
    return /\.(ts|tsx)$/.test(name) ? [full] : [];
  });
}

interface Finding {
  file: string;
  problem: string;
}

function inspect(file: string, source: string): Finding[] {
  const findings: Finding[] = [];
  const rel = path.relative(SAMPLE_DIR, file).split(path.sep).join('/');

  const specifiers = [
    ...source.matchAll(/\b(?:import|export)\s[^;]*?\bfrom\s*['"]([^'"]+)['"]/g),
    ...source.matchAll(/\bimport\s*['"]([^'"]+)['"]/g),
  ].map((m) => m[1]);
  for (const spec of specifiers) {
    if (!spec.startsWith('.')) {
      findings.push({ file: rel, problem: `non-relative import '${spec}'` });
      continue;
    }
    const resolved = path.resolve(path.dirname(file), spec);
    if (!resolved.startsWith(SAMPLE_DIR + path.sep) && resolved !== SAMPLE_DIR) {
      findings.push({ file: rel, problem: `import leaves shared/sample: '${spec}'` });
    }
  }
  if (/\bimport\s*\(/.test(source)) findings.push({ file: rel, problem: 'dynamic import(' });
  if (/\brequire\s*\(/.test(source)) findings.push({ file: rel, problem: 'require(' });
  if (/(?<![\w.$`'"])fetch\s*\(|\bXMLHttpRequest\b|\bsendBeacon\s*\(|\b(?:globalThis|window|self)\.fetch\b/.test(source)) {
    findings.push({ file: rel, problem: 'network primitive' });
  }
  if (/\bprocess\.env\b/.test(source)) findings.push({ file: rel, problem: 'process.env' });
  return findings;
}

describe('shared/sample/** isolation (AC-4)', () => {
  const files = walk(SAMPLE_DIR);

  it('🔵 non-vacuity — the router, the ledger and the fixtures were scanned', () => {
    const names = files.map((f) => path.basename(f));
    expect(names).toEqual(expect.arrayContaining(['router.ts', 'coverage.ts', 'codes.ts']));
    expect(files.length).toBeGreaterThanOrEqual(6);
  });

  it('🔵 the inspector bites — a forbidden import, a fetch and process.env are each found', () => {
    const fake = path.join(SAMPLE_DIR, 'x.ts');
    expect(inspect(fake, "import { getServerEnv } from '@/shared/config/env';")).toHaveLength(1);
    expect(inspect(fake, "import { isSampleVisitor } from '../lib/session';")).toHaveLength(1);
    expect(inspect(fake, 'const r = await fetch(url);')).toHaveLength(1);
    expect(inspect(fake, 'const u = process.env.X;')).toHaveLength(1);
    expect(inspect(fake, "import { A } from './codes';")).toHaveLength(0);
  });

  it('🔴🔴 no file under shared/sample reaches outside it or the network', () => {
    const findings = files.flatMap((f) => inspect(f, readFileSync(f, 'utf8')));
    expect(findings).toEqual([]);
  });
});
