# Task ID

TASK-MONO-346

# Title

Complete `infra/demo/demo.env` for a fresh clone + add the "no unset compose variable" guard (g)

# Status

ready

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

`bash infra/demo/demo-up.sh full` **은 fresh clone(=데모 AMI, CI 러너)에서 ecommerce 백엔드를 하나도 띄우지 못한다.**

`projects/ecommerce-microservices-platform/docker-compose.yml` 은 기본값 없는 bare `${VAR}` 를 **14개** 참조하는데, 그 값의 출처인 `projects/ecommerce-microservices-platform/.env` 는 **gitignored** 다(`.gitignore` 의 `.env` / `.env.*`). 저장소에 있는 것은 `.env.example` 뿐이다. 따라서 갓 clone 한 트리에서는 14개가 전부 빈 문자열로 보간된다.

그 중 **9개가 `POSTGRES_PASSWORD`** 다. `postgres:16-alpine` 은 빈 superuser 비밀번호로 초기화를 **거부**한다(실측):

```
Error: Database is uninitialized and superuser password is not specified.
       You must specify POSTGRES_PASSWORD to a non-empty value for the superuser.
```

→ ecommerce DB 9개가 기동 실패 → 그 위의 앱 서비스 12개가 전부 죽는다.

## 왜 기존 가드가 못 잡았나

`verify-demo-wrapper.sh` 의 가드 (a) 는 `docker compose config -q 2>/dev/null` 이다. **미설정 변수는 error 가 아니라 warning** 이고(`level=warning msg="The \"X\" variable is not set. Defaulting to a blank string."`), `render()` 와 (a) 가 stderr 를 `/dev/null` 로 버린다. 즉 **구조적으로 눈이 멀어 있다**.

이것은 MONO-342/344 와 **같은 클래스의 세 번째 결함**이다 — 정적 불변식(렌더·이름·포트·커버리지·앱≥1)은 전부 통과하는데 **실제로는 기동하지 않는다**. 이번에는 렌더가 성공하는 것이 오히려 함정이었다.

부수 결함: `SETTLEMENT_DB_PASSWORD` 는 compose 가 참조하는데 `.env.example` 에 **선언 자체가 없다**. README 를 따라 `cp .env.example .env` 한 로컬 개발자도 `settlement-postgres` 를 띄울 수 없다.

---

# Scope

## In Scope

- `infra/demo/demo.env` — ecommerce 의 bare 변수 14개를 데모 값으로 주입
  (기존 `REDIS_PASSWORD` / `WMS_OIDC_*` 와 같은 메커니즘: `set -a; source` → compose 가 셸 env 로 보간)
- `infra/demo/verify-demo-wrapper.sh` — **가드 (g) 신설**: 모든 compose 렌더가
  **"variable is not set" 경고 0건**이어야 한다. stderr 를 버리지 않고 판정한다.
- `projects/ecommerce-microservices-platform/.env.example` — 누락된 `SETTLEMENT_DB_PASSWORD` 선언 추가
- `infra/demo/README.md` — 가드 목록 갱신

## Out of Scope

- 프로젝트 `docker-compose.yml` 수정 (byte-unchanged 유지 — bare `${VAR}` 를
  `${VAR:-default}` 로 바꾸는 것이 "더 깔끔"해 보이지만, 그러면 **운영 배포에서
  빈 비밀번호가 조용히 통과**한다. 데모용 값 주입은 데모 파일이 할 일이다.)
- `.gitignore` 정책 변경 (`.env` 는 계속 gitignored 여야 한다)
- TOSS 결제 게이트웨이 실키 — 더미 값으로 둔다 (데모는 결제 승인까지 가지 않는다)
- `full` 전체 동시 기동 스모크 — EC2 권위 (MONO-342/344 이월분)

---

# Acceptance Criteria

- [x] fresh clone(=`.env` 없는 worktree)에서 `demo.env` 를 source 한 뒤 8개 프로젝트 + traefik 을 전부 렌더할 때 **"variable is not set" 경고 0건**
- [x] 가드 (g) 가 `verify-demo-wrapper.sh` 에 존재하고 정적 모드에서 실행된다
- [x] **네거티브 테스트**: `demo.env` 에서 `SETTLEMENT_DB_PASSWORD` 를 지우면 (g)가 **exit 1** 로 FAIL 하고 `ecommerce → SETTLEMENT_DB_PASSWORD` 를 지목한다
- [x] `.env.example` 이 compose 가 참조하는 bare 변수 14개를 전부 선언한다
- [x] 프로젝트 `docker-compose.yml` / `infra/traefik/` git diff == 0
- [x] 기존 가드 (a)~(f) 전부 여전히 PASS
- [ ] CI `demo-wrapper-smoke` GREEN (이 PR 에서 확인)

---

# Related Specs

> Monorepo-level task. Read root `tasks/INDEX.md` § "When to Use Root vs Project Tasks" first.

- `tasks/done/TASK-MONO-344-demo-wire-fullstack-and-app-guard.md` (demo.env 도입 · 가드 (e))
- `tasks/done/TASK-MONO-342-wms-fullstack-compose.md` (같은 결함 클래스의 1차)
- `tasks/done/TASK-MONO-341-demo-wrapper-ci-smoke.md` (CI 잡 `demo-wrapper-smoke`)
- `TEMPLATE.md` § Local Network Convention

# Related Skills

- `.claude/skills/` — devops / CI

---

# Related Contracts

- 없음 (인프라 조립, API/event 계약 변경 없음)

---

# Target Service

- N/A (cross-project infra) — 산출물: `infra/demo/`, `projects/ecommerce-microservices-platform/.env.example`

---

# Architecture

- **경고를 에러로 승격**하는 것이 이 task 의 본질이다. compose 의 "미설정 → 빈 문자열" 기본 동작은 편의 기능이지만, 비밀번호 자리에서는 **조용한 기동 실패**로 바뀐다. 데모 경로에서는 이를 허용하지 않는다.
- 값의 주입 지점은 **`demo.env`(셸 env)** 이지 프로젝트 compose 가 아니다. 셸 env 는 compose 의 `.env` 파일보다 우선하므로, 실 `.env` 를 가진 개발자의 로컬 값도 데모 실행 중에는 데모 값으로 덮인다 — 의도된 동작(`REDIS_PASSWORD=` 와 동일 패턴).
- 가드 (g) 의 판정 입력은 `docker compose config -q` 의 **stderr** 다. 경고 문자열은 `\"NAME\"` 처럼 백슬래시-이스케이프된 따옴표를 포함하므로 파싱 전에 벗겨야 한다(이 함정에 한 번 걸려 거짓 "clean" 을 봤다).

---

# Implementation Notes

- 데모 비밀번호는 `.env.example` 의 `changeme-local` 관례를 따른다. 데모 스택은 host port 를 열지 않고(`expose:` only) Traefik 뒤에 있으므로 리터럴 커밋이 안전하다.
- `set -euo pipefail` 하에서 `grep` 무매치는 exit 1 이다 → 가드 (g) 파이프라인 끝에 `|| true`.
- `2>&1 >/dev/null` 의 순서가 load-bearing: stderr 를 현재 stdout(캡처 대상)으로 복제한 **뒤** stdout 을 버린다. 순서를 뒤집으면 둘 다 사라진다.

---

# Edge Cases

- 실 `.env` 를 가진 개발자 로컬 — 셸 env 가 우선하므로 (g)는 여전히 0건. 단 `.env` 가 있으면 fresh-clone 결손을 못 본다 → **가드는 CI(fresh checkout)에서 권위**를 갖는다.
- `${VAR:-default}` 형태는 경고를 내지 않는다 → (g)가 통과시킨다. 의도한 동작(기본값이 명시됐으므로).
- traefik compose 도 렌더 대상에 포함해 동일 판정한다.
- MINIO/PROJECT_HOSTNAME 등 `.env.example` 의 나머지 키는 compose 에서 bare 참조가 아니므로 (g)의 대상이 아니다.

---

# Failure Scenarios

- 누군가 프로젝트 compose 에 bare `${NEW_SECRET}` 를 추가하고 `.env` 에만 값을 둔다 → (g) FAIL (이 task 의 핵심)
- `demo.env` 에서 값을 지운다 → (g) FAIL, 변수명 지목
- `render()` 에 `2>/dev/null` 이 되돌아온다 → (g)가 다시 눈이 멀지만 (g) 자체는 별도 캡처를 쓰므로 무관
- ecommerce 가 아닌 프로젝트가 나중에 bare 변수를 도입 → (g)가 프로젝트명과 함께 지목

---

# Test Requirements

- `bash -n infra/demo/verify-demo-wrapper.sh`
- fresh-clone worktree(=`.env` 부재)에서 정적 (a)~(g) PASS
- 네거티브: `demo.env` 에서 한 변수 제거 → (g) exit 1 + 변수명 출력
- `git diff --exit-code projects/*/docker-compose.yml infra/traefik/` == 0
- CI `demo-wrapper-smoke` GREEN

---

# Definition of Done

- [ ] `demo.env` 에 ecommerce 14개 변수, `.env.example` 에 `SETTLEMENT_DB_PASSWORD`
- [ ] 가드 (g) 추가 + 네거티브 테스트로 작동 확인
- [ ] fresh-clone 렌더 경고 0건 확인
- [ ] CI GREEN
- [ ] Ready for review
