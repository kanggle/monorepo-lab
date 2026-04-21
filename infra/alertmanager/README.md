# AlertManager 설정

## Slack Webhook 설정

실제 운영 환경에서는 Slack Webhook URL이 secret이므로 저장소에 커밋하면 안 됩니다.

### 로컬 개발

```bash
# 1. Slack Incoming Webhook 생성 (https://api.slack.com/messaging/webhooks)
# 2. 로컬 webhook URL 파일 생성
cp infra/alertmanager/slack-webhook-url.example infra/alertmanager/slack-webhook-url
# 3. 파일을 열어 실제 Webhook URL로 교체
#    (이 파일은 .gitignore에 의해 커밋되지 않음)
```

### 운영 환경

K8s Secret 또는 HashiCorp Vault 등 시크릿 관리 도구로 webhook URL을 주입하고,
`alertmanager.yml`의 `slack_api_url_file` 경로에 마운트합니다.

## 알림 채널 구조

| Severity | Channel | 용도 |
|----------|---------|------|
| default  | `#alerts`           | 기본 알림 (분류되지 않은 것) |
| warning  | `#alerts-warning`   | 경고 수준 — 10% 실패율, P95 1s 초과 등 |
| critical | `#alerts-critical`  | 즉시 대응 필요 — 서비스 다운, 30% 실패율 등 |

## Alert Rules

`infra/prometheus/alert-rules.yml`에 6개 카테고리로 정의:

1. **high_error_rate** — 5xx 비율 1%(WARN) / 5%(CRIT)
2. **slow_response_time** — P95 1s, P99 2s(WARN) / 5s(CRIT)
3. **service_down** — `up == 0` 또는 헬스체크 실패
4. **kafka_consumer_lag** — lag 10k(WARN) / 50k(CRIT)
5. **db_connection_pool** — 80%(WARN) / 95%(CRIT) 사용률
6. **notification_delivery** — 알림 발송 실패율 10%(WARN) / 30%(CRIT)

## 검증

```bash
# 설정 문법 검증
docker run --rm -v $(pwd)/infra/alertmanager:/etc/alertmanager \
  prom/alertmanager:v0.27.0 amtool check-config /etc/alertmanager/alertmanager.yml

docker run --rm -v $(pwd)/infra/prometheus:/etc/prometheus \
  prom/prometheus:v2.54.1 promtool check rules /etc/prometheus/alert-rules.yml

# 런타임 receiver 상태 확인
curl http://localhost:9094/api/v2/status | jq '.config.original'
```
