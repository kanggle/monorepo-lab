# Task ID

TASK-MONO-311

# Title

`ADR-MONO-043 PROPOSED → ACCEPTED` — notification architecture unification transition

# Status

done

# Owner

architect

# Task Tags

- adr
- architecture
- notification
- governance

---

# Goal

Perform the staged-child **PROPOSED → ACCEPTED** transition for `ADR-MONO-043` (notification architecture unification), authored PROPOSED by TASK-MONO-308 (PR #1958). The user supplied the D8 acceptance gate with the exact intent **"ADR-043 ACCEPTED"**. ACCEPTED *finalises* the D1–D8 CHOSEN-PROPOSED directions byte-unchanged and unblocks the D7 P1–P3 implementation chain (as separate user-gated tasks).

# Scope

## In Scope

| 산출물 | 위치 | 설명 |
|---|---|---|
| ADR-043 flip | `docs/adr/ADR-MONO-043-notification-architecture-unification.md` | Status `PROPOSED → ACCEPTED`; History ACCEPTED clause appended; § 3.3 `PAUSED → UNPAUSED` (step 1 marked DONE); § 6 audit-trail ACCEPTED row added. **D1–D8 + § 1/§ 2/§ 4/§ 5 byte-unchanged** (ACCEPTED finalises, does not re-decide). |
| INDEX 갱신 | `tasks/INDEX.md` done 섹션 | 본 task 한 줄 done entry. |

## Out of Scope

- Any D1–D8 re-decision (ACCEPTED is byte-unchanged finalise).
- The D7 P1–P3 implementation (shared contract spec + `libs/` library + per-domain conformance + console-bff aggregator) — separate post-ACCEPTED user-gated tasks.
- ADR-016 § D3 (already carries the additive forward-pointer from #1958; untouched here — HARDSTOP-04).
- `docs/adr/INDEX.md` (012a-onward de-facto not re-indexed).

---

# Acceptance Criteria

- [x] **AC-1** — `ADR-MONO-043` Status = `ACCEPTED`.
- [x] **AC-2** — History gains an ACCEPTED clause naming TASK-MONO-311 + the user-explicit "ADR-043 ACCEPTED" gate (NOT self-ACCEPT) + "D1–D8 finalised byte-unchanged".
- [x] **AC-3** — § 3.3 flipped PAUSED → UNPAUSED; step 1 (ACCEPTED transition) marked DONE; steps 2–4 implement-ready.
- [x] **AC-4** — § 6 audit-trail table gains the ACCEPTED row (TASK-MONO-311, this PR).
- [x] **AC-5 (byte-unchanged finalise)** — `git diff` shows only Status + History clause + § 3.3 heading/step-1 + § 6 row changed; D1–D8 decision bodies + § 1/§ 2/§ 4/§ 5 unchanged.
- [x] **AC-6 (no impl)** — doc-only PR; no contract/library/service/aggregator code (HARDSTOP-09 — ACCEPTED authorises implementation but does not itself implement).

---

# Related Specs

- [ADR-MONO-043](../../docs/adr/ADR-MONO-043-notification-architecture-unification.md) — the ADR being transitioned.
- [ADR-MONO-016 § D3](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) — additive forward-pointer target (byte-unchanged).
- TASK-MONO-308 (done) — the PROPOSED authoring (PR #1958).

# Related Contracts

- None — ADR is an architectural decision, not a contract. The shared notification contract (D3) is a post-ACCEPTED P1 deliverable.

---

# Edge Cases

- **D1–D8 body drift during flip** → AC-5 violation. `git diff` must show decision bodies byte-unchanged; only Status/History/§3.3/§6 change.
- **Ambiguous acceptance intent** → the D8 gate requires the exact form; "ADR-043 ACCEPTED" satisfies it. A bare "진행" would not.
- **ADR-016 § D3 touched** → HARDSTOP-04; the forward-pointer already exists from #1958, so this PR makes zero ADR-016 change.

# Failure Scenarios

- **Self-ACCEPT** (flip without the user gate) → prohibited by D8; this transition was gated on the user's explicit "ADR-043 ACCEPTED".
- **ACCEPTED silently authorises an impl in the same PR** → HARDSTOP-09; the flip is doc-only, P1–P3 are separate user-gated tasks.

---

# Definition of Done

- [x] ADR-043 Status=ACCEPTED + History/§3.3/§6 updated; D1–D8 byte-unchanged.
- [x] `tasks/INDEX.md` done entry.
- [x] Doc-only PR (no impl code).
- [x] commit + push (branch `task/mono-311-adr-043-accepted`).
- [ ] PR open + merge (3-dim verify).
- [ ] P1 (shared contract + `libs/` library) — separate post-ACCEPTED task (next).
