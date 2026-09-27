# Task ID

TASK-BE-612

# Status

ready

# Title

비밀번호 재설정 확인이 **AUTO_DETECT 로 잠긴 계정을 자동 해제**한다 — 배선 안 된 `USER_RECOVERY` 를 연결한다 (`TASK-BE-608` § AC-3 소유자 결정의 구현)

# Owner

iam-platform

# Task Tags

- auth-service
- account-service
- security
- account-lifecycle

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 새 상태 전이가 아니라 이미 선언된 reason(`USER_RECOVERY`)을 기존 유스케이스에서 호출하는 배선이다. 서비스 간 내부 호출 계약 신설이 유일한 설계 결정.

---

# Goal

`TASK-BE-608` § AC-3(잠금 해제 경로 판독)이 확인한 대로, 계정 상태기계는 `LOCKED → ACTIVE (USER_RECOVERY)` 전이를 이미
**선언**하고 있지만(`AccountStatusMachine.java:35`) 그 reason 으로 `changeStatus` 를 호출하는 프로덕션 코드가 어디에도 없다
(`StatusChangeReason.java:8` 가 enum 값만 선언 — grep 전수, 유일한 다른 매치는 그 enum 선언 자신과
`AccountStatusMachineTest`). `TASK-BE-606` 이 재사용 탐지의 `AUTO_LOCK` 을 실제로 켠 뒤로 자동 잠금이 실사용자에게도
발동하므로, 잠긴 사용자가 운영자 개입 없이 스스로 복구할 수 있는 경로가 없다는 공백이 실질적인 문제가 됐다.

소유자 결정(2026-09-26 UTC, `TASK-BE-608` § AC-3): **비밀번호 재설정 확인**(이메일 소유 증명 + 전 세션 폐기가 이미 끝난
지점)이 성공하면, **`AUTO_DETECT` 로 잠긴 계정만** `USER_RECOVERY` 로 자동 해제한다. `ADMIN_LOCK` 으로 잠긴 계정은
그대로 잠금을 유지한다(운영자 개입이 필요한 잠금까지 셀프서비스로 풀리면 안 된다). 시간 경과 자동 해제와 현행 유지(운영자
수동 unlock 만)는 기각됐다.

# Scope

## In Scope

- auth-service `ConfirmPasswordResetUseCase`(`projects/iam-platform/apps/auth-service/src/main/java/com/example/auth/application/ConfirmPasswordResetUseCase.java`)가
  재설정 확인 성공 뒤, account-service 의 현재 계정 상태를 조회해 **`LOCKED` 이고 그 잠금의 최근 원인이 `AUTO_DETECT` 일 때만**
  `POST /internal/accounts/{accountId}/unlock` 을 `reason=USER_RECOVERY` 로 호출한다.
- account-service 쪽 — `AccountLockController.unlockAccount`(`AccountLockController.java:63-85`)가 이미 `reason` 을
  받아 `AccountStatusMachine.transition(LOCKED, ACTIVE, reason)` 을 태우는 경로라면 새 엔드포인트는 불필요 — **기존
  엔드포인트를 재사용**한다(그 reason 이 `AccountStatusMachine.java:35` 의 허용 집합에 이미 있으므로 상태기계 변경 없음).
  `ADMIN_LOCK` 계정에 `USER_RECOVERY` 로 호출이 오면 상태기계가 그 전이를 허용하지 않음을 **확인**한다(둘 다 LOCKED→ACTIVE
  이지만 reason 분기가 없다면 이 티켓이 그 분기를 추가한다 — 아래 Edge Cases 참조).
- 새 내부 호출 계약: `specs/contracts/http/internal/auth-to-account.md` 에 auth-service → account-service
  `POST /internal/accounts/{accountId}/unlock` 소비처를 추가(계약 먼저 — account-service 쪽은 이미 존재하는 엔드포인트이므로
  이 문서는 **새 caller** 를 등록하는 것이지 새 엔드포인트를 만드는 것이 아니다).
- 잠금 원인 조회 — 해제 가부 판단에 "그 계정이 `AUTO_DETECT` 로 잠겼다"는 사실이 필요하다. `account_status_history` 또는
  `accounts` 행 자체에 마지막 잠금 reason 이 남아 있는지 판독하고, 없으면 그 조회 경로부터 만든다(account-service 책임 —
  auth-service 는 조회만 한다).
- 단위 + IT: 정상 케이스(AUTO_DETECT 로 잠긴 계정 → 재설정 확인 → ACTIVE) · 대조군(ADMIN_LOCK 로 잠긴 계정 → 재설정 확인
  → 여전히 LOCKED, unlock 호출 자체를 안 하거나 호출해도 거부됨) · account-service 장애 시 재설정 자체는 실패하지 않아야
  하는지(fail-soft/fail-closed 판단 — Edge Cases).

## Out of Scope

- 시간 경과 자동 해제(TTL) — 소유자가 기각.
- `ADMIN_LOCK` 계정의 셀프서비스 해제 — 소유자가 명시적으로 막았다.
- 잠금 자체의 판정 로직(`TASK-BE-606`) — 이 티켓은 해제만 다룬다.
- `auth.token.reuse.detected` 의 `reusedJti` 다이제스트화 등 `TASK-BE-608` 의 다른 AC — 이미 그 티켓에서 종결됨.

# Acceptance Criteria

- [ ] **AC-1** — 재설정 확인 성공 + 계정이 `AUTO_DETECT` 로 LOCKED → account-service 호출로 ACTIVE 전이 + `account.unlocked`
      이벤트(reason=`USER_RECOVERY`) 발행. 단위/IT 로 확인.
- [ ] **AC-2** — 대조군: 계정이 `ADMIN_LOCK` 으로 LOCKED → 같은 재설정 확인을 거쳐도 계정은 **여전히 LOCKED**(자동 해제되지
      않음). IT 로 확인 — 이 대조군이 없으면 AC-1 은 "모든 잠금이 재설정으로 풀린다"와 구별되지 않는다.
- [ ] **AC-3** — 🔴 **라이브 창 판정**: 데모에서 AUTO_DETECT 로 계정을 잠근 뒤(합성 `auth.token.reuse.detected` 또는 다른
      가용한 방법) 그 계정으로 비밀번호 재설정을 완료하고, `account_db.accounts.status` 가 **결과 상태**로 `ACTIVE` 가
      되는지 확인한다. 로그 침묵은 판정이 아니다(이 티켓이 이어받는 `TASK-BE-608`/`TASK-MONO-672` 의 반복 원칙). 대조군으로
      `ADMIN_LOCK` 계정 하나도 같은 창에서 같이 재고, `LOCKED` 로 남는지 확인한다.
- [ ] **AC-4** — account-service 가 응답하지 않거나 5xx 를 낼 때 재설정 확인 자체(비밀번호 변경 · 세션 폐기)는 실패하지
      않는다(fail-soft) — unlock 호출은 최선 노력이고 재설정의 핵심 효과(새 비밀번호 · 세션 폐기)를 막지 않는다. 이 결정을
      코드 주석과 이 AC 에 명시한다.

# Related Specs

- `specs/features/password-management.md` § 패스워드 재설정
- `specs/use-cases/account-lockout-and-unlock.md` § UC-5·UC-6(자동 잠금) — 이 티켓이 그 대칭인 "자동 해제" UC 를 추가한다.
- `TASK-BE-608` § AC-3 해제 경로 표 · § CORRECTION(2026-09-26 UTC, 소유자 결정) — 이 티켓의 출처.
- `TASK-BE-606` § 후속(AUTO_LOCK 을 실제로 켠 변경).

# Related Contracts

- `specs/contracts/http/internal/auth-to-account.md` — auth-service 가 이 티켓에서 새로 호출하는
  `POST /internal/accounts/{accountId}/unlock` 을 caller 목록에 추가한다(엔드포인트 자체는 기존 —
  `specs/contracts/http/internal/admin-to-account.md` § `POST /internal/accounts/{accountId}/unlock` 참조).
- `specs/contracts/events/account-events.md` — `account.unlocked` 의 `reasonCode` 열거값에 `USER_RECOVERY` 가 **이미**
  문서화돼 있다(계약 변경 없음 — 구현이 스펙을 따라잡는 케이스).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 계정이 `ADMIN_LOCK` 으로 잠김 | 재설정 확인은 성공(비밀번호는 바뀜)하지만 계정은 `LOCKED` 로 남는다 — `AccountStatusMachine` 이 `USER_RECOVERY` 를 그 reason 에 대해 거부하거나, 호출 전에 auth-service 가 마지막 잠금 원인을 먼저 확인해 `AUTO_DETECT` 가 아니면 호출 자체를 생략한다 |
| 계정이 이미 `ACTIVE` | unlock 호출을 하지 않는다(불필요한 호출 금지) — 재설정 확인 흐름에서 상태를 먼저 조회 |
| account-service 장애/타임아웃 | AC-4 대로 fail-soft — 재설정 자체는 그대로 성공, unlock 실패는 로그만 남긴다 |
| 같은 계정이 짧은 시간에 여러 번 재설정을 시도 | unlock 호출은 멱등이어야 한다(`AccountStatusMachine.transition` 이 `current==target` 이면 그대로 반환하는 기존 멱등 규칙을 그대로 탄다) |

# Failure Scenarios

1. **`ADMIN_LOCK` 계정까지 자동 해제한다** → 운영자가 의도적으로 잠근 계정이 사용자 스스로 풀 수 있게 되어 소유자 결정을 어긴다.
2. **account-service 장애가 재설정 확인 자체를 막는다** → 이미 끝난 비밀번호 변경·세션 폐기가 unlock 실패 때문에 롤백되거나
   사용자에게 에러로 보이면 AC-4 위반이다.
3. **호출 계약을 스펙에 먼저 안 적고 코드부터 짠다** → `auth-to-account.md` 가 실제 호출자와 갈린다(이 저장소가 반복해서
   겪은 "계약보다 코드가 먼저" 패턴).
