# Task ID

TASK-MONO-670

# Status

ready

# Title

**org-admin 행이 형제가 이미 싣고 있는 표시명을 싣는다** — `ADR-MONO-073` **ACCEPTED ⓐ** 의 실행

# Owner

monorepo (iam-platform · platform-console)

# Task Tags

- contract
- cross-project
- readability

---

# Goal

`iam admin-service` 의 `GET /{orgNodeId}/admins` 응답에 **운영자 표시명**을 싣고, 콘솔이 그것을
그린다. 🔵 [`ADR-MONO-073`](../../docs/adr/ADR-MONO-073-org-admin-rows-carry-the-operator-display-name.md)
**ACCEPTED — ⓐ 생산자가 조인해서 싣는다**(소유자 결정, 2026-09-11)의 실행이다.

🔴 이 티켓은 **결정하지 않는다.** 갈래는 이미 정해졌고 여기서 다시 열지 마라 — ⓑ(소비자 조회)와
ⓒ(그대로 둔다)가 왜 안 골렸는지는 그 ADR 에 있다.

---

# 🔵 왜 이것이 작은가 — **새 의존성이 0이다**

```java
// GroupAdminUseCase.java:153-161  ← 형제가 이미 하는 조인
AdminOperatorJpaEntity op = adminOperators.findById(m.getOperatorId()).orElse(null);
return new MemberView(..., op == null ? null : op.getDisplayName(), ...);

// OrgNodeAdminUseCase.java:85-89  ← 여기만 안 한다
.map(row -> new OrgAdminGrant(externalOperatorId(row.getOperatorId()), ORG_ADMIN_ROLE, ...))
```

🟢 `OrgNodeAdminUseCase:58` 에 **`AdminOperatorJpaRepository operators` 가 이미 주입돼 있다**
(지금 다른 용도로 쓰고 있다). ⇒ 조회를 새로 배선할 필요가 없다.

---

# Scope

## 포함

| 층 | 바꿀 것 |
|---|---|
| **계약** | `projects/iam-platform/specs/contracts/http/admin-api.md` — 🔴 **구현 전에** |
| 생산자 | `OrgAdminGrant` · `OrgAdminResponse` 에 `displayName` 추가 + `OrgNodeAdminUseCase` 조인 |
| 소비자 | 콘솔 `OrgAdminSchema` zod 한 줄 + 표시(`masterRefLabel` + `data-master-ref`) |
| 가드 | 생산자 유닛 칸 + 콘솔 회귀 가드 하한 |

## 제외

- 🔴 **감사 행(`AdminAuditRowSchema.operatorId`)** — `TASK-MONO-659` § 라이브 정정이 **철회**했다
  (14행 전부 `demo-operator`, UUID 0건). 그리고 그 자리에는 「그때의 이름」 위험이 **있다** —
  ADR 이 그 구분을 결정의 핵심으로 삼았다. **건드리지 마라.**
- 🔴 **`OrgAdminGrantResponse`(단건 grant 응답)** 를 같이 바꿀지는 **이 티켓에서 판단**하라 —
  목록과 단건이 다른 모양이면 그것도 드리프트다. 🔵 다만 `AddGroupMemberResponse` 는 이미
  `displayName` 을 싣는다(형제 선례).

---

# Acceptance Criteria

## AC-0 — 착수 전 재측정

- [ ] 🔴 `OrgNodeAdminUseCase:58` 에 `AdminOperatorJpaRepository` 가 **아직 주입돼 있는지** 확인하라.
      없으면 이 티켓의 「새 의존성 0」 전제가 깨진 것이고, 그때는 **반경을 다시 재라.**
- [ ] 🔴 `GroupAdminUseCase` 의 조인이 **아직 그 모양인지** 확인하라 — 이 티켓은 «형제와 같게
      한다» 이므로 형제가 바뀌었으면 따라갈 대상이 바뀐다.

## AC-1 — 계약 먼저

- [ ] 🔴 `specs/contracts/http/admin-api.md` 의 org-admin 목록 응답에 `displayName` 을 **먼저** 적는다.
      `CLAUDE.md` § Contracts: *"API and event changes must update `specs/contracts/` **before** implementation."*
- [ ] 필드명은 **`displayName`** — `GroupMember` 와 같은 것. 🔴 **열두 번째 이름을 만들지 마라**
      (`TASK-MONO-659` AC-2 둘째 칸이 요구한 것이고, 이 칸이 그 칸을 닫는다).

## AC-2 — 생산자

- [ ] `OrgAdminGrant` · `OrgAdminResponse` 에 `displayName` 추가, `OrgNodeAdminUseCase` 가 조인해 채운다.
- [ ] 🔴 **운영자를 못 찾으면 `null`** — 형제(`GroupAdminUseCase`)와 같은 규칙이다. 🔴 빈 문자열로
      채우지 마라(「이름이 없다」와 「운영자가 없다」가 합쳐진다).
- [ ] 🔴 **`operatorId` 를 지우지 마라.** 더하는 변경이다 — 운영자가 그 id 로 지원 요청을 받는다.

## AC-3 — 소비자(콘솔)

- [ ] `OrgAdminSchema` 에 `displayName: z.string().nullable().optional()`.
      🔵 nullable 인 이유는 위 AC-2 의 규칙 그대로다.
- [ ] `masterRefLabel` + **`data-master-ref` 마커** + 원본 id 는 `title` 에만
      (`TASK-PC-FE-276`/`281` 이 깔아 둔 모양 그대로).
- [ ] 🔴 **회귀 가드 하한을 올려라** — 칸이 늘었는데 하한이 그대로면 비-공허성이 헐거워진다.

## AC-4 — 가드

- [ ] 생산자: 조인이 이름을 채우는 칸 + **참조 미투영이면 `null`** 대조군.
- [ ] 🔴 **bite**: 조인을 지우면 빨강이어야 한다. **실제로 지워서 확인하라.**
- [ ] 🔴 **한계를 적어라** — 콘솔 가드는 «UUID 가 없는가» 를 재지 «이름이 옳은가» 는 못 잰다.

## AC-5 — 되돌려 주기

- [ ] 🔴 **`TASK-MONO-659` AC-2 둘째 칸을 닫아라.** 그 칸이 이 티켓을 이름으로 가리키고 있고,
      *"지금 찍으면 안 쓴 것을 썼다고 하는 것"* 이라고 적혀 있다 — 이 티켓이 랜딩해야 닫힌다.
      🔵 659 는 `in-progress/` 라 편집할 수 있다.

---

# Related Specs / Contracts

- [`ADR-MONO-073`](../../docs/adr/ADR-MONO-073-org-admin-rows-carry-the-operator-display-name.md) — **ACCEPTED ⓐ.** 이 티켓의 근거
- `TASK-MONO-659` AC-2 — 이 의무의 출처. 둘째 칸이 이 티켓을 기다린다
- `TASK-PC-FE-276` / `TASK-PC-FE-281` — `masterRefLabel` · `data-master-ref` · id 폴백 금지
- `projects/iam-platform/specs/contracts/http/admin-api.md` — 🔴 먼저 고칠 파일
- `OrgNodeAdminUseCase.java:58,85-89` · `GroupAdminUseCase.java:153-161` (형제 선례)

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 운영자 레코드가 없다 | `displayName = null` ⇒ 콘솔은 `이름 확인 불가`. 🔴 **id 로 안 돌아간다** |
| 같은 운영자가 여러 노드의 admin | 조회가 노드당 반복된다 — 🔵 형제도 같다. 문제가 되면 그때 잰다 |
| 단건 grant 응답 | § 제외 참조 — **판단하고 적어라** |
| 표시명이 바뀐다 | 🟢 **현재 소속 목록이므로 지금 이름이 맞다.** 이것이 ADR 이 감사 행과 가른 지점이다 |

---

# Failure Scenarios

1. 🔴🔴 **감사 행까지 같이 바꾼다** → ADR 이 명시적으로 뺀 자리이고, 거기엔 「그때의 이름」 위험이 있다.
2. 🔴 **계약을 나중에 고친다** → `CLAUDE.md` § Contracts 위반.
3. 🔴 **`operatorId` 를 `displayName` 으로 교체한다** → 더하는 변경이다(`TASK-PC-FE-277` Failure 1).
4. 🔴 **못 찾은 운영자를 빈 문자열로 채운다** → 「이름이 없다」와 「운영자가 없다」가 영영 안 갈린다.
5. 🔴 **`data-master-ref` 를 안 단다** → 콘솔 회귀 가드의 모집단 밖이라 **초록인데 안 지켜진다.**

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 갈래가 이미 정해졌고(ADR ⓐ) 형제 선례가 같은 파일에 있다.
🔴 다만 **계약 먼저**와 **감사 행 제외**는 기계적이지 않다 — 구현자가 그 경계를 읽어야 한다.
