// =============================================================================
// `features/demo-tour` — **샘플이 먹이는 화면들.** 라이브 API 가 먹이는 화면과 섞이지 않는다
// =============================================================================
// 🔴🔴 이 디렉터리 안의 컴포넌트는 **전부** `@demo/public-data` 의 봉투 하나에서만 값을
//    받는다. 백엔드·BFF·IAM 을 부르는 코드가 한 줄도 없고, 있으면 안 된다.
//
//    나머지 `features/*` 는 정반대다 — `@/shared/api/*` 를 통해 세션 쿠키로 실제 도메인
//    API 를 부르고, 그래서 `(console)` 그룹의 인증 가드 **뒤에서만** 렌더된다.
//
// 🔴 경계를 «주의해서 지키는 것» 으로 두지 않는다. 두 축이 구조로 갈려 있다:
//      ① 데이터의 문이 하나다        `api/read-console-sample.ts` 뿐이고 그 문 뒤엔
//                                    백엔드로 가는 코드가 없다(판독자에 그 코드가 없다).
//      ② 라우트 그룹이 다르다        `(demo)/**` 는 인증 가드가 없고, `(console)/**` 는
//                                    첫 줄이 `isAuthenticated()` 다. 한 파일이 양쪽에
//                                    동시에 있을 수 없다.
//
// 🔴 여기서 무언가를 `(console)` 화면이 재사용하고 싶어지면 **그것은 신호다** — 재사용할
//    것은 `shared/ui` 의 표현 원자이지 이 디렉터리가 아니다. 반대 방향(둘러보기가
//    `features/*-ops` 를 가져다 쓰는 것)은 **금지**다: 그 모듈들은 fetch 를 한다.
// =============================================================================

export {
  readConsoleSample,
  __resetConsoleSampleCache,
  overviewDomain,
  sectionDomains,
  findDomain,
  type ConsoleSampleResult,
} from './api/read-console-sample';

export { filterRows, rowSearchText } from './lib/filter-rows';
export { actionsForTable, DEMO_DISABLED_REASON } from './lib/sample-actions';

export { SampleDataBanner } from './ui/SampleDataBanner';
export { SampleProvenance } from './ui/SampleProvenance';
export { SampleMetrics } from './ui/SampleMetrics';
export { SampleTableCard } from './ui/SampleTableCard';
export { DemoActionButton } from './ui/DemoActionButton';
export { DomainCard } from './ui/DomainCard';
export { DemoTourNav } from './ui/DemoTourNav';
