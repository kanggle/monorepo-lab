# Task ID

TASK-BE-146

# Title

auth-service — ConfirmPasswordResetUseCase 가 access token 도 즉시 무효화 (Low L-2)

# Status

done

# Owner

backend

# Task Tags

- security
- low

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

2026-04-27 보안 리뷰(L-2, Low): `ConfirmPasswordResetUseCase` 가 refresh token 은 무효화하지만 이미 발급된 짧은 TTL access token 은 그대로 유효.

- `apps/auth-service/.../ConfirmPasswordResetUseCase.java:89-91`
- gateway 의 `JwtAuthenticationFilter` 는 `access:invalidate-before:{accountId}` Redis key 를 확인하지만 use case 는 이 키를 쓰지 않음 — `auth:invalidate-all:{accountId}` (refresh 무효화) 만 씀

영향: 비밀번호 탈취당한 사용자가 reset 후에도 도둑이 보유한 access token 으로 최대 30분(기본 TTL) 간 인증 통과.

본 태스크는 reset 성공 시 동일 use case 에서 `access:invalidate-before:{accountId}` 키를 `Instant.now().toEpochMilli()` 로 기록 (TTL = accessTokenTtlSeconds).

---

# Scope

## In Scope

- `ConfirmPasswordResetUseCase` 가 reset 성공 시 access token invalidation marker 를 Redis 에 기록
- 키 형식: 기존 gateway 가 읽는 패턴 그대로 (`access:invalidate-before:{accountId}` 또는 동등)
- TTL: `auth.jwt.access-token-ttl-seconds` 와 동일

## Out of Scope

- gateway 의 JwtAuthenticationFilter 변경 없음 (이미 키 확인 로직 존재)
- 다른 보안 이벤트(예: account lock) 에서의 access token 즉시 무효화는 별도 점검

---

# Acceptance Criteria

- [x] ConfirmPasswordResetUseCase 가 password 변경 + refresh 무효화 + access invalidation marker 까지 모두 기록
- [x] marker 키는 gateway 가 실제로 읽는 키와 동일 (코드 기반 verify) — `access:invalidate-before:` (JwtAuthenticationFilter#ACCESS_INVALIDATE_KEY_PREFIX, ForceLogoutUseCase 와 동일)
- [x] TTL 은 access token TTL 과 일치 — 만료 후 자동 정리 (`tokenGeneratorPort.accessTokenTtlSeconds()`)
- [x] 단위 테스트: reset 성공 시 marker 기록 호출 검증, reset 실패 시 호출 안 됨 검증
- [ ] 통합 테스트(있으면): reset 직후 기존 access token 으로 gateway 호출 → 401 — 해당 통합 테스트 부재(out: spec 의 "있으면" 조항으로 단위 테스트로 대체)
- [x] `:apps:auth-service:test` 통과

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/services/gateway-service/architecture.md`

# Related Skills

- `.claude/skills/backend/security/SKILL.md` (있으면)

---

# Related Contracts

- 없음 (내부 Redis key 정책)

---

# Target Service

- `auth-service` (+ gateway-service 의 키 패턴 확인용 read)

---

# Architecture

Follow `specs/services/auth-service/architecture.md`.

---

# Implementation Notes

- 기록 시점: refresh revoke 직후, Redis token delete 직전에 추가 (저장 실패해도 reset 자체는 이미 commit 된 상태 — fail-soft).
- 실제로 gateway 가 읽는 키 prefix 는 `apps/gateway-service/.../JwtAuthenticationFilter.java` 에서 grep 후 정확히 동일하게 사용 — 보고서 추정 키와 다르면 실제 코드 기준.
- `RefreshTokenUseCase` 의 reuse-detection 경로에서도 동일 marker 를 기록하는지 점검 (이미 있으면 패턴 일치, 없으면 별도 태스크).

---

# Edge Cases

- 동일 계정의 마커가 이미 더 큰 epoch 로 존재 시 덮어쓰기로 회귀하지 않도록: `SET ... NX` 는 부적절(첫 침해 marker 만 유지) → 항상 SET 로 덮어쓰되 가장 최근 reset 시각이 사용되도록 함.
- 시간 동기화: 서버 NTP 의존. 큰 clock drift 는 별도 운영 이슈.

---

# Failure Scenarios

- gateway 의 키 prefix 가 마커 prefix 와 다르면 무효화 작동 안 함 — 단위 테스트 + 통합 테스트로 검증.
- TTL 이 access token TTL 보다 짧으면 무효화 윈도우 부족 — 정확히 같거나 길게.

---

# Test Requirements

- `ConfirmPasswordResetUseCaseTest`:
  - 정상 reset → marker 기록 (Mockito verify)
  - reset 실패(잘못된 token, 만료 등) → marker 기록 안 됨
- 통합 테스트(가능하면):
  - reset 후 같은 accountId 의 access token 으로 gateway 호출 시 401 응답.

---

# Definition of Done

- [x] marker 기록 추가
- [x] 단위/통합 테스트 통과 (단위 only — 통합 테스트 부재로 spec 의 "있으면" 조항 적용)
- [x] gateway 키 prefix 일치 확인 (`access:invalidate-before:` — JwtAuthenticationFilter:39 와 동일)
- [x] Ready for review
