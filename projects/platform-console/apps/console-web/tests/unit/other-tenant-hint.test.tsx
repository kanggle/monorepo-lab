import { describe, it, expect, vi, beforeEach } from 'vitest';
import { readFileSync } from 'node:fs';
import path from 'node:path';

/**
 * TASK-MONO-719 (소유자 결정 ⓑ) — «이 테넌트에는 없습니다» 힌트.
 *
 * 🔴 이 층이 왜 생겼는지: `TASK-MONO-718` ⓑ 는 게이트에서 «활성 테넌트가 이 제품의
 * `tenants` 에 있는가» 를 물었는데, 2026-09-22 데모 창의 라이브 레지스트리는
 * `ecommerce -> [demo-corp, ecommerce]` 라 그 술어가 **언제나 통과**했다.
 * 자격(entitlement)과 데이터 소유는 이 시스템에서 갈라진다.
 */

const cookieJar = new Map<string, string>();
vi.mock('next/headers', () => ({
  cookies: async () => ({
    get: (n: string) => (cookieJar.has(n) ? { value: cookieJar.get(n)! } : undefined),
  }),
}));

let catalogResult: unknown = { degraded: false, products: [] };
vi.mock('@/features/catalog', () => ({
  getCatalog: async () => {
    if (catalogResult instanceof Error) throw catalogResult;
    return catalogResult;
  },
}));

import { renderToStaticMarkup } from 'react-dom/server';
import { OtherTenantHint, getTenantScope } from '@/widgets/domain-tenant-gate';
import { ACCESS_COOKIE, OPERATOR_COOKIE, TENANT_COOKIE } from '@/shared/lib/session';

const LIVE_PRODUCTS = [
  // 🔴 2026-09-22 15차 창 실측. 이 모양이 이 티켓의 전부다.
  { productKey: 'iam', available: true, tenants: ['demo-corp', 'ecommerce'] },
  { productKey: 'wms', available: true, tenants: ['demo-corp', 'ecommerce'] },
  { productKey: 'scm', available: true, tenants: ['demo-corp'] },
  { productKey: 'ecommerce', available: true, tenants: ['demo-corp', 'ecommerce'] },
];

beforeEach(() => {
  cookieJar.clear();
  catalogResult = { degraded: false, products: LIVE_PRODUCTS };
});

function assumedInto(tenant: string) {
  cookieJar.set(ACCESS_COOKIE, 'base.iam');
  cookieJar.set(OPERATOR_COOKIE, 'op');
  cookieJar.set(TENANT_COOKIE, tenant);
}

describe('getTenantScope — 서버가 아는 것만 내려보낸다', () => {
  it('🔴 활성 `demo-corp` 에서 **라이브 레지스트리 그대로** 다른 테넌트를 찾아낸다', async () => {
    assumedInto('demo-corp');
    expect(await getTenantScope()).toEqual({
      activeTenant: 'demo-corp',
      otherTenants: ['ecommerce'],
    });
  });

  it('활성 테넌트는 **자기 자신을 권하지 않는다** (대조군: 방향이 반대인 경우)', async () => {
    assumedInto('ecommerce');
    expect(await getTenantScope()).toEqual({
      activeTenant: 'ecommerce',
      otherTenants: ['demo-corp'],
    });
  });

  it('🔴 `available:false` 인 제품의 테넌트는 세지 않는다 — 스위처가 제시하지 않는 것을 권하면 누를 수 없는 조언이 된다', async () => {
    catalogResult = {
      degraded: false,
      products: [
        { productKey: 'ecommerce', available: true, tenants: ['demo-corp'] },
        { productKey: 'ghost', available: false, tenants: ['ghost-corp'] },
      ],
    };
    assumedInto('demo-corp');
    const scope = await getTenantScope();
    expect(scope.otherTenants).toEqual([]);
  });

  it.each([
    ['degraded 레지스트리', { degraded: true, products: [] }],
    ['던지는 레지스트리', new Error('registry down')],
    ['제품이 하나도 없음', { degraded: false, products: [] }],
  ])('🔴 %s — 힌트 재료 없음(침묵). 못 확인했을 때 틀리게 말하지 않는다', async (_l, result) => {
    assumedInto('demo-corp');
    catalogResult = result;
    expect(await getTenantScope()).toEqual({
      activeTenant: 'demo-corp',
      otherTenants: [],
    });
  });

  it('🔴 활성 테넌트가 없으면 아무것도 말하지 않는다 — 그 경우는 292 게이트 소관이다', async () => {
    cookieJar.set(ACCESS_COOKIE, 'base.iam');
    cookieJar.set(OPERATOR_COOKIE, 'op');
    expect(await getTenantScope()).toEqual({ activeTenant: null, otherTenants: [] });
  });
});

describe('OtherTenantHint — 힌트이지 단언이 아니다', () => {
  it('🔴 측정된 케이스: `demo-corp` + 전환 가능한 `ecommerce` → 양쪽 이름을 모두 댄다', () => {
    const html = renderToStaticMarkup(
      <OtherTenantHint activeTenant="demo-corp" otherTenants={['ecommerce']} />,
    );
    expect(html).toContain('other-tenant-hint');
    expect(html).toContain('demo-corp');
    expect(html).toContain('ecommerce');
  });

  it('🔴 문구가 «데이터가 없습니다» 가 아니라 «이 테넌트에는 없습니다» 여야 한다', () => {
    // 진짜로 어느 테넌트에도 0건일 수 있다 ⇒ 단언하면 거짓말이 된다.
    const html = renderToStaticMarkup(
      <OtherTenantHint activeTenant="demo-corp" otherTenants={['ecommerce']} />,
    );
    expect(html).toContain('에는 없습니다');
    expect(html).not.toMatch(/데이터가 없습니다|존재하지 않습니다/);
  });

  it('🔴 대조군 — 전환할 다른 테넌트가 없으면 **아무것도 안 그린다**', () => {
    expect(
      renderToStaticMarkup(<OtherTenantHint activeTenant="demo-corp" otherTenants={[]} />),
    ).toBe('');
  });

  it('🔴 대조군 — 활성 테넌트가 없으면 안 그린다', () => {
    expect(
      renderToStaticMarkup(<OtherTenantHint activeTenant={null} otherTenants={['ecommerce']} />),
    ).toBe('');
  });
});

describe('배선 — 힌트가 실제로 빈 목록 옆에 있다', () => {
  const SRC = path.resolve(__dirname, '..', '..', 'src');

  it('🔴 `OrdersScreen` 이 `order-empty` 를 **지우지 않고** 그 옆에 힌트를 둔다', () => {
    const src = readFileSync(
      path.join(SRC, 'features', 'ecommerce-ops', 'components', 'OrdersScreen.tsx'),
      'utf8',
    );
    // 빈 목록 자체는 남아 있어야 한다 — 힌트는 대체가 아니라 덧붙임이다.
    expect(src).toContain('data-testid="order-empty"');
    expect(src).toContain('<OtherTenantHint');
    // 힌트는 빈 분기 안에 있어야 한다: 목록이 찼을 때 뜨면 소음이다.
    const emptyBranchStart = src.indexOf('data-testid="order-empty"');
    const hintAt = src.indexOf('<OtherTenantHint');
    expect(hintAt).toBeGreaterThan(emptyBranchStart);
    expect(hintAt - emptyBranchStart).toBeLessThan(600);
  });

  it('🔴 주문 페이지가 `getTenantScope()` 를 실제로 불러 내려보낸다 (부르지 않으면 힌트는 영원히 침묵한다)', () => {
    const src = readFileSync(
      path.join(SRC, 'app', '(console)', 'ecommerce', 'orders', 'page.tsx'),
      'utf8',
    );
    expect(src).toContain('getTenantScope');
    expect(src).toContain('activeTenant={activeTenant}');
    expect(src).toContain('otherTenants={otherTenants}');
  });

  it('🔵 opt-in 임을 고정한다 — 719 는 **측정된 화면 하나**에만 배선했다', () => {
    // 2026-09-22 창이 잰 것은 /ecommerce/orders 다. 나머지 화면으로 넓히는 것은
    // «아무도 재지 않은 주장» 이므로 별도 결정이고, 이 칸이 그 경계를 지킨다.
    // 🔴 넓히는 PR 은 이 기대값을 함께 고쳐야 한다 — 조용히 번지지 않는다.
    const wired = ['orders', 'products', 'sellers', 'users', 'shippings'].filter((r) => {
      const f = path.join(SRC, 'app', '(console)', 'ecommerce', r, 'page.tsx');
      try {
        return readFileSync(f, 'utf8').includes('getTenantScope');
      } catch {
        return false;
      }
    });
    expect(wired).toEqual(['orders']);
  });
});
