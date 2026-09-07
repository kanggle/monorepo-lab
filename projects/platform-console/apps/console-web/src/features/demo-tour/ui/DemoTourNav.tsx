import Link from 'next/link';
import type { ConsoleSampleDomain } from '@demo/public-data';

/**
 * 둘러보기 안에서만 도는 섹션 내비게이션.
 *
 * 🔴🔴 실시간 콘솔의 사이드바(`shared/ui/ConsoleSidebarNav`)를 **재사용하지 않는다.**
 *    그 컴포넌트의 항목은 전부 `(console)` 그룹의 보호 경로이고, 익명 방문자에게 그것을
 *    보여 주면 «클릭할 때마다 로그인으로 튕기는 메뉴» 가 된다. 재사용의 대상은
 *    **표현 원자**(`Card`·`Button`·`data-table`)이지 **경로를 아는 컴포넌트**가 아니다.
 *
 * 🔵 목록의 출처는 하드코딩한 배열이 아니라 **봉투가 실제로 담고 있는 도메인**이다.
 *    픽스처에 도메인이 하나 늘면 메뉴도 함께 는다(같은 사실이 두 곳에 생기지 않는다).
 */
export function DemoTourNav({ domains }: { domains: ConsoleSampleDomain[] }) {
  return (
    <nav aria-label="둘러보기 메뉴" data-testid="demo-tour-nav">
      <ul className="flex flex-wrap gap-x-4 gap-y-1.5">
        {/* 🔴 testid 가 `demo-nav-overview` 가 **아니다** — 봉투에 `overview` 라는
            도메인이 실제로 있고, 아래 루프가 그 항목에 같은 이름을 붙인다. 그러면 셀렉터
            하나가 둘을 집어 «못 찾음» 이 아니라 «둘 찾음» 으로 깨진다(실측). */}
        <li>
          <Link
            href="/demo"
            data-testid="demo-nav-home"
            className="rounded text-sm text-muted-foreground underline-offset-2 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            둘러보기 홈
          </Link>
        </li>
        {domains.map((d) => (
          <li key={d.key}>
            <Link
              href={`/demo/${d.key}`}
              data-testid={`demo-nav-${d.key}`}
              className="rounded text-sm text-muted-foreground underline-offset-2 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            >
              {d.label}
            </Link>
          </li>
        ))}
      </ul>
    </nav>
  );
}
