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
# 🔴🔴 TASK-MONO-615 — `@demo/backend-resolver` 는 이 빌드 컨텍스트 **밖**에 있다
# -----------------------------------------------------------------------------
# 이 앱의 package.json 은 `"@demo/backend-resolver": "link:../../../../infra/demo/
# backend-resolver"` 다(TASK-MONO-614 / ADR-MONO-068 § D6 = B2 — 구현은 저장소에
# **하나뿐**이고 그 자리는 저장소 루트의 `infra/demo/` 다). 그런데 이 이미지의 빌드
# 컨텍스트는 이 프로젝트 디렉터리이므로 그 경로는 컨텍스트에 **없다**.
#
# 🔴 그리고 그 결핍은 install 에서 안 터진다. `pnpm install --frozen-lockfile` 은
#    **성공한다**(실측: 576 resolved, Done in 13.3s) — `link:` 는 대상이 없어도
#    심링크를 만들고 끝나기 때문이다. 죽는 것은 그 다음 `next build` 이고, 메시지는
#    *"Module not found: Can't resolve '@demo/backend-resolver'"* 다.
#    ⇒ **「install 이 통과했다」는 워크스페이스가 온전하다는 증거가 아니다.**
#
# 왜 AMI 굽기에서야 드러났나 (2026-09-03 실측):
#   · CI 는 `pnpm --filter <app> build` 를 **러너에서** 돈다(저장소 루트라 링크 해소됨).
#     **이 이미지를 굽는 CI 잡은 없다.**
#   · 형제 web-store 는 데모에서 억제돼 있어(ADR-MONO-067 단계 2) 데모 굽기에서
#     이미지가 안 만들어지고, Vercel 은 저장소 루트에서 빌드한다.
#   ⇒ 컨테이너 안에서 이 패키지를 빌드하는 자리는 **AMI 굽기 하나뿐**이었고,
#     `link:` 가 들어온 `c2df17060`(#3586) 이후 아무도 굽지 않아 잠복해 있었다.
#     가드 (z26)이 이 축을 정적으로 문다.
#
# 🔵 목적지가 `/infra/...` 인 것은 임의가 아니라 **lockfile 이 정한다.** pnpm 은
#    `link:` 를 선언한 package.json 의 디렉터리 기준으로 푼다:
#      /app/<app-dir> → .. → .. → .. → ..  ⇒ `/`   (루트의 부모는 루트)
#    따라서 `../../../../infra/demo/backend-resolver` = `/infra/demo/backend-resolver`.
#    저장소에서는 같은 상대경로가 저장소 루트를 가리킨다 — 깊이가 일치한다.
# 🔵 러너 이미지에는 안 들어간다. `next.config.ts` 의 `transpilePackages` 가 이
#    패키지의 TS 소스를 번들에 컴파일해 넣으므로 런타임에는 필요 없다.
COPY --from=demo-backend-resolver . /infra/demo/backend-resolver
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
