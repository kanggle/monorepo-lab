# TASK-PC-FE-246 — 상대시간 포매터 dedup → `formatRelativeAge()`

**Status:** done
**Area:** platform-console / console-web · `shared/lib/datetime.ts` + `features/{notifications,ledger-ops}`
**Type:** `TASK-PC-FE` (frontend refactor — dedup, **행동 불변**)
**Lifecycle:** backlog(2026-07-18 발굴; 조사 2건은 PC-FE-247 로 분리) → 구현 → review

> **구현 완료 (2026-07-18, 미머지)**: 두 개의 바이트-동일 상대시간 버킷 로직을 `shared/lib/datetime.ts formatRelativeAge()` 로 통합. 검증: tsc 0 · lint clean · NotificationBell + LedgerFxRates 31 tests GREEN, 테스트 무수정.

---

# Goal

동일한 "상대 age 버킷"(방금 / N분 전 / N시간 전 / N일 전) 로직이 두 곳에 독립 재구현돼 있었다 — 갈라진 게 아니라 **동일 로직의 전파**(라벨을 바꾸면 두 곳을 다 고쳐야 하고 하나를 놓칠 위험). 공용 `formatRelativeAge()` 로 통합.

# 재측정 결론 (갈라졌는가 먼저)

- `NotificationBell.formatShortDate(iso)` 와 `FxRatesTable.humanizeAge(seconds)` 의 **버킷 4단계·라벨 문자열이 바이트 동일**. 차이는 (a) 입력 타입(iso vs seconds), (b) NotificationBell 만 ≥30일에서 절대 날짜로 롤(FxRatesTable 은 무제한 "N일 전"). → 코어(seconds→버킷)는 동일 → dedup 이 옳다(전파 위험 해소, false coupling 없음).

# Scope (구현 완료)

- **신규** `shared/lib/datetime.ts formatRelativeAge(ageSeconds)` — 4단계 버킷(음수=방금 clamp). TZ-free.
- `FxRatesTable.tsx` — 로컬 `humanizeAge` 제거, `formatRelativeAge` 위임(호출부 1곳). 바이트 동일 출력.
- `NotificationBell.tsx` — `formatShortDate` 를 `ageSeconds` 계산 후 <30일은 `formatRelativeAge` 위임, ≥30일은 기존 절대 fallback 유지로 재구성. `now` 소스는 `new Date()` 유지(테스트 mock 안전). 경계·출력 바이트 동일(중첩 floor 동치 검증).

# Out (의도적 보존 — 관찰가능 변경 제외)

- **NotificationBell 절대 fallback 의 `timeZone` pin 미적용** — datetime.ts 관례(§1: `Asia/Seoul` pin = SSR/hydration 안전)에 비추면 이 30일+ 절대 fallback 은 pin 이 없어 §1 드리프트지만, **pin 추가는 관찰가능 변경**이라 이 dedup 에서 제외. **후속 후보**로 남김(NotificationBell 은 client 컴포넌트라 hydration 영향 낮음, 30일+ 알림은 희소).
- **조사 2건**(api/types.ts 상태머신 게이트 sizing · DomainHealthCard pill 정책) → **`TASK-PC-FE-247`** (backlog investigation) 로 분리.

# Acceptance Criteria

- [x] `formatRelativeAge` 신설, 두 소비자 위임, 출력 바이트 동일(음수 clamp·경계 보존).
- [x] 검증: tsc 0 · next lint clean · NotificationBell + LedgerFxRates 31 tests GREEN, 테스트 무수정.
- [x] 조사 2건 PC-FE-247 로 분리.

# Related Specs

- 정경 `docs/conventions/frontend-ui.md` §1 (datetime — 절대 포맷 TZ pin). `shared/lib/datetime.ts` 헤더 관례.

# Edge Cases / Failure Scenarios (검증됨)

- 중첩 floor 동치: `NotificationBell` 원본 `floor(floor(floor(diffMs/60000)/60)/24)` 와 `floor(ageSeconds/86400)` 는 비음정수에서 동일 → 30일 경계·일수 라벨 불변.
- FxRatesTable 음수 age(clock skew) → `<60` 분기로 "방금" clamp 보존.
- 두 컴포넌트 테스트가 age 라벨을 pin → 바이트 동일 위임으로 GREEN(무수정).

# Review Notes

- 발굴: 2026-07-18 리팩토링 스윕. "중복이 곧 병은 아니다" 원칙으로 갈라짐 여부 먼저 재측정 → 코어 동일 확인 후 dedup.
