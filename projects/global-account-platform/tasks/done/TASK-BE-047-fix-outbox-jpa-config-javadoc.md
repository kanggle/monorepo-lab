# Task ID

TASK-BE-047-fix-outbox-jpa-config-javadoc

# Title

libs/java-messaging OutboxJpaConfig javadoc 교정 — "main application class" → 전용 @Configuration 클래스

# Status

ready

# Owner

backend

# Task Tags

- docs
- fix

# depends_on

- TASK-BE-047 (완료됨)

---

# Goal

BE-047 리뷰 Warning: `libs/java-messaging/.../OutboxJpaConfig.java:20` 의 javadoc은 서비스가 `@EnableJpaRepositories`/`@EntityScan`을 "on its main application class"에 선언하라고 지시하지만, 실제 BE-047 구현은 `@WebMvcTest` 슬라이스 격리를 보존하려고 서비스별 `infrastructure/config/JpaConfig.java` 별도 @Configuration에 두었다. javadoc이 반대 가이드를 주면 새 서비스가 잘못된 위치에 선언해 슬라이스 테스트가 JPA wiring을 불필요하게 트리거할 수 있다.

---

# Scope

## In Scope

- `libs/java-messaging/src/main/java/com/example/messaging/outbox/OutboxJpaConfig.java` javadoc 수정: "on its main application class" → "in a dedicated `@Configuration` class within the service's `infrastructure.config` package (NOT on the main application class, to preserve `@WebMvcTest` slice isolation)"

## Out of Scope

- OutboxJpaConfig 동작/선언 변경
- 서비스 구현 수정

---

# Acceptance Criteria

- [ ] javadoc 문구가 실제 패턴과 일치
- [ ] 빌드 회귀 없음 (javadoc-only)

---

# Related Specs

- 없음

---

# Target Service

- `libs/java-messaging`

---

# Edge Cases

- 없음

---

# Failure Scenarios

- 없음

---

# Test Requirements

- 없음 (javadoc only). `./gradlew :libs:java-messaging:compileJava` 확인 권장.

---

# Definition of Done

- [ ] javadoc 교정 + Ready for review
