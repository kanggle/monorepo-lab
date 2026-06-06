# fan-platform

> **K-pop 류 아티스트↔팬 커뮤니티** 백엔드 + Next.js 프론트엔드. Weverse-style.
> Built with Claude Code · spec-driven · IAM OIDC consumer · Traefik hostname routing

[![CI](https://github.com/kanggle/monorepo-lab/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/kanggle/monorepo-lab/actions/workflows/ci.yml?query=branch%3Amain)
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

🚧 **부트스트랩 진행 중** (2026-05-02 ~ )

v1 목표: gateway + community + artist 3 서비스 + lean Next.js 프론트엔드. 자세한 service map 은 [PROJECT.md](PROJECT.md#service-map-v1--v2).

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

### Service map (v1)

```
                       ┌──────────────────────┐
                       │  Traefik (host :80)  │
                       └──────────┬───────────┘
                                  │ Host: fan-platform.local
                       ┌──────────▼───────────┐
                       │   gateway-service    │ ← OIDC token 검증, tenant gate
                       └──────┬───────────────┘
                              │
                ┌─────────────┼─────────────┐
                ▼             ▼             ▼
        ┌──────────────┐ ┌──────────┐ ┌────────────┐
        │  community-  │ │ artist-  │ │ (membership│
        │   service    │ │ service  │ │  v2)       │
        └──────┬───────┘ └────┬─────┘ └────────────┘
               │              │
               └──────┬───────┘
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
| `gateway-service` | 🚧 첫 부트스트랩 태스크 ([TASK-FAN-BE-001](tasks/ready/)) |
| `community-service` | 📝 spec 작성 후 발행 |
| `artist-service` | 📝 spec 작성 후 발행 |
| `fan-platform-web` | 📝 백엔드 v1 안정화 후 |

상세는 [tasks/INDEX.md](tasks/INDEX.md) 참조.

## Differentiation from IAM's frozen `community-service`

IAM 안에 [`community-service`](../iam-platform/apps/community-service/) 가 frozen demo 로 존재하지만, 본 fan-platform 은 다음 점에서 차별화:

| 측면 | IAM frozen demo | fan-platform |
|---|---|---|
| 위치 | IAM 안 product-layer demo | 별도 프로젝트 |
| 인증 | IAM 내부 API 직접 호출 | OAuth2 Resource Server 표준 패턴 |
| Service split | community 단일 | community + artist (master data 분리) + membership (v2) |
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
