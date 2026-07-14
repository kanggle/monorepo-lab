# Task ID

TASK-BE-512

# Title

auth-service 이메일별 로그인 rate-limit 의 429 에 `Retry-After` 헤더 누락 (계약: 429 는 항상 포함)

# Status

ready

# Owner

backend

# Task Tags

- code
- api

---

# 🔴 발견 경위 (2026-07-15 라이브 풀스택 스윕)

게이트웨이 경유 로그인을 반복 호출해 두 rate-limit 레이어를 각각 발화시켜 429 응답 헤더를 비교했다. **Layer-1(게이트웨이 IP 버킷)은 `Retry-After` 포함, Layer-2(auth-service 이메일별 실패 카운터)는 누락**. `rate-limiting.md` 는 "429 응답은 항상 `Retry-After` 를 포함한다" 고 규정한다. 아래는 실측 — **착수 시 재현부터.**

---

# Goal

auth-service 의 이메일별 연속 로그인 실패 rate-limit 이 429 를 반환할 때 **`Retry-After` 헤더를 포함**하게 한다.

## 실측 근거 (2026-07-15, 재검증 대상)

| 레이어 | 발화 조건 | 응답 코드 | `Retry-After` |
|---|---|---|---|
| Layer-1 (게이트웨이 IP 토큰버킷) | 서로 다른 이메일로 15회 | `429 RATE_LIMITED` | **`60` ✅** |
| Layer-2 (auth-service per-email 카운터) | 같은 이메일 6회 오답 | `429 LOGIN_RATE_LIMITED` | **없음 ✗** |

# Root Cause

`apps/auth-service/src/main/java/com/example/auth/presentation/exception/AuthExceptionHandler.java` 의 `LOGIN_RATE_LIMITED`(이메일별 카운터 초과) 응답 빌더가 429 상태와 본문은 세팅하지만 **`Retry-After` 헤더를 붙이지 않는다**. 게이트웨이 `RateLimitFilter` 는 붙인다.

# Scope

## In Scope
- `AuthExceptionHandler`(또는 해당 429 를 만드는 지점)에서 `LOGIN_RATE_LIMITED` 429 응답에 `Retry-After` 헤더 추가. 값은 rate-limit 윈도우 잔여시간(초) — `rate-limiting.md` 의 Layer-2 정책(5회/15분 윈도우) 및 게이트웨이 Layer-1 과 의미론 정합.
- 다른 429 발생 지점(있으면)도 `Retry-After` 유무 전수 확인.

## Out of Scope
- Layer-1 게이트웨이 rate-limit(이미 정상).
- rate-limit 임계값/윈도우 정책 변경(정책은 그대로, 헤더만 보강).
- **remaining-count/reset 헤더 노출 금지** 규칙 유지(`rate-limiting.md` — 공격자 조력 방지). `Retry-After` 만 추가하고 잔여 카운트는 노출하지 않는다.

# Acceptance Criteria

- [ ] **AC-0 (착수=재측정)**: 같은 이메일로 반복 오답 로그인 → `429 LOGIN_RATE_LIMITED` 에 **현재 `Retry-After` 없음** 재현. Layer-1 은 있음도 대조.
- [ ] Layer-2 `429 LOGIN_RATE_LIMITED` 응답에 `Retry-After`(초, 양의 정수) 포함.
- [ ] 값이 윈도우 잔여시간을 합리적으로 반영(고정 상수여도 계약상 허용되나, 의미 있는 값 권장).
- [ ] `remaining`/`reset` 류 카운트 헤더는 **여전히 미노출**(회귀 0).
- [ ] auth-service 테스트에 `LOGIN_RATE_LIMITED` 429 의 `Retry-After` 존재 단언 추가.
- [ ] `:check` GREEN.

# Related Specs

> Before reading: `platform/entrypoint.md` Step 0.

- `specs/features/rate-limiting.md` (2-layer 정책, "429 always Retry-After", 카운트 미노출)
- `specs/features/authentication.md` (5회/15분 실패 임계)
- `specs/services/auth-service/architecture.md`

# Related Contracts

- `specs/contracts/http/auth-api.md` (`POST /api/auth/login` 429 응답 형태) — 필요 시 `Retry-After` 명시 보강

# Target Service

- `auth-service`

# Edge Cases

- Layer-2 는 `/api/auth/login`(레거시)뿐 아니라 OIDC 경로에도 걸리는지 확인 — 걸린다면 그 429 도 동일 처리.
- `Retry-After` 는 초(delta-seconds) 또는 HTTP-date 둘 다 유효하나, Layer-1 과 형식 통일(초) 권장.
- 레거시 `/api/auth/login` 은 `TASK-BE-398` 로 일몰 예정 — 그래도 sunset(2026-08-01) 전까지 살아있으므로 이 헤더 결함은 유효(단, Layer-2 카운터가 OIDC 로그인에도 적용되면 일몰과 무관하게 필요).

# Failure Scenarios

- `Retry-After` 를 추가하면서 실수로 `X-RateLimit-Remaining` 류를 노출하면 `rate-limiting.md` 위반.
- HTTP-date 형식 오류(잘못된 포맷)는 클라이언트가 무시 — 초 단위 정수가 안전.

# Definition of Done

- [ ] AC-0 재측정 (Layer-2 누락 / Layer-1 존재 대조)
- [ ] Layer-2 429 에 `Retry-After` 추가, 카운트 미노출 유지
- [ ] 테스트 추가, `:check` GREEN
- [ ] Ready for review
