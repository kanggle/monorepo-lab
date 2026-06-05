# membership-service — Dependencies

## Internal Services

### account-service (내부 HTTP)
- **목적**: 구독 활성화 전 계정 상태 유효성 확인 (LOCKED/DELETED 계정 구독 거부)
- **경로**: `/internal/accounts/{accountId}/status`
- **인증**: 내부 서비스 토큰 (mTLS 대체)
- **타임아웃**: 연결 2s / 응답 3s
- **재시도**: 2회 (읽기 전용, 5xx만)
- **장애 처리**: fail-closed — 계정 상태 확인 불가 시 구독 활성화 거부 (서비스 보호 우선)
- **Circuit Breaker**: 실패율 50% / 10초 슬라이딩 윈도우 → OPEN → fallback(503 반환)

## External Services

### 결제 게이트웨이 (Stub)
- **현 단계**: `PaymentGatewayStub` — `processPayment()` 항상 `PaymentResult.SUCCESS` 반환
- **인터페이스**: `PaymentGateway` (domain port). Stub은 infrastructure 구현체
- **실제 연동 시**: 인터페이스만 유지하고 구현체 교체

## Failure Mode Handling

| 다운스트림 | 장애 시 UX |
|---|---|
| account-service 장애 | 구독 활성화 거부 (503). 기존 구독 조회/해지는 정상 동작 |
| MySQL 장애 | 전체 서비스 불가. 표준 5xx 응답 |
| Kafka 장애 | 아웃박스 저장 후 발행 지연. 구독 작업은 정상 완료 (outbox 패턴 보장) |
| 스케줄러 지연 | 만료 처리 지연 — 다음 실행 주기에 재처리. 멱등 설계 (ACTIVE 구독만 처리) |
