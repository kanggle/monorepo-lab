# Task ID

TASK-MONO-526

# Title

5개 프로젝트의 Kafka 볼륨이 **Kafka 가 쓰지 않는 경로**에 마운트돼 있다 — 재기동마다 토픽·오프셋·DLT 가 전부 사라진다

# Status

ready

# Owner

monorepo

# Task Tags

- bug
- infra
- cross-project

---

# 배경 — `TASK-ERP-BE-043` 의 AC-0 이 **숫자를 물려받길 거부해서** 드러났다

BE-043 의 AC-0 은 *"DLT end-offset 을 **다시** 잰다"* 를 요구했다. 티켓 본문이 적어 둔 값은
`delegated` DLT 4 · `submitted` DLT 4 · `delegation.revoked` DLT 2 였다. 재측정 결과:

```
erp.approval.*  (본 토픽 · retry-0/1 · .DLT 전부)  ⇒  end-offset **전 토픽 0**
```

물려받았다면 "우리가 고쳐서 DLT 가 0 이 됐다" 로 읽었을 것이다. **틀린 결론이었을 것이다** —
0 인 이유는 수정이 아니라 **브로커가 이전 상태를 하나도 갖고 있지 않아서**다.

## 실측 — 볼륨은 비어 있고, 데이터는 컨테이너 `/tmp` 에 있다

```
compose 선언 : kafka-data:/var/lib/kafka/data
컨테이너 실측 : /var/lib/kafka/data          → 비어 있음 (ls -A 무출력)
              /tmp/kafka-logs/meta.properties               ← 존재
              /tmp/kafka-logs/erp.approval.delegated.v1-0/  ← 세그먼트가 여기 있다
```

`apache/kafka:3.7.0` 은 로그 디렉터리를 컨테이너 `/tmp` 아래에 잡고, compose 는
**`KAFKA_LOG_DIRS` 를 설정하지 않는다.** ⇒ 선언된 볼륨은 **아무것도 담지 않는다**. 컨테이너가
재생성되는 모든 경로(`down` → `up`, 이미지 교체, 재배포)에서 토픽·오프셋·컨슈머 그룹·DLT 가
**조용히 전부 사라진다.** 실패도 경고도 없다 — 토픽이 자동 재생성되므로 **정상처럼 보인다.**

## 전수 — 7개 중 **5개가 같은 모양**

| 프로젝트 | 이미지 | `kafka-data` 볼륨 | `KAFKA_LOG_DIRS` | 판정 |
|---|---|---|---|---|
| erp-platform | apache/kafka:3.7.0 | `/var/lib/kafka/data` | 없음 | 🔴 선언돼 있는데 안 쓰임 |
| fan-platform | apache/kafka:3.7.0 | `/var/lib/kafka/data` | 없음 | 🔴 동일 |
| iam-platform | apache/kafka:3.7.0 | `/var/lib/kafka/data` | 없음 | 🔴 동일 (`/var/lib/kafka/data` 비어 있음 실측) |
| scm-platform | apache/kafka:3.7.0 | `/var/lib/kafka/data` | 없음 | 🔴 동일 |
| wms-platform | apache/kafka:3.7.0 | `/var/lib/kafka/data` | 없음 | 🔴 동일 |
| ecommerce-…-platform | apache/kafka:3.7.0 | **없음** | 없음 | 🔵 볼륨을 선언하지 않음 — **정직하게 휘발성** |
| finance-platform | apache/kafka:3.7.0 | **없음** | 없음 | 🔵 동일 |

**`KAFKA_LOG_DIRS` 를 설정한 프로젝트는 0개.**

🔴 위험한 쪽은 5개다. 2개(ecommerce·finance)는 "영속이 아님" 이 **선언과 일치**한다. 5개는
`docker volume ls` 에 이름이 뜨고 디스크를 잡고 있어 **영속인 것처럼 보이는데** 실제로는
아니다 — 이것이 이 티켓이 존재하는 이유다. 잘못된 안심이 없는 상태보다 나쁘다.

## 왜 지금까지 아무도 안 걸렸나

Kafka 는 없는 토픽을 자동 생성하고, 이 저장소의 소비자는 대부분 `auto-offset-reset=earliest`
로 붙는다. 그래서 **유실이 곧바로 화면 오류로 번지지 않는다.** 유실이 문제가 되는 자리는
*"이전 런에서 쌓인 것"* 을 판정 근거로 쓸 때이고, 그것이 정확히 BE-043 의 AC-0 이었다.

🔵 **CI 는 이것을 절대 못 본다** — Testcontainers 는 매 런 새 브로커를 띄우므로, 재기동
사이의 유실이라는 성질 자체가 CI 에 존재하지 않는다. 신선 볼륨은 순서·영속 결함에 대해
구조적으로 영원히 초록이다.

# Goal

Kafka 볼륨의 **선언과 실제가 일치**한다 — 영속이라고 선언한 프로젝트는 실제로 영속하고,
아닌 프로젝트는 아니라고 적혀 있다.

# Scope

## In Scope

- 5개 프로젝트의 `docker-compose.yml` kafka 서비스 (erp · fan · iam · scm · wms)
- 선언 없이 휘발성인 2개(ecommerce · finance)를 **어느 쪽으로 정렬할지 결정**
- 회귀 가드 (아래 AC-4)

## Out of Scope

- Kafka 버전 업그레이드 · KRaft 설정 튜닝 · 리텐션 정책
- 이미 유실된 데이터의 복구 (**복구 대상이 아니다** — 데모 데이터는 시드가 재생성한다)
- `TASK-ERP-BE-043` 의 테넌트 축 (별건, 이미 닫힘)

# Acceptance Criteria

- [ ] **AC-0 (재현)** — 대상 프로젝트 하나를 띄워 토픽을 만들고, `down` → `up` 후 end-offset 이
      **0 으로 돌아가는지** 실측한다. 🔴 볼륨 내부를 보는 것만으로는 부족하다 — *"비어 있다"* 와
      *"재기동에서 실제로 잃는다"* 는 다른 주장이고, 후자가 이 티켓의 주장이다
- [ ] **AC-1 (경로 정렬)** — `KAFKA_LOG_DIRS` 를 마운트 지점으로 설정하거나 마운트 지점을
      실제 로그 경로로 옮긴다. **어느 쪽을 골랐는지와 이유를 적는다.** 🔴 이미지 기본값에
      의존하지 말 것 — 지금 상태가 정확히 "이미지 기본값을 안 적어서" 생겼다
- [ ] **AC-2 (실측으로 닫는다)** — 수정 후 같은 절차를 반복해 **오프셋이 살아남는지** 본다.
      컨테이너 내부에서 `meta.properties` 와 토픽 디렉터리가 **마운트된 볼륨 안에** 있는지도
      함께 확인한다(둘 다 봐야 "우연히 안 지워짐" 과 구별된다)
- [ ] **AC-3 (7개 전수)** — 나머지 2개(ecommerce·finance)를 **명시적으로 처리**한다:
      영속화하거나, 휘발성임을 compose 주석에 적는다. 🔴 조용히 두면 다음 사람이 5개만 보고
      "전부 영속" 이라고 읽는다
- [ ] **AC-4 (가드 + 물기)** — `kafka-data` 볼륨을 선언한 compose 는 `KAFKA_LOG_DIRS` 가 그
      마운트 지점을 가리켜야 한다는 가드를 **도는 레인에 배선**한다(`ci.yml`, compose 변경
      경로 필터). 🔴 **무는지 확인한다** — 한 프로젝트에서 그 env 를 지우면 RED.
      🔴 그리고 **0건은 통과가 아니다** — compose 를 한 개도 못 찾으면 실패로 끝낼 것
- [ ] **AC-5 (모집단 재확인)** — 착수 시 `apache/kafka` 를 쓰는 프로젝트를 **다시 센다.**
      위 표는 2026-08-12 것이고, 그 사이 새 프로젝트가 붙었을 수 있다

# Related Specs

- `TEMPLATE.md` § Local Network Convention (compose 규약)
- `projects/erp-platform/docs/erp-tenant-axis.md` § 6 — 이 사실이 AC-6 의 폐기 결정을
  어떻게 *실행 이전에* 확정했는지

# Related Contracts

- 없음 (인프라 배선)

# Edge Cases

- 경로를 바꾸면 **기존 컨테이너의 클러스터 id 가 새 디렉터리에 없다** — 첫 기동에서 KRaft
  포맷이 다시 돌 수 있다. 그 자체는 무해하지만(지금도 매번 그렇다) 처음 한 번은 확인할 것
- `iam` 은 `kafka-ui` 가 붙어 있다 — 경로 변경 후 UI 가 여전히 붙는지 본다
- 볼륨 이름은 그대로 두어야 한다(`<proj>_kafka-data`). 이름을 바꾸면 **디스크에 고아 볼륨**이
  남는다(이 호스트에서 반복된 모양)

# Failure Scenarios

- **볼륨 내부가 빈 것만 보고 닫음** → "쓰지 않는다" 는 보였지만 "재기동에서 잃는다" 는 안
  보였다. AC-0/AC-2 가 그것을 요구한다
- **erp 만 고침** → 나머지 4개가 같은 모양으로 남는다. 이 저장소가 반복해서 밟은 형제 파리티
  낙오. AC-3/AC-5 가 막는다
- **가드를 만들고 레인에 안 걸음** → 술어만 있고 도는 곳이 없는 가드는 영원히 초록이다
  (`TASK-MONO-518`·`524`). AC-4

# Definition of Done

- [ ] AC-0~AC-5 충족
- [ ] 대상 프로젝트 기동 스모크 (compose up → 토픽 생성 → down/up → 오프셋 생존)
- [ ] Ready for review
