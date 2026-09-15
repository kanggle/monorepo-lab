# Task ID

TASK-MONO-683

# Title

🔴 데모 시드가 SKU→공급사 매핑에 **UUID** 를 넣는다 — 보충 제안을 승인해 발주를 확정하면 wms 는 그 값을 **코드**로 찾다 놓치고 DLT 로 버린다(잠복)

# Status

ready

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
| 운영자 | 보충 제안을 승인 → `POST /po/from-suggestion` → 확정 | `procurement-api.md` § from-suggestion (값을 검증하지 않는다) |
| 발행 | inbound-expected 이벤트의 `supplierId` = 그 UUID | `OutboxProcurementEventPublisher.java:136` |
| wms | `findPartnerByCode(<UUID>)` 가 빈다 → 재시도 없이 DLT | `CreateScmInboundExpectationService.java:139-141` · `InboundExpectationRejectedException.java:8-11` |

🔵 **왜 지금은 안 터지나**: 시드는 제안을 **승인하지 않고**, 시드가 직접 만드는 발주(`POST /po`)는 목적지가 없어
`isWmsWarehouseDestination()` 이 거짓이라 이벤트를 안 낸다(`PurchaseOrder.java:368-372`). 또 wms ref 테이블이
비어 있던 문제(`TASK-MONO-675`)가 이 경로를 먼저 막았을 것이다. ⇒ **데모 시연자가 «보충 제안 승인» 을 한 번
누르는 순간** 드러난다.

곁가지로 같은 뿌리의 흔적 둘:
- `projects/scm-platform/specs/contracts/http/demand-planning-api.md:61,139` 가 아직 `"supplierId": "uuid"` 예시 — D9 와 모순.
- 콘솔 `features/scm-replenishment/.../ReplenishmentTable.tsx:104` 가 `supplierId` 를 원문으로 그린다.

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

- [ ] **AC-0 — 재측정.** 위 표의 file:line 넷과 곁가지 둘을 **다시 읽어라** — 677 구현 에이전트의 보고이지 오케스트레이터가 연 것이 아니다. 하나라도 틀리면 그 칸부터 정정한다.
- [ ] **AC-1 — 잠복을 «실측» 으로 확인하거나 기각한다.** 저장소에서 재현할 수 있는 가장 싼 방법(서비스 IT 또는 컨슈머 단위 테스트에 시드와 같은 UUID 를 넣기)으로 «DLT 로 간다» 를 보인다. 🔴 추론만으로 결함이라 적지 마라.
- [ ] **AC-2 — 두 서비스의 코드 공간을 대조한다.** scm 공급사 코드와 wms 파트너 코드가 **같은 값으로 만나는가**. 안 만나면 «시드를 코드로 바꾼다» 만으로는 안 고쳐진다 — 그 사실과 선택지를 적고, 선택이 필요하면 소유자에게 묻는다(🔴 추천을 결정으로 적지 마라).
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

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** (두 서비스의 코드 공간 대조 + 계약·시드·표시 세 층)
