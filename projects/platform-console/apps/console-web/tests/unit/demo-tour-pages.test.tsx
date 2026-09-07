/**
 * 공개 둘러보기 `(demo)` — **세션도 백엔드도 없이 실물로 선다**.
 *
 * 🔴🔴 이 파일에서 가장 중요한 칸은 «렌더됐다» 가 아니라 **«fetch 가 0회였다»** 이다.
 *    화면이 그려지는 것만 재면, 어느 날 누군가 이 화면에 실데이터 호출을 하나 얹어도
 *    (그리고 그 호출이 익명이라 401 로 조용히 실패해도) 테스트는 계속 초록이다. 이
 *    라우트 그룹의 명제는 «백엔드를 안 부른다» 이므로 그것을 직접 잰다.
 *
 * 🔵 픽스처를 안 만든다 — 저장소에 커밋된 **번들 시드 그 자체**를 읽는다. 시드가 계약을
 *    어기면 `bundledEnvelope` 가 던지고 이 테스트가 죽는다. 그것이 원하는 동작이다
 *    (시드 파손과 «데이터 없음» 이 같은 초록이 되면 안 된다).
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import type { ReactNode } from 'react';

// 🔴 나머지 props 를 **그대로 흘린다**. 첫 판은 `href` 와 `children` 만 넘겼고, 그래서
//    `data-testid` 가 통째로 사라져 «렌더 안 됐다» 와 «목이 삼켰다» 가 같은 빨강이 됐다.
vi.mock('next/link', () => ({
  default: ({
    children,
    href,
    ...rest
  }: { children: ReactNode; href: string } & Record<string, unknown>) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

import DemoTourLayout from '@/app/(demo)/layout';
import DemoHomePage from '@/app/(demo)/demo/page';
import DemoDomainPage from '@/app/(demo)/demo/[domain]/page';
import { __resetConsoleSampleCache } from '@/features/demo-tour';

let fetchSpy: ReturnType<typeof vi.fn>;

beforeEach(() => {
  __resetConsoleSampleCache();
  // 🔴 «백엔드 없음» 을 흉내내는 것이 아니라, **불렀는지 여부를 잰다**. 어떤 호출도
  //    여기서 실패(throw)하므로, 하나라도 있으면 테스트가 조용히 통과하지 못한다.
  fetchSpy = vi.fn(() => {
    throw new Error('둘러보기 화면이 네트워크를 호출했습니다 — 이 그룹의 명제 위반입니다.');
  });
  vi.stubGlobal('fetch', fetchSpy);
  delete process.env.DEMO_PUBLIC_DATA_BASE_URL;
});

afterEach(() => {
  vi.unstubAllGlobals();
});

/** 서버 컴포넌트다 — 호출해서 나온 엘리먼트를 렌더한다(`demo-backend-notice.test.tsx` 형판). */
async function renderServer(el: Promise<React.ReactElement> | React.ReactElement) {
  render(await el);
}

describe('(demo) 레이아웃 — 익명·백엔드 없음', () => {
  it('샘플 배너 + 로그인 진입점 + 출처 표기를 렌더한다', async () => {
    await renderServer(DemoTourLayout({ children: <p>본문</p> }));

    const banner = screen.getByTestId('demo-sample-banner');
    expect(banner.textContent).toContain('샘플(합성) 데이터');
    expect(banner.textContent).toContain('로그인');

    // 실시간 콘솔로 가는 분명한 진입점 — 배너 안 + 헤더에 각각 하나씩.
    expect(screen.getByTestId('demo-login-link')).toHaveAttribute('href', '/login');
    expect(screen.getByTestId('demo-header-login')).toHaveAttribute('href', '/login');

    // 🔴 출처를 «실시간/실데이터» 로 읽히게 두지 않는다. 이 데이터셋의 source 는 authored 다.
    const prov = screen.getByTestId('demo-provenance');
    expect(prov.textContent).toContain('샘플(합성) 데이터');
    expect(prov.textContent).not.toMatch(/실시간|라이브|live/i);
  });

  it('🔴 fetch 를 한 번도 부르지 않는다', async () => {
    await renderServer(DemoTourLayout({ children: <p>본문</p> }));
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('메뉴가 봉투의 도메인에서 파생된다(하드코딩 목록이 아니다)', async () => {
    await renderServer(DemoTourLayout({ children: <p>본문</p> }));
    const nav = screen.getByTestId('demo-tour-nav');
    expect(within(nav).getByTestId('demo-nav-home')).toHaveAttribute('href', '/demo');
    for (const key of ['overview', 'ecommerce', 'wms', 'scm', 'erp', 'finance', 'iam']) {
      expect(within(nav).getByTestId(`demo-nav-${key}`)).toHaveAttribute(
        'href',
        `/demo/${key}`,
      );
    }
  });
});

describe('/demo — 둘러보기 홈', () => {
  it('개요 지표 + 도메인 헬스 표 + 나머지 도메인 카드를 렌더한다', async () => {
    await renderServer(DemoHomePage());

    expect(screen.getByRole('heading', { name: '운영자 콘솔 둘러보기' })).toBeTruthy();
    expect(screen.getByTestId('demo-overview-metrics')).toBeTruthy();
    expect(screen.getByTestId('demo-grid-domain-health')).toBeTruthy();

    // 개요는 카드가 아니라 본문이므로 카드 목록에서 빠진다.
    const cards = screen.getByTestId('demo-domain-cards');
    expect(within(cards).queryByTestId('demo-domain-card-overview')).toBeNull();
    for (const key of ['ecommerce', 'wms', 'scm', 'erp', 'finance', 'iam']) {
      expect(within(cards).getByTestId(`demo-domain-card-${key}`)).toBeTruthy();
    }
  });

  it('🔴 실시간 콘솔 경로를 **링크로 만들지 않는다**(익명이 누르면 로그인으로 튕긴다)', async () => {
    await renderServer(DemoHomePage());
    const live = screen.getByTestId('demo-live-href-ecommerce');
    expect(live.tagName.toLowerCase()).not.toBe('a');
    expect(live.closest('a')).toBeNull();
    expect(live.textContent).toBe('/ecommerce/orders');

    // 도메인 카드의 링크는 둘러보기 안으로만 간다.
    expect(screen.getByTestId('demo-domain-link-ecommerce')).toHaveAttribute(
      'href',
      '/demo/ecommerce',
    );
  });

  it('🔴 fetch 를 한 번도 부르지 않는다', async () => {
    await renderServer(DemoHomePage());
    expect(fetchSpy).not.toHaveBeenCalled();
  });
});

describe('/demo/[domain] — 도메인 화면', () => {
  it.each([
    ['ecommerce', ['orders', 'products']],
    ['wms', ['inventory']],
    ['scm', ['procurement']],
    ['erp', ['approval']],
    ['finance', ['ledger']],
    ['iam', ['tenants']],
  ])('%s — 지표 + 표 + **각 표의 기능 설명**을 렌더한다', async (key, tables) => {
    await renderServer(
      DemoDomainPage({ params: Promise.resolve({ domain: key as string }) }),
    );

    expect(screen.getByTestId(`demo-${key}-metrics`)).toBeTruthy();
    for (const t of tables as string[]) {
      expect(screen.getByTestId(`demo-grid-${t}`)).toBeTruthy();
      // 🔴 요구사항의 "각 화면의 기능 설명" — 비어 있으면 안 된다.
      const desc = screen.getByTestId(`demo-table-${t}-description`);
      expect((desc.textContent ?? '').trim().length).toBeGreaterThan(10);
    }
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('모르는 도메인은 404 다(개요로 조용히 되돌리지 않는다)', async () => {
    await expect(
      DemoDomainPage({ params: Promise.resolve({ domain: 'no-such-domain' }) }),
    ).rejects.toThrow(/NEXT_NOT_FOUND|NEXT_HTTP_ERROR_FALLBACK/);
  });
});
