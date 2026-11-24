# Task ID

TASK-MONO-832

# Title

공유 라이브러리 카탈로그(`platform/shared-library-policy.md` § Catalog)에 빠진 모듈 4개를 채우고, `settings.gradle` 과 카탈로그가 다시 갈라지지 않게 막는 가드

# Status

review

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

- [x] AC-0: 착수 직전 재측정 — 루트 `libs:*` 12 · 카탈로그 8 · 빠진 것이 위 4개 그대로인가. 달라졌으면 차이를 기록하고 그 숫자로 진행.
- [x] AC-1: 카탈로그에 4행이 있고, 각 Consumers 칸이 AC-0 시점 실측과 맞는다(측정 명령을 PR 본문에).
- [x] AC-2: 가드가 현재 트리에서 rc=0, `--self-test` 전부 PASS.
- [x] AC-3: bite — 카탈로그에서 한 행을 지우면 rc=1 + 그 모듈 이름 출력, `settings.gradle` 에 가짜 루트 모듈을 넣으면 rc=1, 되돌리면 rc=0. 🔴 bite 는 **복사본**에서(작업 트리의 `git checkout --` 금지).
- [x] AC-4: `scripts/` 가드 **전부** 실행(스테이지 뒤) — 비0 은 사유와 함께 PR 본문에. `check-ls-files-guard-count.sh` rc=0.
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

# Implementation Evidence (2026-11-24 UTC)

- **AC-0** — 착수 직전 재측정: `settings.gradle` 루트 `libs:*` **12** · 카탈로그 행 **8** · 빠진 넷이 기안과 동일. 🔵 새 가드를 행 추가 **전에** 돌려 이 차이를 가드가 직접 지목하는지 먼저 봤다 → rc=1, `libs/java-security-servlet` · `payment-core` · `payment-portone` · `payment-toss` 네 줄.
- **AC-1** — 4행 추가. Consumers 측정: `git ls-files '*.gradle' | xargs grep -l "':libs:<m>'"` (모듈 자신 제외) → `java-security-servlet` 24 서비스(ecommerce 2 · erp 4 · fan 4 · finance 2 · iam 3 · scm 4 · wms 5) · `payment-core` 어댑터 2 + ecommerce 1 · fan 1 · `payment-portone` fan 1 · `payment-toss` ecommerce 1. 🔴 공유 경로라 서비스 이름은 적지 않고 **프로젝트 이름까지만**(§ Catalog 머리말의 선언된 예외 범위, `TASK-MONO-412`).
- **AC-2** — `check-shared-lib-catalog.sh` rc=0 («12 repo-root shared-library module(s)») · `--self-test` **12/12 PASS**(별도 프로세스 진입점 · 기준선 · 행 삭제 · 새 모듈 · 새 모듈+행 · 낡은 행 · 프로젝트 전용 무시 · `//` 주석 무시 · § Catalog 밖 표 모양 줄 무시 · 빈 settings / 바뀐 제목 / 파일 없음 fail-closed). 변형 case 마다 «주입이 실제로 바꿨는가» 를 먼저 단언.
- **AC-3** — scratchpad 사본에서: `payment-toss` 행 삭제 → rc=1 + `libs/payment-toss` · 가짜 `'libs:java-fake'` 추가 → rc=1 + `libs/java-fake` · 원본 복원 → rc=0. 작업 트리는 건드리지 않았다.
- **AC-4** — 스테이지 뒤 `scripts/check-*.sh` **41개**: 39 rc=0. 비0 둘은 830 과 같은 환경 전제 — `check-erp-single-tenant-ratchet`(실행 중 erp DB 필요) · `check-prerendered-demo-verdict`(`DEMO_API_BASE` + web-store 빌드 필요, CI `frontend-checks` 가 돌린다). `scripts/check-*.mjs` 6개 전부 rc=0. `check-ls-files-guard-count.sh` — 추가 직후 rc=1(«28 of the 69» 아님, 두 집 지목) → CLAUDE.md · `platform/git-workflow-policy.md` 를 69 로 → rc=0. 🔵 새 가드는 `ls-files` 문자열을 쓰지 않으므로 분자는 28 그대로, 분모만 68 → 69.
- 곁에서 고친 것: 첫 판은 CRLF 체크아웃(이 호스트)에서 `$0 == "# Catalog"` 이 우연히 맞았다(msys awk 가 CR 을 지움) — CI(LF)와 다른 경로로 초록일 수 있어 두 파서 앞에 `tr -d ''` 를 명시. 출력 문구 «exactly one catalog row» 는 중복을 검사하지 않으므로 거짓 → 문구 수정.
- **AC-5** — PR CI 대기.

# Definition of Done

- AC 전부 · 머지 · 4-dim close.
