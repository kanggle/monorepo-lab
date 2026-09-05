# Task ID

TASK-PC-FE-274

# Title

`console-web` 의 Next 판이 **Vercel 배포에서 거부된다** — 빌드는 성공하고 그 다음에 죽는다

# Status

in-progress

# Owner

platform-console

# Task Tags

- frontend
- dependency
- deployment
- blocks-mono-585

---

# Goal

`console-web` 의 `next` / `react` / `react-dom` / `eslint-config-next` 를 **형제 앱 셋과 같은
레인지**로 올려, `kanggle-console` 의 프로덕션 배포가 성립하게 한다.

이 티켓은 `TASK-MONO-585`(ADR-MONO-067 단계 3)의 **라이브 검증을 막고 있는 유일한 것**이다.
585 는 저장소 몫을 다 끝내고 `review/` 에 있으며, 첫 배포가 생겨야 닫힌다.

# Scope

**In**

- `projects/platform-console/apps/console-web/package.json` — 위 4개 의존성의 스펙
- `projects/platform-console/apps/console-web/pnpm-lock.yaml` — 재생성
- `projects/platform-console/docs/conventions/frontend-ui.md` § 5.3 의 버전 표(§ Edge Cases ②)

**Out**

- `vitest`(현 `^2.1.4`)·`@testing-library/*`·`typescript` — 이 축과 무관하고, 한 번에 올리면
  테스트가 빨개졌을 때 **원인이 둘 이상**이 된다. 필요가 실측되면 별도 티켓.
- 형제 앱 3개 — 이미 배포되고 있다. 건드릴 이유가 없다.
- 앱 코드 — 이 티켓은 **의존성 판 교체**다. 코드를 고쳐야 한다면 그 자체가 보고 대상이다
  (§ Failure Scenarios ①).

---

# 🔴 왜 이 티켓이 존재하는가 — 실측 (2026-09-05 UTC)

## ① 배포는 **빌드 실패가 아니다**. 빌드는 성공했고 그 다음에 거부됐다

`kanggle-console` 의 첫 프로덕션 배포(`dpl_ZmABBJsKpkxrcE2oMD2Er3KqU5XQ`,
GitHub deployment `6281458564`, 커밋 `462e9bd96`)의 로그 꼬리:

```
Build Completed in /vercel/output [2m]
Deploying outputs...
Vulnerable version of Next.js detected, please update immediately.
```

GitHub deployment status API — **양성 대조군을 옆에 두고** 읽었다:

| deployment | 프로젝트 | state |
|---|---|---|
| `6281458564` | `kanggle-console` | 🔴 **failure** |
| `6281101895` | `kanggle-store` | ✅ success (같은 날, 대조군) |

🔵 **이 구별이 중요한 이유**: 「빌드가 깨졌다」면 코드를 고쳐야 하고, 「빌드는 됐는데
배포가 거부됐다」면 **의존성 판**만 문제다. 로그를 안 읽고 「배포 실패」로만 읽었다면
이 티켓은 코드 조사가 됐을 것이다. 실제 고칠 것은 `package.json` 네 줄이다.

## ② 판별자 — 배포되는 셋과 안 되는 하나가 **정확히 이 축에서 갈린다**

| 앱 | `next` 스펙 | 락 해석값 | `react` 해석값 | Vercel |
|---|---|---|---|---|
| **`console-web`** | **`15.0.3`** (정확 핀) | **15.0.3** | **19.0.0-rc-66855b96-20241106** | 🔴 **거부** |
| `fan-platform-web` | `^15.1.0` | 15.5.15 | 19.2.5 | ✅ |
| `web-store` | `^15.1.0` | 15.5.14 | 19.2.4 | ✅ |
| `auth-forwarder` | (범위) | 15.5.25 | 19.2.8 | ✅ |

**4개 중 1개만 거부되고, 그 1개만 15.0.x 이며, 그 1개만 정확 핀이고, 그 1개만 React RC 다.**
네 축이 같은 앱에 겹쳐 있어 이 표만으로는 «어느 축이 원인인가» 를 못 가른다 — 하지만
**처방은 같다**: 형제와 같은 레인지로 옮기면 네 축이 동시에 해소된다. 축 분리는 이 티켓의
목적이 아니다(그걸 하려면 실패하는 배포를 반복 생성해야 하고, 배포 한도는 계정 전체다).

## ③ RC 와 15.0.x 는 **한 덩어리**다 — 따로 못 올린다

Next 15.0.x 는 React 19 **RC** 를 요구했고 15.1 부터 React 19 **stable** 을 받는다.
그래서 `next` 만 올리는 선택지는 없다. 형제 셋이 전부 `^15.1.0` + `^19.0.0` 인 것이
그 짝의 증거다.

🔵 부수 효과 — 지금 `@types/react`/`@types/react-dom` 은 이미 `^19.0.0`(stable) 로
떠 있는데 런타임만 RC 다. 이 불일치도 같이 해소된다.

## ④ 폭발 반경은 **이 앱 하나**

`console-web` 은 자기 `pnpm-lock.yaml` 을 따로 갖는다(`projects/platform-console/` 에도,
저장소 루트에도 워크스페이스 락이 없다). 형제 앱은 각자 다른 락을 쓴다.

---

# Acceptance Criteria

- [x] **AC-0 (착수 시 재측정 — 상속 금지)** — 위 § 실측의 네 값을 **다시 재고** 티켓에 적는다:
      (a) `6281458564` 의 state 가 여전히 `failure` 인가 · (b) 네 앱의 `next`/`react` 스펙과
      락 해석값 · (c) `console-web` 이 여전히 자기 락을 단독 소유하는가 · (d) 🔴 **형제의
      해석값은 오늘 다시 재라** — `^15.1.0` 은 **설치 시점의 최신 15.x** 로 풀리므로 위 표의
      15.5.14/15/25 는 **오늘의 값이지 성질이 아니다**.
      🔵 (a) 가 뒤집혀 있으면(누군가 재배포했고 성공) **멈추고 보고** — 이 티켓의 전제가 없다.

- [x] **AC-1** — `package.json` 의 네 줄을 형제와 **문자 그대로 같은 스펙**으로 바꾼다:
      `next: ^15.1.0` · `react: ^19.0.0` · `react-dom: ^19.0.0` · `eslint-config-next: ^15.1.0`.
      🔴 **내가 고른 숫자를 새로 발명하지 않는다** — 형제에서 복사한다. 근거는 「최신이라서」가
      아니라 **이 저장소에서 실제로 배포되고 있는 조합이라서**다.

- [x] **AC-2** — `pnpm install` 로 락을 재생성하고, **해석된 실제 버전을 티켓에 적는다**
      (`next` · `react` · `react-dom`). AC-0(d) 때문에 이 값은 미리 못 적는다.

- [x] **AC-3 (게이트 — 각각 독립 statement 로, 파이프 없이)** — 전부 rc=0:
      | 게이트 | 기준 |
      |---|---|
      | `tsc --noEmit` | 0 오류 |
      | `pnpm lint` | 0 오류 (§ Edge Cases ④) |
      | `pnpm test` (vitest) | **착수 전 baseline 과 같은 통과 수** |
      | `pnpm build` (`next build`) | rc=0 |
      🔴 vitest 는 **먼저 옛 판에서 baseline 을 재고** 그 숫자를 적은 뒤에 올린다. baseline 이
      없으면 「29xx 통과」는 아무것도 증명하지 않는다.

- [x] **AC-4** — `frontend-ui.md` § 5.3 의 버전 표(`console-web | 15.0.3 | ^2.1.4 | …`)를
      새 값으로 고치고 그 절의 측정일(`Measured 2026-08-06`)을 다시 찍는다.
      🔴 이 표는 **같은 사실의 두 번째 사본**이다. 안 고치면 저장소가 자기 버전을 놓고 거짓말한다.

- [ ] **AC-5 (라이브 판정 — 이 티켓의 진짜 종료 조건)** — 머지 후:
      (a) `vercel-deploy.yml` 의 `kanggle-console` 잡이 이 커밋에서 **발사**됐다
          (`package.json` 이 `vercel-ignore.sh` 의 `SPECS` 첫 줄 아래라 자동으로 자격을 얻는다)
      (b) 새 deployment 의 status 가 **`success`** — 🔴 판정은 **GitHub deployment status API**
          로 한다. `404`/`200` 같은 HTTP 프로브로 갈음하지 않는다(585 가 그 함정을 이미 기록했다:
          `404` 는 「프로젝트 없음」과 「배포 없음」을 못 가른다)
      (c) 같은 시각 `kanggle-store` 또는 `kanggle-fan` 의 최신 배포를 **양성 대조군**으로 함께 읽는다

---

# 🔎 구현 기록 (2026-09-05 UTC)

## AC-0 재측정 — 세 축 전부 티켓의 전제대로 (뒤집힌 것 없음)

| 축 | 재측정 결과 |
|---|---|
| (a) `6281458564` | 여전히 **failure**. 🔵 더 새로운 `kanggle-console` 배포는 **없다** — 전체 최신 6건을 양성 대조군으로 함께 읽어 확인 |
| (b) 형제 스펙 | `^15.1.0` / `^19.0.0` 셋 다 불변. 락 해석 = fan 15.5.15 · store 15.5.14 · auth-forwarder 15.5.25 |
| (c) 락 소유 | 저장소 락 6개(root · ecommerce · fan · auth-forwarder · console-web · federation-e2e)가 서로 독립. `console-web` 단독 소유 확인 |

🔵 **`eslint-config-next` 는 형제 중 `fan-platform-web` 에만 있다**(`^15.1.0`). store · auth-forwarder
엔 없다. 그래도 선례는 하나로 충분하고, `next` 와 같은 판을 따라가는 것이 이 패키지의 계약이다.

## AC-2 — 해석값

```
next               15.5.25      (^15.1.0)
react              19.2.8       (^19.0.0)
react-dom          19.2.8       (^19.0.0)
eslint-config-next 15.5.25      (^15.1.0)
```

🔵 `auth-forwarder` 와 **같은 해석값**이다(가장 최근에 설치된 형제).
🔴 **락이 아니라 `node_modules/*/package.json` 에서 읽었다** — 락은 «무엇을 설치하기로 했나»이고
설치본이 «무엇이 설치됐나»다. 판정은 후자다.

## AC-3 — 게이트 (각각 **독립 statement**, 파이프 없음)

| 게이트 | rc | 값 |
|---|---|---|
| `npx tsc --noEmit` | **0** | 아래 「tsc 는 두 번 재야 했다」 |
| `pnpm lint` | **0** | `No ESLint warnings or errors` |
| `pnpm test` | **0** | **282 files / 2932 tests passed** |
| `pnpm build` | **0** | `Compiled successfully in 32.4s` · 정적 66/66 |

### 🔴 RC → stable 인데 **테스트가 한 칸도 안 움직였다**

```
옛 판 (next 15.0.3 · react 19.0.0-rc-...-20241106)   282 files / 2932 tests   145.15s
새 판 (next 15.5.25 · react 19.2.8)                  282 files / 2932 tests   138.60s
```

**baseline 을 먼저 잰 것이 이 문장을 가능하게 한다.** 새 판만 재고 *"2932 통과"* 라고 적었다면
그것은 «변한 게 없다» 를 **주장할 수 없는** 숫자였다 — 2932 가 원래 몇이었는지 아무도 모르니까.
AC-3 이 baseline 을 요구한 이유가 이것이다.

### 🔴🔴 `tsc` 는 두 번 재야 했다 — **첫 rc=0 은 CI 조건이 아니었다**

`next lint`(또는 `next build`)가 `next-env.d.ts` 를 **자동으로 갱신**하며 한 줄을 넣는다:

```diff
+/// <reference path="./.next/types/routes.d.ts" />
```

내 게이트 순서는 `tsc → lint` 였다. 즉 **tsc 를 돌린 시점의 `next-env.d.ts` 는 아직 옛 내용**이고,
그 rc=0 은 커밋될 파일을 재지 않았다. 그리고 `ci.yml` 의 `Frontend unit tests` 잡은
`install → pnpm test → tsc --noEmit → pnpm lint` 순서라 **`next build` 가 없다** ⇒ `.next/` 는
gitignore 이므로 CI 체크아웃에는 참조 대상 파일이 **존재하지 않는다**. 로컬 초록 + CI 빨강의 전형.

⇒ **CI 조건을 복제해서 다시 쟀다**:

```
.next/types/routes.d.ts 존재     tsc rc=0     (양성 대조군: 파일이 실제로 있음을 ls 로 확인)
rm -rf .next  후                  tsc rc=0     ← CI 조건. 무사하다
주입한 타입 오류 1건              tsc rc=2     ← 🔴 계측기가 실제로 문다는 증거
```

🔵 마지막 줄이 없으면 앞의 두 rc=0 은 **«아무 일도 안 하고 0»** 과 구별되지 않는다.
결론: `next-env.d.ts` 의 새 줄은 CI 를 깨지 않는다 — **논증이 아니라 측정으로** 그렇게 판정했다.

## AC-4 — `frontend-ui.md` § 5.3

표의 `console-web` 행을 `^15.1.0` 으로 고치고, 측정일을 **2026-09-05 로 다시 찍되 세 행을 전부
다시 쟀다**(형제 두 앱의 `vitest` · `lint` 스크립트 포함 — 안 잰 행에 새 날짜를 붙이면 그 표가
바로 거짓말을 시작한다). 그리고 세 가지를 덧붙였다:

- **Next 칸은 버전이 아니라 스펙이다** — 이 표에서 숫자를 읽어 가지 마라
- `console-web` 이 정확 핀을 들고 있다가 배포를 거부당한 경위 (= 정확 핀의 비용)
- 🔵 **경고는 실패가 아니다** — 15.5 에서 `next lint` 는 deprecation 경고를, Next 는
  «워크스페이스 루트를 추론했다» 경고를 찍는다. **둘 다 rc=0 이다.** 게이트 기준은 rc 이고,
  경고를 없애려고 이 티켓의 범위를 넓히지 않았다 (Edge Case ④). `next lint` 이탈은 Next 16
  전에 갚아야 할 **별도 결정**이며, 그 사실을 그 절에 적어 뒀다.

## 부수 관찰 두 가지

① `next-env.d.ts` 는 **내가 쓴 것이 아니라 Next 가 쓴 것**이다(도구 산출물). 안 커밋하면 매
빌드마다 트리가 더러워지고, 커밋하면 위의 CI 조건 질문이 생긴다. 재서 안전한 쪽을 골랐다.

② 🔴 **이 파일을 한 번 `review/` 로 먼저 옮겼다가 훅에 막혔다.** `HARDSTOP-05` 가 옳다 —
`review/` 는 frozen 이고, 구현 기록은 **`in-progress/` 에서** 써야 한다. 그것이 이 파일이
지금 `in-progress/` 에 있는 이유이며, `review/` 로의 이동은 AC-5 를 닫는 **다음 chore** 의
몫이다. (막힌 것은 손해가 아니라 규칙이 작동한 것이다.)

## 🔴 남은 것 = AC-5 뿐

이 PR 이 머지되면 `package.json` 이 `SPECS` 의 `:/projects/platform-console/apps/console-web`
아래이므로 훅이 **자동으로** 발사된다(585 가 겪은 「첫 배포 구간」을 이번엔 안 겪는다).
그 배포가 `success` 로 찍히는 것을 **deployment status API + 양성 대조군**으로 확인해야
이 티켓이 다음 단계로 간다.

---

# Related Specs

- `projects/platform-console/docs/conventions/frontend-ui.md` § 5.3 (버전 표 — AC-4)
- `projects/platform-console/apps/console-web/VERCEL.md` (배포 배선 · 첫 배포 구간)

# Related Contracts

없음 — 와이어 형태를 바꾸지 않는다.

# Related Tasks

- `TASK-MONO-585` (`review/`) — **이 티켓이 그것을 막고 있다.** 585 는 저장소 몫이 끝났고
  라이브 검증(200 · 로그인 왕복 · 데모-off 배너)만 남았는데, 배포가 없어서 못 잰다.
- `TASK-MONO-610` — `kanggle-fan` 의 첫 배포 구간(같은 계열의 배포 배선 함정)
- `TASK-PC-FE-272` — `frontend-ui.md` § 5 를 만든 티켓(AC-4 가 고칠 표의 출처)

# Edge Cases

① **RC → stable 은 렌더링 거동을 바꿀 수 있다.** React 19 RC(2024-11) 와 19.2.x 사이에는
   실제 동작 차이가 있다. 2900+ 개의 vitest 가 이 축의 계측기이고, **계측기가 빨개지면 그것이
   신호다** — § Failure Scenarios ① 로 간다.

② **버전이 두 곳에 적혀 있다.** `package.json` 과 `frontend-ui.md` § 5.3 표. 🔴 한쪽만 고치면
   나머지가 조용히 거짓이 된다 — 그리고 표 쪽에는 **아무 가드도 없다**(그래서 AC-4 가 있다).

③ **`^15.1.0` 은 숫자가 아니라 범위다.** 오늘 15.5.15 로 풀린다고 해서 다음 달에도 그렇지
   않다. 티켓에 적는 것은 **스펙**(형제와 동일)이고, 해석값은 **그때 잰 값**으로만 적는다.

④ **`next lint` 는 15.5 에서 deprecated 다.** 경고가 뜰 수 있다. 🔴 경고와 실패를 섞지 마라 —
   AC-3 의 기준은 **rc=0** 이다. 경고가 났으면 그 사실을 적되 그것으로 게이트를 실패시키지도,
   그것을 고치려고 이 티켓의 범위를 넓히지도 않는다(그건 별도 티켓 후보).

⑤ **로컬 Docker 빌드도 같은 락을 읽는다.** `console-web/Dockerfile` 은 `pnpm-lock.yaml` 을
   복사한다(그리고 `TASK-MONO-585` 가 `@demo/backend-resolver` 를 `additional_contexts` 로
   넣었다). 락을 바꾸면 그 경로도 바뀐다 — Docker 빌드는 이 티켓의 게이트가 아니지만,
   **깨진다면 그것은 이 티켓이 만든 것**이므로 § Failure Scenarios ② 로 간다.

⑥ **nightly 전용 스위트는 이 PR 레인을 안 탄다.** console 풀스택 e2e 는 `nightly-e2e.yml`
   에만 있다. Next 마이너 판 교체는 라우팅·렌더를 건드릴 수 있는 부류이므로, 머지 후
   **다음 nightly 를 한 번 확인**한다(`CLAUDE.md` § Post-merge nightly check).

# Failure Scenarios

① **vitest 가 빨개진다** → 🔴 **고치지 말고 멈춰서 보고한다.** 이 티켓의 범위는 의존성 판
   교체이고, 테스트를 초록으로 만드는 수정은 **계측기를 깎는 것**이 된다. 빨간 테스트의
   이름·개수·첫 실패 메시지를 그대로 보고하고 소유자 판단을 받는다. (범위를 넓혀 코드를
   고치는 것은 별도 티켓.)

② **`next build` 는 되는데 Docker 빌드가 깨진다** → 게이트 밖이지만 원인이 이 티켓이므로
   보고한다. 로컬 데모 경로가 죽으면 585 의 「데모 켜짐」 축을 못 잰다.

③ **올렸는데 Vercel 이 여전히 거부한다** → 판별자 ②의 네 축이 겹쳐 있었다는 뜻이므로
   **가정이 틀린 것**이다. 배포를 반복 생성하지 말고(배포 한도는 계정 전체다) 멈춰서
   `npx vercel inspect <dpl> --logs` 의 실제 문구를 근거로 다시 진단한다.

④ **훅이 안 발사된다** → `vercel-ignore.sh` 의 판정자를 그 커밋에서 **직접 실행해서**
   rc 를 확인한다(rc=1 = 빌드). 585 가 이 절차를 `VERCEL.md` § 첫 배포 구간에 적어 뒀다.

# Definition of Done

- [ ] AC-0 ~ AC-5 전부 닫힘
- [ ] `frontend-ui.md` § 5.3 표가 새 값 + 새 측정일
- [ ] 새 프로덕션 배포가 **`success`**(양성 대조군과 함께 판정)
- [ ] `TASK-MONO-585` 의 라이브 검증이 **가능한 상태**가 됐음을 585 에 기록

---

분석=Opus 5 / 구현 권장=Sonnet 5 (의존성 판 교체 + 게이트 — 판단이 아니라 실행이다.
🔴 단, § Failure Scenarios ① 이 발동하면 그 시점부터는 Opus 판단이 필요하다).
