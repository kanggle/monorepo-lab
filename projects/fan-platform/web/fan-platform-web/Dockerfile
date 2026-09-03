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
# TASK-MONO-563: the build now writes `public/build-info.json`, so `public/` exists
# after every build. The mkdir stays as a belt — if the generator is ever removed
# the runner's COPY must not start failing instead of saying what changed.
# NOTE the value it writes HERE is `commit=unknown`: this build context has no
# `.git` and no `VERCEL_GIT_*`. That is deliberate — the freshness judge reads
# `unknown` as **undecidable**, never as fresh. An absent file and a file that
# says "I do not know" are different states.
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
