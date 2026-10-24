# Task ID

TASK-BE-657

# Title

wms dead-letter resolvers still return the source partition — a record from source partition 1 or 2 logs «non-existent partition …DLT-N» on every dead-lettering

# Status

ready

# Owner

backend

# Task Tags

- code
- bug
- wms

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (one resolver expression × 5 sibling configs, drop a now-redundant override, one test per service)
> Drafted 2026-10-25 UTC. Owner chose «wms DLT 리졸버 (Recommended)» from the next-candidate list on 2026-10-25 UTC. Facts below read on origin/main `e329f566d`.

# Dependency Markers

- 출처: `TASK-MONO-813` (done) AC-3 — the 34th demo window's poisoned record reached `wms.admin.settings.v1.DLT` p0, with the warning «Destination resolver returned non-existent partition wms.admin.settings.v1.DLT-1, KafkaProducer will determine partition». Recorded there as a finding and in `TASK-MONO-672` § 34th, not ticketed until now.
- 관련: `ADR-MONO-085` D3 («파티션은 키로 고른다 … `-1`, 또는 wms 처럼 레코드에서 파티션을 지운다»). `platform/event-driven-policy.md:114` («resolver partition `-1`»).
- 관련: `TASK-BE-651` (done) — added `CauseLoggingRetryListener` to the same five configs. Not touched here.

# Goal

A wms dead-lettering never names a partition that does not exist: the resolver returns partition `-1` (the producer picks by key), as `platform/event-driven-policy.md` § DLQ Policy says, and the log of a dead-letter incident carries no misleading «non-existent partition» warning.

# Scope

## In Scope

- **The pattern (confirmed in source, all five wms consumers):** each resolver builds `new TopicPartition(record.topic() + dltSuffix, record.partition() < 0 ? 0 : record.partition())`, and each recoverer overrides `createProducerRecord` to send with partition `null`:
  - inventory `config/KafkaConsumerConfig.java:49-50` (override `:53`)
  - outbound `config/KafkaConfig.java:49-50` (override `:53`)
  - inbound `config/KafkaConsumerConfig.java:49-50` (override `:53`)
  - notification `config/KafkaConsumerConfig.java:48-50` (override `:53`)
  - admin `config/ProjectionKafkaConsumerConfig.java:53-55` (override `:58`)
- **Why it is wrong although the record arrives:** pre-created wms dead-letter topics have 1 partition (`scripts/kafka-create-topics.sh:19,76`, `docker-compose.yml:239`) and sources have 3. For a record from partition 1 or 2 the resolver names `…DLT-1` / `…DLT-2`. Spring's `DeadLetterPublishingRecoverer` checks the partition before sending and logs the warning. The override then drops the partition, so the record still lands (measured: p0, 34th window). The cost is a false warning in the middle of a dead-letter incident, an extra `partitionsFor` metadata lookup, and a resolver that disagrees with the policy line.
- **AC-0 (measure before changing):** reproduce the warning in a unit test. Use a mocked `KafkaOperations` whose `partitionsFor` reports 1 partition and a source record from partition 2, then confirm the call path. Confirm with the spring-kafka version in use that a `-1` resolver yields a `ProducerRecord` with a `null` partition, so the override becomes redundant.
- **Fix:** resolver → `new TopicPartition(record.topic() + dltSuffix, -1)` in all five, and remove the `createProducerRecord` overrides (redundant once the partition is `-1`). Keep the key, the value and the headers the recoverer already forwards. Changing behaviour (for example, the override keeping `record.value()` rather than the serialized bytes) is not a goal — AC-0 checks the override's other effects before it is removed.

## Out of Scope

- `scripts/check-dead-letter-suffix.sh` R6's `createProducerRecord` exemption. ADR-MONO-085 D3 permits the wms way («또는 wms 처럼»), so the exemption encodes an allowed alternative, not a defect. Once the five files stop overriding, nothing will use it. Retracting it would narrow the ADR, which is an owner decision. Record it in the review notes; do not change it here.
- Provisioning partition counts (`DLQ_PARTITIONS=1`). The policy says the resolver must cope with fewer partitions; it does not ask the count to match.
- The `@RetryableTopic` path (erp · scm · finance), ADR-MONO-085 D3 ⚪.

# Acceptance Criteria

- [ ] **AC-0** — the reproduction above (red on today's code: the resolver names a partition ≥ 1 on a 1-partition dead-letter topic), and a file:line statement of what each override does besides dropping the partition.
- [ ] **AC-1** — in all five services, a record from source partition 2 is dead-lettered with partition `null`/`-1` and no partition-existence lookup. Red before the change, shown by mutation if the test touches a changed signature.
- [ ] **AC-2** — control: the key, the value and the headers forwarded to the dead-letter record are unchanged (same assertions on today's code and after). The existing `*ErrorLoggingTest` and `MasterLocationDltRoutingIntegrationTest` stay green (the latter in the CI wms Testcontainers lane, confirmed SUCCESS and not SKIPPED).
- [ ] **AC-3** — live, next demo window (owner schedules): a poisoned record from a partition ≠ 0 reaches its `.DLT` with no «non-existent partition» warning. ⚪ is acceptable if the window cannot place a record on partition ≠ 0, with the reason.

# Related Specs

- `platform/event-driven-policy.md` § DLQ Policy
- `docs/adr/ADR-MONO-085-dead-letter-topic-suffix-dlt.md` § M ⑤ · D3
- `projects/wms-platform/specs/services/*/architecture.md` (Service Type of each consumer — read the matching `platform/service-types/` file)

# Related Contracts

- None changed. Dead-letter topic names stay `<topic>.DLT`.

# Edge Cases

- `record.partition()` can be negative for a synthetic record. Today that maps to 0; with `-1` everywhere the special case disappears.
- A dead-letter topic auto-created by the broker (demo relay) gets the broker's default partition count. `-1` is correct for any count.

# Failure Scenarios

1. Only inventory is fixed (where the warning was seen) and the four siblings keep the pattern. AC-1 names all five.
2. The override is removed while the resolver still returns the source partition. The send then targets a partition that does not exist, which is worse than today. AC-1 asserts the partition of the sent record, not just the absence of a warning.
3. Removing the override changes what is forwarded (value bytes vs object, headers). AC-2 checks this.
