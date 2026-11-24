# Task ID

TASK-MONO-832

# Title

공유 라이브러리 카탈로그(`platform/shared-library-policy.md` § Catalog)에 빠진 모듈 4개를 채우고, `settings.gradle` 과 카탈로그가 다시 갈라지지 않게 막는 가드

# Status

in-progress

# Owner

monorepo

# Task Tags

- docs
- ci
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (문서 행 + 기존 가드 본뜨기) · 가드 술어는 Opus 검토
> 기안 2026-11-24 UTC. 출처: 소유자 «다음 할 일 추천» → 이 항목 → 소유자 «진행해».

# Dependency Markers

- 없음. (관련: `TASK-MONO-830` 이 같은 표에서 `java-notification` 행을 지웠다 — 그때 빠진 4개를 아무도 못 봤다는 것이 이 티켓의 근거 하나다.)

# Goal

`platform/shared-library-policy.md` § Change Rule 은 *«the shared-library catalog entry list … must be documented in this file **before** any `libs/` touch»* 라고 정한다. 그런데 2026-11-24 UTC 실측으로 `settings.gradle` 의 루트 `libs:*` 모듈은 **12개**, 카탈로그 행은 **8개**다. 빠진 넷:

| 모듈 | 들어온 날 | 출처 |
|---|---|---|
| `libs/java-security-servlet` | 2026-07-13 | `TASK-MONO-382` · `ADR-MONO-049` D5-2 (#2489) |
| `libs/payment-core` | 2026-07-24 | `TASK-MONO-478` · `ADR-MONO-056` D2 (#2979) |
| `libs/payment-portone` | 2026-07-24 | 같음 |
| `libs/payment-toss` | 2026-07-24 | 같음 |

넉 달 동안 네 번의 PR 이 그 규칙을 지나쳤고, 2026-11-24 에 같은 표를 두 번 고친 `TASK-MONO-830` 도 못 봤다 — **사람 눈이 잡는 종류가 아니다.** 그래서 행을 채우는 것과 함께, 어긋나면 CI 가 빨개지는 가드를 단다(«게이트 없는 목록은 반드시 낡는다»).

# Scope

## In Scope

- 카탈로그 표에 4행 추가. Contents 는 각 ADR · 모듈의 main 클래스에서, Consumers 는 **실측**(`':libs:<m>'` 을 선언한 `build.gradle`)에서. 기안 시점 실측:
  - `java-security-servlet` — 서비스 24개 / 7 프로젝트(ecommerce 2 · erp 4 · fan 4 · finance 2 · iam 3 · scm 4 · wms 5 — 게이트웨이 아닌 servlet 서비스). 클래스: `ResourceServerChainAssembler` · `TenantClaimEnforcer` · `ActorContext*` · `CallerTokenPropagation` 등.
  - `payment-core` — 어댑터 둘(`payment-portone` · `payment-toss`) + ecommerce `payment-service` · fan `membership-service`. 클래스: `PaymentGatewayPort` · `RefundablePaymentGateway` · `RecurringBillingGateway` 등.
  - `payment-portone` — fan `membership-service`. `payment-toss` — ecommerce `payment-service`.
- 새 가드 `scripts/check-shared-lib-catalog.sh`:
  - 모집단 A = `settings.gradle` 이 include 하는 **루트** 모듈 `'libs:<name>'`(앞에 `projects:…:` 가 붙은 프로젝트 전용 `libs` 는 제외 — 그것은 카탈로그 대상이 아니다, CLAUDE.md § Project-scoped shared modules).
  - 모집단 B = 정책 파일 `# Catalog` 절의 표 행 `| \`libs/<name>\` |`.
  - A \ B 또는 B \ A 가 비면 안 된다 → **양방향** 실패. 빈 모집단은 fail-closed.
  - `--self-test`: 실제 트리의 **사본**을 변형(`check-libs-ci-coverage.sh` 와 같은 방식) — 별도 프로세스 진입점 · 기준선 통과 · 행 삭제 bite · 모듈 추가 bite · 모듈+행 추가 통과 · 프로젝트 전용 libs 는 무시 · 주석 속 모듈 이름은 무시 · 빈 모집단 fail-closed.
- `ci.yml`: 잡 + `changes` 경로 필터(`settings.gradle` · `platform/shared-library-policy.md` · 가드 자신), pure-positive.
- `scripts/` 에 파일이 하나 늘어나므로 **가드 전부** 재실행 + `check-ls-files-guard-count.sh` 가 가리키는 산문 집(CLAUDE.md · `platform/git-workflow-policy.md`) 숫자 갱신.

## Out of Scope

- 카탈로그 기존 8행의 문구 개정(Consumers 를 실측으로 바꾸는 일 포함) — 이 티켓은 **빠진 것**을 채운다.
- `libs/` 디렉터리는 있는데 `settings.gradle` 에 없는 모듈 — 지금 0개이고, 그런 디렉터리는 빌드에 없으므로 별개 결함이다.
- 라이브러리 코드 변경 0.

# Acceptance Criteria

- [ ] AC-0: 착수 직전 재측정 — 루트 `libs:*` 12 · 카탈로그 8 · 빠진 것이 위 4개 그대로인가. 달라졌으면 차이를 기록하고 그 숫자로 진행.
- [ ] AC-1: 카탈로그에 4행이 있고, 각 Consumers 칸이 AC-0 시점 실측과 맞는다(측정 명령을 PR 본문에).
- [ ] AC-2: 가드가 현재 트리에서 rc=0, `--self-test` 전부 PASS.
- [ ] AC-3: bite — 카탈로그에서 한 행을 지우면 rc=1 + 그 모듈 이름 출력, `settings.gradle` 에 가짜 루트 모듈을 넣으면 rc=1, 되돌리면 rc=0. 🔴 bite 는 **복사본**에서(작업 트리의 `git checkout --` 금지).
- [ ] AC-4: `scripts/` 가드 **전부** 실행(스테이지 뒤) — 비0 은 사유와 함께 PR 본문에. `check-ls-files-guard-count.sh` rc=0.
- [ ] AC-5: PR CI 에서 새 잡이 **실행되어** success(`SKIPPED` 는 통과가 아니다).

# Related Specs

- `platform/shared-library-policy.md` § Catalog · § Change Rule
- `platform/testing-strategy.md` § 가드 규칙
- CLAUDE.md § Task Rules («Adding or deleting a file under `scripts/` … sweep them all»)

# Related Contracts

- 없음.

# Edge Cases

- 카탈로그 표 밖(본문 산문)에도 `` `libs/<name>` `` 가 많이 나온다 — 모집단 B 는 **`# Catalog` 절의 표 행**으로만 좁힌다. 아니면 «산문에 언급됨» 이 «카탈로그에 있음» 으로 읽힌다.
- `settings.gradle` 의 `//` 주석 속 모듈 이름(«No consumer depends on them yet …» 같은 문장) — 주석을 벗기고 센다(`check-libs-ci-coverage.sh` 의 같은 교훈).
- 프로젝트 전용 `projects:<p>:libs:<m>` — A 에서 제외. 제외를 정규식 한 글자로 하므로 self-test 가 그 경계를 문다.

# Failure Scenarios

- 술어가 산문의 `libs/` 언급까지 세면 → 행을 지워도 초록(공허한 통과). AC-3 의 «행 삭제 bite» 가 잡는다.
- 프로젝트 전용 libs 를 A 에 넣으면 → 첫날 빨강(카탈로그에 없으므로). 기준선 통과 case 가 잡는다.
- 가드를 `ci.yml` 에 이름만 걸고 경로 필터를 빠뜨리면 → 영원히 SKIPPED. AC-5.

# Test Requirements

- 가드 `--self-test` · bite(AC-3) · 가드 전수(AC-4) · CI(AC-5).

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
