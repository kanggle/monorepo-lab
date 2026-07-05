# TASK-PC-FE-201 — fix: wms-overview-state 테스트 주/월 경계 clock 미고정 flake (매주 월요일 RED)

**Status:** done
**Area:** platform-console / console-web · **Type:** test-fix (main GREEN 복구)
**Analysis model:** Opus 4.8 · **Impl model:** Opus 4.8

---

## Goal

`tests/unit/wms-overview-state.test.ts`가 **매주 월요일(및 매월 1일) KST 창에서 결정적으로 2건 실패**하는 선재 date-boundary 버그를 고쳐 `Frontend unit tests (console-web vitest)` 필수 레인을 복구한다. sweep(PC-FE-197~200)과 무관 — 2026-07-06(월) 날짜 롤오버로 표면화됨(clean origin/main에서도 재현, `git stash`로 확증).

## 근본 원인

`seedHappy()`의 `m.listShipments` mock은 `p.shippedAtFrom`이 `bounds.todayStartInstant` / `weekStartInstant` / `monthStartInstant` 중 무엇이냐로 today/week/month 카운트를 분기(`switch`). `const bounds = kstPeriodBounds()`가 **모듈 로드 시 실제 날짜**로 계산되는데:

- 월요일: KST 주 시작 == 오늘 시작 → `todayStartInstant === weekStartInstant` (동일 문자열).
- 매월 1일: 월 시작 == 오늘(또는 주) 시작.

JS `switch`는 첫 매칭 case만 실행하므로 두 bound가 같으면 뒤 `case`가 **죽은 분기**가 되어 그 창의 windowed 읽기가 다른 창의 mock 값을 받는다. 07-06(월)에 week 읽기가 today mock(2)을 받아 기대값 5와 불일치 → `expected { today: 2, week: 2, month: 6 } to deeply equal { today: 2, week: 5, month: 6 }`.

## 수정

`kstPeriodBounds()` 호출 직전에 **clock을 고정 non-boundary 시각으로 핀**:

```ts
vi.useFakeTimers({ toFake: ['Date'], now: new Date('2026-07-15T12:00:00+09:00') });
const bounds = kstPeriodBounds();
```

2026-07-15(수, 월중) → day=07-15 / week=07-13(월) / month=07-01 항상 distinct. `toFake:['Date']`만 고정(setTimeout 등 미고정 → 비동기 fan-out 무영향). `afterAll(() => vi.useRealTimers())`로 정리(vitest 파일 격리이나 방어적). 프로덕션 코드·다른 테스트 무변경. 오해 소지 있던 "starts are stable" 주석도 정정.

## Acceptance Criteria
- **AC-1** `vitest run tests/unit/wms-overview-state.test.ts` 9/9 green (요일 무관 결정적).
- **AC-2** `tsc --noEmit` 0 + `next lint` 0.
- **AC-3** 프로덕션 소스(`overview-state.ts`·`kst-period.ts`) 무변경 — 테스트 fixture만.
- **AC-4** CI `Frontend unit tests (console-web vitest)` 레인 green 복구.

## Edge Cases / Failure Scenarios
- fake `Date`가 파일 밖으로 누출되지 않도록 `afterAll` 정리(vitest 파일 격리로도 안전하나 이중 방어).
- `toFake:['Date']`만 → fan-out의 Promise/microtask·`shippedAtTo=now`(문자열 존재만 단언) 동작 불변.

## Related
- 표면화 계기: PC-FE-200(ecommerce sweep 2/2) 로컬 검증 중 발견(무관 회귀).
- 프로덕션 로직 정상 — 순수 테스트 fixture clock 버그.
