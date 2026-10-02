# Task ID

TASK-BE-612

# Status

done

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

- [x] **AC-1** — 재설정 확인 성공 + 계정이 `AUTO_DETECT` 로 LOCKED → account-service 호출로 ACTIVE 전이 + `account.unlocked`
      이벤트(reason=`USER_RECOVERY`) 발행. 단위/IT 로 확인.
- [x] **AC-2** — 대조군: 계정이 `ADMIN_LOCK` 으로 LOCKED → 같은 재설정 확인을 거쳐도 계정은 **여전히 LOCKED**(자동 해제되지
      않음). IT 로 확인 — 이 대조군이 없으면 AC-1 은 "모든 잠금이 재설정으로 풀린다"와 구별되지 않는다.
- [ ] **AC-3** ⏳ 재굽기 뒤 창(`TASK-MONO-737` · `TASK-BE-609` 와 같은 창) — 🔴 **라이브 창 판정**: 데모에서 AUTO_DETECT 로 계정을 잠근 뒤(합성 `auth.token.reuse.detected` 또는 다른
      가용한 방법) 그 계정으로 비밀번호 재설정을 완료하고, `account_db.accounts.status` 가 **결과 상태**로 `ACTIVE` 가
      되는지 확인한다. 로그 침묵은 판정이 아니다(이 티켓이 이어받는 `TASK-BE-608`/`TASK-MONO-672` 의 반복 원칙). 대조군으로
      `ADMIN_LOCK` 계정 하나도 같은 창에서 같이 재고, `LOCKED` 로 남는지 확인한다.
- [x] **AC-4** — account-service 가 응답하지 않거나 5xx 를 낼 때 재설정 확인 자체(비밀번호 변경 · 세션 폐기)는 실패하지
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

---

# 구현 결과 (2026-09-29 UTC · 분석=Opus 5.5)

**계약 먼저**: `auth-to-account.md` § `POST /internal/accounts/{accountId}/unlock — 비밀번호 재설정에 의한 자기 복구`(새 caller) ·
`account-lockout-and-unlock.md` UC-8(자동 잠금 UC-6 의 대칭).

**설계 판단 — 해제 가부의 권위는 account-service** (Edge Case 1 의 두 선택지 중 «상태기계 쪽 거부» + auth-service 의 «LOCKED 일 때만 호출»):
auth-service 가 잠금 사유를 판정하면 호출자 실수 하나로 `ADMIN_LOCK` 이 풀린다(Failure Scenario 1). 그래서 판정은 한 곳 —
`AccountStatusUseCase.requireSelfRecoverableLock` — 에 두고, auth-service 는 **상태만** 보고 호출한다.
- 🔴 판정 행 = **그 계정을 실제로 잠근 전이**(`from != LOCKED, to == LOCKED` 의 가장 최근 행)이지 맨 위 행이 아니다. `ADMIN_LOCK` 뒤
  자동 탐지가 한 번 더 발동하면 `LOCKED→LOCKED(AUTO_DETECT)` 멱등 행이 맨 위에 쌓이고, 맨 위만 보면 운영자 잠금이 풀린다 — 셀로 핀.
- 잠근 전이 이력이 없으면 거부(모르면 풀지 않는다). 거부 = 기존 `StateTransitionException` → `409 STATE_TRANSITION_INVALID`, 상태·이력·이벤트 불변.
- `ADMIN_UNLOCK` 등 다른 사유는 이력을 보지 않는다(운영자 해제 불변 — 셀로 핀).
- 부수 수정: `AccountLockController.unlockAccount` 가 모든 해제를 `actorType=operator` 로 적고 있었다 → `USER_RECOVERY` 는
  `actorType=user`, `actorId=<accountId>`(`account-events.md` 가 이미 문서화한 값).

**auth-service**: `ConfirmPasswordResetUseCase` 가 **트랜잭션 커밋 뒤**(`TransactionSynchronization.afterCommit`) 상태를 조회하고
`LOCKED` 일 때만 `AccountServicePort.unlockForSelfRecovery` 를 부른다(`X-Tenant-Id` 없음 — 계정 행의 테넌트). 🔴 **fail-soft (AC-4)**:
상태 조회·해제의 어떤 실패도 로그만 남기고 재설정 응답은 그대로 — 새 비밀번호·세션 폐기는 이미 커밋돼 있다. 409 는 실패가 아니라 `REFUSED`.
4xx 는 재시도·서킷 집계 대상이 아니다(표준 resilience 설정) — 409 폭주가 서킷을 열지 않는다.

**검증 (로컬)**: `account-service:test` 527 / 0 fail / 47 skip(Docker IT) · `auth-service:test` 885 / 0 fail / 31 skip — **rc=0**.
새 셀: `AccountStatusUseCaseTest` 5(AUTO_DETECT 해제 · ADMIN_LOCK 거부 · ADMIN_LOCK+멱등 AUTO_DETECT 거부 · 이력 없음 거부 · ADMIN_UNLOCK 불변) ·
`ConfirmPasswordResetUseCaseTest` 5(LOCKED → 호출 · ACTIVE → 호출 없음 · 해제 실패 fail-soft · 조회 실패 fail-soft · **커밋 전에는 호출 안 함**) ·
`AccountServiceClientUnitTest` 3(200 UNLOCKED + 헤더 없음 · 409 REFUSED 1회 · 503 예외) · IT `AccountMutationTenantConfinementIntegrationTest` 2
(실제 DB: 자동 잠금 → 해제 200 · 이력 `actor_type=user` / 운영자 잠금 → 409 · LOCKED 유지 — 🔴 Docker → **CI 판정**).
**bite** (백업 → 변이 → 복사 원복 · md5 일치): ① 판정 제거 → 4 빨강 ② 커밋 전 즉시 실행 → 1 빨강 ③ 409 를 REFUSED 로 안 받음 → 1 빨강.
기존 셀 0 → 원복 후 rc=0.

⚪ **남긴 것 (소유자 결정 범위 밖 · 정보)**: `PASSWORD_FAILURE_THRESHOLD` 는 상태기계에 선언만 있고 **그 사유로 잠그는 호출자가 저장소에 없다**
(grep 전수 — 선언·상태기계·actor 매핑뿐). 누군가 이 사유로 잠그기 시작하면 «비밀번호를 잊어 잠긴 사용자가 재설정해도 안 풀리는» 경로가 된다 —
그때 소유자 결정(«AUTO_DETECT 만»)을 다시 물어야 한다.

# AC-3 창 런북

0단계 = 재굽기 확인(`RepoCommit` 이 이 PR 스쿼시를 조상으로 포함). 🔴 공유 데모 계정 금지 — **일회용 계정 둘**.
1. 계정 A: 합성 `auth.token.reuse.detected`(17차 창과 같은 방법)로 **자동 잠금** → `accounts.status=LOCKED` 확인 · 잠근 이력 행 `reason_code=AUTO_DETECT` 확인.
2. A 로 비밀번호 재설정(`TASK-BE-609` 런북 스텝 1 — 게이트웨이 경유) → 🟢 **`accounts.status=ACTIVE`**(결과 상태) · 최신 이력 `reason_code=USER_RECOVERY, actor_type=user` · 새 비밀번호 로그인 성공.
3. 대조군 계정 B: 콘솔에서 **운영자 잠금**(`TASK-MONO-737` 런북 ①) → B 로 재설정 → 🟢 재설정 204 · 새 비밀번호는 저장되지만 **`accounts.status=LOCKED` 유지** · auth-service 로그 `self-recovery … REFUSED`.
- 🔴 유효성 술어: 스텝 2 이전에 A 가 **LOCKED 였음**을 먼저 적어라(이미 ACTIVE 면 «해제됨» 은 아무것도 재지 않았다).

---

## CORRECTION (2026-10-02 UTC) — AC-3 라이브 🟢 (대조군 포함, 결과 상태 = DB)

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

| 계정 | 잠금 | 재설정 | `accounts.status` (결과) | `account_status_history` |
|---|---|---|---|---|
| `be612-1790934793@example.com` (`8cb67fce-…`, 풀 계정) | 합성 `auth.token.reuse.detected` 2건 → **LOCKED** | request 204 → Redis 의 토큰(값=계정 id 로 찾음, 출력 안 함) → confirm **204** | **ACTIVE** | `LOCKED · AUTO_DETECT · system` → `ACTIVE · USER_RECOVERY · user` |
| 대조군 `live18-1790933938@example.com` (`0bf5cab4-…`) | 콘솔 잠금 → **LOCKED** | request 204 → confirm **204** | **LOCKED 유지** | `LOCKED · ADMIN_LOCK · operator` (그 뒤 행 없음) |

- 재설정 뒤 새 비밀번호로 팬 로그인 성공(세션 accountId `8cb67fce-…`) · 틀린 비밀번호는 IAM 폼 오류 — `TASK-BE-609` § CORRECTION 2026-10-02.
- «합성 이벤트로 잠갔다» — 실제 리프레시 재사용이 이벤트를 내는 앞 구간은 이 측정 밖이다(`TASK-MONO-672` 의 방법과 같음).
⇒ `done/`.
