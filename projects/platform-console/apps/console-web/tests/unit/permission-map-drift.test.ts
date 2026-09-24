import { describe, it, expect } from 'vitest';
import { GROUPS, isParent } from '@/shared/ui/console-nav-config';
import {
  DEMO_TEST_ACCOUNT,
  PERMISSION_MAP,
  RBAC_ROLES,
  RBAC_SEED_MATRIX,
  navLeaves,
  resolvePermissionMap,
  testAccountAccess,
} from '@/shared/guide/permission-map';
import { SCREEN_COVERAGE } from '@/shared/sample/coverage';
import { SEED_ROLES, PERMISSION_KEYS } from '@/features/iam-guide/data';
import { citedPath, citedPathExists } from '../helpers/cited-path';

/**
 * TASK-PC-FE-298 — 권한·기능 매핑 표 드리프트 가드.
 *
 * 🔴 nav 목록을 **여기서 하드코딩하지 않는다** — `GROUPS`(사이드바가 실제로 렌더하는 원장)
 * 를 직접 펴서 매핑 표와 양방향 대조한다. 그래서 사이드바에 항목을 추가하고 매핑 행을 안
 * 넣으면 이 파일이 빨개진다(티켓 AC-3 bite: 추가 → RED → 행 추가 → GREEN 으로 확인).
 *
 * 🔵 한계(티켓 Edge Case): 이 가드는 **nav 항목 누락/고아**와 권한 키의 **존재**만 잡는다.
 * 권한 코드가 최신인지(rbac.md·컨트롤러가 바뀌었는지)는 자동으로 못 잡는다.
 */

const navHrefs = navLeaves().map((l) => l.leaf.href);
const mapHrefs = PERMISSION_MAP.map((r) => r.href);

describe('permission map ↔ nav (drift)', () => {
  it('every sidebar leaf has a mapping row', () => {
    const missing = navHrefs.filter((h) => !mapHrefs.includes(h));
    expect(missing, `nav hrefs without a permission-map row: ${missing.join(', ')}`).toEqual([]);
  });

  it('every mapping row is a sidebar leaf (no orphans)', () => {
    const orphans = mapHrefs.filter((h) => !navHrefs.includes(h));
    expect(orphans, `permission-map rows no nav item points at: ${orphans.join(', ')}`).toEqual([]);
  });

  it('no href is mapped twice', () => {
    expect(new Set(mapHrefs).size).toBe(mapHrefs.length);
  });

  it('navLeaves() walks the same leaves the sidebar renders (non-vacuity)', () => {
    const direct = GROUPS.flatMap((g) =>
      g.items.flatMap((n) => (isParent(n) ? n.children.map((c) => c.href) : [n.href])),
    );
    expect(navHrefs).toEqual(direct);
    // a floor so an accidentally-empty walk cannot agree with an empty map
    expect(navHrefs.length).toBeGreaterThanOrEqual(48);
  });

  it('resolvePermissionMap() joins every row in nav order with label + depth', () => {
    const rows = resolvePermissionMap();
    expect(rows.map((r) => r.href)).toEqual(navHrefs);
    for (const r of rows) {
      expect(r.label.length).toBeGreaterThan(0);
      expect([1, 2]).toContain(r.depth);
    }
  });
});

describe('permission map — sources and permission keys (AC-4)', () => {
  it('every row cites at least one source, and every cited path exists in the repo', () => {
    const bad: string[] = [];
    for (const r of PERMISSION_MAP) {
      if (r.sources.length === 0) bad.push(`${r.href}: no source`);
      for (const s of r.sources) {
        if (!citedPathExists(s)) bad.push(`${r.href}: ${citedPath(s)}`);
      }
    }
    expect(bad, 'cited paths that do not exist').toEqual([]);
  });

  it('every admin permission key in the map is a key of the rbac.md seed matrix', () => {
    const keys = new Set(Object.keys(RBAC_SEED_MATRIX));
    for (const r of PERMISSION_MAP) {
      if (r.gate.kind === 'admin') expect(keys.has(r.gate.permission), r.href).toBe(true);
      if (r.gate.kind === 'admin-per-card') {
        for (const p of r.gate.permissions) expect(keys.has(p), `${r.href} ${p}`).toBe(true);
      }
    }
  });

  it('the matrix copy agrees with the IAM guide seed roles (two readers of rbac.md cannot disagree)', () => {
    for (const role of SEED_ROLES) {
      const fromMatrix = Object.entries(RBAC_SEED_MATRIX)
        .filter(([, cells]) => cells[role.name as (typeof RBAC_ROLES)[number]])
        .map(([perm]) => perm)
        .sort();
      expect([...role.permissions].sort(), role.name).toEqual(fromMatrix);
    }
    expect(PERMISSION_KEYS.map((k) => k.key).sort()).toEqual(
      // tenant.admin.delegate is a grant-menu key, listed too
      Object.keys(RBAC_SEED_MATRIX).sort(),
    );
  });

  it('guide rows are static in the sample ledger (visible to logged-out visitors)', () => {
    for (const r of PERMISSION_MAP.filter((x) => x.gate.kind === 'public')) {
      expect(SCREEN_COVERAGE[r.href], r.href).toBe('static');
    }
  });
});

describe('permission map — known mismatches and derived access (AC-5)', () => {
  it('records the operator.manage gate on the read-only 권한 / 권한 세트 screens', () => {
    for (const href of ['/permissions', '/permission-sets']) {
      const row = PERMISSION_MAP.find((r) => r.href === href)!;
      expect(row.gate).toEqual({ kind: 'admin', permission: 'operator.manage' });
      expect(row.crud).toEqual({ read: true, create: false, update: false, delete: false });
      expect(row.mismatch).toMatch(/operator\.manage/);
    }
  });

  it('derives test-account access from the matrix and the demo seed, not by hand', () => {
    const at = (href: string) => testAccountAccess(PERMISSION_MAP.find((r) => r.href === href)!.gate);
    expect(DEMO_TEST_ACCOUNT.adminRoles).toEqual(['SUPER_ADMIN']);
    expect(at('/tenants')).toBe('yes'); // tenant.manage ∈ SUPER_ADMIN
    expect(at('/partnerships')).toBe('no'); // partnership.manage ∉ SUPER_ADMIN (rbac.md:112)
    expect(at('/wms/outbound')).toBe('yes'); // demo-corp subscribes wms
    expect(at('/guide')).toBe('yes');
  });
});
