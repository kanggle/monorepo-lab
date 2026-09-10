# Task ID

TASK-FAN-FE-021

# Title

🔴 라이브 가드가 **채택된 결정이 이미 없앤 리다이렉트**를 아직 요구한다 — 사흘째 빨강은 사이트가 아니라 기대가 낡은 것이다

# Status

ready

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

- [ ] **AC-0 (모집단 재측정)** — 착수 시점에 `check-fan-guard-live.sh` 가 검사하는
      **모든 경로**를 `public-paths.ts` 와 대조해 어긋난 칸을 **전부** 나열한다.
      🔴 `/artists` **하나만 고치고 끝내지 마라** — close chore 가 라이브에서 본 것이
      하나일 뿐, 스크립트의 다른 칸(`/posts` 등)은 **안 쟀다**.
      🔵 `public-paths.ts` 는 `PUBLIC_PREFIXES` · `PUBLIC_EXACT` · `INFRA_PREFIXES`
      **세 갈래**다 — 한 갈래만 보면 또 어긋난다.
- [ ] **AC-1** — 스크립트가 `https://fan.hubwang.com` 에 대고 **rc=0** 이다.
- [ ] **AC-2 (가드가 약해지지 않았다)** — 🔴 **공개가 된 경로를 검사 목록에서 «빼면 안
      된다».** 빼면 그 경로가 «공개여야 한다» 는 성질을 **아무도 안 잰다** — 훗날
      실수로 닫히면(로그인 벽이 생기면) 조용히 통과한다. **기대를 뒤집어** 그 칸을
      살려라: `/artists` → **200 이어야 하고 리다이렉트가 없어야 한다**.
- [ ] **AC-3 (판별자 보존)** — `/nonexistent-xyz` → **307** 칸이 **그대로** 있다.
      🔴 그것이 「미들웨어가 도는가」를 재는 유일한 칸이고, `TASK-FAN-FE-018` 의 결함이
      정확히 그 칸이었다. 이 칸을 건드리면 이 스크립트가 존재할 이유가 사라진다.
- [ ] **AC-4 (자가검사 유지)** — `--self-test` 가 **그대로 통과**한다(고정 입력 10개).
- [ ] **AC-5 (bite)** — `public-paths.ts` 에서 `/artists` 를 빼면(= 다시 보호 경로가
      되면) 스크립트가 **빨개진다**. 🔴 AC-2 를 「뒤집어 단언」으로 한 것이 실제로 무는지
      증명한다. 🔵 라이브에 대고는 못 하므로, 로컬 prod build + 서버로 재라
      (`TASK-FAN-FE-019` 가 그 방법을 이미 썼다 — 라이브 없이 로컬에서 재현).
- [ ] **AC-6** — 게이트가 **각각 독립 statement + 명시 `rc=$?`**
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
