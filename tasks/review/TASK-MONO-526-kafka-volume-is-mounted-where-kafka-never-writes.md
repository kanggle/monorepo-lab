# Task ID

TASK-MONO-526

# Title

6개 프로젝트의 Kafka 볼륨이 **Kafka 가 쓰지 않는 경로**에 마운트돼 있다 — 재기동마다 토픽·오프셋·DLT 가 전부 사라진다

> 제목의 "5개" 는 착수 시 AC-5 재계수에서 **6개**로 정정됐다(finance 누락). § 구현 기록 AC-5.

# Status

review

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

> 🔴 **이 표는 착수 시 AC-5 재계수에서 두 군데가 틀린 것으로 드러났다** — § 구현 기록 AC-5.
> ① `finance-platform` 은 볼륨을 **선언하고 있다**(compose L347, 호스트에 `finance_kafka-data`
> 실재) ⇒ 위험군은 5개가 아니라 **6개**. ② 표는 *프로젝트*를 셌는데 실제 모집단은 compose
> **파일**이라 `iam` 의 e2e compose 가 빠져 있었다. 표를 믿었으면 finance 브로커는 고쳐지지
> 않은 채 남았고, 이 티켓은 "완료" 로 닫혔을 것이다.

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

- [x] **AC-0 (재현)** — 대상 프로젝트 하나를 띄워 토픽을 만들고, `down` → `up` 후 end-offset 이
      **0 으로 돌아가는지** 실측한다. 🔴 볼륨 내부를 보는 것만으로는 부족하다 — *"비어 있다"* 와
      *"재기동에서 실제로 잃는다"* 는 다른 주장이고, 후자가 이 티켓의 주장이다
- [x] **AC-1 (경로 정렬)** — `KAFKA_LOG_DIRS` 를 마운트 지점으로 설정하거나 마운트 지점을
      실제 로그 경로로 옮긴다. **어느 쪽을 골랐는지와 이유를 적는다.** 🔴 이미지 기본값에
      의존하지 말 것 — 지금 상태가 정확히 "이미지 기본값을 안 적어서" 생겼다
- [x] **AC-2 (실측으로 닫는다)** — 수정 후 같은 절차를 반복해 **오프셋이 살아남는지** 본다.
      컨테이너 내부에서 `meta.properties` 와 토픽 디렉터리가 **마운트된 볼륨 안에** 있는지도
      함께 확인한다(둘 다 봐야 "우연히 안 지워짐" 과 구별된다)
- [x] **AC-3 (7개 전수)** — 나머지 2개(ecommerce·finance)를 **명시적으로 처리**한다:
      영속화하거나, 휘발성임을 compose 주석에 적는다. 🔴 조용히 두면 다음 사람이 5개만 보고
      "전부 영속" 이라고 읽는다
- [x] **AC-4 (가드 + 물기)** — `kafka-data` 볼륨을 선언한 compose 는 `KAFKA_LOG_DIRS` 가 그
      마운트 지점을 가리켜야 한다는 가드를 **도는 레인에 배선**한다(`ci.yml`, compose 변경
      경로 필터). 🔴 **무는지 확인한다** — 한 프로젝트에서 그 env 를 지우면 RED.
      🔴 그리고 **0건은 통과가 아니다** — compose 를 한 개도 못 찾으면 실패로 끝낼 것
- [x] **AC-5 (모집단 재확인)** — 착수 시 `apache/kafka` 를 쓰는 프로젝트를 **다시 센다.**
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

- [x] AC-0~AC-5 충족
- [x] 대상 프로젝트 기동 스모크 (compose up → 토픽 생성 → down/up → 오프셋 생존)
- [x] Ready for review

---

# 구현 기록 (2026-08-13)

## AC-5 — 모집단을 다시 셌고, **티켓 표가 두 군데 틀렸다**

`git ls-files '*docker-compose*.yml'` 로 전수. 판정 술어는 *"프로젝트"* 가 아니라
**"`image: apache/kafka` 이면서 `KAFKA_PROCESS_ROLES` 를 가진 compose 서비스"** 로 잡았다 —
같은 이미지를 쓰는 `kafka-init` 1회성 잡(iam·wms)은 브로커가 아니기 때문이다.

| compose 파일 | 볼륨 선언(526 이전) | 조치 |
|---|---|---|
| `projects/erp-platform/docker-compose.yml` | ✅ | `KAFKA_LOG_DIRS` 추가 |
| `projects/fan-platform/docker-compose.yml` | ✅ | 동 |
| `projects/iam-platform/docker-compose.yml` | ✅ | 동 |
| `projects/scm-platform/docker-compose.yml` | ✅ | 동 |
| `projects/wms-platform/docker-compose.yml` | ✅ | 동 |
| `projects/finance-platform/docker-compose.yml` | ✅ **(티켓 표는 "없음" 이라고 적었다)** | 동 |
| `projects/ecommerce-…/docker-compose.yml` | ❌ | 볼륨 + `KAFKA_LOG_DIRS` 신설 (AC-3) |
| `projects/iam-platform/docker-compose.e2e.yml` | ❌ **(티켓 표에 없었다)** | `KAFKA-EPHEMERAL` 선언 (AC-3) |

⇒ 브로커 **8개** / 위험군은 5개가 아니라 **6개**.

정정 두 건:
- **finance** — 티켓 표는 "볼륨 자체가 없어 정직하게 휘발성(🔵)" 이라고 적었다. 실제로는
  compose L347 에 `kafka-data:/var/lib/kafka/data` 가 있고 호스트에 `finance_kafka-data`
  볼륨이 실재한다. **가장 위험한 쪽(선언은 있는데 안 쓰임)에 속한다.**
- **모집단의 단위** — 표는 프로젝트를 셌는데 실제 단위는 compose 파일이다. `iam` 은 브로커를
  **둘**(운영용 + e2e) 가지고 있어 표의 7행으로는 표현되지 않는다.

🔵 범위 밖으로 확인한 것: `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.ecommerce.yml`
에도 브로커가 있으나 **gitignore 된 로컬 전용 파일**(헤더에 `LOCAL DEMO — NOT committed CI`)이라
추적 대상이 아니다. 가드의 `git ls-files --exclude-standard` 도 이 파일을 보지 않는다.

## AC-0 — 재현. *"볼륨이 비었다"* 가 아니라 *"재기동에서 잃는다"* 를 쟀다

erp 브로커 단독 기동(`docker compose -p erp … up -d kafka`):

```
토픽 생성 + 3건 produce   → end-offset  mono526.probe:0:3
/var/lib/kafka/data       → 비어 있음 (ls -A 무출력)
/tmp/kafka-logs           → meta.properties + mono526.probe-0
KAFKA_LOG_DIRS            → (unset)
docker compose down → up  → 토픽 목록에 없음 / end-offset 없음
```

**오프셋이 0 으로 돌아간 게 아니라 토픽 자체가 사라졌다** — 티켓이 예상한 것보다 한 단계 더
나쁘다. 이미지 안의 config 파일은 `log.dirs=/tmp/kraft-combined-logs` 라고 적혀 있는데 실제
경로는 `/tmp/kafka-logs` 였다(엔트리포인트가 넣는다) ⇒ **문서로 추적할 수 없는 기본값**이다.
AC-1 의 *"이미지 기본값에 의존하지 말 것"* 이 정확히 이 지점이다.

## AC-1 — `KAFKA_LOG_DIRS` 를 마운트 지점으로 (반대 방향을 고르지 않은 이유)

두 선택지 중 **`KAFKA_LOG_DIRS: /var/lib/kafka/data`** 를 골랐다. 마운트를 `/tmp/kafka-logs`
로 옮기는 반대 방향은:

1. **여전히 이미지 기본값에 의존한다** — 그 경로는 어느 config 파일에도 없고 이미지 버전이
   바뀌면 조용히 어긋난다. AC-1 이 금지한 바로 그 형태다.
2. `/tmp` 는 의미상 "버려도 되는 곳" 이다. 영속 상태를 거기에 두면 다음 청소가 지운다.
3. `/var/lib/kafka/data` 는 이 저장소가 **이미 선언한 의도**다(erp compose 주석: bitnami→apache
   전환 시 `data dir: /bitnami/kafka → /var/lib/kafka/data`). 고칠 것은 의도가 아니라 배선이다.

⚠️ **새로 생긴 실패 양식** — 이제 볼륨이 실제로 KRaft 포맷되므로 `CLUSTER_ID`(6개 프로젝트가
`${KAFKA_KRAFT_CLUSTER_ID:-…}` 로 읽는다)를 바꾸면 브로커가 기동을 거부한다. 지금까지는
디렉터리가 매번 새것이라 **불가능했던** 고장이다. 각 compose 주석에 복구 절차
(`docker volume rm <proj>_kafka-data`)를 함께 적었다.

## AC-3 — 볼륨 없던 2개를 **명시적으로** 갈랐다

기준은 **스택의 수명**이다:

- `ecommerce` (장수 데모 스택) → **영속화**. 같은 파일이 postgres 10개 · minio · elasticsearch
  볼륨을 전부 선언한다 ⇒ kafka 만 빠진 것은 결정이 아니라 **누락**이고, 형제 6개와 성질을 맞췄다.
- `iam` e2e → **휘발성 유지 + 선언**. e2e 는 매 런 알려진 빈 상태에서 시작해야 한다. 여기에
  토픽·오프셋이 살아남으면 순서 의존 플레이크가 되고, 그 최악의 형태는 *"단독 실행 초록,
  전체 실행만 빨강"* 이다. 침묵으로 두지 않기 위해 기계가 읽는 토큰 `KAFKA-EPHEMERAL:` 을
  서비스 블록 안에 넣었고, **가드가 그 토큰의 부재를 RED 로 만든다.**

## AC-4 — 가드 + 배선 + 물기

`scripts/check-kafka-log-dirs.sh` (신규). 술어:

```
브로커(image apache/kafka + KAFKA_PROCESS_ROLES) 마다
  (1) kafka-data:<경로> 마운트가 있으면 ⇒ KAFKA_LOG_DIRS == <경로>
  (2) 마운트가 없으면                  ⇒ KAFKA-EPHEMERAL: 선언이 있어야 한다
  (3) 마운트 없이 KAFKA_LOG_DIRS 만    ⇒ 갈 곳 없는 경로. 실패
브로커 0건 ⇒ 통과가 아니라 계측 실패
```

배선: `.github/workflows/ci.yml` 에 `kafka-log-dirs` 필터(순수 positive) + outputs + 잡 3스텝
(`bash -n` → `--self-test` → 본 검사). `code-changed` 와 **AND 하지 않았다** — 이 드리프트의 두
도착 경로 중 하나가 *가드 자신의 `.sh` 가 약화되는 것*이고 `.sh` 는 `code-changed` 밖이다.

**물기 — 픽스처가 아니라 실물에서:**

| bite | 결과 |
|---|---|
| wms 에서 `KAFKA_LOG_DIRS` 제거 (= 526 이전 상태) | **rc=1** |
| iam e2e 에서 `KAFKA-EPHEMERAL:` 제거 | **rc=1** |
| 복구 후 | rc=0 — 브로커 8개 (영속 7 · 휘발성 1) |

자기검증(`--self-test`) **7/7**. 픽스처를 손으로 짓지 않고 **실제 erp compose 를 복사해 변형**
한다 — 손으로 지은 픽스처는 실물보다 관대하기 쉽고 그러면 초록이 아무것도 증명하지 못한다.
포함된 케이스: 원본 통과 / env 제거 / 경로 불일치 / 마운트·선언 둘 다 없음 / 휘발성 선언 /
브로커 0건 / `kafka-init` 은 브로커로 세지 않음.

🔴 **가드가 처음에 오탐을 냈고, 그게 진짜 결함이었다.** 파서 레코드 구분자를 탭으로 뒀더니
bash `read` 가 **연속 탭을 하나로 접어**(탭은 IFS 공백류) 빈 필드가 사라지고 `ephemeral=1` 이
`mount` 자리로 밀렸다 — 정상인 iam e2e 를 *"`kafka-data:1` 을 마운트하는데 …"* 로 고발했다.
구분자를 `|` 로 바꿔 수정. 스크립트 헤더에 남겼다.

🔴 그리고 **주석 줄은 env 파싱에서 제외**해야 했다. 이 커밋이 넣은 설명 주석들이 산문으로
`KAFKA_LOG_DIRS` 를 언급하기 때문에, 제외하지 않으면 가드가 **자기 설명문을 설정으로 오인해**
전부 초록이 된다. (반대로 `KAFKA-EPHEMERAL:` 은 주석 줄에서만 읽는다.)

## AC-2 — 수정 후 같은 절차, 반대 결과

```
KAFKA_LOG_DIRS (컨테이너 실측)  = /var/lib/kafka/data
토픽 생성 + 3건 produce         → mono526.after:0:3
/var/lib/kafka/data             → meta.properties + mono526.after-0
/tmp/kafka-logs                 → 디렉터리 자체가 없음
docker inspect Mounts           → volume erp_kafka-data -> /var/lib/kafka/data
down → up                       → 토픽 존재, end-offset **mono526.after:0:3 보존**
```

마운트 지점 안의 `meta.properties` **와** 토픽 디렉터리를 둘 다 확인했고(AC-2 요구),
호스트 쪽 `docker inspect` 로 그 경로가 **이름 있는 볼륨**임을 함께 확인했다 — 컨테이너 안의
`ls` 만으로는 "우연히 안 지워진 컨테이너 레이어" 와 구별되지 않는다.

### Edge Case 확인 (iam — `kafka-init` + `kafka-ui` 를 둘 다 가진 유일한 스택)

- **KRaft 재포맷**: 첫 기동에서 새 디렉터리를 포맷했고 무해. `InconsistentClusterId` 로그 **0건**.
- **`kafka-ui`**: 재기동 후에도 `status:"online"`, `brokerCount:1` — 로그 경로와 무관하게 붙는다.
- **`kafka-init` 멱등**: `status=exited`, `exit=0` (2회차).
- **볼륨 이름**: `erp_/fan_/finance_/iam_/scm_/wms_kafka-data` 전부 그대로. 고아 볼륨 0.

🔴 **이 Edge Case 를 처음엔 잘못 쟀다.** 판정을 *토픽 개수 비교*로 잡았더니 `kafka-init` 이
아직 토픽을 만드는 중이라 스냅샷마다 값이 달랐고(4 → 13), `docker inspect .State.ExitCode` 를
성공 근거로 썼는데 **실행 중인 컨테이너도 0 을 돌려준다**. 둘 다 판정 근거가 못 된다. 그래서
술어를 `kafka-init` 과 무관한 것으로 바꿨다 — **init 이 만들지 않는 마커 토픽**을 직접 만들고
재기동 후 생존을 본다(`mono526.iam.marker:0:2` → 재기동 후 동일). 그리고 `ExitCode` 대신
`State.Status == exited` 를 먼저 확인한다.

## 정리

프로브 토픽(`mono526.*`)은 **영속 볼륨에 남지 않도록 삭제**했다 — 이제 볼륨이 진짜로 남기
때문에, 검증이 만든 잔여물을 그대로 두면 다음 사람이 그것을 실제 데이터로 읽는다. erp·iam
양쪽 `--list` 에 `mono526` **0건** 확인. 컨테이너 0개, traefik 포함 전부 down.
