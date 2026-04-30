# community-service — Observability

## Metrics (Prometheus / Micrometer)

| 메트릭 | 타입 | 레이블 | 용도 |
|---|---|---|---|
| `community_post_published_total` | counter | `type` (ARTIST_POST/FAN_POST), `visibility` | 포스트 발행 수 |
| `community_comment_created_total` | counter | — | 댓글 생성 수 |
| `community_reaction_added_total` | counter | `emoji_code` | 반응 추가 수 |
| `community_feed_request_seconds` | histogram | `page_size` | 피드 조회 latency |
| `community_access_check_total` | counter | `result` (ALLOWED/DENIED/ERROR) | 멤버십 접근 체크 결과 |
| `community_access_check_seconds` | histogram | — | membership-service 호출 latency |
| `community_post_status_transition_total` | counter | `from_status`, `to_status` | 포스트 상태 전이 수 |

엔드포인트: `/actuator/prometheus`

## Structured Logging

```json
{
  "timestamp": "2026-04-13T12:34:56.789Z",
  "level": "info",
  "message": "...",
  "traceId": "...",
  "spanId": "...",
  "requestId": "req-uuid",
  "accountId": "acc-uuid",
  "postId": "post-uuid",
  "service": "community-service"
}
```

- `accountId`: JWT sub claim
- `postId`: 포스트 관련 요청의 경우
- **PII 금지**: 포스트 본문·댓글 내용을 로그에 기록하지 않음

## Alerts

| Alert | Expression | Severity | Runbook |
|---|---|---|---|
| `CommunityAccessCheckErrorHigh` | `rate(community_access_check_total{result="ERROR"}[5m]) > 0.1` | critical | `docs/runbooks/community-access-check.md` |
| `CommunityFeedLatencyHigh` | `histogram_quantile(0.95, community_feed_request_seconds_bucket) > 1` | warning | `docs/runbooks/community-feed.md` |

## Health Checks

`/actuator/health` — DB 연결, outbox 지연 (표준 actuator)

## Dashboards

`infra/grafana/dashboards/community-overview.json` — 패널:
1. 포스트 발행 수 (type별)
2. 댓글·반응 수
3. 피드 조회 p95 latency
4. 멤버십 접근 체크 성공/실패 비율
5. 포스트 상태 전이 분포
