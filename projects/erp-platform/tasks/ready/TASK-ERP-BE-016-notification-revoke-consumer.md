# Task ID

TASK-ERP-BE-016

# Title

**notification-service delegation-revoked 알림 — `erp.approval.delegation.revoked.v1` 소비 (revoke 알림 leg, ERP-BE-014 의 revoke 짝).** ERP-BE-015 가 발행하게 된 `erp.approval.delegation.revoked.v1` 을 notification-service 가 **6번째 consumer** 로 구독 → 위임 권한을 잃는 **delegate**(`delegateId`)에게 "위임 권한 회수됨" in-app 알림. ERP-BE-014(grant 알림) 의 정확한 revoke 짝. 기존 5 consumer(4 transition + delegated) 경로 **byte-unchanged** + 평행 additive(`DelegationRevokedEvent` render + `NotifyOnDelegationRevokedCommand` + resolve/from overload + `ApprovalDelegationRevokedConsumer`). `NotificationType.DELEGATION_REVOKED` 신규 + **V3 마이그레이션이 `ck_notification_type` CHECK allow-list 확장**(§16 교훈 선반영). delegation 도메인 알림(grant+revoke) 대칭 완성.

# Status

ready

# Owner

backend-engineer/dispatcher 직접 (notification-service revoke consumer 증분 — ERP-BE-014 mirror, additive)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- event
- test

---

# Dependency Markers

- **consumes (producer)**: TASK-ERP-BE-015 (`erp.approval.delegation.revoked.v1` outbox 이벤트 — revoke 시 ACTIVE→REVOKED 전이에 발행, main `8fc218d9`; payload grantId/delegatorId/delegateId/reason?/tenantId/occurredAt/actor). approval-service 불변(notification 소비만).
- **builds on / mirror**: TASK-ERP-BE-014 (delegation-granted notification consumer — `DelegationEvent`/`NotifyOnDelegationCommand`/`ApprovalDelegatedConsumer`/mapper.mapDelegation/UseCase overload/V2 CHECK). 이 task = revoke 짝(평행 additive). [[feedback_spring_boot_diagnostic_patterns]] §16(CHECK allow-list).
- **realises**: notification-subscriptions.md "Delegation revoke notification — waits on a future `erp.approval.delegation.revoked` producer increment" — ERP-BE-015 가 그 producer 를 만들었으니 이 task 가 소비. erp.md E5/E6/E7/E8 + T8 + A2/A3.
- **decision (user, 2026-06-06)**: 다음 작업 = revoke 알림 leg (notification 6번째 consumer).

# Goal

위임이 회수되면(`erp.approval.delegation.revoked.v1`) 권한을 잃는 delegate 에게 in-app 알림으로 통지. ERP-BE-014(grant→delegate 알림)의 revoke 대칭. terminal consumer(재발행 0, E5).

# Scope

## In Scope
- **신규 consumer** `ApprovalDelegationRevokedConsumer` — `erp.approval.delegation.revoked.v1`, group `erp-notification-v1`, @RetryableTopic 3-retry+DLT + manual ACK + dedupe(T8) + invalid→즉시 DLT. partition key=grantId. (ERP-BE-014 `ApprovalDelegatedConsumer` mirror.)
- **recipient=delegate**: `payload.delegateId`(권한 잃는 당사자). null/blank→invalid→DLT.
- **신규 render** `DelegationRevokedEvent`(pure record: eventId, tenantId, grantId, delegatorId, delegateId, reason?). `type()`=DELEGATION_REVOKED. (DelegationEvent 는 validFrom 필수라 revoke payload(validFrom 없음)에 재사용 불가 → 별 record.)
- **`NotificationFactory.from(DelegationRevokedEvent,…)`** overload — title "위임 권한 회수됨" + body(delegatorId, reason if present) + `SourceRef.delegation(grantId)`(sourceType DELEGATION 재사용).
- **`RecipientResolver.resolve(DelegationRevokedEvent)`** → delegate.
- **`NotificationType.DELEGATION_REVOKED`** enum 값 + 기존 ApprovalEvent 경유 exhaustive switch(`RecipientResolver`/`NotificationFactory` title·body)에 방어적 `case DELEGATION_REVOKED -> throw`(ApprovalEvent 는 이 타입 안 실음).
- **application** `NotifyOnDelegationRevokedCommand` + `NotifyOnApprovalEventUseCase.handle(NotifyOnDelegationRevokedCommand)` overload(공유 `dispatch` helper 재사용; dedupe provenance aggregateId=grantId).
- **mapper** `EnvelopeToCommandMapper.mapDelegationRevoked(rawValue, topic)`(tenantId 불변식 + grantId/delegateId 필수, delegateId 부재→invalid DLT). 기존 `map`/`mapDelegation` byte-unchanged.
- **support** `ApprovalEventConsumerSupport.processDelegationRevoked` (process/processDelegation mirror).
- **Flyway V3** `notification` `ck_notification_type` allow-list 에 `DELEGATION_REVOKED` 추가(DROP CHECK + ADD CONSTRAINT; sourceType DELEGATION 은 V2 에서 이미 허용 — source CHECK 무변경). **§16: enum STRING 길이 OK ≠ 마이그레이션 불필요, CHECK 별도 확장 — Testcontainers IT 권위.**
- **계약/스펙**: notification-subscriptions.md(revoked 토픽 구독 + recipient=delegate; "waits on future producer" 노트→consumed) + architecture.md(v1.2 amendment + Identity event consumption + Out-of-Scope) + erp-approval-events.md(revoke 토픽 소비자=notification 추가; read-model[ERP-BE-015]+notification[이 task]).
- **tests**: RecipientResolver(revoked→delegate) + NotificationFactory(revoked title/body, reason absent) + UseCase(revoked happy + dedupe) + mapper(mapDelegationRevoked valid + delegateId null→invalid) + IT(revoked→inbox + dedupe; topic pre-create + envelope helper).

## Out of Scope
- 기존 5 consumer(4 transition + delegated) 경로 변경(byte-unchanged).
- 외부 채널/preferences/digest(notification v2).
- 콘솔 가시화(notification-bell PC-FE-052 가 type free-string tolerant 로 이미 새 타입 수용 — 별 변경 불요).
- approval-service / read-model 변경.

# Acceptance Criteria
- [ ] **AC-1** `ApprovalDelegationRevokedConsumer` 가 `erp.approval.delegation.revoked.v1` 소비 → recipient=delegateId → `Notification`(type=DELEGATION_REVOKED, sourceType=DELEGATION, sourceId=grantId, title "위임 권한 회수됨", body delegatorId+reason?) 생성. dedupe(T8) skip. invalid(null eventId/delegateId/payload)→즉시 DLT.
- [ ] **AC-2** in-app 전달: Notification 영속 + delivery DELIVERED(attempt=1) — 공유 dispatch helper 재사용.
- [ ] **AC-3** 기존 5 consumer 경로 byte-unchanged(4 transition + delegated; ApprovalEvent/DelegationEvent/map/mapDelegation/기존 handle 불변; 기존 unit/IT 통과).
- [ ] **AC-4** terminal consumer: publish/outbox/재발행 0(grep). DELEGATION_GRANTED + DELEGATION_REVOKED 둘 다 도메인 로직 0.
- [ ] **AC-5** body: delegatorId 항상; reason present 시만(NON_NULL absent 관용). ids-only.
- [ ] **AC-6** V3 가 `ck_notification_type` 에 DELEGATION_REVOKED 추가. `:notification-service:check` GREEN(unit). IT(@Tag integration, CI Linux Testcontainers) revoked→inbox + dedupe. H2 미사용.

# Related Specs
- `specs/services/notification-service/architecture.md`(v1.2 amendment) + notification-subscriptions.md(revoked 토픽) + erp-approval-events.md(revoke 소비자 notification 추가). ADR-MONO-005 Category C. erp.md E5/E6/E7/E8 + T8 + A2/A3.

# Related Contracts
- consume: `erp-approval-events.md` `erp.approval.delegation.revoked.v1`(ERP-BE-015 발행). subscriptions: notification-subscriptions.md(6번째 consumed topic). serve: notification-api.md 불변(inbox 가 새 타입 free-string 노출).

# Edge Cases
- reason 부재: body 생략(NON_NULL absent).
- delegateId null/blank: invalid→DLT.
- 중복(at-least-once): dedupe(eventId) skip.
- producer 가 ACTIVE→REVOKED 전이에만 1회 발행(ERP-BE-015) → 멱등 re-revoke 무발행; 소비측 dedupe 가 재전달 방지.
- 신규 enum 값이 ApprovalEvent switch 누락 → 컴파일 차단 → 방어적 case.
- CHECK: V3 누락 시 insert 거부(Testcontainers IT RED; §16 가드 — 선반영).

# Failure Scenarios
- consume 실패→3-retry+DLT. 부분기록 0(tx).
- invalid envelope→즉시 DLT.
- CHECK allow-list 누락→insert 거부(IT 적발) — V3 선반영으로 가드.
- 재발행 유혹→E5 위반(no outbox); grep 게이트.

# Test Requirements
- unit: RecipientResolver(revoked→delegate) + NotificationFactory(revoked title/body/reason absent) + UseCase(revoked happy+dedupe) + mapper(mapDelegationRevoked valid/delegateId null→invalid). 기존 unit 전량 통과.
- IT: `NotificationEndToEndIntegrationTest` 에 revoked→inbox(recipient=delegate, type=DELEGATION_REVOKED) + dedupe. topic pre-create 에 revoked 추가. H2 forbidden.
- `:notification-service:check` GREEN. publish/outbox grep 0. V3 CHECK 명시. IT CI Linux 권위.

# Definition of Done
- [ ] `ApprovalDelegationRevokedConsumer` + `DelegationRevokedEvent` + `NotifyOnDelegationRevokedCommand` + UseCase overload + resolve/from overload + `NotificationType.DELEGATION_REVOKED` + mapper.mapDelegationRevoked + support.processDelegationRevoked + V3 CHECK.
- [ ] 기존 5 consumer 경로 byte-unchanged(회귀 통과).
- [ ] terminal consumer(grep 0) + dedupe + DLT.
- [ ] spec/contract 갱신.
- [ ] `:check` GREEN; IT CI Linux GREEN(revoked→inbox + dedupe).
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Sonnet (notification consumer 증분 — ERP-BE-014 정확한 mirror, 평행 additive). 직접 구현(작고 패턴 보유). 사용자 "revoke 알림 leg" 선택. 메타: ① **ERP-BE-014 의 revoke 대칭** — grant→delegate 알림의 짝(revoke→delegate "권한 회수됨"). ② revoke payload 는 validFrom 없음 → `DelegationEvent` 재사용 불가, 별 `DelegationRevokedEvent`(평행 additive, granted 경로 byte-unchanged). ③ **V3 CHECK allow-list 선반영**(§16: enum STRING 길이 OK ≠ 마이그레이션 불필요 — ERP-BE-014 의 CI-RED 재발 방지). [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]] [[project_platform_console_adr_013]]
