# TASK-BE-542 — 레지스트리가 함대 전체에 선언한 `DATA_INTEGRITY_VIOLATION` 409 를 ecommerce 9개 서비스가 구현하지 않는다

**Status:** ready

**Type:** TASK-BE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (배선 자체는 기계적이나 **어떤 모양을 표준으로 삼을지**가 판정이고, 무조건 409 는 틀렸다)

> `TASK-BE-538` **Edge 3** 수행 중 형제 대조로 발견. 개별 엔드포인트 결함이 아니라 **프로젝트 하나가 통째로 straggler** 인 경우다.

---

## Goal

`platform/error-handling.md:137` 은 함대 전체 규칙을 선언한다:

> `| DATA_INTEGRITY_VIOLATION | 409 | Generic DB constraint violation not covered by a domain-specific code. **Catch-all surfaced by Spring `DataIntegrityViolationException`** when no `*Exception.java` mapping applies. Prefer a domain code when a known constraint is hit |`

형제 대조 실측(2026-07-20):

| 프로젝트 | `@ExceptionHandler(DataIntegrityViolationException.class)` |
|---|---|
| wms-platform | ✅ `outbound-service` |
| scm-platform | ✅ `procurement-service` |
| finance-platform | ✅ `account-service` |
| fan-platform | ✅ 4개 서비스 (`AbstractDomainExceptionHandler` 공통 기반) |
| ecommerce | ⚠️ `user-service` **하나뿐** (아래 정정 참조) |

> **🔴 2026-07-20 정정 (`TASK-BE-541` 착수 중 발견)** — 이 티켓의 최초 본문은 *"보유 = `auth-service`·`user-service` 둘뿐"* 이라고 적었다. **틀렸다.** `PROJECT.md:53` 이 `auth-service` 를 **RETIRED** 로 선언한다(`settings.gradle` include 제외, IAM OIDC 로 대체, 소스는 이력 보존 목적으로만 잔존). 빌드·배포되지 않는 서비스를 함대 준수 집계에 넣을 수 없다.
>
> ⇒ **실질 보유는 `user-service` 하나**이며, 결함은 최초 서술보다 **크다.** 다만 `auth-service` 의 핸들러(`GlobalExceptionHandler.java:71-82`)는 **설계 참조로는 여전히 유효**하다 — 아래 § 무조건 409 는 틀렸다 가 인용하는 "제약 이름 선별" 모양이 그것이다. **참조로는 읽되 준수 집계에서는 뺄 것.** AC-0 재측정은 은퇴 서비스를 모집단에서 제외하는 것부터 시작한다.

ecommerce 의 **나머지 9개** — `product`·`order`·`payment`·`promotion`·`settlement`·`shipping`·`review`·`notification`·`search` — 는 없다. 각자 자기 `GlobalExceptionHandler` 에 `@ExceptionHandler(Exception.class)` 를 두고 있어(예: `product:189`, `order:171`, `settlement:160`, `review:122`, `notification:129`, `shipping:145`) **잡히지 않은 제약 위반은 전부 500 `INTERNAL_ERROR`** 로 나간다.

이 서비스들의 `GlobalExceptionHandler` 에 있는 `ConstraintViolationException` 핸들러는 **전부 `jakarta.validation`** — 빈 검증이지 DB 제약이 아니다. 이름이 비슷해 이미 처리된 것처럼 보이는 것이 이 결함이 오래 보이지 않은 이유 중 하나로 보인다.

### 왜 이게 "선언 ↔ 진실" 결함인가

`tasks/done/TASK-MONO-052-error-handling-catalog-wave-3.md:57` 이 출처를 기록해 두었다 — 이 코드는 **`user-service` 의 GlobalExceptionHandler catch-all 에서 Platform-Common 으로 승격**된 것이다. **승격은 됐는데 형제 배선이 따라오지 않았다.** 한 서비스의 관행이 함대 규칙이 됐지만 그 함대의 대부분은 모른다.

`TASK-MONO-444`(문서가 코드에 없는 것을 약속) 와 같은 클래스의 **더 큰 사례**다.

---

## ⚠️ 무조건 409 는 틀렸다 — 이것이 이 task 의 판정 지점

기존 두 구현이 **서로 다른 모양**이다:

**`user-service` (`GlobalExceptionHandler.java:127-132`)** — 원인을 보지 않고 전부 409:
```java
return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ErrorResponse.of("DATA_INTEGRITY_VIOLATION", "Data integrity violation"));
```

**`auth-service` (`GlobalExceptionHandler.java:71-82`)** — 제약 이름을 보고, 아는 것만 409, 나머지는 500:
```java
if (message.contains(UNIQUE_EMAIL_CONSTRAINT)) { ... 409 EMAIL_ALREADY_EXISTS ... }
log.error("Unexpected data integrity violation", ex);
return ... INTERNAL_SERVER_ERROR ... "INTERNAL_ERROR" ...
```

**`auth-service` 쪽이 더 정직하다.** `DataIntegrityViolationException` 은 유니크 위반만이 아니라 **NOT NULL 위반·FK 위반·CHECK 위반**도 포함하고, 그것들은 클라이언트의 충돌이 아니라 **서버 결함**이다. 무조건 409 로 매핑하면:

- 서버 버그를 클라이언트 잘못으로 보고한다.
- 클라이언트가 "재시도해도 소용없는" 요청을 충돌로 오해해 재시도한다.
- **500 이 사라지므로 알림·모니터링에서도 사라진다** — 결함이 조용해진다.

즉 이 task 는 "9곳에 핸들러를 붙인다" 가 아니라 **"어떤 모양이 표준인지 정하고, 그 결정을 레지스트리 문장과 함께 정렬한다"** 이다.

---

## Scope

### In Scope

1. **AC-0 형제 대조 재측정.**
2. 표준 모양 판정(무조건 409 / 제약 이름 기반 선별 / 제3안) + 근거.
3. 판정된 모양을 ecommerce straggler 서비스에 배선.
4. 판정이 레지스트리 문장과 어긋나면 **`platform/error-handling.md:137` 문장 갱신을 후속 티켓으로 제기**(이 task 에서 고치지 않는다 — 아래 참조).

### Out of Scope

- **공유 경로 변경 0.** `libs/java-web-servlet/CommonGlobalExceptionHandler` 수정이나 `platform/error-handling.md` 수정은 **monorepo-level 작업**이라 루트 `tasks/ready/` 에 별 task 가 필요하다(`CLAUDE.md` § Task Rules). **AC-3 참조 — 판정이 "libs 에 넣는다" 로 나오면 이 task 는 거기서 멈추고 루트 task 를 세운다.**
- 개별 엔드포인트의 중복 방어 정확성은 `TASK-BE-541`. **이 task 는 백스톱이지 수정이 아니다.**
- 선체크 범위 불일치는 `TASK-BE-540`.

---

## Acceptance Criteria

- **AC-0 (gate — 형제 대조 재측정)** — 위 표를 **직접 다시 센다.** 프로젝트별 `@ExceptionHandler(DataIntegrityViolationException.class)` 보유 서비스와 미보유 서비스를 전수 열거한다. **탐지식 자기검증**: `user-service`·`auth-service` 가 hit 로 나와야 한다(0 이면 탐지식이 깨진 것). ecommerce 미보유 9건이라는 숫자도 가설이다 — `search-service` 처럼 쓰기 경로가 거의 없는 서비스가 섞여 있으니 **"핸들러 없음" 과 "필요 없음" 을 구분**해 센다.
- **AC-1 (판정)** — 표준 모양을 정하고 근거를 적는다. **무조건 409 를 고른다면 NOT NULL/FK 위반이 409 로 나가는 것이 왜 허용 가능한지 명시적으로 답해야 한다.** 답하지 못하면 선별 방식이 답이다.
- **AC-2 (배선)** — AC-0 에서 "필요" 로 분류된 서비스 전부에 판정된 모양이 배선된다. **도메인별 구체 예외 매핑이 있으면 그것이 우선**이고(더 구체적인 `@ExceptionHandler` 가 이김) 이 핸들러는 백스톱이다.
- **AC-3 (경계 준수)** — 판정이 *"`libs` 공통 기반에 넣고 ecommerce 가 상속한다"* 로 나오면 **이 task 를 여기서 멈추고** 루트 `tasks/ready/` 에 monorepo-level task 를 세운 뒤 이 티켓을 그 후속으로 재배치한다. **선행 조사 필독**: `projects/finance-platform/tasks/done/TASK-FIN-BE-058-globalexceptionhandler-common-base-dedup-investigation.md` 가 이미 `extends CommonGlobalExceptionHandler` 전환이 **drop-in 이 아니라고** 결론냈다(`:60`). 그 결론을 뒤집으려면 왜 틀렸는지 적어야 한다.
- **AC-4 (레지스트리 정렬)** — 판정이 `error-handling.md:137` 문장과 어긋나면(예: 선별 방식을 고르면 "Generic DB constraint violation → 409" 라는 문장이 과도해진다) **그 사실을 기록하고 문장 수정을 루트 후속 티켓으로 제기**한다. 이 task 에서 공유 파일을 고치지 않는다.
- **AC-5 (테스트)** — 배선된 각 서비스에 대해 실제 제약 위반이 판정된 상태 코드로 나오는 IT. **모킹으로 핸들러만 호출하는 테스트는 배선을 증명하지 못한다** — 예외가 실제로 그 핸들러까지 도달하는지가 요점이다.
- **AC-6** — `search-service` 처럼 DB 쓰기 경로가 없어 "필요 없음" 으로 분류한 서비스는 **그 근거를 한 줄로 적는다.** 분류도 주장이다.

---

## Related Specs

- `platform/error-handling.md:137` — 선언 (읽기 전용, 이 task 에서 수정 금지)
- `platform/error-handling.md:58` — 기본 매핑 표(Conflict → 409)와 :66 의 도메인 오버라이드 규칙
- `tasks/done/TASK-MONO-052-error-handling-catalog-wave-3.md:57` — 코드의 출처(user-service → Platform-Common 승격)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-058-...md` — 공통 기반 클래스 전환이 drop-in 이 아니라는 선행 결론 (AC-3)
- `tasks/ready/TASK-BE-538-adr-002-d3-wording-adjudication.md` § Edge 3 — 출처

## Related Contracts

- `specs/contracts/http/**` — 배선으로 **기존 500 이 409 로 바뀌는 엔드포인트**가 생긴다. 계약 문서에 해당 엔드포인트의 에러 응답이 명시돼 있으면 **갱신이 선행**이다. AC-2 대상 서비스별로 대조할 것.

---

## Edge Cases

1. **🔴 500 을 없애면 결함이 조용해진다** — 이 배선의 가장 큰 위험. 지금은 제약 위반이 500 으로 터져 로그·알림에 남는다. 409 로 바꾸면 정상 응답처럼 보인다. **판정이 무엇이든 서버 결함성 위반(FK/NOT NULL)은 계속 시끄러워야 한다.**
2. **제약 이름 문자열 매칭은 깨지기 쉽다** — `auth-service` 방식은 `ex.getMessage().contains(...)` 다. DB 벤더·드라이버 버전에 따라 메시지가 바뀌면 조용히 500 으로 되돌아간다. 선별 방식을 고른다면 **이 취약성을 어떻게 다룰지**(테스트로 고정 / 벤더 중립 추출)를 함께 답해야 한다.
3. **더 구체적인 핸들러와의 우선순위** — 같은 예외 타입에 대해 한 advice 안에 두 메서드가 있으면 Spring 이 모호성으로 기동 실패한다. 기존에 DIVE 핸들러가 있는 `auth`·`user` 를 건드릴 때 특히 주의.
4. **`search-service`** — 쓰기 경로가 사실상 없다면 배선이 무의미하다. AC-6 으로 근거를 남기되 **"없어 보인다" 로 넘기지 말 것.**

---

## Failure Scenarios

- **F1 — 9곳에 `user-service` 핸들러를 복붙하고 닫는다.** 판정을 건너뛴 것이고, NOT NULL/FK 위반까지 409 로 만들어 **결함을 조용하게 만든다.** Edge 1.
- **F2 — 이 task 로 `TASK-BE-541` 을 대체한다.** 500 은 사라지지만 모든 중복이 일반 코드로 뭉개져 `DUPLICATE_ORDER_REQUEST` 같은 도메인 코드가 영영 안 나온다. **백스톱은 수정이 아니다.**
- **F3 — 공유 파일을 여기서 고친다.** `libs/` 나 `platform/` 을 이 프로젝트 task 에서 수정하면 monorepo 경계 위반(HARDSTOP-03 인접). AC-3/AC-4 가 그래서 있다.
- **F4 — AC-0 을 건너뛰고 본문의 9라는 숫자를 인용한다.** 모집단 물려받기. 이 저장소가 반복해 대가를 치른 실패다.

---

## Test Requirements

- 서비스별 IT: 실제 유니크 제약을 때려 판정된 상태 코드 확인 (Testcontainers, **CI Linux 권위**).
- 선별 방식을 고른 경우: **알려지지 않은 제약** 위반이 여전히 500 으로 나가는지도 단언(Edge 1 방어).
- 기존 도메인 코드가 이 백스톱에 가려지지 않는지 회귀 확인.

---

## Definition of Done

- [ ] AC-0 형제 대조 전수 재측정 (탐지식 자기검증 + "필요 없음" 분류 근거)
- [ ] AC-1 표준 모양 판정 + 근거 (무조건 409 면 FK/NOT NULL 질문에 답할 것)
- [ ] AC-3 경계 판단 — libs 로 가면 루트 task 세우고 여기서 정지
- [ ] AC-2 배선 완료
- [ ] AC-4 레지스트리 어긋남 기록 + 루트 후속 티켓 제기 (해당 시)
- [ ] AC-5 배선 IT (모킹 아닌 실제 도달 확인)
- [ ] 계약 문서 대조 및 필요 시 선행 갱신

---

## Notes

- **분량**: medium. 배선은 기계적이나 **판정과 경계 준수가 실질.**
- **dependency**: `선행` = 없음. **`TASK-BE-541` 과의 순서 주의** — 이 task 를 먼저 하면 541 의 "수정 전 500 확인" 이 불가능해진다. 541 을 먼저 하거나, 541 의 RED 기준을 "잘못된 에러 코드" 로 바꿔 잡을 것.
- `형제` = `TASK-BE-539` · `TASK-BE-540` · `TASK-BE-541` (모두 `TASK-BE-538` Edge 3 산물) · `TASK-MONO-444`(같은 "선언↔진실" 클래스).
- **이 task 가 방어하는 실패 모드**: **한 서비스의 관행을 함대 규칙으로 승격하는 것은 무손실이 아니다.** `MONO-052` 가 `user-service` 의 catch-all 을 Platform-Common 으로 올렸지만 형제 배선은 따라오지 않았고, 그 결과 **레지스트리는 함대 대부분이 하지 않는 일을 선언해 왔다**(언제부터인지는 `MONO-052` 머지 시점으로 확인 가능 — AC-0 에서 함께 적을 것). 승격 시점에 "이제 누가 이걸 지키는가" 를 세지 않으면 규칙은 문서에만 산다. [[project_enforcement_straggler_sibling_parity]] [[feedback_repo_knows_what_it_does_not_say]] [[feedback_workaround_becomes_the_contract]]
