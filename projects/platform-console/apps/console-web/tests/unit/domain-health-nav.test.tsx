import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * `(console)/layout.tsx` nav-source guard.
 *
 * Originally a TASK-PC-FE-013 additive guard ("도메인 상태" added after
 * "통합 개요"). TASK-PC-FE-034 consolidates the two overview entries:
 *   - the separate "통합 개요" entry (`nav-operator-overview`) is REMOVED;
 *   - the single "개요" entry (`nav-dashboards`) now points at the
 *     5-domain cross-domain overview `/dashboards/overview` (the console
 *     landing);
 *   - "도메인 상태" (`nav-domain-health`, `/dashboards/health`) is
 *     unchanged and still reachable;
 *   - a new "ERP 운영" entry (`nav-erp`, `/erp`) is added after Finance.
 *
 * The layout is a server component and can't be mounted in jsdom without a
 * Next.js app-router context; asserting on the source string is the
 * simplest objective nav guard (same approach as the prior FE-013 guard).
 */

const APP_ROOT = path.resolve(__dirname, '..', '..');
const LAYOUT_PATH = path.resolve(
  APP_ROOT,
  'src',
  'app',
  '(console)',
  'layout.tsx',
);

describe('(console)/layout.tsx — nav consolidation guard (TASK-PC-FE-034)', () => {
  it('the single "개요" entry points at the 5-domain overview /dashboards/overview', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).toContain('data-testid="nav-dashboards"');
    expect(src).toContain('href="/dashboards/overview"');
    expect(src).toContain('개요');
  });

  it('removes the separate "통합 개요" (nav-operator-overview) entry', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).not.toContain('data-testid="nav-operator-overview"');
    expect(src).not.toContain('통합 개요');
  });

  it('no nav entry points at the bare /dashboards (GAP detail is card-reached only)', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).not.toContain('href="/dashboards"');
  });

  it('keeps the "도메인 상태" entry pointing at /dashboards/health (unchanged)', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).toContain('data-testid="nav-domain-health"');
    expect(src).toContain('href="/dashboards/health"');
    expect(src).toContain('도메인 상태');
  });

  it('adds the new "ERP 운영" entry (nav-erp) pointing at /erp, after Finance', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).toContain('data-testid="nav-erp"');
    expect(src).toContain('href="/erp"');
    expect(src).toContain('ERP 운영');
    const financeIdx = src.indexOf('data-testid="nav-finance"');
    const erpIdx = src.indexOf('data-testid="nav-erp"');
    expect(financeIdx).toBeGreaterThan(-1);
    expect(erpIdx).toBeGreaterThan(-1);
    expect(erpIdx).toBeGreaterThan(financeIdx);
  });

  it('does NOT remove any of the other pre-existing nav entries', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).toContain('data-testid="nav-dashboards"');
    expect(src).toContain('data-testid="nav-domain-health"');
    expect(src).toContain('data-testid="nav-catalog"');
    expect(src).toContain('data-testid="nav-audit"');
    expect(src).toContain('data-testid="nav-operators"');
    expect(src).toContain('data-testid="nav-wms"');
    expect(src).toContain('data-testid="nav-scm"');
    expect(src).toContain('data-testid="nav-finance"');
  });
});
