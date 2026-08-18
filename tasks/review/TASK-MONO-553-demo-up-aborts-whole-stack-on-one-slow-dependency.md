# Task ID

TASK-MONO-553

# Title

`demo-up.sh` 가 **느린 의존성 하나에 전체 스택을 포기**하고, 그 잔해가 **옛 주소로만 열리는 좀비 스택**이 된다

# Status

review

# Owner

monorepo

# Task Tags

- infra
- demo

---

# 배경 — 2026-08-17(UTC) 데모 stop/start 실증에서 관측

`TASK-MONO-552` 로 굳은 호스트를 `/stop` → `/start` 로 되살린 직후, **데모의 모든 공개 주소가 죽었다.**
런처 페이지가 건네는 주소는 404 를 냈고, 스택은 멀쩡히 돌고 있었다.

## 관측된 인과 (라벨·로그·메모리로 확정)

```
재시작
 → docker restart policy 가 옛 라벨 컨테이너 94개를 되살린다
 → demo-stack.service 가 demo-boot.sh 를 돌린다
 → IMDSv2 로 새 공인 IP 를 파생: DEMO_DOMAIN=13-209-2-22.sslip.io   (재시작마다 IP 가 바뀐다)
 → compose 가 새 라벨로 재생성을 시작한다 (iam 부터)
 → iam-kafka 의 healthcheck 를 기다린다
 → ✖ 타임아웃: "dependency failed to start: container iam-kafka is unhealthy"
 → demo-stack.service exit 1  ⇒ **나머지 7개 프로젝트는 손도 못 댄다**
 → 옛 라벨 컨테이너가 계속 서빙 ⇒ 새 주소 404 / 옛 주소 307
```

**iam-kafka 는 고장난 게 아니었다.** 로그가 그 시각에 `__consumer_offsets` **50 파티션을
파티션당 ~1초씩** 로딩 중이었다(8개 도메인 동시 재기동으로 I/O 경합). 1~2분 뒤 **healthy** 가 됐다.
⇒ **경합에 의한 레이스이지 손상이 아니다.**

## 🔴 지문 — `iam-grafana` 만 새 IP 를 갖고 있었다

실측(라벨에 sslip 호스트명을 담은 컨테이너 12개):

| 컨테이너 | 라벨의 호스트명 |
|---|---|
| `iam-grafana` | **13-209-2-22**.sslip.io ← 새 IP |
| `platform-console-web` · `fan-platform-web` · `fan-platform-gateway` · `ecommerce-web-store` · `ecommerce-gateway-service` · `erp-platform-gateway` · `finance-platform-gateway` · `scm-platform-gateway` · `wms-gateway-service` · `wms-kafka-ui` · `wms-grafana` | 3-35-223-31.sslip.io ← **옛 IP** |

**iam 을 처리하다 중간에 죽은 자국이 라벨에 그대로 남았다.** 이보다 더 정확한 증거는 없다.

## 왜 이것이 별건인가 (`552` 와 분리하는 이유)

`552` 는 **용량**(호스트가 굳는다)이고 이것은 **부팅 견고성**이다. RAM 이 넉넉해도
느린 의존성 하나면 같은 결과가 난다 — 그리고 **재시작은 온디맨드 데모의 기본 동작**이다.
면접관이 `/start` → `/stop` → `/start` 를 하는 순간 밟는다.

그리고 이 결함은 **조용하다**: `/domains` 는 옛 컨테이너들을 보고 `up` 이라 답한다
(`TASK-MONO-551` 결함 B 와 합성되면 **런처는 초록인데 모든 링크가 404**).

## 🔴 내가 이 결함을 두 번 오진했다 (기록)

1. *"호스트 포화(CPU/디스크)"* — CPU 45% · EBS 576 IOPS(기준선 3000) 로 **반증**.
2. *"Traefik 라우터가 첫 부팅 IP 에 고정된다"* — 그럴듯했고 **틀렸다**. 라벨을 실제로 읽으니
   고정이 아니라 **갱신이 중단된 것**이었다. 🔵 *기전이 검증 가능한 것과 그것이 원인인 것은 다르다* —
   "옛 호스트명이 307 을 낸다" 는 관측은 **두 설명 모두와 일관**됐고, 갈라 준 것은
   `iam-grafana` 한 칸이었다.

---

# Goal

재시작 후 **사람 손 0** 으로 모든 공개 주소가 새 IP 에서 열린다. 느린 의존성 하나가
전체를 무너뜨리지 않는다.

# Scope

## In Scope

- **A — 부분 실패가 전체 실패가 되지 않게 한다.** 한 프로젝트의 기동 실패가 나머지 7개의
  기동을 막지 않아야 한다(현재는 `set -e` + compose 종료코드가 그대로 서비스를 죽인다).
  - 🔴 단, **실패를 삼키면 안 된다** — 실패한 프로젝트는 **비-0 로 보고**되어야 하고
    `/domains` 가 그것을 `down` 으로 드러내야 한다. *"계속 진행" 이 "조용히 성공" 이 되는 것*이
    이 고침의 최악의 결과다.
- **B — 느린 의존성에 재시도를 준다.** ~~compose 의 `--wait-timeout` / healthcheck
  `start_period` 를 **동시 재기동 부하 하에서 실측한 값**으로 잡는다~~
  🔴 **AC-0 이 이 문장의 전제를 반증했다**: 호출 지점에 `--wait-timeout` 은 **없다**(그런
  인자를 주지 않는다). 그래서 손잡이는 두 개뿐이고 — 8개 프로젝트에 흩어진 healthcheck
  파라미터(= **CI 도 쓰는 값**)를 전역으로 완화하거나, **기동 루프가 실패를 다시 시도**
  하거나 — 후자를 택했다. 실패의 성격이 **레이스**이므로(관측: 1~2분 뒤 healthy) 같은
  명령을 조금 뒤에 다시 부르면 성공하고, compose 는 멱등하다.
- **C — 라벨 드리프트를 판정 가능하게 한다.** 기동 완료 시점에 **호스트명 라벨이 현재 IP 와
  일치하는지** 확인하고, 어긋나면 실패로 보고한다. 이것이 이 결함의 **직접적인 술어**다.

## Out of Scope

- 호스트가 굳는 원인 → `TASK-MONO-552`.
- 헬스 스냅샷의 신선도/술어 → `TASK-MONO-551`.
- **Elastic IP 도입** — IP 고정은 이 결함을 *가리기만* 한다(라벨 갱신이 중단되는 사실은
  그대로다). 비용·주소 안정성 관점의 별개 판단이므로 필요하면 별도 티켓.

# Acceptance Criteria

**AC-0 — 재확인 (verify-then-act).** ✅ **완료 (2026-08-18, origin/main @ `4d328cfd0`)**

`demo-up.sh` · `demo-boot.sh` · `demo-stack.service` 를 읽었다. 결과:

| 물음 | 실제 |
|---|---|
| compose 대기 타임아웃 기본값 | **그런 것이 없다.** 호출은 `docker compose -p <p> -f … up -d` 뿐 — `--wait` 도 `--wait-timeout` 도 주지 않는다. |
| 그럼 무엇이 기다리는가 | 각 프로젝트 compose 의 `depends_on: condition: service_healthy`. 한도는 **의존 대상 자신의 healthcheck** 다. |
| iam-kafka 의 그 값 | `interval 15s · timeout 10s · retries 10 · start_period 30s` (`projects/iam-platform/docker-compose.yml:146`, e2e 오버레이도 동일) |
| 에러 전파 | `demo-up.sh` 머리의 `set -euo pipefail`. compose 가 비-0 를 내면 **거기서 스크립트가 끝난다** — 루프에 실패 처리가 없었다. |
| 그 종료코드의 행선지 | `demo-boot.sh` 마지막 줄이 `exec` 한다 ⇒ 그대로 `demo-stack.service`(Type=oneshot)의 결과가 된다. |

🔴 **이 티켓 본문의 "compose 의 `--wait-timeout`" 이라는 표현은 부정확했다** — 그런 인자는
이 저장소의 호출 지점에 없다. 고칠 손잡이는 compose 옵션이 아니라 **healthcheck 파라미터**
이거나 **스크립트의 에러 전파**이고, 이 고침은 후자를 택했다(In Scope B 의 근거가 여기서
바뀐다). healthcheck 값을 건드리지 않은 이유: 그 값은 8개 프로젝트 compose 에 흩어져 있고
**CI 도 같은 값을 쓴다** — 필요한 것은 전역 완화가 아니라 *데모 부팅이라는 한 상황*의
경합 내성이다.

🔵 그 healthcheck 는 `kafka-broker-api-versions.sh` — **JVM 을 새로 띄운다.** 8개 도메인이
동시에 재기동해 I/O 가 경합하면 10초 timeout 을 넘기는 것이 이상한 일이 아니다. 관측된
"1~2분 뒤 healthy" 와 정확히 맞는다.

🔵 덤으로 발견: `demo-up.sh` 의 시드 블록 주석은 *"비-0 로 끝나되 기동은 유지"* 라고 계약을
적어 두었는데 **코드는 그렇게 하지 않고 있었다**(마지막 `echo` 의 rc=0 으로 끝났다).
주석과 코드가 어긋나 있었고, 어긋난 쪽은 코드였다 — 종료코드 집계에 포함했다.

**AC-1 — 재시작이 손 없이 완결된다.** ⏳ **AC-4 대기** (재굽기 뒤에만 판정 가능)
`/stop` → `/start` 후 SSM 명령 **0건**으로 `http://console.<새IP>.sslip.io/` 가 2xx/3xx 를 낸다.
🔴 **판정은 새 호스트명으로** — 옛 호스트명이 응답하는 것이 바로 이 결함의 증상이다.

**AC-2 — 부분 실패가 격리된다. 대조군 필수.** ✅ **완료** — 가드 `(z4)` (`verify-demo-wrapper.sh`)
한 프로젝트를 **의도적으로 실패**시켰을 때(예: 그 프로젝트의 kafka healthcheck 를 실패로 고정):
- 나머지 7개는 **정상 기동**하고,
- 실패한 프로젝트는 `/domains` 에서 **`down`/`partial` 로 드러나고**,
- `demo-stack.service` 는 **성공으로 보고하지 않는다**.
세 가지를 모두 확인한다. 첫째만 보면 "조용히 삼키는" 구현과 구별되지 않는다.

> **구현 방식**: 진짜 kafka 를 굶기는 대신 `docker` 를 **대역으로 바꿔** compose 의 종료코드
> 하나만 통제한다. 러너에서 굶기기는 재현 불가능하고(그것이 `TASK-MONO-552` 다), 재현돼도
> *무엇이* 실패했는지 통제할 수 없다. 묻는 것은 "compose 가 비-0 를 냈을 때 **스크립트가**
> 어떻게 행동하는가" 이므로 통제 대상은 정확히 그 종료코드다.
>
> 네 칸 전부 측정(가운데 도메인 `wms` 만 실패시킴):
> (1) 대조군 rc=0 · (2) 실패 뒤의 `console` 도 기동 시도 · (3) rc=1 (성공 보고 안 함) ·
> (4) 실패 도메인 이름 출력. **고침 전 코드에 대고 돌리면 (2) 에서 FAIL** 한다(측정 완료).
>
> 🔵 두 번째 불릿(`/domains` 가 `down`/`partial`)은 **자동으로 성립**한다 — 실패한
> 프로젝트는 컨테이너가 안 뜨므로 `demo-status.sh` 의 `healthy < total` 이 된다. 다만 그
> 판정의 **품질**은 `TASK-MONO-551`(결함 A: 종료한 init 컨테이너를 unhealthy 로 셈)에
> 달려 있고, 그건 이 티켓의 Out of Scope 다.

**AC-3 — 라벨 일치 가드(C).** ✅ **완료** — `infra/demo/check-label-drift.sh` + 가드 `(z5)`
기동 후 호스트명 라벨과 현재 공인 IP 의 불일치를 탐지한다.
**bite**: 라벨을 옛 IP 로 되돌리면 빨개져야 한다. 🔴 가드는 **자기 설명 문구에 안 걸리게**
(스크립트가 예시 호스트명을 주석에 담는다) 줄머리 앵커나 실행 결과로 판정할 것.

> 술어의 입력은 **실행 중인 컨테이너의 라벨**(`docker inspect`)이지 소스가 아니다 —
> 그래서 자기 문서에 걸릴 수 없다. 세 칸 측정: 대조군(현재 도메인만) rc=0 · bite(기동
> 대상에 옛 도메인) rc=1 + 컨테이너 이름 명시 · 기동 대상 **밖**의 옛 라벨은 경고만 rc=0.
> 마지막 칸이 없으면 `demo-core` 부팅이 항상 빨개진다(나머지 4개 도메인이 옛 라벨로 남는
> 것은 정상이다) — 그리고 빨개지는 가드는 곧 꺼진다.

**AC-4 — 재굽기 + 라이브.** ⏳ **미완** — `demo-up.sh`·`demo-boot.sh`·`check-label-drift.sh`·
유닛 파일은 **baked 층**이다. 저장소를 고쳐도 **AMI 를 다시 굽기 전에는 라이브에 반영되지
않는다.** ⚠️ `packer build`/`terraform apply` 는 **사용자 승인 대상**이므로 이 PR 에 포함하지
않는다. `TASK-MONO-551`·`TASK-MONO-554` 와 **한 번에 묶어 굽는다**(굽기 1회 = 약 20분 +
인스턴스 가동 시간이므로 건마다 굽는 것은 예산 낭비다).

# Related Specs

- `infra/demo/demo-up.sh` — 실패 전파의 주체
- `infra/demo/demo-boot.sh` L55~70 (`derive_domain`) — IP → 호스트명 파생
- `infra/demo/demo-stack.service` — exit 1 이 서비스 실패가 되는 자리
- `projects/*/docker-compose.yml` — `depends_on: condition: service_healthy` 와 kafka healthcheck
- `TASK-MONO-551` — 이 결함이 **조용한** 이유(헬스 스냅샷이 옛 컨테이너를 보고 `up` 이라 답한다)

# Related Contracts

없음 (인프라 전용).

# Edge Cases

- **공인 IP 는 재시작마다 바뀐다**(EIP 없음). 실증 스크립트에 IP 를 박으면 다음 회차에 썩는다.
- **첫 부팅에는 이 결함이 안 보인다** — 옛 컨테이너가 없어 라벨 드리프트가 생기지 않는다.
  **판정은 반드시 stop/start 왕복에서.**
- **restart policy 가 먼저 뜬다** — `docker` 데몬이 `demo-stack.service` 보다 먼저 옛 컨테이너를
  살린다. 이 순서 자체는 정상이며, 문제는 그 뒤 갱신이 완결되지 않는 것이다.
- **일회성 init 컨테이너**(`*-kafka-init`, `*-minio-init`)는 `Exited (0)` 이 정상이다
  (`TASK-MONO-551` 결함 A). 이 티켓의 실패 판정이 그것들을 실패로 세지 않도록 할 것.

# Failure Scenarios

- **타임아웃만 늘리고 닫는다** — 더 느린 날에 다시 터진다. 부분 실패 격리(A)가 본체다.
- **실패를 삼킨다** — `|| true` 로 넘기면 `demo-stack.service` 가 초록이 되고, 이 저장소가
  이미 여러 번 만난 *"아무것도 안 보면서 초록"* 이 된다.
- **첫 부팅만으로 닫는다** — 라벨 드리프트는 왕복에서만 발화한다.
- **`/domains` 로 판정한다** — `TASK-MONO-551` 이 닫히기 전까지 그것은 옛 컨테이너를 보고
  `up` 이라 답한다. **판정은 HTTP 표면으로.**

# Notes

- 분석 = **Opus 5** / 구현 권장 = **Opus** — bash 에러 전파 + compose 의존성 의미론 +
  가드 설계 + stop/start 왕복 실증. 단순 fix 아님.
- 선행: 없음(독립). 다만 **판정 품질은 `551` 에 의존**한다.
- 관련: `TASK-MONO-550`(이 층을 노출시킨 고침), `TASK-MONO-552`(같은 실증에서 나온 용량 축),
  `TASK-MONO-389`(*"잘린 웜업"* 후보를 처음 적은 티켓 — **이 관측이 그 후보를 지지한다**).
