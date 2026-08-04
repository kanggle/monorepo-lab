# fan-platform

> **K-pop 류 아티스트↔팬 커뮤니티** 백엔드 + Next.js 프론트엔드. Weverse-style.
> Built with Claude Code · spec-driven · IAM OIDC consumer · Traefik hostname routing

[![CI](https://github.com/kanggle/fan-platform/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kanggle/fan-platform/actions/workflows/ci.yml?query=branch%3Amain)
[![Java 21](https://img.shields.io/badge/java-21-007396)](https://adoptium.net/)
[![Spring Boot 3.4](https://img.shields.io/badge/spring--boot-3.4-6DB33F)](https://spring.io/projects/spring-boot)
[![Next.js 15](https://img.shields.io/badge/next.js-15-000000)](https://nextjs.org/)

## What this is

AI-assisted 풀스택 포트폴리오 — 엔터테인먼트 회사 (HYBE / SM / YG 류) 채용 시그널 + 일반 IT 사회·콘텐츠 도메인 시그널을 동시에 보여주는 데모.

도메인은 K-pop 으로 구체화했으나, 다음 엔지니어링 패턴은 모든 creator-community / 사회적 콘텐츠 플랫폼에 동일하게 적용됨:

- **Event-driven 아키텍처** — Kafka outbox + audit trail
- **OIDC IdP 통합** — IAM 를 OAuth2 Resource Server 로 소비 (RS256 + `tenant_id` claim)
- **multi-tenant 격리** — row-level `tenant_id=fan-platform`, 향후 팬덤 추가 시 인프라 보존
- **content-heavy** — 미디어 스토리지 분리 (MinIO + CDN), 검색 인덱싱, 멀티계층 캐시
- **fail-closed 멤버십 접근 제어** — 멤버십 서비스 503 시 보수적 거부

## Status

✅ **v1.1 — 5 backend services + web, 전부 구현·배포됨**

`gateway` · `community` (post / comment / reaction / follow feed) · `artist` (profile + fandom) · `membership` (subscription state machine + PG mock + outbox) · `notification` (membership events → per-fan inbox) 5 개 백엔드 서비스와 Next.js 15 프론트엔드까지 모두 구현 완료. 자세한 service map 은 [PROJECT.md](PROJECT.md#service-map-v1--v2).

## Quick Start

```bash
# 1. 한 번만 실행 (모노레포 루트에서) — *.local 호스트네임 등록 + Traefik 기동
bash scripts/dev-setup.sh           # Linux/macOS
.\scripts\dev-setup.ps1              # Windows (Admin)
pnpm traefik:up

# 2. fan-platform 기동 (서비스 부트스트랩 후)
pnpm fan-platform:up

# 3. 브라우저 접속
open http://fan-platform.local/
```

## Architecture

### Service map (v1.1 — 5 backend + web)

```
                       ┌──────────────────────┐
                       │  Traefik (host :80)  │
                       └──────────┬───────────┘
                                  │ Host: fan-platform.local
                       ┌──────────▼───────────┐
                       │   gateway-service    │ ← OIDC token 검증, tenant gate
                       └──────┬───────────────┘
                              │
                ┌─────────────┼─────────────┬─────────────┐
                ▼             ▼             ▼             ▼
        ┌──────────────┐ ┌──────────┐ ┌────────────┐ ┌────────────┐
        │  community-  │ │ artist-  │ │ membership-│ │notification│
        │   service    │ │ service  │ │  service   │ │  -service  │
        └──────┬───────┘ └────┬─────┘ └─────┬──────┘ └─────┬──────┘
               │              │             │              │
               └──────┬───────┴─────────────┴──────────────┘
                      ▼
              ┌─────────────────┐
              │  Postgres + Kafka│
              │     + Redis      │
              └─────────────────┘
                      ▲
                      │ OIDC JWT (RS256, tenant_id=fan-platform)
                      │
              ┌───────┴────────┐
              │      IAM       │  http://iam.local/
              │ (auth-service +│
              │ account-service│
              │  + admin)      │
              └────────────────┘
```

### Service status (live)

| Service | Status |
|---|---|
| `gateway-service` | ✅ 구현·배포 완료 ([TASK-FAN-BE-001](tasks/done/TASK-FAN-BE-001-gateway-service-bootstrap.md)) |
| `community-service` | ✅ 구현·배포 완료 |
| `artist-service` | ✅ 구현·배포 완료 |
| `membership-service` | ✅ 구현·배포 완료 (TASK-FAN-BE-009) |
| `notification-service` | ✅ 구현·배포 완료 (TASK-FAN-BE-013) |
| `fan-platform-web` | ✅ 구현·배포 완료 (Next.js 15) |

상세는 [tasks/INDEX.md](tasks/INDEX.md) 참조.

## Differentiation from IAM's (retired) `community-service`

IAM 안에 `community-service` 가 product-layer demo 로 존재했으나 **2026-07-14 TASK-MONO-394 로 retired** (소스 제거, git history 에만 보존 — 상세는 [iam-platform PROJECT.md](../iam-platform/PROJECT.md)). fan-platform 은 그 데모가 살아 있던 시점부터 다음 점에서 차별화된 독립 구현이었고, 현재는 그 도메인의 유일한 실제 구현체다:

| 측면 | IAM demo (retired 2026-07-14) | fan-platform |
|---|---|---|
| 위치 | IAM 안 product-layer demo | 별도 프로젝트 |
| 인증 | IAM 내부 API 직접 호출 | OAuth2 Resource Server 표준 패턴 |
| Service split | community 단일 | community + artist (master data 분리) + membership + notification |
| Multi-tenant | 단일 tenant | `tenant_id=fan-platform` 격리 검증 |
| Frontend | 없음 | Next.js 15 + Tailwind |
| 운영성 | dev 데모 | Traefik routing, content moderation, audit trail |

## Project layout

```
projects/fan-platform/
├── PROJECT.md              ← classification (domain/traits/service_types)
├── README.md               ← this file
├── apps/                   ← service implementations
├── web/                    ← Next.js frontend
├── specs/
│   ├── contracts/          ← HTTP / event 계약
│   ├── services/           ← per-service architecture
│   ├── features/           ← feature specs (multi-tenancy, moderation, ...)
│   └── use-cases/
├── tasks/                  ← project task lifecycle
├── knowledge/              ← design references, ADRs
├── docs/                   ← project-specific docs
└── infra/                  ← project infrastructure (docker-compose 등)
```

## References

- [PROJECT.md](PROJECT.md) — 분류 + service map + IAM 통합 + scope
- [tasks/INDEX.md](tasks/INDEX.md) — task lifecycle
- [IAM ADR-001](../iam-platform/docs/adr/ADR-001-oidc-adoption.md) — OIDC 통합 결정
- [ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) — hostname routing
- [rules/domains/fan-platform.md](../../rules/domains/fan-platform.md) — 도메인 규칙

## License

(TBD — 포트폴리오 공개 전 결정)
