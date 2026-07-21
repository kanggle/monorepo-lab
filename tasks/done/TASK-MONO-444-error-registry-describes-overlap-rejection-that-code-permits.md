# Task ID

TASK-MONO-444

# Title

`platform/error-handling.md` 가 코드가 하지 않는 동작을 선언한다 — `PERIOD_WINDOW_INVALID` 의 "overlaps an existing period"

# Status

done

# Owner

architecture / platform

# Task Tags

- docs
- governance
- platform

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

[`platform/error-handling.md`](../../platform/error-handling.md) 의 `## Settlement` 절이 이렇게 적고 있다:

> `PERIOD_WINDOW_INVALID | 422 | The settlement period window is malformed (`from >= to`, **or it overlaps an existing period**).`

**코드는 겹침을 거부하지 않는다.** `OpenSettlementPeriodUseCase` 의 클래스 Javadoc 이 정반대를 명시한다 — *"no overlap check (**a tenant may run overlapping windows** — the close folds whichever accruals fall in `[from, to)`)"*. 그리고 프로젝트 계약 [`settlement-api.md`](../../projects/ecommerce-microservices-platform/specs/contracts/http/settlement-api.md) 은 `from ≥ to` 만 422 사유로 적고 겹침은 **언급하지 않는다**.

즉 **layer-5 공유 파일이 두 하위 계층과 어긋나 있다.** `CLAUDE.md` § Source of Truth Priority 상 `platform/`(5) > 계약(6) > 코드(14) 이므로, 문자 그대로 읽으면 **코드가 위반 상태**가 된다.

## 왜 CI 가 이걸 못 잡나 (이 티켓의 핵심)

`scripts/check-error-code-registry.sh` 는 **GREEN 이다.** 그 검사의 술어는 *"방출되는 모든 에러코드가 레지스트리에 등록돼 있는가"* 이지 *"등록된 설명이 참인가"* 가 아니다. **산출물(코드 등록)은 옳고 문장(설명)이 틀린 경우라 구조적으로 탐지 밖**이다.

이 저장소가 반복해 만난 클래스다 — 선언은 문서에 있고 실행 경로에는 없다. 다만 여기서는 방향이 반대다: **문서가 코드보다 더 많은 것을 약속한다.**

## 발견 경위

`TASK-BE-535`(정산 기간 중복 OPEN 가드) 착수 중, AC-4 의 "exact-dup 을 막을 것인가 overlap 을 막을 것인가" 판단을 위해 레지스트리를 읽다 발견. **그 판단을 이 모순이 좌우했으므로** 그냥 넘기지 않고 기록했다. BE-535 는 사용자 결정으로 **exact-dup 만** 가드했고(부분 unique 인덱스), 겹침은 문서화된 설계라 건드리지 않았다.

---

# Scope

## In Scope

**먼저 어느 쪽이 참이어야 하는지 판정한다.** 두 갈래이며, 이 task 는 그 판정을 요구할 뿐 미리 고르지 않는다:

- **(A) 문서가 틀렸다** — 겹침 허용이 의도된 설계이므로 레지스트리 설명에서 "or it overlaps an existing period" 를 제거한다. 가장 싸고, 코드·계약·Javadoc 셋과 일치한다.
- **(B) 코드가 틀렸다** — 겹침은 실제로 거부돼야 하므로 `OpenSettlementPeriodUseCase` 에 overlap 검사를 넣고 Javadoc·계약을 함께 고친다. **이 경우 라이브 콘솔 소비자의 동작이 바뀌고**, 문서화된 설계를 뒤집는 것이라 **ADR 게이트(HARDSTOP-09) 대상**이다.

판정 후 그에 맞춰 정정한다.

## Out of Scope

- **BE-535 가 넣은 exact-dup 가드 수정 0** — 부분 unique 인덱스 `(tenant_id, period_from, period_to) WHERE status='OPEN'` 는 어느 갈래에서도 유효하다(중복은 겹침의 퇴화형이고, 재개방 정정은 계속 허용돼야 한다).
- **레지스트리 검사 스크립트 확장 0** — "등록된 설명이 참인가" 를 기계 검사하는 건 별개의 큰 문제다(§ Failure C 참조). 필요하면 별 티켓.
- 다른 에러코드 설명 감사 0 — 이 한 건만.

---

# Acceptance Criteria

0. **AC-0 재측정** — 착수 시 다시 확인한다: (a) 레지스트리 문장이 여전히 겹침 거부를 선언하는가, (b) `OpenSettlementPeriodUseCase` 가 여전히 겹침을 허용하는가, (c) `settlement-api.md` 가 여전히 침묵하는가. **하나라도 다르면 범위를 재조정한다.** (선행 숫자·문장은 출처가 아니라 가설.)
1. (A)/(B) 중 어느 쪽이 참인지 **판정하고 그 근거를 기록**한다.
2. 판정에 맞춰 정정한다. (B) 를 고르면 ADR 이 선행이며 이 task 는 그 ADR 이 ACCEPTED 된 뒤에만 구현으로 넘어간다.
3. 정정 후 `platform/error-handling.md` · `settlement-api.md` · `OpenSettlementPeriodUseCase` Javadoc **세 곳이 서로 모순되지 않는다** — 하나만 고치고 끝내지 않는다(그게 이 결함을 만든 방식이다).
4. `bash scripts/check-error-code-registry.sh` GREEN 유지.
5. (A) 를 골랐다면 코드·테스트 변경 0(문서만). (B) 를 골랐다면 겹침 거부를 단언하는 테스트가 존재.

---

# Related Specs

- [`platform/error-handling.md`](../../platform/error-handling.md) § Settlement — 문제의 문장
- [`projects/ecommerce-microservices-platform/specs/contracts/http/settlement-api.md`](../../projects/ecommerce-microservices-platform/specs/contracts/http/settlement-api.md) § `POST /api/admin/settlements/periods`
- `projects/ecommerce-microservices-platform/apps/settlement-service/.../OpenSettlementPeriodUseCase.java` — 겹침 허용을 명시한 Javadoc
- [`CLAUDE.md`](../../CLAUDE.md) § Source of Truth Priority — 왜 이게 모순인지의 근거
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-535-money-path-duplicate-request-guards.md` — 발견 경위 + exact-dup 가드의 범위

---

# Related Contracts

- `settlement-api.md` — (B) 를 고르면 422 사유에 겹침이 추가돼야 하고, 이는 **라이브 콘솔 소비자**(`platform-console` → `/api/ecommerce/settlements/periods`)의 동작 변경이다.

---

# Edge Cases

1. **겹침 허용이 실제로 이중지급 벡터다** — `close` 가 accrual 을 변경 없이 read-only fold 하므로, 겹치는 두 기간은 **의도된 겹침이든 아니든** 같은 accrual 을 두 번 지급한다. 즉 (A)"문서만 고친다" 를 골라도 **그 리스크는 남는다**. 남긴다면 그 사실을 명시적으로 적어라 — 조용히 남기지 마라.
2. **"문서를 코드에 맞춘다" 가 항상 옳지는 않다** — 문서가 원래 의도를 담고 코드가 미구현일 수도 있다. Javadoc 이 겹침 허용을 *적극적으로 정당화*하고 있다는 점이 (A) 쪽 증거지만, 그 Javadoc 도 코드와 같은 커밋에서 왔을 수 있다. **git 이력으로 어느 쪽이 먼저였는지 확인하면 판정에 도움이 된다.**
3. **한 곳만 고치는 유혹** — 레지스트리만 고치고 계약·Javadoc 을 안 보면 다음 사람이 같은 모순을 다른 지점에서 다시 발견한다.

---

# Failure Scenarios

## A. AC-0 에서 이미 정정돼 있음

→ phantom. 기록하고 종료.

## B. (B) 로 판정했는데 ADR 게이트가 필요하다는 걸 뒤늦게 깨달음

→ 구현 전에 STOP. 겹침 거부는 문서화된 설계를 뒤집고 라이브 소비자 동작을 바꾼다 = HARDSTOP-09. ADR 먼저.

## C. "레지스트리 설명이 참인지" 를 기계 검사하고 싶어짐

→ 매력적이지만 범위 밖이고 어렵다(설명은 자연어다). **이 티켓에서 시도하지 마라.** 하려면 별 티켓이고, 착수 전에 *현재 위반이 몇 건인지* 부터 세라 — 위반 0건이면 `TASK-MONO-328` 선례(신호 관측 전 착수 금지)가 적용된다.

---

# Test Requirements

- (A): 문서 3곳 정합 확인, 코드 diff 0, `check-error-code-registry.sh` GREEN.
- (B): 겹치는 윈도우 → 422 를 단언하는 테스트 + 기존 `overlapping_but_not_identical_window_is_still_allowed()` IT 의 **의도적 삭제**(그 테스트가 현재 반대 성질을 단언하고 있으므로 — 지우는 것 자체가 판정의 산출물이다).

---

# Definition of Done

- [ ] AC-0 재측정
- [ ] (A)/(B) 판정 + 근거 기록
- [ ] 레지스트리 · 계약 · Javadoc 세 곳 정합
- [ ] `check-error-code-registry.sh` GREEN
- [ ] 겹침 이중지급 리스크의 존치 여부를 명시적으로 기록

---

# Notes

- **Recommended impl model**: 분석=Opus 4.8 / **구현 권장=Sonnet** (A 로 판정 시 문서 3곳 정합). **(B) 로 판정되면 Opus** — ADR + 라이브 소비자 동작 변경.
- **분량**: (A) small / (B) medium+ADR.
- **dependency**: `선행` 없음. `TASK-BE-535`(done) 가 발견 출처.
- **이 task 가 방어하는 실패 모드**: 공유 규칙 파일이 코드보다 더 많은 것을 약속하면, 그 파일을 읽고 설계하는 사람은 **있지도 않은 보호를 전제로 설계한다.** 그리고 CI 는 그걸 못 잡는다 — 검사의 술어가 등록 여부이지 참·거짓이 아니기 때문이다.
