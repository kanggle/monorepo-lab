# Task ID

TASK-ERP-BE-010

# Title

**read-model approval-fact 투영 — approval→read-model 이벤트 루프 완결 (통합조회 v2 first increment).** read-model-service 가 `erp.approval.{submitted,approved,rejected,withdrawn}.v1`(TASK-ERP-BE-009 가 발행하나 **소비자 0**)을 구독→`approval_fact_proj`(요청당 최신 fact) 투영 + read-only `GET /api/erp/read-model/approvals` list/detail 제공. masterdata→read-model 루프 미러; ADR-016 §D3 read-model amendment 의 "full integrated view(approval facts)=v2" forward-decl 의 approval-facts slice 집행. E5 유지(latest fact 만; full history 권위=approval-service REST).

# Status

done

# Owner

backend-engineer (erp read-model-service; ADR-016 §D3 amendment + read-model architecture/contract amendment 이 spec PR 동반 — impl)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- test

---

# Dependency Markers

- **realises**: ADR-MONO-016 §D3 amendment(2026-06-05, TASK-ERP-BE-010) — read-model 통합조회 v2 의 approval-facts slice first increment. `erp-approval-events.md` § Consumer rules(forward — for the v2 consumers)의 read-model 측 집행. erp.md E5(read-only 투영, 도메인 로직 미보유) + E6(read 인가 + org_scope) + T8(dedupe) + ADR-MONO-005 Category C(retry+DLT).
- **consumes (producer)**: TASK-ERP-BE-009 (`erp.approval.*.v1` outbox 이벤트 — 이미 발행 중, main `575bc9bf`). approval-service 불변(read-model 이 소비만).
- **builds on**: TASK-ERP-BE-007 (read-model-service 4 masterdata consumer + projection + `processed_events` dedupe + read API — 같은 패턴 재사용) + TASK-ERP-BE-008 (org_scope subtree read-filter — approval-fact list 에 적용).
- **decision (user, 2026-06-05)**: 다음 작업 = read-model approval-fact 투영(이벤트 루프 완결).

# Goal

approval-service 가 발행만 하던 결재 이벤트를 read-model 이 소비→투영하여 결재 상태를 통합 read store 에서 조회 가능케 한다(producer→consumer 루프 완결). erp 통합조회가 employee org-view 를 넘어 approval facts 로 확장(v2 first slice). E5 read-only — full history 는 approval-service 가 권위.

# Scope

## In Scope

- **4 approval consumer** `erp.approval.{submitted,approved,rejected,withdrawn}.v1` — 기존 masterdata consumer(@KafkaListener + @RetryableTopic 3-retry+DLT + manual ACK) 미러; consumer group `erp-read-model-v1` 합류; partition key=`aggregateId`(approvalRequestId). `processed_events` dedupe(T8). 잘못된 envelope(null eventId/payload)→즉시 DLT.
- **`approval_fact_proj` projection**(Flyway 신규 테이블 또는 read_model_db 추가): PK `approval_request_id`, `status`(SUBMITTED|APPROVED|REJECTED|WITHDRAWN), `subject_type`, `subject_id`, `approver_id`, `submitter_id`, `submitted_at`(nullable), `finalized_at`(nullable), `last_reason`(nullable), `last_event_at`. **latest-state upsert**(요청당 1행, full history 미보유). **terminal-once**(terminal 후 SUBMITTED 로 revert 금지; last-terminal-wins). out-of-order(terminal without submitted) 허용(submitted_at ABSENT, no fabrication).
- **read API** `GET /api/erp/read-model/approvals`(list, scope-aware, query status/subjectType/subjectId/approverId/submitterId/page/size) + `GET /api/erp/read-model/approvals/{approvalRequestId}`(detail). 응답=`ApprovalFact`(subject 는 read-time `employee_proj`/`department_proj` 해소 ref, 미해소→null + `meta.unresolved`; NON_NULL absent submittedAt/finalizedAt/lastReason). **org_scope subtree 필터**(ERP-BE-008): subject department(DEPARTMENT=자신 / EMPLOYEE=employee_proj.department)가 org_scope subtree 내인 fact 만; `["*"]`/미설정=전체 net-zero. out-of-scope detail→404 `MASTERDATA_NOT_FOUND`(존재 누설 방지, org-view 규칙 미러).
- **인가**: 기존 read-model `ReadAuthorizationGate`(entitlement-trust dual-accept + `erp.read`∨operator∨entitled) 재사용. read-only(E5) — mutating 0, Idempotency-Key 무관.
- **E5 규율**: no business logic(상태기계·authz·멱등=approval-service 소유), no re-emission, no write-back, no publish. latest fact 만(history 권위=approval-service REST). subject enrichment=read-time only.
- **tests**: consumer unit(4 이벤트 upsert + terminal-once + out-of-order + dedupe + invalid→DLT) + projection/query unit(latest-state + org_scope subtree 필터[DEPARTMENT/EMPLOYEE subject] + 미해소 subject null) + REST slice(list 필터·detail 404·scope) + **IT(@Tag integration, Testcontainers MySQL + Kafka)**: 실 approval 이벤트 produce→consume→project→read end-to-end(submitted→approved 순서, terminal-once, scope 내 list/scope 밖 404, net-zero). H2 forbidden.

## Out of Scope

- approval-service 변경(read-model 이 소비만; producer 불변).
- business-partner / permission facts 투영 — read-model v2 잔여.
- notification-service 소비 — approval-service v2 consumer.
- multi-stage/위임 approval facts(IN_REVIEW, delegate) — approval-service v2 미발행이라 범위 밖.
- full approval history 투영 — E5(history 권위=approval-service); read-model 은 latest fact 만.
- 콘솔 approval-fact 카드 — 별 PC-FE(콘솔은 이미 PC-FE-051 로 approval-service 직접 소비; read-model 경유 통합뷰는 별건).

# Acceptance Criteria

- [ ] **AC-1** 4 approval consumer 가 `erp.approval.*.v1` 소비→`approval_fact_proj` latest-state upsert(submitted=SUBMITTED+submittedAt; approved/rejected/withdrawn=terminal+finalizedAt+lastReason). dedupe(T8) — 중복 eventId skip.
- [ ] **AC-2** terminal-once: terminal fact 후 동일 approvalRequestId 의 비중복 transition 이 와도 terminal 유지(SUBMITTED revert 0). out-of-order(terminal before submitted)→행 upsert, submittedAt ABSENT(no fabrication).
- [ ] **AC-3** `GET /approvals` list — status/subject/role 필터; **org_scope subtree 필터**(subject department 기준; `["*"]`/미설정=전체); scope-aware. `GET /approvals/{id}` detail — 미존재/out-of-scope→404 `MASTERDATA_NOT_FOUND`.
- [ ] **AC-4** subject ref read-time 해소(`employee_proj`/`department_proj`); 미해소→`subject:null` + `meta.unresolved:["subject"]`(no fabrication, E5). NON_NULL absent(submittedAt/finalizedAt/lastReason).
- [ ] **AC-5** E5: read-model 이 approval 이벤트 외 어떤 publish/write-back/재발행 0(grep 게이트). latest fact 만(history 미보유).
- [ ] **AC-6** `./gradlew :apps:read-model-service:check` GREEN. IT(@Tag integration) CI Linux(Testcontainers MySQL+Kafka). H2 미사용. net-zero 회귀(기존 employee org-view consumer/read GREEN).

# Related Specs

- read-model-service `architecture.md` amendment(이 spec PR) + `read-model-subscriptions.md`(approval 4 topic 추가) + `read-model-api.md`(approvals endpoint). ADR-MONO-016 §D3 amendment(2026-06-05). consume: `erp-approval-events.md`(payload). erp.md E5/E6 + T8 + ADR-MONO-005 Category C.

# Related Contracts

- consume: `erp-approval-events.md`(4 topic, envelope). serve: `read-model-api.md`(approvals list/detail). subject enrichment: masterdata read(read-time, employee/department proj).

# Edge Cases

- terminal before submitted(replay-from-middle): 행 upsert, submittedAt ABSENT.
- 중복 terminal(at-least-once): dedupe(eventId) skip; idempotent.
- 두 terminal 다른 종류(out-of-contract): terminal-once — 첫 terminal 유지 또는 last-terminal-wins(architecture 명시), revert 금지.
- subject 미해소(employee/department proj 미투영): subject null + meta.unresolved.
- org_scope EMPLOYEE subject: employee_proj.department 경유 subtree 판정; employee 미투영→보수적 제외(scope 판정 불가).
- DRAFT: 이벤트 없음→fact 없음(정상).

# Failure Scenarios

- invalid envelope(null eventId/payload)→즉시 DLT(no retry).
- consume 처리 실패→3-retry+DLT(Category C); projection 부분기록 0(tx).
- org_scope 필터 누락→scope 밖 누설(회귀). list/detail 양쪽 단언 게이트.
- subject 해소 위해 master write-back 유혹→E5 위반(read-only); read-time 해소만.

# Test Requirements

- consumer unit(4 upsert + terminal-once + out-of-order + dedupe + invalid DLT) + query unit(latest-state + org_scope DEPARTMENT/EMPLOYEE + 미해소 null) + REST slice(필터·404·scope) + **IT**(Testcontainers MySQL+Kafka: produce→consume→project→read, terminal-once, scope list/404, net-zero). H2 forbidden.
- `./gradlew :apps:read-model-service:check` GREEN. IT CI Linux. E5 publish/write-back grep 0.

# Definition of Done

- [ ] 4 approval consumer + `approval_fact_proj` + approvals read API(list/detail) + org_scope 필터 + subject read-time 해소.
- [ ] E5 유지(no publish/write-back/history); dedupe+DLT; net-zero 회귀(employee org-view 불변).
- [ ] read-model `:check` GREEN; IT CI Linux GREEN.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (event-driven projection — 4 consumer + terminal-once 상태 머지 + org_scope subtree 필터[subject→department 해소] + E5 규율 + Testcontainers Kafka IT). 사용자 "read-model approval-fact 투영" 선택. 메타: approval-service(ERP-BE-009)가 발행만 하던 이벤트의 소비자 고리 완결(masterdata→read-model[ERP-BE-007] 미러); forward-decl 집행이라 새 ADR 불요(architecture amendment=HARDSTOP-09 충족). E5 — latest fact 만, history 권위=approval-service. v2(business-partner/permission/notification/multi-stage) deferred. [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]] [[project_platform_console_adr_013]]
