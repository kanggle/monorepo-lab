import Link from 'next/link';
import { notFound } from 'next/navigation';
import {
  readConsoleSample,
  findDomain,
  SampleMetrics,
  SampleTableCard,
} from '@/features/demo-tour';

export const dynamic = 'force-dynamic';

/**
 * `/demo/<domain>` — 도메인 하나의 샘플 운영 화면.
 *
 * 구성은 실시간 콘솔의 도메인 화면과 같다: 지표 줄 → 표(들). 표마다
 *   · `description`  이 화면이 실제로 무슨 일을 하는지 (요구사항의 "각 화면의 기능 설명")
 *   · 브라우저 검색  이미 받은 행 위에서만 (서버 왕복 없음)
 *   · 비활성 버튼    그 화면의 쓰기 작업이 무엇인지 + 왜 지금은 못 누르는지
 * 를 함께 그린다.
 *
 * 🔴 모르는 `domain` 은 `notFound()` 다. 개요로 조용히 되돌리면 «이 도메인은 존재하는데
 *    화면이 비었다» 와 «그런 도메인이 없다» 가 같은 모양이 된다.
 * 🔴 `generateStaticParams` 를 쓰지 않는다 — 그러면 도메인 목록이 **빌드 시점에** 굳고,
 *    새 세대가 도메인을 하나 더 실어 와도 그 경로만 404 가 된다(레이아웃의 메뉴에는
 *    보이는데 열리지 않는 항목 = 가장 진단이 어려운 종류).
 */
export default async function DemoDomainPage({
  params,
}: {
  params: Promise<{ domain: string }>;
}) {
  const { domain: key } = await params;
  const { data } = await readConsoleSample();
  const domain = findDomain(data, key);
  if (!domain) notFound();

  return (
    <section aria-labelledby="demo-domain-heading">
      <nav aria-label="위치" className="mb-4 text-sm text-muted-foreground">
        <Link
          href="/demo"
          className="rounded underline-offset-2 hover:text-foreground hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          둘러보기
        </Link>
        <span aria-hidden="true"> / </span>
        <span>{domain.label}</span>
      </nav>

      <h1 id="demo-domain-heading" className="mb-2 text-2xl font-semibold">
        {domain.label}
      </h1>
      <p className="mb-2 text-sm text-muted-foreground">{domain.description}</p>
      {/* 🔴 실시간 경로는 **텍스트**다 — 링크로 만들면 익명 방문자가 `/login` 으로 튕긴다
          (`DomainCard` 헤더의 같은 논거). */}
      <p className="mb-8 text-xs text-muted-foreground">
        실시간 콘솔 경로{' '}
        <span className="font-mono" data-testid="demo-domain-live-href">
          {domain.liveHref}
        </span>{' '}
        — 로그인한 운영자에게만 열립니다.
      </p>

      <SampleMetrics
        metrics={domain.metrics}
        testIdPrefix={`demo-${domain.key}`}
      />

      {domain.tables.length === 0 ? (
        <p
          role="status"
          data-testid="demo-domain-no-tables"
          className="rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          이 샘플 세대에는 이 도메인의 표가 포함되어 있지 않습니다.
        </p>
      ) : (
        domain.tables.map((t) => <SampleTableCard key={t.key} table={t} />)
      )}
    </section>
  );
}
