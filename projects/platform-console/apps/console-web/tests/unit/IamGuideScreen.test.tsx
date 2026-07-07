import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import { IamGuideScreen } from '@/features/iam-guide';
import {
  SEED_ROLES,
  SCREEN_ACCESS,
  DOMAIN_ROLE_MAP,
  OPERATOR_ONBOARDING_AXES,
  PERMISSION_KEYS,
  ACCOUNT_HATS,
  AUTH_PLANES,
} from '@/features/iam-guide/data';
import { runAxe } from '../a11y/axe-helper';

/**
 * IAM 개요(가이드) 화면 (TASK-PC-FE-163) — 순수 정적 참조 화면.
 *   - 6개 seed role 카드가 각각 보유 권한과 함께 렌더된다
 *   - 화면 접근 매트릭스가 data.ts(=rbac.md § Seed Matrix)와 셀 단위로 일치한다
 *   - 위임 체인 3단계 + 도메인 롤 파생표 + 권한 키 부록이 렌더된다
 *   - WCAG AA axe-clean
 */
describe('IamGuideScreen', () => {
  it('renders all six admin-console seed roles with their permission keys', () => {
    render(<IamGuideScreen />);
    for (const role of SEED_ROLES) {
      const card = screen.getByTestId(`iam-guide-role-${role.name}`);
      expect(card).toBeInTheDocument();
      // The role name + Korean subtitle appear, and every permission chip.
      expect(within(card).getByText(role.name)).toBeInTheDocument();
      expect(within(card).getByText(role.koName)).toBeInTheDocument();
      for (const perm of role.permissions) {
        expect(within(card).getByText(perm)).toBeInTheDocument();
      }
    }
  });

  it('labels each role with its scope tier (플랫폼/테넌트 스코프)', () => {
    render(<IamGuideScreen />);
    for (const role of SEED_ROLES) {
      const badge = screen.getByTestId(`iam-guide-scope-${role.name}`);
      if (role.scope === 'platform') {
        expect(badge).toHaveTextContent('플랫폼 스코프');
        expect(badge).not.toHaveTextContent('테넌트');
        // Only SUPER_ADMIN carries the wildcard sentinel marker.
        if (role.elevated) expect(badge).toHaveTextContent('플랫폼 스코프(*)');
      } else {
        expect(badge).toHaveTextContent('테넌트 스코프');
      }
    }
  });

  it('renders an access matrix cell for every role × screen matching data.ts levels', () => {
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
    // setup-first order (matches sidebar IAM nav): 운영자 관리 → 계정 운영 → 감사·보안.
    // 운영자 관리 = operator.manage → SUPER_ADMIN + TENANT_ADMIN only.
    expect(SCREEN_ACCESS[0].cells.SUPER_ADMIN.level).toBe('full');
    expect(SCREEN_ACCESS[0].cells.TENANT_ADMIN.level).toBe('full');
    expect(SCREEN_ACCESS[0].cells.SUPPORT_READONLY.level).toBe('none');
    // 계정 운영 = account.read → SUPPORT_LOCK cannot open (lock w/o read).
    expect(SCREEN_ACCESS[1].cells.SUPPORT_LOCK.level).toBe('none');
    // 감사·보안 → SUPPORT_LOCK is 기본만(partial), others with security.event.read full.
    expect(SCREEN_ACCESS[2].cells.SUPPORT_LOCK.level).toBe('partial');
    expect(SCREEN_ACCESS[2].cells.SECURITY_ANALYST.level).toBe('full');
    expect(SCREEN_ACCESS[2].cells.TENANT_ADMIN.level).toBe('none');
  });

  it('renders the two authorization planes (admin RBAC ⟂ domain roles) with the disjoint invariant', () => {
    render(<IamGuideScreen />);
    for (const plane of AUTH_PLANES) {
      const card = screen.getByTestId(
        `iam-guide-plane-${plane.token.split(' ')[0]}`,
      );
      expect(card).toBeInTheDocument();
      expect(within(card).getByText(plane.koName)).toBeInTheDocument();
      expect(within(card).getByText(plane.token)).toBeInTheDocument();
    }
    // the disjoint invariant (ADR-MONO-035) is surfaced verbatim
    expect(
      screen.getByText(/SUPER_ADMIN 이 WMS_OPERATOR 가 되지 않는다/),
    ).toBeInTheDocument();
  });

  it('renders the delegation chain and the domain-role (separate axis) map', () => {
    render(<IamGuideScreen />);
    expect(screen.getByTestId('iam-guide-delegation-0')).toBeInTheDocument();
    expect(screen.getByTestId('iam-guide-delegation-2')).toBeInTheDocument();

    const domainTable = screen.getByTestId('iam-guide-domain-role-map');
    for (const d of DOMAIN_ROLE_MAP) {
      expect(within(domainTable).getByText(d.domain)).toBeInTheDocument();
    }
  });

  it('renders the operator onboarding axes (home tenant / assignment / org_scope)', () => {
    render(<IamGuideScreen />);
    for (const axis of OPERATOR_ONBOARDING_AXES) {
      const card = screen.getByTestId(
        `iam-guide-onboarding-axis-${axis.term.replace(/\s+/g, '-')}`,
      );
      expect(card).toBeInTheDocument();
      expect(within(card).getByText(axis.term)).toBeInTheDocument();
      expect(within(card).getByText(axis.koName)).toBeInTheDocument();
    }
    // the three canonical axes are present, in order
    expect(OPERATOR_ONBOARDING_AXES.map((a) => a.term)).toEqual([
      'home tenant',
      'tenant assignment',
      'org_scope',
    ]);
  });

  it('renders the four account hats (①~④, 하나의 계정 4개의 모자) in order', () => {
    render(<IamGuideScreen />);
    const table = screen.getByTestId('iam-guide-hats');
    expect(table).toBeInTheDocument();
    // Each hat row carries its relation + token mapping; rows are ordered ①②③④.
    for (let i = 0; i < ACCOUNT_HATS.length; i++) {
      const row = screen.getByTestId(`iam-guide-hat-${i}`);
      expect(row).toHaveTextContent(ACCOUNT_HATS[i].relation);
      expect(row).toHaveTextContent(ACCOUNT_HATS[i].token);
    }
    expect(ACCOUNT_HATS.map((h) => h.marker)).toEqual(['①', '②', '③', '④']);
  });

  it('lists every permission key in the appendix', () => {
    render(<IamGuideScreen />);
    const table = screen.getByTestId('iam-guide-permission-keys');
    for (const p of PERMISSION_KEYS) {
      expect(within(table).getByText(p.key)).toBeInTheDocument();
    }
  });

  it('is WCAG AA axe-clean', async () => {
    const { container } = render(<IamGuideScreen />);
    const violations = await runAxe(container);
    expect(violations).toEqual([]);
  });
});
