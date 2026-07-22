# TASK-BE-549 — auth-service DIVE 핸들러를 fleet 표준 헬퍼로 수렴 (message-string 매칭 제거)

**Status:** done

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet (소규모 fix — 단, 단일-제약 가정 재측정 + 동시성 IT 가 실질. blind 교체 금지)

> `TASK-MONO-450`(done, PR #2814)이 `libs/java-common` 에 `DataIntegrityViolations.isUniqueViolation()`(SQLSTATE 23505 / MySQL 1062 판별)을 도입하고 fleet 전역 `GlobalExceptionHandler` 를 selective 매핑(unique→409, FK/NOT NULL→500)으로 재배선했다. **17개 표현계층 DIVE 핸들러 중 16개가 헬퍼에 위임**한다. **auth-service 만 유일하게 헬퍼를 채택하지 않고 brittle message-string 매칭으로 unique 를 판별**한다 — 헬퍼가 없애려던 바로 그 anti-pattern. 이 티켓은 마지막 straggler 를 수렴시킨다.

---

## Goal

`apps/auth-service/.../presentation/advice/GlobalExceptionHandler.java:71-82` 의 `handleDataIntegrityViolation` 은 unique 위반을 **예외 메시지 문자열 매칭**으로 판별한다:

```java
private static final String UNIQUE_EMAIL_CONSTRAINT = "uq_users_email";
...
if (message.contains(UNIQUE_EMAIL_CONSTRAINT)) {  // → 409 EMAIL_ALREADY_EXISTS
    ...
}
// else → 500 INTERNAL_ERROR
```

`DataIntegrityViolations` 의 Javadoc 이 명시적으로 경고하는 그 패턴이다: **예외 메시지는 vendor·driver 의존적이라 DB/드라이버 업그레이드 시 조용히 깨진다.** 깨지면 중복 이메일 동시 등록이 `409 EMAIL_ALREADY_EXISTS` 대신 **500 으로 누출**된다 — 나머지 fleet 은 MONO-450 이후 이 잠재 위험이 없다.

이 티켓은 auth 를 `DataIntegrityViolations.isUniqueViolation()` 으로 수렴시켜 SQLSTATE 기반 판별로 바꾼다. **단, blind 교체가 아니다** (아래 AC-0).

---

## Scope

### In Scope

1. `handleDataIntegrityViolation`(line 71-82)의 `message.contains("uq_users_email")` 판별을 `DataIntegrityViolations.isUniqueViolation(ex)` 로 교체 — **단, AC-0 의 단일-제약 가정이 성립할 때만** 이 형태가 정당하다.
2. `UNIQUE_EMAIL_CONSTRAINT` 상수 및 관련 message-string 로직 제거(교체로 죽은 코드가 되면).
3. 동시 중복 이메일 등록 race → `409 EMAIL_ALREADY_EXISTS` 를 SQLSTATE 경로로 보존함을 증명하는 통합 테스트.

### Out of Scope

- **`EmailAlreadyExistsException` 핸들러(line 65-69)는 불변** — 애플리케이션이 사전 검사로 던지는 정상 경로. line 71 의 DIVE 핸들러는 그 검사를 통과한 **동시성 race 의 백스톱**일 뿐이다. 둘은 상보.
- 다른 unique 제약을 위한 **새 에러코드 신설**(auth 에 email 외 표현계층에 도달하는 unique 제약이 실제로 있고, 그것이 별도 코드를 요구할 때만 — AC-0 이 판정). 없으면 out of scope.
- 다른 서비스 — MONO-450 으로 이미 16/17 수렴, 재작업 불필요.
- 응답 envelope / `ErrorResponse`(shared `libs/java-web`) 구조 변경.

---

## Acceptance Criteria

- **AC-0 (gate — 단일-제약 가정 재측정, 코드가 이긴다)** — 착수 시 다음을 **직접 grep·확인**한다. 이 티켓 본문의 문장·기존 코드 주석은 출처가 아닌 **가설**이다:
  1. auth-service 의 **표현계층 DIVE 백스톱에 실제로 도달 가능한 unique 제약이 `uq_users_email` 하나뿐인가** — DB 마이그레이션(`schema.sql`/flyway)·엔티티의 `@Column(unique=…)`·`@Table(uniqueConstraints=…)` 를 전수 확인. 다른 unique 제약(예: username, external oauth subject 등)이 이 핸들러에 도달할 수 있으면 그 목록을 적는다.
  2. known-positive 로 fleet 표준을 자기검증: 헬퍼를 이미 쓰는 이웃(예: `user-service` `GlobalExceptionHandler`)이 `isUniqueViolation(ex)` → 409 로 매핑하는 형태를 확인.
  3. **판정 분기**:
     - **가정 성립(uq_users_email 유일)** → `isUniqueViolation(ex)` → `409 EMAIL_ALREADY_EXISTS` 로 단순 교체(코드 의미 보존). AC-1 진행.
     - **가정 불성립(다른 unique 제약도 도달)** → `isUniqueViolation` 만으로는 어느 제약인지 못 가르므로 **naive 교체 시 다른 unique 위반이 EMAIL_ALREADY_EXISTS 로 오분류**된다. 이 경우 설계 판정을 본문에 기록: (a) email 제약만 409 EMAIL_ALREADY_EXISTS, 그 외 unique 는 generic `409 DATA_INTEGRITY_VIOLATION`(fleet 코드)로 분기하되 제약 식별은 어떻게 할지, 또는 (b) 범위를 email backstop 으로 국한. **가정이 틀리면 이 티켓의 형태 자체가 바뀐다 — AC-0 이 그것을 먼저 잡는다.**
- **AC-1** — `handleDataIntegrityViolation` 이 `message.contains(...)` 대신 `DataIntegrityViolations.isUniqueViolation(ex)` 로 unique 를 판별. 기존 관측 가능 동작 보존: 중복 이메일 동시 등록 → `409 EMAIL_ALREADY_EXISTS`; non-unique DIVE(FK/NOT NULL) → `500 INTERNAL_ERROR`. `uq_users_email` 상수 제거(죽은 코드 방지, [[feedback_deletion_leaves_survivors_grep_the_consumers]]).
- **AC-2 (술어가 실패 모드에 맞는 IT)** — 통합 테스트가 **실제 동시 중복 이메일 등록 race**(사전 검사를 통과해 DB unique 제약이 발화하는 경로)를 재현하고 `409 EMAIL_ALREADY_EXISTS` 를 단언. "핸들러가 409 를 낸다"만이 아니라 **DIVE 백스톱 경로가 SQLSTATE 로 판별됨**을 검증(제목이 아닌 본문이 race 를 통과해야 함, [[feedback_guard_predicate_wrong_verify_the_artifact]]). fleet 이 쓰는 `GlobalExceptionHandlerDataIntegrityTest`/`DataIntegrityViolationMappingIntegrationTest` 패턴 참조.
- **AC-3** — `auth-service` 빌드 + 테스트 GREEN. **로컬 Windows Testcontainers FLAKY — CI Linux 가 권위** ([[project_testcontainers_docker_desktop_blocker]]).

---

## Related Specs

- `apps/auth-service/src/main/java/com/example/auth/presentation/advice/GlobalExceptionHandler.java:28,71-82` — straggler(message-string 매칭 지점). line 65-69 = 정상 경로 핸들러(불변).
- `libs/java-common/src/main/java/com/example/common/persistence/DataIntegrityViolations.java` — fleet 표준 헬퍼(`isUniqueViolation`, Javadoc 의 message-match 경고).
- `apps/user-service/.../presentation/exception/GlobalExceptionHandler.java` — known-positive 이웃(헬퍼 위임 형태 참조).
- `tasks/done/TASK-MONO-450-*` (root `tasks/done/`) — selective 매핑 fleet 표준 확립.
- `platform/error-handling.md` § `DATA_INTEGRITY_VIOLATION` — "409 는 unique 위반만; FK/NOT NULL/CHECK → 500(`INTERNAL_ERROR`)".

## Related Contracts

- auth-service 회원가입/등록 엔드포인트 계약 — 409 EMAIL_ALREADY_EXISTS 응답은 **동작 보존**이므로 계약 변경 없음이 정상. **단** AC-0 이 다른 unique 제약에 새 409 코드를 신설하는 판정을 내리면 해당 contract + `platform/error-handling.md` 레지스트리를 **구현 전에** 갱신.

---

## Edge Cases

1. **🔴 email 외 unique 제약이 백스톱에 도달** — naive `isUniqueViolation → EMAIL_ALREADY_EXISTS` 교체는 그 위반을 "이메일 중복"으로 오분류한다. AC-0 이 이 가정을 먼저 검증하는 이유. 이것이 이 티켓이 "기계적 리팩토링"이 아니라 domain 판정을 요구하는 핵심.
2. **`EmailAlreadyExistsException` 정상 경로와의 중복 코드** — 애플리케이션 사전 검사(line 65)와 DB race 백스톱(line 71)이 **같은 409 EMAIL_ALREADY_EXISTS** 를 낸다(의도된 것). 교체가 이 일관성을 깨지 않게.
3. **`DataAccessException`(line 108, 503) 과의 우선순위** — `DataIntegrityViolationException` 은 `DataAccessException` 의 하위. Spring `@ExceptionHandler` 가 더 구체적 타입을 먼저 매칭하므로 DIVE 는 line 71 로 간다(교체 후에도 유지 확인).
4. **드라이버별 SQLSTATE** — 헬퍼는 PG/H2 23505 + MySQL 1062 를 커버. auth 의 런타임 DB(prod)와 테스트 DB(H2/Testcontainers)가 모두 헬퍼 커버 범위 안인지 확인.

## Failure Scenarios

- **F1 — AC-0 없이 blind 교체.** email 외 unique 제약이 있으면 그 위반이 EMAIL_ALREADY_EXISTS 로 오분류 → 사용자 혼란. AC-0 이 강제.
- **F2 — IT 가 race 를 통과하지 않음.** 핸들러를 직접 호출해 409 만 확인하면(제목만 커버) message→SQLSTATE 전환의 실효를 증명 못 한다. AC-2 가 실제 동시 등록 경로를 요구.
- **F3 — `uq_users_email` 상수만 남기고 방치.** 교체 후 죽은 상수/import 가 남으면 다음 독자가 두 판별 경로로 오인. AC-1 이 제거를 요구.
- **F4 — non-unique DIVE 를 409 로 넓힘.** 헬퍼의 selective 계약(FK/NOT NULL→500)을 어기면 fleet 표준 위반. AC-1 이 500 보존을 단언.

---

## Definition of Done

- [ ] AC-0 단일-제약 가정 재측정 + 판정 분기 기록
- [ ] AC-1 `isUniqueViolation` 위임 + 상수 제거 (동작 보존)
- [ ] AC-2 동시 중복 이메일 race IT → 409 EMAIL_ALREADY_EXISTS
- [ ] AC-3 GREEN (CI Linux 권위)

---

## Notes

- **분량**: small. 파일 1개(+테스트). 실질 리스크는 코드량이 아니라 **AC-0 의 단일-제약 가정** — 그것이 틀리면 형태가 바뀐다.
- **발견 경로**: MONO-450 fleet rewire 정합화 감사에서 17개 중 16개 수렴 확인, auth 만 message-string 잔존. 형제 대조로 드러난 straggler([[project_enforcement_straggler_sibling_parity]]).
- **이 task 가 방어하는 실패 모드**: **라이브러리가 소비자에게 표준을 제공했는데 한 소비자만 옛 workaround(message-match)를 유지** — 공급원(헬퍼)은 이미 옳고, 결함은 미채택이다([[feedback_workaround_becomes_the_contract]]). auth 가 MONO-450 scope 목록에 없어 rewire 가 건드리지 않은 **선재(先在) 갭**이지 회귀가 아니다.
- **dependency**: `선행` = `TASK-MONO-450`(done, 헬퍼·fleet 표준).
