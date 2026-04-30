# TASK-MONO-018 — GAP CI workflows + concrete OutboxPollingScheduler

## Goal

TASK-MONO-017에서 out-of-scope로 미뤘던 두 가지 후속 작업을 처리한다:

1. **GAP backend 7개 서비스의 CI 워크플로우 추가** — `.github/workflows/`에 wms/ecommerce와 동일한 패턴으로 GAP 서비스 build/test 워크플로우 추가.
2. **GAP 6개 앱의 concrete `OutboxPollingScheduler` 서브클래스 등록** — 모노레포의 `OutboxPollingScheduler`는 abstract base이며, 각 앱은 `resolveTopic(eventType)`을 구현한 `@Component` 서브클래스를 등록해야 integration 테스트가 통과한다 (`@MockitoBean OutboxPollingScheduler` 의존성).

## Scope

**In scope:**

1. **단일 ci.yml에 GAP 7개 서비스 step 추가** (`.github/workflows/ci.yml`):
   - 모노레포는 단일 `ci.yml`에 `build-and-test` job + frontend/integration/e2e job들이 모인 monolithic 구조 → wms/ecommerce 패턴(개별 파일 아님) 따른다.
   - GAP 서비스의 `:check` 등록은 통합 테스트 분리(@Tag) 작업이 끝나야 가능. 본 태스크에서는 보수적으로 `:compileJava` + `:compileTestJava` + 새 `*OutboxPollingSchedulerTest`만 실행.
   - 통합 테스트 분리 + 전체 `:check` 추가는 별도 follow-up.

2. **6개 GAP 앱에 concrete `OutboxPollingScheduler` 서브클래스 등록**:
   - `auth-service` — `AuthOutboxPollingScheduler` (auth.* 5개 + session.revoked 6개 토픽)
   - `account-service` — `AccountOutboxPollingScheduler` (account.* 5개 토픽)
   - `security-service` — `SecurityOutboxPollingScheduler` (security.* 2개 토픽)
   - `admin-service` — `AdminOutboxPollingScheduler` (admin.action.performed 1개)
   - `community-service` — `CommunityOutboxPollingScheduler` (community.* 3개)
   - `membership-service` — `MembershipOutboxPollingScheduler` (membership.* 3개)
   - gateway-service: 이벤트 publish 없음, 대상 외
   - 패턴: `projects/ecommerce-microservices-platform/apps/review-service/src/main/java/com/example/review/infrastructure/event/ReviewOutboxPollingScheduler.java` 참조
   - 각 서브클래스에 단위 테스트 1개 (`resolveTopic`의 정상/Unknown event type 분기)

3. **Topic 상수 검증**: `projects/global-account-platform/docker-compose.yml` `kafka-init` 서비스에 정의된 토픽 목록과 정확히 일치해야 함.

**Out of scope:**

- admin-web (Next.js) CI 추가 — 별도 frontend 태스크
- `tests:e2e` 모듈 CI — Testcontainers 기반, 별도 분리
- `kanggle/global-account-platform` standalone repo 최초 sync — 본 태스크 완료 후 `scripts/sync-portfolio.sh global-account-platform` 수동 실행
- `OutboxFailureHandler`/`OutboxMetricsAutoConfiguration`을 각 앱 application.yml에 active 설정 — 기본 auto-config로 충분

## Acceptance Criteria

- [ ] `.github/workflows/ci.yml`의 `build-and-test` job에 GAP 7개 서비스 `:compileJava` + `:compileTestJava` step 추가
- [ ] 같은 job에 GAP 6개 `*OutboxPollingSchedulerTest` 실행 step 추가 (gateway-service 제외, 이벤트 publish 없음)
- [ ] `./gradlew :projects:global-account-platform:apps:auth-service:test --tests "com.example.auth.infrastructure.event.AuthOutboxPollingSchedulerTest"` PASS (account/admin/community/membership/security 동일)
- [ ] 각 앱의 `*OutboxPollingScheduler` 클래스는 `@Component` 등록, 부모 abstract 생성자 호출
- [ ] `resolveTopic(eventType)`은 standalone GAP의 `application.yml` `outbox.topic-mapping` + 코드상 `*EventPublisher.write(...)` 호출과 일치
- [ ] `libs/java-test-support`에 `java-test-fixtures` plugin + `AbstractIntegrationTest`/`DockerAvailableCondition` 승격 (Phase 2 누락분 보완)
- [ ] WMS 회귀 없음: `./gradlew :projects:wms-platform:apps:master-service:compileJava` PASS
- [ ] ecommerce 회귀 없음: `./gradlew :projects:ecommerce-microservices-platform:apps:auth-service:compileJava` PASS

## Related Specs

- `CLAUDE.md` § "Cross-Project Changes"
- `projects/global-account-platform/docs/migration-notes.md` § "OutboxPollingScheduler note"
- `platform/service-types/` (스케줄러 컴포넌트 참조)
- 참조 구현: `projects/ecommerce-microservices-platform/apps/review-service/src/main/java/com/example/review/infrastructure/event/ReviewOutboxPollingScheduler.java`
- 참조 구현: `projects/wms-platform/apps/master-service/src/main/java/com/wms/master/adapter/out/messaging/MasterOutboxPollingScheduler.java`

## Related Contracts

- 없음 (인프라 등록 + CI 추가, API 계약 변경 없음)

## Edge Cases

- GAP의 `application-test.yml`에 `outbox.polling.enabled=false` 또는 유사 설정이 있어 통합 테스트에서 스케줄러를 비활성화할 가능성 — 확인 후 `@MockitoBean` 패턴이 동작함을 보장
- gateway-service는 이벤트 publish 없음 — Outbox 스케줄러 등록 대상 외
- TASK-BE-077 (Standalone) 이력에 따라 outbox 스케줄러는 `ContextClosedEvent` 시 graceful shutdown 필요 — abstract base가 `@Scheduled` 사용하므로 Spring 라이프사이클로 자동 처리됨, 추가 작업 불필요
- CI 워크플로우의 `paths` 트리거는 GAP 서비스 디렉토리 + `libs/**` + `projects/global-account-platform/build.gradle` + root `settings.gradle`을 모두 포함해야 함

## Failure Scenarios

- Topic 이름 불일치: 서브클래스의 `resolveTopic` 반환값이 docker-compose.yml의 `kafka-init` 토픽과 다르면 통합 테스트가 publish 후 consume에서 실패 → 토픽 상수 정의 시 docker-compose.yml과 1:1 매핑 검증 필수
- GitHub Actions matrix 시 path 필터 누락으로 무관한 PR에서도 GAP CI가 트리거됨 → wms/ecommerce 워크플로우의 paths 패턴 정확히 모방
- `@MockitoBean` 주입 시 Spring이 abstract base를 만나 빈 등록 실패 → concrete 서브클래스가 `@Component`로 정확히 등록되어야 함

## Definition of Done

- [ ] Implementation completed
- [ ] Unit tests added (각 `*OutboxPollingScheduler` 1개씩)
- [ ] Tests passing
- [ ] CI workflows registered and trigger correctly
- [ ] PR created
