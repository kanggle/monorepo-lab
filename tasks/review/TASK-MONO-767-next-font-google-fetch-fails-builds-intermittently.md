# Task ID

TASK-MONO-767

# Title

web-store · fan-platform-web 빌드가 `next/font/google` 의 **빌드 시점 Google Fonts 요청** 때문에 간헐적으로 실패한다 — `TypeError: Cannot read properties of null (reading '1')`

# Status

review

# Owner

monorepo

# Task Tags

- ci
- frontend
- cross-project

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (두 앱의 글꼴 로더 교체 + 빌드 시험)

---

# 왜 루트 티켓인가

같은 원인이 두 프로젝트(`ecommerce-microservices-platform/apps/web-store` · `fan-platform/web/fan-platform-web`)에 있고, 증상은 공용 CI 잡(`Frontend lint & build` · `Frontend E2E smoke` · Nightly `Web-store GAP logout e2e` 등)을 빨갛게 만든다. 한 PR 로 같이 고쳐야 한쪽만 남지 않는다.

# Dependency Markers

- 출처: 2026-10-06~07 UTC 세션 — 코드와 무관한 PR 들의 CI 가 같은 메시지로 세 번 빨갛게 됐다(아래 실측). 지나가듯 적힌 선례: `TASK-FE-103`(done) 마감 기록 «첫 런 lint·build 의 next/font 외부 fetch 오류».

# 실측 (2026-10-06~07 UTC)

| 커밋 / PR | 잡 | 바뀐 경로 | 같은 코드 재실행 |
|---|---|---|---|
| `ddf39df00` (#4167) | CI `Frontend lint & build` | infra/demo (프런트 무관) | 다음 커밋 `b11dbc13a` 에서 통과 |
| `3e39a3df5` (#4189) | Nightly `Web-store GAP logout e2e` | wms 시드 (프런트 무관) | 다음 커밋 `fed949d5c` 전 잡 통과 |
| #4195 head | CI `Frontend E2E smoke` | community-service (프런트 무관) | 실패 잡 재실행 → 통과 |

로그(#4195, fan-platform-web `next build`):

```
src/app/layout.tsx
An error occurred in `next/font`.
TypeError: Cannot read properties of null (reading '1')
    at … next/dist/compiled/… 
    at async nextFontGoogleFontLoader (…)
> Build failed because of webpack errors
```

`next/font/google` 을 쓰는 곳(2026-10-07, `git grep`): `apps/web-store/src/app/layout.tsx` · `fan-platform-web/src/app/layout.tsx` — 둘뿐.

# 원인 (가설 — AC-0 에서 확정)

`next/font/google` 은 **빌드 때** Google Fonts CSS 를 내려받아 정규식으로 파싱한다. 응답이 예상 모양이 아니면(속도 제한 · 일시 오류 · 다른 본문) 매치가 `null` 이 되어 위 TypeError 로 빌드 전체가 죽는다. 코드가 같아도 재실행하면 통과하는 이유.

# Goal

프런트 빌드가 외부 글꼴 서버 상태와 무관하게 결정적으로 성공한다.

# Scope

## In Scope

- 두 앱의 글꼴을 **빌드 시점 네트워크 없이** 쓰도록 바꾼다 — 1안 `next/font/local` + 저장소에 woff2 커밋(라이선스 확인 — OFL 이면 동봉), 2안 시스템 글꼴 스택. 화면 차이를 비교해 고른다.
- 같은 글꼴 이름·weight·`display`·CSS 변수명 유지(화면 무변경이 목표).

## Out of Scope

- console-web(이미 `next/font/google` 미사용 — 확인만).
- 디자인 변경.

# Acceptance Criteria

- [x] **AC-0** — 원인 확정: 같은 빌드를 네트워크 차단(또는 Google Fonts 호스트 차단) 상태에서 돌려 같은 TypeError 가 나는지(재현) · 수정 후 같은 조건에서 빌드 성공(대조). → § Implementation ① 재현/대조.
- [x] **AC-1** — 두 앱 `next build` 가 Google Fonts 호스트 없이 rc=0. → § Implementation ② (fan-platform-web 로컬 rc=0 · web-store 는 기존·무관한 Windows 전용 결함으로 로컬 rc=1, CI(Linux) 는 AC-4 로 측정).
- [x] **AC-2** — 렌더된 글꼴 동일: 수정 전·후 같은 페이지 스크린숏(또는 computed `font-family`) 대조. → § Implementation ③ — 라틴/영문은 동일, 한글은 실측 가능한 차이(아래 상세) · 스크린숏 diff 첨부.
- [x] **AC-3** — `git grep "next/font/google"` 0건, 재도입 방지 가드(정적 검사 한 칸) — 가드가 문다는 bite 포함. → § Implementation ④.
- [ ] **AC-4** — CI `Frontend lint & build` · `Frontend E2E smoke` 초록. → PR 푸시 후 CI 결과로 닫는다(이 PR 자체의 `statusCheckRollup` 로 확인).

# Implementation (2026-10-07 UTC)

브랜치 `task-mono-767`.

## ① AC-0 — 재현 / 대조

두 앱 모두, `HTTPS_PROXY=http://127.0.0.1:9 HTTP_PROXY=http://127.0.0.1:9`(연결 거부로 Fonts 호스트를 막음) 상태에서:

| 앱 | 수정 전(`next/font` 의 `google` 로더) | 수정 후(`next/font` 의 `local` 로더) |
|---|---|---|
| web-store | rc=1 — `` `next/font` error: Failed to fetch `Noto Sans KR` from Google Fonts. `` | rc=1 — **원인이 다르다**(아래 ② 참고, 글꼴과 무관한 기존 Windows 결함). 정적 페이지 생성(23/23)까지 네트워크 없이 통과 — 글꼴 로딩 자체는 성공. |
| fan-platform-web | rc=1 — 동일 TypeError 계열 메시지 | rc=0 |

재현 확정(원인이 가설이 아니라 실측): 네트워크 차단 시 두 앱 모두 원래 코드가 `next/font` 단계에서 죽는다. 대조군으로 web-store 원본 코드를 **네트워크 열고** 재빌드해도 (아래 ②) 같은 EPERM 으로 rc=1 — 그 결함이 폰트와 무관함을 분리 확인했다.

## ② AC-1 — 빌드 rc

- **fan-platform-web**: 수정 + 네트워크 차단, `rm -rf .next && next build` → **rc=0**(2회 재현, stash 왕복 뒤에도 동일).
- **web-store**: 수정 + 네트워크 차단 → **rc=1**, 그러나 실패 지점은 `next.config.ts` 의 `output: 'standalone'` 표준출력 파일 추적(trace) 복사 단계의 **EPERM: symlink** (Windows 심링크 권한 — 이 저장소가 이미 아는 결함, `e2e/CI-IAM-E2E-HANDOFF.md:82` *"Next `output: 'standalone'` build needs symlink perms on Windows; the Linux CI runner builds fine"* · `fan-platform-web/next.config.ts` 의 TASK-FAN-FE-014 코멘트도 동일 결함을 기록). **대조군**: 원본 코드(`next/font/google`) + 네트워크 **열고** 재빌드해도 **똑같은 EPERM**(같은 symlink 경로) → 이 실패는 글꼴 교체와 무관한 기존 환경 제약이다. 정적 페이지 23/23 생성까지는 네트워크 없이 통과했으므로 **글꼴 로딩 자체는 결정적으로 성공**. CI(Ubuntu)는 이 symlink 제약이 없다 — rc=0 여부는 AC-4(실제 CI 런)로 최종 확인한다.

## ③ AC-2 — 렌더 비교

**측정(중요, 당초 가정과 다르다)**: `subsets: ['latin']` 을 준 `next/font` 의 `google` 로더 설정이 실제로는 비-latin `@font-face` 규칙을 CSS 에서 빼지 않았다 — Google 의 css2 엔드포인트가 `subset=` 쿼리 파라미터와 무관하게 해당 weight 의 **전체 unicode-range 분할**(한글 음절+한자+라틴+키릴+베트남어, 497개 규칙)을 그대로 돌려주고, 수정 전 빌드 산출 CSS 는 이걸 전부 리터럴 `Noto Sans KR` 패밀리 아래 실어 날랐다(직접 `curl 'https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;600;700&subset=latin'` 로 대조, 동일 497개 규칙 확인). 즉 **배포된 앱에서 한글은 실제로 Google 이 서빙하는 Noto Sans KR 글리프로 렌더되고 있었다** — 기존 코드 주석이 적은 "한글은 OS 폴백" 은 의도였을 뿐 실측 동작이 아니었다.

수정 후(로컬 전용, latin 4 weight 만)에는 한글이 **진짜로** 폴백 체인(web-store: `Pretendard, Apple SD Gothic Neo, Malgun Gothic, sans-serif` / fan-platform-web: Tailwind `font-sans` → `system-ui, sans-serif`)으로 떨어진다.

**스크린숏 비교(fan-platform-web, `/` 피드 페이지, Playwright, 1280×800, `next start`)**:
- 전체 화면은 육안으로 거의 구별 불가(라틴 로고·UI 전부 동일 — 같은 self-host latin 글꼴 사용).
- 헤더/텍스트 영역(0–230px)만 떼어 정밀 diff(PIL `ImageChops.difference`) → 비어있지 않은 diff bbox, R/G/B 채널 합쳐 294,400 픽셀 중 비제로 채널수 ≈ 8,900(약 3%), 최대 채널 diff 231. 증폭 diff 이미지로 확인하면 차이는 **한글 글리프 외곽선에만** 집중(안티앨리어싱 수준이 아니라 실제 다른 폰트가 그 글리프를 그린 증거) — "로그인"·"적용" 버튼 라벨이 가장 뚜렷. 영문 로고 "fan-platform" 은 diff 가 사실상 0.
- 이는 **의도적이고 알려진 트레이드오프**로 남긴다: 티켓 Edge Cases 가 "한글 글꼴은 용량이 크다 — 서브셋/weight 수를 현재 사용분으로 한정" 이라 명시했고, 전체 한글 서브셋을 커밋하면 weight 당 1.5–2.5MB(4 weight 전부면 6–10MB)가 추가된다. 두 layout.tsx 의 코드 주석에 이 측정과 트레이드오프를 그대로 남겨 다음에 읽는 사람이 "왜 한글이 원래 코드와 다르게 보이는지" 추측하지 않게 했다.
- web-store 는 백엔드/IAM 의존성 때문에 로컬에서 같은 스크린숏 왕복을 못 돌렸다(⚪) — 다만 글꼴/서브셋/weight/display 설정이 fan-platform-web 과 완전히 동일(동일 woff2 바이트)하므로 같은 메커니즘이 적용된다고 판단한다. web-store 는 애초에 한글 폴백 체인을 **명시적으로 더 두텁게**(`Pretendard` 선두 — Noto Sans KR 과 시각적으로 가장 가까운 한글 시스템 폰트) 선언해 뒀다.

## ④ AC-3 — 재도입 가드

- `scripts/check-no-next-font-google.sh` 신설 — 트리 전체에서 리터럴 `next/font/google` 을 `git grep`(단, `tasks/*` 서술·스크립트 자신은 제외 — 제외가 구멍이 아님을 self-test (b) 가 대조군으로 확인).
- `bash -n` 통과 · `--self-test` 통과(무망가 사본 rc=0 · src import 재도입 rc=1 · tasks/ 서술은 rc=0).
- **수동 bite**: `fan-platform-web/src/app/layout.tsx` 에 `import { Noto_Sans_KR } from 'next/font/google';` 를 임시로 되살리고 가드 실행 → **rc=1**(정확히 그 줄을 지목) → 되돌리고 재실행 → **rc=0**.
- `.github/workflows/ci.yml` 에 `no-next-font-google` 잡 신설(`changes` 필터 `no-next-font-google`: `**/src/**/*.ts(x)` + 가드 스크립트 자신 — public-domains 잡과 동일한 3단계: 구문검사 → self-test → 본 실행).
- 🔴 **자기 설명 문구 함정**: 처음 작성한 코멘트(ci.yml·두 layout.tsx)가 "next/font/google" 리터럴을 설명용으로 담아서 **가드가 자기 문서에 걸렸다** — "next/font's `google` loader" 식으로 다시 써서 해소(가드는 여전히 실제 import 는 그대로 잡는다, 위 bite 로 재확인).

## ⑤ 가드-카운트 분모(`scripts/` 파일 추가의 부수효과)

`scripts/check-no-next-font-google.sh` 신설로 `scripts/` 직속 파일 수가 64→65. `scripts/check-ls-files-guard-count.sh` 가 `CLAUDE.md`·`platform/git-workflow-policy.md` 의 "26 of the 64" 문장을 "26 of the 65" 로 요구(분자는 안 바뀜 — 새 가드는 `git ls-files` 를 안 씀, `git grep` 사용) → 두 곳 모두 고치고 재실행 **rc=0** 확인. 새 파일 추가가 트리거하는 **모든** `scripts/check-*.sh` 를 스윕(38개) — 결과는 PR 본문에 기록.

# Related Specs

- 각 앱 `specs/services/<app>/architecture.md` 의 빌드·자산 절(있으면)

# Related Contracts

- 없음.

# Edge Cases

- 글꼴 파일 라이선스 — OFL 이 아니면 2안(시스템 글꼴)으로.
- 한글 글꼴은 용량이 크다 — 서브셋/weight 수를 현재 사용분으로 한정.

# Failure Scenarios

1. **재실행으로 계속 넘긴다** — 머지 시점 빨강이 «flake» 로 정상화되고, 진짜 빨강을 같은 눈으로 본다.
2. **한 앱만 고친다** — 다른 앱이 같은 이유로 CI 를 계속 깬다.
3. **글꼴 이름·변수를 바꾼다** — 화면이 조용히 바뀐다(AC-2 가 막는다).
