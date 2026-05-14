# Task ID

TASK-BE-158

# Title

notification-service `external-integrations.md` authoring — Slack Incoming Webhooks v1 marquee + Kafka/Postgres/Secret-Manager infrastructure catalog (portfolio-wide gap closure #1)

# Status

ready

# Owner

wms-platform

# Task Tags

- wms
- notification-service
- spec
- backfill
- integration-heavy
- be

---

# Goal

Portfolio-wide audit (BE-156/157 메모리 surfaced) 결과 wms 7 service 중 4 service (master / admin / notification / gateway) 가 `external-integrations.md` 미존재 = `integration-heavy` trait Required Artifact 1 미충족. 본 task 가 그 중 첫 closure — notification-service 의 spec authoring.

notification-service 는 4 service gap 중 **유일한 non-zero vendor surface** 보유:

- v1 sole external channel: **Slack Incoming Webhooks** (outbound HTTPS, URL-token auth, JDK HttpClient, Resilience4j circuit + retry, 2xx 성공 / 4xx permanent / 5xx retryable mapping)
- 2 channel alias: `wms-alerts`, `wms-shipping` (env-injected per-deploy: `SLACK_WEBHOOK_URL_WMS_ALERTS` + `SLACK_WEBHOOK_URL_WMS_SHIPPING`)
- v2 후보: email (SMTP/SES), SMS, mobile push, Mattermost vendor swap

BE-156 inventory zero-state (97 line) 와 BE-049 outbound TMS marquee (`outbound-service/external-integrations.md` 767 line) 사이의 **mid-scale 사례** — single real vendor + infrastructure catalog. 본 spec authoring 으로:

1. notification-service 의 `integration-heavy` Required Artifact 1 충족.
2. Slack adapter 의 wire-level 명세 (timeout/circuit/retry/permanent-4xx mapping) 가 spec 으로 박힘 — 향후 vendor swap (Mattermost) 또는 v2 multi-channel 도입 시 baseline reference.
3. portfolio-wide 4 service gap 의 첫 closure — master / admin / gateway 는 별 task (모두 zero-state 가능, BE-156 답습 batch 후보).

본 task = **신규 spec authoring 단일** (production code 0, schema 0, markdown only). BE-141~157 single-PR closure 답습.

---

# Scope

## In Scope

### A. 신규 `notification-service/external-integrations.md`

대상 경로: `projects/wms-platform/specs/services/notification-service/external-integrations.md` (신규).

구조 (sibling outbound external-integrations.md 답습 + notification-specific 단순화):

1. **헤더 + intent** — Required Artifact 1 충족 + v1 sole real vendor = Slack 명시 + v2 path 미리 정해진 placeholder 명시.
2. **Catalog Summary** — vendor 표 (Slack Incoming Webhooks + Kafka + PostgreSQL + Secret Manager 4 row, sibling inbound 답습).
3. **Slack Incoming Webhooks (v1 sole marquee)** — section 의 큰 부분 (~120 line):
   - 3.1 Endpoint — `POST {channel.webhook_url}` (vendor-controlled URL, no path)
   - 3.2 Authentication — URL embedded token (separate header 없음). 2 alias env-var injection.
   - 3.3 Anti-replay — N/A (idempotency 는 service-side via deliveryId, vendor-side dedupe 없음)
   - 3.4 Failure Modes table (2xx 성공 / 4xx ChannelPermanentFailure / 5xx RetryableException / IO / timeout / blank URL ChannelNotConfigured)
   - 3.5 Adapter Layout — `adapter/outbound/slack/` 디렉토리 명시 (SlackChannelAdapter / SlackBodyRenderer / SlackChannelProperties)
   - 3.6 Timeouts (I1) — 3s connect / 5s read, JDK HttpClient
   - 3.7 Circuit Breaker (I2) — Resilience4j `slack` (TIME_BASED / 10 / 5 minCalls / 50% / 10s open)
   - 3.8 Retry (I3) — 3 max attempts / 500ms base / exp×2.0 / ignoreExceptions ChannelPermanentFailure
   - 3.9 Idempotency (I4) — service-side deliveryId, Slack vendor 미지원 (vendor policy 명시)
   - 3.10 Bulkhead (I9) — N/A v1 (JDK HttpClient default executor); v2 multi-vendor 시 dedicated executor 필요
   - 3.11 Internal Model Translation (I7+I8) — `AlertEnvelope` (domain) → `{"text":"..."}` Slack body (SlackBodyRenderer)
   - 3.12 4xx Permanent Failures table (404 channel not found / 410 token revoked / others)
   - 3.13 Outer retry budget (delivery layer above adapter) — 5 attempts × [1s, 5s, 30s, 2m, 10m] ±20% jitter, retry-poll-interval 5s, separate from Resilience4j `slack` retry
4. **Kafka Cluster** — direction (consume 6 source topics + publish notification.delivered audit), config, failure modes (sibling inbound 답습).
5. **PostgreSQL** — owned `notification_db`, HikariCP, Flyway, failure modes.
6. **Secret Manager** — v1 env-var injection primary (`SLACK_WEBHOOK_URL_*`), v1 prod AWS SM (or equivalent), rotation 정책.
7. **Aggregated Resilience Policy** — vendor × (timeout / CB / retry / idempotency / bulkhead / DLQ) 표.
8. **Observability** — Slack-specific metrics (delivery.attempts / delivery.duration / channel.send.failed / circuit state).
9. **Test Suite** (I10) — WireMock Slack mocks + Testcontainers Kafka/Postgres + integration test matrix.
10. **Per-Vendor Runbook Pointers** — Slack outage runbook 위치.
11. **Not In v1** — email/SMS/push placeholders (v2), bidirectional Slack (slash command / button callback), blocks templating, per-user preference UI, Slack vendor swap.
12. **References** — application.yml + adapter source + architecture.md + notification-subscriptions.md + sibling external-integrations files.

예상 크기: ~250-350 line.

### B. spec 이외 정합 확인 (file authoring 0)

`notification-service/architecture.md` 의 L155-176 (이미 외부 vendor + Slack 분량 적게 documented) 와 본 file 의 byte-identical 정합 확인. spec drift 0 (architecture.md 가 high-level / external-integrations.md 가 wire-level 디테일).

### C. portfolio gap surface (file authoring 0)

INDEX.md done entry 본문 또는 task body 에 잔존 3 service gap (master / admin / gateway external-integrations.md 미존재) 을 surface — 본 PR scope 는 notification only.

## Out of Scope

- master / admin / gateway external-integrations.md authoring (모두 zero-state 가능, 별 task 또는 BE-N batch 후보).
- notification-service domain-model.md / idempotency.md / architecture.md 변경 (spec 정합만 확인, file 갱신 0).
- Slack adapter 코드 변경 0 — wire-level spec 이 현재 코드를 reflection 하는 것이며 코드를 spec 에 맞춰 바꾸지 않음.
- 신규 application.yml 설정 / 신규 env var = 0.

---

# Acceptance Criteria

- [ ] `projects/wms-platform/specs/services/notification-service/external-integrations.md` 신규 file 존재, 약 250-350 line, 12 section 구조.
- [ ] Slack section (§ 3) 의 12-13 sub-section 모두 작성 (Endpoint / Auth / Anti-replay / Failure Modes / Adapter Layout / Timeouts I1 / CB I2 / Retry I3 / Idempotency I4 / Bulkhead I9 / I7-I8 Translation / 4xx Permanent / Outer retry budget).
- [ ] Slack adapter spec 값 byte-identical 검증:
  - timeout = 3s connect / 5s read
  - CB = TIME_BASED / 10 / 5 / 50% / 10s
  - Retry = 3 max / 500ms / exp×2.0 / ignoreExceptions=ChannelPermanentFailureException
  - Outer delivery budget = 5 attempts × [1s, 5s, 30s, 2m, 10m]
- [ ] 4 vendor catalog (Slack + Kafka + PostgreSQL + Secret Manager) 모두 행 1개씩 포함.
- [ ] References section 의 모든 cross-link 실재 (~10개: application.yml + adapter source + architecture.md + idempotency.md + notification-subscriptions.md + sibling 3 file + rules/traits/integration-heavy.md + platform/error-handling.md).
- [ ] HARDSTOP-03 PASS — 신규 file 이 wms-specific (project-internal).
- [ ] grep `(Open Item` projects/wms-platform/specs/services/notification-service/ → 0 (regression 0 / 신규 마커 신설 X).
- [ ] production code 변경 0 (git diff `--stat` 으로 markdown 만).
- [ ] CI = path-filter (TASK-MONO-074/075) → markdown-only wms-spec → ~1 PASS + 15 SKIP 예상.

---

# Related Specs

> **Before reading Related Specs**: Follow `platform/entrypoint.md` Step 0 — read `PROJECT.md` (`domain: wms`, `traits: [transactional, integration-heavy]`), then load `rules/common.md` + `rules/domains/wms.md` + `rules/traits/transactional.md` + `rules/traits/integration-heavy.md`. Service Type 은 `notification-service/architecture.md § Identity` 의 `event-consumer` (pure v1).

- `rules/traits/integration-heavy.md` — Required Artifacts (1) + I1-I10
- `projects/wms-platform/specs/services/notification-service/architecture.md` — § Identity, § Dependencies, § Routing Rules (L155-176)
- `projects/wms-platform/specs/services/notification-service/domain-model.md` — AlertEnvelope / ChannelType / NotificationDelivery
- `projects/wms-platform/specs/services/notification-service/idempotency.md` — Outbound dedupe (deliveryId)
- `projects/wms-platform/specs/services/notification-service/runbooks/dlt-replay.md` — runbook ref
- `projects/wms-platform/specs/contracts/events/notification-subscriptions.md` — 6 source topic catalog
- `projects/wms-platform/specs/services/inbound-service/external-integrations.md` — sibling reference (Kafka + Postgres + Secret Manager pattern)
- `projects/wms-platform/specs/services/outbound-service/external-integrations.md` — sibling reference (TMS marquee = vendor-side full I1-I10 pattern)
- `projects/wms-platform/specs/services/inventory-service/external-integrations.md` — sibling reference (zero-state, BE-156)
- `projects/wms-platform/apps/notification-service/src/main/resources/application.yml` — Resilience4j + Slack channels config
- `projects/wms-platform/apps/notification-service/src/main/java/com/wms/notification/adapter/outbound/slack/SlackChannelAdapter.java` — adapter source
- `platform/error-handling.md` — `DELIVERY_RETRY_EXHAUSTED` (422), `CHANNEL_NOT_CONFIGURED` 등 등록 여부 확인

# Related Skills

- `.claude/skills/INDEX.md`

---

# Related Contracts

- `projects/wms-platform/specs/contracts/events/notification-subscriptions.md` — 6 source topic
- `projects/wms-platform/specs/contracts/events/notification-events.md` — `notification.delivered.v1` audit only

---

# Target Service

- `notification-service`

---

# Edge Cases

1. **architecture.md L155-176 (vendor catalog summary table + 6-row routing rules table)** 가 본 file 과 byte-identical 정합 필요. drift 발생 시 architecture.md 가 high-level summary, external-integrations.md 가 wire-level detail 분담 명시. 본 task 는 architecture.md 변경 0.
2. **Slack vendor-side idempotency 부재** — vendor 가 dedupe header 미지원. service-side deliveryId 기반 idempotency 가 유일한 방어. v2 multi-tenant deploy 시 risk 증가 가능 — § 3.9 에 명시.
3. **2 alias env-var injection** (`wms-alerts` + `wms-shipping`) — application.yml 의 default = empty → ChannelNotConfiguredException → fail-closed (per `SlackChannelAdapter`). § 5 Secret Manager 와 cross-link.
4. **JDK HttpClient (not RestClient)** — sibling outbound TMS 는 Spring `RestClient`. notification 은 JDK `java.net.http.HttpClient`. 이유: notification 의 vendor body 가 단순 (`{"text":"..."}`) → Spring 의존 줄임 + virtual thread 직접 호환. 답습 안함 — adapter 의도 보존.
5. **Bulkhead I9 = N/A v1** — JDK HttpClient default executor (cached thread pool) 으로 충분. v2 multi-vendor 시 dedicated executor / SEMAPHORE bulkhead 필요. § 3.10 에 명시 (outbound TMS 는 SEMAPHORE 10 + Apache HttpClient5 connection pool 10 = dedicated).

---

# Failure Scenarios

1. **byte-identical Resilience4j config mismatch** — application.yml 의 slack circuit / retry 값과 spec 본문 값 mismatch. 작성 후 즉시 grep 으로 검증.
2. **adapter source 의 timeout 값 mismatch** — `SlackChannelAdapter.java` L50-51 의 `CONNECT_TIMEOUT = Duration.ofSeconds(3)` + `READ_TIMEOUT = Duration.ofSeconds(5)` 와 spec 의 3s/5s mismatch. SQL constraint 처럼 inline 검증.
3. **CRLF vs LF on Windows** — BE-156/157 답습 (LF 작성). hook 검증.
4. **path-filter notification-spec markdown-only PASS** — 정상 (BE-156/157 답습).
5. **architecture.md vs external-integrations.md drift** — high-level vs wire-level 분담 작성 시 wording 일치도 검증. § 11 Not In v1 (architecture.md 와 동일 항목: email/SMS/push, slash commands, blocks templating, per-user preference, vendor swap) — 일치 확인.

---

# Validation Plan

- `ls projects/wms-platform/specs/services/notification-service/external-integrations.md` → file 존재 확인.
- `wc -l` → 250-350 line 범위 확인.
- adapter source 값 byte-identical 검증:
  - `grep "CONNECT_TIMEOUT\|READ_TIMEOUT" SlackChannelAdapter.java` ↔ spec § 3.6
  - `grep "slidingWindowType\|failureRateThreshold\|waitDurationInOpenState" application.yml` ↔ spec § 3.7
  - `grep "maxAttempts\|waitDuration\|exponentialBackoffMultiplier\|ignoreExceptions" application.yml` ↔ spec § 3.8
  - `grep "backoff-seconds\|max-attempts" application.yml` ↔ spec § 3.13
- 모든 cross-reference (~10개) `ls` 으로 실재 확인.
- `grep -rn "(Open Item" projects/wms-platform/specs/services/notification-service/` → 0 (regression 0).
- CI = path-filter (TASK-MONO-074/075) → ~1 PASS + 15 SKIP 예상.

---

# Implementation Notes

- BE-141~157 single-PR closure 답습 — ready → done.
- D4 OVERRIDE: ADR-MONO-003a § D1.1 (project-internal spec backfill).
- **portfolio-wide gap 4 service 중 #1 closure** — 잔존 3 service (master/admin/gateway) 모두 zero-state 가능, 별 task 또는 batch 후보.
- 분석=Opus 4.7 / 구현 권장=Opus 4.7 (mid-scale single-vendor wire-level spec authoring + byte-identical adapter source 정합 + sibling pattern 답습).
