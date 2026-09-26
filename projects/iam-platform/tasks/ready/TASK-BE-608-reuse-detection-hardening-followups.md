# Task ID

TASK-BE-608

# Status

ready

# Title

재사용 탐지 후속 — Redis 카운터 원자성 · 이벤트에 원문 토큰 · 잠금 해제 경로 · 늦은 재제출 오탐의 크기

# Owner

iam-platform

# Task Tags

- auth-service
- security-service
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(①②) · 판독/측정(③④)

---

# Goal

`TASK-BE-606`(#4036, 2026-09-26 UTC)이 SAS 재사용 탐지를 실제로 켜고, 재사용 **횟수**가 잠금을 가르게 했다(1회 = ALERT, 1시간 안 2회 이상 = AUTO_LOCK).
그 결과 전에는 가려져 있던 넷이 드러났다(BE-606 § 후속):

1. 🔴 **카운터 원자성** — `RedisTokenReuseCounter` 가 `INCR` 과 `EXPIRE` 를 따로 부른다. `EXPIRE` 가 실패하면 키가 **영구**가 되고, 그 계정의 이후 재사용은 전부 2회 이상 = 잠금이다.
   이제 카운트가 점수를 가르므로 영향이 커졌다. (`INCR` + `EXPIRE NX` 를 한 스크립트/트랜잭션으로.)
2. **이벤트에 원문 토큰** — `auth.token.reuse.detected` 의 `reusedJti` 가 SAS refresh 토큰 **원문**이다. identity-platform 의 «refresh 토큰을 로그에 남기지 않는다» 와 충돌한다
   (이벤트는 security-service 에 적재된다). 해시로 바꾸려면 **계약 먼저**(`auth-events.md`) + 소비자 확인.
3. **잠금 해제 경로** — 자동 잠금 뒤 사용자가 어떻게 풀리는가(운영자 수동 · 시간 경과 · 셀프 복구)가 확인되지 않았다. 탐지가 켜졌으므로 확인이 필요하다.
4. **늦은 재제출 오탐의 크기** — 콘솔이 응답을 잃으면 쿠키를 그대로 두고(`console-web/src/shared/lib/session-refresh.ts:229-231`) 몇 분 뒤 같은 토큰을 다시 낸다 ⇒ 30초 유예 밖 = 재사용
   (패밀리 폐기 + ALERT). 잠금은 아니지만 사용자는 로그아웃된다. 빈도는 ⚪ 미측정.

# Scope

## 포함

- ① 원자화 + 단위 테스트(`EXPIRE` 실패 모사 → 키 TTL 이 남는다).
- ② 계약 결정 → 해시화(또는 필드 제거) + 소비자 영향 확인.
- ③ 코드·스펙 판독으로 해제 경로 표 — 없으면 소유자 결정 항목.
- ④ 데모 로그의 `refresh_error`/`refresh_failed` 빈도 측정(창 항목 — `TASK-MONO-672` 로 넘겨도 된다).

## 제외

- 유예 정책 자체(BE-606 결정) · 콘솔 POST 경로(`TASK-PC-FE-300`).

# Acceptance Criteria

- [ ] **AC-1** — 카운터 증가와 만료가 원자적이다(테스트로).
- [ ] **AC-2** — `reusedJti` 가 원문이 아니다 — 계약·구현·소비자 일치.
- [ ] **AC-3** — 잠금 해제 경로 표(경로 · 주체 · file:line) — 부재면 소유자 결정 요청.
- [ ] **AC-4** — 늦은 재제출 빈도: 측정값 또는 «측정 불가 + 이유».

# Related Specs

- `specs/contracts/events/auth-events.md` · `specs/features/abnormal-login-detection.md` · `platform/service-types/identity-platform.md`
- `TASK-BE-606`(§ 후속) · `TASK-PC-FE-300`

# Related Contracts

- `auth.token.reuse.detected` — `reusedJti` 의미 변경 시 계약 먼저.

# Edge Cases

| 상황 | 기대 |
|---|---|
| Redis 장애로 카운터를 못 읽음 | BE-606 결정대로 AUTO_LOCK(fail-closed) — 이 티켓은 바꾸지 않는다 |

# Failure Scenarios

1. **INCR 만 고치고 이미 영구화된 키를 둔다** → 배포 전 키가 남는다(정리 필요 여부를 판단).
2. **해시로 바꾸고 소비자를 안 본다** → security-service 가 원문을 기대하던 곳이 조용히 틀린다.
