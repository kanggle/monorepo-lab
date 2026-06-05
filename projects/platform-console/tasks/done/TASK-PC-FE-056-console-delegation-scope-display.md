# Task ID

TASK-PC-FE-056

# Title

**console-web 위임 현황 카드 scope 표시 — per-request delegation slice 완성 (BE-018 의 콘솔 짝).** TASK-ERP-BE-018 이 read-model `delegation_fact_proj` 에 `scope`(GLOBAL|REQUEST) + `scopeRequestId` 를 투영하고 `GET /api/erp/read-model/delegations`(+`/{grantId}`) 응답에 노출했으나(read-model-api.md §Delegation facts), 콘솔 "위임 현황" 카드(PC-FE-055 `DelegationFactCard`)는 아직 표시 안 함. 이 증분이 카드에 **"범위" 컬럼**을 추가 — `GLOBAL`→"전체"(blanket, A 의 모든 결재 대행), `REQUEST`→"특정 건" + `scopeRequestId` 표시. NON_NULL-absent 관용(revoke-only out-of-order 행은 scope ABSENT→"—"; GLOBAL 은 scopeRequestId ABSENT→미표시). **읽기 전용 read-model 카드** — 쓰기 표면(PC-FE-054 DelegationScreen) 무변경. `DelegationFactSchema` 에 `scope`/`scopeRequestId` zod `.optional()` 추가. per-request scoping 3-표면 슬라이스(BE-017 producer → BE-018 read-model → 이 task console) 종결.

# Status

done

# Owner

frontend-engineer (dispatched, model=sonnet — 작은 read-only 컬럼 추가; dispatcher 독립 재검증 + MONO-166 gate)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **consumes**: TASK-ERP-BE-018 (read-model `delegation_fact_proj.scope`/`scopeRequestId` 투영 + `GET /api/erp/read-model/delegations` 응답 노출, main `059ccbd6`; read-model-api.md §Delegation facts `DelegationFact.scope`/`scopeRequestId`).
- **builds on / mirror**: TASK-PC-FE-055 (`DelegationFactCard` + `DelegationFactSchema` + `useDelegationFacts` + GET-only 프록시 route). 이 task = scope 컬럼 가산(필터/페이지네이션/warning/READ-ONLY 패턴 byte-unchanged).
- **realises**: approval-service architecture.md §v2.3 "console scope display is the forward follow-up (TASK-PC-FE-056)" + read-model architecture.md §v1.3 동일 forward 노트.
- **decision (user, 2026-06-06)**: 다음 작업 = PC-FE-056 콘솔 scope 카드(슬라이스 완성).
- ADR-MONO-013 §D3.1 (콘솔 parity discipline). [[project_platform_console_adr_013]]

# Goal

운영자가 "위임 현황" 카드에서 각 위임이 **전체 대행(GLOBAL)** 인지 **특정 결재 건 한정(REQUEST + 대상 requestId)** 인지 한눈에 식별. per-request scoping 의 사용자-가시 표면 완성.

# Scope

## In Scope (console-web only)

- **`api/types.ts` `DelegationFactSchema`**: `scope: z.string().optional()`(GLOBAL|REQUEST|absent — free-string 관용, 미래값/대문자 tolerant) + `scopeRequestId: z.string().optional()` 추가. `.passthrough()` 유지. (List/Detail 응답 스키마는 DelegationFactSchema 재사용이라 자동 반영.)
- **`components/DelegationFactCard.tsx`**: 테이블에 **"범위" 컬럼**(헤더 `<th>범위</th>`; 위치=대행자 뒤 권장) + 행 셀 `data-testid={`delegation-fact-scope-${i}`}`:
  - `scope === 'GLOBAL'` → "전체"(badge 또는 텍스트; 의미="전체 결재 대행").
  - `scope === 'REQUEST'` → "특정 건" + `scopeRequestId`(mono small; 부재 시 "특정 건"만).
  - `scope` absent(revoke-only out-of-order) → "—"(graceful, NON_NULL-absent 관용, NEVER throw).
  - 미지의 scope 값(미래) → 값 그대로 표시(free-string 관용 — status badge 패턴과 동일).
- **tests** `DelegationFactCard.test.tsx`: REQUEST fact → "특정 건"+scopeRequestId 표시; GLOBAL fact → "전체"; scope absent fact → "—"(no crash). 기존 assertion(warning/ABSENT graceful/status badge/filter/pagination/READ-ONLY) 통과.

## Out of Scope

- **scope 필터** (`?scope=`/`?scopeRequestId=` 조회 입력) — 표시만, 필터는 read-model 이 아직 미지원(BE-018 out-of-scope) → 후속. 기존 4 필터(delegatorId/delegateId/status/activeAt) byte-unchanged.
- **쓰기 표면**(PC-FE-054 `DelegationScreen`/`delegation-api`/`delegation-types` = approval-service write) 무변경 — scope 입력은 별 증분(현재 create 는 GLOBAL 기본; REQUEST 생성 UI 는 추후).
- read-model/approval-service/backend 변경.
- 프록시 route 변경(GET passthrough — scope 는 이미 JSON 으로 흐름; 스키마만 typed).

# Acceptance Criteria

- [ ] **AC-1** REQUEST scope fact 행이 "특정 건" + `scopeRequestId` 표시; GLOBAL fact 행이 "전체"; scope absent fact 행이 "—"(crash 없음).
- [ ] **AC-2** `DelegationFactSchema` 가 scope/scopeRequestId 를 `.optional()` 로 파싱(absent 관용; `.nullable()` 아님 — NON_NULL-absent). 미지의 scope 문자열도 파싱 통과(free-string).
- [ ] **AC-3** 기존 "위임 현황" 카드 동작(warning banner / validFrom·validTo·reason·revokedAt graceful / status badge / 4 필터 / 페이지네이션 / READ-ONLY=쓰기 affordance 0) byte-unchanged.
- [ ] **AC-4** READ-ONLY 유지 — scope 표시는 read-model 카드의 read 표면; PC-FE-054 쓰기 화면 무변경.
- [ ] **AC-5** console-web MONO-166 4-gate GREEN: `vitest` + `tsc --noEmit` + `lint` + `build`.

# Related Specs

- `read-model-api.md` §Delegation facts (`DelegationFact.scope`/`scopeRequestId` — BE-018). ADR-MONO-013 §D3.1 parity.

# Related Contracts

- consume: `read-model-api.md` `GET /api/erp/read-model/delegations`(+`/{grantId}`) 응답 `scope`/`scopeRequestId`. serve: 콘솔 UI only(신규 API 0).

# Edge Cases

- scope absent(revoke-only out-of-order 행): "—" graceful.
- GLOBAL: scopeRequestId absent → "전체"만(scopeRequestId 미표시).
- REQUEST + scopeRequestId 존재: "특정 건" + id.
- 미지의 scope 값(producer 미래 확장): 값 그대로 표시(free-string, status badge 와 동일 관용).
- 빈 목록 / warning 대기: 기존 empty/warning 경로 무변경.

# Failure Scenarios

- scope 를 `.nullable()` 로 잘못 선언 → null 값에서 작동하나 absent(생략)와 의미 혼동 → `.optional()` 사용(NON_NULL-absent 일관). zod parse 가 미지의 scope 에서 throw 하지 않도록 free-string.
- 컬럼 추가로 기존 컬럼 인덱스/테스트 깨짐 → data-testid 기반 단언(컬럼 순서 무관) 유지.

# Test Requirements

- unit (`DelegationFactCard.test.tsx`): REQUEST→"특정 건"+scopeRequestId / GLOBAL→"전체" / scope absent→"—" no-crash. 기존 카드 단언 회귀 통과.
- `DelegationFactSchema` parse: scope/scopeRequestId optional(있음/없음/미지값) — 기존 schema 테스트 있으면 가산, 없으면 카드 fixture 로 커버.
- MONO-166 4-gate(vitest/tsc/lint/build) GREEN.

# Definition of Done

- [ ] `DelegationFactSchema` scope/scopeRequestId optional.
- [ ] `DelegationFactCard` "범위" 컬럼(GLOBAL/REQUEST+id/absent "—"/미지값 passthrough).
- [ ] 기존 카드 동작 byte-unchanged + READ-ONLY.
- [ ] test 가산 + 기존 회귀 통과.
- [ ] MONO-166 4-gate GREEN.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Sonnet (작은 read-only 컬럼 + zod 필드 가산 — PC-FE-055 패턴 답습; dispatcher 독립 재검증 + MONO-166 gate). 사용자 "PC-FE-056 콘솔 scope 카드" 선택. 메타: ① **per-request scoping 3-표면 슬라이스 종결**(BE-017 producer → BE-018 read-model → PC-FE-056 console). ② read-only read-model 카드 — 쓰기(PC-FE-054)와 분리 유지(scope create UI 는 별 증분). ③ NON_NULL-absent 관용(scope absent=revoke-only out-of-order→"—", scopeRequestId absent=GLOBAL→미표시) + free-string scope(미래값 tolerant) — DelegationFactCard 의 기존 ABSENT/free-string status 패턴 답습. [[project_platform_console_adr_013]] [[project_monorepo_template_strategy]]
