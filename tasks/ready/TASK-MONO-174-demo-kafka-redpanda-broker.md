# Task ID

TASK-MONO-174

# Title

federation-hardening-e2e **demo overlay** — run a real lightweight Kafka broker (redpanda) so the GAP services' transactional-outbox relay actually sends, instead of blocking 60s per send against the DNS-only placeholder. This removes the admin-service connection/thread starvation that intermittently times out the console **감사·보안(audit)** leg in long-running demos.

# Status

ready

# Owner

devops-engineer (demo compose overlay only — infra; NO production code, NO base CI compose change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- deploy

---

# Dependency Markers

- **follows**: TASK-MONO-170 (the demo overlay `docker-compose.federation-e2e.demo.yml` it extends).
- **root cause (diagnosed at runtime)**: the base `docker-compose.federation-e2e.yml` ships Kafka as a **DNS-only placeholder** (`alpine:3.19` + `sleep infinity`, `KAFKA_BOOTSTRAP_SERVERS=127.0.0.1:9999`) because the CI e2e specs never exercise event paths. The GAP outbox schedulers (admin/account/auth — `*OutboxPollingScheduler`) are plain `@Component`s with `@Scheduled` and are **NOT** gated by `outbox.polling.enabled` (unlike scm/erp/ecommerce which carry `@ConditionalOnProperty`), so they cannot be disabled by env. admin-service writes a **meta-audit `admin_actions` row on every audit READ**, so its outbox always has pending rows; the polling scheduler runs `kafkaTemplate.send(...).get()` **inside a transaction** and blocks up to `max.block.ms` (60s) on the unreachable broker, holding a DB connection. Over a long-running demo this starves the HikariCP pool, so the synchronous meta-audit INSERT on each audit query stalls → console `audit_timeout` (5s) → the 감사·보안 card shows the degraded state. (Confirmed via console-web logs: `audit_timeout timeoutMs 5000` + one `audit_ok rows 15`; admin-service logs: `Topic admin.action.performed not present in metadata after 60000 ms`.)
- **NOT a tenant/permission/entitlement issue**: admin-service does not even read `X-Tenant-Id` for audit; the audit query is scoped to the operator's home tenant (the console never sends the `tenantId` query param). acme vs globex made no semantic difference — the symptom was the intermittent admin-service timeout.
- **no dependency on**: any production code, the base CI compose (kept byte-unchanged — the overlay-only invariant from TASK-MONO-170), or any contract/ADR.

# Goal

In the local console demo stack, the GAP outbox relay reaches a real broker → sends succeed (topics auto-created) → the outbox drains → no 60s blocking → admin-service stays responsive → the console 감사·보안 (and the overview audit leg) load reliably. As a bonus the event-driven path is actually exercised end-to-end in the demo.

# Scope

## In Scope

- **`tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.demo.yml`** (overlay only):
  - Add a single-node **redpanda** service (`docker.redpanda.com/redpandadata/redpanda`, `--mode dev-container`, auto-create topics enabled), listening on `redpanda:9092` (internal) with a `rpk cluster health` healthcheck.
  - Override `KAFKA_BOOTSTRAP_SERVERS: redpanda:9092` for **auth-service**, **account-service**, **admin-service** (merged into the base `x-gap-service-env` anchor value `127.0.0.1:9999`), and add `depends_on: redpanda: { condition: service_healthy }` to each.

## Out of Scope

- The base CI compose (`docker-compose.federation-e2e.yml`) — **byte-unchanged** (CI federation gate keeps the placeholder; CI specs do not exercise events). The overlay-only invariant (TASK-MONO-170) is preserved.
- Adding the missing `@ConditionalOnProperty(outbox.polling.enabled)` parity gate to the GAP schedulers — a legitimate separate GAP-internal improvement, but it requires production-code changes + 3 image rebuilds; the redpanda overlay achieves the demo fix infra-only and additionally makes events flow (chosen over the gate per the user).
- Domain producers (wms/scm/finance/erp) — they do not run the outbox scheduler in this stack (they don't extend `OutboxPollingScheduler`) and the demo ops pages are read-only, so they never accumulate outbox rows; no change needed.
- The console audit being scoped to the active (switched) tenant — a separate product gap (deferred backlog), not this infra fix.

# Acceptance Criteria

- [ ] **AC-1** A redpanda broker runs in the demo stack and reports healthy; auth/account/admin-service connect to `redpanda:9092`.
- [ ] **AC-2** After the change, admin-service logs no longer emit `Topic ... not present in metadata after 60000 ms` / `Kafka send failed` for the outbox relay (sends succeed; the outbox drains).
- [ ] **AC-3** The console audit leg returns reliably: `audit_ok` (no `audit_timeout`) across repeated loads and tenant switches; the 감사·보안 page renders rows (or an honest empty/degraded state only when genuinely so), not the timeout-driven degraded state.
- [ ] **AC-4** The base CI compose is byte-unchanged (`git diff` touches only the demo overlay + task docs); CI federation gate behavior unaffected.

# Related Specs

- N/A (test/demo infrastructure). The transactional-outbox relay contract is `libs/java-messaging` (`OutboxPollingScheduler` / `OutboxSchedulerConfig`); this task does not modify it.

# Edge Cases

- **Topic auto-creation**: redpanda `--mode dev-container` + `--set redpanda.auto_create_topics_enabled=true` so the outbox topics (`admin.action.performed`, `tenant.*`, account/auth event topics) are created on first produce. Even if a topic were missing, the broker being *reachable* removes the 60s metadata block (the actual starvation cause).
- **DNS re-resolution**: GAP services already set `-Dnetworkaddress.cache.ttl=0` (base `JAVA_TOOL_OPTIONS`), so `redpanda` resolves once it's up.
- **Resource**: one extra container (~single-node redpanda, smp 1, ~1G). Acceptable for the local demo host.

# Failure Scenarios

- If redpanda fails to pull/start, the 3 services' `depends_on: redpanda healthy` keeps them from starting against a dead broker (fail-fast at compose level rather than a silent half-broken demo). Rollback = drop the overlay redpanda block (base placeholder behavior returns).

# Test Requirements

- Local: bring up redpanda + recreate auth/account/admin-service with the overlay; verify AC-1..AC-3 from container logs (`audit_ok`, no Kafka 60s errors) and a console `/audit` load.
- CI: the PR runs the standard gate; since the base compose is unchanged, the federation gate is unaffected (the overlay is not used by CI).

# Definition of Done

- [ ] redpanda service + 3 service `KAFKA_BOOTSTRAP_SERVERS` overrides + depends_on in the demo overlay.
- [ ] Local stack recreated; AC-1..AC-3 verified from logs + a console audit load.
- [ ] Base CI compose byte-unchanged; diff confined to the demo overlay + task docs.
- [ ] Task md + root `tasks/INDEX.md` updated.
- [ ] Reviewed + merged (impl PR, 3-dim verified; all CI GREEN).

---

분석=Opus 4.8 / 구현=Opus(직접). 사용자 진단 요청("acme는 감사 되고 globex는 안 됨")의 근본 원인 = 테넌트 권한이 아니라 admin-service outbox→데드-Kafka 60s 블록發 커넥션 잠식으로 인한 audit 5s 타임아웃. 사용자 선택 = 실 브로커(redpanda) 추가(인프라 전용, 프로덕션 코드 0). **메타: 데모 Kafka 가 DNS 플레이스홀더라 GAP outbox(특히 audit READ마다 메타-감사 행을 쓰는 admin-service)가 트랜잭션 내 60s `send().get()` 블록 → 풀 고갈. GAP 스케줄러는 scm/erp 와 달리 `outbox.polling.enabled` 게이트가 없어 env 로 못 끔 → 실 브로커가 가장 적은 변경(인프라 전용)으로 해소 + 이벤트 경로까지 실동작.**
