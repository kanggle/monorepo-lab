import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-013 no-drift guard for the Phase 7 second composition route.
 *
 * The hard invariants in § 2.4.9.2 + the BE Javadoc mandate that the
 * `/api/console/dashboards/domain-health` proxy + the
 * `features/domain-health/` feature module are READ-ONLY and do NOT
 * carry over the operator-token from § 2.4.9.1:
 *
 *   - NO `X-Operator-Token` header (the BFF does not consume it on this
 *     route — D4 scope clarification).
 *   - NO `Idempotency-Key` (no mutation surface).
 *   - NO `X-Operator-Reason` (no audited mutation).
 *   - NO `POST` / `PUT` / `PATCH` / `DELETE` method (GET only).
 *
 * This guard scans every production source file under the two new
 * locations for absence of those literals. Uses the TASK-PC-FE-012
 * CRLF-aware strip-comment pattern so a stray CRLF in a Windows-checked
 * file does not cause a false negative.
 */

const APP_ROOT = path.resolve(__dirname, '..', '..');
const FEATURE_DIR = path.resolve(APP_ROOT, 'src', 'features', 'domain-health');
const PROXY_FILE = path.resolve(
  APP_ROOT,
  'src',
  'app',
  'api',
  'console',
  'dashboards',
  'domain-health',
  'route.ts',
);

function listProductionFiles(root: string): string[] {
  const out: string[] = [];
  function walk(dir: string) {
    for (const entry of readdirSync(dir)) {
      const abs = path.join(dir, entry);
      const st = statSync(abs);
      if (st.isDirectory()) {
        walk(abs);
      } else if (
        st.isFile() &&
        (abs.endsWith('.ts') || abs.endsWith('.tsx')) &&
        !abs.endsWith('.test.ts') &&
        !abs.endsWith('.test.tsx')
      ) {
        out.push(abs);
      }
    }
  }
  walk(root);
  return out;
}

function stripCommentsCrlfSafe(src: string): string {
  // TASK-PC-FE-012 CRLF-aware strip: normalise CRLF → LF first, then
  // remove block + line comments. The trailing assertion in callers
  // confirms no stray \r leaked through.
  return src
    .replace(/\r\n/g, '\n')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '');
}

const FORBIDDEN_LITERALS = [
  'X-Operator-Token',
  'Idempotency-Key',
  'X-Operator-Reason',
] as const;

const FORBIDDEN_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'] as const;

describe('domain-health no-drift — feature module', () => {
  const featureFiles = listProductionFiles(FEATURE_DIR);

  it('feature module has production source files (sanity)', () => {
    expect(featureFiles.length).toBeGreaterThan(0);
  });

  for (const file of FORBIDDEN_LITERALS) {
    it(`no production file in features/domain-health/ contains the literal "${file}"`, () => {
      for (const abs of featureFiles) {
        const raw = readFileSync(abs, 'utf8');
        const stripped = stripCommentsCrlfSafe(raw);
        // CRLF guard — the strip pattern normalised LF.
        expect(stripped).not.toMatch(/\r/);
        expect(
          stripped.includes(file),
          `${path.relative(APP_ROOT, abs)} contains forbidden literal "${file}"`,
        ).toBe(false);
      }
    });
  }

  for (const method of FORBIDDEN_METHODS) {
    it(`no production file in features/domain-health/ contains a "method: '${method}'" or "method: \\"${method}\\""`, () => {
      const pattern = new RegExp(`method:\\s*['"]${method}['"]`);
      for (const abs of featureFiles) {
        const raw = readFileSync(abs, 'utf8');
        const stripped = stripCommentsCrlfSafe(raw);
        expect(stripped).not.toMatch(/\r/);
        expect(
          pattern.test(stripped),
          `${path.relative(APP_ROOT, abs)} contains mutation method ${method}`,
        ).toBe(false);
      }
    });
  }
});

describe('domain-health no-drift — proxy route', () => {
  for (const literal of FORBIDDEN_LITERALS) {
    it(`proxy route does not contain the literal "${literal}"`, () => {
      const raw = readFileSync(PROXY_FILE, 'utf8');
      const stripped = stripCommentsCrlfSafe(raw);
      expect(stripped).not.toMatch(/\r/);
      expect(stripped.includes(literal)).toBe(false);
    });
  }

  for (const method of FORBIDDEN_METHODS) {
    it(`proxy route does not invoke method "${method}"`, () => {
      const raw = readFileSync(PROXY_FILE, 'utf8');
      const stripped = stripCommentsCrlfSafe(raw);
      expect(stripped).not.toMatch(/\r/);
      // method: 'POST' / "POST" — same pattern as feature scan.
      const pattern = new RegExp(`method:\\s*['"]${method}['"]`);
      expect(pattern.test(stripped)).toBe(false);
      // export const POST = ... / export async function POST() — Next.js
      // route handlers register HTTP methods via named exports.
      const exportPattern = new RegExp(
        `export\\s+(?:const|async\\s+function|function)\\s+${method}\\b`,
      );
      expect(exportPattern.test(stripped)).toBe(false);
    });
  }
});

describe('domain-health no-drift — operator-token isolation (deep grep)', () => {
  it('neither feature module nor proxy imports getOperatorToken (the BFF does not consume X-Operator-Token here)', () => {
    const files = [PROXY_FILE, ...listProductionFiles(FEATURE_DIR)];
    for (const abs of files) {
      const raw = readFileSync(abs, 'utf8');
      const stripped = stripCommentsCrlfSafe(raw);
      expect(
        stripped.includes('getOperatorToken'),
        `${path.relative(APP_ROOT, abs)} imports getOperatorToken`,
      ).toBe(false);
    }
  });
});
