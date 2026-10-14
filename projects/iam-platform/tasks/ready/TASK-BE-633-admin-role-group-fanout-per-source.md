# Task ID

TASK-BE-633

# Title

관리 역할 그룹 fan-out 의 **단일 출처** 결함 — G1 회수가 G2 가 준 같은 역할을 지운다 · 그룹 행 위 직접 역할 PATCH 의 조용한 출처 변환(추론) 확인

# Status

ready

# Owner

backend

# Task Tags

- code
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (fan-out · PK · 회수 경로)
> 출처: `ADR-MONO-083` § Outstanding(ACCEPT PR 에서 기안 — «ACCEPT 후 기안» 을 산문에만 남기지 않는다).

# Dependency Markers

- **선행**: 없음(ADR-083 단계와 독립). `TASK-BE-631` 이 같은 문제를 도메인 세트에서 출처별 행으로 푼다 — 그 설계를 재사용할지 AC-0 에서 정한다.

# Goal

관리 역할(`admin_operator_roles`) 그룹 fan-out 은 행 하나에 `group_origin` 한 칸이라, 두 그룹이 같은 역할을 주면 먼저 만든 그룹이 행을 갖고 그 그룹 회수 시 다른 그룹 멤버의 권한이 사라진다(`GroupFanOutService.java:131-145` · `data-model.md:243` 이 v1 성질로 인정). 이것을 출처별로 안전하게 만든다.

# Scope

## In Scope

- AC-0 측정: G1 · G2 중복 회수 시험(지금 빨강을 먼저 확인) · 🔵 그룹 행 위 같은 테넌트 직접 역할 PATCH 가 `saveAll` merge 로 그룹 행을 감사 없이 직접 행으로 바꾸는지 시험으로 확인(지금은 코드 추론 — `PatchOperatorRoleUseCase.java:126-132` · `JpaAdminOperatorAdapter.java:373-381` · 엔티티에 `Persistable` 없음).
- 출처별 표현(출처 표 또는 PK 확장)과 마이그레이션 · fan-out · 회수 · 내보내기 · 784 의 마지막 관리자 가드 대조.
- 비-내보내기 경로(그룹 TENANT_ASSIGNMENT 회수 · 탈퇴 · 그룹 삭제)가 다른 테넌트의 직접 역할 행을 소속 없이 남기는지(784 D-1 불변식) 측정 · 필요하면 수선.

## Out of Scope

- 그룹을 상속 모델로 전환(ADR-046 D2-B — 별도 ADR).
- 도메인 세트(`TASK-BE-631`).

# Acceptance Criteria

- [ ] **AC-0** — 위 측정 셋의 결과(빨강/초록 · file:line) · 수선 설계 선택과 이유.
- [ ] **AC-1** — 🔴 대조군: G1 · G2 가 같은 역할 → G1 탈퇴 → 역할 유지 → G2 탈퇴 → 사라짐 · 직접 + 그룹 → 그룹 회수 뒤 직접 유지 · 직접 회수 뒤 그룹 유지.
- [ ] **AC-2** — 직접 PATCH 와 그룹 행 공존 시 감사에 출처 변화가 남거나 두 출처가 공존한다(조용한 변환 0).
- [ ] **AC-3** — 784 불변식: 비-`'*'` 역할 행의 테넌트 ∈ 소속 — 그룹 경로 회수 뒤에도 참.

# Related Specs

- `docs/adr/ADR-MONO-046-operator-group-model.md` D2 · D5 · `docs/adr/ADR-MONO-083-…md` D4 · § Outstanding
- `projects/iam-platform/specs/services/admin-service/data-model.md` · `rbac.md`

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` § groups (응답에 출처가 드러나는 칸이 바뀌면)

# Edge Cases

- 같은 역할을 직접 + 두 그룹이 주는 상태에서 그룹 삭제 — 남는 출처 둘.
- 마지막 관리자 가드(784)가 «다른 출처가 남아 있음» 을 보고 회수를 허용해야 하는 경우.

# Failure Scenarios

1. 기존 데이터 이전 없이 PK 만 바꾼다 — 이미 «먼저 쓴 그룹» 에 묶인 행의 다른 출처가 영영 기록되지 않는다(재-fan-out 필요).
2. 추론(PATCH merge)을 측정 없이 사실로 적고 고친다 — 없는 결함을 고치거나 있는 결함을 놓친다.

---

# Implementation Record

> 2026-10-14 UTC · backend-engineer (Opus 5.5) · worktree `task/be-633-admin-role-group-source`. 커밋 · PR 전.

## AC-0 — 측정 상태 (🔴 정직하게: 로컬에서 «돌린» 빨강은 0)

이 호스트에 Docker 가 없어 Testcontainers IT 를 **로컬에서 실행하지 못했다**. 세 측정은 «V0053 이전 코드에서 빨강이도록 쓴 IT» 로 고정했고, 첫 단언은 V0053 전후 모두 있는 칸(`group_origin` · 행 수)만 읽어 옛 코드에서는 **SQL 오류가 아니라 결함 지점의 단언**으로 실패하게 했다. 빨강의 실측은 CI 몫이다 — 테스트만 먼저 올린 커밋(수정 없이)을 CI 에 한 번 태우면 아래 (a)(b)(c) 가 빨강으로 찍힌다.

| # | 측정 | 옛 코드 경로 (file:line, 수정 전) | 고정한 IT | 상태 |
|---|---|---|---|---|
| (a) | G1 · G2 같은 역할 → G1 탈퇴 → 역할 사라짐 | `GroupFanOutService.java:142-145`(3칸 `existsById` → G2 skip) · `:101`(`deleteByGroupOriginAndOperatorId`) | `AdminRoleSourceIntegrationTest.twoGroupsSameRole_leaveOrder` · `lastAdmin_otherSourceRemains`(옛 코드: G1 탈퇴가 409) | 코드 경로 — CI 판정 대기 |
| (b) | 그룹 행 위 같은 역할 직접 PATCH → 조용한 출처 변환 | `PatchOperatorRoleUseCase.java:126-132` → `JpaAdminOperatorAdapter.java:373-381` `saveAll` → `SimpleJpaRepository.save` 가 id 있는 엔티티(`@IdClass`, `@Version`·`Persistable` 없음)를 `merge` → 3칸 키가 같은 그룹 행에 `group_origin = NULL` 덮어쓰기. 같은 merge 가 노드 grant(`OrgNodeAdminUseCase.java:201-203`)로 직접·그룹 행을, PATCH 로 노드 행을 덮는다 | `groupThenDirect_noSilentConversion`(그룹 행 바이트 불변 단언) · `directOverNodeRow_coexists` | 🔵 **추론**(Spring Data `isNew` = 부분 id 가 하나라도 non-null 이면 false) — CI 판정 대기. 수선은 추론이 틀려도 해가 없다(출처가 PK 에 있으면 merge 대상이 같은 출처 행뿐) |
| (c) | 비-내보내기 그룹 경로가 «소속 없는 역할» 을 남기는가(784 D-1) | `GroupFanOutService.java:102 · :110 · :121`(그룹발 소속 무조건 삭제). G 의 TENANT_ASSIGNMENT(U) 로 소속된 사람이 그 소속으로 U 직접 역할을 받은 뒤 G 회수 · 탈퇴 · 삭제 → U 역할 행만 남음 | `ac3_tenantAssignmentRevoke` · `ac3_memberRemoval` · `ac3_groupDeletion` | 코드 경로 — CI 판정 대기 |

추론이 틀린 것으로 드러난 것: 784 가 «PATCH 의 노드 · 그룹 행 변환(F6)» 을 고쳤다고 적었으나(`admin-api.md` § PATCH …/roles «바이트 불변 · F6 수선»), 그 수선은 **다른 역할** 에만 맞았다 — 같은 역할이면 (b) 의 merge 가 남아 있었다(추론, 위와 같이 CI 판정 대기). 스펙 문장을 고쳤다.

## 설계 — 출처를 PK 에 (TASK-BE-631 모양 재사용)

`admin_operator_roles` PK `(operator_id, role_id, tenant_id)` → `(…, source_key)`, `source_key ∈ {DIRECT, NODE, GROUP:<id>}`.

- 기각 ① **별도 출처 표**(`admin_operator_role_source`): 평가 · 목록 · 가드 · 내보내기 등 읽는 쪽이 한 표만 보는 구조(ADR-046 «평가는 flat row 만»)를 깨고 두 표의 동기화 불변식을 새로 만든다.
- 기각 ② **G1 회수 시 남은 그룹으로 소유 이전**(행 하나 유지): 직접 + 그룹 겹침과 PATCH merge(b)를 못 고치고, 회수마다 «누가 또 주나» 를 계산해야 한다.
- 기각 ③ **생성 칸**(`source_key AS (COALESCE(group_origin…)) STORED`): MySQL 은 ON DELETE CASCADE FK 칸을 바탕으로 한 stored 생성 칸을 받지 않는다.
- `NODE` 를 별도 출처로 둔 이유: 노드 행을 `DIRECT` 로 두면 PATCH 가 노드 행을 지우거나(merge 로) 덮는다. `NODE` 는 `(operator, role, tenant)` 당 하나라 «다른 노드에 다시 주면 옮겨진다» 는 기존 의미가 그대로다.
- API 응답 칸 변경 없음. `fannedOutGrants` / `fannedOutRows` 는 «멤버에게 새로 생긴 역할» 만 세게 해 값의 의미를 유지했다(다른 출처로 이미 있던 역할의 그룹 행은 만들되 세지 않음).

## 마이그레이션 `V0053__admin_operator_roles_source_key.sql`

① `source_key VARCHAR(48) NOT NULL DEFAULT 'DIRECT'`(가드) — 출처를 안 적는 시드 · 픽스처는 계속 직접 grant. ② PK 바꾸기 **전** 기존 행 출처 도출(노드 → `NODE`, 그룹 → `GROUP:<group_origin>`, 그 밖 `DIRECT` — 다른 칸 불변). ③ PK 한 문장 교체(가드, `operator_id` 선행). ④ CHECK 둘(`ck_admin_operator_roles_source` · `ck_admin_operator_roles_node_source`; `group_origin` 쪽은 ER 3823 이라 애플리케이션 + 형태 시험). ⑤ **재-fan-out**: 모든 ROLE 템플릿 × 현재 멤버 → `GROUP:<id>` 행이 없으면 INSERT(NOT EXISTS 멱등 · 멤버가 그 그룹 테넌트 소속일 때만 · `'*'` 제외). 행 삭제 0 · DIRECT 행 불변 · 재실행 0행. 🔵 ⑤ 는 v1 에서 잃어버린 역할을 **되돌려 줄 수 있다**(감사 행 없음) — 배포 전 그 목록을 세는 읽기 전용 SQL 을 `data-model.md` § Migration Strategy V0053 에 적었다.

## 읽는 쪽 대조 (중복 행 · 토큰)

- 바꿈: `JpaAdminOperatorAdapter.bulkLoadRoleNamesByOperator`(`'*'` 운영자 목록 — 같은 역할을 이름 하나로; 784 의 «두 회사 같은 역할» 중복도 함께 사라짐) · `deleteDirectByOperatorIdAndTenantId`(`source_key = 'DIRECT'`).
- 확인(변경 불요 — 합집합 · Set · 이름 중복 제거): `PermissionEvaluatorImpl` · `AdminGrantScopeEvaluator`(scope · anyRoleScope · permissionsHeldIn · grantedOrgNodeIds · isPlatformScope) · `findRolesForOperator`(`findAllById` IN) · `anyRoleRequires2fa` · `bulkLoadRoleNamesByOperatorInTenant` · `findRoleNamesByTenant`(/me · memberships) · `PatchOperatorRoleUseCase` 응답 · `OperatorOwnedTenantLimit`(Set) · `OperatorMembershipAddition.isPlatformRow` · `OrgNodeAdminUseCase.listNodeAdmins`(노드 행만) · `OperatorCompanyExitCascade`(테넌트 전체 삭제 · `groupOrigin` 술어).
- 토큰: 관리 JWT 에 역할 없음(`OperatorAccessTokenIssuer`), auth-service 는 admin_db 를 읽지 않는다 — 변화 0.

## 마지막 관리자 가드 (784)

`TenantLastAdminGuard` 는 «지워지지 않는 행이 하나라도 남는 보유자» 를 센다 — 출처별 행이 되자 코드 변경 없이 «다른 출처가 남으면 한 출처 회수 허용» 이 됐다(멤버 제거 · 그룹 회수 · 그룹 삭제의 술어는 `group_origin = G` 행만, PATCH 는 직접 행만 지움). IT: `lastAdmin_otherSourceRemains` · `lastAdmin_directRemovedWhileGroupRemains`.

## AC-3 수선

그룹 경로 셋(TENANT_ASSIGNMENT 회수 · 멤버 제거 · 그룹 삭제)이 그룹발 소속을 지울 때, 그 사람이 그 테넌트에 역할 행을 가지면 **직접 소속으로 바꿔 남긴다**(«이 회사에서 내보내기» 의 소유자 결정 2026-10-13 UTC 와 같은 규칙) — 감사 `detail` 에 `keptAsDirect=<N>`.

## 시험

- 로컬: `:admin-service:test` rc=0 — 1306 tests · 0 failures · 0 errors · 58 skipped(Docker 조건부). `compileTestJava` rc=0. 가드: `check-flyway-version-collision.sh` · `check-flyway-unresolvable-placeholder.sh` · `check-dev-seed-migration-band.sh` rc=0(새 마이그레이션 `git add -N` 뒤). 오류 코드 추가 없음.
- 새 단위/형태: `V0053MigrationShapeTest`(9) · `GroupFanOutServiceTest`(+3).
- 새 IT(CI): `AdminRoleSourceIntegrationTest`(10) · `V0053RoleSourceBackfillIntegrationTest`(2). 고친 IT: `GroupAdminIntegrationTest.idempotentSkip_directGrantSurvives`(이제 행 둘) · 노드 행 · 그룹 행을 손으로 넣는 픽스처 셋에 `source_key`(`OrgNodeAdmin` · `TenantOrgNodePlacement` · `OperatorMembershipWriteAxis`).

## Owner decisions (2026-10-14 UTC, main session question → owner answer)

1. **V0053 re-fan-out restores roles lost under v1 without an audit row — allowed (repair).** In the demo window, run the read-only query in data-model.md § Migration Strategy V0053 right after boot and record the restored rows (see the MONO-672 run order).
2. **AC-3 — when a group path drops a group-given membership and the person still holds role rows in that tenant, the membership is kept as a direct membership.** This extends the 2026-10-13 company-exit decision; the audit records `keptAsDirect=N`.
3. **Org-node grants are their own source, `NODE` — node and direct rows coexist** (no silent overwrite).

## AC-0 red measurement (CI, 2026-10-14 UTC — #4353 first commit = IT only + old code)

Integration lane A, `AdminRoleSourceIntegrationTest`: **10 out of 10 FAILED**. Where each one stopped:

- **At the defect assertion (meaningful red) — 9**:
  - AC-3, an orphaned role row outside any membership, «expected 0 but was 1» × 3: TENANT_ASSIGNMENT revoke, member leave, group deletion.
  - 🔴 **AC-0(b)** «the group row is byte-unchanged — not merged into a direct row» → **the inference was confirmed by measurement.** A direct PATCH quietly turns a group row into a direct row.
  - AC-0(a) «one row per group».
  - Last-admin guard: `409` where it should have been allowed × 2.
  - Missing role (`$.roles[0]` absent).
  - Edge 1, the reading after a two-source survival.
- **SQL error (not red) — 1**: the AC-2 sibling (direct PATCH over an org-node row). Its fixture inserts `source_key` directly, so it stops at `BadSqlGrammarException` on the old schema. This one is judged by the GREEN after the fix.
