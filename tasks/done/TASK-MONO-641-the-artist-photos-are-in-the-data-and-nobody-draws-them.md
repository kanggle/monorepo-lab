# Task ID

TASK-MONO-641

# Title

🔴 아티스트 사진이 **데이터에는 있고 아무도 그리지 않는다** — `TASK-MONO-638` 이 채운 필드에 렌더러가 없다

# Status

done

# Owner

monorepo

# Task Tags

- demo
- fan
- ui
- bug

---

# Goal

`TASK-MONO-638` 이 여섯 아티스트의 `profileImageUrl` 을 채웠다. 그런데 **그 값을 읽는
컴포넌트가 하나도 없다.** 공개 아티스트 카드와 프로필 화면은 여전히 이니셜 두 글자를
그라디언트 위에 그린다.

⇒ 소유자가 요구한 «아티스트 이미지 사진 추가» 는 **화면에서 참이 아니다.** 데이터만 늘었다.

🔴🔴 이 저장소가 이름 붙인 함정이다 — **«아무도 렌더 안 하는 데이터»**. 필드를 채우는 것과
그 필드가 보이는 것은 다른 축이고, 앞엣것만 하면 시험도 가드도 초록인 채로 화면은 그대로다.

---

# Context — 실측 (2026-09-08 UTC, `main` = `4d1a498ef`)

**라이브에서 셌다** — 배포가 끝난 뒤 `https://fan.hubwang.com/artists` 를 받아서:

```
아티스트 이름           6 개   ← 638 이 늘린 것이 보인다
images.unsplash.com     0 건   ← 🔴 사진은 한 장도 없다
```

**저장소에서 셌다** — `profileImageUrl` 을 쓰는 곳:

```
$ grep -rn "profileImageUrl" projects/fan-platform/web/fan-platform-web/src
…/__tests__/public-empty-state.test.tsx:23:  profileImageUrl: null,
```

**시험 픽스처 한 줄뿐이다.** 프로덕션 코드에는 단 한 번도 안 나온다.

두 컴포넌트가 그 자리를 이니셜로 채우고 있다:

| 파일 | 지금 |
|---|---|
| `PublicArtistCard.tsx` | `h-32` 그라디언트 블록에 `stageName.slice(0, 2).toUpperCase()` |
| `PublicArtistProfile.tsx` | 같은 방식 |

🔵 **양성 대조군**: 스토어는 상품 이미지를 실제로 그린다(`store.hubwang.com/products` 에
`images.unsplash.com` 이 잡힌다). 즉 «이미지가 안 보인다» 는 CDN·CSP·계약 문제가 아니라
**이 두 컴포넌트가 그 필드를 안 읽는 것**이다.

---

# Scope

## 포함

- `projects/fan-platform/web/fan-platform-web/src/features/public-browse/ui/PublicArtistCard.tsx`
- `projects/fan-platform/web/fan-platform-web/src/features/public-browse/ui/PublicArtistProfile.tsx`
- 그 축을 무는 시험

## 제외

- 🔴 `@/features/artist` 의 `ArtistCard`(로그인 후 화면) — **다른 타입**(`Artist`)을 받고,
  두 컴포넌트를 합치지 않은 것이 의도다(`PublicArtistCard` 머리말의 근거). 이 티켓은 공개
  화면만 고친다.
- 데이터 확충(`TASK-MONO-638`, 머지됨) · 화면 캡처(`TASK-MONO-639`)
- 🔵 다만 **순서상 이 티켓이 `639` 보다 먼저**다 — 사진이 안 나오는 채로 캡처하면 팬 카드가
  이니셜만 있는 화면으로 박제된다.

---

# Acceptance Criteria

## AC-0 — 착수 전 재측정 (verify-then-act)

- [x] 🔴 라이브 `https://fan.hubwang.com/artists` 를 **다시 받아** `images.unsplash.com`
      건수를 센다. 0이 아니면 그 사이 누가 고친 것이므로 재현 조건이 사라진다 — 그때는
      «보이는가» 가 아니라 **«두 컴포넌트가 그 필드를 읽는가»** 로 판정한다.
- [x] 🔴 **양성 대조군을 함께 센다**: 같은 방법으로 `store.hubwang.com/products` 에서
      이미지가 잡히는지. 둘 다 0이면 원인이 컴포넌트가 아니라 수집 방법일 수 있다
      (0건은 «없다» 가 아니라 «내가 본 창구에 없다» 다).
- [x] `grep -rn "profileImageUrl" …/fan-platform-web/src` 결과를 적는다.

## AC-1 — 사진이 보인다

- [x] 두 컴포넌트가 `artist.profileImageUrl` 이 있으면 **사진을 그린다.**
- [x] 🔴 **없으면 지금의 이니셜 블록이 그대로 나온다.** `null` 은 오늘까지의 정상 상태였고
      (638 이전 여섯 명 전원이 `null` 이었다), 그 경로를 깨면 저장본이 비거나 수집이 실패한
      날 얼굴 자리가 통째로 깨진다.
- [x] 🔴 이미지가 **404 여도** 카드가 깨지지 않는다 — 그 칸만 이니셜로 되돌아간다.
- [x] `alt` 텍스트가 있다(스크린리더가 «이미지» 라고만 읽지 않는다).

## AC-2 — 이미지 변환 할당량을 새로 밀지 않는다

- [x] 🔵 `fan-platform-web` 은 `next.config` 에 `images: { unoptimized: true }` 라 Vercel
      Image Optimization 축을 **안 탄다**(`TASK-MONO-638` AC-5 실측). 그 사실을 **다시
      확인하고** 적는다 — 만약 이 티켓이 `unoptimized` 를 끄게 되면 이미 112% 초과인 축을
      새로 밀게 되고, 그것은 `TASK-MONO-587` 의 결정 사항이 된다.
- [x] 원본 크기를 그대로 받지 않는다(URL 에 폭 지정이 이미 있다 — 638 이 `w=400&h=400`).

## AC-3 — 가드

- [x] «데이터에 값이 있는데 화면에 안 나온다» 를 무는 시험을 둔다. 🔴 **렌더 결과에서**
      확인한다 — 컴포넌트가 그 prop 을 받는지가 아니라 **그려진 것에 그 주소가 있는가**.
- [x] 🔴 **양방향**이다: 값이 있으면 사진이 나오고, `null` 이면 이니셜이 나온다. 한쪽만
      재면 «항상 사진» 이나 «항상 이니셜» 이 통과한다.
- [x] **bite**: 렌더러를 다시 떼면 그 시험이 빨개진다.
- [x] 🔵 `public-pages.test.tsx` 의 아티스트 칸은 **저장본에서 파생**하도록 638 이 고쳐
      두었다 — 이 티켓이 그 성질을 깨지 않는지 확인한다.

---

# Related Specs / Contracts

- `TASK-MONO-638` (`review/`) — 이 필드를 채운 티켓. 🔴 frozen 이라 고쳐 쓰지 않고
  **Review Rules 대로 이 티켓을 새로 기안**했다
- [`ADR-MONO-070`](../../docs/adr/ADR-MONO-070-public-browsing-without-the-backend.md) — 공개 필드 허용 목록에 `profileImageUrl` 이 있다
- `TASK-MONO-587` — Vercel 이미지 변환 할당량(이 티켓은 안 탄다는 것을 확인만 한다)
- `TASK-MONO-639` — **이 티켓이 선행이다**(사진 없이 찍으면 이니셜 화면이 박제된다)

---

# Edge Cases

| 상황 | 기대 |
|---|---|
| `profileImageUrl` = `null` | 이니셜 블록(오늘의 동작) |
| 이미지 404 | 그 칸만 이니셜로 폴백 · 카드는 정상 |
| 저장본이 비었을 때 | 기존 «저장본이 비어 있습니다» 경로 그대로 |
| 아주 긴 `stageName` | 이니셜 두 글자 규칙 유지 |

---

# Failure Scenarios

1. **`null` 경로를 지운다** → 저장본이 비거나 수집이 실패한 날 얼굴 자리가 깨진다. 그리고
   그 상태는 638 이전의 **정상 상태**였다.
2. **컴포넌트가 prop 을 받는지만 시험한다** → 받아 놓고 안 그려도 통과한다. 이 티켓이 고치는
   결함이 정확히 그 모양이다(필드는 있었고 아무도 안 그렸다).
3. **`unoptimized` 를 끈다** → 이미 112% 초과인 변환 축을 새로 민다. 그것은 `587` 의 결정이다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** (컴포넌트 둘 + 시험. 폴백 축만 조심하면 단순하다).

---

# Verification — 구현 세션 (2026-09-09 UTC)

## AC-0 — 착수 전 재측정

| 축 | 값 |
|---|---|
| `fan.hubwang.com/artists` 의 `images.unsplash.com` | **0건** — 재현 조건 그대로 |
| `store.hubwang.com/products` (**양성 대조군**) | **2건** — 수집 방법은 유효하다 |
| `grep -rn profileImageUrl …/fan-platform-web/src` | **1건**(시험 픽스처) · 프로덕션 코드 0건 |

🔵 대조군이 있어서 «0건» 을 «없다» 로 읽을 수 있었다. 둘 다 0이었다면 원인이 컴포넌트가
아니라 내 수집 방법이었을 수 있다.

## AC-1 — 사진이 보인다

`PublicArtistAvatar` 를 새로 만들어 카드와 프로필이 함께 쓴다.

- 값이 있으면 `<img src={profileImageUrl}>`, 없으면 **지금의 이니셜 블록 그대로**.
- 🔴 `null` 경로를 지우지 않았다 — 638 이전 여섯 명 전원의 상태였고, 저장본이 비거나
  수집이 실패하면 지금도 그 값이 온다.
- 🔴 404 여도 그 칸만 이니셜로 되돌아간다(`onError`). 그것이 이 파일만 `'use client'` 인
  유일한 이유이고, 부모 둘은 서버 컴포넌트로 남는다.
- `alt` = `«{stageName} 프로필 사진»`.

## AC-2 — 변환 할당량을 새로 밀지 않는다

- `fan-platform-web` 의 `next.config` 는 `images: { unoptimized: true }` — **다시 확인했다.**
  ⇒ 이미 112% 초과인 Vercel Image Optimization 축을 **안 탄다**(`TASK-MONO-587`).
- 🔵 그래서 `next/image` 를 **일부러 안 썼다.** 이 앱 어디에도 안 쓰고 있고, 여기서만
  도입하면 그 설정과 결합이 생긴다.
- 폭·높이는 URL 이 이미 들고 있다(`w=400&h=400`, `TASK-MONO-638`) — 원본을 받지 않는다.

## AC-3 — 가드

**시험**: `src/features/public-browse/__tests__/artist-avatar.test.tsx`

🔴 «컴포넌트가 prop 을 받는가» 를 묻지 않는다 — **받아 놓고 안 그리는 것**이 이 결함의
모양이고, prop 검사는 그 상태에서도 초록이다. 묻는 것은 **그려진 결과에 그 주소가 있는가** 다.

| 칸 | 무엇 |
|---|---|
| 방향 ① 값→사진 | 카드·프로필 각각 `img[src]` 가 그 주소 · `alt` 에 예명 |
| 방향 ② `null`→이니셜 | 카드·프로필 각각 `img` 가 **없고** 이니셜이 나온다 |
| 404 폴백 | `fireEvent.error` 후 `img` 가 사라지고 이니셜이 나오며 **카드는 남는다** |
| 저장본 대조 | 저장본의 아티스트가 **전부** 사진을 들고 있고, 그 첫 명이 실제로 그려진다 |

🔴 방향이 하나면 새는 것: ①만 → «항상 사진» 통과 · ②만 → **638 직후 상태(아무도 안 그림)가
그대로 통과.** 그리고 마지막 칸이 없으면 «저장본에는 사진이 없는데 컴포넌트만 준비된»
상태가 통과한다 — 638 직후가 정확히 그 **반대** 모양이었다.

### bite

사진 경로를 떼어 항상 이니셜만 그리게 만들면(= 638 직후 상태) **4칸이 빨개진다.**
복원 후 다시 초록(`rc=0`).

## 실행한 게이트

| 게이트 | 결과 |
|---|---|
| `vitest run src/features/public-browse` (`--maxWorkers=2 --minWorkers=1`) | ✅ **51/51 통과** · 5파일 |
| bite (렌더러 제거) | ✅ **4칸 빨강** · 복원 후 rc=0 |
| `pnpm install --frozen-lockfile` (새 worktree) | ✅ rc=0 |

🔵 **처음 돌렸을 때 두 칸이 빨갰는데 그것은 컴포넌트가 아니라 내 기대가 틀린 것이었다**:
이니셜은 두 글자인데 세 글자로 적었고, `error` 는 버블하지 않아 `dispatchEvent` 로는 React
합성 핸들러가 안 불린다(`fireEvent.error` 를 써야 한다). 시험을 고쳤고 그 사실을 그 파일
주석에 남겼다 — 안 남기면 다음 사람이 같은 자리에서 «폴백이 고장났다» 로 오독한다.

🔴 **미측정**: 라이브 화면. 머지 → Vercel 배포 뒤 `fan.hubwang.com/artists` 의
`images.unsplash.com` 건수가 **0에서 6으로** 바뀌는지 확인해야 한다(그것이 이 티켓의
재현 조건을 뒤집는 유일한 측정이다).

## CORRECTION — § 미측정이었던 「라이브 화면」을 실제로 쟀다 (2026-09-08 UTC)

배포 후 `fan.hubwang.com/artists` 를 브라우저로 렌더해서 쟀다:

```
unsplash <img> = 6      실제로 그려짐 = 6      깨짐(naturalWidth=0) = 0
```

🔵 **두 축을 갈라서 쟀다.** `<img>` 태그가 있다는 것과 픽셀이 실렸다는 것은 다르고,
이 티켓의 결함이 정확히 그 갈래였다(값은 데이터에 있고 아무도 그리지 않았다). `naturalWidth`
로 재야 «그려졌다» 가 증명된다.

🔵 폴백 경로도 살아 있다 — 이 컴포넌트는 `profileImageUrl` 이 없거나 `onError` 가 나면
이니셜 블록으로 떨어진다. 라이브에서 폴백은 **0건**이고, 그것이 기대값이다(여섯 명 전부
사진을 들고 있다).
