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
RUN pnpm install --frozen-lockfile

# ---- builder stage ----
FROM node:20-alpine AS builder
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate
WORKDIR /app

COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Build env — public vars must be present at build time if needed.
# Server-side secrets (OIDC_*) are injected at container runtime, not here.
ENV NEXT_TELEMETRY_DISABLED=1
ENV NODE_ENV=production
ENV NEXT_STANDALONE=1

RUN pnpm build

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
