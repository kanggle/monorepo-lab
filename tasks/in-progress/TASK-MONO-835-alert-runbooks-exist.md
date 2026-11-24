# Task ID

TASK-MONO-835

# Title

배포되는 알림의 `runbook:` 링크 17개가 전부 없는 파일을 가리킨다 — iam 14 · wms 5(새 알림 둘 포함) runbook 을 쓰고, 링크가 실제 파일을 가리키는지 가드가 본다

# Status

in-progress

# Owner

monorepo

# Task Tags

- docs
- observability
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (runbook 작성 — 코드 · 스펙 · compose 를 읽고) · 가드 확장은 Opus 검토
> 기안 2026-11-24 UTC. 출처: `TASK-MONO-834` 증거 «남은 것 ③» → 소유자 «진행»(권장안 = 전부 작성).

# Dependency Markers

- 선행: `TASK-MONO-834` · `TASK-BE-662` · `TASK-BE-663`(done) — 알림이 이제 실제로 울린다. 울린 다음 읽을 문서가 이 티켓.

# Goal

2026-11-24 UTC 실측: `projects/iam-platform/infra/prometheus/alert-rules.yml` 의 `runbook:` 14개, `projects/wms-platform/…` 3개 — **17개 전부** 가 존재하지 않는 `docs/runbooks/*.md` 를 가리킨다(두 프로젝트에 그 디렉터리가 없다). wms 의 새 알림 둘(`WmsOutboxBacklogHigh` · `WmsOutboxPublishFailing`, `TASK-MONO-834`)은 링크조차 없다. 알림을 받은 사람이 링크를 열면 404 다.

ecommerce 는 `knowledge/runbooks/`(README 색인 + 「새벽 3시에 쓴 사람이 아닌 사람이 읽는다 — 모든 명령은 그대로 복사해 실행 가능」 작성 규칙, `TASK-BE-533`)를 갖고 있다. 그 관례를 따른다.

# Scope

## In Scope

1. **iam `projects/iam-platform/knowledge/runbooks/`** — 알림 14개당 하나(파일 이름은 기존 링크의 basename 유지: `service-down.md` · `high-error-rate-5xx.md` · `high-latency-p99.md` · `gateway-jwt-failure-spike.md` · `gateway-rate-limit-exceeded.md` · `auth-login-failure-spike.md` · `auth-refresh-token-reuse.md` · `auth-outbox-lag-high.md` · `account-outbox-lag-high.md` · `security-consumer-lag-high.md` · `security-dlq-depth-high.md` · `security-auto-lock-spike.md` · `admin-action-spike.md` · `admin-command-failure-high.md`) + `README.md` 색인(ecommerce 형식: Runbook | Triggered by | Covers + Writing rule + Priority).
2. **wms `projects/wms-platform/knowledge/runbooks/`** — `service-down.md` · `high-error-rate-5xx.md` · `high-latency-p99.md` · `wms-outbox-backlog-high.md` · `wms-outbox-publish-failing.md` + `README.md`. wms 에 이미 있는 `specs/services/*/runbooks/*`(예: notification `dlt-replay.md`)는 **링크로 가리키고 내용을 복제하지 않는다**.
3. 각 runbook 의 내용(코드 · 스펙 · compose 에서 **확인한 것만**): ① 이 알림이 뜻하는 것(조회하는 metric 과 그 metric 을 내보내는 클래스) ② 먼저 볼 것 — 그대로 실행 가능한 명령(로컬 `docker-compose.yml` 기준 컨테이너 이름 · Prometheus 질의 · 로그 위치) ③ 흔한 원인과 각각의 확인법 ④ 복구 · 완화 ⑤ 에스컬레이션. 모르는 것은 «확인 안 됨» 으로 적는다 — 지어내지 않는다.
4. **알림 링크 갱신**: 두 규칙 파일의 `runbook:` 을 `knowledge/runbooks/<name>.md` 로(경로 기준 = 프로젝트 루트, ecommerce 의 `description` 이 쓰는 형식과 같음). wms 새 알림 둘에 `runbook:` 추가.
5. **가드 확장** `scripts/check-alert-rule-metrics.sh`: 규칙의 `runbook:` 값이 있으면 `projects/<p>/<그 경로>` 가 추적되는 파일이어야 한다. 실패 메시지에 프로젝트 · 알림 · 경로. `--self-test` 에 «없는 runbook bite · 있는 runbook 통과» 두 case. (새 스크립트가 아니므로 `scripts/` 수 불변.)

## Out of Scope

- 알림 식 · 임계값 변경.
- ecommerce runbook(이미 있음) · 다른 프로젝트.
- 운영 환경(EC2 데모) 고유 절차 — 로컬 compose 기준으로 쓰고, 데모 호스트는 «같은 컨테이너 이름, `ssm` 로 접속» 처럼 차이만.

# Acceptance Criteria

- [ ] AC-1: 19개 runbook + README 2개가 있고, 각 runbook 이 ①~⑤ 절을 갖는다. 각 명령의 컨테이너 이름 · 포트 · 엔드포인트가 그 프로젝트의 `docker-compose.yml` / `prometheus.yml` / 코드와 맞는다(대조 근거를 PR 본문에 표로).
- [ ] AC-2: 두 규칙 파일의 `runbook:` 이 전부 존재하는 파일을 가리킨다 · `promtool check rules` 통과.
- [ ] AC-3: 가드 rc=0 · `--self-test` 전부 PASS(새 case 둘 포함) · bite — 사본에서 runbook 하나를 지우면 rc=1.
- [ ] AC-4: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께).
- [ ] AC-5: PR CI 의 `Alert rule metrics …` 잡이 **실행되어** success.

# Related Specs

- `projects/ecommerce-microservices-platform/knowledge/runbooks/README.md`(형식 · 작성 규칙)
- 각 서비스 `specs/services/*/observability.md` · `architecture.md` · wms `specs/services/*/runbooks/*`

# Related Contracts

- 없음.

# Edge Cases

- 한 runbook 이 여러 알림에 쓰일 수 있다(지금은 1:1). README 의 Triggered by 칸이 그것을 적는다.
- `knowledge/` 는 `specs/` 보다 낮은 우선순위 — runbook 이 스펙과 어긋나면 스펙이 이긴다(ecommerce README § Priority 를 같은 문장으로).

# Failure Scenarios

- 그럴듯하지만 실재하지 않는 명령(없는 컨테이너 · 없는 actuator 엔드포인트) → 새벽에 첫 줄부터 막힌다. AC-1 의 대조표가 막는다.
- 가드가 `runbook:` 값의 따옴표 · 앞뒤 공백을 못 벗김 → 첫날 빨강. self-test 의 «있는 runbook 통과» 가 잡는다.

# Test Requirements

- 가드 self-test · bite · promtool · 가드 전수 · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
