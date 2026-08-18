# Task ID

TASK-MONO-553

# Title

`demo-up.sh` 가 **느린 의존성 하나에 전체 스택을 포기**하고, 그 잔해가 **옛 주소로만 열리는 좀비 스택**이 된다

# Status

ready

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
- **B — 느린 의존성에 재시도를 준다.** compose 의 `--wait-timeout` / healthcheck
  `start_period` 를 **동시 재기동 부하 하에서 실측한 값**으로 잡는다(현재 값이 무엇이고
  왜 그 값인지 AC-0 에서 확인할 것 — 인계 금지).
- **C — 라벨 드리프트를 판정 가능하게 한다.** 기동 완료 시점에 **호스트명 라벨이 현재 IP 와
  일치하는지** 확인하고, 어긋나면 실패로 보고한다. 이것이 이 결함의 **직접적인 술어**다.

## Out of Scope

- 호스트가 굳는 원인 → `TASK-MONO-552`.
- 헬스 스냅샷의 신선도/술어 → `TASK-MONO-551`.
- **Elastic IP 도입** — IP 고정은 이 결함을 *가리기만* 한다(라벨 갱신이 중단되는 사실은
  그대로다). 비용·주소 안정성 관점의 별개 판단이므로 필요하면 별도 티켓.

# Acceptance Criteria

**AC-0 — 재확인 (verify-then-act).**
`origin/main` 에서 `demo-up.sh` · `demo-boot.sh` · `demo-stack.service` 를 읽고 **현재의
타임아웃/에러 전파 방식을 다시 확인한다.** 위 인과는 관측이지 코드 독해가 아니다.
compose 의 대기 타임아웃 기본값이 얼마인지 **문서가 아니라 실제 호출 지점**에서 확인할 것.

**AC-1 — 재시작이 손 없이 완결된다.** `/stop` → `/start` 후 SSM 명령 **0건**으로
`http://console.<새IP>.sslip.io/` 가 2xx/3xx 를 낸다.
🔴 **판정은 새 호스트명으로** — 옛 호스트명이 응답하는 것이 바로 이 결함의 증상이다.

**AC-2 — 부분 실패가 격리된다. 대조군 필수.**
한 프로젝트를 **의도적으로 실패**시켰을 때(예: 그 프로젝트의 kafka healthcheck 를 실패로 고정):
- 나머지 7개는 **정상 기동**하고,
- 실패한 프로젝트는 `/domains` 에서 **`down`/`partial` 로 드러나고**,
- `demo-stack.service` 는 **성공으로 보고하지 않는다**.
세 가지를 모두 확인한다. 첫째만 보면 "조용히 삼키는" 구현과 구별되지 않는다.

**AC-3 — 라벨 일치 가드(C).** 기동 후 호스트명 라벨과 현재 공인 IP 의 불일치를 탐지한다.
**bite**: 라벨을 옛 IP 로 되돌리면 빨개져야 한다. 🔴 가드는 **자기 설명 문구에 안 걸리게**
(스크립트가 예시 호스트명을 주석에 담는다) 줄머리 앵커나 실행 결과로 판정할 것.

**AC-4 — 재굽기 + 라이브.** `demo-up.sh`·`demo-boot.sh`·유닛 파일은 **baked 층**이다.
⚠️ `packer build`/`terraform apply` 는 **사용자 승인 대상**.

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
