# Task ID

TASK-MONO-683

# Title

🔴 데모 시드가 SKU→공급사 매핑에 **UUID** 를 넣는다 — 보충 제안을 승인해 발주를 확정하면 wms 는 그 값을 **코드**로 찾다 놓치고 DLT 로 버린다(잠복)

# Status

in-progress

# Owner

monorepo

# Task Tags

- demo
- seed
- cross-project
- contract-drift

---

# Goal

`TASK-MONO-677` 구현 중(2026-09-15) 발견한 **잠복 결함**의 집이다. 677 은 발주 화면의 공급사 **표시**만 고쳤고
(id·code 둘 다로 조인), 아래 **흐름**은 고치지 않았다.

`ADR-MONO-050` D9 는 *"서비스 간 식별자는 코드"* 이고, `scm.procurement.inbound-expected.v1` 의 `supplierId` 는
공급사 **CODE** 로 정의돼 있다(`scm-procurement-events.md:414`). 그런데 데모 시드는 그 자리에 **UUID** 를 넣는다.

| 단계 | 무엇이 일어나나 | 근거(677 구현 에이전트 보고 — 🔴 AC-0 이 다시 읽는다) |
|---|---|---|
| 시드 | 공급사를 등록한 뒤 **서버 발급 UUID** 를 `sku_supplier_map.supplier_id` 에 쓴다 | `infra/demo/seed/seed-scm.sh:141-143` |
| 운영자 | 보충 제안을 승인 → `POST /po/from-suggestion` → 확정 | `procurement-api.md` § from-suggestion (값을 검증하지 않는다) — 🔵 **AC-0 정정**: 승인은 **DRAFT 까지만** 만든다(`procurement-api.md:576` «no auto-SUBMIT»). 확정까지는 운영자 submit → 공급사 ack(ACKNOWLEDGED) → 운영자 confirm 세 걸음이고, 이벤트는 confirm 안에서 난다(`PurchaseOrderApplicationService.java:274,289`) |
| 발행 | inbound-expected 이벤트의 `supplierId` = 그 UUID | `OutboxProcurementEventPublisher.java:136` |
| wms | `findPartnerByCode(<UUID>)` 가 빈다 → 재시도 없이 DLT | `CreateScmInboundExpectationService.java:139-141` · `InboundExpectationRejectedException.java:8-11` — 🔵 **AC-0 보강**: 재시도 제외를 실제로 거는 줄은 `KafkaConsumerConfig.java:71`(`addNotRetryableExceptions(IllegalArgumentException)`)이고, 8-11 은 그 사실을 적은 javadoc 이다. 또 판정 **순서**가 창고(89→127-129) → 업무중복(93) → 공급사(99→139-141) → SKU(161-163) 라서 공급사만 고쳐도 SKU 에서 다시 걸린다(AC-2) |

🔵 **왜 지금은 안 터지나**: 시드는 제안을 **승인하지 않고**, 시드가 직접 만드는 발주(`POST /po`)는 목적지가 없어
`isWmsWarehouseDestination()` 이 거짓이라 이벤트를 안 낸다(`PurchaseOrder.java:368-372`). 또 wms ref 테이블이
비어 있던 문제(`TASK-MONO-675`)가 이 경로를 먼저 막았을 것이다. ⇒ **데모 시연자가 «보충 제안 승인» 을 한 번
누르는 순간** 드러난다.

> 🔴 **AC-0 정정 (2026-09-16) — «한 번 누르는 순간» 은 측정된 적이 없다.** 누를 **제안이 데모에 생기는지**부터
> 열려 있다. 제안은 POST 로 만들지 않고 두 경로로만 생긴다: wms 저재고 알림(`EvaluateReorderUseCase.java:107`)
> 또는 IVS 스냅샷 야간 스윕(`SweepReorderUseCase.java:125`). 둘 다 **wms/IVS 가 가진 skuCode** 로 매핑을 찾는데,
> 시드가 매핑한 SKU 는 `SKU-DEMO-A1`/`SKU-DEMO-B2`(`seed-scm.sh:68-69`)이고 wms dev 시드엔 없다(AC-2 표).
> 시드 자신의 로그도 *"60초 동안 0건"*(`seed-scm.sh:183`) 이다. 그래서 이 결함은 **«제안이 생기면» 반드시
> 터지는** 것(AC-1 이 실측)이지, «시연자가 누르면» 터지는 것이 아니다 — 지금 데모에선 그 앞에서 흐름이 끊긴다.
> 이건 AC-4 가 볼 것이고, 아래 선택지의 크기를 바꾼다(공급사 코드만이 아니라 **SKU 코드**도 맞아야 한다).
>
> 🔴 **AC-0 정정 — `TASK-MONO-675` 는 이 경로를 막지 않는다.** wms 쪽 판정은 inbound-service 의
> `MasterReadModelPort`(inbound DB 의 `partner_snapshot`·`sku_snapshot`·`warehouse_snapshot`)를 읽는다 —
> `ScmInboundExpectedConsumerIT` 가 바로 그 테이블에 행을 넣어 통과시킨다. 675 의 빈 테이블은 admin-service 의
> ref 테이블이다. inbound 의 snapshot 은 `R__seed_dev_masterref.sql` 이 이미 채운다(아래 AC-2 표).

곁가지로 같은 뿌리의 흔적 셋(셋째는 AC-0 이 찾았다):
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md:61,139` 가 아직 `"supplierId": "uuid"` 예시 — D9 와 모순.
- 콘솔 `features/scm-replenishment/.../ReplenishmentTable.tsx:104` 가 `supplierId` 를 원문으로 그린다.
- 🔵 **AC-0 추가 (셋째)**: `procurement-api.md:589` — `POST /po/from-suggestion` 요청 예시의 `supplierId` 도
  UUID(`"9b1d4a8c-…"`)다. 같은 파일 `:152-157`(677 규칙)과 `scm-procurement-events.md:89,414` 는 이 경로의 값을
  **CODE** 라고 적는다 ⇒ 같은 D9 모순. (`scm-procurement-events.md:123,159,200,245,285` 의 UUID 예시는 운영자
  발주 공통 이벤트라 모순이 아니다 — `:89` 가 «operator-authored POs carry whatever» 라고 허용한다.)

---

# Scope

## 포함

- 시드의 `sku_supplier_map.supplier_id` 를 D9 대로 **코드**로 맞출지, 아니면 다르게 할지 고르고 고친다.
- 🔴 코드로 맞춘다면 **wms 쪽에 그 코드의 파트너가 있는가** 까지 확인한다(scm 공급사 코드 `SUP-DEMO-01` 류 ↔ wms 파트너 시드 `SUP-001` 류 — 이름이 다르면 코드로 바꿔도 역시 빈다).
- `demand-planning-api.md` 예시 정정.
- `ReplenishmentTable.tsx:104` 표시 — 677 과 같은 규칙(`masterRefLabel` + `data-master-ref`)을 쓸지 판단.

## 제외

- 발주 화면 공급사 표시(`TASK-MONO-677` — 끝났다).
- D9 결정 자체의 재논의(ADR 축).
- wms admin ref 시드(`TASK-MONO-675`).

---

# Acceptance Criteria

- [x] **AC-0 — 재측정.** 위 표의 file:line 넷과 곁가지 둘을 **다시 읽어라** — 677 구현 에이전트의 보고이지 오케스트레이터가 연 것이 아니다. 하나라도 틀리면 그 칸부터 정정한다.
- [x] **AC-1 — 잠복을 «실측» 으로 확인하거나 기각한다.** 저장소에서 재현할 수 있는 가장 싼 방법(서비스 IT 또는 컨슈머 단위 테스트에 시드와 같은 UUID 를 넣기)으로 «DLT 로 간다» 를 보인다. 🔴 추론만으로 결함이라 적지 마라.
- [x] **AC-2 — 두 서비스의 코드 공간을 대조한다.** scm 공급사 코드와 wms 파트너 코드가 **같은 값으로 만나는가**. 안 만나면 «시드를 코드로 바꾼다» 만으로는 안 고쳐진다 — 그 사실과 선택지를 적고, 선택이 필요하면 소유자에게 묻는다(🔴 추천을 결정으로 적지 마라).
- [ ] **AC-3 — 고친다(계약 먼저).** `demand-planning-api.md` 예시를 먼저 고치고, 시드·표시를 고친다. bite: 고친 시드를 되돌리면 AC-1 의 테스트가 빨개진다.
- [ ] **AC-4 — 판정.** 가능하면 데모 창에서 «제안 승인 → 확정 → wms 인바운드 예정 생성» 을 한 번 끝까지 본다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

---

# Related Specs

- `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` § 7 D9
- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` § inbound-expected (`supplierId` = CODE)
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md` (예시가 `uuid`)
- `projects/scm-platform/specs/contracts/http/procurement-api.md` § supplier reference fields (`TASK-MONO-677`)

# Related Contracts

- `scm.procurement.inbound-expected.v1` — 바꾸지 않는다(이미 CODE 로 정의됨). 🔴 바뀌어야 한다는 결론이 나면 ADR 축으로 올린다.
- `demand-planning-api.md` — 예시 정정.

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 운영자가 콘솔 `SupplierMapForm` 으로 UUID 를 넣는다 | 폼이 무엇을 받는지부터 — D9 라면 코드를 받아야 한다. 🔴 폼 문구가 «free-text/uuid» 였던 흔적이 677 에서 정리됐다 |
| scm 코드 ↔ wms 코드가 다른 이름 체계 | AC-2 — 시드 한쪽만 고치면 여전히 빈다 |
| 이미 확정된 발주가 UUID 를 들고 DLT 에 있다 | 신선 볼륨 데모라 없다. 로컬 기존 볼륨이면 그렇게 적는다 |

# Failure Scenarios

1. **시드만 코드로 바꾸고 wms 파트너 코드를 안 본다** → 이름 체계가 다르면 똑같이 DLT.
2. **추론으로 «결함이다» 를 적고 고친다** → AC-1 이 막는다. 잠복 결함은 재현이 판정이다.
3. **표시(ReplenishmentTable)만 고치고 닫는다** → 화면은 친절해지고 흐름은 그대로 깨진다.

---

# Phase 1 측정 (2026-09-16)

## AC-1 — 실측: UUID 는 wms 에서 거절되고, 재시도 없이 DLT 로 간다 ✅

- **테스트**: `projects/wms-platform/apps/inbound-service/src/test/java/com/wms/inbound/adapter/in/messaging/scm/ScmInboundExpectedDemoSeedShapeDltTest.java`
  - 진짜인 것: `ScmInboundExpectedEventParser` · `ScmInboundExpectedConsumer` · `CreateScmInboundExpectationService` ·
    `KafkaConsumerConfig#kafkaErrorHandler` 가 만드는 `DefaultErrorHandler`(리플렉션으로 **그 빈 메서드를** 부른다 — 사본 아님).
  - 마스터 read model = **wms dev 시드 파일 자체**(`db/seed/R__seed_dev_masterref.sql`)를 읽어 채운 가짜. 목은 영속·아웃박스 포트와 Kafka 프로듀서뿐.
  - `seedShapedSupplierUuid_isRejectedByWms_andGoesToDltWithoutRetry` — `supplierId`=UuidV7(프로크루어먼트가 발급하는 모양) ·
    창고 `WH01` · SKU `SKU-DEMO-A1` → `InboundExpectationRejectedException("unknown supplierId=<uuid>…")`, ASN 저장 0회 →
    같은 실패를 핸들러의 `handleRemaining` 에 넘기면 `….inbound-expected.v1.DLT` 로 **1회 send**, `consumer.seek` **0회**(= 재전달 없음).
  - 대조군 `control_retryableFailure_isSeekedBackForRetry_notSentToDlt` — `IllegalStateException` 은 send 0회 · seek 1회 · `RecordInRetryException`.
    이게 없으면 «거절이라 즉시 DLT» 와 «모든 실패가 즉시 DLT» 를 못 가른다.
  - 비공허 가드 `wmsDevSeed_isLoadedNonVacuously` — 가짜가 `WH01`/`SUP-001`/`SKU-APPLE-001` 을 실제로 읽었는지.
- **실행**: `./gradlew :projects:wms-platform:apps:inbound-service:test --tests 'com.wms.inbound.adapter.in.messaging.scm.ScmInboundExpectedDemoSeedShapeDltTest' --offline` → **rc=0**, 결과 XML `tests="5" failures="0" errors="0"`.
- **bite**: `KafkaConsumerConfig.java:71` 의 `addNotRetryableExceptions(...)` 한 줄을 주석 처리 → 같은 명령 **rc=1**, 실패 **정확히 1건** =
  `seedShapedSupplierUuid_…`(`RecordInRetryException` ← `InboundExpectationRejectedException`). 원복 후 `git diff` 0.
- 🔴 **한계(정직하게)**: Docker 가 이 호스트에서 안 떠 있어(`dockerDesktopLinuxEngine` 파이프 없음) Testcontainers IT 는 못 돌렸다 — 브로커·DB 는 안 거쳤다.
  scm 쪽 값(`SUP-DEMO-01`·`SKU-DEMO-A1`)은 `seed-scm.sh:67-68` 에서 **옮겨 적은** 상수이고, 테스트는 `seed-scm.sh` 를 읽지 않는다
  ⇒ AC-3 의 «시드를 되돌리면 이 테스트가 빨개진다» 는 **이 테스트로는 성립하지 않는다**(Phase 2 에서 연결 방식을 정해야 한다).
  scm 쪽 «UUID 가 매핑 → 제안 → PO → 이벤트로 그대로 흐른다» 는 코드 읽기(`ProcurementDraftPoClient.java:62` · `PurchaseOrderApplicationService.java:173,289` · `OutboxProcurementEventPublisher.java:136`)이지 실행이 아니다.

## AC-2 — 두 코드 공간은 **만나지 않는다** (공급사도, SKU 도) ✅

| 축 | scm 데모 시드 | wms dev 시드 (inbound 가 판정에 쓰는 것) | 만나나 |
|---|---|---|---|
| 공급사 | `SUP-DEMO-01` — `infra/demo/seed/seed-scm.sh:67` (등록: `:97`, 매핑엔 **UUID**: `:109-111,141-143`) | `SUP-001` — inbound `R__seed_dev_masterref.sql:95` · master `R__05_seed_dev_partners.sql:28` · admin `R__seed_dev_masterref.sql:224`. `BOTH-001`(canSupply)은 master `:62`·admin `:242` 에만 있고 **inbound snapshot 엔 없다**(master 이벤트 투영이 와야 생김). `CUST-001` 은 공급 불가 | ❌ |
| SKU | `SKU-DEMO-A1` `:68` · `SKU-DEMO-B2` `:69` | inbound `SKU-APPLE-001` `:70` · master `R__04_seed_dev_skus.sql:27,46,65` = `SKU-BOX-001`/`SKU-EA-001`/`SKU-APPLE-001` | ❌ |
| 창고 | (시드가 안 정한다 — 알림/IVS 가 준다) | `WH01` — inbound `:31` · master `R__01_seed_dev_warehouse.sql:31` | 경로상 wms 값이 그대로 흐름 |
| (참고) ERP 공급사 | — | `BP-SUP-001` — `seed-erp.sh:305` (다른 도메인, wms 와 무관) | — |

- 테스트로도 고정: `scmSeedSupplierCode_isAlsoUnknownToWmsDevSeed`(코드 `SUP-DEMO-01` 로 바꿔도 `unknown supplierId`) ·
  `wmsSeedSupplierCode_passesSupplierGate_butScmSeedSku_isUnknownToWms`(`SUP-001` 이면 공급사는 통과, 곧바로 `unknown skuCode=SKU-DEMO-A1`).
- 🔵 코드 공간이 **만나는 곳은 e2e 픽스처뿐**이다: `tests/federation-hardening-e2e/fixtures/seed-wms-inbound.sql:13-27`(`SUP-FED-IE`/`SKU-FED-IE`/`WH-FED-IE`) — D9 가 말한 «양쪽에 같은 코드를 심는 forcing function» 은 e2e 에만 있고 데모 시드엔 없다.
- ⇒ **«시드의 UUID 를 코드로 바꾼다» 만으로는 안 고쳐진다.** 공급사 코드와 SKU 코드를 **한쪽으로 맞추는 선택**이 필요하다 → 아래.

## 선택지 — 소유자 결정 대기

> 🔴 **아래는 결정이 아니다.** D9(`ADR-MONO-050:227`)는 방향만 정했다 — *"scm's `sku_supplier_map.supplier_id` must be seeded
> with the supplier **code** wms's partner master knows"*. **어느 코드로 맞출지**, 그리고 D9 가 말하지 않은 **SKU 축**을 어떻게 할지는 열려 있다.

**ⓐ scm 시드가 wms 의 코드로 간다** — scm 공급사를 `SUP-001` 로 등록하고 매핑 `supplierId`=`"SUP-001"`, 매핑·정책·시드 PO 의 SKU 를 wms 가 가진 `SKU-APPLE-001`(등)로 바꾼다.
- 👍 바꾸는 파일이 `seed-scm.sh` 하나(+계약 예시). wms 세 서비스 시드(master·inbound·admin)가 **이미 `SUP-001`/`SKU-APPLE-001` 로 서로 맞춰져 있다**(`R__05…:15-17` 헤더) — 그 정렬을 안 건드린다. D9 문장과 글자 그대로 일치.
- 👍 `seed-wms.sh` 가 실제로 `SKU-APPLE-001` 을 입고·출고하므로(재고 이벤트가 그 SKU 로 난다) 제안이 생길 **가능성**이 생긴다(🔴 저재고 임계를 넘는지는 미측정 — AC-4).
- 👎 콘솔 `/scm/config`·`/scm/procurement` 에 보이던 `SUP-DEMO-01`/`SKU-DEMO-*` 이름이 바뀐다(677 의 공급사 표시 문구·스크린샷·README 가 이 이름을 인용하는지 grep 필요). 기존 볼륨엔 `SUP-DEMO-01` 공급사 행과 그 id 로 만든 PO 3건이 남는다(신선 볼륨이면 무관).
- 👎 scm 공급사 마스터의 코드가 wms 파트너 코드를 **흉내 내는** 모양이 된다 — 두 마스터가 한 코드를 공유한다는 v1 가정(`scm-procurement-events.md:414` «v1 supplier-code stand-in»)을 데모가 그대로 드러낸다.

**ⓑ wms 시드가 scm 의 코드를 더 갖는다** — wms master·inbound·admin dev 시드 셋에 파트너 `SUP-DEMO-01`(SUPPLIER, ACTIVE)과 SKU `SKU-DEMO-A1`/`SKU-DEMO-B2` 를 추가하고, scm 시드는 매핑만 UUID→`"SUP-DEMO-01"` 로 바꾼다.
- 👍 콘솔 scm 화면의 이름이 그대로다. 데모 전용 이름이 «데모 데이터» 임을 드러낸다.
- 👎 repeatable Flyway 시드 **세 파일**을 같은 UUID 로 맞춰 고쳐야 하고(한 곳만 고치면 조용히 어긋난다 — `seed-wms.sh:245-250` 이 그 사고의 기록), 체크섬이 바뀌어 기존 볼륨에서 재실행된다(`ON CONFLICT DO NOTHING` 이라 안전은 하나 실행은 된다).
- 👎 **UNIQUE 코드 충돌 확인 필요** — 예전 시드가 IT 둘을 깨뜨린 전례. (`SUP-DEMO`/`SKU-DEMO` 는 `projects/wms-platform` 전체에서 이 티켓이 추가한 테스트 1파일 외 0건 — `Grep` 실측 — 이지만 dev 시드를 여는 IT 가 있는지는 Phase 2 에서 확인.)
- 👎 새 SKU 가 wms 에 생겨도 **재고가 0** 이라 그 SKU 의 저재고 알림이 날지는 별개다(ⓐ 보다 제안 발생 가능성이 낮다 — 미측정).
- 👎 wms-platform 프로젝트 파일을 건드리므로 이 루트 티켓의 범위가 커진다(프로젝트 경계).

**ⓒ 최소 정합만 — 코드로만 바꾸고 만남은 따로 연다** — 계약 예시(`demand-planning-api.md:61,139` · `procurement-api.md:589`)와 시드 매핑을 UUID→`"SUP-DEMO-01"` 로 고치고(D9 **형식** 정합), 공급사·SKU 코드 정렬은 별도 티켓으로 기안한다.
- 👍 가장 작다. 계약·시드의 D9 모순(«UUID 를 쓴다»)은 즉시 사라진다.
- 👎 **흐름은 여전히 DLT** — AC-1 의 테스트가 그대로 증명한다(`scmSeedSupplierCode_isAlsoUnknownToWmsDevSeed`). 이 티켓의 제목 결함은 안 고쳐진 채 남는다(Failure Scenario 1 을 의식적으로 택하는 것).

**(검토 후 제외) ⓓ procurement 가 from-suggestion 에서 id→code 로 변환** — D9 는 «시드가 코드를 넣는다» 로 정했고, 코드에서 변환하면 계약(`procurement-api.md:610` «does not FK-validate»)과 ADR 을 바꿔야 한다 ⇒ 이 티켓 범위(제외: «D9 결정 자체의 재논의») 밖.

**추천(결정 아님)**: **ⓐ** — 변경면이 scm 시드 한 파일로 가장 좁고, wms 세 서비스가 이미 서로 맞춰 둔 정렬을 건드리지 않으며, D9 문장과 글자 그대로 맞는다. 제안이 실제로 생길 가능성도 ⓑ 보다 높다(재고가 움직이는 SKU). 대가는 콘솔 scm 화면의 데모 이름이 바뀌는 것.

## 미측정 (Phase 1 이 재지 못한 것)

- 데모에서 보충 제안이 **한 건이라도 생기는가** (어느 선택지든) — 스택이 떠야 한다 → AC-4 / `TASK-MONO-672`.
- Testcontainers IT(브로커·DB 경유) — Docker 미기동.
- scm 쪽 UUID 전파(매핑 → 제안 → from-suggestion → confirm → outbox)의 **실행** 확인 — 코드 읽기만 했다.
- 콘솔 `SupplierMapForm` 이 실제로 무엇을 받는지(Edge Case 1행) — 안 열었다.
- `ReplenishmentTable.tsx:104` 표시 판단 — 선택지가 정해진 뒤 Phase 2.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** (두 서비스의 코드 공간 대조 + 계약·시드·표시 세 층)
