# Task ID

TASK-PC-FE-051

# Title

**콘솔 결재함 parity slice — approval-service 조회/제출/승인 UI (ADR-016 §D3.1 parity slice).** TASK-ERP-BE-009 가 라이브화한 approval-service(결재 워크플로 상태기계)를 콘솔이 소비: `features/erp-ops` 에 결재 요청 list(scope-aware) + detail(history) + inbox(내 미결) + create(DRAFT) + 4 전이(submit/approve/reject/withdraw, reason-gated + Idempotency-Key) UI. read-model→PC-FE-049 선례대로 backend 를 콘솔에서 demonstrable 하게(제출→승인 루프 가시화). erp 의 첫 실 도메인-로직 서비스(상태기계)를 운영 화면에 노출.

# Status

review

# Owner

frontend-engineer (platform-console console-web; TASK-ERP-BE-009 approval-api 라이브 — 이 task 는 콘솔 UI + proxy)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- test

---

# Dependency Markers

- **선행 (cross-project, erp)**: TASK-ERP-BE-009 (approval-service first increment — `/api/erp/approval/*` 라이브, main 머지됨 `575bc9bf`). 콘솔이 런타임 호출.
- **builds on**: TASK-PC-FE-049 (erp-ops read 카드 + erp proxy 패턴) + TASK-PC-FE-048 (erp 마스터 write parity — Idempotency-Key + reason-gated mutation + 도메인-facing 토큰 erp.write[BE-336] + MasterWriteDialog/generic write 훅 선례) + TASK-BE-336/337 (assume-tenant 토큰 erp.write scope + org_scope — approval write authz 도 동일 도메인 scope 커버).
- **realises**: ADR-MONO-016 §D3.1 parity slice — "approval inbox" parity row(read-only 였던 §D3.1 을 write affordance 로 확장, PC-FE-046/048 선례; write logic 은 approval-service backend, affordance 만 콘솔).
- **decision (user, 2026-06-05)**: 다음 작업 = 콘솔 결재함 parity slice.

# Goal

운영자-admin 이 콘솔에서 결재 요청을 조회(list/detail/inbox)하고, 제출·승인·반려·회수(상태기계 전이)를 수행할 수 있다. approval-service 의 상태기계가 콘솔 운영 화면에서 end-to-end demonstrable — erp 결재 bounded context 가시화.

# Scope

## In Scope

- **proxy routes**(server-only, erp 도메인-facing 토큰; 기존 `app/api/erp/_proxy.ts` 에러매핑 재사용; masterdata write proxy[PC-FE-048] 패턴):
  - `app/api/erp/approval/requests/route.ts` — **GET**(list, query status/role/page/size) + **POST**(create DRAFT, Idempotency-Key).
  - `app/api/erp/approval/requests/[id]/route.ts` — **GET**(detail+history).
  - `app/api/erp/approval/requests/[id]/[transition]/route.ts` (또는 4 개별 route) — **POST** submit/approve/reject/withdraw(Idempotency-Key + reject/withdraw reason 필수, X-Operator-Reason echo). write 핸들러는 POST 만.
  - `app/api/erp/approval/inbox/route.ts` — **GET**(내 미결 SUBMITTED).
- **erp-ops feature 확장**(`features/erp-ops/`):
  - types(zod): `ApprovalRequest`(detail+history) / `ApprovalSummary`(list/inbox) / `ApprovalHistoryEntry` / create·transition input. **NON_NULL absent 파싱**(reason/submittedAt/finalizedAt 미설정 시 absent→optional/null, PC-FE-049/050 선례).
  - api client: `listApprovalRequests`/`getApprovalRequest`/`listApprovalInbox`(read) + `createApprovalRequest`/`submitApproval`/`approveApproval`/`rejectApproval`/`withdrawApproval`(write, server-only hardened call, Idempotency-Key 클라 생성, reason 헤더).
  - keys + hooks: list/detail/inbox query + 5 mutation; 성공 시 invalidate approval list/detail/inbox keys.
  - components: `ApprovalScreen`(또는 ErpOpsScreen 에 "결재함" 섹션) — 요청 list(상태 필터) + inbox(내 미결) + detail drawer(history 타임라인 + 상태 badge) + create dialog + 전이 액션 버튼(submit/approve/reject/withdraw); reject/withdraw 는 reason 필수 dialog(masterdata write reason 패턴). 상태기계에 맞춘 액션 가시성(DRAFT→submit/withdraw; SUBMITTED→approve/reject/withdraw; terminal→액션 없음).
  - **에러 graceful 처리**: 403 `APPROVAL_NOT_AUTHORIZED_APPROVER`(내가 결재자/기안자 아님) / 409 `APPROVAL_STATUS_TRANSITION_INVALID` / 409 `APPROVAL_ALREADY_FINALIZED` / 422 `APPROVAL_ROUTE_INVALID`(자기결재·subject 미해소) / `IDEMPOTENCY_*` → inline 안내(크래시 금지, 콘솔 운영자가 결재자가 아닐 수 있음을 UX 로 수용).
  - ErpOpsScreen nav 에 "결재함" 진입 + index.ts export.
- **tests**: 컴포넌트(상태별 액션 가시성·reason-gated reject/withdraw·history 렌더·에러 inline·inbox) + api client 단위(list/detail/inbox 파싱 absent-field, create/transition payload+Idempotency-Key) + proxy route 단위(GET/POST 메서드 노출·에러매핑·server-only token). console-web **vitest + tsc --noEmit + lint + build** GREEN(MONO-166 gate).

## Out of Scope

- approval-service backend — TASK-ERP-BE-009(소비만).
- approval v2(multi-stage / 대결·위임 / IN_REVIEW / inbox 필터) — backend v2 미구현이라 콘솔도 범위 밖.
- read-model 의 approval-fact 투영 / notification — 별 backend task.
- subject(department/employee) picker 고도화 — 기본 id 입력 + 가능 시 기존 erp masterdata 목록 재사용(degrade 허용).

# Acceptance Criteria

- [ ] **AC-1** 결재함 화면에서 요청 list(status 필터) + inbox(내 미결) 표시; 행 클릭 → detail(상태 badge + history 타임라인, absent reason/finalizedAt 정상 렌더).
- [ ] **AC-2** create dialog → DRAFT 생성(subjectType/subjectId/title/approverId, Idempotency-Key); submit → SUBMITTED.
- [ ] **AC-3** approve/reject/withdraw 전이 — reject/withdraw 는 reason 필수; 성공 시 detail/list/inbox 갱신. 상태기계에 맞는 액션만 노출(terminal=액션 없음).
- [ ] **AC-4** 에러 graceful: 403 not-authorized-approver / 409 invalid-transition·finalized / 422 route-invalid / idempotency → inline 안내(크래시 0). 콘솔 운영자가 결재자 아닐 때 approve 403 을 수용 가능한 메시지로.
- [ ] **AC-5** proxy: requests=GET+POST, detail=GET, transition=POST, inbox=GET(다른 메서드 미노출); 토큰 server-only(도메인-facing erp 토큰)·Idempotency-Key·X-Operator-Reason 전달; 에러매핑 기존 erp proxy 일치.
- [ ] **AC-6** console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). 기존 erp-ops/operators 테스트 회귀 0.

# Related Specs

- consume: `projects/erp-platform/specs/contracts/http/approval-api.md`(ERP-BE-009). ADR-MONO-016 §D3.1 parity slice(이 task 가 write affordance 확장). ADR-MONO-013/015/017(console model). console-integration-contract.md §2.4.8(erp binding — approval read/write surface note 추가).

# Related Contracts

- consume: approval-api.md(8 endpoint). console-web 자체 proxy(same-origin; requests GET+POST / detail GET / transition POST / inbox GET).

# Edge Cases

- 콘솔 운영자가 요청의 approverId 아님: approve/reject → 403 `APPROVAL_NOT_AUTHORIZED_APPROVER`(inline; inbox 는 내가 결재자인 것만 보이므로 inbox 경유 approve 는 정상). detail 에서 타인 요청 액션은 비활성 또는 403 수용.
- 자기결재(approverId==본인): submit 시 422 `APPROVAL_ROUTE_INVALID`(self_approval) → inline.
- subject 미해소(retired/없음): submit 422 route-invalid(subject_unresolved) → inline.
- terminal(APPROVED/REJECTED/WITHDRAWN) 요청: 전이 액션 미노출(클라 사전 차단); 우회 시 409 finalized 수용.
- Idempotency: 전이 버튼 더블클릭 → 같은 키 replay(중복 전이 없음); 클라가 전이당 키 1회 생성.
- absent 필드(reason/submittedAt/finalizedAt): zod optional, UI "—"/숨김.

# Failure Scenarios

- approval-service 불가(503/timeout): 섹션 degrade(기존 erp proxy degrade 패턴) + 재시도 안내.
- reason 누락 reject/withdraw: 400 `VALIDATION_ERROR` — 다이얼로그가 reason 필수 입력으로 사전 차단.
- 잘못된 메서드 proxy 호출: 405/미노출.
- write proxy 토큰 클라 노출: server-only(`getServerEnv`/도메인-facing 토큰) 가드.

# Test Requirements

- 컴포넌트: `ApprovalScreen`/`ApprovalDetail`/`ApprovalActionDialog`(상태별 액션·reason-gated·history·에러 inline·inbox), api client 단위(파싱 absent + payload/Idempotency-Key), proxy route 단위(메서드 노출·에러매핑·server-only).
- console-web `vitest` + `tsc --noEmit` + `lint` + `build` GREEN(MONO-166). erp-ops/operators 회귀 0.
- Local(선택): erp 전체 재배포(approval-service 포함) 후 라이브 — 콘솔에서 create→submit→approve 루프(운영자=approver 시) 확인.

# Definition of Done

- [ ] proxy(requests GET+POST / detail GET / 4 transition POST / inbox GET) + erp-ops feature(types/api/hooks/ApprovalScreen+dialogs) + 상태기계 액션 가시성 + 에러 graceful.
- [ ] console-web vitest + tsc + lint + build GREEN(MONO-166); 회귀 0.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (상태기계 액션 가시성 + reason-gated mutation + Idempotency-Key + 다중 에러코드 graceful + read/write 결합 + history 타임라인). 사용자 "콘솔 결재함 parity slice" 선택. 메타: read-model→PC-FE-049 선례(backend→콘솔 가시화)의 approval 판 — erp 첫 실 도메인-로직(상태기계) 서비스를 운영 화면에 노출. 선행 ERP-BE-009 머지됨(`575bc9bf`). 콘솔 운영자가 결재자 아닐 수 있음을 UX 로 수용(403 graceful). v2(multi-stage/위임/IN_REVIEW) backend 미구현이라 콘솔 범위 밖. [[project_platform_console_adr_013]] [[project_gap_idp_promotion]]
