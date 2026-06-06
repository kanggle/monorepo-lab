# Task ID

TASK-MONO-182

# Title

`docs/project-overview.md` **reality-alignment (13th)** — reflect the `gap`→`iam` rename (MONO-179/180/181) in the portfolio SoT prose: current-architecture project-name references `GAP`→`IAM` + domain slug `gap`→`iam` + header 갱신-시점 bump. Docs-only.

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level docs reality-alignment (root `docs/`). One atomic PR (MONO-141/148/168/172/177/178 cadence).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Dependency Markers

- **선행**: TASK-MONO-179 (#1149) + 180 (#1151) + 181 (#1153) — gap→iam rename 전체 종결. 179가 `docs/project-overview.md`의 **구조적 토큰**(`iam.local` hostname, sync 테이블 `kanggle/iam-platform`, §2.2 heading 링크 `iam-platform/PROJECT.md`)은 이미 갱신했으나 **현재-서술 prose의 "GAP" 짧은-이름 + 도메인 slug `gap`**은 미반영.
- **trigger 부류**: reality-alignment cadence (MONO-141 6th / 148 7th / 168 9th / 172 10th / 177 11th / 178 12th) — 대형 구조 변경(rename)이 SoT 정합을 요구하는 정확한 케이스. docs-only, no code/spec/ADR.

# Goal

After this task, `docs/project-overview.md` describes the current system with the project's current name (`iam` / `iam-platform`) — no `GAP`/`gap` short-name remains as a **current-architecture** descriptor (ID-provider lines, OIDC client refs, domain-slug enumerations, §7 ADR-table project labels, §2.2 heading), with a one-line rename provenance note + bumped 갱신-시점. Dead-ref count stays 0 (sweep touches prose labels, not the already-`iam-platform` link URLs).

# Scope

## In Scope (docs/project-overview.md only)

1. **Header L4** — 갱신 시점 `2026-06-05` → `2026-06-07` + prepend latest change: gap→iam 전체 rename (MONO-179/180/181: `global-account-platform`→`iam-platform`, 콘솔 slug/registry productKey/OIDC provider id/예약 tenant/console tenant_id + redirect_uris + spec dead-ref 16→0).
2. **Current-architecture `GAP` → `IAM`** (prose project-name; this doc is a current-state snapshot, no English-word "gap"): §2.1 L40 ID-provider, §2.2 L46/L65/L66, §2.3 L87 "GAP migration", §2.5 L110 "GAP V0011 client", §2.6 L126/L128-130/L136, §2.7 L153, §2.8 L175 ID-provider, §7 L345-347 ADR-table project labels ("GAP [ADR-001/003/004]"), §8 L358 "GAP cutover", §9 L376 "Third project (GAP)".
3. **Domain slug `gap` → `iam`** in domain enumerations: §1 L13 `(gap·wms·scm·finance·erp)`, §2.6 L127 `(`gap` + `wms` + …)`.
4. **§2.2 heading L43** `(GAP)` → provenance note `(IAM — 옛 GAP/global-account-platform, MONO-179/180 rename)`.

## Out of Scope

- Any code/spec/ADR/migration (this is the SoT doc only; the rename's code/spec already landed in 179/180/181).
- ADR *filenames* + the project-level ADR-001/003/004 *content* (link URLs already `iam-platform/...`, unchanged).
- Re-dating historical events (e.g. "2026-05-03 v1 종결") — only the project NAME within them aligns to current `IAM`.

# Acceptance Criteria

- AC-1: `git grep -n "\bGAP\b" docs/project-overview.md` returns 0 (all current-architecture project-name refs → IAM). Domain-slug `gap·`/`` `gap` `` enumerations → `iam`.
- AC-2: Spec/doc dead-ref checker still 0 broken (the sweep changes prose labels, not link URLs — URLs already `iam-platform/...`).
- AC-3: `git diff` confined to `docs/project-overview.md` + `tasks/` — no code/spec/ADR/migration change.
- AC-4: Header 갱신 시점 = 2026-06-07 with the rename as the leading change entry.

# Related Specs

- None (docs-only). `docs/project-overview.md` is the portfolio snapshot, sibling to `docs/adr/`.

# Related Contracts

- None.

# Edge Cases

- The doc has **no English-word "gap"** and is markdown (no Tailwind) — `GAP`→`IAM` uppercase sweep is collision-free, unlike code sweeps.
- Link URLs already use lowercase `iam-platform/...` (MONO-179) — the `GAP`→`IAM` label sweep must NOT alter URLs (it doesn't; URLs contain no `GAP`).
- §7 ADR-table "GAP [ADR-001]" rows are **current catalog** pointers (not dated narrative) → label → IAM.

# Failure Scenarios

- **Re-dating history** → loss of audit trail. Only the project name aligns; event dates byte-unchanged.
- **Sweep hits a link URL** → dead-ref. URLs are lowercase `iam-platform`, no `GAP` — verify AC-2 (dead-ref 0) post-edit.
