# Task ID

TASK-BE-373

# Title

Operator↔central-identity **opt-in link surface** + `admin_operators.identity_id` column (ADR-MONO-034 U6 **step 3c** / U3 / ADR-MONO-032 D5 step 3). The security crux of the unification: formalize the `oidc_subject` bridge into a first-class `identity_id` link, set/cleared **only** by an explicit, authorized, audited, reversible admin operation — **email-match necessary-but-NOT-sufficient** (no silent/auto merge), **fail-CLOSED** on downstream errors.

# Status

done

> **완료 (2026-06-14)**: PR #1555 squash `889d12524` — operator↔central-identity opt-in 링크 surface + `admin_operators.identity_id` (ADR-MONO-034 U6 step 3c / U3). admin-service: V0036(identity_id nullable+index, no backfill) + AdminOperatorJpaEntity link/unlink mutators(managed-entity saveAndFlush) + AccountServiceClient.resolveIdentity(fail-closed) + Link/UnlinkOperatorIdentityUseCase(email-match necessary-not-sufficient·fail-closed·idempotent·tenant-scope operator.manage·audit) + OperatorAdminController `:link`/`:unlink` + ActionCode/permission registry + AdminExceptionHandler(422/422/409) + admin-api.md 계약. **CI 2회 실패→수정**: ①V0036 `COMMENT`가 `AFTER` 뒤=SQLSyntaxError(Flyway 붕괴→admin @DataJpaTest 전건 실패) → COMMENT를 AFTER 앞으로. ②OperatorAdminControllerTest WebMvc 슬라이스에 신규 use case `@MockitoBean` 누락=NoSuchBeanDefinition → 추가. **교훈**: 로컬 Mockio/타깃 테스트는 마이그레이션 SQL·WebMvc bean-wiring 못 잡음; CI `:check`(Linux Docker로 @DataJpaTest Flyway + 전체 Docker-free unit)가 권위. 3-dim verified(MERGED·`889d12524`·**Integration iam Testcontainers 3m4s**=V0036+매핑validate+link/unlink영속+changeStatus-no-clobber). 보안 로직 오케스트레이터 직접 리뷰. 다음=step 3d 통합 프로비저닝. 분석=Opus 4.8 / 구현=Opus(서브에이전트)+오케스트레이터 검증.

# Owner

backend-engineer

# Task Tags

- backend
- iam
- admin-service
- security

---

# Dependency Markers

- **child of**: ADR-MONO-034 (ACCEPTED). Third execution step (U6 step 3c) — implements the U3 opt-in link.
- **depends on**: TASK-BE-371 (3a — identities registry) + TASK-BE-372 (3b — account-service identity resolve EP, which this step CALLS). Both in `main`.
- **followed by**: step 3d (unified provisioning — new operator → resolve/create identity + set `identity_id`, U4).
- **enforces** (U3 / U7): no forced/silent merge — email-match is a NECESSARY precondition the explicit request gates on; matching email alone never links (closes the § 1.3 cross-tenant email-collision escalation vector). Fail-CLOSED at link time (authorization decision; opposite of issuance fail-soft).
- **keeps disjoint** (U5): the link sets only `identity_id` (identity correlation). `admin_operator_roles` (admin-console RBAC) and `account_roles` (domain roles) are untouched — linking identity does NOT merge role spaces.
- **does NOT amend** `jwt-standard-claims.md`; token issuance unchanged. Operator login/`password_hash` untouched (U2 — login consolidation is step 4).

# Goal

Connect the disjoint operator store to the central identity registry via an explicit, safe, reversible link — so one person's operator and consumer identities can be recognized as the same, without any forced or silent merge.

# Scope

- `apps/admin-service/.../db/migration/V0036__add_identity_id_to_admin_operators.sql` — `ALTER TABLE admin_operators ADD COLUMN identity_id VARCHAR(36) NULL AFTER oidc_subject` + `idx_admin_operators_identity_id`. **No backfill** (cross-DB impossible + U3 opt-in; the `oidc_subject`→identity backfill happens via the explicit link surface).
- `AdminOperatorJpaEntity` — `identity_id` mapping + `linkIdentity(id, at)` / `unlinkIdentity(at)` mutators (managed-entity load-modify-`saveAndFlush` pattern, mirrors `changeStatus`).
- `AdminOperatorPort` + `JpaAdminOperatorAdapter` — `OperatorView.identityId` + id-addressed `linkIdentity` / `unlinkIdentity`.
- `AccountServiceClient` — `resolveIdentity(tenantId, accountId)` → identityId|null (calls the 3b EP with `X-Tenant-Id`; fail-CLOSED: 4xx→NonRetryable, other→DownstreamFailure).
- `LinkOperatorIdentityUseCase` + `UnlinkOperatorIdentityUseCase` — the U3 design (load operator → tenant-scope authorize `operator.manage` → fail-closed resolve email+identity → null-identity reject → email-match necessary → idempotency/already-linked → persist + audit). Reversible unlink. Idempotent.
- exceptions: `IdentityLinkEmailMismatchException` (422), `AccountIdentityUnresolvableException` (422), `OperatorAlreadyLinkedException` (409) + `AdminExceptionHandler` mappings.
- `OperatorAdminController` — `PATCH /api/admin/operators/{operatorId}/identity:link` (body `{accountId, tenantId}`) + `:unlink`, `@RequiresPermission(OPERATOR_MANAGE)`, `X-Operator-Reason`, audited.
- `ActionCode` + `AdminActionPermissionRegistry` — `OPERATOR_IDENTITY_LINK` / `OPERATOR_IDENTITY_UNLINK` gated on `operator.manage`.
- `specs/contracts/http/admin-api.md` — both endpoints documented (contract-first).
- Tests: `LinkOperatorIdentityUseCaseTest` (7) + `UnlinkOperatorIdentityUseCaseTest` (3) Docker-free + `AdminOperatorJpaRepositoryTest` V0036 IT (column + link/unlink persist + changeStatus does not clobber identity_id).

# Acceptance Criteria

- **AC-1** Link sets `admin_operators.identity_id` to the account's central identity ONLY on the explicit request AND when operator.email == account.email (case-insensitive). Email mismatch → 422, no link, no success-audit.
- **AC-2** Fail-CLOSED: account-service unavailable/error → propagates (no link); resolved `identityId == null` → 422 (no link).
- **AC-3** Idempotent: re-link to the SAME identity → no-op success (audited); already linked to a DIFFERENT identity → 409.
- **AC-4** Unlink clears `identity_id` (reversible, U6); idempotent when already unlinked; audited.
- **AC-5** Authorized by `operator.manage` scoped to the managed operator's home tenant (ADR-024 D2); both actions audited (`OPERATOR_IDENTITY_LINK` / `_UNLINK`).
- **AC-6** U5: only `identity_id` changes — `admin_operator_roles` / `account_roles` / token issuance / `jwt-standard-claims.md` unchanged. Migration additive net-zero (nullable column, no backfill).
- **AC-7** `AdminOperatorJpaEntity` update of another field (e.g. `changeStatus`) does NOT clobber `identity_id` (managed-entity dirty-update — IT-verified).
- **AC-8** Docker-free unit GREEN locally (10/10); Testcontainers IT GREEN on CI (V0036 + mapping validate).

# Related Specs

- `docs/adr/ADR-MONO-034-account-credential-unification-model.md` (U3, U5, U6, U7)
- `projects/iam-platform/specs/contracts/http/admin-api.md` (the operator admin API this extends)
- `projects/iam-platform/specs/contracts/http/internal/account-internal-provisioning.md` (the 3b resolve EP called)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` — link/unlink EPs documented here.
- `platform/contracts/jwt-standard-claims.md` — NOT amended.

# Edge Cases

- `oidc_subject`-bearing operators (dev-seeded) are linked via this explicit surface, not a bulk backfill (U3).
- account exists but no identity (new-account window) → resolve returns null → 422 (cannot link).
- already linked to same identity → idempotent success; to different → 409 (unlink first).
- downstream (account-service) down → fail-CLOSED (link fails), unlike issuance fail-soft.

# Failure Scenarios

- Auto-link by email / bulk backfill from `oidc_subject` → re-introduces the § 1.3 silent-merge escalation vector; FORBIDDEN. Link is explicit-only.
- Fail-soft on downstream error → could link without verifying email-match; must be fail-CLOSED.
- Folding `admin_operator_roles` across the link → violates U5; only `identity_id` changes.
- Mapping `identity_id` such that an operator update nulls it → must use the managed-entity dirty-update pattern (IT guards this).
