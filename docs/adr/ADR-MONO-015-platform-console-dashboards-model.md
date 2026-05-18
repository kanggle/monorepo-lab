# ADR-MONO-015 — platform-console Dashboards Model (Composed Operator Overview, not Grafana Embed)

**Status:** PROPOSED
**Date:** 2026-05-16
**History:** PROPOSED 2026-05-16 (TASK-MONO-111 — resolves a HARDSTOP-06/09 surfaced during the ADR-MONO-013 Phase 2 slice-4 (FE-005 dashboards) investigation; decision direction **B (composed operator overview)** chosen by user-explicit answer).
**Decision driver:** ADR-MONO-013 § 3 / D7.4 parity checklist lists a `dashboards` line for the GAP `admin-web` absorption, but — unlike accounts/audit/operators/security — there is **no GAP producer endpoint** for it (the `admin-api.md` inventory has no `/api/admin/dashboard*`) and the `admin-web` reference implementation is a **Grafana iframe** (`admin-web/architecture.md:75`). The console (ADR-MONO-013 Model B) cannot realize the `dashboards` parity line without an architecture decision on what "console dashboards" *is* — embed Grafana (a cross-origin observability-auth boundary) vs compose an overview from existing read APIs vs defer. This decision is unspecifiable during implementation.
**Supersedes:** none. **Amends:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) § 3 / § D7.4 (the `dashboards` parity-checklist line — additively refined here, no ADR-013 decision change). **Reconciles:** `projects/platform-console/specs/contracts/console-integration-contract.md` § 3 (the bare `dashboards` line — bound to a § 2.4.4 surface post-ACCEPTED).
**Related:** [ADR-MONO-013](ADR-MONO-013-platform-console-foundation.md) (console foundation, Model B, parity-gated admin-web retirement), [ADR-MONO-014](ADR-MONO-014-platform-console-operator-auth-token-exchange.md) (operator token — the credential the overview reads use; same staged-ADR precedent), `projects/global-account-platform/specs/services/admin-web/architecture.md` (`dashboards/page.tsx` = Grafana iframe — the parity source), `projects/global-account-platform/specs/contracts/http/admin-api.md` (no dashboard endpoint — the gap), TASK-PC-FE-002/003/004 (#575/#576/#577 — the read surfaces the overview composes).

---

## 1. Context

### 1.1 The gap

- **ADR-MONO-013 § 3 / D7.4 parity checklist** enumerates the GAP `admin-web` operator surfaces the console must reach parity with before `admin-web` retires (D4, parity-gated): accounts / audit / **dashboards** / operators / security. Four of five are now implemented and bound to a `console-integration-contract.md` § 2.4.x surface (FE-002 § 2.4.1, FE-003 § 2.4.2, FE-004 § 2.4.3). The **`dashboards` line is bare** — no implementing task, no § 2.4.x binding.
- **No producer endpoint.** The GAP `admin-api.md` endpoint inventory (accounts / audit / auth / operators / me) contains **no** `/api/admin/dashboard*` (or metrics/stats/overview/summary). There is nothing for the console to call for a "dashboards" payload.
- **The parity source is a Grafana iframe.** `admin-web/architecture.md:75` — `dashboards/page.tsx # Grafana iframe`. `admin-web`'s "dashboards" is an embedded **observability** dashboard, not a domain-data screen.

### 1.2 Why an ADR

Per `platform/hardstop-rules.md` HARDSTOP-06 (#3, required spec missing/conflicting) + HARDSTOP-09 (#2, architecture decision not in specs): realizing the `dashboards` line forces a cross-cutting decision with an auth/deployment-boundary dimension (does the console embed Grafana? how does an operator authenticate to Grafana cross-origin from `console.local`? is the observability stack in the console's reach?), or a redefinition of what "parity" means for this line. Either is an architecture decision that cannot be made silently during FE-005 implementation — choosing implicitly would bake an undefendable decision (the exact HARDSTOP-09 failure mode). Same staged-ADR discipline as ADR-MONO-013/014: this is the decision record; `TASK-PC-FE-005` is PAUSED until it is ACCEPTED.

### 1.3 Staged PROPOSED → ACCEPTED

Same pattern as ADR-MONO-008/013/014. PROPOSED records the decision direction (Model B) + the parity-redefinition + the downstream sequence; ACCEPTED (user-explicit) authorizes the spec reconciliation + `TASK-PC-FE-005`. ACCEPTED does not itself implement — it unblocks.

---

## 2. Decision

### D1 — Model

| Option | Mechanics | Verdict |
|---|---|---|
| **B. Composed operator overview (read)** | The console's "dashboards" is a server-side **operator overview** screen composed from the **existing** GAP read endpoints already integrated (accounts count/recent via `GET /api/admin/accounts`, recent audit + security signals via `GET /api/admin/audit`, operator counts via `GET /api/admin/operators`). No new producer, no Grafana, no observability-auth boundary. Reuses the FE-003 read-only client pattern + the exchanged operator token. | **CHOSEN** (user direction) — smallest blast radius; zero new producer/auth surface; consistent with Model B (console renders by calling existing domain read APIs). Trade-off owned in D2. |
| A. Grafana iframe embed | Console embeds the same Grafana dashboard(s) as `admin-web` | Rejected (now) — introduces a console→Grafana operator-auth boundary (cross-origin, a second auth domain distinct from the operator-token boundary), an observability-stack reachability/deployment decision from `console.local`, and CSP/iframe-sandbox surface. Materially larger and a different security boundary than every other Phase-2 slice; a future dedicated observability ADR may revisit if real Grafana parity is required. |
| C. Defer dashboards entirely | ADR removes `dashboards` from the parity gate, defers to Phase 7 console-bff / a future observability ADR | Rejected (now) — leaves the admin-web-retirement gate (D4) with a permanent unscoped hole and gives operators no overview at all; B delivers operator value now without the A boundary. |

### D2 — Parity-line redefinition (the HARDSTOP-09 mitigation — explicit, not implicit)

ADR-MONO-013 § 3 / D7.4's `dashboards` parity line is **refined**: for the console it means an **operator overview composed from the already-integrated read surfaces**, *not* a reproduction of `admin-web`'s Grafana observability iframe. This is recorded here explicitly (defensible, documented) rather than decided inside FE-005:

- The console `dashboards` parity line is satisfied by the operator-overview screen (D1-B). Observability/Grafana metrics dashboards are **out of scope** of the platform-console parity gate and, if ever required, are a separate observability ADR (not an admin-web-retirement blocker).
- `admin-web` retirement (ADR-MONO-013 Phase 3, D4) is gated on the **refined** checklist (overview, not Grafana). If GAP operators still need the Grafana observability view post-retirement, that is served by the observability stack directly (operator/SRE tooling), independent of the console — explicitly noted so the retirement decision is defensible.

### D3 — Surface (composed, no new producer)

- The overview is **read-only**, server-side, tenant-scoped, authenticated with the exchanged operator token (ADR-MONO-014) — identical trust boundary to FE-002/003/004 (`getOperatorToken()`, never the GAP OIDC token; `X-Tenant-Id` from the active tenant).
- Composed from **existing** endpoints only (no new GAP contract): a small fan-out (accounts page total, recent audit/security activity, operator counts/status mix). Per-source failure degrades that card only (§ 2.5 resilience parity); the overview never blanks the shell. The fan-out is bounded (integration-heavy I1) — explicit timeouts, no unbounded default; the producers' meta-audit (audit query) is respected (one overview load = one bounded set of calls, no aggressive auto-refetch).
- No mutations (read-only slice — no `X-Operator-Reason`/`Idempotency-Key`/confirm scaffolding; carrying FE-002/004 mutation patterns here would be a defect, mirroring the FE-003 read discipline).

### D4 — Contract reconciliation mandate (spec-first, part of execution)

ACCEPTED execution (TASK-PC-FE-005) MUST, before code:
- `projects/platform-console/specs/contracts/console-integration-contract.md` — bind the § 3 `dashboards` line to a new **§ 2.4.4 "GAP operator overview (composed)"** (cross-reference to the *existing* `admin-api.md` read endpoints it composes; explicitly no new producer; the D2 parity-redefinition restated; resilience/tenant/operator-token obligations).
- `projects/platform-console/specs/services/console-web/architecture.md` — add the `features/dashboards` (or `features/overview`) module to the Layered-by-Feature map (canonical form intact).
- GAP specs **unchanged** (no producer change — composition only; cross-reference the existing endpoints).

### D5 — Downstream task plan + sequencing (post-ACCEPTED)

1. **`TASK-PC-FE-005`** (platform-console, spec-first): the composed operator-overview screen (D3) + the § D4 reconciliation + vitest (operator-token-not-GAP, read-only no-mutation-artifacts, per-card degrade, tenant scope, meta-audit-respecting fan-out). Slice 4/5.
2. **`TASK-PC-FE-006`** (parity-verify, slice 5/5): formal verification of the **refined** § 3 checklist — accounts/audit/operators/security + the D2-refined dashboards (overview) — = the ADR-MONO-013 Phase 3 admin-web-retirement gate.
3. ADR-MONO-013 § 3 / D7.4 carries the additive D2 refinement note (no ADR-013 decision change).

### D6 — Readiness + ACCEPTED transition

ACCEPTED when: user-explicit intent ("ADR-015 ACCEPTED" / "대시보드 진행" / equivalent). On ACCEPTED (a follow-up governance task, ADR-MONO-014/TASK-MONO-110 precedent): append § 6 ACCEPTED row, reword § 1.3/§ D6 to ACCEPTED past-tense, author `TASK-PC-FE-005`, begin the D5 sequence. Until then `TASK-PC-FE-005` / FE-006 stay unstarted (FE-006 depends on the dashboards line being resolved).

---

## 3. Consequences

- **PROPOSED merge (this PR):** the HARDSTOP-06/09 is recorded + resolved-in-principle; the parity-checklist `dashboards` hole has a defined, defensible path; no code; no churn-clock effect (doc-only).
- **ACCEPTED moment:** `TASK-PC-FE-005` authored; the overview built spec-first (composing existing reads); § 3 dashboards line bound to § 2.4.4; FE-006 can then verify the full refined checklist.
- **Post:** ADR-MONO-013 Phase 2 completes (5/5 slices); Phase 3 admin-web retirement gate is the refined checklist (overview, not Grafana) — defensible because the observability/Grafana view is explicitly re-scoped to operator/SRE tooling, not a console-parity blocker.
- **Future-self:** a session asked to "build FE-005 dashboards / resume Phase 2 slice 4" must first confirm ADR-MONO-015 ACCEPTED; otherwise re-enter this Hard Stop. A request for *true* Grafana/observability parity in the console is a new observability ADR, not this one.

## 4. Alternatives Considered

A (Grafana iframe) / C (defer entirely) — see § D1 table (rejected with reasons). Doing nothing (implement FE-005 by guessing compose-vs-embed) — rejected: bakes an undefendable implicit architecture + parity-redefinition decision (the exact HARDSTOP-09 failure mode this ADR exists to prevent).

## 5. Relationship to ADR-MONO-013 / 014 / FE-002–004

| | ADR-MONO-013 | ADR-MONO-014 | FE-002/003/004 | ADR-MONO-015 (this) |
|---|---|---|---|---|
| Role | Console foundation, parity checklist | Operator-auth bridge | accounts/audit/operators surfaces | **Resolves the bare `dashboards` parity line** |
| dashboards | § 3 line, undefined | n/a | n/a | **decides: B composed overview; parity-line refined (not Grafana)** |

This ADR amends ADR-MONO-013 § 3 / D7.4 (the parity checklist) additively and is a prerequisite for ADR-MONO-013 Phase 2 slice 4–5 + Phase 3.

## 6. Status Transition History

Append-only.

| Date | Transition | Decision | User intent quote | PR(s) |
|---|---|---|---|---|
| 2026-05-16 | created PROPOSED | B (composed operator overview) | "B. 합성 operator overview (read)" (resolution-direction answer) | this PR (TASK-MONO-111) |

(ACCEPTED row reserved — appended when execution begins per § D6.)

## 7. Provenance

- HARDSTOP-06 #3 + HARDSTOP-09 #2 (`platform/hardstop-rules.md`) — mandate for an ADR + PAUSE-until-ACCEPTED on a missing-spec / undocumented cross-cutting console architecture + parity-definition decision.
- Surfaced during the ADR-MONO-013 Phase 2 slice-4 (FE-005 dashboards) investigation (2026-05-16): `console-integration-contract.md` § 3 bare `dashboards` line vs no GAP producer endpoint vs `admin-web/architecture.md:75` Grafana iframe.
- Decision direction B = user-explicit answer to the resolution-direction question (2026-05-16).

분석=Opus 4.7 / 구현 권장: ADR·계약 재정렬 = Opus (parity-definition + cross-cutting boundary call — interpretive); composed-overview 화면 구현 = Sonnet/Opus (read-only fan-out; 보안 경로는 Opus, FE-003 패턴 재사용).
