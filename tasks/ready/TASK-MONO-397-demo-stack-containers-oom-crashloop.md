# Task ID

TASK-MONO-397

# Title

데모 스택의 컨테이너들이 **자기 메모리 리밋에서 OOM-킬 되며 크래시루프한다** — `ecommerce-kafka` 는 **512 MiB 짜리 Kafka 브로커**다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- reliability

---

# Goal

`TASK-MONO-389` 가 데모의 정문을 열고 방문자 경로를 끝까지 증명하는 과정에서, **스택 자체가 안정적이지 않다**는 것이 드러났다. 부팅은 완주하고 console 은 뜨지만, **여러 컨테이너가 계속 죽었다 살아난다.**

이건 389 의 결함(정문)이 아니라 **용량/리밋의 결함**이고, 별도로 다룬다.

---

# 실측 (2026-07-14, 데모 호스트 `m6i.2xlarge` / 32 GB / profile=full)

부팅 완주 직후(`[demo] up complete`) 재시작 횟수:

| 컨테이너 | RestartCount | 리밋 |
|---|---|---|
| `ecommerce-kafka` | **14** | **536,870,912 = 512 MiB** |
| `erp-platform-masterdata` | 14 | (미확인) |
| `erp-platform-approval` | 14 | (미확인) |
| `erp-platform-notification` | 12 | (미확인) |
| `finance-platform-ledger` | 5 | (미확인) |

커널 로그가 원인을 못박는다 — **호스트 OOM 이 아니라 cgroup(컨테이너별 리밋) OOM 이다**:

```
oom-kill:constraint=CONSTRAINT_MEMCG, ... task=java
Memory cgroup out of memory: Killed process (java) total-vm:3859948kB, anon-rss:481028kB
```

`anon-rss ≈ 470 MB` 에서 죽었다 — **512 MiB 리밋의 벽이다.** 같은 시각 호스트는 **7.9 GB 여유**였다. 즉 **호스트를 키워도 안 고쳐진다.** 리밋이 틀린 것이다.

> **512 MiB 짜리 Kafka 브로커는 Kafka 브로커가 아니다.**

## 왜 지금까지 안 보였나

- **CI 는 이 compose 를 안 돌린다** — scm/erp/fan e2e 는 Testcontainers 기반이다(`TASK-MONO-353` 이 `bitnami/kafka:3.7` 삭제를 발견했을 때와 **똑같은 사각지대**).
- 로컬 `demo-up.sh` 는 대개 `demo-core`(4개 프로젝트)로 돌린다 — 메모리 압박이 다르다.
- **크래시루프는 초록으로 보인다.** `docker ps` 는 `Up 16 seconds` 를 보여주고, 재시작 정책이 다시 띄우고, 최종적으로 healthcheck 를 통과하기도 한다. **RestartCount 를 세지 않으면 아무 일도 없어 보인다.**

---

# 이 결함이 다른 결함의 원인일 수 있다 — **인과를 확정하지 않는다**

`TASK-MONO-389` 는 다음을 관측했다: 웜업 도중 정지당한 인스턴스가 **이후 모든 부팅에서** kafka 기동에 실패했다.

```
FATAL: The log dir /tmp/kafka-logs/account.status.changed-0 does not have a topic ID,
       which is not allowed when running in KRaft mode.
```

389 는 이를 **인스턴스 정지가 웜업을 잘라 로그 디렉터리를 반쯤 쓴 채 남긴 것**으로 읽었고, 그 근거로 처녀 인스턴스에서 kafka 가 `healthy` 가 된 것을 들었다.

**그러나 OOM-킬 역시 같은 손상을 만들 수 있다** — 파티션 디렉터리를 만든 뒤 메타데이터를 커밋하기 전에 JVM 이 죽으면 똑같이 topic ID 없는 log dir 이 남는다. 그리고 그 OOM-킬은 **처녀 인스턴스에서도 14회 일어났다.**

⇒ **둘 중 무엇이 그 손상을 만들었는지 389 는 증명하지 않았고, 이 티켓도 아직 증명하지 않는다.** 리밋을 고친 뒤 **재현을 시도해서** 가른다. (389 의 유휴가드 수정은 그 자체로 독립적으로 증명됐다 — 배포된 Lambda 가 `idle_sec = 342`(= 가동 초)를 반환한다. 그 명제는 이 티켓과 무관하게 참이다.)

---

# Scope

## In Scope

- 각 프로젝트 compose 의 **메모리 리밋 실측 및 정정** — 최소한 kafka/JVM 서비스. 리밋의 **출처**를 찾을 것(`docker-compose.yml` 의 `mem_limit`/`deploy.resources` 인지, `.env` 인지, 상속인지).
- **profile=full 의 총 메모리 예산**과 `m6i.2xlarge`(32 GB) 사이의 실제 여유 재측정. `TASK-MONO-366` 이 *"실제 여유 5.5 GB"* 로 정정한 값이 여전히 유효한지.
- **가드**: 크래시루프를 초록으로 보고하지 않게 한다. `verify-demo-wrapper.sh --live` 가 `up complete` 후 **RestartCount > 0 인 컨테이너를 FAIL** 로 잡는다(임계값은 실측 후 결정 — 0 이 맞는지, 재시도가 정상인 컨테이너가 있는지 먼저 셀 것).

## Out of Scope

- 인스턴스 타입 상향 — **이건 cgroup OOM 이다. 호스트를 키워도 안 고쳐진다.** 리밋을 고친 뒤에도 호스트가 모자라면 그때 별도로 판단한다.
- `wms-notification-service` 의 `unhealthy` — 재시작이 아니라 헬스체크 실패다. 별개 증상이므로 섞지 않는다(관측: 부팅 후 8분째 계속 `unhealthy`).

---

# Acceptance Criteria

- [ ] **AC-1 — 리밋의 출처를 특정한다.** `ecommerce-kafka` 의 512 MiB 가 **어느 파일의 어느 줄**에서 오는지. (추측 금지 — `docker inspect` 로 확인된 값은 `536870912` 다.)
- [ ] **AC-2 — 실측으로 정정한다.** 각 서비스가 실제로 쓰는 RSS 를 재고, 리밋을 그 위로 올린다. **"넉넉히 잡았다" 가 아니라 잰 값이어야 한다.**
- [ ] **AC-3 — `profile=full` 부팅 후 RestartCount 가 전부 0.** 데모 호스트에서 실측한다.
- [ ] **AC-4 — 인과 판정.** 리밋을 고친 뒤 인스턴스를 웜업 도중 강제 정지시켜 본다. kafka 가 그래도 살아나면 → 389 의 읽기가 맞다. 여전히 죽으면 → **정지 자체가 손상 원인이고, `demo-boot.sh` 가 손상된 kafka 볼륨을 감지·복구해야 한다**(별도 티켓).
- [ ] **AC-5 — 가드가 크래시루프를 잡는다** + mutation(재시작을 인위적으로 만들어 RED 확인).
- [ ] CI GREEN.

---

# Edge Cases

- **리밋을 없애면(무제한) 호스트 OOM 으로 옮겨간다** — 32 GB 에 96개 컨테이너다. 리밋은 **필요하고**, 다만 틀린 값이었다. 무제한은 답이 아니다.
- **`docker ps` 는 크래시루프를 숨긴다.** `Up 16 seconds` 는 건강해 보인다. 반드시 `RestartCount` 를 봐라.
- **kafka 는 `OOMKilled=false, ExitCode=0` 으로 보고됐다** — cgroup 이 JVM 스레드를 죽였고 컨테이너 자신은 깨끗이 종료했기 때문이다. **`OOMKilled` 플래그만 보면 OOM 을 놓친다.** 커널 `dmesg` 의 `CONSTRAINT_MEMCG` 가 권위다.

# Failure Scenarios

- **F1 — 호스트 타입만 올리고 끝낸다** → cgroup 리밋은 그대로이므로 **아무것도 안 고쳐진다.** 비용만 오른다.
- **F2 — 리밋을 눈대중으로 올린다** → 다시 터지거나 호스트를 굶긴다. AC-2 가 실측을 요구하는 이유다.
- **F3 — 가드를 `RestartCount > 3` 같은 임계로 무디게 잡는다** → 크래시루프가 "정상 범위" 로 통과한다. 임계는 **실측 후** 정한다.

# Test Requirements

- 실기동: 데모 호스트에서 `profile=full` 부팅 후 `RestartCount` 전수.
- 가드: `--live` 모드 + mutation.

# Definition of Done

- [ ] AC-1~5 + CI GREEN
- [ ] `tasks/INDEX.md` done entry

---

# Provenance

발굴 2026-07-14 — `TASK-MONO-389`(데모 정문) 의 **AC-2 를 실제로 걸어가다** 드러났다. 정문을 열고 방문자 경로를 끝까지 따라가지 않았다면, 스택이 크래시루프하는 채로 **"부팅 완주, console healthy"** 라는 초록 보고만 남았을 것이다.

**이 티켓의 뿌리는 `TASK-MONO-353` 과 같다**: **CI 가 이 compose 들을 한 번도 실행하지 않는다.** 353 은 그 사각지대에서 *삭제된 이미지*를 발견했고, 이 티켓은 같은 사각지대에서 *틀린 메모리 리밋*을 발견했다. 가드 (h)(이미지 실재)는 그 자리에 붙었지만, **"컨테이너가 살아 있는가" 를 묻는 가드는 아직 없다.**

분석=Opus 4.8 / 구현 권장=**Opus** (리밋 실측 + 인과 판정(AC-4)이 있고, 그 판정이 다른 티켓의 서술을 정정할 수 있다. 눈대중으로 리밋을 올리는 것은 이 티켓을 푸는 게 아니라 덮는 것이다.)
