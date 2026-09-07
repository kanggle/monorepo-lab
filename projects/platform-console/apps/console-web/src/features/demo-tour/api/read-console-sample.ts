// DEMO-PUBLIC-DATA-CONSUMER: console-web
//
// =============================================================================
// 공개 둘러보기가 데이터를 얻는 **유일한 경로** (ADR-MONO-070 § D3)
// =============================================================================
// 이 파일이 `src/features/demo-tour/` 전체에서 데이터를 들여오는 **단 하나의 문**이다.
// 그리고 그 문 뒤에는 백엔드로 가는 코드가 **없다** — 판독자(`@demo/public-data`)에
// 애초에 그런 코드가 없기 때문이지, 이 파일이 조심해서가 아니다.
//
// 🔴🔴 여기서 `@/shared/api/*` 를 임포트하지 마라. 그 모듈들은 전부 세션 쿠키를 읽어
//    IAM·BFF·도메인 게이트웨이를 부른다. 둘러보기는 **익명 표면**이므로 그 호출은
//    ① 어차피 401 로 실패하고 ② 실패하지 않는 배포에서는 **실제 운영 데이터를 익명
//    화면에 실어 나른다.** 두 번째가 되돌릴 수 없는 쪽이다.
// 🔴🔴 `@/shared/config/env.ts` 도 임포트하지 마라. 그것은 서버 전용 모듈이고 12개의
//    백엔드 URL 을 들고 있다(그 파일의 헤더 § SERVER-ONLY MODULE). 둘러보기는 백엔드
//    주소를 **알 필요가 없다** — 알 필요가 없는 것을 안 가져오는 것이 경계다.
//
// -----------------------------------------------------------------------------
// 🔵 왜 「번들 시드」 하나로 충분한가
// -----------------------------------------------------------------------------
// `console-sample` 데이터셋은 **합성**이다(`infra/demo/public-data/src/datasets.ts`
// § console-sample). 발행자에게 이것을 백엔드에서 뽑는 경로가 **존재하지 않는다** —
// 그래서 "실수로 실제 운영 데이터가 여기로 흘러든다" 가 표현 불가능하다.
// 그 대가로 화면은 **정직하게 말해야** 한다: 봉투의 `source` 는 `authored` 이고,
// 그것을 화면 문구로 옮기는 것이 `SampleProvenance` 의 몫이다.
// =============================================================================

import {
  createPublicDataReader,
  bundledEnvelope,
  type ConsoleSampleData,
  type ConsoleSampleDomain,
  type PublicDataResult,
} from '@demo/public-data';
import bundledJson from '@demo/public-data/snapshots/console-sample.json';

/**
 * 판독자 인스턴스는 **모듈 스코프에 하나**다.
 *
 * 🔴 요청마다 만들면 판독자의 TTL 캐시가 매번 비어 있게 되고, 저장본이 설정된 배포에서
 *    한 페이지 렌더가 Blob 을 여러 번 때린다(레이아웃 1회 + 페이지 1회 = 최소 2회).
 *    인스턴스가 하나면 그 둘이 같은 캐시 항목을 본다.
 * 🔴 `bundledEnvelope` 는 시드가 계약을 어기면 **여기서 던진다**. 조용히 빈 화면으로
 *    가면 「시드가 깨졌다」와 「아직 데이터가 없다」가 구별되지 않는다 — 빌드가 죽는
 *    편이 낫고, 그 판단은 판독자 쪽이 이미 내려 뒀다.
 */
const reader = createPublicDataReader(
  'console-sample',
  bundledEnvelope('console-sample', bundledJson),
);

export type ConsoleSampleResult = PublicDataResult<ConsoleSampleData>;

/** 봉투 하나를 읽는다. 개요·도메인 상세·검색이 전부 **이 하나**에서 파생된다. */
export async function readConsoleSample(): Promise<ConsoleSampleResult> {
  return reader.readPublicData();
}

/** 테스트 전용 — 칸 사이에 TTL 캐시가 새지 않게 한다. */
export const __resetConsoleSampleCache = reader.__resetPublicDataCache;

/** 개요 카드/헤더가 기준으로 삼는 도메인. 없으면 `undefined`(화면이 그 사실을 말한다). */
export function overviewDomain(
  data: ConsoleSampleData,
): ConsoleSampleDomain | undefined {
  return data.domains.find((d) => d.key === 'overview');
}

/** 개요를 제외한 나머지 도메인 — `/demo` 의 도메인 카드 목록. */
export function sectionDomains(data: ConsoleSampleData): ConsoleSampleDomain[] {
  return data.domains.filter((d) => d.key !== 'overview');
}

/** 경로 파라미터 → 도메인. 모르는 키는 `undefined`(호출부가 404 로 번역한다). */
export function findDomain(
  data: ConsoleSampleData,
  key: string,
): ConsoleSampleDomain | undefined {
  return data.domains.find((d) => d.key === key);
}
