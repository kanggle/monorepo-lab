# Task ID

TASK-MONO-483

# Title

`docker-cleanup.sh`에 익명(anonymous) dangling 볼륨 자동 정리 추가

# Status

in-progress

# Owner

monorepo

# Task Tags

- infra
- docker
- cleanup

---

# 배경

`scripts/docker-cleanup.sh`(TASK-MONO-391)는 매주 컨테이너/dangling 이미지/빌드캐시(나이 기준)를
자동 정리하고 컨테이너 JSON 로그를 감시한다. 그러나 **볼륨은 전혀 건드리지 않는다.**

2026-07-28 수동 진단에서 `docker volume ls -f dangling=true`가 47개를 반환했는데, 그중 29개는
Testcontainers가 남긴 **익명 볼륨**(이름이 64자리 hex, 예: `1d80db4ee14b...`)이고 나머지 18개는
`federation-hardening-e2e_ecommerce-*-postgres-data`, `iam_mysql-data`, `iamsocial_kafka-data`,
`smoke-*_redis-data` 같은 **이름 있는(named) 데모/시드 데이터 볼륨**이었다. 익명 29개만 삭제하고
named 18개는 보존한 결과 1.1GB를 안전하게 회수했다(`docker volume rm`은 실행 중인 컨테이너가
참조하는 볼륨은 애초에 dangling으로 안 잡히므로 추가 안전장치 불필요).

이 판별(익명=64자리 hex 정규식 vs named=사람이 붙인 이름)은 재현 가능하고 안전이 실증됐으므로
주간 스크립트에 편입한다.

# Goal

`scripts/docker-cleanup.sh`의 기본 실행 경로에 **익명 dangling 볼륨만** 자동 삭제하는 단계를
추가한다. named 볼륨(데모 시드 데이터 등)은 이 스크립트가 앞으로도 절대 건드리지 않는다.

# Scope

## In Scope

- `scripts/docker-cleanup.sh`:
  - 익명 볼륨 판별 함수 추가 — `docker volume ls -f dangling=true --format '{{.Name}}'` 중
    이름이 `^[0-9a-f]{64}$` 정규식에 매치하는 것만 대상.
  - 기본 실행 경로(옵션 없음)에서 위 대상만 `docker volume rm`으로 삭제. named dangling 볼륨은
    건드리지 않고 개수만 보고.
  - `--dry-run`에서 삭제 대상 개수·목록과 보존되는 named 볼륨 개수를 출력(실제 삭제 없음).
  - "청소 전/후" `docker system df` 출력 사이에 볼륨 정리 섹션 추가(기존 컨테이너/이미지/캐시
    섹션과 동일한 출력 스타일 — 무엇을 왜 지우는지 침묵하지 않는다, 기존 스크립트 관례 유지).

## Out of Scope

- named 볼륨 삭제(수동 전용 유지 — 데이터 손실 위험).
- vhdx compact 자동화(기존처럼 관리자 수동 전용 유지).
- `--images`(안 쓰는 태그 이미지) 자동화 확대 — 이번 티켓과 무관, 동작 변경 없음.

# Acceptance Criteria

**AC-1 — named 볼륨 미삭제 실증.**
named dangling 볼륨이 최소 1개 존재하는 상태(또는 테스트용 named 볼륨을 만들어)에서 스크립트를
기본 실행한 뒤 `docker volume ls`로 그 named 볼륨이 **여전히 존재**함을 확인한다. 정규식이 아니라
실제 `docker volume ls` 대조로 검증한다(가정 금지).

**AC-2 — 익명 볼륨 실삭제.**
익명(64자리 hex) dangling 볼륨을 최소 1개 만든 뒤(예: `docker volume create`로 생성 후 즉시
미사용 상태로 두거나, `docker run --rm -v /tmp busybox true`로 익명 볼륨 생성) 기본 실행 후
`docker volume ls`에서 사라졌는지 확인한다.

**AC-3 — `--dry-run` 무변경.**
`--dry-run` 실행 전후로 `docker volume ls` 출력이 동일함(아무것도 안 지워짐)을 확인하고, 출력에
삭제 예정 개수가 표시됨을 확인한다.

**AC-4 — 기존 섹션 회귀 없음.**
컨테이너 prune / dangling 이미지 prune / 빌드캐시 나이컷 / 로그 감시 섹션의 동작·출력 포맷이
이번 변경으로 바뀌지 않았음을 스크립트 실행 결과로 확인한다.

# Related Specs

없음 — project-agnostic 유지보수 스크립트(`scripts/`)이며 특정 프로젝트 도메인 지식 불필요.

# Related Contracts

없음 — 인프라 전용, API/이벤트 계약 무변경.

# Edge Cases

- **익명 dangling 볼륨이 0개** — "없음" 명시 출력(침묵 금지, 로그 감시 섹션과 동일 관례).
- **named 볼륨 이름이 우연히 64자리 hex 패턴과 일치** — Docker가 실제로 부여하는 익명 볼륨 ID
  포맷과 동일한 패턴이므로 이론상 사용자가 그런 이름을 명시적으로 지정할 수도 있다. 이 경우
  오탐 삭제 위험이 있음을 스크립트 주석에 남긴다(정규식 판별의 알려진 한계).
- **도커 데몬 다운** — 기존처럼 스크립트 최상단에서 명확히 에러 종료(변경 없음).

# Failure Scenarios

- **정규식이 named 볼륨과 오매치되어 데모 시드 데이터 삭제** — 되돌릴 수 없는 데이터 손실. AC-1이
  실측으로 이를 막는다(가정이 아니라 실제 `docker volume ls` 대조).
- **`--dry-run`과 실제 실행 로직이 분리 구현되어 서로 다른 대상을 판별** — dry-run은 안전해
  보이지만 실제 실행에서 다른 걸 지움. 동일한 익명-판별 함수를 dry-run/실제 실행 양쪽에서
  재사용하여 방지(AC-3에서 목록이 실제 삭제 대상과 일치함을 확인).

# Notes

- 분석 = Sonnet 5(본 세션) / 구현 권장 = Sonnet — 기존 스크립트(컨테이너/이미지 prune)와 동일한
  패턴을 볼륨에 한 겹 더 얹는 정형 작업, 복잡한 설계 판단 불필요.
- 2026-07-28 세션에서 수동으로 동일 로직(`docker volume ls -f dangling=true --format '{{.Name}}'
  | grep -E '^[0-9a-f]{64}$'`)을 실행해 29개/47개를 안전하게 분리한 실증 있음 — 그 로직을 그대로
  스크립트에 이식하면 된다.
- 관련 메모리: `env_rancher_desktop_vhdx_no_shrink`(VM 내부 prune과 호스트 C: 회수는 별개),
  `env_docker_container_json_log_unbounded_otlp_spam`(같은 스크립트의 로그 감시 설계 근거),
  `feedback_prune_old_image_after_rebuild`(같은 스크립트의 이미지 prune 설계 근거).
