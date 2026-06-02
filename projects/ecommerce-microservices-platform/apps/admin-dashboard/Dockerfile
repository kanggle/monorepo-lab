# syntax=docker/dockerfile:1
FROM node:20-alpine AS base

FROM base AS builder
RUN apk add --no-cache libc6-compat
WORKDIR /app
RUN corepack enable pnpm

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml turbo.json ./
COPY apps/admin-dashboard/package.json apps/admin-dashboard/
COPY apps/web-store/package.json apps/web-store/
COPY packages/ packages/
# pnpm store cache mount — persists the content-addressable store across builds
# so a lockfile change (deps-layer cache miss) re-links from cache instead of
# re-downloading. No effect on the image (store lives only in the mount).
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY apps/admin-dashboard/ apps/admin-dashboard/
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_USE_MOCK
ENV NEXT_PUBLIC_USE_MOCK=$NEXT_PUBLIC_USE_MOCK
# .next/cache mount — persists Next.js's incremental webpack/SWC compile cache
# across builds. The `COPY apps/admin-dashboard/` layer is invalidated on every
# source change, so without this `pnpm build` always recompiles cold; the mount
# turns source-only rebuilds into incremental compiles. NOT copied into the
# runner image (only .next/standalone + .next/static are), so size is unchanged.
RUN --mount=type=cache,target=/app/apps/admin-dashboard/.next/cache \
    pnpm --filter admin-dashboard build
RUN mkdir -p /app/apps/admin-dashboard/public

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder /app/apps/admin-dashboard/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/apps/admin-dashboard/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/apps/admin-dashboard/.next/static ./apps/admin-dashboard/.next/static

USER nextjs
EXPOSE 3001
ENV PORT=3001
ENV HOSTNAME="0.0.0.0"
CMD ["node", "apps/admin-dashboard/server.js"]
