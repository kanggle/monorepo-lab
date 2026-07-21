# Task ID

TASK-MONO-461

# Title

controller-slice 테스트 네이밍이 스펙을 어긴 채 3형태로 갈라졌다 — 정경화(`*ControllerSliceTest`) + 가드 + 소수형 rename

# Status

ready

# Owner

monorepo

# Task Tags

- code
- test

# Goal

`platform/testing-strategy.md` 는 controller-slice 테스트 네이밍을 **`*ControllerTest.java`** 로 선언한다(§ Controller Slice Tests + Naming Conventions 표). 그런데 실제 코드는 **3형태로 갈라져** 있고, Source-of-Truth 우선순위(platform > code)상 **코드가 스펙을 위반**하는 상태다:

| 형태 | 프로젝트 (2026-07-21 관찰 — ⚠️ AC-0 재측정) |
|---|---|
| `*ControllerTest` | ecommerce, iam (정경 준수) |
| `*ControllerSliceTest` | fan, erp, finance, scm (**다수**) |
| `*ControllerWebMvcTest` | wms (admin; 같은 wms 안에서 master 는 `*ControllerTest`) |

이건 단순 오타가 아니라 **선언↔진실 드리프트**다(강제하는 게 없어 갈라졌다). 발단: `TASK-MONO-458` 커버리지 감사에서 `*ControllerTest$` regex 가 슬라이스를 과소집계해 하마터면 13건짜리 phantom 갭 티켓을 낳을 뻔했다(본문 확인으로 기각). 그 마찰은 다음 감사·도구에서 반복된다.

**이 task 의 목표는 "이름만 rename" 이 아니다.** rename 만 하면 강제하는 것이 없어 다시 갈라진다("1곳에만 있는 규칙 = 없는 규칙"). 목표는 **정경 화해 + 가드 + 소수형 rename 을 한 원자 단위로** 랜딩하는 것이다.

## 정경 방향 판정: `*ControllerTest` 가 아니라 `*ControllerSliceTest` 로 (스펙을 올린다)

정경을 코드로 끌어내리지(`*ControllerTest` 로 통일) 말고, **더 나은 다수형으로 스펙을 올린다.** 근거:

1. **다수**(4 프로젝트)가 이미 이걸 쓴다.
2. **`*ControllerTest` 는 정보가 적다** — `FooControllerTest` 만 봐선 `@WebMvcTest` 슬라이스인지 full-context 컨트롤러 IT인지 구분 불가. `*ControllerSliceTest` 는 **격리 수준(테스트 타입)을 이름에 인코딩**해 한눈에 구분된다. 즉 "드리프트" 가 실은 프로젝트들이 독립 수렴한 **개선**이다.
3. 스펙의 현재 Naming 표에는 잠재 충돌도 있다 — controller-slice = `{ControllerName}Test` 이면서 Integration(infrastructure) = `{ClassName}Test`. 슬라이스에 `Slice` 마커를 넣으면 이 모호함도 사라진다.

# Scope

## In Scope

1. **정경 화해 (스펙 우선).** `platform/testing-strategy.md` § Controller Slice Tests 의 `Naming: *ControllerTest.java` 와 Naming Conventions 표의 controller-slice 행을 **`*ControllerSliceTest`** 로 개정. (스펙이 먼저 — 가드가 무엇을 강제할지 정의된다.)
2. **CI 드리프트 가드 신설.** `@WebMvcTest`(및 존재 시 `@WebFluxTest`) 애노테이션을 담은 테스트 파일은 파일명이 `*ControllerSliceTest.java` 여야 한다고 단언. 대리지표(파일명 추측) 아닌 **애노테이션 자체를 술어로**. `scripts/check-*-drift.sh` + `ci.yml` 잡, `code-changed` 게이팅.
3. **소수형 rename.** `*ControllerTest`(ecommerce, iam) + `*ControllerWebMvcTest`(wms) 의 controller-slice 파일을 `*ControllerSliceTest` 로 `git mv`. 참조(있다면) 갱신.
4. **rename 과 가드를 같은 PR 에.** 가드가 첫날 GREEN 이도록(§ G2).

## Out of Scope

- **full-context 컨트롤러 IT** 이름 변경 — 슬라이스(`@WebMvcTest`, service mock)만 대상. `@SpringBootTest` full-context 는 `*IntegrationTest` 정경 유지.
- 슬라이스가 아닌 웹 테스트(예: `GlobalExceptionHandler` slice 가 controller 이름을 안 가진 경우) — 가드는 controller-slice 만 좁게 문다.
- 다른 계층(unit/event/e2e) 네이밍 — 이 task 는 controller-slice 축만.
- 새 슬라이스 테스트 추가 — 순수 rename + 스펙 + 가드.

# Acceptance Criteria

**AC-0 — 착수 전 재측정 (verify-then-act).** 위 3형태 분포표를 `origin/main` 에서 **다시 센다** — 인계 숫자는 출처가 아니라 가설이다([[feedback_recount_population_dont_inherit_scope]]). 검출은 파일명이 아니라 **애노테이션**(`@WebMvcTest`/`@WebFluxTest`)으로: 각 파일의 실제 격리 수준을 확인하고, 그중 canonical 접미사를 안 쓴 파일만 rename 대상이다. `*ControllerTest` 중 실제로는 full-context IT 인 것(오분류)이 있으면 제외한다. 예상 rename 규모 ~50-70 파일이나 실측이 이긴다.

**AC-1 — 스펙을 먼저 개정한다.** `testing-strategy.md` 두 곳(§ Controller Slice Tests, Naming 표) + Change Rule 준수. 가드·rename 은 개정된 스펙을 강제/추종한다.

**AC-2 — 가드가 애노테이션을 문다(대리지표 금지).** 술어 = "`@WebMvcTest`/`@WebFluxTest` 를 담은 파일 ⇒ 파일명 `*ControllerSliceTest.java`". 파일명만 보고 추측하지 않는다([[feedback_guard_predicate_wrong_verify_the_artifact]]).

**AC-3 — 가드가 무는 것을 mutation 으로 증명하고, 첫날 GREEN.** rename 이 끝난 상태에서 가드는 GREEN, 파일 하나를 옛 이름으로 되돌리면 RED(§ G3). 가드와 rename 이 같은 PR 이라 첫날 RED 가 아니다(§ G2 — 첫날 RED 가드는 꺼진다).

**AC-4 — 무손실 rename.** `git mv` 만; 테스트 본문·로직 무변경. 각 모듈 `:test`(또는 `compileTestJava`) 가 rename 후에도 초록. 참조(문서/다른 테스트가 클래스명 인용)가 있으면 같은 커밋에 갱신([[feedback_deletion_leaves_survivors_grep_the_consumers]]).

**AC-5 — 가드가 커버 안 하는 것을 적는다(§ G8).** 예: controller 이름 없는 슬라이스, `@JsonTest`/`@DataJpaTest` 등 다른 슬라이스 타입은 이 가드 대상 아님 — 명시.

# Related Specs

> **Before reading**: `platform/entrypoint.md` Step 0(공유 파일 대상이므로 프로젝트 분류는 각 rename 대상 프로젝트별로 확인).

- `platform/testing-strategy.md` — § Controller Slice Tests(`Naming: *ControllerTest.java`), § Naming Conventions 표(controller-slice 행), § CI Guards/Drift Detectors § G1~G8(가드 저작 규칙)
- `.github/workflows/ci.yml` + `scripts/check-*-drift.sh` — 기존 드리프트 가드 패턴(가드 잡 배선 참조)
- `docs/adr/` — 필요 시 네이밍 결정 기록(경미해 ADR 불요 예상, 판단은 착수 시)

# Related Contracts

없음 (테스트 파일 rename + 스펙 + CI 가드 — API/이벤트 계약 무변경).

# Edge Cases

- **`*ControllerTest` 중 full-context IT 오분류.** `@SpringBootTest` 로 컨트롤러를 도는 파일이 `*ControllerTest` 로 이름 붙었을 수 있다. 애노테이션으로 판별 — 그건 slice 아니므로 rename·가드 대상 아님.
- **controller 이름 없는 슬라이스.** `@WebMvcTest` 를 쓰지만 클래스명에 `Controller` 가 없는 경우(예외 핸들러 슬라이스). 가드 술어를 "controller-slice" 로 좁게 둘지, "모든 `@WebMvcTest` ⇒ `*ControllerSliceTest`" 로 넓게 둘지 착수 시 판정하고 AC-5 에 기록.
- **wms 내부 혼재.** wms 는 `*ControllerWebMvcTest`(admin)와 `*ControllerTest`(master)가 공존 — 한 프로젝트 안에서도 갈라졌다는 게 "강제 부재" 진단을 강화한다. 둘 다 rename.
- **`@WebFluxTest` 리액티브 슬라이스.** 게이트웨이는 full-context IT 라 해당 없지만, 다른 서비스에 있으면 포함할지 판정.
- **대량 rename 의 리뷰 부담.** ~50-70 파일 `git mv` 는 diff 가 크다 — 순수 rename 커밋과 스펙/가드 커밋을 분리하면 리뷰가 쉽다(같은 PR 안에서).

# Failure Scenarios

- **가드를 파일명-추측으로 만드는 것.** "이름에 Controller 있고 Test 로 끝나는데 SliceTest 아님" 식은 full-context 컨트롤러 IT 를 오탐한다(§ G2, MONO-360 의 `all` 오탐 클래스). 애노테이션으로 물어야 한다.
- **rename 만 하고 가드를 안 넣는 것.** 강제 부재가 그대로라 재드리프트. 이 task 의 존재 이유가 그것이다.
- **가드를 rename 전에 머지.** 첫날 RED → 꺼진다(§ G2). rename+가드 원자 랜딩 필수.
- **스펙을 안 고치고 코드만 통일.** 스펙은 여전히 `*ControllerTest` 를 말해 다음 저자가 그걸 보고 되돌린다. 스펙이 정경(platform > code).
- **참조 유실.** 클래스명을 인용하는 문서/주석/다른 테스트를 grep 안 하면 rename 후 깨진 참조가 남는다([[feedback_deletion_leaves_survivors_grep_the_consumers]]).

# Notes

- **긴급도 낮음(위생).** 지금까지 치른 실제 비용은 감사 도구 마찰 1회(MONO-458, 이미 DONE 노트+메모리 문서화)뿐. 큐에 높은 가치 작업이 있으면 뒤로 미뤄도 된다 — 다만 스펙 위반이 살아있어 HARDSTOP-06성 모호함을 남기므로 백로그에 명시적으로 둔다.
- **출처**: `TASK-MONO-458`(게이트웨이 IT 형제 parity) 커버리지 감사 중 발견 — slice regex 가 3형태를 못 세 phantom 갭을 낼 뻔한 것이 계기. 정경 방향(`*ControllerSliceTest`)은 사용자와의 논의에서 확정(다수 + 테스트 타입 인코딩 + 모호함 해소).
- **모델**: 스펙 판정은 경미(방향 이미 정함), 본체는 대량 기계적 rename + 가드 저작. 분석=Opus 4.8 / 구현 권장=**Sonnet**(rename 은 기계적, 가드는 § G1~G8 패턴 추종 — 단 AC-2 술어 설계는 주의).
- 인접: `TASK-MONO-458`(계기) · `platform/testing-strategy.md § CI Guards`(가드 저작 규칙) · 기존 드리프트 가드들(MONO-345/352/360/363/371/372 — 같은 선언↔진실 클래스).

[[feedback_repo_knows_what_it_does_not_say]] [[feedback_guard_predicate_wrong_verify_the_artifact]] [[project_guard_reachability_not_just_bite]] [[feedback_recount_population_dont_inherit_scope]] [[feedback_deletion_leaves_survivors_grep_the_consumers]]
