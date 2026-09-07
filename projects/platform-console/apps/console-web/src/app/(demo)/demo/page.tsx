import {
  readConsoleSample,
  overviewDomain,
  sectionDomains,
  SampleMetrics,
  SampleTableCard,
  DomainCard,
} from '@/features/demo-tour';

export const dynamic = 'force-dynamic';

/**
 * `/demo` — 둘러보기 홈. 실시간 콘솔의 랜딩(`/dashboards/overview`, 5도메인 통합 개요)과
 * **같은 구성**을 샘플 값으로 보여 준다: 지표 → 도메인 헬스 표 → 도메인 카드.
 *
 * 🔴 값의 출처는 봉투 하나(`readConsoleSample`)뿐이다. 이 파일에 fetch·쿠키·env 접근이
 *    없다는 것이 «익명이 안전한 이유» 이고, 그것은 임포트 목록만 봐도 확인된다.
 *
 * 🔵 `overview` 도메인이 없을 수도 있다는 분기를 남긴다 — 픽스처는 지금 그것을 갖고 있지만,
 *    없을 때 화면이 죽는 것보다 «그 칸이 비었다» 를 말하는 편이 낫다(빈 화면과 고장을
 *    구별하는 것이 이 저장소의 규율이다).
 */
export default async function DemoHomePage() {
  const { data } = await readConsoleSample();
  const overview = overviewDomain(data);
  const sections = sectionDomains(data);

  return (
    <section aria-labelledby="demo-home-heading">
      <h1 id="demo-home-heading" className="mb-2 text-2xl font-semibold">
        운영자 콘솔 둘러보기
      </h1>
      <p className="mb-8 text-sm text-muted-foreground">
        {overview?.description ??
          '테넌트가 구독한 도메인의 상태를 한 화면에 모으는 콘솔 개요입니다.'}
      </p>

      {overview ? (
        <>
          <SampleMetrics
            metrics={overview.metrics}
            testIdPrefix="demo-overview"
          />
          {overview.tables.map((t) => (
            <SampleTableCard key={t.key} table={t} />
          ))}
        </>
      ) : (
        <p
          role="status"
          data-testid="demo-overview-missing"
          className="mb-8 rounded-md border border-border bg-muted px-4 py-6 text-sm text-muted-foreground"
        >
          이 샘플 세대에는 개요 데이터가 포함되어 있지 않습니다. 아래 도메인별 화면은
          그대로 볼 수 있습니다.
        </p>
      )}

      <h2 className="mb-1 text-lg font-medium text-foreground">도메인별 화면</h2>
      <p className="mb-4 text-sm text-muted-foreground">
        각 카드를 열면 그 도메인의 운영 화면 구성과 하는 일을 볼 수 있습니다.
      </p>
      <div
        data-testid="demo-domain-cards"
        className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3"
      >
        {sections.map((d) => (
          <DomainCard key={d.key} domain={d} />
        ))}
      </div>
    </section>
  );
}
