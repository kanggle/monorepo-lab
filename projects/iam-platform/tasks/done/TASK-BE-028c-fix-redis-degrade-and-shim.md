# Task ID

TASK-BE-028c-fix-redis-degrade-and-shim

# Title

admin-service — CachingPermissionEvaluator degrade catch 확장 + auth-service UuidV7 shim 보강

# Status

ready

# Owner

backend

# Task Tags

- code

# depends_on

- TASK-BE-028c

---

# Goal

TASK-BE-028c 리뷰에서 발견된 Warning 3건을 해소한다. Redis degrade 경로가 Lettuce 하위 예외 전체를 안전하게 수용하고, auth-service UuidV7 shim이 승격 libs와 동일 API 표면을 forward하며 제거 압박을 유지하도록 한다.

---

# Scope

## In Scope

### Fix 1 — CachingPermissionEvaluator degrade catch 범위 확장
- `apps/admin-service/src/main/java/com/example/admin/infrastructure/persistence/rbac/CachingPermissionEvaluator.java:105, 125`의 catch 절이 `RedisConnectionFailureException | QueryTimeoutException` 만 처리.
- Lettuce가 발생시키는 하위 예외(`io.lettuce.core.RedisException`, `io.lettuce.core.RedisCommandTimeoutException`, `java.net.SocketException` 등) 및 일시적 I/O 예외를 포함하도록 확장.
- 권장: `catch (DataAccessException | io.lettuce.core.RedisException ex)` 또는 최소 상위 Spring `DataAccessException` + Lettuce 최상위 `RedisException` 둘 다.
- 로그는 WARN 레벨 유지, degrade 경로는 origin DB 경로로 위임.

### Fix 2 — auth-service UuidV7 shim 강화
- `apps/auth-service/src/main/java/com/example/auth/domain/session/UuidV7.java:16` 의 `@Deprecated`를 `@Deprecated(forRemoval = true, since = "TASK-BE-028c")`로 변경.
- Javadoc에 이관 경로 명시: `com.example.common.id.UuidV7` 사용 권장.
- shim에 `public static long timestampMs(UUID uuid)` forward 메서드 추가(libs 원본 delegation).
- 후속 클린업 태스크로 `RegisterOrUpdateDeviceSessionUseCase` 이관을 노트로 기록.

### Fix 3 — rbac.md Open Issues 후속 태스크 번호 교정
- `specs/services/admin-service/rbac.md:238` 해소 문구에 잘못 적힌 `TASK-BE-030` 참조 수정. 올바른 후속 태스크는 TTL 단독 의존 한계를 다루는 별도 backlog 항목 (현재 미지정이면 "추후 backlog"로 표기).

### (선택) Suggestion 대응
- `PermissionEvaluatorCacheTest:111`의 `Thread.sleep(2_200)`을 Awaitility polling으로 교체 — Critical/Warning 수정과 함께 진행 권장, 분리 가능.
- libs UuidV7 `rand_a` 12비트 전체 무작위 사용으로 확장 (현재 상위 4비트가 0). RFC 권고 충족.

## Out of Scope

- UUIDv7 pub/sub invalidation (별도 태스크)
- `@ConfigurationProperties` 바인딩 분리 (Suggestion 수준)

---

# Acceptance Criteria

- [ ] Redis 다운 시뮬레이션 테스트가 `RedisConnectionFailureException` 외 `RedisException`/`RedisCommandTimeoutException`에서도 origin 경로로 degrade
- [ ] `CachingPermissionEvaluator` 외부로 Redis 예외가 전파되지 않음 (권한 평가는 성공 또는 origin 결과 반환)
- [ ] `com.example.auth.domain.session.UuidV7`가 `forRemoval = true`로 표시되고 `timestampMs` forward 존재
- [ ] `rbac.md` Open Issues 후속 태스크 번호 정정
- [ ] `./gradlew :apps:admin-service:test :apps:auth-service:test :libs:java-common:test` 통과

---

# Related Specs

- `specs/services/admin-service/rbac.md`
- `platform/shared-library-policy.md`
- `rules/traits/integration-heavy.md` (degrade 원칙)

# Related Contracts

- (없음)

---

# Target Service

- `apps/admin-service`
- `apps/auth-service` (shim 보강)
- `libs/java-common` (선택 rand_a 수정)

---

# Edge Cases

- Lettuce 버전 업그레이드 시 예외 계층 변경 가능성 — `DataAccessException`/`RedisException` 조합으로 방어
- 단위 테스트에서 shim forward 호출은 원본 libs 결과와 일치해야 함

---

# Failure Scenarios

- catch 확장이 과도하게 포괄적으로 되어 비-Redis 버그를 삼키는 위험 — catch 절에 예외 타입 명시 유지, 로그에 클래스명 기록

---

# Test Requirements

- Unit: degrade 경로에 `RedisException` mock 주입 후 origin 결과 반환 검증
- Unit: shim의 `timestampMs` forward가 libs 원본과 동일 UUID에 대해 같은 값을 반환
- 기존 테스트 회귀 없음

---

# Definition of Done

- [ ] 구현 완료
- [ ] 테스트 통과
- [ ] Ready for review
