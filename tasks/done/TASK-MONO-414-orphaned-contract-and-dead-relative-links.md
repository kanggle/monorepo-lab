# Task ID

TASK-MONO-414

> **⚠️ 리넘버 (2026-07-15): `TASK-MONO-409` → `TASK-MONO-414`.** 이 task 는 `409` 로 작성·구현·머지됐다(spec+impl 번들 PR **#2574**, squash `a70be3a37`). 그런데 내가 작업하는 사이 **다른 세션의 PR #2573**(`/validate-rules` 산출물, squash `8728d78b7`)이 먼저 머지되며 **`TASK-MONO-409`~`413` 을 선점**했다 — 그쪽 `409` 는 *"dispatch 규칙이 dispatch 에 도달하지 않는다"* 로 **완전히 다른 task** 다. 파일명이 달라 git 은 충돌을 보고하지 않았고, **같은 ID 를 가진 task 두 개가 main 에 나란히 착지했다.**
>
> **`409` 는 그쪽이 갖는다** — 먼저 머지됐고, `409`~`413` 이 하나의 시리즈다. 하나짜리이고 이미 종결된 이쪽을 물린다.
>
> **원인은 절차 위반이다.** 착수 *전에* ID 를 확인했고 그때 `409` 는 비어 있었다. 규칙은 **push 직전 재검증**을 요구하는데(동시 세션 task-id 충돌) **그 재검증을 건너뛰었다.** ⇒ **ID 는 픽 시점이 아니라 push 시점에 예약된다.** 앞선 확인이 "지금도 참" 이라는 보증은 어디에도 없다.
>
> **PR #2574 의 제목·커밋·본문은 `409` 를 말한다 — 이력은 불변이므로 고치지 않는다.** 이 파일과 `tasks/INDEX.md` 의 done 행이 그 매핑의 정경이다.

# Title

정경 계약 하나가 읽기 순서 어디에도 없다(=사실상 없는 규칙), 그리고 상대링크 13개가 깨져 있다

# Status

done

# Owner

platform

# Task Tags

- docs
- onboarding

---

# Goal

스펙 전수 진단(2026-07-15)에서 나온 **기계적으로 닫히는** 결함 둘. 둘 다 doc-only, 의미 변경 0.

### ① `platform/contracts/notification-inbox-contract.md` 는 어떤 읽기 순서에서도 안 보인다

`rules/common.md § Platform Contracts` 는 스스로를 *"`platform/contracts/` 하위의 cross-cutting 계약 파일들"* 이라고 소개하고 표를 연다 — 그런데 **그 디렉터리의 2개 중 1개만 적혀 있다**(`jwt-standard-claims.md` 만; `notification-inbox-contract.md` 누락). `platform/entrypoint.md` 의 Core·Service-Type·Auxiliary 표에도 없다.

⇒ **`CLAUDE.md` 가 규정한 읽기 순서를 정확히 따르는 에이전트는 이 파일을 절대 열지 않는다.** 그런데 이 파일은 ADR-MONO-043 D3 의 산출물이고 *"every per-domain notification surface 가 conform 하는"* **정경**이다. 실제로 erp·fan 이 conformance task 를 수행했지만, 그건 **task 가 직접 가리켜서**이지 읽기 순서가 데려다줘서가 아니다. **1곳에만(그것도 읽기 순서 밖에) 있는 규칙은 사실상 없는 규칙이다.**

### ② 상대링크 13개가 깨져 있다 (대상 파일은 존재 — `../` 깊이가 하나 모자람)

`projects/<p>/specs/contracts/events/` 에서 저장소 루트까지는 **5단계**인데 `../../../../`(4단계)로 적혀 `projects/<p>/docs/adr/...` 라는 없는 경로를 가리킨다. 같은 유형이 cross-project 참조에도 있다.

---

# Scope

## In Scope

- **AC-0 재측정 우선.** 아래 숫자(1 고아 / 13 링크)는 **인계된 가설**이다. 착수 시 다시 센다.
- `rules/common.md § Platform Contracts` 표에 `notification-inbox-contract.md` 행 추가(적용 시점 포함).
- `platform/entrypoint.md § Auxiliary` 에 배선 — 기존 태그 재사용(`api`), **새 태그를 만들지 않는다**(새 태그는 `.claude/config/activation-rules.md` 동반 수정을 요구한다 — entrypoint.md § Change Rule).
- 깨진 상대링크 13개 수정(깊이 교정만; 링크 텍스트·대상 불변).

## Out of Scope

- **`abac-data-scope.md` / `access-conditions.md` 배선 — 필요 없다. 이미 도달 가능하다.** 진단 초안은 이 둘도 "고아" 라고 적었으나 **반증됐다**: `platform/security-rules.md`(**Core — 항상 읽음**) `:70-75` 가 둘 다 *"read when a task narrows operator access by data slice or runtime condition"* 이라는 포인터와 함께 링크한다. **건드리지 말 것** — 없는 병을 고치면 다음 사람이 그 변경을 근거로 또 움직인다.
- 비대 스펙 분할(console-integration-contract 3460줄 등) — 별개 판단.
- 에러 봉투 / 페이지네이션 정경 모순 — **결정이 필요한 사안**이라 별개 티켓(이 task 는 결정 없이 닫히는 것만).
- `platform/error-handling.md` 의 도메인별 섹션 — `platform/README.md:57` 이 명시적으로 허용한 설계다. 순도 위반 아님.

---

# Acceptance Criteria

- [x] **AC-0 (모집단 재측정)** — **진단 초안이 반증됐다.** 모집단에 **Core 를 포함**해 다시 세니: `notification-inbox-contract` = 읽기순서 참조 **0곳**(진짜 고아) / **`abac-data-scope`·`access-conditions` = `platform/security-rules.md`(Core, 항상 읽음) `:70-75` 에서 명시적 "read when…" 포인터로 도달 — 고아가 아니다.** 초안은 `entrypoint`·`common`·`domains`·`traits` 만 모집단으로 잡아 **Core 를 빼먹었고**, 그래서 멀쩡한 파일 2개를 고아로 오진했다. 검사식 자기검증: 아는 양성(`jwt-standard-claims`)=2곳 ✔ / 아는 음성(존재하지 않는 이름)=0곳 ✔.
- [x] **AC-1** `platform/contracts/` = 파일 **2개**(실측 `ls`), `rules/common.md § Platform Contracts` 표 = **1행**이었다 → `notification-inbox-contract.md` 행 추가로 **표 == 디렉터리**. 표 아래에 *"이 표는 디렉터리 전체를 열거한다 — 새 계약 추가 시 행도 추가"* 규범 문장 명시(같은 결함 재발 방지).
- [x] **AC-2** `platform/entrypoint.md § Auxiliary` 의 **기존 `api` 태그 행**에 추가(`jwt-standard-claims` 와 같은 자리). **새 태그 0개** ⇒ `.claude/config/activation-rules.md` 동반 수정 불필요(분류기 차단 구역 회피).
- [x] **AC-3** 깨진 상대링크 **13 → 0**(389 파일 / 1932 링크 전수 재해석). 앵커 **0건**. 검사식은 **아는 답 2개로 자기검증 후** 신뢰했다.
- [x] **AC-4** 의미 변경 0 — 규칙 문장 신규 서술 없음. 링크 경로 교정 + 인덱스 행 1개 + 태그 행 1개.

---

# Related Specs

- [`rules/common.md`](../../rules/common.md) § Platform Contracts — 표가 디렉터리를 열거한다고 선언한 자리.
- [`platform/entrypoint.md`](../../platform/entrypoint.md) § Auxiliary + § Change Rule.
- [`platform/contracts/notification-inbox-contract.md`](../../platform/contracts/notification-inbox-contract.md) — 배선 대상(ADR-MONO-043 D3).
- [`platform/security-rules.md`](../../platform/security-rules.md) `:70-75` — **abac/access-conditions 가 이미 도달 가능하다는 반증 근거.**

# Related Contracts

없음(doc-only). 계약의 **내용**은 바꾸지 않는다 — 계약에 **도달하는 경로**만 만든다.

# Edge Cases

- **`platform/contracts/` 에 파일이 더 늘어날 수 있다** — AC-1 은 "지금 2개" 가 아니라 **"표 == 디렉터리"** 를 요구한다. 착수 시 `ls` 로 세라.
- **링크 깊이 교정이 앵커를 깨면 안 된다** — `#anchor` 부분은 건드리지 않는다.
- **`../` 를 하나 더 넣는 게 늘 정답은 아니다** — 각 링크마다 **파일 위치에서 실제로 해석**해 확인하라(cross-project 링크는 깊이가 다르다).

# Failure Scenarios

- **"고아" 를 고치겠다고 `abac-data-scope`/`access-conditions` 를 entrypoint 에 또 등록한다** → 이미 Core 에서 도달하는 파일을 이중 배선. 없는 병에 처방하는 것이고, 다음 사람은 그 이중 등록을 보고 "왜 여긴 두 번 적혔지" 를 또 묻는다. Out of Scope 가 게이트.
- **새 태그(`notification` 등)를 만든다** → `.claude/config/activation-rules.md` 미동반 시 배선이 반쪽이 되고, `.claude/` 는 분류기 차단 구역이라 그 수정이 막힐 수 있다. AC-2 가 게이트.
- **링크를 일괄 치환한다** → cross-project 링크는 깊이가 다르므로 sed 일괄은 멀쩡한 링크를 깬다. 파일별 해석 검증이 게이트.

---

# Implementation Notes

## 🔴 진단이 틀렸고, 착수 재측정이 그걸 잡았다

이 티켓의 초안(및 그것을 낳은 진단 에이전트)은 **정경 계약 3개가 고아**라고 적었다. 실측은 **1개**다.

**틀린 건 검사식이 아니라 모집단이었다.** 나는 "읽기 순서 파일" 을 `entrypoint.md` + `common.md` + `rules/domains/` + `rules/traits/` 로 잡았다. 그런데 `entrypoint.md` 자신이 **Core 5개 파일을 "Always Read" 로 규정**한다 — 그 Core 도 읽기 순서다. 그리고 `platform/security-rules.md`(Core) `:70-75` 가 두 파일을 정확히 이렇게 링크하고 있었다:

> *"Authorization beyond authentication is governed by two axis-② contracts (**read when** a task narrows operator access by data slice or runtime condition): `abac-data-scope.md` … `access-conditions.md` …"*

**대조군(`jwt-standard-claims`=2곳)이 통과해서 검사식이 검증된 것처럼 보였다.** 검사식은 멀쩡했다 — **내가 세는 대상에서 답이 있는 곳을 빼놨을 뿐이다.** 그대로 갔으면 이미 도달 가능한 파일 2개를 `entrypoint.md` 에 **이중 배선**하고, 다음 사람은 "왜 두 번 적혔지" 를 묻게 된다. **없는 병에 처방하는 것도 드리프트다.**

## 무엇이 진짜였나

`rules/common.md § Platform Contracts` 는 스스로를 *"`platform/contracts/` 하위의 계약 파일들"* 이라 소개하고 표를 여는데 — **디렉터리엔 2개, 표엔 1개**였다. `notification-inbox-contract.md`(ADR-MONO-043 D3, *"every per-domain notification surface 가 conform"* 하는 정경)는 **읽기 순서 어디에도 없었다.** erp·fan 이 conformance task 를 실제로 수행했지만 그건 **task 가 직접 가리켜서**이지 읽기 순서가 데려다줘서가 아니다.

⇒ 표에 행을 추가하고, **표가 디렉터리 전체를 열거한다는 규범 문장**을 그 아래 못박았다(다음 계약이 또 조용히 누락되지 않도록). `entrypoint.md` 는 **기존 `api` 태그**에 얹었다 — 새 태그를 만들면 `.claude/config/activation-rules.md` 를 같은 PR 에서 고쳐야 하는데(§ Change Rule), 거긴 분류기 차단 구역이라 반쪽 배선이 될 위험이 있다.

## 링크 13건 — 일괄 치환하지 않았다

11건은 `../` 깊이 off-by-one(`projects/<p>/specs/contracts/events/` → 루트까지 5단계인데 4단계로 적힘). **나머지 2건은 깊이가 아니라 경로가 틀렸다**: `user-api.md` 가 `contracts/events/` 의 파일을 **같은 디렉터리**로 가리켰고(`../events/` 필요), `batch-worker/overview.md` 는 `../` 가 **하나 많았다**. sed 일괄 치환이었으면 이 둘은 못 고치거나 멀쩡한 링크를 깼다 — **파일별로 실제 해석해 검증**했다.

**검증**: 389 파일 / 1932 링크 전수 재해석 → 깨진 링크 **0**, 깨진 앵커 **0**. 검사식은 아는 답 2개(`OAuth2 / OIDC …` → `oauth2--oidc-…`, `` `org_node` `` → `org_node`)로 자기검증한 뒤 신뢰했다. **진단 단계에서 이 검사식의 첫 판(공백 다중→하이픈 1개, 밑줄 삭제)은 오탐 15건을 냈다** — 자기검증 없이 실었으면 멀쩡한 앵커 15개를 "고쳤을" 것이다.

## 범위 밖으로 남긴 것 (진단의 나머지)

- **에러 봉투** — wms 계약 5개가 `{error:{…}}` 중첩을 정의하면서 flat 을 못박은 `platform/error-handling.md` 를 근거로 인용한다. 코드·테스트·콘솔 파서 2개가 이미 그 위에 서 있어 **결정(ADR)이 필요**하다.
- **페이지네이션** — `platform/service-types/rest-api.md:46`("`PageResult` MUST") ↔ `platform/contracts/notification-inbox-contract.md:68`("wrapper shape 은 domain-owned")가 **공유 층 안에서 서로 모순**이고, 그 아래 5가지 wire shape 이 배포돼 있다. **정경의 자기모순을 먼저 풀어야** 프로젝트를 건드릴 수 있다.
- **HARDSTOP-03 자신** — 4가지 범위로 적혀 있고 훅은 그 4개 중 아무것도 탐지하지 않는 5번째(경로 리터럴)를 구현한다.
- **`rules/domains/saas.md`** — iam 전용 API·테이블을 담고 있는데 `saas` 는 iam·console **둘**이 선언한다.
