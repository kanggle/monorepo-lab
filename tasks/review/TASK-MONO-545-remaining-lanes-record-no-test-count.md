# Task ID

TASK-MONO-545

# Title

같은 공백이 남은 7개 레인에 그대로 있다 — 초록으로 끝난 e2e·유닛 잡도 몇 개가 돌았는지 남기지 않는다 (`TASK-MONO-544` AC-4 전수 산물)

# Status

review

# Owner

monorepo

# Task Tags

- ci
- testing

---

# 배경

`TASK-MONO-544` 가 `_integration.yml` 을 고치면서 **AC-4 로 전수를 셌다**. 결과:
`.github/workflows/` 의 `upload-artifact` 스텝 **20개 중 8개**가 `if: failure()` 게이트다.

| 워크플로 / 잡 | 아티팩트 | 상태 |
|---|---|---|
| `_integration.yml / integration` | (입력) | ✅ `MONO-544` 가 닫음 |
| `_platform-e2e.yml / e2e` | (입력) | 🔴 남음 — **재사용 레인, 호출자 다수** |
| `ci.yml / build-and-test` | `test-reports` | 🔴 남음 — **전 저장소 유닛 테스트** |
| `ci.yml / frontend-e2e-smoke` | `playwright-report-smoke` | 🔴 남음 |
| `ci.yml / iam-platform-e2e-smoke` | `iam-platform-e2e-smoke-test-reports` | 🔴 남음 |
| `nightly-e2e.yml / web-store-iam-logout-e2e` | `…-iam-logout-nightly` | 🔴 남음 |
| `nightly-e2e.yml / iam-e2e-full` | `iam-e2e-full-test-reports-nightly` | 🔴 남음 |
| `nightly-e2e.yml / ecommerce-fulfillment-e2e-full` | `…-full-test-reports-nightly` | 🔴 남음 |

## 공백의 성격 (`MONO-544` 배경 그대로)

초록으로 끝난 잡은 **아티팩트를 남기지 않고**, 러너가 **카운트를 찍지도 않는다**. 그래서

1. 이 저장소의 AC 관용구 — *"네 값을 아티팩트에서 읽어라"* — 가 **성공 경로에서 작동하지
   않는다**.
2. **발견된 테스트가 0개여도 초록**이다. 태그 필터 · 네이밍 규칙 · 스펙 글롭이 어긋나
   스위트가 비어도 통과하고, 되짚을 산출물도 없다.

🔴 **`build-and-test` 가 가장 크다** — 저장소 전체 유닛 테스트다. 여기서 0-발견이 조용히
통과하면 그 회차 CI 는 사실상 아무것도 검증하지 않는다.

## 🔵 `MONO-544` 가 이미 만든 것 (복사해서 쓸 것)

`_integration.yml` 의 *"Summarise integration results and fail on an empty suite"* 스텝.
설계 결정 3개가 이미 검증돼 있다:

- **모집단을 글롭이 아니라 태스크 목록에서** 뽑는다 — 글롭은 아무것도 안 맞아도 자기와
  일치한다. 이 차이 때문에 **잡 합계가 건강해도 빈 모듈을 지목**할 수 있다.
- `set +e` · `pipefail` 미사용 — `bash -e` 는 매칭 없는 `grep` 하나로 스텝을 죽이고
  `grep | head -1` 은 SIGPIPE 를 받는다. **둘 다 가드가 조용히 안 도는 모습**이 된다.
- Gradle 이 이미 실패했으면 **보고만 하고 실패시키지 않는다**(컴파일 에러는 정당하게
  결과가 없다 — 두 번째 혼란스러운 실패를 만들지 않는다).

🔴 **그대로 복사할 수는 없다** — e2e 레인은 태스크 이름이 `:…:tests:e2e:e2eSmokeTest`
형태라 **모듈 경로 유도 규칙이 다르고**, Playwright 잡은 **JUnit XML 이 아니다**(리포터
포맷을 먼저 확인할 것). 프런트 잡은 vitest/Playwright라 산출물 형태가 또 다르다.

---

# Goal

남은 7개 레인도 **초록일 때 몇 개가 돌았는지 남기고**, **0개 발견이 초록이 아니다**.

---

# Scope

레인의 성격이 셋으로 갈리므로 **묶어서 처리한다**:

1. **Gradle/JUnit XML 계열** — `_platform-e2e.yml`, `ci.yml / build-and-test`,
   `ci.yml / iam-platform-e2e-smoke`, `nightly-e2e.yml / iam-e2e-full`,
   `nightly-e2e.yml / ecommerce-fulfillment-e2e-full`.
   `MONO-544` 스텝을 **경로 유도 규칙만 바꿔** 재사용.
2. **Playwright 계열** — `ci.yml / frontend-e2e-smoke`,
   `nightly-e2e.yml / web-store-iam-logout-e2e`.
   🔴 **리포터 산출물을 먼저 확인**할 것(`results.json`? `junit.xml`? 설정에 따라 다르다).
   없으면 리포터를 켜는 것이 선행 작업이다.
3. 공통 스텝을 **composite action 으로 뽑을지**는 AC-2 가 정한다 — 중복 7벌이 나오면
   그때 뽑는다. **선제 추상화 금지.**

## Out of Scope

- `_integration.yml` — `MONO-544` 가 닫았다.
- 아티팩트 업로드 조건 완화 — `MONO-544` 가 근거를 들어 기각했다(0-발견 시 올릴 파일이
  없어 **자기가 막을 경우에 무력**하고, `upload-artifact@v4` 의 **동일 이름 거부**가
  `MONO-541` 의 증상을 재생산한다). **되살리지 말 것.**
- 테스트 내용 변경.

---

# Acceptance Criteria

- [ ] **AC-0 (전수 재계수)** — 착수 시 `upload-artifact` 스텝을 **다시 세어** 위 표와
      대조한다. 🔴 `MONO-544` 이후 워크플로가 바뀌었을 수 있고, **표를 물려받는 순간
      틀린 모집단으로 일한다**. 차이가 있으면 표를 갱신하고 시작한다.
- [ ] **AC-1 (레인별 산출물 실측)** — 각 레인이 **실제로 무엇을 남기는지** 확인한다
      (JUnit XML 경로 / Playwright 리포터 포맷). 🔴 **추측 금지** — 최근 실패 회차
      아티팩트를 **내려받아** 확인한다.
- [ ] **AC-2 (구현)** — 각 레인에 카운트 보고 + 0-발견 실패를 넣는다. 중복이 실제로
      쌓이면 그때 공통화한다.
- [ ] **AC-3 (bite, 레인마다)** — 레인 **각각**에서 0-발견을 강제해 **그 잡이 빨개지는지**
      확인하고 되돌린다. 🔴 **한 레인만 물려 보고 나머지를 유추하지 말 것** — 산출물
      포맷이 다르므로 파서도 다르고, **안 무는 파서는 조용히 0을 보고한다**.
- [ ] **AC-4 (성공 회차에서 네 값)** — 각 레인의 **성공한** 잡에서 카운트를 실제로 읽어
      적는다. 🔵 로그에서 읽을 때 **숫자를 요구하는 술어**를 쓸 것 —
      Actions 가 스크립트를 그대로 에코하므로 `grep <marker>` 는 **스텝이 안 돌아도**
      템플릿 줄을 반환한다(`MONO-544` 가 자기 AC-3 에서 이 함정을 밟았다).
- [ ] **AC-5 (첫날 RED 처리)** — 가드가 기존 낙오를 드러내면 **같은 PR 에서** 처리한다.
      🔴 이 저장소의 결론이다(`MONO-360`·`MONO-451`): **첫날 빨간 가드는 꺼진다.**

---

# Related Specs

- `.github/workflows/_integration.yml` — 선례 구현 (`MONO-544`)
- `.github/workflows/_platform-e2e.yml` · `ci.yml` · `nightly-e2e.yml` — 변경 대상

# Related Contracts

없음 — CI 배선이며 API·이벤트 계약을 건드리지 않는다.

---

# Edge Cases

- **Playwright 리포터가 아무 파일도 안 남길 수 있다** — 그러면 카운트를 셀 대상이 없고,
  리포터 활성화가 선행이다. 그 경우 이 티켓에서 할지 별건으로 뺄지 **성격을 보고** 정한다.
- **nightly 레인은 PR 에서 안 돈다** — bite 를 PR 에서 확인할 수 없다.
  🔴 `workflow_dispatch` 로 수동 실행하거나, 확인 못 한 것을 **확인 못 했다고 적을 것**.
  ("nightly 전용 스펙이 초록으로 머지된 뒤 main 을 빨갛게 만든" 선례가 이 저장소에 있다.)
- **의도적으로 비어 있는 스위트**가 있는지 먼저 확인한다 — 있으면 그 잡이 즉시 빨개진다.

# Failure Scenarios

- **한 레인만 물려 보고 나머지를 유추한다** → 포맷이 다른 파서가 조용히 0을 보고한다.
- **AC-0 을 건너뛰고 위 표를 그대로 쓴다** → 틀린 모집단으로 일한다.
- **선제로 composite action 을 만든다** → 레인별 차이가 드러나기 전에 추상화가 굳는다.
- **첫날 RED 를 별건으로 미룬다** → 가드가 꺼진다.
