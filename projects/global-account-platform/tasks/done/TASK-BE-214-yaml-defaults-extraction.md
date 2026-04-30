---
id: TASK-BE-214
title: "application-defaults.yml 추출 — 6개 서비스 반복 YAML 설정 중앙화"
type: refactoring
status: ready
service: libs/java-common, auth-service, account-service, admin-service, membership-service, security-service, community-service
---

## Goal

6개 서비스 application.yml에 `spring.datasource.hikari`, `spring.jpa`, `spring.jackson`, `management.*` 설정이
동일하게 반복되어 있다. `libs/java-common/src/main/resources/application-defaults.yml` 에 공통 기본값을 추출하고,
각 서비스에서 `spring.config.import`로 가져오도록 변경하여 중복을 제거한다.

## Scope

### 추가
- `libs/java-common/src/main/resources/application-defaults.yml`
  - `spring.datasource.hikari.*` (6개 서비스 동일)
  - `spring.jpa.*` (6개 서비스 동일)
  - `spring.jackson.*` (6개 서비스 동일)
  - `management.*` 기본값 (5개 서비스 동일: `include: health,info,prometheus`, `show-details: always`, `metrics.tags.service`)

### 수정 (각 서비스 application.yml)
- `spring.config.import: optional:classpath:application-defaults.yml` 추가 (application name 바로 아래)
- 중복된 `spring.datasource.hikari.*`, `spring.jpa.*`, `spring.jackson.*` 블록 제거
- 표준 `management.*` 블록 제거 (기본값과 동일한 5개 서비스: auth, account, security, community, membership)
- admin-service: 기본값과 다른 부분만 유지 (`include` override, circuitbreaker group/health 추가)

## Acceptance Criteria

- [ ] `libs/java-common/src/main/resources/application-defaults.yml` 파일이 존재한다
- [ ] 각 서비스 application.yml에 `spring.config.import: optional:classpath:application-defaults.yml`이 추가된다
- [ ] 6개 서비스에서 `spring.datasource.hikari.*`, `spring.jpa.*`, `spring.jackson.*` 블록이 제거된다
- [ ] 5개 서비스(auth, account, security, community, membership)에서 표준 `management.*` 블록이 제거된다
- [ ] admin-service에서 circuitbreaker 관련 부분만 남고 기본값과 동일한 부분은 제거된다
- [ ] `./gradlew :apps:auth-service:test :apps:account-service:test :apps:admin-service:test :apps:membership-service:test :apps:security-service:test :apps:community-service:test` 통과

## Related Specs

- `platform/shared-library-policy.md`

## Related Contracts

- 없음

## Edge Cases

- `optional:` prefix로 인해 defaults 파일이 없어도 오류 없음 (테스트 환경에서 java-common jar 없이 빌드할 경우)
- 서비스별 `application-test.yml`은 영향 없음: 프로파일 활성화 시 test 프로파일 yml이 기본 yml 위에 덮어쓰며, defaults의 값은 기본 yml 임포트 체인에서 이미 로드됨
- admin-service의 `management.endpoint.health.show-details: always`와 `management.metrics.tags.service`는 defaults에서 상속되므로 admin-service에서 명시 불필요

## Failure Scenarios

- `spring.config.import` 구문 오류 → 서비스 기동 실패. 테스트로 조기 탐지
- defaults 파일에서 잘못된 property → 모든 서비스에 영향. 단위 테스트가 없는 경우 발견 늦어짐
