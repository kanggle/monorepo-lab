# Task ID

TASK-BE-625

# Title

`ADR-MONO-047` 개정(2026-10-07) — 테넌트를 org-node 에 **두는 · 옮기는 · 빼는** API: 양쪽 관리자 검사 · 감사 · 테넌트 생성의 선택 `orgNodeId`

# Status

ready

# Owner

backend

# Task Tags

- code
- api
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (관리 범위가 옮겨 가는 쓰기 · 격리 대조군)

---

# Dependency Markers

- **선행**: `ADR-MONO-047` § 개정 (2026-10-07 UTC, `TASK-MONO-775`) — 결정 «양쪽을 다 관리하는 사람만» · 라이더 P1 · P2.
- **후속**: `projects/platform-console` `TASK-PC-FE-312`(이 API 의 화면) — 이 티켓 머지 뒤.

# Goal

ADR-047 D1 이 정한 «테넌트를 노드에 묶는다» 를 쓰는 길이 없다 — org-node API 에 테넌트를 붙이는 엔드포인트가 0 이고(`admin-api.md` § Org Hierarchy), `Tenant.assignOrgNode()` 의 호출자가 0 이다(account-service `domain/tenant/Tenant.java:144`). 소유자 결정대로 연다:

- **쓰기 하나**: 테넌트 T 의 소속을 «지금 위치» → «목적지»(노드 D 또는 무소속). 넣기 · 옮기기 · 빼기가 모두 이것.
- **출발 쪽**: actor 가 T 를 관리 — `SUPER_ADMIN` · T 를 subtree 에 둔 노드의 `ORG_ADMIN` · (P1) **무소속 T 일 때만** T 의 `TENANT_ADMIN`.
- **도착 쪽**: 목적지가 D 면 `administers(actor, D)`. 무소속이면 출발 쪽만.
- 범위 밖 → **404**(존재 비노출) + best-effort DENIED 감사 행. 성공 → `admin_actions`(사유 · 이전/이후 노드).
- 테넌트 생성(`POST /api/admin/tenants`)에 선택 `orgNodeId` — 같은 쓰기 규칙(출발 = 무소속, 생성자는 `SUPER_ADMIN`).

# Scope

## In Scope

- **계약 먼저**: `specs/contracts/http/admin-api.md` — `PUT /api/admin/tenants/{tenantId}/org-node`(본문 `{ "orgNodeId": "…" | null }`, `X-Operator-Reason` · `Idempotency-Key` 규율은 org-node 쓰기와 같게) · 테넌트 생성 본문의 선택 `orgNodeId` · 거절 코드. `specs/contracts/http/internal/admin-to-account.md` — 소속 변경 내부 쓰기.
- account-service: 내부 쓰기 → `Tenant.assignOrgNode()` 배선 · 노드 존재 확인 · 같은 값이면 no-op(멱등).
- admin-service: 양쪽 검사(기존 `TenantScopeGuard` · `OrgNodeScopeGuard` 재사용 — 새 판정기를 만들지 않는다) · 감사 · `org.manage` 권한 게이트.
- 상한 효과(P2): 거절하지 않는다. 응답에 이동 전후 유효 상한을 싣거나(콘솔 확인 화면용) 콘솔이 기존 조회로 계산 — AC-0 에서 정한다.
- 테스트: 아래 AC.

## Out of Scope

- 콘솔 화면(`TASK-PC-FE-312`) · 노드 생성 권한(루트 = `SUPER_ADMIN` 만, 그대로) · 상한 의미(deny-only, 그대로).

# Acceptance Criteria

- [ ] **AC-0** — 착수 시 재측정: org-node 엔드포인트 목록 · `assignOrgNode` 호출자 0 · `TenantScopeGuard` / `OrgNodeScopeGuard` · `AdminGrantScopeEvaluator` 의 subtree 판정 file:line. P2 의 «잃는 도메인» 을 API 가 줄지 콘솔이 계산할지 정하고 이유를 적는다. 🔴 TASK-ID 충돌 확인(`TASK-BE-` 네임스페이스가 프로젝트 사이에 공유되는지).
- [ ] **AC-1** — 🔴 격리 대조군(실패 쪽 먼저): 노드 D 의 `ORG_ADMIN` 이 **자기 범위 밖** 테넌트를 D 로 끌어오려 하면 **404 · 소속 불변 · DENIED 행**. 같은 시험에서 양쪽을 관리하는 actor 는 성공.
- [ ] **AC-2** — T 의 `TENANT_ADMIN` 이 노드 S 아래의 T 를 무소속으로 빼려 하면 거절(상한 탈출 금지). S 의 `ORG_ADMIN` 은 성공.
- [ ] **AC-3** — (P1) 무소속 T 의 `TENANT_ADMIN` 이면서 D 의 `ORG_ADMIN` 인 actor → 성공 · `TENANT_ADMIN` 만 → 거절.
- [ ] **AC-4** — 상한이 걸린 노드로 옮긴 뒤 T 의 유효 도메인이 상한과의 교집합으로 줄고, 다시 빼면 원래대로(구독 행 불변).
- [ ] **AC-5** — 테넌트 생성 + `orgNodeId` → 그 노드 아래 생성 · `orgNodeId` 없음 → 무소속(지금과 동일, 회귀).
- [ ] **AC-6** — 같은 요청 반복 = 같은 결과(멱등) · 감사 행에 이전/이후 노드 · 사유.

# Related Specs

- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` D1 · D2 · D5 · D6 · D7 · § 개정 (2026-10-07)
- `specs/services/admin-service/rbac.md` (`org.manage` · `ORG_ADMIN`)
- `specs/features/multi-tenancy.md`

# Related Contracts

- `specs/contracts/http/admin-api.md` § Org Hierarchy · § tenants
- `specs/contracts/http/internal/admin-to-account.md` (org-nodes)

# Edge Cases

- 목적지 = 지금 노드 → no-op 200.
- 노드 삭제와 경합 — 목적지 노드가 그 사이 삭제되면 404(account-service 의 존재 확인이 권위).
- `SUPER_ADMIN` 은 모든 곳을 관리하므로 항상 성공(net-zero).

# Failure Scenarios

1. 도착 쪽만 검사한다 — 노드 관리자가 남의 테넌트를 끌어와 관리 범위를 넓힌다(권한 상승).
2. 출발 쪽에 `TENANT_ADMIN` 을 무조건 넣는다 — 테넌트 주인이 혼자 노드에서 빠져 상한을 벗어난다.
3. 범위 밖을 403 으로 답한다 — 남의 노드·테넌트 존재가 새어 나간다(org-node 규율은 404).
