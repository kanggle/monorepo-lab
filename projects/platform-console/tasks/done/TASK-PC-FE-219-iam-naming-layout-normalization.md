# TASK-PC-FE-219 — IAM 네이밍/레이아웃 정규화 (audit hook 배치 + core-wrapper 판단)

**Status:** done
**Done:** 2026-07-08 · impl PR #2313 squash `42b97837e` (3-dim verified) — (1)audit use-audit-screen.ts→hooks/(git mv+소비처 3), (2)accounts/audit core-wrapper SKIP 양쪽(근거: audit=const-move 과분할, accounts=exportAccount가 callGapAdmin 우회→PROFILE 누출), tsc 0·lint 0·audit 67/67.
**Area:** platform-console / console-web · **Refactor:** behavior-preserving rename/move (저위험 정리)
**Analysis model:** Opus 4.8 · **Impl model:** Sonnet 4.6 (기계적 move + import 갱신 + 1 판단)

---

## Goal

PC-FE-217(WMS/SCM/EC 정규화)의 IAM 짝. IAM sweep(208~212) 후 남은 **네이밍/레이아웃 편차**를 정리해 콘솔 전 도메인 컨벤션을 완전 정렬한다. 동작 무변경(순수 이동·개명·import 갱신).

## Scope

**(1) audit form-hook 배치 정규화 [확정]** — `audit/components/use-audit-screen.ts`(190줄)는 `use-*` 훅인데 `components/`에 있고, `audit/hooks/`는 이미 존재(`use-audit.ts`). PC-FE-217 이커머스 픽스와 정확히 동일 패턴 → **`audit/hooks/use-audit-screen.ts`로 `git mv`** + 소비처 2개 import 갱신:
- `audit/components/AuditFilterBar.tsx`, `audit/components/AuditScreen.tsx` (`./use-audit-screen` → `../hooks/use-audit-screen`).
- 이동한 훅 자체의 상대 import 깊이 조정(`../api/*`·`../hooks/*` 등). `'use client'` 유지.
(이동 전 grep으로 소비처 전수 재확인 — 위 2개 외 없음 확증.)

**(2) accounts/audit core-wrapper 네이밍 [판단]** — operators/partnerships/subscriptions는 core를 전용 `*-client.ts`로 두는데(다중 endpoint 모듈이 core 공유), accounts/audit는 core를 `*-api.ts`에 접어넣음. **판단 기준**: `*-client.ts` 분리는 **≥2개 endpoint 모듈이 core를 공유할 때** 생긴 패턴 — accounts(단일 `accounts-api.ts`, 8 함수)·audit(단일 `audit-api.ts`, `queryAudit` 1함수)는 **단일 endpoint 모듈**이라 core를 접어넣은 게 spirit상 어긋나지 않을 수 있다. 구현자 판단:
- **(a) 정렬 우선**이면 `accounts-client.ts`(callGapAdmin+ACCOUNTS_PROFILE)·`audit-client.ts`(core+AUDIT_PROFILE) 추출 → `accounts-api.ts`/`audit-api.ts`는 endpoint만. 소비처(state·proxy·test) import 갱신.
- **(b) SKIP**이면 근거를 PR 설명에 명시("core 파일은 ≥2 endpoint 모듈 공유 시 분리; 단일 모듈은 접는 게 일관 — accounts/audit는 후자"). PC-FE-217 demand-planning SKIP과 동형.
- **lean = 실익으로 판단**(단일-모듈 features를 억지로 쪼개 audit-api를 1함수만 남기는 건 과분할 위험 → SKIP도 정당). audit(1함수)는 특히 SKIP 쪽, accounts(8함수)는 (a)가 더 나을 수 있음 — 개별 판단 허용.

**Out of scope:** 훅/엔드포인트 로직·시그니처·proxy·contract·컴포넌트 마크업 무변경. barrel(`index.ts`)은 이미 클린(문제 없음)이라 손대지 않음. onboarding-client.ts(별도 auth 계약)·overview-api·operator-overview-api(BFF/fan-out)는 dedup/정규화 대상 아님.

## Acceptance Criteria
- **AC-1** (1) `use-audit-screen.ts`가 `audit/hooks/`로 이동, 소비처 2개 + 자체 import 갱신, 동작 무변경.
- **AC-2** (2) 개별 판단((a) 추출 or (b) SKIP)과 근거를 PR 설명에 명시. (a)면 소비처 import 전수 갱신·동작 무변경.
- **AC-3** `tsc --noEmit` + `pnpm lint` + `vitest`(audit·accounts 관련 전체) green. 이동/개명으로 import 깨진 곳 0(가장 큰 함정 — `pnpm lint`+`tsc`로 전수 확인, env_console_web_local_verify_needs_lint). 테스트가 이동 모듈을 path로 import하면 **import 경로만** 갱신(전 편집 보고).

## Edge Cases / Failure Scenarios
- **import 경로 누락**이 최대 함정 — 이동/개명 후 소비처(테스트 포함) 미갱신 시 CI RED. `tsc`+`pnpm lint` 전수 확인 필수.
- (2)에서 core 추출 시 `*UnavailableError`·PROFILE·per-endpoint 헤더 매트릭스가 endpoint 함수와 동일 계약 유지(shared iam-gateway `callAdminGateway` 경유 불변).

## Related
- 미러: PC-FE-217(WMS/SCM/EC 정규화 — audit hook 이동은 이커머스 hook 이동과 동일 패턴), PC-FE-208(iam-gateway core).
- 파일 disjoint 병렬 가능: PC-FE-218(operators/hooks).
