# CI handoff — GAP-backed web-store logout e2e (TASK-INT-023)

`.github/workflows/` edits are **classifier-blocked for the AI agent**, so the CI
job that runs the RP-initiated logout AC-1 spec against a real GAP is provided
here for a maintainer to apply by hand. Everything else (compose, seed, helper,
spec, config) is already merged and the spec is **gated by `SKIP_GAP_E2E`** — so
until this job is added, the existing nightly run simply skips it (no breakage).

The spec was already verified locally against a real GAP: `1 passed (51.0s)`.

## Add to `.github/workflows/nightly-e2e.yml`

A new job (sibling of `frontend-e2e-fullstack`). It needs the GAP `auth-service`
bootJar, the lean GAP stack, the consumer seed, and the `/etc/hosts` issuer entry
(the PC-FE-028 constraint — the browser AND the Next.js server must resolve
`auth-service`).

```yaml
  web-store-iam-logout-e2e:
    name: Web-store GAP logout e2e (RP-initiated end_session, real GAP)
    runs-on: ubuntu-latest
    needs: changes            # reuse the existing path-filter gate
    timeout-minutes: 30
    # if: needs.changes.outputs.<ecommerce-or-iam-filter> == 'true'
    defaults:
      run:
        working-directory: projects/ecommerce-microservices-platform
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }

      # Build the GAP auth-service bootJar (the iam-e2e Dockerfile COPYs it).
      - name: Build auth-service bootJar
        working-directory: projects/iam-platform
        run: ./gradlew :apps:auth-service:bootJar --no-daemon

      - name: Issuer hostname resolves on the host (browser + Next.js server)
        run: echo "127.0.0.1 auth-service" | sudo tee -a /etc/hosts

      - name: Boot lean GAP stack
        run: docker compose -f docker-compose.iam-e2e.yml up -d --build

      - name: Wait for auth-service healthy
        run: |
          for i in $(seq 1 40); do
            s=$(docker inspect -f '{{.State.Health.Status}}' \
                 $(docker compose -f docker-compose.iam-e2e.yml ps -q auth-service))
            [ "$s" = "healthy" ] && exit 0
            sleep 5
          done
          docker compose -f docker-compose.iam-e2e.yml logs auth-service
          exit 1

      - name: Seed CONSUMER credential
        run: |
          docker compose -f docker-compose.iam-e2e.yml exec -T mysql \
            mysql -uroot -prootpass \
            < apps/web-store/e2e/fixtures/iam-consumer-seed.sql

      - uses: pnpm/action-setup@v4
      - uses: actions/setup-node@v4
        with: { node-version: '22', cache: 'pnpm' }
      - run: pnpm install --frozen-lockfile
      - run: pnpm --filter web-store exec playwright install --with-deps chromium

      - name: Run RP-initiated logout spec
        working-directory: projects/ecommerce-microservices-platform/apps/web-store
        env:
          CI: '1'                                  # makes playwright.config start the webServer
          SKIP_GAP_E2E: '0'                        # un-skip the GAP spec
          OIDC_ISSUER_URL: http://auth-service:8081
          ECOMMERCE_WEB_STORE_CLIENT_ID: ecommerce-web-store-client
          ECOMMERCE_WEB_STORE_CLIENT_SECRET: ecommerce-dev
          NEXTAUTH_SECRET: ci-iam-e2e-secret
          NEXTAUTH_URL: http://localhost:3001
          AUTH_TRUST_HOST: 'true'
        # NOTE: playwright.config webServer runs `pnpm start` (needs a build).
        # Add `pnpm --filter web-store build` before this, OR switch the webServer
        # command to `pnpm dev` for this job. (Local verification used `next dev`
        # because Next `output: 'standalone'` build needs symlink perms on Windows;
        # the Linux CI runner builds fine.)
        run: pnpm --filter web-store exec playwright test rp-initiated-logout.spec.ts

      - name: Tear down
        if: always()
        run: docker compose -f docker-compose.iam-e2e.yml down -v
```

## Notes

- The job is **additive**; it does not touch the existing `frontend-e2e-fullstack`
  job (which stays `SKIP_GAP_E2E=1`).
- The lean stack has **no account-service** — login works off `auth_db.credentials`
  alone (the GAP token pipeline emits no `account_type` claim, which web-store's
  `signIn` accepts).
- To later un-skip the 4 CRUD specs (`golden-flow`/`cart`/`wishlist`/`account-type-guard`)
  against GAP, they'd also need the full ecommerce backend (gateway + producers) —
  out of scope for this logout-focused job.
