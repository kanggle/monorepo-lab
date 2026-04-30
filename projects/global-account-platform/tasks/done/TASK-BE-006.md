# Task ID

TASK-BE-006

# Title

PII 마스킹 유틸리티 통합 — libs/java-security 단일 구현

# Status

review

# Owner

backend

# Task Tags

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

---

# Goal

`security-service`의 `PiiMaskingUtils`(maskIp, truncateFingerprint)와 `admin-service`의 `AdminPiiMaskingUtils`(maskEmail, maskAccountId, maskPhone)가 동일한 목적(PII 마스킹)을 위해 완전히 별개로 구현되어 있다.

`libs/java-security`에 단일 `PiiMaskingUtils`를 두고 두 서비스 모두 이를 의존하도록 통합한다.

---

# Scope

## In Scope

- `libs/java-security`에 통합 `PiiMaskingUtils` 추가
  - maskEmail, maskAccountId, maskPhone (admin-service 로직)
  - maskIp, truncateFingerprint (security-service 로직)
- `security-service`의 `PiiMaskingUtils` 삭제 → `libs/java-security` 의존으로 교체
- `admin-service`의 `AdminPiiMaskingUtils` 삭제 → `libs/java-security` 의존으로 교체
- `platform/shared-library-policy.md` 준수

## Out of Scope

- 마스킹 알고리즘/패턴 변경 없음 (기존 로직 그대로 이전)
- 다른 서비스에 PII 마스킹 적용 확대 없음
- 로그 출력 형식 변경 없음

---

# Acceptance Criteria

- [x] `libs/java-security`에 통합 `PiiMaskingUtils`가 추가된다 (`com.gap.security.pii.PiiMaskingUtils`)
- [x] `security-service`의 `PiiMaskingUtils`가 삭제되고 `libs/java-security` 의존으로 교체된다
- [x] `admin-service`의 `AdminPiiMaskingUtils`가 삭제되고 `libs/java-security` 의존으로 교체된다
- [x] 기존 마스킹 결과가 변경되지 않는다 (회귀 테스트 통과 — 로직 verbatim 이전)
- [x] `libs/java-security`가 서비스 도메인 클래스에 의존하지 않는다 (`com.gap.security.pii` 패키지, 외부 의존 없음)
- [x] 통합 `PiiMaskingUtils` 단위 테스트 추가 (16개 테스트)
- [x] 빌드 및 테스트 통과 (`:libs:java-security:test`, `:apps:security-service:test`, `:apps:admin-service:test` 모두 BUILD SUCCESSFUL)

---

# Related Specs

- `platform/shared-library-policy.md`
- `specs/services/security-service/architecture.md`
- `specs/services/admin-service/architecture.md`
- `specs/services/admin-service/security.md` (존재하는 경우)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md`

---

# Related Contracts

없음 — 공개 API 변경 없음

---

# Target Service

- `libs/java-security`
- `security-service`
- `admin-service`

---

# Architecture

Follow:

- `platform/shared-library-policy.md`
- `specs/services/security-service/architecture.md`
- `specs/services/admin-service/architecture.md`

---

# Implementation Notes

- `PiiMaskingUtils`는 static 유틸리티 클래스 또는 Spring Bean — 기존 사용 방식에 맞춰 결정.
- 마스킹 패턴이 서비스마다 미묘하게 다를 경우 파라미터화하거나 오버로드 메서드 제공.
- `libs/java-security`가 이미 존재하는 경우 기존 클래스와 네이밍 충돌 없는지 확인.

---

# Edge Cases

- `null` 또는 빈 문자열 입력 시 `null`/빈 문자열 반환 (기존 동작 유지)
- 이메일 형식이 잘못된 경우 마스킹 실패 없이 원본 반환 또는 전체 마스킹

---

# Failure Scenarios

- 두 서비스의 동일 필드 마스킹 패턴이 달랐던 경우 → 통합 후 회귀 발생 (단위 테스트로 방지)
- `libs/java-security` 미존재 시 모듈 생성 필요

---

# Test Requirements

- 통합 `PiiMaskingUtils` 단위 테스트: 각 마스킹 메서드별 정상 입력, null 입력, 경계 값
- `security-service`, `admin-service` 기존 PII 마스킹 테스트: 결과가 변경되지 않는지 회귀 검증

---

# Definition of Done

- [x] Implementation completed
- [x] Tests added
- [x] Tests passing
- [x] Contracts updated if needed (N/A — 공개 API 변경 없음)
- [x] Specs updated first if required (N/A — 순수 리팩토링)
- [x] Ready for review
