# ADR-MONO-085 — 데드레터 토픽 이름을 하나로 정한다 (갈래 A·B·C, 추천 A = `<topic>.DLT`)

**Status:** PROPOSED
**Date:** 2026-10-24
**주관 티켓:** `TASK-MONO-813`
**Decision driver:** 33차 창 관측 — wms inventory-service 가 `wms.outbound.picking.cancelled.v1.DLT` 로 데드레터를 보냈는데 그 토픽이 없었다(첫 전송에 `UNKNOWN_TOPIC_OR_PARTITION`). `TASK-BE-651` AC-0 ③ 이 그것을 함대 전체의 접미사 분열로 넓혔고, 소유자가 2026-10-24 선택창에서 «별도 루트 티켓으로» 를 골라 `TASK-MONO-813` 이 기안됐다.
**Supersedes:** 없음. 이 ADR 이 ACCEPT 되면 `platform/event-driven-policy.md` § Broker · § DLQ Policy, `platform/service-types/event-consumer.md` § Retry and DLQ, `platform/glossary.md` «DLQ» 행의 이름 규칙을 바꾼다.
**Related:** [`ADR-MONO-005`](ADR-MONO-005-saga-timeout-escalation-dead-letter-policy.md) (데드레터 *범주*를 정했고 이름은 `event-driven-policy.md` 에 맡겼다 — 이 ADR 은 그 맡겨진 이름을 정한다) · [`ADR-MONO-062`](ADR-MONO-062-cross-project-event-delivery.md) (데모 릴레이 — 데드레터 토픽은 중계하지 않는다) · `TASK-BE-651` · `TASK-MONO-706`(데모 브로커가 `.DLT` 를 처음 보낼 때 자동 생성하고 레코드를 보존함을 실측) · `TASK-MONO-816`(이 ADR 이 다루지 않는 원인 로깅 · 데드레터 부재)

> 🟡 **PROPOSED** — 소유자 ACCEPT 는 정확형 **`ADR-MONO-085 ACCEPTED — A`**(또는 `— B` · `— C`)로만 넘어간다. 이름 · `ACCEPTED` · 갈래 글자 셋이 한 메시지에 있어야 한다(`platform/architecture-decision-rule.md` § The ACCEPTED Gate).
> 🔴 **2026-10-24 선택창의 «(a) .DLT 로 통일 (Recommended)» 는 이 ADR 의 ACCEPT 가 아니다.** ADR 을 이름으로 지목하지 않았고, 고른 선택지가 **에이전트 추천**이었다(§ The ACCEPTED Gate 표의 ②·③ 모양). 그 선택은 이 ADR 을 *쓰게* 했을 뿐이다 — § History.
> 이 문서는 구현을 인가하지 않는다. 구현은 ACCEPT 뒤 `TASK-MONO-813` 이 `in-progress/` 로 옮겨 가며 한 원자 PR 로 한다.

---

## Context

### 무엇을 정하나

Kafka 소비자가 재시도를 다 쓴 레코드를 보내는 **데드레터 토픽의 이름** 하나다. 그런데 이름은 네 곳에 동시에 있어야 맞는다 — **규칙**(platform 문서와 스킬) · **보내는 쪽**(각 소비자의 recoverer) · **브로커**(미리 만드는 토픽) · **읽는 쪽**(게이지 · 알림 · 테스트 · 계약). 지금은 넷이 서로 다른 말을 한다.

### § M — 실측 (2026-10-24 UTC, origin/main `6f5431876`)

모집단: `git ls-files 'projects/*/apps/*/src/main/**'` (java · yml · properties) — 컨슈머가 있는 서비스 **25개**, `@KafkaListener` **116개**. `libs/*/src/main` 은 데드레터를 한 곳도 다루지 않는다(0건).

#### ① 보내는 쪽 — 접미사가 둘이고, 셋째(접미사 없음)가 하나 있다

| 접미사 | 서비스 | 방식 · 위치 | 리스너 |
|---|---|---|---|
| `.dlq` | ecommerce 10 (auth · notification · order · payment · product · promotion · search · settlement · shipping · user) | 직접 만든 resolver — 예: `projects/ecommerce-microservices-platform/apps/order-service/src/main/java/com/example/order/infrastructure/config/KafkaConsumerConfig.java:19-24` | 37 |
| `.dlq` | iam auth · security | resolver — `iam-platform/apps/auth-service/.../KafkaConsumerConfig.java:62,79` · `iam-platform/apps/security-service/.../kafka/KafkaConsumerConfig.java:91,111` | 8 |
| `.dlq` | fan notification | resolver — `fan-platform/apps/notification-service/.../KafkaConsumerConfig.java:50-58` | 5 |
| `.DLT` | wms admin · inbound · inventory · notification · outbound | resolver + 속성 `${<svc>.kafka.dlt-suffix:.DLT}` — 예: `wms-platform/apps/inbound-service/.../KafkaConsumerConfig.java:38-42` · `application.yml:130` | 40 |
| `.DLT` | erp notification · read-model | `@RetryableTopic(dltTopicSuffix = ".DLT")` 16곳 — 예: `erp-platform/apps/read-model-service/.../ApprovalEventConsumer.java:56` | 16 |
| `.DLT` | scm inventory-visibility · demand-planning · logistics | `@RetryableTopic` 6곳 + resolver 1곳(`scm-platform/apps/logistics-service/.../KafkaConsumerConfig.java:88-90`) | 7 |
| `.DLT` | finance ledger | `@RetryableTopic` 2곳 — `finance-platform/apps/ledger-service/.../TransactionEventConsumer.java:52,65` | 2 |
| **없음** | iam account-service | `LoginSucceededConsumer.java:49` — error handler 빈이 없어 기본 핸들러가 재시도 후 **버린다** | 1 |

`.dlq` = 3 프로젝트 · 13 지점 · 리스너 50. `.DLT` = 4 프로젝트 · 30 지점 · 리스너 65.

🔴 **«Spring 기본값» 은 하나가 아니다.** `DeadLetterPublishingRecoverer` 의 기본 접미사는 `.DLT` 이지만 `@RetryableTopic` 의 기본 접미사는 **`-dlt`** 다. 지금 `.DLT` 를 쓰는 24개 `@RetryableTopic` 은 전부 `dltTopicSuffix = ".DLT"` 를 **명시**해서 그렇게 된 것이고, 하나라도 그 줄을 빠뜨리면 세 번째 이름이 생긴다.

#### ② 규칙 — 전부 `.dlq`, 그리고 스킬이 그것을 적극적으로 가르친다

| 위치 | 문장 |
|---|---|
| `platform/event-driven-policy.md:20` | `Dead-letter topic per source topic: <topic>.dlq.` |
| `platform/event-driven-policy.md:113` | `DLQ topic naming: <original-topic>.dlq.` |
| `platform/service-types/event-consumer.md:35` | `DLQ topic name: <topic>.dlq` |
| `platform/glossary.md:55` | `Named <topic>.dlq` |
| `.claude/skills/messaging/consumer-retry-dlq/SKILL.md:33-36,68-76` | 예시 코드 + *«DLQ topics use the `.dlq` suffix — **not** Spring Kafka's default `.DLT`»* |
| `.claude/skills/service-types/event-consumer-setup/SKILL.md:71,125` | `<topic>.dlq` |

⇒ 규칙을 따른 프로젝트는 3개, 따르지 않은 프로젝트는 4개이고 리스너 수로는 따르지 않은 쪽이 더 많다.

#### ③ 브로커 — 미리 만드는 곳은 둘, 둘 다 `.dlq`

| 프로젝트 | 데드레터 토픽 생성 | 소비자가 보내는 곳 | 결과 |
|---|---|---|---|
| wms | `docker-compose.yml:239` · `scripts/kafka-create-topics.sh:76` → `<t>.dlq` (파티션 1 · 보존 30일) | `.DLT` | 🔴 미리 만든 `.dlq` 는 **아무도 안 쓰는 고아**, 실제 데드레터는 자동 생성된 `.DLT`(브로커 기본 설정) |
| iam | `docker-compose.yml:244` · `docker-compose.e2e.yml:174` → `<t>.dlq` (파티션 1) | `.dlq` | 일치 |
| ecommerce · erp · fan · finance · scm | 없음 — `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` (각 `docker-compose.yml`) | — | 원본 토픽과 데드레터 토픽이 똑같이 자동 생성 |

데모 릴레이(`infra/demo/relay/mm2.properties:91-115`)의 중계 목록에는 데드레터 토픽이 없다(0건).

#### ④ 읽는 쪽 — 코드로 읽는 것은 하나

- 재처리 도구 **0건** · 데드레터 토픽을 구독하는 코드 **0건**.
- iam `security_dlq_depth` 게이지가 `.dlq` 토픽 6개를 하드코딩해 읽는다 — `iam-platform/apps/security-service/.../config/SecurityMetricsConfig.java:42-51`. 그 알림이 `iam-platform/infra/prometheus/alert-rules.yml:140`. 🔴 `wms-platform/infra/prometheus/alert-rules.yml:140` 과 그 대시보드에도 **같은 알림의 복사본**이 있는데 wms 에는 그 메트릭을 내는 서비스가 없다.
- 계약 문서: `.DLT` 31곳(wms 17 · erp 5 · scm 5 · ecommerce 4) · `.dlq` 15곳(iam 9 · ecommerce 4 · fan 2). ecommerce 는 **두 접미사를 모두** 적는다(자기 소비자 = `.dlq`, wms 가 ecommerce 이벤트를 소비하다 버린 곳 = `.DLT`).
- 테스트 파일: `.DLT` 17 · `.dlq` 7. 운영 문서: `.DLT` 5 · `.dlq` 2.
- `event-driven-policy.md:116` 의 *«DLQ depth > 0 MUST trigger an alert»* 를 만족하는 곳은 iam security-service 하나뿐이다. 그 규칙의 함대 적용은 이 ADR 의 범위가 아니다(§ 이 ADR 이 정하지 않는 것).

#### ⑤ 파티션 — 이름과 별개로 숨은 결함 하나

미리 만든 데드레터 토픽은 **파티션 1개**다(`DLQ_PARTITIONS=1`, wms · iam 공통). 원본은 3개다(`MAIN_PARTITIONS=3`). 그런데 resolver 들이 데드레터의 파티션을 고르는 방식이 셋으로 갈린다:

| 방식 | 서비스 |
|---|---|
| 원본과 **같은 파티션 번호** (`record.partition()`) | ecommerce 10 · fan notification · iam security · (스킬 예시 코드 `consumer-retry-dlq/SKILL.md:36`) |
| `-1` (프로듀서가 키로 고름) | iam auth · scm logistics |
| 같은 번호를 넘기지만 레코드 생성에서 파티션을 **지움** (키로 고름) | wms 5 (`inbound-service/.../KafkaConsumerConfig.java:44-60` 의 `createProducerRecord` 재정의) |
| (`@RetryableTopic`) | erp · scm · finance — Spring 의 retry-topic 기본 |

🔴 **iam security-service** 는 파티션 3개짜리 원본에서 받은 레코드를 파티션 1개짜리 `.dlq` 의 **같은 번호**로 보낸다(`KafkaConsumerConfig.java:111`). 파티션 1·2 에서 온 레코드는 존재하지 않는 파티션을 겨눈다. ⚪ **정적 판독이다** — 실제로 전송이 실패하고 소비가 그 레코드에서 멈추는지는 실행해서 보지 않았다. ecommerce product-service 도 같은 모양**으로 보인다**(추론 — 파티션 수를 재지 않았다): 릴레이가 만든 wms 토픽을 소비하고, `.dlq` 는 자동 생성(브로커 기본 파티션 수)이다.

### 이름을 바꿀 때 남는 것

어느 갈래든 한쪽 프로젝트들의 데드레터 토픽 이름이 바뀐다. 이미 옛 이름 토픽에 쌓인 레코드는 거기 남는다. § M ④ 대로 그것을 코드로 읽는 것은 iam 게이지 하나뿐이므로, 옮기지 않아도 **읽던 것이 끊기는** 곳은 iam 게이지 하나다(바꾸는 쪽이 `.dlq` 일 때). 이미 쌓인 레코드의 재처리는 `TASK-MONO-813` § Out of Scope 다.

---

## Alternatives Considered

### A — 규칙을 `<topic>.DLT` 로 바꾸고 `.dlq` 쪽을 옮긴다 (추천 — 구현자 선호)

- **바꾸는 곳:** platform 3파일 4줄 + 스킬 2개 · resolver 13곳(ecommerce 10 · iam 2 · fan 1) · 미리 만드는 스크립트 4곳(iam 2 · wms 2 — wms 는 소비자가 이미 `.DLT` 라 생성만 바꾼다) · iam 게이지 토픽 6개 · 계약 15곳 · 테스트 7파일.
- **얻는 것:** 리스너 65개(다수)가 이미 맞다 · wms 의 고아 `.dlq` 가 사라지고 33차 창의 증상이 «미리 만든 토픽에 떨어진다» 로 바뀐다 · `DeadLetterPublishingRecoverer` 를 resolver 없이 쓰는 새 코드가 우연히 맞는다.
- **치르는 것:** platform 규칙 변경(이 ADR) · iam 의 데모 브로커에 옛 `.dlq` 토픽이 남는다.

### B — 규칙 `.dlq` 를 지키고 `.DLT` 쪽을 옮긴다

- **바꾸는 곳:** `@RetryableTopic` 24곳(erp 16 · scm 6 · finance 2) · wms 속성 기본값 5곳 + yml 2곳 · scm logistics resolver 1곳 · 계약 31곳 · 테스트 17파일. platform 규칙은 그대로라 원칙적으로 ADR 이 필요 없다.
- **얻는 것:** 규칙 문서 · 스킬 · iam · wms 생성 스크립트가 이미 맞는다.
- **치르는 것:** 변경 면적이 A 의 약 두 배 · 네 프로젝트의 데드레터 토픽 이름이 바뀌어 데모의 기존 `.DLT` 레코드가 옛 이름에 남는다 · `DeadLetterPublishingRecoverer` 를 쓰는 새 코드는 resolver 를 **반드시** 넣어야 맞는다(기본값이 규칙과 어긋난 채로 남는다).

### C — 분열을 유지하고 프로젝트별로 문서화한다

- **바꾸는 곳:** 프로젝트마다 접미사 선언(규칙 문서의 `## Overrides` 또는 각 프로젝트 `specs/contracts/events/README.md`) · wms 생성 스크립트 `.dlq` → `.DLT` · 가드가 **프로젝트별 접미사 표**를 들고 대조.
- **얻는 것:** 코드 변경이 가장 적다(wms 프로비저닝 2곳).
- **치르는 것:** 이 저장소의 프로젝트는 서로의 이벤트를 소비한다(릴레이) — ecommerce 계약이 이미 두 접미사를 다 적어야 하는 것처럼, 프로젝트를 건너는 운영자는 계속 두 이름을 알아야 한다 · 새 소비자를 쓸 때마다 «이 프로젝트는 어느 쪽인가» 를 먼저 찾아야 한다 · 「규칙 하나」 라는 `TASK-MONO-813` Goal 을 충족하지 않는다(Goal 을 바꾸는 갈래다).

### 추천과 그 출처

**추천은 A 이고, 이것은 이 ADR 을 쓴 에이전트의 선호다.** 근거: 변경 면적이 B 의 절반이고, 33차 창의 실제 증상(wms 가 `.DLT` 로 보냈는데 `.dlq` 만 있었다)을 고치는 방향이 «생성 쪽을 소비자에 맞춘다» 이며, 그 방향이 함대의 다수와도 일치한다. B 를 고르면 규칙 문서가 옳았다는 것을 존중하는 대신 실제로 돌아가는 코드의 다수를 옮긴다.

---

## Decision

(A 일 때. B · C 에서 달라지는 것은 각 항목에 적었다.)

- **D1 — 이름.** 데드레터 토픽은 `<원본 토픽>.DLT` 다. 🔴 **기본값에 기대지 않고 모든 지점에서 명시한다** — `@RetryableTopic` 은 `dltTopicSuffix = ".DLT"` 를 반드시 적는다(빠뜨리면 `-dlt`), resolver 는 문자열 `".DLT"` 또는 그것을 기본값으로 하는 속성을 쓴다. (B: `.dlq`, C: 프로젝트별 표.)
- **D2 — 생성은 원본 토픽을 따른다.** 원본 토픽을 미리 만드는 프로젝트(오늘 wms · iam)는 데드레터 토픽도 같은 스크립트에서 같은 목록으로 미리 만든다. 원본을 자동 생성에 맡기는 프로젝트는 데드레터도 자동 생성에 맡긴다. → **OD-1**.
- **D3 — 파티션은 키로 고른다.** resolver 는 데드레터의 파티션 번호를 원본에서 물려받지 않는다(`-1`, 또는 wms 처럼 레코드에서 파티션을 지운다). 미리 만든 데드레터 토픽의 파티션 수가 원본과 달라도 전송이 성립해야 하기 때문이다(§ M ⑤). 갈래와 무관하게 적용된다. ⚪ `@RetryableTopic` 경로(erp · scm · finance)가 데드레터 파티션을 어떻게 고르는지는 재지 않았다 — 그 서비스들은 자동 생성 프로젝트라 오늘은 파티션 수 불일치가 생기지 않지만, 구현이 확인하고 기록한다.
- **D4 — 읽는 쪽을 같은 PR 에서 옮긴다.** iam `SecurityMetricsConfig` 토픽 목록 · 계약 · 테스트 · 운영 문서 · 스킬 2개.
- **D5 — 가드.** 다음 중 하나라도 어긋나면 실패하는 스크립트를 `scripts/` 에 두고 CI 에 배선한다:
  1. 모집단 `projects/*/apps/*/src/main/**` 의 모든 데드레터 접미사 표기(resolver 문자열 · `dltTopicSuffix` · `dlt-suffix` 속성과 그 `@Value` 기본값)가 D1 의 이름이다.
  2. 모든 `@RetryableTopic` 이 `dltTopicSuffix` 를 명시한다.
  3. 데드레터 토픽을 만드는 모든 스크립트(`projects/*/docker-compose*.yml` · `projects/*/scripts/*.sh`)가 D1 의 이름으로 만든다.
  4. platform 규칙 세 파일이 D1 의 이름을 적는다.
  🔴 오늘 트리에서 **빨강**이어야 한다(`TASK-MONO-813` AC-2) — wms 생성 스크립트(`.dlq`)와 ecommerce · iam · fan resolver(`.dlq`)가 걸린다. 그리고 가드가 0건을 보고 초록이 되지 않도록 모집단 하한을 둔다.
- **D6 — 옛 토픽.** 옛 이름의 데드레터 토픽을 지우거나 옮기지 않는다. iam 생성 스크립트에서 `.dlq` 생성을 빼면 새 볼륨에는 생기지 않고, 기존 볼륨에는 남는다. → **OD-2**.

## Outstanding follow-ups

### 열린 결정 (OD) — 답이 없으면 기본값으로 간다

| OD | 질문 | 기본값 (구현자 선택) |
|---|---|---|
| OD-1 | 자동 생성 프로젝트 5개(ecommerce · erp · fan · finance · scm)에도 데드레터 토픽을 미리 만드나? (`TASK-MONO-813` Failure Scenario 2 — 나중에 자동 생성을 끄면 `.DLT` 전송이 전부 실패한다) | **아니오.** 원본 토픽도 자동 생성이므로 D2 가 «같은 방식» 을 이미 만족한다. 자동 생성을 끄는 변경이 오면 그 변경이 원본과 데드레터를 함께 만들어야 한다 — D5 가드 3번이 그때 생성 스크립트를 모집단에 넣는다. |
| OD-2 | iam 기존 볼륨의 옛 `.dlq` 토픽을 정리하는 단계를 두나? | **아니오.** 데모는 굽기마다 신선 볼륨을 산다(`TASK-MONO-672`) — 다음 굽기 뒤 옛 토픽은 없다. |

### 이 ADR 이 정하지 않는 것

- 실패 원인 로깅(wms 4 · scm logistics · `@RetryableTopic` 5 서비스)과 iam account-service 의 데드레터 부재 → `TASK-MONO-816`.
- `event-driven-policy.md:116` 의 «DLQ depth > 0 알림» 을 함대 전체에 적용하는 것 — iam 외에는 없다. 이름을 고친 뒤에야 셀 수 있는 일이라 별도로 다룬다.
- 이미 쌓인 데드레터 레코드의 재처리.

## Consequences

- 함대의 데드레터 토픽 이름이 하나가 되고, 가드가 그것을 지킨다 — 새 소비자가 다른 이름을 쓰면 PR 이 빨강이 된다.
- 바뀌는 쪽(A 면 ecommerce · iam · fan)의 옛 데드레터 토픽에 이미 있던 레코드는 거기 남는다. 그것을 읽는 코드는 iam 게이지 하나이고, 같은 PR 에서 새 이름으로 옮긴다.
- iam security-service 의 파티션 결함 후보(§ M ⑤)가 D3 으로 함께 닫힌다.
- 스킬이 바뀌므로, 이후 에이전트가 만드는 소비자는 새 이름과 새 파티션 규칙을 따른다.

## Verification

- **정적:** D5 가드가 오늘 트리에서 빨강(어긋난 지점을 이름으로 출력), 구현 트리에서 초록. 모집단 하한으로 «0건 초록» 을 막는다.
- **테스트:** 각 프로젝트의 데드레터 단언 테스트(§ M ④ — `.dlq` 7파일)가 새 이름으로 초록.
- **라이브:** 다음 굽기 뒤 데모 브로커에서 일부러 실패시킨 레코드 하나가 **미리 만든** 데드레터 토픽(wms 또는 iam)에 떨어진다 — `TASK-MONO-813` AC-3.

## 단계

| 단계 | 내용 | 티켓 |
|---|---|---|
| P1 | D1~D6 을 한 원자 PR 로 — 규칙 · 스킬 · resolver · 생성 · 읽는 쪽 · 가드(오늘 트리 빨강 증명 포함) | `TASK-MONO-813` AC-2 |
| P2 | 다음 굽기 뒤 데모 브로커에서 일부러 실패시킨 레코드 하나가 미리 만든 데드레터 토픽에 떨어지는 것을 본다 | `TASK-MONO-813` AC-3 |

---

## History

- **2026-10-24 UTC — PROPOSED.** `TASK-MONO-813` AC-0 측정 뒤 소유자에게 선택창으로 (a)/(b)/(c) 를 물었고 «(a) .DLT 로 통일 (Recommended)» 가 왔다. 🔴 **그것을 ACCEPT 로 기록하지 않았다** — 선택창의 질문이 ADR 을 이름으로 지목하지 않았고(이 ADR 은 그때 존재하지 않았다), 선택지 라벨에 에이전트의 «Recommended» 가 붙어 있었다. 게이트가 요구하는 형식을 묻지 않은 것은 **에이전트의 질문 형식** 탓이다. 정확형 `ADR-MONO-085 ACCEPTED — <A|B|C>` 를 따로 받는다.
- 같은 질문의 둘째 항목(원인 로깅 · iam account-service 데드레터 부재를 이 티켓에 싣나)에는 «별도 티켓으로 분리» 가 왔다 → `TASK-MONO-816` 기안. 그것은 ADR 결정이 아니라 티켓 범위 결정이다.
- 측정 중에 § M ⑤(파티션)가 새로 드러났다 — 선택창을 낼 때는 몰랐던 사실이고, D3 으로 넣었다. 어느 갈래든 적용되므로 선택의 전제를 바꾸지 않는다.
