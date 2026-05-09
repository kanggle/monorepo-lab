---
name: fan-platform
domain: fan-platform
traits: [transactional, content-heavy, read-heavy, integration-heavy, multi-tenant]
service_types: [rest-api, event-consumer, frontend-app]
compliance: [pipa]
data_sensitivity: pii
scale_tier: startup
taxonomy_version: 0.1
---

# fan-platform

## Purpose

K-pop 류 아티스트↔팬 커뮤니티 백엔드 플랫폼. Weverse 스타일의 **비대칭 콘텐츠 관계** (소수 아티스트 발행자 ↔ 다수 팬 소비자) 를 중심에 두는 풀스택 시스템.

이 프로젝트는 백엔드 포트폴리오로서 **프로덕션 지향 설계** + **풀스택 demo path** 를 모두 추구한다. ecommerce-microservices-platform 과 함께 **두 번째 풀스택 B2C 도메인** 으로, GAP 의 OIDC IdP 통합·multi-tenant 격리·content-heavy 패턴을 깊이 검증한다.

GAP 안에 [frozen `community-service`](../global-account-platform/apps/community-service/) (포스트/댓글/반응 데모) 가 이미 있지만, 그것은 GAP 의 internal API 호출 시연용 mini-demo 이며, 본 fan-platform 은 다음 점에서 차별화된다:

- **자체 프로젝트** — GAP 와 같은 레벨의 독립 서비스 군. GAP 를 표준 OIDC IdP 로 소비
- **service split** — community / artist / membership / notification / admin 으로 도메인 경계를 나눠 비대칭 관계 (artist 1 : N 팬) 를 데이터 모델 레벨에서 명시
- **multi-tenant 격리** — `tenant_id=fan-platform` 으로 GAP 의 멀티테넌트 인프라 검증
- **풀스택** — Next.js 15 + Tailwind 기반 lean frontend 포함 (5~7 페이지)
- **production 지향 운영성** — Traefik 기반 hostname routing (ADR-MONO-001 Option C), audit-heavy 감사 트레일, content moderation pipeline

## Domain Rationale

`fan-platform` (taxonomy 신규 도메인, [rules/taxonomy.md](../../rules/taxonomy.md#fan-platform) 참조) 을 선택한 이유:

- 핵심 모델이 **아티스트 1 : N 팬 비대칭** 이며, 일반 `community` (peer-to-peer) 또는 `sns` (양방향 follow 그래프) 와 명확히 구분됨
- 멤버십 기반 차등 접근 (PUBLIC / MEMBERS_ONLY / PREMIUM) 이 핵심 비즈니스 규칙
- `content-platform` 은 에디토리얼 워크플로 중심 (편집/배포) 이라 부적합

## Trait Rationale

- **transactional**: 멤버십 결제·포스트 발행·댓글 모더레이션이 강한 일관성 + 멱등성 요구. Saga + idempotency + 상태 기계 패턴 필수. 적용: `community-service`, `artist-service`, `membership-service`.
- **content-heavy**: 포스트·댓글·미디어가 핵심 자산. CMS 패턴, 미디어 스토리지 분리(MinIO/S3 + CDN), 검색 인덱싱, 멀티계층 캐시 필수.
- **read-heavy**: 피드 조회/포스트 상세/아티스트 디렉토리가 쓰기 트래픽보다 훨씬 많음. CDN + Redis 캐시 + 읽기 복제·페이지네이션 최적화.
- **integration-heavy**: GAP OIDC IdP, MinIO 미디어, 푸시 알림 (FCM/APNs), 이메일, 결제 (membership-service via PG mock) 등 외부 연동 다수.
- **multi-tenant**: GAP 의 `tenant_id=fan-platform` claim 을 row-level isolation 으로 적용. 향후 다른 팬덤 (스포츠/eSports/창작자) 추가 시 multi-tenant 인프라가 핵심.

## Service Map (v1 + v2)

### v1 (포트폴리오 1차 배포 목표)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `gateway-service` | rest-api | 엣지 라우팅, OIDC token 검증 (GAP 의 RS256 JWT), tenant gate (`tenant_id=fan-platform` 만 통과), rate limit |
| `community-service` | rest-api | post / comment / reaction / feed (팔로우 기반) — frozen GAP demo 의 도메인을 깊이 +α |
| `artist-service` | rest-api | 아티스트 프로필 + follow 관계 + fandom 메타데이터 — community 의 master data |

### v2 (정해진 후 발행, 별도 부트스트랩 태스크)

| Service | Service Type | 핵심 책임 |
|---|---|---|
| `membership-service` | rest-api | 멤버십 / 구독 / 프리미엄 접근 제어 (PG 통합 mock) |
| `notification-service` | event-consumer | 이벤트 fanout (FCM/APNs/이메일) |
| `admin-service` | rest-api | B2C 운영 콘솔 (post 모더레이션 / 신고 처리 / 아티스트 등록) |

### Frontend

| App | Type | 책임 |
|---|---|---|
| `fan-platform-web` | frontend-app (Next.js 15) | 5~7 페이지: 피드 / 아티스트 디렉토리 / 아티스트 프로필 / 포스트 상세 / 로그인 / 멤버십 게이트 |

상세 아키텍처는 각 service 의 `specs/services/<service>/architecture.md` 에서 선언.

## GAP IdP Integration

`fan-platform` 은 [global-account-platform](../global-account-platform/PROJECT.md) (GAP) 을 표준 OIDC IdP 로 사용한다 ([ADR-001](../global-account-platform/docs/adr/ADR-001-oidc-adoption.md)). 모든 fan-platform 서비스는 OAuth2 Resource Server 패턴으로 GAP 의 JWKS 기반 RS256 access token 을 검증하고, `tenant_id=fan-platform` claim 만 통과시킨다.

frontend (`fan-platform-web`) 는 `next-auth` + GAP OIDC custom provider 로 `authorization_code` + PKCE 플로우 적용 (TASK-BE-254 consumer-integration-guide 따름).

통합 상세는 [specs/integration/gap-integration.md](specs/integration/gap-integration.md) (TASK-FAN-BE-001 부트스트랩 시 작성).

## Local Network

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) Option C 채택 — `fan-platform.local` 호스트네임으로 Traefik routing. PORT_PREFIX 미사용. 부트스트랩 시점부터 [TASK-MONO-022](../../tasks/done/TASK-MONO-022-traefik-hostname-routing-migration.md) 의 `infra/traefik/` 인프라에 join.

## Out of Scope (의도적 제외)

명시적으로 선언하지 않은 분류:

- **community** / **sns**: 평등한 사용자 관계 (peer-to-peer / 양방향 그래프) 가 도메인 핵심 아님. fan-platform 은 비대칭 (아티스트 1:N 팬).
- **ott** / **media-streaming**: 영상 스트리밍이 중심 가치 아님. 포스트 첨부 미디어 정도만.
- **regulated**: 금융·의료급 규제 대상 아님. PIPA 만 명시.
- **real-time**: 라이브 스트리밍은 v3+ 고려. 현재는 polling 기반 피드.
- **audit-heavy**: 멤버십 결제·아티스트 발행은 audit 추적하나, 법적 감사 수준의 불변 로그는 v1 범위 밖.
- **batch-heavy**: 알림 fanout 외에 대량 배치 없음.
- **data-intensive**: 콘텐츠는 사용자당 구조적으로 작음.
- **internal-system**: 외부 사용자 대상 B2C.

이 경계가 바뀌면 본 PROJECT.md 의 traits 를 수정하고 [rules/traits/](../../rules/traits/) 의 해당 파일을 로딩 범위에 포함시킬 것.

## Overrides

현재 명시적 override 없음. 공통/도메인/특성 규칙을 모두 기본값대로 따른다.

예외가 필요한 경우 이 섹션에 다음 형식으로 기록:

```
- **rule**: rules/traits/<trait>.md#<rule-id>
- **reason**: <why>
- **scope**: <which service(s)>
- **expiry**: <date or condition>
```
