# Task ID

TASK-BE-582

# Title

admin-service 의 프로젝션이 **아무도 발행하지 않는 토픽 2개**를 구독한다 — 콘솔 입고·출고 목록이 영구히 빈다

# Status

ready

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
- 콘솔 `/wms/outbound` — 같은 이유로 `/dashboard/orders` 가 비어 있다.
- ASN 취소(`inbound.asn.cancelled`)도 같은 asn 프로젝션에 의존하므로 **입고 프로젝션
  전체가 죽어 있다.**

🔴 **`TASK-BE-581` 의 서술을 정정해야 한다.** 그 티켓은 `/wms/outbound` 가 비는 이유를
테넌트 스코프(`tenant_id=NULL` vs restricted 조회)로 돌렸다. 그 사실 자체는 맞지만
**콘솔 화면은 그 엔드포인트를 쓰지 않는다** — admin 프로젝션을 읽는다. 즉 콘솔이 비는
직접 원인은 이 티켓이고, BE-581 은 **원시 API 표면의 별개 결함**이다.

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

- [ ] **AC-0 (재측정)** — 위 표를 다시 만든다. 🔴 **토픽 존재 여부로 판정하지 마라** —
      둘 다 존재한다(kafka-init 이 만든다). **오프셋(메시지 수)** 으로 물어야 한다.
      🔵 `kafka-topics` 는 이 이미지(`apache/kafka:3.7.0`)의 PATH 에 **없다**
      (`/opt/kafka/bin/kafka-topics.sh`) — 실패한 명령의 빈 출력을 "0건" 으로 읽지 말 것
- [ ] **AC-1 (방향 결정)** — admin 의 이름을 고칠지, 발행자의 이름을 고칠지 정하고
      근거를 적는다. 🔴 **발행자를 고치면 토픽 이름이 바뀌는 브레이킹 변경**이다
      (기존 컨슈머·DLQ·운영 대시보드). admin 쪽 한 줄이 가역적이다
- [ ] **AC-2 (왜 못 잡았나)** — 기존 테스트가 이 어긋남을 **왜** 통과시켰는지 확인한다.
      가설(테스트가 토픽 이름을 직접 정한다)을 코드로 확인하고, 그 구멍을 메운다
- [ ] **AC-3 (라이브)** — 콘솔 `/wms/inbound` · `/wms/outbound` 가 브라우저에서
      **비어 있지 않다.** 🔴 200 은 판정 근거가 아니다 — 원소 수를 DB 실측과 대조한다
- [ ] **AC-4 (형제 전수)** — admin 의 나머지 구독 + 다른 도메인의 프로젝션 배선을
      **전수 대조**한다. 0건이면 "0건" 이라고 적는다
- [ ] **AC-5 (백필)** — 이미 흘러간 이벤트를 되살릴지 정한다. 컨슈머 그룹 오프셋을
      earliest 로 되감으면 되는지, 아니면 재발행이 필요한지

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

- [ ] 대조표 재측정 + 방향 결정 근거
- [ ] 수정 + 어긋남 재발 방지 테스트/가드
- [ ] 콘솔 두 화면 라이브 증거 (원소 수 대조)
- [ ] 형제 전수 결과 기록
- [ ] Ready for review
