# admin-web — Observability

## Web Vitals

`web-vitals` 패키지로 측정 후 `/api/web-vitals` route handler로 전송. Next.js route handler가 Prometheus 메트릭을 백엔드 수집 서비스(또는 admin-service의 dedicated 엔드포인트)로 릴레이.

측정 지표:
- **LCP** (Largest Contentful Paint) — 목표 p95 < 2.5s
- **INP** (Interaction to Next Paint) — 목표 p95 < 200ms
- **CLS** (Cumulative Layout Shift) — 목표 p95 < 0.1
- **TTFB** (Time to First Byte) — 목표 p95 < 800ms

## Client Error Capture

- **Sentry** (또는 equivalent) — DSN을 런타임 env로 주입
- 운영 환경에서만 활성화 (`NODE_ENV === 'production'`)
- PII 필터: 이메일·전화·operator name 마스킹
- Source map 업로드는 CI에서 수행

## Structured Logging (server-side)

Next.js route handler 및 server component의 로그는 JSON 구조화:
```json
{
  "timestamp": "2026-04-13T12:34:56.789Z",
  "level": "info",
  "message": "...",
  "requestId": "...",
  "operatorId": "op-001",
  "route": "/accounts/acc-abc"
}
```

- `requestId`: incoming 요청의 `x-request-id` 헤더 또는 UUID 생성
- `operatorId`: 쿠키에서 추출한 sub claim
- `route`: 현재 Next.js 라우트

## Business Metrics

| Metric | Type | Labels | 용도 |
|---|---|---|---|
| `admin_web_page_view_total` | counter | `route`, `role` | 페이지별 방문 |
| `admin_web_command_issued_total` | counter | `command`, `outcome` | UI에서 발급한 명령 수 (서버 기록과 교차 검증) |
| `admin_web_login_total` | counter | `result` | 로그인 시도/성공/실패 |
| `admin_web_api_error_total` | counter | `endpoint`, `code` | 클라이언트가 받은 에러 코드 분포 |
| `admin_web_web_vitals` | histogram | `metric` (`lcp`/`inp`/`cls`/`ttfb`), `route` | 사용자 경험 |

지표는 `/api/web-vitals`, `/api/metrics`(옵션) route handler에서 Prometheus text format으로 노출하거나, backend collector로 전송.

## Alerts

| Alert | Expression | Severity | Runbook |
|---|---|---|---|
| `AdminWebLCPHigh` | `histogram_quantile(0.95, admin_web_web_vitals_bucket{metric="lcp"}) > 4` | warning | `docs/runbooks/admin-web-lcp.md` |
| `AdminWebClientErrorSpike` | `rate(admin_web_api_error_total{code=~"5.."}[5m]) > 0.1` | critical | `docs/runbooks/admin-web-errors.md` |
| `AdminWebLoginFailureHigh` | `rate(admin_web_login_total{result="failure"}[10m]) > 0.2` | warning | `docs/runbooks/admin-web-login.md` |

## Dashboards

`infra/grafana/dashboards/admin-web-overview.json` — 패널:
1. Page views by route
2. Active operators (last 5m)
3. Command issue rate (by command, outcome)
4. API error rate (by endpoint)
5. LCP / INP / CLS p95
6. Login success rate
7. Top slow routes (p99)
8. Client error breakdown (by code)

## Observability in CI

- **Lighthouse** a11y score >= 90 — CI 실패 조건
- **next-bundle-analyzer** — 번들 예산 회귀 시 CI 실패
- Playwright E2E 결과가 Grafana로 푸시 (옵션)

## Privacy

- operator_id는 로그·메트릭에서 허용 (운영자 식별은 감사 대상)
- **최종 사용자 PII는 프론트엔드 로그/메트릭에 기록 금지** — 에러 메시지에 이메일 등장 시 Sentry가 자동 마스킹
- 쿼리 파라미터는 로깅 전 sanitize (`?email=xxx@y.com` → `?email=<redacted>`)
