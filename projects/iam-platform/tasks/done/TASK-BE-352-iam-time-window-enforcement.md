# Task ID

TASK-BE-352

# Title

ADR-MONO-028 step 3 — iam admin `TIME_WINDOW` enforcement composed AND-only with `SOURCE_IP`. Generalise `RequiresPermissionAspect`'s 4th authorization gate to evaluate a **set** of access conditions (`SOURCE_IP` + `TIME_WINDOW`) AND-only: an RBAC-granted admin mutation proceeds only when every configured condition is satisfied; any unsatisfied configured condition → 403 `ACCESS_CONDITION_UNMET`. Wire the `TimeWindowCondition` from domain guard-config (`admin.access.time-window.*`, net-zero default). Mutation-only, fail-safe, net-zero, producer-untouched (D3-B). The first multi-condition composition (ADR-026 § D1 blessed it; the `SOURCE_IP` pilot / BE-351 — a single condition — did not exercise it).

# Status

done

> **완료 (2026-06-11)**: enforcement PR #1316 (squash `2011f900`). 3차원 ✓ (MERGED / origin/main tip=`2011f900` 일치 / 머지 전 20 pass·0 fail — Build & Test + Integration(iam) + iam e2e smoke GREEN). `RequiresPermissionAspect` 4번째 게이트를 **조건 set AND-only**(`anyConditionUnmet`)로 일반화 — SOURCE_IP AND TIME_WINDOW, 어느 하나 unsatisfied→403 `ACCESS_CONDITION_UNMET`(변이만·RBAC 후·fail-safe). `TimeWindowCondition` 빈(AccessConditionConfig)+nested `TimeWindow` config(AdminAccessConditionProperties)+`ADMIN_ACCESS_TIME_WINDOW_*` env, **request time=`ObjectProvider<Clock>.getIfUnique(systemUTC)`**(prod 시스템시계·슬라이스 fixed @MockitoBean). 슬라이스 2종: TIME_WINDOW(in/out/ordering) + **합성**(in-CIDR+in-window 200 / in-CIDR+out-window 403 / out-CIDR 403=AND 단락). net-zero·producer 무변경(D3-B). **첫 다중조건 합성 실증**(BE-351 단일조건이 미검증). ⚠️재사용: @WebMvcTest 슬라이스의 @MockitoBean은 MockitoExtension 밖이라 lenient(deny 테스트의 미사용 useCase/clock stub 안전)·AND 단락 시 clock 미stub. 다음=optional fed-e2e 합성 증명. 분석/구현=Opus 4.8.

# Owner

backend

# Task Tags

- access-conditions
- conditional-policy
- iam
- security
- rbac

---

# Dependency Markers

- **executes**: ADR-MONO-028 § 3.3 step 3 (the iam pilot's `TIME_WINDOW` enforcement composed with `SOURCE_IP`) on the ACCEPTED base (TASK-MONO-223).
- **depends on**: TASK-MONO-224 (the shared `TimeWindowCondition` evaluator in `libs/java-security`, #1313) + TASK-BE-351 (the `SOURCE_IP` enforcement + the `RequiresPermissionAspect` 4th-gate seam this generalises).
- **mirrors**: TASK-BE-351 (`SOURCE_IP` enforcement) — same seam, 2nd condition type, now composed.
- **blocks**: the optional federation-e2e composition proof (TASK-MONO-2xx).

# Goal

Make the `TIME_WINDOW` access condition enforceable on the iam admin mutation surface, composed AND-only with the existing `SOURCE_IP` condition at the single authorization decision site (`RequiresPermissionAspect`), so an admin mutation is allowed only when it is both in-CIDR AND in-window — proving the multi-condition composition end-to-end at the unit/slice layer, while keeping every unconfigured path byte-identical (net-zero).

# Scope

- `…/infrastructure/config/AdminAccessConditionProperties.java` — add the nested `TimeWindow` guard-config (`zone` / `days` / `start` / `end`, all blank/empty default → net-zero).
- `…/infrastructure/config/AccessConditionConfig.java` — add the `TimeWindowCondition` bean built from the config (`TimeWindowCondition.fromConfig(...)`).
- `…/presentation/aspect/RequiresPermissionAspect.java` — add `ObjectProvider<TimeWindowCondition>` + `ObjectProvider<Clock>`; generalise the 4th gate (`anyConditionUnmet(request)`) to deny when ANY configured condition is unsatisfied (AND-only), evaluated only for mutations, after RBAC granted; the request time is the unique `Clock` bean if present else `Clock.systemUTC()`.
- `…/resources/application.yml` — add `admin.access.time-window.{zone,days,start,end}` with `ADMIN_ACCESS_TIME_WINDOW_*` env hooks (empty default → net-zero).
- NEW `…/test/.../aspect/AdminTimeWindowConditionEnforcementTest.java` — slice: in-window → 200; out-of-window → 403 `ACCESS_CONDITION_UNMET` (mutation not executed); RBAC denial precedence.
- NEW `…/test/.../aspect/AdminAccessConditionCompositionTest.java` — slice (both conditions): in-CIDR + in-window → 200; in-CIDR + out-of-window → 403; out-of-CIDR → 403 (AND short-circuits before the time check).

**Out of scope**: any producer / token-customizer / IAM change (D3-B); the `RESOURCE_TAG` type; cross-midnight windows (ADR-028 § D3 fast-follow); the federation-e2e composition proof (separate task).

# Acceptance Criteria

- **AC-1 (TIME_WINDOW met/unmet)** With a configured window, an RBAC-granted mutation whose request time is in-window → 2xx; out-of-window → 403 `ACCESS_CONDITION_UNMET` and the mutation is not executed.
- **AC-2 (AND-only composition)** With BOTH `SOURCE_IP` and `TIME_WINDOW` configured: in-CIDR + in-window → 2xx; in-CIDR + out-of-window → 403; out-of-CIDR → 403 (either unsatisfied condition denies).
- **AC-3 (mutation-only)** GET reads are never gated by either condition (the gate runs only for POST/PUT/PATCH/DELETE).
- **AC-4 (net-zero)** Unconfigured `TIME_WINDOW` (no zone/days/start/end) ⟹ no gate; existing slice tests (no condition bean) and the `SOURCE_IP`-only behaviour are byte-identical.
- **AC-5 (ordering / fail-safe)** RBAC denial precedes the condition gate (`PERMISSION_DENIED`, not `ACCESS_CONDITION_UNMET`); an unresolvable input (bad time/zone) denies (the evaluator is fail-safe).
- **AC-6** `:projects:iam-platform:apps:admin-service:test` GREEN + the monorepo Build & Test + Integration (iam) jobs GREEN.

# Related Specs

- `docs/adr/ADR-MONO-028-time-window-access-condition.md` § D2 (the iam compose-AND pilot) + § D3 (the schema)
- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` (the framework + the `SOURCE_IP` sibling)
- `projects/iam-platform/specs/services/admin-service/rbac.md` (the 4th-gate enforcement seam)

# Related Contracts

- `platform/access-conditions.md` (the access-condition contract — § 1 `TIME_WINDOW` implemented + the AND-only composition note)

# Edge Cases

- Each evaluator stays **input-specific** (`SourceIpCondition`/String IP, `TimeWindowCondition`/`Instant`); the aspect composes them AND-only in `anyConditionUnmet` — there is no unifying interface (keeps the libs evaluators clean).
- **AND short-circuit**: `SOURCE_IP` is evaluated before `TIME_WINDOW`; an out-of-CIDR request denies without reaching the time check (so the `Clock` is not consulted) — the composition slice test must not stub the clock in that case (lenient `@MockitoBean`, outside `MockitoExtension`).
- The request **`Clock`** is resolved via `ObjectProvider<Clock>.getIfUnique(Clock::systemUTC)` — no `Clock` bean is registered in production (system clock), and there is none elsewhere in admin-service, so production stays on `systemUTC`; slice tests inject a fixed `@MockitoBean Clock`.
- **Net-zero**: a `TimeWindowCondition` bean is always present (built from empty config → `isConfigured()` false → skipped), so existing full-context behaviour is unchanged until a window is configured.
- New `ObjectProvider` fields on the aspect resolve to empty providers when no bean exists (slice tests that import the aspect without a `TimeWindowCondition`/`Clock` bean are unaffected — net-zero).

# Failure Scenarios

- If the gate evaluated conditions with OR semantics (any satisfied → allow), an out-of-window in-CIDR request would wrongly pass — AC-2 pins AND-only (any unsatisfied → deny).
- If the `TIME_WINDOW` bean were absent (not built from config), a misconfiguration could silently disable the gate; AC-4 + the always-present bean keep net-zero explicit and opt-in.
- If reads were gated, AC-3 would fail — the gate is mutation-only.
- If a production `Clock` bean conflicted, `getIfUnique` falls back to `systemUTC` rather than throwing — preventing a wiring failure from a stray `Clock` bean.
