# Task ID

TASK-BE-069

# Title

fix(auth-service): OAuthLoginUseCase#callback `@Transactional` 범위 축소 — 외부 HTTP 호출을 트랜잭션 밖으로

# Status

ready

# Owner

backend

# Task Tags

- code
- refactor

# depends_on

- TASK-BE-066 (superseded, split)

---

# Goal

`OAuthLoginUseCase#callback` 이 `@Transactional` 범위 안에서 외부 provider(Google/Microsoft) 로 2건의 HTTP 호출(token exchange + userinfo)을 수행 중. 이 패턴은 Hikari 커넥션을 외부 호출 구간 동안 점유시켜 통합 테스트의 MySQL testcontainer 연결 고갈 및 OAuthLoginIntegrationTest 의 503 실패를 유발하는 것으로 지목됨(TASK-BE-062 #18 CI 실측).

본 task 는 외부 HTTP 호출을 `@Transactional` 밖으로 분리하여 커넥션 pinning 을 해소한다. Testcontainers 설정 변경은 out-of-scope (TASK-BE-070).

---

# Scope

## In Scope

1. **`OAuthLoginUseCase#callback` 구조 재설계**:
   - 외부 HTTP 호출 구간 (token + userinfo 조회) → **트랜잭션 밖**
   - 조회 결과를 input 으로 받는 별도 `@Transactional` 메서드 (또는 컴포넌트) 에서 DB writes (account/credential/session/outbox) 수행
   - 분리 방식은 구현자 선택: (a) 같은 클래스 private `@Transactional` 메서드 (self-invocation 이 AOP 프록시를 안 타므로 금지 — 별도 컴포넌트로 분리), (b) `OAuthLoginTransactionalStep` 등 새 컴포넌트 분리 권장
2. **입력 command 객체 도입**(선택적): 트랜잭션 컴포넌트에 전달할 데이터 DTO 정의. 이름은 구현자 결정.
3. **보상(compensation) 고민**:
   - 외부 HTTP 가 성공했는데 DB 트랜잭션이 실패하는 경우, outbox write 가 롤백되므로 downstream 이벤트는 발행되지 않음 → 현재 동작과 동일
   - 외부 HTTP 가 실패하면 기존과 동일하게 예외 반환 → DB 작업 시작 전 fail-closed
   - 새로 생기는 위험: 외부 HTTP 성공 후 txn 커밋 실패 시 **user 관점에는 "로그인 실패"** 인데 provider 쪽에는 인증 흔적이 남음. 이는 기존 동작 대비 악화 아님(이미 outbox 롤백 특성상 본질적으로 동일). README/주석에 기록만 하면 충분.
4. **unit test** (`OAuthLoginUseCaseTest` 또는 신규 `OAuthLoginTransactionalStepTest`): 경계 분리가 의도한 대로 동작하는지 (외부 HTTP mock → transactional 컴포넌트 호출 검증, 트랜잭션 메서드는 입력만 받아 DB write). Mockito 기반.

## Out of Scope

- Testcontainers 설정 변경 / reuse 활성화 / 안정성 튜닝 (→ TASK-BE-070)
- `OAuthLoginIntegrationTest` 의 `@Disabled` 제거 (→ TASK-BE-071)
- 다른 통합 테스트 수정
- OAuth provider 추가 / 인증 정책 변경

---

# Acceptance Criteria

- [ ] `OAuthLoginUseCase#callback` 경로에서 외부 HTTP 호출이 `@Transactional` 범위 밖에서 일어남을 코드로 확인 가능 (트랜잭션 어노테이션이 HTTP 호출 라인을 감싸지 않음)
- [ ] DB writes (credential / session / outbox 등) 가 단일 `@Transactional` 범위 안에 있음
- [ ] `OAuthLoginUseCaseTest` (unit) — 분리 구조 검증: 외부 HTTP mock, txn 컴포넌트 호출 일관성
- [ ] `./gradlew :apps:auth-service:test` green (slice/unit — 통합 테스트는 여전히 `@Disabled` 상태 유지)
- [ ] 기존 `OAuthLoginUseCase` 호출 경로(login 성공/실패) 회귀 없음 (기존 `OAuthLoginUseCase` 관련 unit 테스트 pass)
- [ ] Scope 3 의 compensation 관련 메모가 코드 주석 또는 `specs/services/auth-service/architecture.md` 의 관련 섹션에 1-2 문장 기록됨

---

# Related Specs

- `specs/services/auth-service/architecture.md`
- `specs/features/authentication.md`
- `platform/event-driven-policy.md` (outbox 정책)

---

# Related Contracts

- `specs/contracts/events/auth-events.md` (auth.login.succeeded 발행 timing 관련 — 본 task 로 timing 이 바뀌지 않음을 확인만)

---

# Target Service

- `apps/auth-service`

---

# Architecture

layered 4-layer. 변경 범위는 application layer (OAuthLoginUseCase + 신규 컴포넌트). infrastructure/presentation 변경 없음.

---

# Edge Cases

- **Self-invocation 문제**: 같은 클래스 안에서 `@Transactional` 메서드를 private 호출하면 Spring AOP 프록시를 타지 않아 트랜잭션이 적용되지 않음. 별도 빈(컴포넌트) 로 분리 필수.
- **예외 타입**: HTTP 호출 단계에서의 예외는 트랜잭션 진입 전이므로 `@Transactional` rollback 규칙과 무관. HTTP 예외를 기존 동일하게 propagate (또는 매핑) 하는지 기존 테스트로 검증.
- **이벤트 순서**: outbox write 는 여전히 같은 txn 안에 있으므로 순서/원자성 보존.

---

# Failure Scenarios

- self-invocation 으로 인해 트랜잭션이 적용 안 되는 버그 → unit test 에서 빈 분리 여부를 명시적으로 assert (예: 트랜잭션 컴포넌트가 `@Autowired` 또는 생성자 주입으로 들어오는지 확인)
- compensation 가정이 스펙과 불일치 → `auth-events.md` 및 `authentication.md` 재확인

---

# Test Requirements

- **unit**: `OAuthLoginUseCaseTest` 또는 신규 `OAuthLoginTransactionalStepTest` 로 경계 검증
- 통합 테스트는 본 task 에서 activate 하지 않음 (여전히 @Disabled, TASK-BE-071 에서 재활성화)

---

# Definition of Done

- [ ] OAuthLoginUseCase#callback 의 HTTP 호출이 트랜잭션 밖으로 이동됨
- [ ] unit test pass
- [ ] architecture.md 또는 코드 주석에 compensation 메모
- [ ] Ready for review
