# Task ID

TASK-MONO-022

# Title

Traefik 인프라 신설 (Phase 1) — `infra/traefik/` 스택 + dev-tooling 가이드

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

[ADR-MONO-001](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) (ACCEPTED, Option C) 의 Phase 1 작업: **Traefik 공유 reverse proxy 스택을 monorepo 루트에 신설** 한다. 기존 3개 프로젝트의 docker-compose 는 본 태스크에서 건드리지 않는다 (TASK-MONO-024 가 책임).

이 태스크 완료 후:

- 루트 `infra/traefik/docker-compose.yml` 가 단일 Traefik v3 인스턴스를 운영 (`:80`/`:443`/`:8080` dashboard)
- 외부 docker network `traefik-net` 가 생성되어, 프로젝트들이 join 할 수 있는 상태
- 신규 프로젝트 (fan-platform·scm·erp·mes) 가 부트스트랩 시점부터 Traefik 패턴으로 시작 가능
- DB 도구 접근 (DBeaver / Redis Insight / Kafka UI) 의 3가지 방법 가이드 작성
- 일회성 dev 환경 셋업 스크립트 (`/etc/hosts` 등록) 제공

기존 프로젝트의 docker-compose 마이그레이션은 TASK-MONO-024 (Phase 2) 에서 다룬다.

---

# Scope

## In Scope

- 루트에 `infra/traefik/` 디렉토리 신설:
  - `docker-compose.yml` — Traefik v3 single instance, dashboard `:8080` 노출, docker provider 활성화 (`exposedByDefault=false`), `traefik-net` external network 정의
  - `traefik.yml` 또는 inline command — entrypoints (`web :80`, `websecure :443`), providers (docker), api (insecure dashboard for dev)
  - `README.md` — 기동·종료 명령, dashboard 접근 방법, 트러블슈팅
- 루트 `package.json` 또는 `Makefile` 에 다음 스크립트 추가:
  - `traefik:up` — `docker compose -f infra/traefik/docker-compose.yml up -d`
  - `traefik:down` — `docker compose -f infra/traefik/docker-compose.yml down`
  - `traefik:logs` — `docker compose -f infra/traefik/docker-compose.yml logs -f`
- `docs/guides/dev-tooling.md` 신설 — DB 도구가 내부 네트워크 서비스에 접근하는 3가지 방법:
  - 방법 1: `docker exec` (가장 안전, 외부 도구 불필요)
  - 방법 2: 프로젝트별 `docker-compose.dev.yml` overlay (commit 안 하는 로컬 dev 전용 ports 노출)
  - 방법 3: Traefik TCP 라우팅 (정식 — 프로젝트의 Traefik 라벨에 TCP entryPoint 등록)
- `scripts/dev-setup.sh` (or `.ps1` for Windows) — `*.local` 호스트명을 `/etc/hosts` 에 idempotent 하게 등록하는 helper:
  - `ecommerce.local`, `wms.local`, `gap.local` 우선 등록 (현재 운영 중 — Phase 2 마이그레이션 전이라도 미래 대비)
  - 스크립트가 admin/sudo 권한 요청
  - 이미 등록된 항목은 건너뜀
- 루트 `README.md` 에 dev 환경 셋업 절차 단계 추가:
  1. `bash scripts/dev-setup.sh` (1회)
  2. `pnpm traefik:up` (Traefik 기동)
  3. 프로젝트 띄우기

## Out of Scope

- 기존 3개 프로젝트의 docker-compose 마이그레이션 — TASK-MONO-024 (Phase 2)
- HTTPS / TLS 적용 (자체 서명 인증서 + Traefik certResolver) — TASK-MONO-025 follow-up
- Traefik dashboard 인증 — dev 환경이라 비활성. 운영 환경 채택 시 별도
- production K8s Ingress 매핑 — 본 태스크는 로컬 dev 만
- service mesh (Istio/Linkerd) — overkill, 본 태스크 범위 밖
- 5자리 host port (Jaeger UI `16686` 등) 의 Traefik 노출 — 옵션 (별도 가이드 노트)
- 4 신규 프로젝트 (fan-platform/scm/erp/mes) 의 docker-compose 작성 — 각 프로젝트 부트스트랩 태스크 책임 (Traefik 인프라가 준비되어 있으니 day-1 부터 Traefik 패턴 채택)

---

# Acceptance Criteria

- [ ] `infra/traefik/docker-compose.yml` 가 Traefik v3 단일 인스턴스를 정의하고 `traefik-net` external network 를 생성
- [ ] Traefik dashboard 접근 가능 (`http://localhost:8080`)
- [ ] `pnpm traefik:up` / `pnpm traefik:down` / `pnpm traefik:logs` 스크립트 동작
- [ ] `docs/guides/dev-tooling.md` 가 DB 도구 접근 3가지 방법을 문서화
- [ ] `scripts/dev-setup.sh` (or `.ps1`) 가 hosts 파일 등록을 idempotent 하게 처리 (재실행 안전)
- [ ] 루트 README.md 가 새로운 dev 환경 셋업 절차를 안내
- [ ] 본 태스크는 **기존 3 프로젝트의 docker-compose 를 건드리지 않음** (Phase 2 와의 분리 보장)
- [ ] Traefik 기동 후 `docker network inspect traefik-net` 으로 외부 네트워크 존재 확인

---

# Related Specs

- [docs/adr/ADR-MONO-001-port-prefix-scaling.md](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) — 결정 근거 (Option C)
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
- 루트 `package.json` (스크립트 추가) 또는 `Makefile`
- 루트 `docs/guides/dev-tooling.md` (신규)
- 루트 `scripts/dev-setup.sh` (or `.ps1`, 신규)
- 루트 `README.md` (단계 추가)

---

# Architecture

`docs/adr/ADR-MONO-001-port-prefix-scaling.md` § Recommendation 의 Option C 패턴:

```yaml
# infra/traefik/docker-compose.yml
services:
  traefik:
    image: traefik:v3
    command:
      - "--api.insecure=true"
      - "--providers.docker=true"
      - "--providers.docker.exposedByDefault=false"
      - "--entrypoints.web.address=:80"
      - "--entrypoints.websecure.address=:443"
    ports:
      - "80:80"
      - "443:443"
      - "8080:8080"   # dashboard (dev only)
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock:ro
    networks:
      - traefik-net

networks:
  traefik-net:
    name: traefik-net
    driver: bridge
```

신규 프로젝트가 `traefik-net` 에 join 할 때:

```yaml
# projects/<new>/docker-compose.yml
networks:
  default:
    name: <project>-net
  traefik-net:
    external: true
```

---

# Implementation Notes

- Traefik v3.x 사용 (`traefik:v3` 태그). v2 와 라벨 syntax 호환되지만 v3 의 default config 가 더 sane.
- Docker provider 활성화 + `exposedByDefault=false` — 라벨 명시한 서비스만 라우팅됨 (보안 + 명시성).
- Traefik dashboard (`:8080`) 은 dev 편의를 위해 노출하되 prod-style 인증은 생략.
- 본 태스크는 **기존 프로젝트 docker-compose 를 안 건드리는 게 핵심** — Phase 1/2 분리의 목적은 인프라 신설을 작은 PR 로 분리해 신규 프로젝트가 day-1 부터 Traefik 사용 가능하게 만드는 것.
- `scripts/dev-setup.sh` 는 macOS/Linux 용. Windows 는 PowerShell 스크립트 (`scripts/dev-setup.ps1`) 별도. 둘 다 hosts 파일 등록만 다루고 docker 실행은 사용자가 직접.
- `traefik-net` 이름은 `external: true` 로 참조될 때 정확히 "traefik-net" 이어야 함 — `infra/traefik/docker-compose.yml` 에 `networks.traefik-net.name: traefik-net` 명시.

---

# Edge Cases

- 사용자가 이미 다른 도구 (Apache, Nginx 등) 가 호스트 :80/:443 점유 중일 때 → README 에서 Traefik 포트를 환경변수로 override 가능함을 명시 (`TRAEFIK_HTTP_PORT=8800`)
- `*.local` mDNS / Bonjour 충돌 (macOS 의 `.local` reserved 영역) → 대안 도메인 (예: `.localhost` 또는 `.test`) 검토. ADR 후속 보완.
- WSL2 + Docker Desktop 환경에서 hosts 파일 위치 (Windows 측 vs WSL 측) → setup script 가 양쪽 갱신
- Traefik 미기동 상태에서 프로젝트가 `traefik-net` external 참조 → 프로젝트 docker compose 가 fail. README 가이드: "프로젝트 기동 전 `pnpm traefik:up` 먼저"
- dashboard `:8080` 이 다른 서비스와 충돌 가능 → README 에 환경변수 override (`TRAEFIK_DASHBOARD_PORT`) 명시

---

# Failure Scenarios

- **Traefik dashboard 노출이 운영 ENV 로 leak**: dev 환경 docker-compose 에만 dashboard `:8080` 활성화하고, prod compose 로 복제되지 않도록 분리.
- **로컬 hosts 파일 권한 부족 (특히 Windows)**: setup script 가 admin 권한 요청 또는 manual 단계로 fallback.
- **mDNS/.local 도메인 인식 실패 (macOS)**: 도메인 자체를 `.localhost` 로 변경하는 follow-up. 본 태스크에서 발견 시 ADR 보강.

---

# Test Requirements

- 검증 (수동 또는 스크립트):
  - `pnpm traefik:up` → Traefik 컨테이너 실행 + `:80`/`:443`/`:8080` 호스트 포트 점유 확인
  - `docker network inspect traefik-net` → 네트워크 존재 + 드라이버 bridge 확인
  - `curl http://localhost:8080/api/rawdata` → Traefik API 응답 (dashboard 동작 신호)
  - `pnpm traefik:down` → 컨테이너 + 네트워크 정상 정리
  - 기존 ecommerce/wms/GAP 프로젝트 `docker compose up -d` 가 영향 없이 동작 (Phase 1 의 격리성 검증)
- CI 영향: 기본 검증 외에 별도 CI 잡 추가 불필요. Phase 2 (TASK-MONO-024) 에서 e2e 잡 영향 분석.

---

# Definition of Done

- [ ] Implementation completed (Traefik infra + setup script + 가이드 + README 갱신)
- [ ] 검증 명령 통과
- [ ] 기존 3 프로젝트 docker-compose 영향 없음 확인
- [ ] Documentation (README + dev-tooling.md) 갱신
- [ ] TASK-MONO-024 (Phase 2) 가 본 태스크의 산출물 위에서 진행될 수 있음 명시
- [ ] Ready for review
