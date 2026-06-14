# syntax=docker/dockerfile:1
FROM node:20-alpine AS base

FROM base AS builder
RUN apk add --no-cache libc6-compat
WORKDIR /app
RUN corepack enable pnpm

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml turbo.json ./
COPY apps/web-store/package.json apps/web-store/
COPY packages/ packages/
# pnpm store cache mount — persists the content-addressable store across builds
# so a lockfile change (deps-layer cache miss) re-links from cache instead of
# re-downloading. No effect on the image (store lives only in the mount).
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

COPY apps/web-store/ apps/web-store/
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ARG NEXT_PUBLIC_TOSS_CLIENT_KEY
ENV NEXT_PUBLIC_TOSS_CLIENT_KEY=$NEXT_PUBLIC_TOSS_CLIENT_KEY
# .next/cache mount — persists Next.js's incremental webpack/SWC compile cache
# across builds. The `COPY apps/web-store/` layer is invalidated on every source
# change, so without this `pnpm build` always recompiles cold; the mount turns
# source-only rebuilds into incremental compiles. NOT copied into the runner
# image (only .next/standalone + .next/static are), so image size is unchanged.
RUN --mount=type=cache,target=/app/apps/web-store/.next/cache \
    pnpm --filter web-store build
RUN mkdir -p /app/apps/web-store/public

FROM base AS runner
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=builder /app/apps/web-store/public ./public
COPY --from=builder --chown=nextjs:nodejs /app/apps/web-store/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/apps/web-store/.next/static ./apps/web-store/.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME="0.0.0.0"
CMD ["node", "apps/web-store/server.js"]
