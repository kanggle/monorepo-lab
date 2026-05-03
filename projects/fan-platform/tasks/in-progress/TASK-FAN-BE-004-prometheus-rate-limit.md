# Task ID

TASK-FAN-BE-004

# Title

fan-platform `/actuator/prometheus` 스크레이프 보호 (rate-limit 또는 네트워크 격리)

# Status

ready

# Owner

backend

# Task Tags

- code
- security
- ops

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

community-service 와 (장차) artist-service 의 `/actuator/prometheus` 엔드포인트는 인증 없이 expose 되며, 카디널리티 폭주 / 익명 익스플로잇 위험이 있다.

`projects/fan-platform/specs/contracts/http/community-api.md` 의 health/metrics 표는 이 엔드포인트를 "gateway-rate-limited" 로 명시하지만, 실제 gateway-service (TASK-FAN-BE-001 / PR #115) 는 `/api/v1/community/**` 와 `/api/v1/artist/**` 만 라우팅한다 — actuator 경로는 gateway 를 거치지 않는다. PR #116 review 에서 발견된 갭이며, 본 task 에서 다음 중 하나로 결정한다:

1. **Gateway route 추가**: gateway-service 에 `/actuator/prometheus` 전용 라우트 + RequestRateLimiter (e.g. 1 req/s, burst 10) 를 추가하여 외부 스크레이퍼가 gateway 를 통해서만 접근하도록 강제. 그러면 community-service 의 `SecurityConfig.PUBLIC_PATHS` 는 그대로 두되, K8s NetworkPolicy / docker-compose network isolation 으로 직접 접근을 차단.
2. **In-process rate limit**: community-service / artist-service 자체에 Bucket4j 또는 Resilience4j 기반 servlet filter 를 추가해서 `/actuator/prometheus` 만 별도 한도(e.g. 6 req/min) 에 걸어둔다.
3. **네트워크 격리만**: rate-limit 없이 docker-compose 의 actuator 포트를 외부에 노출하지 않도록 설계 — Prometheus 서버는 docker network 내부에서 스크레이프 (`http://community-service:8080/actuator/prometheus`).

선택을 task 진행 시점에 ADR 또는 spec 업데이트로 명시하고, 그 후 spec/문서를 정합화한다.

본 task 완료 시점에 community-api.md 의 임시 표시 ("rate-limit gap — see TASK-FAN-BE-004") 가 해소되어야 한다.

---

# Scope

## In Scope

- 위 옵션 중 하나(또는 조합) 선택 + 구현
- spec 갱신: `projects/fan-platform/specs/contracts/http/community-api.md` 의 prometheus 행 → 정합 표현으로 교체
- artist-service (TASK-FAN-BE-003) 가 부트스트랩되어 있다면 동일 정책 적용
- ops 가이드: `projects/fan-platform/docs/operations/prometheus-scrape.md` (옵션) 에 스크레이퍼 IP allowlist / interval 권고 추가

## Out of Scope

- gateway-service 자체의 광범위한 라우팅 리팩터링 (별도 task)
- Grafana / Loki 대시보드 디자인
- 다른 actuator 엔드포인트(env, heapdump 등) 정책 — 별도 검토 필요 (`SecurityConfig` 는 이미 prometheus / health / info 만 permitAll 이지만, 추가 확정 필요 시 별도 task)

---

# Acceptance Criteria

1. `/actuator/prometheus` 가 unauthenticated 호출 폭주에 의해 cardinality 또는 latency 폭주를 일으키지 않음 (rate-limit 또는 외부 차단으로 보호)
2. spec `community-api.md` 의 prometheus 행이 실제 동작과 일치 — "TASK-FAN-BE-004" 임시 참조가 제거됨
3. local docker-compose 에서 Prometheus 컨테이너(있다면) 가 정상적으로 매 N초 스크레이프할 수 있음
4. 단위 테스트 또는 integration test 로 의도한 보호가 동작함을 검증 (rate-limit 옵션이라면 burst 초과 시 429)

---

# Related Specs

- `projects/fan-platform/specs/services/community-service/architecture.md`
- `projects/fan-platform/specs/services/gateway-service/architecture.md` (PR #115)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/community-api.md` § Health / metrics

---

# Edge Cases

- Prometheus 스크레이프 재시도가 burst 한도에 걸려 데이터 갭 발생 — 한도는 실제 스크레이프 주기(보통 15s) 의 2~3 배 burst 를 허용해야 함
- Kubernetes 환경에서 sidecar metric agent 도 동일 endpoint 를 호출 → 그 호출도 카운트되는지 확인
- gateway 가 `/actuator/prometheus` 를 forward 하는 경우, gateway 자체의 prometheus 와 community-service 의 prometheus 가 같은 path 로 충돌 — gateway 가 자기 metrics 를 다른 path 로 옮기거나 host header 기반 구분이 필요

---

# Failure Scenarios

- gateway 라우트 추가 후 community-service 가 forwarded `X-Forwarded-For` 를 신뢰하지 않으면 rate-limit 키가 무의미 → `forward-headers-strategy: framework` 가 이미 설정되어 있는지 재확인 + 명시
- in-process rate-limit 도입 시 Spring Boot Actuator의 management.server.port 분리 (별도 포트로 actuator 서빙) 와 충돌 가능 — 도입 시 `application.yml` 의 management.server 섹션 검토

---

# Notes

- 본 task 는 PR #116 (community-service bootstrap) review 에서 발견된 follow-up — Warning 4
- UUID v7 migration (TASK-MONO-025) 와 무관
