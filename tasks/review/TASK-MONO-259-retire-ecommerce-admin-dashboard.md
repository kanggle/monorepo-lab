# TASK-MONO-259 — retire ecommerce admin-dashboard app (ADR-031 Phase 6)

**Status:** review
**Level:** monorepo-level (root) — touches root `package.json`, root `docs/`, ecommerce app/specs/docs/infra.
**Parent:** ADR-MONO-031 Phase 6 (admin-dashboard app deletion, D7 parity gate — all 6 operator areas absorbed into platform-console: products·orders·image·users·promotions·shippings·notifications).

## Goal

Delete the standalone ecommerce `admin-dashboard` Next.js app and scrub every dangling reference so CI stays
GREEN. The 6 operator areas it carried are now fully absorbed into platform-console (Phases 1–5 done), so the
D7 parity gate is satisfied. **One atomic PR.** ADRs and portfolio case-studies are immutable historical record
and are LEFT untouched (they record valid past decisions).

**Standalone portfolio impact (accepted):** the dual-published ecommerce standalone repo loses its operator UI;
it becomes backend + web-store only. This was explicitly accepted by the user (ADR-031 §"포트폴리오 영향").
README gets a note.

## Scope — atomic deletion checklist

### 1. Delete the app directory (whole)
- `projects/ecommerce-microservices-platform/apps/admin-dashboard/` — `git rm -r` the entire directory
  (includes its `Dockerfile`, `package.json`, `src/`, `e2e/`, `.env.local.example`, all tests).

### 2. Regenerate the pnpm lockfile (MANDATORY — else `--frozen-lockfile` CI fails)
- After deleting the dir, run `pnpm install --lockfile-only` in `projects/ecommerce-microservices-platform/`
  to regenerate `pnpm-lock.yaml` without admin-dashboard. Commit the updated lockfile. (`--lockfile-only`
  avoids a full node_modules install — sufficient for CI's frozen-lockfile check.)

### 3. Delete k8s subtree (whole files)
- `projects/ecommerce-microservices-platform/k8s/services/admin-dashboard/{deployment,service,configmap,pdb}.yaml`
- `projects/ecommerce-microservices-platform/k8s/network-policies/admin-dashboard.yaml`

### 4. Edit-remove infra references
- **Root `package.json`** — remove the `"ecommerce:admin": "pnpm --dir ... --filter admin-dashboard dev"` script line.
- `projects/ecommerce-microservices-platform/docker-compose.yml` — remove the `admin-dashboard:` service stanza
  (the whole block, ~lines 1198–1234) + drop the admin-dashboard mention from the frontend comment (~1154–1159).
  Confirm no other service `depends_on: admin-dashboard`.
- `projects/ecommerce-microservices-platform/k8s/ingress/ingress.yaml` — remove `admin.example.com` from the TLS
  hosts list + remove the entire `- host: admin.example.com` rule block (points to admin-dashboard svc :3001).
- `projects/ecommerce-microservices-platform/apps/web-store/Dockerfile` — remove
  `COPY apps/admin-dashboard/package.json apps/admin-dashboard/` (would fail the build once the dir is gone).
- `projects/ecommerce-microservices-platform/apps/gateway-service/src/main/resources/application.yml` — change the
  hardcoded CORS fallback `http://localhost:3000,http://localhost:3001` → `http://localhost:3000` only.
- `projects/ecommerce-microservices-platform/scripts/e2e-healthcheck.sh` — remove the admin-dashboard healthcheck line (:3001).
- `projects/ecommerce-microservices-platform/.env.example` — remove admin-dashboard refs: the build-comment mentions,
  the `ECOMMERCE_ADMIN_DASHBOARD_CLIENT_SECRET` line + its comment, and `http://admin.ecommerce.local` from CORS_ALLOWED_ORIGINS.
- `projects/ecommerce-microservices-platform/.env` (committed dev env) — remove `:13001` from CORS_ALLOWED_ORIGINS,
  remove the `:13001/oauth/callback` from OAUTH_CALLBACK_ALLOWLIST, drop admin-dashboard from the port comment.

### 5. web-store runtime/code edits
- **`apps/web-store/src/features/auth/ui/LoginForm.tsx`** (RUNTIME — operator-rejected message): the current message
  tells operators to go to the admin dashboard. admin-dashboard no longer exists, and the web-store standalone has
  NO console either (console is hub-only). Make the message **generic** — drop the dead "admin dashboard 로 이동"
  redirect; e.g. `'operator 계정으로는 web-store 에 접근할 수 없습니다. 운영자 콘솔을 이용해 주세요.'` (no hardcoded
  URL — standalone has no console; hub operators use platform-console).
- `apps/web-store/src/shared/auth/auth.ts` — remove the "Same workaround as admin-dashboard's auth.ts" phrase from
  the comment (comment-only; no runtime dep).
- `apps/web-store/src/shared/config/__tests__/api.test.ts` — remove the admin-dashboard cross-reference comment (test logic unchanged).
- `apps/web-store/e2e/account-type-guard.spec.ts` — update the comments to drop the admin-dashboard mention and note
  operators use platform-console; the test logic (operator rejected from web-store) stays valid and unchanged.

### 6. Specs — RETIRED banner + cross-ref scrub
- `specs/services/admin-dashboard/architecture.md` + `overview.md` — **do NOT delete**; add a top banner:
  `> **RETIRED (ADR-MONO-031 Phase 6, TASK-MONO-259).** Absorbed into platform-console. See projects/platform-console/specs/contracts/console-integration-contract.md § 2.4.10.` (per ADR-031 D7).
- Cross-references → replace `admin-dashboard` with `platform-console` (keep the surrounding meaning):
  `specs/integration/iam-integration.md` (operator/OIDC-client/scope rows — note the OIDC client RETIRED),
  `specs/features/order-processing.md`, `specs/features/product-management.md`, `specs/features/user-management.md`,
  `specs/use-cases/signup-and-login.md` (remove admin-dashboard, keep web-store),
  `specs/contracts/events/user-events.md`, `specs/services/user-service/dependencies.md`,
  `specs/services/web-store/overview.md`, `specs/services/batch-worker/overview.md`.

### 7. Docs (root + project) — update, NOT the immutable ADRs
- `docs/project-overview.md` (root SoT) — remove admin-dashboard from the ecommerce Frontend cell; adjust the
  docker-cache note to web-store-only going forward.
- `projects/ecommerce-microservices-platform/README.md` — remove the admin-dashboard screenshot entry, the coverage-table
  row, the `admin.ecommerce.local → admin-dashboard` routing line, and the directory-tree entry. **LEAVE** the
  "Case Study — admin-dashboard 뮤테이션 훅 팩토리화" section (historical portfolio showcase). Add a short note that the
  operator UI is now in platform-console (hub).
- `projects/ecommerce-microservices-platform/docs/portfolio/portfolio.html` — remove the admin-dashboard coverage line +
  table row + screenshot `<img>`; **LEAVE** the case-study section.
- `projects/ecommerce-microservices-platform/docs/integration-verification.md` — remove the admin-dashboard verification section + the :3001 table row.
- `projects/ecommerce-microservices-platform/docs/screenshots/README.md` — remove the `05-admin-dashboard.png` row.
- `projects/ecommerce-microservices-platform/docs/guides/{contracts-guide.md, specs-guide.md}` — update the service-list tables / directory-tree examples to drop admin-dashboard.
- `projects/ecommerce-microservices-platform/k8s/services/auth-service-deprecated/README.md` — drop `and admin-dashboard` (keep web-store history).
- **LEAVE-AS-HISTORICAL** (do NOT edit): all `docs/adr/ADR-MONO-*` + `projects/.../docs/adr/ADR-00*` (immutable
  decision records), `docs/migration-notes.md`, `specs/services/auth-service-deprecated/README.md`.

### 8. PROJECT.md + portfolio sync
- `projects/ecommerce-microservices-platform/PROJECT.md` — remove `admin-dashboard` from the multi-tenant "in-migration / Out of Scope" service list.
- `scripts/sync-portfolio.sh` — remove the now-dead `apps/admin-dashboard/**` + `specs/services/admin-dashboard/architecture.md` entries from the ecommerce `PROJECT_EXCLUDE_PATHS` block (dead after deletion). Keep the `k8s/` whole-dir exclusion.

### Deferred follow-up (NOT in this PR)
- **OIDC client retire migration** — a new `V00NN__retire_ecommerce_admin_dashboard_client.sql` in
  `projects/iam-platform/apps/auth-service/src/main/resources/db/migration/` deleting `ecommerce-admin-dashboard-client`
  + `ecommerce.operator` scope. **Deferred** because a concurrent iam-platform session is actively adding auth-service
  Flyway migrations (V-number collision hazard); the dormant dev-only client causes zero runtime harm once the app is
  gone (broken-main is not created by deferring). File this as a follow-up task once the iam session settles. The
  existing V0012/V0016/V0024 client seeds are immutable — only a new forward migration removes it.

## Acceptance Criteria
- `apps/admin-dashboard/` directory and the k8s subtree fully removed; `pnpm-lock.yaml` regenerated (no admin-dashboard).
- A repo-wide grep for `admin-dashboard` returns ONLY the intentional LEAVE-AS-HISTORICAL hits (ADRs, the two case-study
  sections, `migration-notes.md`, `auth-service-deprecated` specs/readme, and the new RETIRED banners). No infra/build/CI/docker/k8s/spec-table/active-doc hit remains.
- `docker compose -f projects/ecommerce-microservices-platform/docker-compose.yml config` parses (no admin-dashboard service, no dangling depends_on).
- web-store builds/lints clean (the Dockerfile COPY + LoginForm + comment edits don't break it). If a console-web/web-store
  tsc/lint is cheaply runnable, run it; otherwise rely on CI.
- The RETIRED banner is present on both admin-dashboard spec files (files retained).
- ADRs + case-study sections untouched.

## Related Specs / ADRs
- `docs/adr/ADR-MONO-031-ecommerce-operator-ui-console-consolidation.md` (D7 parity gate; LEAVE — may note Phase 6 complete in the close-chore/memory, not by editing the ADR).
- `projects/platform-console/specs/contracts/console-integration-contract.md` §2.4.10.1–.4 (the absorbed surfaces — the RETIRED banner points here).

## Edge Cases
- A leftover `COPY apps/admin-dashboard/...` in web-store Dockerfile → Docker build fails. Must be removed (step 5).
- Frozen lockfile mismatch in CI frontend jobs if `pnpm-lock.yaml` not regenerated (step 2).
- web-store standalone has no console → the LoginForm message must not hardcode a console URL that 404s in standalone.

## Failure Scenarios
- Deleting the app dir without regenerating the lockfile → `frontend-unit-tests` + `frontend-checks` CI RED (frozen-lockfile).
- Editing an immutable ADR / removing a case-study → portfolio/history damage. Keep them.
- Editing V0012/V0016/V0024 iam migrations in place → Flyway checksum mismatch on every existing deployment. Never edit applied migrations.
