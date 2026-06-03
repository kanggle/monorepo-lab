import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * Console nav-source guard.
 *
 * History: TASK-PC-FE-013 additive guard → TASK-PC-FE-034 overview
 * consolidation → **TASK-PC-FE-039 moves the section nav out of the top-bar
 * `(console)/layout.tsx` into a Vercel-style left sidebar
 * (`ConsoleSidebarNav.tsx`)**. The nav is now a data-driven list (`testid: …`
 * / `href: …`) rather than inline JSX attributes, so the guard reads that
 * source and asserts the data entries; it also verifies the layout still wires
 * the sidebar in.
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
  'ConsoleSidebarNav.tsx',
);
const LAYOUT_PATH = path.resolve(
  APP_ROOT,
  'src',
  'app',
  '(console)',
  'layout.tsx',
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

  it('no nav entry points at the bare /dashboards (GAP detail is card-reached only)', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).not.toContain("'/dashboards'");
  });

  it('keeps the "도메인 상태" entry pointing at /dashboards/health (unchanged)', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).toContain("'nav-domain-health'");
    expect(src).toContain("'/dashboards/health'");
    expect(src).toContain('도메인 상태');
  });

  it('keeps the "ERP 운영" entry (nav-erp) pointing at /erp, after Finance', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).toContain("'nav-erp'");
    expect(src).toContain("'/erp'");
    expect(src).toContain('ERP 운영');
    const financeIdx = src.indexOf("'nav-finance'");
    const erpIdx = src.indexOf("'nav-erp'");
    expect(financeIdx).toBeGreaterThan(-1);
    expect(erpIdx).toBeGreaterThan(-1);
    expect(erpIdx).toBeGreaterThan(financeIdx);
  });

  it('does NOT remove any of the other pre-existing nav entries', () => {
    const src = readFileSync(NAV_PATH, 'utf8');
    expect(src).toContain("'nav-dashboards'");
    expect(src).toContain("'nav-domain-health'");
    expect(src).toContain("'nav-catalog'");
    expect(src).toContain("'nav-audit'");
    expect(src).toContain("'nav-operators'");
    expect(src).toContain("'nav-wms'");
    expect(src).toContain("'nav-scm'");
    expect(src).toContain("'nav-finance'");
  });
});
