# Task ID

TASK-PC-FE-301

# Status

ready

# Title

선택 가능한 테넌트가 **0개**인 운영자에게 «테넌트를 선택하세요» 대신 «접근 가능한 테넌트가 없습니다(권한 없음)» 를 보인다

# Owner

platform-console

# Task Tags

- console-web
- ux
- rbac

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 분기 하나와 문구. 게이트 화면이 여럿이라 **한 곳**에서 판정하게 하는 것이 요점.

---

# Goal

`TASK-MONO-730` AC-1 창(2026-09-26 UTC, 16차 AMI)에서 `viewer@demo.com`(역할 0)으로 로그인 → **스위처 없음** · 모든 게이트 화면이 «테넌트를 먼저 선택하세요».
선택할 수 없는데 선택하라고 한다 — 막다른 길이고, 무권한 계정이 보여야 할 «권한 없음» 에 도달하지 못한다.
- 선택 가능한 테넌트 = 쓸 수 있는 상품의 테넌트(`src/shared/lib/active-tenant-default.ts:44` `selectableTenants`) ⇒ 역할 0 ⇒ 0개.
- 게이트 화면들은 권한보다 테넌트를 먼저 본다(`app/(console)/{operators,audit,tenants,accounts,permissions,permission-sets,operator-groups,org-hierarchy,subscriptions,partnerships,dashboards/*}/page.tsx` · `widgets/domain-tenant-gate/DomainTenantGate.tsx` · 도메인 `layout.tsx` 들).

**소유자 결정 (2026-09-26 UTC) = ⓒ** — 선택 가능한 테넌트가 0 이면 안내를 바꾼다(기각: ⓐ viewer 에 권한 없는 테넌트 부여 · ⓑ 현 동작을 정답으로).

# Scope

## In Scope

- «선택 가능 0» 판정을 **한 곳**에 두고, 위 화면들의 «테넌트를 먼저 선택하세요» 분기가 그것을 쓰게 한다(문구 복제 금지).
- 문구: «이 계정에는 접근 가능한 테넌트가 없습니다 — 권한이 필요합니다(관리자에게 요청하세요)» 수준. 정확한 문구는 구현에서 기존 권한 거부 문구(`/partnerships` 의 «… 권한이 필요합니다») 톤에 맞춘다.
- 스위처가 **있지만 아직 안 고른** 경우(선택 가능 ≥ 2)는 지금 문구 유지.

## Out of Scope

- viewer 계정의 시드/권한 변경(ⓐ, 기각).

# Acceptance Criteria

- [ ] **AC-1** — 단위: 선택 가능 0 → 새 안내 · 1 → 자동 선택(기존 ②) · ≥2 미선택 → 기존 «선택하세요». 게이트 화면 대표 3곳 이상에서 같은 판정 함수를 쓰는지(렌더 테스트).
- [ ] **AC-2** — 🔴 **창 판정**: `viewer@demo.com` → 게이트 화면에서 새 안내 · `demo@demo.com`(대조군) → 스위처 · 정상 화면. 판정 뒤 `TASK-MONO-730` AC-1 · `TASK-BE-597` AC-2 를 이 결과로 다시 닫는다.

# Related Specs

- `specs/…/console-integration-contract.md` § 2.2(테넌트 범위는 OIDC 토큰에서 파생하지 않는다)
- `TASK-PC-FE-292`(활성 테넌트 기본값) · `TASK-MONO-730` § CORRECTION (2026-09-26)

# Related Contracts

- 없음(프런트 표시).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 레지스트리 조회 실패 | «0개» 로 읽지 않는다 — 기존 오류/저하 표시(없음 ≠ 못 읽음) |
| 익명 방문자(샘플 모드, ADR-MONO-074) | 영향 없음 |

# Failure Scenarios

1. **레지스트리 실패를 «테넌트 0» 으로 처리** → 장애 때 모든 운영자에게 «권한 없음» 이 뜬다.
2. **화면마다 문구를 고친다** → 한 곳이 빠진다(«테넌트를 먼저 선택하세요» 는 20곳 넘게 있다).
