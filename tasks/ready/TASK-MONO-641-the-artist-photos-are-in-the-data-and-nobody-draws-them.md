# Task ID

TASK-MONO-641

# Title

🔴 아티스트 사진이 **데이터에는 있고 아무도 그리지 않는다** — `TASK-MONO-638` 이 채운 필드에 렌더러가 없다

# Status

ready

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

- [ ] 🔴 라이브 `https://fan.hubwang.com/artists` 를 **다시 받아** `images.unsplash.com`
      건수를 센다. 0이 아니면 그 사이 누가 고친 것이므로 재현 조건이 사라진다 — 그때는
      «보이는가» 가 아니라 **«두 컴포넌트가 그 필드를 읽는가»** 로 판정한다.
- [ ] 🔴 **양성 대조군을 함께 센다**: 같은 방법으로 `store.hubwang.com/products` 에서
      이미지가 잡히는지. 둘 다 0이면 원인이 컴포넌트가 아니라 수집 방법일 수 있다
      (0건은 «없다» 가 아니라 «내가 본 창구에 없다» 다).
- [ ] `grep -rn "profileImageUrl" …/fan-platform-web/src` 결과를 적는다.

## AC-1 — 사진이 보인다

- [ ] 두 컴포넌트가 `artist.profileImageUrl` 이 있으면 **사진을 그린다.**
- [ ] 🔴 **없으면 지금의 이니셜 블록이 그대로 나온다.** `null` 은 오늘까지의 정상 상태였고
      (638 이전 여섯 명 전원이 `null` 이었다), 그 경로를 깨면 저장본이 비거나 수집이 실패한
      날 얼굴 자리가 통째로 깨진다.
- [ ] 🔴 이미지가 **404 여도** 카드가 깨지지 않는다 — 그 칸만 이니셜로 되돌아간다.
- [ ] `alt` 텍스트가 있다(스크린리더가 «이미지» 라고만 읽지 않는다).

## AC-2 — 이미지 변환 할당량을 새로 밀지 않는다

- [ ] 🔵 `fan-platform-web` 은 `next.config` 에 `images: { unoptimized: true }` 라 Vercel
      Image Optimization 축을 **안 탄다**(`TASK-MONO-638` AC-5 실측). 그 사실을 **다시
      확인하고** 적는다 — 만약 이 티켓이 `unoptimized` 를 끄게 되면 이미 112% 초과인 축을
      새로 밀게 되고, 그것은 `TASK-MONO-587` 의 결정 사항이 된다.
- [ ] 원본 크기를 그대로 받지 않는다(URL 에 폭 지정이 이미 있다 — 638 이 `w=400&h=400`).

## AC-3 — 가드

- [ ] «데이터에 값이 있는데 화면에 안 나온다» 를 무는 시험을 둔다. 🔴 **렌더 결과에서**
      확인한다 — 컴포넌트가 그 prop 을 받는지가 아니라 **그려진 것에 그 주소가 있는가**.
- [ ] 🔴 **양방향**이다: 값이 있으면 사진이 나오고, `null` 이면 이니셜이 나온다. 한쪽만
      재면 «항상 사진» 이나 «항상 이니셜» 이 통과한다.
- [ ] **bite**: 렌더러를 다시 떼면 그 시험이 빨개진다.
- [ ] 🔵 `public-pages.test.tsx` 의 아티스트 칸은 **저장본에서 파생**하도록 638 이 고쳐
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
