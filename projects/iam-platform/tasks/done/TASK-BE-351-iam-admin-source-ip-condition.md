# Task ID

TASK-BE-351

# Title

ADR-MONO-026 § D7 step 2 (axis ② 2단계 pilot) — iam admin-service `SOURCE_IP` access-condition enforcement. Wire the shared `com.example.security.access.SourceIpCondition` (TASK-MONO-218) into the admin-service `RequiresPermissionAspect` as the **4th authorization gate** (after RBAC): an admin **mutation** that passed `@RequiresPermission` is additionally denied `403 ACCESS_CONDITION_UNMET` when the request source IP is outside the domain-configured allowlist (`admin.access.source-ip-allowed-cidrs`, D3-B guard-config — no producer/JWT change). Restriction-only, fail-safe, net-zero/opt-in (empty allowlist ⇒ no gate; reads never gated). Slice tests for met/unmet/fallback/precedence.

# Status

done

> **완료 (2026-06-11, 3-dim verified — impl PR #1290 squash `37fed322d`)**: ADR-MONO-026 § D7 step 2 (axis ② 2단계 pilot). iam admin-service `SOURCE_IP` access condition = **4번째 authz 게이트**(RBAC 통과 후). `AdminAccessConditionProperties`(`admin.access.source-ip-allowed-cidrs`, 빈 기본=net-zero) + `AccessConditionConfig`(`SourceIpCondition` bean, D3-B 가드설정, producer/JWT 무변경) + `RequiresPermissionAspect` 4번째 게이트(변이만, `X-Forwarded-For` 첫홉→remote-addr, configured&&!satisfied→DENIED audit+`AccessConditionUnmetException`) + `AdminExceptionHandler`→403 `ACCESS_CONDITION_UNMET` + rbac.md 서브섹션 + application.yml 문서화. `ObjectProvider` 주입=빈 부재 시 net-zero(기존 슬라이스 무영향). 슬라이스 5: met/unmet/remote-addr fallback 양방향/RBAC-denial 우선. **3-dim**: (a) MERGED+`37fed322d`; (b) origin/main tip 일치; (c) pre-merge **Build & Test + Integration(iam) + 전 체크 GREEN, 0 fail**. ⚠️재사용: `RequiresPermissionAspect.checkAnnotated` 가 RBAC/조건 단일 결정지점·`ObjectProvider`로 optional evaluator 주입=net-zero 안전·`@WebMvcTest` 슬라이스 set remoteAddr=`.with(req->{req.setRemoteAddr(...);return req;})`. **ADR-026 전 4단계 종결(216 PROPOSED→217 ACCEPTED→218 계약+evaluator→351 iam enforcement)**. 분석=Opus 4.8 / 구현=Opus. [[feedback_spring_boot_diagnostic_patterns]]

# Owner

backend

# Task Tags

- adr
- conditional-policy
- iam
- authorization
- security

---

# Dependency Markers

- **depends on**: TASK-MONO-218 (access-conditions contract + shared `SourceIpCondition` evaluator — PR #1288 squash `6379561d3`) and the ACCEPTED ADR-MONO-026 (PR #1286). Bases on that merged main.
- **realises**: ADR-MONO-026 § D4 (the iam admin + `SOURCE_IP` pilot) + § D7 step 2; `platform/access-conditions.md` § 4 names iam admin-service as the first adopter.
- **composes with**: ADR-MONO-024 D2 tenant-confinement + ADR-019/020 RBAC (the access condition is an orthogonal 4th gate, never a substitute).

# Goal

Demonstrate the 2단계 conditional-policy pattern end-to-end in iam: a corp-CIDR `SOURCE_IP` gate on the admin mutation surface, enforced consumer-side from the shared evaluator, net-zero by default. Proves an access condition gates an already-RBAC-authorised action without any producer/token change.

# Scope

- NEW `infrastructure/config/AdminAccessConditionProperties.java` — `@ConfigurationProperties("admin.access")` with `sourceIpAllowedCidrs` (default empty ⇒ net-zero).
- NEW `infrastructure/config/AccessConditionConfig.java` — `@EnableConfigurationProperties` + `SourceIpCondition` bean from the properties.
- NEW `application/exception/AccessConditionUnmetException.java` + handler in `AdminExceptionHandler` → `403 ACCESS_CONDITION_UNMET`.
- EDIT `presentation/aspect/RequiresPermissionAspect.java` — inject `ObjectProvider<SourceIpCondition>`; after the permission grant, on **mutation** methods, deny when `isConfigured() && !isSatisfiedBy(sourceIp)`; `X-Forwarded-For` first-hop → remote-addr fallback; records a DENIED audit row.
- EDIT `application.yml` — documented `admin.access.source-ip-allowed-cidrs` (empty default = net-zero).
- EDIT `specs/services/admin-service/rbac.md` — "Source-IP Access Condition (ADR-MONO-026)" subsection (the 4th gate).
- NEW slice test `presentation/aspect/AdminSourceIpConditionEnforcementTest.java`.
- This task file + `projects/iam-platform/tasks/INDEX.md` ready entry.

**Out of scope**: the `TIME_WINDOW` / `RESOURCE_TAG` condition types (reserved); the signed-claim carrier (D3-A); gating GET reads; any producer/token-customizer change.

# Acceptance Criteria

- **AC-1** With a configured allowlist (`10.0.0.0/8`), an RBAC-granted admin mutation with an in-range source IP (via `X-Forwarded-For` or remote-addr) proceeds (200); an out-of-range one is denied `403 ACCESS_CONDITION_UNMET` and the downstream command is NOT executed.
- **AC-2** Net-zero: with no configured allowlist (default empty, or no `SourceIpCondition` bean) every existing path is byte-identical — no gate. Existing aspect tests pass unchanged.
- **AC-3** Fail-safe: a configured gate with an unresolvable/blank source IP denies.
- **AC-4** Reads (GET) are never gated; only POST/PUT/PATCH/DELETE under `@RequiresPermission`.
- **AC-5** Ordering: an RBAC denial still returns `PERMISSION_DENIED` (the condition is evaluated only after the permission grant).
- **AC-6** The producer / token-customizer is untouched (D3-B consumer-side); no JWT claim added.
- **AC-7** `:projects:iam-platform:apps:admin-service:test` GREEN (new slice test + no regression).

# Related Specs

- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (§ D3-B carrier, § D4 iam/SOURCE_IP pilot, § D7 step 2)
- `platform/access-conditions.md` (the contract; § 4 iam adopter recipe)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (the authorization decision site; updated here)

# Related Contracts

- `platform/access-conditions.md` (consumed; no change)

# Edge Cases

- Empty/blank allowlist (`ADMIN_ACCESS_SOURCE_IP_CIDRS` unset) binds to a net-zero condition — the gate must not bite (existing behaviour preserved).
- `X-Forwarded-For` may carry a CSV chain — only the first hop (the real client) is used.
- No request bound (async/edge) → source IP null → fail-safe deny (only when configured).
- The `SourceIpCondition` bean is absent in `@WebMvcTest` slices that do not import `AccessConditionConfig` — `ObjectProvider.getIfAvailable()` returns null → net-zero, so existing slice tests are unaffected.

# Failure Scenarios

- If the gate ran before the permission check, a low-privilege caller from a valid IP could probe the mutation surface — the condition is evaluated strictly AFTER the grant (AC-5).
- If reads were gated, the blast radius would exceed the pilot — mutation-only (AC-4).
- If an unconfigured gate denied, every admin mutation would regress — `isConfigured()` net-zero short-circuit (AC-2).
- If the source IP were taken from remote-addr only (ignoring `X-Forwarded-For`), every request behind the gateway would appear to originate from the gateway — first-hop `X-Forwarded-For` is used.
