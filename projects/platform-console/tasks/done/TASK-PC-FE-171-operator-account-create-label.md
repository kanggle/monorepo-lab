# TASK-PC-FE-171 — 운영자 생성 액션 라벨 **"운영자 생성 → 운영자 계정 생성"**

**Status:** done
**Area:** platform-console / console-web · **Scope:** 운영자 관리(/operators) 생성 액션 라벨 + IAM 가이드 네비 참조
**Type:** UI 라벨 일관성 정합 (naming — 순수 label, 도메인/동작 변화 없음)
**Implemented:** branch `task/pc-fe-171-operator-account-label` → **#2161 merged** (squash `e1b748729`). `next lint` + `tsc --noEmit` + `vitest` (80/80) green; CI 22 checks pass.
**Analysis model:** Opus 4.8 · **Impl model:** Opus.

## Goal

운영자 생성 폼은 사람이 아니라 **자격증명을 가진 계정(email·password·roles)을 프로비저닝**한다 —
"특권 작업 / 감사 사유 필수"인 이유가 바로 그것. 액션 라벨을 **"운영자 생성" → "운영자 계정 생성"**으로
바꿔 (a) 성격을 정확히 전달하고, (b) 이미 그 용어를 쓰는 IAM 가이드(`data.ts` `desc: '운영자 계정 생성…'`)
및 내 계정 화면("운영자 계정")과의 기존 불일치를 해소한다.

## Scope (implemented)

**명사형 액션 라벨만 변경** (동사형 서술은 자연스러워 유지):

- `CreateOperatorForm.tsx` — 폼 `aria-label` + `<h2>` 제목 + 플랫폼 스코프 안내문 + submit 버튼(`… (확인 필요)`).
- `operators-confirm-copy.tsx` — 확인 다이얼로그 제목(`… (특권 작업)`) + 확인 버튼 라벨.
- `OperatorsScreen.tsx` — 생성 실패 에러 메시지 + 섹션 설명 액션 목록 첫 항목.
- `iam-guide/data.ts` — 위임 체인 서술 내 **네비 라벨 참조**(`운영자 관리 → 운영자 계정 생성/역할 변경`)만.

**의도적으로 유지(동사형/서술 — 라벨 아님):**

- 확인 다이얼로그 설명문 "…운영자를 <테넌트> 테넌트에 생성합니다."
- `errors.ts` "…운영자만 생성할 수 있습니다."
- `data.ts` "…운영자를 생성하고", "운영자 생성 시 tenantId", "운영자가 생성될 때", 홈-테넌트 코드 주석.
- 섹션명 **"운영자 관리"** (계정 관리로 바꾸면 `/accounts`의 "계정 운영"과 진짜 충돌 → 손대지 않음).

## Acceptance Criteria

- [x] **AC-1** 운영자 생성 관련 명사형 UI 라벨(폼 제목/aria/버튼/확인 다이얼로그 제목·버튼/생성 실패 에러)이
  모두 "운영자 계정 생성" 계열로 읽힌다.
- [x] **AC-2** IAM 가이드의 네비 라벨 참조가 버튼과 일치("운영자 계정 생성"); 가이드 `desc`(기존 "운영자 계정
  생성")와 정합.
- [x] **AC-3** 동사형 서술·섹션명("운영자 관리")·코드 주석은 불변 — "계정" 중복 최소화.
- [x] **AC-4** testid/role 기반 테스트 불변(라벨 텍스트 단언 없음 확인); `pnpm lint` + `tsc --noEmit` + `vitest` green.

## Related Specs / Contracts

- 없음 — 순수 프런트 라벨 변경. API·계약·동작 무변화(`create` 호출/헤더/스키마 그대로).

## Edge Cases

- "계정" 중복: `/accounts` "계정 운영"(고객 계정)과 "운영자 계정"(운영자 계정)이 한 IAM 영역에 공존 →
  "운영자" 수식어로 한국어 구분 충분(운영자의 계정 ↔ 계정을 운영). 섹션명 미변경으로 충돌 최소화.

## Failure Scenarios

- 라벨 텍스트를 단언하는 테스트가 있었다면 RED → 사전 grep 결과 테스트는 testid/role 기반, 해당 문자열
  단언 0건 확인(회귀 없음).
