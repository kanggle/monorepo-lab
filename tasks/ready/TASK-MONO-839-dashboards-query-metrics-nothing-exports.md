# Task ID

TASK-MONO-839

# Title

Grafana 대시보드 패널이 아무도 내보내지 않는 지표를 묻는다(iam 20 · ecommerce 1) — 있는 이름으로 고치고, 스펙에도 없는 패널은 지우고, 소유자 결정 대기 이름은 프로젝트별 «대기 목록» 에 적고, 알림 가드가 대시보드도 보게 한다

# Status

ready

# Owner

monorepo

# Task Tags

- observability
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (대시보드 텍스트 편집 + 기존 가드 확장)
> 기안 2026-11-25 UTC. 출처: `TASK-BE-666` · `TASK-MONO-838` 의 «결정 없이 할 수 있는 남은 결함 ①» → 소유자 «진행».

# Dependency Markers

- 선행: `TASK-MONO-834`(알림 → 코드 가드 `check-alert-rule-metrics.sh` — 이 티켓이 같은 술어를 대시보드로 넓힌다) · `TASK-MONO-837`(스펙 → 코드 분류, D1 = «미구현 + 대시보드가 묻는다» 6개 — 소유자 결정 대기) · `TASK-BE-666`(wms 대시보드 재작성) · `TASK-MONO-838`(datasource uid 가드).
- 🔶 **소유자 결정과의 경계**: 837 의 D1 · D2 처리(구현 / «계획» 표시 / 삭제)는 아직 정해지지 않았다. 이 티켓은 그 결정을 **앞당기지 않는다** — 스펙이 선언한 이름을 묻는 패널은 지우지 않고 «대기 목록» 에 둔다.

# Goal

2026-11-25 UTC 실측 — 대시보드 `expr` 의 지표 이름을 `check-alert-rule-metrics.sh` 와 **같은 술어**(정확 이름 = 아무 코드 리터럴 · 접미사 뗀 이름 = meter 등록에 넘어간 이름 · 프레임워크 접두사 · record 규칙)로 대조:

| 프로젝트 | 참조 | 없는 이름 |
|---|---:|---:|
| iam | 90 | 20 (5개 대시보드) |
| ecommerce | 68 | 1 (`payment_failed_total`, 837 D1) |
| wms | 90 | 1 — **오탐**: `inventory_optimistic_lock_retry_count_total` 은 실제 scrape 로 존재(`TASK-BE-666`). 코드 이름 `inventory.optimistic-lock.retry.count` 의 `-` 를 술어가 `_` 로 정규화하지 않는다 — **알림 가드에도 같은 구멍** |

iam 20개의 성격:

- **837 D1(결정 대기) 5**: `account_signup_total` · `admin_audit_write_total` · `admin_audit_query_total` · `auth_token_issued_total` · `auth_token_refresh_total`.
- **프레임워크가 이미 냄 1**: `auth_circuit_breaker_state` → `resilience4j_circuitbreaker_state{name="accountService"}`(`TASK-BE-665` 실측).
- **스펙 이름과 다름(스펙 이름도 미구현 — 837 D2) 7 → 6개 스펙 이름**: `account_state_transition_total` → `account_status_transition_total` · `account_active_count` → `account_total_by_status` · `security_event_processing_seconds_bucket` → `security_event_processing_duration_seconds_bucket` · `security_risk_score_bucket` → `security_risk_score_distribution_bucket` · `security_suspicious_events_total` · `security_rules_triggered_total` → `security_suspicious_detected_total{rule_code}` · (`admin_downstream_duration_seconds_bucket` 은 이미 스펙 이름).
- **스펙에도 코드에도 없음 7 (패널 5)**: `account_profile_reads_total` · `hibernate_query_execution_seconds_bucket`(`TASK-MONO-834` 가 같은 이유로 알림에서 지웠다) · `gateway_jwks_cache_hits_total` · `gateway_jwks_cache_misses_total` · `security_login_outcome_total` · `security_redis_hits_total` · `security_redis_misses_total`.

# Scope

## In Scope

1. iam `auth-overview.json` 차단기 패널 → `resilience4j_circuitbreaker_state{service="auth-service",name="accountService"}`(state 별).
2. iam 패널의 이름을 스펙 이름으로(위 6) — 라벨도 스펙 라벨로(`by (from,to)` 유지 · `by (rule_code)` 등).
3. 스펙에도 코드에도 없는 이름만 묻는 iam 패널 5개 삭제(`Profile Reads` · `DB Query Latency p99` · `JWKS Cache Hit Rate` · `Login Outcomes` · `Redis Miss Rate`). 텍스트 단위로 그 패널 블록만 지운다(손으로 쓴 압축 JSON 형식을 다시 직렬화하지 않는다).
4. **대기 목록** `projects/<p>/infra/grafana/pending-metrics.txt`(iam · ecommerce): 한 줄에 `<prometheus 이름>  # <스펙 위치> — <대기 이유>`. 들어갈 것 = 스펙이 선언했고 대시보드가 묻지만 아직 구현 안 된 이름(iam 11 · ecommerce 1).
5. **가드 확장** `scripts/check-alert-rule-metrics.sh`(새 스크립트 아님 — 같은 술어를 두 벌로 두지 않는다):
   - 대시보드 `projects/*/infra/grafana/dashboards/**/*.json` 의 모든 `expr` 을 같은 파서로(JSON 문자열을 `node` 로 꺼낸다 — 이스케이프된 `\"` 를 푼 원문).
   - 없는 이름이 그 프로젝트의 대기 목록에 있으면 통과(개수 보고). **대기 목록 항목이 이제 내보내지면 실패**(면제 회수) · **어느 대시보드도 안 묻는 항목도 실패**(낡은 면제).
   - 정규화에 `-` → `_` 추가(리터럴 · 등록 이름 둘 다) — wms 오탐 제거.
   - `--self-test` 에 새 case: 대시보드의 없는 이름 bite · 이스케이프된 라벨 값 안의 이름은 무시 · 대기 목록 통과 · 대기 항목이 내보내지면 bite · 안 묻는 대기 항목 bite · `-` 가 든 등록 이름 매치.
   - CI 잡 paths 에 `projects/*/infra/grafana/dashboards/**` · `projects/*/infra/grafana/pending-metrics.txt`.

## Out of Scope

- 837 D1 · D2 의 구현 · 스펙 수정(소유자 결정).
- wms `admin.query.cache.hit.rate` 상수 gauge(별 결함).

# Acceptance Criteria

- [ ] AC-1: 고치기 전 트리에서 확장 가드 rc=1 · iam 20 · ecommerce 1 · wms 0(`-` 정규화 뒤). 고친 뒤 rc=0, 대기 목록 통과 수 = iam 11 · ecommerce 1.
- [ ] AC-2: `--self-test` 전부 PASS(기존 18 + 새 case).
- [ ] AC-3: 고친 iam 대시보드 5개가 Grafana 11.2.0 에 오류 없이 프로비저닝된다.
- [ ] AC-4: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 에서 `Alert rule metrics …` 잡이 **실행되어** success.

# Related Specs

- iam `specs/services/{account,admin,auth,gateway,security}-service/observability.md`
- ecommerce `specs/services/payment-service/observability.md`

# Related Contracts

- 없음.

# Edge Cases

- 한 패널이 대기 이름과 없는 이름을 같이 묻는다 → 없는 이름 쪽이 판정을 정한다(지우거나 고친다).
- `expr` 이 템플릿 변수(`$job`)를 쓴다 → 라벨 값이라 이름으로 안 잡힌다(파서가 `{…}` 를 지운다).

# Failure Scenarios

- JSON 의 `\"` 를 안 풀고 파싱 → 라벨 값이 이름으로 새어 거짓 빨강. self-test 가 잡는다.
- 대기 목록이 «결함을 숨기는 곳» 이 된다 → 내보내지면 실패 · 안 쓰이면 실패로 목록이 줄기만 하게.

# Test Requirements

- 고치기 전 bite · self-test · Grafana 프로비저닝 · 가드 전수 · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
