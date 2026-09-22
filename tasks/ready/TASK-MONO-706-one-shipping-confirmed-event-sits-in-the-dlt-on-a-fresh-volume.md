# Task ID

TASK-MONO-706

# Title

🔴 신선 볼륨의 데모에서 **`wms.outbound.shipping.confirmed.v1.DLT` 에 레코드 1건** — 출고 확정 이벤트 하나를 어떤 소비자가 처리하지 못했다

# Status

ready

# Owner

monorepo

# Task Tags

- wms
- kafka
- dlt
- demo-seed

---

# Goal

2026-09-17 UTC 둘째 데모 창(AMI `af0018aa6`, 인스턴스 교체 = **신선 볼륨**, 시드만 돈 상태)에서 `TASK-MONO-675` AC-1 을 재려고 인스턴스 안에서 읽기 전용 Kafka 조회를 했는데, 범위 밖에서 이것이 보였다:

```
wms.outbound.shipping.confirmed.v1.DLT:0:0
wms.outbound.shipping.confirmed.v1.DLT:1:1
wms.outbound.shipping.confirmed.v1.DLT:2:0
```

⇒ 부팅 후 약 27분(창 시작 16:34:55Z, 조회 ≈17:03Z) 안에 **출고 확정 이벤트 1건이 소비에 실패해 DLT 로 갔다.** 같은 조회에서 `wms-admin-service` 그룹의 `wms.outbound.shipping.confirmed.v1` 파티션 1 은 `CURRENT-OFFSET 1 · LAG 0` 이었다(admin 은 소비했다) ⇒ 실패한 쪽은 **다른 그룹**이다(후보: 같은 조회의 그룹 목록 `wms-outbound-service` · `wms-inbound-service` · `wms-notification-service` · `wms-inbound-scm-expected-v1` · `wms-inventory-service`, 그리고 다른 프로젝트의 소비자 — 🔴 추론으로 고르지 마라).

🔴 이 한 건이 `TASK-MONO-667` 이 2026-09-11 창에서 본 **출고 사가 `STUCK_RECOVERY_FAILED`** 와 같은 뿌리인지는 모른다 — 연결은 AC-0 이 관측으로 한다. 신선 볼륨에서 시드만으로 DLT 가 생긴다는 것은 «매 창마다 재현된다» 일 수 있고, 그러면 데모 화면(출고 · 재고)에도 흔적이 있을 수 있다.

---

# Scope

## 포함

- DLT 레코드의 **헤더**(원 토픽 · 예외 클래스 · 메시지 · 실패한 소비자 그룹)를 읽어 실패 지점을 지목.
- 그 이벤트가 시드의 어느 출고에서 왔는지, 같은 조건이 매 신선 부팅마다 재현되는지.
- 결함이면 고친다(계약/스펙 먼저) · 의도(예: 데모에 없는 하류 서비스)면 소유자에게 표시 방식을 묻는다.

## 제외

- master DLQ(`wms.master.*.v1.dlq`) 오프셋 — `TASK-MONO-675` AC-1 이 들고 있다.
- 출고 사가 전반의 재설계.

---

# Acceptance Criteria

- [x] 🟢 **AC-0 — 실패 지점을 관측으로 지목.** 창에서(읽기 전용) `kafka-console-consumer.sh --topic wms.outbound.shipping.confirmed.v1.DLT --from-beginning --property print.headers=true --max-messages 5` 로 헤더(`kafka_dlt-original-consumer-group` · `kafka_dlt-exception-fqcn` · `kafka_dlt-exception-message` 등)를 읽는다. 🔴 페이로드에 개인정보가 없는지 먼저 보고, 티켓에는 헤더와 키 목록만 적는다. 🔴 명령은 소유자가 실행한다(SSM 은 에이전트에게 막혀 있다). 창이 없으면 ⚪ + `TASK-MONO-672`.
- [ ] **AC-1 — 재현성.** 다음 신선 부팅에서도 같은 DLT 레코드가 생기는가(오프셋 다시 조회). 한 번뿐이면 경합, 매번이면 시드/코드.
- [ ] **AC-2 — 결함/의도 판정 → (결함이면) 고친다 + bite, (의도면) 소유자에게 묻는다.** 로컬 재현(IT)이 되면 그것으로 bite.
- [ ] **AC-3 — 창 판정.** 고친 AMI 의 신선 부팅에서 그 DLT 끝 오프셋이 전 파티션 0 이고, 출고 화면에 `STUCK_*` 가 없는지 본다. 창이 없으면 ⚪ + `TASK-MONO-672`.

---

# Related Specs

- `projects/wms-platform/specs/contracts/events/` — outbound shipping confirmed 이벤트 계약(AC-0 에서 파일 특정)
- `tasks/in-progress/TASK-MONO-675-the-denormalised-codes-are-null-because-the-ref-tables-are-empty.md` § 창 실측(2026-09-17 둘째 창) — 발견 기록
- `tasks/in-progress/TASK-MONO-667-*` — 2026-09-11 출고 `STUCK_RECOVERY_FAILED` 관측

# Related Contracts

- wms outbound 이벤트 계약 · 소비자 쪽 재시도/DLT 정책. 🔴 바꾸면 계약 먼저.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 실패 소비자가 데모에 안 뜬 묶음의 서비스 | «의도» 후보 — 그래도 DLT 가 쌓이는 것을 받아들일지는 소유자 결정 |
| 레코드가 이미 보존기간(24h)으로 사라짐 | 다음 신선 부팅에서 재현(AC-1)으로 대신 |
| 여러 건으로 늘어남 | 부팅 후 경과 시간과 함께 기록 — 주기 작업이 원인일 수 있다 |

# Failure Scenarios

1. **admin 그룹의 LAG 0 을 보고 «문제 없음» 으로 닫는다** → DLT 는 다른 그룹의 실패다.
2. **667 의 `STUCK_RECOVERY_FAILED` 와 같은 뿌리라고 추론으로 묶는다** → AC-0 의 헤더가 판정이다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus 5** (이벤트 소비 실패 원인 지목 · 사가 연관 가능성)

---

# 🔵 창 실측 — 2026-09-18 UTC 둘째 창 (07:57:15Z–08:16:26Z · **17분** / 상한 30분) · AMI `ami-02613b0378621b124`(`af0018aa6`) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 7종(console · console-ecommerce · console-wms · console-scm · console-erp · console-finance · fan) · 소유자 승인 «창 30분, 683 쓰기 포함»

## 2026-09-18 둘째 창 — 같은 SSM 묶음에 실어 넘겼다 (결과 미수령)

AC-0 이 요구한 `kafka-console-consumer.sh --topic wms.outbound.shipping.confirmed.v1.DLT`
(헤더 포함, `--max-messages 1`)를 `TASK-MONO-675` 의 오프셋 조회와 **한 명령으로 묶어**
소유자에게 넘겼다. 🔴 이 세션 안에 출력이 돌아오지 않아 AC-0 은 열려 있다.

🔵 **묶은 것이 옳았다** — 둘 다 같은 `wms-kafka` 컨테이너를 읽고, 창에서 가장 비싼 것은
명령 자체가 아니라 **창을 여는 일**이다.


---

# 🟢 AC-0 — 실패 지점을 읽었다 (2026-09-22 UTC 데모 창 · 분석=Opus 5)

## 🔴 먼저, 이 AC 가 적은 「SSM 은 에이전트에게 막혀 있다」는 **낡은 표였다**

이 칸은 *"🔴 명령은 소유자가 실행한다(SSM 은 에이전트에게 막혀 있다)"* 라고 적혀 있었다.
2026-09-22 에 그냥 시도하니 **통과했다**. ⇒ 그 문장을 ⚪ 의 사유로 쓰지 마라 — 그 자리에서
한 번 시도하고, 정말 막히면 그때 적는다(`TASK-MONO-672` § 14차 창 수확에 같은 정정).

## 오프셋

```
wms.outbound.shipping.confirmed.v1      : 0:1  1:0  2:0
wms.outbound.shipping.confirmed.v1.DLT  : 0:1  1:0  2:0
wms.outbound.shipping.confirmed.v1.dlq  : 0:0
```

⇒ 원본 1건 · DLT 1건. 🔵 `.DLT`(대문자)와 `.dlq`(소문자)가 **둘 다 존재한다** — 이 축을 물을 때
철자를 틀리면 「없음」이 아니라 **「안 물었다」** 가 0 으로 돌아온다.

## 헤더 (개인정보 없음 — 헤더와 키 이름만 적는다)

```
CreateTime : 1790065118911  (= 2026-09-22T08:18:38Z)
eventId    : 01a0c832-0aa7-760f-90dd-4091acf8c014
eventType  : outbound.shipping.confirmed
kafka_dlt-exception-fqcn        : org.springframework.kafka.listener.ListenerExecutionFailedException
kafka_dlt-exception-cause-fqcn  : java.lang.IllegalArgumentException
kafka_dlt-exception-message     : Listener method '…ShippingConfirmedConsumer.handle(String,String)'
    threw exception; shipping.confirmed line (skuId=01910000-…-403, lotId=01910000-…-601)
    has no matching reservation line on reservation d987aea0-b36c-4018-8a34-9952c9b55f25
스택 최말단: ShippingConfirmedConsumer.applyConfirm(ShippingConfirmedConsumer.java:148)
            ← lambda$applyConfirm$2(:150) ← Optional.orElseThrow
```

## 🔴🔴 그리고 헤더가 **예상 밖의 것**을 말한다 — 예약 id 가 두 개다

같은 이벤트(`eventId` 로 대조)의 **페이로드**는 이렇게 말한다:

```
"reservationId": "01a0c831-fb7b-7328-a4c3-870edfdabfd2"   (UUIDv7 · sagaId 와 동일)
"orderNo": "SO-DEMO-0001" · "shipmentNo": "SHP-20260922-9643"
"lines": [{ "skuId": "01910000-…-403", "lotId": "01910000-…-601", "qtyConfirmed": 10 }]
```

🔴 **예외가 말하는 예약(`d987aea0-…`, UUIDv4 꼴)은 페이로드의 예약(`01a0c831-…`, UUIDv7)이 아니다.**
⇒ 컨슈머는 페이로드가 지목한 예약이 아니라 **다른 예약**을 집어 그 예약의 라인과 대조하고 있다.
🔵 그러므로 이 결함의 이름은 «예약 라인이 없다» 가 아니라 **«예약을 잘못 고른다»** 일 가능성이
높다 — 그러나 **그것은 아직 가설이다**(`applyConfirm` 의 조회 키를 읽지 않았다).

## 🟡 AC-1 (재현성) — 절반

- 13차 AMI 의 **신선 볼륨 첫 부팅**(08:04Z)에서 이 레코드가 **또** 생겼다. 12차 볼륨에서 이 티켓이
  기안된 것과 합치면 **서로 다른 AMI·서로 다른 볼륨에서 두 번** = 「한 번뿐인 경합」이 아니다.
- 🔴 그러나 **같은 볼륨의 재기동(09:17Z)에서는 새 레코드가 안 생겼다** — wms 시드가
  `존재  출고 SO-DEMO-0001 이미 SHIPPED` 로 건너뛰기 때문이다(`생성 0 · 기존 2`).
  ⇒ **AC-1 의 「다음 신선 부팅」은 다음 재굽기까지 못 잰다.** 그 전까지는 위 두 표본이 전부다.

## ⚪ 남은 것

- `applyConfirm` 이 예약을 **무엇으로 조회하는지**(코드 독해) — 창 없이 가능하다. AC-2 의 입구.
- 로컬 IT 재현(AC-2 의 bite).
