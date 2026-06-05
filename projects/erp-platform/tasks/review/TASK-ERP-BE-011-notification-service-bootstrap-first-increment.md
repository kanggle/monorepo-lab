# Task ID

TASK-ERP-BE-011

# Title

**notification-service 부트스트랩 — 결재 알림 fan-out first increment (ERP v2 pillar).** ADR-MONO-016 §D3 + PROJECT.md v2 service map 이 forward-declare 한 `notification-service` 를 read-model(ERP-BE-007)/approval(ERP-BE-009) 선례대로 first increment 로 실행: dual-type Hexagonal(`apps/notification-service/`, `com.example.erp.notification`; **event-consumer** primary + **rest-api** inbox) — `erp.approval.{submitted,approved,rejected,withdrawn}.v1` 4 topic 구독 → recipient 해소(submitted→approver / approved·rejected→submitter / withdrawn→approver) → 메시지 렌더 + in-app `Notification` 영속 + recipient-scoped inbox read API(`GET /api/erp/notifications` + `POST /{id}/read`). ADR-MONO-005 Category C(single-step retry+DLT, notification 이 reference). terminal consumer(no outbox / no re-emit). approval→notification forward-consumer leg 완결.

# Status

review

# Owner

backend-engineer (erp notification-service bootstrap; ADR-016 §D3 amendment + architecture/contracts 이 spec PR 동반 — impl)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- code
- api
- event
- deploy
- test

---

# Dependency Markers

- **realises**: ADR-MONO-016 §D3 amendment(2026-06-05, TASK-ERP-BE-011) — notification-service first increment forward-declaration 집행. `erp-approval-events.md` § forward consumers("notification-service v2 — fan-out approval-state notifications") 의 notification 측. erp.md § Integration Boundaries(알림 채널) + E6(read 인가)/E7(internal)/E8(추적) + internal-system I1/I2/I6 + audit-heavy A2/A3 + transactional T8(dedupe). ADR-MONO-005 Category C(single-step retry+DLT).
- **consumes (producer)**: TASK-ERP-BE-009 (`erp.approval.*.v1` outbox 이벤트 — 이미 발행 중, main). approval-service 불변(notification 이 소비만).
- **builds on**: TASK-ERP-BE-007 (read-model event-consumer 컨벤션 — @RetryableTopic+DLT+processed_events dedupe + OutboxAutoConfiguration exclude + RS256+entitlement-trust 재사용) + wms notification-service(delivery 상태기계 + Resilience4j blueprint). read-model approval-fact(ERP-BE-010)와 별개 consumer(read-model=projection, notification=fan-out).
- **deploy**: root `settings.gradle` include `projects:erp-platform:apps:notification-service` + `.github/workflows/ci.yml` erp `:check`/`:integrationTest` 추가 + erp `docker-compose.yml` notification-service 블록 + `erp.local` `PathPrefix(/api/erp/notifications)` 라우팅(rest-api inbox surface). **monorepo-level shared(root settings.gradle/CI) atomic 동반**(approval-service TASK-ERP-BE-009 선례).
- **decision (user, 2026-06-05)**: 다음 작업 = notification-service 부트스트랩.

# Goal

approval-service 가 발행하던 결재 이벤트를 notification-service 가 소비→수신자에게 in-app 알림으로 fan-out 하고, 수신자가 inbox 로 조회 가능케 한다. erp 알림 채널 bounded context 라이브화(첫 fan-out consumer). E5-adjacent — 도메인 로직·재발행 0, recipient 해소+렌더만.

# Scope

## In Scope

- **service skeleton** `apps/notification-service/` Hexagonal(domain/application/adapter/config), package `com.example.erp.notification`, `NotificationServiceApplication`(**`@SpringBootApplication(exclude={OutboxAutoConfiguration, OutboxMetricsAutoConfiguration})`** — terminal consumer, no re-emit; read-model 미러), build.gradle(read-model 미러: web/data-jpa/validation/actuator/security/oauth2-resource-server/kafka + MySQL/Flyway + Resilience4j[Category C 구조] + java-common/web/messaging/observability/security). MySQL `erp_db`(notification 테이블; Flyway).
- **4 approval consumer** `erp.approval.{submitted,approved,rejected,withdrawn}.v1` — @RetryableTopic 3-retry+DLT + manual ACK + `processed_events` dedupe(T8) + invalid envelope→즉시 DLT. consumer group `erp-notification-v1`. per-approvalRequestId ordering.
- **recipient 해소 + 렌더** `RecipientResolver`(pure): submitted→`approverId`, approved·rejected→`submitterId`, withdrawn→`approverId`. `NotificationType`(APPROVAL_SUBMITTED|APPROVED|REJECTED|WITHDRAWN). title/body 는 이벤트 payload(ids + reason)로 렌더(이름 해소 optional, ids-only 허용 — bus 는 ids만).
- **Notification 영속 + in-app 전달** `Notification`(id, recipientId, type, title, body, sourceType=APPROVAL, sourceId=approvalRequestId, read flag, createdAt, readAt nullable) + `NotificationDelivery`(Category C 구조: status PENDING→DELIVERED/FAILED, attempt_count, scheduled_retry_at). **first increment 채널=IN_APP**(전달=영속, 즉시 DELIVERED, attempt_count=1). `NotificationChannelPort`(IN_APP adapter + stub external adapter no-op/log; 외부 채널 Category C retry scheduler=v2).
- **inbox read API**(rest-api) `GET /api/erp/notifications`(현 recipient[JWT sub] inbox; query unread?/page/size) + `GET /api/erp/notifications/{id}`(본인 것만, 타인→404) + `POST /api/erp/notifications/{id}/read`(mark read, 멱등 — Idempotency-Key 불요). read authz=entitlement-trust dual-accept + `erp.read`∨operator∨entitled(read-model gate 재사용); recipient==caller scope.
- **audit/traceability**(E8/I6/A2): notification dispatch + read 운영 추적(경량 기록; processed_events + delivery outcome). 무거운 immutable audit_log 는 v2(audit-read surface 부재).
- **security/multi-tenancy**: RS256 + entitlement-trust dual-accept + external-traffic rejection; single-tenant erp; public=actuator only.
- **deploy 배선**(atomic): settings.gradle include + ci.yml erp `:check`/`:integrationTest` + docker-compose notification-service 블록 + `erp.local` `PathPrefix(/api/erp/notifications)`.
- **error code**: `NOTIFICATION_NOT_FOUND`(404, recipient-scoped) — platform/error-handling.md erp Notification 섹션 등록(이 spec PR).
- **tests**: recipient mapping unit(4 event→correct recipient) + delivery 상태기계 unit(IN_APP→DELIVERED) + dedupe unit + inbox query/scope unit(본인 것만, unread 필터, mark-read 멱등) + **IT(@Tag integration, Testcontainers MySQL+Kafka)**: 4 approval event produce→consume→Notification 생성(correct recipient)→inbox read(scope)→mark-read; dedupe; invalid→DLT. H2 forbidden.

## Out of Scope

- 외부 채널(Slack/SMTP/push) + exercised Category C retry scheduler — v2(first increment=IN_APP, stub external adapter).
- masterdata-change 알림(`erp.masterdata.*.changed.v1` 소비) — v2(approval 통지만).
- permission/delegation 알림 — v2.
- notification preferences/routing rules / digest·batching — v2.
- 콘솔 notification-bell parity slice — 별 PC-FE task.
- approval-service / masterdata-service / read-model-service 변경(notification 이 소비만).

# Acceptance Criteria

- [ ] **AC-1** 4 approval consumer 가 `erp.approval.*.v1` 소비→recipient 해소(submitted→approver / approved·rejected→submitter / withdrawn→approver)→`Notification` 1건 생성(type/title/body/sourceId). dedupe(T8) 중복 eventId skip. invalid→즉시 DLT.
- [ ] **AC-2** in-app 전달: Notification 영속 + delivery status=DELIVERED(attempt_count=1). NotificationChannelPort IN_APP adapter; 외부 채널 stub(no-op).
- [ ] **AC-3** inbox: `GET /notifications` 가 caller(JWT sub) recipient 것만(unread 필터); 타인 notification 조회/mark-read→404 `NOTIFICATION_NOT_FOUND`. `POST /{id}/read` 멱등(재호출 200, readAt 보존).
- [ ] **AC-4** terminal consumer(E5-adjacent): publish/outbox/재발행 0(grep 게이트; OutboxAutoConfiguration exclude). recipient 해소·렌더 외 도메인 로직 0.
- [ ] **AC-5** internal-system: RS256 + entitlement-trust dual-accept + 외부 트래픽 거부; public=actuator only.
- [ ] **AC-6** `./gradlew :apps:notification-service:check` GREEN. IT(@Tag integration) CI Linux(Testcontainers MySQL+Kafka). H2 미사용. deploy 배선 + healthy 기동. `docker compose config -q` 통과.

# Related Specs

- `specs/services/notification-service/architecture.md`(이 spec PR) + `specs/contracts/events/notification-subscriptions.md` + `specs/contracts/http/notification-api.md`(이 spec PR). ADR-MONO-016 §D3 amendment(2026-06-05). ADR-MONO-005 Category C. consume: `erp-approval-events.md`. erp.md E6/E7/E8 + internal-system I1/I2/I6 + audit-heavy A2/A3 + transactional T8. platform/error-handling.md erp Notification(NOTIFICATION_NOT_FOUND).

# Related Contracts

- consume: `erp-approval-events.md`(4 topic). serve: `notification-api.md`(inbox list/detail/mark-read). subscriptions: `notification-subscriptions.md`.

# Edge Cases

- recipient 미존재/비활성 employee: notification 은 여전히 영속(recipient id 저장; inbox 는 그 id 의 JWT sub 매칭 시 노출). 이름 해소 실패→ids-only 렌더(no fabrication).
- 중복 이벤트(at-least-once): dedupe(eventId) skip; notification 1건만.
- mark-read 재호출: 멱등(200, readAt 불변).
- 타인 notification 접근: 404(존재 누설 방지).
- withdrawn recipient=approver(기안자 본인 회수라 본인 통지 무의미; 대기 결재자 통지).
- 외부 채널 미구현(stub): IN_APP 만 DELIVERED; stub 은 no-op(green-wash 금지 — 외부 전달 주장 안 함).

# Failure Scenarios

- consume 처리 실패→3-retry+DLT(Category C). 부분기록 0(tx).
- invalid envelope(null eventId/payload)→즉시 DLT.
- inbox 인가 우회 시도→fail-closed(403/404).
- terminal consumer 재발행 유혹→E5-adjacent 위반(no outbox); grep 게이트.

# Test Requirements

- recipient mapping(4 event) + delivery 상태기계(IN_APP DELIVERED) + dedupe + inbox query/scope(본인/unread/mark-read 멱등/타인 404) unit. **IT**: Testcontainers MySQL+Kafka produce→consume→Notification(recipient)→inbox→mark-read + dedupe + invalid DLT. H2 forbidden.
- `./gradlew :apps:notification-service:check` GREEN. IT CI Linux. publish/outbox grep 0. healthy 기동.

# Definition of Done

- [ ] notification-service Hexagonal skeleton(event-consumer + rest-api inbox) + 4 approval consumer + recipient 해소 + Notification 영속 + in-app 전달 + inbox read/mark-read.
- [ ] terminal consumer(no outbox/no re-emit, grep 0) + dedupe + DLT + internal-system 경계.
- [ ] settings.gradle/CI/docker-compose 배선(atomic) + healthy 기동 + NOTIFICATION_NOT_FOUND 등록.
- [ ] `:check` GREEN; IT CI Linux GREEN.
- [ ] Task md + INDEX 갱신.
- [ ] Reviewed + merged (3-dim).

---

분석=Opus 4.8 / 구현 권장=Opus (event-driven fan-out — 4 consumer + recipient 해소 + delivery 상태기계[Category C 구조] + inbox read/scope + 신규 서비스 부트스트랩 배선 + dedupe/DLT). 사용자 "notification-service 부트스트랩" 선택. 메타: read-model(ERP-BE-007)/approval(ERP-BE-009) first-increment 선례대로 forward-decl 집행(새 architecture.md=HARDSTOP-09 충족). terminal consumer(no outbox, E5-adjacent — recipient 해소/렌더 로직은 있으나 도메인 로직·재발행 0). approval→notification leg 완결. first increment=IN_APP 채널(외부 Slack/SMTP Category C retry scheduler=v2, green-wash 금지). [[project_monorepo_template_strategy]] [[feedback_spring_boot_diagnostic_patterns]] [[project_platform_console_adr_013]]
