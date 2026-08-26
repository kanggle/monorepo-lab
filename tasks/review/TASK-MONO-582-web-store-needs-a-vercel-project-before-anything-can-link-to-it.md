# Task ID

TASK-MONO-582

# Title

`ADR-MONO-067` **단계 2** — web-store 를 Vercel 에 올린다. **배선의 저장소 몫**(프로젝트 생성은 소유자).

# Status

review

# Owner

monorepo

# Task Tags

- adr
- demo
- ci

---

# ⏳ 선행 — **없다. 지금 착수 가능하다.**

| # | 선행 | 상태 |
|---|---|---|
| 1 | 앱이 백엔드 주소를 런타임에 얻는가 | ✅ `TASK-MONO-580` — `shared/config/demo-backend.ts` |
| 2 | 해석기를 어디 둘지 | ✅ `ADR-MONO-068` ACCEPTED — 앱 안에 |
| 3 | Vercel 무시 규칙의 공용 판정자 | ✅ `scripts/vercel-should-build.sh` (`TASK-MONO-562`·`572`) |

🔵 **소유자 승인이 필요한 것은 AC-4 하나뿐**이고 그것은 저장소 밖(Vercel 대시보드)이다.
AC-0~AC-3 은 승인 없이 끝난다.

---

# Goal

**web-store 를 Vercel 이 실제로 굽게 만든다.** `TASK-MONO-580` 은 앱이 데모 백엔드를 런타임에
찾도록 고쳤지만, **그 앱은 아직 Vercel 에 배포되지 않는다** — `vercel.json` 이 없기 때문이다.
이 티켓은 그 배선의 **저장소 몫**을 끝내고, 소유자가 대시보드에서 할 일을 한 페이지로 남긴다.

---

# Context — 실측 (2026-08-26)

## 🔴 내가 후속 계획에 적은 전제가 틀렸다

`TASK-MONO-580` 은 AC-3(론처의 스토어 링크를 Vercel 로)을 분리하면서 후속 작업을
*"(z14) 뒤집기 + 링크 전환, `TASK-MONO-579` 의 (z9) 뒤집기와 같은 크기"* 라고 적었다.

**착수 직전에 목적지를 실측했더니 목적지가 없었다:**

```
$ ls projects/ecommerce-microservices-platform/apps/web-store/
... vercel.json 없음 ...
$ find . -maxdepth 3 -name vercel.json -not -path '*/node_modules/*'
(0건)
$ git ls-files '*vercel.json'
infra/demo/aws/site/vercel.json
projects/fan-platform/web/fan-platform-web/vercel.json
```

**Vercel 프로젝트는 둘이고 web-store 는 그중 없다.** 링크를 바꿀 주소가 존재하지 않는다.
⇒ 후속은 한 건이 아니라 **둘**이다. 이 티켓이 앞의 하나이고, 링크 전환과 (z14) 뒤집기는
`TASK-MONO-583` 이다.

🔴 **"다음 단계의 전제부터 실측하라"** 가 이 자리에서 또 물었다. 580 의 그 문장은 *가드를
읽고* 쓴 것이라 (z14) 쪽은 정확했는데, **링크의 반대쪽 끝은 아무도 안 봤다.**

## 어느 축이 실제로 물릴 수 있는가 — 배포 압력

`ADR-MONO-067` AC-0 ④ 가 *"Vercel 무료 플랜의 한도"* 를 **아직 안 잰 항목**으로 남겼고,
이 저장소는 이미 그 플랜의 다른 한도(배포 rate limit)에 물려 24시간 동안 모든 PR 이 빨갰다.
**세 번째 프로젝트를 만드는 것이 정확히 그 축을 건드린다.** 그러니 재고 시작한다:

최근 30일(2026-07-27~08-26, `origin/main` 커밋 **479개**) 중 각 프로젝트의
**빌드 트리거 경로**를 건드린 커밋 수:

| Vercel 프로젝트 | 트리거된 커밋 | 비율 |
|---|---:|---:|
| `kanggle-portfolio` (론처) | 10 | 2.1% |
| `kanggle-fan` | 14 | 2.9% |
| **web-store (신설 시)** | **10** | **2.1%** |

세 번째는 형제들과 **같은 자릿수**다. 무시 규칙이 붙어 있는 한 빌드는 늘지 않는다.

## 🔴🔴 그런데 내가 잰 축은 **과금이 세는 축이 아니다**

위 표는 **빌드**를 세었다. Vercel 은 푸시마다 **배포(deployment)를 먼저 만들고**
`ignoreCommand` 는 그 뒤에 *빌드할지*를 정한다 — `Canceled by Ignored Build Step` 은
**배포가 생긴 뒤의 상태**다. 즉 세 번째 프로젝트는 **푸시마다 배포 1건을 무조건 더 만든다.**

그리고 **푸시 수는 저장소에서 잴 수 없다** — `git log` 는 커밋을 세지 푸시를 세지 않고,
PR 한 건의 5커밋 푸시는 배포 1건이다. 🔴 **그러니 이 티켓은 그 축을 "쟀다"고 말하지 않는다.**
소유자 대시보드가 유일한 출처이고, AC-0 이 그것을 **착수 전 확인 항목**으로 세운다.

🔵 이 구분을 적어 두는 이유: `잰 축 ≠ 과금 축` 은 이 저장소가 이미 한 번 물린 모양이다
(이미지 최적화 402 — 바이트를 쟀는데 과금은 변환 건수였다).

## 무시 규칙의 목록은 **JSON 밖**에 산다

`TASK-MONO-563` 이 이유를 남겼다: Vercel 스키마는 명령 문자열에 **`maxLength: 256`** 을 걸고,
`TASK-MONO-562` 가 pathspec 5개를 `ignoreCommand` 에 직접 넣어 **261자**가 되면서
**모든 배포가 0초에 죽었다**(빌드 로그조차 없음). ⇒ 새 프로젝트도 **래퍼 스크립트 방식**을
따른다. 론처의 인라인 방식은 *"짧아서 아직 안 죽은"* 쪽이지 본받을 쪽이 아니다.

## 가드는 세 번째를 **기다리고 있었다**

`scripts/check-vercel-build-triggers.sh` 는 모집단을 `git ls-files` 로 발견하고
하한(`FLOOR=2`)을 둔다. 주석에 이렇게 적혀 있다:

> *"하드코딩한 모집단을 쓰는 가드는 대상이 사라져도 자기가 적어둔 것을 계속 테스트하고
> 통과한다. **세 번째 Vercel 프로젝트가 생기면 이 가드는 그것을 봐야 한다.**"*

⇒ 파일을 놓기만 하면 가드가 **자동으로** 새 프로젝트의 무시 규칙을 실행해 대조한다.
남는 일은 **하한을 3으로 올리는 것**뿐이다(안 올리면 새 프로젝트를 지워도 안 문다).

---

# Scope

**In:**

- `projects/ecommerce-microservices-platform/apps/web-store/vercel.json` (신규)
- `projects/ecommerce-microservices-platform/apps/web-store/vercel-ignore.sh` (신규)
- `projects/ecommerce-microservices-platform/apps/web-store/VERCEL.md` (신규 — 소유자 절차)
- `scripts/check-vercel-build-triggers.sh` — `FLOOR` 2 → 3 (+ provenance)
- **"Vercel 프로젝트는 둘"** 이라 적힌 산문 전부 (아래 § Edge Cases 에 전수)

**Out:**

- 론처의 스토어 링크 전환 · `(z14)` 뒤집기 → **`TASK-MONO-583`**
- Vercel 대시보드에서의 프로젝트 생성 · 환경변수 입력 → **소유자**(AC-4 가 절차만 남긴다)
- 데모 호스트의 web-store 컨테이너 제거 → **`TASK-MONO-581`**(재굽기 번들)
- `next-auth` / OIDC 축 → **`D4`**, `ADR-MONO-067` 이 별도 결정으로 뺐다

---

# Acceptance Criteria

## AC-0 — 착수 전 확인 (재는 것이 아니라 **묻는 것**이다)

소유자에게 다음을 확인하고 답을 티켓에 적는다. 🔴 **저장소에서 잴 수 없는 축이다** —
"대략 괜찮을 것" 으로 대신하지 않는다.

1. Vercel 계정의 **현재 배포 사용량**과 플랜 한도(일/월). 세 번째 프로젝트가 그 한도에서
   얼마를 더 먹는지.
2. web-store 를 **자기 프로젝트**로 만들지, 기존 프로젝트의 경로로 얹을지.
   🔵 형제 둘이 각자 프로젝트이므로 기본값은 **자기 프로젝트**다.

## AC-1 — `vercel.json` — 형제가 죽은 방식으로 죽지 않는다

- `ignoreCommand` 는 **래퍼 스크립트**를 부른다. pathspec 을 문자열에 직접 넣지 않는다.
- 최상위 문자열 값의 **디코드된 길이**가 전부 **256 미만**이다.
- **주석 흉내 키(`"//..."`)가 없다.** `TASK-MONO-557` 이 그것으로 배포를 두 번 죽였다.
- `installCommand` 가 있다 — 없으면 루트 lockfile 을 찾아 monorepo 전체를 설치한다.

## AC-2 — 무시 규칙: **좁히는 쪽이 위험하다**

`vercel-ignore.sh` 의 pathspec 목록이 web-store 빌드에 **실제로 들어가는 것 전부**를 덮는다:
앱 자신 · 워크스페이스 멤버(`packages/*`) · 루트 `package.json` · lockfile ·
`pnpm-workspace.yaml` · 판정자와 래퍼 자신.

🔴 **빠뜨린 경로의 증상은 "배포 실패" 가 아니라 "조용히 건너뜀"** 이다 — CI 는 초록이고
사이트는 마지막 성공 배포를 계속 서빙하므로 URL 을 찔러도 200 이다. **아무도 안 본다.**
⇒ 의심스러우면 **넣는다.**

## AC-3 — 가드가 세 번째를 본다

- `scripts/check-vercel-build-triggers.sh` 의 `FLOOR` 를 **3** 으로 올리고 provenance 를 적는다.
- 가드를 **무망가 상태로 돌려** 새 프로젝트의 무시 규칙이 실제로 실행되어 통과하는지 본다.
- 🔴 **bite**: `vercel-ignore.sh` 에서 pathspec 을 지운 사본으로 가드가 **문다**는 것을 확인한다.
  (통과만 보면 "추출이 0건이라 아무것도 안 재고 초록" 과 구별되지 않는다.)
- `--self-test` 전 칸 통과.

## AC-4 — 소유자 절차를 **한 페이지로** 남긴다 (`VERCEL.md`)

저장소가 못 하는 일을 정확히 적는다:

| 항목 | 값 |
|---|---|
| Root Directory | `projects/ecommerce-microservices-platform/apps/web-store` |
| 필요한 환경변수 | `DEMO_API_BASE` (← `terraform output api_base_url`) |
| 프레임워크 | Next.js — 감지는 대시보드에 맡긴다 |

그리고 🔴 **첫 배포에서 깨질 수 있는 지점 둘**을 미리 지목한다(둘 다 저장소에서 판정 불가):

1. **`output: 'standalone'`** — `next.config.ts` 에 있다. Docker 용이고 Vercel 은 자기 빌드
   출력을 쓴다. 충돌하면 **첫 배포에서** 드러난다.
2. **`next-auth` v5 의 `NEXTAUTH_SECRET`** — 모듈 최상위에서 `NextAuth({secret})` 를 부른다.
   빌드 중 정적 생성이 그 경로를 타면 값 부재가 **빌드를 죽일 수 있다.**

🔵 **이 둘을 "아마 괜찮다" 로 적지 않는다.** 판정 출처는 소유자의 첫 배포 로그 하나뿐이고,
그 로그를 본 뒤에 이 문서를 갱신한다.

## AC-5 — 검증

- `bash scripts/check-vercel-build-triggers.sh` rc=0
- `bash scripts/check-vercel-build-triggers.sh --self-test` 전 칸 통과
- `bash -n` 으로 새 셸 스크립트 문법 검사
- 새 `vercel.json` 의 문자열 길이를 **실제로 파싱해서** 출력하고 티켓에 적는다

---

# 🎯 착수 중 발견 — **AC-2 의 방어가 산문뿐이었고, 그것을 bite 가 증명했다**

AC-2 는 *"목록을 좁히는 쪽이 위험하다 — 의심스러우면 넣는다"* 라고 적고 방어를
**「의심스러우면 넣는다」라는 지시**에 맡겼다. 그것이 지켜지는지 재는 것이 없었다.

배선을 끝낸 뒤 bite 를 두 번 걸었다:

| bite | 기대 | 실제 |
|---|---|---|
| pathspec 모양을 깨뜨림(따옴표 변경) | 문다 | ✅ rc=1 — 칸 (4)가 발화 |
| **목록에서 `packages/` 를 통째로 뺌** | 문다 | 🔴 **rc=0 — 침묵** |

**두 번째가 이 티켓의 본체가 됐다.** 기존 칸들은 *목록에 적힌 경로가 옳게 동작하는가* 만
보고, **적혀야 하는데 안 적힌 것**은 아무도 안 봤다. `@repo/{api-client,types,ui,utils}` 는
`transpilePackages` 로 **소스가 그대로 빌드에 들어가는데**, 그 패키지만 바뀐 커밋은
배포가 **조용히 건너뛰어지고** CI 는 초록이며 사이트는 마지막 성공 배포를 200 으로 서빙한다.

⇒ **칸 (12) 를 추가했다** — 앱의 `package.json` 에서 `workspace:*` 의존을 **파생**해
(하드코딩 아님) 각각이 트리거 목록에 덮이는지 단언한다. 이름 해석은 그 앱의
**워크스페이스 안에서만** 한다(프로젝트가 여럿이라 같은 패키지 이름이 두 곳에 있을 수 있고,
전역으로 찾으면 **엉뚱한 디렉터리**를 덮여 있다고 판정한다).

## 🔴🔴 그리고 그 칸의 초판이 **죽은 채로 초록을 보고했다**

칸 (12)를 처음 넣고 돌렸더니 rc=0 이었다 — 그런데 **(12)의 줄이 하나도 안 찍혔다.**
node 페이로드가 `python heredoc → bash heredoc → node` 로 이스케이프를 **세 겹** 지나면서
백슬래시가 한 겹 벗겨져 `SyntaxError` 로 죽었고, 파이프로만 읽던 `while read` 가
**빈 스트림을 돌아 조용히 통과**한 것이다.

🔴 **일을 하나도 안 하고 rc=0** — 이 파일의 헤더가 경고하는 바로 그 부류를, 그 파일을
고치면서 내가 다시 밟았다. 고친 것 둘:

1. **fail-closed** — node 의 종료코드를 읽고, **판정 줄(`COUNT`/`SKIP`)이 하나도 없으면 실패**.
   *"검사기가 죽은 것"* 과 *"위반이 없는 것"* 은 다른 사건이다.
2. **페이로드에서 백슬래시 리터럴을 전부 제거** (`String.fromCharCode(92|10)` + `norm()`).
   이스케이프 층이 셋이면 리터럴은 조용히 벗겨진다.

🔵 **대조군도 확인했다** — `fan-platform-web` 의 `SKIP: workspace:* 의존이 없습니다` 가
**참인지** 따로 셌다(의존 24개 중 `workspace:*` **0건**). 거짓 SKIP 이면 그 프로젝트는
안 재고 초록이 된다. web-store 는 **6개 전부 덮임**(양성 대조군).

---

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — 단계 2, AC-0 ④
- [`docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md`](../../docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md)
- [`projects/fan-platform/web/fan-platform-web/VERCEL.md`](../../projects/fan-platform/web/fan-platform-web/VERCEL.md) — 따라야 할 형태

# Related Contracts

없음 — API 계약을 바꾸지 않는다. 이 티켓은 **빌드/배포 배선**이다.

---

# Edge Cases

- 🔴 **"Vercel 프로젝트는 둘" 이라 적힌 자리가 넷이다.** 한 사실이 여러 절에 있으면
  한쪽만 고쳐진다 — 전수로 고친다:
  - `scripts/check-vercel-build-triggers.sh:5`
  - `scripts/vercel-should-build.sh:5`
  - `infra/demo/aws/site/build.sh:56`
  - `projects/fan-platform/web/fan-platform-web/VERCEL.md:96`
- `scripts/` 는 **repo-root 공유 경로**다(HARDSTOP-03). `FLOOR` 를 올리는 것은 숫자와
  provenance 주석뿐이고 프로젝트 경로를 리터럴로 넣지 않는다 — 모집단은 계속 발견된다.
- `vercel-ignore.sh` 는 **프로젝트 소유**다. 목록이 ecommerce 의 사실이므로 그 집에 둔다.
- `.gitattributes` 가 `*.sh` 를 `eol=lf` 로 고정한다. CRLF 로 커밋되면 Vercel 리눅스에서
  shebang 이 깨진다.

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| pathspec 을 좁게 잡음 | **조용히 건너뜀** — 사이트는 낡은 판을 200 으로 서빙 | AC-2: 의심스러우면 넣는다 · 판정자가 fail-open |
| `ignoreCommand` 가 256자 초과 | **배포가 0초에 죽는다**, 빌드 로그 없음 | AC-1 + 가드 칸 (5)가 디코드 길이를 잼 |
| `FLOOR` 를 안 올림 | 새 `vercel.json` 을 지워도 가드가 **안 문다** | AC-3 |
| 무시 규칙이 **전부** 건너뜀으로 고장 | CI 초록, 배포 없음, 아무도 모름 | 가드 칸 (1)이 본체 — "자기 경로가 바뀌면 빌드한다" |
| 세 번째 프로젝트가 한도를 넘김 | 24시간 전 PR 빨강 + 낡은 판 서빙 | AC-0 ① — **소유자에게 묻는다**(저장소에서 못 잼) |
| 첫 배포가 `standalone`/`NEXTAUTH_SECRET` 로 죽음 | 배포 실패 | AC-4 가 미리 지목 · 로그 보고 문서 갱신 |
