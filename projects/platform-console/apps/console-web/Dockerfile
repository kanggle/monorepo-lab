# syntax=docker/dockerfile:1
# Multi-stage Next.js standalone production image.
# Mirrors the standard Next.js standalone pattern; admin-web has no Dockerfile
# so this follows the official Next.js Docker best-practice for standalone output.
#
# Requirements:
#   - next.config.mjs must declare output: 'standalone'
#   - .dockerignore should exclude node_modules and .next

# ---- deps stage ----
FROM node:20-alpine AS deps
RUN apk add --no-cache libc6-compat
WORKDIR /app

# Install pnpm — pin to the host workflow's `pnpm/action-setup@v4 with
# version: '9.15.0'` to avoid the unpinned upstream channel resolving to a
# release that requires a newer Node major than `node:20-alpine` ships
# (TASK-PC-FE-025; pnpm 11.2.2 required Node v22.13+ and crashed corepack
# at build time). Future intentional bumps must update both this Dockerfile
# and `.github/workflows/nightly-e2e.yml` together.
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate

COPY package.json pnpm-lock.yaml* ./

# 🔴🔴 TASK-MONO-585 — `@demo/backend-resolver` is OUTSIDE this build context
# -----------------------------------------------------------------------------
# This app's package.json declares `"@demo/backend-resolver":
# "link:../../../../infra/demo/backend-resolver"` (TASK-MONO-614 /
# ADR-MONO-068 D6 = B2 — there is exactly ONE implementation in this repository
# and its home is the repo root under `infra/demo/`). This image's build context
# is `apps/console-web`, so that path is NOT in the context.
#
# 🔴 The gap does NOT surface at install. `pnpm install --frozen-lockfile`
#    SUCCEEDS: `link:` creates the symlink whether or not the target exists.
#    What dies is the following `next build`, with
#    *"Module not found: Can't resolve '@demo/backend-resolver'"*.
#    ⇒ "install went green" is NOT evidence that the workspace is intact.
#    Both siblings (fan-platform-web, web-store) were bitten by exactly this in
#    TASK-MONO-615 — 10 minutes into an AMI bake, an hour-long feedback loop.
#    Guard (z26) in `infra/demo/verify-demo-wrapper.sh` bites it statically now.
#
# 🔵 The destination is not arbitrary — the LOCKFILE decides it. pnpm resolves
#    `link:` relative to the declaring package.json's directory:
#      /app -> .. -> .. -> .. -> ..  =  `/`   (the root's parent is the root)
#    so `../../../../infra/demo/backend-resolver` = `/infra/demo/backend-resolver`.
#    In the repo the same relative path lands on the repo root — depths match.
#
# 🔴 It is copied into BOTH stages, and that is not redundancy. `deps` needs it
#    so `pnpm install` links onto something real; `builder` needs it because the
#    builder inherits `node_modules` by `COPY --from=deps`, which copies the
#    SYMLINK — and a symlink whose target is absent in this stage is exactly the
#    dangling link that kills `next build`. The sibling apps install and build in
#    ONE stage, so their single copy is not a precedent for a single copy here.
#
# 🔵 It does not reach the runner image: `next.config.mjs` `transpilePackages`
#    compiles this package's TS source into the bundle, so nothing needs it at
#    runtime.
COPY --from=demo-backend-resolver . /infra/demo/backend-resolver
# pnpm store cache mount — persists the content-addressable package store across
# builds so a lockfile change (deps-layer cache miss) re-links from cache instead
# of re-downloading. No effect on the image (store lives only in the mount).
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

# ---- builder stage ----
FROM node:20-alpine AS builder
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate
WORKDIR /app

# 🔴 TASK-MONO-585 — see the deps stage: this stage inherits the dangling
#    `node_modules/@demo/backend-resolver` symlink, so the target must exist
#    HERE too or `pnpm build` below dies with "Module not found".
COPY --from=demo-backend-resolver . /infra/demo/backend-resolver
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build env — public vars must be present at build time if needed.
# Server-side secrets (OIDC_*) are injected at container runtime, not here.
ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production
ENV NEXT_STANDALONE=1

# .next/cache mount — persists Next.js's incremental webpack/SWC compile cache
# across builds. The `COPY . .` layer is invalidated on every source change, so
# without this `pnpm build` always recompiles cold; the mount turns source-only
# rebuilds into incremental compiles. NOT copied into the runner image (only
# .next/standalone + .next/static are), so image size is unchanged.
RUN --mount=type=cache,target=/app/.next/cache \
    pnpm build

# ---- runner stage ----
FROM node:20-alpine AS runner
WORKDIR /app

ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production

RUN addgroup --system --gid 1001 nodejs \
 && adduser  --system --uid 1001 nextjs

# Copy standalone output
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static
COPY --from=builder --chown=nextjs:nodejs /app/public ./public

USER nextjs

EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

CMD ["node", "server.js"]
