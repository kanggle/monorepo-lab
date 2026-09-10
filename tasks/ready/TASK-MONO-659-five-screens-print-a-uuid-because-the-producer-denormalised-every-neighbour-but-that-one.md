# Task ID

TASK-MONO-659

# Title

🔴 **화면이 UUID 를 그리는 5곳은 콘솔에서 못 고친다 — 생산자가 옆 필드는 전부 비정규화해 놓고 그 하나만 빼놨다**

# Status

ready

# Owner

monorepo (wms-platform · iam-platform)

# Task Tags

- contract
- readability
- cross-project

---

# Goal

`TASK-PC-FE-277` AC-1 이 「참조 칸의 UUID 를 이름으로 바꾼다」를 하다가 **9곳 중 2곳만
고칠 수 있었다.** 나머지 중 **넷**은 콘솔의 결함이 아니다 — **읽을 이름이 그 화면에 도착조차
하지 않는다.** 그 4개 응답 DTO 에 이미 옆 필드로 하고 있는 비정규화를 한 칸 더 한다.

🔴🔴 **제목과 파일명의 「5곳」은 낡았다 — 넷이다.** 2026-09-10 의 데모 창에서 감사 행을
실제로 열어 보니 그 자리는 결함이 아니었다(§ 라이브 정정). 🔵 **슬러그는 안 고친다** —
바꾸면 이 티켓을 가리키는 곳들이 조용히 낡는다. **세는 곳은 § 실측의 표**다.

🔴 이 티켓은 「콘솔을 고친다」가 **아니다.** 콘솔 쪽 변경은 277 이 이미 했고, 여기서
바뀌는 것은 **생산자의 응답**이다.

---

# 🔵 왜 이 티켓이 따로 있나 — 콘솔에서 고치는 길이 셋 다 막혔다

`TASK-PC-FE-277` 이 세 길을 전부 재 보고 남긴 결과다:

1. **payload 에서 읽는다** → 없다. 아래 표의 다섯 DTO 에 코드/이름 필드가 없다.
2. **화면이 이미 가진 것에서 해석한다** → 2곳은 됐고(그것이 277 AC-1 이다) 이 5곳은 안 된다.
3. **조회를 새로 붙인다** → `useWmsRefs` 가 `features/wms-ops` 안에 있다. `scm-replenishment`
   와 `wms-outbound-ops` 는 **다른 feature** 이고 `tests/unit/layer-dependency-rules.test.ts`
   (*"no feature imports another feature"*)가 실제로 문다 ⇒ `shared/` 승격이 필요한데
   그것은 **아키텍처 결정**(`platform/architecture-decision-rule.md`)이지 화면 수정이 아니다.

🔴🔴 그리고 3번은 **길이 막혀서** 안 하는 게 아니라 **틀린 길이라서** 안 한다: 목록 한
페이지를 더 받아 클라이언트에서 join 하면 페이지 밖 참조가 `이름 확인 불가` 가 되고, 그것은
생산자가 한 줄로 실어 줄 수 있는 값을 화면 개수만큼 복제해 얻은 **더 나쁜 답**이다.

---

# Scope

## 🔴 실측 — 다섯 자리 (2026-09-10 UTC, `TASK-PC-FE-277` AC-1)

| # | 응답 DTO | 없는 것 | 🔴 **같은 DTO 가 이미 하고 있는 것** |
|---|---|---|---|
| 1 | `wms admin-service` `InventorySnapshotResponse` | `warehouseCode` | **`locationCode` · `skuCode` · `lotNo` 셋을 비정규화한다.** 창고만 빠졌다 |
| 2 | `wms admin-service` `AsnSummaryResponse` | `warehouseCode` | **`supplierName` 을 비정규화한다**(`supplierPartnerId` 옆에) |
| 3 | `wms outbound-service` `OrderLineResponse` | `skuCode` | 🔴 이 서비스는 인입 때 **코드로 조회해서** UUID 를 얻는다 (`FulfillmentRequestedConsumer` → `findSkuByCode`) — 코드를 **손에 쥐고 있다가 버린다** |
| ~~4~~ | ~~`iam` 통합 감사 `admin` 행 (`AdminAuditRowSchema`)~~ | — | 🔴🔴 **철회. 아래 § 라이브 정정** |
| 5 | `iam` `OrgAdminSchema` (`GET /{orgNodeId}/admins`) | 운영자 표시명 | 같은 프로젝트의 `GroupMember` 는 **`displayName` 을 싣는다** |

🔵 **1~3 과 5 는 다른 부류다.** 앞 셋은 «옆 칸은 했는데 이 칸만 안 했다» 는 **누락**이고,
5 는 «이 계약이 이름을 한 번도 안 실었다» 는 **설계**다. 한 PR 로 묶지 마라.

### 🔴🔴 라이브 정정 (2026-09-10 UTC, `TASK-MONO-645` ④ 의 데모 창) — **넷이다, 다섯이 아니다**

`AdminAuditRowSchema.operatorId` 를 **결함으로 적은 것이 틀렸다.** 창을 열고 `/audit` 을
로그인해서 열어 보니 행위자 칸의 실제 값은 이것이다:

```
소스   액션/이벤트   행위자/계정      대상/위치        결과      발생 시각
admin  AUDIT_QUERY   demo-operator   *               SUCCESS   2026. 9. 10. 20:59:18
admin  AUDIT_QUERY   demo-operator   demo-operator   DENIED    2026. 9. 10. 20:55:18
admin  UNKNOWN       demo-operator   -               DENIED    2026. 9.  9. 20:21:46
```

**14행 전부 `demo-operator` 이고 UUID 가 한 건도 없다.** `targetId` 도 `*` · `demo-operator` ·
`-` 로 읽을 수 있다(그것은 `TASK-PC-FE-277` 의 ⚪ 목록에 있었고, 여기서 **닫힌다**).

⇒ 🔵 `operatorId` 는 **UUID 가 아니라 운영자 핸들**이다. 생산자가 이름을 안 실은 것이
맞지만 **싣고 있는 값이 이미 읽을 수 있으므로 결함이 아니다.** 이 자리는 이 티켓에서 뺀다.

🔴 **왜 틀렸나 — 같은 실수의 세 번째 반복이다.** 277 은 이 자리를 «시드의 운영자 id 가
UUID 이므로 감사 행의 `operatorId` 도 UUID일 것» 이라고 **추론**했다. 감사 행의 그 필드는
`admin_actions` 가 자기 형식으로 적는 값이고, **다른 테이블의 PK 모양이 그것을 결정하지
않는다.** 277 이 `inspectorId` 에서 이미 같은 추론을 했고 그때도 틀렸다.
⇒ **런타임 값은 그 필드를 실제로 쓰는 곳에서 읽어야 한다.**

## 제외

- **콘솔 화면 수정** — 277 이 했다. 이 티켓이 끝나면 콘솔은 그 필드를 **선언만** 하면 된다
  (277 이 `scm` 에서 한 것이 정확히 그 모양이다: 값은 이미 오고 있었고 없던 것은 zod 한 줄).
- **`useWmsRefs` 의 `shared/` 승격** — 위 3번 이유로 이 티켓의 답이 아니다. 필요해지면 ADR.
- **⚪ 17곳** (`TASK-PC-FE-277` AC-0 ③) — 런타임 값을 아직 못 봤다. 이 티켓의 다섯과 다르다.

---

# Acceptance Criteria

## AC-0 — 고치기 전에, 그 필드가 정말 없는지 **응답에서** 재라

- [ ] 🔴🔴 **DTO 소스 grep 은 답이 아니다.** `.passthrough()` 스키마 아래에서는 「선언이
      없다」와 「값이 안 온다」가 다르다 — 277 이 그것을 밟았다: `scm` 의 `warehouseCode` 는
      **이미 전선에 있었는데** 콘솔 스키마가 선언을 안 해서 없는 줄 알았다. ⇒ 다섯 자리
      각각에 대해 **실제 응답 본문**을 떠서 붙여라(데모 스택 또는 그 서비스의 IT).
- [ ] 🔵 하나라도 「이미 오고 있다」면 그 자리는 **콘솔 한 줄**이고 이 티켓에서 빠진다.
      그 경우 277 처럼 zod 선언 + 표시 + `data-master-ref` 마커로 닫는다.

## AC-1 — 누락 셋 (wms)

- [ ] `InventorySnapshotResponse` · `AsnSummaryResponse` 에 `warehouseCode`,
      `OrderLineResponse` 에 `skuCode` 를 **추가**한다. 🔴 **기존 필드는 지우지 마라** —
      UUID 로 검색하는 경로가 있다(`TASK-PC-FE-277` Failure 1).
- [ ] 🔴 **read-model 에 그 값이 있는지부터** 확인하라. 없으면 프로젝터가 채워야 하고,
      그러면 **기존 행은 NULL 인 채로 남는다**(이 저장소가 이름 붙인 축: 마이그레이션은
      코드 질문 이전에 데이터 질문이다). 그때 재투영이 필요한지도 이 AC 에서 답하라.
- [ ] 🔵 3번은 값을 **인입 시점에 이미 갖고 있다** — 저장하고 있는지부터 보라.

## AC-2 — 설계 둘 (iam)

- [ ] 🔴 **먼저 결정이다, 구현이 아니다.** 감사 행과 org-admin 행에 운영자 표시명을
      싣는 것은 ①생산자가 조인해서 싣는가 ②소비자가 별도 조회로 해석하는가 의 선택이고,
      감사 로그는 **그 시점의 이름**이 필요할 수 있다(지금 이름으로 해석하면 과거 기록이
      바뀐다). 🔴 그 판단을 적지 않고 필드만 더하지 마라.
- [ ] 결정이 「싣는다」면 `GroupMember.displayName` 과 **같은 필드명**을 써라. 열두 번째
      이름을 만들지 마라.

## AC-3 — 콘솔 쪽 마무리

- [ ] 각 자리에서 `TASK-PC-FE-277` 이 쓴 `masterRefLabel` + **`data-master-ref` 마커**를
      단다. 🔴 마커를 안 달면 `tests/unit/erp-master-ref-names.test.tsx` 의 모집단 밖이다.
- [ ] 그 가드의 **하한을 올려라** — 새 칸이 늘었는데 하한이 그대로면 비-공허성이 헐거워진다.

## AC-4 — 🔴 이 티켓이 닫힐 때 277 의 ⚪ 목록도 다시 봐라

- [ ] 277 AC-0 의 ⚪ 17곳 중 여기서 해결되는 것이 있는지 대조하고, 남는 것은 **그대로
      ⚪ 로 남겨라**(추측으로 닫지 마라).

---

# Related Specs / Contracts

- `TASK-PC-FE-277` — 이 다섯을 재서 넘긴 티켓. 술어(`tools/id-cell-census.mjs`)와 분류가 거기 있다
- `TASK-PC-FE-276` — `masterRefLabel` · `codeName` · `data-master-ref` · 회귀 가드의 출처
- `ADR-MONO-050` D9 — *"cross-service identifiers are CODES"*. 🔵 scm 이 이미 따르고 있고, wms 응답 DTO 는 안 따른다
- `projects/wms-platform/apps/admin-service/.../dashboard/dto/InventorySnapshotResponse.java`
- `projects/wms-platform/apps/outbound-service/.../web/dto/response/OrderLineResponse.java`
- `projects/platform-console/apps/console-web/src/shared/api/iam-audit-types.ts`

---

# Edge Cases

- **이름이 바뀐다** — 감사 기록의 운영자 이름을 «지금» 으로 해석하면 과거가 바뀐다(AC-2).
- **read-model 의 기존 행** — 새 컬럼은 NULL 로 태어난다. 화면은 `이름 확인 불가` 를
  그리게 되는데, 🔵 그것은 결함이 아니라 **재투영이 필요하다는 신호**다.
- **코드가 없는 행이 정상인 경우** — scm 의 BATCH 출처 추천이 그렇다(`warehouse_code` NULL).
  🔴 그 행까지 「결함」으로 세지 마라.

---

# Failure Scenarios

1. 🔴🔴 **DTO 를 grep 하고 「없다」로 판정한다.** `.passthrough()` 아래에서 그것은 「선언이
   없다」만 말한다 — 277 이 정확히 그렇게 한 칸을 놓칠 뻔했다.
2. 🔴 **UUID 필드를 코드로 «교체»한다.** 운영자가 그 id 로 지원 요청을 받는다. 더하는 것이지
   바꾸는 것이 아니다.
3. 🔴 **다섯을 한 PR 로 묶는다.** 앞 셋은 누락 수정이고 뒤 둘은 계약 설계다 — 뒤 둘의
   논의가 앞 셋을 붙잡는다.
4. 🔴 **콘솔에 조회를 붙여서 우회한다.** feature 경계를 넘고, 페이지 밖 참조를 잃고,
   화면 수만큼 복제된다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet**(AC-1 의 누락 셋 — 기계적) → **Opus**(AC-2 의 계약 결정).
🔴 AC-0 을 건너뛰면 나머지가 전부 헛일이 될 수 있다.
