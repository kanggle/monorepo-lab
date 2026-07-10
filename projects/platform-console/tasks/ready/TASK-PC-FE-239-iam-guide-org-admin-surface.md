# Task ID

TASK-PC-FE-239

# Title

IAM 가이드에 `ORG_ADMIN` / `org.manage` / `/org-hierarchy` 표면 추가 (PC-FE-238 이 PC-FE-237 에 위임한 잔여분)

# Status

ready

# Owner

frontend

# Task Tags

- code
- test
- docs

---

# Dependency Markers

- **선행 (prerequisite)**: `TASK-PC-FE-237` (org-hierarchy 화면) — **DONE, PR #2384 머지됨**. 화면이 존재해야 가이드가 그것을 설명할 수 있다.
- **후속**: 없음.

---

# Goal

`features/iam-guide/data.ts` 는 6개 seed role 만 다룬다. `rbac.md` 가 정의하는 7번째 role `ORG_ADMIN` 과 `org.manage` 권한 키, 그리고 `/org-hierarchy` 메뉴가 빠져 있다.

PC-FE-238 이 이를 **의도적으로** 뺐던 이유는 "그 role 을 조작하는 콘솔 화면이 아직 없다"였고, 그 전제는 **PC-FE-237(PR #2384)이 화면을 구현하면서 거짓이 되었다.** 지금 상태는 스코프 결정이 아니라 드리프트다. `data.ts` 상단 주석이 이 위임을 명시적으로 기록해 두었으므로(조용한 드리프트 방지) 그것을 이행한다.

---

# Scope

## In Scope

`projects/platform-console/apps/console-web/src/features/iam-guide/data.ts` 의 네 구조 + 가이드 테스트:

- `CONSOLE_MENUS` — `/org-hierarchy` 엔트리(목적·가능 작업·게이트=`org.manage`·변경 가능).
- `PERMISSION_KEYS` — `org.manage` 행.
- `SEED_ROLES` — `ORG_ADMIN` (`rbac.md` § Seed Roles 기준: `{org.manage, operator.manage, tenant.admin.delegate}`).
- `SCREEN_ACCESS` — `ORG_ADMIN` 열 + `/org-hierarchy` 행. 매트릭스는 행=메뉴 / 열=역할.
- 가이드 테스트 — 매트릭스를 **DOM 위치**로 단언하므로 열 추가에 따라 함께 갱신.
- `data.ts` 상단의 "알려진 누락" 주석 블록 제거(이행 완료 후 남으면 그 자체가 드리프트).

## Out of Scope

- `rbac.md` 변경 — 이미 `ORG_ADMIN`/`org.manage` 를 정의하고 있다. 가이드가 스펙을 따라가는 방향이지 그 반대가 아니다.
- `ORG_ADMIN` 을 어떤 operator 에 배정하는 것 — seed 는 의도적으로 inert 하다 (`V0041`, `admin_operator_roles` row 없음).
- `SUPER_ADMIN` 을 grantable 하게 만드는 것.

---

# Acceptance Criteria

- [ ] **AC-1**: `SEED_ROLES` 에 `ORG_ADMIN` 이 있고 그 권한 집합이 `rbac.md` § Seed Roles 와 일치한다.
- [ ] **AC-2**: `ORG_ADMIN` 이 **`subscription.manage` / `partnership.manage` 를 갖지 않는다**는 점이 가이드에 드러난다 — 이 때문에 `ORG_ADMIN` 은 `TENANT_ADMIN` 을 발급할 수 없다(no-escalation, ADR-024 D3). 이것은 v1 의 **의도된 한계**이며 테스트를 통과시키려고 seed 를 넓히면 안 된다.
- [ ] **AC-3**: `SCREEN_ACCESS` 에 `/org-hierarchy` 행이 있고, 접근 가능한 역할은 `org.manage` 보유자(`SUPER_ADMIN`, `ORG_ADMIN`)뿐이다.
- [ ] **AC-4**: `PERMISSION_KEYS` 에 `org.manage` 가 있다.
- [ ] **AC-5**: `CONSOLE_MENUS` 에 `/org-hierarchy` 엔트리가 있다.
- [ ] **AC-6**: ceiling 이 **상한(deny-only)** 이지 부여가 아니라는 점이 메뉴 설명에 드러난다 — 가이드가 "허용 도메인 부여"로 읽히면 D2-A 의미가 뒤집힌다.
- [ ] **AC-7**: `data.ts` 상단 "알려진 누락" 주석 블록이 제거된다.
- [ ] **AC-8**: `pnpm lint` 0 · `tsc --noEmit` 0 · `vitest run` 전량 GREEN (가이드 axe 포함).

---

# Related Specs

- `projects/iam-platform/specs/services/admin-service/rbac.md` (§ Permission Keys / § Seed Roles / § Seed Matrix — **authority**)
- `docs/adr/ADR-MONO-047-org-node-tenant-hierarchy.md` (§ D2-A ceiling = deny-only, § D3 `ORG_ADMIN`)
- `docs/adr/ADR-MONO-024-*` (§ D3 no-escalation `RoleGrantGuard`)

# Related Contracts

- 없음 (정적 표시 데이터).

---

# Edge Cases

- 가이드 테스트가 role **열 인덱스**로 셀을 찾는다 → 열 하나 추가가 여러 단언을 밀어낸다. 인덱스가 아니라 헤더 텍스트로 찾도록 고치는 편이 낫다.
- `ORG_ADMIN` 은 어떤 operator 에도 배정되어 있지 않다(inert). 가이드는 이것을 "사용 안 함"이 아니라 "배정 가능하지만 현재 미배정"으로 설명해야 한다.

---

# Failure Scenarios

- `ORG_ADMIN` 에 `subscription.manage` 를 넣어 매트릭스를 "깔끔하게" 만든다 → no-escalation 캡이 뚫려 `ORG_ADMIN` 이 `TENANT_ADMIN` 을 발급할 수 있게 된다. AC-2 가 가드.
- ceiling 을 "부여"로 서술한다 → 운영자가 상한을 넓혀 놓고 접근이 열리길 기다린다. AC-6 가 가드.
- 주석 블록을 남긴 채 데이터만 추가한다 → 주석이 이미 이행된 위임을 계속 지시한다. AC-7 가 가드.
