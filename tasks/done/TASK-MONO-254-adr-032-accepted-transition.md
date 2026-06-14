# Task ID

TASK-MONO-254

# Title

**ADR-MONO-032 PROPOSED → ACCEPTED transition** — Unified identity model (single account → roles set; remove the `account_type` CONSUMER/OPERATOR partition). Doc-only governance flip finalising the D1-D6 CHOSEN-PROPOSED direction byte-unchanged + effecting the **ADR-MONO-021 supersession**. Sibling staged-child ACCEPTED pattern (ADR-019→MONO-153 / ADR-020→MONO-157 / ADR-021→MONO-165 / ADR-023→MONO-206 / ADR-024→MONO-209). UNPAUSES the § 3.3 6-step execution roadmap.

# Status

done

> **완료 (2026-06-14)**: impl PR #<this>. ADR-MONO-032 PROPOSED → ACCEPTED — 통합 identity 모델(account_type xor 파티션 제거, roles set 유일 축) 결정 **확정**. User-explicit intent = *"추천대로 진행해줘"* (PROPOSED #1513 squash `82cb08c0` 머지 후 제안한 "ADR-032 ACCEPTED 승급" next-step 선택; sibling ADR-021 *"진행"* same-session PROPOSED→ACCEPTED 동형). D1-D6 byte-unchanged finalise(§ 1-5/7 byte-identical). flip = Status + History ACCEPTED clause + § 6 ACCEPTED row(+#1513 해소) + § 3.3 PAUSED→UNPAUSED. **동반**: ADR-MONO-021 Status `ACCEPTED → SUPERSEDED by ADR-032` + History SUPERSEDED clause(ADR-021 본문 byte-unchanged — supersession note 한정). ADR-003a audit row #34(append-only). doc-only(apps/·contracts/ 0). 후속 = § 3.3 6-step UNPAUSED: contract-first `jwt-standard-claims.md` rewrite(D5 step0, breaking) → dual-read gateways(5 gw) → roles-only issuance → account unify(opt-in) → drop legacy → e2e, dependency-correct base = 본 PR ACCEPTED main. 각 step 별 task, 분석=Opus 4.8 / 구현 권장=Opus.

# Owner

architecture

# Task Tags

- docs
- adr
- security

---

# Dependency Markers

- **predecessor**: TASK-MONO-253 (ADR-032 PROPOSED publish, #1513 squash `82cb08c0`) — this task finalises that decision.
- **supersedes (effects)**: ADR-MONO-021 (ACCEPTED → SUPERSEDED) — the supersession declared in ADR-032's PROPOSED body takes effect at this ACCEPTED flip.
- **unpauses**: ADR-032 § 3.3 6-step execution roadmap (contract rewrite → dual-read gateways → roles-only issuance → account unify → drop legacy → e2e). Each is a future task.

# Goal

Flip ADR-MONO-032 PROPOSED → ACCEPTED (doc-only, D1-D6 byte-unchanged finalise) so the unified-identity execution roadmap proceeds from a dependency-correct ACCEPTED base, and effect the ADR-MONO-021 supersession (Status + History note, ADR-021 body byte-unchanged).

# Scope

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` — Status PROPOSED → ACCEPTED; History ACCEPTED clause; § 6 table PROPOSED-row PR# `#<this>`→`#1513` + new ACCEPTED row; § 6 PROPOSED note → ACCEPTED note; § 3.3 PAUSED→UNPAUSED. § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance **byte-identical** to PROPOSED.
- `docs/adr/ADR-MONO-021-account-type-claim-source.md` — Status `ACCEPTED` → `SUPERSEDED by ADR-MONO-032` + History SUPERSEDED clause. **§ 1-7 body byte-unchanged** (supersession is a Status+History note, not a re-decision of the now-removed claim's source).
- `docs/adr/ADR-MONO-003a-d4-override-scope-canonicalization.md` § 3 audit table — append row #34 (Meta-policy: ADR-032 ACCEPTED transition; same one-off category as row #32; does NOT add to § D1; rows #1-#33 byte-unchanged).
- Doc-only. NO contract/schema/code change (the breaking `jwt-standard-claims.md` rewrite is D5 step 0, a separate post-ACCEPTED execution task — contract-first per its § Change Rule).

# Acceptance Criteria

- **AC-1** ADR-MONO-032 Status = ACCEPTED; D1-D6 byte-unchanged from PROPOSED #1513 `82cb08c0` (§ 1-5/7 byte-identical).
- **AC-2** ADR-032 § 6 carries the ACCEPTED row + PROPOSED-row PR# resolved to #1513; § 3.3 roadmap UNPAUSED.
- **AC-3** ADR-MONO-021 Status = SUPERSEDED by ADR-MONO-032; ADR-021 § 1-7 body byte-unchanged (Status+History note only).
- **AC-4** ADR-003a § 3 audit row #34 appended (append-only; rows #1-#33 byte-unchanged).
- **AC-5** Doc-only diff (no `apps/` code, no `platform/contracts/` change, no migrations).
- **AC-6** NOT a re-decision: ACCEPTED finalises D1-D6, does not re-litigate them.

# Related Specs

- `docs/adr/ADR-MONO-032-unified-identity-roles-model.md` (the ADR being accepted)
- `docs/adr/ADR-MONO-021-account-type-claim-source.md` (the superseded ADR)
- `platform/contracts/jwt-standard-claims.md` (the contract the execution roadmap rewrites — NOT touched here)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` (breaking rewrite deferred to D5 step 0 execution task).

# Edge Cases

- ADR-003a audit table is append-only — verify rows #1-#33 byte-unchanged.
- ADR-021 supersession must be a Status+History note only — its D1-D5 body stays byte-unchanged (historical record of how the now-removed claim was sourced).
- ACCEPTED must not rewrite `jwt-standard-claims.md` — the contract's § Change Rule requires the rewrite to precede *implementation* (D5 step 0), not the ACCEPTED governance flip.

# Failure Scenarios

- If ADR-032 D1-D6 are re-decided at ACCEPTED (not byte-unchanged finalise) → violates the staged-child ACCEPTED discipline (ACCEPTED finalises, does not re-litigate).
- If the contract or any gateway/issuance code is changed in this task → violates the doc-only scope + the contract § Change Rule sequencing (rewrite is a separate post-ACCEPTED task).
