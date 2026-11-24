# Task ID

TASK-MONO-834

# Title

배포되는 알림 규칙이 조회하는 metric 은 코드나 프레임워크가 실제로 내보내야 한다 — 가드 + wms 의 iam 복사본 알림 파일 교체 + iam 알림식 하나 정정

# Status

review

# Owner

monorepo

# Task Tags

- observability
- ci
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (가드는 기존 가드 본뜨기 · 규칙 파일) · 가드 술어는 Opus 검토
> 기안 2026-11-24 UTC. 출처: `TASK-MONO-833` 작업 중 발견 → 소유자 «알림 규칙 정리 + 가드» → 전제 정정 뒤 «코드에 구현» (iam 은 `TASK-BE-662`).

# Dependency Markers

- 선행: **`TASK-BE-662`**(iam 이 알림이 조회하는 metric 4개를 구현). 662 머지 전에는 이 가드가 iam 에서 빨강이다.

# Goal

`projects/*/infra/prometheus/alert-rules.yml` 은 compose 로 Prometheus 에 실리는 실제 알림이다. 그 `expr` 이 조회하는 metric 이 아무도 내보내지 않는 이름이면 알림은 **no-data 로 영원히 조용하다** — 깨진 알림과 건강한 시스템이 구별되지 않는다. 2026-11-24 UTC 실측(세 프로젝트의 규칙 파일, metric 31개):

- **iam**: 4개 이름이 코드 · `libs/` 에 없다 → `TASK-BE-662` 가 구현.
- **wms**: `alert-rules.yml` 의 알림 목록이 **iam 의 것과 한 줄도 다르지 않다**(`diff` 0) — `Auth*` · `Account*` · `Security*` · `Admin*` 등 wms 에 없는 서비스의 알림뿐이고, wms 서비스 자신의 알림은 **0개**. wms `prometheus.yml` 은 이 파일을 `rule_files` 로 싣고 wms 의 7개 서비스를 긁는다.
- **ecommerce**: 처음 «없음» 으로 잡힌 `event_publish_failure_total` 은 `libs/java-observability` `EventMetricNames` 가 내보낸다(규칙 파일의 주석이 이미 그렇게 적는다) — **측정의 모집단이 `projects/*/apps` 만 봐서 생긴 오탐**. 가드는 `libs/` 를 모집단에 넣는다.

# Scope

## In Scope

1. **가드 `scripts/check-alert-rule-metrics.sh`**:
   - 모집단 = 각 `projects/<p>/infra/prometheus/alert-rules.yml` 의 `expr` 에서 metric 이름. 파서: `{…}` 라벨 셀렉터 · `[…]` 범위 · `by (…)`/`without (…)`/`on (…)`/`ignoring (…)`/`group_left|right (…)` · 문자열 · 숫자를 먼저 지우고, 남은 식별자 중 PromQL 함수 · 연산자 키워드가 아닌 것.
   - 술어 = 이름이 (a) 같은 프로젝트 `projects/<p>/apps/*/src/main/**/*.java` 또는 `libs/*/src/main/**/*.java` 의 **문자열 리터럴**(`.`→`_` 정규화)과 같거나, Prometheus 접미사(`_total` · `_count` · `_sum` · `_bucket` · `_max`, 그 뒤 `_seconds`)를 벗긴 이름이 같다, 또는 (b) **명시된 프레임워크 접두** 목록(`jvm_` · `process_` · `system_` · `http_server_requests_` · `http_client_requests_` · `hikaricp_` · `kafka_consumer_` · `kafka_producer_` · `spring_` · `resilience4j_` · `tomcat_` · `logback_` · `executor_` · `disk_` · `lettuce_` · `up`)으로 시작한다. 목록은 가드 안에 한 곳.
   - 🔵 (a) 는 **느슨하다**(로그 문구 속 같은 문자열도 통과시킨다) — 놓치는 쪽이지 틀리게 무는 쪽이 아니다. 상수로 정의된 이름도 리터럴이므로 잡힌다.
   - 빈 모집단 fail-closed. `--self-test` = 실제 입력 사본 변형: 별도 프로세스 · 기준선 · 규칙에 없는 이름 bite · 라벨 이름(`service`, `job`)은 metric 아님 · `by (…)` 안 식별자 무시 · 프레임워크 접두 통과 · `libs/` 의 이름 통과 · 다른 프로젝트 코드에만 있는 이름 bite · 접미사 정규화.
2. **wms `infra/prometheus/alert-rules.yml` 교체**: iam 복사본을 걷어 내고 wms 의 것으로 —
   - 그대로 유효한 플랫폼 공통 셋(`ServiceDown` · `HighErrorRate5xx` · `HighLatencyP99` — 프레임워크 metric + `service` 라벨, wms `prometheus.yml` 이 `service` 라벨을 붙인다).
   - wms 서비스의 실제 metric 으로: outbox 적체(`*_outbox_pending_count > 100`, 기준 = wms 스펙 «alerts at pending.count > 100» — `notification-service/external-integrations.md:333` 등) · outbox 발행 실패(`admin_outbox_publish_failure_total` · `notification_outbox_publish_failure_total` 의 지속 증가, `TASK-MONO-833` 이 문서화한 «alert on a sustained non-zero rate»).
   - wms 게이트웨이는 rate-limit 거부 counter 를 내보내지 않는다(`gateway_ratelimit_rejected_total` 은 iam 게이트웨이 코드에만) → 그 규칙은 옮기지 않는다.
3. **iam 알림식 정정 한 줄**: `GatewayJwtFailureSpike` 의 `{result="failure"}` → `{result!="valid"}`(스펙의 값은 valid/expired/invalid_signature/missing 이고 `failure` 는 없다 — `TASK-BE-662` 가 스펙대로 구현).
4. `ci.yml` 잡 + `changes` 필터(`projects/*/infra/prometheus/**` · `projects/*/apps/*/src/main/**/*.java` · `libs/*/src/main/**/*.java` · 가드 자신 — pure-positive).
5. `scripts/` 70 → 71(+ 가드가 `git ls-files` 를 쓰면 독자 수도): 가드 **전부** 재실행 + 산문 집.

## Out of Scope

- 알림의 라벨 셀렉터 값(`{service="…"}`)이 실제 시리즈 라벨과 맞는지 — 이름만 본다.
- `docs/runbooks/*.md` 링크(규칙 annotations 가 가리키지만 iam 에는 그 디렉터리가 없다) — 별개.
- 스펙에 있고 알림이 조회하지 않는 미구현 metric — 기록만(«남은 것»).

# Acceptance Criteria

- [x] AC-0: 착수 직전 — `TASK-BE-662` 가 머지됐는가. 아니면 STOP. 그리고 가드 정규식으로 다시 센 «없는 metric» 이 iam 0 · wms 교체 대상 · ecommerce 0 인가.
- [x] AC-1: wms 규칙 파일이 wms 서비스의 metric 만 조회하고, 각 새 규칙의 임계값에 스펙 근거가 주석으로 붙는다. `promtool check rules` 통과(로컬에 없으면 CI 에서 — 없으면 YAML 파싱만이라고 적는다).
- [x] AC-2: 가드 rc=0 · `--self-test` 전부 PASS.
- [x] AC-3: bite — 사본에서 규칙에 없는 이름을 넣으면 rc=1 + 프로젝트 · 알림 · 이름 출력, 복원 → rc=0.
- [x] AC-4: 가드 전부(스테이지 뒤) · `check-ls-files-guard-count.sh` rc=0.
- [ ] AC-5: PR CI 에서 새 잡이 **실행되어** success. ecommerce `alert-rules.test.yml`(promtool 테스트)이 CI 에 있으면 그것도 초록.

# Related Specs

- `platform/observability.md` § Alerting Rules
- wms 각 서비스 스펙의 outbox 알림 기준 · `projects/iam-platform/specs/services/*/observability.md` § Alerts

# Related Contracts

- 없음.

# Edge Cases

- `{__name__=~"…"}` 로 이름을 정규식으로 고르는 식 — 이름이 안 보이므로 가드가 검사할 수 없다. 새 wms 규칙은 이름을 **명시적으로** 쓴다(`or` 로 나열).
- 녹화 규칙(`record:`)의 이름을 다른 식이 조회하면 — 지금 0개. 생기면 그 이름도 «정의됨» 으로 쳐야 한다(가드가 `record:` 를 모집단 (a) 에 더한다).

# Failure Scenarios

- 파서가 라벨 이름을 metric 으로 읽음 → 첫날 빨강(`service` · `job` · `le` 가 실측에서 그렇게 잡혔다). self-test 가 문다.
- 프레임워크 접두 목록이 넓어 오타 metric 을 통과시킴(`http_server_requests_seconds_cout`) — 알려진 한계. 목록을 접두 «단어» 단위로 좁게 유지.

# Test Requirements

- 가드 self-test · bite · 가드 전수 · CI(+ promtool 테스트가 있으면).

# Implementation Evidence (2026-11-24 UTC)

- **AC-0** — `TASK-BE-662` 머지(#4669, `08bb2e01f`) 확인 뒤 `origin/main` 위로 재적용. 가드를 처음(662 · 규칙 수정 **전**) 돌린 결과 rc=1, **17줄**: iam 6(662 의 4 이름 × 5 알림 + 🆕 `AccountDbSlowQueries` → `hibernate_query_execution_seconds_bucket`) · wms 11(iam 복사본 전부 + 같은 `hibernate_*`). ecommerce 0(`event_publish_failure_total` 은 `libs/java-observability` — 모집단에 `libs/` 를 넣은 근거 그대로). 🔴 티켓이 몰랐던 하나: **`AccountDbSlowQueries`** — Hibernate statistics 를 켠 iam 서비스가 없고 스펙에도 없다 → 내보내는 것 없음.
- **AC-1** — wms `alert-rules.yml` 교체: 플랫폼 공통 3(`ServiceDown` · `HighErrorRate5xx` · `HighLatencyP99`) + `WmsOutboxBacklogHigh`(6 서비스 `*_outbox_pending_count > 100`, 근거 주석 = `notification-service/external-integrations.md` 등 «alerts at pending.count > 100») + `WmsOutboxPublishFailing`(admin · notification 발행 실패의 지속 — `TASK-MONO-833` 이 적은 «sustained non-zero rate»). `GatewayRateLimitExceeded` 는 옮기지 않음(wms 게이트웨이는 그 counter 가 없다, 주석). iam: `GatewayJwtFailureSpike` `{result="failure"}` → `sum(…{result!="valid"})` · `AccountDbSlowQueries` 제거(이유 주석). **`promtool check rules`(prom/prometheus 도커 이미지)**: wms 5 rules · iam 14 rules SUCCESS — 쓰고 나서 이미지 삭제.
- **AC-2** — 662 위에서 가드 rc=0(«54 metric reference(s) in 3 rules file(s)») · `--self-test` **13/13**(별도 프로세스 · 기준선 · 모르는 이름 bite · `{…}`·`by (…)` 안 라벨 무시 · 프레임워크 접두·`up` · 오타 프레임워크 이름 bite · `.`→`_` · 타이머 `_seconds_bucket` · `libs/` 이름 · 다른 프로젝트에만 있는 이름 bite · `record:` 이름 · `expr: |` 블록 안 bite · 규칙 파일 없음 fail-closed). 사본의 코드 쪽은 실제 소스 · libs 의 문자열 리터럴을 **뽑아** 프로젝트당 한 파일로(수천 파일 복사 대신).
- **AC-3** — bite: AC-0 의 실제 17줄(프로젝트 · 알림 · 이름) 이 실물이고, self-test 의 «unknown metric bites» 가 사본에서 같은 것을 증명.
- **AC-4** — 스테이지 뒤 `check-*.sh` **43개**: 41 rc=0, 비0 둘은 환경 전제(erp 래칫 · prerendered verdict — 830·832·833 과 같음). `.mjs` 6/6. `check-ls-files-guard-count.sh` — 새 가드가 `git ls-files` 를 읽어 30/71 → 두 집 네 곳 갱신 → rc=0.
- **AC-5** — PR CI 대기. ecommerce `alert-rules.test.yml`(promtool 단위 테스트)은 ci.yml 에서 도는 곳이 없다(검색 0) → 해당 없음으로 기록.
- 🔵 **남은 것(티켓 없음)**: ① auth `OutboxLagMetric` 이 v1 빈 `outbox` 를 읽어 `AuthOutboxLagHigh` 가 사실상 울리지 않는다(+ `NOW()` 시간대) — 이름은 있으므로 이 가드 밖, **값** 의 결함(`TASK-BE-662` 증거). ② iam security-service 스펙의 «필수 메트릭» 표 원래 11개 중 지금도 코드에 없는 **8개**(`security_events_consumed_total` 등 — 11 에서 원래 있던 `security_dlq_depth` · 662 가 구현한 `security_auto_lock_issued_total` · 이름만 다른 `security_consumer_lag`(코드는 `kafka.consumer.lag`)를 뺀 수). 알림은 이 8개를 조회하지 않는다. ③ 알림 annotations 의 `docs/runbooks/*.md` 는 iam · wms 에 디렉터리가 없다.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
