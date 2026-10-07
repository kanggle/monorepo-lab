import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, cleanup } from '@testing-library/react';
import { ConsoleSidebarNav } from '@/shared/ui/ConsoleSidebarNav';

/**
 * TASK-PC-FE-314 — 사이드바가 권한(roles)·구독(subscribedDomains)과 무관하게
 * 거의 전부를 보이던 것을 고친다. Goal table:
 *   - `admin`/`admin-per-card` 게이트 — 역할이 하나도 못 가지면 숨김.
 *   - `domain` 게이트 — 구독 안 하면 보이되 부모에 «구독 필요» 배지.
 *   - `public`/`operator` — 그대로.
 */
let mockPath = '/dashboards/overview';
vi.mock('next/navigation', () => ({
  usePathname: () => mockPath,
}));

beforeEach(() => {
  cleanup();
  mockPath = '/dashboards/overview';
});

const TENANT_ADMIN_COMBO = ['TENANT_ADMIN', 'TENANT_BILLING_ADMIN'];

describe('AC-1 — TENANT_ADMIN + TENANT_BILLING_ADMIN (새 B2B 관리자)', () => {
  it('테넌트 · 조직 계층 없음', () => {
    render(<ConsoleSidebarNav myRoles={TENANT_ADMIN_COMBO} />);
    expect(screen.queryByTestId('nav-tenants')).toBeNull();
    expect(screen.queryByTestId('nav-org-hierarchy')).toBeNull();
  });

  it('도메인 구독 · 파트너십 있음 (조직 설정 그룹, 최상위 flat leaf)', () => {
    render(<ConsoleSidebarNav myRoles={TENANT_ADMIN_COMBO} />);
    expect(screen.getByTestId('nav-subscriptions')).toBeInTheDocument();
    expect(screen.getByTestId('nav-partnerships')).toBeInTheDocument();
  });

  it('운영자 관리 있음 (IAM 드릴 안, operator.manage — TENANT_ADMIN 이 가짐)', () => {
    render(<ConsoleSidebarNav myRoles={TENANT_ADMIN_COMBO} />);
    fireEvent.click(screen.getByTestId('nav-iam'));
    expect(screen.getByTestId('nav-operators')).toBeInTheDocument();
    // audit.read 는 둘 다 없음 — 감사·보안은 숨는다(대조군, AC-1 에 명시된 항목은
    // 아니지만 Goal 표의 직접 귀결).
    expect(screen.queryByTestId('nav-audit')).toBeNull();
  });

  it('조직 설정 그룹 자체는 남아있다 (구독 · 파트너십이 보이므로 전부 숨지 않음)', () => {
    render(<ConsoleSidebarNav myRoles={TENANT_ADMIN_COMBO} />);
    expect(screen.getByTestId('nav-group-org-settings')).toBeInTheDocument();
  });
});

describe('AC-2 (대조군) — SUPER_ADMIN · ORG_ADMIN', () => {
  it('SUPER_ADMIN: 조직 설정 그룹 — 테넌트 · 조직 계층 · 구독 모두 있음, 단 파트너십은 예외', () => {
    render(<ConsoleSidebarNav myRoles={['SUPER_ADMIN']} />);
    expect(screen.getByTestId('nav-tenants')).toBeInTheDocument();
    expect(screen.getByTestId('nav-org-hierarchy')).toBeInTheDocument();
    expect(screen.getByTestId('nav-subscriptions')).toBeInTheDocument();
    expect(screen.getByTestId('nav-accounts')).toBeInTheDocument();
    // AC-0 finding (see task Implementation Notes): SUPER_ADMIN genuinely
    // lacks `partnership.manage` in the seed matrix today — this was already
    // true before this task (permission-map.ts's own `mismatch` field
    // documents it), just never enforced by the sidebar. Hiding it here is
    // this task's fix applying to SUPER_ADMIN too, not a new regression.
    expect(screen.queryByTestId('nav-partnerships')).toBeNull();
  });

  it('SUPER_ADMIN: IAM 드릴 안 — 운영자 관리 · 감사 모두 있음(오늘과 같음)', () => {
    render(<ConsoleSidebarNav myRoles={['SUPER_ADMIN']} />);
    fireEvent.click(screen.getByTestId('nav-iam'));
    expect(screen.getByTestId('nav-operators')).toBeInTheDocument();
    expect(screen.getByTestId('nav-audit')).toBeInTheDocument();
    expect(screen.getByTestId('nav-iam-operator-groups')).toBeInTheDocument();
    expect(screen.getByTestId('nav-iam-permissions')).toBeInTheDocument();
    expect(screen.getByTestId('nav-iam-permission-sets')).toBeInTheDocument();
  });

  it('myRoles 미지정(기존 호출/테스트) — SUPER_ADMIN 포함 지금처럼 전부 보임 (회귀)', () => {
    render(<ConsoleSidebarNav />);
    expect(screen.getByTestId('nav-tenants')).toBeInTheDocument();
    expect(screen.getByTestId('nav-org-hierarchy')).toBeInTheDocument();
    expect(screen.getByTestId('nav-partnerships')).toBeInTheDocument();
  });

  it('ORG_ADMIN: 조직 계층 있음', () => {
    render(<ConsoleSidebarNav myRoles={['ORG_ADMIN']} />);
    expect(screen.getByTestId('nav-org-hierarchy')).toBeInTheDocument();
    expect(screen.queryByTestId('nav-tenants')).toBeNull();
  });
});

describe('AC-3 — 도메인 구독 배지', () => {
  it('구독 0 — 모든 도메인 부모에 배지, 메뉴 자체는 보임', () => {
    render(<ConsoleSidebarNav subscribedDomains={new Set()} />);
    for (const parent of ['nav-wms', 'nav-scm', 'nav-finance', 'nav-erp', 'nav-ecommerce']) {
      expect(screen.getByTestId(parent)).toBeInTheDocument();
      expect(screen.getByTestId(`${parent}-subscription-badge`)).toBeInTheDocument();
    }
  });

  it('구독한 도메인엔 배지 없음(대조군) — 안 한 도메인엔 있다', () => {
    render(<ConsoleSidebarNav subscribedDomains={new Set(['wms'])} />);
    expect(screen.queryByTestId('nav-wms-subscription-badge')).toBeNull();
    expect(screen.getByTestId('nav-scm-subscription-badge')).toBeInTheDocument();
  });

  it('AC-0 ③ — 활성 테넌트 없음(prop 생략/undefined) → 배지 전혀 없음', () => {
    render(<ConsoleSidebarNav />);
    for (const parent of ['nav-wms', 'nav-scm', 'nav-finance', 'nav-erp', 'nav-ecommerce']) {
      expect(screen.queryByTestId(`${parent}-subscription-badge`)).toBeNull();
    }
  });

  it('드릴인 상태(부모 헤더)에도 배지가 한 번 더 보인다 — 자식마다는 아니다', () => {
    render(<ConsoleSidebarNav subscribedDomains={new Set()} />);
    fireEvent.click(screen.getByTestId('nav-wms'));
    expect(screen.getByTestId('nav-wms-subscription-badge')).toBeInTheDocument();
    // children never carry their own badge — only the parent header does.
    expect(screen.queryByTestId('nav-wms-inventory-subscription-badge')).toBeNull();
  });
});

describe('AC-5 — 모르면 숨기지 않는다', () => {
  it('시드 표에 없는 커스텀 역할 → 보임', () => {
    render(<ConsoleSidebarNav myRoles={['CUSTOM_FUTURE_ROLE']} />);
    expect(screen.getByTestId('nav-tenants')).toBeInTheDocument();
    expect(screen.getByTestId('nav-partnerships')).toBeInTheDocument();
  });

  it('/me 실패(myRoles=null) → 전부 보임', () => {
    render(<ConsoleSidebarNav myRoles={null} />);
    expect(screen.getByTestId('nav-tenants')).toBeInTheDocument();
    expect(screen.getByTestId('nav-org-hierarchy')).toBeInTheDocument();
    expect(screen.getByTestId('nav-partnerships')).toBeInTheDocument();
  });
});

describe('AC-4 regression — 역할·구독 계산은 registry 게이트(fan)를 건드리지 않는다', () => {
  it('fan 은 여전히 availableProductKeys 로만 게이트된다', () => {
    render(
      <ConsoleSidebarNav
        myRoles={['SUPER_ADMIN']}
        availableProductKeys={['iam', 'wms', 'scm', 'erp', 'finance', 'ecommerce']}
      />,
    );
    expect(screen.queryByTestId('nav-fan')).toBeNull();
  });
});
