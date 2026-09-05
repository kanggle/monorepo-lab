# Vercel 배선 — `kanggle-fan` (TASK-MONO-562 · 563)

`vercel.json` 옆의 이 파일이 그 JSON 이 담을 수 없는 것을 담는다. **JSON 에는 주석이 없고,
`vercel.json` 은 스키마가 엄격하다.**

| | |
|---|---|
| Vercel 프로젝트 | **`kanggle-fan`** |
| Root Directory | **`projects/fan-platform/web/fan-platform-web`** — 2026-08-21 소유자가 대시보드에서 확인 |
| 빌드 | Next.js 15 App Router. 프레임워크 감지는 대시보드에 맡긴다 |
| `vercel.json` 이 선언하는 것 | `installCommand` + `ignoreCommand` 둘뿐 |
| 무시 규칙의 경로 목록 | **[`vercel-ignore.sh`](./vercel-ignore.sh)** — JSON 안이 아니다. 이유는 아래 § |

## 🔴🔴 스키마는 **길이도** 검사한다 — 561자가 아니라 **261자**가 이 프로젝트를 죽였다

이 파일의 이전 판은 첫 문단에서 이렇게 경고했다: *"`vercel.json` 은 스키마가 엄격해 모르는
최상위 키를 거부한다 — `TASK-MONO-557` 이 설명을 `"//installCommand"` 키로 끼워 넣었다가
배포를 연속으로 깼다."* **그리고 `TASK-MONO-562` 는 다른 문으로 같은 방에 들어갔다.**

공개 스키마(`https://openapi.vercel.sh/vercel.json`, 2026-08-21 실측 — 최상위 property **40개**,
`additionalProperties: false`)는 명령 문자열에 **`maxLength: 256`** 을 건다.

| 파일 | `ignoreCommand` 값 길이 | 한도 256 |
|---|---|---|
| `infra/demo/aws/site/vercel.json` (배포가 **되는** 쪽) | 129 | ✅ |
| 이 파일의 `vercel.json` (562 판) | **261** | ❌ **+5** |

562 는 pathspec 5개를 `ignoreCommand` 문자열에 **직접** 넣었고 5자가 넘쳤다. 결과:

- `vercel.json` 이 거부되어 **배포가 0초에 죽는다** — 빌드 로그조차 생기지 않는다.
- 커밋 상태 문구는 `Deployment failed.` + **project-configuration 문서 링크** — 557 이
  모르는 키로 받았던 **바로 그 링크**다.
- **`ignoreCommand` 는 영원히 실행되지 않는다.** 그래서 fan 은 무관한 커밋마다 배포를
  구웠고(`#3413`·`#3414` 실측), 같은 커밋에서 portfolio 는 `Canceled by Ignored Build Step`
  을 정상으로 냈다. **비대칭의 정체가 이것이다.**

🔵 **그래서 목록을 문자열 밖으로 뺐다.** [`vercel-ignore.sh`](./vercel-ignore.sh) 에는 길이
제한이 없고, 경로를 하나 더해도 `vercel.json` 은 길어지지 않는다(261 → **99자**).
제한 자체는 이제 `scripts/check-vercel-build-triggers.sh` 의 **칸 (5)** 가 지킨다 —
그 칸은 원문이 아니라 **디코드된 값**의 길이를 잰다(`\"` 는 원문 2자 · 값 1자라
grep 으로 세면 틀린다). 대조군: 562 판 파일을 그대로 물려 **`261자`** 라고 지목하는 것을 확인했다.

## Root Directory 는 확인됐다 — 후보 두 자리를 하나로 줄였다

`TASK-MONO-562` 는 이 값을 몰라서 **후보 두 자리에 같은 규칙을 뒀다**
(`projects/fan-platform/vercel.json` 과 이 파일). 2026-08-21 소유자가 확정했고, 읽히는 자리는
**이 디렉터리** 다. 얕은 쪽 사본은 삭제했다.

🔴 **사본을 남기지 않은 이유**: 읽히지 않는 설정 파일은 *무해* 가 아니라 **거짓 증거**다 —
규칙이 두 벌이면 다음 사람은 어느 쪽이 실제로 행사되는지 모르는 채 둘 다 고쳐야 하고,
한쪽만 고치면 그 사실이 아무 데서도 드러나지 않는다.

## 🔴 왜 `installCommand` 가 있는가 — 설치 지점이 CI 와 다르다

이 앱은 pnpm 워크스페이스의 **멤버**이고, `pnpm-lock.yaml` 과 `pnpm-workspace.yaml` 은
한 단계 위 `projects/fan-platform/` 에 있다. **Root Directory 는 그보다 깊다** ⇒ Vercel 이
설치를 시작하는 디렉터리에는 lockfile 이 **없다.**

2026-08-21 실측(두 컨텍스트를 각각 만들어 실제로 돌렸다):

| 설치 지점 | `CI=1 pnpm install --frozen-lockfile` | `pnpm build` |
|---|---|---|
| 워크스페이스 루트가 **보일 때** | ✅ rc=0 (39.6s) | ✅ rc=0 · 14 라우트 |
| Root Directory **만** (lockfile 없음) | ❌ `ERR_PNPM_NO_LOCKFILE` — **첫 명령에서 사망** | 도달 못 함 |
| Root Directory 만 + `--no-frozen-lockfile` | ✅ rc=0 (48.2s) | ✅ rc=0 · 14 라우트 |

`--frozen-lockfile` 은 **CI 환경에서 pnpm 이 기본으로 켜는 값**이다(pnpm 이 직접 그렇게
안내한다). Vercel 은 `CI=1` 이므로 아무도 요청하지 않아도 켜진다.

🔵 그래서 `installCommand` 는 새 정책이 아니라 **CI 가 fan 에 쓰는 것과 정확히 같은 명령**이다
— `.github/workflows/ci.yml` 의 fan 스텝 3곳이 전부 `pnpm install --no-frozen-lockfile` 이다
(ecommerce · console-web 은 `--frozen-lockfile`; fan 만 예외).

🔴 **이 두 결함은 서로 다른 층에 있다.** 길이 위반은 배포 **생성** 시점에, 설치 컨텍스트는
**빌드** 시점에 죽인다. 앞의 것을 고치기 전에는 뒤의 것이 실행조차 되지 않으므로,
길이만 고쳤을 때 설치가 다시 문제가 될 수 있다 — 그래서 둘 다 고쳤다.

### 남은 갈림길을 다음 커밋이 스스로 가른다

Vercel 에는 *"Root Directory 바깥 소스를 빌드 단계에 포함"* 설정이 있고 그 값도 대시보드에만 있다.

- **포함 ON** — pnpm 이 위로 올라가 워크스페이스를 찾는다(위 표 1행). `installCommand` 는
  lockfile 을 그대로 쓰되 갱신을 허용할 뿐이라 **해가 없다.**
- **포함 OFF** — 위 표 2행이 재현된다. `installCommand` 가 그것을 3행으로 바꾼다.
  🔴 다만 이 경우 `ignoreCommand` 는 판정자·래퍼가 빌드 컨텍스트에 없어 **발화하지 못한다.**

🔵 **관측으로 가른다.** 배포가 성공하기 시작한 뒤 **`tasks/**` 만 바꾼 커밋**을 보라:

| 그 커밋에서 `Vercel – kanggle-fan` 이 | 결론 |
|---|---|
| `Canceled by Ignored Build Step` | 포함 **ON** — 규칙이 행사된다. 끝 |
| 빌드하고 (성공이든 실패든) 돌았다 | 포함 **OFF** — 대시보드에서 켜야 한다 |

## 왜 `ignoreCommand` 가 필요한가

이 저장소는 Vercel 프로젝트가 **셋**이다(`kanggle-fan` + 론처 `kanggle-portfolio` +
web-store — `TASK-MONO-582`). 그래서 **커밋 하나가 배포 셋을 굽는다.** 2026-08-19 의 티켓 파일링 러시(문서 전용 PR 13건)가 그대로
배포로 번역되어 무료 플랜 한도에 닿았고, 24시간 동안 모든 PR 이 빨개졌다.

🔴 **진짜 피해는 빨간 체크가 아니었다** — 그동안 **론처가 낡은 판을 계속 서빙했다.**
`TASK-MONO-560` 의 방문자 화면 링크가 머지된 뒤에도 방문자는 그 링크가 없는 페이지를 봤고,
URL 은 200 을 냈다. 배포가 죽은 것과 사이트가 죽은 것은 다른 사건이다.

## 트리거 경로

경로 목록과 각 항목의 근거는 [`vercel-ignore.sh`](./vercel-ignore.sh) 안에 있다 —
목록 옆에 두는 편이 여기 복사해 두는 것보다 낫다(같은 사실이 두 곳에 있으면 한쪽만 고쳐진다).

판정 규약과 fail-open 설계는
[`scripts/vercel-should-build.sh`](../../../../scripts/vercel-should-build.sh) 헤더에 있다.

---

## 🔴 데모 env 두 개 — 이 프로젝트가 **원장**이다 (`TASK-MONO-622`)

`infra/demo/verify-demo-wrapper.sh` 의 칸 (x2) 가 실행될 때마다 *"원장: 이 파일"* 이라고
가리킨다. 🔴 **그런데 2026-09-04 까지 이 파일에는 두 키가 0건이었다** — 형제
[`apps/web-store/VERCEL.md`](../../../ecommerce-microservices-platform/apps/web-store/VERCEL.md)
에는 각각 5건이 있었는데도. 가드가 *"실측값은 원장이 든다"* 라고 지시하면서 **막다른 길을
가리키고 있었다.** 이 절이 그 지시를 실재하게 만든다. 칸 **(z29)** 가 앞으로 그것을
빨갛게 만든다.

### `DEMO_API_BASE` — 해석기의 **입력**

| | |
|---|---|
| 값 | 컨트롤 플레인 API Gateway 의 베이스 URL (`terraform output api_base_url`) |
| 환경 | Production |
| 왜 | 데모 백엔드의 공인 IP 는 **부팅마다 바뀐다**. 그래서 앱은 주소를 굽지 않고 런타임에 `{DEMO_API_BASE}/status` 를 조회해 그날의 백엔드를 해석한다 (`ADR-MONO-068` § D2) |
| 코드 | `src/shared/config/demo-backend.ts` → `@demo/backend-resolver` |
| 🔵 없으면 | 앱은 **조용히 정상 동작**한다 — 「데모가 아닌 환경」으로 판정한다. 로컬·CI 가 그 경로다. 🔴 그래서 **부재가 에러로 보이지 않는다**(`src/__tests__/demo-backend.test.ts` 가 그 칸을 지킨다) |
| 🔴 끝 슬래시 | 해석기가 먹는다(`//status` 가 되지 않게) — `demo-backend.test.ts:142` |

### `DEMO_PAYMENT_MOCK` — 결제 불변식의 **프런트 절반**

🔴🔴 **극성이 형제와 반대다.** ecommerce 는 실 PG 가 기본이라 목을 **켜야** 하지만,
팬은 **목이 기본**이고 실 PG 가 opt-in 이다:

```
MockPaymentGatewayAdapter      @Profile("!portone")   ← 목이 기본
PaymentGatewayConfig (PortOne) @Profile("portone")    ← 실 PG 는 opt-in
```

⇒ 불변식은 **프런트 플래그 ON ⟺ 백엔드 프로파일에 `portone` 이 없음** 이다.
🔴 여기에 형제 원장의 *"백엔드가 `demo-pg` 인 한"* 을 옮겨 적으면 **정반대를 단언한다** —
`demo-pg` 는 ecommerce 의 프로파일 이름이고 팬에는 없다.

| | |
|---|---|
| 값 | `1` |
| 환경 | Production |
| 왜 | 데모는 돈을 받지 않는다. 없으면 프런트가 `PortOne 키 미설정` 으로 **구독 요청 자체를 안 보낸다**(`TASK-FAN-FE-015` 가 고친 상태가 정확히 그것) |
| 백엔드 절반 | `membership-service` 의 `SPRING_PROFILES_ACTIVE` 에 `portone` 이 **없어야** 한다 — 칸 (x2) 가 그 절반을 실행해서 잰다 |

### 🙋 판정은 사람이 한다 — 저장소가 못 하는 부분

`TASK-MONO-618` 이 *"CI 도 이 스크립트도 이 축을 판정할 수 없다"* 를 근거를 들어
**수용**했다(값이 저장소 밖 Vercel env 에 있다). 그래서:

- **누가·언제** — 소유자가, 데모 기동 창마다 그리고 이 프로젝트 env 를 만질 때마다
- **명령** — `vercel env ls production --project kanggle-fan | grep DEMO_PAYMENT_MOCK`
- 🔴 **최종 판정은 env 목록이 아니다** — `/api/payment-config` 가 `{"demoPayment":true}` 를
  내야 한다. env 변경은 **다음 배포부터** 적용된다.
- 🔴🔴 **그런데 팬은 그 창구가 로그인 뒤에 있다.** `src/middleware.ts` 가 fail-closed 라
  (`TASK-FAN-FE-019` AC-1 의 선택지 (A)) 공개 경로는 `/login` · `/api/auth/*` ·
  `build-info.json` 뿐이고, 나머지는 전부 **307 → `/login`** 이다. 세션 없이 부르면 로그인
  페이지가 200 으로 오는데, 그 응답은 「값이 `false`」와 **구별되지 않는다.**
  ⇒ 판정 전에 유효성 술어를 놓아라: 최종 URL 이 `/login` 이 아닐 것.

### ✅ 소유자가 넣은 값 (2026-09-04 UTC)

🔵 실측값이 **여기** 있는 이유: 칸 (x2) 는 이 값을 빨갛게 만들 수단이 없으므로, 스크립트
메시지에 현재 상태를 적으면 소유자 조작 한 번에 거짓이 된다. 원장은 낡아도 **낡았다는 것을
날짜가 말해 준다.**

| Key | 값 | 환경 | 상태 |
|---|---|---|---|
| `DEMO_API_BASE` | 컨트롤 플레인 URL | Production | 🟢 투입·배포 완료 |
| `DEMO_PAYMENT_MOCK` | `1` | Production | 🟢 투입·배포 완료 |

🔴 **효과는 2026-09-04 시점에 아직 안 쟀다.** 데모가 꺼져 있었고(컨트롤 플레인 `/status` =
`stopped`), 위 fail-closed 때문에 창 밖에서는 원리적으로 못 잰다. 판정은
`TASK-MONO-622` AC-1·AC-2 가 기동 창에서 든다 — 그 티켓이 **판정의 소유자**다.
