# Task ID

TASK-MONO-842

# Title

`platform/observability.md` 의 «모든 서비스 필수 metric» 표에 아무도 내보내지 않는 이름 3개가 있다 — 실제 이름으로 고치고, 2026-11-24~25 에 생긴 규약(스펙의 «계획» 표시 · 대시보드 대기 목록 · 세 방향 가드)을 플랫폼 문서에 적는다

# Status

review

# Owner

monorepo

# Task Tags

- docs
- observability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (문서 한 파일)
> 기안 2026-11-25 UTC. 출처: 세션 마무리 «문서 업데이트» 요청 — `TASK-MONO-837` 은 `projects/*/specs` 만 셌고 `platform/` 은 모집단 밖이었다.

# Dependency Markers

- 선행: `TASK-MONO-833`(코드 → 스펙) · `TASK-MONO-834` · `839`(알림 · 대시보드 → 코드) · `TASK-MONO-840`(«계획» 표시 형식) · `TASK-MONO-841`(스펙 → 코드). 이 티켓은 그 결과를 플랫폼 규칙으로 적기만 한다.

# Goal

2026-11-25 UTC 실측: `platform/observability.md` § Required Metrics 표의 `http_requests_total` · `http_request_duration_seconds` · `db_connection_pool_active` 는 저장소 전체(`projects/*/infra` · `specs` · `platform` · `rules`)에서 **그 표에만** 나온다. Spring Boot 가 실제로 내보내는 이름은 `http_server_requests_seconds_*`(대시보드 · 알림 37곳이 묻는다) · `hikaricp_connections_active`(8곳)다. `jvm_memory_used_bytes` 는 맞다.

또 지난 이틀의 규약이 티켓 증거 · 가드 메시지에만 있다 — 새 metric 을 추가하는 사람이 플랫폼 문서에서 알 수 없다.

# Scope

## In Scope

1. § Required Metrics 표: 세 이름을 실제 Prometheus 이름 · 출처(Spring Boot / HikariCP 바인더)로. p99 에 필요한 히스토그램 버킷은 `libs/java-observability` 가 켠다는 한 줄(`TASK-MONO-836`).
2. § Change Rule 아래 새 절 **«스펙 ↔ 코드 ↔ 소비자 일치»**: 세 방향과 각 가드 이름 · 스펙 표의 «계획» 표시 형식(그대로 복사 가능한 문자열) · 파생값 표기 · 대시보드 대기 목록(`projects/<p>/infra/grafana/pending-metrics.txt`)의 규칙(스펙이 선언한 이름만, 알림엔 적용 안 됨, 내보내지거나 안 쓰이면 가드 실패) · Grafana datasource uid 규칙(`check-grafana-datasource-uid.mjs`).
3. 이름 규칙 한 줄: Micrometer 이름 → Prometheus 이름 변환은 추측하지 말고 실제 `PrometheusMeterRegistry` scrape 로 확인(`TASK-BE-666` 실측 — Counter `x.count` → `x_count_total`, Gauge `x.count` → `x_count`).

## Out of Scope

- § Tracing 의 «Spans must be created for …» 규칙과 현실(수동 span 0)의 차이 — 측정하지 않았다. 별 판단.
- 프로젝트 스펙.

# Acceptance Criteria

- [x] AC-1: 표의 이름이 전부 실제로 내보내지는 이름이다(대시보드 · 알림이 이미 묻는 이름과 같은 철자).
- [x] AC-2: 새 절의 가드 이름 4개(`check-metric-name-registry.sh` · `check-alert-rule-metrics.sh` · `check-spec-metrics-implemented.mjs` · `check-grafana-datasource-uid.mjs`)가 `scripts/` 에 실제로 있고, «계획» 표시 문자열이 `check-spec-metrics-implemented.mjs` 가 찾는 문자열과 같다.
- [ ] AC-3: 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- `platform/observability.md`

# Related Contracts

- 없음.

# Edge Cases

- `platform/` 은 공유 경로 — 프로젝트 이름 · 서비스 이름을 규칙 본문에 넣지 않는다(HARDSTOP-03). 실측 예시는 티켓 번호로만 가리킨다.

# Failure Scenarios

- 문서의 «계획» 문자열이 가드가 찾는 문자열과 한 글자라도 다르면, 문서를 보고 쓴 표시가 가드를 통과 못 한다. AC-2 가 막는다.

# Test Requirements

- 문자열 대조 · 가드 · CI.

# Implementation Evidence (2026-11-25 UTC)

- **§ Required Metrics** — 표를 실제로 내보내지는 이름 + 출처 칸으로: `http_server_requests_seconds_count` · `http_server_requests_seconds_bucket`(버킷은 `libs/java-observability` — 836, 그 라이브러리를 안 쓰는 서비스는 속성) · `jvm_memory_used_bytes`(그대로) · `hikaricp_connections_active`. 옛 이름 셋(`http_requests_total` · `http_request_duration_seconds` · `db_connection_pool_active`)은 저장소에서 이 표에만 있었다 — 새 이름은 대시보드 · 알림 · 스펙이 이미 묻는 철자(`http_server_requests_seconds_count` 37곳 · `hikaricp_connections_active` 8곳).
- **새 절 «Spec ↔ Code ↔ Consumers»** — 세 방향 표(가드 · 실패 조건) · datasource uid 가드 · «계획» 표시 형식(복사 가능한 문자열 + 범례 줄) · 파생 표기 · span 은 표시로만 통과 · 대시보드 대기 목록 규칙 · 이름 변환 실측(Counter/Gauge/Timer · `-` · 강한 참조 gauge). 🔴 처음 쓴 «세 방향이 모두 한 술어» 는 틀렸다(코드 → 스펙 가드는 다른 술어) — «코드가 내보내나» 를 묻는 두 방향만 공유한다고 고쳐 적었다.
- **AC-1** — 위.
- **AC-2** — 가드 4개 `scripts/` 에 존재(파일 확인) · 문서의 표시 문자열 `🔵 *계획 · 미구현` 이 `check-spec-metrics-implemented.mjs:48` `PLANNED` 와 같다 · 공유 문서에 프로젝트 · 서비스 이름 0(grep — HARDSTOP-03).
- **AC-3** — 스테이지 뒤 `check-*.sh` 43: 41 rc=0, 비0 둘 = 환경 전제(erp 래칫 · prerendered verdict). `.mjs` 8/8. PR CI: 대기.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
