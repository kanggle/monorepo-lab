# Trait: integration-heavy

> **Activated when**: `PROJECT.md` includes `integration-heavy` in `traits:`.

---

## Scope

외부 시스템(써드파티 API, 웹훅, 파일 교환, 메시지 브로커)과의 연동이 다수이며, 연동 안정성이 시스템 품질을 좌우하는 서비스에 적용된다.

적용 범위는 프로젝트마다 다르며 각 서비스의 `specs/services/<service>/architecture.md` 또는 `specs/services/<service>/external-integrations.md`에서 선언된다. 일반 원칙:

- **필수 적용**: 외부 알림 제공자(이메일/SMS/푸시), OAuth 제공자, 결제·PG, 배송·물류 시스템(TMS/ERP), 바코드/RFID 스캐너, 리스크 인텔리전스 API 등 외부 시스템과 직접 연동하는 서비스
- **조건부**: 외부 API를 2개 이상 호출하는 서비스 (공용 풀 고갈·연쇄 장애 위험)
- **제외**: 외부 연동이 없는 순수 내부 서비스

---

## Mandatory Rules

### I1. 모든 외부 호출은 타임아웃 필수
외부 API 호출은 연결·읽기 타임아웃을 반드시 명시한다. 기본값 의존 금지. 타임아웃 없는 호출은 circuit exhaustion의 주요 원인.

권장: 연결 5초, 읽기 30초 (도메인별 조정 가능하나 문서화 필요).

### I2. Circuit Breaker 적용
외부 호출은 circuit breaker 뒤에 배치한다. 연속 실패율/응답 시간 임계치 초과 시 circuit을 열어 **빠른 실패(fast fail)** 로 전환. Spring Cloud Circuit Breaker, Resilience4j 또는 동등 구현 사용.

### I3. 재시도는 지수 백오프 + Jitter
일시적 실패(네트워크, 5xx)에 대한 재시도는 **지수 백오프 + 랜덤 jitter** 로 스케줄링한다. 고정 간격 재시도는 thundering herd 유발. 최대 재시도 횟수는 명시적으로 제한(기본 3회).

재시도 금지 대상: 4xx 클라이언트 에러, 명시적 비멱등 연산.

### I4. 멱등한 Side Effect 설계
외부로의 쓰기 호출(결제, 주문 접수, 알림 발송)은 **멱등 키(idempotency key)** 를 벤더에 전달하거나, 벤더가 미지원이면 내부 dedupe 테이블로 보완한다. "재시도 때문에 두 번 청구/발송"은 절대 금지.

### I5. Dead Letter Queue (DLQ) 필수
비동기 경로(이벤트 컨슈머, 메시지 기반 연동)는 반복 실패 후 DLQ로 격리한다. DLQ는 **조회 가능**해야 하고, 재처리 수단(수동 재시도 API)을 제공한다.

### I6. Webhook 수신은 인증·재생 방지 필수
외부에서 들어오는 webhook은 반드시 다음을 검증:

- **서명 검증** (HMAC 또는 공개키 서명)
- **타임스탬프 검증** (재생 공격 방지, 윈도우 일반적으로 5분)
- **멱등 처리** (동일 이벤트 ID 재수신 시 중복 처리 금지)

### I7. 벤더별 Adapter 레이어 분리
외부 시스템 호출 코드는 **도메인 로직과 분리**된 어댑터(adapter/gateway) 레이어에 둔다. 도메인 코드에서 벤더 SDK를 직접 import하는 것은 금지. Hexagonal/DDD 프로젝트는 outbound port/adapter 구조를 따른다.

### I8. 벤더 응답은 내부 모델로 번역
외부 API 응답 객체를 그대로 도메인·DB·이벤트에 노출 금지. 어댑터에서 **내부 도메인 모델**로 번역한다. 벤더 SDK 변경이 도메인 전체로 전파되는 것을 방지.

### I9. 장애 격리 — Bulkhead 패턴
여러 외부 시스템과 연동 시, 한 벤더의 장애가 다른 벤더 호출에 영향을 주지 않도록 **스레드 풀·커넥션 풀을 벤더별로 분리**한다. 공용 풀 고갈 방지.

### I10. 통합 테스트는 WireMock 등 fake로
외부 API 연동 테스트는 실제 벤더 호출이 아닌 WireMock/Mountebank 등 **가짜 서버**를 띄워 수행한다. 성공·타임아웃·5xx·재생 공격 등 실패 모드를 모두 테스트.

---

## Forbidden Patterns

- ❌ **타임아웃 없는 외부 호출** (기본값 의존)
- ❌ **Circuit breaker 없이 직접 외부 호출**
- ❌ **고정 간격 재시도** (jitter 없음)
- ❌ **4xx에 대한 재시도** (벤더 정책 위반 원인)
- ❌ **웹훅 서명 검증 생략**
- ❌ **벤더 SDK를 도메인 레이어에서 직접 사용**
- ❌ **DLQ 없이 이벤트 소비**
- ❌ **테스트 코드가 실제 벤더를 호출**

---

## Required Artifacts

1. **외부 연동 카탈로그** — 연동하는 모든 벤더 목록, 담당 서비스, 호출 방향(in/out), 인증 방식. 위치: `specs/services/<service>/external-integrations.md`
2. **Circuit/Retry 정책 표** — 벤더별 타임아웃, circuit 임계치, 재시도 횟수·백오프. 설정 파일 또는 위 문서와 병행
3. **Webhook 인증 규약** — 들어오는 webhook 각각의 서명·재생 방지 방식
4. **DLQ 재처리 절차 문서** — DLQ 메시지 조회·재시도 수동 절차 + 알림 연결
5. **Adapter 레이어 구조** — 어디서 벤더 호출이 일어나는지, 번역 레이어 위치
6. **WireMock 테스트 스위트** — 성공·실패·타임아웃·재생 공격 시나리오

---

## Interaction with Common Rules

- [../../platform/event-driven-policy.md](../../platform/event-driven-policy.md)의 DLQ·retry 정책과 일관성 유지. 내부 이벤트 경로와 외부 연동 경로가 동일 패턴을 따른다.
- [../../platform/observability.md](../../platform/observability.md)에 다음 메트릭 추가: 벤더별 호출 성공률, p95/p99, 재시도 횟수, circuit 상태 변경, DLQ 깊이, webhook 서명 실패 수.
- [../../platform/error-handling.md](../../platform/error-handling.md)에 `EXTERNAL_SERVICE_UNAVAILABLE`, `EXTERNAL_TIMEOUT`, `WEBHOOK_SIGNATURE_INVALID`, `DLQ_RETRY_EXHAUSTED` 등 연동 관련 에러 코드가 존재해야 함.
- [../../platform/security-rules.md](../../platform/security-rules.md)의 비밀 관리 규칙을 따라 벤더 API 키·시크릿을 안전하게 저장한다 (환경변수·Secret Manager 등).

---

## Checklist (Review Gate)

- [ ] 모든 외부 호출에 타임아웃이 명시되어 있는가? (I1)
- [ ] Circuit breaker가 외부 호출을 감싸고 있는가? (I2)
- [ ] 재시도가 지수 백오프 + jitter인가? 4xx 재시도가 없는가? (I3)
- [ ] 외부 쓰기 호출이 idempotent한가? (I4)
- [ ] 비동기 경로에 DLQ가 있고 재처리 수단이 존재하는가? (I5)
- [ ] 들어오는 webhook이 서명·타임스탬프·멱등 검증을 모두 거치는가? (I6)
- [ ] 벤더 SDK가 adapter 레이어에 격리되어 있고 도메인에 직접 유출되지 않는가? (I7)
- [ ] 벤더 응답이 내부 모델로 번역되는가? (I8)
- [ ] 벤더별 스레드 풀·커넥션 풀이 분리되어 있는가? (I9)
- [ ] 통합 테스트가 WireMock 등 fake로 구성되고 실패 시나리오를 커버하는가? (I10)
- [ ] 외부 연동 카탈로그 및 DLQ 재처리 문서가 존재하는가?
- [ ] 벤더 API 키가 안전한 비밀 저장소에 있는가?
