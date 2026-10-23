# Task ID

TASK-MONO-816

# Title

Kafka consumers that drop the failure cause, and one consumer with no dead letter at all — fleet-wide sweep after `TASK-BE-651`

# Status

ready

# Owner

backend

# Task Tags

- code
- observability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (pattern copy of `TASK-BE-651`'s `CauseLoggingRetryListener` across services, plus one error-handler bean)
> Drafted 2026-10-24 UTC from `TASK-MONO-813` AC-0 ⑤. Owner decision 2026-10-24 (selection window): «별도 티켓으로 분리» — 813 owns the dead-letter *name*; this ticket owns *what the operator can see when a record is dead-lettered*, and the one consumer that has no dead letter.

# Dependency Markers

- 출처: `TASK-MONO-813` AC-0 ⑤ · `docs/adr/ADR-MONO-085-dead-letter-topic-suffix-dlt.md` § Outstanding follow-ups («이 ADR 이 정하지 않는 것»).
- 선례: `TASK-BE-651` (wms inventory-service) — `CauseLoggingRetryListener` 를 붙여 리스너 예외의 원인을 남겼다. 🔴 **새로 짓지 말고 그것을 복사하라** (CLAUDE.md Required Workflow step 10).
- 후속 관계: `TASK-MONO-813` 이 같은 `KafkaConsumerConfig` 파일들을 건드린다(접미사 · 파티션). **813 이 먼저 머지된 뒤 착수**하면 같은 파일 충돌을 피한다 — 순서 제약이지 기능 의존은 아니다.

# Goal

When a consumer gives up on a record, the log says **why** — the exception class and message of the root cause, with topic · partition · offset — in every service. And every `@KafkaListener` has a dead-letter path, so no consumer silently discards a record after its retries.

# Scope

## In Scope

- **AC-0 measurement (re-measure — the list below is from 813's static read on 2026-10-24, origin/main `6f5431876`):**
  - Recoverers whose resolver does **not** log the cause (no `log.error/warn` near `new DeadLetterPublishingRecoverer`):
    - wms admin `ProjectionKafkaConsumerConfig.java:43` · inbound `KafkaConsumerConfig.java:39` · notification `KafkaConsumerConfig.java:38` · outbound `KafkaConfig.java:39` (inventory already fixed by BE-651)
    - scm logistics `KafkaConsumerConfig.java:88`
  - `@RetryableTopic` services with **no `@DltHandler`**: erp notification · erp read-model · finance ledger · scm demand-planning · scm inventory-visibility. ⚪ Whether Spring's default DLT handler logs the root cause was **not** measured — measure it first; if it already does, those five are phantom and are recorded as such.
  - Resolvers that log only `ex.getMessage()` (ecommerce 10 · iam 2 · fan 1): decide whether message-only counts as «the cause». `ListenerExecutionFailedException` wraps the real exception, so the top-level message may not name it — measure one.
  - iam account-service `LoginSucceededConsumer.java:49` — **no error-handler bean**, so Spring Boot's default handler retries and then **drops** the record (no dead letter).
- Implementation per the measured list.

## Out of Scope

- The dead-letter topic **name**, partition choice and provisioning → `TASK-MONO-813` / `ADR-MONO-085`.
- The fleet-wide «DLQ depth > 0 MUST alert» rule (`platform/event-driven-policy.md:116`) — only iam has it today; separate.
- Replaying records already dead-lettered.

# Acceptance Criteria

- [ ] **AC-0** — the list above re-measured with file:line; phantom entries recorded as phantom, not «fixed».
- [ ] **AC-1** — every remaining entry logs root-cause class + message + topic/partition/offset when a record is dead-lettered, by copying `TASK-BE-651`'s mechanism (or recording why a service needs a different one).
- [ ] **AC-2** — iam account-service has an error handler that dead-letters per the name rule `ADR-MONO-085` settles (whatever the owner accepts there), with a test that a poison record lands on it.
- [ ] **AC-3** — one test per mechanism shape (resolver · `@RetryableTopic`) that asserts the cause appears in the log output, red before the change.

# Related Specs

- `platform/event-driven-policy.md` § DLQ Policy · § Error Classification
- `platform/service-types/event-consumer.md` § Retry and DLQ
- `.claude/skills/messaging/consumer-retry-dlq/SKILL.md`

# Related Contracts

- None — logging and error handling do not change any event contract.

# Edge Cases

- `DeserializationException` arrives with a `null` value; the log line must not NPE while describing it (iam security-service already handles this — `KafkaConsumerConfig.java:94-109`).
- Logging the full payload may leak personal data (iam login events). Log the cause and coordinates, not the record value.

# Failure Scenarios

1. Only the wms services get fixed because BE-651 was wms → the `@RetryableTopic` five stay silent. AC-0 measures all shapes for this reason.
2. AC-2 lands before `ADR-MONO-085` is accepted and hard-codes a suffix that the ADR then changes → account-service becomes the first violation of 813's guard. Wait for the ADR, or let 813's guard catch it.
