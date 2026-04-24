FROM node:20-alpine AS base

FROM base AS builder
RUN apk add --no-cache libc6-compat
WORKDIR /app
RUN corepack enable pnpm

COPY package.json pnpm-lock.yaml pnpm-workspace.yaml turbo.json ./
COPY apps/web-store/package.json apps/web-store/
COPY apps/admin-dashboard/package.json apps/admin-dashboard/
COPY packages/ packages/
RUN pnpm install --frozen-lockfile

COPY apps/web-store/ apps/web-store/
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
RUN pnpm --filter web-store build
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
