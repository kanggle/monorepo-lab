# TASK-MONO-677 — 발주 화면이 공급사를 UUID 로 보여 준다. 마스터에는 **코드도 이름도 있다**

# Status

in-progress

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

- [x] ⓐ 또는 ⓒ 면 `projects/scm-platform/specs/contracts/http/procurement-api.md`(ⓒ 는 이벤트 계약도)를 **구현보다 먼저** 고친다.
      → 2026-09-15 `procurement-api.md` § POST /po 밑에 **「`PurchaseOrderResponse` — supplier reference fields」** 절을 새로 두고(필드 표 + 규칙 6개), 201 예시에 두 필드를, `GET /po` · `GET /po/{poId}` 에 그 절 참조를 넣었다. 이벤트 계약은 **안 건드렸다**(ⓐ 라서).
- [x] 필드 이름은 형제와 맞춘다 — 🔴 새 이름을 만들지 마라(wms 는 `supplierName`, 공급사 마스터는 `code`·`name`).
      → `supplierCode` · `supplierName` (wms `supplierName` 과 같은 이름, `code` 는 마스터 `code` 에 `supplier` 접두 — `warehouseCode`·`skuCode` 형제와 같은 규칙).
- [x] 콘솔의 낡은 주석(*"no supplier master in v1"*)을 고친다.
      → 🔴 **셋이 아니라 다섯이었다.** AC-0 이 센 세 곳(`sku-supplier-map/[skuCode]/route.ts` · `scm-config/api/types.ts` · `demand-planning-seed-api.ts`)에 더해 **`scm-config/components/SupplierMapForm.tsx:16-17`**(문구가 줄바꿈으로 `no` / `supplier master in v1` 에 걸쳐 있어 한 줄 grep 에 안 걸렸다)와 **소비자 계약 `platform-console/specs/contracts/console-integration-contract.md:1060`** 도 같은 말을 했다. 다섯 다 «마스터는 있다(BE-059/ADR-SCM-001), demand-planning 은 그것으로 풀지 않는다, D9 상 이 값은 공급사 CODE» 로 고쳤다.

## AC-3 — 판정은 **화면**이다

- [x] 🔴 응답에 필드가 생긴 것으로 닫지 마라. 콘솔 `/scm/procurement` 의 「공급사」 칸에 **UUID 가 사라졌는가**가 판정이다. 🔵 `TASK-MONO-675` 가 배운 것: 필드가 있어도 값이 null 일 수 있다 — **값**을 봐라.
- [x] 🔴 이 칸은 **새 코드로 AMI 를 다시 구운 창**에서만 판정된다(데모 AMI 는 jar 를 구워 넣는다 — `TASK-MONO-667` 게이트 정정). 창이 없으면 ⚪ + 갈 곳(`TASK-MONO-672`).
- [ ] 판정이 나면 scm README § Screenshots 의 보류 사유를 되돌려 준다.

## AC-4 — 가드

- [x] 콘솔 `erp-master-ref-names` 가드 계열(`TASK-MONO-659`·`670` 이 블록을 더한 그 파일)에 scm 발주의 공급사 칸이 **들어가는지** 판단하고 이유를 적어라.
      → 🟢 **들어간다 — 두 칸(목록 「공급사」 · 상세 「공급사」), 하한 +6(목록 5 · 상세 1).** 이유: ① 그 칸은 **다른 엔티티(공급사 마스터)를 가리키는 참조**이고 `masterRefLabel` 의 세 상태(없음 `—` / 해석 `CODE · 이름` / 미해석 `이름 확인 불가`)가 그대로 성립한다 — 가드의 모집단 정의(«`data-master-ref` 를 단 참조 칸») 에 정확히 맞는다. ② 결함 모양이 가드가 잡는 그것(«참조 칸에 UUID 원문») 과 **같다**. ③ 새 가드를 지으면 술어가 두 벌이 된다(276 이 막으려던 Failure 2). 🔴 **넣지 않은 칸 하나**: 상세의 **「공급사 ID」** 줄은 저장값 **자체**이고 「공급사 ID」 필터가 매칭하는 값을 화면에서 볼 수 있는 유일한 곳이라 원문을 유지하고 마커를 달지 않았다 — `TASK-PC-FE-281` § 제외(업무 번호 칸)와 같은 논리이고, 그 줄이 모집단 **밖**임을 단언하는 대조군을 같이 넣었다(안 넣으면 누가 «일관성» 으로 마커를 달아도 모른다). 파일 이름은 `erp-…` 그대로 둔다(277 주석: 이름이 아니라 주석이 범위).

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
| 공급사가 삭제·비활성 | ~~ⓐ 면 이름이 null~~ → **못 찾으면(행 없음) null · 비활성은 해석한다** (소유자 결정 2026-09-16, § 구현 노트 «내가 고른 것» 1). 🔴 빈 문자열로 채우지 마라(670 이 계약에 적은 규칙과 같다) |
| 운영자 작성 발주가 마스터에 없는 값을 `supplierId` 로 가진다 | 계약상 허용이다(FK 없음). 🔴 조인이 비는 행이 **정상**일 수 있다 — 화면 표시 규칙을 정해라 |
| 다른 테넌트의 공급사 | 조인은 테넌트 경계를 넘지 않아야 한다 |

# Failure Scenarios

1. **콘솔에서 UUID 를 줄여 보여 준다** → 읽을 수 없는 값을 짧게 만든 것뿐이다. 판정(AC-3)을 통과하지 못한다.
2. **필드만 더하고 창 없이 닫는다** → 데모 호스트에는 새 jar 가 없어서 화면은 그대로다.
3. **이 칸을 또 다른 티켓의 Out of scope 로 내보낸다** → 이 티켓이 생긴 이유 그 자체다. 내보내려면 받는 쪽에 행을 먼저 만들어라.

---

# 구현 노트 (2026-09-15 UTC · in-progress)

🔴 **닫지 않는다** — AC-3(화면 판정)은 새 jar 로 AMI 를 다시 구운 창에서만 난다. 코드·계약·가드까지가 이 커밋이다.

## 무엇이 바뀌었나

| 층 | 변경 |
|---|---|
| 계약 | `procurement-api.md` § POST /po 밑 «`PurchaseOrderResponse` — supplier reference fields» (필드 표 + 규칙 6개) · 201 예시 · `GET /po` · `GET /po/{poId}` 참조. 이벤트 계약 **무변경**. |
| 응답 | `PurchaseOrderView` · `PurchaseOrderResponse` 에 `String supplierCode, String supplierName` (`supplierId` 바로 뒤). `supplierId` 가 저장하는 값은 **안 바뀐다**. |
| 포트 | `SupplierRepository.findAllByIds(Collection<String> ids, String tenantId)` · `findAllByCodes(Collection<String> codes, String tenantId)` — JPA `findByTenantIdAndIdIn` / `findByTenantIdAndCodeIn`, 빈 입력은 **쿼리 안 함**. |
| 해석 | `PurchaseOrderApplicationService.resolveSuppliers(...)` **한 곳**: 테넌트 안에서 id 배치 1회 → 못 맞춘 ref 만 code 배치 ≤1회(남은 게 없으면 생략) → id 적중이 code 적중을 이긴다 → 못 찾으면 맵에 없음 = 두 필드 `null`. 포트가 남의 테넌트 행을 돌려줘도 `tenantId.equals(s.getTenantId())` 로 한 번 더 거른다. 목록은 페이지당 쿼리 ≤2(N+1 없음). |
| 콘솔 | zod 두 필드(`nullable().optional()` — 옛 생산자도 파싱) · `ScmPoTable` 「공급사」 칸 · `PoDetailDialog` 「공급사」 줄 → `masterRefLabel` + `data-master-ref="po.supplierId"` + 원본 id 는 `title`. 필터 입력(「공급사 ID」, 원문 매칭)은 **무변경**. |
| 주석 | «no supplier master in v1» **다섯 곳**(AC-2 셋째 칸 참조). |

## 🔵 내가 고른 것 — 소유자 결정(ⓐ) 밖의 세부, 전부 적는다

1. **비활성(`INACTIVE`/`CONTRACT_EXPIRED`) 공급사도 해석한다** — 🔴 이 티켓 Edge Case 표의 «삭제·비활성이면 이름 null» 과 **다르다**. 이유: v1 에는 공급사 **삭제 경로가 없어** 행과 이름이 남고, 과거 발주는 그때의 공급사를 가리킨다(비활성 공급사의 옛 발주를 «이름 확인 불가» 로 그리면 읽을 수 있는 것을 일부러 지운다). 소유자 결정의 null 조건은 «못 찾으면» 이다. «삭제 → 행 없음 → null» 은 규칙대로 성립하지만 지금 도달 경로가 없다. 빈 문자열은 어느 경우에도 안 나온다(단위 테스트 + 슬라이스가 `"supplierCode":null` 을 문자열로 확인).
   🔵 **소유자 결정 (2026-09-16) — 유지.** 선택창 원문: 질문 «비활성 공급사도 코드·이름을 채웁니다 … 어떻게 할까요?»
   → 답 **「유지 (추천)」**(선택지: 유지 / 되돌리기). ⇒ Edge Case 표를 이 결정으로 고쳤다. 되돌리는 후속 PR 없음.
2. **목록·상세만이 아니라 `PurchaseOrderResponse` 를 돌려주는 모든 경로**(draft · from-suggestion · submit · confirm · cancel · supplier-ack 웹훅)에 싣는다. 이유: 같은 레코드를 공유하므로 안 풀면 변이 응답의 null(«계산 안 함») 이 «못 찾음» 의 null 과 **구별 불가**가 된다. `draft` 는 가드가 이미 id 로 로드한 공급사를 재사용(추가 쿼리 0), 나머지는 단건 해석(쿼리 ≤2).
3. **멱등 replay** 는 첫 실행 때 저장된 응답(그때의 두 필드)을 돌려준다 — 계약 규칙 5에 적었다.
4. **상세 다이얼로그는 두 줄**: 「공급사」(이름, 모집단 안) + 「공급사 ID」(저장값 원문, 보조 서체, 마커 없음). 목록에서 id 를 `title` 로 옮기면 필터에 넣을 값을 볼 곳이 사라지기 때문(AC-4 참조).
5. 두 필드가 **없는** 옛 생산자 응답 → «이름 확인 불가»(UUID 아님). 659/670 의 null 처리와 같다.

## 🔴 AC-0 표 정정 — «운영자 작성 = 아무 문자열» 은 `POST /po` 에 대해 틀렸다

`PurchaseOrderApplicationService.draft` 는 `supplierRepository.findById(cmd.supplierId(), tenant)` 로 조회해 없으면 `SUPPLIER_NOT_FOUND`, 비활성이면 `SUPPLIER_INACTIVE` 를 던진다 ⇒ **운영자·시드 발주의 `supplierId` 는 늘 같은 테넌트 마스터의 id** 다. 인용된 `procurement-api.md:569` 의 «FK 검증 안 함» 은 **`from-suggestion`** 절의 문구다. «검증 없는 아무 값» 은 from-suggestion(= DEMAND_PLANNING 발) 한 경로뿐이고, 그래서 code 조인이 필요한 것도 그 경로다. 계약 규칙 1 은 정정된 쪽으로 썼다.

## D9 판정 — 시드의 UUID 는 wms 로 가는가

- 🟢 **시드 발주 3건은 안 간다.** `seed-scm.sh:192-227` 은 `POST /po` 로 만든다 → 목적지(`destinationNodeType`/`destinationWarehouseId`)가 없다 → `PurchaseOrder.isWmsWarehouseDestination()` false (`PurchaseOrder.java:368-372`) → confirm 해도 `PurchaseOrderApplicationService.maybePublishInboundExpected` 가 아무것도 안 낸다. 그 경로에서 UUID 는 문제가 아니다.
- 🔴 **그러나 같은 시드가 `sku_supplier_map` 에도 UUID 를 넣는다** — `seed-scm.sh:141-143` `"supplierId":"$SUPPLIER_ID"`(= 마스터 UUID). 계약은 그 칸을 **공급사 CODE** 로 취급한다(`scm-procurement-events.md:396,414`). 운영자가 콘솔에서 보충 제안을 **승인**하면 from-suggestion 발주가 `supplierId=UUID` + `WMS_WAREHOUSE` 목적지로 생기고, confirm 시 inbound-expected 페이로드 `supplierId` 가 그 UUID 다(`OutboxProcurementEventPublisher.java:136`, `publishInboundExpected`) → wms `CreateScmInboundExpectationService.java:139-141` `findPartnerByCode(UUID)` 미스 → `InboundExpectationRejectedException` → `IllegalArgumentException` 계열이라 **non-retryable → `.DLT`** (`InboundExpectationRejectedException.java:8-11`).
- ⇒ **잠재 결함, 실재.** 데모에서 «보충 제안 승인 → 발주 확정» 을 밟는 순간 wms 인바운드 연계가 DLT 로 떨어진다. 시드 자신은 제안을 승인하지 않으므로(대기만 한다, `seed-scm.sh:161-185`) **시드만으로는 발화하지 않는다.** 🔵 코드를 넣어도 wms 쪽 partner 에 그 코드가 있어야 하고, 지금은 ref 테이블 0건(`TASK-MONO-675`)이 먼저 막는다 — **두 원인이 겹친다**. 부수: `demand-planning-api.md:61,139` 예시가 `"supplierId": "uuid"` 라 D9 와 **계약끼리도** 어긋난다.
- 🔴 **받는 티켓이 아직 없다** — ID 를 할당하지 않고 오케스트레이터에 보고했다(Failure Scenario 3: 내보내려면 받는 쪽 행이 먼저). 이 티켓의 ⓐ 는 그 결함을 고치지도 악화하지도 않는다(표시만 id·code 둘 다로 푼다).
  → 🔵 **받는 티켓 = `tasks/ready/TASK-MONO-683-the-seed-maps-skus-to-a-supplier-uuid-that-wms-resolves-as-a-code.md`** (2026-09-15 같은 PR 에서 기안, INDEX ready 행 확인). 아래 `ReplenishmentTable.tsx:104` 곁발견도 그 티켓 Scope 에 넣었다.

## 테스트 · rc (전부 이 worktree, 2026-09-15 UTC)

| 명령 | rc | 실측 |
|---|---|---|
| `./gradlew :projects:scm-platform:apps:procurement-service:test` | **0** | JUnit XML: `PurchaseOrderApplicationServiceTest` 33/0 실패(기존 25 + 신규 8) · `PurchaseOrderControllerSliceTest` 15/0(+2) · `IdempotencyExecutorTest` 5/0 · `SupplierAckWebhookControllerSliceTest` 2/0 · `ActorContextAuthPathSliceTest` 10/0 |
| `npx vitest run tests/unit/erp-master-ref-names.test.tsx tests/unit/ScmProcurementScreen.test.tsx tests/unit/scm-api.test.ts` | **0** | 3 files · **65 passed** |
| `npx tsc --noEmit` (console-web) | **0** | |

⚪ **안 돌린 것**: `MultiTenantIsolationIntegrationTest` 에 더한 IT(`supplierReferenceResolvesInsideTheTenantOnly` — 실제 Postgres 에서 id/code/타 테넌트 셋을 get 과 search 둘 다로) — 🔴 **이 호스트에 Docker 데몬이 없다**(`docker info` rc=1). `compileTestJava` 는 통과했으므로 **컴파일만** 증명됐다. 파생 쿼리 `findByTenantIdAndIdIn`/`CodeIn` 의 실 DB 동작도 같은 이유로 ⚪ — 첫 증거는 CI integration 잡이거나 창.

## Bite (주입 → 빨강 → 복구 → 초록)

- **백엔드** — `resolveSuppliers` 의 code 배치 앞을 `if (true) return resolved;`(id 만 조인)로 바꿨다 → 서비스 테스트 **33 중 4 실패**: code 적중(`AssertionFailedError :514`) · 페이지 배치(`AssertionFailedError :590`) — **값 단언이 문 것** 둘, 그리고 미해석 · 타 테넌트 둘은 `UnnecessaryStubbingException`(code 조회 스텁이 **안 불렸다**는 STRICT_STUBS 의 신호 — 값이 아니라 호출 부재로 물었다). 복구 후 전체 `test` rc=0.
- **콘솔** — `ScmPoTable` 칸을 `{p.supplierId ?? '—'}`(마커는 유지)로 되돌렸다 → `erp-master-ref-names` **36 중 1 실패**, 정확히 새 목록 칸: *«scm 발주 목록: 참조 셀에 UUID 원문이 그려졌습니다: expected [ …(3) ]»*(해석 1 + 미해석 2 = UUID 3건). 상세 칸 테스트는 그 bite 가 안 건드렸으므로 초록 — 대조군으로 맞다. 복구 후 65 passed.

## ⏳ 남은 것 (창이 필요)

- **AC-3** — 새 jar 로 AMI 를 다시 구운 창에서 `/scm/procurement` 「공급사」 칸의 **값**을 본다(필드 존재가 아니라 값; 시드 발주는 id 적중이어야 `SUP-DEMO-01 · demo supplier`). 창이 없으면 ⚪ + 갈 곳 `TASK-MONO-672`.
  🔴 **기대값 갱신 (2026-09-16):** `TASK-MONO-683`(소유자 결정 ⓐ, PR #3860)이 머지되면 시드 공급사 코드는 **`SUP-001`** 이다(wms 코드와 정렬, 이름 `demo supplier` 는 그대로). 그 시드로 구운 AMI 의 신선 볼륨이면 기대값은 **`SUP-001 · demo supplier`** 다. 🔴 창에서 판정하기 전에 **구운 AMI 의 `RepoCommit` 이 683 머지 뒤인지** 먼저 확인하라 — 앞이면 옛 기대값 `SUP-DEMO-01` 이 맞다.
- **AC-3** — 판정이 나면 scm README § Screenshots 보류 사유 되돌림.
- 곁발견(범위 밖, 기록만): `scm-replenishment/components/ReplenishmentTable.tsx:104` 도 `supplierId` 를 원문으로 그린다(추천 행의 값은 D9 상 코드여야 하지만 시드는 UUID 를 넣는다 — 위 D9 결함과 같은 뿌리).

---

# 🔵 창 실측 — 2026-09-17 UTC · AMI `ami-0d30513151d07e163`(RepoCommit `b54296645`) · 창 08:50:37Z~10:08:16Z

- **AC-3** 콘솔 `/scm/procurement`(테넌트 `demo-corp`) 「공급사」 칸: 발주 3건(CONFIRMED · ACKNOWLEDGED · DRAFT) **전부 `SUP-001 · demo supplier`** — UUID 없음. 이미지를 열어 확인. 🔵 코드가 `SUP-DEMO-01` 이 아니라 `SUP-001` 인 것은 `TASK-MONO-683`(결정 ⓐ)이 시드를 바꿨기 때문이다(§ AC-3 기대값 갱신 문단과 일치).
- ⏳ 남은 칸: scm README § Screenshots 보류 사유 되돌리기(판정이 났으므로 이제 할 수 있다).
