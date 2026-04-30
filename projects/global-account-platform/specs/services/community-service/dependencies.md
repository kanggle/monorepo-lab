# community-service — Dependencies

## Internal Services

### membership-service (내부 HTTP)
- **목적**: 프리미엄 포스트 (MEMBERS_ONLY) 접근 권한 체크
- **경로**: `/internal/membership/access?accountId=&planLevel=FAN_CLUB`
- **인증**: 내부 서비스 토큰 (mTLS 대체)
- **타임아웃**: 연결 2s / 응답 3s
- **재시도**: 없음 (fail-closed)
- **장애 처리**: 503 / 연결 실패 시 접근 거부(403) — 멤버십 체크 불가 시 프리미엄 콘텐츠 차단
- **Circuit Breaker**: 실패율 50% / 10초 슬라이딩 윈도우 → OPEN → fallback(DENIED)

### account-service (내부 HTTP)
- **목적**: 작성자 표시명 조회 (피드·포스트 응답에 표시)
- **경로**: `/internal/accounts/{accountId}/profile`
- **타임아웃**: 연결 2s / 응답 3s
- **재시도**: 2회 (읽기 전용, 5xx만)
- **캐시**: 표시명은 로컬 캐시 5분 TTL — account-service 부하 감소
- **장애 처리**: 캐시 만료 후 장애 시 `displayName: null` 반환 (포스트 본문은 노출)

## External Services

### 결제 게이트웨이 (Stub)
- **현 단계**: 항상 성공 반환 (Stub 구현). 실제 PG 연동은 향후 과제
- **community-service 직접 의존 없음** — membership-service가 처리

## Failure Mode Handling

| 다운스트림 | 장애 시 UX |
|---|---|
| membership-service 503/504 | 프리미엄 포스트 접근 거부 (fail-closed). 공개 포스트는 정상 서빙 |
| account-service 장애 | 캐시된 표시명 사용. 캐시 없으면 `null`로 응답 (포스트 본문 유지) |
| MySQL 장애 | 전체 서비스 불가. 표준 5xx 응답 |
| Kafka 장애 | 아웃박스 저장 후 발행 지연. 포스트 작업은 정상 완료 (outbox 패턴 보장) |
