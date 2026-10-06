# Task ID

TASK-FAN-FE-027

# Title

fan-platform-web 도 같은 모양의 refresh 경합 해저드를 갖고 있다 — `ecommerce web-store` 와 동일한 in-process-dedupe 가정

# Status

ready

# Owner

fan-platform

# Task Tags

- auth
- bug

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus 5.5 — `TASK-FE-106` 과 같은 레이어 판단이 필요하다.

---

# Dependency Markers

- 출처: `TASK-MONO-764` 23차 창 — `ecommerce web-store` 의 라이브 결함(`TASK-FE-106`)을
  조사하는 중 **코드 형태 일치**로 발견했다. 🔴 **fan-platform-web 자체는 이번 창에서
  라이브로 재현되지 않았다(n=0)** — 이 티켓은 코드 비교에서 나왔다.
- **선행으로 읍을 것: `TASK-FE-106`.** 같은 근본 원인(NextAuth `jwt` 콜백의 in-process
  refresh dedupe 가 Vercel 서버리스 다중 인스턴스를 못 넘는다)이므로, `TASK-FE-106` 이
  고른 해법을 **먼저 보고** 이 저장소의 관례(형제 grep, `CLAUDE.md` 10단계)대로 같은
  모양을 복사하는 것이 우선이다 — 독립적으로 다른 해법을 발명하지 않는다.

---

# 배경 — 코드 대조 (2026-10-06 UTC)

`projects/fan-platform/web/fan-platform-web/src/shared/auth/auth-callbacks.ts:116-122`:

```ts
/**
 * The `jwt` callback body — persists tokens on sign-in and performs proactive
 * silent refresh on subsequent calls (Phase 4.5 F3). Pure: only `fetch`
 * (mockable) and `Date.now` as side inputs.
 *
 * NextAuth serialises the `jwt` callback per session token, providing
 * in-flight deduplication of refresh calls.
 */
export async function jwtCallback({ token, account, profile, user }: JwtCallbackArgs) {
```

이것은 `ecommerce web-store` 의 `auth-callbacks.ts:174` 주석(`TASK-FE-106` 배경 참조)과
**문구까지 거의 동일**하다 — 둘 다 "NextAuth 가 `jwt` 콜백을 세션 토큰당 직렬화하므로
그것이 dedupe 지점이다" 라고 적고 있고, 둘 다 그 직렬화가 **한 Node 프로세스 안에서만**
성립한다는 전제를 놓치고 있다. 두 앱 다 Vercel 에 배포되므로, `TASK-FE-106` 이 겪은
다중 인스턴스 경합(유휴 뒤 복귀 시 병렬 요청 → 각 인스턴스가 독립적으로 refresh 시도 →
iam 유예창 내 재사용 거절 → 세션 붕괴)이 fan-platform-web 에서도 같은 코드 모양으로
일어날 수 있다.

---

# Goal

fan-platform-web 의 세션 refresh 가 다중 서버리스 인스턴스 경합에서도 세션을 깨뜨리지
않는다 — `TASK-FE-106` 의 결론과 같은 보증을 fan 쪾에도 둔다.

---

# Scope

## In Scope

- 착수 시 **먼저 `TASK-FE-106` 의 상태/결론을 확인**한다. 그 티켓이 아직 `ready`/
  `in-progress` 라면 그 결과를 기다리거나 같은 조사를 병행하되, **같은 결론에
  수렴시킨다**(두 번 다른 해법을 발명하면 쌍둥이 코드가 서로 다른 모양으로 갈라진다).
- fan-platform-web 에서 같은 재현 절차(유휴 뒤 복귀 + 동시 요청)를 시도한다.
- 재현되면(또는 안 되더라도 방어적으로) `TASK-FE-106` 이 고른 패턴을 fan-platform-web 의
  `jwt` 콜백 구조에 맞게 적용한다.

## Out of Scope

- `TASK-FE-106` 과 다른 독자적인 해법 설계 — 같은 메커니즘이면 같은 해법을 복사한다
  (다르다고 판단되면 그 이유를 이 티켓에 적을 것).
- iam 유예창 정책 변경(`TASK-BE-606`/`608`) — `TASK-FE-106` 과 동일하게 범위 밖.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정 + 선행 확인)** — `auth-callbacks.ts:122` 의 주석이 여전히 같은
      가정을 적고 있는지 확인하고, `TASK-FE-106` 의 현재 상태(진행 중/완료 시 어떤 해법을
      골랐는지)를 먼저 읍는다.
- [ ] **AC-1** — fan-platform-web 에서 같은 경합을 재현해 본다(라이브 또는 로컬). 재현
      결과를 PASS/FAIL/⚪ 로 기록한다 — 재현 안 되더라도 코드 형태 일치만으로 방어적 수정을
      진행할 수 있다(그 경우 근거를 적을 것).
- [ ] **AC-2** — `TASK-FE-106` 이 고른 패턴(또는 fan 구조에 맞게 조정한 동형의 패턴)을
      적용한다.
- [ ] **AC-3 (회귀)** — 기존 팔로우/반응/세션 테스트가 영향받지 않는다.

---

# Related Specs

- `projects/iam-platform/tasks/done/TASK-BE-606-sas-reuse-detection-misses-rotated-token-replay.md`
- `projects/iam-platform/tasks/done/TASK-BE-608-reuse-detection-hardening-followups.md`
- `projects/ecommerce-microservices-platform/tasks/ready/TASK-FE-106-store-session-lost-under-concurrent-refresh-on-serverless.md`
  (쌍둥이 티켓 — 먼저 읍을 것)

# Related Contracts

- iam SAS refresh-token 유예창 시맨틱(위 두 티켓이 정의)

---

# Edge Cases

- `TASK-FE-106` 이 이 티켓보다 먼저 닫히면, 이 티켓의 구현은 "복사 + fan 구조로 번역"
  작업이 된다 — 새로운 설계를 하지 않는다.
- 라이브 재현이 이번 창에서 안 될 수 있다(n=0 출발) — AC-1 이 그 경우도 허용한다.

# Failure Scenarios

- **`TASK-FE-106` 의 결론을 안 읍고 독립적으로 고친다** — 같은 메커니즘에 대해 두 코드베이스가
  서로 다른 해법을 갖게 되고, 다음 사람이 "fan 은 왜 다르게 짰나"를 또 조사해야 한다
  (`CLAUDE.md` 10단계가 경고하는 바로 그 패턴).
- **재현을 시도하지 않고 "같은 결함일 것"이라고만 적고 코드를 바꾼다** — AC-1 이 그
  확인을 요구한다.
