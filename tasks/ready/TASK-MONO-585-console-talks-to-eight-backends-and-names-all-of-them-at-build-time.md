# Task ID

TASK-MONO-585

# Title

`ADR-MONO-067` **단계 3** — 콘솔은 백엔드 **여덟 곳**을 부르고, 그 주소를 **빌드 타임에** 박는다.

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- frontend
- demo

---

# ⏳ 선행 — **하나는 소유자, 하나는 결정이다. 착수 전에 둘 다 확인하라.**

| # | 선행 | 상태 |
|---|---|---|
| 1 | `ADR-MONO-067` AC-0 ③ **OIDC 왕복** | ❌ `TASK-MONO-574` — 🔴 **콘솔은 D4 축에 정면으로 걸린다** |
| 2 | `ADR-MONO-068` **승격 트리거**가 이 티켓에서 발화한다 | 🔴 **아래 § 참조 — 소유자 결정 사안** |
| 3 | Vercel 프로젝트를 하나 더 만들어도 되는가 | ⏳ `TASK-MONO-575` |

🔴 **1번이 이 티켓의 진짜 게이트다.** `ADR-MONO-067` § D4 가 *"이 축이 안 풀리면 3·4단계는
못 간다"* 라고 명시했고, 콘솔은 **운영자 로그인이 화면의 전부**다 — 로그인이 안 되면 이관해도
빈 껍데기다. web-store 가 파일럿으로 뽑힌 이유가 *"D4 축에 아예 안 걸린다"* 였다.

---

# Goal

**콘솔의 브라우저가 Vercel 만 부르게 하고, 백엔드 주소를 런타임에 얻게 한다.**

---

# Context — 실측 (2026-08-26)

## 🔴 ADR 의 서술이 실측과 안 맞는다 — **AC-0 에서 다시 세라**

`ADR-MONO-067` § 결정 표는 단계 3 의 남은 일을 *"남은 **절대 fetch 5건** + OIDC/쿠키 축"*
이라고 적었다. **그 5건을 소스에서 못 찾았다:**

```
$ grep -rn "fetch(['\"`]http" projects/platform-console/apps/console-web/src
(0건)
```

대신 나온 것은 **`env.ts` 한 파일에 zod `.default()` 로 박힌 오리진 12개**다:

| 대상 | 개수 | 예 |
|---|---:|---|
| `iam.local` | 4 | `IAM_ADMIN_API_BASE`, 레지스트리·토큰교환·온보딩 |
| `wms.local` | 2 | admin · outbound |
| `finance.local` | 2 | `FINANCE_BASE_URL` · `LEDGER_BASE_URL` |
| `scm.local` · `erp.local` · `ecommerce.local` | 3 | 각 1 |
| `console.local` | 1 | `NEXT_PUBLIC_APP_URL` |

🔴 **두 숫자는 다른 것을 센 것일 수 있다** — ADR 의 5건은 `TASK-MONO-565` 가 **빌드 산출물**
에서 센 값이고, 위 12건은 **소스**다. **어느 쪽도 상대의 반증이 아니다.** 그래서 AC-0 이
**둘 다 다시 센다** — 상속하지 않는다.

🔵 **그러나 방향은 같다**: 주소가 **한 파일**에 모여 있고 **빌드 타임에 확정**된다.
데모 IP 는 부팅마다 바뀌므로 그 자리에 넣을 수 있는 고정값이 **존재하지 않는다.**

## 🔴🔴 이 티켓은 **`ADR-MONO-068` 의 승격 트리거를 발화시킨다**

`ADR-MONO-068` 은 *"해석기는 앱별로 구현한다. 단 **두 번째 구현이 생기는 순간 CI 가 RED**"*
로 ACCEPT 됐고, 트리거 숫자는 **2** 다. 지금 해석기를 가진 앱은 **web-store 하나**다.

⇒ **이 티켓이 두 번째가 되면 `scripts/check-demo-resolver-copies.sh` 가 RED 가 된다.
그것은 결함이 아니라 설계된 동작이다.** 그 시점에 결정해야 한다:

| 선택지 | 대가 |
|---|---|
| **공유 패키지로 승격** | 🔴 세 프로젝트가 **각각 별도 pnpm 워크스페이스**다(`ecommerce: apps/*+packages/*` · `fan: web/*` · `console-web: 단독`). 공유하려면 **루트 워크스페이스 신설** — `ADR-MONO-068` 이 되돌리기 비용으로 명시한 바로 그 항목 |
| **두 번째 사본을 감수** | 트리거를 상향(2 → 3)해야 하고, **그러면 트리거가 스스로 약해진다** |
| **그 밖** | 예: 해석기를 repo-root `libs/` 의 TS 판으로 — 🔴 `libs/` 는 **project-agnostic** 이어야 한다(HARDSTOP-03). 해석 로직 자체는 project-agnostic 이므로 성립할 수 있으나 **TS 모듈이 없다** |

🔴 **이 티켓은 그것을 고르지 않는다.** `ADR-MONO-068` 의 결정을 바꾸는 일이고
**소유자 정확형 지정** 사안이다(`platform/architecture-decision-rule.md`).
⇒ **AC-0 에서 멈추고 올린다.**

## 단계 3↔4 순서는 **열린 질문**이다

`TASK-MONO-578` 이 3↔4 순서의 근거를 무너뜨렸고(fan 이 더 싸 보인다) `ADR-MONO-067`
History 에 **열린 질문**으로 올라가 있다. **순서는 ACCEPT 된 결정의 일부라 내가 못 바꾼다.**

🔵 **어느 쪽이 먼저 가든 위 승격 트리거는 그 첫 번째에서 발화한다.** 즉 이 결정은
`TASK-MONO-586`(단계 4, fan)과 **공유**된다 — 먼저 착수하는 쪽이 그것을 만난다.

---

# Scope

**In:**

- `projects/platform-console/apps/console-web/` — 주소를 만드는 지점 · 모듈 경계 · 해석기
- 그 앱의 Vercel 배선(`vercel.json` + 무시 래퍼 + `VERCEL.md`) — `TASK-MONO-582` 와 같은 모양
- `scripts/check-vercel-build-triggers.sh` `FLOOR` 3 → 4
- `TEMPLATE.md` § 공개 호스트명 배분 — `console.hubwang.com` 상태 갱신

**Out:**

- 🔴 **`ADR-MONO-068` 승격 여부** → 소유자 정확형 지정 (AC-0 에서 올린다)
- D4(OIDC·쿠키) → `TASK-MONO-576` / `TASK-MONO-574`
- 론처 링크 전환 → 별도(단계 2 의 `TASK-MONO-583` 과 **같은 모양**의 후속)
- Vercel 프로젝트 생성 → 소유자 (`TASK-MONO-575` 게이트)

---

# Acceptance Criteria

## AC-0 — 착수 전에 **다시 세고, 막히면 올린다**

1. **모집단 재계수** — 소스의 zod `.default()` 오리진과 **빌드 산출물**의 오리진을 **각각**
   센다. 🔴 ADR 의 «5건» 을 상속하지 마라. 두 숫자가 다르면 **왜 다른지**를 적는다.
2. **어느 것이 클라이언트 번들에 들어가는가** — `NEXT_PUBLIC_*` 만이 아니다.
   🔴 `env.ts` 가 **한 모듈**이라 서버 전용 값도 클라 청크로 끌려갈 수 있다
   (`TASK-MONO-565` 가 *"전부 한 청크"* 라고 적었다). **산출물에서** 확인한다.
3. 🔴 **승격 트리거 결정을 소유자에게 올린다**(위 § 표). 답이 오기 전에는 해석기를
   **두 번째로 구현하지 않는다** — 구현하고 나서 물으면 그 답은 **이미 정해진 것**이 된다.
4. `TASK-MONO-574`(AC-0 ③ OIDC 왕복)의 상태를 확인한다. **미해결이면 여기서 멈춘다.**

## AC-1 — 브라우저가 백엔드를 직접 부르지 않는다

산출물 기준 백엔드 오리진 **0건**. 🔵 web-store 가 이미 이 상태였다(`TASK-MONO-565`).

## AC-2 — 서버가 주소를 **런타임에** 얻는다

`ADR-MONO-068` D1 에 따라, AC-0 ③ 의 답이 정한 자리에 둔다. 동작은 web-store 판과 같다:
`DEMO_API_BASE` 부재 → **부르지 않음** · `state != running` → **주소를 안 만듦** ·
`/status` 실패 → **기존 env 사슬**.

🔴 **콘솔은 백엔드가 여덟 곳이다** — web-store 처럼 «업스트림 하나» 가 아니다.
해석기가 **도메인별 접두사**를 붙일 수 있어야 하고, 그 매핑의 출처는 **하드코딩이 아니라
기존 env 키**여야 한다(둘이면 한쪽만 고쳐진다).

## AC-3 — 데모가 꺼져 있을 때

web-store 의 `DemoBackendNotice` 와 **같은 요구**다. 🔴 콘솔은 **여덟 곳 중 일부만** 죽을 수
있다 — «전부 아니면 전무» 로 표현하면 거짓말이 된다.

## AC-4 — Vercel 배선 + 가드

`TASK-MONO-582` 와 같은 모양(`vercel.json` · 래퍼 · `VERCEL.md` · `FLOOR` 상향).
🔴 `FLOOR` 를 **같은 커밋에서** 올려라 — 안 올리면 지워도 안 문다.

## AC-5 — 검증

- 산출물 오리진 **0건**을 **빌드해서** 확인(정규식 대리지표 아님)
- 해석기 단위 테스트(부재 · `running` 아님 · 타임아웃 · 여덟 도메인 매핑)
- 🔴 **`check-demo-resolver-copies.sh` 의 상태를 명시**한다 — RED 라면 **왜 RED 인지와
  AC-0 ③ 의 답**을 함께 적는다. 초록으로 만들려고 마커를 빼지 마라.
- `check-vercel-build-triggers.sh` rc=0 + `--self-test`
- 🔴 vitest 는 이 호스트에서 못 돈다(Node 24 + vitest 4) ⇒ **CI 권위**

---

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — 단계 3 · D4
- [`docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md`](../../docs/adr/ADR-MONO-068-where-the-demo-backend-resolver-lives.md) — 승격 트리거
- `TASK-MONO-580` — web-store 판 해석기(따라야 할 형태) · `TASK-MONO-582` — Vercel 배선
- `TASK-MONO-586` — 단계 4(fan). **승격 결정을 공유한다**

# Related Contracts

없음.

---

# Edge Cases

- 🔴 `console-bff` 는 **Traefik 호스트명이 없다**(`TASK-MONO-362`, 가드 (I2)가 지킨다).
  브라우저는 절대 닿지 않으므로 **공개 호스트명을 주지 마라.**
- 🔴 콘솔의 백엔드 여덟 곳은 **데모 도메인 접두사가 각각 다르다**(`iam`·`wms`·`scm`·
  `erp`·`finance`·`ecommerce`…). 한 곳만 매핑을 빠뜨리면 **그 화면만** 죽고 나머지가 멀쩡해
  원인이 안 보인다.
- `env.ts` 는 zod 스키마다 — `.default()` 를 지우면 **부팅이 실패**할 수 있다. 값을 옮기는
  것과 검증을 없애는 것은 다른 일이다.
- 🔴 콘솔은 **nightly-e2e 에만 도는 풀스택 스위트**가 있다(`CLAUDE.md` § Post-merge nightly).
  라우트·testid·heading 을 건드리면 **머지 시점에 한 번도 안 돌아 본 채** 초록일 수 있다.

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| ADR 의 «5건» 을 상속 | 모집단이 틀린 채 «끝났다» | AC-0 ① 재계수 |
| 해석기를 먼저 만들고 승격을 나중에 물음 | 답이 **이미 정해진 것**이 된다 | AC-0 ③ — 올리고 멈춘다 |
| 여덟 매핑 중 하나 누락 | 그 화면만 죽고 **원인이 안 보인다** | AC-2 — 매핑 출처를 기존 env 키로 |
| 트리거를 초록으로 만들려고 마커 제거 | 승격 트리거가 **영구 무력화** | AC-5 — 상태를 명시하되 회피 금지 |
| D4 미해결인데 착수 | 화면은 뜨는데 **로그인이 안 됨** = 빈 껍데기 | AC-0 ④ — 멈춘다 |
| `FLOOR` 미상향 | 새 `vercel.json` 을 지워도 **안 문다** | AC-4 |
