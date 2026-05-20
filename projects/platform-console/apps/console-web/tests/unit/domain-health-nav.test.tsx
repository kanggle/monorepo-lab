import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-PC-FE-013 nav-additive guard.
 *
 * The Phase 7 second dashboard adds ONE nav entry "도메인 상태" pointing
 * at `/dashboards/health`. The FE-011 "통합 개요" entry pointing at
 * `/dashboards/overview` must remain BYTE-UNCHANGED — additive only,
 * never re-ordered, never removed.
 *
 * We assert directly on the layout source string (the layout is a server
 * component and can't be mounted in jsdom without a Next.js app router
 * context; the existing erp-nav / operators-nav tests sidestep this by
 * testing the *target screen*, but the layout is the only file the task
 * mandates to be additive). Reading source is the simplest objective
 * additive guard.
 */

const APP_ROOT = path.resolve(__dirname, '..', '..');
const LAYOUT_PATH = path.resolve(
  APP_ROOT,
  'src',
  'app',
  '(console)',
  'layout.tsx',
);

describe('(console)/layout.tsx — nav additive guard', () => {
  it('contains the existing FE-011 "통합 개요" entry pointing at /dashboards/overview', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).toContain('data-testid="nav-operator-overview"');
    expect(src).toContain('href="/dashboards/overview"');
    expect(src).toContain('통합 개요');
  });

  it('adds the new FE-013 "도메인 상태" entry pointing at /dashboards/health', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    expect(src).toContain('data-testid="nav-domain-health"');
    expect(src).toContain('href="/dashboards/health"');
    expect(src).toContain('도메인 상태');
  });

  it('the new entry appears AFTER the existing "통합 개요" entry (additive, never re-orders)', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    const overviewIdx = src.indexOf('data-testid="nav-operator-overview"');
    const healthIdx = src.indexOf('data-testid="nav-domain-health"');
    expect(overviewIdx).toBeGreaterThan(-1);
    expect(healthIdx).toBeGreaterThan(-1);
    expect(healthIdx).toBeGreaterThan(overviewIdx);
  });

  it('does NOT remove any pre-existing nav entry (5 prior entries still present)', () => {
    const src = readFileSync(LAYOUT_PATH, 'utf8');
    // FE-001..010 nav entries — all must still be present.
    expect(src).toContain('data-testid="nav-dashboards"');
    expect(src).toContain('data-testid="nav-operator-overview"');
    expect(src).toContain('data-testid="nav-catalog"');
    expect(src).toContain('data-testid="nav-audit"');
    expect(src).toContain('data-testid="nav-operators"');
    expect(src).toContain('data-testid="nav-wms"');
    expect(src).toContain('data-testid="nav-scm"');
    expect(src).toContain('data-testid="nav-finance"');
  });
});
