# Task ID

TASK-MONO-679

# Title

로그인한 방문자가 보는 **DB 글에는 사진이 없다** — 백엔드가 `mediaRefs` 를 저장만 하고 돌려주지 않고, 공개 샘플과 DB 는 글 id·제목부터 다르다

# Status

ready

# Owner

monorepo

# Task Tags

- fan-platform
- contract
- demo
- content

---

# Goal

`TASK-MONO-678` 이 공개(익명) 경로에 글 사진을 실었다. 이 티켓은 **로그인한 방문자가 백엔드에서 받아 보는 글(팔로우 피드 ·
회원 상세)에도 사진이 있게** 하고, 공개 샘플과 DB 가 **다른 글 목록을 말하는 상태**를 정리한다.

---

# 🔴 정정 (2026-09-15 UTC) — 기안의 전제 하나가 틀렸다

기안(PR #3806)은 제목과 Goal 에 *"로그인하면 **같은 글의** 사진이 사라진다 — 익명으로 `/posts/[id]` 를 열면 사진이 있고,
로그인하면 회원 판을 그리므로 사진이 없다"* 고 적었다. **같은 주소에서는 그렇지 않다.** 코드를 끝까지 따라가지 않고
«로그인하면 회원 판을 그린다» 에서 멈춘 판단이었다.

| 사실 | 근거 |
|---|---|
| 로그인하면 `/posts/[id]` 는 게이트웨이를 **먼저** 묻는다 | `app/(main)/posts/[id]/page.tsx:41-44` |
| 게이트웨이가 404 를 포함해 판정 못 하면 `null` → **공개 판으로 내려간다**(사진 있음) | `features/post/ui/memberPostDetail.tsx:93-94` |
| DB 글 id 는 발행 시 **서버가 새로 만든다** | `PublishPostUseCase.java:37` `UuidV7.randomString()` — `seed-fan.sh` 도 이 API 로 발행한다 |
| 공개 샘플 글 id 는 **고정 리터럴**(`0199de80-…-00000000b001` 등) | `fixtures/raw-backend-responses.mjs` |

⇒ 공개 카드를 눌러 들어간 로그인 방문자에게 게이트웨이는 «그런 글 없음»(404)으로 답하고, 화면은 **사진이 있는 공개 판**을
그린다. 로그인 뒤에도 공개 피드·아티스트 화면은 그대로 보이고 회원 조각이 **덧붙을 뿐**이다(`app/(main)/page.tsx:59`,
`app/(main)/artists/[id]/page.tsx:41`).

**실제로 남은 차이는 둘이다** — 아래 § Context 가 그것을 다룬다:

1. **DB 글에는 사진이 아예 없다.** 팔로우 피드(`FollowingFeedSection`)와 DB 글의 회원 상세는 백엔드 응답을 그리는데,
   응답에 사진 필드가 없다.
2. **공개 샘플과 DB 는 다른 글 목록이다.** id 가 다르고(위 표) 제목·구성도 다르다(§ 두 벌). 로그인한 방문자는 한 화면
   안에서 같은 아티스트의 글을 **두 벌**(위 팔로우 피드 = DB, 아래 공개 피드 = 샘플)로 볼 수 있다.

🔴 이 정정은 **코드 읽기**로 한 것이다. 데모 백엔드가 꺼져 있어 실제 로그인 재현은 안 했다 — AC-0 이 그 재현을 요구한다.

---

# Context — 실측 (2026-09-15 UTC, `main` = `08dae23a5`, 정정 시 `e7f40f796` 에서 재확인)

## 쓰기는 되고 읽기가 없다

| 층 | 상태 |
|---|---|
| `PublishPostRequest` / `UpdatePostRequest` | `mediaRefs: List<@Size(max=1024) String>` **받는다** |
| DB `posts.media_refs` | `JSONB` 로 **저장한다** (`V1__init.sql:17`) |
| `PostResponse` · `FeedItemResponse` | 🔴 **그 필드가 없다** |
| `specs/contracts/http/community-api.md` | Publish 요청엔 `mediaRefs` 가 있고 응답 예시엔 **없다** |
| `fan-platform-web` 회원 판(`features/post`, `features/feed`) | `mediaRefs` 참조 **0건** |
| `infra/demo/seed/seed-fan.sh` | 글 발행 요청에 `mediaRefs` **안 보냄** |

## 🔴 `mediaRefs` 가 무엇을 담는지가 **정해지지 않았다**

- `PostMediaRefSerializer` 주석: *"S3 / MinIO keys, raw uploads are v2"* ⇒ **저장소 키**를 뜻한다.
- 계약 예시: `"mediaRefs": ["s3://...", "..."]` ⇒ **`s3://` 참조**.
- 678 이 공개 경로에 실은 것: **`https://images.unsplash.com/...` 절대 URL**.

⇒ 시드가 Unsplash URL 을 `mediaRefs` 에 넣으면 **필드의 선언된 의미(키)와 값(URL)이 다르다**. 응답이 그대로
돌려주면 화면은 그리겠지만, v2 업로드가 들어오는 날 같은 필드에 키와 URL 이 섞인다. 🙋 **이것은 계약 결정이다.**

## 🔴 두 벌은 이미 갈라져 있다 — 638 의 「같은 구성」은 제목 수준에서 거짓이다

| 아티스트 | 번들 시드(`RAW_POSTS`) 공개 글 제목 | 실제 시드(`seed-fan.sh`) 공개 글 제목 |
|---|---|---|
| 루미 | 첫 정규 앨범 작업을 시작했습니다 | 새 싱글 「밤의 끝」 발매 안내 |
| 노아 | 프로듀싱 노트 — 드럼 사운드 잡기 | 프로듀싱 노트를 시작합니다 |

그리고 구성도 다르다: 번들엔 노아의 **잠긴 글이 있고** 실제 시드엔 **없다**, 실제 시드엔 루미의 **PREMIUM 글이 있고** 번들엔 **없다**.
`scripts/check-seed-catalogue-parity.sh` 는 **이커머스 상품만** 대조한다(팬 글은 어느 가드도 대조하지 않는다).

🔴 **id 는 맞출 수 없다** — 발행 API 가 id 를 받지 않고 서버가 만든다. 그러므로 두 벌의 «같은 글» 판정은 id 가 아니라
**다른 키**(예: 아티스트 + 제목)로 해야 한다. 이 사실이 AC-4 의 설계를 정한다.

---

# Scope

## 포함

- `projects/fan-platform/specs/contracts/http/community-api.md` — 응답에 사진 필드 (🔴 **구현보다 먼저**)
- `projects/fan-platform/apps/community-service` — `PostResponse` · `FeedItemResponse` (+ 필요하면 `MyPostsResponse`)
- `projects/fan-platform/web/fan-platform-web/src/features/{post,feed}` — 회원 판이 사진을 그린다
- `infra/demo/seed/seed-fan.sh` — 공개 글에 사진, 그리고 번들 시드와 **글 구성·제목 정렬**
- 필요하면 팬 글의 번들↔실제 시드 대조 가드

## 제외

- 미디어 **업로드**(v2) · Blob 발행
- 공개 카드 → 회원 상세의 id 연결 — 위 § id 는 맞출 수 없다. 지금의 «404 → 공개 판» 폴백은 옳은 동작이다

---

# Acceptance Criteria

## AC-0 — 착수 전 실측

- [ ] 위 두 표를 **다시 잰다**.
- [ ] 🔴 **정정의 전제를 실제 로그인으로 재현한다** (데모 창 필요): ① 공개 카드 → `/posts/<샘플 id>` 가 로그인 상태에서도
      **사진 있는 공개 판**인가 ② 팔로우 피드의 DB 글 → 회원 상세가 **사진 없는 회원 판**인가 ③ 홈에서 팔로우 피드와
      공개 피드가 **다른 제목**으로 같은 아티스트를 보여 주는가. 창이 없으면 ⚪ 로 적고 코드 근거를 남긴다.

## AC-1 — 🙋 계약 결정: 사진 필드가 무엇을 담는가

- [ ] 갈래를 적고 **소유자가 고른다**: ⓐ `mediaRefs` 를 «표시 가능한 https URL» 로 재정의 · ⓑ 키는 `mediaRefs` 로 두고
      응답에 해석된 `mediaUrls` 를 별도로 싣는다 · ⓒ 그 밖. 🔴 에이전트가 조용히 고르지 않는다.
- [ ] 고른 결과를 `community-api.md` 에 **먼저** 반영한다(CLAUDE.md: 계약 → 구현).

## AC-2 — 응답이 사진을 싣는다

- [ ] 단건 · 피드 응답에 AC-1 의 필드.
- [ ] 🔴🔴 **잠긴 피드 항목(`locked=true`)은 제목·미리보기처럼 사진도 비운다** — 사진 경로도 본문이다
      (ADR-MONO-070 · 공개 계약이 이미 그렇게 정했다). 시험으로 문다.
- [ ] 단건 조회 403(`MEMBERSHIP_REQUIRED`) 경로에서 사진이 새지 않는다.

## AC-3 — 회원 판 화면이 사진을 그린다

- [ ] 678 의 공개 판과 **같은 표시 규칙**(카드=첫 장, 상세=전부, 로드 실패=자리만 사라짐).
- [ ] 🔵 가능하면 이미지 컴포넌트(`PublicPostImage`)를 공개 판과 **공유**한다 — 두 벌이면 한쪽만 고쳐진다.

## AC-4 — 두 시드가 같은 글 목록을 말한다

- [ ] `seed-fan.sh` 의 글이 번들 시드와 **같은 제목·같은 사진·같은 구성**(잠긴 글 유무·등급)을 갖는다.
      어느 쪽을 정본으로 할지 적고 근거를 댄다.
- [ ] 🔴 «같은 글» 의 키는 **id 가 아니다**(§ id 는 맞출 수 없다) — 무엇으로 대조하는지 적는다.
- [ ] 🔴 가드를 둘지 판단하고 근거를 적는다(선례 `check-seed-catalogue-parity.sh`). 두면 **bite** 와 **비공허성**.

## AC-5 — 게이트

- [ ] community-service 단위 + 슬라이스 + (계약 변경이므로) 통합 시험.
- [ ] `fan-platform-web` vitest · tsc · lint.
- [ ] 🔴 `seed-fan.sh` 가 **실제로** 사진 달린 글을 발행하는가는 스택이 떠야 잰다 — 창이 없으면 ⚪ 로 적고
      **`TASK-MONO-672`** 에 집을 준다.

---

# Related Specs / Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` — Posts · Feed
- `projects/fan-platform/specs/services/community-service/architecture.md` § Visibility Tiers (잠긴 항목 리댁션)
- [`ADR-MONO-070`](../../docs/adr/ADR-MONO-070-public-browsing-without-the-backend.md) — «이미지 경로도 본문이다»
- 선행: `TASK-MONO-678` · 선례: `TASK-MONO-638` Failure 2 · 측정 집: `TASK-MONO-672`

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 잠긴 피드 항목에 사진이 저장돼 있음 | 응답에서 비운다 |
| 권한 없는 단건 조회 | 403, 본문·사진 모두 없음 |
| 사진 0장인 글 | 필드는 빈 배열(키 부재 아님) — 화면이 두 모양을 가르지 않게 |
| 옛 DB 행(`media_refs` NULL) | 빈 배열로 응답 |
| 사진 URL 404 | 공개 판과 같이 자리만 사라진다 |
| 로그인 방문자가 공개 샘플 id 로 상세 진입 | 게이트웨이 404 → 공개 판(지금 동작 유지) |

---

# Failure Scenarios

1. **계약을 안 고치고 DTO 에 필드만 붙인다** → 명세와 코드가 갈라지고, 다음 사람이 계약을 믿고 짠 소비자가 틀린다.
2. **잠긴 항목의 사진을 안 비운다** → 회원 전용 사진 경로가 비회원 피드 응답에 실린다. 되돌릴 수 없는 누출.
3. **`mediaRefs` 의미를 정하지 않고 URL 을 넣는다** → v2 업로드가 같은 필드에 키를 넣는 날 화면이 깨진다.
4. **시드 사진만 넣고 제목을 안 맞춘다** → 한 화면에서 같은 아티스트의 글이 «두 벌» 로 보이는 지금 상태가 남는다.
5. **회원 판 이미지 컴포넌트를 복제한다** → 로드 실패 처리가 한쪽만 고쳐진다.
6. **두 시드를 id 로 대조하는 가드를 만든다** → id 는 서버가 만들어 영원히 불일치다. 가드는 첫날부터 빨갛고, 늘 빨간 가드는 꺼진다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Opus** (계약 결정 + 잠긴 항목 리댁션 + 두 시드 정렬이 조용히 틀리기 쉬운 자리).
