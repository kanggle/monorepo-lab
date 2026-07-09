# Task ID

TASK-MONO-339

# Title

fed-e2e 로컬 기동의 **완전성 보장** — 손으로 나열한 서비스 목록(14) ↔ compose 선언(19) 드리프트 제거 + 기동 후 누락 검출

# Status

review

# Owner

monorepo

# Task Tags

- infra
- chore

---

# Goal

`tests/federation-hardening-e2e/README.md`의 기동 절차는 **서비스를 손으로 나열**한다(Phase 1: 6개, Phase 2: 8개 = 14개). 그런데 `docker-compose.federation-e2e.yml`은 **19개**를 선언한다. 목록에 없는 5개:

```
victoriatraces
scm-inv-postgres
scm-inventory-visibility-service
wms-admin-postgres
wms-admin-service
```

**손으로 관리하는 목록은 compose 파일과 반드시 갈라진다.** 서비스를 compose에 추가해도 README를 고치지 않으면 그 서비스는 영원히 안 뜨고, **아무도 모른다** — 기동 절차가 "성공"으로 끝나기 때문이다.

**2026-07-10 실측 사고**: `victoriatraces`(트레이스 백엔드)가 이 드리프트로 인해 **컨테이너로 아예 생성된 적이 없었다**(`docker ps -a`에도 없음). 그런데 7개 서비스가 `MANAGEMENT_OTLP_TRACING_ENDPOINT: http://victoriatraces:10428/...`로 span export를 시도한다. 36시간 동안 초당 ~15회 실패 → 스택트레이스(≈6KB/회) 무한 기록 → 컨테이너 JSON 로그 **17.1GB**. 회수에 zero-fill + `diskpart compact vdisk` 전체 사이클(데모 중단 포함, C: +27.9GB)이 필요했다.

`scm-inventory-visibility-service`(5.6GB 로그)와 `wms-admin-service`(현재 `Exited(255)`)도 같은 드리프트의 피해자다 — 목록에 없어서 나중에 임시로 얹혔고, 그래서 상태가 제각각이다.

이 task는 **기동 절차에서 사람이 유지하는 서비스 목록을 없애고, 기동 후 누락을 기계적으로 검출**한다.

---

# Scope

## In Scope

- **tracked 파일만**: `tests/federation-hardening-e2e/README.md`, 신규 `scripts/fed-e2e-up.sh` (+ `scripts/fed-e2e-up.ps1` POSIX/PowerShell 짝 — `scripts/console-demo-up.{sh,ps1}` 선례를 따른다).
- **완전성 단언(핵심 AC)**: 기동 종료 직전, compose가 선언한 서비스 집합과 실제 running 컨테이너 집합을 비교해 **차집합이 비어있지 않으면 실패**한다.

```bash
declared=$(docker compose -p "$PROJ" -f "$BASE" config --services | sort)
running=$(docker compose -p "$PROJ" -f "$BASE" ps --services --status running | sort)
missing=$(comm -23 <(echo "$declared") <(echo "$running"))
[ -z "$missing" ] || { echo "[ERR] declared but not running: $missing" >&2; exit 1; }
```

- **README의 하드코딩 목록 제거**: seed 순서를 위해 Phase 1 / Phase 2 구분은 유지하되, Phase 2는 열거 대신 **전체 `up -d`**(Phase 1에서 이미 뜬 것은 no-op)로 바꾼다. 그러면 compose에 서비스를 추가하는 것만으로 기동에 자동 포함된다.
- 누락 5개가 기동 후 running 인지 확인(특히 `victoriatraces` — 이게 없으면 OTLP 스팸이 재발한다).

## Out of Scope

- **untracked 로컬 오버레이**(`docker-compose.federation-e2e.ecommerce.yml`, `erp-fullstack.yml`, `ledger.yml`, `zz-*.yml`). `tests/federation-hardening-e2e/.gitignore:7-17`이 **의도적으로** 무시하도록 결정한 파일들이다(근거: 메모리 `env_console_demo_local_redeploy`). 이 task는 그 결정을 뒤집지 않는다. 해당 오버레이가 추가하는 서비스는 완전성 단언 범위 밖(단언은 tracked base/demo 기준).
- **`infra/demo/demo-up.sh`로의 데모 이관** — 별개의 큰 증분. `projects/iam-platform/docker-compose.yml`과 `projects/wms-platform/docker-compose.yml`은 **인프라만** 정의하고 앱 서비스(auth/account/admin, wms-master/admin)가 없으며(파일 헤더에 의도 명시), federation env 배선도 미완이다(`infra/demo/README.md:63-70` "남은 작업"). 게다가 `full` 프로파일은 41 JVM이라 이 호스트에서 실기동 검증이 금지돼 있다(같은 README:60-61). § Related 참조.
- 로그 로테이션 — `TASK-MONO-338` 소관(증폭 조건 차단). 이 task는 **원인** 차단.
- CI 워크플로(`federation-hardening-e2e.yml`)는 자체적으로 compose를 호출하므로 무수정.

---

# Acceptance Criteria

- [ ] `tests/federation-hardening-e2e/README.md`에 **하드코딩된 서비스 열거가 0건**(Phase 2 기준). Phase 1은 seed 선행 조건상 최소 집합 유지 가능하나, 그 목록도 근거를 주석으로 남길 것.
- [ ] `scripts/fed-e2e-up.sh` + `.ps1`가 존재하고, 기동 후 **완전성 단언**을 수행한다.
- [ ] **음성 검증(필수)**: compose에 더미 서비스를 임시 추가하고 스크립트를 돌리면 **비-0으로 실패**하고 그 서비스명을 출력한다. (단언이 실제로 동작함을 증명 — 통과만 확인하면 `comm` 인자 순서 실수를 못 잡는다.)
- [ ] 스크립트 실행 후 `victoriatraces` 포함 19서비스 전부 running.
- [ ] `docker logs --since 3m <scm-demand-planning 계열 서비스> | grep -c "Failed to export"` = **0**.
- [ ] `bash -n scripts/fed-e2e-up.sh` 통과.
- [ ] `tasks/INDEX.md` done entry.

---

# Related Specs

- `tasks/ready/TASK-MONO-338-e2e-compose-log-rotation.md` — 같은 사고의 **증폭 조건** 차단(안전망). 이 task는 **원인** 차단. 둘은 독립이며 순서 무관.
- `tasks/done/TASK-MONO-336-integrated-demo-compose.md` + `infra/demo/README.md` — 장기적으로 데모는 `demo-up.sh`(프로젝트별 `-p` + Traefik)로 가야 한다. 그 전제(앱 서비스 정의 + federation env 승격)가 아직 없으므로 이 task는 **현재 fed-e2e 경로를 건전화**하는 데 한정한다.
- Memory `env_docker_container_json_log_unbounded_otlp_spam` (사고 실측)
- Memory `env_rancher_desktop_vhdx_no_shrink` (회수 비용)
- Memory `feedback_console_redeploy_no_deps_only` (`--no-deps`는 **단일 서비스 재배포 전용** — 최초 기동에 쓰면 `depends_on`이 무시돼 오늘 같은 누락이 생긴다)

# Related Skills

N/A — 스크립트 + 문서.

---

# Related Contracts

None.

---

# Target Service

N/A — e2e 하네스의 기동 절차.

---

# Architecture

N/A — 절차/스크립트, 동작 불변, ADR 없음.

---

# Implementation Notes

- **`docker compose up -d <svc>`는 기본적으로 `depends_on`을 따라 의존 서비스를 함께 생성한다.** 그럼에도 `victoriatraces`가 없었던 이유는 두 가지 중 하나다: (a) OTLP `depends_on`이 compose에 **나중에**(MONO-144) 추가됐고 기존 컨테이너는 재생성되지 않았다, (b) 어느 시점에 `--no-deps`로 기동했다. 어느 쪽이든 **완전성 단언이 있으면 즉시 드러난다** — 구현자는 원인 규명보다 단언 도입을 우선할 것.
- **`comm`은 정렬된 입력을 요구한다.** `sort` 누락 시 조용히 틀린 결과가 나온다 → 음성 검증 AC가 이걸 잡는다.
- `.ps1` 짝에서는 `comm`이 없으므로 `Compare-Object` 사용.
- 완전성 단언은 **`--status running`** 기준이어야 한다. `ps --services`만 쓰면 `Exited(255)` 컨테이너도 "있다"로 세어 `wms-admin-service` 같은 케이스를 놓친다.
- 스크립트는 `scripts/console-demo-up.sh`와 **연쇄 가능**해야 한다(그 스크립트는 베이스가 이미 UP임을 전제하고 preflight로 검사한다).

---

# Edge Cases

- **Phase 1 이전에 전체 `up -d`를 하면 seed 전에 console/producer가 뜬다.** 현행 README가 phase를 나눈 이유가 이것이므로, Phase 1(인프라 + IAM 3서비스) → seed → Phase 2(전체 `up -d`) 순서를 유지할 것.
- 로컬에 untracked 오버레이가 존재하면 `-f`에 포함되지 않은 서비스가 running 상태로 남는다. 단언은 **declared ⊄ running** 방향만 검사(반대 방향은 검사하지 않음) → 오탐 없음.
- `up -d`가 이미 healthy 인 컨테이너를 재생성하지 않으므로(config 해시 동일) 반복 실행은 멱등.
- 19서비스 동시 콜드 기동 = JVM 다수. 이 호스트에서 관측된 한계는 아니나(현재 16 + victoriatraces 상시 가동), 일괄 재빌드는 OOM 위험(메모리 `env_ecommerce_mass_redeploy_oom_docker_hang`) → 스크립트는 `--build`를 기본값으로 두지 말 것.

---

# Failure Scenarios

- **단언이 통과만 확인되고 실제로는 항상 참** (`comm` 인자 뒤바뀜, 정렬 누락) → 보호 0인데 "검증됨"으로 오인. 완화: 음성 검증 AC(더미 서비스 주입) 필수.
- Phase 2를 전체 `up -d`로 바꾸면서 seed 순서가 깨짐 → producer가 빈 DB로 기동. 완화: Phase 경계 유지 + seed 후 healthy 대기.
- `.ps1` 짝의 `Compare-Object` 방향 실수 → POSIX와 다른 판정. 완화: 두 스크립트 모두 음성 검증.

---

# Test Requirements

- `bash -n scripts/fed-e2e-up.sh`.
- 로컬 실기동 1회: 19서비스 running + `victoriatraces` healthy + OTLP 실패 로그 0건.
- 음성 검증: 더미 서비스 추가 → 스크립트 비-0 종료 + 서비스명 출력. (검증 후 더미 제거, 커밋 금지.)

---

# Definition of Done

- [ ] README 하드코딩 열거 제거 + 스크립트 2개 추가.
- [ ] 완전성 단언 양방향 검증(통과 케이스 + 음성 케이스).
- [ ] 19서비스 running, OTLP 실패 0건.
- [ ] `tasks/INDEX.md` done entry.

---

# Implementation Result (2026-07-10)

구현 = `scripts/fed-e2e-up.sh` + `scripts/fed-e2e-up.ps1` 신규, `tests/federation-hardening-e2e/README.md` 열거 제거.

**단언이 켜지자마자 `victoriatraces`와 동일 상태의 서비스 2개를 추가 적발**: `finance-account-service` · `erp-masterdata-service` — compose 선언 + 옛 README 목록에도 있었으나 **컨테이너가 아예 없었다**(콘솔 Finance/ERP 도메인이 백엔드 없이 degrade 렌더 중이었음). 빌드 후 기동, 둘 다 healthy. 현재 선언 21/21 running.

**검증**: `bash -n` + PS 파서 통과. 양성=두 스크립트 모두 `all 21 declared services are running` exit 0. **음성=더미 서비스 주입 시 두 스크립트 모두 exit 1 + 이름 지목**(compose 파일 원복 확인). OTLP export 실패 0건(프로듀서 4종). console-web 200.

**계획 외 추가 — `ASSERT_ONLY` / `-AssertOnly`**: 라이브 데모 컨테이너는 gitignore된 로컬 오버레이까지 포함해 생성되므로, base+demo만으로 `up -d` 하면 config 드리프트로 `console-web`·`wms-admin-service`가 **재생성되며 오버레이 설정이 소실**된다. 기동 없이 완전성만 검사하는 모드가 필요했고, 로컬 검증도 이 모드로 수행.

**구현 중 밟은 함정 2종(주석으로 코드에 고정)**:
- 절대 POSIX 경로를 Windows docker 바이너리에 넘기면 `C:\c\Users\…`로 망가진다 → compose 디렉터리로 `cd` 후 **상대 `-f` 경로**.
- PowerShell 5.1에서 `try { docker info *> $null } catch`는 네이티브 stderr를 ErrorRecord로 감싸 `$ErrorActionPreference='Stop'`과 만나 **정상 docker를 "not running"으로 오진** → `$LASTEXITCODE` 게이트로 교체.

**미해명(정직 보고)**: 검증 중 `wms-*` 4개 + `ecommerce-kafka`가 `22:06:44Z`에 SIGTERM으로 동시 정지. 격리 실험 결과 두 스크립트는 컨테이너를 변경하지 않음(실행 전후 22개 불변). 원인 미특정. 컨테이너는 복구됨.

**후속**: `TASK-MONO-338`(로그 로테이션, 증폭 차단)은 독립 — 이 task로 원인이 막혔으므로 우선순위 그대로 낮음.

---

# Provenance

Surfaced 2026-07-10 — 사용자 "도커 용량 정리" → 17.1GB 컨테이너 로그 발견 → "근본 해결책 있어?" 질의에서 원인 추적. 최초 가설 두 개가 **조사로 반증**됐다: (1) "데모를 `demo-up.sh`로 이관" → iam/wms 프로젝트 compose에 앱 서비스가 없고 federation env 미배선, 로컬 실기동 금지(41 JVM) → 큰 증분이라 즉시 불가. (2) "untracked 오버레이를 git으로 승격" → `.gitignore:7-17`이 의도적 결정임을 확인, 뒤집으면 안 됨. 남은 진짜 결함은 **tracked README의 하드코딩 서비스 목록(14) ↔ compose 선언(19) 드리프트**였고, 누락 5개 중 `victoriatraces`가 정확히 이번 사고의 원인이었다. 착수 전 검증(메모리 `project_untickected_backlog_candidates_2026_06_19`의 "REAL-GAP 구현상태 검증 필수" 규칙)이 두 번의 헛수고를 막았다.

분석=Opus 4.8 / 구현 권장=Sonnet (스크립트 + 문서; 완전성 단언의 음성 검증만 주의).
