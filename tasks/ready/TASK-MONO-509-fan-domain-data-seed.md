# Task ID

TASK-MONO-509

# Title

팬 플랫폼 도메인 데이터 시드 — 아티스트 · 팬덤 · 게시물 3종 가시성 · 멤버십 구독을 `infra/demo/seed/seed-fan.sh` 로 재현 가능하게 만든다

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo
- test

---

# 배경 — `TASK-MONO-506` 의 S2 슬라이스

MONO-506 이 시드 **프레임워크**(`infra/demo/seed/`)와 ecommerce 시드(S3)를 만들고 라이브
검증까지 마쳤다. 팬 슬라이스는 **의도적으로 분리했다** — 이유는 두 가지다:

1. **호스트 자원.** 로컬 도커 가용 메모리는 11.7 GiB 인데 iam + console + ecommerce(축소본)
   35 컨테이너가 이미 9.2 GiB 를 쓴다(실측). 팬 스택을 함께 띄울 수 없어, ecommerce 를 내리고
   팬을 올리는 **별도 슬라이스**가 필요하다.
2. **검증하지 않은 시드는 거짓 약속이다.** MONO-506 의 원칙이 "넣는 행위가 곧 검증" 인데,
   띄워 보지 않은 채 스크립트만 커밋하면 그 원칙을 스스로 어긴다.

---

# Goal

`bash infra/demo/demo-up.sh iam fan console` 로 뜬 스택에서 `demo@demo.com` 으로 팬 웹에
로그인하면 아티스트 · 게시물 · 멤버십 · 알림 화면이 **비어 있지 않고**, 팬 게시물 작성과
멤버십 구독이 실제로 성공한다.

---

# Scope

## In Scope

- `infra/demo/seed/seed-fan.sh` (신규) — 드라이버가 자동 디스패치한다(파일명이 키)
- 팬 웹 화면 9개 라이브 검증
- `docs/guides/interview-demo-walkthrough.md` § 3 의 "시드 없음" 경고 제거

## Out of Scope

- 팬 제품 코드 변경 — 발굴한 결함은 **별도 티켓**(MONO-506 AC-8 과 같은 규율)
- 다른 도메인 시드 — `TASK-MONO-510`

---

# 알려진 레시피 (2026-07-24 실측 · **착수 시 재검증 필수**)

> ⚠️ 아래는 12일 전 다른 데모 계정(`fan-demo@example.com`)으로 얻은 관측이다. 값과 제약은
> **DB 에서 다시 확인**할 것 — 특히 데모 계정이 `demo@demo.com` /
> account `0199de70-0000-7000-8000-00000000fa02` / tenant `fan-platform` 으로 바뀌었다.

**왜 직접-DB 가 필요한가 (= `dbexec --why` 에 적을 사유의 원본)**: fan-platform 권한은 OAuth
scope 가 아니라 **JWT `roles` 클레임** 기반이다(각 서비스의
`ActorContextJwtAuthenticationConverter`). 데모 사용자 토큰에는 role 이 없어
**아티스트 · 그룹 · 팬덤 · `ARTIST_POST` 생성 API 가 전부 403** 이다.

**API 로 가능한 것 (= 우선 경로)**: `FAN_POST` 작성 · 팔로우 · 댓글 · 리액션 ·
멤버십 구독. 이것들은 반드시 API 로 넣는다.

**DB 접속**: `docker exec fan-platform-postgres psql -U fanplatform -d <db>`
(4 DB: `fanplatform_artist` / `_community` / `_membership` / `_notification`).
전 테이블 `tenant_id NOT NULL`, `created_at`/`updated_at` 기본값 없음(공급 필수).

**enum 의 권위는 DB CHECK 제약** (`SELECT pg_get_constraintdef(...)` — spec 아님):

| 테이블 | 컬럼 | 값 |
|---|---|---|
| `artists` | `status` / `artist_type` | DRAFT·PUBLISHED·ARCHIVED / SOLO·GROUP_MEMBER |
| `artist_groups` | `status` | ACTIVE·ARCHIVED |
| `group_memberships` | `role` | LEADER·MEMBER·FORMER_MEMBER |
| `fandoms` | `color_hex` | `^#[0-9A-Fa-f]{6}$` |
| `posts` | `visibility` / `status` / `post_type` | PUBLIC·MEMBERS_ONLY·PREMIUM / DRAFT·PUBLISHED·HIDDEN·DELETED / ARTIST_POST·FAN_POST |
| `reactions` | `reaction_type` | LIKE·LOVE·FIRE·SAD |
| `memberships` | `tier` / `status` | MEMBERS_ONLY·PREMIUM / ACTIVE·CANCELED |
| `notifications` | `type` / `status` | WELCOME·CANCELLATION·EXPIRY_REMINDER / UNREAD·READ |

**🔴 `PUBLISHED` 는 `published_at` 을 세팅해야 피드/디렉터리에 노출된다.**
`ARTIST_POST.author_account_id` = 아티스트 엔티티 id. `follows.artist_account_id` = 동일 id.
`notifications.source_event_id` UNIQUE.

**🔴 게이트웨이 경로**: fan 게이트웨이는 `/api/v1/**` 만 받아 다운스트림 `/api/**` 로
rewrite 한다. `/api/artists` 직접 호출은 **404**. 검증 URL 은 `/api/v1/artists` ·
`/api/v1/community/feed` · `/api/v1/memberships` · `/api/v1/notifications`.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 화면 모집단을 코드에서 다시 센다. 2026-08-05 실측으로
      `projects/fan-platform/web/fan-platform-web/src/app/**/page.tsx` 는 **9개**
      (`/login` · `/` · `/artists` · `/artists/[id]` · `/posts/[id]` · `/membership` ·
      `/membership/history` · `/notifications` · `/me`). 다르면 실제가 이긴다.
      **그리고 위 레시피의 CHECK 제약을 DB 에서 다시 읽는다**
- [ ] **AC-1 (API 우선)** — API 로 가능한 것(FAN_POST · 팔로우 · 댓글 · 리액션 · 구독)은
      전부 API 로 넣는다. 직접-DB 는 `dbexec --why` 로만, 사유는 **재검증 가능하게**
      (403 을 실제로 받아 본 엔드포인트 이름과 응답을 적는다)
- [ ] **AC-2 (라이브 검증)** — 9개 화면을 **브라우저로** 연다. 직접 토큰 스모크는 대체가
      아니다 — 이 프로젝트에는 그 둘이 갈린 전례가 있다(`TASK-FAN-FE-008`: verify-seed 는
      200 인데 브라우저 SSR 은 전 페이지 401)
- [ ] **AC-3 (가시성 3종)** — PUBLIC / MEMBERS_ONLY / PREMIUM 게시물이 멤버십 등급에 따라
      **다르게** 보인다. 셋 다 보이거나 셋 다 안 보이면 게이팅을 검증한 것이 아니다
- [ ] **AC-4 (대표 쓰기)** — 팬 게시물 작성 + 멤버십 구독이 브라우저에서 성공한다
- [ ] **AC-5 (멱등)** — 시드를 연속 2회 실행해도 행 수가 수렴한다
- [ ] **AC-6 (메모리 실측)** — 슬라이스의 컨테이너 수 + 메모리를 기록한다(MONO-399 AC-2 입력)
- [ ] **AC-7 (가드)** — `verify-demo-wrapper.sh` 의 가드 (y) 를 통과한다(직접 `psql` 호출 0건)
- [ ] **AC-8 (발굴 결함 분리)** — 별도 티켓. 0건이면 "0건" 이라고 적는다

---

# Related Specs

- `infra/demo/seed/README.md` — 시드 규약(이것을 먼저 읽을 것)
- `infra/demo/seed/seed-ecommerce.sh` — 두 신원 · 도메인 규칙 존중의 선례
- `projects/fan-platform/specs/`

# Edge Cases

- 팬 테넌트는 `fan-platform` 이다 — `demo-corp` 가 아니다. 시드는 테넌트를 **파라미터로** 받는다
- 알림은 이벤트 기반이다 — 컨슈머가 떠 있어야 채워진다
- 멤버십 게이팅은 community → membership S2S 호출에 의존한다(fail-closed).
  `TASK-FAN-BE-029/030` 이 그 경로를 고쳤으니 회귀했는지 확인할 것

# Failure Scenarios

- **초록 시드, 빈 화면** — `published_at` 미세팅이 이 프로젝트의 대표 사례다
- **`/api/...` 로 검증** — 게이트웨이가 404 를 낸다(`/api/v1/...` 여야 한다)
- **직접 토큰으로만 검증** — 브라우저 SSR 경로는 별개다(FE-008 전례)

# Definition of Done

- [ ] `seed-fan.sh` 커밋 + 2회 실행
- [ ] 9개 화면 브라우저 증거
- [ ] 메모리 실측 기록
- [ ] 가이드 § 3 경고 제거
- [ ] Ready for review
