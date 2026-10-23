# Task ID

TASK-MONO-813

# Title

Dead-letter topic suffix is split across the fleet — platform says `<topic>.dlq`, four projects' code sends to `<topic>.DLT`, and the broker pre-creates only `.dlq`

# Status

ready

# Owner

backend / platform

# Task Tags

- code
- docs
- infra

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (fleet-wide naming decision + per-project adaptation in one atomic PR)
> Drafted 2026-10-24 UTC from `projects/wms-platform/tasks/review/TASK-BE-651-*.md` AC-0 ③. Owner decision 2026-10-24 (selection window): «별도 루트 티켓으로» — BE-651 closed its scope at the consumer fix + cause logging; this ticket owns the suffix question. Not started until the owner says so.

# Dependency Markers

- 출처: `TASK-BE-651` AC-0 ③ (wms DLT audit) · 33차 창 관측 (`TASK-MONO-672` § 33rd window — harvest): inventory-service dead-lettered to `wms.outbound.picking.cancelled.v1.DLT`, which did not exist (`UNKNOWN_TOPIC_OR_PARTITION` on first send).
- 관련: `TASK-MONO-706` measured that the demo broker auto-creates a `.DLT` topic on first send and keeps the record — so records are believed not lost on the demo, but nothing pre-creates or monitors them.

# Goal

Make one dead-letter naming rule true everywhere — the platform rule, every consumer's recoverer, the broker's pre-created topics, and whatever reads dead letters — so a failed record lands on a topic that exists, is provisioned the same way as its source, and is where operators look.

# Scope

## In Scope

- **AC-0 measurement** (file:line):
  - ① every consumer's dead-letter destination in every project, i.e. a `DeadLetterPublishingRecoverer` destination resolver or an equivalent. Count measured 2026-10-24 with `git grep` over `:(glob)projects/<p>/apps/*/src/main/**`. It counts files that mention the suffix, so re-measure per consumer.

    | Suffix | Project · files |
    |---|---|
    | `.dlq` | ecommerce 13 · iam 11 · fan 8 |
    | `.DLT` | erp 17 · scm 8 · wms 13 · finance 2 · fan 1 |
    | neither | `libs/*/src/main` |

  - ② the platform rule: `platform/event-driven-policy.md:20,113` · `platform/service-types/event-consumer.md:35` · `platform/glossary.md:55`. All three say `<topic>.dlq`.
  - ③ broker provisioning per project: which `kafka-init` / topic scripts create which suffix. Example: the wms `docker-compose.yml:239` creates `.dlq` for every topic, and no wms consumer uses it.
  - ④ whatever reads dead letters: replay tooling, ops docs, monitoring, and any e2e that asserts a DLT.
  - ⑤ the same «listener cause not logged» gap that BE-651 fixed for wms inventory (`CauseLoggingRetryListener`). The other wms consumers have it too: outbound `KafkaConfig`, inbound / notification `KafkaConsumerConfig`, admin `ProjectionKafkaConsumerConfig`. List it across the fleet and decide whether it rides here.
- **Decision (owner selection window after AC-0)**:
  - (a) change the platform rule to `.DLT`, the Spring Kafka default, and adapt the `.dlq` projects;
  - (b) keep `.dlq` and change the `.DLT` projects;
  - (c) a per-project `## Overrides` entry, which keeps the split but documents it and makes provisioning match.

  If the choice changes a platform rule, it needs an ADR (`platform/architecture-decision-rule.md`).
- Implementation in **one atomic PR** per CLAUDE.md § Cross-Project Changes.

## Out of Scope

- Replaying the records already sitting in dead-letter topics on the demo.

# Acceptance Criteria

- [x] **AC-0** — ①~⑤ above, recorded with file:line. → **Closed 2026-10-24 UTC** (origin/main `6f5431876`). The full table with file:line is `docs/adr/ADR-MONO-085-dead-letter-topic-suffix-dlt.md` § M; it is not copied here, so the two cannot drift. Summary:
  - ① 25 consuming services · 116 `@KafkaListener`. `.dlq` = ecommerce 10 · iam 2 · fan 1 resolvers (50 listeners). `.DLT` = wms 5 resolvers · erp 16 / scm 6 / finance 2 `@RetryableTopic` · scm logistics 1 resolver (65 listeners). **No dead letter at all**: iam account-service (`LoginSucceededConsumer.java:49`, no error-handler bean). `libs/` 0. 🔴 The «Spring default» is two different things: `DeadLetterPublishingRecoverer` → `.DLT`, `@RetryableTopic` → **`-dlt`**. Every `.DLT` `@RetryableTopic` today declares the suffix explicitly.
  - ② As drafted, plus **two skills** that teach `.dlq`: `.claude/skills/messaging/consumer-retry-dlq/SKILL.md:33-36,68-76` (*«not Spring Kafka's default `.DLT`»*) · `.claude/skills/service-types/event-consumer-setup/SKILL.md:71,125`.
  - ③ Pre-creation only in wms (`docker-compose.yml:239` · `scripts/kafka-create-topics.sh:76`) and iam (`docker-compose.yml:244` · `docker-compose.e2e.yml:174`), both `.dlq`, 1 partition, 30-day retention. The other five projects auto-create everything (`KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`). The demo relay (`infra/demo/relay/mm2.properties`) mirrors no dead-letter topic.
  - ④ Replay tooling 0 · code subscribing to a dead-letter topic 0. The one code reader is iam `security_dlq_depth` (`SecurityMetricsConfig.java:42-51`, six `.dlq` topics hard-coded) + its alert (`iam-platform/infra/prometheus/alert-rules.yml:140`). A copy of that alert sits in wms, which has no service emitting the metric. Contracts: `.DLT` 31 · `.dlq` 15. Test files: `.DLT` 17 · `.dlq` 7.
  - ⑤ Cause not logged: wms admin · inbound · notification · outbound, scm logistics, and the five `@RetryableTopic` services (no `@DltHandler`; whether the default handler logs the cause was not measured). → **moved to `TASK-MONO-816`** (owner, selection window 2026-10-24: «별도 티켓으로 분리»).
  - 🆕 **Not in the draft — partitions.** Pre-created dead-letter topics have 1 partition, sources have 3. iam security-service sends to the **same partition number** as the source (`KafkaConsumerConfig.java:111`), so records from partitions 1 and 2 aim at partitions that do not exist. ⚪ This is a static reading; the failure was not reproduced. ADR-MONO-085 D3 covers it under every option.
- [ ] **AC-1** — owner decision (+ ADR if a platform rule changes).
  - 2026-10-24 UTC: the selection window offered (a)/(b)/(c), and the owner chose «(a) .DLT 로 통일 (Recommended)». 🔴 **That is not the ADR ACCEPT.** The question named no ADR, because none existed yet, and the chosen label carried the agent's «Recommended» (`platform/architecture-decision-rule.md` § The ACCEPTED Gate, shapes ② and ③). It caused `ADR-MONO-085` to be written as **PROPOSED**.
  - **Open:** the exact form `ADR-MONO-085 ACCEPTED — <A|B|C>`. Until it arrives this task stays in `ready/` and AC-2 does not start (HARDSTOP-09).
- [ ] **AC-2** — implementation: rule, recoverers, provisioning, and readers all agree, plus a guard that fails when a consumer's dead-letter suffix and the pre-created topics disagree. The guard must bite: show it red on today's tree.
- [ ] **AC-3** — after the next bake: one deliberately failed record lands on a pre-created dead-letter topic on the demo broker.

# Related Specs

- `platform/event-driven-policy.md` · `platform/service-types/event-consumer.md` · `platform/glossary.md`

# Related Contracts

- Each project's event contracts that name a DLT/DLQ topic (AC-0 ① lists them).

# Edge Cases

- Renaming a dead-letter topic strands any record already on the old one. AC-0 ④ must say whether anything reads them before the rename.
- On the demo, auto-created topics get the broker's default partition count, not the source topic's.

# Failure Scenarios

1. Only the platform text changes and the code keeps both suffixes → the split stays. AC-2's guard prevents this.
2. Auto-create is turned off on some broker later → every `.DLT` send fails and the record is lost. Today's split hides this.
