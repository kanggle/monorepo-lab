# Task ID

TASK-MONO-348

# Title

에러코드↔HTTP 상태 드리프트 정정 — `IllegalStateException` → `ILLEGAL_STATE`(422) 4서비스 + ledger `IllegalArgumentException` catch-all의 거짓 `CURRENCY_MISMATCH` 제거

# Status

done

# Owner

monorepo

# Task Tags

- bug
- contracts
- backend

---

# Goal

finance(ledger·account) + erp(masterdata·approval) 4개 `GlobalExceptionHandler`가 **자기 자신이 선언한 코드↔상태 표를 위반**한다. 두 부류의 결함을 제거하고, 표를 우회할 수 없게 구조적으로 봉한다.

네 파일은 모두 동일 설계를 공유한다 — `STATUS_BY_CODE` 상수를 두고 javadoc이 이렇게 선언한다:

> "The authoritative code→HTTP table lives in `<x>-api.md` § Error code → HTTP; `STATUS_BY_CODE` is the exhaustive mechanical mirror — a single `<X>DomainException` handler resolves the status from the code **so the mapping cannot drift per-exception**."

그런데 그 표를 **우회하는 핸들러**가 바로 같은 파일 안에 있다.

## 결함 A — `IllegalStateException` → `VALIDATION_ERROR` @ 422 (4서비스 전부)

네 서비스 모두 `STATUS_BY_CODE`에 `VALIDATION_ERROR → 400`을 등록해 두고(계약서도 `| VALIDATION_ERROR | 400 |`), `handleIllegalState`가 **같은 코드 문자열을 422로** 내보낸다. 결과: **한 서비스가 하나의 코드를 두 개의 HTTP 상태로 발행한다.** `code` 필드로 분기하는 클라이언트는 같은 코드에 대해 400과 422를 모두 보게 된다.

정답 코드는 이미 존재한다 — `platform/error-handling.md:133`:

> `| ILLEGAL_STATE | 422 | Generic IllegalStateException caught at the controller boundary (aggregate invariant violated) — the unclassified fallback. ... |`

scm procurement + fan 4서비스는 **이미 이 코드를 쓴다**(`GlobalExceptionHandler.java:173-178` / `AbstractDomainExceptionHandler`). finance·erp만 빠졌다.

## 결함 B — ledger `IllegalArgumentException` catch-all이 모든 IAE를 `CURRENCY_MISMATCH`로 라벨링

`ledger-service/GlobalExceptionHandler.java:102-106`:

```java
@ExceptionHandler(IllegalArgumentException.class)
public ResponseEntity<ApiErrorBody> handleIllegalArgument(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ApiErrorBody.of("CURRENCY_MISMATCH", e.getMessage()));   // ← 모든 IAE
}
```

**통화 관련 예외는 이 catch-all에 도달하지 않는다.** 전부 자기 전용 핸들러를 갖는다:

| 예외 | 상속 | 핸들러 |
|---|---|---|
| `Currency.UnsupportedCurrencyException` | `RuntimeException` | `handleUnsupportedCurrency` → 422 `CURRENCY_MISMATCH` |
| `Money.CurrencyMismatchException` | `RuntimeException` | `handleMoneyCurrencyMismatch` → 422 `CURRENCY_MISMATCH` |
| `LedgerErrors.CurrencyMismatchException` | `LedgerDomainException` | `handleDomain` → 표 |

즉 IAE catch-all에 도달하는 예외는 **정의상 통화와 무관한 것들뿐**인데, 전부 `CURRENCY_MISMATCH`라는 이름을 달고 나간다.

**클라이언트가 실제로 때릴 수 있는 경로** — `SettlementRequest.toCommand()`(F5 회피용 `String` 수동 파싱이라 bean-validation 방패가 없다):

```
POST /api/finance/ledger/settlements  { "settlementRate": "abc", ... }
  → SettlementRequest.parseRate → IllegalArgumentException("settlementRate must be a decimal string: abc")
  → handleIllegalArgument
  → 422 { "code": "CURRENCY_MISMATCH", "message": "settlementRate must be a decimal string: abc" }
```

**한 응답 안에서 코드와 메시지가 서로 모순된다.** 같은 파일의 `handleMalformed`는 "Malformed request body"를 **400 `VALIDATION_ERROR`**로 처리한다 — 본문 *안*의 깨진 십진수는 동일 부류의 실패인데 수동 파싱이라는 이유만으로 `422 CURRENCY_MISMATCH`가 된다. 그 밖에 `RevaluationRequest:53`(`closingRate`), `JournalLine`, `PostingPolicy`, `FxPositionLot`, `Money`의 IAE도 모두 "통화 불일치"로 보고된다.

운영 영향: FX 대시보드/알림이 통화 장애를 **과대집계**하고, 운영자를 엉뚱한 FX 디버깅으로 유인한다.

## 왜 지금까지 살아남았나 (재발 방지의 핵심)

**4개 핸들러 중 3개는 단위 테스트가 아예 없다** — ledger·erp masterdata·erp approval에 `presentation/advice/` 테스트 디렉터리가 존재하지 않는다. account에는 `GlobalExceptionHandlerTest`가 있으나 `handleDomain`/`handleMoneyCurrencyMismatch`/`handleUnsupportedCurrency`/`handleGeneral`만 검증하고 **`handleIllegalArgument`·`handleIllegalState`는 건드리지 않는다**. 결함 핸들러는 전부 미테스트 영역이었다.

scm이 올바른 이유는 정확히 그 반대다 — `GlobalExceptionHandlerTest.java:135` `"IllegalStateException → 422 ILLEGAL_STATE"`가 존재한다.

따라서 이 task는 코드 수정만으로 끝나지 않는다. **누락된 핸들러 테스트를 신설**해야 드리프트가 되돌아오지 않는다.

---

# Scope

## IN

**공유 (root 경로)**
- `platform/error-handling.md` — `ILLEGAL_STATE` 행의 emitter 목록에 erp(masterdata·approval) + finance(account·ledger) 추가.

**finance-platform**
- `apps/ledger-service/.../presentation/advice/GlobalExceptionHandler.java`
  - `handleIllegalArgument`: `422 CURRENCY_MISMATCH` → **`400 VALIDATION_ERROR`**
  - `handleIllegalState`: `422 VALIDATION_ERROR` → **`422 ILLEGAL_STATE`**
  - `STATUS_BY_CODE`에 `ILLEGAL_STATE → 422` 등록
  - 코드를 합성하는(=도메인 예외가 코드를 실어오지 않는) 핸들러 전부를 `STATUS_BY_CODE`를 거치는 단일 `respond(code, message)` 헬퍼로 통일 → **한 코드가 두 상태로 나갈 수 없게 구조적으로 봉함**
- `apps/account-service/.../presentation/advice/GlobalExceptionHandler.java`
  - `handleIllegalState`: `422 VALIDATION_ERROR` → **`422 ILLEGAL_STATE`**
  - `STATUS_BY_CODE`에 `ILLEGAL_STATE → 422` 등록 + 동일 `respond()` 통일
- `specs/contracts/http/ledger-api.md` — `VALIDATION_ERROR | 400` **신규 등록**(현재 핸들러가 발행 중인데 계약 표에 없다) + `ILLEGAL_STATE | 422` 등록
- `specs/contracts/http/account-api.md` — `ILLEGAL_STATE | 422` 등록
- `presentation/dto/SettlementRequest.java` · `RevaluationRequest.java` — "surfaces as a 422" javadoc 정정(이제 400)
- 테스트: ledger `GlobalExceptionHandlerTest` **신규** · account `GlobalExceptionHandlerTest` 확장

**erp-platform**
- `apps/masterdata-service` · `apps/approval-service`의 `GlobalExceptionHandler.java`
  - `handleIllegalState`: `422 VALIDATION_ERROR` → **`422 ILLEGAL_STATE`**
  - `STATUS_BY_CODE`에 `ILLEGAL_STATE → 422` 등록 + `respond()` 통일
  - **`handleIllegalArgument`는 손대지 않는다** — 이미 `400 VALIDATION_ERROR`로 표와 일치한다
- `specs/contracts/http/masterdata-api.md` · `approval-api.md` — `ILLEGAL_STATE | 422` 등록
- 테스트: 두 서비스 `GlobalExceptionHandlerTest` **신규**(현재 없음)

## OUT (의도적 제외 — 근거 포함)

- **account-service의 `handleIllegalArgument`(`422 AMOUNT_INVALID`) 유지.** ledger와 달리 **이건 옳다.** `account-api.md:170`이 `| AMOUNT_INVALID | 422 | ≤0 / scale / minor-unit violation |`이라 규정하고, 그 검증이 정확히 `Money.of()`가 던지는 IAE다(`Money.java:32` 음수 minor units · `:46` 비정수 문자열). account-service의 지배적 IAE 소스가 `Money`이므로 400 `VALIDATION_ERROR`로 바꾸면 **문서화된 동작을 깨뜨린다.** ledger에는 `AMOUNT_INVALID` 코드 자체가 계약에 없고, IAE 소스가 rate 파싱·도메인 불변식이라 상황이 다르다. **두 서비스를 대칭으로 만들려는 유혹을 명시적으로 거부한다.**
- **scm procurement 무수정.** `procurement-api.md:367`이 `| VALIDATION_ERROR | 400/422 |`로 이중 상태를 **명시적으로 계약**한다 → 자기일관적. `ISE → ILLEGAL_STATE` 레퍼런스로만 참조.
- **fan-platform 무수정.** 이미 `AbstractDomainExceptionHandler`에서 `ILLEGAL_STATE`를 쓴다.
- `STATE_TRANSITION_INVALID`로의 재분류 — 각 IAE/ISE 던지는 지점을 도메인 코드로 승격하는 작업은 별건(레지스트리 `:133`이 "prefer a domain-specific code"라 권고하나, 본 task는 **catch-all의 거짓말 제거**가 목표다). 후속 백로그 후보.
- `libs/java-observability/`의 JVM 크래시 덤프 커밋 위생 — 무관, 별건.

---

# Acceptance Criteria

- **AC-1** — 4개 서비스 모두 `IllegalStateException` → **`422 ILLEGAL_STATE`**. 각 서비스 `GlobalExceptionHandlerTest`가 이를 단언한다.
- **AC-2** — ledger `IllegalArgumentException` → **`400 VALIDATION_ERROR`**. `CURRENCY_MISMATCH`는 **전용 3개 핸들러**(도메인/`Money`/`Currency`)에서만 나온다. 테스트가 두 사실을 함께 단언한다.
- **AC-3 (드리프트 봉인)** — 각 핸들러에서 **동일 코드 문자열이 서로 다른 HTTP 상태로 발행되는 경로가 존재하지 않는다.** 코드를 합성하는 모든 핸들러가 `STATUS_BY_CODE`를 통해 상태를 해석한다(`respond()`). 테스트가 `VALIDATION_ERROR`를 발행하는 **모든** 핸들러(malformed body · type mismatch · missing header · IAE)가 **전부 400**임을 단언한다.
- **AC-4 (행동 불변 증명)** — `respond()` 통일은 **결함 핸들러 2종을 제외한 모든 핸들러에서 바이트 동일**해야 한다. 각 핸들러가 발행하는 코드가 이미 `STATUS_BY_CODE`에 그 상태로 등록돼 있음을 확인하고, 기존 테스트(account `GlobalExceptionHandlerTest`, 슬라이스/IT)가 **무수정으로 GREEN**임을 보인다.
- **AC-5 (계약 선행)** — 4개 계약서에 `ILLEGAL_STATE | 422` 등록 + `ledger-api.md`에 `VALIDATION_ERROR | 400` 등록. 코드 변경 전에 계약이 갱신된다(CLAUDE.md § Contracts).
- **AC-6** — `platform/error-handling.md:133`의 `ILLEGAL_STATE` emitter 목록이 실제 emitter 집합과 일치한다(scm + fan + erp×2 + finance×2).
- **AC-7 (mutation-check)** — 새 가드가 실제로 무는지 확인한다. 결함을 되돌려 주입(`ILLEGAL_STATE`→`VALIDATION_ERROR`, ledger IAE→`CURRENCY_MISMATCH`)했을 때 신규 테스트가 **실제로 FAIL**해야 한다. 통과하는 가드는 무는 가드가 아니다.
- **AC-8** — `./gradlew :projects:finance-platform:apps:ledger-service:test :projects:finance-platform:apps:account-service:test :projects:erp-platform:apps:masterdata-service:test :projects:erp-platform:apps:approval-service:test` GREEN.

---

# Related Specs

- `platform/error-handling.md` — 코드 레지스트리(`:94` `VALIDATION_ERROR | 400` · `:133` `ILLEGAL_STATE | 422`)
- `projects/finance-platform/specs/services/{ledger-service,account-service}/architecture.md`
- `projects/erp-platform/specs/services/{masterdata-service,approval-service}/architecture.md`

# Related Contracts

- `projects/finance-platform/specs/contracts/http/ledger-api.md` (§ Error codes)
- `projects/finance-platform/specs/contracts/http/account-api.md` (§ Error code → HTTP status)
- `projects/erp-platform/specs/contracts/http/masterdata-api.md` (`:416`)
- `projects/erp-platform/specs/contracts/http/approval-api.md` (`:442`)
- 레퍼런스(무수정): `projects/scm-platform/specs/contracts/http/procurement-api.md:384`

---

# Edge Cases

- **`handleGeneral`은 `respond()`를 거치지 않는다.** `INTERNAL_ERROR`는 `STATUS_BY_CODE`에 없으므로 `getOrDefault(…, 422)`가 **500을 422로 만들어 버린다.** 터미널 catch-all은 하드코딩 500을 유지한다. (이 함정이 `respond()` 통일에서 가장 밟기 쉬운 곳이다.)
- **`handleDomain`의 `getOrDefault(422)` 폴백 유지** — 미등록 도메인 코드가 422로 떨어지는 기존 동작은 건드리지 않는다(별건).
- **ledger `Money`의 음수/비정수 IAE가 이제 400이 된다.** ledger 계약에 `AMOUNT_INVALID`가 없으므로 `VALIDATION_ERROR`가 유일한 정합 코드다. account와 비대칭이지만 **의도된 비대칭**이다(위 OUT 참조).
- **`FxTolerance`의 IAE는 심층방어 백스톱**이다 — use case가 상위에서 검증해 `VALIDATION_ERROR`를 던진다(`FxTolerance.java:61-62` javadoc). 이 경로의 관측 가능한 동작은 변하지 않는다.
- ISE 메시지가 내부 상태를 노출할 수 있다 — 기존 `log.warn` 유지, 메시지 노출 정책은 현행 유지(별건).

# Failure Scenarios

- **`respond()` 통일이 조용히 상태를 바꾼다** — 어떤 핸들러가 `STATUS_BY_CODE`에 **미등록**인 코드를 발행하고 있었다면 `getOrDefault` 폴백(422)이 상태를 갈아치운다. → 통일 전에 각 핸들러의 발행 코드가 표에 그 상태로 등록돼 있는지 **1:1 대조**하고, 대조 결과를 AC-4로 고정한다.
- **결함을 고정 중인 기존 테스트와 충돌** — 사전 조사 결과 그런 테스트는 **없다**(슬라이스 테스트의 `CURRENCY_MISMATCH` 단언은 진짜 `CurrencyMismatchException` 경로라 catch-all과 무관). 그럼에도 예상 밖 RED가 나오면 **"테스트를 고쳐 통과시키기" 전에** 그 테스트가 결함을 고정 중인지 정상 동작을 보호 중인지 먼저 판별한다.
- **CI가 증명하지 못하는 수정으로 오인** — 본 건은 순수 단위 테스트로 완전 증명 가능하다(Docker 불필요). Testcontainers IT 부재를 이유로 검증을 건너뛰지 않는다.
