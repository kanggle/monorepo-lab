# Task ID

TASK-PC-FE-153

# Title

console-web erp-ops `approval-api.ts`(435줄) behavior-preserving 분할 — 연산 그룹별 sub-module(shared call core + reads + mutations)로 쪼개고 원본 `approval-api.ts`는 named re-export barrel 로 유지(PC-FE-098 동형, 공개 import 경로 안정)

# Status

review

# Owner

frontend (Opus 4.8 분석·구현 — behavior-preserving 모듈 분할; contract/spec/backend 무변경)

# Task Tags

- code
- refactor
- test

---

# Dependency Markers

- **builds on**: TASK-PC-FE-051(erp approval-service workflow 클라이언트 — ADR-MONO-016 § D3.1 parity slice). 본 task는 그 위에서 **호출 시그니처·wire·로직 동일**, 모듈 경계만 재편한다(PC-FE-098 fat-api barrel 분할과 동형).
- **note (현 구조)**: `approval-api.ts`(435줄, server-only)는 한 파일에 (a) `CallOptions`/`parseApprovalError`/`callApproval`(단일 하드닝 call site) + query-string 헬퍼(`pageParams`/`listQs`/`inboxQs`) + `parseApprovalRequest`(shared core), (b) reads(`listApprovalRequests`/`getApprovalRequest`/`listApprovalInbox`), (c) writes(`createApprovalRequest` + 4 transitions)를 모두 보유.
- **note (소스텍스트 가드)**: `tests/unit/approval-api.test.ts`는 `@/features/erp-ops/api/approval-api` named import 만 사용(`readFileSync`/`resolve(` on-disk 소스 가드 **없음**). 따라서 코어 이동 시 가드 경로 수정 불필요 — barrel named re-export 만 유지하면 vacuous pass 위험 없음.

# Goal

`approval-api.ts`를 연산 그룹별 sub-module 로 behavior-preserving 분할하고, 원본 파일은 named re-export barrel 로 유지한다. 공개 import 경로(`@/features/erp-ops/api/approval-api` — `erp-state.ts` + `/api/erp/approval/**` route handler 4곳 소비)는 안정. 호출 시그니처·wire body·헤더(Idempotency-Key/X-Operator-Reason)·에러 taxonomy·로그 이벤트명은 byte-identical.

# Scope

## In Scope

- **신규 `src/features/erp-ops/api/approval-call.ts`** — shared core: `CallOptions`(export), `parseApprovalError`, `callApproval`(export), `listQs`/`inboxQs`(export; 내부 `pageParams`), `parseApprovalRequest`(export). credential/timeout/에러 매핑/로그 전부 verbatim.
- **신규 `src/features/erp-ops/api/approval-reads.ts`** — `listApprovalRequests`/`getApprovalRequest`/`listApprovalInbox`(`approval-call` 소비).
- **신규 `src/features/erp-ops/api/approval-mutations.ts`** — `createApprovalRequest`(multi-stage vs legacy approver 분기) + `submitApproval`/`approveApproval`/`rejectApproval`/`withdrawApproval`(`approval-call` 소비).
- **`src/features/erp-ops/api/approval-api.ts`** — barrel: reads + mutations named re-export. 공개 함수 시그니처 8종 전부 동일 경로로 노출.

## Out of Scope

- approval 호출 wire/헤더/에러 매핑/로그 이벤트명/타임아웃 정책 변경.
- approval-types.ts / proxy route handler / hook(`use-erp-ops.ts`) 변경.
- 다른 erp-ops api(`erp-api.ts` 등).

# Acceptance Criteria

- [x] reads 3종·writes 5종이 동일 경로(`@/features/erp-ops/api/approval-api`)로 노출되어 `erp-state.ts` + route handler 4곳 import 무변경.
- [x] credential(domain-facing IAM OIDC token, no X-Tenant-Id)·Idempotency-Key(create+4 transitions)·X-Operator-Reason(reject/withdraw/reasoned-approve)·에러 taxonomy(401/403/503/timeout)·NON_NULL 파싱 모두 불변.
- [x] `createApprovalRequest` 의 approverIds(v2.0) vs approverId(legacy) 분기 wire 동등.
- [x] `npx tsc --noEmit` clean / `npx next lint` clean / `npx vitest run approval` green(approval-api.test.ts 포함, 무회귀). scope = console-web only.

# Related Specs

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.8 / § 2.5(resilience) — read-only 소비, 변경 없음.

# Related Contracts

- 변경 없음. producer `approval-api.md`(base `/api/erp/approval`) UNCHANGED, 동일 호출·동일 envelope.

# Target Service

- `platform-console` / `apps/console-web` — `src/features/erp-ops/api/{approval-api.ts(barrel), approval-call.ts(신규), approval-reads.ts(신규), approval-mutations.ts(신규)}`. behavior-preserving fat-api barrel 분할.

# Architecture

- 서버 전용 fat-api 모듈의 연산-그룹별 sub-module + barrel 분할 패턴(PC-FE-098 동형). 단일 하드닝 call site(`callApproval`)와 query/parse 헬퍼를 `approval-call` 코어로 모으고, read/write 연산을 각 sub-module 로 분리한 뒤 원본 파일을 named re-export barrel 로 유지 → 공개 import 경로 불변, server-only posture(`getServerEnv()` throws off-server) 보존.

# Edge Cases

- `callApproval`/`parseApprovalRequest`/`listQs`/`inboxQs` 는 export 로 승격(코어 간 소비) — 단 barrel 은 read/write 함수만 re-export 해 공개 표면 동일 유지(헬퍼는 누출하지 않음).
- `approveApproval` 의 reason-optional → operatorReason 헤더 조건부(`...(reason ? {operatorReason} : {})`) verbatim.
- create 의 approver payload 분기(`approverIds.length > 0 ? {approverIds} : {approverId}`) 동등.

# Failure Scenarios

- barrel 에서 함수 하나라도 누락 re-export 시 route handler / `erp-state.ts` import RED → 8종 전부 re-export, tsc 로 검증.
- 코어 이동 중 헤더/에러 분기 변형 시 wire/taxonomy 회귀 → `callApproval` verbatim 이동, vitest(approval-api 263 케이스: credential/reads/transitions/error)로 가드.
- soure-text 가드 없음 확인 완료(readFileSync/resolve 미존재) → vacuous pass 위험 없음, named import 테스트가 실제 동작 검증.
- lint(no-unused-vars: import 분산) / tsc RED → push 전 3종 게이트 필수.

# Definition of Done

- [x] `approval-call.ts`/`approval-reads.ts`/`approval-mutations.ts` 추출, `approval-api.ts` barrel(named re-export)로 전환
- [x] 호출 시그니처·wire·헤더·에러 taxonomy·로그 이벤트명 behavior-preserving
- [x] vitest(approval) + tsc + lint green, 무회귀; scope = console-web only
- [x] 공개 import 경로(`@/features/erp-ops/api/approval-api` 8종 함수) 불변
- [x] Acceptance Criteria 충족
- [x] Ready for review
