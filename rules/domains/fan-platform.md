# Domain: fan-platform

> **Activated when**: `PROJECT.md` declares `domain: fan-platform`.

---

## Scope

K-pop 류 팬덤 커뮤니티 플랫폼. 핵심 모델은 **아티스트 1 : N 팬 비대칭 콘텐츠 관계** — 소수 아티스트가 발행자, 다수 팬이 소비자 + 상호작용자. Weverse / NCSOFT UNIVERSE 가 대표 예시.

이 도메인은 "아티스트가 어떤 콘텐츠를 발행하고, 팬이 어떻게 소비·상호작용하며, 멤버십에 따라 어떻게 차등 접근되는가" 에 집중한다. 일반 `community` (peer-to-peer 평등 관계) 또는 `sns` (양방향 follow 그래프 + 피드 알고리즘) 와 명확히 구분된다.

---

## Bounded Contexts (표준)

fan-platform 도메인 프로젝트가 일반적으로 가지는 bounded context 묶음. 실제 서비스 분할은 트래픽·소유권·변경 빈도에 따라 달라진다.

| Bounded Context | 책임 |
|---|---|
| **Artist** | 아티스트 프로필 (이름, 데뷔일, 멤버, 이미지), follow / fandom 관계, 그룹·멤버 구조 |
| **Content** | 포스트 (텍스트 + 미디어), 댓글, 반응 (이모지), 상태 머신 (DRAFT / PUBLISHED / HIDDEN / DELETED) |
| **Feed** | 팬이 팔로우한 아티스트의 포스트 시간 순 / 알고리즘 순 정렬, 캐시 |
| **Membership** | 무료 / 유료 멤버십 등급, 구독 결제, 갱신, 만료, 멤버십 기반 콘텐츠 접근 권한 |
| **Notification** | 새 포스트, 멤버십 만료, 댓글 멘션 등 이벤트 기반 알림 (push / email / in-app) |
| **Moderation** | 신고 처리, 콘텐츠 자동·수동 검열, 차단·삭제 워크플로 (audit trail 필수) |
| **Admin / Operations** | 운영자 대시보드, 아티스트 등록·제재, 운영 정책 설정 |

각 context 는 자체 데이터 저장소를 가지며, context 간 통신은 **이벤트** 또는 **잘 정의된 내부 HTTP** 로만 이루어진다.

---

## Ubiquitous Language

- **Artist** — 콘텐츠를 발행하는 1차 주체. 개인 (솔로) 또는 그룹 (멤버 N명). 본인 또는 소속사 운영자가 운영.
- **Fan** — 콘텐츠를 소비·상호작용하는 다수 주체. 아티스트를 follow 함으로써 fandom 에 진입.
- **Fandom** — 특정 아티스트를 follow 한 팬의 집합. Read-model 또는 별도 aggregate 로 관리 가능.
- **Post** — 아티스트가 발행하는 콘텐츠 단위. 본문 (text) + 미디어 (image/video URL 참조) + 공개 범위 (visibility) + 상태 (status).
- **Visibility** — 포스트 공개 범위. `PUBLIC` (전체) / `MEMBERS_ONLY` (유료 멤버십 전용) / `PREMIUM` (상위 등급 전용).
- **Status** — 포스트 상태. `DRAFT` / `PUBLISHED` / `HIDDEN` (소프트 비공개) / `DELETED` (소프트 삭제). 상태 전이 이력 (`post_status_history`) append-only.
- **Comment** — 포스트에 속한 팬 댓글. 소프트 삭제 (`deleted_at`). 단일 레벨 (대댓글은 v2+ 검토).
- **Reaction** — 포스트별 팬별 이모지 반응. 계정×포스트 unique constraint (한 팬이 한 포스트에 한 반응만).
- **Feed** — 팬이 팔로우한 아티스트의 PUBLISHED 포스트 모음. 시간 순 (recent) / 알고리즘 순 (personalized — v2+).
- **Follow** — 팬 → 아티스트 관계. `feed_subscriptions` 테이블 한 방향. 양방향 friendship 아님 (sns 와의 차이).
- **Membership** — 팬이 특정 아티스트 (또는 플랫폼 전체) 의 유료 회원 상태. 등급 (FREE / BASIC / PREMIUM) + 만료일.
- **Moderation** — 콘텐츠 신고 / 자동 검열 / 운영자 검토 / 삭제·복구 워크플로. 각 단계가 audit trail 에 기록됨.

이 용어들은 코드·API·문서에서 일관되게 사용되어야 한다.

---

## Standard Error Codes

fan-platform 도메인에서 공통으로 발생하는 에러는 [../../platform/error-handling.md](../../platform/error-handling.md) 의 전역 카탈로그에 등록되어야 한다. 본 도메인 특유의 코드:

### Artist
- `ARTIST_NOT_FOUND` — 존재하지 않는 아티스트
- `ARTIST_INACTIVE` — 비활성화된 아티스트에 대한 작업 시도
- `FOLLOW_SELF_FORBIDDEN` — 자기 자신을 follow 시도 (운영자 계정의 아티스트 동기화 케이스)

### Content
- `POST_NOT_FOUND` — 존재하지 않는 포스트
- `POST_STATUS_TRANSITION_INVALID` — 허용되지 않는 상태 전이 (예: DELETED → PUBLISHED)
- `COMMENT_NOT_FOUND` — 존재하지 않는 댓글
- `REACTION_INVALID_EMOJI` — 허용되지 않는 이모지 코드

### Membership
- `MEMBERSHIP_REQUIRED` — MEMBERS_ONLY 콘텐츠에 대한 무료 사용자 접근
- `MEMBERSHIP_EXPIRED` — 만료된 멤버십으로 접근 시도
- `MEMBERSHIP_DOWNGRADE_BLOCKED` — 활성 결제 주기 중 다운그레이드 차단

### Moderation
- `POST_REPORTED_PENDING_REVIEW` — 신고되어 검토 중인 포스트 (작성자 외 접근 시 표시)
- `MODERATION_DECISION_REQUIRED` — 운영자 결정 필요한 신고

### Cross
- `PERMISSION_DENIED` — 작성자 / 운영자 권한 없음 (포스트 수정 등)

---

## Integration Boundaries

### 외부 (플랫폼 경계 바깥)

- **GAP (global-account-platform)** — OIDC IdP. RS256 JWT 검증, `tenant_id=fan-platform` claim, account profile 조회. **표준 OAuth2 Resource Server 패턴** 으로 통합.
- **MinIO / S3** — 미디어 (포스트 이미지, 아티스트 프로필 사진) 저장. CDN 연동.
- **PG (Payment Gateway)** — 멤버십 결제 (v2). mock 으로 시작 가능.
- **푸시 알림 채널** — FCM / APNs / 이메일 / 카카오톡 알림톡 (v2 notification-service).

### 내부 (같은 프로젝트 내 다른 서비스)

- gateway-service → 모든 service: OIDC token 검증 후 `X-Tenant-Id`, `X-Account-Id` 헤더 전파
- community-service → artist-service: 포스트 작성 시 author 의 artist 정보 (표시명, 프로필 사진) 조회
- community-service → membership-service (v2): MEMBERS_ONLY 포스트 접근 시 멤버십 권한 체크 (fail-closed: 503 시 거부)
- artist-service → community-service: 아티스트 비활성화 시 해당 아티스트의 포스트 일괄 HIDDEN 전환 (이벤트)
- 모든 service → notification-service (v2): 이벤트 발행 (post.published / comment.created / membership.expired)

### 내부 이벤트 카탈로그 (권장)

- `<prefix>.artist.created` / `.updated` / `.deactivated`
- `<prefix>.fan.followed` / `.unfollowed`
- `<prefix>.post.published` / `.hidden` / `.deleted`
- `<prefix>.comment.created` / `.deleted`
- `<prefix>.reaction.added` / `.removed`
- `<prefix>.membership.activated` / `.expired` / `.cancelled`
- `<prefix>.moderation.reported` / `.decided`

---

## Mandatory Rules

### F1. 비대칭 관계는 데이터 모델 레벨에서 명시
artist (1) ↔ fan (N) 관계는 별도 테이블 (`feed_subscriptions` 또는 `follows`) 로 명시. peer 관계 (양방향 friendship) 와 혼용 금지. 양방향이 필요하면 별도 도메인 (community / sns) 으로 재분류.

### F2. 멤버십 기반 접근은 fail-closed
MEMBERS_ONLY / PREMIUM 콘텐츠 접근 시 멤버십 서비스 호출이 timeout / 503 / 5xx 인 경우 **접근 거부** (403 MEMBERSHIP_REQUIRED). fail-open (의심 시 허용) 절대 금지.

### F3. 포스트 상태 전이는 명시된 머신만 허용
허용 전이: DRAFT → PUBLISHED, PUBLISHED → HIDDEN, PUBLISHED → DELETED, HIDDEN → PUBLISHED, HIDDEN → DELETED. 그 외 조합은 422 POST_STATUS_TRANSITION_INVALID. 이력은 `post_status_history` append-only.

### F4. 반응은 unique upsert
계정×포스트당 reaction 1 행만. 동일 사용자가 다른 이모지로 변경 시 emojiCode 만 update. INSERT 두 번 발생 금지 (DB unique constraint + application 레벨 모두).

### F5. 미디어는 본문에 직접 저장 금지
포스트의 media URL 만 DB 에 저장. 실제 이미지·영상은 MinIO/S3 에 업로드. URL 검증 (allowlist 도메인) 또는 자체 호스트만 허용.

### F6. 모든 운영자 (admin) 행위는 audit trail
포스트 강제 HIDDEN / 삭제 / 아티스트 등록·정지 / 멤버십 강제 만료 등 운영자 행위는 `admin_actions` 에 append-only 기록. `tenant_id=fan-platform` 보존.

### F7. 멀티테넌트 격리는 row-level 강제
모든 도메인 테이블에 `tenant_id` NOT NULL. 모든 read/write 쿼리에 `WHERE tenant_id = ?` 명시. JPA repository 메서드 첫 인자가 `tenantId` 인 컨벤션 강제.

---

## Forbidden Patterns

- ❌ **양방향 follow 관계** 를 fan-platform 안에 구현 (sns 도메인의 책임이며 비대칭 모델과 충돌)
- ❌ **멤버십 체크 fail-open** (보수적 거부 위반)
- ❌ **포스트 hard delete** (모든 삭제는 `deleted_at` 소프트 삭제 + audit trail)
- ❌ **상태 전이를 직접 UPDATE** (use case + state machine 우회 금지)
- ❌ **comment / reaction 의 cross-tenant 조회** (격리 위반)
- ❌ **media binary 를 DB BLOB 으로 저장** (객체 스토리지 분리 원칙 위반)
- ❌ **운영자 행위를 audit row 없이 실행** (audit trail 누락)

---

## Required Artifacts

1. **Bounded context 맵** — 각 context 의 책임·소유 데이터·통신 방향. 위치: `specs/services/` 전반 또는 `specs/architecture.md`
2. **포스트 상태 머신** — DRAFT / PUBLISHED / HIDDEN / DELETED 전이 다이어그램. 위치: `specs/services/community-service/state-machines/post-status.md`
3. **멤버십 등급 정의** — FREE / BASIC / PREMIUM 등급별 콘텐츠 접근 매트릭스. 위치: `specs/services/membership-service/membership-tiers.md` (v2)
4. **GAP OIDC 통합 가이드** — `tenant_id=fan-platform` 검증, JWKS URI, role mapping. 위치: `specs/integration/gap-integration.md`
5. **에러 코드 등록** — 위 Standard Error Codes 가 [../../platform/error-handling.md](../../platform/error-handling.md) 에 존재
6. **Frontend → Backend API 계약** — Next.js fan-platform-web 이 호출하는 gateway 라우트. 위치: `specs/contracts/http/community-api.md`, `artist-api.md` 등

---

## Interaction with Common Rules

- [../../platform/architecture.md](../../platform/architecture.md) 의 서비스 경계 원칙을 따르되, 위 bounded context 구분을 참조한다.
- [../../platform/error-handling.md](../../platform/error-handling.md) 에 위 Standard Error Codes 가 등록되어야 한다.
- [../traits/transactional.md](../traits/transactional.md) 의 트랜잭션·멱등성 규칙이 멤버십 결제·포스트 발행·댓글 모더레이션에 적용된다.
- [../traits/content-heavy.md](../traits/content-heavy.md) 의 미디어·검색·캐시 규칙이 포스트·댓글에 적용된다.
- [../traits/read-heavy.md](../traits/read-heavy.md) 의 캐시·페이지네이션 규칙이 피드·아티스트 디렉토리에 적용된다.
- [../traits/integration-heavy.md](../traits/integration-heavy.md) 의 외부 연동 규칙이 GAP / MinIO / PG 연동에 적용된다.

---

## Checklist (Review Gate)

- [ ] 비대칭 (1:N) 관계가 데이터 모델에 명시되어 있는가? (F1)
- [ ] 멤버십 기반 접근 제어가 fail-closed 인가? (F2)
- [ ] 포스트 상태 전이가 머신을 통해서만 일어나는가? (F3)
- [ ] 반응이 unique upsert 로 동작하는가? (F4)
- [ ] 미디어 URL 만 DB 에 저장되고 binary 가 분리되었는가? (F5)
- [ ] 모든 운영자 행위가 `admin_actions` 에 기록되는가? (F6)
- [ ] 모든 도메인 테이블에 `tenant_id` NOT NULL + 쿼리에 명시되는가? (F7)
- [ ] 표준 에러 코드가 플랫폼 카탈로그에 등록되었는가?
- [ ] GAP OIDC 통합 가이드가 작성되었는가?
- [ ] 멤버십 등급별 접근 매트릭스가 명시되어 있는가? (v2 시점)
