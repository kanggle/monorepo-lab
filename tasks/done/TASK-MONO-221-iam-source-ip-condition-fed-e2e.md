# Task ID

TASK-MONO-221

# Title

ADR-MONO-026 § D7 step 3 — iam admin `SOURCE_IP` access-condition federation-e2e proof. Adds a federation-hardening-e2e spec proving the 4th authorization gate (the `SOURCE_IP` access condition built in-process by step 1 `platform/access-conditions.md` + the shared `SourceIpCondition` evaluator, and enforced on admin-service by step 2 / TASK-BE-351) bites end-to-end on the live stack: with the shared admin-service configured with an allowlist (`ADMIN_ACCESS_SOURCE_IP_CIDRS`), an RBAC-granted admin mutation from an out-of-range source IP is gated (403 `ACCESS_CONDITION_UNMET`) while the same mutation from an in-range source IP proceeds (2xx), and reads are never gated even from a blocked IP (mutation-only). Driven by `SUPER_ADMIN` through the real `console_operator_token` → `/api/admin/**` surface, with the source IP set deterministically per-request via `X-Forwarded-For`.

# Status

done

> **완료 (2026-06-11)**: impl PR #1305 (squash `3fdf81f0`) — spec + compose allowlist + V9004 + seed.sql §16 + README. **federation-hardening-e2e workflow_dispatch GREEN: run 27334730514 (17 passed / 0 failed) on fixed SHA `4e6ace363`** — 신규 3 테스트(gated/unaffected/mutation-only) clean pass + 기존 14 테스트 전부 GREEN(suite-level net-zero 실증). 3차원 ✓ (PR MERGED / origin/main tip=`3fdf81f0` / 머지 전 PR 표준체크 20 pass·0 fail) + AC-5 (federation GREEN) 충족. **ADR-MONO-026 § D7 step 3 종결 = access-conditions 이니셔티브(축 ② 2단계) 전체 완료** (step0 MONO-216/217 / step1 MONO-218 / step2 BE-351 / step3 MONO-221).
>
> **증명**: 공유 admin-service에 allowlist ON(`ADMIN_ACCESS_SOURCE_IP_CIDRS`=사설+loopback) → SUPER_ADMIN이 `console_operator_token`으로 `/api/admin/**` 직호출, source IP를 `X-Forwarded-For`로 per-request 제어. out-of-range(203.0.113.7)→403 `ACCESS_CONDITION_UNMET`(미실행) / in-range(10.20.30.40)→201·204 / blocked-IP GET→200(mutation-only). 게이트는 RBAC와 직교(granted 후 실행)라 SUPER_ADMIN도 게이트됨 → 진짜 조건거부(`ACCESS_CONDITION_UNMET`, RBAC `PERMISSION_DENIED` 아님).
>
> **진단 메타 (재사용)**: ① 1차 run(27333924441) 15 pass/1 fail — 실패는 read-back 파싱뿐: `OperatorAssignmentListResponse` JSON 키는 `assignments`(BE-339)인데 스펙이 `items`로 가정 → `body.items` 항상 undefined→0 → "count==0" 단언은 vacuous 통과·"count==1"만 실패. 게이팅(403/201) 자체는 정상·net-zero(다른 15 GREEN)도 정상이었음. **교훈: e2e read-back은 producer DTO의 정확한 JSON 필드명을 코드로 확인(추정 금지)**. ② net-zero 보존 핵심=SOURCE_IP는 서비스-레벨 config라 공유 admin-service에 켜면 전 스위트 영향 → allowlist에 RFC1918+loopback 전부 포함시켜 기존 스펙(XFF 없이 remoteAddr fallback=docker 사설IP)을 in-range로 유지. ③ federation-e2e는 nightly/dispatch(PR 미게이트)라 `gh workflow run … --ref <branch>`로 머지 전 권위검증 필수. 분석=Opus 4.8 / 구현=Opus 4.8.

# Owner

backend

# Task Tags

- e2e
- federation-hardening
- adr
- security
- access-conditions

---

# Dependency Markers

- **proves**: ADR-MONO-026 D3-B (the condition is carried as domain/endpoint guard-config — `admin.access.source-ip-allowed-cidrs`, no producer / JWT change) + D4 (the iam-admin `SOURCE_IP` pilot) — the runtime capstone of the § D7 staged roadmap (step 3, the optional federation-e2e proof).
- **depends on**: TASK-MONO-218 (step 1 — `platform/access-conditions.md` contract + shared `com.example.security.access.SourceIpCondition` evaluator in `libs/java-security`), TASK-BE-351 (step 2 — admin-service `SOURCE_IP` enforcement: the 4th gate in `RequiresPermissionAspect`, `AdminAccessConditionProperties` / `AccessConditionConfig`, 403 `ACCESS_CONDITION_UNMET`, net-zero/opt-in, fail-safe).
- **builds on (harness)**: TASK-MONO-207 / TASK-MONO-210 (the dedicated-tenant + admin-surface federation-e2e pattern — `ip-pilot-corp` mirrors `umbrella-corp` / `initech-corp`).

# Goal

Make the ADR-026 `SOURCE_IP` access condition executable on the full federation stack: prove, through the real `console_operator_token` → admin RBAC mutation path, that an already-authorised admin mutation is denied when the request's source IP is outside the configured allowlist (403 `ACCESS_CONDITION_UNMET`) and proceeds when it is inside — the condition gates an action the operator otherwise holds (restriction-only), never grants one — while reads are never gated (mutation-only) and the rest of the federation suite (whose admin mutations present a private docker-network source) is unaffected (net-zero for legitimate, in-range operators).

# Scope

**Account-side seed (a dedicated, isolated tenant the proof's mutation targets):**
- NEW `projects/iam-platform/apps/account-service/.../db/migration-dev/V9004__seed_ippilot_e2e_customer.sql` — a DEDICATED tenant `ip-pilot-corp` (present at account-service startup, like globex V9001 / initech V9002 / umbrella V9003). No subscription is needed — the proof only assigns/unassigns an operator to this tenant; isolation keeps the assign/unassign mutations from racing the fullyParallel acme/globex/initech/umbrella specs.

**Admin-side seed (the throwaway target the proof mutates):**
- `tests/federation-hardening-e2e/fixtures/seed.sql` § 16 — one operator `ip-pilot-target` (role SUPPORT_READONLY @ ip-pilot-corp), the object of the assign/unassign mutation. It has NO auth_db credential — it never logs in (it is only the path `{operatorId}`, never an authenticated caller). Referenced by no other spec → mutating it is parallel-safe.

**Stack config (turn the condition ON for the shared admin-service):**
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.yml` — set `ADMIN_ACCESS_SOURCE_IP_CIDRS` on `admin-service` to a broad private + loopback allowlist (`10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,127.0.0.0/8,::1/128`). This is the pilot's guard-config (D3-B). The allowlist deliberately covers every RFC1918 + loopback range so that the EXISTING suite's admin mutations — which call `ADMIN_BASE` directly with NO `X-Forwarded-For`, hence resolve to the docker-network remote address — stay in-range and are unaffected (the suite-level net-zero). The proof itself overrides the perceived source IP per-request via `X-Forwarded-For`.

**Harness + proof:**
- NEW `tests/federation-hardening-e2e/specs/iam-admin-source-ip-condition.spec.ts` — the proof (below). Uses the persisted `SUPER_ADMIN` storageState (no new login helper needed); the gate is service-level (orthogonal to RBAC), so it bites even `SUPER_ADMIN`.
- `tests/federation-hardening-e2e/README.md` — post-MVP spec note.

**Spec design (serial; all via SUPER_ADMIN storageState, source IP per-request via `X-Forwarded-For`):**
- **gated (out-of-range)**: assign `ip-pilot-target` → `ip-pilot-corp` with `X-Forwarded-For: 203.0.113.7` (TEST-NET-3, outside every allowlisted range) → 403 `ACCESS_CONDITION_UNMET`; a follow-up read confirms the assignment row was NOT created (the mutation did not execute).
- **unaffected (in-range)**: the SAME assign with `X-Forwarded-For: 10.20.30.40` (∈ 10.0.0.0/8) → 201; unassign (in-range) → 204. Differs from the gated case ONLY by source IP.
- **mutation-only (reads never gated)**: GET the operator's assignments with `X-Forwarded-For: 203.0.113.7` (the blocked IP) → 200 — a read is never gated, even from outside the allowlist.

# Acceptance Criteria

- **AC-1 (gated — out-of-range mutation)** The assign mutation with an out-of-range `X-Forwarded-For` → 403 with body `code = ACCESS_CONDITION_UNMET`, and a subsequent read shows the assignment was not created (the RBAC-granted mutation was stopped by the condition gate, not executed).
- **AC-2 (unaffected — in-range mutation)** The SAME assign with an in-range `X-Forwarded-For` → 201, and the matching unassign → 204 — proving the gate does not disturb a legitimate, in-range operator (the discriminant is the source IP alone).
- **AC-3 (mutation-only)** A GET read of the target's assignments with the out-of-range (blocked) `X-Forwarded-For` → 200 — reads are never gated (restriction is mutation-only).
- **AC-4 (suite-level net-zero)** Every pre-existing federation spec (whose admin mutations carry no `X-Forwarded-For` and resolve to a private docker-network source) stays GREEN — enabling the allowlist did not perturb the existing in-range operators.
- **AC-5** GREEN on the federation-hardening-e2e workflow (nightly / `gh workflow run federation-hardening-e2e.yml`), all specs (the new spec parallel-safe with the existing cohort).

# Related Specs

- `docs/adr/ADR-MONO-026-role-grant-access-conditions.md` § D7 step 3 (the optional federation-e2e proof) + § D4 (the iam-admin `SOURCE_IP` pilot) + § D3-B (guard-config carrier)
- `docs/adr/ADR-MONO-018-platform-console-phase-8-federation-hardening.md` (harness location/scope)
- `platform/access-conditions.md` (the shared access-condition contract + the three invariants: restriction-only / fail-safe / net-zero)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/admin-api.md` (the assign/unassign + list-assignments surfaces the spec drives)

# Edge Cases

- `ip-pilot-corp` + `ip-pilot-target` are referenced by NO other spec → the assign/unassign mutations are parallel-safe; `finally` restores state (assignment removed) for re-runnability + the CI `retries: 2`.
- The condition reads the **raw** `X-Forwarded-For` first hop (`RequiresPermissionAspect.resolveSourceIp`), not Tomcat's processed `remoteAddr` — so the proof controls the perceived source IP deterministically per-request, independent of docker networking. The existing specs, which send no `X-Forwarded-For`, fall through to `remoteAddr` (a private docker-network address) → in-range → unaffected.
- The allowlist MUST cover every private + loopback range (`10/8`, `172.16/12`, `192.168/16`, `127/8`, `::1`) so that the existing suite's `remoteAddr`-resolved mutations stay in-range regardless of the exact docker subnet / userland-proxy vs iptables publishing / IPv4-mapped-IPv6 form (Java collapses `::ffff:a.b.c.d` to a 4-byte `Inet4Address`, so a v4-mapped remote addr still matches the v4 CIDRs). A genuine host→`localhost:18085` call never presents a public source, so the existing suite can never be gated.
- The condition is **mutation-only** (POST/PUT/PATCH/DELETE): the GET read in AC-3 is never gated, even from the blocked IP — this is the restriction-only invariant on the read side.
- The condition is **orthogonal to RBAC** (it runs only AFTER RBAC granted): it gates even `SUPER_ADMIN` ('*'), which is what makes the out-of-range 403 a true access-condition denial (`ACCESS_CONDITION_UNMET`), not an RBAC one (`PERMISSION_DENIED`).
- Write-heavy admin e2e on a cold stack can hit the admin_db `outbox` poller's range `PESSIMISTIC_WRITE` lock (no SKIP LOCKED) during Kafka warm-up → transient 500 (the MONO-207/210 lesson). The spec front-loads an outbox warm-up gate (in-range assign/unassign until 2xx) in `beforeAll` and retries transient 5xx in `send()`; the proof's 403 assertions are NOT retried (only 5xx is).

# Failure Scenarios

- If the gate ran BEFORE RBAC (or replaced it), an out-of-range request would deny with the wrong code / the wrong precedence — AC-1 asserts the denial is specifically `ACCESS_CONDITION_UNMET` (the condition gate), proving it runs as the 4th gate after RBAC granted.
- If the gate were NOT mutation-only, the AC-3 read from the blocked IP would 403 — guarding the restriction-only/read-exempt invariant on the live stack.
- If the allowlist were too narrow (missed the docker-network `remoteAddr` range), the entire existing suite would go RED (a net-zero regression) — AC-4 / AC-5 (the whole workflow GREEN) guard it; the broad private+loopback allowlist prevents it.
- If the condition fell open on an unresolvable / blank source (instead of fail-safe deny), the out-of-range case could leak through — the shared evaluator's fail-safe semantics + AC-1 guard it.
- If a future production account-service migration reuses a V9000+ number, the dev band would collide — the band is far above the production timeline by design (mirrors the MONO-207/210 V9001/V9002/V9003 lesson).
