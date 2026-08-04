# syntax=docker/dockerfile:1
# =============================================================================
# fan-platform-web — production container for the integrated demo (TASK-FAN-FE-014)
# =============================================================================
# Build context is `projects/fan-platform` (the pnpm workspace root — that is
# where package.json / pnpm-lock.yaml / pnpm-workspace.yaml live). Building from
# `web/fan-platform-web` instead would leave the lockfile outside the context and
# `--frozen-lockfile` would fail.
#
# Modelled on `apps/web-store/Dockerfile` (ecommerce) but NOT copied — three
# things differ and each one matters:
#
#   1. Workspace shape. ecommerce is `apps/*` + `packages/*` + turbo; fan-platform
#      is `web/*` only, no shared packages and no turbo.json. So there is no
#      `COPY packages/` layer and the filter is `--filter fan-platform-web`.
#   2. Build-time public env. web-store bakes NEXT_PUBLIC_API_URL / the Toss
#      client key in as build args. This image bakes NOTHING host-shaped —
#      see the note below; it is the reason AC-7 holds for the AMI.
#   3. Standalone output is opt-in here (`NEXT_OUTPUT_STANDALONE=1`), not the
#      app's default — see next.config.ts for why.
#
# Why no NEXT_PUBLIC_* build args
# -----------------------------------------------------------------------------
# `NEXT_PUBLIC_*` is inlined at BUILD time, and the demo AMI is pre-baked while
# DEMO_DOMAIN is still unknown (it is derived from the instance IP at boot). Any
# hostname baked here would be frozen to whatever the bake host was called.
#
# We can avoid it entirely because this app never fetches the gateway from the
# browser: `src/shared/api/client.ts` is server-only by construction ("Browser-
# side fetches are intentionally not implemented in this module") and reads
# `env.gatewayInternalUrl` — i.e. `GATEWAY_URL_INTERNAL`, a RUNTIME env. The
# public-facing value the app does need (its own origin, for OIDC callbacks) is
# `NEXTAUTH_URL`, also read at runtime.
#
# Result: this image is host-agnostic. The same layer serves `web.fan-platform.local`
# and `web.fan-platform.<ip>.sslip.io` with no rebuild.
# =============================================================================
FROM node:20-alpine AS base

FROM base AS builder
RUN apk add --no-cache libc6-compat
WORKDIR /app
RUN corepack enable pnpm

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY web/fan-platform-web/package.json web/fan-platform-web/
# pnpm store cache mount — persists the content-addressable store across builds
# so a lockfile change (deps-layer cache miss) re-links from cache instead of
# re-downloading. No effect on the image (the store lives only in the mount).
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY web/fan-platform-web/ web/fan-platform-web/
# Opt into `output: 'standalone'` for this build only. The app's default stays
# off because the standalone file-tracing copier needs elevated symlink
# privileges on Windows, where several developers on this repo build. Inside
# this image the build is Linux, so the constraint does not apply.
ENV NEXT_OUTPUT_STANDALONE=1
# .next/cache mount — persists Next's incremental compile cache. The
# `COPY web/fan-platform-web/` layer is invalidated on every source change, so
# without this every rebuild compiles cold. NOT copied into the runner image.
RUN --mount=type=cache,target=/app/web/fan-platform-web/.next/cache \
    pnpm --filter fan-platform-web build
# The app ships no `public/` directory today. Create it so the runner's COPY has
# a source either way, and so adding one later needs no Dockerfile change.
RUN mkdir -p /app/web/fan-platform-web/public

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder /app/web/fan-platform-web/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/web/fan-platform-web/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/web/fan-platform-web/.next/static ./web/fan-platform-web/.next/static

USER nextjs
EXPOSE 3000
# 3000, not the 3002 in package.json. That 3002 is the HOST-side dev convention
# (`next dev --port 3002`, and the localhost:3002 callback IAM registers in
# V0028) and is meaningless here: the container publishes no host port and is
# reached only through Traefik by hostname.
ENV PORT=3000
# Binds Node to IPv4 only. Guard (j) in infra/demo/verify-demo-wrapper.sh exists
# because of this pair: with HOSTNAME=0.0.0.0 the server is IPv4-only, alpine
# maps `localhost` to both 127.0.0.1 and ::1, and busybox wget picks ::1 and
# does NOT fall back — so a healthcheck against `localhost` fails while the app
# is fine, and Traefik SILENTLY skips non-healthy containers. The compose
# healthcheck therefore uses 127.0.0.1 explicitly.
ENV HOSTNAME="0.0.0.0"
CMD ["node", "web/fan-platform-web/server.js"]
