# Task ID

TASK-BE-148

# Title

auth-service — application 레이어 `catch (Exception)` 정책 준수 (TASK-BE-147 리뷰 후속)

# Status

review

# Owner

backend

# Task Tags

- refactor
- architecture
- security

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

TASK-BE-147 리뷰 중 발견된 pre-existing 위반: `platform/coding-rules.md:32` — "top-level handler 외 `catch (Exception)` 금지" — 가 세 use case 에 남아 있음.

- `LogoutUseCase.java:38–42` — JWT 파싱 실패를 `catch (Exception)` 로 흡수
- `RefreshTokenUseCase.java:50–54, 85–90` — JWT 파싱 실패 × 2 를 `catch (Exception)` 로 처리
- `RequestPasswordResetUseCase.java:105–113` — 이메일 발송 실패를 `catch (Exception)` 로 흡수

위반 수정 방안:
1. `TokenParseException` (unchecked) 신설 → `JwtTokenGenerator` 의 `extract*` 메서드가 `JwtException` 을 `TokenParseException` 으로 래핑
2. `EmailSendException` (unchecked) 신설 → `EmailSenderPort` 구현체가 발송 실패를 `EmailSendException` 으로 래핑 (현 dev stub 은 예외 없음, 프로덕션 어댑터 가이드용)
3. 세 use case 의 `catch (Exception)` → `catch (TokenParseException)` / `catch (EmailSendException)` 교체

---

# Scope

## In Scope

- `TokenParseException` 신설 (`application/exception/`)
- `EmailSendException` 신설 (`application/exception/`)
- `JwtTokenGenerator` — `extractJti`, `extractAccountId`, `extractIssuedAt` 에서 `JwtException` → `TokenParseException` 래핑
- `LogoutUseCase` — `catch (Exception)` → `catch (TokenParseException)`
- `RefreshTokenUseCase` — `catch (Exception)` × 2 → `catch (TokenParseException)`
- `RequestPasswordResetUseCase` — `catch (Exception)` → `catch (EmailSendException)`
- 기존 테스트 갱신 (`LogoutUseCaseTest`, `RequestPasswordResetUseCaseTest`)
- 신규 테스트 추가 (`RefreshTokenUseCaseTest` — JWT 파싱 실패 경로 × 2)

## Out of Scope

- 동작 변경 없음 — 순수 catch 범위 교체
- 기존 예외 계층 변경 없음
- `Slf4jEmailSender` dev stub 수정 없음 (예외를 던지지 않으므로 래핑 불필요)

---

# Acceptance Criteria

- [x] `TokenParseException` / `EmailSendException` 신설
- [x] `JwtTokenGenerator` — `parseClaims` 헬퍼 추출 + `JwtException | IllegalArgumentException` 래핑 + `extractIssuedAt` null 체크
- [x] `TokenGeneratorPort` / `EmailSenderPort` 에 `@throws` Javadoc 추가
- [x] 세 use case 에서 `catch (Exception)` 완전 제거
- [x] `LogoutUseCaseTest` — `tokenParsingFails` 케이스가 `TokenParseException` 기반으로 갱신
- [x] `RequestPasswordResetUseCaseTest` — `emailSendFails` 케이스가 `EmailSendException` 기반으로 갱신
- [x] `RefreshTokenUseCaseTest` — JWT 파싱 실패 → `TokenExpiredException`, `iat` 추출 실패 → `SessionRevokedException` 케이스 추가
- [x] `:apps:auth-service:test` 통과
- [ ] `:apps:auth-service:test` 통과

---

# Related Specs

- `platform/coding-rules.md` (catch (Exception) 제한)
- `specs/services/auth-service/architecture.md`

---

# Related Contracts

- 없음 (내부 예외 계층, 동작 불변)

---

# Target Service

- `auth-service`

---

# Architecture

hexagonal: application layer 는 포트 계약에서 선언된 예외만 catch — 인프라 라이브러리 예외(`JwtException`)가 application layer 로 유출되지 않도록 adapter 에서 래핑.

---

# Edge Cases

- `JwtException` 하위 타입(`ExpiredJwtException`, `MalformedJwtException` 등) 모두 `JwtException` 상속 — `catch (JwtException)` 으로 전부 포착 후 `TokenParseException` 로 래핑하면 됨.
- `Slf4jEmailSender` dev stub 은 예외를 던지지 않으므로 래핑 구현 불필요. `EmailSendException` 는 프로덕션 어댑터 가이드용으로 신설.

---

# Failure Scenarios

- `catch (TokenParseException)` 로 좁힌 후 구현체가 `JwtException` 을 래핑 없이 던지면 `LogoutUseCase` 의 `return` 경로가 실행되지 않아 이후 `Optional.empty()` 분기를 타지만 동작은 동일(빈 refresh token → early return). 단위 테스트가 이를 검증.
- `catch (EmailSendException)` 로 좁힌 후 실제 프로덕션 어댑터가 `EmailSendException` 를 던지지 않으면 best-effort 흡수가 작동하지 않음 → 어댑터 개발 시 래핑 강제.

---

# Test Requirements

- `RefreshTokenUseCaseTest` 추가:
  - `refreshFailsWhenTokenParseFails` — `extractJti` throws `TokenParseException` → `TokenExpiredException`
  - `refreshFailsWhenIatExtractFails` — marker 존재 + `extractIssuedAt` throws `TokenParseException` → `SessionRevokedException`
- `LogoutUseCaseTest` 수정:
  - `logoutNoEventWhenTokenParsingFails` — `new RuntimeException` → `new TokenParseException`
- `RequestPasswordResetUseCaseTest` 수정:
  - `execute_emailSendFails_swallowsException` — `new RuntimeException("smtp down")` → `new EmailSendException("smtp down")`

---

# Definition of Done

- [ ] 세 use case 에서 `catch (Exception)` 완전 제거
- [ ] `TokenParseException` / `EmailSendException` 신설 및 래핑 적용
- [ ] 신규/갱신 단위 테스트 통과
- [ ] `:apps:auth-service:test` 전체 통과
- [ ] Ready for review
