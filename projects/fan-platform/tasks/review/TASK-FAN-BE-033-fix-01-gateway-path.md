# TASK-FAN-BE-033-fix-01: billing-key 엔드포인트가 게이트웨이로 도달 불가

## Goal

TASK-FAN-BE-033(billing-key 자동 갱신) 구현 검증 중 발견된 라우팅 결함을 수정한다.

`BillingKeyController`가 `/api/fan/membership/billing-key`(단수)로 매핑되었으나,
gateway-service의 기존 리라이트 규칙은 `/api/v1/memberships/**`(복수,
TASK-FAN-BE-009)만 매칭해 `/api/fan/memberships${segment}`로 리라이트한다. 단수
경로는 게이트웨이를 거치는 어떤 클라이언트도 도달할 수 없었다 — TASK-FAN-FE-013
(빌링키 발급 UI) 착수 전에 발견해 수정한다.

## Scope

### Backend (membership-service)

1. `BillingKeyController`의 `@RequestMapping`을 `/api/fan/membership/billing-key`
   → `/api/fan/memberships/billing-key`(복수)로 변경 — 기존 게이트웨이 리라이트
   규칙이 그대로 커버(게이트웨이 변경 불필요).
2. `BillingKeyEnrollmentIntegrationTest`의 리터럴 경로 문자열 갱신.
3. `specs/contracts/http/membership-api.md`의 두 엔드포인트 헤더 경로 갱신.

## Acceptance Criteria

- [ ] `BillingKeyController`가 `/api/fan/memberships/billing-key`(복수)로 매핑된다.
- [ ] `MembershipController`의 기존 `/api/fan/memberships/**` 매핑과 라우팅 충돌
      없음(다른 하위경로/HTTP 메서드).
- [ ] IT + 계약 스펙의 경로 문자열이 갱신된 경로와 일치한다.
- [ ] `./gradlew :projects:fan-platform:apps:membership-service:test`
      (Docker-free 유닛/슬라이스) 통과.
