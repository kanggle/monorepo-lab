# Task ID

TASK-BE-625

# Title

`ADR-MONO-047` 개정(2026-10-07) — 테넌트를 org-node 에 **두는 · 옮기는 · 빼는** API: 양쪽 관리자 검사 · 감사 · 테넌트 생성의 선택 `orgNodeId`

# Status

review

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

---

# 작업 기록 (2026-10-08 UTC)

## AC-0 — 착수 재측정 (코드 읽기, 착수 시점 `origin/main` = `779195d40`)

| 잰 것 | 결과 | 근거 |
|---|---|---|
| org-node 엔드포인트 | 생성 · 목록 · 단건 · 수정 · 삭제 · 상한 · 소속 테넌트 **조회** · `ORG_ADMIN` 목록/부여/회수 — 테넌트를 붙이는 쓰기 **0** | `admin-api.md` § Org Hierarchy(착수 시 `:2140`~`:2497`) · `OrgNodeAdminController.java:54-158` |
| `assignOrgNode` 호출자 | **0** — 정의 둘뿐(도메인 · JPA 쌍둥이) | account-service `domain/tenant/Tenant.java:144` · `infrastructure/persistence/TenantJpaEntity.java:85` (`assignOrgNode(` grep = 정의 2건) |
| `TenantScopeGuard` | `requireTenantInScope` = **403** `TENANT_SCOPE_DENIED` + DENIED 행 → 이 표면(404 규율)에서는 **부르지 않는다**. 그것이 감싸는 술어만 쓴다 | admin-service `application/TenantScopeGuard.java:49-63` |
| `OrgNodeScopeGuard` | `resolveReach` · `Reach.administers`(SUPER_ADMIN 또는 N·조상의 `ORG_ADMIN`) · `requireAdministers`(404 + DENIED) | `application/OrgNodeScopeGuard.java:124` · `:84` · `:150` |
| `AdminGrantScopeEvaluator` subtree 판정 | `effectiveAdminScope` 의 `'*'` 선스캔 → `org_node_id` 행은 subtree 테넌트(fail-closed) → 그 외 `tenant_id`. `isTenantInAdminScope` 가 그 위의 술어 | `infrastructure/persistence/rbac/AdminGrantScopeEvaluator.java:80-114` · `:209` |
| subtree 캐시 | 성공만 캐시, TTL 기본 5초 | `OrgNodeSubtreeResolver.java:50` |
| 🔴 게이트웨이 노출 | `/internal/tenants/**` 가 테넌트 워크로드에 라우팅되고 path↔JWT 테넌트만 대조 → 소속 쓰기를 그 아래 두면 **테넌트가 혼자 노드에서 빠지는 길**(Failure Scenario 2)이 생긴다 → 내부 쓰기는 `/internal/tenant-placements/**`(게이트웨이 라우트 없음) | gateway `application.yml:61` · `JwtAuthenticationFilter.java:49` |
| 🔴 TASK-ID | `TASK-BE-` 접두사는 **프로젝트 사이에 공유된다**(ecommerce `review/TASK-BE-624` 가 iam `done/TASK-BE-623` 다음 번호). `TASK-BE-625` 는 이 티켓 하나뿐(나머지 출현은 이 티켓을 가리키는 루트 `TASK-MONO-775` · `TASK-PC-FE-312` · ADR 개정의 참조) — 충돌 없음 | `projects/*/tasks/**/TASK-BE-62*` glob |

### P2 결정 — «잃는 도메인» 은 **API 가 준다** (미리보기 + 쓰기 응답)

- **이유 1 — 콘솔은 계산할 재료가 없다.** 잃는 도메인 = T 의 ACTIVE 구독 − 목적지 상한. 이 쓰기의 주 사용자인 `ORG_ADMIN` 은 `subscription.manage` 가 없어(`rbac.md` Seed Matrix) T 의 구독을 읽는 길이 없다. 무소속 T 를 넣는 P1 actor(`TENANT_ADMIN`)도 마찬가지다.
- **이유 2 — 확인은 쓰기 전이다.** P2 는 «확인 화면에 보여 준다» 이므로 효과가 쓰기 **전에** 읽혀야 한다 → 쓰기 응답에만 싣는 것으로는 부족하다. `GET /api/admin/tenants/{tenantId}/org-node/preview?orgNodeId=` 를 두고(쓰기와 **같은 양쪽 판정** — 미리보기로 남의 테넌트 도메인을 읽지 못한다), 쓰기 응답에도 같은 모양(+`changed`)을 싣는다.
- **이유 3 — 계산 지점은 하나.** 구독과 상한의 권위는 account-service 이고 D6 seam 도 거기 있다(`TenantEntitledDomainsQueryUseCase`). 효과 계산도 거기서 한 번 한다(`TenantOrgNodePlacementUseCase.effect`).
- 상한은 거절 사유가 아니다(P2 기본값 그대로) — `lostDomains` 가 있어도 쓴다.

### 구현 중 정한 것 (ADR 결정의 기계적 귀결 — 새 결정 아님, 보고에 명시)

- **경합**: 판정(admin) 과 쓰기(account) 사이에 소속이 바뀌면 판정하지 않은 출발지에서 T 를 빼 오는 길이 생긴다 → 쓰기는 판정한 출발지(`expectedOrgNodeId`)를 조건으로, 테넌트 행 `FOR UPDATE` 아래에서 비교한다. 어긋나면 `409 TENANT_ORG_NODE_CONFLICT`(새 코드 — `platform/error-handling.md` · `rules/domains/saas.md` 에 등록).
- **출발 쪽 실패의 코드**: `404 TENANT_NOT_FOUND`(없는 테넌트와 구별 안 됨), 출발 쪽을 **먼저** 판정 — 관리하지 않는 테넌트로 «목적지 노드가 있는가» 를 캐 볼 수 없다. 도착 쪽 실패는 `404 ORG_NODE_NOT_FOUND`.
- **no-op 도 판정 뒤**: 판정 전에 «이미 D 아래» 를 보고 200 을 주면 소속 정보가 샌다.
- **본문 `{}` 는 400**: `null` 이 «빼기» 라 키 누락이 조용히 빼기가 되면 안 된다.
- **P1 직전 subtree 캐시 비우기**: 방금 S 에서 빠진 무소속 T 가 5초 캐시로 S 의 `ORG_ADMIN` 출발 쪽을 통과하지 않도록. 다른 인스턴스는 TTL 창(기존 grant 회수와 같은 창) — `rbac.md` 에 기록.
- **생성 + `orgNodeId`**: 생성 내부 호출(`POST /internal/tenants`)에 노드 칸을 싣지 않았다(같은 게이트웨이 노출 이유). 도착 판정 → 생성 → 소속 쓰기 순. 그 사이 노드 삭제 경합이면 테넌트는 무소속으로 남고 오류가 응답된다 — 계약에 명시.

## 판정 조합 (구현 위치)

- `OrgNodeScopeGuard.requirePlacementAllowed` — 출발: 노드 S 아래면 `Reach.administers(S)`, 무소속이면 `AdminGrantScopeEvaluator.isTenantInAdminScope(actor, operator.manage, T)`(P1) → 실패 `404 TENANT_NOT_FOUND` + DENIED(`side=SOURCE`). 도착: D 면 `Reach.administers(D)` → 실패 `404 ORG_NODE_NOT_FOUND` + DENIED(`side=DESTINATION`). 새 판정기 없음.
- `TenantOrgNodePlacementUseCase`(admin) — 지금 위치 읽기 → (무소속이면 subtree 캐시 비우기) → 판정 → `place(T, D, expected=판정한 출발지)` → 캐시 비우기 → `TENANT_ORG_NODE_ASSIGN` 감사.
- `TenantOrgNodePlacementUseCase`(account) — `FOR UPDATE` → 출발지 비교(409) → 노드 존재(404) → 같으면 no-op → `Tenant.assignOrgNode()`.

## AC 결과

| AC | 상태 | 닫는 시험 |
|---|---|---|
| AC-0 | ✅ | 위 재측정 표 · P2 결정 · TASK-ID 확인 |
| AC-1 | ✅ 단위 · ⚪ IT | 단위: admin `TenantOrgNodePlacementUseCaseTest$PullIn` — 실패 쪽 먼저(`ORG_ADMIN @ D` → 404 · `place` 호출 0 · DENIED `side=SOURCE`), 같은 시험에서 양쪽 관리자 성공 + 조건부 쓰기(`expected=G`). 🔴 bite: 출발 쪽 판정을 끄면 이 클래스 15건 중 출발 쪽 6건만 빨강(나머지 9건 초록) 확인 후 원복. IT(⚪): `TenantOrgNodePlacementIntegrationTest#destinationOnlyAdmin_cannotPullIn_bothSidesAdminCan` — 작성, 이 호스트에 Docker 없음(`docker info` rc=1) → `integrationTest` 에서 SKIPPED. CI iam 통합 잡이 첫 실행. |
| AC-2 | ✅ 단위 · ⚪ IT | 단위: `$Detach` — `TENANT_ADMIN @ T`(+ 다른 노드 `org.manage`) 빼기 → 404 · 쓰기 0 · `isTenantInAdminScope` 미호출(그룹 T 에 P1 없음); S 관리자 빼기 성공. 슬라이스: `TenantOrgNodePlacementControllerSliceTest#tenantAdminOnly_isDeniedAtTheGate`(`org.manage` 없음 → 403). IT ⚪ 위와 같은 이유. |
| AC-3 | ✅ 단위 · ⚪ IT | 단위: `$RiderP1` — `TENANT_ADMIN @ T` + `ORG_ADMIN @ D` 성공 / `TENANT_ADMIN @ T` + 다른 노드 → 404 `ORG_NODE_NOT_FOUND`(DESTINATION). `TENANT_ADMIN` 만 → 403(슬라이스). IT ⚪. |
| AC-4 | ✅ 단위 · ⚪ IT | 단위: account `TenantOrgNodePlacementUseCaseTest#attachThenDetach_ceilingNarrowsThenRestores`(BOUNDED{wms} 아래로 → finance 잃음, 빼면 회복, 구독 저장소는 읽기 2회 외 상호작용 0). IT(⚪): `OrgNodeHierarchyIntegrationTest#placementNarrowsThenDetachRestores` — 실제 D6 seam(`effectiveEntitledDomains`)으로 축소·회복 + 원시 구독 행 불변. Docker 없음 → SKIPPED. |
| AC-5 | ✅ 단위 · ⚪ IT | 단위: `$CreateUnder` — 생성 → 소속(출발 null) → 감사 순서, 범위 밖 노드는 생성 0. 슬라이스: `TenantAdminControllerSliceTest` — `orgNodeId` 있음 → 소속 경로, 없음 → 기존 경로 · 소속 use-case 상호작용 0(회귀). IT ⚪. |
| AC-6 | ✅ 단위 · ⚪ IT | 단위: `$IdempotenceAndAudit` — 감사 행(`TENANT`/T, `from…to…changed=`, 사유), 반복 = `changed=false`, no-op 도 판정 먼저. account `#sameTarget_noOp`(쓰기 0). IT ⚪. |

⚪ 의 이유는 하나다: 이 호스트에서 Docker 데몬에 닿지 않는다(`docker info` rc=1). 두 IT 클래스는 `:integrationTest --tests …` 로 돌렸고 **전부 SKIPPED** 로 보고됐다(`DockerAvailableCondition`) — «돌았다» 가 아니다. 첫 실제 실행은 이 PR 의 CI iam 통합 잡이다.
