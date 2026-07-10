# Task ID

TASK-MONO-344

# Title

Wire the full-stack composes into `infra/demo` and add the "app service ≥1" CI guard

# Status

done

# Owner

devops

# Task Tags

- deploy
- code
- test

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

TASK-MONO-342 가 `projects/wms-platform/docker-compose.e2e.yml` 을 신설했다. 이제 통합 데모가 실제로 앱을 띄우도록 배선하고, **같은 결함이 다시 일어나면 CI 가 잡도록** 가드를 추가한다.

배경: `demo-up.sh full` 은 프로젝트당 compose 파일을 **하나만** 넘겼다. iam / wms 는 base 가 인프라 전용이라 **DB 만 뜨고 앱이 0개**였다. iam 은 이 모노레포의 OIDC IdP 이므로, 그 상태에서는 나머지 전 도메인의 토큰 검증이 무너진다 — 데모가 근본적으로 동작하지 않는다.

MONO-336/341 의 검증(렌더·이름 유일성·포트·커버리지)은 **구조적 불변식만** 봤기 때문에 이 결함을 통과시켰다. "각 프로젝트가 앱을 하나라도 기여하는가"를 묻지 않았다.

---

# Scope

## In Scope

- `infra/demo/projects.sh`
  - `COMPOSE[slug]` 를 **공백 구분 파일 목록**으로 확장 (iam / wms = base + e2e)
  - `compose_args()` / `compose_files()` 헬퍼 신설 (단일 출처 유지)
- `infra/demo/demo-up.sh` / `demo-down.sh` — 다중 `-f` 전달
- `infra/demo/demo.env` 신설 — 데모 전용 cross-project env
  - `REDIS_PASSWORD=` (wms/iam 앱은 이 env 를 읽지 않는데 base redis 가 requirepass 를 건다)
  - `WMS_OIDC_*` (wms → iam IdP), TMS / Slack 스텁 URL
- `infra/demo/verify-demo-wrapper.sh`
  - 다중 파일 지원
  - **가드 (e) 신설**: 각 프로젝트가 `build:` 서비스를 **≥1개** 기여해야 한다
- `infra/demo/README.md` 갱신

## Out of Scope

- 프로젝트 compose 파일 수정 (byte-unchanged 유지)
- `.github/workflows/ci.yml` 수정 — 기존 `demo-wrapper-smoke` 잡이 이미
  `verify-demo-wrapper.sh --live` 를 호출하므로 새 가드가 자동 편입된다
- **JVM 힙 캡** — 프로젝트 compose 가 `${JAVA_TOOL_OPTIONS}` 를 참조하지 않아
  셸 env 로 주입 불가. 데모 호스트를 32GB 로 잡는 것으로 대응 (demo.env 에 사유 기록)
- console 5/5 federated 렌더 (federation env 완성) — 별도 증분
- `full` 전체 동시 기동 스모크 — EC2 권위

---

# Acceptance Criteria

- [x] `projects.sh` 가 iam / wms 에 base + `docker-compose.e2e.yml` 을 함께 등록
- [x] `demo-up.sh` / `demo-down.sh` / `verify-demo-wrapper.sh` 가 다중 `-f` 로 동작
- [x] 가드 (e) 통과 — iam 5 · wms 7 · scm 4 · fan 5 · finance 2 · erp 4 · ecommerce 14 · console 2
- [x] **네거티브 테스트**: iam 을 base-only 로 되돌리면 가드 (e)가 **exit 1** 로 FAIL
- [x] 정적 (a)~(e) PASS, `--live` (f) PASS (두 redis 공존 healthy)
- [x] `container_name` 91개 전역 유일, host port 무충돌
- [x] 프로젝트 compose / `infra/traefik/` git diff == 0
- [x] CI `demo-wrapper-smoke` GREEN (PR #2383, 22s, 1차 실행)

---

# Related Specs

> Monorepo-level task. Read root `tasks/INDEX.md` § "When to Use Root vs Project Tasks" first.

- `tasks/done/TASK-MONO-342-wms-fullstack-compose.md` (선행 — wms 풀스택 compose 신설)
- `tasks/done/TASK-MONO-336-integrated-demo-compose.md` (래퍼 도입 · 이 결함의 출처)
- `tasks/done/TASK-MONO-341-demo-wrapper-ci-smoke.md` (CI 가드 잡)
- `projects/iam-platform/docker-compose.yml` (헤더에 "infrastructure only" 명시)

# Related Skills

- `.claude/skills/` — devops / CI

---

# Related Contracts

- 없음

---

# Target Service

- N/A (cross-project infra) — 산출물: `infra/demo/`

---

# Architecture

- 프로젝트당 compose 파일 목록 = `projects.sh` 단일 출처. 다중 `-f` 는 **단일 compose 프로젝트 내부 오버레이**라 안전하다 (위험한 것은 *프로젝트 간* 병합 — MONO-336 의 침묵 병합).
- 가드 (e) 판정 기준은 `build:` 서비스 수. 이 저장소가 소스에서 굽는 서비스 = 애플리케이션이라는 휴리스틱. (ecommerce 의 `elasticsearch` 처럼 커스텀 이미지도 포함되므로 "정확한 앱 수"는 아니다 — **0 인지 아닌지**를 잡는 것이 목적이다.)

---

# Implementation Notes

- `demo.env` 값에 공백이 있으면 `set -a; source` 시 셸이 명령으로 해석한다. 따옴표 필수.
- `git checkout -- <tracked>` 는 **HEAD 로** 되돌린다 — 미커밋 작업본 복구용이 아니다.
- 데모는 프로젝트 간 container_name DNS 로 통신한다 (`iam-gateway-service` 등). 따라서 iam e2e 의 앱 컨테이너가 `traefik-net` 에 붙어야 하며, federation env 완성은 후속 증분.

---

# Edge Cases

- 패턴 2 프로젝트(scm/fan/…)는 파일이 하나뿐 — `compose_args` 가 `-f` 하나만 낸다.
- iam e2e 는 host port 를 publish 한다(13306/16379/18080…). Local Network Convention 의 `expose:`-only 원칙과 다르지만 기존 파일이므로 무변경, 포트 충돌 가드가 감시한다.
- `notification-service` 는 세 프로젝트(ecommerce/fan/erp)에 같은 키로 존재 — 별도 `-p` 라 무해.

---

# Failure Scenarios

- 신규 프로젝트가 인프라 전용 compose 만 등록 → 가드 (e) FAIL (이 task 의 핵심)
- 신규 프로젝트 미등록 → 가드 (d) FAIL
- 누군가 래퍼를 `include:` 로 되돌림 → 가드 (f) 가 redis 1개만 잡아 FAIL
- `demo.env` 에 따옴표 없는 공백 값 추가 → 모든 스크립트가 source 단계에서 죽음

---

# Test Requirements

- `bash -n` × 4 스크립트
- 정적 (a)~(e) + `--live` (f)
- 네거티브: iam base-only → (e) exit 1
- byte-unchanged diff == 0
- CI `demo-wrapper-smoke` GREEN

---

# Definition of Done

- [x] `projects.sh` 다중 파일 + 헬퍼, `demo-up/down` 다중 `-f`, `demo.env` 신설
- [x] 가드 (e) 추가 + 네거티브 테스트로 작동 확인
- [x] 로컬 정적 + `--live` PASS, byte-unchanged 확인
- [x] CI GREEN (PR #2383 — 23 SUCCESS / 1 SKIPPED / failing required 0)
- [x] Ready for review
