import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { IamGuideScreen } from '@/features/iam-guide';
import {
  SEED_ROLES,
  SCREEN_ACCESS,
  CONSOLE_MENUS,
  DOMAIN_ROLE_MAP,
  OPERATOR_ONBOARDING_AXES,
  PERMISSION_KEYS,
  ACCOUNT_HATS,
  AUTH_PLANES,
} from '@/features/iam-guide/data';
import { runAxe } from '../a11y/axe-helper';

/**
 * IAM 가이드 화면 (TASK-PC-FE-163, 재구성 TASK-PC-FE-238) — 순수 정적 참조 화면.
 *
 * 화면은 3부로 분리된다: 1. 개념 · 2. 메뉴 사용법 · 3. 레퍼런스.
 *   - 사용법: 11개 메뉴가 라우트·작업·게이트와 함께 렌더된다(스텁 구분 포함)
 *   - 레퍼런스: 6개 seed role 카드 + 7개 표면 × 6개 role 매트릭스 + 권한 키 + 도메인 롤
 *   - 매트릭스가 data.ts(=rbac.md § Seed Matrix)와 셀 단위로 일치한다
 *   - WCAG AA axe-clean
 */
describe('IamGuideScreen', () => {
  /* ── 1. 개념 ─────────────────────────────────────────────── */

  it('renders the four account hats (①~④) in order', () => {
    render(<IamGuideScreen />);
    expect(screen.getByTestId('iam-guide-hats')).toBeInTheDocument();
    for (let i = 0; i < ACCOUNT_HATS.length; i++) {
      const row = screen.getByTestId(`iam-guide-hat-${i}`);
      expect(row).toHaveTextContent(ACCOUNT_HATS[i].relation);
      expect(row).toHaveTextContent(ACCOUNT_HATS[i].token);
    }
    expect(ACCOUNT_HATS.map((h) => h.marker)).toEqual(['①', '②', '③', '④']);
  });

  it('renders the two authorization planes with the never-mixed invariant', () => {
    render(<IamGuideScreen />);
    AUTH_PLANES.forEach((plane, i) => {
      const card = screen.getByTestId(`iam-guide-plane-${i}`);
      expect(within(card).getByText(plane.koName)).toBeInTheDocument();
      expect(within(card).getByText(plane.token)).toBeInTheDocument();
    });
    // The disjoint invariant (ADR-MONO-035) is surfaced in plain Korean.
    expect(
      screen.getByText(/SUPER_ADMIN 이라고 WMS 운영자가 되지는 않습니다/),
    ).toBeInTheDocument();
  });

  /* ── 2. 메뉴 사용법 ──────────────────────────────────────── */

  it('renders every IAM console menu with its route, actions and gate', () => {
    render(<IamGuideScreen />);
    const table = screen.getByTestId('iam-guide-menus');
    expect(table).toBeInTheDocument();
    for (const menu of CONSOLE_MENUS) {
      const row = screen.getByTestId(`iam-guide-menu-${menu.href}`);
      expect(row).toHaveTextContent(menu.label);
      expect(row).toHaveTextContent(menu.href);
      expect(row).toHaveTextContent(menu.purpose);
    }
  });

  it('marks each menu as 준비 중 / 변경 가능 / 조회 전용', () => {
    render(<IamGuideScreen />);
    // /operator-groups is the only stub today.
    expect(
      screen.getByTestId('iam-guide-menu-/operator-groups'),
    ).toHaveTextContent('준비 중');
    // A write surface and a read surface, respectively.
    expect(screen.getByTestId('iam-guide-menu-/operators')).toHaveTextContent(
      '변경 가능',
    );
    expect(screen.getByTestId('iam-guide-menu-/audit')).toHaveTextContent(
      '조회 전용',
    );
    // The stub carries no permission gate — it opens for any console entrant.
    expect(CONSOLE_MENUS.find((m) => m.stub)?.gate).toBe('—');
  });

  it('renders the onboarding delegation chain and the three reach axes', () => {
    render(<IamGuideScreen />);
    expect(screen.getByTestId('iam-guide-delegation-0')).toBeInTheDocument();
    expect(screen.getByTestId('iam-guide-delegation-2')).toBeInTheDocument();

    for (const axis of OPERATOR_ONBOARDING_AXES) {
      const card = screen.getByTestId(
        `iam-guide-onboarding-axis-${axis.term.replace(/\s+/g, '-')}`,
      );
      expect(within(card).getByText(axis.term)).toBeInTheDocument();
      expect(within(card).getByText(axis.koName)).toBeInTheDocument();
    }
    expect(OPERATOR_ONBOARDING_AXES.map((a) => a.term)).toEqual([
      'home tenant',
      'tenant assignment',
      'org_scope',
    ]);
  });

  /* ── 3. 레퍼런스 ─────────────────────────────────────────── */

  it('renders all six admin-console seed roles with their permission keys', () => {
    render(<IamGuideScreen />);
    for (const role of SEED_ROLES) {
      const card = screen.getByTestId(`iam-guide-role-${role.name}`);
      expect(within(card).getByText(role.name)).toBeInTheDocument();
      expect(within(card).getByText(role.koName)).toBeInTheDocument();
      for (const perm of role.permissions) {
        expect(within(card).getByText(perm)).toBeInTheDocument();
      }
    }
  });

  it('labels each role with its scope tier', () => {
    render(<IamGuideScreen />);
    for (const role of SEED_ROLES) {
      const badge = screen.getByTestId(`iam-guide-scope-${role.name}`);
      if (role.scope === 'platform') {
        expect(badge).toHaveTextContent('플랫폼 전체');
        if (role.elevated) expect(badge).toHaveTextContent('제약 없음');
      } else {
        expect(badge).toHaveTextContent('자기 테넌트');
      }
    }
  });

  it('grants TENANT_ADMIN the partnership.manage key (rbac.md § Seed Matrix)', () => {
    const tenantAdmin = SEED_ROLES.find((r) => r.name === 'TENANT_ADMIN');
    expect(tenantAdmin?.permissions).toContain('partnership.manage');
    // SUPER_ADMIN deliberately does NOT hold it.
    const superAdmin = SEED_ROLES.find((r) => r.name === 'SUPER_ADMIN');
    expect(superAdmin?.permissions).not.toContain('partnership.manage');
  });

  it('renders an access matrix cell for every screen × role matching data.ts levels', () => {
    render(<IamGuideScreen />);
    for (const role of SEED_ROLES) {
      for (const s of SCREEN_ACCESS) {
        const cell = screen.getByTestId(
          `iam-guide-cell-${role.name}-${s.href}`,
        );
        const expected = s.cells[role.name]?.level ?? 'none';
        expect(cell).toHaveAttribute('data-level', expected);
      }
    }
  });

  it('encodes the canonical rbac.md matrix (spot checks)', () => {
    const at = (href: string) => {
      const row = SCREEN_ACCESS.find((s) => s.href === href);
      if (!row) throw new Error(`no matrix row for ${href}`);
      return row.cells;
    };
    // 운영자 관리 = operator.manage → SUPER_ADMIN + TENANT_ADMIN only.
    expect(at('/operators').SUPER_ADMIN.level).toBe('full');
    expect(at('/operators').TENANT_ADMIN.level).toBe('full');
    expect(at('/operators').SUPPORT_READONLY.level).toBe('none');
    // 테넌트 = tenant.manage → SUPER_ADMIN only (read is gated by the same key).
    expect(at('/tenants').SUPER_ADMIN.level).toBe('full');
    expect(at('/tenants').TENANT_ADMIN.level).toBe('none');
    // 계정 운영 = account.read → SUPPORT_LOCK cannot open (lock w/o read).
    expect(at('/accounts').SUPPORT_LOCK.level).toBe('none');
    // 감사·보안 → SUPPORT_LOCK is 기본만(partial); security.event.read holders full.
    expect(at('/audit').SUPPORT_LOCK.level).toBe('partial');
    expect(at('/audit').SECURITY_ANALYST.level).toBe('full');
    expect(at('/audit').TENANT_ADMIN.level).toBe('none');
    // 도메인 구독 = subscription.manage → SUPER_ADMIN + TENANT_BILLING_ADMIN.
    expect(at('/subscriptions').TENANT_BILLING_ADMIN.level).toBe('full');
    expect(at('/subscriptions').TENANT_ADMIN.level).toBe('none');
    // 파트너십 = partnership.manage → TENANT_ADMIN only. SUPER_ADMIN is ❌ by design.
    expect(at('/partnerships').TENANT_ADMIN.level).toBe('full');
    expect(at('/partnerships').SUPER_ADMIN.level).toBe('none');
  });

  it('lists every permission key with its description', () => {
    render(<IamGuideScreen />);
    const table = screen.getByTestId('iam-guide-permission-keys');
    for (const p of PERMISSION_KEYS) {
      expect(within(table).getByText(p.key)).toBeInTheDocument();
    }
    expect(PERMISSION_KEYS.map((p) => p.key)).toContain('partnership.manage');
  });

  it('renders the subscribed-domain → domain-role map (the separate axis)', () => {
    render(<IamGuideScreen />);
    const domainTable = screen.getByTestId('iam-guide-domain-role-map');
    for (const d of DOMAIN_ROLE_MAP) {
      expect(within(domainTable).getByText(d.domain)).toBeInTheDocument();
    }
  });

  /* ── 구조 · 접근성 ───────────────────────────────────────── */

  it('separates usage from reference — role/permission catalogs live only in part 3', () => {
    render(<IamGuideScreen />);
    // The three part headings exist and are ordered 개념 → 사용법 → 레퍼런스.
    const parts = ['iam-guide-concepts', 'iam-guide-usage', 'iam-guide-reference'];
    const positions = parts.map((id) => {
      const el = document.getElementById(id);
      expect(el).not.toBeNull();
      return el as HTMLElement;
    });
    expect(
      positions[0].compareDocumentPosition(positions[1]) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(
      positions[1].compareDocumentPosition(positions[2]) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();

    // The catalogs (role cards, permission keys, domain roles) come AFTER the
    // reference heading — the usage section must not repeat them.
    const reference = positions[2];
    for (const testid of [
      'iam-guide-role-SUPER_ADMIN',
      'iam-guide-access-matrix',
      'iam-guide-permission-keys',
      'iam-guide-domain-role-map',
    ]) {
      const el = screen.getByTestId(testid);
      expect(
        reference.compareDocumentPosition(el) &
          Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    }
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<IamGuideScreen />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
