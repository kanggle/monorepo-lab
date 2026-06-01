# Task ID

TASK-MONO-168

# Title

`docs/project-overview.md` reality-alignment (9th, MONO-141/148 pattern) — record ADR-MONO-019/020/021 ACCEPTED + post-Phase-8 work (customer-tenant entitlement-trust, operator N:M assignment + assume-tenant, account_type OIDC claim, console overview consolidation, console-web PR CI gate, internal-system trait rules) that landed after the 2026-05-28 (MONO-148) snapshot — surgical doc edits only, no decisions

# Status

done

# Owner

docs / reality-alignment (no code)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **precedent**: TASK-MONO-141 (#896, 2026-05-28 reality-alignment 6th) + TASK-MONO-148 (#922, 7th, last touch of `docs/project-overview.md`). This is the 9th surgical reality-alignment of the same human-reference portfolio doc.
- **records (already-merged, no new decisions)**: ADR-MONO-019 (customer-tenant model + entitlement-trust, ACCEPTED 2026-05-31), ADR-MONO-020 (operator↔multi-customer N:M assignment + assume-tenant RFC8693, ACCEPTED 2026-05-31), ADR-MONO-021 (account_type OIDC claim source, ACCEPTED 2026-06-02) + their execution chains (BE-322/324/325/326/327, MONO-154/156-162, BE-329/330, INT-024) + PC-FE-034 (console overview consolidation), MONO-166 (console-web PR CI gate), MONO-167 (internal-system trait rules).
- **monorepo-level**: edits root `docs/project-overview.md` (shared). NOT `docs/guides/` (that is AI-source-of-truth-excluded; project-overview.md is the portfolio human-reference doc that reality-alignment legitimately maintains, per MONO-141/148 precedent).

---

# Goal

Bring the portfolio human-reference doc back to current reality. Its last meaningful update (line 4 "갱신 시점: 2026-05-28", MONO-148) stops at ADR-MONO-018 / Phase 8 federation hardening MVP. Since then, three ADRs (019/020/021) reached ACCEPTED with full execution chains, plus several platform-console + rules changes landed — none reflected. A portfolio doc that lags 3 ACCEPTED ADRs misrepresents the system to evaluators (the doc's whole purpose).

This is **surgical doc editing with NO decisions** — it records what already merged (verifiable in `git log origin/main`), in the existing structure (§7 ADR table rows + §2.2/§2.6 status lines + §9 roadmap + line 4 timestamp).

# Scope

## In Scope

1. **line 4 갱신 시점** — `2026-05-28` → `2026-06-02` + append the new meaningful changes (ADR-019/020/021 ACCEPTED + entitlement-trust runtime + console overview consolidation + console-web CI gate + internal-system rules).
2. **§7 ADR 테이블** — add three rows: ADR-MONO-019 (ACCEPTED 2026-05-31, customer-tenant model + entitlement-trust), ADR-MONO-020 (ACCEPTED 2026-05-31, operator N:M assignment + assume-tenant), ADR-MONO-021 (ACCEPTED 2026-06-02, account_type OIDC claim source).
3. **§2.2 global-account-platform** — reflect: customer-tenant entitlement-trust runtime (acme-corp/globex seeds), operator N:M assignment + assume-tenant RFC8693 exchange, account_type CONSUMER/OPERATOR claim now emitted (BE-329/330), workload-identity migration (ADR-005, already may be noted — verify).
4. **§2.6 platform-console** — reflect: ADR-019/020 entitlement-trust + multi-tenant active-tenant switcher (D4 A↔B), overview consolidation (PC-FE-034: home=5-domain overview / GAP drill-down / ERP nav), console-web admitted to PR CI gate (MONO-166).
5. **§9 향후 로드맵** — update post-Phase-8 status (D4 observability / D5 isolation COMPLETE; ADR-019/020/021 execution COMPLETE; remaining = ADR-020 D6 step4 user-gated cleanup + external triggers).
6. **(if present) rules-library mention** — internal-system trait rules now authored (MONO-167) — only if the doc enumerates trait coverage.
7. **Task md + root `tasks/INDEX.md`** ready entry.

## Out of Scope

- **No new decisions / no ADR authoring** — only record merged facts.
- **No code / spec / contract change** — `docs/project-overview.md` (+ task lifecycle) only.
- **No `docs/guides/` edit** — out of scope (AI-source-excluded).
- **No rewrite** — surgical edits in the existing structure (MONO-141/148 discipline: minimal targeted edits, not a re-author).
- **Other docs** (README, ADR bodies) — unchanged.

# Acceptance Criteria

- [ ] **AC-1** §7 ADR table has rows for ADR-MONO-019, 020, 021 with correct ACCEPTED dates + 1-line summaries; links resolve to the actual ADR files.
- [ ] **AC-2** line 4 timestamp = 2026-06-02 with the new meaningful-change list appended.
- [ ] **AC-3** §2.2 GAP reflects account_type claim (BE-329/330) + customer-tenant entitlement-trust + operator N:M assignment/assume-tenant (factually, citing the merged tasks/PRs).
- [ ] **AC-4** §2.6 platform-console reflects entitlement-trust active-tenant switch + overview consolidation (PC-FE-034) + console-web CI gate (MONO-166).
- [ ] **AC-5** §9 roadmap shows post-Phase-8 reality (ADR-019/020/021 execution COMPLETE; remaining = ADR-020 D6 + external triggers).
- [ ] **AC-6** Every date/PR/ADR cited is verifiable in `git log origin/main` (no invented facts). Edits are surgical (no structural rewrite).
- [ ] **AC-7** Diff = `docs/project-overview.md` + task lifecycle only; no code/spec/contract/guides.

# Related Specs

- [`docs/project-overview.md`](../../docs/project-overview.md) (the target).
- [ADR-MONO-019](../../docs/adr/ADR-MONO-019-customer-tenant-model.md) / [020](../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md) / [021](../../docs/adr/ADR-MONO-021-account-type-claim-source.md) (the ADRs to record — verify exact filenames).
- MEMORY: `project_platform_console_adr_013` + `project_gap_idp_promotion` (the merged-fact source for the alignment).

# Related Contracts

- None (human-reference doc; no API/event contract).

# Edge Cases

- **ADR filename mismatch** — verify the exact ADR-019/020/021 filenames via Glob before linking (avoid a dangling link — the very class of issue audit-memory flags).
- **§2.2 already partially current** — ADR-005 workload-identity may already be noted; check before adding to avoid duplication.
- **Roadmap Phase numbering** — Phase 8 was the last numbered phase; ADR-019/020/021 are post-Phase-8 execution, not a new "Phase 9" unless the doc already coined one. Record as post-Phase-8 work, don't invent a phase number.

# Failure Scenarios

- **Inventing facts/dates** — reality-alignment must only record verifiable merged work. Mitigation: AC-6 (every citation git-verifiable).
- **Structural rewrite drift** — re-authoring instead of surgical edits risks introducing inconsistency. Mitigation: AC-6 surgical constraint, MONO-141/148 precedent.
- **Dangling ADR link** — linking a wrong ADR filename. Mitigation: AC-1 + Edge Case Glob-verify.
- **CI** — docs-only, markdown fast-lane.

---

분석=Opus 4.8 / 구현 권장=Sonnet (docs reality-alignment, surgical edits, merged-fact transcription — no decisions). 단 ADR 3건 + 다수 chain 의 정합 판단(무엇을 어느 섹션에 어떻게 1줄로)이 필요하면 Opus. dispatcher 가 메모리로 사실을 이미 보유하므로 직접 수행 효율적.
