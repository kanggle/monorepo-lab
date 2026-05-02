# Task ID

TASK-MONO-022

# Title

Traefik hostname-routing 마이그레이션 (PORT_PREFIX → 호스트명 기반 라우팅)

# Status

ready

# Owner

backend / devops

# Task Tags

- code
- deploy
- adr

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

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) (ACCEPTED, Option C) 의 결정에 따라 monorepo 의 로컬 dev 네트워크 모델을 **PORT_PREFIX 디지털 슬롯** 에서 **Traefik reverse proxy + 호스트명 기반 라우팅** 으로 전환한다.

이 태스크 완료 후:

- 단일 공유 Traefik 컨테이너만 호스트 포트 `:80`/`:443` 을 점유
- 모든 프로젝트의 gateway/frontend 는 `expose:` + Traefik 라벨로 hostname 등록
- 백엔드 서비스 (postgres, redis, kafka 등) 는 호스트 포트 0개 점유 — docker network 내부에서만 접근 가능
- 7개 프로젝트 (ecommerce + wms + GAP + fan-platform + scm + erp + mes) 모두 한 머신에서 동시 기동 가능
- 신규 프로젝트 추가 시 PORT_PREFIX 할당 의사결정 불필요

---

# Scope

## In Scope

- 루트에 `infra/traefik/docker-compose.yml` 신설 — Traefik v3 single instance, dashboard `:8080` 노출, `traefik-net` external network 정의
- 루트 `Makefile` 또는 `package.json` 스크립트에 `make traefik-up` / `make traefik-down` 추가
- 기존 3개 프로젝트의 docker-compose 마이그레이션:
  - `projects/ecommerce-microservices-platform/docker-compose.yml` — 모든 서비스의 `ports:` 제거, gateway 에 Traefik 라벨, `traefik-net` 외부 네트워크 join
  - `projects/wms-platform/docker-compose.yml` — 동일
  - `projects/global-account-platform/docker-compose.yml` — 동일
- 각 프로젝트 `.env.example` 에서 `PORT_PREFIX=N` 라인 제거, `PROJECT_HOSTNAME=<name>.local` 추가
- `docs/guides/dev-tooling.md` 신설 — DBeaver / Redis Insight / Kafka UI 가 내부 네트워크 서비스 접근하는 3가지 방법 (docker exec / overlay / Traefik TCP) 가이드
- `README.md` 루트 — 일회성 dev 환경 셋업 절차에 `/etc/hosts` 등록 단계 추가
- `scripts/dev-setup.sh` (or `.ps1`) — `*.local` 호스트명을 hosts 파일에 자동 등록하는 helper (idempotent)
- 검증: 7개 프로젝트 (3 기존 + 4 신규는 부트스트랩 후) 동시 `docker compose up -d` 시 충돌 없이 모두 health check 통과

## Out of Scope

- HTTPS / TLS 적용 (자체 서명 인증서 + Traefik certResolver) — TASK-MONO-024 follow-up
- Traefik dashboard 인증 — dev 환경이라 비활성. 운영 환경 채택 시 별도
- production K8s Ingress 매핑 — 본 태스크는 로컬 dev 만
- service mesh (Istio/Linkerd) — overkill, 본 태스크 범위 밖
- 5자리 host port (Jaeger UI `16686` 등) 의 Traefik 노출 — 옵션 (별도 가이드 노트)
- 4 신규 프로젝트 (fan-platform/scm/erp/mes) 의 docker-compose 작성 — 각 프로젝트 부트스트랩 태스크 책임

---

# Acceptance Criteria

- [ ] `infra/traefik/docker-compose.yml` 가 Traefik v3 단일 인스턴스를 정의하고 `traefik-net` external network 를 생성
- [ ] 3개 기존 프로젝트의 docker-compose.yml 에서 `${PORT_PREFIX}XXXX:YYYY` 패턴이 모두 사라짐 (`ports:` → `expose:` 또는 Traefik 라벨)
- [ ] 각 프로젝트의 gateway 가 `traefik.http.routers.<project>.rule=Host(\`<project>.local\`)` 라벨 보유
- [ ] `docker compose -f infra/traefik/docker-compose.yml up -d` + 3개 프로젝트 `docker compose up -d` 동시 기동 시 호스트 포트 충돌 없음
- [ ] `curl http://ecommerce.local/actuator/health`, `http://wms.local/actuator/health`, `http://gap.local/actuator/health` 모두 200 OK 반환
- [ ] 각 프로젝트의 `.env.example` 에서 `PORT_PREFIX` 제거, `PROJECT_HOSTNAME` 추가
- [ ] `docs/guides/dev-tooling.md` 가 DB 도구 접근 3가지 방법을 documented
- [ ] `scripts/dev-setup.sh` (or `.ps1`) 가 hosts 파일 등록을 idempotent 하게 처리
- [ ] 루트 README.md 가 새로운 dev 환경 셋업 절차 (Traefik 기동 + hosts 등록) 를 안내
- [ ] CI 의 `Frontend E2E full-stack` 잡이 새 라우팅 모델에서도 통과 (또는 별도 follow-up 태스크로 분리됨이 명시)

---

# Related Specs

- [docs/adr/ADR-MONO-001-port-prefix-scaling.md](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) — 결정 근거
- [CLAUDE.md § Local Network Convention](../../CLAUDE.md) — 정책 정의
- [TEMPLATE.md § Local Network Convention](../../TEMPLATE.md) — 신규 프로젝트 부트스트랩 절차

# Related Skills

- `.claude/skills/infra/` (해당 항목 존재 시)

---

# Related Contracts

해당 없음 (인프라 변경, HTTP/이벤트 contract 변경 없음).

---

# Target Service / Component

- 루트 `infra/traefik/` (신규)
- `projects/ecommerce-microservices-platform/docker-compose.yml`
- `projects/wms-platform/docker-compose.yml`
- `projects/global-account-platform/docker-compose.yml`
- `docs/guides/dev-tooling.md` (신규)
- `scripts/dev-setup.sh` (or `.ps1`, 신규)
- `README.md` 루트 (단계 추가)

---

# Architecture

`docs/adr/ADR-MONO-001-port-prefix-scaling.md` § Recommendation 의 Option C 패턴을 따른다:

```yaml
services:
  gateway:
    expose: ["8080"]
    labels:
      - "traefik.enable=true"
      - "traefik.http.routers.<project>.rule=Host(`<project>.local`)"
      - "traefik.http.services.<project>.loadbalancer.server.port=8080"
    networks: [traefik-net, <project>-net]

  postgres:
    expose: ["5432"]
    networks: [<project>-net]

networks:
  traefik-net:
    external: true
  <project>-net:
    driver: bridge
```

---

# Implementation Notes

- Traefik v3.x 사용 (`traefik:v3` 태그). v2 와 라벨 syntax 호환되지만 v3 의 default config 가 더 sane.
- Traefik 의 docker provider 활성화: `--providers.docker=true --providers.docker.exposedByDefault=false`. 라벨 명시한 서비스만 라우팅됨.
- Traefik dashboard (`:8080`) 은 dev 편의를 위해 노출하되 prod-style 인증은 생략.
- 3개 프로젝트의 docker-compose 마이그레이션은 atomic 하게 같은 PR 에 묶음 (cross-project 변경의 정합성 보존 — `CLAUDE.md` § Cross-Project Changes).
- `.env.example` 의 `PORT_PREFIX` 제거 시 `.env` 캐시된 값이 있으면 삭제 가이드 README 에 명시.
- e2e-tests CI 잡 (TASK-MONO-014) 이 PORT_PREFIX 가정에 의존하면 같이 수정. 영향 분석 필수.

---

# Edge Cases

- 사용자가 이미 다른 도구 (Apache, Nginx 등) 가 호스트 :80/:443 점유 중일 때 → README 에서 Traefik 포트를 `:8800`/`:8843` 등으로 override 가능함을 명시
- `*.local` mDNS / Bonjour 충돌 (macOS 의 `.local` reserved 영역) → 대안 도메인 (예: `.localhost` 또는 `.test`) 검토. ADR 후속 보완.
- WSL2 + Docker Desktop 환경에서 hosts 파일 위치 (Windows 측 vs WSL 측) → setup script 가 양쪽 갱신
- Traefik 기동 전에 프로젝트가 먼저 `traefik-net` 에 join 시도 → 외부 네트워크가 존재하지 않아 실패. 시작 순서 README 에 명시 또는 docker-compose `external: true` 의존성으로 해결
- 기존 PORT_PREFIX 기반 e2e 테스트 (TASK-MONO-014/015) 는 docker-compose 의 `ports:` 를 가정 → 마이그레이션 시 함께 수정

---

# Failure Scenarios

- **Traefik dashboard 노출이 운영 ENV 로 leak**: dev 환경 docker-compose 에만 dashboard `:8080` 활성화하고, prod compose 로 복제되지 않도록 분리.
- **HTTPS 미적용 상태에서 staging URL 모방 어려움**: staging 환경은 별도 ADR 로 처리. 본 태스크는 dev 만.
- **로컬 hosts 파일 권한 부족 (특히 Windows)**: setup script 가 admin 권한 요청 또는 manual 단계로 fallback.
- **mDNS/.local 도메인 인식 실패 (macOS)**: 도메인 자체를 `.localhost` 로 변경하는 follow-up. 본 태스크에서 발견 시 ADR 보강.

---

# Test Requirements

- 통합 검증 (수동 또는 스크립트):
  - `docker compose -f infra/traefik/docker-compose.yml up -d`
  - 3개 프로젝트 `docker compose up -d` 동시 실행
  - `curl http://ecommerce.local/actuator/health` → 200
  - `curl http://wms.local/actuator/health` → 200
  - `curl http://gap.local/actuator/health` → 200
  - `docker port` 출력에서 호스트 포트 점유: Traefik `:80`, `:443`, `:8080` 만
- CI 영향 검증:
  - `frontend-e2e` (TASK-MONO-014) 잡이 새 모델에서 통과하거나 별도 follow-up 으로 분리됨
  - `gap-integration-tests` 잡 (Testcontainers 사용 — 호스트 포트와 무관) 영향 없음 확인

---

# Definition of Done

- [ ] Implementation completed (Traefik infra + 3개 프로젝트 docker-compose 마이그레이션 + setup script + 가이드)
- [ ] 동시 기동 검증 통과 (3개 프로젝트 + Traefik)
- [ ] 호스트 포트 점유 = Traefik 만 확인
- [ ] Documentation (README + dev-tooling.md) 갱신
- [ ] CI 잡 영향 분석 + 필요 수정 또는 follow-up 분리
- [ ] Ready for review
