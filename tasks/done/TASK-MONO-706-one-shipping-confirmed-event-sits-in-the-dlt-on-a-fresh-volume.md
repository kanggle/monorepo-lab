# Task ID

TASK-MONO-706

# Title

🔴 신선 볼륨의 데모에서 **`wms.outbound.shipping.confirmed.v1.DLT` 에 레코드 1건** — 출고 확정 이벤트 하나를 어떤 소비자가 처리하지 못했다

# Status

done (2026-09-24 UTC — 4차원 검증 · impl PR #3971 squash `bb7c13847` · 14차 AMI 창 판정으로 마지막 AC 닫힘) ‖ 직전: review (2026-09-23 UTC — AC-2 닫힘: 소유자 결정 **ⓐ any-lot 폴백** 구현 · 🔴 AC-1·AC-3 은 창 판정(재굽기 뒤 신선 부팅) — 단위 초록으로 닫지 않는다)

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
- [x] 🟢 **AC-2 — 결함/의도 판정 → (결함이면) 고친다 + bite, (의도면) 소유자에게 묻는다.** 로컬 재현(IT)이 되면 그것으로 bite. → **결함**(계약 두 개의 충돌) · 소유자 결정 ⓐ · 아래 § AC-2.
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

---

# 🟢 AC-2 — 결함이다: 계약 두 개가 서로를 부정한다 (2026-09-23 UTC · 창 없음 · 분석=Opus 5.5)

## 🔴 먼저, 위 § AC-0 의 가설 «예약을 잘못 고른다» 는 **거짓이었다**

id 가 둘인 것은 정상 매핑이다. 페이로드 `reservationId` 는 outbound `PickingRequest.id` 이고
컨슈머는 그것으로 `findByPickingRequestId` 를 한다(`ShippingConfirmedConsumer.java:119-120`).
예외 메시지가 찍는 것은 inventory 자신의 PK `reservation.id()` 이고, 그 PK 는
`ReserveStockService.java:269` 의 `UUID.randomUUID()` — **v4 꼴이 그 지문이다.**
⇒ 컨슈머는 **맞는 예약**을 찾았다. 🔵 «v7 ≠ v4» 는 두 id 공간의 차이를 잰 것이지 오선택의 증거가 아니었다.

## 기전 (코드와 시드로 추적 — 전부 저장소 안)

1. 시드는 LOT 추적 SKU 를 `lotNo` 만 주고 입고한다(`infra/demo/seed/seed-wms.sh:66-67,186` — 계약 §2.2 *"lot reconciled later"*) ⇒ 재고 행 `lot_id` **NULL**.
2. 출고 주문 라인 `lotId: null`(`seed-wms.sh:264`) ⇒ `inventory-events.md` §C2 *"Null = any available lot (matches rows with `lot_id IS NULL`)"* ⇒ 예약 라인 `lotId` **NULL**.
3. 피킹 확정에서 운영자가 실물 lot `…601`(`L-20260418-A`)을 고른다(`seed-wms.sh:289,337-341` — 시드 주석이 이 설계를 그대로 적는다).
4. 출하 확정 페이로드 `lotId=…601` → `ShippingConfirmedConsumer.java:146` 의 `Objects.equals(NULL, …601)` = false → `IllegalArgumentException` → DLT.

⇒ **신선 부팅마다 결정론적**이다(경합 아님) — § AC-1 의 두 표본(12차·13차, 서로 다른 AMI·볼륨)과 일치한다.

## 계약 충돌 (HARDSTOP-06 으로 멈추고 소유자에게 물었다)

- 생산자 `outbound-events.md` §5 *"Actual lot picked; may differ from planned if operator substituted"* · §7 *"Actual lot that was shipped (from `PickingConfirmation`)"*.
- 소비자 `inventory-events.md` §C4 *"resolves each shipped line … by `(skuId, lotId)` … no matching reservation line is a hard error"*.
- ⇒ 생산자가 허용하는 정상 흐름(any-lot 예약 → 확정 시 lot 지정)을 소비자 계약이 **영구히 DLT 로** 보낸다.

## 소유자 결정 — **ⓐ** (2026-09-23 UTC)

| | 규칙 | 결과 |
|---|---|---|
| **ⓐ 채택** | 정확 `(skuId, lotId)` 먼저 → 없고 출하 lot 이 non-null 이면 **그 sku 의 any-lot(NULL) 라인이 정확히 하나일 때** 그것 | 이 DLT 해소. 구체 lot 대체(A→B)·모호(any-lot 둘 이상)는 여전히 hard error |
| ⓑ 기각 | sku 유일하면 lot 무시 | 대체 시 **다른 lot 행**을 조용히 차감 |
| ⓒ 기각 | 시드만 고침 | 생산자 계약이 허용하는 흐름이 운영에서 계속 DLT — 결함을 숨긴다 |

## 구현

- **계약 먼저**: `inventory-events.md` §C4 에 **Line matching rule** 3단(정확 → any-lot 폴백 → hard error) + `lines[].lotId` 설명. `outbound-events.md` §7 소비자 기대에 한 줄 포인터.
- `ShippingConfirmedConsumer.matchLine` — 위 규칙 그대로. 🔴 폴백은 **출하 lot 이 non-null 일 때만** 연다(NULL→NULL 은 정확 매칭이 이미 처리).

## bite — 단위 테스트로 (IT 는 안 썼다, 이유 아래)

`ShippingConfirmedConsumerTest` 에 4칸 추가, **고치기 전 트리**에서 돌렸다:

| 칸 | 고치기 전 | 고친 뒤 |
|---|---|---|
| 데모 모양(예약 lot NULL · 출하 lot 지정) → 그 라인으로 확정 | 🔴 **FAIL**(유일한 실패, `10 tests completed, 1 failed`) | 🟢 |
| 정확 매칭이 any-lot 라인보다 우선 | 🟢 | 🟢 |
| 구체 lot 대체(A→B) → hard error 유지 | 🟢 | 🟢 |
| any-lot 라인 둘 → hard error(추측 안 함) | 🟢 | 🟢 |

🔵 초록 셋은 **경계를 핀**하는 칸이다 — 폴백이 넓어지면(ⓑ 쪽으로) 빨개진다. inventory-service 전체 `test` rc=0 · **248 tests / 0 failures / 0 errors / 0 skipped**(결과 XML 41개 합산).

🔴 **IT 를 안 쓴 이유**: 이 컨슈머에는 기존 IT 가 **0개**이고(테스트 트리 grep — 단위 테스트 하나뿐), 결함은 DB·Kafka 가 아니라 **매칭 술어 한 줄**에 있다. 단위 테스트가 실제 `OutboundEventParser` 로 **생산자 와이어 모양 그대로**를 먹이므로 기전을 전부 덮는다. ⇒ 남는 미측정은 «실제 시드 흐름 끝에서 DLT 가 0 인가» 이고 그것은 **AC-3(창)** 이 잰다.

## 🟡 AC-1 에 더한 것

기전이 코드로 확정됐으므로 «매번이면 시드/코드» 의 답은 **코드**다. 다만 AC-1 이 요구한 관측(다음 신선 부팅의 오프셋)은 **고친 AMI** 에서는 «재현되지 않음» 으로만 나온다 ⇒ AC-1 과 AC-3 은 같은 창에서 **한 조회**로 닫힌다(고친 AMI 의 신선 부팅에서 DLT 끝 오프셋 전 파티션 0).

## 🔴 AC-3 에 얹을 관측 하나 (가설 — 추론으로 묶지 마라)

이 DLT 는 `inventory.confirmed` 가 **영영 안 나간다**는 뜻이다 ⇒ 출고 사가가 `SHIPPED` 에 머문다 ⇒ 계약 §7 의 스위퍼(`SHIPPED` 5분 초과 재발행)가 돈다. `TASK-MONO-667` 의 `STUCK_RECOVERY_FAILED` 가 **이 사슬의 끝**일 수 있다. 🔴 이것은 **가설**이다 — AC-3 이 이미 «출고 화면에 `STUCK_*` 가 없는가» 를 보므로, 고친 AMI 에서 그것이 사라지면 관측으로 연결되고, 남으면 **다른 뿌리**다.

## 후속

- 구체 lot 대체(예약 lot A → 운영자가 B 를 집음)는 생산자 계약이 허용하는데 여전히 hard error → DLT 다. ⓐ 가 **의도적으로 남긴** 구멍이고 정책(어느 재고 행을 차감하나)은 이 티켓 범위 밖 ⇒ **`TASK-MONO-724`** 로 기안.
- 🔴 **재굽기 묶음에 넣어라** — 이 수정이 이미지에 없으면 다음 창은 옛 코드를 잰다(`TASK-MONO-672` 의 재굽기 표).

---

# 🟢 AC-1 · AC-3 — 창 판정 PASS (2026-09-24 UTC · 14차 AMI `ami-03789993d93a320c1` · RepoCommit `f1da21800` · 신선 볼륨)

인스턴스 교체(`terraform apply`, `i-008d1ac1ce23665ad`) 뒤 첫 부팅. wms 묶음은 05:5xZ 에 추가 기동, 시드가 `SO-DEMO-0001` 을 출고까지 몰았다(`outbound_order.status=SHIPPED` 05:57:10Z).

```
wms.outbound.shipping.confirmed.v1      : 0:1  1:0  2:0        ← 원본 1건 (13차와 같다)
wms.outbound.shipping.confirmed.v1.DLT  : 토픽 없음            ← DLT 로 보낸 적이 한 번도 없다
wms.outbound.shipping.confirmed.v1.dlq  : 0:0
컨슈머 그룹 wms-inventory-service       : p0 cur=1 end=1 lag=0  ← «안 물었다» 가 아니라 «소비했다»
wms.inventory.confirmed.v1              : 0:0  1:1  2:0        ← 13차엔 영영 안 나갔던 그 이벤트
outbound_saga                           : COMPLETED 1건 (05:57:11Z) · STUCK_* 0
```

- 🔴 **철자 대조**: 토픽 목록을 `--list` 로 직접 읽었다 — `.dlq`(소문자)는 있고 `.DLT`(대문자)는 **존재하지 않는다**. 0 을 «안 물었다» 로 읽지 않도록 컨슈머 오프셋을 함께 봤다.
- `SKU-APPLE-001` 재고 행(lot NULL)은 `available_qty=85`, `updated_by=system:shipping-confirmed-consumer` — ⓐ 의 any-lot 폴백 라인이 실제로 차감됐다.

## 🔵 AC-3 의 가설(667)에 대한 관측

STUCK_* 가 **없다** — 사가가 `COMPLETED` 로 끝나 스위퍼가 잡을 `SHIPPED` 행이 없다. ⇒ 가설 «667 의 `STUCK_RECOVERY_FAILED` 가 이 DLT 사슬의 끝» 과 **관측이 일치**한다(고친 AMI 에서 사라졌다). 🔴 이것은 한 번의 신선 부팅 표본이고, 667 의 원 관측(2026-09-11)을 재현한 것은 아니다 — 667 쪽에서 «같은 뿌리» 로 닫을지는 그 티켓의 판정이다.

⇒ **AC-0~AC-3 전부 닫힘.**
