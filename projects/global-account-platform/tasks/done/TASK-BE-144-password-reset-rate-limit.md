# Task ID

TASK-BE-144

# Title

auth-service — RequestPasswordResetUseCase 이메일별 rate limit 추가 (High H-2)

# Status

done

# Owner

backend

# Task Tags

- security
- high

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

2026-04-27 보안 리뷰(H-2, High): `RequestPasswordResetUseCase` 가 rate limit 없음.

- `apps/auth-service/src/main/java/com/example/auth/application/RequestPasswordResetUseCase.java:51-79`
- 동일 기능을 가진 이메일 인증 재발송(`tryAcquireResendSlot`), 로그인 시도(`LoginAttemptCounter`) 는 모두 rate limit 가 있는데 password reset 만 누락

영향:
1. 응답 latency 차이로 이메일 enumeration 가능
2. 피해자 inbox 스팸 (이메일 도달성 저하 또는 사용자 distress)

본 태스크는 `LoginAttemptCounter` 패턴을 그대로 따라 `PasswordResetAttemptCounter` 를 추가하고 use case 진입 시 검증한다.

---

# Scope

## In Scope

- 신규 `PasswordResetAttemptCounter` 인터페이스 + Redis 구현
- `RequestPasswordResetUseCase.execute()` 진입 시 카운터 확인 후 초과 시 `PasswordResetRateLimitedException`
- 이메일 hash 키 (이미 존재하는 `LoginUseCase.hashEmail` 패턴 재사용 — PII 미저장)
- 윈도우/임계값: 이메일당 15분 내 3회 (운영 정책으로 조정 가능, application.yml 설정)
- 카운터 증가는 use case 의 모든 분기(찾음/못찾음) 에서 동일하게 — enumeration 방지

## Out of Scope

- IP 기반 rate limit — 별도 태스크 (이메일 hash 가 우선, IP 는 우회 쉬움)
- 다른 무방어 엔드포인트 점검 — 별도 태스크

---

# Acceptance Criteria

- [ ] `PasswordResetAttemptCounter` 인터페이스 + Redis 구현 추가
- [ ] use case 가 카운터 초과 시 예외 발생, 호출자에게 동일 응답(204) 반환 시 enumeration 방지 확인
- [ ] 카운터 증가는 발견/미발견 분기와 무관하게 동일 시점에 1회
- [ ] 윈도우/임계값은 application.yml 의 `auth.password-reset.rate-limit.window-seconds`, `.max-attempts` 로 설정
- [ ] 단위 테스트: 한도 내 통과 / 한도 초과 거부 / TTL 만료 후 재허용
- [ ] 통합 테스트(있으면): 동일 이메일 4회 호출 시 4번째 거부
- [ ] `:apps:auth-service:test` 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/features/password-reset/*` (있으면)

# Related Skills

- `.claude/skills/backend/security/SKILL.md` (있으면)

---

# Related Contracts

- `specs/contracts/api/auth-service/password-reset.md` 가 존재하면 rate limit 응답 코드 정책 명시 (현재는 204 동일 응답으로 enumeration 방지 가정)

---

# Target Service

- `auth-service`

---

# Architecture

Follow `specs/services/auth-service/architecture.md`.

---

# Implementation Notes

- 키 형식: `pwd-reset-rate:{sha256(email)[:10]}` — 기존 LoginAttemptCounter 와 일관
- TTL: 윈도우 길이로 설정, 첫 attempt 시 SET ... EX 적용
- 카운터 증가는 `INCR` + 첫 INCR 에서 EXPIRE 설정
- 한도 초과 시 응답 정책 옵션:
  - A) 동일하게 204 반환하고 내부적으로 발송 안 함 (enumeration 방지에 가장 강함)
  - B) 429 응답 (정직하나 enumeration 일부 노출)
  - 권장: A. 다만 운영 모니터링 로그 남김 (rate-limited 이벤트)
- `PasswordResetRateLimitedException` 을 use case 내부에서 catch 해 정상 응답으로 흡수, 단 메트릭/로그 기록.

---

# Edge Cases

- Redis 장애 시 fail-open vs fail-closed: 기존 `LoginAttemptCounter` 와 동일 정책 따름 (현재 fail-open — 별도 H-1 medium 이슈 M-1 로 추적). 본 태스크는 동일 패턴 유지.
- 정상 사용자가 짧은 시간 내 여러 단말에서 reset 요청 시 한도에 걸릴 수 있음 — 임계값 보수적으로 설정 (15분 / 3회 권장).
- 이메일 normalization (대소문자, gmail dots) — `LoginUseCase.normalize` 와 일관.

---

# Failure Scenarios

- enumeration 방지 위해 응답을 동일하게 유지하되 내부 카운팅 누락 시 무방어 — 발견/미발견 분기 양쪽에서 카운터 증가 검증 필수.
- 한도 초과 응답을 429 로 분기하면 enumeration 노출 — 옵션 A 채택.

---

# Test Requirements

- `PasswordResetAttemptCounterTest`:
  - 첫 attempt 통과, EXPIRE 설정 검증
  - max+1 호출 거부
  - TTL 경과 후 재허용
- `RequestPasswordResetUseCaseTest`:
  - 이메일 존재 + 한도 내 → 정상 흐름
  - 이메일 미존재 + 한도 초과 → 응답 204 (동일), 발송 없음
  - 이메일 존재 + 한도 초과 → 응답 204, 발송 없음, 메트릭 발화

---

# Definition of Done

- [ ] Counter + 구현 추가
- [ ] use case 통합
- [ ] 단위/통합 테스트 통과
- [ ] application.yml 설정 추가
- [ ] Ready for review
