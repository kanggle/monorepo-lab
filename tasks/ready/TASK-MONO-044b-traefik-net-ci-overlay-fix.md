# Task ID

TASK-MONO-044b

# Title

frontend-e2e CI Job 의 `traefik-net external network not found` 회귀 fix (compose stack 부팅 회복)

# Status

ready

# Owner

backend / devops

# Task Tags

- ci
- infra

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

[TASK-MONO-044](../review/TASK-MONO-044-main-baseline-ci-regression-cleanup.md) 의 진단 (`knowledge/incidents/2026-05-05-ci-regression.md` § Root Cause #2) 에서 식별된 **Frontend E2E full-stack (web-store) Job 의 단독 root cause** 회귀 fix.

**도입 commit**: `ee13ecc` (2026-05-03, TASK-MONO-024 PR #129). ecommerce `docker-compose.yml` 의 networks 블록이 `traefik-net: external: true, name: traefik-net` 를 선언. 모든 service 가 `networks: [ecommerce-net, traefik-net]` 으로 등록.

CI 환경에는 shared Traefik 스택 (`pnpm traefik:up` / `infra/traefik/`) 을 RAM 한계로 띄우지 않음. 그러나 `docker-compose.ci.yml` overlay 가 traefik-net 부재를 보상하지 않으므로 `docker compose -f docker-compose.yml -f docker-compose.ci.yml up` 호출이 즉시 abort:

```
network traefik-net declared as external, but could not be found
##[error]Process completed with exit code 1.
```

이 fix 후:
- Frontend E2E full-stack Job 의 "Start docker compose stack" step 통과
- gateway-service 컨테이너가 부팅을 시도 (그 후 healthcheck PASS 여부는 [TASK-MONO-044a](TASK-MONO-044a-libs-java-web-servlet-leak-fix.md) 의 servlet leak fix 에 의존 — 044a + 044b 양쪽 모두 머지되어야 Job 4 가 최초 PASS)

---

# Scope

## In Scope

- CI 의 frontend-e2e Job 이 `docker compose up` 호출 시 `traefik-net` lookup 실패 없이 진행하도록 수정.
- 다음 세 옵션 중 하나 채택 (PR description 에 결정 근거 기록):
  - **(i) workflow step 추가**: `.github/workflows/ci.yml` frontend-e2e Job 의 docker-compose-up step 직전에 `docker network create traefik-net || true` 1줄 추가. 가장 단순; base compose 파일 변경 없음.
  - **(ii) `docker-compose.ci.yml` 에서 traefik-net 재정의**: external 플래그를 internal bridge driver 로 override. CI overlay 파일 1개만 변경.
  - **(iii) 모든 service 의 `traefik-net` networks 등록을 CI overlay 에서 제거**: service 별 networks 재정의로 ecommerce-net 만 남기기. 가장 보수적이나 overlay 가 service-by-service 갱신이라 verbose.
- 단, `pnpm traefik:up` 로컬 dev workflow 영향 0 검증 (traefik-net 이 외부에 있을 때도 conflict 없도록).
- ecommerce 외 다른 프로젝트 (wms / GAP / fan-platform) 가 동일 패턴이면 cross-project fix 검토 (현재 frontend-e2e 만 docker compose 사용 — others 는 Testcontainers 사용으로 무관).

## Out of Scope

- Root Cause #1 (`libs/java-web` servlet leak) 의 fix — TASK-MONO-044a
- Frontend E2E Job 자체의 RAM / timeout / scenario 분할 등 별도 환경 한계 — 044a + 044b 머지 후 노출되면 별도 후속
- shared Traefik stack 을 CI 에서 띄우는 옵션 — RAM 한계로 본 task 범위 명시 제외

---

# Acceptance Criteria

## CI 부팅 회복

1. main 의 다음 CI run 에서 frontend-e2e Job 의 "Start docker compose stack" step 이 `traefik-net not found` 에러 없이 PASS
2. docker compose 가 모든 ecommerce backend 12개 + Postgres + Kafka + Redis + Elasticsearch + MinIO 컨테이너를 정상 launch
3. (TASK-MONO-044a 가 머지된 후) gateway-service healthcheck PASS → Playwright suite 실행 → Job 최종 PASS

## 로컬 dev workflow 회귀 0

4. `pnpm traefik:up` + `pnpm ecommerce:up` 흐름이 변경 없이 동작 (traefik-net 이 정말 외부에 존재하면 정상 동작)
5. 로컬 traefik 스택 미기동 상태에서 `pnpm ecommerce:up` 호출 시 (만약 옵션 i 미적용) 에러 메시지 + dev-tooling.md 가이드대로 동작

## 문서

6. `docker-compose.ci.yml` 또는 ci.yml 변경 부분에 의도 주석 (CI 한정 mitigation 명시)
7. PR description 에 옵션 (i)(ii)(iii) 중 선택 근거 기록

## 회귀 검증

8. 다른 docker-compose 사용 Job (현재 frontend-e2e 단독) 또는 dev shortcut script 회귀 0

---

# Related Specs

- [TASK-MONO-044 진단 보고서](../../knowledge/incidents/2026-05-05-ci-regression.md) § "Root Cause #2"
- [TASK-MONO-024 (Traefik 마이그레이션)](../done/TASK-MONO-024-existing-projects-traefik-migration.md) — 회귀 도입 history
- [TASK-MONO-022 (Traefik 인프라 Phase 1)](../done/TASK-MONO-022-traefik-hostname-routing-migration.md) — 인프라 도입 history
- [ADR-MONO-001 — port-prefix-scaling.md](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) — hostname routing 결정
- `docs/guides/dev-tooling.md` — DB 도구 접근 가이드 (CI 가 아닌 dev 흐름)
- `infra/traefik/docker-compose.yml` — shared Traefik stack
- `.github/workflows/ci.yml` § frontend-e2e Job — 변경 대상

---

# Related Contracts

- 없음

---

# Target Service / Component

- `.github/workflows/ci.yml` (옵션 i 채택 시: frontend-e2e Job 에 step 1줄 추가)
- `projects/ecommerce-microservices-platform/docker-compose.ci.yml` (옵션 ii 또는 iii 채택 시)

---

# Implementation Notes

- 옵션 (i) 가 가장 적은 변경 표면 + 가장 명확한 의도. 권장.
- 옵션 (i) 적용 예 (workflow step 추가):

```yaml
- name: Ensure traefik-net exists (CI mitigation for TASK-MONO-024)
  run: docker network create traefik-net || true
  working-directory: ${{ github.workspace }}

- name: Start docker compose stack (backends + infra, no observability)
  run: |
    docker compose -f docker-compose.yml -f docker-compose.ci.yml up --build -d \
      ...
```

- 옵션 (ii) 의 `docker-compose.ci.yml` 재정의 패턴:

```yaml
networks:
  traefik-net:
    external: false
    driver: bridge
```

- compose merge 동작: ci overlay 의 networks 블록이 base 의 external:true 를 override 가능 (compose v2 behavior 검증 필요).
- 옵션 (iii) 은 service 별 `networks: !override [ecommerce-net]` 스타일로 가능하나 12+ service 갱신이라 verbose.
- 검증 명령:
  ```
  cd projects/ecommerce-microservices-platform
  docker network create traefik-net || true
  docker compose -f docker-compose.yml -f docker-compose.ci.yml config --quiet  # syntax check
  docker compose -f docker-compose.yml -f docker-compose.ci.yml up --build -d gateway-service redis kafka  # subset smoke
  docker compose -f docker-compose.yml -f docker-compose.ci.yml down -v
  ```

---

# Edge Cases

1. **로컬 dev 가 traefik-net 에 이미 attach 된 상태**: 옵션 (i) 의 `docker network create traefik-net || true` 는 idempotent, 기존 네트워크 그대로 두고 `|| true` 로 빠져나옴. 안전.
2. **Multiple ecommerce gateway 컨테이너가 traefik-net 에 attach 시도**: ci overlay 가 internal driver 만 만든다면 traefik 라벨이 무시될 뿐 (Traefik 컨테이너 자체가 없으므로 routing 의미 없음). 정상.
3. **Cross-project frontend-e2e**: 현재 ecommerce 단독. 만약 fan-platform-web 또는 GAP admin frontend 가 동일 패턴으로 docker compose 사용하면 동일 fix 필요. CI workflow grep 으로 확인 후 cross-project 적용 여부 결정.
4. **docker-compose.ci.yml 에 networks override 가 base 의 external:true 를 못 이김**: compose 의 merge semantics 가 networks 블록은 deep-merge 가 아닌 replace 일 수 있음. 옵션 (ii) 채택 시 사전 `docker compose config` 출력으로 검증.

---

# Failure Scenarios

## A. 옵션 (i) 적용 후 docker compose up 은 통과하나 gateway-service healthcheck 가 BeanDefinitionOverrideException 으로 실패

이 시나리오는 TASK-MONO-044a 미머지 상태. 정상이며 본 task 의 책임 밖. 044a 머지를 기다리거나 044a + 044b 함께 머지.

## B. compose merge 가 옵션 (ii) 의 networks override 를 무시

옵션 (ii) 가 의도대로 동작 안 함. mitigation: 옵션 (i) 로 fallback.

## C. CI 환경에서 docker network 가 prior run 에서 leak 되어 conflict

`docker network create traefik-net` 가 이미 존재하는 네트워크와 conflict. `|| true` 로 무시. 또는 명시적 `docker network rm traefik-net 2>/dev/null; docker network create traefik-net` 으로 cleanup-recreate.

---

# Test Requirements

- frontend-e2e Job 이 docker compose up step PASS (다음 main CI run 검증)
- 로컬 (`projects/ecommerce-microservices-platform`) 에서 `pnpm ecommerce:up` 변경 없이 동작 (traefik 스택 가동 시)
- compose `config` 명령으로 syntax 검증
- 044a 도 머지된 시점에 frontend-e2e Job 최종 PASS

---

# Definition of Done

- [ ] 옵션 (i)(ii)(iii) 중 선택 + 적용
- [ ] frontend-e2e Job 의 docker compose up 단계 PASS 검증 (CI run 결과 PR 첨부)
- [ ] 로컬 dev workflow 회귀 0 (traefik 스택 가동 시 정상)
- [ ] PR description 에 결정 근거 기록
- [ ] Ready for review

---

# Notes

- **Recommended impl model**: **Sonnet** — 단순 CI workflow 변경 1–2 줄 + compose syntax 검증.
- **분량 추정**: workflow yml 1개 또는 docker-compose.ci.yml 1개 + (선택) 다른 프로젝트 cross-check. 작은 PR.
- **dependency**:
  - `선행`: 없음 (044a 와 독립적, 병렬 가능)
  - `후속`: 044a + 044b 양쪽 머지 후 frontend-e2e Job 최초 PASS
- **CI failure 와 본 PR 영향**: 본 PR 자체도 frontend-e2e + 다른 3 Job 이 FAIL 상태로 진행될 가능성 — admin-override 검토 필요 (044a 와 같은 정책).
