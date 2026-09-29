# Task ID

TASK-FAN-FE-025

# Title

공개 피드(홈)에 아티스트·공개 범위 필터와 제목 검색을 추가한다 — 저장본 안에서만, 익명 게이트웨이 호출 0 유지

# Status

review

# Owner

frontend

# Task Tags

- code
- frontend
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 순수 함수 하나 + 서버 렌더 GET 폼 + 쿼리스트링 보존. 새 계약·백엔드·공용 패키지 변경 없음.

---

# Goal

2026-09-24 포트폴리오 UX 요청 § 12 는 팬 플랫폼의 비로그인 화면에 **피드·검색·필터**를 요구했다. 2026-09-29 점검에서 아티스트 디렉토리(`/artists?q=`)에는 검색이 있지만, **홈 공개 피드(`/`)는 페이지 넘김뿐이고 필터·검색이 없다**는 것을 확인했다(`src/app/(main)/page.tsx` 는 `searchParams` 에서 `page` 만 읽는다). 이 티켓은 그 공백을 메운다.

방문자는 공개 피드를 **아티스트**, **공개 범위**(공개 / 멤버 전용 / 프리미엄)로 거르고, **제목·아티스트명**으로 찾을 수 있게 된다. 모든 질의는 이미 읽은 공개 저장본(ADR-MONO-070) 안에서 돌고, 익명 방문은 여전히 백엔드로 요청을 하나도 보내지 않는다.

# Scope

## In Scope

- **순수 선택 함수** — `src/features/public-browse/lib/select.ts` 에 `filterFeedPosts(data, { artistId, visibility, q })`(이름은 구현자 판단)를 추가한다.
  - 정렬은 기존 `feedPosts` 와 **같은** `byPublishedAtDesc` 를 쓴다(같은 글이 화면마다 다른 순서로 보이면 안 된다 — 파일 주석).
  - `q` 는 `@demo/public-data` 가 이미 export 하는 `normalizeQuery` 로 정규화하고, 공백으로 나눈 토큰이 **모두** `title` + `artistStageName` 에 부분 문자열로 들어 있으면 매치한다(`queryArtists` 와 같은 약한 규칙 — 형태소 분석을 흉내내지 않는다).
  - **기본값(필터 없음)의 결과는 `feedPosts` 와 원소·순서가 같아야 한다.** 잠긴 글을 기본으로 거르지 않는다는 `feedPosts` 의 정책(🔴🔴 주석)을 그대로 유지한다. 잠긴 글이 빠지는 것은 방문자가 공개 범위를 「공개」로 **직접 고를 때뿐**이다.
- **홈 화면** `src/app/(main)/page.tsx`:
  - `searchParams` 에서 `artist`, `visibility`, `q`, `page` 를 읽는다.
  - `paginate(filtered, …, result.data.posts.length)` — `corpusSize` 는 **필터 이전** 모집단 그대로다(현재 주석이 예고한 자리: *"필터가 생기는 날 여기만 고치면 되게 한다"*). 그래서 0건일 때 `emptyKind` 가 `no-match`(조건 불일치)와 `empty-corpus`(저장본이 빔)를 계속 구별한다.
  - 필터 UI는 **서버 렌더 `<form method="get" action="/">`** 이다. 아티스트 `<select>`(저장본 `artists` 에서 채움, 「전체」 포함), 공개 범위 `<select>`(전체/공개/멤버 전용/프리미엄), 검색 `<input name="q">`, 적용 버튼, 필터가 하나라도 걸려 있을 때만 「필터 해제」 링크(`/`). 클라이언트 컴포넌트·상태·새 의존성을 추가하지 않는다(아티스트 페이지의 검색 폼과 같은 방식).
  - 폼에는 `page` 를 넣지 않는다 — 필터를 바꾸면 첫 페이지로 돌아간다.
  - `<Pagination hrefFor>` 가 걸려 있는 필터를 **보존**한다(`URLSearchParams` 로 조립, 빈 값은 생략, `page=0` 이면 `page` 생략).
  - 0건이면 기존 `PublicEmptyState` 를 쓴다(`kind` + `query={q}` + `noun="포스트"` — 그 컴포넌트는 이미 「조건에 맞는 … 찾지 못했습니다」 문구를 갖고 있다).
  - 로그인 사용자의 `FollowingFeedSection` 은 **건드리지 않는다.** 필터는 그 아래 공개 피드 영역에만 적용되며, 폼 위치·문구가 그 범위를 드러내야 한다(예: 필터를 공개 피드 제목 바로 아래에 두기).
- **잘못된 쿼리 값**: 모르는 `visibility` 값, 저장본에 없는 `artist` id 는 **무시**(전체로 취급)하고, 폼의 선택 상태도 **실제로 적용된 값**을 보여준다 — 화면이 걸려 있지 않은 필터를 걸려 있다고 말하지 않게 한다.
- **테스트**:
  - `select.ts` 유닛 — **실제 번들 시드**(`@demo/public-data/snapshots/fan.json`)로: 기본값 = `feedPosts` 와 동일(원소·순서), 아티스트별, 공개 범위별, `q`(제목 일치·아티스트명 일치·대소문자·다중 토큰 AND), 조합, 모르는 값 무시. 기대값은 시드에서 **계산**하고 숫자를 하드코딩하지 않는다(시드가 바뀌어도 테스트가 거짓이 되지 않게). 단, 비공허성을 위해 각 필터 결과가 0보다 크고 전체보다 작은 칸이 적어도 하나는 있음을 단언한다.
  - 홈 페이지 테스트(`public-pages.test.tsx` 패턴) — 필터 적용 렌더, 0건 시 `no-match` 문구, 페이지 링크의 필터 보존, **익명 렌더에서 `fetch` 호출 0회**(기존 zero-gateway 단언 패턴).
  - **bite** — 기본값에서 잠긴 글을 거르도록 함수를 일시 변조하면 «기본값 = `feedPosts`» 단언이 빨개지는지, 페이지 링크에서 필터를 빼면 보존 단언이 빨개지는지 각각 1회 실측하고 원복한다.
- 브라우저 확인(로컬 `next dev` 또는 `next start`) — 데스크톱 1280px·모바일 ~400px, 필터 적용·페이지 넘김·해제·새로고침·직접 URL 진입·뒤로가기.

## Out of Scope

- `infra/demo/public-data`(공용 패키지)의 변경 — 백엔드 피드(`GET /api/community/feed`, `community-api.md:212`)는 `page`·`size` 만 받는 **팔로우 기반 개인화 피드**라 보존해야 할 백엔드 필터 의미가 없다. 그래서 이 필터는 팬 앱 표면의 기능이고 팬 앱 안(`select.ts`)에 둔다. 공용 `query.ts` 에 넣으면 루트 티켓 범위가 된다.
- 백엔드 API·계약 변경, 로그인 사용자의 팔로잉 피드 필터.
- 정렬 옵션 추가(발행일 내림차순 고정 유지), 아티스트 디렉토리·아티스트 프로필 화면의 필터.
- 저장본 데이터(시드) 변경.

# Acceptance Criteria

- [ ] **AC-1** — `/` 에서 아티스트·공개 범위·검색어로 공개 피드를 거를 수 있고, 조합이 AND 로 동작한다. 필터 없는 `/` 의 결과는 변경 전과 원소·순서가 같다(유닛 단언 + 브라우저 대조).
- [ ] **AC-2** — 기본값에서 잠긴 글이 여전히 포함된다(`feedPosts` 정책 유지). 잠긴 글의 본문·이미지가 필터 결과 어디에도 새로 노출되지 않는다(기존 `locked-redaction` 스위트 통과).
- [ ] **AC-3** — 페이지 넘김 링크가 걸려 있는 필터를 보존하고, 필터 변경 시 첫 페이지로 돌아간다. 새로고침·직접 URL 진입·뒤로가기에서 필터 상태가 URL 그대로 복원된다(브라우저 확인).
- [ ] **AC-4** — 0건일 때 조건 불일치(`no-match`)와 저장본이 빔(`empty-corpus`)이 계속 다른 문구로 나뉜다(`corpusSize` = 필터 이전 모집단).
- [ ] **AC-5** — 모르는 `visibility`·`artist` 값은 무시되고, 폼은 실제로 적용된 값을 보여준다.
- [ ] **AC-6** — 익명 방문(필터 포함)의 게이트웨이 호출이 0회다(테스트). 새 클라이언트 컴포넌트·새 의존성 0건(`package.json` 무변경).
- [ ] **AC-7** — 유닛 테스트 + bite 2건 실측(주입 확인 → RED → 원복 → GREEN) 기록.
- [ ] **AC-8** — `tsc --noEmit` · lint · 전체 유닛 테스트 · `build` 가 로컬에서 각각 rc=0(파이프 없이 개별 실행, 종료 코드 직접 확인). 1280px·400px 브라우저 확인(필터 폼이 모바일에서 줄바꿈되며 겹치지 않음).

# Related Specs

- `projects/fan-platform/specs/services/fan-platform-web/architecture.md`
- `docs/adr/ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md` (공개 화면은 저장본만 읽는다 · D3)
- `projects/fan-platform/web/fan-platform-web/src/app/(main)/page.tsx` (홈 — `corpusSize` 주석)
- `projects/fan-platform/web/fan-platform-web/src/features/public-browse/lib/select.ts` (`feedPosts` 잠긴 글 정책 · 정렬)
- `projects/fan-platform/web/fan-platform-web/src/features/public-browse/lib/empty-state.ts`
- `projects/fan-platform/web/fan-platform-web/src/app/(main)/artists/page.tsx` (기존 서버 렌더 검색 폼 선례)
- `infra/demo/public-data/src/query.ts` (`normalizeQuery`·`paginate` — 읽기만)

# Related Contracts

- 없음. `projects/fan-platform/specs/contracts/http/community-api.md:212` `GET /api/community/feed` 는 필터 파라미터가 없고 이 티켓은 그것을 부르지 않는다.

# Target App

- `web/fan-platform-web`

# Edge Cases

- 시드가 바뀌어 어떤 아티스트의 글이 0건이 되는 경우 — 아티스트 `<select>` 에는 여전히 나오고 결과는 `no-match` 문구. 테스트 기대값은 시드에서 계산한다.
- `q` 가 공백뿐 — 정규화 후 빈 토큰이면 검색어 없음으로 취급한다(`queryArtists` 와 같다).
- `page` 가 필터 결과의 마지막 페이지를 넘는 URL — 기존 `paginate` 동작(빈 `content`)을 따르되, 이 경우 `emptyKind` 가 `no-match` 로 오판하지 않는지 확인하고 판정을 구현 기록에 남긴다(현재 홈과 같은 동작이면 그대로 둔다).
- 로그인 사용자 — 팔로잉 피드 섹션은 필터와 무관하게 그대로 보인다. 필터가 그 섹션까지 거르는 것처럼 읽히지 않게 배치한다.

# Failure Scenarios

- 필터 로직을 공용 `infra/demo/public-data/src/query.ts` 에 넣는다 → 스토어와 공유하는 루트 패키지가 바뀌어 프로젝트 티켓 범위를 넘는다.
- 필터 폼을 클라이언트 컴포넌트로 만들다가 세션·알림 모듈을 끌어와 익명 방문에 게이트웨이 호출이 생긴다(zero-gateway 불변식 붕괴 — `TASK-FAN-FE-024` 가 지킨 축).
- 기본값에서 잠긴 글을 걸러 «이 아티스트는 글이 없다»는 거짓을 만든다.
- `corpusSize` 를 필터 결과 크기로 넘겨 0건이 늘 «저장본이 비었다»로 읽힌다.
- 페이지 링크가 필터를 잃어 2페이지로 넘어가는 순간 필터가 풀린다.
- 테스트 기대값을 숫자로 하드코딩해, 시드 재발행 뒤 테스트가 틀리거나 공허해진다.

---

# Implementation Record (2026-09-29 UTC · 분석=Opus 5.5 · 구현=Opus 5.5)

## 변경 파일

- `src/features/public-browse/lib/select.ts` — `FeedFilter` · `FEED_VISIBILITIES` · `NO_FEED_FILTER` · `resolveFeedFilter`(원문 쿼리 → **적용되는** 필터, 모르는 값은 그 축 `null`) · `isFeedFiltered` · `filterFeedPosts`(`feedPosts` 위에서 AND 필터, 검색은 `normalizeQuery` 토큰이 제목+아티스트명에 전부 포함) · `feedHref`(필터 보존 링크, 빈 축·`page=0` 생략).
- `src/features/public-browse/ui/PublicFeedFilter.tsx` (신규) — 서버 렌더 `<form method="get" action="/" autoComplete="off" role="search">`. 아티스트 `<select>`(활동명 가나다순)·공개 범위 `<select>`(공개/멤버 전용/프리미엄 — 카드 배지 「멤버 전용」과 같은 표기)·검색 `<input type="search">`·적용 버튼·필터가 걸렸을 때만 「필터 해제」. `'use client'` 없음.
- `src/features/public-browse/index.ts` — 위 export 추가.
- `src/app/(main)/page.tsx` — `resolveFeedFilter` → `filterFeedPosts` → `paginate(…, corpusSize = 필터 이전 전체)`, 폼은 `FollowingFeedSection` **뒤**(필터가 공개 피드에만 걸린다는 배치), 0건은 `PublicEmptyState query={filter.q}`, 페이지 링크는 `feedHref(filter, p)`.
- 테스트: 신규 `__tests__/feed-filter.test.ts`(14칸, 실제 번들 시드에서 기대값 계산 + 비공허성), `__tests__/public-pages.test.tsx` 에 `/ (공개 피드 필터)` 7칸 추가(링크 보존 칸은 시드를 3배로 복제한 봉투 — 시드 13건으로는 필터 결과가 한 페이지를 못 넘는다).
- **무변경**: `infra/demo/public-data`(공용 패키지), `package.json`(의존성 0 추가), 백엔드·계약.

## AC 판정

- **AC-1** ✅ — 세 축 AND(유닛: 아티스트·공개 범위 3값·검색 제목/아티스트명/대소문자/다중 토큰·조합). 필터 없는 결과 = `feedPosts` 원소·순서 동일(유닛), 브라우저에서 `/` 10건·「다음」=`/?page=1` 로 변경 전 모양 유지.
- **AC-2** ✅ — 기본값에 잠긴 글 포함(유닛 + 기존 홈 칸 「잠긴 글은 티저로 나온다」 그대로 통과). `locked-redaction` 스위트 통과(필터는 봉투를 거르기만 하고 본문 필드를 만지지 않는다).
- **AC-3** ✅ — 링크 보존(유닛 `feedHref` + 페이지 렌더 칸), 폼에 `page` 없음 ⇒ 필터 변경 시 첫 페이지(브라우저: 제출 뒤 URL 에 `page=` 없음). 새로고침·직접 URL·뒤로/앞으로 복원 — 브라우저 확인(아래). 🔴 **여기서 결함 1건을 찾아 고쳤다**(아래 § 브라우저가 찾은 것).
- **AC-4** ✅ — `corpusSize` = `result.data.posts.length`(필터 이전). 불일치 → 「검색 결과가 없습니다」+ `"검색어" 와 일치하는 포스트`, 저장본 빔 → 「저장본이 비어 있습니다」(기존 칸) 그대로.
- **AC-5** ✅ — 모르는 `artist`/`visibility`/공백 `q` 무시, 폼은 「전체」·해제 링크 없음(유닛 + 렌더 칸 + 브라우저).
- **AC-6** ✅ — 필터 포함 익명 렌더 `fetch` 0회·`FollowingFeedSection` 미생성(렌더 칸). 브라우저에서 이미지가 아닌 외부 요청 0건(이미지 12건은 저장본 글의 `images.unsplash.com` — 변경 전과 같은 공개 데이터). 새 클라이언트 컴포넌트 0 · `package.json` diff 0.
- **AC-7** ✅ — bite 2건(주입 줄 수 확인 → RED → 원복 → 주입 0줄 확인 → GREEN):
  - ① `filterFeedPosts` 가 필터 없을 때 잠긴 글을 거르게 변조 → `feed-filter` 「필터가 없으면 feedPosts 와 원소·순서가 같다」 + 기존 `public-pages` 「잠긴 글은 티저로 나온다」 **2칸 RED**.
  - ② 홈의 `hrefFor` 를 옛 `(p) => p === 0 ? '/' : '/?page=' + p` 로 되돌림 → 「페이지 링크가 필터를 보존한다」 **RED**.
  - 원복 후 두 파일 40/40 GREEN.
- **AC-8** ✅ — 개별 실행·종료 코드 직접 확인: `tsc --noEmit` rc=0 · `lint` rc=0 · 전체 유닛 rc=0(**37 files / 326 tests**) · `build` rc=0(`/` ƒ 동적 라우트). 브라우저 1280px·400px 가로 넘침 0.

## 브라우저가 찾은 것 — 뒤로가기 뒤 폼이 적용되지 않은 값을 보였다 (고침)

Playwright(Chromium, `next start`) 32칸 시나리오의 첫 실행에서 「뒤로가기 → 이전 필터」 2칸(1280·400)이 빨갰다. 진단: 뒤로 간 URL(`?artist=&visibility=PUBLIC`)과 결과(6건)는 맞는데, 아티스트 `<select>` 가 **떠나기 전에 골라 두었던** 값(노아)을 보이고 있었다 — 브라우저의 폼 값 복원이 서버가 렌더한 `defaultValue` 를 덮었다. 화면이 걸려 있지 않은 필터를 걸려 있다고 말하는 것이라 AC-5 의 취지 위반.
- 고침: 폼에 `autoComplete="off"`(폼 값 복원 끔). 클라이언트 코드 없이 닫힌다.
- 재측: 수정 후 프로덕션 빌드 32칸 전체 통과를 여러 차례, 개발 서버에서도 3회 연속 32/32.

## 범위 밖 관측 — React #418 하이드레이션 오류는 **main 에 이미 있다** (고치지 않음)

같은 시나리오에서 간헐적으로 `Minified React error #418`(서버 HTML ↔ 클라이언트 불일치)이 콘솔에 떴다. 이 변경의 결함인지 가르려고 origin/main(`bda353d81`)을 별도 worktree 에 빌드해 **같은 탐색 순서**(폼 조작 없이 URL·새로고침·뒤로/앞으로·`/artists` 왕복 10단계 × 20 브라우저)를 두 서버에 태웠다:

| 빌드 | 1차 | 2차 |
|---|---|---|
| origin/main (대조군) | 11건 / 191 이동 | 9건 / 184 이동 |
| 이 변경 | 3건 / 187 이동 | 6건 / 185 이동 |

대조군에서도 필터 코드가 닿지 않는 `/artists`·`/`·reload 단계에서 같은 오류가 난다 ⇒ **이 PR 이 만든 것이 아니다.** 개발 모드(오류 원문이 나오는 판)에서는 3회 모두 재현되지 않아 불일치 지점을 특정하지 못했다. 원인 조사·수정은 별도 티켓 대상이다(이 티켓은 기록만).

🔵 측정 중 하네스 실수 1건: 같은 앱 디렉터리에서 `next dev` 를 띄워 프로덕션 서버의 `.next` 를 덮어써 CSS/JS 청크가 400 이 된 6회 측정은 **무효 처리**했다(클린 재빌드 후 재측).

## Edge Case 판정

- 마지막 페이지를 넘는 `page` — 기존 `paginate` 동작 그대로(빈 `content`, `totalElements > 0` 이므로 `emptyKind` 는 0건 판정을 내지 않는다). 변경 전 홈과 같은 동작이라 그대로 둔다.
- 공백뿐인 `q` — 적용 안 됨(`resolveFeedFilter` 가 `null`), 페이지 링크에도 안 실림(렌더 칸).
- 로그인 사용자 — `FollowingFeedSection` 은 폼 **위**에 남고 필터 영향 없음(코드 배치). 로그인 상태 브라우저 확인은 하지 않았다(로컬 IAM 없음) — 코드상 분기가 바뀌지 않았다.

## 측정하지 못한 것

- 로그인 상태의 브라우저 화면(팔로잉 피드 + 필터 배치).
- 실제 Vercel 서빙본 — 머지 뒤 확인할 일.

## CORRECTION (2026-09-29 UTC — PR #4060 CI, 머지 전) — e2e 스모크 회귀 1건을 이 PR 이 만들었고 고쳤다

- **증상**: CI `Frontend E2E smoke` 의 `e2e-smoke/home.spec.ts:35` «백엔드가 닫혀 있어도 / 가 공개 피드를 그린다» 가 `getByText('루미').first()` → `unexpected value "hidden"` 으로 빨갰다(재시도 포함 2회).
- **원인(이 변경)**: 새 필터 폼의 아티스트 `<select>` 가 활동명을 `<option>` 으로 **피드보다 앞에** 그린다. 페이지 전체의 `.first()` 가 보이지 않는 option 을 잡았다.
- 🔴 **놓친 이유**: 로컬 게이트(유닛·빌드·자작 Playwright)만 돌리고 앱의 `e2e-smoke/` 를 돌리지 않았다. 텍스트를 새로 그리는 변경은 기존 e2e 선택자와 충돌할 수 있다 — 이번 AC-8 의 게이트 목록이 그 스위트를 빠뜨렸다.
- **고침**: 선택자를 `page.getByTestId('public-feed').getByText('루미').first()` 로 좁혔다. 그 칸의 명제는 «저장본의 글이 **피드에** 보인다» 이므로 원래 뜻 그대로이고 오히려 정확해졌다. 저장소 전체에서 팬 홈 텍스트에 기대는 다른 선택자 0건(재그렙).
- **로컬 재현·확인**(`CI=1 pnpm run e2e:smoke`, 자체 서버 3002/3003 기동 — 다른 세션 서버 재사용 방지, CI 와 같게 `.env.local` 을 잠시 치움): 옛 선택자 **rc=1**(같은 실패) → 새 선택자 **rc=0, 18 passed**.
