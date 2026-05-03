# Global Account Platform (GAP)

> Weverse Account에서 영감받은 **글로벌 계정/인증/보안 백엔드 플랫폼**. 다수의 하위 제품이 공유하는 계정 인프라 레이어를 프로덕션 수준으로 구현합니다.

[![CI](https://github.com/kanggle/global-account-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/kanggle/global-account-platform/actions/workflows/ci.yml)

---

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                         Client (Web / Mobile)                        │
└───────────────────────────────┬──────────────────────────────────────┘
                                │
                    ┌───────────▼───────────┐
                    │    Gateway Service     │  JWT 검증 · Rate Limiting
                    │    (Spring Cloud GW)   │  JWKS 캐시 · 라우팅
                    └───┬───────┬───────┬───┘
                        │       │       │
          ┌─────────────▼┐  ┌──▼──────┐ ┌▼──────────────┐
          │ Auth Service  │  │Account  │ │ Admin Service  │
          │              │  │Service  │ │               │
          │ · 로그인/로그아웃│  │· 회원가입 │ │· RBAC + 2FA   │
          │ · JWT 발급    │  │· 프로필   │ │· Lock/Unlock  │
          │ · OAuth 소셜  │  │· 상태 기계│ │· Bulk Lock    │
          │ · Refresh 회전│  │· GDPR   │ │· 감사 로그     │
          │ · 디바이스 세션│  │         │ │· Circuit Breaker│
          └──────┬───────┘  └────┬────┘ └───────┬───────┘
                 │               │               │
          ┌──────▼───────────────▼───────────────▼──────┐
          │                   Kafka                      │
          │  auth.login.succeeded · account.locked · ... │
          └──────────────────────┬──────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   Security Service      │
                    │                         │
                    │ · 로그인 이력 기록        │
                    │ · 비정상 탐지 (5개 규칙)  │
                    │ · Impossible Travel     │
                    │ · IP Reputation         │
                    │ · 자동 계정 잠금          │
                    └─────────────────────────┘

    ┌──────────────┐
    │  Admin Web   │  Next.js 15 · React 19
    │  (Frontend)  │  운영자 콘솔
    └──────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| **Language** | Java 21, TypeScript 5 |
| **Backend** | Spring Boot 3, Spring Cloud Gateway, Spring Data JPA |
| **Frontend** | Next.js 15 (App Router), React 19, Tailwind CSS, shadcn/ui |
| **Database** | MySQL 8.0, Redis 7 |
| **Messaging** | Apache Kafka 3.7 (KRaft) |
| **Auth** | JWT (self-signed JWKS), Argon2id, TOTP 2FA, OAuth 2.0 (Google/Kakao) |
| **Observability** | Prometheus, Grafana, Loki, Promtail |
| **Infra** | Docker Compose, GitHub Actions CI/CD |
| **Testing** | JUnit 5, Testcontainers, WireMock, RestAssured, Vitest |
| **Build** | Gradle 8.14 (multi-module monorepo) |

---

## Key Features

### Authentication & Authorization
- **JWT 기반 인증** — self-signed JWKS, access/refresh 토큰 분리
- **Refresh Token Rotation** — 재사용 탐지 시 전체 체인 revoke
- **OAuth 소셜 로그인** — Google, Kakao (Authorization Code + BFF 패턴)
- **디바이스 세션 관리** — 동시 세션 제한, 세션별 revoke
- **Rate Limiting** — Redis 기반 per-endpoint throttling

### Admin Operations (RBAC)
- **역할 기반 접근 제어** — SUPER_ADMIN, ACCOUNT_ADMIN, AUDITOR
- **2FA (TOTP)** — AES-GCM 암호화 저장, 10개 recovery codes
- **계정 Lock/Unlock/Bulk Lock** — 멱등성 키 기반, 부분 실패 허용
- **세션 강제 종료** — 관리자 주도 force logout
- **통합 감사 로그** — admin_actions + login_history + suspicious_events

### Security & Detection
- **5개 탐지 규칙** — DeviceChange, GeoAnomaly, Velocity, ImpossibleTravel, IpReputation
- **리스크 스코어 집계** — 규칙별 가중치, 임계값 기반 자동 계정 잠금
- **DLQ 처리** — 실패 이벤트 Dead Letter Queue 전파 + 관측성 노출
- **Circuit Breaker** — Resilience4j 기반 다운스트림 장애 격리

### Compliance (GDPR/PIPA)
- **계정 삭제 (Right to Erasure)** — PII SHA-256 해싱 + 프로필 마스킹
- **데이터 내보내기 (Right to Portability)** — JSON 형식 개인정보 일괄 추출
- **감사 추적 보존** — 삭제 후에도 비식별 감사 로그 유지

### Event-Driven Architecture
- **Transactional Outbox** — 서비스별 OutboxPollingScheduler → Kafka 발행
- **14개 도메인 이벤트** — auth, account, security, admin, session, community, membership
- **멱등 소비** — processed_events 테이블 기반 중복 방지

---

## Project Stats

| Metric | Count |
|---|---|
| 커밋 | 250+ |
| 완료 태스크 | 91 |
| Java 소스 파일 | 637 |
| 테스트 파일 | 112 |
| Flyway 마이그레이션 | 46 |
| Feature Specs | 13 |
| API/Event Contracts | 14 |
| E2E 시나리오 | 4 (Golden Path, Refresh Reuse, Cross-Service Bulk Lock, DLQ) |

---

## Services

| Service | Type | Port | Description |
|---|---|---|---|
| `gateway-service` | Spring Cloud Gateway | 8080 | 엣지 라우팅, JWT 검증, rate limiting |
| `auth-service` | REST API | 8081 | 로그인, JWT 발급, OAuth, refresh 회전, 디바이스 세션 |
| `account-service` | REST API | 8082 | 회원가입, 프로필, 계정 상태 기계, GDPR 삭제/내보내기 |
| `security-service` | Event Consumer | 8084 | Kafka 이벤트 소비, 비정상 탐지, 자동 잠금 |
| `admin-service` | REST API | 8085 | 운영자 RBAC/2FA, lock/unlock, 감사 조회 |
| `admin-web` | Next.js | 3000 | 관리자 콘솔 (로그인, 계정 관리, 감사 로그) |

---

## Quick Start

### Prerequisites
- Java 21+
- Docker Desktop
- Node.js 20+ (admin-web)

### 1. Infrastructure

bootRun 워크플로우 (host JVM이 MySQL/Kafka/Redis 에 접근):

```bash
# host port re-publish overlay 적용 — MySQL :33306, Kafka :39093, Redis :36379
pnpm gap:bootrun
# 또는: docker compose -f docker-compose.yml -f docker-compose.bootrun.yml up -d \
#         mysql redis kafka kafka-init
```

컨테이너 풀스택 (gateway 도 컨테이너; 호스트네임 라우팅):

```bash
bash scripts/dev-setup.sh    # *.local hosts 등록 (한 번만)
pnpm traefik:up               # 공유 Traefik (한 번만)
pnpm gap:up                   # http://gap.local/ 으로 gateway 접근
```

### 2. Backend Services
```bash
./gradlew bootRun  # 또는 개별 서비스:
./gradlew :apps:auth-service:bootRun

# admin-service 로컬 기동 (dev profile로 테스트 JWT/TOTP 키 주입)
./gradlew :apps:admin-service:bootRun --args='--spring.profiles.active=dev'
```

> `dev` 프로파일은 **로컬 개발 전용**입니다. `application-dev.yml`에 고정 테스트
> JWT PEM / TOTP AES 키가 baked-in되어 있으므로 배포 환경(staging/prod)에서는
> 절대 활성화하지 마세요. 프로덕션은 `application-prod.yml` + 실제 secret
> 주입 경로를 사용합니다.

### 3. Frontend
```bash
cd apps/admin-web
pnpm install
pnpm dev
```

### 4. E2E Tests
```bash
# 전체 스택 기동
docker compose -f docker-compose.e2e.yml -p gap-e2e up -d --build

# 서비스 healthy 대기 후 테스트 실행
./gradlew :tests:e2e:test
```

### 5. Unit + Integration Tests

```bash
./gradlew :apps:auth-service:test
```

> Testcontainers 기반 통합 테스트는 `@EnabledIf("isDockerAvailable")`로
> Docker 환경이 유효할 때만 실행됩니다. **Windows + Docker Desktop 4.69+
> 환경에서는 로컬 skip이 정상 동작**이며, [GitHub Actions CI](.github/workflows/ci.yml)의
> `build-backend` job이 Ubuntu runner에서 모든 통합 테스트를 실행하여
> 실질 안전망 역할을 합니다. 로컬 실행이 필요한 경우
> [docs/guides/local-integration-testing.md](docs/guides/local-integration-testing.md)
> 6단계(Rancher Desktop / WSL docker-ce / Docker Desktop 다운그레이드)
> 참고.

---

## Monitoring

호스트네임 라우팅 (Traefik) 기준. `pnpm traefik:up` 으로 공유 Traefik 기동 후 접근.

| Dashboard | URL | Description |
|---|---|---|
| Grafana | http://grafana.gap.local/ | 서비스 메트릭 + 로그 |
| Kafka UI | http://kafka.gap.local/ | 토픽/컨슈머 그룹 모니터링 |
| Prometheus | (internal — `gap-prometheus:9090` from container) | 메트릭 수집/쿼리 |
| Alertmanager | (internal — `gap-alertmanager:9093` from container) | 알림 라우팅 |

> Prometheus / Alertmanager 는 호스트 노출이 필요 없으므로 docker network 내부 전용. 외부 접근이 필요하면 `docker exec` 또는 [docs/guides/dev-tooling.md](../../docs/guides/dev-tooling.md) 의 dev overlay 패턴 참조.

---

## Repository Structure

```
global-account-platform/
├── apps/
│   ├── auth-service/          # 인증/OAuth/세션
│   ├── account-service/       # 계정/프로필/GDPR
│   ├── admin-service/         # 운영자 RBAC/2FA/감사
│   ├── security-service/      # 보안 이벤트 소비/탐지
│   ├── gateway-service/       # API 게이트웨이
│   └── admin-web/             # 관리자 프론트엔드
├── libs/                      # 공유 라이브러리 (common, web, messaging, security, observability)
├── tests/e2e/                 # E2E 통합 테스트
├── specs/                     # 스펙 (contracts, services, features, use-cases)
├── tasks/                     # 태스크 기반 워크플로우 (91 done)
├── infra/                     # Prometheus/Grafana/Loki 설정
├── docker/                    # MySQL init, 서비스별 Dockerfile
├── .github/workflows/         # CI/CD (GitHub Actions)
└── docker-compose*.yml        # 로컬 개발 + E2E 환경
```

---

## Development Methodology

이 프로젝트는 **Spec-Driven, Task-Driven** 방법론을 따릅니다:

1. **PROJECT.md** — 도메인/특성 분류 (`saas`, `transactional`, `regulated`, `audit-heavy`, `integration-heavy`)
2. **specs/** — 기능 스펙, API/이벤트 계약, 서비스 아키텍처가 구현의 source of truth
3. **tasks/** — 모든 구현은 태스크 단위로 추적 (backlog → ready → done)
4. **rules/** — taxonomy 기반 규칙 시스템으로 도메인별 제약 자동 적용
5. **review** — 태스크 완료 후 리뷰 → fix 태스크 발행 사이클

---

## License

This project is for portfolio/educational purposes.
