# Task ID

TASK-MONO-024

# Title

기존 3 프로젝트 docker-compose 의 Traefik 마이그레이션 (Phase 2)

# Status

ready

# Owner

backend / devops

# Task Tags

- code
- deploy

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

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) (ACCEPTED, Option C) 의 Phase 2 작업: **기존 3개 프로젝트의 docker-compose 를 PORT_PREFIX 패턴에서 Traefik hostname routing 패턴으로 일괄 마이그레이션** 한다.

전제: [TASK-MONO-022](TASK-MONO-022-traefik-hostname-routing-migration.md) (Phase 1) 가 머지되어 `infra/traefik/` 스택과 `traefik-net` external network 가 운영 가능한 상태.

이 태스크 완료 후:

- ecommerce-microservices-platform / wms-platform / global-account-platform 의 `docker-compose.yml` 에서 `${PORT_PREFIX:-N}XXXX:YYYY` 패턴이 모두 사라짐
- 각 프로젝트의 gateway/frontend 가 Traefik 라벨로 `<project>.local` 등록
- 백엔드 서비스 (postgres/redis/kafka 등) 는 호스트 포트 0개 점유 → docker network 내부에서만
- 7개 프로젝트 (3 기존 + 4 신규 부트스트랩) 모두 한 머신에서 동시 기동 시 호스트 포트 충돌 없음
- 각 프로젝트 `.env.example` 에서 `PORT_PREFIX` 제거, `PROJECT_HOSTNAME` 명시
- e2e/Frontend E2E full-stack CI 잡이 새 모델에서 통과 (또는 별도 follow-up 분리)

---

# Scope

## In Scope

### docker-compose 마이그레이션 (3 프로젝트)

각 프로젝트의 `docker-compose.yml` 에 다음 변경 적용:

- 모든 backing service (postgres, redis, kafka, zookeeper, schema-registry, observability stack 등) 의 `ports:` → `expose:` 로 전환
- gateway 서비스에 Traefik 라벨 추가:
  ```yaml
  gateway:
    expose: ["8080"]
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.<project>.rule=Host(`<project>.local`)"
      - "traefik.http.services.<project>.loadbalancer.server.port=8080"
    networks: [traefik-net, <project>-net]
  ```
- `traefik-net` 외부 네트워크 join + 자체 `<project>-net` bridge network 정의
- 5자리 호스트 포트 (예: Jaeger UI `16686`) 는 별도 처리:
  - 옵션 A: 그대로 노출 (충돌 가능성 낮음)
  - 옵션 B: Traefik dashboard 처럼 dev-only 라벨로 재구성

### 환경변수 / 문서

- 각 프로젝트 `.env.example` 의 `PORT_PREFIX=N` 제거
- 각 프로젝트 `.env.example` 에 `PROJECT_HOSTNAME=<name>.local` 추가 (참조용, 실제 동작은 docker-compose 의 라벨이 결정)
- 각 프로젝트 README 에 새 접근 방법 (`http://<project>.local/`) 안내
- 루트 `TEMPLATE.md` § Local Network Convention 의 hostname 할당 표에서 "(legacy PORT_PREFIX)" 표기 제거

### CI 영향 분석 + 수정

- `frontend-e2e` (TASK-MONO-014) 잡: docker-compose `ports:` 가정에 의존하는 부분 수정
  - Playwright config 의 `webServer` URL 을 hostname 기반으로 변경
  - CI runner 에서 `*.local` 호스트네임 등록 (workflow step 으로 `/etc/hosts` 추가)
  - 또는 hostname 의존성을 제거하고 docker network 내부에서 직접 호출하는 방식으로 변경
- `gap-integration-tests` 잡: Testcontainers 사용 — 호스트 포트와 무관, 영향 없음 확인
- `Integration (master-service, Testcontainers)` 잡: 동일하게 Testcontainers — 영향 없음

### 검증

- 3 프로젝트 + Traefik 동시 기동 후:
  - `curl http://ecommerce.local/actuator/health` → 200
  - `curl http://wms.local/actuator/health` → 200
  - `curl http://gap.local/actuator/health` → 200
- `docker port` / `netstat` 에서 호스트 포트 점유: Traefik (`:80`/`:443`/`:8080`) 만 보임 (5자리 포트 노출 유지 시 그것도 보임)

## Out of Scope

- HTTPS/TLS 적용 — 별도 follow-up
- Traefik dashboard 인증 — 별도 follow-up
- 4 신규 프로젝트 (fan-platform/scm/erp/mes) 의 docker-compose 작성 — 각 프로젝트 부트스트랩 태스크가 책임
- production 환경 매핑 — 본 태스크는 로컬 dev 만

---

# Acceptance Criteria

- [ ] 3개 프로젝트의 docker-compose.yml 에서 `${PORT_PREFIX}XXXX:YYYY` 패턴 모두 제거 (검증: `grep -r 'PORT_PREFIX' projects/` → 빈 결과)
- [ ] 각 프로젝트의 gateway 가 Traefik 라벨 보유 (`traefik.http.routers.<project>.rule=Host(...)` 확인)
- [ ] `pnpm traefik:up` + 3 프로젝트 `docker compose up -d` 동시 실행 시 호스트 포트 충돌 없음
- [ ] hostname 기반 health check 3건 모두 200 반환
- [ ] 각 프로젝트 `.env.example` 에서 `PORT_PREFIX` 제거 + `PROJECT_HOSTNAME` 추가
- [ ] 각 프로젝트 README 갱신
- [ ] `frontend-e2e` CI 잡이 PASS (수정 후) 또는 의도적으로 deferred 됨이 명시
- [ ] `gap-integration-tests` / `Integration (master-service)` 잡 영향 없음 검증

---

# Related Specs

- [docs/adr/ADR-MONO-001-port-prefix-scaling.md](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) — 결정 근거
- [CLAUDE.md § Local Network Convention](../../CLAUDE.md) — 정책 (이미 머지됨)
- [TEMPLATE.md § Local Network Convention](../../TEMPLATE.md) — 부트스트랩 절차

# Related Skills

- `.claude/skills/infra/` (해당 항목 존재 시)

---

# Related Contracts

해당 없음.

---

# Target Service / Component

- `projects/ecommerce-microservices-platform/docker-compose.yml`
- `projects/ecommerce-microservices-platform/.env.example`
- `projects/ecommerce-microservices-platform/README.md`
- `projects/wms-platform/docker-compose.yml`
- `projects/wms-platform/.env.example`
- `projects/wms-platform/README.md`
- `projects/global-account-platform/docker-compose.yml`
- `projects/global-account-platform/.env.example`
- `projects/global-account-platform/README.md`
- `.github/workflows/ci.yml` (frontend-e2e 잡 수정)
- `TEMPLATE.md` (hostname 할당 표의 legacy 표기 제거)

---

# Architecture

ADR-MONO-001 § Recommendation 의 Option C 패턴. TASK-MONO-022 가 신설한 `infra/traefik/` 스택 위에 동작.

마이그레이션 패턴 (예: wms-platform):

```yaml
# projects/wms-platform/docker-compose.yml (after migration)
services:
  gateway-service:
    image: ...
    expose: ["8080"]
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.wms.rule=Host(`wms.local`)"
      - "traefik.http.services.wms.loadbalancer.server.port=8080"
    networks: [traefik-net, wms-net]

  postgres:
    image: postgres:16
    expose: ["5432"]
    networks: [wms-net]

  kafka:
    image: confluentinc/cp-kafka:7.x
    expose: ["9092"]
    networks: [wms-net]

networks:
  traefik-net:
    external: true
  wms-net:
    driver: bridge
```

---

# Implementation Notes

- 마이그레이션 PR 은 **3 프로젝트 atomic commit** — `CLAUDE.md` § Cross-Project Changes 에 따라 한 PR 안에 묶음.
- 각 프로젝트의 `.env` 캐시된 `PORT_PREFIX` 값이 있으면 README 에 "old `.env` 파일 삭제 후 `.env.example` 복사" 안내.
- `frontend-e2e` 잡 수정 시 docker-compose 의 `gateway` 서비스에 host port 가 없어졌으므로, Playwright config 가 hostname 으로 접근하도록 변경. CI runner 의 `/etc/hosts` 에 `127.0.0.1 ecommerce.local wms.local gap.local` 추가하는 step 필요.
- 5자리 host port (Jaeger UI `16686`, observability dashboards) 는 본 태스크 범위에서 그대로 유지 가능. 충돌 빈도 낮음.
- 마이그레이션 검증을 자동화하는 스크립트 (`scripts/verify-traefik-migration.sh`) 작성 권장.

---

# Edge Cases

- 기존 사용자가 `.env` 에 `PORT_PREFIX` 를 명시적으로 override 해 사용 중 → README 가이드: "마이그레이션 후 `.env` 의 `PORT_PREFIX` 라인 삭제 필요"
- 외부 도구 (DBeaver 등) 가 기존 `localhost:25432` 형태로 wms postgres 접근 중 → TASK-MONO-022 의 `dev-tooling.md` 가이드 참조하여 docker exec 또는 dev overlay 사용
- frontend-e2e 잡이 docker-compose 의 published port 를 기대 → CI runner 의 hosts 등록 step 추가 + Playwright `webServer` URL 변경
- Jaeger UI / Grafana / Prometheus 등 5자리 포트의 dashboard 들은 별도 라벨링 또는 그대로 유지 (별도 결정)

---

# Failure Scenarios

- **마이그레이션 후 e2e 잡 fail**: 즉시 follow-up fix 또는 잡 의도적 disable + 별도 sub-task. 본 태스크의 AC 에 명시.
- **Traefik 라벨 syntax 오류로 라우팅 실패**: 검증 스크립트로 `curl http://<project>.local/health` 후 200 확인 필수.
- **5자리 포트 충돌 (Jaeger UI 16686 등)**: 별도 follow-up 으로 분리. 본 태스크에서는 5자리 포트 그대로 유지.
- **docker-compose `external: true` 네트워크 미존재 시 시작 실패**: README 가이드에 "Traefik 먼저 기동" 명시 + Phase 1 의 setup script 우선 실행 안내.

---

# Test Requirements

- 통합 검증 (수동 또는 스크립트):
  - `pnpm traefik:up`
  - 3 프로젝트 모두 `docker compose up -d`
  - hostname 기반 health check 3건 PASS
  - 호스트 포트 점유: Traefik 만 (5자리 dashboards 예외 가능)
- CI 검증:
  - `Build & Test` PASS
  - `frontend-e2e` PASS (수정 후) 또는 따로 deferred
  - `gap-integration-tests`, `Integration (master-service)` 영향 없음 확인

---

# Definition of Done

- [ ] 3 프로젝트 docker-compose / .env / README 마이그레이션 완료 (atomic commit)
- [ ] hostname 기반 health check 검증 통과
- [ ] frontend-e2e 잡 수정 또는 deferred 분리
- [ ] gap-integration-tests / master-service integration 영향 없음 검증
- [ ] TEMPLATE.md hostname 표에서 "(legacy)" 표기 제거
- [ ] Ready for review
