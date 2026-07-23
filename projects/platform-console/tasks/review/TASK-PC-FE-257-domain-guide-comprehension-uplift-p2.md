# TASK-PC-FE-257 — 도메인 가이드 이해도 개선 P2 (읽기경로 배너 + 발견성 + 교차링크)

**Status:** review
**Area:** platform-console / console-web · **Files:** `src/features/*-guide/{components/*GuideScreen.tsx,data.ts}` (6종) + `src/features/{wms-ops,scm-ops,ecommerce-ops}/components/*Overview.tsx` (3종) + `src/shared/ui/guide-primitives.tsx`
**Type:** TASK-PC-FE (frontend, 순수 presentation 레이어)

## Goal

TASK-PC-FE-255(P0 목차·다이어그램) · 256(P1 레시피·용어) 후속 = **P2**. 가이드를 "찾고, 어디부터 읽고, 도메인 사이를 넘나드는" 네비게이션을 표현 레이어만으로 개선한다. 세 레버:

- **A. 읽기경로 배너** — "처음이면 여기부터" 안내. **현재 IAM 가이드에만** 있다(상단 intro `<p>` "처음이라면 1·2 만 읽으세요"). 나머지 5개 가이드(WMS · SCM · Finance · ERP · E-Commerce)에 동등 안내를 붙인다.
- **B. 발견성(개요→가이드 링크)** — **형제 straggler 교정**. IAM · Finance · ERP 개요 화면은 이미 `개요 → 가이드` 바로가기 링크가 있으나(`finance-overview-link-guide` 등), **WMS · SCM · E-Commerce 개요 화면엔 없다**(실측: 그 3개 Overview 파일에 `guide`/`가이드` 참조 0건). Finance 패턴을 복제해 3개 개요에 가이드 링크를 배선한다.
- **C. 교차링크** — 가이드 본문에서 **다른 도메인을 가리키는 진짜 참조**를 클릭 가능한 링크(그 도메인의 가이드/화면)로 만든다. 과잉 링크 금지(자기 도메인 용어는 대상 아님).

신규 데이터 fetch·백엔드·계약 변경 **없음**. 개요 화면은 이미 client 컴포넌트일 수 있으나 **가이드 화면은 server-component 정적성 유지**(`'use client'` 신규 도입 금지).

## 🔴 선행 측정 (착수 시 필수 — 상속 금지)

- **B(발견성)**: 착수자가 Finance/ERP/IAM 개요의 기존 가이드 링크 마크업(`<nav aria-label="… 바로가기">` 안 `<Link href="/…/guide" data-testid="…-overview-link-guide">가이드</Link>`)을 **실제로 읽고** 동일 패턴·동일 testid 규칙으로 3개(wms/scm/ecommerce)에 복제한다. 각 개요 화면의 기존 구조(바로가기 nav 유무)를 확인하고 자연스러운 위치에 삽입.
- **C(교차링크)**: 각 `*-guide/data.ts` 렌더 문자열을 **실측**해 "다른 도메인을 가리키는 참조"만 추린다(예: E-Commerce 가이드가 WMS 풀필먼트를 언급 → WMS 가이드로 링크). 자기 도메인 용어(ecommerce 가이드의 "주문/배송")는 교차링크 대상이 **아니다**. 참조가 없거나 억지스러운 도메인은 링크를 강제하지 않는다(0개 허용).

측정 산출물(도메인별 교차링크 목록 / 배너 문구)을 PR 설명 또는 done 노트에 남긴다.

## Scope

**IN (P2 — 이 태스크):**

1. **`GuideReadingPath`(또는 동등물) 공용 프리미티브** — 상단 배너 형태의 "처음이면 여기부터" 안내(제목 + 짧은 안내). `guide-primitives.tsx` 에 추가. 5개 가이드(IAM 제외)에 배선. IAM 의 기존 inline 안내가 문구 원형 — 깔끔하면 IAM 도 이 공용 컴포넌트로 정규화 가능(선택, 강제 아님; 정규화 시 기존 문구·의미 보존).
2. **개요→가이드 링크** — Finance 패턴(`<Link href="/<domain>/guide" data-testid="<domain>-overview-link-guide">가이드</Link>`) 복제로 WMS · SCM · E-Commerce 개요 화면에 배선(기존 바로가기 nav 있으면 거기, 없으면 개요 헤더 하단에 신설).
3. **교차링크** — 실측된 진짜 교차도메인 참조를 Next `<Link>` 로. 링크 대상은 그 도메인의 가이드 라우트(`/<domain>/guide`) 또는 해당 운영 화면. 자기 도메인·억지 참조 제외.

**OUT:**

- 새 다이어그램(시퀀스/데이터 흐름)·검색·인터랙티브 시뮬레이터 등 `'use client'` 도입이 필요한 것 — 별도 판단.
- 가이드 콘텐츠 자체의 대규모 재작성 — 이 태스크는 네비게이션/발견성만.

## Acceptance Criteria

- 공용 `GuideReadingPath`(읽기경로 배너)가 `guide-primitives.tsx` 에 추가되고 **5개 가이드(WMS·SCM·Finance·ERP·E-Commerce)에 배선**된다. 각 배너 문구는 그 도메인의 실제 섹션 구성에 맞다(허구 섹션 언급 0). IAM 은 기존 안내 유지 또는 공용 컴포넌트로 정규화(둘 다 허용, 의미 보존).
- WMS · SCM · E-Commerce 개요 화면 각각에 `개요→가이드` 링크가 추가되고, **IAM/Finance/ERP 와 동일한 testid 규칙**(`<domain>-overview-link-guide`)·동일 접근성(키보드 포커스 링) 을 따른다.
- 교차링크는 **실측된 진짜 교차도메인 참조에만** 적용된다(자기 도메인 용어 링크화 0, 억지 링크 0 — 정의된 각 링크가 실제 그 문맥에서 다른 도메인을 가리킴을 정독으로 입증). 참조 없는 도메인은 0개 허용.
- 신규 데이터 fetch·권한 게이트 **없음**. 가이드 화면에 `'use client'` 신규 도입 **없음**(개요 화면은 기존 지시어 유지 — 새로 추가 금지). 백엔드/계약/producer 파일 **0 변경**.
- 기존 `data-testid`(가이드 `*-guide-*` + 255 `guide-toc`/`state-flow` + 256 `*-guide-recipe*`/`*-guide-glossary*` + 개요 `*-overview-*`) **전부 보존**. 신규 요소엔 신규 testid.
- 접근성: 배너·링크 키보드 접근, 다크/라이트 대비 유지, axe 회귀 0.
- `tsc --noEmit` 0 · `pnpm lint`(=`next lint`) 0 · vitest 회귀 0(신규 배너·개요 링크 렌더 테스트 추가 — 최소 1개 개요 화면의 가이드 링크 + 1개 가이드의 읽기경로 배너 마운트 단언).

## Related Specs

- `specs/services/console-web/architecture.md` — 콘솔 프론트 구조.
- `platform-console/docs/conventions/frontend-ui.md` — console UI 컨벤션(정경 홈). 링크·포커스 스타일 준수.

## Related Contracts

- 없음. 콘솔 내부 표현/네비게이션 전용.

## Edge Cases

- 개요 화면에 기존 "바로가기" nav 가 없으면 신설하되, 기존 헤더/레이아웃과 충돌하지 않게(다른 개요의 배치 참조).
- 교차 참조가 없는 가이드는 링크 0개(억지 생성 금지 — 잘못된 링크가 오히려 오도).
- 읽기경로 배너가 짧은 가이드에서 과해 보이지 않게(1~2문장).
- IAM 정규화 선택 시 기존 3부-분리 안내("1·2 만 읽으세요")의 뉘앙스 보존.

## Failure Scenarios

- **개요→가이드 링크 testid 를 형제와 다르게 지으면** parity 가 깨져 나중 회귀 가드/테스트가 놓친다 → 반드시 `<domain>-overview-link-guide` 규칙.
- 교차링크를 자기 도메인 용어에까지 남발하면 가독성 저하·오도 → 실측된 진짜 교차참조만.
- 개요 화면에 `'use client'` 를 새로 끌어들이거나 가이드에 도입하면 정적성/기존 경계 위반 → 기존 지시어 유지, 신규 도입 금지.
- 배너가 실제 없는 섹션("먼저 3번 표를…")을 가리키면 오도 → 각 가이드의 실제 섹션 구성 대조.

## Dependencies

- **선행** = PC-FE-255(P0, #2917) · 256(P1, #2921) — 둘 다 DONE. `guide-primitives.tsx`·`GuideToc` 위에 얹는다.
- **후속** = P2 이후 잔여(시퀀스 다이어그램 등)는 필요 시 별도 발행. 이 태스크로 UX 평가(2026-07-23) P0/P1/P2 로드맵 종결.

## Reference

- UX 평가·개선 로드맵(세션 산출, 2026-07-23): 종합 A−. P0=목차+다이어그램(255 DONE), P1=레시피+용어(256 DONE), **P2=읽기경로 배너+발견성+교차링크(이 태스크)**.
- 실측(2026-07-23): 읽기경로 안내는 IAM 만 보유(`IamGuideScreen` intro `<p>`). 개요→가이드 링크는 IAM/Finance/ERP 보유·WMS/SCM/E-Commerce 미보유(straggler). 발견성 패턴 = `FinanceOverviewScreen` 의 `<nav aria-label="Finance 화면 바로가기">` 안 `<Link href="/finance/guide" data-testid="finance-overview-link-guide">`.
- 공용 원자 `guide-primitives.tsx` 현재 export: `Mono`·`NoteCard`·`StateTh`·`AttentionCell`·`TerminalCell`·`GuideToc`·`StateFlow`·`GuideRecipe`·`Term`·`Glossary`.
