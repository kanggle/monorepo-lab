# ADR-MONO-028 — `TIME_WINDOW` Access Condition (2nd condition type under ADR-026's closed enum)

**Status:** ACCEPTED

**Date:** 2026-06-11

**Deciders:** platform (IAM axis)

**Parent / inherits:** **ADR-MONO-026** (axis ② 2단계 — access conditions). This ADR executes ADR-026 § D7.4 ("additional condition types … each its own ADR/task") for the **`TIME_WINDOW`** member of the ADR-026 closed enum. It inherits ADR-026's entire framework **unchanged** — the closed enum, AND-only combination, restriction-only + fail-safe + net-zero invariants, and the D3-B domain/endpoint guard-config carrier — and decides only the options ADR-026 left open for a *new* condition type: the **pilot domain + composition** and the **`TIME_WINDOW` semantics + config schema**.

---

## 1. Context

ADR-026 (ACCEPTED 2026-06-11) established the access-condition framework and piloted exactly one type — **`SOURCE_IP`** on the iam admin mutation surface (`TASK-BE-351`), with a federation-e2e proof (`TASK-MONO-221`). The closed enum it fixed already *names* `TIME_WINDOW`:

> contract `platform/access-conditions.md` § 1: `TIME_WINDOW` | request time + zone | request-time within an allowed local time-of-day / day-of-week window | **reserved (added when first piloted)**.

This ADR is that first pilot. It adds nothing to the framework; it adds the **second enum member** via the framework's own blessed mechanism ("adding a type is a code change — a new evaluator class in `com.example.security.access` + tests", ADR-026 § D1).

### 1.1 What is genuinely new here (vs ADR-026)

ADR-026 proved a **single** condition gate. `TIME_WINDOW` is the first occasion to prove the **AND-only multi-condition composition** that ADR-026 § D1 blessed but never exercised: an endpoint guarded by *two* conditions where **both** must hold. That composition — not the time-of-day check itself — is the architecturally interesting increment, and it is why the iam-admin pilot (composing `TIME_WINDOW` with the existing `SOURCE_IP`) is the recommended direction.

### 1.2 The raw material (already in place)

- The **4th-gate enforcement seam** in iam admin-service (`RequiresPermissionAspect`, after RBAC) that BE-351 built for `SOURCE_IP` — currently evaluates a single condition via `ObjectProvider<SourceIpCondition>`.
- The shared evaluator pattern in `libs/java-security` (`com.example.security.access.SourceIpCondition`) — `TimeWindowCondition` is a sibling class.
- The **server clock + request time** at the enforcement seam — the input `TIME_WINDOW` needs, with no new infrastructure (mirrors how `SOURCE_IP` reads the request's forwarded address).
- The federation harness (`ip-pilot-corp` / `ip-pilot-target`, MONO-221) — reusable for the optional composition proof.

### 1.3 Why an ADR (not just a task)

ADR-026 § D7.4 routed additional condition types to "each its own ADR/task", and this increment makes two genuine choices ADR-026 left open — the **pilot domain + whether it composes with the existing `SOURCE_IP`**, and the **`TIME_WINDOW` config schema/semantics** (zone source, day/time window shape, midnight-wrap, fail-safe input). These are gate decisions of the same kind ADR-026 fixed at its ACCEPTED transition (D3 carrier, D4 pilot), so the same staged PROPOSED → ACCEPTED discipline applies (ADR-019…026 pattern). No new framework is decided — only the new type's pilot.

---

## 2. Decision

### D1 — Framework inherited from ADR-026 (NOT re-decided)

Carried verbatim, no change: **closed enum** (`TIME_WINDOW` is an existing member), **AND-only** combination, **restriction-only** (a condition only gates an already-RBAC-/tenant-/data-scope-authorised action, never grants), **fail-safe** (unresolvable input ⇒ deny), **net-zero/opt-in** (unconfigured ⇒ no gate, byte-identical), **D3-B carrier** (domain/endpoint guard-config — no producer / token / IAM change). This ADR does not reopen any of these.

### D2 — Pilot domain + composition (FIXED at ACCEPTED 2026-06-11 = D2-A)

Which domain enforces `TIME_WINDOW`, and whether it composes with an existing condition. **User-selected at the ACCEPTED gate: D2-A** — iam admin, `TIME_WINDOW` composed AND-only with the existing `SOURCE_IP` (the multi-condition composition pilot).

- **D2-A (CHOSEN, ACCEPTED 2026-06-11)** — **iam admin-service, composed AND-only with the existing `SOURCE_IP`** on the same `@RequiresPermission`-gated admin mutation surface. The 4th gate evaluates *both* conditions: a mutation proceeds only when it is **in-CIDR AND in-window**; either unmet → `403 ACCESS_CONDITION_UNMET`. *Pro:* exercises the blessed-but-unproven **AND-only multi-condition composition** (the real new architecture); smallest delta (extends BE-351's single-condition aspect to a condition list); reuses the MONO-221 federation harness for the composition proof. *Con:* requires generalising the aspect from one condition to a set (a small, contained refactor).
- **D2-B (alternative)** — **wms write endpoints, fresh single `TIME_WINDOW`** (the ADR-026 § D4 not-chosen candidate — e.g. outbound-confirm only during business hours). *Pro:* proves the evaluator generalises to a *second domain*. *Con:* does **not** exercise composition (single condition again, like BE-351); a brand-new domain enforcement seam (more surface than extending iam's existing one).
- **D2-C (rejected)** — both domains at once. Too broad for a pilot; violates the minimal-blast-radius discipline (ADR-025 § D3 / ADR-026 § D4 each picked exactly one).

### D3 — `TIME_WINDOW` semantics + config schema (FIXED at ACCEPTED 2026-06-11)

The schema below is fixed as proposed; **midnight-wrap stays deferred** to a fast-follow (same-day `start < end` only in the pilot).

- **Input**: the request time (server `Clock`/`Instant`) evaluated against a declared **IANA zone** (e.g. `Asia/Seoul`), so DST is handled by `java.time` — no manual offset math.
- **Config (D3-B domain guard-config, `@ConfigurationProperties` / `application.yml`)** — PROPOSED shape:
  ```
  <domain>.access.time-window:
    zone: Asia/Seoul          # IANA zone id
    days: [MON, TUE, WED, THU, FRI]   # allowed days-of-week
    start: "09:00"            # inclusive local start (HH:mm)
    end:   "18:00"            # exclusive local end (HH:mm)
  ```
  **Empty / unset ⟺ unconfigured ⟺ net-zero** (`isConfigured()` false), exactly as the `SOURCE_IP` empty allowlist.
- **Semantics**: satisfied ⟺ the request's local date-time (in `zone`) falls on an allowed `day` AND its local time is within `[start, end)`. Otherwise UNMET → deny.
- **Fail-safe**: a missing/unparseable zone, malformed `start`/`end`, or unresolvable request time ⇒ **UNMET (deny)**, never allow — a misconfiguration fails closed (ADR-026 § D2 / contract § 2).
- **Midnight-wrap (sub-decision)** — PROPOSED: the pilot supports a **same-day window only** (`start < end`); a cross-midnight window (e.g. `22:00`–`06:00`) is **out of scope for the pilot** and documented as a fast-follow (it is a pure evaluator extension, no framework change). To fix at ACCEPTED if a wrap window is wanted in the pilot.

### D4 — Net-zero / opt-in (inherited)

Absent `TIME_WINDOW` config ⟺ no gate ⟺ behaviour byte-identical to today. The existing federation suite + iam admin behaviour stay GREEN until a window is configured (mirrors MONO-221's net-zero discipline; the composition pilot configures it only for the dedicated harness tenant/endpoint).

### D5 — Still no full engine (inherited boundary)

No cron expression language, no runtime-registrable schedule SPI, no boolean nesting. A single same-day-per-weekday window over a fixed config schema is the entire `TIME_WINDOW` type. Richer recurrence (holidays, nth-weekday, exception dates) is explicitly **not** scoped — that would be the policy-engine slope ADR-026 § D6 closed.

### D6 — Staged execution (zero-regression), mirroring ADR-026 § D7

1. This ADR **PROPOSED → ACCEPTED** (doc-only; the gate fixes D2 pilot/composition + D3 schema/midnight-wrap).
2. **Shared evaluator** `com.example.security.access.TimeWindowCondition` (libs/java-security, sibling to `SourceIpCondition`: `fromConfig(zone, days, start, end)` / `isConfigured()` / `isSatisfiedBy(Instant)` — fail-safe, net-zero) + **contract** `platform/access-conditions.md` § 1 flip `TIME_WINDOW` reserved → implemented + § 4 adopter row + the **AND-only multi-condition** enforcement note. Unit tests. Producer untouched (D3-B).
3. **Pilot enforcement** (the chosen D2) — for D2-A: generalise `RequiresPermissionAspect`'s 4th gate to evaluate a **set** of configured conditions (`SOURCE_IP` + `TIME_WINDOW`) AND-only; config props + wiring; IT proving **met (both) / unmet-time / unmet-ip / absent (net-zero) / AND-composition**.
4. **(optional, future)** federation-e2e composition proof (reuses MONO-207/210/221 harness): in-CIDR + in-window → 2xx; in-CIDR + out-of-window → 403; out-of-CIDR + in-window → 403 (AND-only); read never gated.

---

## 3. Scope

### 3.1 Hard invariants (inherited from ADR-026 § 3.1, carried)

Restriction-only · fail-safe · net-zero · closed enum (code-not-data, AND-only). Unchanged.

### 3.2 What this ADR does NOT do

- No change to the ADR-026 framework, the `SOURCE_IP` type, or ADR-025's data-scope.
- No full schedule/cron policy language, no runtime-registrable recurrence SPI, no boolean combinators (§ D5).
- No producer / token-customizer / IAM change (inherits D3-B; the signed-claim D3-A carrier stays deferred).
- No additive / elevation / break-glass (owned by ADR-020 / ADR-024).
- No cross-midnight window in the pilot (§ D3, fast-follow).

### 3.3 Future-self (post-ACCEPTED execution roadmap — sketch, finalised at ACCEPTED)

0. **`TASK-MONO-222` (PROPOSED, DONE #1309)** — doc-only. Model = Opus (analysis).
1. **`TASK-MONO-223` (ACCEPTED transition, this)** — the gate fixed **D2 = D2-A** (iam admin composed AND-only with the existing `SOURCE_IP`) + **D3** (the schema above; midnight-wrap deferred). Doc-only. Model = Opus.
2. **`TASK-MONO-224`** — `TimeWindowCondition` evaluator (libs/java-security, sibling to `SourceIpCondition`) + `platform/access-conditions.md` § 1 `TIME_WINDOW` reserved → implemented + § 4 adopter note + the AND-only multi-condition enforcement note. Unit tests. Producer untouched (D3-B). Model = Opus (security-lib).
3. **`TASK-<iam>-BE-xxx`** — the iam pilot's `TIME_WINDOW` enforcement composed AND-only with `SOURCE_IP` inside `RequiresPermissionAspect` (generalise the 4th gate to evaluate a condition set) + config + IT (met-both / unmet-time / unmet-ip / absent net-zero / AND-composition). Model = Opus (authorization enforcement; security-critical).
4. **(optional)** federation-e2e composition proof (reuses the MONO-221 `ip-pilot` harness): in-CIDR + in-window → 2xx; in-CIDR + out-of-window → 403; out-of-CIDR + in-window → 403 (AND-only); read never gated. Model = Opus.

---

## 4. Alternatives Considered

- **A cron/recurrence expression or schedule policy language.** Rejected — that is the policy-engine slope ADR-026 § D6 closed; a fixed same-day-per-weekday window over a config schema gives the time-condition story at a fraction of the cost.
- **Per-operator signed-claim carrier (D3-A `access_conditions`).** Deferred — inherits ADR-026's D3-B-first stance (no producer touch); promotable later if per-operator time windows are wanted.
- **wms fresh single pilot (D2-B) as the chosen direction.** Presented as the alternative; not recommended because it re-proves the single-condition path BE-351 already proved and skips the AND-only composition that is the genuine new increment.
- **Bundle `TIME_WINDOW` into ADR-026 (no new ADR).** Rejected — ADR-026 § D7.4 routed additional types to their own ADR/task, and the pilot/composition + schema are genuine gate choices; a thin child ADR keeps the decision surface auditable (matches the 019…026 staged pattern).

---

## 5. Relationship to ADR-MONO-026

| | ADR-026 (parent) | **ADR-028 (this)** |
|---|---|---|
| Scope | the access-condition **framework** + the `SOURCE_IP` pilot | the **2nd enum member** `TIME_WINDOW` + its pilot |
| New architecture | the closed enum, AND-only, restriction-only/fail-safe/net-zero, D3-B carrier, the 4th gate | **none** — inherits all; first to exercise **AND-only multi-condition composition** |
| Pilot | iam admin + `SOURCE_IP` (single condition) | (D2-A) iam admin + `SOURCE_IP` **AND** `TIME_WINDOW` (composition) — or (D2-B) wms + `TIME_WINDOW` |
| Carrier | D3-B domain guard-config | D3-B (inherited) |

`TIME_WINDOW` is the last gate at the same seam: RBAC → tenant-scope → data-scope → **access condition(s)** (AND-only). All must pass; a condition only gates, never grants.

---

## 6. Status Transition History

Append-only.

| Date | Transition | Decision direction | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-06-11 | created PROPOSED | Executes ADR-026 § D7.4 for the `TIME_WINDOW` enum member; inherits ADR-026's framework unchanged (closed enum / AND-only / restriction-only / fail-safe / net-zero / D3-B carrier); decides only the open gates — **D2** pilot domain + composition (A: iam admin composed AND-only with the existing `SOURCE_IP` [chosen-PROPOSED, exercises multi-condition composition + reuses the MONO-221 harness] vs B: wms fresh single `TIME_WINDOW`) and **D3** `TIME_WINDOW` semantics + config schema (IANA zone + days-of-week + same-day `[start,end)` local window; fail-safe on bad input; net-zero on unset; cross-midnight deferred). D4-D5 inherited (net-zero; no full engine). D6 staged (evaluator+contract → pilot enforcement+IT → optional fed-e2e composition proof). | "TIME_WINDOW 2번째 조건타입" — after the access-conditions axis ② 2단계 SOURCE_IP pilot closed end-to-end (BE-351 + MONO-221 federation GREEN) and with the non-SCM ready queue drained, the user selected adding the 2nd closed-enum condition type as the next increment. ADR-first per the established ADR-019…026 staged pattern. | #1309 (TASK-MONO-222) |
| 2026-06-11 | PROPOSED → ACCEPTED | D1, D4-D6 directions **finalised unchanged** from PROPOSED #1309 squash `c0f8ac4c` (framework inherited from ADR-026); **D2 finalised = D2-A** (iam admin-service, `TIME_WINDOW` composed AND-only with the existing `SOURCE_IP` — the multi-condition composition pilot; generalises `RequiresPermissionAspect`'s 4th gate to a condition set); **D3 finalised** = the proposed schema (IANA zone + days-of-week + same-day `[start,end)` local window; fail-safe on bad input; net-zero on unset) with **midnight-wrap deferred** to a fast-follow. Authorises the § 3.3 execution roadmap (dependency-correct base = this ACCEPTED main): `TimeWindowCondition` evaluator (`libs/java-security`) + `platform/access-conditions.md` reserved→implemented flip + AND-only multi-condition note → iam pilot enforcement (compose with `SOURCE_IP`) + IT → optional federation-e2e composition proof. Same one-off Meta-policy category as the sibling ACCEPTED transitions (ADR-023/024/025/026). | "A: iam admin + SOURCE_IP와 AND 합성" (+ D3 schema as proposed, midnight-wrap deferred) — the user fixed the pilot=iam-admin + composition=AND-with-`SOURCE_IP` at the gate and authorised ACCEPTED + execution. | #<this> (TASK-MONO-223) |
