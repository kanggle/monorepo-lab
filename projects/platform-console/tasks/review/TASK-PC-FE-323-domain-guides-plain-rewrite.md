# Task ID

TASK-PC-FE-323

# Status

review

# Title

도메인 가이드 6개(IAM · WMS · SCM · Finance · ERP · E-Commerce)를 쉬운 말 · 짧은 문장으로 다시 쓰고, 화면의 출처 · 근거 표시를 뺀다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- guide

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 화면 문구 · 정적 데이터. 공용 틀과 시험은 Opus 5.5, 가이드 6개 문장은 Sonnet 5 에이전트 6개가 나눠 맡음.
>
> 🔵 **소유자 결정(2026-10-09 UTC):** 전역 가이드(`TASK-PC-FE-322`)에 이어 «도메인별 가이드도 다 쉽게» → 구현자 추천(가이드별 같은 방식 · 번호 노출 가드) → «진행».

---

# Dependency Markers

- 선행: `TASK-PC-FE-322`(전역 가이드 · 같은 문체의 본보기 `features/global-guide/data.ts`) — #4249 머지됨.
- 같은 시기: 다른 세션의 열린 worktree 둘(`chore/demo-ami-pin-24`, `chore/close-window24`)은 콘솔 가이드를 건드리지 않음(2026-10-09 확인).

# Background (기안 시 측정, `origin/main` `9941fb5be`)

- 공용 틀 `shared/guide/DomainGuideTabs.tsx` 의 «권한 안내» 탭이 `PermissionMapTable` 을 `showSources` 기본값(true)으로 그려 **«근거» 열(파일 경로)이 6개 가이드 전부에 보였다**.
- 렌더된 글자에서 실제로 보이는 번호 누출은 **5곳**(IAM `ADR-MONO-046` · SCM `ADR-MONO-027` ×3 · E-Commerce `ADR-MONO-037`). 소스에서 센 «번호·파일 이름이 나오는 줄» 95개는 대부분 코드 주석이었다 — 화면 기준으로 다시 잰 값이 5다.

# Goal

처음 보는 사람이 도메인 가이드의 탭 하나를 짧게 읽고 이해한다 — 전역 가이드(322)와 같은 문체.

# Scope

## In Scope

- 공용 틀 `DomainGuideTabs.tsx`: 메뉴 표의 «근거» 열 끄기(`showSources={false}`) · 탭 안내 문장 · 표/절 제목(«메뉴별 권한», «알아 둘 예외») · «정보 없음» 문장.
- 6개 가이드의 `features/<d>-guide/data.ts` · `components/<D>GuideScreen.tsx` 화면 문장.
- 새 시험 `tests/unit/domain-guides-plain.test.tsx` — 6개 가이드 × 8개 탭의 렌더된 글자에 티켓 · ADR 번호, 파일 이름, `파일:줄` 0, «출처» · «근거» 표시 0.

## Out of Scope

- 첫 탭의 «도메인 한눈에» 요약(`shared/guide/domain-features.ts`, 321 — 소유자 확인 끝남) · 전역 가이드(322).

# Acceptance Criteria

- AC-1: 6개 가이드의 탭 8개 이름 · 순서 · testid · 섹션 id 불변(기존 가이드 시험 초록).
- AC-2: 새 시험 — 6개 가이드 어디에도 «출처» · «근거» 표시가 없다.
- AC-3: 새 시험 — 6개 가이드 × 8개 탭 + 머리말의 렌더된 글자에 `TASK-` · `ADR-` · 파일 이름 · `파일:줄` 0. 시험이 무는지 bite 로 확인.
- AC-4: 숫자 · 역할 이름 · 상태 이름 · 메뉴 이름은 바뀌지 않는다(새 사실을 지어내지 않는다) — 구현자 diff 검토.
- AC-5: tsc · lint(바꾼 파일) · vitest 전체 rc=0, axe 위반 0.
- AC-6: 소유자가 배포된 콘솔에서 «쉽고 보기 쉽다» 확인.

# Related Specs

- `specs/services/console-web/architecture.md` (가이드는 정적 화면)

# Related Contracts

- 없음(정적 화면).

# Edge Cases

- 6개 가이드가 한 worktree 에서 동시에 고쳐진다 — 파일이 겹치지 않게 가이드별 디렉터리 + 그 가이드 시험만 맡김. 공용 틀은 구현자(Opus)만 고친다.
- 시험은 렌더된 `textContent` 만 본다 — 주석 · `sources` 데이터(화면에 안 그려짐)는 걸리지 않는 것이 맞다.

# Failure Scenarios

- 문장을 줄이다 사실이 틀어짐 → 에이전트에게 «모르면 줄이되 다시 해석하지 말 것», 구현자가 diff 에서 숫자 · 이름 변화를 확인.

# Implementation Notes (2026-10-09 UTC)

- 공용 틀 · 시험 = 구현자(Opus 5.5). 가이드 6개 문장 = Sonnet 5 에이전트 6개(가이드별 디렉터리만 · git 금지). 6개 모두 자기 가이드 시험 + 누출 시험 + tsc + lint rc=0 보고.
- IAM 은 12줄만 바뀌었다 — 화면 문장이 이미 쉬운 편(PC-FE-238/298)이었다는 에이전트 보고.
- **구현자 대조에서 바로잡은 것 3건**(AC-4):
  1. Finance — 계좌 서비스의 «홀드/캡처/해제» 가 «자금 묶기/풀기» 로 줄며 **캡처가 빠짐** → «묶기(홀드) · 확정(캡처) · 풀기».
  2. Finance — 대사 상태를 «미해결/해결» 로 바꿈 → 개요 타일의 실제 글자는 «미해소 대사 차이»(`FinanceOverviewScreen.tsx`), 원장 필터는 `OPEN`/`RESOLVED` → «미해소(OPEN) · 해소(RESOLVED)» 로 화면 글자와 맞춤.
  3. SCM — 권한 안내가 «테넌트 전환에서 `scm` 을 선택해야» 라고 했다. **원문부터 낡은 사실**이었다: SCM 게이트웨이는 `trustEntitledDomains()` 로 SCM 을 구독한 테넌트도 받는다(`OAuth2ResourceServerConfig.java` `tenantGate()`). → «SCM 을 구독한 테넌트(예: 데모의 demo-corp)를 고르면 열린다».
- 확인했지만 맞았던 것: WMS «가용 수량 10개 이하면 저재고» = `availableQty <= 10`(`InventoryProjectionService.java`). E-Commerce «정보가 비어 보이는 회원» 설명 = 원문과 같은 사실.
- 따옴표로 부른 화면 이름 대조(스크립트): 새로 넣은 따옴표 이름 18개 중 가이드 밖 콘솔 코드에 없는 것 5개 — 둘은 구현자의 2번 수정(`OPEN`/`RESOLVED` 괄호), 둘은 원문에 이미 있던 이름, 하나는 괄호 안 풀이만 바뀐 것(«빈 상한(아무것도 불가 → 못 씀)»). 화면 이름을 새로 지어낸 경우 없음.

# AC Results (2026-10-09 UTC)

- AC-1 ✅ 6개 가이드 기존 시험(탭 8개 · 섹션 id · testid) 전부 초록 — 시험 파일은 하나도 고치지 않았다.
- AC-2 ✅ 새 시험 «출처/근거 표시 0» × 6.
- AC-3 ✅ 새 시험 «번호 · 파일 이름 0» × 6(기안 시 3개 가이드 빨강 → 초록). **bite**: SCM `data.ts` 에 `(ADR-MONO-027)` 주입 → `scm-guide` 의 그 시험 하나만 빨강, 원본 복원 `cmp` 일치.
- AC-4 ✅ 구현자 대조 — 위 3건 정정, 나머지 숫자 · 상태 · 역할 · 메뉴 이름 불변.
- AC-5 ✅ `tsc --noEmit` rc=0 · `next lint`(바꾼 디렉터리 + 새 시험) rc=0 · vitest 전체 **351/351 파일 · 3987/3987** rc=0 · 각 가이드 axe 시험 초록.
- AC-6 ⚪ 소유자 확인 대기(배포 후).
