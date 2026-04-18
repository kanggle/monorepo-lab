# Trait: transactional

> **Activated when**: `PROJECT.md` includes `transactional` in `traits:`.

---

## Scope

쓰기 경로가 "두 번 실행되면 안 되는" 연산을 포함하는 모든 서비스에 적용된다. 특히 **금전·재고·권한 변경** 같이 비즈니스적으로 되돌리기 어려운 연산.

적용 범위는 프로젝트마다 다르며 각 서비스의 `specs/services/<service>/architecture.md`에서 선언된다. 일반 원칙:

- **필수 적용**: 인증·인가·계정 상태 전이, 금전 거래, 재고 이동/조정, 운영자 강제 작업, 주문·결제 라이프사이클 등 되돌리기 어려운 상태 변경
- **조건부**: 외부 시스템으로 자동 명령을 발행하는 경로 (명령이 중복 수신되면 side effect가 두 번 일어나는 경우)
- **제외**: 순수 읽기 경로 (조회, 검색, 이력 표시)

---

## Mandatory Rules

### T1. Idempotency Key 필수
상태를 변경하는 모든 공개 API는 `Idempotency-Key` 헤더를 받아들이고 존중해야 한다. 같은 키로 반복 호출되면 **정확히 1회**만 실행되고, 반복 호출은 첫 실행의 결과를 반환한다.

- 키 유효 기간: 최소 24시간
- 키 저장: Redis, 또는 서비스 DB의 dedupe 테이블
- 키 범위: (clientId, endpoint, idempotencyKey) tuple

### T2. Command 처리는 원자적 트랜잭션 경계를 명확히 가진다
하나의 비즈니스 명령(예: "주문 생성")은 단일 aggregate 경계 내에서 원자적으로 커밋되어야 한다. Aggregate 간 일관성이 필요하면 **Saga 패턴** 또는 **아웃박스 이벤트**로 분리한다.

- 로컬 트랜잭션 안에서 여러 aggregate를 건드리지 않는다
- 서비스 간 트랜잭션은 금지 — 분산 트랜잭션 없음

### T3. Outbox 패턴으로 이벤트 발행 보장
상태 변경과 이벤트 발행은 같은 DB 트랜잭션 안에서 아웃박스 테이블에 기록되고, 별도 프로세스가 이를 브로커에 전달한다. "DB 커밋했는데 이벤트는 유실" 상황 금지.

### T4. State Transition은 상태 기계로 모델링
도메인 aggregate의 상태 변경은 사전 정의된 상태 기계에서만 허용된다. 허용되지 않은 전이 요청은 `STATE_TRANSITION_INVALID` 오류로 거부한다. 직접 status 컬럼 update 금지.

### T5. Optimistic Concurrency Control
동일 aggregate의 동시 수정은 version 필드(또는 동등)를 이용한 낙관적 락으로 감지하고, 충돌 시 `CONFLICT` 오류로 거부한다. Pessimistic lock은 특별한 근거 없이 금지.

### T6. Saga는 보상 트랜잭션(Compensation)을 명시
여러 서비스에 걸친 프로세스는 Saga로 모델링하며, 각 단계는 반드시 **보상 액션**을 정의한다. 실패 시 이미 수행된 단계를 역순으로 보상.

### T7. Auditable State History
핵심 상태 변경(주문 상태, 결제 상태, 환불 상태)은 변경 이력 테이블 또는 이벤트 스트림으로 보존한다. "누가/언제/왜" 변경했는지 추적 가능해야 한다.
(이 요구는 `audit-heavy` trait과 부분 중복되지만, transactional 단독으로도 해당 범위에 한해 강제된다.)

### T8. At-least-once 소비는 반드시 Idempotent Handler로 처리
이벤트 컨슈머는 최소 1회 전달을 가정하고, 동일 이벤트 중복 수신에도 비즈니스 상태가 중복 변경되지 않도록 설계한다. eventId 기반 dedupe 또는 멱등한 upsert.

---

## Forbidden Patterns

- ❌ **직접 상태 컬럼 UPDATE** (`UPDATE orders SET status = ? WHERE id = ?`) — 반드시 command + 상태 기계 경로를 통해야 함
- ❌ **분산 2PC (XA 트랜잭션)** — 대신 Saga 또는 아웃박스
- ❌ **Idempotency Key 없이 금전/재고 변경 API 노출**
- ❌ **fire-and-forget HTTP 호출** (retry·ack 없이 "찔러보는" 방식)
- ❌ **이벤트 발행 후 DB 커밋** (발행은 아웃박스 경유)
- ❌ **컨슈머에서 순서 의존 가정** — 브로커 파티션 내에서만 순서 보장, 교차 파티션은 순서 없음

---

## Required Artifacts

transactional trait이 활성화된 프로젝트는 다음 산출물을 **필수**로 갖춰야 한다:

1. **State machine 다이어그램** — 각 핵심 aggregate (예: Order, Payment, Refund)의 상태와 전이 조건. 위치: `specs/services/<service>/state-machines/`
2. **Saga 정의** — 여러 서비스에 걸친 프로세스. 각 단계, 보상 액션, 타임아웃. 위치: `specs/features/<feature>.md` 또는 `specs/services/<service>/sagas/`
3. **Idempotency 키 전략 문서** — 키 범위, 저장소, 만료. 위치: `specs/services/<service>/idempotency.md`
4. **Outbox 테이블 스키마** — DB migration에 포함
5. **실패 시나리오 테스트** — 동일 명령 2회 호출, Saga 중간 실패, 이벤트 중복 소비 시나리오

---

## Interaction with Common Rules

- [../../platform/error-handling.md](../../platform/error-handling.md)의 `CONFLICT`, `STATE_TRANSITION_INVALID`, `DUPLICATE_REQUEST` 에러 코드를 이 trait 규칙에 맞춰 사용한다.
- [../../platform/testing-strategy.md](../../platform/testing-strategy.md)의 Integration/Event 테스트 레이어에서 **멱등성·보상 시나리오**를 필수 포함한다.
- [../../platform/observability.md](../../platform/observability.md)에 정의된 메트릭에 더해, idempotency hit rate, saga failure rate, outbox lag 메트릭을 추가한다.

---

## Checklist (Review Gate)

- [ ] 상태 변경 API가 `Idempotency-Key` 헤더를 받고 저장·검사하는가? (T1)
- [ ] Command가 단일 aggregate 경계에서 원자적으로 커밋되는가? (T2)
- [ ] 이벤트 발행이 아웃박스 패턴을 통하는가? (T3)
- [ ] 상태 전이가 상태 기계로 모델링되어 있고 직접 UPDATE가 없는가? (T4)
- [ ] Aggregate에 version/updated_at 기반 optimistic locking이 있는가? (T5)
- [ ] Saga 단계마다 보상 액션이 정의되어 있는가? (T6)
- [ ] 핵심 상태 변경이 이력 테이블 또는 이벤트로 보존되는가? (T7)
- [ ] 이벤트 컨슈머가 eventId dedupe 또는 멱등 upsert로 구현되어 있는가? (T8)
- [ ] 금지 패턴(직접 UPDATE, 2PC, 키 없는 API 등)이 코드베이스에 존재하지 않는가?
- [ ] State machine, Saga, Idempotency 전략 문서가 `specs/services/<service>/` 아래에 존재하는가?
