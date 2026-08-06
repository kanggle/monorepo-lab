# Task ID

TASK-BE-582

# Title

admin-service 의 프로젝션이 **아무도 발행하지 않는 토픽**을 구독한다 — 입고·출고 프로젝션이 영구히 0행

> 🔴 착수 후 정정: **2개가 아니라 4개**였다(`asn.received` · `putaway.instructed` ·
> `order.received` · `picking.requested`). 제목은 히스토리를 위해 남긴다.

# Status

review

# Owner

wms-platform

# Task Tags

- bug
- contract

---

# 배경 — `TASK-MONO-510` AC-2 라이브 스윕이 발굴 (AC-8)

콘솔 WMS 7화면을 세션 쿠키로 훑다가 나왔다. 화면은 전부 `200` 이고 로그인·테넌트
선택도 정상인데 **입고와 출고 목록만 비어 있었다.**

## 🔴 산출물 수준 증거 (2026-08-06 실측)

발행자(`inbound-service` / `outbound-service`)의 아웃박스 릴레이는 토픽을
**이벤트 타입에서 기계적으로** 만든다:

```java
// inbound-service · outbound-service 의 OutboxPublisher 양쪽 동일
private static TopicResolver topicResolver() {
    return eventType -> "wms." + eventType + ".v1";
}
```

`admin-service` 는 `application.yml` 의 `admin.projection.kafka.topics.*` 에
**손으로 적은 토픽 이름**을 구독한다. 두 이름 규약이 다르다:

| admin 구독 | 발행 토픽 (`wms.<eventType>.v1`) | |
|---|---|---|
| `wms.inbound.asn.v1` | `inbound.asn.received` → **`wms.inbound.asn.received.v1`** | ❌ |
| `wms.outbound.order.v1` | `outbound.order.received` → **`wms.outbound.order.received.v1`** | ❌ |
| `wms.inbound.inspection.completed.v1` | `inbound.inspection.completed` → 동일 | ✅ |
| `wms.inbound.putaway.completed.v1` | `inbound.putaway.completed` → 동일 | ✅ |
| `wms.outbound.shipping.confirmed.v1` | `outbound.shipping.confirmed` → 동일 | ✅ |
| `wms.inventory.*.v1` (6개) | `inventory.*` → 동일 | ✅ |

**이벤트 타입이 우연히 admin 이 적은 이름과 같아지는 것들만 동작한다.**
`*.received` 로 끝나는 둘이 정확히 어긋난다.

Kafka 오프셋 실측 — 두 토픽 **모두 존재하고**(kafka-init 이 만든다) 한쪽만 찬다:

```
wms.inbound.asn.received.v1        msgs=2   ← 발행자가 쓴다
wms.inbound.asn.v1                 msgs=0   ← admin 이 구독한다 (한 번도 쓰인 적 없음)
wms.outbound.order.received.v1     msgs=1   ← 발행자가 쓴다
wms.outbound.order.v1              msgs=0   ← admin 이 구독한다
wms.inbound.inspection.completed.v1  msgs=1  (양쪽 일치)
wms.inbound.putaway.completed.v1     msgs=1  (양쪽 일치)
```

DB 로도 같은 그림이다. 아웃박스는 **발행했다**(`published_at` 채워짐):

```
inbound_outbox    inbound.asn.received        published_at=2026-08-06 08:00:42
admin_event_dedupe  inbound.inspection.completed / inbound.putaway.completed /
                    inventory.received / inventory.reserved     ← asn.received 없음
admin_asn_summary   0행      admin_order_summary  0행
admin_inspection_summary 1행  admin_inventory_snapshot 1행
```

🔵 **기동 경합이 아니다.** 스택이 안정된 뒤 ASN 을 하나 더 만들어(201) 60초를 기다려도
`admin_asn_summary` 는 0 이었고 dedupe 에도 추가되지 않았다. 형제 이벤트는 같은 순간
정상 투영된다.

## 파급

- 콘솔 `/wms/inbound` — `callWmsAdmin('/dashboard/asns')` 로 **admin 프로젝션**을 읽는다
  ⇒ 입고가 아무리 생겨도 **영구히 빈 목록**. (inbound-service 의 원시
  `GET /api/v1/inbound/asns` 는 같은 순간 `totalElements=1` 을 낸다 — 데이터는 있다.)
- ASN 취소(`inbound.asn.cancelled`)도 같은 asn 프로젝션에 의존하므로 **입고 프로젝션
  전체가 죽어 있다.**

🔴🔴 **착수 시 정정 — 이 절에 있던 `/wms/outbound` 항목이 틀렸다.**

> ~~콘솔 `/wms/outbound` — 같은 이유로 `/dashboard/orders` 가 비어 있다.~~
> ~~`TASK-BE-581` 의 서술을 정정해야 한다.~~

**콘솔 출고 화면은 admin 프로젝션을 읽지 않는다.** 런타임 로그로 확인했다:
`console-web` 이 `{"msg":"wms_outbound_ok","path":"/orders"}` 를 찍고,
`WMS_OUTBOUND_BASE_URL=http://wms.local/api/v1/outbound` 이므로 상류는
**outbound-service 원시 API** 다. `wms-ops/api/wms-inventory-api.ts` 의
`callWmsAdmin('/dashboard/orders')` 는 **어떤 라우트도 호출하지 않는 죽은 코드**이고,
콘솔이 쓰는 건 이름만 같은 `wms-outbound-ops/api/outbound-api.listOrders` 다. 코드베이스에
`listOrders` 가 **셋** 있어서 정적 grep 이 갈라주지 못했다.

⇒ **`/wms/outbound` 가 비는 원인은 `TASK-BE-581`(테넌트 스코프) 이며 이 티켓이 아니다.**
BE-581 에 넣었던 정정은 되돌렸다. 이 티켓이 고치는 화면은 **`/wms/inbound` 하나**이고,
`admin_order_summary` 는 이제 차지만 **오늘 그것을 렌더하는 화면은 없다**
([[feedback_data_nobody_renders_is_the_prior_question]]).

## 왜 초록 스위트가 못 잡았나 (가설 — AC-2 가 확인한다)

컨슈머 테스트는 토픽 이름을 **테스트가 직접 정해** 리스너에 밀어 넣는다. 발행자와
구독자의 이름이 **같은 실행 안에서 만난 적이 없으면** 이 어긋남은 어떤 단위/슬라이스
테스트에도 잡히지 않는다.

---

# Goal

발행 토픽과 구독 토픽이 **한 출처에서 나온다.** 콘솔 `/wms/inbound` · `/wms/outbound`
목록이 실제 데이터로 찬다.

---

# Scope

## In Scope

- `admin-service` 의 `admin.projection.kafka.topics.inbound-asn` · `outbound-order`
- 또는 발행자 쪽 `TopicResolver` — **어느 쪽을 움직일지가 이 티켓의 판단**
- 어긋남을 다시 못 생기게 하는 가드 또는 테스트
- 이미 발행됐지만 소비되지 않은 이벤트의 **백필 여부** 판단

## Out of Scope

- 다른 도메인(scm/erp/finance)의 같은 패턴 — 미측정. AC-4 가 세기만 한다

---

# Acceptance Criteria

- [x] **AC-0 (재측정)** — 재측정했고 **어긋남은 2개가 아니라 4개**였다. 판정은
      토픽 존재가 아니라 **오프셋**과, 그보다 더 권위 있는 **컨슈머 그룹 실제 배정**
      (`kafka-consumer-groups --describe`)으로 했다.

      ```
      발행 O / 구독 X   wms.inbound.asn.received.v1        msgs=2
                       wms.inbound.putaway.instructed.v1  msgs=1
                       wms.outbound.order.received.v1     msgs=1
                       wms.outbound.picking.requested.v1  msgs=1
      구독 O / 발행 X   wms.inbound.asn.v1  wms.outbound.order.v1   (둘 다 msgs=0)
      ```

      🔵 계측 함정 2개를 실제로 밟았다: `kafka-topics`/`GetOffsetShell` 은 이
      이미지의 PATH·클래스패스에 **없고**(`kafka-get-offsets.sh` 가 맞다), Git Bash 가
      `/opt/...` 를 Windows 경로로 바꿔 `exec` 를 깬다(`sh -c` 로 감싸야 한다). 둘 다
      **빈 출력 + 비-0 종료** 라 rc 를 안 봤으면 "0건" 으로 오독했다.

- [x] **AC-1 (방향 결정) — admin 쪽을 고친다. 근거는 가역성이 아니라 계약이다.**

      `inbound-events.md § Topic Layout` / `outbound-events.md § Topic Layout` 이
      **발행 측 권위** 이고, 거기엔 이벤트 타입당 토픽 하나가 명시돼 있다. 문제의 두
      이름은 `admin-events.md` 가 "documentation convenience" 로 만든 **개념적 롤업**
      이고, 그 각주는 심지어 *"The ProjectionConsumer in admin-service listens on all
      three split topics"* 라고 **하지도 않는 일을 사실처럼** 적어 두었다. 구현이 그
      롤업 이름을 문자 그대로 구독했다.

      ⇒ 스펙 변경은 필요 없다. **구현을 스펙에 맞춘다.** 이름 바꾸기 한 줄이 아니라
      **분리된 토픽 전부를 구독**하는 것이 맞는 수정이다(발행자 변경은 브레이킹인 데다
      계약에 반한다). `admin-events.md` 의 롤업 행과 각주는 **삭제**했다 — 실재하지 않는
      토픽 이름은 구독 목록에 있어선 안 된다.

- [x] **AC-2 (왜 못 잡았나)** — 가설이 맞았다. `InboundProjectionKafkaIT` /
      `OutboundProjectionKafkaIT` 는 `kafkaTemplate.send("wms.inbound.asn.v1", …)` 처럼
      **테스트가 토픽 이름을 직접 정해** 넣는다. 발행자의 `TopicResolver` 와 구독자의
      이름이 **같은 실행 안에서 만난 적이 없다.**

      구멍을 메운 것: (1) 두 IT 를 **실제 발행 토픽**으로 옮겼고, (2) 새 단위 가드
      `ProjectionTopicWiringTest` 가 ① 매핑된 모든 토픽이 발행자 규칙
      (`wms.<eventType>.v1`, master 는 aggregate fold, inventory 는 alert 예외)의
      산출물인지, ② `@KafkaListener` 구독 집합(플레이스홀더를 **실제
      `application.yml`** 로 해소)이 그 매핑과 정확히 같은지를 검사한다.

      🔴 **가드가 무는지 확인했다**: 결함을 `application.yml` + `TopicEventTypeMap`
      양쪽에 되돌려 넣고 돌리니 **두 테스트 다 FAILED (rc=1)**. 복구 후 재실행 rc=0.

- [x] **AC-3 (라이브)** — 200 이 아니라 **원소 수**로 판정했다. `/wms/inbound` 는 찼고,
      `/wms/outbound` 는 **이 티켓의 범위가 아니었다**(위 파급 정정 참조).

      ```
      admin_asn_summary  0 → 2행 ·  admin_order_summary  0 → 1행   (DB 실측)
      GET /api/wms/inbound/asns  (콘솔 BFF)  elements 0 → 2   ✅
      GET /api/wms/inventory                 elements 1       (변화 없음, 정상)
      GET /api/wms/outbound                  elements 0       ← TASK-BE-581
      GET /api/wms/operations/projection-status  403          ← 권한, 별개
      ```

      🔴 **재배포 중 내가 만든 오탐 하나**: `demo.env` 를 source 하지 않고 compose 를
      돌렸더니 admin 이 `OIDC_ISSUER_URL` 을 compose 기본값
      (`http://iam-gateway-service:8080`)으로 잡아 **모든 `/dashboard/*` 가 401** 이었다.
      토큰의 `iss` 는 `http://iam.local` 이다. 형제 컨테이너의 env 와 대조해 잡았다 —
      **데모 스택 재배포는 반드시 `set -a; source infra/demo/demo.env`** 를 거쳐야 한다.

- [x] **AC-4 (형제 전수)** — admin 의 구독 25개 + 발행 측 전수 대조. 결과:
      **어긋남 4개(모두 이 티켓에서 수정), 나머지 21개 일치.** 다른 도메인
      (scm/erp/finance)의 같은 패턴은 **미측정 — 0건이 아니라 세지 않았다**(범위 밖).
      부수적으로 발견해 정리한 것: `kafka-init` 이 **아무도 쓰지 않는 토픽 9개**
      (`wms.inbound.{asn,inspection,putaway}.v1`,
      `wms.outbound.{order,picking,packing,shipped}.v1`,
      `wms.alert.{low-stock,anomaly-detected}`)를 만들고 있었고, 정작 실제 발행 토픽
      12개는 **auto-create 로만** 존재했다. 목록을 계약 기준으로 다시 썼다.

- [x] **AC-5 (백필) — 아무것도 할 필요가 없었다. 재기동만으로 복구된다.**
      admin 은 `auto-offset-reset: earliest` 이고, 기존 컨슈머 그룹에 **새로 추가된
      토픽-파티션에는 커밋된 오프셋이 없으므로** 그 규칙이 적용돼 offset 0 부터 읽는다.
      실측: 재기동 직후 `admin_asn_summary=2`, `admin_order_summary=1`, 신규 구독 전
      토픽 **lag=0**, `admin_event_dedupe` 에 8개 이벤트 타입. 재발행도, 수동 되감기도
      불필요. 🔵 단, Kafka 보존기간(7일)을 넘긴 이벤트는 복구되지 않는다 — 데모에서는
      시드를 다시 돌리면 된다.

---

# Related Specs

- `projects/wms-platform/specs/services/admin-service/architecture.md`
- `projects/wms-platform/specs/contracts/events/` — 이벤트·토픽 계약

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` (`/dashboard/asns`)

# Edge Cases

- `kafka-init` 이 **양쪽 토픽을 다 만든다** — 그래서 "토픽이 없다" 는 증상이 나지 않고
  조용히 빈 목록만 나온다. 토픽 생성 목록도 함께 정리할 대상이다
- 발행자 이름을 바꾸면 기존 `.dlq` 토픽 이름과도 갈린다

# Failure Scenarios

- **토픽 존재 확인만 하고 통과 판정** → 둘 다 존재한다. AC-0 이 막는다
- **admin 이름만 고치고 백필을 안 함** → 화면은 그때부터 들어오는 것만 보인다.
  데모에서는 시드를 다시 돌리면 되지만 **그 사실을 적어야** 한다. AC-5
- **발행자를 고쳐 브레이킹** → 다른 컨슈머가 조용히 굶는다(지금 admin 이 그렇듯).
  AC-1 이 가역성을 먼저 따진다

# Definition of Done

- [x] 대조표 재측정 + 방향 결정 근거 (AC-0 / AC-1 — 근거는 계약 문서)
- [x] 수정 + 어긋남 재발 방지 가드 (`ProjectionTopicWiringTest`, **문다** 확인)
- [x] 콘솔 라이브 증거 (원소 수 대조) — `/wms/inbound` 0 → 2.
      🔴 `/wms/outbound` 는 이 티켓 범위가 아님이 실측으로 드러났다 → `TASK-BE-581`
- [x] 형제 전수 결과 기록 (25개 중 4개 어긋남; 타 도메인은 미측정이라고 명시)
- [x] Ready for review
