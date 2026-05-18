# Task ID

TASK-BE-302

# Title

GAP architecture.md Identity-table "Data store" drift fix — PostgreSQL → MySQL across 6 services (spec-only, reality-alignment)

# Status

review

# Owner

backend

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

- **depends on**: nothing (self-contained GAP spec correction).
- **origin**: surfaced by `project_refactor_sweep_status` (BE-295 + BE-300 비차단 관찰 (c)) as a pre-existing genuine spec-drift; verified GAP-wide during the BE-301 follow-up scan.
- **prerequisite for**: nothing.
- **spec-first / spec-only**: this **is** the spec change. Zero production code / contract / schema / event / test. No ADR — see § Decision authority.

---

# Goal

Every GAP backend service that owns persistence declares `| Data store | PostgreSQL (…) |` on line 21 of its `specs/services/<svc>/architecture.md` **Identity** table, but the entire GAP platform runs on **MySQL** — there is **zero PostgreSQL** anywhere in GAP runtime config, dependencies, or migrations. This is a stale pre-import-era boilerplate artifact (GAP was templated before its MySQL reality solidified) that was never corrected. It is internally self-contradicting (auth-service's own architecture.md § 퍼시스턴스 L206 + test-strategy rows L214-215 already say MySQL).

Correct the **engine word only** (`PostgreSQL` → `MySQL`) in the Identity-table `Data store` cell of the 6 affected services, preserving each service's parenthetical context verbatim and the ADR-MONO-012 canonical architecture.md form. `gateway-service` is correct (`none (stateless)`) and is untouched.

# Decision authority (why MySQL, why no ADR / decision-gate)

This is a **factual reality-alignment**, not a convention choice — so it follows the BE-290 G7 / BE-294 discipline ("live merged code + higher-layer SoT beat a stale service-spec line"), and requires **no ADR and no decision-gate**:

- **Live merged code (authoritative)**: every owning service's `application.yml` = `jdbc:mysql://…/<svc>_db`; every Flyway migration set is MySQL DDL (`db/migration/V0001…`); `grep -ril postgres` across all GAP `apps/*/src/main/resources/application*.yml` + `docker-compose*.yml` = **0**.
- **Dependency SoT**: `account/admin/auth/security` `dependencies.md` declare `### MySQL`; `community/membership` `application.yml` = `jdbc:mysql://…community_db`/`…membership_db` and `membership-service/dependencies.md` explicitly lists "MySQL 장애" failure mode.
- **Same-document corroboration**: `auth-service/architecture.md` L206 § 퍼시스턴스 = "**MySQL** — credentials, refresh_tokens, …"; L214-215 test rows = "Testcontainers (MySQL…)". Project README Tech Stack = "Database | MySQL 8.0, Redis 7".

The architecture.md:21 `PostgreSQL` is the sole outlier vs. an unbroken MySQL chain → corrected to fact. Per BE-290/BE-294: a reality-alignment that has no competing convention does **not** open an ADR (ADR is for normalize/hoist decisions, not for fixing a line to match what the code already does).

# Scope

## In Scope

Edit exactly line 21 (`| Data store | … |`) of each, **changing only the leading engine word `PostgreSQL` → `MySQL`** and keeping the `(…)` parenthetical byte-identical:

- `projects/global-account-platform/specs/services/account-service/architecture.md` — `PostgreSQL (owned, not shared)` → `MySQL (owned, not shared)`
- `projects/global-account-platform/specs/services/admin-service/architecture.md` — `PostgreSQL (감사 로그만, downstream 도메인 상태 미보유)` → `MySQL (…)`
- `projects/global-account-platform/specs/services/auth-service/architecture.md` — `PostgreSQL (credentials + refresh tokens + login history + JPA OAuth2 영속화)` → `MySQL (…)`
- `projects/global-account-platform/specs/services/community-service/architecture.md` — `PostgreSQL (owned)` → `MySQL (owned)`
- `projects/global-account-platform/specs/services/membership-service/architecture.md` — `PostgreSQL (owned)` → `MySQL (owned)`
- `projects/global-account-platform/specs/services/security-service/architecture.md` — `PostgreSQL (security history + detection state)` → `MySQL (…)`

## Out of Scope

- `gateway-service/architecture.md` — `Data store | none (stateless)` is correct; untouched.
- Any other line/section of any architecture.md (Identity table structure, `### Service Type Composition` H3, References tail, § 퍼시스턴스, test rows — all already correct / untouched). ADR-MONO-012 canonical form is preserved (a single table-cell value changes; no structural edit).
- `dependencies.md` / `data-model.md` / `overview.md` — already correct (MySQL); not touched.
- Any production code / `application.yml` / Flyway / contract / event — none (this is purely the stale doc cell).
- An ADR — explicitly not warranted (§ Decision authority).
- WMS / ecommerce / scm / fan / platform-console — out of GAP scope; not assessed here.

# Acceptance Criteria

- **AC-1**: `grep -rn "PostgreSQL" projects/global-account-platform/specs/services/*/architecture.md` → **0** matches (every Identity `Data store` now `MySQL`; gateway unaffected — it never said PostgreSQL).
- **AC-2 (surgical)**: each of the 6 files' diff = exactly **1 changed line** (line 21), only the token `PostgreSQL`→`MySQL`; the `(…)` parenthetical and the rest of the Identity table byte-identical. No other line in any file changes.
- **AC-3 (canonical form intact)**: each architecture.md still has its `## Identity` table + `### Service Type Composition` H3 + `## References` tail (ADR-MONO-012). `grep -c "^## Identity$"` unchanged; no HARDSTOP-10 hook regression.
- **AC-4 (reality-consistent)**: post-fix, each service's architecture.md `Data store` cell agrees with its `application.yml` (`jdbc:mysql`), Flyway dialect, and `dependencies.md`. Repo-wide GAP `grep -ril postgres` in runtime config stays 0 (no new contradiction introduced).
- **AC-5 (no behavioral surface)**: zero production code / contract / schema / event / test changed; `git diff --stat` = 6 markdown files, 6 lines.
- **AC-6 (CI)**: doc-only change → CI `changes` fast lane (markdown path-filter); no build/test job needed (and none would be affected). Self-review APPROVED.

# Related Specs

- 6 × `projects/global-account-platform/specs/services/<svc>/architecture.md` (Identity table, line 21) — the edited files.
- `projects/global-account-platform/specs/services/auth-service/architecture.md` § 퍼시스턴스 (L206) — the in-document MySQL corroboration the fix aligns line 21 to.
- `project_refactor_sweep_status` (memory) — BE-295/BE-300 비차단 관찰 (c) recorded this as a genuine pre-existing spec-drift candidate.

# Related Contracts

- None. No HTTP/event contract references a database engine; this cell is operator/onboarding documentation only. Invisible across every service boundary.

# Edge Cases

- **Parenthetical preservation**: the `(…)` after the engine word differs per service and is otherwise accurate (owned / 감사 로그만 / credentials… / security history…); only the first token changes. A blunt `sed s/PostgreSQL/MySQL/` is acceptable *because* PostgreSQL appears nowhere else in these files (AC-1 confirms 0 remaining) — but the edit must not alter the parenthetical text.
- **gateway-service**: must NOT be edited (stateless, correct). The grep scope check (AC-1) naturally excludes it (it has no `PostgreSQL`).
- **community/membership are FROZEN product-layer demo consumers**: this is a spec-accuracy doc fix, not a feature/scope change — permitted (no new task/feature created; PROJECT.md frozen-scope rule is about new functionality, not correcting a wrong DB declaration).
- **Self-contradiction already present**: auth-service architecture.md said both PostgreSQL (L21) and MySQL (L206) — the fix removes the contradiction, it does not introduce risk.

# Failure Scenarios

- **An edit also alters the parenthetical or another line** → AC-2 (1-line, token-only diff per file) rejects it on review.
- **gateway-service accidentally edited** → AC-1/AC-2 + the explicit Out-of-Scope; gateway has no PostgreSQL token so any change there is a finding.
- **A service genuinely uses Postgres (false assumption)** → disproven: AC-4 + the § Decision-authority evidence (every owning service `jdbc:mysql` + Flyway MySQL + zero `postgres` in GAP runtime). No service is misclassified.
- **Treated as needing an ADR / decision-gate (over-process)** → § Decision authority: reality-alignment with no competing convention is not an ADR trigger (BE-290/BE-294 precedent). Over-gating would be the error.

# Verification

1. `grep -rn "PostgreSQL" projects/global-account-platform/specs/services/*/architecture.md` → 0 (AC-1).
2. `git diff --stat` → 6 files, 6 insertions(+) / 6 deletions(-) (one line each) (AC-2/AC-5).
3. `git diff` per file → only line 21 token `PostgreSQL`→`MySQL`, parenthetical byte-identical (AC-2).
4. `grep -c "^## Identity$"` per file unchanged; HARDSTOP-10 hook PASS on commit (AC-3).
5. `grep -ril postgres projects/global-account-platform/apps/*/src/main/resources/application*.y*ml projects/global-account-platform/docker-compose*.yml` → 0 (AC-4, no contradiction remains).
6. CI: `changes` markdown fast lane only; no code job triggered (AC-6).

분석=Opus 4.7 / 구현 권장=Sonnet 4.6 (mechanical 6-cell token correction + grep verification; spec-only, reality-aligned, no judgement beyond the already-decided MySQL fact) — executed directly this session given size / 리뷰=Opus 4.7 (inline self-review; surgical-diff + decision-authority discipline).
