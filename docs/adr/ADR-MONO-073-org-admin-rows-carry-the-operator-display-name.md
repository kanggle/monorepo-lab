# ADR-MONO-073 — org-admin 행이 **운영자 표시명을 싣는다** (그리고 「그때의 이름」 위험은 이 행에 없다)

**Status:** ACCEPTED
**Date:** 2026-09-11
**주관 티켓:** `TASK-MONO-659` AC-2
**근거 실측:** 2026-09-11 UTC — `iam-platform/apps/admin-service` 소스 직접 확인
**선행:** [`ADR-MONO-050`](ADR-MONO-050-cross-service-identifiers-are-codes.md) D9 (교차서비스 식별자는 CODE 다) ·
`TASK-PC-FE-276` (`masterRefLabel` — 콘솔은 **id 로 되돌아가지 않는다**)

**출처 — 소유자 결정 (2026-09-11), 아래 § 갈래의 셋 중:**

> **「ⓐ 생산자가 조인해서 싣는다」**

---

## History

- 2026-09-11 — **ACCEPTED (ⓐ).** 갈래 셋을 각각이 **무엇을 포기하는지**와 함께 올렸고
  소유자가 ⓐ 를 골랐다.
  🔴 **처음 받은 의사표시는 게이트를 통과하지 못했다** — `ADR-MONO-073 ACCEPT` 는 ADR 을
  이름으로 지목했지만 **갈래 letter 가 비어 있었다.** 이 ADR 은 갈래가 셋이고, 내 추천은
  ⓐ 였다. ⇒ 그 상태에서 넘겼다면 `platform/architecture-decision-rule.md` § The ACCEPTED
  Gate 가 금지하는 *"launders an agent's own preference into an accepted decision"* 그
  자체다. **다시 물어서 ⓐ 를 받았다**(우연히 추천과 같았다는 사실은 판단의 정당성과 무관하다).
  🔵 2026-08-07 `ADR-MONO-059`/`060` 에서 **같은 모양**을 한 번 걸러낸 전례가 있다.
- 🔴 **결정 본문(§ 갈래 · § Consequences)은 한 바이트도 안 바꿨다** — ACCEPT 는 *finalise*
  이지 *re-decide* 가 아니다.
- 🔴 ACCEPTED 는 구현을 **authorise 만 한다.** 실행은 별도 티켓이고
  (`platform/architecture-decision-rule.md` HARDSTOP-09), **이 PR 에서 같이 기안했다**:
  **`TASK-MONO-670`**. 🔵 「ACCEPT 후에 기안한다」는 아무도 안 한다 — 산문에는 게이트가 없다.

---

## Context — 무엇이 관측됐나

`TASK-PC-FE-277` 이 콘솔의 참조 칸에서 raw UUID 를 찾다가 **콘솔에서 고칠 수 없는 부류**를
갈라냈다. 그중 하나가 org-admin 목록이다:

```java
// OrgNodeAdminController.java:235
public record OrgAdminResponse(String operatorId, String roleName, Instant grantedAt) {}
```

⇒ **운영자를 가리키는 값이 id 하나뿐**이고, 화면은 그것을 그대로 그리는 것 말고 할 수 있는
것이 없다.

### 🔴 그런데 **같은 서비스의 형제는 이미 이름을 싣는다**

```java
// GroupMemberResponse.java:10 / AddGroupMemberResponse.java:14
String displayName,
```

그리고 그 값을 **어떻게** 얻는지도 같은 코드베이스에 있다:

```java
// GroupAdminUseCase.java:153-161
return groupMembers.findByGroupId(group.getId()).stream()
        .map(m -> {
            AdminOperatorJpaEntity op = adminOperators.findById(m.getOperatorId()).orElse(null);
            return new MemberView(
                    op == null ? null : op.getOperatorId(),
                    op == null ? null : op.getDisplayName(),   // ← 조인 한 줄
                    m.getAddedAt());
        })
```

바로 옆의 org-admin 은 **그 조인을 안 한다**:

```java
// OrgNodeAdminUseCase.java:85-89
return operatorRoles.findByOrgNodeId(orgNodeId).stream()
        .filter(row -> row.getRoleId().equals(orgAdmin.getId()))
        .map(row -> new OrgAdminGrant(
                externalOperatorId(row.getOperatorId()), ORG_ADMIN_ROLE, row.getGrantedAt()))
```

🔵 **`TASK-MONO-659` 가 wms 에서 고친 것과 문자 그대로 같은 모양**이다 — *"옆 칸은 전부
비정규화해 놓고 그 하나만 빼놨다."*

---

## 🔴🔴 결정의 핵심 — **「그때의 이름」 위험은 이 행에 없다**

`TASK-MONO-659` AC-2 가 망설인 이유가 이것이다:

> 감사 로그는 **그 시점의 이름**이 필요할 수 있다(지금 이름으로 해석하면 과거 기록이 바뀐다).

🔴 **그 위험은 참이지만, 그 대상이 이 행이 아니다.** AC-2 는 원래 **두 자리**를 함께 물었다:

| 자리 | 성격 | 「그때의 이름」 문제 |
|---|---|---|
| ~~`AdminAuditRowSchema.operatorId`(감사 행)~~ | **역사적 기록** | 🔴 **있다** — 오늘 이름으로 해석하면 과거가 바뀐다 |
| `OrgAdminResponse`(org-admin 행) | **현재 소속 목록** | 🟢 **없다** — 「지금 누가 관리자인가」이므로 **지금 이름이 맞다** |

🔵 그리고 감사 행은 **이미 철회됐다** — `TASK-MONO-659` § 라이브 정정(2026-09-10 데모 창)이
`/audit` 14행을 열어 보니 행위자 칸이 전부 `demo-operator` 라는 **읽을 수 있는 핸들**이었고
UUID 가 0건이었다. ⇒ **결함이 아니어서 티켓에서 빠졌다.**

🔴🔴 **그러므로 AC-2 를 막고 있던 위험은 남은 자리에 적용되지 않는다.** 이것을 안 가르면
«감사 로그의 시점 문제» 때문에 **현재 소속 목록**이 영영 id 만 싣게 된다.

---

## 갈래 — 🔴 **소유자가 고른다**

### ⓐ 생산자가 조인해서 싣는다 *(추천)*

`OrgAdminGrant`/`OrgAdminResponse` 에 `displayName` 을 더하고, `GroupAdminUseCase` 가 하는
것과 **같은 조회**로 채운다.

- 🟢 **새 의존성이 0이다** — `OrgNodeAdminUseCase:58` 에 `AdminOperatorJpaRepository operators`
  가 **이미 주입돼 있다**(현재 다른 용도로 쓰고 있다). 조인 한 줄이면 된다.
- 🟢 **형제와 필드명이 같다**(`displayName`) — `TASK-MONO-659` AC-2 둘째 칸이 *"열두 번째
  이름을 만들지 마라"* 로 요구한 것.
- 🟢 콘솔은 **zod 한 줄 + 표시**만 하면 된다(`TASK-PC-FE-281` 이 바로 그 모양을 이미 깔았다).
- 🔴 대가: 목록 크기만큼 조회가 는다(N+1). 🔵 다만 org-admin 목록은 **노드당 소수**이고,
  형제인 그룹 멤버 목록이 **이미 같은 방식**으로 돌고 있다 ⇒ 새로 들이는 성질이 아니다.

### ⓑ 소비자(콘솔)가 별도 조회로 해석한다

- 🔴 **`TASK-MONO-659` 가 wms 에서 이미 기각한 길**이다: feature 경계를 넘고, 페이지 밖
  참조가 `이름 확인 불가` 가 되며, **화면 개수만큼 복제**된다.
- 🔴 그리고 그 티켓의 판단은 *"길이 막혀서가 아니라 **틀린 길이라서** 안 한다"* 였다.
  같은 근거가 여기에도 적용된다.

### ⓒ 아무것도 안 한다 — id 만 싣는다

- 🔵 정직한 선택지다. org-admin 화면은 운영자가 **자기 조직의 관리자**를 보는 자리이고,
  그 수가 적으면 id 로도 식별이 가능할 수 있다.
- 🔴 대가: 콘솔이 `이름 확인 불가` 를 그린다(`TASK-PC-FE-276` 이 **id 폴백을 금지**했으므로
  UUID 를 그리는 선택지는 이제 없다). ⇒ **화면이 아무 정보도 못 준다.**
- 🔴 그리고 `GroupMember` 와 **같은 화면군에서 다른 약속**이 남는다.

---

## Consequences — ⓐ 를 고르면

- `iam admin-service` 의 계약이 바뀐다(**추가**이므로 기존 소비자는 안 깨진다).
- `projects/iam-platform/specs/contracts/` 를 **구현 전에** 고쳐야 한다(`CLAUDE.md` § Contracts).
- 콘솔 쪽은 `TASK-PC-FE-281` 이 깔아 둔 `masterRefLabel` + `data-master-ref` 자리에 그대로 얹힌다.
- 🔴 **가드**: 생산자 쪽에 «이름이 실린다» 를 무는 칸이 필요하다. `GroupAdminUseCase` 의
  대응 테스트가 이미 있으면 **같은 모양**으로 쓴다.

## 🔴 안 고른 것을 숨기지 않는다

ⓑ 는 **틀린 길**이라 버린다(위 근거). ⓒ 는 **버려지지 않고 갈래로 남는다** — org-admin 목록이
운영상 거의 안 쓰인다는 관측이 나오면 ⓒ 가 옳아진다. 🔵 그 관측은 지금 **없다**(라이브에서
그 화면의 사용을 잰 적이 없다). 그것이 ⓒ 를 추천하지 않는 이유이지, ⓒ 가 틀렸다는 뜻은 아니다.

## ⚪ 이 ADR 이 재지 **못한** 것

- **그 화면이 실제로 얼마나 쓰이는가** — 라이브 사용량을 안 쟀다. ⓒ 의 근거가 될 수 있는
  유일한 관측인데 없다.
- **org-admin 목록의 실제 크기** — N+1 의 비용이 그것에 달려 있다. 🔵 다만 형제가 같은
  방식으로 이미 돌고 있어 **새로 들이는 위험이 아니다**.
