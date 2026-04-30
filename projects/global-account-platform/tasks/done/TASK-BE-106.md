---
id: TASK-BE-106
title: "패스워드 관리 기반 — auth-api.md 계약 추가 + PasswordPolicy 도메인 + Credential.changePassword()"
status: ready
priority: high
target_service: auth-service
tags: [contract-change, refactor]
created_at: 2026-04-26
---

# TASK-BE-106: 패스워드 관리 기반 작업

## Goal

패스워드 변경·재설정 API의 공통 기반을 구축한다.

1. `specs/contracts/http/auth-api.md`에 3개 엔드포인트 계약을 추가 (contract-first)
2. `PasswordPolicy` 도메인 서비스 신설 — 복잡도·길이·이메일 일치 금지 검증
3. `Credential` 도메인 엔터티에 `changePassword(CredentialHash newHash)` 메서드 추가

TASK-BE-107(패스워드 변경), TASK-BE-108(재설정 요청), TASK-BE-109(재설정 확인)이
이 태스크 결과물에 의존한다.

## Scope

### In

**계약 파일 (specs/contracts/http/auth-api.md)**
- `PATCH /api/auth/password` 엔드포인트 계약 추가
- `POST /api/auth/password-reset/request` 엔드포인트 계약 추가
- `POST /api/auth/password-reset/confirm` 엔드포인트 계약 추가

**PasswordPolicy 도메인 서비스**
- 위치: `domain/credentials/PasswordPolicy.java`
- 메서드: `void validate(String plainPassword, String email)` — 위반 시 예외
- 검증 규칙 (specs/features/password-management.md 기준):
  - 길이: 8자 이상, 128자 이하
  - 복잡도: 대문자·소문자·숫자·특수문자 중 3종 이상
  - 이메일 일치 금지: `email.toLowerCase()` 포함 여부 확인
- 위반 시: `PasswordPolicyViolationException` (application/exception/)

**Credential 도메인 메서드**
- `Credential.changePassword(CredentialHash newHash)` 추가
- 반환: 새 `Credential` 인스턴스 (불변 패턴 — 동일 id/accountId/email 유지, hash 교체)
- 또는 mutable이면 `this.credentialHash = newHash; this.updatedAt = Instant.now();` 패턴

**단위 테스트**
- `PasswordPolicyTest`: 길이 경계, 복잡도 불충분, 이메일 일치, 통과 케이스
- `CredentialTest`: `changePassword()` 호출 후 hash 교체 검증

### Out
- Controller, UseCase, Repository 구현 (BE-107·108·109 담당)
- Redis 토큰 저장소 (BE-108 담당)
- 이메일 발송 인프라 (BE-108 담당)

## Acceptance Criteria

1. `specs/contracts/http/auth-api.md`에 3개 엔드포인트 계약이 추가된다.
2. `PasswordPolicy.validate()` 가 각 규칙 위반 시 `PasswordPolicyViolationException`을 던진다.
3. `PasswordPolicyViolationException`은 application/exception/ 아래 정의된다.
4. `Credential.changePassword(CredentialHash)` 가 새 해시를 담은 인스턴스(또는 변경된 this)를 반환한다.
5. 단위 테스트가 모두 통과한다.
6. `./gradlew :apps:auth-service:test` BUILD SUCCESSFUL.

## Related Specs

- `specs/features/password-management.md` — 전체 정책 및 해시 전략
- `specs/services/auth-service/architecture.md` — 레이어 규칙
- `platform/entrypoint.md` — 스펙 읽기 순서
- `rules/traits/regulated.md` — R4 (패스워드 평문 저장·로깅 금지)

## Related Contracts

- `specs/contracts/http/auth-api.md` — 이 태스크에서 직접 수정

## Edge Cases

- 패스워드가 null/빈 문자열인 경우: 길이 검증에서 실패
- 이메일이 null인 경우: 이메일 일치 검사 생략 (방어적 처리)
- 특수문자 집합 정의: 영문·숫자 이외의 ASCII 출력 가능 문자 (`!@#$%^&*...`)

## Failure Scenarios

- 복잡도 검증에서 정규식 오류: 컴파일 타임 오류로 조기 발견

## Test Requirements

### 단위 테스트
- `PasswordPolicyTest`:
  - `validate_tooShort_throws`: 7자 패스워드
  - `validate_tooLong_throws`: 129자 패스워드
  - `validate_insufficientComplexity_throws`: 소문자만으로 구성
  - `validate_matchesEmail_throws`: 패스워드 == 이메일
  - `validate_valid_passes`: 모든 규칙 통과하는 패스워드
- `CredentialTest`:
  - `changePassword_replacesHash`: 변경 후 `getCredentialHash()` 가 새 값 반환

## Implementation Notes

### 계약 추가 형식 (auth-api.md)
기존 스타일 따라 아래 3개 섹션 추가:

```
## PATCH /api/auth/password

**인증**: Bearer Access Token 필수

**Request Body**:
| Field | Type | Required | Description |
|---|---|---|---|
| currentPassword | string | Y | 현재 패스워드 (검증용) |
| newPassword | string | Y | 새 패스워드 (정책 검증) |

**Response**: 204 No Content

**Error**:
| Code | HTTP | Description |
|---|---|---|
| CREDENTIALS_INVALID | 400 | 현재 패스워드 불일치 |
| PASSWORD_POLICY_VIOLATION | 400 | 새 패스워드가 정책 미충족 |

---

## POST /api/auth/password-reset/request

**인증**: 불필요

**Request Body**:
| Field | Type | Required | Description |
|---|---|---|---|
| email | string | Y | 재설정 대상 이메일 |

**Response**: 204 No Content (계정 존재 여부와 무관하게 항상 동일)

---

## POST /api/auth/password-reset/confirm

**인증**: 불필요

**Request Body**:
| Field | Type | Required | Description |
|---|---|---|---|
| token | string | Y | 재설정 토큰 (Redis, TTL 1시간) |
| newPassword | string | Y | 새 패스워드 (정책 검증) |

**Response**: 204 No Content

**Error**:
| Code | HTTP | Description |
|---|---|---|
| PASSWORD_RESET_TOKEN_INVALID | 400 | 토큰 없음·만료·이미 사용됨 |
| PASSWORD_POLICY_VIOLATION | 400 | 새 패스워드가 정책 미충족 |
```

### PasswordPolicy 위치
`apps/auth-service/src/main/java/com/example/auth/domain/credentials/PasswordPolicy.java`

### PasswordPolicyViolationException 위치
`apps/auth-service/src/main/java/com/example/auth/application/exception/PasswordPolicyViolationException.java`
- `extends RuntimeException`
- 생성자에 `violationMessage` 포함
