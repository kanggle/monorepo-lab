# gateway-service — Observability

기준: [platform/observability.md](../../../platform/observability.md)

---

## Metrics (Prometheus)

### 필수 메트릭

| 이름 | 타입 | 라벨 | 설명 |
|---|---|---|---|
| `gateway_requests_total` | counter | `method`, `route`, `status` | 전체 요청 수 |
| `gateway_request_duration_seconds` | histogram | `method`, `route` | 요청 처리 시간 (p50/p95/p99) |
| `gateway_jwt_validation_total` | counter | `result` (valid/expired/invalid_signature/missing) | JWT 검증 결과 |
| `gateway_ratelimit_total` | counter | `scope`, `result` (allowed/rejected) | Rate limit 판정 |
| `gateway_ratelimit_rejected_total` | counter | `scope` | 429 응답 수 |
| `gateway_jwks_fetch_total` | counter | `result` (success/failure/cache_hit) | JWKS 페치 결과 |
| `gateway_jwks_cache_age_seconds` | gauge | — | 현재 캐시된 JWKS의 나이 |
| `gateway_upstream_duration_seconds` | histogram | `upstream_service` | 다운스트림 서비스 응답 시간 |
| `gateway_circuit_breaker_state` | gauge | `upstream_service` | 0=closed, 1=half_open, 2=open |

### 비즈니스 메트릭

해당 없음. gateway는 비즈니스 상태를 가지지 않음.

---

## Logs

### 구조화 필드 (JSON, 모든 요청)

| 필드 | 소스 | 예시 |
|---|---|---|
| `traceId` | OTel propagation / 자체 생성 | `abc123def456` |
| `requestId` | `X-Request-ID` 헤더 (없으면 생성) | `req_7f3a...` |
| `method` | HTTP method | `POST` |
| `path` | 요청 URI (쿼리 제외) | `/api/auth/login` |
| `status` | HTTP 응답 상태 | `200` |
| `duration_ms` | 처리 시간 | `42` |
| `client_ip` | 원본 IP (마스킹됨, 마지막 두 옥텟 `*`. canonical: [auth-service device-session.md "IP Masking Format"](../auth-service/device-session.md)) | `192.168.*.*` |
| `upstream` | 라우팅된 다운스트림 서비스 이름 | `auth-service` |

**PII 금지**: User-Agent 전문, 이메일, 토큰 값은 로그에 남기지 않음 ([rules/traits/regulated.md](../../../rules/traits/regulated.md) R4).

---

## Traces (OTel)

| Span 이름 | 설명 |
|---|---|
| `gateway.filter.jwt` | JWT 검증 처리 |
| `gateway.filter.ratelimit` | Rate limit 판정 |
| `gateway.route.{upstream}` | 다운스트림 포워딩 (HTTP client span) |
| `gateway.jwks.fetch` | JWKS 원격 페치 |

**Trace propagation**: 인바운드 `traceparent` 헤더를 존중하고, 없으면 root span 생성. 다운스트림 호출 시 `traceparent` 전파.

---

## Alerts

| 이름 | 조건 | 심각도 | 대응 |
|---|---|---|---|
| `GatewayHighErrorRate` | 5xx 비율 > 5% (5분 윈도우) | critical | 다운스트림 상태 확인 |
| `GatewayJwksFetchFailing` | 연속 3회 JWKS 페치 실패 | warning | auth-service 상태 확인, 캐시 유효 여부 점검 |
| `GatewayRateLimitSpiking` | 429 비율 > 20% (1분 윈도우) | warning | 정상 트래픽 증가 vs 공격 판별 |
| `GatewayCircuitOpen` | 어떤 upstream이든 circuit breaker open | critical | 해당 upstream 서비스 점검 |
| `GatewayP99LatencyHigh` | p99 > 2초 (5분 윈도우) | warning | upstream 지연 또는 Redis 지연 조사 |

---

## Dashboard (Grafana)

게이트웨이 대시보드 권장 패널:

1. **Request Rate** — `gateway_requests_total` rate by route
2. **Error Rate** — 4xx/5xx rate by route
3. **Latency Distribution** — `gateway_request_duration_seconds` p50/p95/p99
4. **JWT Validation** — valid/expired/invalid pie chart
5. **Rate Limit** — allowed vs rejected rate
6. **JWKS Cache Age** — gauge, threshold line at 10분
7. **Circuit Breaker State** — per upstream service
8. **Upstream Latency** — histogram per downstream service
