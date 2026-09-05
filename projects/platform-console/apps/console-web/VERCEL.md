# Vercel 배선 — `kanggle-console` (TASK-MONO-585 / ADR-MONO-067 단계 3)

`vercel.json` 옆의 이 파일이 그 JSON 이 담을 수 없는 것을 담는다. **JSON 에는 주석이 없고,
`vercel.json` 은 스키마가 엄격하다.**

| | |
|---|---|
| Vercel 프로젝트 | **`kanggle-console`** — ✅ **생성됨 (2026-09-05)**. 첫 배포는 아래 § 첫 배포 구간 참조 |
| Root Directory | **`projects/platform-console/apps/console-web`** |
| 빌드 | Next.js 15 App Router (webpack). 프레임워크 감지는 대시보드에 맡긴다 |
| `vercel.json` 이 선언하는 것 | `git.deploymentEnabled` + `installCommand` + `ignoreCommand` |
| 무시 규칙의 경로 목록 | **[`vercel-ignore.sh`](./vercel-ignore.sh)** — JSON 안이 아니다 |
| 공개 호스트명 | `console.hubwang.com` (`TEMPLATE.md` § 공개 호스트명 배분) |

## 🔴🔴 저장소 몫과 배포 몫은 다른 일이다 — 이 파일은 **저장소 몫이 끝났다**는 기록이다

`TASK-MONO-585` 는 두 반쪽이다:

| 반쪽 | 상태 |
|---|---|
| **저장소** — 브라우저에서 백엔드 주소 제거 · 런타임 해석 · 데모-off 표현 · 가드 · 배선 파일 | ✅ 이 PR |
| **배포** — Vercel 프로젝트 생성 · env 주입 · Deploy Hook secret | 🙋 **소유자** |

🔴 **순서를 뒤집지 마라.** 저장소 몫 없이 프로젝트를 먼저 만들면 빈 프로젝트가 계정 전체
배포 한도만 먹는다(`TASK-MONO-590` 실측: **건너뛴 배포도 한도를 먹는다** — 74건 중 69건이
`Canceled by Ignored Build Step` 이었는데도 한도에 닿았다).

## 🙋 소유자 체크리스트 — 이 순서로

1. **프로젝트 생성** — 이름 `kanggle-console`, Root Directory
   `projects/platform-console/apps/console-web`.
   🔴 **"Include source files outside of the Root Directory in the Build Step"** 를 켜라.
   이 앱의 `package.json` 은 `"@demo/backend-resolver": "link:../../../../infra/demo/
   backend-resolver"` 를 갖고, 그 경로는 Root Directory **밖**(저장소 루트)이다.
   🔵 형제 둘(`kanggle-fan`·`kanggle-store`)이 같은 조건에서 배포되고 있으므로 이 조합은
   실증돼 있다. 🔴 그러나 결핍은 **install 에서 안 터진다** — `pnpm install` 은 댕글링
   심링크를 만들고 성공하며, 죽는 것은 `next build` 이고 메시지는
   *"Module not found: Can't resolve '@demo/backend-resolver'"* 다.
2. **환경변수** (Production + Preview 동일):

   | 이름 | 값 | 왜 |
   |---|---|---|
   | `DEMO_API_BASE` | 컨트롤 플레인 베이스 | **이 값이 없으면 해석기가 아무 일도 안 한다** — 앱은 `.local` 을 그대로 부르고 전부 실패한다. 형제 둘과 같은 값 |
   | `CONSOLE_PUBLIC_ORIGIN` | `https://console.hubwang.com` | 런타임 오리진. `NEXT_PUBLIC_APP_URL` 은 **빌드 타임 인라인**이라 이 일을 못 한다(`TASK-MONO-358`) |
   | `OIDC_ISSUER_URL` | `https://auth.hubwang.com` | `ADR-MONO-069` `C2`. 🔴 **`.local` 을 넣지 마라** — 발급자는 데모 도메인 파생 대상이 **아니다**(`shared/config/demo-backend.ts` 헤더) |
   | `OIDC_REDIRECT_URI` | `https://console.hubwang.com/api/auth/callback` | 🔵 IdP 에 **이미 등록돼 있다** — `TASK-BE-589` / `V0034`. 접미사 없는 `/api/auth/callback` 이고 로그아웃 랜딩은 `/login` 이다(fan 과 **다르다**) |
   | `OIDC_CLIENT_ID` | `platform-console-web` | 코드 기본값과 같다. 명시해 두면 대시보드에서 읽힌다 |

   🔴 **`NEXT_PUBLIC_APP_URL` 은 설정하지 마라.** 지금 이 앱의 클라이언트 번들에는 백엔드
   오리진이 **0건**이고(아래 § 실측), 그 성질은 «클라이언트 그래프가 서버 config 모듈에
   닿지 않는다» 로 유지된다. `NEXT_PUBLIC_*` 은 빌드 타임에 **인라인**되므로 새로 넣는
   순간 그 값이 번들에 박히는 축이 다시 열린다.
   🔵 `CONSOLE_PUBLIC_ORIGIN` 은 접두사가 없어 인라인되지 않는다 — 그래서 이 일을 한다.
3. **Deploy Hook 생성** (Settings → Git → Deploy Hooks, branch `main`) 후 그 URL 을
   저장소 secret **`VERCEL_DEPLOY_HOOK_CONSOLE`** 에 넣는다.
   🔴 이 secret 이 비어 있는 동안 `vercel-deploy.yml` 의 `kanggle-console` 잡은 **빨갛다** —
   그것이 정확한 신호다(앱은 저장소에 있는데 배포할 곳이 없다). 🔵 판정자가 이 앱의 경로를
   안 건드린 커밋은 건너뛰므로 **매 커밋 빨간 것은 아니다**.
4. **도메인 연결** — `console.hubwang.com`.
5. 🔴 **첫 배포를 확인한다.** *"No Deployment"* 로 보이면 위 § 첫 배포 구간이다 —
   대시보드 Redeploy 를 누르지 말고, **이 앱 경로를 건드리는 커밋을 하나** 밀어라.
6. 랜딩 후 **론처 링크 전환**은 이 티켓이 아니라 별도 후속이다(단계 2 의 `TASK-MONO-583`,
   단계 4 의 `TASK-MONO-618` 과 같은 모양 — 론처 행의 `data-served` 를 `vercel` 로).

## 🔴🔴 첫 배포 구간 — **새 프로젝트에는 「배포가 영원히 안 생기는」 창이 있다**

소유자가 프로젝트를 만들고 도메인까지 붙였는데 Vercel 이 이렇게 말한다:

```
console.hubwang.com — No Deployment
Your domain is properly configured, but you don't have a production deployment.
```

🔵 **고장이 아니다. 이 저장소가 세 번째로 겪는, 설계된 동작이다.**
(`infra/demo/auth-forwarder/README.md` 가 `kanggle-auth` 에 대해 처음 적었고,
`TASK-MONO-610` 이 `kanggle-fan` 의 env 발효에서 두 번째를 적었다.)

기전: 이 프로젝트는 `git.deploymentEnabled.main = false` 라 **push 로는 배포가 안 만들어지고**,
배포를 만드는 것은 `vercel-deploy.yml` 의 Deploy Hook 뿐이다. 그 훅은
[`vercel-ignore.sh`](./vercel-ignore.sh) 의 판정자가 *"이 커밋이 내 `SPECS` 를 건드렸다"*
라고 할 때만 발사된다. **Import 는 그때의 `main` 을 클론하는데**, 그 커밋이 이 앱의 경로를
안 건드렸으면 판정자는 정확히 설계대로 **건너뛴다.**

🔴 **대시보드 Redeploy 는 길이 아니다** — 같은 커밋을 다시 재므로 또 건너뛴다.

### 이번 실측 (2026-09-05) — 왜 하필 이 프로젝트에서 창이 넓었나

| 커밋 | 이 앱 경로 | `kanggle-console` 잡 | 뜻 |
|---|---|---|---|
| `697f80139` (`TASK-MONO-585` 구현) | ✅ 건드림 | 🔴 **failure** | 자격은 있었는데 **secret 이 아직 없었다**(*"secret 이 비어 있습니다"*) |
| `5ff3d88b2` · `3ff547239` · `39da225d5` | ❌ 안 건드림 | ✅ success | **건너뜀** — 훅을 안 쏜다 |

`VERCEL_DEPLOY_HOOK_CONSOLE` 은 `2026-09-05T13:10:59Z` 에 들어왔다. ⇒ **자격을 갖춘 유일한
커밋이 secret 보다 먼저 지나갔고**, 그 뒤로는 자격을 갖춘 커밋이 없었다.

### 빠져나오는 길 — **`SPECS` 안의 경로를 건드리는 커밋 하나**

`kanggle-auth` 는 자기 README 에 이 절을 적는 것으로 빠져나왔다(그 파일이 그 프로젝트의
`SPECS` 아래에 있다). **이 절이 실린 커밋이 이 프로젝트에 대해 같은 일을 한다** — 이 파일은
`projects/platform-console/apps/console-web/` 아래이고, 그것이 `SPECS` 의 첫 줄이다.

🔵 그래서 이 문단은 «기록» 인 동시에 **트리거**다. 다음 사람이 다섯 번째 프로젝트를 만들 때
같은 창을 만나면, 여기 적힌 대로 그 앱 안의 문서를 한 번 고치면 된다.

🔴 **순서를 바꿔도 창은 남는다.** secret 을 먼저 넣고 프로젝트를 나중에 만들어도, Import 가
클론하는 커밋이 그 앱을 안 건드렸으면 마찬가지다. 창을 없애려면 «프로젝트 생성 직후 그 앱을
건드리는 커밋을 하나 민다» 를 체크리스트에 넣는 수밖에 없다 — 아래 § 체크리스트 6번.

## 🔴 왜 `installCommand` 가 `--no-frozen-lockfile` 인가 — **형제와 다른 이유로 같은 값**

fan·web-store 는 pnpm 워크스페이스 **멤버**라 Root Directory 에 lockfile 이 **없고**, 그래서
`--frozen-lockfile`(Vercel 은 `CI=1` 이라 pnpm 이 기본으로 켠다)이 `ERR_PNPM_NO_LOCKFILE` 로
**첫 명령에서 죽는다**. console-web 은 사정이 다르다 — 워크스페이스 멤버가 아니고
`pnpm-lock.yaml` 이 Root Directory **안에** 있어서 frozen 이 성립한다.

그런데도 같은 값을 쓰는 이유는 **게이트를 어디서 걸 것인가**의 문제다:

- lockfile 드리프트는 **이미 CI 가 문다** — `ci.yml` 의 console-web 잡이
  `pnpm install --frozen-lockfile` 을 돌린다(그 잡은 pnpm **9.15.0** 으로 핀돼 있다).
- Vercel 이 쓰는 pnpm 은 **그 핀이 아니다**(이 앱에는 `packageManager` 필드가 없다).
  거기서 frozen 을 켜면 «lockfile 이 틀렸다» 가 아니라 «pnpm 판이 다르다» 로 배포가 죽을 수
  있고, 그 실패는 게이트가 아니라 **잡음**이다.

⇒ 판정은 CI 에 두고 Vercel 은 굽기만 한다. 🔵 2026-09-05 실측: 이 lockfile 은 pnpm
**9.15.0** 의 `--frozen-lockfile` 로 rc=0 이다(그리고 그 install 이 이 PR 의 게이트다).

## ✅ 실측 — 이 배선이 지키는 것 (2026-09-05, 클린 `next build`)

같은 실행·같은 추출기로 두 축을 함께 쟀다. **서버 쪽 12가 대조군**이다 — 그 칸이 0이면
「클라 0」은 추출기가 죽은 것과 구별되지 않는다.

| | jsFiles | distinct `.local` URL |
|---|---:|---:|
| **CLIENT** (`.next/static`) | 275 | **0** |
| SERVER (`.next/server`) | 525 | **12** ← 양성 대조군 |

착수 전(= `main` `59fd9e1da`)의 같은 측정은 **CLIENT 12 / SERVER 12** 였다.

## 🔴 알려진 한계 — `console-bff` 는 Vercel 에서 **닿지 않는다**

`console-bff` 는 **공개 호스트명이 없다**. `TASK-MONO-362` 가 그 Traefik 라우터를 일부러
없앴고(백엔드 서비스는 엣지에 노출되지 않는다 — `api-gateway-policy.md` L14), 이 티켓의
Edge Case 가 *"공개 호스트명을 주지 마라"* 로 그 결정을 다시 못 박았다. 주소는 도커 네트워크
DNS(`http://console-bff:8080`)이고 데모 도메인으로 **파생될 수 있는 값이 아니다.**

⇒ Vercel 에서는 BFF 를 지나는 레그가 실패한다. 실측 범위:

| 레그 | 라우트 |
|---|---|
| 운영 개요 합성 | `/api/console/dashboards/operator-overview` → `/dashboards/overview` |
| 도메인 상태 합성 | `/api/console/dashboards/domain-health` → `/dashboards/health`, `/console` |
| 알림 인박스 | `/api/console/notifications/**` |

🔵 **화면은 뜬다.** 세 레그 전부 실패를 이미 상태로 표현한다(`bffUnavailable: true` →
degrade 배너 / 502 `BAD_GATEWAY` 봉투) — 이관이 만든 결함이 아니라 이관이 **드러낸** 것이고,
`ADR-MONO-067` 이 요구한 *"백엔드 없는 상태를 앱이 표현해야 한다"* 를 이미 만족한다.
🔴 나머지 도메인 화면(iam·wms·scm·finance·erp·ecommerce)은 BFF 를 지나지 않는다
(`ADR-MONO-017` D3.B — console-web → 도메인 게이트웨이 **직접**) ⇒ 영향 없다.

🔴 **이 한계를 이 티켓에서 «해결» 하지 않는다.** 해결하려면 BFF 에 공개 경로를 주거나
(엣지 노출 금지에 정면으로 걸린다) BFF 합성을 콘솔 서버로 옮겨야 하고, 둘 다 아키텍처
결정이다(`platform/architecture-decision-rule.md`). 여기서는 **적어 두고 넘긴다** — 조용히
넘기면 다음 사람이 이것을 이관의 회귀로 오진한다.
