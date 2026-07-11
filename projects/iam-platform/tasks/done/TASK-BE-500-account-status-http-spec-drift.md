# Task ID

TASK-BE-500

# Title

iam 스펙 트리의 계정상태 HTTP 드리프트 정정 — `ACCOUNT_LOCKED`/`DORMANT`/`DELETED` 403 → 423/423/410 (TASK-BE-462 잔여)

# Status

done

# Owner

iam-platform

# Task Tags

- docs
- contracts
- bug

---

# Goal

`TASK-BE-462`가 계정상태 로그인 거부의 HTTP 상태를 **blanket 403 → 코드별 매핑**으로 바꾸면서 **코드와 `platform/error-handling.md`(공유 레지스트리)는 갱신했지만 iam 스펙 트리 4개 파일을 전부 403에 남겨뒀다.**

**배포된 코드** (`auth-service/presentation/exception/AuthExceptionHandler.java`):

```java
// :82-87
@ExceptionHandler(AccountLockedException.class)
public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException e) {
    // ACCOUNT_LOCKED → 423 LOCKED per platform/error-handling.md § Account (TASK-BE-462).
    return ResponseEntity.status(HttpStatus.LOCKED)          // 423
            .body(ErrorResponse.of("ACCOUNT_LOCKED", "Account is locked"));
}

// :95-104 — "not a blanket 403"
@ExceptionHandler(AccountStatusException.class)
public ResponseEntity<ErrorResponse> handleAccountStatus(AccountStatusException e) {
    HttpStatus status = switch (e.getErrorCode()) {
        case "ACCOUNT_DORMANT" -> HttpStatus.LOCKED;         // 423
        case "ACCOUNT_DELETED" -> HttpStatus.GONE;           // 410
        default -> HttpStatus.INTERNAL_SERVER_ERROR;         // ACCOUNT_STATUS_UNKNOWN → 500
    };
    ...
}
```

**공유 레지스트리** (`platform/error-handling.md:470-472`) — 코드와 일치:

| Code | HTTP |
|---|---|
| `ACCOUNT_LOCKED` | **423** |
| `ACCOUNT_DELETED` | **410** |
| `ACCOUNT_DORMANT` | **423** |

**iam 스펙 트리** — 전부 **403**에 머물러 있다:

| 파일 | 스테일 행 |
|---|---|
| `specs/contracts/http/auth-api.md` | `:364` `:365` `:366` (로그인) · `:434` (refresh) · `:695` `:696` `:697` (소셜 로그인) — **7행** |
| `specs/features/account-lifecycle.md` | `:22` (LOCKED) · `:30` (DORMANT) |
| `specs/use-cases/signup-and-login.md` | `:62` `:63` `:64` (EF-4/5/6) |
| `specs/use-cases/account-lockout-and-unlock.md` | `:28` |

## 왜 중요한가 (런타임 버그는 아니다)

프런트/BFF 중 이 코드들로 분기하는 곳은 **없다**(TS/TSX 전수 grep 0건) → **현재 살아있는 버그는 아니다.**

문제는 **Source of Truth 우선순위의 모순**이다. CLAUDE.md § Source of Truth Priority 에서 `platform/`(5층)이 `specs/contracts/`(6층)보다 **높다**. 지금 6층이 5층과 정면으로 다른 값을 말하므로, `auth-api.md` 를 근거로 구현/검증하려는 사람(또는 에이전트)은 **HARDSTOP-06(required specs missing or in conflict)** 조건에 놓인다. 실제로 이 갭은 코드를 읽지 않고 계약만 읽는 독자에게 **원리적으로 비가시**하다 — MONO-339/341/345/347 과 동일한 드리프트 클래스.

---

# Scope

## IN — iam 스펙 4파일의 상태값 정정 (문서 전용, 코드 무수정)

- `specs/contracts/http/auth-api.md` — 7행: `403` → `423`(LOCKED) / `423`(DORMANT) / `410`(DELETED)
- `specs/features/account-lifecycle.md` — `:22` `:30`
- `specs/use-cases/signup-and-login.md` — EF-4/5/6
- `specs/use-cases/account-lockout-and-unlock.md` — `:28`
- 각 표면에 근거(TASK-BE-462 / `platform/error-handling.md` § Account)를 남겨 **다음 독자가 403 으로 되돌리지 않게** 한다.
- `ACCOUNT_STATUS_UNKNOWN` → **500** 경로가 계약에 없다면 추가(핸들러 `default` 분기의 실제 동작).

## IN — 같은 파일의 라이더 1건 (방향 유일하므로 함께 정정)

- `auth-api.md:369` — 로그인 에러표의 `| 422 | VALIDATION_ERROR |` → **400**. iam `AuthExceptionHandler` 가 상속하는 공유 `CommonGlobalExceptionHandler`(`libs/java-web-servlet`)는 **모든** `VALIDATION_ERROR` 경로를 `HttpStatus.BAD_REQUEST` 로 반환하고(`:23` `:30` `:36` `:42`), 레지스트리(`error-handling.md:94`)도 400 이다. 422 는 어디서도 나오지 않는다. **방향이 유일**하므로 본 task 에 포함한다(같은 파일에 남겨두면 AC-4 의 "잔여 0" 정신에 어긋난다).

## OUT (의도적 제외 — 근거 포함)

- **코드 무수정.** 코드가 옳다(레지스트리와 일치). 고칠 대상은 스펙이다.
- **`INVALID_STATE` / `INVALID_CREDENTIALS` 상태 모순 → `TASK-MONO-350`(root 백로그, AC-0 사람-결정 게이트)로 분리.** 같은 파일(`auth-api.md`)에 있지만 **성질이 다르다**: 저건 방향이 정해져 있지 않다(iam 401 ↔ ecommerce 400 ↔ 레지스트리 400). 에이전트가 임의로 한쪽을 고르면 **라이브 로그인 흐름의 클라이언트 가시 계약을 아무 결정 없이 바꾸는 것**이 된다. 본 task 는 **방향이 유일한** 드리프트만 다룬다.
- **`auth-api.md:692` 의 `| 401 | INVALID_CODE |` → `TASK-MONO-350` 로 이관.** 이 코드는 **전 코드베이스에 emitter 가 0 건**이다(Java 전수 grep). 계약에만 존재하는 유령 행이고, 실제 authorization-code 교환 실패는 `OAuthProviderException` → **502 `PROVIDER_ERROR`** 로 나간다(= 클라이언트 오류가 게이트웨이 오류로 보고된다). **행을 삭제할지 vs 코드를 구현할지는 사람 판단**이므로 본 task 에서 손대지 않는다. 조용히 지우면 "구현 예정이던 것"을 없애는 것일 수도 있다.

---

# Acceptance Criteria

- **AC-1** — `auth-api.md` 의 `ACCOUNT_LOCKED`/`ACCOUNT_DORMANT` 행이 전부 `423`, `ACCOUNT_DELETED` 가 `410`. 3개 섹션(로그인 `:364-366` · refresh `:434` · 소셜 `:695-697`) **전부**.
- **AC-2** — `features/account-lifecycle.md` · `use-cases/signup-and-login.md` · `use-cases/account-lockout-and-unlock.md` 의 403 표기가 모두 정정.
- **AC-3** — 정정된 값이 **배포 코드**(`AuthExceptionHandler:82-104`)와 **레지스트리**(`platform/error-handling.md:470-472`) **양쪽과 일치**한다. 셋이 한 값을 말한다.
- **AC-4 (잔여 0)** — 정정 후 iam 트리 전체에서 `ACCOUNT_LOCKED|ACCOUNT_DORMANT|ACCOUNT_DELETED` 와 `403` 이 같은 줄에 남아 있는 곳이 **없다**(grep 로 확인 — 부분 수정은 같은 클래스의 잔여를 남긴다).
- **AC-5 (main 코드 무수정 + 누락 가드 신설)** — 배포 코드는 **한 줄도 바꾸지 않는다**(코드가 옳다). 다만 조사 결과 **`ACCOUNT_DORMANT`(423) 와 `ACCOUNT_DELETED`(410) 를 고정하는 테스트가 하나도 없다** — `handleAccountStatus` 의 `switch` 가 결정하는 바로 그 두 값이고, `AuthExceptionHandler` 단위 테스트 자체가 부재한다(`ACCOUNT_LOCKED`→423 만 `LoginControllerTest` 슬라이스가 고정). **이 부재가 스펙이 403 으로 남아도 아무것도 실패하지 않은 이유다** — `TASK-MONO-348` 과 동일한 구조적 원인(미테스트 핸들러 = 드리프트 허용). 따라서 `AuthExceptionHandlerTest` 를 신설해 DORMANT→423 · DELETED→410 · unknown→500(fail-loud) · **"어떤 계정상태 거부도 403 이 아니다"** 를 고정한다. 테스트 전용 diff 이므로 **행동 불변**.
- **AC-6 (mutation-check)** — 신규 가드가 실제로 무는지 확인한다. `handleAccountStatus` 를 blanket 403 으로 되돌려 주입했을 때 신규 테스트가 **실제로 FAIL** 해야 한다. 통과하는 가드는 무는 가드가 아니다(MONO-348 · BE-493 교훈).
- **AC-7** — `./gradlew :projects:iam-platform:apps:auth-service:test` GREEN.

---

# Related Specs

- `platform/error-handling.md` § Account (`:470-472`) — **권위**
- `projects/iam-platform/specs/features/account-lifecycle.md`
- `projects/iam-platform/specs/use-cases/signup-and-login.md`
- `projects/iam-platform/specs/use-cases/account-lockout-and-unlock.md`
- 선행: `TASK-BE-462`(코드 + 레지스트리를 바꾼 원 task)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (§ 로그인 / refresh / 소셜 로그인 에러표)

---

# Edge Cases

- **`ACCOUNT_STATUS_UNKNOWN` → 500.** 핸들러 `default` 분기가 **인식 못 한 모든 상태**를 500 으로 보낸다. 이건 의도된 fail-loud(알 수 없는 계정상태를 조용히 403 으로 흘리지 않음)이므로 계약에 **명시**한다.
- **423 LOCKED 는 WebDAV 코드**다(RFC 4918). 계정 잠금에 쓰는 건 널리 통용되지만 흔치는 않으므로, 계약에 "`LOCKED`(423) — 잠금 해제 흐름 필요" 같은 의미를 함께 적어 클라이언트가 401/403 과 구분해 처리하도록 유도한다.
- **`410 GONE` 은 캐시/크롤러 의미가 있다** — soft-delete 된 계정에 대한 로그인 시도 응답이므로 실무상 무해하나, 계약에 "삭제(익명화)된 계정" 의미를 명시.

# Failure Scenarios

- **부분 수정** — `auth-api.md` 만 고치고 `use-cases/`·`features/` 를 남기면 **드리프트가 그대로 남는다**(다음 독자는 use-case 를 읽는다). AC-4 의 grep 이 이걸 막는다.
- **방향 오판** — "스펙이 403 이니 코드를 403 으로 되돌리자"는 **정반대 수정**이다. 레지스트리(상위 SoT)가 423/410 이고 TASK-BE-462 가 의도적으로 바꾼 것이다. 코드는 건드리지 않는다.
- **범위 전염** — 같은 파일의 `INVALID_STATE`(401 vs 400) 가 눈에 띄어 "겸사겸사" 고치고 싶어진다. **하지 말 것** — 방향이 정해지지 않았다(`TASK-MONO-350`).
