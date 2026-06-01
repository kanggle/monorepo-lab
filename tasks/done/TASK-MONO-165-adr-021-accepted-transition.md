# Task ID

TASK-MONO-165

# Title

ADR-MONO-021 PROPOSED → ACCEPTED 승급 (doc-only governance flip). `account_type` OIDC 클레임 source 결정(D1-D5)을 byte-unchanged finalise. sibling ADR-020→MONO-157 / ADR-019→MONO-153 staged-child ACCEPTED 패턴 답습. UNPAUSE the § 3.3 execution roadmap.

# Status

done

> **완료 (2026-06-02)**: impl PR #1008 (squash `6bbdca08`). ADR-MONO-021 `PROPOSED → ACCEPTED` doc-only flip (sibling ADR-020/MONO-157). D1-D5 finalised byte-unchanged(§1-5/7 byte-identical to PROPOSED #1006 `20f19c26`; flip=Status+History ACCEPTED clause+§6 ACCEPTED row[`#1006` 해소]+§1.3/§3.3 past-tense, HARDSTOP-04). ADR-003a §3 row #28(sibling #26/#24). doc-only. 3차원 ✓(docs fast-lane). **효과**: ADR-021 §3.3 execution roadmap **UNPAUSED** — credentials `account_type` 컬럼+customizer/provider 주입(D1/D3) → provisioning(D2) → INT-023 e2e 단언(D4 step3), 각 future task, dependency-correct base = 본 ACCEPTED main. 분석=Opus 4.8 / 구현=Opus.

# Owner

architecture

# Task Tags

- docs
- adr

---

# Dependency Markers

- **depends on**: ADR-MONO-021 PROPOSED (TASK-MONO-164 #1006 `20f19c26`) on `origin/main`.
- **sibling pattern**: ADR-019→MONO-153 / ADR-020→MONO-157 (same-session staged-child PROPOSED→ACCEPTED).

# Goal

Flip ADR-MONO-021 to ACCEPTED (doc-only) so its § 3.3 `account_type` execution roadmap is authorized as a dependency-correct base.

# Scope

- `docs/adr/ADR-MONO-021-account-type-claim-source.md` — Status `PROPOSED → ACCEPTED`; History ACCEPTED clause append; § 6 ACCEPTED row + PROPOSED-row PR `#<this>`→`#1006` resolution; PENDING note → UNPAUSED. **D1-D5 + § 1 Context + § 2 Decision tables + § 3 Consequences + § 4 Alternatives + § 5 Relationship + § 7 Provenance byte-identical** (ACCEPTED finalises, does not re-decide — HARDSTOP-04 discipline).
- `docs/adr/ADR-MONO-003a-*.md` § 3 audit row #28 (Meta-policy: ADR-021 ACCEPTED transition; sibling #23/#24/#26 — does NOT add to § D1).
- Doc-only.

# Acceptance Criteria

- **AC-1** ADR-MONO-021 Status = ACCEPTED; D1-D5 decision body byte-unchanged from PROPOSED.
- **AC-2** § 6 ACCEPTED row records the user-explicit intent + the finalise-not-re-decide note.
- **AC-3** ADR-003a § 3 row #28 appended (rows #1-#27 byte-unchanged).
- **AC-4** Doc-only diff.

# Related Specs

- `platform/contracts/jwt-standard-claims.md`

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` § account_type.

# Edge Cases

- ACCEPTED must finalise byte-unchanged — verify the D1-D5 tables are byte-identical to the PROPOSED merge.

# Failure Scenarios

- Re-deciding any D1-D5 option at ACCEPTED → violates the staged-child finalise discipline (HARDSTOP-04). ACCEPTED is governance-only.
