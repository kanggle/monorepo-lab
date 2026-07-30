import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-259 — the mechanical guard the layer rule never had.
 *
 * `specs/services/console-web/architecture.md`:
 *
 *   § Allowed Dependencies
 *     | `features/<F>/` | 자체 폴더 내부, `shared/*` |
 *     | `shared/`       | 자체만 (features / app 금지) |
 *     의존성은 위에서 아래로만 흐른다 (`app/ → features/ → shared/`).
 *     같은 계층 `features/A → features/B` 상호 참조 금지
 *     (공유 가치는 `shared/` 로 승격).
 *
 *   § Forbidden Dependencies
 *     - `features/A` → `features/B` 상호 참조
 *
 * That rule had **no automated enforcement**, which is exactly how 17
 * violations across 8 feature pairs accumulated before the TASK-PC-FE-259
 * audit found them — and 14 of the 17 were additionally "deep" imports that
 * bypassed the target feature's public `index.ts` barrel. A rule only one
 * document states, and no test checks, is a rule that drifts.
 *
 * These two greps run against the REAL on-disk source (not a mock), so a new
 * cross-layer import fails CI at the moment it is written.
 *
 * ── IF THIS TEST FAILS ──
 * Do NOT add an exception here. Apply the rule's own remedy to the offending
 * edge:
 *   - the value is genuinely shared → promote it to `shared/` and have BOTH
 *     features import it from there (precedent: `shared/api/rbac-catalog.ts`,
 *     `shared/lib/money.ts`, `shared/api/iam-{accounts,audit,operators}-read.ts`);
 *   - the value is route-level composition (e.g. a registry eligibility
 *     pre-flight) → it belongs in `app/`, which IS allowed to import
 *     `features/*` (precedent: `app/(console)/erp/_eligibility.ts`,
 *     `app/(console)/ecommerce/products/_eligibility.ts`);
 *   - the value is a producer wire type an aggregator merely borrowed → give
 *     the aggregator its own view-model (precedent:
 *     `IamOverviewAuditRow` in `features/iam-overview`).
 */

const SRC = path.resolve(__dirname, '../../src');

function walk(dir: string): string[] {
  const out: string[] = [];
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name);
    if (statSync(full).isDirectory()) out.push(...walk(full));
    else out.push(full);
  }
  return out;
}

function sourceFiles(root: string): string[] {
  return walk(root).filter((f) => f.endsWith('.ts') || f.endsWith('.tsx'));
}

/**
 * Blanks out comment lines (block-comment bodies, `/* … *\/` openers/closers and
 * `//` lines) while PRESERVING line count, so a `@/features/...` mention inside
 * a doc comment never counts. Several promoted `shared/` modules deliberately
 * cite the feature path they moved away from — documentation, not a dependency.
 */
function stripCommentLines(src: string): string {
  let inBlock = false;
  return src
    .split(/\r?\n/)
    .map((line) => {
      const t = line.trim();
      if (inBlock) {
        if (t.includes('*/')) inBlock = false;
        return '';
      }
      if (t.startsWith('/*')) {
        if (!t.includes('*/')) inBlock = true;
        return '';
      }
      if (t.startsWith('//') || t.startsWith('*')) return '';
      return line;
    })
    .join('\n');
}

/**
 * Matches a real `import … from '@/features/<f>'` / `export … from '@/features/<f>'`
 * statement, single- or multi-line.
 *
 * `m` + `^[ \t]*(?:import|export)\b` anchors on a LINE that starts a statement;
 * `[^;]*?` (which spans newlines) then reaches the `from` clause of that same
 * statement, so `import {\n  X,\n} from '@/features/y';` is caught.
 *
 * ⚠️ Calibration note — the first version of this guard split the file on `;`
 * and anchored `^\s*` inside each chunk. That predicate silently FAILED to
 * detect an injected violation, because a chunk following a block that ends in
 * `}` (any function or object literal) starts with `}\nimport …`, which `^\s*`
 * cannot skip. A guard is only worth its green if you have watched it go red:
 * if you change this matcher, re-run the injected-violation probe below.
 */
const FEATURE_IMPORT =
  /^[ \t]*(?:import|export)\b[^;]*?from\s+['"]@\/features\/([^/'"]+)/gm;

function featureImportsIn(file: string): { line: number; target: string }[] {
  const hits: { line: number; target: string }[] = [];
  const src = stripCommentLines(readFileSync(file, 'utf8'));
  FEATURE_IMPORT.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = FEATURE_IMPORT.exec(src)) !== null) {
    const line = src.slice(0, m.index).split('\n').length;
    hits.push({ line, target: m[1] });
  }
  return hits;
}

const SHARED_UPWARD_IMPORT =
  /^[ \t]*(?:import|export)\b[^;]*?from\s+['"]@\/(features|app)\/([^/'"]+)/gm;

describe('architecture.md § Allowed / Forbidden Dependencies (TASK-PC-FE-259)', () => {
  it('no feature imports another feature (deep OR through its barrel)', () => {
    const featuresRoot = path.join(SRC, 'features');
    const files = sourceFiles(featuresRoot);
    // Self-check: if the walk silently returned nothing the grep would pass
    // vacuously (an empty detector's 0 is not an absence).
    expect(files.length).toBeGreaterThan(100);

    const offenders: string[] = [];
    for (const file of files) {
      const rel = path.relative(featuresRoot, file).replace(/\\/g, '/');
      const owner = rel.split('/')[0];
      for (const hit of featureImportsIn(file)) {
        // A feature importing from its OWN subtree via the alias is fine.
        if (hit.target === owner) continue;
        offenders.push(
          `features/${rel}:${hit.line} → @/features/${hit.target}`,
        );
      }
    }

    expect(offenders).toEqual([]);
  });

  it('shared/ imports no feature and no app route (dependencies flow one way)', () => {
    const sharedRoot = path.join(SRC, 'shared');
    const files = sourceFiles(sharedRoot);
    expect(files.length).toBeGreaterThan(20);

    const offenders: string[] = [];
    for (const file of files) {
      const rel = path.relative(sharedRoot, file).replace(/\\/g, '/');
      const src = stripCommentLines(readFileSync(file, 'utf8'));
      SHARED_UPWARD_IMPORT.lastIndex = 0;
      let m: RegExpExecArray | null;
      while ((m = SHARED_UPWARD_IMPORT.exec(src)) !== null) {
        const line = src.slice(0, m.index).split('\n').length;
        offenders.push(`shared/${rel}:${line} → @/${m[1]}/${m[2]}`);
      }
    }

    expect(offenders).toEqual([]);
  });

  /**
   * Calibration — proves the matcher above actually BITES, on the exact shape
   * the real violations had (a deep import placed after a closing brace, which
   * defeated this guard's first predicate). Without this, a broken detector
   * would report a permanently green "0 violations".
   */
  it('the detector itself catches a deep cross-feature import (guard calibration)', () => {
    const probe = [
      "import { cn } from '@/shared/lib/cn';",
      '',
      'export function existing() {',
      '  return cn();',
      '}',
      // The shape that broke the first version: a statement whose preceding
      // chunk ends in `}` rather than `;`.
      "import type { Department } from '@/features/erp-ops/api/types';",
      '',
      '/**',
      " * A doc-comment mention of '@/features/audit/api/types' must NOT count.",
      ' */',
      "// import { queryAudit } from '@/features/audit/api/audit-api';",
      "export { something } from '@/features/catalog';",
    ].join('\n');

    const stripped = stripCommentLines(probe);
    FEATURE_IMPORT.lastIndex = 0;
    const targets: string[] = [];
    let m: RegExpExecArray | null;
    while ((m = FEATURE_IMPORT.exec(stripped)) !== null) targets.push(m[1]);

    // Both real statements caught; neither commented mention counted.
    expect(targets).toEqual(['erp-ops', 'catalog']);
  });
});
