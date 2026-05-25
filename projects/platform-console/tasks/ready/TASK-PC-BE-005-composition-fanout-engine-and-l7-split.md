# Task ID

TASK-PC-BE-005

# Title

console-bff composition fan-out engine 공통 추출 + `OperatorOverviewCompositionUseCase` 593-line god-class 분리

# Status

ready

# Owner

backend

# Task Tags

- spec
- code

---

# Goal

`apps/console-bff/.../application/usecase/OperatorOverviewCompositionUseCase.java` (593 line) 와 `DomainHealthCompositionUseCase.java` 가 거의 동일한 fan-out 엔진 (`fanOut` / `time` / `resolve` / `emitErrorCounter` / `lowercase` / `COMPOSITION_TIMEOUT` / `CARD_ORDER` 등 300+ line 골격) 을 각자 보유. 두 use-case 의 공통 엔진을 `CompositionEngine` (또는 `FanOutEngine`) 으로 추출하고, 그 결과 `OperatorOverviewCompositionUseCase` 는 use-case 본연의 책임만 유지. 추가로 use-case 내부에 nested 된 6개 port interface + 1개 record (`CompositionLeg`) 를 `application/port/outbound/` 패키지로 이동 → Hexagonal 패턴 정합성 회복.

외부 동작 변경 없음 — Overview / Health endpoint response shape 불변.

---

# Scope

## In Scope

| L | 대상 | 변경 |
|---|---|---|
| L7 (god-class) | `apps/console-bff/.../application/usecase/OperatorOverviewCompositionUseCase.java` 593 line | use-case 본연 책임만 유지 (≤ 250 line). fan-out 엔진은 `CompositionEngine` 으로 추출 |
| L6 (engine 중복) | 위 + `apps/console-bff/.../application/usecase/DomainHealthCompositionUseCase.java` 300+ line 중복 | `CompositionEngine.fanOut(legs, timeout)` 공통 추출. credential-aware vs credential-less 시그니처 차이는 generic 또는 sub-interface 로 흡수 |
| L4 (nested port) | `OperatorOverviewCompositionUseCase` 내부의 nested public interface 6개 (`GapAccountsReadPort`, `WmsInventoryReadPort`, `ScmInventoryReadPort`, `FinanceBalanceReadPort`, `ErpDepartmentsReadPort`, ...) | `apps/console-bff/.../application/port/outbound/` 패키지 아래 독립 파일로 이동. import 일괄 갱신 |
| L4 (nested record) | `CompositionLeg` record (518 line 근처, nested public) | `application/port/outbound/CompositionLeg.java` 또는 `application/composition/CompositionLeg.java` 로 분리 |

## Out of Scope

- API contract 변경 (Overview / Health response shape 그대로)
- 새로운 port / use-case 추가
- `*HealthReadAdapter` / `*ReadAdapter` 의 mechanical 중복 — TASK-PC-BE-004 별건

---

# Acceptance Criteria

- [ ] `CompositionEngine` 클래스 도입, fan-out / supply / resolve / emitErrorCounter / lowercase / COMPOSITION_TIMEOUT / CARD_ORDER 등 공통 멤버를 모두 보유
- [ ] 두 use-case 의 fan-out 관련 코드 line 수 합계 ≥ 300 line 감소 (use-case 내부에 fan-out 엔진 로직 0)
- [ ] `OperatorOverviewCompositionUseCase` line 수 ≤ 250
- [ ] `DomainHealthCompositionUseCase` line 수 ≤ 150
- [ ] 6개 nested port interface 가 `application/port/outbound/` 아래 6 독립 파일로 존재
- [ ] `CompositionLeg` record 가 use-case 외부에 위치
- [ ] Overview endpoint 응답 byte-equal (snapshot test)
- [ ] Health endpoint 응답 byte-equal
- [ ] `./gradlew :projects:platform-console:apps:console-bff:check` BUILD SUCCESSFUL
- [ ] 기존 e2e (있다면) GREEN

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0.

- `platform/refactoring-policy.md` — Extract Class / Move Class
- `platform/coding-rules.md`
- `platform/service-types/rest-api.md` (또는 bff 적용 service type)
- `projects/platform-console/specs/services/console-bff/architecture.md` — Hexagonal port-adapter pattern 적용 여부 명시 확인
- 관련 ADR (있다면): ADR-MONO-013/017 (platform-console 도입)

# Related Skills

- `.claude/skills/backend/refactoring/SKILL.md` — Extract Class, "Why unit-only baseline is insufficient" 캐비엇 (Spring AOP self-invocation)

# Related Contracts

- Overview / Health endpoint response shape — refactor 후 byte-equal 필수

---

# Target Services

- console-bff

---

# Implementation Notes

## 엔진 추출 설계

- `CompositionEngine<C, R>` generic: `C` = credential context type (또는 `Void` for credential-less Health), `R` = leg result.
- `CompositionLeg<C, R>` record: leg name + supplier + timeout override + fallback (있으면).
- 두 use-case 의 차이:
  - Overview: credential 필요, 5 leg, 도메인별 응답 shape 다름
  - Health: credential 불필요, 5 leg, 모두 `{status, lastChecked}` shape 동일
- 둘 다 같은 engine 의 generic 변종으로 흡수 가능.

## 책임 분리 후 use-case 구조

- `OperatorOverviewCompositionUseCase` 가 가지는 것: 5 port 주입 + leg 정의 + engine 호출 + 결과 매핑.
- `DomainHealthCompositionUseCase` 가 가지는 것: 5 port 주입 + leg 정의 + engine 호출 + 결과 매핑.

## Port 이동

- 6 nested interface → 6 독립 파일. 패키지: `com.example.platformconsole.consolebff.application.port.outbound`.
- 호출처 (controller / adapter / config) 의 import 일괄 갱신.

## 테스트

- 두 use-case 의 unit test 가 기존 mock 을 사용해 다시 통과해야 함 — assertion 변경 금지.
- `CompositionEngine` 단위 테스트 신규 작성: leg 1 실패 시 다른 leg 결과 보존, timeout 초과 시 부분 결과 + error counter 증가 등.
- Overview / Health endpoint 의 byte-equal snapshot test 1개씩.

---

# Edge Cases

- `CompositionEngine` 의 timeout 처리 — 기존 `COMPOSITION_TIMEOUT` 동일 값 유지.
- error counter 의 meter name + tags 동일 보장 (Prometheus 쿼리 영향).
- credential-less Health leg 가 generic engine 에 들어갈 때 `Void` 또는 별 sub-interface 필요 — 설계 결정.
- nested port 이동 후 어떤 config (`@Bean`) 가 port 를 명시적으로 wire 하는지 확인 — `@Qualifier` 또는 default-name binding.

---

# Failure Scenarios

- response shape drift → frontend 또는 monitoring 깨짐. byte-equal snapshot test 필수.
- error counter 의 tag drift → Prometheus 쿼리 깨짐. meter site 별 enumerate 후 동일 보장.
- nested → top-level 이동 후 access modifier (public) 누락 시 컴파일 깨짐.
- generic engine 의 type bound 가 너무 좁으면 두 use-case 모두 흡수 못함 — `<C, R>` 시그니처를 충분히 일반화.

---

# Test Requirements

- 기존 단위 + IT 전부 통과 (assertion 변경 금지)
- `CompositionEngine` 단위 테스트 신규 (≥3 시나리오: all-success / partial-failure / timeout)
- Overview + Health snapshot test 신규 또는 기존 강화

Test command:

```
./gradlew :projects:platform-console:apps:console-bff:check
```

---

# Definition of Done

- [ ] `CompositionEngine` 추출 완료
- [ ] 두 use-case line 수 목표 달성 (≤ 250 / ≤ 150)
- [ ] 6 nested port + record 이동 완료
- [ ] Overview / Health byte-equal 검증
- [ ] `:check` BUILD SUCCESSFUL
- [ ] commit + push
- [ ] Ready for review
