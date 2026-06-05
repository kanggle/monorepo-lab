# Task ID

TASK-ERP-BE-015

# Title

**delegation-fact read model + revoke 이벤트 (2-leg) — "누가 누구를 대신할 수 있는가" 정확한 통합조회.** read-model-service 가 `erp.approval.delegated.v1`(grant) + 신규 `erp.approval.delegation.revoked.v1`(revoke)를 구독→`delegation_fact_proj`(ACTIVE/REVOKED) 투영 + read-only 조회 REST. ERP-BE-013 의 revoke 가 audit-only(이벤트 무발행)라 read model 이 취소를 반영 못 하던 갭을 **producer leg(approval-service revoke 이벤트 발행) + consumer leg(read-model 투영)** 1 atomic PR 로 해소. ERP-BE-010(approval-fact projection) 청사진 답습. terminal consumer(no outbox/no re-emit, E5). approval→read-model delegation 루프 완결.

# Status

done

# Owner

backend-engineer (Opus dispatch — 2-service atomic: approval-service revoke producer + read-model delegation consumer) + dispatcher 독립 재검증

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- test

---

# Dependency Markers

- **realises**: read-model-service architecture.md (현재 delegation 미투영) + read-model-subscriptions.md L72-74("`erp.approval.delegated.v1` is NOT consumed") + ERP-BE-014 가 남긴 후속("read-model delegation projection = ERP-BE-015 후보"). 신규 ADR 불요(ERP-BE-010 forward-decl 패턴 + amendment-blockquote; HARDSTOP-09 = 갱신 architecture.md 가 충족).
- **consumes/extends (producer)**: TASK-ERP-BE-013 `DelegationGrant`(grant create→`erp.approval.delegated.v1` 이미 발행; revoke=audit-only 무발행). **이 task 가 producer leg 추가** = revoke 시 `erp.approval.delegation.revoked.v1` 발행(기존 grant create 경로·4 transition 토픽 불변).
- **builds on**: TASK-ERP-BE-010 (approval-fact projection 청사진: consumer/projection/apply·query usecase/controller/repo/JPA/Flyway+CHECK/org_scope subtree 필터 — 그대로 답습) + TASK-ERP-BE-007 (employee/department projection — org_scope 해소 경유).
- **decision (user, 2026-06-06)**: 다음 작업 = read-model delegation projection, **2-leg(revoke 이벤트 추가 + create+revoke 투영)** 선택(읽기모델 본질=정확성).

# Goal

approval-service 의 위임(grant/revoke) 사실을 read-model 이 정확히 투영(ACTIVE/REVOKED)하여 "누가 누구를 대신할 수 있는가"를 read-only 로 조회 가능케 한다. revoke 이벤트가 없어 read model 이 취소를 못 보던 갭을, revoke 이벤트 발행(producer) + 투영(consumer)으로 닫는다. E5 — read model 은 도메인 로직·재발행 0; approval-service 가 권위(history).

# Scope

## In Scope

### Leg A — producer (approval-service, 기존 경로 불변 + revoke 이벤트만 추가)
- **신규 토픽 `erp.approval.delegation.revoked.v1`** (`aggregateType=DelegationGrant`, `aggregateId`=partition key=`grantId`). payload: `grantId`/`delegatorId`/`delegateId`/`reason`(NON_NULL absent)/`tenantId`/`occurredAt`/`actor`. `ApprovalEventPublisher.publishRevoked(DelegationGrant g, String actor)` 신설(`publishDelegated` 미러).
- **`DelegationApplicationService.revokeDelegation()`** 에 `eventPublisher.publishRevoked(...)` 호출 추가 — **revoke 의 `@Transactional` outbox 경계 안**(audit row + outbox 이벤트 원자적; A7). 멱등 revoke(이미 REVOKED 재호출)는 이벤트 재발행 안 함(상태 전이 시에만 발행) 또는 멱등 발행 — 멱등 소비라 무해하나 전이-시-1회 권장.
- 기존 grant create→`erp.approval.delegated.v1` 발행 + 4 transition 토픽 + DelegationResolver/전이 SoD = **byte-unchanged**.

### Leg B — consumer (read-model-service, ERP-BE-010 청사진 답습)
- **신규 consumer** `DelegationFactEventConsumer` — 2 `@KafkaListener`(`erp.approval.delegated.v1` + `erp.approval.delegation.revoked.v1`), consumer group `erp-read-model-v1`(기존 합류), @RetryableTopic 3-retry+DLT + manual ACK + `processed_events` dedupe(T8) + invalid→즉시 DLT. partition key=`grantId`. envelope DTO=approval-fact 의 `ApprovalEventEnvelope` 재사용 또는 동형 신설.
- **domain** `DelegationFactProjection`(grantId PK, delegatorId, delegateId, validFrom, validTo nullable, status `DelegationFactStatus`{ACTIVE,REVOKED}, reason nullable, revokedAt nullable, lastEventAt, lastEventId provenance) — **last-event-wins + out-of-order 허용**: delegated→ACTIVE(`ofGranted`)/revoked→REVOKED(`ofRevoked` out-of-order: grant 미수신 상태에서 revoke 먼저 와도 payload 로 row 생성, 미상필드 ABSENT, no fabrication); revoke 후 늦은 grant 재도착→REVOKED 유지(취소 안 되돌림, terminal-ish). dedupe(eventId) 가 진짜 중복 방지.
- **apply usecase** `ApplyDelegationFactUseCase`(@Transactional, dedupe→findById→ofGranted|applyGrant / ofRevoked|applyRevoke→save→markProcessed). **query usecase** `QueryDelegationFactUseCase`(list/detail; 필터 delegatorId/delegateId/status/activeAt[validFrom≤t≤validTo∧status=ACTIVE]; org_scope subtree 필터).
- **org_scope 필터** = **delegator 직원의 부서 subtree 기준**(approval-fact EMPLOYEE subject→`employee_proj.departmentId` 해소 동형; delegatorId 가 employee id). `["*"]`/미설정=net-zero(무필터). 미해소 delegator dept=bounded scope 에서 제외(fail-closed). detail out-of-scope→404 `MASTERDATA_NOT_FOUND`(존재 누설 방지, approval-fact 동형).
- **controller** `DelegationFactController` — `GET /api/erp/read-model/delegations`(필터 delegatorId/delegateId/status/activeAt?/page/size, org_scope read filter) + `GET /api/erp/read-model/delegations/{grantId}`(404 out-of-scope/miss). READ gate(entitlement-trust dual-accept + erp.read∨operator∨entitled, 기존 재사용).
- **Flyway V3** `delegation_fact_proj` 테이블 + **`CONSTRAINT ck_delegation_fact_proj_status CHECK (status IN ('ACTIVE','REVOKED'))`**(⚠️ ERP-BE-014 교훈: enum 컬럼 STRING 이어도 CHECK allow-list 명시 필수 — Docker-free :check 미포착, Testcontainers IT 권위 게이트) + 인덱스(delegator_id, delegate_id, status, valid_from/valid_to, last_event_at).
- **계약/스펙**: erp-approval-events.md(revoke 토픽 v2.2 amendment + payload) + read-model-subscriptions.md(delegation 2 토픽 구독 + 투영 semantics + L72-74 "NOT consumed"→consumed 수정) + read-model-api.md(delegation 조회 endpoint) + read-model architecture.md(amendment blockquote + Identity event consumption + Out-of-Scope 정합) + approval-service architecture.md(revoke 이벤트 발행 노트).
- **tests**: 
  - approval-service: revoke→`erp.approval.delegation.revoked.v1` 발행 unit + IT 회귀(grant create/transition/SoD 불변). 
  - read-model: DelegationFactProjection unit(granted→ACTIVE, revoked→REVOKED, out-of-order revoke-before-grant, revoke 후 grant 재도착 REVOKED 유지) + Apply/Query usecase unit(dedupe, org_scope delegator-subtree 필터, activeAt) + controller slice + **IT**(@Tag integration, Testcontainers MySQL+Kafka): grant publish→ACTIVE 투영→조회 / revoke publish→REVOKED 전이 / dedupe / org_scope list+404 / activeAt 필터. H2 forbidden.

## Out of Scope
- per-request/per-route 위임·자동부재(auto-absence)·transitive delegation = approval-service v2.2(별).
- delegation 이벤트의 notification 소비(ERP-BE-014 가 grant 알림 처리; revoke 알림은 revoke 이벤트 생기면 별 후속 — notification leg).
- business-partner/permission read facts.
- 콘솔 가시화(PC-FE — 위임 grant 관리 UI PC-FE-054 이미 출하; read-model delegation 조회 카드는 별 PC-FE 후보).
- masterdata-service / notification-service 변경.

# Acceptance Criteria

- [ ] **AC-1 (producer leg)** revoke 시 `erp.approval.delegation.revoked.v1` 발행(`@Transactional` outbox 경계, payload grantId/delegatorId/delegateId/reason?/tenantId/occurredAt/actor). grant create→delegated 발행 + 4 transition 토픽 + SoD/resolver = byte-unchanged(회귀 통과).
- [ ] **AC-2 (consumer leg)** 2 토픽 구독→`delegation_fact_proj` 투영: delegated→ACTIVE, revoked→REVOKED. dedupe(T8) 중복 eventId skip. invalid→즉시 DLT. partition key=grantId.
- [ ] **AC-3 (projection 정확성)** last-event-wins: revoke 후 늦은 grant→REVOKED 유지(취소 안 되돌림). out-of-order: grant 미수신 revoke→payload 로 REVOKED row 생성(미상필드 ABSENT, no fabrication).
- [ ] **AC-4 (query + org_scope)** `GET /delegations` delegatorId/delegateId/status/activeAt 필터 + org_scope=delegator 부서 subtree(net-zero on `["*"]`/미설정). `GET /delegations/{grantId}` out-of-scope/miss→404 `MASTERDATA_NOT_FOUND`. READ gate(entitlement-trust).
- [ ] **AC-5 (E5)** read-model publish/outbox/write-back 0(grep 게이트). 도메인 로직 0(상태 투영만). approval-service 가 history 권위.
- [ ] **AC-6 (CHECK 게이트)** V3 마이그레이션이 `delegation_fact_proj.status` CHECK allow-list(`ACTIVE`,`REVOKED`) 명시. `./gradlew :…:read-model-service:check` + `:…:approval-service:check` GREEN(unit). IT(@Tag integration, CI Linux Testcontainers) = 권위 게이트(grant→ACTIVE, revoke→REVOKED, dedupe, org_scope, activeAt). H2 미사용.

# Related Specs

- `specs/services/read-model-service/architecture.md`(이 PR — delegation amendment) + `specs/services/approval-service/architecture.md`(revoke 이벤트 노트). consume/produce: `specs/contracts/events/erp-approval-events.md`(revoke 토픽). subscriptions: `read-model-subscriptions.md`. serve: `read-model-api.md`(delegation 조회). ADR-MONO-005 Category C. ADR-MONO-016 §D3. erp.md E5/E6/E7/E8 + transactional T8/A7 + audit-heavy A2/A3.

# Related Contracts

- produce(approval): `erp-approval-events.md` 신규 `erp.approval.delegation.revoked.v1`(+ 기존 `erp.approval.delegated.v1` 불변). consume(read-model): 두 토픽. serve: `read-model-api.md` `GET /api/erp/read-model/delegations` + `/{grantId}`. subscriptions: `read-model-subscriptions.md`.

# Edge Cases

- revoke-before-grant(out-of-order): payload 로 REVOKED row 생성, validFrom/validTo 등 grant-only 필드는 revoke payload 에 없으면 ABSENT(no fabrication).
- 멱등 revoke(이미 REVOKED 재호출): producer 가 전이-시-1회 발행 권장(또는 멱등 발행→소비측 dedupe). 소비측 revoke 재수신=dedupe skip 또는 REVOKED 유지(no-op).
- activeAt 조회: status=ACTIVE ∧ validFrom≤t ∧ (validTo 없음 ∨ t≤validTo). 무기한(validTo absent)=상한 무제한.
- org_scope: delegator dept 미해소(employee_proj 미투영)→bounded scope 제외(fail-closed); `["*"]`=무필터.
- 중복 이벤트(at-least-once): dedupe(eventId) skip.
- CHECK 제약: status enum STRING 이어도 V3 에 CHECK allow-list 명시(ERP-BE-014 함정).

# Failure Scenarios

- consume 처리 실패→3-retry+DLT(Category C). 부분기록 0(tx).
- invalid envelope(null eventId/grantId/payload, non-erp tenantId)→즉시 DLT.
- revoke 이벤트 발행 누락(outbox 경계 밖)→read model 이 영원히 ACTIVE(회귀: revoke→REVOKED IT 가 게이트).
- CHECK allow-list 누락→insert 거부(Testcontainers IT RED; ERP-BE-014 재발 가드).
- read-model 재발행 유혹→E5 위반(no outbox); grep 게이트.

# Test Requirements

- approval-service: publishRevoked unit + revoke→이벤트 IT + grant/transition/SoD 회귀.
- read-model: DelegationFactProjection unit(ACTIVE/REVOKED/out-of-order/last-wins) + Apply/Query usecase(dedupe/org_scope delegator-subtree/activeAt) + controller slice + IT(grant→ACTIVE→조회 / revoke→REVOKED / dedupe / org_scope list+404 / activeAt). H2 forbidden.
- `:read-model-service:check` + `:approval-service:check` GREEN. read-model publish/outbox grep 0. V3 CHECK allow-list 명시. IT CI Linux 권위.

# Definition of Done

- [ ] Leg A: `publishRevoked` + revokeDelegation 이벤트 발행(outbox 경계) + erp-approval-events.md revoke 토픽. grant/transition byte-unchanged.
- [ ] Leg B: DelegationFactEventConsumer(2 토픽) + DelegationFactProjection(ACTIVE/REVOKED/out-of-order/last-wins) + Apply/Query usecase + Controller(list/detail org_scope) + V3(CHECK allow-list) + repo/JPA.
- [ ] terminal consumer(no outbox/no re-emit, grep 0) + dedupe + DLT.
- [ ] spec/contract 갱신(read-model architecture amendment + subscriptions + read-model-api + erp-approval-events revoke + approval architecture 노트).
- [ ] `:read-model-service:check` + `:approval-service:check` GREEN; IT CI Linux GREEN(grant→ACTIVE, revoke→REVOKED, dedupe, org_scope, activeAt).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (2-service atomic event-driven: producer revoke 이벤트 + consumer projection 상태기계[ACTIVE/REVOKED, out-of-order, last-wins] + org_scope subtree 필터 + dedupe/DLT/Category C + V3 CHECK). 사용자 "delegated-event 소비자 고리"→ERP-BE-015 2-leg 선택(읽기모델 정확성). 메타: ① **읽기모델 정확성엔 revoke 이벤트 필수** — create-only 투영은 수동 취소를 못 반영(시간창 만료만 거름) → producer leg(revoke 이벤트) + consumer leg 를 1 atomic PR(2 서비스, 모노레포 장점). ② **V3 CHECK allow-list 명시**(ERP-BE-014 교훈: `@Enumerated(STRING)` 길이 OK ≠ 마이그레이션 불필요, CHECK 별도 — Docker-free :check 미포착, Testcontainers IT 권위). ③ ERP-BE-010 청사진 1:1 답습(consumer/projection/apply·query/controller/repo/JPA/Flyway/org_scope). [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]] [[project_platform_console_adr_013]]
