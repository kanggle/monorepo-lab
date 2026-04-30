# Internal HTTP Contract: gateway-service → auth-service

gateway-service가 JWT 검증을 위해 auth-service에서 JWKS(JSON Web Key Set)를 주기적으로 페치한다.

**호출 방향**: gateway-service (client) → auth-service (server)
**노출 경로**: `/internal/auth/jwks`
**인증**: mTLS 또는 내부 네트워크 (JWKS는 공개 키이므로 인증 없이도 안전하나, 내부 경로 격리 원칙상 내부 전용)

---

## GET /internal/auth/jwks

현재 활성 서명 키의 JWKS 반환. 게이트웨이는 이 응답을 Redis에 캐시하고 JWT `kid` 헤더와 매칭하여 서명을 검증한다.

**Response 200**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-2026-04-01",
      "use": "sig",
      "alg": "RS256",
      "n": "string (base64url-encoded modulus)",
      "e": "AQAB"
    }
  ]
}
```

**Response 503**: auth-service가 서명 키를 아직 초기화하지 않음 (부팅 중)
```json
{
  "code": "SERVICE_NOT_READY",
  "message": "Signing keys not yet initialized",
  "timestamp": "2026-04-12T10:00:00Z"
}
```

---

## Key Rotation Protocol

1. auth-service가 새 키를 생성하면 **기존 키 + 새 키** 모두 JWKS에 포함 (grace period)
2. 새 키로 발급된 토큰에는 새 `kid`가 설정됨
3. 게이트웨이는 `kid` 매칭 실패 시 즉시 JWKS 리페치 (캐시 무효화)
4. 기존 키는 발급된 모든 access token이 만료된 후 (최대 30분) JWKS에서 제거
5. **롤백 시나리오**: 새 키에 문제가 있으면 auth-service가 JWKS에서 새 키를 제거하고 기존 키로 돌아감. 게이트웨이는 다음 페치 주기에 자동 반영

---

## Caller Constraints (gateway-service 측)

- 페치 주기: **10분** (고정 스케줄)
- 캐시: Redis `jwks:cache` (TTL 600초)
- `kid` miss 시: 즉시 리페치 (TTL 무시), 실패 시 이전 캐시 유지
- 타임아웃: 연결 3s, 읽기 5s
- 재시도: 2회 (지수 백오프)
- auth-service 장애 시: 이전 캐시된 JWKS로 **최대 5분** 추가 검증 (in-memory grace). 이후에도 복구 안 되면 `GatewayJwksFetchFailing` 알림 + 새 토큰 검증 실패

---

## Server Constraints (auth-service 측)

- JWKS 엔드포인트는 **항상 최신 활성 키 세트**를 반환. 캐시 없음 (auth-service 내부에서 키 스토어 직접 조회)
- 응답은 경량 JSON (키 수: 보통 1~2개). 페이로드 크기 1KB 미만
- 이 엔드포인트는 rate limit 대상이 아님 (내부 전용 + 호출 빈도 낮음)
