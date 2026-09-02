# `@demo/backend-resolver`

데모 백엔드 주소의 **런타임 해석** — 이 저장소에서 **유일한 구현**이다.
`ADR-MONO-068 § D6 = B2` (2026-09-01, 소유자 정확형 지정) / `TASK-MONO-614`.

```ts
import { createDemoBackendResolver } from '@demo/backend-resolver';

const resolver = createDemoBackendResolver({
  servicePrefix: 'ecommerce',
  fallbackEnvNames: ['API_URL_INTERNAL', 'NEXT_PUBLIC_API_URL'],
  fallbackBaseUrl: 'http://localhost:8080',
});

export const {
  resolveDemoBackend,
  resolveDemoBackendState,
  resolveUpstreamBaseUrl,
  __resetDemoBackendCache,
} = resolver;
```

---

## 자리 — 왜 `infra/demo/` 이고 왜 나머지가 아닌가

`TASK-MONO-614 AC-1` 이 *"고르고 나서 「왜 나머지가 아닌가」를 적어라"* 를 요구한다.

### 고른 이유 — **계약과 그 클라이언트들이 이미 여기 산다**

이 모듈이 읽는 `DEMO_API_BASE` 는 앱의 설정이 아니라 **계약**이고, 그 이름을 정하는 것은
[`infra/demo/aws/site/build.sh`](../aws/site/build.sh) 다. 같은 계약의 **다른 클라이언트**인
론처(`infra/demo/aws/site/index.html`)도 여기 있고, `<ip-대시>.sslip.io` 파생 규칙을 공유하는
[`infra/demo/demo-boot.sh`](../demo-boot.sh) 도 여기 있다.

⇒ 세 번째 클라이언트의 공유 구현만 다른 최상위 디렉터리로 보내면 **계약이 그 클라이언트들과
갈라진다**. 파생 규칙을 바꾸는 사람이 봐야 할 파일이 두 디렉터리로 흩어진다.

### 기각 ① 루트 `libs/<name>`

🔴 [`CLAUDE.md` § Repository Layout](../../../CLAUDE.md) 이 그 디렉터리를 **`shared Java
libraries`** 라고 명시한다. 실측(2026-09-02): `libs/` 아래 파일 확장자는 `java` 189 · `gradle`
13 · `md` 2 · `imports` 2 · `xml` 1 — **TS/JS 0개**.

🔴 그리고 더 나쁜 것은 **가드가 그것을 못 본다는 점**이다.
[`scripts/check-libs-ci-coverage.sh`](../../../scripts/check-libs-ci-coverage.sh) 의 모집단은
디스크의 디렉터리가 아니라 **`settings.gradle` 이 포함한 모듈**이다. TS 디렉터리는 거기
안 들어가므로 그 가드는 *"N개 모듈 전부 자기 `:check` 를 돌린다"* 는 **초록을 그대로 낸다** —
커버리지 밖의 모듈이 하나 생겼는데 커버리지 가드는 아무 말도 하지 않는다.
읽히지 않는 자리에 두는 것은 무해가 아니라 **거짓 증거**다.

### 기각 ② 루트 `packages/<name>`

새 최상위 디렉터리이고 `CLAUDE.md` · `TEMPLATE.md` 의 레이아웃을 늘려야 한다 — 그 자체는
비용이 아니다. 🔴 기각한 이유는 **읽히는 방식**이다: 루트 `packages/` 는 이 저장소에서
`ADR-MONO-068 § D6` 의 **`B`(루트 워크스페이스)** 와 구별되지 않는다. `pnpm-workspace.yaml`
없이 디렉터리만 만들어도 다음 사람은 *"루트 워크스페이스가 있구나"* 로 읽고 거기에 두 번째
패키지를 놓는다. 채택된 것은 `B2` 이고, **`B2` 와 `B` 를 가르는 것은 정확히 그 한 줄**이다.

🔵 루트 `package.json` 이 자기 `description` 에 *"No workspace / no dependencies at this
level"* 이라고 적어 두었다. 그 문장을 지키는 자리를 골랐다.

### 기각 ③ 어느 한 프로젝트의 `packages/` (예: ecommerce 의 `@repo/*` 옆)

🔴 `fan-platform` 이 `ecommerce` 의 내부를 의존하게 된다 — 프로젝트 경계 위반이고,
`TEMPLATE.md` 의 추출(한 프로젝트만 떼어내기)이 그 자리에서 깨진다.

### 🔵 `infra/` 가 런타임 코드를 담은 적이 없다는 말은 사실이 아니다

`TASK-MONO-614` 의 후보 표에 *"`infra/` 는 지금까지 **런타임 코드가 아니었다**"* 라고
적혀 있었는데, **틀렸다**: [`infra/demo/aws/site/`](../aws/site/) 는 `vercel.json` +
`build.sh` + `index.html` 을 가진 **배포되는 Vercel 프로젝트**(`kanggle-portfolio`)다.
자리 선택의 걸림돌 하나가 실재하지 않았다.

---

## 이름 — 왜 `@repo/` 가 아닌가

`projects/ecommerce-microservices-platform/packages/` 의 여섯 패키지가 `@repo/<name>` 을
쓴다(`ADR-MONO-068 § D6.2`). 관용구 중 **따를 것**은 따랐다 — `private: true` ·
`version: 0.0.0` · `main`/`types` 가 `./src/index.ts` (빌드 산출물이 아니라 TS 소스).

🔴 **스코프만 다르게 했다.** `@repo/*` 는 **그 워크스페이스 안에서 `workspace:*` 로 해석되는
이름들**이다. 그 워크스페이스 밖에 사는 패키지가 같은 스코프를 쓰면, `web-store` 의
`package.json` 안에 워크스페이스 멤버와 그렇지 않은 `@repo/*` 가 **섞인다** — 읽는 사람이
어느 것이 워크스페이스 멤버인지 이름으로 구별할 수 없게 된다.

---

## 소비자는 `link:` 로 의존한다 — `workspace:*` 도 `file:` 도 아니다

```jsonc
// projects/ecommerce-microservices-platform/apps/web-store/package.json
"@demo/backend-resolver": "link:../../../../infra/demo/backend-resolver"
// projects/fan-platform/web/fan-platform-web/package.json
"@demo/backend-resolver": "link:../../../../infra/demo/backend-resolver"
```

🔵 두 앱이 **깊이가 같아** 상대경로 문자열이 같다.
🔴 `workspace:*` 는 `B` 다 — 그것을 쓰려면 루트 `pnpm-workspace.yaml` 이 있어야 하고,
`B2` 는 그것을 만들지 않기로 한 안이다. `link:` 는 **상대경로 의존**이므로 `B2` 안이다.

### 🔴🔴 왜 `file:` 이 아니라 `link:` 인가 — CI 가 가르쳤다

첫 판은 `file:` 이었고, 그러자 web-store 의 **vitest 4 가 기동하다 죽었다**:

```
[BUNDLER_INITIALIZE_ERROR] Invalid jsx option: `automatic`.
  Plugin: vite:oxc
  File: .../node_modules/.pnpm/@demo+backend-resolver@file+..+..+infra+demo+backend-resolver/…/src/index.ts
```

**기전은 «어디에 있는가» 다.** 두 프로토콜이 만드는 것이 다르다 (실측):

| 의존 | `node_modules/@…` 의 realpath |
|---|---|
| `@repo/utils` (`workspace:*`) | `projects/…/packages/utils` — **저장소 실경로** |
| `@demo/backend-resolver` (**`file:`**) | 🔴 `node_modules/.pnpm/@demo+backend-resolver@file+…/` — **node_modules 안** |
| `@demo/backend-resolver` (**`link:`**) | ✅ `infra/demo/backend-resolver` — **저장소 실경로** |

`file:` 은 **가상 스토어로 복사**하고 `link:` 는 **심링크**한다. Vite/oxc 는 realpath 가
`node_modules` 안인 파일을 앱 소스와 다르게 다루고, 거기서 이 앱에 유효하지 않은 jsx 옵션이
적용된다.

🔵 **`link:` 가 형제들이 쓰는 바로 그 기전이다.** 락파일이 그것을 직접 말한다 —
`@repo/api-client` 의 `specifier: workspace:*` 가 **`version: link:../../packages/api-client`**
로 풀린다. 즉 `workspace:*` 는 «워크스페이스에서 이름을 찾은 뒤 `link:` 하는 것» 이고,
`B2` 는 그 **이름 찾기만 뺀** 것이다.

🔴 **그래서 첫 수정(«tsconfig.json 이 없어서다»)은 원인이 아니었다.** 그 파일을 넣고도
CI 는 **같은 오류**를 냈다 — 증상이 살아남으면 그것은 원인이 아니다. 🔵 tsconfig 는 그대로
두었다: 이제는 realpath 가 저장소 안이라 **실제로 발견되고**, 형제들이 전부 갖고 있는
것이기도 하다(아래).

`main` 이 TS 소스이므로 소비자의 `next.config.ts` 는 이 이름을 **`transpilePackages`** 에
넣어야 한다(`@repo/*` 가 web-store 에서 이미 그렇게 쓰인다).

---

## `tsconfig.json` — 형제들이 전부 갖고 있어서 둔다

`projects/ecommerce-microservices-platform/packages/{api-client,types,ui,utils}` 는 **넷 다
자기 `tsconfig.json` 을 갖는다.** 같은 방식(TS 소스 직접 노출)으로 소비자의 변환기를 지나는
패키지들이므로 그 모양을 따랐다. 🔴 그리고 **넷 다 `jsx` 를 설정하지 않는다** ⇒ 여기서도
설정하지 않는다. 값은 `@repo/tsconfig/base.json` 을 그대로 옮겼다.

🔴 그들처럼 `@repo/tsconfig/library.json` 을 `extends` 할 수는 **없다** — 그 패키지는
ecommerce 워크스페이스의 멤버이고 이 패키지는 그 밖에 산다(`B2` 가 루트 워크스페이스를
만들지 않기로 한 결과다). 그래서 자족적으로 적었다.

🔵 이 저장소의 tsconfig 13개 중 **주석을 쓰는 것은 0개**라, 설명을 JSON 안에 두지 않고
여기 둔다.

🔴🔴 **이 파일은 위 `link:` 문제의 «수정» 이 아니었다.** 처음엔 그렇다고 판단해 넣었고,
넣은 뒤에도 CI 는 **같은 오류**를 냈다. 남겨 두는 이유는 형제와 모양을 맞추기 위해서이고,
`link:` 로 바꾼 지금은 realpath 가 저장소 안이라 **실제로 발견된다.**

### 🔵 `files` 에 `tsconfig.json` 을 넣어 둔 이유 — `file:` 시절의 흉터

`file:` 이던 동안 pnpm 은 이 패키지를 **가상 스토어로 복사**했고, 그 복사는 `package.json` 의
**`files` 를 따랐다**. `files: ["src"]` 였을 때 소비자가 실제로 받은 것은
`README.md · package.json · src` **뿐**이었다 — 즉 트리에는 있는데 **소비자에게는 없는**
파일이 생긴다. `link:` 인 지금은 복사가 없어 `files` 가 이 축에 영향을 주지 않지만,
누군가 `file:` 로 되돌리면 그 함정이 되살아나므로 목록에 남겨 둔다.

🔵 **선언 파일 grep ≠ 런타임 모집단.** «파일을 만들었다» 와 «소비자에게 도달한다» 는 다른
사건이고, 후자는 따로 재야 한다:

```bash
node -e "console.log(require('fs').realpathSync('node_modules/@demo/backend-resolver'))"
```

---

## 🔴 이 패키지는 자기 테스트를 갖지 않는다 — 의도다

두 소비자의 기존 스위트(각 196줄)가 이 구현을 **각자의 설정으로** 통과시키고, 그 둘이 바로
이 패키지가 파라미터로 받는 축이다(web-store 는 `API_URL_INTERNAL` 사슬, fan 은
`GATEWAY_URL_INTERNAL` 사슬). 두 소비자를 통과하는 것이 단일 스위트보다 **파라미터화를 더
잘 잰다**.

🔴 그리고 여기 vitest 스위트를 두면 **러너가 없다** — 이 저장소의 프런트 유닛 잡은
`ecommerce + fan-platform + console-web` 워크스페이스를 돌 뿐, `infra/` 를 돌지 않는다.
러너 없는 스위트는 썩고, 썩은 스위트는 «있다» 는 이유로 아무도 안 본다.
스위트를 여기 두려면 **CI 잡을 같이** 만들어야 한다.

---

## 🔴 되돌리는 법 (`TASK-MONO-614 AC-4`)

이 변경은 세 Vercel 프로젝트 중 **둘**의 install 단계가 Root Directory **밖**의 경로를
참조하게 만든다. 그 설정(*"Include files outside root directory"*)은 **대시보드에만** 있다.

**측정된 것 (2026-09-02)**

| 프로젝트 | Root Directory 밖 포함 | 근거 |
|---|---|---|
| `kanggle-store` | ✅ **ON** | Root Directory 는 `apps/web-store` 인데 `@repo/*` **6개를 `workspace:*`** 로 의존한다. 그 패키지들은 `packages/*` — **밖**이다. `workspace:` 는 워크스페이스 루트 없이 해석이 **불가능**하고 (패키지가 `private: true` 라 레지스트리 폴백도 없다), 그런데 `https://store.hubwang.com` 이 **Next 렌더를 200 으로 서빙한다**(`Server: Vercel`, `X-Matched-Path: /`) |
| `kanggle-fan` | 🔴 **미지수** | fan 은 `workspace:*` 의존이 **없다**. 그래서 fan 의 배포 성공은 이 축에 대해 **아무것도 증명하지 않는다** |

🔵 `ADR-MONO-068` 표의 ㉯(*"Next 빌드가 루트 lockfile 을 본다"*)는 **로컬 빌드**의 관측이다.
Vercel 의 설정을 증명하지 않는다.

**그래서 관측을 머지 앞으로 당겼다.** 세 `vercel.json` 이 `git.deploymentEnabled` 에
`"preview/*": true` 를 두고 있으므로, 이 작업의 브랜치 이름을 `preview/...` 로 하면
**머지 전에 실제 Vercel 빌드가 돈다.** 관측 결과는 `TASK-MONO-614` 의 구현 결과 절에 적는다.

**되돌리기 — 실패했을 때**

1. **즉시**: 이 PR 을 머지하지 않는다. 브랜치 배포는 Preview 환경이라 **프로덕션 URL 은
   그대로다**(`store.hubwang.com` · fan 도메인은 마지막 성공 배포를 계속 서빙한다).
2. 이미 머지된 뒤라면 — `git revert <squash-sha>` 한 커밋이면 된다. 이 변경은 **파일 추가 +
   두 앱의 import 교체 + 가드 교체**뿐이고, 마이그레이션도 상태도 없다.
   revert 가 main 에 들어가면 `vercel-deploy.yml` 이 두 훅을 다시 쏘아 **마지막으로 성공하던
   판**을 굽는다.
3. 🔵 대시보드에서 해당 프로젝트의 *"Include files outside root directory"* 를 켜는 것이
   **진짜 수정**이다. revert 는 그때까지의 지혈이다.
