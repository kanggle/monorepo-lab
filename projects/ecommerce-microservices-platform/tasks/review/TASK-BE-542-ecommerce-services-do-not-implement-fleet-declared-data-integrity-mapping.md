# TASK-BE-542 — 레지스트리가 함대 전체에 선언한 `DATA_INTEGRITY_VIOLATION` 409 를 ecommerce 9개 서비스가 구현하지 않는다

**Status:** review

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

## 구현 결과 (2026-07-20)

### AC-0 — 재측정이 이 티켓의 프레이밍을 반증했다

티켓은 *"ecommerce 만 뒤처진 straggler"* 로 썼다. **틀렸다 — 표준이 아예 없다.**

| 프로젝트 / 서비스 | 모양 | emit 코드 |
|---|---|---|
| wms `outbound` · scm `procurement` · fan-platform 4개 | 무조건 409 | `CONFLICT` |
| finance `account` | 무조건 409 | `CONCURRENT_MODIFICATION` |
| ecommerce `user` | 무조건 409 | `DATA_INTEGRITY_VIOLATION` |

**홀더 5곳이 코드 3종**이고, 레지스트리(`error-handling.md:137`)가 정경으로 적은 코드는 **5곳 중 1곳만** 낸다. **선별 방식을 쓰는 곳은 하나도 없었다.**

**티켓의 "미보유 9곳" 도 틀렸다 — 실제 8곳.** `search-service` 는 컨트롤러 3개지만 **JPA 리포지터리 0**(Elasticsearch, `db/` 없음)이라 필요 없음. AC-6 분류 근거: `gateway-service`(컨트롤러·JPA 0, 순수 라우팅) · `batch-worker`(컨트롤러 0 = HTTP 표면 없음) · `search-service`(관계형 쓰기 경로 없음). `auth-service` 는 RETIRED 라 집계 제외.

**🔴 탐지식이 두 번 깨졌다.** ① `@ExceptionHandler(DataIntegrityViolationException.class)` 를 그냥 grep 하니 `JpaWebhookDeliveryStore` 의 **Javadoc 언급**이 hit — `TASK-BE-541` 에서 내가 쓴 주석이 내 탐지식을 오염시켰다. `^\s*@ExceptionHandler` 앵커로 해소. ② 서비스별 핸들러 수 스크립트가 `bc` 부재로 **전 서비스 0** 반환 — 믿었으면 "배선이 하나도 안 됐다" 로 오독했다. `wc -l` 로 교체 + `search`/`gateway` known-negative 대조군으로 재확인.

### AC-1 — 판정: 선별. 첫 판별식 안은 물 수 없는 가드였다

**무조건 409 를 거부한다.** DIVE 는 FK·NOT NULL·CHECK 위반도 싣고 그것들은 **서버 결함**이다. 409 로 매핑하면 서버 버그를 클라 잘못으로 보고하고 **500 이 사라져 모니터링에서도 사라진다.**

**🔴 `DuplicateKeyException` 판별 안은 발화 불가였다.** `spring-orm 6.2.1` 바이트코드 확인:

- `HibernateJpaDialect` 는 Hibernate `ConstraintViolationException` → **평범한 `DataIntegrityViolationException`**(오프셋 267 → 278)
- `DuplicateKeyException` 은 **`NonUniqueObjectException`**(세션 레벨)에서만 생성(380 → 386) — DB 유니크 위반과 무관
- ⇒ **예외 타입은 판별식이 될 수 없다**

채택: **cause 체인의 SQLState `23505`**. 웹 계층에 Hibernate 미유입, 메시지 문자열 매칭(은퇴한 `auth-service` 방식 — 벤더 버전에 조용히 깨짐) 회피. 대안 Hibernate 6.6.4 `getKind()==UNIQUE` 는 결합도 때문에 불채택.

### AC-2 — 배선 8곳

unique → **409 `DATA_INTEGRITY_VIOLATION`** / 그 외 → **500 `INTERNAL_ERROR` + `log.error`**(각 서비스 기존 `Exception.class` 핸들러와 **바이트 동일**이라 비-unique 위반 거동은 오늘과 구분 불가). 도메인 예외가 더 구체적이라 여전히 우선(`DuplicateOrderPlacementException` → `DUPLICATE_ORDER_REQUEST` 확인). 핸들러 수 1/1 × 9, 중복 0 → 기동 ambiguity 없음.

`product-service` 만 `ResponseEntity` 사용 — 상태가 런타임에 갈려(409/500) 컴파일 상수인 `@ResponseStatus` 로 표현 불가. 같은 파일 `handleMethodNotSupported` 가 같은 이유로 이미 쓰는 탈출구.

### AC-5 — 배선 도달성: 두 층 + 핵심 발견

**Layer 1 (8곳)** — `MockMvc` + `setControllerAdvice` 로 Spring 실제 예외 해소 경로를 태운다. 기존 `GlobalExceptionHandler*Test` 는 **핸들러를 직접 호출**(AC-5 가 거부하는 기법)이라 확장 대신 신설.

**가드가 무는지 실측** — notification 애노테이션 임시 제거 시 2건 중 1건 실패(복원 확인). **어느 것이 실패했는지가 중요**: `23505→409` 만 배선을 증명하고, **`23503→500` 은 핸들러가 없어도 통과**한다(DIVE 가 catch-all 로 떨어져 바이트 동일한 500). 후자는 구조상 부재 탐지가 불가능하며 그게 옳다 — 그 테스트의 일은 **과잉 매핑 안 했음**을 고정하는 non-regression 이지 배선 증거가 아니다.

**Layer 2 (실 DB 1건)** — `review-service` 에서 **결정적 도달 경로**를 찾았다: 선체크는 테넌트 스코프인데 `uq_reviews_user_product_active` 에 `tenant_id` 가 없어, 두 번째 테넌트의 같은 `(user, product)` 가 선체크를 통과한 뒤 전역 인덱스를 위반. 로컬 Postgres 실행·통과(`tests=2 F=0`). 409 만이 아니라 **`code == DATA_INTEGRITY_VIOLATION`** 을 단언 — 안 그러면 선체크의 409 `REVIEW_ALREADY_EXISTS` 와 구분되지 않아 어느 경로가 돌았는지 증명하지 못한다.

**🔴 나머지 7개 서비스에서는 결정적 도달이 불가하다.** 모든 유니크 위반 경로가 이미 선점됨 — **지역 catch → 도메인 예외**(order `:66` · promotion `:111` · payment `:254` · product ×3 · settlement `:63`) 또는 **선체크 후 삽입**(shipping `existsByOrderId` · notification 템플릿/푸시).

⇒ **정직한 범위는 "결정적 경로 1개 + 나머지는 경합 시에만"** 이다. *"8개 서비스가 이제 409 를 낸다"* 가 아니다. 실질 수확은 `JpaWebhookDeliveryStore` 의 동시 중복 500 이 **부하에서 409 가 되는 것** — `TASK-BE-541` 이 "이 계층에서 못 고친다" 고 명시한 바로 그 건이다.

### 🔴 이 배선이 `TASK-BE-540` 의 결함을 조용하게 만든다 — 반드시 읽을 것

Layer 2 가 찾은 결정적 경로는 **`TASK-BE-540` 사례 B 그 자체**다. 그 티켓은 도달성을 **미측정**으로 남겼는데 **이제 측정됐다 — 도달 가능하다.** 사례 B 는 정합성 결함이 아니라 **실재하는 결정적 결함**이고 우선순위가 올라간다.

그리고 이 배선이 증상을 **500 → 409 `DATA_INTEGRITY_VIOLATION`** 으로 바꾼다. **올바른 응답은 201 이다**(다른 테넌트의 정당한 리뷰). 기능적으로 더 나빠지진 않지만 **더 조용해진다** — 500 은 알림에 남고 409 는 안 남는다. `TASK-BE-540` § F1 이 경고한 시나리오를 알면서 밟은 것이다. **백스톱이 BE-540 을 가려 준다고 읽지 말 것.**

또한 **BE-540 이 review 인덱스를 `tenant_id` 포함으로 재정의하면 이 IT 는 바뀌어야 하고**(두 번째 삽입이 정당하게 201), 그러면 함대에 **결정적 백스톱 경로가 0 개**가 된다. 테스트 Javadoc 에도 기록했다.

### 테스트 계수 (로컬, XML 집계)

notification **123** · order **383** · promotion **90** · payment **210** · product **366** · review **108** · shipping **187** · settlement **133** — 합계 **1,600**, F/E/S 모두 0. Layer-2 `integrationTest` 2건. **8/8 신규 Layer-1 클래스가 XML 에 `tests=2` 로 존재함을 개별 확인** — 레인 초록만으로는 내 파일이 실행됐다는 증거가 못 된다.

### AC-3 / AC-4 — 경계 준수

- **AC-3**: 판정이 "libs 공통 기반" 이 아니라 정지 조건 미발동. 다만 **판별식이 8벌 중복**이고 승격 후보다 → `TASK-MONO-446` AC-5.
- **AC-4**: 선별은 `error-handling.md:137` 의 *"Generic DB constraint violation"* 과 **어긋난다**(FK·NOT NULL 도 문자 그대로는 409). 공유 파일을 여기서 고치지 않고 **`TASK-MONO-446`** 을 루트에 세웠다(별도 spec PR — `tasks/INDEX.md` § PR Separation Rule).

### 정직한 한계

- **Layer 2 는 단일 로컬 표본**이고 이 호스트 Testcontainers 는 FLAKY 다. **CI Linux 가 권위.**
- ecommerce 8곳은 이제 다른 4개 프로젝트와 **의도적으로 다른 모양**이다. 수렴 전까지 함대는 **가장 올바른 상태가 아니라 가장 일관되지 않은 상태**이며, `TASK-MONO-446` 이 그 부채의 소유자다.

---

## Notes

- **분량**: medium. 배선은 기계적이나 **판정과 경계 준수가 실질.**
- **dependency**: `선행` = 없음. **`TASK-BE-541` 과의 순서 주의** — 이 task 를 먼저 하면 541 의 "수정 전 500 확인" 이 불가능해진다. 541 을 먼저 하거나, 541 의 RED 기준을 "잘못된 에러 코드" 로 바꿔 잡을 것.
- `형제` = `TASK-BE-539` · `TASK-BE-540` · `TASK-BE-541` (모두 `TASK-BE-538` Edge 3 산물) · `TASK-MONO-444`(같은 "선언↔진실" 클래스).
- **이 task 가 방어하는 실패 모드**: **한 서비스의 관행을 함대 규칙으로 승격하는 것은 무손실이 아니다.** `MONO-052` 가 `user-service` 의 catch-all 을 Platform-Common 으로 올렸지만 형제 배선은 따라오지 않았고, 그 결과 **레지스트리는 함대 대부분이 하지 않는 일을 선언해 왔다**(언제부터인지는 `MONO-052` 머지 시점으로 확인 가능 — AC-0 에서 함께 적을 것). 승격 시점에 "이제 누가 이걸 지키는가" 를 세지 않으면 규칙은 문서에만 산다. [[project_enforcement_straggler_sibling_parity]] [[feedback_repo_knows_what_it_does_not_say]] [[feedback_workaround_becomes_the_contract]]
