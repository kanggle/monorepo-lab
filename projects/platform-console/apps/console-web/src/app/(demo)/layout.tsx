// =============================================================================
// `(demo)` — **공개 둘러보기 셸.** 인증 가드가 없고, 그것이 이 그룹의 정의다
// =============================================================================
// 🔴🔴 이 레이아웃에는 `isAuthenticated()` 가 **없다.** 형제 그룹 `(console)/layout.tsx`
//    첫 줄의 가드(`if (!(await isAuthenticated())) redirect(await buildLoginRedirect())`)와
//    **정확히 반대**이고, 그 둘이 갈라져 있는 것이 이 변경의 안전성 전부다:
//
//      `(console)/**`   65개 화면. 로그인 필수. 실제 고객·주문·재무·계정 데이터.
//      `(onboarding)/**` 로그인은 했으나 아직 운영자가 아닌 상태.
//      `(demo)/**`      익명. **합성 샘플만.** 백엔드·BFF·IAM 호출 0.
//
// 🔴 그래서 이 그룹에 화면을 하나 더 추가할 때 물어야 하는 것은 «이 화면이 예쁜가» 가
//    아니라 **«이 화면이 읽는 값이 봉투에서만 오는가»** 다. 값이 다른 데서 온다면 그
//    화면은 `(demo)` 가 아니라 `(console)` 소속이다. 기존 보호 경로를 이쪽으로 옮기는
//    변경은 **없다** — 새 경로만 생겼다.
//
// -----------------------------------------------------------------------------
// 🔴 왜 이 경계가 «파일을 잘 나눈 것» 이상인가
// -----------------------------------------------------------------------------
// 이 앱에서 실제 데이터를 가져오는 모든 모듈(`@/shared/api/*`)은 HttpOnly 세션 쿠키를
// 읽는다. 익명 요청에는 그 쿠키가 없으므로 그 경로들은 **애초에 아무것도 못 가져온다.**
// 즉 위험은 "익명이 실데이터를 본다" 가 아니라 **"보호 경로가 실수로 공개된다"** 이고,
// 그 실수의 유일한 자리는 `(console)/layout.tsx` 의 가드 한 줄이다. 그래서 이 작업은
// 그 줄을 **건드리지 않는다**(회귀 테스트가 그 사실을 잰다 —
// `tests/unit/demo-tour-console-guard-regression.test.tsx`).
//
// -----------------------------------------------------------------------------
// 🔵 데모 하트비트를 여기에 **안 단다**
// -----------------------------------------------------------------------------
// 60초 하트비트는 인증된 `(console)` 셸에만 있다. 익명 방문자의 브라우저가 EC2 를 계속
// 켜 두게 하면 예산이 «둘러보던 사람» 에게 소진된다 — 그 축의 설명은
// `widgets/demo-heartbeat/DemoHeartbeat.tsx` 헤더에 있다.
// =============================================================================

import type { ReactNode } from 'react';
import type { Metadata } from 'next';
import Link from 'next/link';
import {
  readConsoleSample,
  SampleDataBanner,
  SampleProvenance,
  DemoTourNav,
} from '@/features/demo-tour';

export const metadata: Metadata = {
  title: '둘러보기 · Platform Console',
  description:
    '운영자 콘솔의 샘플(합성) 데이터 둘러보기 — 로그인 없이 각 도메인 화면의 구성과 기능을 볼 수 있습니다.',
};

/**
 * 🔴 `force-dynamic` 인 이유는 «쿠키를 읽어서» 가 아니다(읽지 않는다). 판독자가 런타임에
 *    영속 저장본을 따라갈 수 있어야 하기 때문이다 — 정적으로 구우면 봉투가 **빌드 순간에
 *    고정**되고, 새 세대를 발행해도 재배포 전까지 옛 샘플이 서빙된다. 판독자는 자체 60초
 *    TTL 캐시를 갖고 있어 이 선택이 매 요청 왕복을 뜻하지도 않는다.
 */
export const dynamic = 'force-dynamic';

export default async function DemoTourLayout({
  children,
}: {
  children: ReactNode;
}) {
  const { envelope, data } = await readConsoleSample();

  return (
    <div className="flex min-h-screen flex-col">
      <SampleDataBanner />
      <header className="sticky top-0 z-40 border-b border-border bg-background/80 backdrop-blur supports-[backdrop-filter]:bg-background/60">
        <div className="mx-auto flex max-w-6xl flex-col gap-2 px-4 py-3 sm:px-6 lg:px-8">
          <div className="flex items-center justify-between gap-3">
            <Link
              href="/demo"
              className="rounded-sm text-sm font-semibold tracking-tight text-foreground transition-colors hover:text-foreground/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              Platform Console <span className="text-muted-foreground">둘러보기</span>
            </Link>
            <Link
              href="/login"
              data-testid="demo-header-login"
              className="rounded-md border border-border px-3 py-1.5 text-sm font-medium text-foreground hover:bg-accent focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              운영자 로그인
            </Link>
          </div>
          <DemoTourNav domains={data.domains} />
        </div>
      </header>
      <main className="min-w-0 flex-1 px-4 py-8 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-6xl">{children}</div>
      </main>
      <footer className="border-t border-border px-4 py-6 sm:px-6 lg:px-8">
        <div className="mx-auto max-w-6xl space-y-1">
          <SampleProvenance envelope={envelope} />
          <p className="text-xs text-muted-foreground">
            이 화면의 모든 값은 합성입니다. 실제 테넌트·고객·주문·계정 데이터는 로그인한
            운영자에게만, 그리고 서버 측 권한 확인을 통과한 경우에만 제공됩니다.
          </p>
        </div>
      </footer>
    </div>
  );
}
