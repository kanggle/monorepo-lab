# Task ID

TASK-MONO-172

# Title

`docs/project-overview.md` reality-alignment (10th) — record, since the 9th alignment (TASK-MONO-168, 2026-06-02): ADR-MONO-020 D6 step4 "intentional non-execution" disposition (MONO-169 → ADR-020 effectively complete), the **MONO-170 console full-stack local demo → runtime producer↔consumer drift cleanup wave** (8 fixes: ERP-BE-006 / SCM-BE-020 / MONO-171 / BE-331 / BE-332 / BE-333 / BE-334 / SCM-BE-021), and the frontend Docker build-cache optimization (PC-FE-035 / FE-072).

# Status

done

# Owner

(docs reconcile — surgical edits to `docs/project-overview.md` only; no code, no spec, no ADR content change)

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Dependency Markers

- **follows**: TASK-MONO-168 (9th reality-alignment, the prior snapshot). This is the periodic monorepo-level docs reconcile (10th).
- **reflects (already-merged work, no behaviour change here)**: MONO-169, MONO-170 (demo), and the demo-surfaced drift chain ERP-BE-006 / SCM-BE-020 / MONO-171 / BE-331 / BE-332 / BE-333 / BE-334 / SCM-BE-021, plus PC-FE-035 / FE-072.
- **no dependency on**: any new code/spec/ADR. `docs/project-overview.md` is a human-facing snapshot (CLAUDE.md § Source of Truth: NOT read by agents as truth); this task only re-aligns it with `origin/main` reality + the project memory.

---

# Goal

`docs/project-overview.md` accurately snapshots the monorepo as of 2026-06-03 — the "갱신 시점" header, per-project status (§2.1 wms / §2.4 scm / §2.6 console), §6 build/test, §7 ADR table (ADR-020 row), and §9 roadmap (Phase 8+ row) reflect the post-9th-alignment merged work.

# Scope

## In Scope

`docs/project-overview.md` only — 7 surgical edits:
1. **Header 갱신 시점** 2026-06-02 → 2026-06-03 + new "마지막 의미 있는 변화" clause (MONO-169 ADR-020 완료 / MONO-170 demo-driven drift wave / frontend Docker cache).
2. **§2.1 wms** — read-model 42P18 query fixes (BE-331/332) + outbound startup/security prerequisites (BE-333/334) + outbound IT deferral note.
3. **§2.4 scm** — console-ops-demo runtime read-consumer 정합 (SCM-BE-020 decimal-string / MONO-171 seed / SCM-BE-021 422→500).
4. **§2.6 console** — MONO-170 full-stack local demo as the sender of the 8-fix runtime drift cleanup + the "merge+CI green ≠ runtime federation 정합" meta.
5. **§6 build/test** — frontend Docker build-cache optimization (PC-FE-035 / FE-072, 60~82% build-time↓; fan-platform-web Dockerfile-less = out of scope).
6. **§7 ADR table** — ADR-MONO-020 row: D6 step4 = MONO-169 intentional-non-execution disposition → 사실상 완료.
7. **§9 roadmap** — Phase 8+ row 잔여 갱신 (ADR-020 D6 step4 dispositioned + drift wave + docker cache; 잔여=0 except deferred outbound IT).

## Out of Scope

- Any code/spec/ADR change. No new ADR.
- Memory files (already updated in their respective task closures).
- The deferred outbound IT overhaul (noted as deferred, not started).

# Acceptance Criteria

- [x] **AC-1** Header "갱신 시점" = 2026-06-03 and its clause names MONO-169 / MONO-170 drift wave / PC-FE-035·FE-072.
- [x] **AC-2** §2.1 / §2.4 / §2.6 carry the demo-driven runtime cleanup; §6 carries the docker-cache note; §7 ADR-020 row + §9 Phase 8+ row reflect MONO-169 disposition.
- [x] **AC-3** Diff confined to `docs/project-overview.md` (+ task lifecycle). No code/spec/ADR change. Markdown links resolve (existing ADR paths reused; MONO-170 task link added).

# Related Specs

- N/A (docs snapshot). Source reality = `origin/main` git log + project memory `project_platform_console_adr_013` / `project_refactor_sweep_status` / `project_frontend_docker_build_cache_optimization`.

# Edge Cases

- The doc explicitly states it changes only at "명시적 갱신 시점" and that git log + the header date are the drift-resolution authority (§ trailing note) — this task is exactly such an explicit alignment.

# Failure Scenarios

- Over-claiming completeness: avoided — the deferred outbound IT overhaul is named as deferred, not done; fan-platform-web docker-cache is noted as out of scope (no Dockerfile).

# Test Requirements

- Docs-only; no build/test. Visual diff review + link sanity.

# Definition of Done

- [x] 7 surgical edits applied to `docs/project-overview.md`.
- [x] Diff confined; no code/spec/ADR change.
- [x] Task md + root `tasks/INDEX.md` updated.
- [x] Reviewed + merged (impl PR #1051 squash `f43e803c`, 3-dim verified).

---

분석=Opus 4.8 / 구현=Opus(직접, docs reconcile). 10th reality-alignment. **메타: project-overview 는 "머지+CI green ≠ 런타임 federation 정합" 을 MONO-170 데모가 실증한 회차 — full-stack 데모 구동이 mock/slice 가 못 잡는 cross-service drift 의 sender 임을 §2.6 에 명문화.**
