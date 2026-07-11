# Task ID

TASK-MONO-353

# Title

`bitnami/kafka:3.7` 레지스트리 삭제로 깨진 scm/erp/fan compose 를 `apache/kafka:3.7.0` 으로 이관 + 이미지 실재성 가드 추가

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- bug

---

# Goal

**`bitnami/kafka:3.7` 이 Docker Hub 에서 삭제됐다.** 태그 조회가 404 이고, `bitnami/kafka`
레포지토리의 태그 목록 자체가 비어 있다. 이를 참조하는 compose 는 기동 시점에
`failed to resolve reference "docker.io/bitnami/kafka:3.7": not found` 로 즉사한다.

| Kafka 이미지 | 프로젝트 |
|---|---|
| `apache/kafka:3.7.0` ✅ | iam, wms, ecommerce |
| **`bitnami/kafka:3.7` ❌** | **scm** (`docker-compose.yml:135`), **erp** (`:378`), **fan** (`:307`) |

지금 누구든 scm/erp/fan 을 `docker compose up` 하면 실패한다. 통합 데모
(`infra/demo/demo-up.sh full`)는 iam→wms→**scm** 순서에서 죽어 **33/43 컨테이너**로 멈춘다
— scm·finance·erp·ecommerce·fan·console 은 **시작조차 못 한다**.

## 왜 지금까지 아무도 몰랐는가 (이 task 의 진짜 교훈)

nightly 의 scm/fan/erp E2E 는 **전부 Testcontainers 기반**이고 자체 Kafka 이미지를 쓴다.
**어떤 CI 잡도 `projects/{scm,erp,fan}/docker-compose.yml` 을 실행하지 않는다.** 그 파일들은
로컬 개발과 데모에서만 쓰이는 **CI 사각지대**였다.

게다가 이 결함은 **우리 커밋과 무관하게 외부에서 발생**했다 — 외부 레지스트리의 이미지
삭제는 어떤 diff-기반 검사로도 잡히지 않는다. PR CI 는 계속 초록이었고, 2026-07-11 온디맨드
데모를 **AWS 에서 실제로 부팅해서야** 처음 드러났다.

따라서 이관만으로는 부족하다. **재발 가드가 이 task 의 절반이다.**

---

# Scope

## In Scope

- `projects/scm-platform/docker-compose.yml` · `projects/erp-platform/docker-compose.yml`
  · `projects/fan-platform/docker-compose.yml` 의 `kafka` 서비스 → `apache/kafka:3.7.0`.
- `infra/demo/verify-demo-wrapper.sh` 에 **검사 (h) 이미지 실재성** 추가.

## Out of Scope

- Kafka 버전 업그레이드(3.7 유지 — 이관이지 업그레이드가 아니다).
- iam/wms/ecommerce (이미 apache/kafka).
- `provectuslabs/kafka-ui` 등 다른 서드파티 이미지(현재 살아 있음; 가드 (h)가 앞으로 감시).

---

# 이미지 두 개는 drop-in 호환이 아니다

`image:` 한 줄 교체로는 브로커가 뜨지 않거나, **뜨더라도 조용히 고장 난다.**

| | bitnami | apache |
|---|---|---|
| env 접두사 | `KAFKA_CFG_*` + `ALLOW_PLAINTEXT_LISTENER` | raw `KAFKA_*` |
| CLUSTER_ID | 자동 생성 | **명시 필수** |
| 데이터 디렉터리 | `/bitnami/kafka` | `/var/lib/kafka/data` |
| CLI 경로 | PATH 에 있음 | `/opt/kafka/bin/` (healthcheck 절대경로 필요) |
| `offsets.topic.replication.factor` 기본값 | **1** | **3** |

**마지막 행이 가장 위험하다.** 단일 브로커에서 replication factor 3 을 요구하면 브로커는
`healthy` 로 뜨지만 **모든 컨슈머 그룹이 offsets 토픽 초기화에 실패**한다. 헬스체크는 통과하고
이벤트 소비만 조용히 죽는다. → `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1` +
`KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1` + `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1`
이 **load-bearing** 이다(iam/wms/ecommerce 의 기존 블록에 이미 들어 있다).

---

# Acceptance Criteria

- [ ] `grep -r "bitnami" projects/*/docker-compose.yml` → **0건**.
- [ ] scm/erp/fan 의 kafka 가 `apache/kafka:3.7.0` + KRaft + 단일-브로커 replication 오버라이드.
- [ ] **가드 (h)**: `verify-demo-wrapper.sh` 가 compose 참조 이미지의 레지스트리 실재를 확인.
      `build:` 를 가진 서비스(= 소스에서 굽는 `…:local` 태그)는 **제외**한다 — 포함하면
      30여 건이 전부 "확인 실패"로 잡혀 skip 목록을 채우고 **진짜 레이트리밋 skip 을 가린다**.
- [ ] **가드 (h) 는 레이트리밋과 삭제를 구분한다.** 확정적 부재(`manifest unknown` /
      `not found` / `repository does not exist`)에만 FAIL. 나머지(429·네트워크·인증)는 skip 하되
      **건수와 목록을 반드시 출력**한다(조용한 truncation 금지).
- [ ] **가드 (h) mutation-check**: 죽은 이미지를 되돌리면 실제로 FAIL 해야 한다.
      통과만으로는 가드가 무는지 알 수 없다.
- [ ] **실기동 증명**: 브로커가 healthy 가 되는 것으로 **불충분**하다. 컨슈머 그룹을 실제로
      만들어 offsets 토픽이 초기화되는지까지 확인한다(위 replication-factor 함정을 겨냥).
- [ ] CI GREEN.

---

# Related Specs

- `tasks/done/TASK-MONO-346-demo-env-completeness-guard.md` (검사 (g) — 같은 스크립트)
- `tasks/done/TASK-MONO-342/344` (데모 래퍼 커버리지 가드)
- Memory `project_bitnami_kafka_image_deleted`

# Related Contracts

None — 인프라 이미지 교체, API/이벤트 계약 무변경.

# Target Service

N/A — 3개 프로젝트의 로컬 개발/데모 compose.

# Architecture

ADR 불필요. Kafka 는 이미 KRaft 단일 브로커였고 토폴로지 무변경(단일 PLAINTEXT 리스너 유지).

---

# Edge Cases

- **kafka-data 볼륨**: 기존 로컬 볼륨은 bitnami 레이아웃(`/bitnami/kafka`)이다. 마운트 지점이
  바뀌므로 기존 볼륨은 apache 에게 빈 디렉터리로 보인다 → KRaft 자동 포맷. 데모/개발 데이터는
  휘발성이라 무해하나, 로컬에 남은 옛 볼륨이 있으면 `docker volume rm` 권장.
- **Docker Hub 레이트리밋**: 익명 100 pull/6h/IP, GH 러너는 IP 공유. 가드 (h)가 이를
  실패로 처리하면 flaky 해지고, **flaky 한 가드는 결국 꺼진다.** skip 으로 분류하되 출력한다.
- `KAFKA_KRAFT_CLUSTER_ID` 는 iam/wms 와 동일한 기본값을 쓴다(프로젝트별 격리된 브로커라 충돌 없음).

# Failure Scenarios

- replication-factor 오버라이드를 빠뜨림 → 브로커 healthy, **컨슈머 그룹만 조용히 실패**.
  헬스체크로는 절대 안 잡힌다. → AC 의 "컨슈머 그룹 실기동" 항목이 이를 겨냥한다.
- healthcheck 를 `kafka-broker-api-versions.sh`(상대경로) 로 두면 apache 이미지에서 `not found`
  → 컨테이너가 영원히 unhealthy → `depends_on: condition: service_healthy` 인 앱들이 전부 대기.

# Test Requirements

- `bash -n infra/demo/verify-demo-wrapper.sh`
- `bash infra/demo/verify-demo-wrapper.sh` (정적 (a)~(e),(g),(h)) PASS
- 가드 (h) mutation-check (죽은 이미지 주입 → FAIL 확인)
- scm kafka 실기동 + 컨슈머 그룹 생성 확인 (32GB 호스트 — 로컬 Docker VM 11.7GiB 는 불가)
- CI GREEN

# Definition of Done

- [ ] 3개 compose 이관 + 가드 (h) 추가
- [ ] mutation-check 로 가드가 무는 것 확인
- [ ] 컨슈머 그룹 실기동 증명
- [ ] CI GREEN
- [ ] `tasks/INDEX.md` done entry

---

# Provenance

2026-07-11, 온디맨드 데모를 AWS 에서 처음 실제 부팅하다가 발견. 스택이 33/43 컨테이너에서
멈췄고 `demo-stack.service` 가 exit 1 이었다. journal 을 따라가니 scm 의 kafka pull 이
`not found` 였다. **로컬(Docker VM 11.7GiB)에서는 `full` 을 띄울 수 없어 원리적으로 볼 수
없었던 결함이고, CI 는 이 compose 들을 아예 실행하지 않아 초록이었다.**

분석=Opus 4.8 / 구현 권장=Opus (이미지 한 줄 교체로 보이지만 env 규약·데이터 경로·CLI 경로·
단일브로커 replication 이 전부 연쇄하고, 그중 하나는 healthy 인 채로 조용히 고장 난다)
