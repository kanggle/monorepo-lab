# Task ID

TASK-MONO-678

# Title

면접관이 로그인 없이 둘러보는 팬 피드에 **사진이 한 장도 없다** — 공개 글에 사진을 싣고, 화면이 그것을 실제로 그리게 한다

# Status

ready

# Owner

monorepo

# Task Tags

- demo
- public-data
- content
- frontend

---

# Goal

`fan.hubwang.com` 을 로그인 없이 연 방문자(면접관)가 **사진이 있는 피드**를 본다.

소유자 요청(2026-09-15): *"면접관이 로그인 없이도 사이트를 둘러볼 수 있게 사진이 포함된 공개 피드 추가"*.

🔴🔴 **이 티켓의 어려운 부분은 «사진 URL 넣기» 가 아니라 «넣은 사진이 보이게 하기» 다.** `TASK-MONO-638`
이 아티스트 사진을 채웠는데 **그 필드를 읽는 코드가 0개**라 `TASK-MONO-641` 이 따로 필요했다. 글
사진(`imageUrls`)은 지금 정확히 그 상태다 — 계약에 필드가 있고, 변환기가 채우고, **아무도 안 그린다.**

---

# Context — 실측 (2026-09-15 UTC, `main` = `08dae23a5`)

## 공개 피드가 사는 곳 — 하나뿐이다

| 질문 | 실측 |
|---|---|
| 라이브 `fan.hubwang.com/` 의 출처 배너 | **「샘플 데이터 (아직 발행 전)」** ⇒ 봉투 `source = bundled` |
| 그 데이터 | 저장소에 커밋된 `infra/demo/public-data/snapshots/fan.json` (Vercel 빌드가 함께 싣는다) |
| Vercel Blob 저장본 | **안 쓰인다** (`DEMO_PUBLIC_DATA_BASE_URL` 미설정 — 발행은 638 때부터 소유자 승인 대기) |
| DB | **안 거친다** — 공개 화면은 게이트웨이를 부르지 않는다(`public-pages.test.tsx` 가 fetch 0건을 단언) |

⇒ **이 티켓의 변경은 «저장소 커밋 → Vercel 재배포» 로만 라이브에 닿는다.** DB 에는 안 들어간다.

## 사진 축의 현재 상태

| 층 | 상태 |
|---|---|
| 공개 계약 `datasets.ts:95` | `imageUrls: string[]` **있다** ("공개 글의 이미지만. 잠긴 글은 항상 빈 배열") |
| 봉투 검증 `contract.mjs:202` | 잠긴 글에 이미지가 오면 **거부한다** — 공개 글의 URL 모양은 **안 본다** |
| 변환기 `transform.mjs:121` | `imageRefs` / `images` 를 읽어 `imageUrls` 로 싣는다(잠긴 글은 `[]`) |
| 픽스처 `RAW_POSTS` | 공개 글 **6개 전부 이미지 필드 없음** (잠긴 음성 대조군 `b004` 만 MinIO 주소를 든다) |
| 스냅샷 `fan.json` | 글 12 · 공개 6 · **`imageUrls` 가 비어 있지 않은 글 0** |
| 화면 `fan-platform-web/src` | `imageUrls` 를 참조하는 **렌더 코드 0건**(시험 파일·주석만) |
| Vercel Image Optimization 축(`TASK-MONO-587`) | `next.config.ts` `images.unoptimized: true` ⇒ **이 앱은 그 축을 안 탄다** |

## 로그인한 사람의 경로 — 이 티켓 밖이고 이유가 있다

community-service 는 `mediaRefs` 를 **받아서 저장만** 한다(`PublishPostRequest` · `media_refs JSONB`).
`PostResponse` · `FeedItemResponse` 는 그 필드를 **돌려주지 않는다**. 그러므로 로그인한 방문자가
`/posts/[id]` 로 들어가면(회원 판은 게이트웨이를 그린다) 공개 판에 있던 사진이 **안 보인다**.
그걸 고치려면 **계약 변경**이 필요하다 ⇒ **`TASK-MONO-679`** 가 들고 간다.

---

# Scope

## 포함

- `infra/demo/public-data/fixtures/raw-backend-responses.mjs` — 공개 글 6개에 `imageRefs`
- `infra/demo/public-data/snapshots/fan.json` — **재생성으로만** 바뀐다(`bin/build-bundled-snapshots.mjs`)
- `infra/demo/public-data/src/contract.mjs` — 공개 글 `imageUrls` 의 모양(문자열 · `https:`) 검증
- `infra/demo/public-data/tests/public-data.test.mjs`
- `projects/fan-platform/web/fan-platform-web/src/features/public-browse/ui/` — 카드·상세가 사진을 그린다
- `projects/fan-platform/web/fan-platform-web/src/features/public-browse/__tests__/`

## 제외

- 🔴 `infra/demo/seed/seed-fan.sh` · community-service · 회원 판 화면 — **`TASK-MONO-679`**
- Vercel Blob 발행 — 638 과 같은 이유로 소유자 승인 대기 중인 인프라 작업
- 미디어 업로드 기능(community-service v2 범위)

## `projects/<name>/` 영향

`projects/fan-platform/web/fan-platform-web` 의 공개 브라우징 슬라이스 **하나**. 백엔드·계약 변경 없음.

---

# Acceptance Criteria

## AC-0 — 착수 전 실측 (verify-then-act)

- [ ] 위 표의 수(글 12 · 공개 6 · 이미지 있는 글 0 · 렌더 코드 0)를 **다시 세서** 적는다.
- [ ] 손대지 않은 트리에서 `build-bundled-snapshots.mjs --check` 가 `rc=0` 인지 확인한다.
      🔴 드리프트가 있으면 늘리기 전에 그것부터 처리한다(`TASK-MONO-640` 이 CRLF 오진을 고쳐 두었다).

## AC-1 — 공개 글이 사진을 갖는다

- [ ] 공개 글 **6개 전부** 1장 이상. 적어도 하나는 **2장 이상**(상세의 여러 장 표시가 시험되게).
- [ ] 이미지는 스토어·아티스트와 **같은 방식**(Unsplash CDN URL) — 새 이미지 호스트를 들이지 않는다.
- [ ] 🔴 **주소를 지어내지 않는다** — 넣는 URL 은 **그 문자열 그대로** 찔러 `200 image/*` 인 것만.
- [ ] 🔴🔴 **실존 인물의 식별 가능한 얼굴을 넣지 않는다**(638 AC-2 와 같은 이유) — 후보를 **열어서 보고** 고른다.
      악기·무대·스튜디오처럼 글 내용과 맞는 사진을 고른다.
- [ ] 🔴 잠긴 글의 `imageUrls` 는 **여전히 `[]`**. 음성 대조군 `b004` 의 MinIO 주소를 **지우지 않는다**.

## AC-2 — 봉투가 이상한 사진 주소를 거부한다

- [ ] 공개 글의 `imageUrls` 가 배열이 아니거나, 문자열이 아니거나, `https:` 절대주소가 아니면 거부한다.
      선례: 포인터의 `url` 검사(`contract.mjs:163`). 🔵 이 봉투는 저장소 밖(Blob)에서도 오므로
      `javascript:` · `http:`(mixed content) 가 화면까지 가면 안 된다.
- [ ] **bite**: 각 거부 사유를 칸으로 시험하고, 정상 봉투는 통과하는 **대조군**을 함께 둔다.

## AC-3 — 화면이 사진을 그린다

- [ ] 공개 피드 카드: 첫 장을 썸네일로 그린다. 여러 장이면 개수를 알린다.
- [ ] 공개 상세: 전부 그린다.
- [ ] 🔴🔴 **잠긴 글에서는 `<img>` 가 하나도 안 나온다 — 계약이 깨진 입력이 와도.**
      `locked-redaction.test.tsx` 의 «불가능한 입력» 이 이미 `imageUrls` 를 들고 있다 — 그 입력에
      `<img>` 0개를 단언한다. 🔵 **대조군**: 같은 입력을 `locked: false` 로 주면 `<img>` 가 나온다
      (없으면 «사진을 아무 때도 안 그린다» 도 위 칸을 통과한다).
- [ ] 🔴 `<img>` 에 의미 있는 `alt`.
- [ ] 🔴 **실제 시드로 그린 피드**에서 `<img>` 수가 «공개 글 중 이미지가 있는 글 수» 와 같다
      (첫 페이지 크기에 의존하지 않게 목록 컴포넌트 수준에서).

## AC-4 — 사진이 죽어도 글은 산다

- [ ] 이미지 로드 실패(404 등) 시 **그 자리만 사라지고** 제목·본문은 그대로다. 깨진 이미지 아이콘을 남기지 않는다.
- [ ] 🔴 그 처리(`onError`)는 클라이언트 컴포넌트가 필요하다 — **글 객체를 통째로 넘기지 않는다**
      (`PublicPostCard` 머리말 ③: 클라이언트 컴포넌트에 `post` 를 넘기면 봉투 필드가 RSC 페이로드에 실린다).
      URL 과 alt 만 넘긴다.

## AC-5 — 공개 경로의 성질이 유지된다

- [ ] 익명 렌더에서 `fetch` 0건(`public-pages.test.tsx`)이 **그대로 초록**.
- [ ] `images.unoptimized: true` 를 **바꾸지 않는다** — 바꾸면 이미 초과된 변환 축(587)을 탄다.
- [ ] 누출 대조군이 여전히 문다(생성기의 `assertNoLeak` · 패키지 시험).

## AC-6 — 게이트 + 라이브

- [ ] `node --test infra/demo/public-data/tests/public-data.test.mjs` 전부 통과(현재 개수를 재서 적는다).
- [ ] `build-bundled-snapshots.mjs --check` rc=0.
- [ ] `fan-platform-web`: `vitest run` · `tsc --noEmit` · `next lint`.
- [ ] 머지 → Vercel 배포 뒤 `fan.hubwang.com/` 에서 글 사진 `<img>` 가 **그려지는지** 잰다(641 처럼 «HTML 에 있다» 가 아니라 «깨지지 않고 보인다»).
      🔴 **배포 시각이 머지 시각 뒤인지 먼저** 확인한다(머지+CI초록 ≠ 배포됨).

---

# Related Specs / Contracts

- [`ADR-MONO-070`](../../docs/adr/ADR-MONO-070-public-browsing-without-the-backend.md) — 공개 필드 허용 목록 · 잠긴 글 · 봉투 계약
- `infra/demo/public-data/src/datasets.ts` — `PublicPost.imageUrls` (공개 계약의 정본)
- `infra/demo/public-data/README.md` § 시드 (스냅샷은 손으로 쓰지 않는다) · § 이미지
- `TASK-MONO-638` (데이터 확충) · `TASK-MONO-641` (채웠는데 아무도 안 그린 사진) · `TASK-MONO-587` (이미지 변환 축)
- 후속: `TASK-MONO-679` (로그인한 사람의 경로)

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| 사진 URL 404 | 그 사진 자리만 사라진다. 카드·본문은 그대로 |
| 잠긴 글인데 봉투에 이미지가 있음 | 봉투 거부(계약) + 화면도 안 그림(화면 계층) — 두 겹 |
| 공개 글 `imageUrls` 가 `http:` | 봉투 거부 |
| 공개 글인데 사진 0장 | 사진 자리 없이 예전 카드 그대로 |
| 사진 여러 장 | 카드는 첫 장 + 개수, 상세는 전부 |

---

# Failure Scenarios

1. **데이터만 넣고 화면을 안 고친다** → 시험·가드 전부 초록인데 라이브엔 사진 0장. 641 이 이름 붙인 바로 그 모양.
2. **`snapshots/fan.json` 을 손으로 편집한다** → 다음 재생성에서 조용히 사라지고 «사진이 없어졌다» 로 보인다.
3. **잠긴 분기에서 `post.imageUrls` 를 참조한다** → 계약이 깨진 날 회원 전용 사진 경로가 HTML 에 실린다.
4. **클라이언트 컴포넌트에 `post` 를 통째로 넘긴다** → 화면에 안 그려도 RSC 페이로드에 봉투가 실린다.
5. **주소를 확인 없이 넣는다** → 638 실측에서 후보 56개 중 13개가 죽어 있었다.
6. **얼굴이 식별되는 군중·인물 사진을 고른다** → 실존 인물의 초상이 포트폴리오에 들어간다.
7. **`images.unoptimized` 를 끈다** → 이미 112%/320% 인 변환 축을 조용히 청구로 민다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (데이터 + 화면 컴포넌트 두 개 + 시험. 설계 판단은 이 티켓에 이미 내려져 있다 — 잠긴 분기·클라이언트 경계·계약 검증).
