import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * TASK-PC-FE-341 — every domain base the overview / domain-health / notification-inbox composition
 * reads must be set for `console-web` in `docker-compose.e2e.yml`.
 *
 * Why: ecommerce was the one base left off that list, so the e2e stack ran it on its code default
 * `http://ecommerce.local/...` — unresolvable there. Every ecommerce leg waited out the 4 s leg
 * timeout and, once per nightly, an overview render's in-process self-fetch died at ~10 s with
 * `UND_ERR_CONNECT_TIMEOUT` (the «개요 첫 렌더 지연» flakes). Measured by manipulation: 3 runs before
 * (events 1·1·1) vs 3 runs with the base set (0·0·0).
 *
 * The population is DERIVED from the composition sources (`env.<KEY>`), not typed here, so a new
 * domain leg is checked the day it is added.
 */
const APP = process.cwd();
const COMPOSITION_DIR = path.join(APP, 'src/shared/composition');
const COMPOSE = path.join(APP, '../../docker-compose.e2e.yml');

function compositionEnvKeys(): string[] {
  const keys = new Set<string>();
  for (const f of readdirSync(COMPOSITION_DIR).filter((n) => n.endsWith('.ts'))) {
    const src = readFileSync(path.join(COMPOSITION_DIR, f), 'utf8');
    for (const m of src.matchAll(/\benv\.([A-Z][A-Z0-9_]*(?:BASE_URL|_BASE))\b/g)) keys.add(m[1]);
  }
  return [...keys].sort();
}

function consoleWebComposeKeys(): Set<string> {
  const lines = readFileSync(COMPOSE, 'utf8').split(/\r?\n/);
  const start = lines.findIndex((l) => /^ {2}console-web:\s*$/.test(l));
  if (start < 0) throw new Error('console-web service not found in docker-compose.e2e.yml');
  const keys = new Set<string>();
  for (let i = start + 1; i < lines.length; i++) {
    if (/^ {2}[a-z0-9-]+:\s*$/.test(lines[i])) break; // next service
    const m = lines[i].match(/^ {6}([A-Z][A-Z0-9_]*):/);
    if (m) keys.add(m[1]);
  }
  return keys;
}

describe('docker-compose.e2e.yml sets every composition domain base for console-web', () => {
  const population = compositionEnvKeys();

  it('non-vacuous: the composition reads at least the six domain bases', () => {
    // iam · wms · scm · finance · erp · ecommerce (TASK-PC-FE-302). A shrinking count means the
    // extractor stopped seeing the legs, not that the legs went away.
    expect(population.length).toBeGreaterThanOrEqual(6);
    expect(population).toContain('ECOMMERCE_ADMIN_BASE_URL');
  });

  it('non-vacuous: the compose parser sees console-web keys it is known to carry', () => {
    const keys = consoleWebComposeKeys();
    expect(keys.has('NEXT_PUBLIC_APP_URL')).toBe(true);
    expect(keys.has('FINANCE_BASE_URL')).toBe(true);
  });

  it('every composition base is set (a missing one falls back to an unresolvable *.local default)', () => {
    const keys = consoleWebComposeKeys();
    const missing = population.filter((k) => !keys.has(k));
    expect(missing, `set these for console-web in docker-compose.e2e.yml (non-routable is fine, e.g. http://127.0.0.1:9)`).toEqual([]);
  });
});
