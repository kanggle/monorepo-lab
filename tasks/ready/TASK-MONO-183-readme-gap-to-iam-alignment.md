# Task ID

TASK-MONO-183

# Title

**README-layer reality-alignment** for the `gap`→`iam` rename (MONO-179/180/181/182) — align the **standalone-publish-facing README prose** so each `kanggle/<project>` repo's first screen uses the current `IAM` name. The hub `README.md` + 8 project READMEs still carry `GAP` / `Global Account Platform` as a **current-architecture** descriptor (MONO-179 did structural tokens only; MONO-182 did `docs/project-overview.md` only — the README layer was never swept). Standalone-sync **prep** for the ≥ 2026-06-10 portfolio publish (the publish itself = classifier-blocked user-shell, out of scope). Docs-only.

# Status

ready

# Owner

claude (Opus 4.8) — monorepo-level docs reality-alignment across the README layer (root `README.md` + `projects/*/README.md`). One atomic PR (CLAUDE.md § Cross-Project Changes; MONO-182 reality-alignment cadence precedent).

# Task Tags

<!-- api | event | deploy | code | test | adr | onboarding -->

- onboarding

---

# Dependency Markers

- **선행**: TASK-MONO-179 (#1149, `global-account-platform`→`iam-platform` dir/alias/iss) + 180 (#1151, 4 residual `gap` 표면 → `iam`) + 182 (#1155, `docs/project-overview.md` reality-alignment). 179 가 README 의 **링크 URL/anchor** (`iam-platform/`, `#iam-idp-integration`, `iam.local`, `kanggle/iam-platform`) 는 이미 갱신했으나 README 의 **자신의 섹션 헤더 + 산문 + 링크 텍스트의 "GAP"** 는 미반영.
- **trigger 부류**: standalone portfolio sync cadence (≥ 2026-06-10) prep + reality-alignment cadence (MONO-141/148/168/172/177/178/182). `scripts/sync-portfolio.sh` 는 179 가 이미 `iam-platform` 키/remote 로 갱신 완료 — **본 task 는 그 스크립트가 publish 할 README 산출물의 잔여 naming 정합**.

# Goal

After this task, the hub `README.md` and every `projects/*/README.md` describe the IdP/identity project by its current name (`IAM`) — no `GAP` / `Global Account Platform` remains as a **current-architecture** descriptor (project-name in IdP-integration sections, section headers, link text, ASCII diagrams, dev-token examples), and the console domain-slug enumerations use the current slug (`iam`, not `gap`). Dated historical events keep their dates (only the project NAME aligns). Link URLs/anchors stay byte-unchanged (already `iam-*`). Each standalone repo (`kanggle/{iam,scm,erp,finance,fan,...}-platform`) thus publishes with a consistent IAM-named README at the ≥ 2026-06-10 sync.

# Scope

## In Scope (README layer only)

1. **Hub `README.md`** — 3 stale lines:
   - L20 ecommerce row `GAP IdP migration pending` → `IAM IdP migration pending`.
   - L22 fan-platform row `GAP OIDC consumer` → `IAM OIDC consumer`.
   - L114 Note: `GAP`→`IAM` **+** adjacent stale-status fix — `PORT_PREFIX` is fully retired (CLAUDE.md § Local Network Convention) and `TASK-MONO-024` is in `tasks/done/` (the `tasks/ready/...` link is a hub-README dead-ref, never CI-gated). Rewrite the note to reflect the completed migration.
2. **`projects/iam-platform/README.md`** (the flagship standalone repo's own README) — title L1 `# Global Account Platform (GAP)` → `# Identity & Access Management (IAM)` + one-line provenance note (옛 명칭) so reviewers can map history; current-architecture `GAP`→`IAM` (L47/49 admin-web retirement + backend-only IdP, L96 CI "Integration job" — now `iam-integration-tests`, L193 operator-console). Dated 2026-05-18 events keep dates.
3. **`projects/{erp,scm,finance}-platform/README.md`** — uppercase `GAP`→`IAM` (clean: every `GAP` = the IdP project name): IdP table row + `[GAP integration]` link text, `GAP RS256 JWT 검증`, dev-token `GAP <client>` examples, `## GAP IdP Integration` section header, `GAP 측 인프라`, References `GAP integration`/`GAP 통합`. Task/migration NUMBERS (`TASK-MONO-042/114/119`, `V0013/V0015/V0017/V0018`) byte-unchanged.
4. **`projects/fan-platform/README.md`** — uppercase `GAP`→`IAM`: subtitle L4, OIDC-consumer L18, ASCII diagram L74 (`│ GAP │`→`│ IAM │`, same 3-char width = box alignment preserved), `## Differentiation from GAP's frozen community-service` + its table L92-99, References `GAP 통합`/`[GAP ADR-001]`.
5. **`projects/platform-console/README.md`** + **`apps/console-web/README.md`** — **two** token meanings: uppercase `GAP` (IdP/project name) → `IAM`; lowercase `gap` **domain-slug enumeration** (`gap · wms · scm` / `for gap/wms/scm`) → `iam` (MONO-180 renamed the console domain slug `DomainTarget.GAP`→`IAM`, wire `{"domain":"iam"}`).

## Out of Scope

- **`scripts/sync-portfolio.sh`** — already aligned by MONO-179 (`PROJECT_REMOTES`/`PROJECT_TYPES` use `iam-platform` key + `kanggle/iam-platform.git`). Verified, no change.
- **The actual ≥ 2026-06-10 publish** — GitHub repo rename `kanggle/global-account-platform`→`kanggle/iam-platform` + `bash scripts/sync-portfolio.sh` force-push = classifier-blocked outward-facing op → user-shell hand-off (recorded in Outcome).
- **Hub README "Standalone repo" column publish-status** (iam `_(monorepo-only)_`, fan `_(planned)_`) — that is publish-status alignment, set by the 06-10 publish task when the repos go live under the new name; NOT a rename-naming concern.
- **ecommerce `auth-service-deprecated/README.md` + `k8s/.../auth-service-deprecated/README.md`** — historical deprecation tombstones describing the cutover, AND excluded from ecommerce standalone sync (`PROJECT_EXCLUDE_PATHS`) → won't appear in the published repo. Residue (leave).
- Any code/spec/contract/ADR/migration — README prose only.
- Lowercase identifiers / file paths / link URLs / anchors — already `iam-*` (MONO-179/180); byte-unchanged.

# Acceptance Criteria

- AC-1: `git grep -n "\bGAP\b\|Global Account Platform" README.md projects/*/README.md projects/*/apps/*/README.md` returns 0 (excluding the In-Scope provenance note in iam README + the Out-of-Scope ecommerce deprecated tombstones, which live under `specs/`/`k8s/` not the project root README).
- AC-2: Console domain-slug enumerations show `iam · wms · scm` (not `gap`).
- AC-3: All README link URLs/anchors byte-unchanged (already `iam-*`); ASCII box alignment preserved (GAP→IAM same width).
- AC-4: `git diff` confined to `README.md` + `projects/*/README.md` (+ `tasks/`) — no code/spec/contract/ADR/migration change. Dated historical events keep their dates (only project NAME aligns).

# Related Specs

- None (docs-only). READMEs are portfolio/standalone-discovery surfaces, siblings to the specs (not specs themselves).

# Related Contracts

- None.

# Edge Cases

- **Two `gap` meanings in console READMEs**: uppercase `GAP` (IdP project name → IAM) vs lowercase `gap` (domain slug → iam). Targeted edits, not a blind sweep (MONO-180 sweep-corruption lesson).
- **Uppercase `GAP`→`IAM` is clean in erp/scm/finance/fan/iam READMEs** — `GAP` only ever denotes the IdP project name there; no English-word collision, no path/URL collision (URLs already `iam-*`), same width (ASCII art safe). Case-sensitive `.Replace('GAP','IAM')` leaves lowercase paths untouched.
- **iam README CI line** "GAP Integration job" — the CI job was renamed to `iam-integration-tests` by MONO-179; "IAM Integration job" matches reality.
- **Hub L114** is the only line carrying a non-rename stale fact (PORT_PREFIX retired + dead `tasks/ready/` link) — fixed in-line as reality-alignment since the rename touches it anyway.

# Failure Scenarios

- **Re-dating history** (e.g. admin-web 2026-05-18 retirement) → loss of audit trail. Only the project NAME aligns; dates byte-unchanged.
- **Blind lowercase `gap`→`iam` sweep** → would corrupt paths/anchors. Console-file lowercase edits are targeted to the domain-slug enumerations only.
- **Touching a link URL/anchor** → dead-ref. URLs/anchors already `iam-*`; the `GAP`→`IAM` uppercase edits don't match them.
