# TASK-PC-FE-255 — 도메인 가이드 이해도 개선 (목차 + 상태 다이어그램)

**Status:** backlog
**Area:** platform-console / console-web · **Files:** `src/features/*-guide/components/*GuideScreen.tsx` (6종) + `src/shared/ui/guide-primitives.tsx`
**Type:** TASK-PC-FE (frontend, 순수 presentation 레이어)

## Goal

6개 도메인 가이드(IAM · WMS · SCM · Finance · ERP · E-Commerce)의 **사용자 이해도**를 표현 레이어만 손대서 끌어올린다. 신규 데이터·백엔드·계약 변경 **없음** — 기존 정적 콘텐츠의 렌더 방식만 개선한다. 근거 = 6개 GuideScreen 전문 정독 기반 UX 평가(종합 A−, 남은 레버 3가지: 목차 · 상태 다이어그램 · 작업 레시피).

## Scope

**IN (P0 — 이 태스크):**

1. **페이지 내 목차(in-page TOC)** — 각 GuideScreen 은 이미 섹션 `id`(`scm-guide-procurement`, `wms-guide-inventory` 등)를 갖고 있으나 사용자용 점프 네비가 없다. 기존 `id` 를 소스로 sticky TOC(또는 상단 앵커 칩)를 `guide-primitives.tsx` 에 공용 컴포넌트로 추가하고 6개 화면에 배선. 특히 긴 가이드(WMS · SCM · ERP · E-Commerce)에서 효과 최대.
2. **상태 전이 시각 다이어그램** — 상태가 `상태|종료|의미` 표로만 있어 전이가 문장으로만 읽힌다. 기존 `TerminalCell`(●) 마커를 살린 **칩+화살표** 공용 컴포넌트(예: `준비중 → 발송 → 배송중 → 배송완료`)를 상태표 **위에** 추가(표는 상세로 유지). 선형/분기 상태머신 대상: 주문 · 배송(E-Commerce) · 결재(ERP) · PO(SCM) · 예약·주문(WMS).

**OUT (후속 태스크 후보 — P1/P2):**

- 작업 레시피("이럴 땐 이렇게") 도메인당 2~3개 — IAM 온보딩 흐름 번호형 패턴 재사용 (P1).
- 인프라 용어(`assume-tenant`·`read-model`·`producer`·`사가`·`S5`·`F5`) 인라인 정의/미니 용어집 (P1).
- 모든 도메인 가이드에 "처음이면 여기부터" 읽기-경로 배너(현재 IAM 에만 존재) (P2).
- 가이드 발견성(각 도메인 개요→가이드 유도) + 도메인 간 교차 링크 클릭화 (P2).

## Acceptance Criteria

- 공용 TOC 컴포넌트가 `guide-primitives.tsx` 에 추가되고 6개 GuideScreen 전부에 배선된다(각 화면의 기존 섹션 `id` 를 소스로 자동 구성 — 하드코딩 목차 금지).
- 공용 상태-흐름 컴포넌트가 추가되고, 위 5개 상태머신 화면에 표 **상단**으로 삽입된다(기존 상태 표는 삭제하지 않고 유지).
- `종료(●)` / `주의(●)` / `운영자 변경` 등 기존 상태 시맨틱과 색·마커가 다이어그램에서 **일관**된다.
- 신규 데이터 fetch·권한 게이트·`'use client'` 도입 **없음**(가이드는 server component·정적 유지). 백엔드/계약/producer 파일 **0 변경**.
- 접근성 회귀 0 — TOC 는 키보드 이동·`aria` 라벨, 다이어그램은 스크린리더용 텍스트 대체(기존 상태표가 그 역할 유지 가능).
- 기존 가이드 `data-testid`(예: `wms-guide-order-*`, `scm-guide-po-*`) **보존** — 회귀 테스트 표면 유지. 신규 컴포넌트엔 신규 testid 부여.
- `tsc --noEmit` 0 · `pnpm lint` 0 · vitest 회귀 0(신규 TOC/다이어그램 렌더 테스트 추가).

## Related Specs

- `specs/services/console-web/architecture.md` — 콘솔 프론트 구조.
- `platform-console/docs/conventions/frontend-ui.md` — console UI 컨벤션(정경 홈). 신규 공용 컴포넌트는 이 컨벤션 준수.
- 가이드 화면 자체가 spec-light(정적 참조) — 새 계약 불요.

## Related Contracts

- 없음. 이 태스크는 **콘솔 내부 표현 레이어** 전용 — API/이벤트 계약 무관(가이드는 데이터 fetch 없음).

## Edge Cases

- 섹션 `id` 가 없는 화면(홈 등)엔 TOC 미적용 — 도메인 가이드 6종에만.
- 상태가 3개 이하로 짧은 화면(제품 3상태 등)은 다이어그램 이득이 적음 — 표만 유지 허용(다이어그램 강제 금지).
- 모바일 협폭에서 sticky TOC 가 콘텐츠를 가리지 않도록(접기/상단 배치 fallback).
- 다크/라이트 양 테마에서 다이어그램 칩 대비 유지.

## Failure Scenarios

- 목차를 기존 `id` 가 아니라 **하드코딩**하면 섹션 추가/개편 시 드리프트(가이드는 FE 태스크로 자주 바뀜) → 반드시 `id` 소스 기반 자동 구성.
- 다이어그램이 상태표를 **대체**해 버리면 스크린리더·상세 의미가 소실 → 표는 반드시 유지(다이어그램은 보조).
- 신규 컴포넌트가 `'use client'` 를 끌어들이면 가이드 정적성 위반 → 순수 server component 유지.

## Reference

- UX 평가·개선 로드맵(세션 산출): 6개 GuideScreen + `guide-primitives.tsx` 정독 기반, 종합 A−, P0=목차+다이어그램.
- 참고 화면(패턴 재사용): `IamGuideScreen`(3부 점진 공개·온보딩 번호형), 공용 원자 `guide-primitives.tsx`(`Mono`/`NoteCard`/`StateTh`/`TerminalCell`/`AttentionCell`).

## Dependencies

- 선행 없음(순수 FE presentation). 후속 P1/P2 태스크는 이 태스크 머지 후 별도 발행.
