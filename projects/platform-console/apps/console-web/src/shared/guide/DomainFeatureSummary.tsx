import Link from 'next/link';
import type { DomainFeatures } from './domain-features';

/**
 * TASK-PC-FE-321 — 도메인 하나의 「한 줄 + 기능 묶음」을 렌더하는 공용 컴포넌트.
 * 전역 가이드(새 절 «도메인 한눈에»)와 6개 도메인 가이드 첫 탭이 같은 컴포넌트를 쓴다
 * (Goal #2 — 데이터·표시 둘 다 한 곳). 순수 정적 — 데이터 페치·권한 게이트 없음.
 *
 * 링크 항목(`href` 있음)은 그 메뉴로 이동하는 `<Link>` 로, 콘솔 밖 항목
 * (`outsideConsole: true`)은 이동 없는 텍스트 + 「콘솔 밖」 표시로 렌더한다. fan
 * 디렉터리처럼 콘솔 메뉴가 **있지만** 플랫폼 운영자만 보이는 항목은 `platformOnly: true`
 * (href 없음)로 두고 「플랫폼 운영자 전용」 표시를 단다 — 고객사 운영자 · 방문자에게
 * 죽은 링크를 보여주지 않으면서, 콘솔 밖이라고 거짓으로 말하지도 않는다(Edge Case).
 */
export function DomainFeatureSummary({
  domain,
  testid,
}: {
  domain: DomainFeatures;
  testid: string;
}) {
  return (
    <div data-testid={testid}>
      <p className="mb-4 max-w-3xl text-sm text-muted-foreground" data-testid={`${testid}-oneline`}>
        {domain.oneLine}
      </p>
      <div className="mb-8 grid gap-4 md:grid-cols-2">
        {domain.groups.map((group, gi) => (
          <div
            key={group.title}
            data-testid={`${testid}-group-${gi}`}
            className="rounded-lg border border-border p-4"
          >
            <p className="mb-2 text-sm font-semibold text-foreground">{group.title}</p>
            <ul className="space-y-1.5">
              {group.items.map((item, ii) => (
                <li
                  key={item.text}
                  data-testid={`${testid}-group-${gi}-item-${ii}`}
                  className="text-sm text-muted-foreground"
                >
                  {item.href ? (
                    <Link
                      href={item.href}
                      className="underline underline-offset-2 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                    >
                      {item.text}
                    </Link>
                  ) : (
                    <span>{item.text}</span>
                  )}
                  {item.outsideConsole && (
                    <span
                      className="ml-1.5 inline-block rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground"
                      data-testid={`${testid}-group-${gi}-item-${ii}-outside`}
                    >
                      콘솔 밖
                    </span>
                  )}
                  {item.platformOnly && (
                    <span
                      className="ml-1.5 inline-block rounded bg-muted px-1.5 py-0.5 text-[11px] text-muted-foreground"
                      data-testid={`${testid}-group-${gi}-item-${ii}-platform-only`}
                    >
                      플랫폼 운영자 전용
                    </span>
                  )}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  );
}
