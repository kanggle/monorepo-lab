# Task ID

TASK-BE-348

# Title

account-service — map the `tenant.subscription.changed` outbox event type to its Kafka topic in `AccountOutboxPollingScheduler.resolveTopic`. BE-342 added the `TenantDomainSubscriptionEventPublisher` (emits `tenant.subscription.changed` on every subscribe/suspend/resume/cancel) but never extended the account scheduler's topic switch, so every subscription-lifecycle outbox row hits the `default ->` branch (`IllegalArgumentException: Unknown account event type`), is terminally marked FAILED, and is **never published to Kafka** — a silent violation of the `account-events.md § tenant.subscription.changed` contract (which declares Topic = `tenant.subscription.changed`). Surfaced by the TASK-MONO-210 federation-e2e compose-log dump (FAILED rows for `initech-corp:finance` + `umbrella-corp:finance`).

# Status

done

> **완료 (2026-06-10)**: impl PR #1264 (squash `6ed03766`). 3차원 ✓ (MERGED / origin/main tip=`6ed03766` 일치 / PR 체크 20 pass 0 fail — Build & Test + Integration(iam) GREEN). `AccountOutboxPollingScheduler.resolveTopic` switch 에 `case "tenant.subscription.changed" -> TOPIC_SUBSCRIPTION_CHANGED`("tenant.subscription.changed", account.* 동일이름 관례) 추가 → BE-342 가 발행만 하고 토픽맵 누락해 매 구독변경마다 FAILED 처리되던 이벤트가 이제 정상 발행. 계약 account-events.md §tenant.subscription.changed 준수(코드-only, 스펙 무변경). forward-only(과거 FAILED row 재발행 안 함, fire-and-forget 알림이라 무해). unknown-type throw 유지(deny-by-default). MONO-210 federation-e2e 로그 dump 에서 발견된 결함. 분석=Opus 4.8 / 구현=Sonnet 권장→Opus 4.8 수행(단순 fix).

# Owner

backend

# Task Tags

- bugfix
- event-driven
- outbox
- multi-tenant

---

# Dependency Markers

- **fixes (regression)**: TASK-BE-342 (ADR-MONO-023 D4) added `TenantDomainSubscriptionEventPublisher.EVENT_TYPE = "tenant.subscription.changed"` + the outbox write path but did not add the matching case to `AccountOutboxPollingScheduler.resolveTopic`, so the event has never been deliverable.
- **complies with**: `projects/iam-platform/specs/contracts/events/account-events.md § tenant.subscription.changed` (Topic = `tenant.subscription.changed`) — code-only fix to honour the already-published contract; no contract change.
- **surfaced by**: TASK-MONO-210 (federation-e2e log dump showed the FAILED outbox rows).

# Goal

Make the `tenant.subscription.changed` entitlement-plane event actually publishable: the account outbox poller must resolve its event type to the contract topic so subscribe/suspend/resume/cancel mutations deliver the event to Kafka instead of terminally failing.

# Scope

- `projects/iam-platform/apps/account-service/.../infrastructure/event/AccountOutboxPollingScheduler.java` — add `case "tenant.subscription.changed" -> TOPIC_SUBSCRIPTION_CHANGED;` (constant value `"tenant.subscription.changed"`, identical-name mapping like the `account.*` cases) to the `resolveTopic` switch.
- `projects/iam-platform/apps/account-service/.../infrastructure/event/AccountOutboxPollingSchedulerTest.java` — extend the mapping test with the new event type; keep the unknown-type-throws test.

**Out of scope**: the shared outbox poller `SKIP LOCKED` fragility (separate monorepo-level concern); reprocessing of already-FAILED historical rows (this fix is forward-only — rows marked FAILED before this change stay FAILED; they were never-delivered fire-and-forget notifications, not state of record, and the read paths filter ACTIVE so entitlement correctness is unaffected).

# Acceptance Criteria

- **AC-1** `resolveTopic("tenant.subscription.changed")` returns `"tenant.subscription.changed"` (matches the contract Topic).
- **AC-2** A genuinely unknown event type still throws `IllegalArgumentException` ("Unknown account event type") → terminal FAILED (deny-by-default preserved).
- **AC-3** `account.*` mappings are byte-unchanged (no regression).
- **AC-4** Unit suite green: `:projects:iam-platform:apps:account-service:test` passes (no Docker needed for this slice).

# Related Specs

- `projects/iam-platform/specs/contracts/events/account-events.md § tenant.subscription.changed`
- `docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md` (the event's D4 origin)

# Related Contracts

- `projects/iam-platform/specs/contracts/events/account-events.md`

# Edge Cases

- The event's aggregate is the subscription (`aggregate_type="TenantDomainSubscription"`, `aggregate_id="<tenantId>:<domainKey>"`), unlike the account.* events (aggregate "Account", key account_id) — the topic resolver only keys off `eventType`, so this distinction does not affect the fix, but the test must use the exact event-type string.
- Identical-name topic (event type == topic) matches the existing `account.*` convention and the contract; no topic-prefix translation.
- Historical FAILED rows are not retroactively republished (forward-only); documented as out-of-scope so the FAILED rows observed in the MONO-210 run are expected residue, not a re-open trigger.

# Failure Scenarios

- If the constant value differed from the contract topic (`tenant.subscription.changed`), future consumers (console cache invalidation / billing / notification) would subscribe to the wrong topic and silently receive nothing — AC-1 pins the exact string.
- If the `default -> throw` were replaced with a permissive fallback, an unknown event type would be mis-published to a garbage topic instead of failing closed — AC-2 preserves the deny-by-default terminal-FAILED behaviour.
