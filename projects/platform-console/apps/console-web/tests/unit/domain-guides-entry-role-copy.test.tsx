import type { ComponentType } from 'react';
import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { IamGuideScreen } from '@/features/iam-guide';
import { WmsGuideScreen } from '@/features/wms-guide';
import { ScmGuideScreen } from '@/features/scm-guide';
import { FinanceGuideScreen } from '@/features/finance-guide';
import { ErpGuideScreen } from '@/features/erp-guide';
import { EcommerceGuideScreen } from '@/features/ecommerce-guide';
import { AUTH_PLANES, DOMAIN_ROLE_MAP, IAM_RECIPES } from '@/features/iam-guide/data';
import { SCM_ROLE_NOTE } from '@/features/scm-guide/data';
import { ECOMMERCE_ROLE_NOTE, ECOMMERCE_GLOSSARY } from '@/features/ecommerce-guide/data';
import { PERMISSION_MAP, type PermissionMapRow } from '@/shared/guide/permission-map';

/**
 * TASK-PC-FE-348 — ADR-MONO-083 P5~P8 뒤 가이드 문안. 도메인 운영 권한은 «`*_OPERATOR` 하나 ·
 * 테넌트를 고르면 자동 부여» 가 아니다: 배정된 업무 권한 세트의 영역별 조회/변경 역할이고,
 * `*_OPERATOR` 는 그중 하나라도 있으면 auth 가 덧붙이는 **진입 역할**(배정 불가 · 단독으로 업무 API 를
 * 열지 않음)이다. 옛 문장이 돌아오면 여기서 빨개진다.
 *
 * 역할 이름 · 개수는 손으로 적지 않고 `permission-map.ts`(= 각 도메인 iam-integration.md § 업무 역할 사본)
 * 에서 읽어, 문안과 그 표가 서로 어긋나면 빨개지게 한다.
 */

const GUIDES: [string, ComponentType][] = [
  ['iam-guide', IamGuideScreen],
  ['wms-guide', WmsGuideScreen],
  ['scm-guide', ScmGuideScreen],
  ['finance-guide', FinanceGuideScreen],
  ['erp-guide', ErpGuideScreen],
  ['ecommerce-guide', EcommerceGuideScreen],
];

type DomainRow = PermissionMapRow & {
  gate: Extract<PermissionMapRow['gate'], { kind: 'domain' }>;
};

/** A domain's catalog roles (`gate.roles`) as the permission map declares them. */
function catalogRoles(domain: string): string[] {
  const row = PERMISSION_MAP.find(
    (r): r is DomainRow => r.gate.kind === 'domain' && r.gate.domain === domain,
  );
  if (!row) throw new Error(`no permission-map row for ${domain}`);
  return [...row.gate.roles];
}

const FINE_GRAINED = [
  ['scm', 'SCM_OPERATOR', 8],
  ['erp', 'ERP_OPERATOR', 4],
  ['finance', 'FINANCE_OPERATOR', 5],
  ['ecommerce', 'ECOMMERCE_OPERATOR', 13],
] as const;

/** `SCM_PROCUREMENT_READ` → `SCM_PROCUREMENT` (the area both of its roles share). */
const area = (role: string) => role.replace(/_(READ|WRITE|CONTROL)$/, '');

describe('permission-map catalog sizes match the canonical iam-integration counts', () => {
  it.each(FINE_GRAINED)('%s has the documented number of business roles', (domain, entry, n) => {
    const roles = catalogRoles(domain);
    expect(roles).toHaveLength(n);
    expect(roles).not.toContain(entry);
  });
});

describe.each(GUIDES)('%s — no auto-grant / single-role copy', (prefix, Screen) => {
  it('never says a domain role is granted automatically, or that the tenant alone opens a screen', () => {
    render(<Screen />);
    const text = screen.getByTestId(prefix).textContent ?? '';
    expect(text).not.toMatch(/자동으로\s*(부여|주어)/);
    expect(text).not.toMatch(/테넌트만 맞으면/);
    expect(text).not.toMatch(/권한\s*(하나뿐|하나로만)/);
  });

  it('names an entry role only next to «진입 역할»', () => {
    render(<Screen />);
    const text = screen.getByTestId(prefix).textContent ?? '';
    for (const [, entry] of FINE_GRAINED) {
      let from = 0;
      for (;;) {
        const i = text.indexOf(entry, from);
        if (i < 0) break;
        const around = text.slice(Math.max(0, i - 60), i + 120);
        expect(around, `${entry} @${i}`).toContain('진입 역할');
        from = i + entry.length;
      }
    }
  });
});

describe('SCM guide — 권한 안내', () => {
  const body = () => SCM_ROLE_NOTE.body;

  it('no longer says the domain has a single role', () => {
    expect(body()).not.toMatch(/하나뿐/);
    expect(body()).not.toMatch(/테넌트만 맞으면/);
  });

  it('names the four areas of the 8 catalog roles', () => {
    const roles = catalogRoles('scm');
    for (const a of new Set(roles.map(area))) expect(body(), a).toContain(a);
    expect(body()).toContain('8개');
  });

  it('says who opens what: demand-planning write for replenishment / config, logistics write for the WMS retry', () => {
    expect(body()).toContain('SCM_DEMAND_PLANNING_WRITE');
    expect(body()).toContain('SCM_PROCUREMENT_WRITE');
    expect(body()).toContain('SCM_LOGISTICS_WRITE');
  });

  it('describes SCM_OPERATOR as a derived entry role that opens nothing alone, never as assigned', () => {
    expect(body()).toContain('진입 역할');
    expect(body()).toMatch(/SCM_OPERATOR[^.]*배정하는 역할이 아니라 진입 역할/);
    expect(body()).toMatch(/혼자서는|만으로는/);
  });

  it('keeps «demo opens everything» next to «roles are assigned»', () => {
    expect(body()).toMatch(/배정되어 있어 데모에서는 모두 열려 있습니다/);
  });

  it('mentions the read-only fallback the console really shows', () => {
    expect(body()).toContain('업무 권한 없음');
    expect(body()).toContain('조회 전용');
  });
});

describe('E-Commerce guide — 권한 안내', () => {
  const body = () => ECOMMERCE_ROLE_NOTE.body;

  it('no longer says one role opens every screen', () => {
    expect(ECOMMERCE_ROLE_NOTE.title).not.toMatch(/하나/);
    expect(body()).not.toMatch(/하나뿐|하나로만/);
    expect(body()).not.toMatch(/똑같이/);
  });

  it('names every area of the 13 catalog roles and the count', () => {
    const roles = catalogRoles('ecommerce');
    for (const a of new Set(roles.map(area))) expect(body(), a).toContain(a);
    expect(body()).toContain('13개');
    // members are read-only: the one role with no WRITE twin
    expect(body()).toContain('ECOMMERCE_MEMBER_READ');
    expect(roles).not.toContain('ECOMMERCE_MEMBER_WRITE');
  });

  it('describes ECOMMERCE_OPERATOR as a derived entry role, never as assigned', () => {
    expect(body()).toContain('진입 역할');
    expect(body()).toMatch(/ECOMMERCE_OPERATOR[^.]*배정하는 역할이 아니라 진입 역할/);
  });

  it('keeps «demo opens everything» next to «roles are assigned»', () => {
    expect(body()).toMatch(/배정되어 있어 데모에서는 모두 열려 있습니다/);
  });

  it('the assume-tenant glossary entry no longer promises an automatic grant of one role', () => {
    const entry = ECOMMERCE_GLOSSARY.find((g) => g.key === 'assume-tenant');
    expect(entry?.meaning).not.toMatch(/OPERATOR/);
    expect(entry?.meaning).not.toMatch(/자동/);
    expect(entry?.meaning).not.toMatch(/7개/);
  });
});

describe('IAM guide — 도메인 롤 표', () => {
  it.each(FINE_GRAINED)(
    '%s row lists the catalog business roles, not the entry role',
    (domain, entry) => {
      const row = DOMAIN_ROLE_MAP.find((d) => d.domain === domain);
      expect(row, domain).toBeDefined();
      expect(row!.roles).not.toContain(entry);
      for (const role of catalogRoles(domain)) expect(row!.roles, role).toContain(role);
    },
  );

  it('the domain-role plane is no longer described as «nobody grants it — appears from the subscription»', () => {
    const plane = AUTH_PLANES.find((p) => p.koName === '도메인 운영 권한');
    expect(plane).toBeDefined();
    expect(plane!.storage).not.toMatch(/아무도 부여하지/);
    expect(plane!.storage).not.toMatch(/자동으로 생깁니다/);
    for (const [, entry] of FINE_GRAINED) expect(plane!.roles).not.toContain(entry);
  });

  it('the onboarding recipe assigns a business permission set instead of «subscription = permission»', () => {
    const recipe = IAM_RECIPES.find((r) => r.title === '새 직원에게 도메인 화면 권한 주기');
    expect(recipe).toBeDefined();
    const all = [recipe!.intro ?? '', ...recipe!.steps].join('\n');
    expect(all).not.toMatch(/자동으로 생깁니다/);
    expect(all).not.toMatch(/구독 도메인이 곧/);
    expect(all).toContain('업무 권한 세트');
  });

  it('the reference section explains entry roles are derived, and demo opens everything because roles are assigned', () => {
    render(<IamGuideScreen />);
    const text = screen.getByTestId('iam-guide').textContent ?? '';
    expect(text).not.toContain('구독 중인 도메인에서 아래처럼 자동으로');
    expect(text).toMatch(/배정되어 있어 데모에서는 모두 열려 있습니다/);
  });
});

describe('WMS guide — 테넌트 선택', () => {
  it('does not say WMS permissions are granted automatically on tenant selection', () => {
    render(<WmsGuideScreen />);
    const text = screen.getByTestId('wms-guide').textContent ?? '';
    expect(text).not.toMatch(/자동으로\s*주어/);
    expect(text).toContain('업무 권한 세트');
  });
});
