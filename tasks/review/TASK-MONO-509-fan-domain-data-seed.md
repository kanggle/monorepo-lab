# Task ID

TASK-MONO-509

# Title

팬 플랫폼 도메인 데이터 시드 — 아티스트 · 팬덤 · 게시물 3종 가시성 · 멤버십 구독을 `infra/demo/seed/seed-fan.sh` 로 재현 가능하게 만든다

# Status

review

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

- [x] **AC-0 (재측정)** — 화면 모집단을 코드에서 다시 센다. 2026-08-05 실측으로
      `projects/fan-platform/web/fan-platform-web/src/app/**/page.tsx` 는 **9개**
      (`/login` · `/` · `/artists` · `/artists/[id]` · `/posts/[id]` · `/membership` ·
      `/membership/history` · `/notifications` · `/me`). 다르면 실제가 이긴다.
      **그리고 위 레시피의 CHECK 제약을 DB 에서 다시 읽는다**
      → **9개 그대로**. CHECK 제약은 4개 DB 전수 재조회 결과 레시피 표와 **일치**.
      🔴 다만 **레시피가 든 사유는 틀렸다** — 아래 § 결과 (가)/(나)
- [x] **AC-1 (API 우선)** — API 로 가능한 것(FAN_POST · 팔로우 · 댓글 · 리액션 · 구독)은
      전부 API 로 넣는다. 직접-DB 는 `dbexec --why` 로만, 사유는 **재검증 가능하게**
      (403 을 실제로 받아 본 엔드포인트 이름과 응답을 적는다)
      → 5종 전부 API. 직접-DB 2블록, 사유에 **실제로 받은 응답 문자열**을 적었다
- [x] **AC-2 (라이브 검증)** — 9개 화면을 **브라우저로** 연다. 직접 토큰 스모크는 대체가
      아니다 — 이 프로젝트에는 그 둘이 갈린 전례가 있다(`TASK-FAN-FE-008`: verify-seed 는
      200 인데 브라우저 SSR 은 전 페이지 401)
      → 9/9 렌더 확인, `*.local` 4xx/5xx **0건**
- [x] **AC-3 (가시성 3종)** — PUBLIC / MEMBERS_ONLY / PREMIUM 게시물이 멤버십 등급에 따라
      **다르게** 보인다. 셋 다 보이거나 셋 다 안 보이면 게이팅을 검증한 것이 아니다
      → 상세 **200 / 200 / 403**, 피드 `locked` **false / false / true**,
      해지 후 음성 대조 **200 / 403 / 403**
- [ ] **AC-4 (대표 쓰기)** — 팬 게시물 작성 + 멤버십 구독이 브라우저에서 성공한다
      → 🔴 **둘 다 제품에 경로가 없다**(§ 결과 (마)). 브라우저 쓰기는 **리액션**으로
      증명했다(DB `LIKE → LOVE`). → `TASK-FAN-FE-015` / `TASK-FAN-FE-016`
- [x] **AC-5 (멱등)** — 시드를 연속 2회 실행해도 행 수가 수렴한다
      → 2회차 `생성 0 · 기존 5 · 실패 0`, 11개 테이블 행 수 불변
- [x] **AC-6 (메모리 실측)** — 슬라이스의 컨테이너 수 + 메모리를 기록한다(MONO-399 AC-2 입력)
      → `iam+fan+console` **26컨 = 7.68 GiB** / 가용 11.68 GiB (팬 단독 9컨 = 2.44 GiB).
      비교: `iam+console+ecommerce` 35컨 = 9.2 GiB ⇒ 스토어와 팬은 **번갈아** 띄운다
- [x] **AC-7 (가드)** — `verify-demo-wrapper.sh` 의 가드 (y) 를 통과한다(직접 `psql` 호출 0건)
- [x] **AC-8 (발굴 결함 분리)** — 별도 티켓. 0건이면 "0건" 이라고 적는다
      → **5건**: `TASK-MONO-512` · `TASK-FAN-BE-045` · `TASK-FAN-BE-046` ·
      `TASK-FAN-FE-015` · `TASK-FAN-FE-016`

---

# 결과 (2026-08-05)

## 🔴 (가) 레시피가 든 사유는 증상이었지 원인이 아니다

티켓은 "데모 사용자 토큰에 role 이 없어 403" 이라 적었다. 그 403 은 맞다(실측).
그런데 **운영자 경로를 시도해 보지 않았다.** 시도해 보니 팬 도메인에는 운영자 경로가
아예 없다 — `FAN_OPERATOR` 를 **받는** 코드는 iam(`OperatorRoleDerivation`) ·
artist-service(`ADMIN_ROLES`) · community-service(`isOperator()`) 세 곳에 다 있는데,
그 역할을 **주는** 테넌트가 하나도 없다:

```
demo-corp assume    → roles 에 FAN_OPERATOR 없음, 게다가 게이트웨이가 먼저 자른다
                      403 {"code":"TENANT_FORBIDDEN",
                           "message":"tenant_id 'demo-corp' is not allowed"}
fan-platform assume → {"error":"invalid_grant",
                       "error_description":"operator is not assigned to the selected tenant"}
```

⇒ `FAN_OPERATOR` 는 **IdP 가 발급할 수 없는 역할**이다. → `TASK-MONO-512`

## 🔴 (나) 그것을 고쳐도 `ARTIST_POST` 는 API 로 못 넣는다

피드는 `posts.author_account_id ⋈ follows.artist_account_id` 로 잇고, 프런트는
`<FollowButton artistAccountId={artist.id}>` 로 **아티스트 엔티티 id** 를 넘긴다
(`artists` 에 `account_id` 컬럼 없음 — 컬럼 15개 전수 확인). 그런데
`PublishPostUseCase` 는 저자를 `actor.accountId()`(JWT sub)로 고정한다.
**어떤 실제 호출자도 그 행을 만들 수 없다.** → `TASK-FAN-BE-045`

(나) 때문에 (가) 가 열려도 직접-DB 는 남는다. 두 사유를 각각 `dbexec --why` 에 적었다.

## 🔴 (다) 개발 중 내 시드가 두 번 조용히 틀렸다 — 둘 다 "초록, 화면은 빔"

1. **멤버십 멱등 술어가 `"tier":"MEMBERS_ONLY"` 였다.** 해지된 멤버십도 목록에 남으므로
   한 번 해지하면 시드가 영원히 "이미 있음" 으로 건너뛴다 → `"active":true` 로 교체
2. **고정 `Idempotency-Key` 가 해지된 구독을 재생했다.** `SubscribeUseCase` 는 같은 키 +
   같은 페이로드에 **저장된 멤버십을 그대로 돌려준다** — 응답은 201, 돌아온 것은 CANCELED.
   → 키를 보유 세대로(`…-gen<N>`) 만들고, **2xx 를 믿지 않고 사후에 유효 구독을 재확인**

두 번째가 이 티켓의 교훈이다: **요청이 성공했는가**와 **원하는 상태가 됐는가**는 다르다.

## 🔴 (라) 멤버십 해지 후에도 피드가 5분간 열려 있다

상세는 즉시 403 인데 피드는 `locked:false` + 제목 + 본문 200자를 계속 준다.
redis 키 `feed:<tenant>:<account>:0:20` 를 비우면 즉시 닫힌다(인과 확정).
캐시가 **렌더된 권한 판정**을 담고 TTL(5분)로만 만료되기 때문이다. → `TASK-FAN-BE-046`

## 🔴 (마) AC-4 는 제품에 경로가 없다 — 그리고 화면이 그 사실을 가린다

- **멤버십 구독**: 클릭 → `memberships` 행 **2→2**, "결제 모듈이 설정되지 않았습니다".
  `PORTONE` env 는 compose·demo.env·컨테이너 어디에도 **0건**. 백엔드 목 PG 는 정상이다
  (시드가 API 로 구독에 성공한다). ecommerce 는 `TASK-BE-572` 에서 이 스위치를 받았고
  팬만 남은 straggler다. 게다가 같은 화면이 **"결제는 데모용 모의 PG로 처리됩니다"**
  를 하드코딩된 문구로 약속한다. → `TASK-FAN-FE-015`
- **팬 게시물 작성**: 프런트에 작성 진입점 **0개**(`FAN_POST` 참조는 타입·테스트 2곳뿐).
  API 는 201 을 낸다. → `TASK-FAN-FE-016`

🔴 그 문구 때문에 **내 첫 검증이 거짓 통과했다** — 클릭 뒤 화면에서 `구독|프리미엄` 을
찾았는데 그 단어들은 원래 페이지에 있었다. 술어를 **`memberships` 행 수**로 바꾸고서야
FAIL 이 나왔다. 리액션 항목은 반대로 **거짓 FAIL** 이었다(화면 텍스트가 안 바뀌었을 뿐
DB 는 `LIKE → LOVE`). 같은 원인의 양방향 오차다.

## 시드가 넣는 것

| 무엇 | 수 | 경로 |
|---|---|---|
| 아티스트 (SOLO 2 · GROUP_MEMBER 1) | 3 | 직접-DB |
| 아티스트 그룹 + 그룹 멤버십 | 1 + 1 | 직접-DB |
| 팬덤 | 2 | 직접-DB |
| `ARTIST_POST` (PUBLIC 2 · MEMBERS_ONLY 1 · PREMIUM 1) + 상태이력 | 4 + 4 | 직접-DB |
| 팔로우 | 2 | **API** |
| `FAN_POST` | 1 | **API** |
| 댓글 · 리액션 | 1 · 1 | **API** |
| 멤버십 (MEMBERS_ONLY) | 1 | **API** |
| 알림 (WELCOME) | 이벤트 | **넣지 않는다** — 도착을 기다리고 안 오면 경고한다 |

멤버십을 **PREMIUM 이 아니라 MEMBERS_ONLY** 로 두는 것이 요점이다. PREMIUM 이면 세
가시성이 전부 열려 AC-3 이 공허해진다.

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

- [x] `seed-fan.sh` 커밋 + 2회 실행 (실제로는 6회 — 결함 두 개를 그 사이에 잡았다)
- [x] 9개 화면 브라우저 증거
- [x] 메모리 실측 기록
- [x] 가이드 § 3 경고 제거 (+ 화면별 안내표 · 알려진 한계 5행 추가)
- [x] Ready for review
