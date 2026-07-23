# TASK-PC-FE-256 — 도메인 가이드 이해도 개선 P1 (작업 레시피 + 용어 정의)

**Status:** ready
**Area:** platform-console / console-web · **Files:** `src/features/*-guide/{components/*GuideScreen.tsx,data.ts}` (6종) + `src/shared/ui/guide-primitives.tsx`
**Type:** TASK-PC-FE (frontend, 순수 presentation 레이어)

## Goal

TASK-PC-FE-255(P0 = 목차 + 상태 다이어그램) 후속. 6개 도메인 가이드(IAM · WMS · SCM · Finance · ERP · E-Commerce)의 **사용자 이해도**를 표현 레이어만 손대서 한 단계 더 끌어올린다. P1 레버 **두 가지**를 친다:

- **A. 작업 레시피** ("이럴 땐 이렇게") — 도메인당 2~3개의 번호형 절차. IAM 가이드가 이미 쓰는 `DELEGATION_CHAIN` 번호 `<ol>` 패턴을 공용화해 재사용한다.
- **B. 용어 정의** — 사용자가 읽어도 모를 도메인/인프라 용어에 인라인 정의(툴팁/`<abbr>`) 또는 가이드별 미니 용어집을 붙인다.

신규 데이터 fetch·백엔드·계약 변경 **없음** — 기존 정적 server-component 가이드의 표현만 확장한다.

## 🔴 선행 측정 (착수 시 필수 — 상속 금지)

**이 태스크의 용어·레시피 목록은 아직 확정되지 않았다.** 착수하는 구현자는 **먼저 6개 가이드의 실제 렌더 콘텐츠를 전수 측정**해서 목록을 만든다. 아래 두 함정을 피한다:

1. **용어 후보를 물려받지 마라.** TASK-PC-FE-255 done 노트가 예로 든 `assume-tenant`·`read-model`·`producer`·`사가`·`S5`·`F5` 는 **측정된 출처가 아니라 가설**이다. 실측 결과 이 용어들은 대부분 **코드 주석에만** 있고 **가이드 사용자 문자열(각 `*-guide/data.ts` 의 렌더되는 한글 콘텐츠)에는 거의 등장하지 않는다**(가이드는 이미 평이한 한국어로 쓰였다). ⇒ **각 `data.ts` 를 실제로 읽고, 화면에 렌더되는 문자열에서 "일반 운영자가 모를 법한" 용어만** 골라 정의한다. 렌더되지 않는 용어(코드 주석·타입명)는 대상 아님.
2. **레시피는 그 도메인의 실제 워크플로에 정확해야 한다.** 각 GuideScreen + `data.ts` 의 상태 표·메뉴 표를 근거로, 실제 존재하는 상태·메뉴만 참조하는 절차를 쓴다(허구의 단계·존재하지 않는 버튼 금지).

측정 산출물(어느 용어를 왜 골랐는지 / 도메인별 레시피 목록)을 PR 설명 또는 done 노트에 남긴다.

## Scope

**IN (P1 — 이 태스크):**

### A. 작업 레시피 (공용 컴포넌트 + 배선)

1. `guide-primitives.tsx` 에 공용 **`GuideRecipe`**(또는 동등물) 신설 — 제목 + 번호형 `<ol>` 단계 목록. IAM `DELEGATION_CHAIN` 의 원형 마커 번호 스타일(`h-6 w-6 rounded-full ...`)을 재사용/일반화한다. 순수 정적, `'use client'` 미도입.
2. 6개 도메인 각각에 **2~3개** 레시피 배선. 각 레시피 데이터는 해당 `*-guide/data.ts` 에 상수로 둔다(하드코딩 산재 금지). 예(실측으로 확정): WMS "재고가 모자랄 때 백오더 처리" · E-Commerce "환불 요청이 들어왔을 때" · ERP "결재가 반려됐을 때 재상신" · SCM "발주를 취소해야 할 때" · Finance "전기 마감 후 정정" · IAM "새 직원에게 권한 주기"(기존 온보딩 흐름과 중복 시 그걸 레시피로 재프레이밍).

### B. 용어 정의

3. `guide-primitives.tsx` 에 인라인 용어 정의 프리미티브 신설 — 후보 두 형태 중 택1(또는 병용): (i) `<abbr title>` 기반 인라인 툴팁 래퍼 `Term`, (ii) 가이드 하단 **미니 용어집** `Glossary`(용어 | 뜻 표). 접근성: `<abbr>` 는 스크린리더 호환, 툴팁은 키보드 포커스로도 접근 가능해야 한다(hover-only 금지).
4. **선행 측정에서 확정된 용어만** 정의한다. 도메인별로 정의가 필요한 용어(도메인 enum·약어·역할명·인프라 노출 용어)를 `data.ts` 상수로 두고 배선.

**OUT (후속 P2 후보 — 이 태스크 아님):**

- 모든 도메인 가이드에 "처음이면 여기부터" 읽기-경로 배너(현재 IAM 에만) — P2.
- 가이드 발견성(도메인 개요 화면 → 가이드 유도) + 도메인 간 교차 링크 클릭화 — P2.
- 시퀀스/데이터 흐름 다이어그램(서비스 간 이벤트) — 별도 태스크.
- 가이드 내 검색·인터랙티브 상태 시뮬레이터 등 `'use client'` 도입이 필요한 것 — 정적성 원칙과 트레이드오프, 별도 판단.

## Acceptance Criteria

- 공용 `GuideRecipe`(번호형 절차) 프리미티브가 `guide-primitives.tsx` 에 추가되고, 6개 도메인 각각 **2~3개** 레시피가 배선된다. 각 레시피의 데이터는 해당 `*-guide/data.ts` 상수(하드코딩 산재 금지).
- 각 레시피의 단계가 해당 도메인의 **실제 상태·메뉴**만 참조한다(존재하지 않는 화면·버튼·상태 언급 0 — GuideScreen/`data.ts` 대조로 검증).
- 공용 용어-정의 프리미티브(`Term` 인라인 및/또는 `Glossary` 표)가 추가되고, **선행 측정에서 실제 가이드 문자열에 등장한다고 확인된 용어만** 정의된다(255 예시 목록 상속 금지 — 정의된 각 용어가 그 도메인 렌더 콘텐츠에 실재함을 grep/정독으로 입증).
- 접근성: 용어 툴팁은 키보드 포커스 접근 가능(hover-only 아님), `<abbr>`/`aria` 적절. 레시피 `<ol>` 는 시맨틱 순서 목록. 다크/라이트 양 테마 대비 유지. axe 회귀 0.
- 신규 데이터 fetch·권한 게이트·`'use client'` 도입 **없음**(가이드 server-component 정적성 유지). 백엔드/계약/producer 파일 **0 변경**.
- 기존 가이드 `data-testid`(예: `iam-guide-*`, `wms-guide-*`, `scm-guide-po-*`) 및 PC-FE-255 신설 `guide-toc`/`state-flow` testid **보존**. 신규 컴포넌트엔 신규 testid 부여.
- `tsc --noEmit` 0 · `pnpm lint` 0 · vitest 회귀 0(신규 `GuideRecipe`/용어 프리미티브 렌더 테스트 추가 — 최소 1개 도메인 화면에 레시피·용어가 실제 마운트됨을 단언).

## Related Specs

- `specs/services/console-web/architecture.md` — 콘솔 프론트 구조.
- `platform-console/docs/conventions/frontend-ui.md` — console UI 컨벤션(정경 홈). 신규 공용 컴포넌트는 이 컨벤션 준수(상태칩·날짜·`<dl>` 순서 규칙과 상충 없이).
- 가이드 화면 자체가 spec-light(정적 참조) — 새 계약 불요.

## Related Contracts

- 없음. 콘솔 내부 표현 레이어 전용 — API/이벤트 계약 무관(가이드는 데이터 fetch 없음).

## Edge Cases

- 상태·메뉴가 단순한 도메인(레시피 소재가 2개 미만)은 **2개까지 허용**(3개 강제 금지 — 억지 레시피가 오히려 오해를 부른다).
- 용어가 이미 문맥에서 자명한 화면은 억지 `Term` 래핑 금지(과잉 툴팁은 가독성 저하).
- 인라인 툴팁(`<abbr title>`)은 모바일에서 hover 불가 — 미니 용어집 병용 또는 tap 가능 형태 고려.
- 레시피 `<ol>` 번호와 기존 IAM `DELEGATION_CHAIN` 번호 마커의 시각 스타일 일관.

## Failure Scenarios

- **용어 후보를 255 done 노트에서 그대로 베끼면** 렌더되지 않는 용어(코드 주석 전용)를 정의하거나 실재하지 않는 용어집을 만든다 → 반드시 각 `data.ts` 렌더 문자열 실측 후 확정.
- 레시피가 실제 화면에 없는 단계/버튼을 언급하면 사용자를 오도 → GuideScreen/`data.ts` 의 실제 상태·메뉴만 근거.
- 레시피/용어 데이터를 컴포넌트에 하드코딩 산재하면 유지보수 드리프트 → `data.ts` 상수로 집약.
- 툴팁을 hover-only 로 만들면 키보드·모바일 접근 불가 → 포커스 접근 + 용어집 fallback.
- 신규 컴포넌트가 `'use client'` 를 끌어들이면 가이드 정적성 위반 → 순수 server component 유지.

## Dependencies

- **선행** = TASK-PC-FE-255(P0 목차·다이어그램) — DONE(#2917). `GuideToc`/`StateFlow`/`guide-primitives.tsx` 위에 얹는다.
- **후속** = P2(읽기-경로 배너 · 발견성/교차링크)는 이 태스크 머지 후 별도 발행.

## Reference

- UX 평가·개선 로드맵(세션 산출, 2026-07-23): 6개 GuideScreen + `guide-primitives.tsx` 정독 기반, 종합 A−. P0=목차+다이어그램(255 완료), **P1=작업 레시피+용어 정의(이 태스크)**, P2=읽기경로 배너·발견성.
- 참고 화면(패턴 재사용): `IamGuideScreen` — 3부 점진 공개 + `DELEGATION_CHAIN` 번호형 `<ol>`(레시피 원형) + `OPERATOR_ONBOARDING_AXES` 카드.
- 공용 원자 `guide-primitives.tsx` 현재 export: `Mono`·`NoteCard`·`StateTh`·`AttentionCell`·`TerminalCell`·`GuideToc`·`StateFlow`.
