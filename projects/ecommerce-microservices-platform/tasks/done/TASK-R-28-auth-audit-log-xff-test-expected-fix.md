# Task ID

TASK-R-28

# Title

AuthAuditLogIntegrationTest X-Forwarded-For 기대값 오류 수정 (last → first IP)

# Status

review

# Owner

backend

# Task Tags

- test
- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

If any section is missing or incomplete, this task must not be implemented.

---

# Goal

`AuthAuditLogIntegrationTest.login_xForwardedFor_lastIpStored`는 `X-Forwarded-For: 1.1.1.1, 2.2.2.2, 3.3.3.3` 요청 시 감사 로그에 `3.3.3.3`(마지막 IP)이 저장될 것으로 기대하지만, 이는 `ClientIpResolver`의 실제 동작(leftmost IP 반환) 및 `ClientIpResolverTest` 단위 테스트의 검증과 정면으로 모순된다. `ClientIpResolver`는 Initial commit부터 일관되게 `xff.split(",")[0].trim()`으로 원 클라이언트 IP를 반환하도록 구현되어 있고, X-Forwarded-For 표준 관례 및 `auth-events.md` contract의 `ipAddress`(로그인 주체의 원 IP) 의미와도 일치한다. 따라서 구현/단위테스트/스펙이 옳고 본 통합 테스트 기대값이 틀렸다고 판단, 기대값을 `1.1.1.1`(첫 번째 IP)로 바로잡는다.

---

# Scope

## In Scope

- `apps/auth-service/src/test/java/com/example/auth/AuthAuditLogIntegrationTest.java`의 `login_xForwardedFor_lastIpStored` 테스트 기대값/이름/DisplayName 수정
  - `assertThat(ipAddress).isEqualTo("3.3.3.3")` → `assertThat(ipAddress).isEqualTo("1.1.1.1")`
  - 메서드명 `login_xForwardedFor_lastIpStored` → `login_xForwardedFor_firstIpStored`
  - `@DisplayName` "X-Forwarded-For 헤더에 복수 IP가 있으면 마지막 IP가 ip_address에 저장된다" → "... 첫 번째 IP가 ip_address에 저장된다"
- auth-service 전체 테스트 통과 확인

## Out of Scope

- `ClientIpResolver` 프로덕션 코드 수정
- `ClientIpResolverTest` 단위 테스트 수정 (이미 올바른 기대값)
- 감사 로그 구조 변경, X-Forwarded-For 정책 변경
- 다른 통합 테스트 수정
- 프록시 토폴로지/신뢰 경계(trusted proxy hops) 논의 — 필요하면 별도 스펙 태스크로 분리

---

# Acceptance Criteria

- [ ] 해당 테스트 메서드명/`@DisplayName`이 "first IP"로 정정되었다
- [ ] `assertThat(ipAddress).isEqualTo("1.1.1.1")`로 수정되었다
- [ ] `./gradlew :apps:auth-service:test`가 성공한다 (전체 330여 개 테스트 포함)
- [ ] `ClientIpResolverTest` 및 `ClientIpResolver` 구현과 의미가 완전히 일치한다

---

# Related Specs

- `specs/contracts/events/auth-events.md` (`ipAddress` 필드 의미 — 클라이언트 원 IP)
- `specs/services/auth-service/architecture.md`

# Related Skills

- `.claude/skills/testing/` (해당 스킬 있다면 참조)

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (`LoginSucceeded`, `LoginFailed` 이벤트의 `ipAddress`)

---

# Target Service

- `auth-service`

---

# Architecture

Follow:

- `specs/services/auth-service/architecture.md`

---

# Implementation Notes

- `ClientIpResolver` 구현 ([ClientIpResolver.java:9-15](apps/auth-service/src/main/java/com/example/auth/presentation/support/ClientIpResolver.java#L9-L15)):
  ```java
  String xff = request.getHeader("X-Forwarded-For");
  if (xff != null && !xff.isBlank()) {
      return xff.split(",")[0].trim();
  }
  return request.getRemoteAddr();
  ```
- `ClientIpResolverTest` ([resolve_multipleXff_returnsFirstIp:35-41](apps/auth-service/src/test/java/com/example/auth/presentation/support/ClientIpResolverTest.java#L35-L41))는 multi-XFF에서 첫 번째 IP 반환을 검증 중 — 이 계약이 옳음
- 본 통합 테스트는 Initial commit부터 잘못된 기대값으로 작성되었으나 `OAuthServiceTest` compileTestJava 실패로 가려져 있다가 TASK-R-26 이후 표면화
- X-Forwarded-For 표준 관례: leftmost = 원 클라이언트, rightmost = 직전 프록시. 감사 로그는 "누가 로그인했나"를 기록하므로 원 클라이언트 IP가 적절

## 구현 결과

- [AuthAuditLogIntegrationTest.java](apps/auth-service/src/test/java/com/example/auth/AuthAuditLogIntegrationTest.java) 수정
  - 메서드명 `login_xForwardedFor_lastIpStored` → `login_xForwardedFor_firstIpStored`
  - `@DisplayName` "마지막 IP" → "첫 번째 IP"
  - `assertThat(ipAddress).isEqualTo("3.3.3.3")` → `.isEqualTo("1.1.1.1")`
- `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL — 전체 테스트 통과
- `ClientIpResolver` / `ClientIpResolverTest` / `auth-events.md` contract의 `ipAddress` 의미와 정합성 확보

---

# Edge Cases

- 다른 곳에서도 "lastIp" 네이밍이 남아있는지 확인 → 해당 테스트 메서드 1건 외 없음으로 추정 (grep 확인)
- `ip_address` DB 컬럼 저장 경로 변경 여부 → 본 태스크는 저장 로직 변경하지 않음

---

# Failure Scenarios

- 이름/DisplayName 수정 누락 → 리뷰 단계에서 모순 유지
- 다른 테스트가 동일한 "마지막 IP" 가정에 의존할 가능성 → `lastIp` 문자열 전체 grep 후 없음 확인

---

# Test Requirements

- `./gradlew :apps:auth-service:test` 전체 통과 (본 태스크 적용 전: 329개 중 1개 실패 → 적용 후 0개 실패 기대)
- 기존 `ClientIpResolverTest` 영향 없음

---

# Definition of Done

- [ ] Implementation completed
- [ ] Tests added
- [ ] Tests passing
- [ ] Contracts updated if needed
- [ ] Specs updated first if required
- [ ] Ready for review
