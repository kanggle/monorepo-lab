# Task ID

TASK-MONO-204

# Title

scripts/docker-cleanup.sh 추가 — 안 쓰는 컨테이너/dangling 이미지/빌드캐시 일괄 회수 유틸 (Rancher Desktop WSL2 vhdx 증식 억제용)

# Status

review

# Owner

infra (Opus 4.8 analysis / Sonnet 4.6 impl). monorepo-level shared (`scripts/`). No project code change.

# Task Tags

- code
- infra

---

# Dependency Markers

- **2026-06-10 디스크 관리 요청**: 이 호스트(Windows + Rancher Desktop WSL2)는 Docker vhdx 가 한 번 커지면 prune 만으론 C: 로 안 돌아옴(=프로젝트 메모리 `env_rancher_desktop_vhdx_no_shrink`). 반복 재빌드로 dangling 이미지·빌드캐시가 누적돼 vhdx 가 증식하는 걸 한 줄로 정리하는 유틸 필요.
- **relates**: `compact-rd-vhdx.ps1`(root, untracked — C: 실제 회수=vhdx compact, 별건), feedback 메모리 `feedback_prune_old_image_after_rebuild`.

# Goal

`scripts/docker-cleanup.sh` 추가 — `docker container prune` + `docker image prune`(dangling) + `docker builder prune` 를 안전하게 일괄 실행하고 전후 `docker system df` 를 보여준다. `--dry-run`(회수 가능량만) / `--images`(안 쓰는 태그 이미지까지 `-af`) 옵션 제공.

# Scope

## In Scope

- **`scripts/docker-cleanup.sh`** (신규): `set -euo pipefail`, 옵션 파싱(`--dry-run`/`--images`/`-h`), 전후 `docker system df`, 3종 prune(container/image dangling/builder), `--images` 시 `image prune -af` 추가, 마지막에 "C: 실제 회수는 compact 필요" 안내.

## Out of Scope

- vhdx compact 자체(별건 `compact-rd-vhdx.ps1`, 관리자 권한).
- named 볼륨 prune(데이터 손실 위험 — 의도적으로 미포함; 기본 동작은 "안 쓰는 것만" 안전 회수).
- Windows Task Scheduler 등록(선택 운영 — 본 task 범위 아님).
- `.ps1` 버전(현재 .sh 단일; 필요 시 후속).

# Acceptance Criteria

- [ ] `scripts/docker-cleanup.sh` 존재, `bash -n` 구문 OK.
- [ ] 옵션 없이 = container/dangling-image/builder prune 3종 + 전후 df.
- [ ] `--dry-run` = 회수 가능량(df)만 표시, prune 미실행.
- [ ] `--images` = 안 쓰는 태그 이미지까지(`-af`) 추가 정리.
- [ ] 실행 중 컨테이너·그 이미지·named 볼륨은 보호(안전 — prune 의 기본 동작).
- [ ] 마지막에 "C: 회수는 compact 별건" 안내 출력.

# Related Specs

- 없음 (운영 유틸, 계약 무관).

# Related Contracts

- 변경 없음.

# Target Service

- 없음 (repo-root `scripts/docker-cleanup.sh` 단일 신규 파일).

# Architecture

- Rancher Desktop WSL2 백엔드에서 Docker 데이터는 2차-attach `distro-data\ext4.vhdx` 에 있고, prune 은 VM 내부(ext4) free 블록만 회수한다 → 호스트 vhdx 는 안 줄어듦(C: 불변). 따라서 이 스크립트의 역할은 "C: 회수"가 아니라 "vhdx 증식 억제"(누적 dangling/캐시 제거)이며, 실제 C: 회수는 zero-fill+compact(`compact-rd-vhdx.ps1`, 관리자)로 분리된다. 안전 원칙: "안 쓰는 것만" 회수(running/named-volume 보호).

# Edge Cases

- Hyper-V 소켓 일시 타임아웃(`0x8007274c`)으로 일부 prune 실패 가능 → `set -e` 로 중단(재실행). 메모리 `env_rancher_desktop_vhdx_no_shrink`/`env_console_demo_local_redeploy` 참조.
- `--images`(`-af`)는 떠있는 데모가 쓰지 않는 태그 이미지도 지움 → 재빌드/재pull 필요할 수 있어 기본 비활성(옵트인).

# Failure Scenarios

- named 볼륨까지 prune 하면 DB 데이터 손실 → 의도적으로 미포함(scope-out). AC 가 "named 볼륨 보호" 단언.
- compact 없이 C: 회수 기대 → 안내 문구로 오해 방지(이 스크립트는 vhdx 내부만).

# Definition of Done

- [ ] `scripts/docker-cleanup.sh` 추가 + `bash -n` OK
- [ ] 옵션(`--dry-run`/`--images`) 동작
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
