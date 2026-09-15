# TASK-MONO-677 — 발주 화면이 공급사를 UUID 로 보여 준다. 마스터에는 **코드도 이름도 있다**

# Status

ready

**Type:** TASK-MONO (monorepo-level — scm 생산자 응답 ↔ 콘솔 표시, 두 프로젝트)

**Analysis model:** Opus 5 / **구현 권장:** Opus 5 (계약 결정이 먼저다)

---

# Goal

콘솔 `/scm/procurement` 의 「공급사」 칸이 **UUID 원문**이다. 2026-09-11 에 처음 봤고,
2026-09-12 에 새 코드로 AMI 를 다시 구운 데모에서 **다시 열어도 그대로**였다(발주 3건 모두
`01a09478-…`). 그래서 scm README 는 스크린샷을 싣지 못하고 있다.

목표: 그 칸이 **사람이 읽는 값**(공급사 코드 또는 이름)이 되게 한다.

---

# 🔴 이 칸은 지금까지 **아무 티켓도 들고 있지 않았다**

| 어디 | 뭐라고 적었나 |
|---|---|
| `TASK-MONO-659`(done) | *"`/scm/*` `supplierId`(3) · ⚪ 그대로 — 화면에 데이터가 0건이라 못 봤다(다른 부류)"* |
| scm README | *"추적: `TASK-MONO-659`"* ← 659 는 이 칸을 다루지 않았다 |
| `TASK-MONO-667` | *"같은 티켓(675)에서 다룬다"* |
| `TASK-MONO-675` | Out of scope — *"`TASK-MONO-676` 참조"* |
| `TASK-MONO-676` | **0건** |

⇒ 세 티켓이 서로를 가리키며 **누구도 들고 있지 않았다.** 2026-09-15 에 README 사유를 고치다가
발견했고, 네 곳의 포인터를 이 티켓으로 바로잡았다.

---

# 실측 — 기전은 wms(675)와 **다르다**

| 축 | 값 |
|---|---|
| 공급사 마스터 | 🟢 **있다** — `SupplierResponse(id, tenantId, code, name)`, 등록 API `POST /api/v1/procurement/suppliers` (`projects/scm-platform/tasks/done/TASK-SCM-BE-059-no-supplier-registration-api.md`). 시드 주석은 그 근거로 `ADR-SCM-001`(ACCEPTED) 을 인용한다 |
| 단건 조회 | 🟢 계약에 `GET /api/procurement/suppliers/{supplierId}` 가 있다(`procurement-api.md`) |
| 발주 응답 | 🔴 `PurchaseOrderResponse` 는 공급사를 **`supplierId` 하나로만** 싣는다 — 코드·이름 필드가 없다 |
| 시드가 넣는 값 | 공급사를 등록한 뒤 **서버가 만든 UUID `id`** 를 발주 `supplierId` 로 쓴다(`infra/demo/seed/seed-scm.sh` § 0). 자연키 `SUP-DEMO-01` 은 `code` 다 |
| 콘솔 | 🔴 `api/scm/` 아래에 **공급사 조회 경로가 없다**(`_proxy` · `demand-planning` · `nodes` · `po` · `sku` · `snapshot` · `staleness`) |
| 콘솔 주석 | 🔴 `api/scm/demand-planning/sku-supplier-map/[skuCode]/route.ts` 가 아직 *"`supplierId` is free-text/uuid (**no supplier master in v1**)"* 라고 적는다 — BE-059 이후 **낡았다** |

🔵 wms 는 «이름 필드가 있는데 null(데이터가 없다)» 이고, 여기는 «마스터에 이름이 있는데 **응답이 안
싣는다**» 다. `TASK-MONO-659` 가 wms 에서 고친 «생산자가 옆 필드를 비정규화하지 않았다» 와 같은 모양이다.

---

# Scope

**갈래를 먼저 고른다.** 계약이 움직이는 갈래가 있어서 고르기 전에 구현하지 않는다.

| 갈래 | 하는 일 | 대가 |
|---|---|---|
| **ⓐ 생산자가 싣는다** | `PurchaseOrderResponse` 에 `supplierCode`·`supplierName` 을 더한다(마스터 조인) | 🔴 **계약 변경** — `procurement-api.md` 먼저. 659·670 이 택한 쪽과 같다 |
| **ⓑ 콘솔이 조회한다** | 콘솔 BFF 에 공급사 조회 경로를 새로 두고 `supplierId` 로 이름을 푼다 | 🔴 행마다 조회(N+1) · 콘솔에 새 경로 · 권한 확인 |
| **ⓒ 발주가 코드를 저장한다** | `supplierId` 에 UUID 대신 공급사 `code` 를 쓴다(서비스 간 식별자는 코드 — ADR-MONO-050 D9) | 🔴 **가장 크다** — 저장 의미가 바뀌고 wms 인바운드 연계·기존 행이 따라 움직인다 |

**Out of scope**: wms 의 코드 null(`TASK-MONO-675`) · 공급사 마스터 자체의 설계 · 모든 테넌트에서 막히는 화면(`TASK-MONO-676`).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (전제부터 다시 재라)

- [x] 🔴 `PurchaseOrderResponse` 가 **여전히** 공급사를 id 하나로만 싣는지 다시 읽어라. 이미 이름이 실렸으면 이 티켓은 phantom 이다.
      → 🟢 **여전히 id 하나다 — phantom 아님** (2026-09-15). `PurchaseOrderResponse.java:16` `String supplierId,` 뿐이고 `supplierCode`·`supplierName` 은 없다. 마스터(`SupplierResponse.java:8-18`)는 `code`·`name` 을 든다.
- [x] 🔴 콘솔 `api/scm/` 에 공급사 조회 경로가 **여전히 없는지** 다시 세라.
      → 🟢 **없다.** `api/scm/` 전수 14개 파일(`_proxy.ts` · `nodes` · `po` · `po/[poId]` · `sku/[sku]` · `snapshot` · `staleness` · `demand-planning/{_proxy, suggestions×4, policies/[skuCode], sku-supplier-map/[skuCode]}`)에 `suppliers` **0건**. 화면은 `ScmPoTable.tsx:186` `{p.supplierId ?? '—'}` · `PoDetailDialog.tsx:102` 로 원문을 그린다. 🔵 낡은 주석 *"no supplier master in v1"* 은 **세 곳**이다 — `sku-supplier-map/[skuCode]/route.ts:19` · `features/scm-config/api/types.ts:73` · `features/scm-config/api/demand-planning-seed-api.ts:213` (AC-2 셋째 칸은 셋 다다).
- [x] 발주 `supplierId` 에 무엇이 들어가는지 **시드 경로와 운영자 작성 경로 둘 다** 확인하라 — 계약상 DEMAND_PLANNING 발 발주는 코드를, 운영자 작성 발주는 «운영자가 준 값» 을 싣는다(`scm-procurement-events.md`). 🔴 한 경로만 보고 고르지 마라.
      → 🔴🔴 **같은 칸이 경로에 따라 «UUID» 이기도 하고 «코드» 이기도 하다** — 이것이 갈래 선택의 핵심 입력이다:

      | 경로 | `supplierId` 에 드는 값 | 근거 |
      |---|---|---|
      | 데모 시드 | 마스터의 **서버 발급 UUID `id`** | `seed-scm.sh:116,194` |
      | 운영자 작성 | **아무 문자열**(≤36자, FK 없음) | `procurement-api.md:88,569` |
      | DEMAND_PLANNING 발 | 공급사 **CODE**(`sku_supplier_map.supplier_id`) | `procurement-api.md:89` · `scm-procurement-events.md:414` |
      | wms 인바운드가 받는 값 | **CODE** 로 해석(`findPartnerByCode`) | `ADR-MONO-050:223-225` D9 |

      🔴 ⇒ **ⓐ(id 로 마스터 조인)는 DEMAND_PLANNING 발 발주에서 빈다** — 그 행의 값은 id 가 아니라 코드다. ⓐ 를 고르면 «id 로도 코드로도 찾는다» 까지 정해야 한다.
      🔴 ⇒ **데모 시드는 이미 D9 와 반대로 가고 있다** — 시드 발주가 인바운드 이벤트로 넘어가면 wms 는 UUID 를 코드로 찾는다. 그 연계를 이 데모가 실제로 쓰는지는 이 티켓 범위 밖이지만, ⓒ 의 «대가» 칸이 적은 «wms 인바운드 연계가 따라 움직인다» 는 **반대로 읽어야 할 수 있다**(ⓒ 가 연계를 **맞추는** 쪽).
      🔵 AC-4 가드: `erp-master-ref-names` 는 스크립트가 아니라 **`console-web/tests/unit/erp-master-ref-names.test.tsx`** 다(`data-master-ref` 마커 모집단) — 추가해도 `scripts/` 분모는 안 움직인다.

## AC-1 — 갈래를 고른다 (🔴 소유자 결정)

- [x] ⓐ/ⓑ/ⓒ 를 **소유자에게 묻는다.** 내 추천을 결정으로 적지 마라.
      → 2026-09-15 선택창으로 물었다(AC-0 의 «같은 칸에 UUID·코드가 섞인다» 를 질문에 넣었다). 🔵 추천 표지는 **내 것**, 선택은 소유자.
- [x] 답을 **소유자의 말 그대로** 적고, 안 고른 갈래가 무엇을 포기하는지 함께 적는다.
      → 소유자 선택(선택창 라벨 원문): **「ⓐ 생산자가 싣기, id·code 둘 다 조인 (Recommended)」**
      — 선택지 설명(내가 쓴 것): *`PurchaseOrderResponse` 에 `supplierCode`·`supplierName` 추가(계약 먼저). 같은 테넌트 안에서 id 로 먼저, 없으면 code 로. 못 찾으면 null → 화면은 «이름 확인 불가». 저장 의미는 안 바꾼다.*
      포기한 것: **ⓑ** = 콘솔 BFF 경로 신설 없음(N+1 을 피한다) · **ⓒ** = `supplierId` 저장 의미는 **섞인 채로 남는다** — 시드 발주가 D9(서비스 간 식별자=코드)와 어긋나는 것은 이 티켓이 **고치지 않는다**. 🔴 그 어긋남이 wms 인바운드 연계에서 실제로 문제를 내는지는 **아무 티켓도 안 들고 있다** — 구현 PR 에서 받는 티켓에 행을 만들거나 «문제 아님» 을 실측으로 적는다(Failure Scenario 3 의 규율).

## AC-2 — 계약 먼저

- [ ] ⓐ 또는 ⓒ 면 `projects/scm-platform/specs/contracts/http/procurement-api.md`(ⓒ 는 이벤트 계약도)를 **구현보다 먼저** 고친다.
- [ ] 필드 이름은 형제와 맞춘다 — 🔴 새 이름을 만들지 마라(wms 는 `supplierName`, 공급사 마스터는 `code`·`name`).
- [ ] 콘솔의 낡은 주석(*"no supplier master in v1"*)을 고친다.

## AC-3 — 판정은 **화면**이다

- [ ] 🔴 응답에 필드가 생긴 것으로 닫지 마라. 콘솔 `/scm/procurement` 의 「공급사」 칸에 **UUID 가 사라졌는가**가 판정이다. 🔵 `TASK-MONO-675` 가 배운 것: 필드가 있어도 값이 null 일 수 있다 — **값**을 봐라.
- [ ] 🔴 이 칸은 **새 코드로 AMI 를 다시 구운 창**에서만 판정된다(데모 AMI 는 jar 를 구워 넣는다 — `TASK-MONO-667` 게이트 정정). 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).
- [ ] 판정이 나면 scm README § Screenshots 의 보류 사유를 되돌려 준다.

## AC-4 — 가드

- [ ] 콘솔 `erp-master-ref-names` 가드 계열(`TASK-MONO-659`·`670` 이 블록을 더한 그 파일)에 scm 발주의 공급사 칸이 **들어가는지** 판단하고 이유를 적어라.

---

# Related Specs / Contracts

- `projects/scm-platform/specs/contracts/http/procurement-api.md` — PO 응답 · `GET /api/procurement/suppliers/{supplierId}`
- `projects/scm-platform/specs/contracts/events/scm-procurement-events.md` — `supplierId` 의미(코드 vs 운영자 값)
- `projects/scm-platform/apps/procurement-service/.../presentation/dto/PurchaseOrderResponse.java` · `SupplierResponse.java`
- `projects/scm-platform/tasks/done/TASK-SCM-BE-059-no-supplier-registration-api.md` — 공급사 마스터가 생긴 티켓
- `projects/scm-platform/docs/adr/ADR-001-supplier-master-write-surface.md` — 시드 주석이 `ADR-SCM-001` 로 인용하는 결정
- `infra/demo/seed/seed-scm.sh` § 0 — 시드가 UUID id 를 발주에 쓰는 자리
- `docs/adr/ADR-MONO-050-scm-procurement-wms-inbound-expected.md` § 7 D9 — 서비스 간 식별자는 코드
- `TASK-MONO-659` · `TASK-MONO-670` — 같은 모양을 wms·iam 에서 고친 선례

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 공급사가 삭제·비활성 | ⓐ 면 이름이 null — 🔴 빈 문자열로 채우지 마라(670 이 계약에 적은 규칙과 같다) |
| 운영자 작성 발주가 마스터에 없는 값을 `supplierId` 로 가진다 | 계약상 허용이다(FK 없음). 🔴 조인이 비는 행이 **정상**일 수 있다 — 화면 표시 규칙을 정해라 |
| 다른 테넌트의 공급사 | 조인은 테넌트 경계를 넘지 않아야 한다 |

# Failure Scenarios

1. **콘솔에서 UUID 를 줄여 보여 준다** → 읽을 수 없는 값을 짧게 만든 것뿐이다. 판정(AC-3)을 통과하지 못한다.
2. **필드만 더하고 창 없이 닫는다** → 데모 호스트에는 새 jar 가 없어서 화면은 그대로다.
3. **이 칸을 또 다른 티켓의 Out of scope 로 내보낸다** → 이 티켓이 생긴 이유 그 자체다. 내보내려면 받는 쪽에 행을 먼저 만들어라.
