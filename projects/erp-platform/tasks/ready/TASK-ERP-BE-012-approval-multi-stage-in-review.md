# Task ID

TASK-ERP-BE-012

# Title

**approval-service v2.0 — 다단계 결재선(1~N) + `IN_REVIEW` 상태 (ADR-016 §D3 approval v2 forward-declaration 집행, 2번째 증분).** TASK-ERP-BE-009 가 라이브화한 단일단계 상태기계(`DRAFT→SUBMITTED→APPROVED|REJECTED|WITHDRAWN`)를 **순서있는 1~N 단계 결재선 + `IN_REVIEW` 중간상태**로 확장: 비최종 단계 승인 → `IN_REVIEW`(다음 단계로 전진), 최종 단계 승인 → `APPROVED`; per-stage 결재자 인가(현 단계 결재자만); 어느 단계 반려 → `REJECTED`, 기안자 회수 → `WITHDRAWN`. **하위호환**: 기존 단일-approver 요청 = 1단계 결재선, 콘솔(PC-FE-051) create `approverId` 그대로 수용. **대결/위임(대결자·`erp.approval.delegated`)은 v2.1로 분리**(작동하는 다단계 base 위에 독립 권한위양 모델로 후속).

# Status

ready

# Owner

backend-engineer (erp-platform approval-service; 분석=Opus 4.8 / 구현 권장=Opus — 복합 상태기계 확장 + 하위호환 마이그레이션 + per-stage authz + 이벤트 진화)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- test
- adr

---

# Dependency Markers

- **선행 (같은 서비스)**: TASK-ERP-BE-009 (approval-service first increment — 단일단계 상태기계 라이브, main 머지됨 `575bc9bf`). 이 task 가 그 상태기계를 확장.
- **builds on**: TASK-ERP-BE-010 (read-model approval-fact 투영 — `erp.approval.*` 소비) + TASK-ERP-BE-011 (notification-service — `erp.approval.*` 소비). **두 소비자는 이 증분에서 무변경**(이벤트 가산 필드만, unknown-field tolerant; `approved`=최종단계만 발화로 terminal-once 계약 보존).
- **realises**: ADR-MONO-016 §D3 approval-service v2 forward-declaration 의 **2번째 증분**(read-model-service / approval-service first-increment §D3 amendment 선례). 신규 ADR 불요 — 신규/확장 architecture.md 가 HARDSTOP-09 gate.
- **decision (user, 2026-06-05)**: 다음 작업 = approval v2(#1). 증분 경계 = 다단계+IN_REVIEW 이번, 대결/위임 v2.1 분리.

# Goal

운영자가 1~N 단계 결재선을 가진 결재 요청을 생성·상신하고, 각 단계 결재자가 순서대로 승인하여(비최종 단계 → `IN_REVIEW` 전진, 최종 → `APPROVED`) 다단계 결재 워크플로를 end-to-end 수행할 수 있다. 어느 단계든 반려 시 `REJECTED`, 기안자 회수 시 `WITHDRAWN`. erp 결재 bounded context 의 다단계 라우팅 + `IN_REVIEW` 가 실 도메인 로직으로 실현.

# Scope

## In Scope

- **domain (pure)**:
  - `ApprovalStatus` 에 **`IN_REVIEW`**(non-terminal) 추가; `isFinalized()` 불변(APPROVED/REJECTED/WITHDRAWN 만 terminal).
  - `ApprovalRoute` 를 **순서있는 1~N 단계**로(`List<Approver> stages`, stage_index 순서); `singleStage(submitter, approverId)` 하위호환 유지 + `multiStage(submitter, List<approverId>)` 추가. route-validity: 빈 단계 0개·blank approver → `APPROVAL_ROUTE_INVALID`; submitter ∈ 어느 단계 → self-approval `APPROVAL_ROUTE_INVALID`; **중복 결재자(동일 id 2단계 이상)** → `APPROVAL_ROUTE_INVALID`(`cause=duplicate_stage_approver`, SoD 정신).
  - `ApprovalStateMachine.next(current, command, routeContext)` 확장 — approve(SUBMITTED|IN_REVIEW): **최종 단계면 APPROVED, 아니면 IN_REVIEW**; reject(SUBMITTED|IN_REVIEW)→REJECTED; withdraw(DRAFT|SUBMITTED|IN_REVIEW)→WITHDRAWN; finalized-guard 최우선·legal-edge guard 그대로(T4 — 직접 status UPDATE 금지).
  - `ApprovalRequest` aggregate: `currentStageIndex`/`totalStages` 추가; approve 시 per-stage 결재자 인가(**현 단계 결재자만** = `stages[currentStage].approverId == actor`, else `APPROVAL_NOT_AUTHORIZED_APPROVER`) + 비최종 승인 시 `currentStageIndex++` & status→IN_REVIEW, 최종 승인 시 status→APPROVED+finalizedAt. reject/withdraw 불변(현 단계 결재자/기안자).
- **persistence + migration (하위호환)**: 신규 `approval_route_stage`(id, tenant_id, request_id, stage_index, approver_id, created_at); `approval_request` 에 `current_stage_index INT NOT NULL DEFAULT 0` + `total_stages INT NOT NULL DEFAULT 1`. **Flyway V__ 마이그레이션이 기존 모든 요청을 1단계로 backfill**(approval_route_stage 1행: stage_index=0, approver_id=기존 approver_id; total_stages=1). 기존 `approver_id` 컬럼=현 단계(=stage 0) 결재자로 denormalize 유지(읽기 하위호환).
- **application**: create command 가 1~N approver 수용; submit 시 마스터 참조무결성(E1) 불변; 각 전이 = 상태변경 + audit row(단계 포함) + (해당 시) outbox 이벤트를 **단일 @Transactional**(A7). **비최종 단계 승인 = audit row 만, outbox 무발화**(다음 단계 결재자는 inbox 가 노출; 단계전진 이벤트 fan-out 은 v2.1). inbox = **현 단계가 본인인** pending(SUBMITTED|IN_REVIEW) 요청.
- **events (가산·하위호환)**: payload 에 `currentStage`(0-based)/`totalStages` 가산(NON_NULL; 기존 소비자 unknown-field ignore). **`approved` 는 최종 단계 승인(→APPROVED terminal)에만 발화** — terminal-once 계약 보존(소비자 무변경). submitted(상신 시, approverId=stage 0)/rejected/withdrawn 불변+stage 필드. 신규 토픽 0(`erp.approval.delegated`=v2.1).
- **contracts (additive, spec-first)**: approval-api.md(create `approverIds:[...]`∨legacy `approverId`; detail/summary 에 `stages`/`currentStage`/`totalStages`; status enum +`IN_REVIEW`; approve 전이표에 IN_REVIEW 예측) + erp-approval-events.md(stage 필드 가산, approved=최종-only, 비최종-전진 무발화 명시) + architecture.md(상태기계 §·route §·authz §·events §·endpoints §·Out-of-Scope §, §D3 v2.0 amendment blockquote) + ADR-MONO-016 §D3 amendment blockquote(ERP-BE-012). **error-handling.md**: 신규 코드 0(기존 approval 코드 재사용; `APPROVAL_ROUTE_INVALID` cause 에 `duplicate_stage_approver` 가산은 details 필드라 등록 불요).
- **tests**: 상태기계 매트릭스(1단계=기존 보존 + N단계: SUBMITTED→approve→IN_REVIEW→approve→APPROVED, 중간/최종 경계, 각 단계 reject→REJECTED, withdraw→WITHDRAWN, finalized-guard) + route-validity(빈·blank·self·duplicate) + aggregate per-stage authz(현 단계 결재자만, 이전/이후 단계 결재자→`NOT_AUTHORIZED_APPROVER`) + application(멱등 replay·authz·masterdata) + **Testcontainers IT**(create N단계→submit→stage1 approve(IN_REVIEW, 무이벤트, 1 audit)→stage2 approve(APPROVED, `approved.v1` 1건, audit) + 1단계 하위호환 회귀 + reject/withdraw/finalized/멱등/cross-tenant). `:check`(Docker-free) GREEN + CI "Integration (erp-platform, Testcontainers)" GREEN(권위 게이트).

## Out of Scope

- **대결/위임 (대결자·delegation·`erp.approval.delegated`)** — v2.1(다음 증분; 독립 권한위양 모델). 이 증분은 고정 결재선만.
- 단계전진 이벤트 fan-out(다음 단계 결재자 notification) — v2.1(notification 증분과 함께). 이 증분은 inbox 노출로 충분.
- 콘솔 다단계 UI(create N단계 입력·단계 진행 타임라인) — 별 PC-FE 후속(backend 라이브 후).
- rich inbox 필터(단계 facet·위임 항목) — v2.
- read-model 의 단계-수준 투영 — read-model 은 latest fact(status)만; 단계 상세는 approval-service REST 권위(E5). 이번 증분 read-model 무변경.

# Acceptance Criteria

- [ ] **AC-1** N단계(예: 2) 결재선 create→submit→stage1 결재자 approve → `IN_REVIEW`(currentStage 전진, 미finalized) → stage2 결재자 approve → `APPROVED`. 1단계 요청은 기존대로 submit→approve→`APPROVED`(회귀 0).
- [ ] **AC-2** per-stage authz: 현 단계 결재자만 approve/reject 가능; 다른 단계(이전/이후) 결재자 → 403 `APPROVAL_NOT_AUTHORIZED_APPROVER`. 어느 단계 reject(사유)→`REJECTED`; 기안자 withdraw(사유)→`WITHDRAWN`.
- [ ] **AC-3** route-validity: 빈 결재선/ blank approver/ self-approval(submitter∈단계)/ 중복 결재자 → 422 `APPROVAL_ROUTE_INVALID`(cause 구분). create 하위호환: `approverId`(legacy 단일) ∨ `approverIds`(N단계) 정확히 하나; 콘솔 PC-FE-051 `approverId` 동작.
- [ ] **AC-4** 이벤트: `approved.v1` 은 **최종 단계 승인에만** 1건; 비최종 단계 승인=outbox 무발화(audit row 만); payload `currentStage`/`totalStages` 가산. notification/read-model 소비자 **무변경 동작**(submitted+terminal-once 보존). 멱등 replay=중복 전이·중복 이벤트·중복 audit 0.
- [ ] **AC-5** 마이그레이션: Flyway 가 기존 요청을 1단계로 backfill(approval_route_stage 1행 + total_stages=1 + current_stage_index=0); 기존 요청 전이 동작 불변. 모든 전이 = 상태+audit(+해당 시 outbox) 단일 Tx(A7), 직접 status UPDATE 0(T4).
- [ ] **AC-6** `:approval-service:check`(Docker-free) GREEN + CI "Integration (erp-platform, Testcontainers)" GREEN(실 MySQL+Kafka+WireMock: 다단계 happy + 1단계 회귀 + reject/withdraw/finalized/멱등/cross-tenant). spec-first: 3 계약 + ADR amendment 머지 후 impl.

# Related Specs

- extend: `approval-service/architecture.md`(§State Machine + §Route + §Approver authorization + §Outbox events + §REST endpoints + §Out-of-Scope + §D3 v2.0 amendment). ADR-MONO-016 §D3(approval forward-declaration — 2번째 증분 amendment). `rules/domains/erp.md`(E3 다단계 상태기계 `DRAFT→SUBMITTED→(IN_REVIEW→)APPROVED` Ubiquitous Language 실현 — 결재선 1~N단계).

# Related Contracts

- update(additive): approval-api.md(create approverIds/ legacy approverId / detail stages·currentStage·totalStages / status +IN_REVIEW) + erp-approval-events.md(payload stage 필드 / approved=최종-only / 비최종 무발화). 소비자(read-model-subscriptions.md / notification-subscriptions.md) 무변경(가산 필드 tolerant).

# Edge Cases

- 1단계 결재선(하위호환): SUBMITTED→approve→APPROVED(IN_REVIEW 미경유); 기존 모든 요청 = 1단계.
- 다단계 중 이전 단계 결재자가 다시 approve 시도: 현 단계 아님 → `APPROVAL_NOT_AUTHORIZED_APPROVER`.
- 다단계 중 미래 단계 결재자가 미리 approve: 현 단계 아님 → `APPROVAL_NOT_AUTHORIZED_APPROVER`(순서 강제).
- IN_REVIEW 에서 reject(현 단계 결재자, 사유)→REJECTED; withdraw(기안자, 사유)→WITHDRAWN; 둘 다 terminal-once.
- 중복 결재자(같은 사람 2단계): create/submit 시 `APPROVAL_ROUTE_INVALID` duplicate_stage_approver(SoD).
- 멱등: 같은 키 stage approve 더블클릭 → 첫 결과 replay, currentStage 재전진 0, 이벤트/audit 0.
- finalized(APPROVED/REJECTED/WITHDRAWN)에서 어느 전이 → `APPROVAL_ALREADY_FINALIZED`.
- 이벤트 소비자: 비최종 단계 승인은 버스 무발화라 read-model 은 SUBMITTED→(최종)APPROVED 만 관측(중간 IN_REVIEW 미투영) — eventually-consistent latest-fact 경계 내 honest(상세=approval REST 권위).

# Failure Scenarios

- masterdata 불가(submit 시): 기존대로 submit abort, DRAFT 유지, 422 `APPROVAL_ROUTE_INVALID`(Category B).
- 동시 같은 단계 approve(낙관락): 하나 win, 다른 409 `CONCURRENT_MODIFICATION`(@Version).
- audit append 실패: 전이 전체 실패(audit-fail-closed A10), 500.
- outbox publish 실패: row PENDING 유지, 다음 tick 재시도(멱등 무영향).
- 마이그레이션 backfill 누락 시 기존 요청 currentStage 불일치: V__ 가 1행 보장 + total_stages=1; 검증=IT 1단계 회귀.

# Test Requirements

- domain: `ApprovalStateMachineTest`(1단계 매트릭스 보존 + N단계 approve→IN_REVIEW→APPROVED 경계 + reject/withdraw 각 단계 + finalized 셀), `ApprovalRouteTest`(single/multi 생성·빈·blank·self·duplicate), `ApprovalRequestTest`(per-stage authz: 현/이전/이후 단계 결재자, currentStage 전진, 멱등 형상).
- application: `ApprovalApplicationServiceTest`(N단계 happy + 멱등 replay + authz + masterdata; 이벤트 발화 지점=submitted/최종 approved/reject/withdraw, 비최종 무발화 단언).
- IT(Testcontainers): 다단계 happy(2단계: submitted→stage1 approve[IN_REVIEW, no event, 1 audit]→stage2 approve[APPROVED, approved.v1 1건, audit]) + **1단계 하위호환 회귀** + reject(IN_REVIEW)→REJECTED + withdraw→WITHDRAWN + finalized 재처리 + 멱등(같은 키 approve 2회=1 transition/1 event/1 audit) + cross-tenant 403 + 마이그레이션 backfill(기존 1단계 행 동작).
- `:approval-service:check`(Docker-free) GREEN; CI "Integration (erp-platform, Testcontainers)" GREEN = 권위 기능 게이트. `--rerun-tasks` 1회로 @InjectMocks ctor 변경 stale cache 회피(메모리 선례).

# Definition of Done

- [ ] domain(IN_REVIEW + multi-stage route + state machine + aggregate per-stage authz) + persistence/Flyway backfill + application(이벤트 발화 지점) + contracts additive + ADR amendment.
- [ ] `:approval-service:check` + CI erp Integration(Testcontainers) GREEN; 1단계 회귀 0; 소비자(read-model/notification) 무변경 동작.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (복합 상태기계 확장: IN_REVIEW + 다단계 전진 로직 + per-stage 순서강제 authz + 하위호환 Flyway backfill + 이벤트 발화 지점 진화[approved=최종-only, 비최종 무발화로 terminal-once 보존] + 3 계약 additive + 1단계 회귀 무손상). 사용자 "approval v2(#1)" 선택. 증분 경계 결정: **다단계+IN_REVIEW=이번(상태기계 spine)**, **대결/위임=v2.1 분리**(IN_REVIEW 는 다단계에서만 존재해 한 묶음이나, 위임은 부재 결재자 권한위양이라는 독립 모델 — 작동하는 다단계 base 위에 얹는 게 정석; 묶으면 surface 3배·리뷰 리스크). read-model→PC-FE-049 / approval first-increment §D3 선례의 2번째 증분. [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]]
