# Task ID

TASK-PC-FE-277

# Title

🔵 **erp-ops 밖에도 id 를 그대로 찍는 칸이 48곳 있다 — 그런데 그중 몇이 «결함» 인지는 아무도 안 물었다**

# Status

in-progress

# Owner

platform-console

# Task Tags

- ui
- readability
- census

---

# Goal

`TASK-PC-FE-276` 이 `/erp/masters` 의 8곳을 고치면서 **다른 도메인을 셌다.** 그 수를 여기
넘겨받아, **어느 것이 진짜 결함인지 가른다.** 🔴 이 티켓은 「48곳을 고친다」가 아니다 —
**세는 것과 가르는 것이 먼저**이고, 고칠 목록은 그 결과로 나온다.

---

# 🔵 왜 이 티켓이 따로 있나

`TASK-PC-FE-276` 의 AC-0 ②가 *"다른 도메인도 세라. **있어도 이 티켓에서 고치지 마라** —
수만 적고 별도 티켓을 낸다. 🔵 안 세면 다음 사람이 「erp 만 그랬다」고 믿는다"* 라고 했다.
셌고, 여기 넘긴다.

---

# 🔴 실측 (2026-09-10 UTC, `TASK-PC-FE-276` 착수 중)

## 술어와 그 한계를 먼저 적는다

```
술어: console-web 의 .tsx 에서 `{x.<something>Id}` 또는 `{x.<something>Id ?? '…'}` 를
      **보이는 텍스트 자리**에 렌더하는 줄
제외: key= · data-testid= · value= · 그 밖의 prop 전달
```

**결과: 48곳 / 12 도메인** (`features/erp-ops/` 는 276 이 고쳤으므로 제외):

| 도메인 | 곳 |
|---|---|
| `ecommerce-ops` | 11 |
| `ledger-ops` | 10 |
| `scm-replenishment` | 4 |
| `scm-ops` | 4 |
| `finance-ops` | 4 |
| `wms-ops` | 3 |
| `tenants` | 3 |
| `org-hierarchy` | 3 |
| `wms-outbound-ops` | 2 |
| `operator-groups` | 2 |
| `audit` | 1 |
| `accounts` | 1 |
| **합계** | **48** |

🔵 `operators/OrgScopeDialogBody.tsx` 는 **이 48에 없다** — 그 파일은 이미 이름을 그리고
있고(§ 특수), 이 술어는 「id 를 그대로 찍는 줄」만 센다.

## 🔴🔴 그런데 이 48은 «결함 수» 가 **아니다**

**이 술어는 두 가지를 못 가른다:**

1. **자기 식별자 vs 참조.** `OrdersTable` 의 `{o.orderId}` 는 그 행 **자신의 id** 이고,
   주문 화면에서 주문번호를 보여 주는 것은 정상이다. 276 의 결함은 **다른 엔티티를 가리키는
   칸**(FK)에 그 대상의 이름 대신 id 가 나오는 것이었다.
2. 🔴 **그 값이 런타임에 UUID 인가 읽을 수 있는 코드인가.** ERP 마스터의 id 는 UUID 였다
   (`01a085a1-…` 실측). 그러나 `skuId` 가 `SKU-001` 이면 그것은 **읽을 수 있고 결함이
   아니다.** 이것은 **선언에서 못 읽는다** — 콘솔의 zod 스키마는 전부 `z.string()` 이고
   `.uuid()` 는 **한 건도 없다**(실측).

⇒ **선언 grep 은 런타임 모집단이 아니다.** 그래서 이 티켓의 본체는 고치기가 아니라
**가르기**다.

## 🔵 먼저 볼 만한 후보 (가르지 않은 채, 눈에 띈 것만)

이름을 가진 대상을 가리키는 것으로 **보이는** 칸들 — 🔴 확인 안 했다:

- `audit/AuditRowCells.tsx` · `org-hierarchy/OrgAdminPanel.tsx` — `operatorId`(운영자는 이름이 있다)
- `org-hierarchy/OrgNodeDetail.tsx` — `parentId`(조직 노드는 이름이 있다)
- `wms-ops` · `scm-replenishment` — `warehouseId` · `supplierId`
- `ecommerce-ops` — `sellerId`
- `finance-ops/TransactionsTable.tsx` — `counterpartyAccountId`

## § 특수 — `operators/OrgScopeDialogBody.tsx:91` 는 **다른 종류**다

```tsx
{dept ? codeName(dept) : id}
```

이름을 찾으면 그리고, **못 찾으면 id 로 되돌아간다.** 🔴 276 의 Edge Case 가 정확히 금지한
모양이다: *"id 로 조용히 되돌아가지 마라. 그러면 결함이 «가끔» 되살아나고 그때는 아무도
안 본다."*

🔴🔴 **276 은 이 파일을 결국 통째로 안 건드렸다.** 처음엔 포맷만 헬퍼로 통일하려 했는데,
그 import 가 **feature 간 import** 가 되어 콘솔의 아키텍처 가드가 물었다(§ AC-2 에 실측).
되돌렸다. 여기서 순서를 정한다.

---

# Scope

## 포함

- 48곳을 **자기 식별자 / 참조** 로 가른다
- 참조인 것들에 대해 **런타임 값이 UUID 인가**를 확인한다
- 결함으로 판정된 것만 고친다 — `TASK-PC-FE-276` 의 `masterRefLabel` / `codeName` 을 **쓴다**
- `OrgScopeDialogBody` 의 id 폴백 처리

## 제외

- 🔴 **`features/erp-ops/`** — `TASK-PC-FE-276` 이 이미 고쳤다(8곳 + 마커 + 가드)
- 🔴 **새 포맷을 만드는 것** — `codeName` 이 이미 있다
- 백엔드 계약 변경 — 필요하면 **선행 티켓**이다

---

# Acceptance Criteria

## AC-0 — 세기 전에 술어부터

- [ ] 🔴 **위 48 을 그대로 믿지 마라.** 같은 술어로 다시 세고, 그 사이 늘었는지 적어라.
      🔵 이 저장소는 「줄 번호는 낡는다」를 여러 번 겪었다 — 술어로 세라.
- [ ] 🔴 **자기 식별자 / 참조**로 가른다. 가른 기준을 적어라(예: 그 필드가 **다른** 목록의
      `id` 를 가리키는가).
- [ ] 🔴🔴 **런타임 값을 확인하라 — 선언으로는 못 판정한다.** 콘솔 zod 는 전부
      `z.string()` 이다. ⇒ 콘솔이 떠 있는 창에서 화면을 보거나, 그 도메인의 시드/픽스처에서
      실제 값 모양을 읽어라. 🔵 **못 하면 ⚪ 로 적고 그 항목은 고치지 마라** —
      「읽을 수 있는 코드」를 「이름으로 바꿔야 할 UUID」로 오판하는 것이 이 티켓의 위험이다.

## AC-1 — 결함만 고친다

- [ ] 판정된 것만. 🔴 **자기 식별자를 이름으로 바꾸지 마라** — 운영자는 그 id 로 검색한다.
- [ ] `TASK-PC-FE-276` 의 `masterRefLabel` / `codeName` 을 **재사용**한다.
- [ ] 참조 칸에는 **`data-master-ref` 마커**를 단다 — 276 의 회귀 가드가 그것으로
      모집단을 만든다. 🔴 마커를 안 달면 그 칸은 **가드 밖**이다.

## AC-2 — `OrgScopeDialogBody` 의 id 폴백

- [ ] 🔴🔴 **선행이 있다 — 헬퍼를 `shared/` 로 옮겨야 한다.** `OrgScopeDialogBody` 는
      `features/operators/` 이고 헬퍼는 `features/erp-ops/lib/` 다. 그 import 는 콘솔의
      **아키텍처 규칙 위반**이고, `tests/unit/layer-dependency-rules.test.ts`
      (*"no feature imports another feature (deep OR through its barrel)"*)가 **실제로
      문다.** 🔵 276 이 이것을 실측으로 밟았다: 그 import 를 넣자 그 테스트와
      `OperatorsScreen.test.tsx` 가 **빨개졌고**, 되돌리자 둘 다 통과했다. ⇒ 276 은 이
      파일을 **손대지 않은 채로 남겼다.**
- [ ] 그래서 순서는 ①`codeName`/`masterRefLabel` 을 `shared/lib/` 로 이동(erp-ops 는 거기서
      re-export 하거나 직접 import) → ②`OrgScopeDialogBody` 수정이다.
      🔴 ①은 **erp-ops 전체를 건드리는 이동**이므로 그 자체로 회귀 위험이 있다 —
      276 의 가드가 그 이동을 지킨다(같은 술어가 계속 초록이어야 한다).
- [ ] `{dept ? … : id}` 를 `masterRefLabel` 로 바꾼다 — 못 찾으면 **`이름 확인 불가`**.
- [ ] 🔵 그 화면은 «범위 선택» 이라 id 가 필요할 수 있다 ⇒ 필요하면 `title` 로 싣는다
      (276 이 쓴 방법: 보이는 텍스트가 아니므로 UUID 가드가 안 문다).

## AC-3 — 가드

- [ ] 🔴 **276 의 가드를 넓혀라. 새로 짓지 마라.** `tests/unit/erp-master-ref-names.test.tsx`
      의 술어(`[data-master-ref]` 셀에 UUID 정규식)가 그대로 적용된다.
- [ ] 🔴 **비-공허성**: 하한의 대상은 «렌더된 참조 셀의 수» 다(276 에서 그 하한이 실제로
      물었다 — 픽스처를 비우니 `참조 셀이 0개 — 하한 3`).
- [ ] **bite** — 한 곳을 되돌리면 빨강. 🔴 주입·실행·bite 를 각각 단언하라.

## AC-4 — 판정 불가를 기록한다

- [ ] 🔴 런타임 확인을 못 한 항목은 **«고쳤다» 도 «괜찮다» 도 아니다.** ⚪ 로 목록을 남기고,
      그것을 볼 수 있는 창(`TASK-MONO-645` 계열의 데모 창)에 얹어라.

---

---

# 🟢 AC-0 (2026-09-10 UTC · `in-progress`)

## ① 다시 셌다 — 🔴🔴 그리고 **내 첫 수(111)는 내 추출기의 결함이었다**

같은 술어로 다시 세려 했더니 **111** 이 나왔다(기안은 48). 코드가 두 배로 늘었을 리 없으니
둘 중 하나가 틀렸다 — **내 쪽이었다.**

`OrgAdminPanel.tsx` 를 **눈으로 열어 보다가** 알았다. 내 정규식이 이것을 세고 있었다:

```tsx
data-testid={`org-admin-row-${a.operatorId}`}    ← 보이는 텍스트가 아니다
```

`=` 바로 뒤의 `{` 는 속성이라고 뺐는데, **템플릿 리터럴 안의 `${...}`** 는 앞 글자가 `$` 라
그 제외를 빠져나갔다. 🔵 술어를 좁히자(`$` 도 제외) **66** 이 됐고, 도메인 분포가 기안의
모양과 **같아졌다**(ecommerce-ops 가 가장 크고 ledger-ops 가 그다음).

| 도메인 | 내 술어 | 기안 | | 도메인 | 내 술어 | 기안 |
|---|---|---|---|---|---|---|
| `ecommerce-ops` | 16 | 11 | | `org-hierarchy` | 3 | 3 |
| `ledger-ops` | 14 | 10 | | `tenants` | 3 | 3 |
| `operator-groups` | 6 | 2 | | `wms-ops` | 3 | 3 |
| `scm-ops` | 5 | 4 | | `audit` | 2 | 1 |
| `finance-ops` | 4 | 4 | | `wms-outbound-ops` | 2 | 2 |
| `scm-replenishment` | 4 | 4 | | `accounts` | 1 | 1 |
| `operators` | 3 | (0) | | **합계** | **66** | **48** |

🔴🔴 **그래도 48 과는 다르고, 두 수는 여전히 비교 가능한 값이 아니다.** 기안이 적은 것은
산문이고 — *"「보이는 텍스트 자리」에 렌더"* — **그것을 무엇으로 판정했는지가 없다.**
같은 술어로 다시 셀 수가 없으니 「그 사이 늘었나」도 답할 수 없다
(`feedback_comparing_two_extracts_measures_extractors`: 두 추출값의 비교는 추출기를 잰다).

⇒ 🔵 **이번 수는 재현 가능하게 만든다.** 술어를 스크립트로 박고 **커밋한다**:

```
매치   /(.)\{\s*ident\.[A-Za-z_$][\w$]*Id\s*(\?\?[^}]*)?\}/
제외   앞 글자가 '='(JSX 속성값) 또는 '$'(템플릿 리터럴 보간)
모집단 console-web 의 src/features/**/*.tsx 272개 · features/erp-ops/ 제외(276 이 고쳤다)
결과   66곳 / 13 도메인
```

🔴 **이 술어의 한계도 적는다**: 줄 단위라 여러 줄에 걸친 JSX 를 못 본다. 즉 66 은
**하한**이다.

## ② 가른다 — 🔴 **66 은 «고칠 목록» 이 아니다**

가른 기준(기안이 적으라고 한 것): **그 필드가 «다른» 엔티티의 `id` 를 가리키는가.**
같은 행 자신의 id 면 자기 식별자다.

### 🔴 결함 — 참조이고 런타임이 UUID 다 (**9곳**)

| 필드 | 그리는 곳 | 시드의 실제 값 | 읽을 이름 |
|---|---|---|---|
| `operatorId` | `AuditRowCells` · `GroupDetail` · `OrgAdminPanel` (각 1) | `0199de71-0000-7000-8000-00000000ec01` | ✅ 운영자 이름 |
| `warehouseId` | `ReplenishmentTable` · `WmsAsnDataTable` · `WmsInventoryDetailPanel` (각 1) | `01910000-…-0001` (`warehouse_code='WH01'` 별도) | ✅ |
| `skuId` | `OutboundDrillLines` | `01910000-…-0401` (`sku_code='SKU-BOX-001'` 별도) | ✅ |
| `inspectorId` | `AsnInspectionPanel` | 운영자 참조 | ✅ |
| `parentId` | `OrgNodeDetail` | 조직 노드 참조 | ✅ |

### 🔵 참조지만 **읽을 수 있다** — 고치지 않는다 (**16곳**)

| 필드 | 곳 | 시드의 실제 값 | 근거 |
|---|---|---|---|
| `tenantId` | 9 | **`demo-corp`** · **`ecommerce`** | `infra/demo/seed/` — 운영자의 멘탈 모델 그 자체다 |
| `sellerId` | 7 | **`'default'`** (+ `display_name='Default Seller'`) | `V14__add_seller_axis.sql:31,38` |

🔵 `ecommerce-overview-state.test.ts` 가 *"name-map cell (sellerId → displayName)"* 과
*"No name map → the raw sellerId stays the label (never blank)"* 를 이미 갖고 있다 —
그쪽은 **이미 이름을 그리려 시도하고** 있다. 새로 고칠 것이 아니다.

### 🔵 자기 식별자 — 고치지 않는다 (**24곳**)

`orderId`(`OrderDetail`·`OrdersTable`) · `groupId`(`GroupDetail`) · `accountId`(`AccountDetail`) ·
`entryId`(`JournalEntryDetail`) · `orgNodeId`(`OrgNodeDetail`) · `periodId`(3) ·
`statementId` · `transactionId` · `payoutId` · `shippingId` · `poId` · `promotionId` ·
`discrepancyId`(`DiscrepancyDetail`) · `sellerId`(`SellerDetail`·`SellersTable`) 등.

🔴 **자기 식별자를 이름으로 바꾸지 마라** — 운영자는 그 id 로 검색하고 지원 요청을 받는다.

### ⚪ 판정 못 함 — 고치지 않는다 (**17곳**)

`journalEntryId`(3) · `sourceJournalEntryId`(2) · `nodeId`(3) · `supplierId`(3) ·
`entryId`(`AccountDetail` 2) · `lotId` · `materializedPoId` · `counterpartyAccountId` ·
`reversalOfEntryId` · `reversalOfTransactionId` · `targetId` · `userId` ·
`assignOperatorId` · `orderId`(`AccrualsTable`·`ShippingsTable`) · `discrepancyId`(`StatementDetail`)

🔵 그중 **`journalEntryId` 계열은 «이름이 없는 엔티티»** 일 가능성이 높다(기안 Edge Cases:
*"분개는 이름이 없다. id 가 유일한 표시다"*). 🔴 **그러나 확인하지 않았으므로 ⚪ 다** —
추측으로 고치는 것이 이 티켓의 위험이라고 AC-0 ③ 이 못 박았다.

## ③ 런타임 확인 — **시드가 갈랐다. 선언은 못 갈랐다**

콘솔 zod 는 전부 `z.string()` 이고 `.uuid()` 는 0건이다(기안 실측, 재확인). 그래서 값의
모양은 **시드/마이그레이션**에서 읽었다.

🔴🔴 **그리고 기안의 추측 하나가 틀렸다.** 기안은 이렇게 적었다:
*"그러나 `skuId` 가 `SKU-001` 이면 그것은 **읽을 수 있고 결함이 아니다**."*

`R__04_seed_dev_skus.sql` 이 실제로 넣는 값:

```
id       = '01910000-0000-7000-8000-000000000401'   ← 화면에 그려지는 것
sku_code = 'SKU-BOX-001'                             ← 별도 컬럼
```

⇒ **`skuId` 는 UUID 이고, 읽을 수 있는 코드는 옆 칸에 따로 있다.** `warehouses` 도 같은
모양이다(`id` UUID + `warehouse_code='WH01'`). 🔵 반대로 `sellerId` 는 진짜로 `'default'`
라서 기안의 그 문장이 **`sellerId` 에 대해서는 맞았다** — 필드마다 답이 다르고, 그것이
「선언으로는 못 판정한다」의 실제 내용이다.

## AC-2 의 선행을 **먼저** 했다 — AC 순서와 실제 의존이 반대다

기안은 AC-1(고치기) → AC-2(`OrgScopeDialogBody`, 선행: 헬퍼 `shared/` 이동) 순인데,
🔴 **고칠 9곳이 전부 `erp-ops` 밖이라 AC-1 도 그 이동을 선행으로 갖는다.** 아니면 9곳이
전부 feature 간 import 가 되어 `layer-dependency-rules` 가 문다(276 이 실측으로 밟은 그것).

⇒ `features/erp-ops/lib/master-ref-label.ts` → **`shared/lib/master-ref-label.ts`** 로
`git mv` 하고 `erp-ops` 의 import 10곳을 `@/shared/lib/…` 로 갱신했다.
🔵 **re-export 껍데기를 남기지 않았다** — 남기면 같은 사실이 두 집을 갖는다.
🔴 `tests/unit/erp-master-ref-names.test.tsx`(276 의 가드)도 옛 경로를 가리키고 있어 함께
갱신했다 — **`tsc` 가 그것을 잡았다**(`TS2307`). 이동이 참조를 깨뜨렸고 타입체커가 물었다.

# Related Specs / Contracts

- `TASK-PC-FE-276` — 이 수를 넘겨준 티켓. `masterRefLabel` · `codeName` · `data-master-ref`
  마커 · 회귀 가드가 전부 거기서 왔다
- `projects/platform-console/apps/console-web/src/features/erp-ops/lib/master-ref-label.ts`
- `projects/platform-console/apps/console-web/tests/unit/erp-master-ref-names.test.tsx`
- `TASK-MONO-648` — 이 부류가 처음 눈에 띈 경로(포트폴리오 캡처)

---

# Edge Cases

- **자기 식별자인데 UUID 다** — 예: `OrderDetail` 의 `{data.orderId}`. 🔴 이것은 결함이
  아닐 수 있다(운영자가 그 id 로 지원 요청을 받는다). 바꾸려면 **그 화면의 사용자**를 먼저
  적어라.
- **이름이 없는 엔티티** — 분개(journal entry)는 이름이 없다. id 가 유일한 표시다.
- **런타임을 못 봤다** — AC-0 ③의 ⚪ 로 남긴다. 추측으로 고치지 마라.

---

# Failure Scenarios

1. 🔴🔴 **48곳을 전부 «결함» 으로 세고 전부 고친다.** 자기 식별자를 이름으로 바꾸면
   운영자가 지원 화면에서 id 로 검색하는 경로가 죽는다.
2. 🔴 **선언 grep 만 보고 판정한다.** 콘솔 zod 는 전부 `z.string()` 이라 UUID 인지 못
   가른다 — 「읽을 수 있는 코드」를 UUID 로 오판한다.
3. 🔴 **`data-master-ref` 마커를 안 단다.** 고쳐도 가드 밖이라 다음에 되돌아가도 안 보인다.
4. 🔴 **새 포맷을 만든다.** `codeName` 이 이미 있고, 276 이 그것을 **11곳의 인라인 복제에서
   통일**했다. 열두 번째를 만들지 마라.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus**(AC-0 의 가르기) → **Sonnet**(AC-1 의 교체).
🔴 이 티켓의 본체는 **세기와 가르기**다. 교체는 그 뒤에 기계적이다.
