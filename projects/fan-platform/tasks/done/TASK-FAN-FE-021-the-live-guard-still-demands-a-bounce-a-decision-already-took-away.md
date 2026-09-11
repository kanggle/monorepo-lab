# Task ID

TASK-FAN-FE-021

# Title

🔴 라이브 가드가 **채택된 결정이 이미 없앤 리다이렉트**를 아직 요구한다 — 사흘째 빨강은 사이트가 아니라 기대가 낡은 것이다

# Status

done

# Owner

frontend

# Task Tags

- code
- test

---

# Goal

nightly 의 `Deployed fan surface` 잡(축 1 = `check-fan-guard-live.sh`)이
**`/artists` 가 `/login` 으로 튕기기를** 요구한다. 그런데 `/artists` 는
**공개 경로로 채택됐다**(`ADR-MONO-070/071`). 그래서 그 빨강은 **거짓 빨강**이다.

## 실측 (2026-09-10 UTC, 라이브 `https://fan.hubwang.com`)

```
[fan-guard] ✔ self-test 통과 — 고정 입력 10개를 서로 다르게 가른다.
[fan-guard] ✔ /nonexistent-xyz — 307 → …/login?from=%2Fnonexistent-xyz
[fan-guard] ✖ /artists — 200, 리다이렉트 없음 ⇒ **가드가 안 닫는다**(018 의 지문).
[fan-guard] ✔ /me — 307 → …/login?from=%2Fme
[fan-guard] ✔ /login — 200 (음성 대조군)
[fan-guard] ✔ /api/auth/providers — 200 (설정 있음)
[fan-guard] ⇒ **가드가 기대대로 안 닫는다**(exit 1).
```

🔵 **분류기 자가검사가 먼저 통과했다** — 그러므로 위 판정들은 장식이 아니다.

## 🔴 그런데 «가드가 안 닫는다» 는 결론이 틀렸다

| 자리 | `/artists` 에 대한 기대 |
|---|---|
| `src/shared/auth/public-paths.ts` | `PUBLIC_PREFIXES = ['/artists', '/posts']` — **공개** |
| 유닛 `middleware-public-paths.test.ts:174` | `expect(isPassThrough(await run('/artists'))).toBe(true)` — **공개** |
| 라이브 `check-fan-guard-live.sh:126` | **307 → `/login`** ← 🔴 낡음 |

`public-paths.ts` 헤더가 그 결정을 스스로 적어 뒀다: *"이 목록이 여는 화면들은 전부
`@demo/public-data` 저장본만 읽는다(게이트웨이를 안 부른다). 즉 «열려 있다» 가
«백엔드가 익명 요청을 받는다» 를 뜻하지 않는다."* 미들웨어를 마지막으로 바꾼 커밋도
**`9f0fcd2d6` — feat(demo): 공개 열람을 백엔드 없이 성립시키고 … (ADR-MONO-070/071)** 이다.

🔵 **그리고 가드가 지키려던 성질은 멀쩡하다** — 판별자 `/nonexistent-xyz` 가 **307** 이다
(미들웨어는 라우팅보다 먼저 도므로, 이것이 «가드가 돈다» 의 증거다. `TASK-FAN-FE-018`
의 결함은 여기가 **404** 였던 것이다). `/me` 도 307 로 닫힌다.
⇒ **고칠 것은 사이트가 아니라 스크립트의 기대다.**

## 🔴🔴 이것이 이 저장소가 이름 붙인 그 함정이다

*"핀이 지키려던 결함을 얼린다"* 의 반대 방향 —
**제품이 정당하게 바뀌었는데 핀이 옛 세계를 요구한다.** 그리고 그 대가가 크다:
이 빨강 때문에 `Deployed fan surface` 잡이 **사흘 연속 빨갰고**(09-07·08·09),
그 사이 **진짜 빨강이 났어도 구별되지 않았다.**

---

# Scope

## In Scope

- `check-fan-guard-live.sh` — `/artists` 의 기대를 `public-paths.ts` 에 맞춘다.
- 🔴 그리고 **공개가 된 경로를 «그냥 빼지 말고»** 「공개여야 한다」로 **뒤집어** 단언한다
  (§ Implementation Notes).

## Out of Scope

- **미들웨어·`public-paths.ts` 변경.** `/artists` 공개는 **채택된 결정**이다.
  🔴 여기서 그것을 되돌리려 하지 마라 — 그건 ADR 사안이다.
- **nightly 감시자가 이 빨강을 조용히 닫은 문제** — 별건이고 이미 집이 있다
  (`TASK-MONO-661`). 🔵 두 결함은 겹치지 않는다: 661 은 «빨강이 안 보인다»,
  이 티켓은 «그 빨강이 거짓이다».
- `ami-generation-watch` 빨강 — 같은 런의 다른 잡이고 원인 미측정(661 § 안 잰 것).

---

# Acceptance Criteria

- [x] **AC-0 (모집단 재측정)** — 착수 시점에 `check-fan-guard-live.sh` 가 검사하는
      **모든 경로**를 `public-paths.ts` 와 대조해 어긋난 칸을 **전부** 나열한다.
      🔴 `/artists` **하나만 고치고 끝내지 마라** — close chore 가 라이브에서 본 것이
      하나일 뿐, 스크립트의 다른 칸(`/posts` 등)은 **안 쟀다**.
      🔵 `public-paths.ts` 는 `PUBLIC_PREFIXES` · `PUBLIC_EXACT` · `INFRA_PREFIXES`
      **세 갈래**다 — 한 갈래만 보면 또 어긋난다.
- [x] **AC-1** — 스크립트가 `https://fan.hubwang.com` 에 대고 **rc=0** 이다.
- [x] **AC-2 (가드가 약해지지 않았다)** — 🔴 **공개가 된 경로를 검사 목록에서 «빼면 안
      된다».** 빼면 그 경로가 «공개여야 한다» 는 성질을 **아무도 안 잰다** — 훗날
      실수로 닫히면(로그인 벽이 생기면) 조용히 통과한다. **기대를 뒤집어** 그 칸을
      살려라: `/artists` → **200 이어야 하고 리다이렉트가 없어야 한다**.
- [x] **AC-3 (판별자 보존)** — `/nonexistent-xyz` → **307** 칸이 **그대로** 있다.
      🔴 그것이 「미들웨어가 도는가」를 재는 유일한 칸이고, `TASK-FAN-FE-018` 의 결함이
      정확히 그 칸이었다. 이 칸을 건드리면 이 스크립트가 존재할 이유가 사라진다.
- [x] **AC-4 (자가검사 유지)** — `--self-test` 가 **그대로 통과**한다(고정 입력 10개).
- [x] **AC-5 (bite)** — `public-paths.ts` 에서 `/artists` 를 빼면(= 다시 보호 경로가
      되면) 스크립트가 **빨개진다**. 🔴 AC-2 를 「뒤집어 단언」으로 한 것이 실제로 무는지
      증명한다. 🔵 라이브에 대고는 못 하므로, 로컬 prod build + 서버로 재라
      (`TASK-FAN-FE-019` 가 그 방법을 이미 썼다 — 라이브 없이 로컬에서 재현).
- [x] **AC-6** — 게이트가 **각각 독립 statement + 명시 `rc=$?`**
      (`--self-test` · 라이브 실행 · `vitest run`).

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — `PROJECT.md`
> (`domain: fan-platform`) → `rules/common.md` → 선언된 domain/trait 파일.

- `projects/fan-platform/PROJECT.md`
- `src/shared/auth/public-paths.ts` (허용 목록 — **정본**)
- `src/__tests__/middleware-public-paths.test.ts` (유닛 — 같은 결정을 이미 단언한다)
- `ADR-MONO-070` / `ADR-MONO-071` (공개 열람 결정)
- `TASK-FAN-FE-018` / `TASK-FAN-FE-019` (이 스크립트가 태어난 결함)
- `TASK-MONO-600` (이 잡을 시계에 얹은 티켓)

# Related Contracts

- 없음.

---

# Target App

- `projects/fan-platform/web/fan-platform-web`

---

# Implementation Notes

- 🔴🔴 **«빼기» 와 «뒤집기» 는 다른 수리다.** 빼면 가드가 **덜 잰다**(공개여야 할
  경로가 닫혀도 조용하다). 뒤집으면 **같은 수의 칸으로 반대 성질을 잰다.**
  이 저장소는 이미 그 교훈을 적어 뒀다 — *"금지→허가 가드는 **반전**이지 **축소**가 아니다"*.
- 🔵 스크립트의 루프(`for path in /nonexistent-xyz /artists /me`)는 세 경로에 **같은
  기대**를 먹인다. 경로마다 기대가 다르므로 그 구조부터 바꿔야 한다 —
  «경로 → 기대» 표로 만들면 AC-0 의 대조도 같이 쉬워진다.
- 🔵 `public-paths.ts` 가 **한 곳뿐**임을 그 파일이 스스로 적어 뒀다(`auth.ts` 의
  `authorized` 콜백도 같은 함수를 부른다). ⇒ 스크립트가 그 목록과 갈리는 것이
  **유일한 사본 지점**이고, 이 티켓이 그것을 닫는다.

---

# Edge Cases

- `/artists/<id>` 하위 경로 — 접두사 공개다(`/artistsxyz` 는 **아니다**).
  🔵 유닛이 두 방향을 이미 단언하므로 라이브에서 전부 재현할 필요는 없다.
- `/membership` 은 **정확 일치** 공개이고 `/membership/history` 는 **보호**다.
  🔴 스크립트가 이 쌍을 안 재고 있으면 AC-0 에 «안 잰 칸» 으로 적어라(여기서 추가할지는
  선택이지만, **안 잰다는 사실은 적어야 한다**).

---

# Failure Scenarios

- **공개가 된 경로를 목록에서 빼고 초록을 만든다** → AC-2 가 막는다(가드가 약해진다).
- **`/nonexistent-xyz` 칸을 건드린다** → AC-3 이 막는다(스크립트의 존재 이유).
- **미들웨어를 고쳐서 스크립트를 맞춘다** → 방향이 반대다. 공개는 채택된 결정이다.

---

# Test Requirements

- `--self-test` 통과 유지
- 라이브 rc=0
- bite: `public-paths.ts` 에서 `/artists` 제거 → 빨개짐 (로컬 prod build)

---

# 분석 / 구현 권장

분석=**Opus 5** / 구현 권장=**Sonnet** (한 스크립트 + 판정 규칙이 위에 박혀 있다.
🔴 단 AC-2 의 «뒤집기» 를 «빼기» 로 오해하면 가드를 죽인다 — 그 한 칸만 Opus 수준의 주의)

---

# 구현 기록 (ready → review, 2026-09-10 UTC)

## § AC-0 — 스크립트가 검사하는 **모든** 경로를 `public-paths.ts` 와 대조했다

라이브 실측(`https://fan.hubwang.com`)과 목록을 나란히:

| 경로 | 라이브 | `public-paths.ts` | 옛 스크립트 기대 | 판정 |
|---|---|---|---|---|
| `/nonexistent-xyz` | 307 → /login | 보호(목록에 없음) | 꺾여야 | ✅ |
| `/artists` | **200** | **공개**(`PUBLIC_PREFIXES`) | 꺾여야 | 🔴 **어긋남** |
| `/me` | 307 → /login | 보호 | 꺾여야 | ✅ |
| `/login` | 200 | 공개(`INFRA_PREFIXES`) | 200 | ✅ |
| `/api/auth/providers` | 200 | 공개(`INFRA_PREFIXES`) | 200 | ✅ |

⇒ **어긋난 칸은 정확히 하나**(`/artists`). 🔵 기안이 걱정한 *"하나만 고치고 끝내지 마라"* 는
전수 대조로 답했다 — 나머지는 원래 맞았다.

### 안 재고 있던 칸도 라이브로 확인했다

| 경로 | 라이브 | 목록 | 조치 |
|---|---|---|---|
| `/membership` | **200** | 공개(**정확 일치**) | ✅ **추가**(public) |
| `/membership/history` | **307 → /login** | 보호 | ✅ **추가**(closed) |
| `/artistsxyz` | **307 → /login** | 보호(접두사가 `/` 로 끊긴다) | ✅ **추가**(closed) |
| `/posts` | **404** | 공개(`PUBLIC_PREFIXES`) | 🔴 **안 넣는다** — 아래 |

🔴 **`/posts` 를 넣지 않은 이유**: 공개 접두사인데 **인덱스 라우트가 없어** 404 다.
`classify` 는 `4xx` 도 `open` 으로 부르므로(라우팅에 닿았다는 뜻), 넣으면 «열려 있다» 를
**틀린 이유로** 통과시킨다. 🔵 그리고 그 관찰이 설계를 바꿨다 — `public` 칸은 verdict 만
보지 않고 **2xx 까지** 단언한다(안 그러면 라우트가 사라져도 초록이다).

🔵 `/membership` 쌍은 `public-paths.ts` 가 스스로 *"이 목록에서 **가장 위험한 칸**"* 이라
적어 둔 자리다 — 라이브에서 실제로 갈린다는 것을 이제 잰다.

## § AC-1 / AC-2 / AC-3 — **빼지 않고 뒤집었다**

① 을 균일 루프에서 **경로 → 기대 표**로 바꿨다:

```
/nonexistent-xyz|closed     ← AC-3: 판별자 보존 (018 의 결함이 이 칸이었다)
/me|closed
/membership/history|closed  (신규)
/artistsxyz|closed          (신규)
/artists|public             ← 🔴 뒤집힌 칸
/membership|public          (신규)
```

**커버리지는 엄격한 상위집합이다**: 5칸 → **8칸**, 뺀 칸 **0**.
🔵 *"금지→허가 가드는 **반전**이지 **축소**가 아니다"* — 빼면 「공개여야 한다」를 아무도
안 재고, 훗날 로그인 벽이 생겨도 조용히 통과한다.

라이브 결과:

```
✔ /nonexistent-xyz  307 → …/login?from=%2Fnonexistent-xyz  (보호: 꺾인다)
✔ /me               307 → …  (보호: 꺾인다)
✔ /membership/history 307 → …  (보호: 꺾인다)
✔ /artistsxyz       307 → …  (보호: 꺾인다)
✔ /artists          200  (공개: 열려 있다)
✔ /membership       200  (공개: 열려 있다)
✔ /login            200 (음성 대조군)
✔ /api/auth/providers 200 (설정 있음)
⇒ 가드가 닫는다 · 공개 경로 살아 있음 · 설정 있음 (exit 0)
```

### 🔴 그리고 그 과정에서 **조용히 죽는 자리**를 하나 막았다

표를 `printf … | while read` 로 돌면 루프가 **서브셸**에서 실행돼 `rc`/`unreachable`
대입이 부모에 안 남는다 ⇒ **어떤 칸이 빨개도 스크립트가 0 을 낸다.** herestring
(`done <<< "$CASES"`)으로 바꿔 서브셸을 없앴다(셔뱅이 bash 임을 확인).

## § AC-4 — 자가검사는 손대지 않았다

`--self-test` **rc=0**, 고정 입력 **10개** 그대로. 분류기는 한 글자도 안 바꿨다 —
바뀐 것은 **기대**뿐이다.

## § AC-5 — bite. 🔴 **티켓이 지시한 방법 대신 픽스처 오리진을 썼다**

기안은 *"로컬 prod build + 서버"* 라고 적었다. 그렇게 하지 않았고, 사유는 둘이다:
① 이 호스트에는 **고아 `next start` 가 디스크 정리를 막는** 알려진 해저드가 있다
② prod build 는 인증 env 결핍으로 ③칸이 **다른 이유로** 빨개져 bite 가 모호해진다.

대신 응답을 통째로 통제하는 **픽스처 오리진**을 세웠다 — 변수는 `/artists` **하나뿐**이다:

```
대조군 (/artists → 200)          rc=0   ✔ 8칸 전부 초록
BITE  (/artists → 307 /login)    rc=1   ✖ /artists 만 빨강 — 빨간 칸 수 **1**
```

🔵 **이 bite 가 증명하는 것**: 「그 경로가 다시 꺾이면 이 칸이 문다」.
🔴 **증명하지 않는 것**: `public-paths.ts → 빌드 → 배포된 동작` 전체 사슬. 이 스크립트는
**배포된 오리진만 관측**하므로 그 사슬은 원리상 여기서 못 잰다 — 그 축은 유닛
(`middleware-public-paths.test.ts`)이 이미 덮는다.

### 🔴 bite 가 내 수정의 결함을 하나 잡았다

첫 bite 출력에 `check-fan-guard-live.sh: line 221: public-paths.ts: command not found`
가 섞였다 — `say "… \`public-paths.ts\` …"` 의 **백틱이 큰따옴표 안에서 명령 치환**이
됐다. 메시지가 자기 예시를 실행한 것이다. 리터럴 경로로 고쳤다.

🟢 **그리고 그 가드가 실제로 무는지 양성 대조군으로 확인했다** — 백틱을 되살리니
`scripts/check-message-backticks.sh` 가 **rc=1** 로 내 줄을 정확히 지목했다
(`…/check-fan-guard-live.sh:225`). 즉 *"CI 가 잡았을 것"* 은 추측이 아니다.

## § AC-6 — 게이트

```
bash -n check-fan-guard-live.sh              rc=0
--self-test                                  rc=0   고정 입력 10개
라이브 (https://fan.hubwang.com)              rc=0   8칸
픽스처 대조군 / BITE                          rc=0 / rc=1 (빨간 칸 1)
scripts/check-message-backticks.sh           rc=0   135파일, 위반 0
vitest run (fan)                             rc=0   32 files / 256 tests
```

🔴 **vitest 첫 실행이 6칸 빨갰다 — 그리고 그것은 내 변경이 아니다.** 지문은
`Found multiple elements`(테스트 오염)와 `Test timed out in 5000ms` 였다. 판별:
① **깨끗한 `main`** 에서 같은 스위트 **32/32 · 256/256 초록** ② **같은 worktree 재실행**
도 초록 ③ 내 diff 는 `check-fan-guard-live.sh` **한 파일**이고 vitest 는 그것을 **로드조차
안 한다**. ⇒ 결정적이지 않다.
🔵 **원인은 단정하지 않는다**(「flake=인프라」는 가설이다) — 사실만 적자면 첫 실행이
픽스처 HTTP 서버 넷과 라이브 curl 프로브가 돌던 구간과 겹쳤다.

## 🔴 안 잰 것

- **`public-paths.ts → 배포` 사슬** — 위 AC-5 참조. 원리상 이 스크립트의 관측 밖이다.
- **`/posts`** — 공개 접두사인데 인덱스 라우트가 없어 404 다. 라우트를 만들 일인지,
  목록에서 뺄 일인지는 **이 티켓이 판단하지 않는다**(제품 결정).
- **`/_next`·`/favicon.ico`·`/` 등 나머지 공개 항목** — 검사 목록에 없다. 넣지 않은 것은
  선택이지 누락이 아니지만, **안 잰다는 사실은 여기 적어 둔다.**
