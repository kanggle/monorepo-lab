# TASK-PC-FE-211 — IAM 파트너십·구독 god-file 컴포넌트 분할 (partnerships · subscriptions)

**Status:** ready
**Area:** platform-console / console-web · **Refactor:** behavior-preserving god-file split
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8 (frontend-engineer 디스패치 — testid/markup byte-보존)

---

## Goal

IAM 신원-평면 분할(PC-FE-209/210)에 이어, operator-token/`X-Tenant-Id` 미러인 `partnerships`·`subscriptions`(조직 설정 평면, PC-FE-187/183 신설) 피처의 god-file 컴포넌트를 콘솔 god-file split 시리즈(PC-FE-098~153) 휴리스틱대로 분할. **behavior-preserving** — 마크업·testid·props·데이터 흐름·훅·렌더 출력 전부 불변. 기존 테스트가 계약(무수정 통과).

대상 god-file(components/):
- partnerships: `PartnershipsScreen.tsx`(**~600 — 콘솔 최대 단일 god-file**) · `PartnershipConfirmDialog.tsx`(~137, 경계)
- subscriptions: `SubscriptionsScreen.tsx`(~224) · `SubscriptionConfirmDialog.tsx`(~130, 경계)

## Scope

`PartnershipsScreen`(600)이 핵심 — host/partner `myRole` 섹션 분리·상태전이 매트릭스(accept/suspend/reactivate/terminate) 버튼 gating·invite 폼·participant add/remove·전 변이 reason-capture를 응집 프레젠테이션 조각(예: 역할별 섹션·상태전이 액션바·초대 폼·참여자 리스트·상태 배지)으로 추출; 컨테이너는 orchestration(state·mutation 생명주기·reason-capture→router.refresh·list-state 분기) 유지. `SubscriptionsScreen`은 카탈로그-derived 도메인 카드/리스트·상태 배지 추출. Confirm 다이얼로그 2종은 focus-trap/reason state 컨테이너 잔류·presentational body만(PC-FE-198 선례) — 경계 크기라 순 이득일 때만.

모든 `data-testid`/`aria-*`/className/요소 순서/key/조건 렌더/텍스트 verbatim 보존, export 심볼·시그니처·barrel 공개 API 불변.

**Out of scope:** `api/`·`hooks/`·proxy·producer·contract·테스트 무변경. 컴포넌트(+barrel re-export 경로)만.

## Acceptance Criteria
- **AC-1** `PartnershipsScreen`(600)이 크게 축소되고(가장 큰 이득), `SubscriptionsScreen`도 의미 있게 축소, 추출 조각이 원본 렌더 출력을 byte-동일하게 재현.
- **AC-2** 모든 testid(인덱스 템플릿·상태전이 버튼·participant 행 포함)·aria·요소 순서·상태전이 매트릭스 gating 조건 보존.
- **AC-3** host vs partner myRole 섹션 분기·cross-org 카피·구독 카탈로그-derived 상태(ACTIVE/SUSPENDED/CANCELLED) 로직 verbatim 보존.
- **AC-4** `index.ts` 공개 API 불변, `tsc --noEmit` 0 + `next lint` 0 + `vitest`(partnerships·subscriptions 전 스위트) green, 회귀 0.

## Edge Cases / Failure Scenarios
- **상태전이 매트릭스 gating**(myRole × 현재 상태 → 허용 액션)이 최대 위험 — 조각 추출 시 조건식·disabled 로직 byte-보존, 하나라도 어긋나면 잘못된 액션 노출.
- Confirm 다이얼로그 focus-trap/reason 검증 보존(컨테이너 잔류).
- 경계 파일(PartnershipConfirmDialog 137·SubscriptionConfirmDialog 130) 억지 분할 금지.

## Related
- 미러: PC-FE-197/199/209/210 (컴포넌트 분할).
- 배경: PC-FE-187(partnerships 신설)·PC-FE-183(subscriptions 신설)·PC-FE-195(파트너십 disambiguation).
- 선행(선택): PC-FE-208(iam-gateway dedup) — 파일 disjoint 독립 병렬.
- 기존 테스트(계약): `tests/unit/{PartnershipsScreen,partnerships-*,SubscriptionsScreen,subscriptions-*}.test.tsx`.
