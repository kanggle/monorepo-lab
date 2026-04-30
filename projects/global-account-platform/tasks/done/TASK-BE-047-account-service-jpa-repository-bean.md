# Task ID

TASK-BE-047-account-service-jpa-repository-bean

# Title

account-service — AccountJpaRepository 빈 미등록, @EnableJpaRepositories 스캔 범위 수정

# Status

ready

# Owner

backend

# Task Tags

- code
- fix

# depends_on

- (없음)

---

# Goal

E2E 런타임 중 account-service 기동이 다음 오류로 실패:

```
UnsatisfiedDependencyException: ...
No qualifying bean of type 'com.example.account.infrastructure.persistence.AccountJpaRepository'
available: expected at least 1 bean which qualifies as autowire candidate
```

`AccountRepositoryAdapter`가 `AccountJpaRepository`에 의존하는데 Spring이 해당 인터페이스를 repository 빈으로 인식하지 못한다. 원인은 libs/java-messaging `OutboxJpaConfig`의 `@EnableJpaRepositories` 가 서비스 자체 JPA repository 패키지를 스캔 범위에서 제외시키는 패턴으로 보인다. 유사 현상이 admin/auth/security에도 숨어 있을 수 있어 전수 점검한다.

---

# Scope

## In Scope

1. account-service 메인 애플리케이션 클래스 및 관련 `@Configuration`, `@EnableJpaRepositories`, `@EntityScan` 선언 전수 조사
2. libs/java-messaging `OutboxJpaConfig`가 `basePackages` 또는 `basePackageClasses`로 서비스 repository를 가리지 않는지 확인
3. `AccountJpaRepository`, `OutboxProcessingStateJpaRepository` 등 서비스 repository가 실제 스캔되는지 빈 이름 기준으로 검증
4. 필요 시 account-service 메인 클래스에 명시적 `@EnableJpaRepositories(basePackages = "com.example.account.infrastructure.persistence")` 추가
5. admin/auth/security도 동일 리스크 여부 점검
6. `./gradlew :apps:account-service:test` 통과
7. compose 환경에서 account-service 단독 기동 healthy 확인

## Out of Scope

- 다른 서비스에 같은 결함이 있으면 동일 패턴 수정 포함, 그 외 리팩토링 금지
- JPA 엔티티 자체 스키마 변경

---

# Acceptance Criteria

- [ ] account-service 기동 시 `AccountJpaRepository` 빈이 정상 등록
- [ ] `docker compose -f docker-compose.e2e.yml up -d account-service` 후 healthy
- [ ] `./gradlew :apps:account-service:test` 통과
- [ ] admin/auth/security에서 같은 결함 없음을 확인한 기록

---

# Related Specs

- `specs/services/account-service/architecture.md`
- `libs/java-messaging` outbox 관련 구조

---

# Target Service

- `apps/account-service`
- 필요 시 `libs/java-messaging`

---

# Edge Cases

- OutboxJpaConfig가 `basePackageClasses = {OutboxJpaEntity.class}`만 지정해 서비스 repository가 cascaded scan에서 제외되는 경우 — 서비스 메인에서 별도 `@EnableJpaRepositories` 명시 필요

---

# Failure Scenarios

- 수정이 OutboxJpaConfig를 건드려야 한다면 다른 서비스 회귀 위험 — 서비스별 명시 @EnableJpaRepositories 방식이 안전

---

# Test Requirements

- account-service 단위 테스트 통과
- compose 단독 기동 healthy

---

# Definition of Done

- [ ] 빈 등록 수정 + 런타임 확인
- [ ] Ready for review
