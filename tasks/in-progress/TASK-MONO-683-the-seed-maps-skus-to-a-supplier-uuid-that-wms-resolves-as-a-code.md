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
- [x] **AC-3 — 고친다(계약 먼저).** `demand-planning-api.md` 예시를 먼저 고치고, 시드·표시를 고친다. bite: 고친 시드를 되돌리면 AC-1 의 테스트가 빨개진다. → § Phase 2 (bite 둘 다 rc=1).
- [ ] ⚪ **AC-4 — 판정.** → **측정 불가(데모 창 없음), 갈 곳 = `TASK-MONO-672` § 항목 5 (2026-09-16 수령).** 이 칸은 체크하지 않는다 — 판정은 거기서 난다. 가능하면 데모 창에서 «제안 승인 → 확정 → wms 인바운드 예정 생성» 을 한 번 끝까지 본다. 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).

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

## 선택지 — 소유자 결정 대기 → ✅ 해소 (2026-09-16)

> ✅ **소유자 결정 기록 (2026-09-16 UTC, 원문 그대로)**
>
> - **질문**: «어느 쪽으로 맞출까요?»
> - **답**: «ⓐ scm 시드를 wms 코드로 (추천)»
>
> 아래 선택지는 **지우지 않고 남긴다** — 결정의 입력이었던 trade-off 기록이다. ⓑ·ⓒ·ⓓ 는 이 결정으로 **채택되지 않았다**.
> 구현은 아래 § Phase 2.

> 🔴 **(결정 전 기록) 아래는 결정이 아니다.** D9(`ADR-MONO-050:227`)는 방향만 정했다 — *"scm's `sku_supplier_map.supplier_id` must be seeded
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

# Phase 2 구현 (2026-09-16, 결정 ⓐ)

> 🔵 위 Phase 1 절은 **당시 기록**이다. Phase 2 에서 테스트를 시드에 묶으면서 이름·구성이 바뀌었다:
> `seedShapedSupplierUuid_…` → `serverIssuedSupplierUuid_isRejectedByWms_andGoesToDltWithoutRetry`(SKU 를 wms 가 아는 값으로 바꿔 공급사만이 거절 사유가 되게 함),
> AC-2 고정 테스트 둘(`scmSeedSupplierCode_…` · `wmsSeedSupplierCode_…`)은 **삭제**하고 시드를 직접 읽는 `demoSeedMapping_resolvesInWmsDevSeed` 로 대체했다
> (옛 값 `SUP-DEMO-01`·`SKU-DEMO-A1` 은 이제 저장소 어느 시드에도 없으므로 그 둘을 붙들어 둘 이유가 없다).

## 1. 계약 먼저

- `projects/scm-platform/specs/contracts/http/demand-planning-api.md:61` (suggestion 예시) · `:139` (매핑 `PUT` 예시) — `"uuid"` → `"SUP-0043"`, 그리고 매핑 절에
  «`supplierId` 는 wms 가 아는 **공급사 코드**, UUID 는 이 엔드포인트는 통과(`@Size(max = 36)`)하고 wms 에서 재시도 없이 거절» 한 문단을 붙였다.
- `projects/scm-platform/specs/contracts/http/procurement-api.md:589` (from-suggestion 요청 예시) — `"9b1d4a8c-…"` → `"SUP-0043"`, 필드 설명에 `supplierId` 줄 추가(§ supplier reference fields rule 1 로 연결).
- 🔵 예시 값을 데모 값(`SUP-001`)이 아니라 `SUP-0043` 으로 둔 이유: 같은 계약군의 이벤트 예시(`scm-procurement-events.md:414,442`)·`MappingRequest` javadoc 이 이미 `SUP-0043` 이다 — 예시끼리 맞춘다.
- `scm.procurement.inbound-expected.v1` 은 **안 바꿨다**(이미 CODE).

## 2. 시드 (`infra/demo/seed/seed-scm.sh`)

- `SUPPLIER_CODE="SUP-001"` · `SKU_A="SKU-APPLE-001"` — wms inbound·master·admin dev 시드 셋이 공유하는 값.
- 매핑 `PUT` 본문의 `supplierId` = **`$SUPPLIER_CODE`**(이전 `$SUPPLIER_ID`). 운영자 발주 `POST /po` 는 계속 **id**(`$SUPPLIER_ID`) — draft 유스케이스가 id 로 찾기 때문이다(`PurchaseOrderApplicationService.java:90`). 두 쓰임을 주석으로 갈라 적었다.
- 🔵 **`SKU_B` 결정 = 없앤다.** 두 번째 wms SKU 후보(`SKU-EA-001`·`SKU-BOX-001`)는 master 시드에만 있고 **inbound read-model 시드엔 없다**
  (inbound `R__seed_dev_masterref.sql` 의 SKU 는 `SKU-APPLE-001` 하나 · master 의 Flyway 행은 이벤트를 안 내 inbound 로 투영되지 않는다),
  `seed-wms.sh` 도 `SKU-APPLE-001` 만 움직인다. 그 SKU 로 매핑을 하나 더 심으면 «확정되면 DLT 로 가는 매핑» 을 하나 더 심는 것이다.
  ⇒ 루프는 `for sku in "$SKU_A"`, 확정 발주(0003)도 `SKU_A`. 로그의 config 분모 `/4` → `/2`.
- 🔴 **재실행 멱등 — 발주 키를 바꿨다.** 서버는 같은 `Idempotency-Key` + 다른 본문을 `422 IDEMPOTENCY_KEY_MISMATCH` 로 거절한다(`IdempotencyExecutor.java:28-29`, TTL 24h `:56`).
  683 이전 볼륨에 24시간 안에 새 시드를 돌리면 옛 키 `seed-scm-po-000N` 이 옛 본문(`SKU-DEMO-*`)을 들고 있어 세 발주가 전부 422 가 된다.
  ⇒ 키 = `seed-scm-po-000N-$SUPPLIER_CODE-$SKU_A`(최대 38자, 컬럼 `VARCHAR(80)` — `V1__init.sql:198`). 같은 코드로 재실행하면 같은 키 → replay 로 수렴(기존 행 수 판정 그대로).
- 나머지 멱등: 공급사 등록은 code 로 수렴(다른 키 + 같은 code = 200) · 매핑/정책은 `PUT` upsert · 전이는 PO id 키. 🔵 683 이전 볼륨에서는 `SUP-001` 이 **새 행**으로
  생기고 옛 `SUP-DEMO-01` 행·옛 매핑·옛 발주는 남는다(지우지 않는다 — 옛 발주가 참조). 신선 볼륨에선 없다.
- `bash -n infra/demo/seed/seed-scm.sh` → rc=0. 🔴 **실제 스택에 대고 돌리지는 않았다**(데모 창 없음).

## 3. 참조 전수 (옛 코드 3종)

`Grep` 패턴 `SUP-DEMO-01|SKU-DEMO-A1|SKU-DEMO-B2|SUP-DEMO|SKU-DEMO`, `node_modules` 제외, 저장소 전체.
🔵 **양성 대조군**: 같은 검색이 수정 전 `infra/demo/seed/seed-scm.sh:67-69` 를 **찾았다** — 0건이 «검색이 안 돈 것» 이 아님을 보인다.

| 위치 | 처리 | 이유 |
|---|---|---|
| `infra/demo/seed/seed-scm.sh:67-69,81-90` | **고침** | 이 티켓의 대상. 옛 이름은 주석의 이력 설명으로만 남김 |
| `projects/wms-platform/…/ScmInboundExpectedDemoSeedShapeDltTest.java` | **고침** | 옛 상수 삭제 → 시드를 읽는다(아래 4.) |
| `projects/scm-platform/…/PurchaseOrderApplicationServiceTest.java` (491-613 등 13곳) · `PurchaseOrderControllerSliceTest.java:322,327` | 둠 | 677 의 id/code 조인 단위 테스트 픽스처 — 목 저장소에 넣는 임의 문자열이지 시드를 읽지 않는다. 값이 무엇이든 단언이 같다 |
| `projects/platform-console/apps/console-web/tests/unit/erp-master-ref-names.test.tsx:742-809` | 둠 | 677 의 발주 공급사 칸 픽스처 — 같은 이유(렌더 규칙 테스트) |
| `projects/scm-platform/apps/procurement-service/…/V6__suppliers_natural_key_code.sql:14` | 둠 | 적용된 Flyway 마이그레이션 — 체크섬이 바뀌면 기존 DB 가 거부한다. 과거 사실 서술 |
| `scripts/console-demo/seed/07-scm-inventory.sql:40` (`SKU-DEMO-001`) | 둠 | 정규식 `SKU-DEMO` 에 걸린 **다른 값** — 콘솔 데모 가짜 데이터, 이 흐름과 무관 |
| `tasks/done/TASK-MONO-510-…:446` · `projects/scm-platform/tasks/done/TASK-SCM-BE-059-…:194,197` · `projects/scm-platform/tasks/INDEX.md:93` | 둠 | `done/` 는 frozen, INDEX 행은 그 done 티켓의 요약 |
| `tasks/in-progress/TASK-MONO-677-…:45,202` | 🔴 **안 고침 — 보고** | 677 AC-3 이 라이브 기대값을 `SUP-DEMO-01 · demo supplier` 로 적고 있다. 683 이후 신선 볼륨의 시드 발주는 **`SUP-001 · demo supplier`** 로 그려진다. 다른 세션의 in-progress 티켓이라 여기서 손대지 않고 오케스트레이터에게 넘긴다 |
| README · 론처 스크린샷 문안 · 콘솔 e2e/fixtures · 다른 시드 | 0건 | 위 검색 결과에 없음 |

`demo supplier`(공급사 이름)도 따로 검색 — 시드 1곳 + 위 677 단위 테스트 셋뿐. 이름은 **안 바꿨다**(결정 범위는 코드).

**scm UNIQUE 충돌**: scm-platform 에 dev/seed Flyway 위치가 **없다**(`apps/*/src/main/resources/db/` = `migration` 만). `INSERT INTO suppliers` 도 0건.
`SUP-001`·`SKU-APPLE-001` 이 scm 테스트 7파일에 나오지만 전부 목 또는 Testcontainers 의 **빈 DB** 에 테스트가 직접 넣는 값이다 —
`seed-scm.sh` 는 데모 스택의 REST 에만 쓰므로 IT DB 에 닿을 경로가 없다.

## 4. bite 를 시드에 묶었다

- `ScmInboundExpectedDemoSeedShapeDltTest#demoSeedMapping_resolvesInWmsDevSeed` — `seed-scm.sh` 텍스트에서 (a) 매핑 `PUT` 본문의 `\"supplierId\":\"$VAR\"` 변수,
  (b) 그 `PUT` 을 부르는 `for sku in …; do` 의 변수들을 뽑아 `NAME="literal"` 로 해석한다. `SUPPLIER_ID` 처럼 리터럴이 없는 서버 발급 변수면 UUIDv7 을 넣는다.
  그 값으로 inbound-expected 이벤트를 만들어 **진짜 consumer + service** 에 넣고, wms dev 시드 read-model 에서 **거절 없이 ASN 이 저장되는지** 단언한다.
  못 찾으면 조용히 통과하지 않고 실패한다(파일 없음 · 변수 없음 · 리터럴 없음 각각 AssertionError).
- 🔴🔴 **첫 bite 가 안 물었다 — 원인은 Gradle 이었다.** 시드를 옛 코드로 되돌렸는데 `rc=0` — 로그에 `inbound-service:test UP-TO-DATE`.
  테스트가 읽는 `infra/…/seed-scm.sh` 는 Gradle 이 모르는 입력이고 `org.gradle.caching=true` 라 **결과를 재사용**했다. 그대로 두면 시드만 바꾼 변경에서 이 검사는 영원히 초록이다.
  ⇒ `projects/wms-platform/apps/inbound-service/build.gradle` 에 `tasks.withType(Test).configureEach { inputs.file(rootProject.file('infra/demo/seed/seed-scm.sh')) … }` 를 선언했다.
- 명령(모두 `--offline`, 출력은 파일로 받아 rc 를 따로 읽음): `./gradlew :projects:wms-platform:apps:inbound-service:test --tests 'com.wms.inbound.adapter.in.messaging.scm.ScmInboundExpectedDemoSeedShapeDltTest'`

| 단계 | 시드 상태 | rc | 결과 |
|---|---|---|---|
| (입력 선언 전) bite A | 옛 코드 `SUP-DEMO-01`/`SKU-DEMO-A1` | **0** | 🔴 `test UP-TO-DATE` — 가짜 초록 |
| 초록 | 새 코드 | 0 | `test` 실행, 4 tests / 0 failures |
| bite A | 옛 코드 `SUP-DEMO-01`/`SKU-DEMO-A1` | **1** | `demoSeedMapping_resolvesInWmsDevSeed` **만** 실패(`:146` `isNull`) |
| 원복 | 손 편집(sed) → 백업과 `cmp` rc=0 | — | |
| bite B | 매핑 `supplierId` = `$SUPPLIER_ID`(서버 UUID) | **1** | 같은 테스트만 실패(`:146`) |
| 원복 | 손 편집 → `cmp` rc=0 | — | |
| 초록 (`--rerun`) | 새 코드 | 0 | 4 tests / 0 failures |

- 전체 `:projects:wms-platform:apps:inbound-service:test` → rc=0 (build.gradle 입력 선언이 다른 테스트를 깨지 않음).
- 🔴 **CI 에서 이 bite 가 언제 도는가**: `ci.yml` 의 `wms` 경로 필터는 `projects/wms-platform/**` 뿐이라 **`infra/demo/seed/seed-scm.sh` 만 바꾼 PR 에서는 Java 빌드가 안 돈다**
  (PR 게이트에서 못 막는다). `main` push 는 `code-changed`(`**/*.sh` 포함)로 빌드가 돌고 입력 선언 덕에 캐시도 안 탄다 ⇒ **머지 후 main 에서** 빨개진다.
  필터에 그 경로를 더하는 것은 `ci.yml`(공유 파일·가드 다수) 변경이라 이 티켓에서 하지 않았다 — 필요하면 별도 티켓.

## 5. 표시 (`ReplenishmentTable.tsx`)

- **결정: 677/276 규칙을 쓴다.** 제안의 `supplierId` 는 계약상 **코드**(D9)이므로 값 자체를 `code` 로 넘긴다: `masterRefLabel(s.supplierId, supplierCodeRef(s.supplierId))`,
  `data-master-ref="suggestion.supplierId"`, 원문은 `title`. 🔴 **UUID 모양이면 코드가 아니다**(683 이전 매핑 · 운영자가 폼에 id 를 넣은 경우) ⇒ `이름 확인 불가` 로 **보이게** 한다 —
  코드처럼 그리면 DLT 로 갈 매핑이 정상처럼 보인다. 이름은 이 응답에 없어서 `codeName` 이 코드만 그린다(277 이 만든 반쪽 규칙). 창고 칸과 같은 모양.
- 새 조회는 안 했다(procurement 공급사 마스터를 콘솔이 부르지 않음) — 이름까지 원하면 생산자(demand-planning)가 싣는 별도 계약 변경이다.
- 테스트 `tests/unit/erp-master-ref-names.test.tsx`: 제안 픽스처에 공급사(코드 · UUID · null)를 넣고 2건 추가
  (「공급사」 칸 = `['SUP-001', 이름 확인 불가, —]` · 대조군 = UUID 는 `title` 에만). 🔴 한 컴포넌트에 참조 칸이 둘이 되어 277 의 창고 대조군을
  **칸 키로 좁혔다**(`refCellTexts(container, 'suggestion.warehouseId')` + 길이 3) — 안 좁히면 공급사 칸의 `—`/`이름 확인 불가` 가 창고 대조군을 대신 통과시킨다.
- 명령(console-web, 워크트리 안 `pnpm install --frozen-lockfile` rc=0 — 저장소는 pnpm 잠금파일이라 `npm ci` 대신 CI 와 같은 명령):
  `npx tsc --noEmit` rc=0 · `npx vitest run tests/unit/erp-master-ref-names.test.tsx tests/unit/ReplenishmentScreen.test.tsx` rc=0 (2 files / 53 tests) · `pnpm lint` rc=0.
- bite: 칸 내용을 `{s.supplierId ?? '—'}` 로 되돌림 → vitest rc=1, 3 실패(새 「공급사」 칸 + 전 참조 셀 UUID 술어를 공유하는 277 테스트 둘) → 백업에서 복원, `cmp` rc=0.

## 6. AC-4 ⚪ → `TASK-MONO-672` § 항목 5

`tasks/ready/TASK-MONO-672-…` 에 «항목 5 — TASK-MONO-683 AC-4» 절을 추가하고 `tasks/INDEX.md` 의 672 행 수령 수를 5 로 고쳤다(병합 중 main 이 `TASK-BE-595` 를 항목 4 로 먼저 넣어 번호를 5 로 옮김 — INDEX 행에는 BE-595 가 빠져 있어 함께 셈).

## 미측정 (Phase 2)

- 새 시드를 **실제 스택에 돌린 결과** — 문법 검사(`bash -n`)만. 발주 키 변경의 422 회피도 코드 읽기(`IdempotencyExecutor`)이지 실행이 아니다.
- 데모에서 `SKU-APPLE-001` 보충 제안이 생기는가 · 끝까지 ASN 이 생기는가 — `TASK-MONO-672` 항목 5.
- Testcontainers IT — Docker 미기동(Phase 1 과 같음).
- CI 경로 필터 공백(위 4.) — 관찰만, 안 고침.
- 677 AC-3 의 라이브 기대값 문구 — 보고만(위 3.).
- 콘솔 `SupplierMapForm` 이 무엇을 받는지 — 여전히 안 열었다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** (두 서비스의 코드 공간 대조 + 계약·시드·표시 세 층)

---

# 🔵 창 실측 — 2026-09-17 UTC · AMI `ami-0d30513151d07e163`(RepoCommit `b54296645`) · 창 08:50:37Z~10:08:16Z

- 🟢 **시드 수정이 런타임에 들어갔다**: `GET /api/scm/demand-planning/sku-supplier-map/SKU-APPLE-001` → `supplierId="SUP-001"`(코드, UUID 아님) · `defaultOrderQty=100`. 발주 화면 공급사 칸도 `SUP-001 · demo supplier`(`TASK-MONO-677` 기록).
- ⚪ **AC-4 흐름(제안 승인 → 확정 → wms 입고 예정)은 이번 창에서도 관측 불가** — `GET /api/scm/demand-planning/suggestions` = **0건**. 정책 `SKU-APPLE-001` `reorderPoint=10` · `safetyStock=5` 인데 wms 가용재고 **85**(`/api/wms/inventory`) ⇒ 제안이 생길 조건이 아니다. 끝까지 보려면 재고를 재주문점 아래로 **인위로** 내려야 한다(출고·조정 쓰기) — 이 창의 승인 범위(측정) 밖이라 하지 않았다. 갈 곳은 그대로 `TASK-MONO-672` § 항목 5.

---

# 🔵 창 메모 — 2026-09-17 UTC 둘째 창(시작 2026-09-17T16:34:55Z · 종료 17:21:02Z · 46분) · AMI `ami-02613b0378621b124`(RepoCommit `af0018aa6`, 12차 — 구조된 굽기, provenance operator-record) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 `console console-ecommerce console-wms console-scm store fan` · 소유자 승인 «af0018aa6, 상한 100분»

- AC-4(제안 승인 → 확정 → wms 인바운드 예정): 이 창에서도 **안 쟀다.** 제안이 0건인 이유(가용 85 > 재주문점 10)를 넘으려면 재고를 재주문점 아래로 내리는 **쓰기**가 필요한데, 소유자에게 물었고 선택은 **«쓰기 안 함»**(2026-09-17 선택창 라벨 원문 «쓰기 안 함 (Recommended)»). ⚪ 그대로 `TASK-MONO-672` § 항목 5.
