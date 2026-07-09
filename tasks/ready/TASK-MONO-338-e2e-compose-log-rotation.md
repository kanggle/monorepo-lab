# Task ID

TASK-MONO-338

# Title

e2e compose 서비스에 컨테이너 로그 로테이션(`logging: max-size/max-file`) 적용 — 무제한 JSON 로그로 인한 디스크 고갈 안전망

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- chore

---

# Goal

`tests/federation-hardening-e2e/docker/`의 compose 서비스 **29개 전부가 로그 상한이 없다**(`logging:` 선언 0건). 도커 `json-file` 드라이버는 `max-size` 미지정 시 **무제한 append**이므로, 어떤 서비스가 stdout으로 로그를 폭주시키면 컨테이너 로그 파일 하나가 디스크를 전부 삼킨다.

**2026-07-10 실측 사고**: `victoriatraces`(트레이스 백엔드)가 부분 기동으로 누락된 채 36시간 방치 → 서비스들이 존재하지 않는 호스트로 OTLP span export를 반복 시도하며 실패 스택트레이스를 초당 ~15회(≈6KB/회) 기록. `/var/lib/docker/containers` = **17.1GB**(scm-demand-planning 10.8GB, scm-inventory-visibility 5.6GB). `docker system df`는 이 영역을 집계하지 않아 진단에서 놓치기 쉬웠다. 회수에 zero-fill + `diskpart compact vdisk` 전체 사이클(데모 중단 포함)이 필요했다.

근본 원인(누락 컨테이너)은 해소됐으나, **"상한 없음"이라는 증폭 조건은 그대로 남아 있다.** 이 task는 그 조건을 제거하는 **안전망**이다 — 버그 수정이 아니라 폭발 반경 제한(17GB → 서비스당 150MB 상한).

---

# Scope

## In Scope

- **git-tracked compose 3파일**의 전 서비스에 `logging:` 적용:
  - `docker-compose.federation-e2e.yml` — 19 서비스
  - `docker-compose.federation-e2e.demo.yml` — 6 서비스
  - `docker-compose.federation-e2e.replenishment.yml` — 4 서비스
- base 파일에 YAML 앵커 정의 후 각 서비스에서 참조. 앵커 스타일은 **기존 선례를 따른다**(`docker-compose.federation-e2e.yml:67` `x-iam-service-env: &iam-service-env`).

```yaml
x-logging: &default-logging
  driver: json-file
  options:
    max-size: "50m"
    max-file: "3"

services:
  scm-demand-planning-service:
    logging: *default-logging
```

- 오버레이(`demo`/`replenishment`)에서 **새로 정의되는** 서비스는 base의 앵커를 못 쓰므로(파일 분리 = 앵커 스코프 분리) 각 파일에 앵커를 재선언하거나 리터럴로 명시. 구현 시 택일하고 근거 기록.

## Out of Scope

- **untracked 로컬 오버레이** — `docker-compose.federation-e2e.ecommerce.yml`, `ecommerce-extra.yml`, `erp-fullstack.yml`, `ledger.yml`, `zz-*.yml`은 git에 없다(로컬 데모 스캐폴딩). 커밋 금지. 이 파일들의 서비스는 로테이션 미적용 상태로 남으며, 이는 **의도된 잔여 위험**(§ Edge Cases).
- CI 워크플로 / nightly. compose 파일만.
- 로그 드라이버 교체(`local` 등) 또는 중앙 수집(Loki/Vector) 도입 — 별개 논의.
- `victoriatraces` 누락 재발 방지 — 이미 해소(compose에 정식 정의돼 있고 정상 기동 시 생성됨).

---

# Acceptance Criteria

- [ ] tracked 3파일의 **29개 서비스 전부**에 `logging:` 존재. (`grep -c 'logging:'` = 서비스 수 + 앵커 정의분)
- [ ] **컨테이너 재생성 후** 검증: `docker inspect --format '{{.HostConfig.LogConfig.Config}}' <컨테이너>` → `map[max-file:3 max-size:50m]`. **`map[]`이면 실패.**
- [ ] `docker compose config`가 3파일 조합에서 파싱 성공(앵커 스코프 오류 0).
- [ ] 스택 기동 후 전 서비스 healthy(동작 불변 — `logging:`은 런타임 동작에 영향 없음).
- [ ] `tasks/INDEX.md` done entry.

---

# Related Specs

- Memory `env_docker_container_json_log_unbounded_otlp_spam` (사고 실측·원인 분석)
- Memory `env_rancher_desktop_vhdx_no_shrink` (회수 비용 = compact 전체 사이클)

# Related Skills

N/A — compose YAML 편집.

---

# Related Contracts

None.

---

# Target Service

N/A — e2e 테스트 하네스의 compose 설정만.

---

# Architecture

N/A — 설정 추가, 동작 불변, ADR 없음.

---

# Implementation Notes

- **`LogConfig`는 컨테이너 생성 시점에 고정된다.** compose 파일만 고치고 `docker start`하면 **효과가 전혀 없다**. 반드시 `docker compose up -d --force-recreate`(또는 `down` 후 `up`)로 재생성해야 하며, AC의 `docker inspect` 검증이 이걸 강제한다.
- **데몬 기본값(`/etc/docker/daemon.json`의 `log-opts`)으로는 못 푼다** — 이 호스트(Rancher Desktop)는 기동 때마다 daemon.json을 재생성해 사용자 설정을 지운다(2026-07-10 실증). 그래서 compose 레벨이 유일하게 durable한 지점이다.
- **재생성 = 데모 중단**. 현재 로컬에 fed-hardening-e2e 데모가 상주하므로, 검증 시 티어 순서 재기동 필요(인프라 → 서비스 → 게이트웨이 마지막). Memory `env_rancher_desktop_vhdx_no_shrink` § 2026-07-10 절차 참조. 일괄 `docker start`는 `depends_on`을 무시해 Exit(255) 떼죽음을 부른다.
- 50m×3 = 서비스당 최대 150MB, 29서비스 = 최악 4.35GB. 정상 운영 로그 며칠치는 보존된다.

---

# Edge Cases

- **앵커 스코프**: YAML 앵커는 파일 경계를 넘지 못한다. base에서 `&default-logging`을 정의해도 `demo.yml`에서 `*default-logging` 참조 시 파싱 에러. 오버레이마다 재선언 필요.
- **untracked 오버레이 서비스는 여전히 무제한**. ecommerce 6서비스 등이 폭주하면 이 task의 보호를 받지 못한다. 해당 파일들이 언젠가 tracked가 되면 그때 함께 적용.
- **`docker compose up --force-recreate`가 볼륨은 보존**하나 컨테이너는 새로 만든다 → 컨테이너 내부에 쌓인 임시 상태(예: 수동 SQL seed)는 소실. 로컬 데모에 미커밋 seed가 있다면 사전 확인.
- 상한 도달 시 **가장 오래된 로그부터 삭제**되므로, 장기 실행 후 장애 조사 시 초기 스택트레이스를 못 볼 수 있다.

---

# Failure Scenarios

- **파일만 고치고 재생성 누락** → `LogConfig` = `map[]`, 보호 0인데 "적용됐다"고 오인. 완화: AC의 `docker inspect` 검증이 필수 게이트.
- 앵커 오타/스코프 오류 → `docker compose config` 실패로 즉시 발현(저위험).
- `max-size` 과소 설정(예: 5m) → 정상 로그가 조기 소실돼 디버깅 저해. 50m 기준 유지.

---

# Test Requirements

- `docker compose -f base -f demo -f replenishment config` 파싱 성공.
- `--force-recreate` 후 전 서비스 healthy.
- 임의 3개 컨테이너에 대해 `docker inspect … LogConfig.Config` = `map[max-file:3 max-size:50m]`.

---

# Definition of Done

- [ ] tracked 3파일 29서비스에 `logging:` 적용.
- [ ] 재생성 후 `docker inspect` 검증 통과(`map[]` 아님).
- [ ] 전 서비스 healthy(동작 불변).
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

Surfaced 2026-07-10 — 사용자 "도커 용량 정리" 요청 중 발견. `docker system df`가 보고한 13GB와 실제 ext4 27.6GB의 차액이 전부 컨테이너 JSON 로그(17.1GB)였고, 원인은 `victoriatraces` 누락으로 인한 OTLP export 실패 스택트레이스 무한 누적. 로그 truncate(17.5GB 회수) + `diskpart compact`(vhdx 41.75→14.85GB, C: +27.9GB)로 회수하고 누락 컨테이너를 채워 근본 원인은 해소. 남은 증폭 조건("상한 없음") 제거를 사용자와 논의 후 백로그 등록. **안전망 성격 — 대기 중인 MONO-328/330보다 우선순위 낮음.**

분석=Opus 4.8 / 구현 권장=Sonnet (기계적 compose 설정 반복; 재생성 검증만 주의).
