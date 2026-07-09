# Task ID

TASK-MONO-336

# Title

Assemble an integrated all-projects demo orchestration (`infra/demo/`) for the on-demand portfolio demo host

# Status

done

# Owner

devops

# Task Tags

- deploy
- code

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

포트폴리오 온디맨드 데모 호스트(EC2, scale-to-zero)에서 **한 명령으로 8개 프로젝트 전체 + 공유 Traefik 을 기동**할 수 있는 통합 오케스트레이션을 `infra/demo/` 에 신설한다.

핵심 불변식: **기존 `projects/*/docker-compose.yml` 와 `infra/traefik/docker-compose.yml` 는 byte-unchanged 로 유지**한다. 각 프로젝트를 **자신의 compose 프로젝트(`-p <slug>`)** 로 공유 external `traefik-net` 위에 띄우는 얇은 래퍼 스크립트로 결합해, 각 프로젝트의 단독 기동 능력과 통합 데모가 하나의 소스에서 이중관리 없이 공존하도록 한다.

완료 시: 데모 호스트에서 `bash infra/demo/demo-up.sh full` 로 8개 프로젝트가 뜨고, 온디맨드 호스트의 `demo-stack.service` 가 부팅 시 이를 호출한다.

---

# Design Decision (empirical — supersedes the original "single include: file" premise)

원안은 단일 `infra/demo/docker-compose.demo.yml` 이 프로젝트 compose 들을 `include:` 로 묶는 것이었으나, **실측으로 불가 판정**:

- `docker compose` 의 `include:` 와 `-f` 는 **같은 서비스 키를 조용히 하나로 병합**한다 (probe: `include`=첫째 승, `-f`=마지막 승, 에러 없음).
- 8개 프로젝트는 서로 다른 컨테이너인데도 제네릭 키를 공유한다 — `redis`×7, `kafka`×6, `postgres`×3, `mysql`×3, `grafana`×3, `notification-service`×3.
- 따라서 단일 병합 파일은 7개 redis 중 6개를 소리없이 잃어 대부분 도메인이 안 뜬다.

→ **프로젝트당 별도 `-p` 만이** byte-unchanged 로 전부 살린다. 전제(코드 조사로 확인)는 이미 충족: 모든 `container_name` 프로젝트-슬러그 프리픽스(충돌 0) · host `ports:` 는 traefik/ecommerce-jaeger 뿐(충돌 0) · `traefik-net` 은 8프로젝트 모두 `external: true` 참조(정의자=infra/traefik).

---

# Scope

## In Scope

- `infra/demo/demo-up.sh` — 프로파일별로 프로젝트 집합을 `docker compose -p <slug> -f projects/<n>/docker-compose.yml up -d` 로 기동 (traefik 먼저, console 마지막)
- `infra/demo/demo-down.sh` — 역순 전체 종료 (`KEEP_TRAEFIK` 옵션)
- `infra/demo/README.md` — 근거·사용법·프로파일·검증 상태·남은 작업
- 프로파일 2종: `demo-core`(iam+ecommerce+wms+console) / `full`(8)
- 로컬 검증: 9 compose `docker compose config` 렌더 + 스크립트 `bash -n`

## Out of Scope

- 기존 프로젝트 compose / `infra/traefik/` 수정 (byte-unchanged 불변식)
- cross-domain federation env 배선(OIDC issuer/per-domain base URL/seed) — `tests/federation-hardening-e2e/` 오버레이(MONO-170/174)가 6도메인에 이미 해둔 것과 겹침; 데모 호스트 정식화 시 그 env 를 프로젝트별 `.env` 로 승격/재사용(중복 재구현 금지)
- AWS Terraform / AMI / start-stop Lambda (scratchpad PoC `ondemand-demo/`)
- 신규 애플리케이션 코드/서비스

---

# Acceptance Criteria

- [x] `infra/demo/demo-up.sh` / `demo-down.sh` / `README.md` 추가
- [x] 8개 프로젝트 compose + traefik 가 각각 `docker compose config` 로 오류 없이 렌더
- [x] 래퍼 스크립트 `bash -n` 문법 통과
- [x] `projects/*/docker-compose.yml` 및 `infra/traefik/` git diff == 0 (byte-unchanged)
- [x] `demo-core` / `full` 두 프로파일이 스크립트에 정의됨
- [ ] (EC2/CI 권위) `full` 실기동 후 8개 프로젝트 healthy — 로컬 41 JVM OOM 회피로 이 host 미실행
- [ ] (후속 증분) 콘솔 5/5 federated `available:true` — federation env 배선 필요

---

# Related Specs

> Monorepo-level task. Read root `tasks/INDEX.md` § "When to Use Root vs Project Tasks" first.

- `TEMPLATE.md` § Local Network Convention (Traefik hostname routing, `expose:` 규약)
- `docs/adr/ADR-MONO-001-port-prefix-scaling.md` (hostname routing Option C)
- `tests/federation-hardening-e2e/docker/docker-compose.federation-e2e.demo.yml` (federation env/seed 선례)
- `projects/*/docker-compose.yml`, `infra/traefik/docker-compose.yml` (기동 대상, 읽기 전용)

# Related Skills

- `.claude/skills/` — devops/deploy 관련

---

# Related Contracts

- 없음 (인프라 조립, API/event 계약 변경 없음)

---

# Target Service

- N/A (cross-project infra) — 산출물: `infra/demo/`

---

# Architecture

- 얇은 bash 래퍼가 프로젝트당 별도 compose 프로젝트를 기동. 단일 compose 병합 없음(위 Design Decision).
- `-f` 를 ROOT 절대경로로 전달 → project-directory 가 각 프로젝트 디렉터리로 잡혀 `.env` 로딩·상대 `build:` 컨텍스트가 올바르게 해소됨.

---

# Implementation Notes

- 기동 순서 iam→…→console (iam=OIDC IdP 선행, console=federation 소비자 후행). 종료는 역순.
- `full`(41 JVM 동시) RAM ~32–48GB → 저사양/로컬 OOM/exit137 위험. `demo-core` 부터 검증. 데모 호스트=`m6i.4xlarge`(64GB).
- 이미지: 데모 호스트 AMI 빌드 시 미리 pull/build → 부팅 시 빌드 0. 개발 중엔 `DEMO_BUILD=1`.
- 로컬 검증 함정: 이 host 는 Docker Desktop/Testcontainers FLAKY(권위 아님). 실기동 스모크는 EC2/CI 권위.

---

# Edge Cases

- 프로젝트별 private network 는 이름이 모두 고유(`iam-net`/`ecommerce-net`/…) → 별도 `-p` 라 충돌 없음
- `traefik-net` 은 정의자(infra/traefik) 먼저 up 해야 8프로젝트의 `external: true` 참조가 성립 → 래퍼가 traefik 선행
- console per-domain ops base URL 기본 `*.local` → federation env 배선(후속)에서 컨테이너 DNS/호스트로 재지정
- outbox placeholder Kafka 60s 블록 → federation 데모는 redpanda 편입(MONO-174) 재사용

---

# Failure Scenarios

- (원안 함정) include/-f 로 중복 키가 조용히 병합되어 도메인 미기동 → **본 task 가 `-p` 분리로 회피**(실측 확인)
- 8개 동시 기동 RAM 초과 → OOM/exit137 (ecommerce mass-redeploy OOM 선례) → `demo-core` 로 축소
- traefik-net 미생성 상태에서 프로젝트 up → external network not found → 래퍼가 traefik 선행 보장
- 기존 프로젝트 compose 무심코 수정 → byte-unchanged 위반 → `git diff --exit-code` 게이트

---

# Test Requirements

- 9 compose `docker compose config -q` 렌더 검증 (완료)
- `bash -n` 스크립트 문법 (완료)
- `git diff --exit-code projects/*/docker-compose.yml infra/traefik/` == 0 (완료)
- (EC2/CI) full/demo-core 실기동 healthcheck 스모크

---

# Definition of Done

- [x] `infra/demo/` 래퍼 + README 추가
- [x] config 렌더 + bash -n + byte-unchanged 검증
- [ ] (EC2/CI) 실기동 스모크 — 데모 호스트 정식화 시
- [ ] federation env 배선 후속 증분 티켓화(선택)
- [ ] Ready for review
