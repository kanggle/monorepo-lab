# Trait: audit-heavy

> **Activated when**: `PROJECT.md` includes `audit-heavy` in `traits:`.

---

## Scope

"누가 언제 무엇을 했는가"에 대한 **불변 감사 추적**이 법적·규제적 또는 비즈니스 책임 측면에서 요구되는 시스템에 적용된다. 사건 사후 분석, 내부자 위협 탐지, 규제 보고, 분쟁 해결의 근거가 된다.

일반적으로 `regulated` trait과 함께 선언되지만, 독립적으로도 유효하다 (예: 금융 거래, 의료 기록, 관리자 작업이 많은 운영 플랫폼).

**감사 로그의 핵심 구분**: 일반 애플리케이션 로그(디버깅·성능 분석 목적)와 감사 로그는 **물리적·논리적으로 분리**된다. 감사 로그는 불변이고, 일반 로그는 시간이 지나면 소멸된다.

---

## Mandatory Rules

### A1. 감사 대상(Auditable Action) 명시
어떤 액션이 감사 로그에 기록되는지 **사전에 목록**으로 정의한다. 누락은 감사 부재와 동일. 최소한 다음을 포함:

- 계정 생성/삭제/상태 변경
- 로그인 성공/실패/rate limit/토큰 재사용 탐지
- 권한·역할 부여/회수
- 관리자 작업 전체 (lock, unlock, 강제 로그아웃, 감사 조회)
- 민감 데이터(PII, 결제, 의료 등)에 대한 읽기 (`regulated` R5와 교차)
- 설정 변경 (보안 정책, 기능 플래그, 쿼터 등)
- 이벤트 재처리·수동 개입 (DLQ 재시도, 데이터 수정 등)

### A2. 감사 로그 스키마는 표준화
모든 감사 이벤트는 다음 최소 필드를 가진다:

```
{
  "event_id": "UUID",
  "occurred_at": "ISO-8601 UTC",
  "actor": {
    "type": "user | operator | system",
    "id": "actor identifier",
    "session_id": "optional"
  },
  "action": "standardized action code (e.g., account.locked)",
  "target": {
    "type": "resource type",
    "id": "resource id"
  },
  "context": {
    "ip": "masked",
    "user_agent": "optional",
    "reason": "human-readable reason or ticket id"
  },
  "outcome": "success | failure",
  "metadata": { ... domain-specific ... }
}
```

필드가 비었다고 생략하지 않는다. 명시적 `null` 또는 `unknown`을 기록한다.

### A3. 불변성(Immutability)
감사 로그는 **UPDATE·DELETE 금지**. 기록 후 수정·삭제 경로가 존재하면 안 된다. 기술적 수단:

- append-only 테이블 (트리거로 UPDATE/DELETE 차단) 또는
- append-only 로그 스트림 (Kafka compacted = 금지, 일반 로그 토픽 사용) 또는
- write-once object storage (S3 Object Lock 등)

수정이 필요한 경우는 **정정 이벤트(correction event)** 를 새로 추가한다. 원본은 유지.

### A4. 보존 기간은 비즈니스/규제 요구에 따라 결정
감사 로그 보존 기간은 **일반 로그보다 길다**. 최소 1년, 규제 요구 시 5~7년 이상. `specs/services/<service>/retention.md` 또는 전용 문서에 기록.

### A5. 감사 데이터 접근 제어
감사 로그 조회는 **제한된 operator 역할**에만 허용된다. 감사 로그 조회 자체도 감사된다 (메타 감사 = meta-audit). 감사 로그 조회 엔드포인트는 공개 API로 노출하지 않는다.

### A6. 시계열 일관성
감사 이벤트의 `occurred_at`은 **UTC ISO-8601** 포맷이며, 모든 서비스가 동일한 시계(NTP 동기화) 기준. 로컬 타임존 기록 금지. 클럭 skew 허용 범위(일반적으로 5분)를 넘는 기록은 경고.

### A7. 감사와 애플리케이션 경로의 원자성
비즈니스 액션과 감사 기록은 **동일 트랜잭션 경계** 또는 **아웃박스 패턴**으로 묶는다. "DB 커밋되었는데 감사 로그 누락" 상황을 금지. (`transactional` T3과 교차)

### A8. 재생·복원 가능성
감사 로그만으로 "언제, 누가, 어떤 순서로" 작업했는지 시간순 재구성이 가능해야 한다. 이를 위해 `event_id`는 UUID 충돌 방지 + `occurred_at`은 밀리초 이상 정밀도.

### A9. 개인정보 마스킹과의 조화 (regulated trait와 교차)
감사 로그에는 PII가 포함될 수 있지만 (행위자 식별에 필요), 다음을 준수:

- 로그 파이프라인 밖으로 내보낼 때(export·조회 응답)는 마스킹 규칙 적용
- 원본 저장소 접근은 특권 operator로 제한
- PII가 식별자 이외의 목적으로 본문에 포함되면 제거 또는 별도 참조 구조 사용

### A10. 감사 경로의 가용성
감사 기록 실패는 **비즈니스 액션을 차단**한다. "감사는 best-effort"라는 설계는 금지. Kafka broker 장애 시 비즈니스 액션도 실패해야 한다 (fail-closed).

다만 fail-closed가 제품 전체를 다운시키는 경우(예: 모든 읽기까지 차단), 명시적 fallback(예: 로컬 파일 큐 + 사후 복구)을 설계 문서에 기록한다.

---

## Forbidden Patterns

- ❌ **감사 로그 UPDATE/DELETE 경로 존재**
- ❌ **감사 대상 목록이 문서화되지 않음** ("누락된 것이 뭔지 모름" 상태)
- ❌ **감사 로그와 일반 애플리케이션 로그가 같은 저장소에 섞임**
- ❌ **감사 로그 조회가 감사되지 않음** (메타 감사 부재)
- ❌ **로컬 타임존 또는 낮은 정밀도 타임스탬프**
- ❌ **비즈니스 트랜잭션 커밋 후 감사 기록** (순서·원자성 위반)
- ❌ **감사 실패 시 silent continue** (fail-open)
- ❌ **감사 로그 조회 엔드포인트가 공개 API로 노출됨**

---

## Required Artifacts

1. **감사 대상 카탈로그** — 모든 auditable action의 목록과 코드. 위치: `specs/features/audit-trail.md` 또는 전용 `platform/audit-catalog.md`
2. **감사 이벤트 스키마** — JSON Schema 또는 Avro 정의
3. **저장소·불변성 전략** — 어떤 기술로 append-only를 강제하는지
4. **보존 정책** — 기간, 아카이브, 삭제 경로 (규제 요구에 근거)
5. **감사 조회 API 스펙** — 제한된 operator 전용, 내부 경로
6. **메타 감사 설계** — 감사 조회 자체를 어떻게 기록하는지
7. **시계 동기화 운영 지침** — NTP, 클럭 skew 경보

---

## Interaction with Common Rules

- [./transactional.md](./transactional.md) T3 (outbox 패턴)과 A7이 공동 적용되어 감사 기록의 원자성을 보장한다.
- [./regulated.md](./regulated.md) R5(접근 감사), R4(PII 마스킹)와 교차. regulated는 "무엇을 보호할지", audit-heavy는 "어떻게 기록할지"를 정의한다.
- [../../platform/observability.md](../../platform/observability.md)의 로그 파이프라인과는 **분리된** 감사 파이프라인을 가진다. 혼용 금지.
- [../../platform/event-driven-policy.md](../../platform/event-driven-policy.md)의 이벤트 스키마 · 버전 관리 규칙을 감사 이벤트도 따른다 (오히려 더 엄격하게 — 불변).
- 도메인 [../domains/saas.md](../domains/saas.md)의 S4(모든 인증 분기 이벤트 발행), S5(admin 감사 필수)와 공동 소유.

---

## Checklist (Review Gate)

- [ ] 감사 대상 액션 목록이 문서화되어 있는가? (A1)
- [ ] 모든 감사 이벤트가 표준 스키마(actor/action/target/outcome)를 따르는가? (A2)
- [ ] 감사 저장소가 append-only이고 UPDATE/DELETE 경로가 없는가? (A3)
- [ ] 보존 기간이 명시되어 있고 규제 요구를 충족하는가? (A4)
- [ ] 감사 로그 조회가 제한된 역할에만 허용되고, 조회 자체가 감사되는가? (A5)
- [ ] 모든 타임스탬프가 UTC ISO-8601이며 시계 동기화가 모니터링되는가? (A6)
- [ ] 비즈니스 액션과 감사 기록이 원자적(트랜잭션 또는 아웃박스)인가? (A7)
- [ ] 감사 로그만으로 사건 재구성이 가능한가? (A8)
- [ ] 감사 로그 내 PII가 적절히 마스킹·제한되는가? (A9)
- [ ] 감사 경로 실패 시 비즈니스 액션이 fail-closed인가? (A10)
- [ ] 일반 애플리케이션 로그와 감사 로그가 물리적으로 분리되어 있는가?
- [ ] 금지 패턴(UPDATE/DELETE 경로, 혼합 저장, 공개 조회 등)이 코드베이스에 없는가?
