# Task ID

TASK-MONO-839

# Title

Grafana 대시보드 패널이 아무도 내보내지 않는 지표를 묻는다(iam 20 · ecommerce 1) — 있는 이름으로 고치고, 스펙에도 없는 패널은 지우고, 소유자 결정 대기 이름은 프로젝트별 «대기 목록» 에 적고, 알림 가드가 대시보드도 보게 한다

# Status

done

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

- [x] AC-1: 고치기 전 트리에서 확장 가드 rc=1 · iam 20 · ecommerce 1 · wms 0(`-` 정규화 뒤). 고친 뒤 rc=0, 대기 목록 통과 수 = iam 11 · ecommerce 1.
- [x] AC-2: `--self-test` 전부 PASS(기존 18 + 새 case).
- [x] AC-3: 고친 iam 대시보드 5개가 Grafana 11.2.0 에 오류 없이 프로비저닝된다.
- [x] AC-4: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 에서 `Alert rule metrics …` 잡이 **실행되어** success.

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

# Implementation Evidence (2026-11-25 UTC)

- **변경**
  - `scripts/check-alert-rule-metrics.sh` 확장(새 스크립트 아님 → `scripts/` 수 · 가드 수 산문 불변): 대시보드 `expr` 을 node 로 꺼내 **같은 파서(`parse_rules`) · 같은 술어(`exported`)** 로. 프로젝트당 node 1회(파일당이면 self-test 가 25배로 느려진다). `-` → `_` 정규화(리터럴 · 등록 이름). `FRAMEWORK_EXACT` 에 `ALERTS` · `ALERTS_FOR_STATE`(Prometheus 가 자기 규칙에서 만드는 시리즈 — 모든 `alerts.json` 이 묻는다). 대기 목록 판정 셋: 통과 · 내보내지면 실패 · 안 쓰이면 실패. 알림 규칙에는 대기 목록이 **적용되지 않는다**. CI: paths 에 대시보드 · 대기 목록, `timeout-minutes` 5 → 10.
  - iam 대시보드 4개: `auth-overview` 차단기 패널 → `resilience4j_circuitbreaker_state{service="auth-service",name="accountService"}`(legend `{{state}}`) · 스펙 이름으로 6개(`account_status_transition_total` · `account_total_by_status` · `security_suspicious_detected_total` ×2(`by (rule_code)`, legend `{{rule_code}}`) · `security_risk_score_distribution_bucket` · `security_event_processing_duration_seconds_bucket`) · 패널 5개 삭제(`account-overview` id 2 «Profile Reads» · id 5 «DB Query Latency p99» / `gateway-overview` id 6 «JWKS Cache Hit Rate» / `security-overview` id 8 «Login Outcomes» · id 9 «Redis Miss Rate»). 손으로 쓴 압축 JSON 은 다시 직렬화하지 않고 **텍스트 단위**로 그 패널 블록만 지웠다(수정 뒤 `json.loads` 로 유효성 확인).
  - 대기 목록: `projects/iam-platform/infra/grafana/pending-metrics.txt` 11 · `projects/ecommerce-microservices-platform/infra/grafana/pending-metrics.txt` 1 — 줄마다 스펙 위치 · 837 범주(D1/D2).
- **AC-1** — 확장 가드를 고치기 **전** 트리에 돌린 결과: rc=1, `ALERTS` 3줄(위 FRAMEWORK_EXACT 로 해소) 외에 **iam 20 · ecommerce 1 · wms 0**(기안의 사전 측정 — 같은 술어를 `-` 정규화 없이 파이썬으로 — 에서는 wms 1: `inventory_optimistic_lock_retry_count_total`, `TASK-BE-666` 의 실제 scrape 로 존재가 확인된 이름. 가드에 정규화를 넣은 뒤 돌렸으므로 가드 출력에는 처음부터 없다). 고친 뒤 rc=0: «55 metric reference(s) in 3 rules file(s) … 20 runbook link(s) …; **239** reference(s) in **20** dashboard(s), all exported except **12** listed as pending»(iam 11 + ecommerce 1).
- **AC-2** — `--self-test` **25/25**(기존 18 + 새 7: 대시보드의 없는 이름 bite · 이스케이프된 라벨 값은 이름 아님 · 대기 목록 통과 · 내보내진 대기 항목 bite · 안 쓰이는 대기 항목 bite · 대기 목록은 알림을 면제하지 않음 · `-` 가 든 등록 이름 매치). 기준 복사본에 실제 대시보드 · 대기 목록 · **실제 등록 이름 파일**(`RealRegistrations.java` — 접미사 뗀 이름이 복사본에서도 풀리게)을 넣어 «unmutated copy passes» 가 실제 트리와 같은 판정을 한다. 로컬(msys) 8분 6초 · 같은 잡의 CI 실적 10~14초.
- **AC-3** — `grafana/grafana:11.2.0` 에 iam `provisioning` · `dashboards` 마운트: 대시보드 **9개** 프로비저닝, 패널 수 account 7→5 · security 10→8 · gateway 8→7 · auth 8(그대로), 대시보드 · datasource 관련 error 0. 컨테이너 · 이미지 삭제.
- **AC-4** — 스테이지 뒤 `check-*.sh` 43: 41 rc=0, 비0 둘 = 환경 전제(erp 래칫 · prerendered verdict). `.mjs` 7/7. PR CI(#4698): 첫 실행 실패 1 = `Integration (ecommerce B …)` — `compileJava` 의 Maven Central 의존성 다운로드 실패(`Could not GET … springdoc-openapi-starter-webmvc-ui-2.7.0`, 단언 전 · 이 PR 은 ecommerce 코드를 안 건드림) → `gh run rerun --failed` 로 그 잡 7m40s pass. 최종 실패 0 · SUCCESS 42. `Alert rule metrics …` **실행되어** SUCCESS(27s, 834 시점 10~14s).

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
