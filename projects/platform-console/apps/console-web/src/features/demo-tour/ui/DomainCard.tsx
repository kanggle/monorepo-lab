import Link from 'next/link';
import type { ConsoleSampleDomain } from '@demo/public-data';
import { Card } from '@/shared/ui/Card';

/**
 * `/demo` 개요의 도메인 타일 → `/demo/<key>`.
 *
 * 🔴🔴 `liveHref`(실시간 콘솔의 실제 경로)를 **링크로 만들지 않는다.** 그 경로는 전부
 *    `(console)` 그룹 안이고 익명 방문자가 누르면 `/login` 으로 튕긴다 — 즉 «둘러보다가
 *    갑자기 로그인 화면» 이 되고, 그것은 고장처럼 읽힌다. 픽스처의 타입 주석도
 *    *"둘러보기에서는 링크가 아니라 안내로 쓴다"* 라고 못 박아 두었다.
 *    ⇒ 경로는 **텍스트로** 보여 준다: "실시간 콘솔에서는 여기 있습니다".
 */
export function DomainCard({ domain }: { domain: ConsoleSampleDomain }) {
  return (
    <Card
      data-testid={`demo-domain-card-${domain.key}`}
      className="flex flex-col"
    >
      <h3 className="text-base font-semibold text-foreground">
        <Link
          href={`/demo/${domain.key}`}
          data-testid={`demo-domain-link-${domain.key}`}
          className="rounded underline-offset-2 hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {domain.label}
        </Link>
      </h3>
      <p className="mt-2 flex-1 text-sm text-muted-foreground">
        {domain.description}
      </p>
      <p className="mt-3 text-xs text-muted-foreground">
        실시간 콘솔 경로{' '}
        <span className="font-mono" data-testid={`demo-live-href-${domain.key}`}>
          {domain.liveHref}
        </span>{' '}
        (로그인 필요)
      </p>
    </Card>
  );
}
