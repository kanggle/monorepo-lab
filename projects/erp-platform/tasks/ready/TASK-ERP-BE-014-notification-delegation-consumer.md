# Task ID

TASK-ERP-BE-014

# Title

**notification-service delegation-granted 알림 — `erp.approval.delegated.v1` 소비 (approval delegation leg 완결).** ERP-BE-013 이 발행하나 아무도 구독하지 않던 신규 `erp.approval.delegated.v1`(producer-only forward 토픽)을 notification-service 가 5번째 consumer 로 구독 → 위임받은 결재자(`delegateId`)에게 "결재 권한 위임됨" in-app 알림. 기존 4 approval-transition consumer 경로는 **byte-unchanged** 보존하고 평행 additive 경로(`DelegationEvent` render + `NotifyOnDelegationCommand` + `RecipientResolver`/`NotificationFactory` overload + `SourceRef.delegation` + `ApprovalDelegatedConsumer`)만 추가. `NotificationType.DELEGATION_GRANTED` 신규(enum STRING → Flyway 불필요). terminal consumer(no outbox / no re-emit) 불변. delegation 이벤트 소비자 고리(approval→notification delegated leg) 완결.

# Status

ready

# Owner

backend-engineer (erp notification-service delegation consumer increment — additive, 기존 4-consumer 경로 불변)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- event
- test

---

# Dependency Markers

- **realises**: notification-service architecture.md § Out-of-Scope "Delegation notifications — consuming `erp.approval.delegated.v1` … v2" + `notification-subscriptions.md` § v2 deferred(delegation) + `erp-approval-events.md` v2.1 amendment("a 'you have been delegated' notification … is a separate v2.2 increment"). 신규 architecture.md amendment(v1.1) 가 HARDSTOP-09 충족 — 신규 ADR 불요(forward-declared 토픽의 N번째 소비자, 기존 first-increment 텍스트 보존하는 amendment-blockquote).
- **consumes (producer)**: TASK-ERP-BE-013 (`erp.approval.delegated.v1` outbox 이벤트 — 이미 발행 중, main `3176236b`; grant create 시 발행, revoke 무발행). approval-service 불변(notification 이 소비만).
- **builds on**: TASK-ERP-BE-011 (notification-service first increment — 4 approval consumer + RecipientResolver/NotificationFactory + Notification/NotificationDelivery + dedupe(T8) + Category C + entitlement-trust). 이 task = 그 위에 5번째 consumer 평행 추가.
- **decision (user, 2026-06-06)**: 다음 작업 = delegated-event 소비자 고리(notification leg 우선; read-model delegation projection 은 별 후속).

# Goal

approval-service 가 발행하나 소비자가 없던 `erp.approval.delegated.v1` 을 notification-service 가 소비 → 위임받은 결재자(`delegateId`)에게 "결재 권한이 위임되었습니다" in-app 알림으로 fan-out. 위임자(`delegatorId`)·유효기간(`validFrom`/`validTo`)·사유(`reason`)를 본문에 렌더. 기존 4 approval consumer 동작·계약 불변. terminal consumer(재발행 0) 유지.

# Scope

## In Scope

- **신규 consumer** `ApprovalDelegatedConsumer` — `erp.approval.delegated.v1` 구독, consumer group `erp-notification-v1`(기존과 동일), @RetryableTopic 3-retry+DLT + manual ACK + `processed_events` dedupe(T8) + invalid envelope→즉시 DLT. partition key=`grantId`(per-grant ordering).
- **recipient 해소(delegate)** delegation 이벤트의 수신자 = `payload.delegateId`(위임받은 사람). `RecipientResolver.resolve(DelegationEvent)` overload 추가(기존 `resolve(ApprovalEvent)` 불변). `delegateId` null/blank → invalid → DLT(전달 불가).
- **신규 render 모델** `DelegationEvent`(pure record: eventId, tenantId, grantId, delegatorId, delegateId, validFrom, validTo?, reason?). `NotificationFactory.from(DelegationEvent,…)` overload — title "결재 권한 위임됨" + body(delegatorId, validFrom, validTo[없으면 "무기한"], reason if present) + `SourceRef.delegation(grantId)`.
- **`NotificationType.DELEGATION_GRANTED`** enum 값 추가(`@Enumerated(STRING)` length 32 — DDL/Flyway 불필요). 기존 ApprovalEvent 경유 exhaustive switch(`RecipientResolver`/`NotificationFactory`)에는 방어적 `case DELEGATION_GRANTED -> throw IllegalStateException`(ApprovalEvent 는 이 타입을 절대 안 실음 — 컴파일 안전).
- **`SourceRef.SourceType.DELEGATION`** + `SourceRef.delegation(grantId)` 팩토리(기존 `APPROVAL`/`approval()` 불변).
- **application** `NotifyOnDelegationCommand(DelegationEvent, topic)` + `NotifyOnApprovalEventUseCase.handle(NotifyOnDelegationCommand)` overload — 기존 `handle(NotifyOnApprovalCommand)` 의 persist/deliver/dedupe 시퀀스를 private helper 로 추출해 공유(기존 handle 동작 byte-identical: dedupe→recipient→render→Notification 영속→IN_APP delivery DELIVERED→dedupe provenance→metrics). dedupe provenance aggregateId=`grantId`.
- **mapper** `EnvelopeToCommandMapper.mapDelegation(rawValue, topic)` → `NotifyOnDelegationCommand`(tenantId=erp 불변식 + grantId/delegatorId/delegateId/validFrom 필수 검증, recipient field=delegateId 부재→invalid DLT). 기존 `map(...)` byte-unchanged(envelope DTO `ApprovalEventEnvelope` 재사용 — 범용 envelope+payload-map).
- **tests**: `RecipientResolverTest`(delegation→delegate) + `NotificationFactoryTest`(delegation title/body, validTo 무기한, reason absent) + `NotifyOnApprovalEventUseCaseTest`(delegation happy + dedupe-skip) + `EnvelopeToCommandMapperTest`(mapDelegation valid + delegateId null→invalid) + IT `NotificationEndToEndIntegrationTest` 에 delegation→inbox + dedupe 케이스 추가(topic pre-create + delegationEnvelope helper).

## Out of Scope

- **read-model-service delegation projection**(`erp.approval.delegated.v1` → delegation-fact 투영 / "누가 누구를 대신할 수 있는가" 조회) — 별 후속 증분(ERP-BE-015 후보). 이 task=notification leg 만.
- grant **revoke** 알림 — ERP-BE-013 가 revoke 를 audit-only(이벤트 무발행)로 설계 → 소비할 이벤트 자체가 없음(v2.2 `erp.approval.delegation.revoked` 발행 시 후속).
- 외부 채널(Slack/SMTP) / preferences / digest — notification-service v2 불변.
- 콘솔 가시화 — PC-FE-054(위임 grant 관리 UI) 이미 출하; notification-bell(PC-FE-052)이 새 타입을 free-string tolerant 로 이미 수용(별 변경 불요).
- approval-service / read-model-service / masterdata-service 변경(notification 이 소비만).

# Acceptance Criteria

- [ ] **AC-1** `ApprovalDelegatedConsumer` 가 `erp.approval.delegated.v1` 소비 → recipient=`delegateId` 해소 → `Notification` 1건(type=DELEGATION_GRANTED, sourceType=DELEGATION, sourceId=grantId, title/body 렌더) 생성. dedupe(T8) 중복 eventId skip. invalid(null eventId/delegateId/payload)→즉시 DLT.
- [ ] **AC-2** in-app 전달: Notification 영속 + delivery status=DELIVERED(attempt_count=1) — 기존 IN_APP 경로 재사용(동일 @Transactional helper).
- [ ] **AC-3** 기존 4 approval consumer 경로 **byte-unchanged**: `ApprovalEvent`/`NotifyOnApprovalCommand`/`map(...)`/기존 `handle(NotifyOnApprovalCommand)` 외부 동작 불변(기존 unit/IT 그대로 통과). `RecipientResolver.resolve(ApprovalEvent)`/`NotificationFactory.from(ApprovalEvent)` 결과 동일.
- [ ] **AC-4** terminal consumer 불변: publish/outbox/재발행 0(grep 게이트; `OutboxAutoConfiguration` exclude 유지). delegation 경로도 도메인 로직 0(recipient 해소+렌더만).
- [ ] **AC-5** body 렌더: `delegatorId`, `validFrom` 항상; `validTo` 없으면 "무기한"(open-ended) 표기; `reason` present 일 때만 포함(NON_NULL absent 관용). ids-only(이름 해소 없음, no fabrication).
- [ ] **AC-6** `./gradlew :projects:erp-platform:apps:notification-service:check` GREEN(unit; integrationTest 는 check 제외). IT(@Tag integration) delegation→inbox + dedupe 케이스 CI Linux(Testcontainers MySQL+Kafka). H2 미사용. Flyway 마이그레이션 추가 0(enum STRING).

# Related Specs

- `specs/services/notification-service/architecture.md`(이 spec PR — v1.1 amendment blockquote + Recipient resolution 표 + Identity event consumption + Out-of-Scope 에서 delegation 제거 + NotificationType/SourceType set 갱신). consume: `erp-approval-events.md`(v2.1 amendment delegated 토픽 — 소비자 노트 갱신). erp.md E5(no domain logic/re-emit)/E6(read 인가)/E7(internal)/E8(추적) + internal-system I1/I2/I6 + transactional T8(dedupe) + audit-heavy A2/A3. ADR-MONO-005 Category C(IN_APP single-step). ADR-MONO-016 §D3.

# Related Contracts

- consume: `erp-approval-events.md` § v2.1 amendment `erp.approval.delegated.v1`(payload grantId/delegatorId/delegateId/validFrom/validTo?/reason?/tenantId/occurredAt/actor; aggregateType=DelegationGrant). subscriptions: `notification-subscriptions.md`(이 PR — 5번째 consumed topic 추가 + recipient resolution row + v2 deferred 에서 delegation 제거). serve: `notification-api.md` 불변(inbox 가 새 타입을 그대로 노출 — type 은 free-string).

# Edge Cases

- `validTo` 부재(무기한 위임): body "무기한" 표기, NON_NULL absent 정상.
- `reason` 부재: body 에서 생략(approved 선례 — NON_NULL absent).
- `delegateId` null/blank: invalid envelope → 즉시 DLT(수신자 부재 — 전달 불가).
- 중복 이벤트(at-least-once): dedupe(eventId) skip; notification 1건만.
- delegate=delegator(자기위임)는 producer(ERP-BE-013)가 422 로 차단 → 정상 이벤트엔 등장 불가; 방어적으로 그대로 렌더(소비측은 producer 불변식 신뢰).
- revoke: 이벤트 무발행 → notification 무생성(설계상 정상, silent drop 아님 — § Out of Scope 기록).
- 콘솔: notification-bell(PC-FE-052)이 type 을 free-string tolerant 로 처리 → DELEGATION_GRANTED 무변경 수용.

# Failure Scenarios

- consume 처리 실패 → 3-retry+DLT(Category C). 부분기록 0(tx — Notification+delivery+dedupe 한 트랜잭션).
- invalid envelope(null eventId/delegateId/payload, non-erp tenantId) → 즉시 DLT(no retry).
- 신규 enum 값이 기존 ApprovalEvent switch 누락 → 컴파일 실패(exhaustive, default 없음)로 즉시 검출 → 방어적 case 로 해소.
- terminal consumer 재발행 유혹 → E5 위반(no outbox); grep 게이트.

# Test Requirements

- unit: RecipientResolver(delegation→delegate) + NotificationFactory(delegation title/body, validTo 무기한, reason absent) + UseCase(delegation happy + dedupe-skip, 기존 approval handle 동작 불변 회귀) + mapper(mapDelegation valid / delegateId null→invalid). 기존 unit 전량 통과(byte-unchanged 경로).
- IT: `NotificationEndToEndIntegrationTest` 에 (a) delegation publish→consume→Notification(recipient=delegate, type=DELEGATION_GRANTED, sourceId=grantId) + (b) 동일 eventId 2회→1건(dedupe) 추가. topic pre-create 에 delegated 추가. H2 forbidden.
- `./gradlew :projects:erp-platform:apps:notification-service:check` GREEN. publish/outbox grep 0. Flyway 추가 0.

# Definition of Done

- [ ] `ApprovalDelegatedConsumer` + `DelegationEvent` + `NotifyOnDelegationCommand` + UseCase overload + RecipientResolver/NotificationFactory overload + `NotificationType.DELEGATION_GRANTED` + `SourceRef.delegation` + mapper.mapDelegation.
- [ ] 기존 4 approval consumer 경로 byte-unchanged(회귀 unit/IT 통과).
- [ ] terminal consumer(no outbox/no re-emit, grep 0) + dedupe + DLT 유지.
- [ ] spec/contract 갱신(architecture.md v1.1 amendment + notification-subscriptions.md + erp-approval-events.md 소비자 노트).
- [ ] `:check` GREEN; IT CI Linux GREEN(delegation→inbox + dedupe).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (event-driven consumer 추가 — 평행 additive 경로 설계[기존 4-consumer byte-unchanged 보존] + recipient/render/mapper overload + dedupe/DLT/Category C 재사용 + enum exhaustive-switch 안전). 사용자 "delegated-event 소비자 고리" 선택(notification leg 우선). 메타: ① **새 aggregate-fact 토픽의 N번째 소비자 = 평행 additive 경로**(기존 ApprovalEvent/command/mapper/factory/resolver byte-unchanged, 신규 DelegationEvent 짝 추가) — ERP-BE-010/011 first-increment + ERP-BE-013 "신규 fact=신규 토픽" 의 소비측 짝. ② enum exhaustive switch(default 없음)가 새 타입 누락을 컴파일 차단 → 방어적 case 로 의도 명시. ③ Flyway 불필요(`@Enumerated(STRING)` length 32). read-model delegation projection 은 별 후속(ERP-BE-015 후보). [[project_monorepo_template_strategy]] [[project_platform_console_adr_013]] [[feedback_spring_boot_diagnostic_patterns]]
