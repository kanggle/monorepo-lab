# Task ID

TASK-MONO-840

# Title

스펙의 metric · span 표를 코드와 맞춘다 — 이름만 다른 3 · 프레임워크 이름 6 · 파생값 12 는 스펙을 고치고, 미구현 120 · span 23 은 행에 «계획» 표시, iam «필수 메트릭» 제목은 «핵심 메트릭» 으로

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- observability

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5.5 (문서 — 행 단위 편집, 이름은 837 목록에서만)
> 기안 2026-11-25 UTC. 출처: `TASK-MONO-837` AC-5 → 소유자 «권장안대로 진행»(A2 · B · C = 스펙 정정 · D2 · E = (b) «계획» 표시 · F = 그대로).

# Dependency Markers

- 선행: `TASK-MONO-837`(범주별 이름 목록 — 이 티켓의 유일한 입력). `TASK-MONO-839`(대시보드 · 대기 목록).
- 병행: `TASK-BE-668` · `TASK-BE-669`(837 D1 구현). D1 6행은 이 티켓이 건드리지 않는다(구현되면 그대로 맞는 행이 된다).
- 후속: 스펙 → 코드 가드(셋째 방향) — «계획» 이 아닌 행만 문다. 이 티켓이 «계획» 표시 형식을 정한다.

# Goal

837 이 스펙 → 코드 방향으로 셌다: 고유 251 중 구현 74 · 나머지는 «스펙 쪽이 틀림»(A2 3 · B 6 · C 12) 과 «아직 없음»(D1 6 · D2 120 · E 23) · F 7. 소유자 결정 = 권장안:

- **A2 · B · C(21)**: 스펙의 이름을 코드 · 프레임워크가 실제로 내는 이름으로, 파생값은 «계측 아님 — PromQL» 로 식을 적는다.
- **D2 · E(143)**: 지우지도 구현하지도 않고 행에 «계획» 표시 — 스펙이 «있다» 고 거짓말하지 않게. 알림 · 대시보드가 필요해질 때 구현한다.
- iam «필수 메트릭» 표 다섯 곳: 52행 중 32행이 없었다 → 제목을 **«핵심 메트릭»** 으로(«필수» 는 «지금 있다» 로 읽힌다).

# Scope

## In Scope

1. **표시 형식(한 가지)**: 행의 마지막 칸 끝에 ` — 🔵 *계획 · 미구현 (TASK-MONO-837)*`. 표 위에 한 줄 범례 `> 🔵 *계획* 표시 행은 아직 코드가 내보내지 않는다(2026-11-25 UTC 전수, TASK-MONO-837). 알림 · 대시보드가 필요해질 때 구현한다.` — 그런 행이 하나라도 있는 표에만.
2. **A2(3)**: fan `outbox_pending_count` → `artist.outbox.pending.count` · iam `security_consumer_lag` → `kafka.consumer.lag`(파티션별 gauge) · wms `wms.notification.delivery.attempt` → `notification.delivery.attempts`. 라벨 칸도 코드에 맞춘다.
3. **B(6)**: 스펙 이름 → 실제 프레임워크 시리즈 + 라벨(837 증거 B 표 그대로): fan `http.server.requests`(이미 프레임워크 이름 — «Spring Boot 내장» 표기만) · iam `auth_circuit_breaker_state` · `admin_circuit_breaker_state` → `resilience4j_circuitbreaker_state{name=…}` · `gateway_requests_total` · `gateway_request_duration_seconds` → `http_server_requests_seconds_*`(⚠️ `routeId` 라벨 없음 명시) · wms `notification.channel.circuit.state` → `resilience4j_circuitbreaker_state{name="slack"}`.
4. **C(12)**: 파생값 행을 «파생 — PromQL: `<식>`» 로. 식은 같은 서비스에 **등록된** 카운터로만 쓸 수 있으면 쓰고, 재료 카운터도 미구현이면 «계획» 표시를 함께.
5. **D2(120) · E(23)**: 837 목록의 이름 그대로, 스펙 파일 · 행을 찾아 표시. 같은 이름이 여러 파일(계약 + 서비스)에 있으면 전부.
6. iam 다섯 `observability.md` 의 `### 필수 메트릭` → `### 핵심 메트릭`(그 제목을 가리키는 링크 · 앵커가 있으면 함께).

## Out of Scope

- 코드 · 대시보드 · 알림 변경. D1 6행(668 · 669). F 7행(폐기 서비스 스펙 — 그대로).
- 셋째 방향 가드(후속 티켓).

# Acceptance Criteria

- [ ] AC-1: 837 의 D2 120 · E 23 이름이 전부 표시됐다 — 스펙 전체에서 그 이름이 첫 칸인 행 중 표시 없는 행 0(스크립트로 세고 수를 증거에).
- [ ] AC-2: A2 · B · C 21행의 새 이름이 코드 · 프레임워크에 실제로 있다(A2 는 등록 이름, B 는 837 B 표의 근거, C 는 식의 재료가 등록됐거나 «계획»).
- [ ] AC-3: 표시된 행이 구현된 이름(837 A)을 하나도 포함하지 않는다(오표시 0).
- [ ] AC-4: `check-metric-name-registry.sh` rc=0(코드 → 스펙 방향이 깨지지 않음) · 가드 전부(스테이지 뒤) rc=0(환경 전제 비0 은 사유와 함께) · PR CI 실패 0.

# Related Specs

- 837 증거가 가리키는 모든 스펙 파일(4 프로젝트).

# Related Contracts

- `wms` `specs/contracts/webhooks/erp-*-webhook.md`(D2 이름이 계약 문서에도 있다 — 같은 표시).

# Edge Cases

- 한 행이 여러 이름을 담는다(첫 칸에 백틱 둘) → 그중 하나라도 미구현이면 표시하고 어느 것인지 적는다.
- 표가 아니라 목록(`- \`name\` — …`)으로 적힌 metric 은 837 모집단 밖 — 건드리지 않는다.

# Failure Scenarios

- 837 목록에 없는 행을 «비슷해 보여서» 표시 → 오표시. AC-3 이 막는다.
- 행 끝이 아니라 중간 칸에 넣어 표 칸 수가 깨짐 → 렌더 깨짐. 셀 수 보존을 스크립트로 확인.

# Test Requirements

- 표시 · 오표시 · 셀 수 스크립트 검사 · 가드 · CI.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
