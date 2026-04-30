# Multi-stage build for Next.js apps (standalone output).
# Replace {app} with the app module name.

FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/{app}/package.json apps/{app}/
COPY packages/*/package.json packages/*/
RUN corepack enable && pnpm install --frozen-lockfile

FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
RUN pnpm --filter {app} build

FROM node:20-alpine
WORKDIR /app
COPY --from=builder /app/apps/{app}/.next/standalone ./
COPY --from=builder /app/apps/{app}/.next/static ./apps/{app}/.next/static
COPY --from=builder /app/apps/{app}/public ./apps/{app}/public
EXPOSE 3000
CMD ["node", "apps/{app}/server.js"]
