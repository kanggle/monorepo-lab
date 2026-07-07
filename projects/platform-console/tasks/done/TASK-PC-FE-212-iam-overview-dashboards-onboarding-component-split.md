# TASK-PC-FE-212 — IAM 개요·대시보드·운영자개요·온보딩 god-file 컴포넌트 분할 (iam-overview · dashboards · operator-overview · onboarding)

**Status:** done
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

IAM 신원-평면 분할(PC-FE-209/210/211)을 마무리하는 **전면 sweep의 마지막 배치** — IAM 개요/대시보드/운영자개요/온보딩 피처의 god-file을 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 분할. **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- iam-overview: `IamOverviewScreen.tsx`(~317)
- dashboards: `OperatorOverviewScreen.tsx`(~300)
- operator-overview: `DomainCard.tsx`(~353)
- onboarding: `CreateOrganizationForm.tsx`(~198, **폼 → hook-only 판단 대상**)

## Scope

각 god-file에서 응집된 **프레젠테이션** 조각을 같은 `components/` 디렉터리의 신규 sibling 파일로 추출; 원본은 orchestration 유지. `IamOverviewScreen`/`OperatorOverviewScreen`은 카운트/메트릭 카드·최근 활동 패널·섹션별 degrade 배너를(EcommerceOverview[PC-FE-199] 선례 동형), `DomainCard`(operator-overview의 도메인별 카드, 353)는 카드 내부 상태/액션/배지 블록을 분리 후보로 본다. `CreateOrganizationForm`은 **폼 hook-only 판단**(PC-FE-196/200 선례): 이미 훅 위임된 flat 폼이면 무변경, 반복 필드 그룹 있으면만 추출.

모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처·barrel 공개 API 불변. server component(개요류)는 추출 조각도 `'use client'` 없이 server-compatible 유지(PC-FE-199 EcommerceOverview 선례).

**Out of scope:** `api/`·`hooks/`·proxy·producer·contract·테스트 무변경. 컴포넌트(+barrel re-export 경로)만. `OperatorOverviewScreen`(dashboards)의 fan-out state 로직(overview-api/state) 무변경.

## Acceptance Criteria
- **AC-1** 대상 god-file(IamOverviewScreen·OperatorOverviewScreen·DomainCard)이 의미 있게 축소되고(폼은 hook-only 정당 가능), 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿·도메인 카드 키·메트릭 타일 포함)·aria·요소 순서 보존.
- **AC-3** 섹션별 degrade/forbidden 분기·카운트 vs 레벨 타일 구분·server/client 경계 보존.
- **AC-4** `index.ts` 공개 API 불변, `tsc --noEmit` 0 + `next lint` 0 + `vitest`(iam-overview·dashboards·operator-overview·onboarding 전 스위트) green, 회귀 0.

## Edge Cases / Failure Scenarios
- **server component 경계**: 개요류가 순수 server component면 추출 조각에 `'use client'` 금지(PC-FE-199 선례) — 클라이언트 전용 훅 유입 시 hydration 회귀.
- `DomainCard`(operator-overview)는 도메인별 상태(FRESH/STALE/UNREACHABLE 등) 배지·재시도 액션 조건 보존.
- 폼(CreateOrganizationForm) hook-only 정당성 — flat 폼 강제 분할 금지.
- **동시 세션 조율**: `pc-fe-206-onboarding-what-happens`가 onboarding 표면을 건드릴 수 있음 → 착수 시 rebase/충돌 확인.

## Related
- 미러: PC-FE-199/200 (개요/화면 분할).
- 선행(선택): PC-FE-208(iam-gateway dedup) — 파일 disjoint 독립 병렬.
- 기존 테스트(계약): `tests/unit/{IamOverviewScreen,iam-overview*,OperatorOverviewScreen,operator-overview*,DomainCard,onboarding*,CreateOrganizationForm}.test.tsx`.
- **→ 완결 시 IAM god-file 분할 sweep(PC-FE-209~212) + iam-gateway dedup(PC-FE-208) = IAM "scm처럼" 전면 리팩토링 완료.**
