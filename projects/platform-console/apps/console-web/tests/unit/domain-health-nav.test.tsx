import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * Console nav-source guard.
 *
 * History: TASK-PC-FE-013 additive guard → TASK-PC-FE-034 overview
 * consolidation → TASK-PC-FE-039 moves the section nav out of the top-bar
 * `(console)/layout.tsx` into a Vercel-style left sidebar
 * (`ConsoleSidebarNav.tsx`) → **TASK-PC-FE-244 splits the nav-tree data
 * (`GROUPS` + node types) out of `ConsoleSidebarNav.tsx` into
 * `console-nav-config.ts`**, so this guard's source-file target is repointed
 * there (the literal `testid: …` / `href: …` entries this guard asserts
 * physically live in `console-nav-config.ts` now; the guard's purpose —
 * pinning the nav taxonomy literals — is unchanged, only the target file
 * follows the moved data).
 *
 * The nav is a client component (usePathname) and the layout a server
 * component — neither mounts cleanly in jsdom without app-router context, so
 * asserting on the source string remains the simplest objective nav guard.
 */

const APP_ROOT = path.resolve(__dirname, '..', '..');
const NAV_PATH = path.resolve(
  APP_ROOT,
  'src',
  'shared',
  'ui',
  'console-nav-config.ts',
);
const LAYOUT_PATH = path.resolve(
  APP_ROOT,
  'src',
  'app',
  '(console)',
  'layout.tsx',
);
const HEALTH_PAGE_PATH = path.resolve(
  APP_ROOT,
  'src',
  'app',
  '(console)',
  'dashboards',
  'health',
  'page.tsx',
);

describe('console nav guard (sidebar — TASK-PC-FE-039 / consolidation TASK-PC-FE-034)', () => {
  it('the layout composes the left sidebar nav', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).toContain('ConsoleSidebarNav');
  });

  it('the single "개요" entry points at the 5-domain overview /dashboards/overview', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).toContain("'nav-dashboards'");
    expect(src).toContain("'/dashboards/overview'");
    expect(src).toContain('개요');
  });

  it('removes the separate "통합 개요" (nav-operator-overview) entry', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).not.toContain('nav-operator-overview');
    expect(src).not.toContain('통합 개요');
  });

  it('no nav entry points at the bare /dashboards (IAM detail is card-reached only)', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).not.toContain("'/dashboards'");
  });

  it('removes the "도메인 상태" (nav-domain-health) top-level entry — reached from the 개요 "도메인 상태 요약" card only (TASK-PC-FE-068)', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    // The quoted testid + href LITERALS are gone (a `/dashboards/health` mention
    // survives only inside the explanatory comment, unquoted).
    expect(src).not.toContain("'nav-domain-health'");
    expect(src).not.toContain("'/dashboards/health'");
  });

  it('the domain-health page carries a back link to the 통합 개요 (TASK-PC-FE-068)', () => {
    const src = readFileSync(HEALTH_PAGE_PATH, 'utf8');
    // The page uses JSX double-quoted attributes, so match bare substrings.
    expect(src).toContain('domain-health-back');
    expect(src).toContain('/dashboards/overview');
    expect(src).toContain('통합 개요로 돌아가기');
  });

  it('keeps the "ERP" entry (nav-erp) pointing at /erp, after Finance', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).toContain("'nav-erp'");
    expect(src).toContain("'/erp'");
    // Domain nav labels are the bare domain name (운영 suffix dropped); the
    // section header "도메인 운영" still carries the grouping context.
    expect(src).toContain("label: 'ERP'");
    const financeIdx = src.indexOf("'nav-finance'");
    const erpIdx = src.indexOf("'nav-erp'");
    expect(financeIdx).toBeGreaterThan(-1);
    expect(erpIdx).toBeGreaterThan(-1);
    expect(erpIdx).toBeGreaterThan(financeIdx);
  });

  it('does NOT remove any of the other pre-existing nav entries', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).toContain("'nav-dashboards'");
    expect(src).toContain("'nav-catalog'");
    expect(src).toContain("'nav-audit'");
    expect(src).toContain("'nav-operators'");
    expect(src).toContain("'nav-wms'");
    expect(src).toContain("'nav-scm'");
    expect(src).toContain("'nav-finance'");
  });
});
