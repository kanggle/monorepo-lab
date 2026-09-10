# Task ID

TASK-PC-FE-277

# Title

🔵 **erp-ops 밖에도 id 를 그대로 찍는 칸이 48곳 있다 — 그런데 그중 몇이 «결함» 인지는 아무도 안 물었다**

# Status

ready

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
