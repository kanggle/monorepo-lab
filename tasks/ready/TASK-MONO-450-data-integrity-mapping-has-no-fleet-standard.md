# TASK-MONO-450 — `DataIntegrityViolationException` 매핑에 함대 표준이 없다: 홀더 5곳이 코드 3종, 레지스트리는 1곳만 쓰는 코드를 정경으로 적는다

**Status:** ready

**Type:** TASK-MONO (monorepo-level — `platform/error-handling.md` + 잠재적 `libs/java-web-servlet` 변경)
**Analysis model:** Opus 4.8 / **Recommended impl model:** Opus 4.8 (판정이 실질. 5개 프로젝트의 사용자 가시 계약을 바꾸는 결정이다)

> `TASK-BE-542` AC-0/AC-4 가 제기. BE-542 는 프로젝트 task 라 공유 파일(`platform/`·`libs/`)을 고칠 수 없어(`CLAUDE.md` § Task Rules) 여기로 올린다.

---

## Goal

`platform/error-handling.md:137` 은 함대 규칙을 선언한다:

> `| DATA_INTEGRITY_VIOLATION | 409 | Generic DB constraint violation not covered by a domain-specific code. Catch-all surfaced by Spring `DataIntegrityViolationException` when no `*Exception.java` mapping applies. Prefer a domain code when a known constraint is hit |`

**2026-07-20 실측 결과, 이 문장을 그대로 구현하는 서비스는 함대에 하나뿐이다.**

| 프로젝트 / 서비스 | 모양 | 실제 emit 코드 |
|---|---|---|
| wms `outbound-service` | 무조건 409 | `CONFLICT` |
| scm `procurement-service` | 무조건 409 | `CONFLICT` |
| fan-platform 4개 서비스 | 무조건 409 | `CONFLICT` |
| finance `account-service` | 무조건 409 | `CONCURRENT_MODIFICATION` |
| ecommerce `user-service` | 무조건 409 | `DATA_INTEGRITY_VIOLATION` |
| ecommerce 나머지 8개 | **선별**(unique→409 / 그 외→500) | `DATA_INTEGRITY_VIOLATION` (TASK-BE-542) |

**세 가지가 동시에 어긋나 있다:**

1. **에러 코드가 3종** — `CONFLICT` / `CONCURRENT_MODIFICATION` / `DATA_INTEGRITY_VIOLATION`. 레지스트리가 정경으로 적은 세 번째는 원래 **`user-service` 한 곳의 관행**이었고(`TASK-MONO-052:57` 이 승격 사실을 기록), 승격 후에도 나머지가 따라오지 않았다.
2. **모양이 2종** — `TASK-BE-542` 이후 ecommerce 8개만 선별 방식이다. **의도적 이탈이며 이 티켓이 수렴시켜야 한다.**
3. **레지스트리 문장이 선별 방식을 금지하는 것처럼 읽힌다** — *"Generic DB constraint violation"* 은 FK·NOT NULL·CHECK 위반도 포함하므로, 문자 그대로는 그것들도 409 여야 한다.

---

## 🔴 왜 무조건 409 가 틀렸다고 보는가 (BE-542 의 논거)

`DataIntegrityViolationException` 은 유니크 위반만이 아니라 **FK 위반·NOT NULL 위반·CHECK 위반**을 함께 싣는다. 그것들은 클라이언트의 충돌이 아니라 **서버 결함**이다. 무조건 409 로 매핑하면:

- 서버 버그를 클라이언트 잘못으로 보고한다.
- 클라이언트가 재시도해도 소용없는 요청을 충돌로 오해해 재시도한다.
- **500 이 사라지므로 알림·모니터링에서도 사라진다** — 결함이 조용해진다. 이것이 가장 큰 해악이다.

`TASK-BE-542` 는 그래서 ecommerce 8곳에 **unique 위반만 409, 나머지는 500 유지**를 배선했다.

---

## 🔵 BE-542 가 남긴 경험적 사실 (재측정 불필요, 다만 검증은 환영)

이 결정에 필요한 라이브러리 사실은 이미 바이트코드로 확인했다:

- **`DuplicateKeyException` 으로는 판별할 수 없다.** `spring-orm 6.2.1` 의 `HibernateJpaDialect` 는 Hibernate `ConstraintViolationException` 을 **평범한 `DataIntegrityViolationException`** 으로 매핑한다(오프셋 267→278). `DuplicateKeyException` 은 **`org.hibernate.NonUniqueObjectException`** (세션 레벨 오류)에서만 생성된다(오프셋 380→386) — DB 유니크 위반과 무관하다. **예외 타입은 판별식이 될 수 없다.**
- **쓸 수 있는 판별식은 둘.** ① JDBC `SQLException.getSQLState() == "23505"`(unique_violation, Postgres·H2 공통) — BE-542 가 채택, 프레젠테이션 계층에 Hibernate 를 들이지 않음. ② Hibernate 6.6.4 `ConstraintViolationException.getKind() == ConstraintKind.UNIQUE` — 더 서술적이나 웹 계층이 Hibernate 에 결합된다.
- **메시지 문자열 매칭은 피할 것.** `auth-service`(은퇴)가 `message.contains(UNIQUE_EMAIL_CONSTRAINT)` 를 쓴다. 드라이버·벤더 버전에 따라 조용히 깨진다.

---

## Scope

### In Scope

1. **AC-0 재측정** — 위 표를 다시 센다.
2. **표준 판정**: 모양(무조건 409 / 선별) + 코드(단일화할지, 별칭으로 남길지).
3. `platform/error-handling.md:137` 문장을 판정에 맞게 개정.
4. 판정된 모양으로 **비준수 프로젝트 정렬** (원자적 크로스프로젝트 PR).
5. **중복 제거 판단**: 선별 판별식이 채택되면 지금 ecommerce 8곳에 **동일 로직이 8벌** 존재한다. `libs/java-web-servlet` 로 올릴지 판정.

### Out of Scope

- 개별 엔드포인트의 도메인별 예외 매핑 — 그건 각 프로젝트 몫이고 이 백스톱보다 우선순위가 높다(더 구체적인 `@ExceptionHandler` 가 이긴다).
- `error-handling.md` 의 다른 코드 정합성.

---

## Acceptance Criteria

- **AC-0 (gate)** — 위 표 전수 재측정. **탐지식 주의**: `@ExceptionHandler(DataIntegrityViolationException.class)` 를 그냥 grep 하면 **Javadoc 언급이 hit 된다**(BE-542 에서 실제로 오염됐다 — `shipping-service/JpaWebhookDeliveryStore.java` 의 주석). `^\s*@ExceptionHandler` 로 앵커할 것. 자기검증: `user-service` 가 hit 여야 한다.
- **AC-1 (모양 판정)** — 무조건 409 냐 선별이냐. **무조건 409 를 고른다면 FK·NOT NULL 위반이 409 로 나가 모니터링에서 사라지는 것을 왜 감수하는지 답해야 한다.** 답하지 못하면 선별이 답이다. 선별을 고르면 **BE-542 의 8곳이 이미 그 모양**이므로 나머지 5곳을 맞춘다.
- **AC-2 (코드 판정)** — 3종을 하나로 모을지, 일부를 `error-handling.md` 의 **등록된 의도적 별칭**(`:66`, `:113` 선례)으로 남길지 정한다. **"그냥 다 `CONFLICT` 로" 는 사용자 가시 계약 변경**이므로 각 프로젝트의 `specs/contracts/` 대조가 선행이다.
- **AC-3 (레지스트리 개정)** — `error-handling.md:137` 을 판정에 맞게 고친다. 선별을 고르면 *"Generic DB constraint violation"* 이라는 표현이 과도해지므로 **unique 위반으로 한정**하고, 나머지 무결성 위반은 500 이 정상임을 명시한다.
- **AC-4 (정렬)** — 비준수 프로젝트를 **하나의 원자적 PR** 로 맞춘다(`CLAUDE.md` § Cross-Project Changes). 단계적 PR 은 main 을 일시적으로 깨진 상태로 둔다.
- **AC-5 (중복 판정)** — 선별 채택 시 판별식 8벌을 `libs` 로 올릴지 정한다. **선행 필독**: `projects/finance-platform/tasks/done/TASK-FIN-BE-058-...md:60` 이 `extends CommonGlobalExceptionHandler` 전환은 **drop-in 이 아니라고** 이미 결론냈다. 그 결론을 뒤집으려면 왜 틀렸는지 적어야 한다. **핸들러 전체를 올리지 않고 판별식(순수 함수)만 올리는 제3안**도 후보다.
- **AC-6 (계약 대조)** — 코드나 상태가 바뀌는 엔드포인트가 있으면 각 프로젝트 `specs/contracts/` 를 **먼저** 갱신한다.

---

## Related Specs

- `platform/error-handling.md:137` — 개정 대상 · `:58` 기본 매핑표 · `:66` 도메인 오버라이드 규칙 · `:113` 등록된 별칭 선례
- `tasks/done/TASK-MONO-052-error-handling-catalog-wave-3.md:57` — 코드의 출처(`user-service` → Platform-Common 승격)
- `projects/finance-platform/tasks/done/TASK-FIN-BE-058-globalexceptionhandler-common-base-dedup-investigation.md` — AC-5 선행 결론
- `projects/ecommerce-microservices-platform/tasks/review/TASK-BE-542-...md` — 이 티켓의 출처, 선별 방식의 참조 구현

## Related Contracts

- 각 프로젝트 `specs/contracts/http/**` — AC-2/AC-6. **코드 단일화는 사용자 가시 변경**이다.

---

## Edge Cases

1. **🔴 "그냥 다 `CONFLICT` 로 통일" 이 제일 쉬워 보인다** — 하지만 그건 `DATA_INTEGRITY_VIOLATION` 을 계약에 적어 둔 표면(`wishlist-api.md:49`, `wishlist.md:37`, `wishlist-management.md:55`)을 깨뜨린다. **계약을 먼저 세고 고를 것.**
2. **선별 방식은 "아무것도 거부하지 않는 문장" 의 반대 함정을 판다** — unique 만 409 로 좁히면, 도메인 코드도 없고 unique 도 아닌 위반은 전부 500 이다. 그게 의도지만, **500 이 실제로 알림으로 이어지는지** 확인하지 않으면 "시끄럽게 남긴다" 는 주장이 공허하다.
3. **`auth-service` 를 집계에 넣지 말 것** — `projects/ecommerce-microservices-platform/PROJECT.md:53` 이 RETIRED 로 선언하고 `settings.gradle` 이 제외한다. BE-542 의 최초 인구조사가 이걸 틀렸다.
4. **`search-service`·`gateway-service`·`batch-worker` 는 "필요 없음"** — 각각 JPA 리포지터리 0 / 컨트롤러·JPA 0 / 컨트롤러 0. "핸들러 없음" 과 "필요 없음" 은 다르다.

---

## Failure Scenarios

- **F1 — 레지스트리 문장만 고치고 코드는 안 맞춘다.** 선언↔진실 결함을 반대 방향으로 옮기는 것일 뿐이다.
- **F2 — 단계적 PR 로 프로젝트를 하나씩 정렬한다.** 크로스프로젝트 변경은 원자적 PR 이어야 한다(`CLAUDE.md`).
- **F3 — AC-0 을 이 티켓의 표로 대신한다.** 모집단 물려받기. 이 표도 가설이다.
- **F4 — 8벌 중복을 방치한다.** 같은 결함을 서비스 단위로 반복 수정하게 된다 — 그때 물을 질문은 "공급원이 왜 계속 만드나" 다.

---

## Test Requirements

- 정렬되는 각 서비스에 대해 **배선 도달성 테스트**: 실제 예외가 핸들러까지 도달하는지. 모킹으로 핸들러를 직접 호출하는 테스트는 배선을 증명하지 못한다.
- SQLState 판별식을 채택하면 **실 DB(Testcontainers, CI Linux 권위)로 23505 가 실제로 전달되는지** 최소 1건 증명.

---

## Definition of Done

- [ ] AC-0 전수 재측정 (앵커된 탐지식, 자기검증 선행)
- [ ] AC-1 모양 판정 + 근거
- [ ] AC-2 코드 판정 + 계약 대조
- [ ] AC-3 `error-handling.md:137` 개정
- [ ] AC-4 원자적 크로스프로젝트 정렬 PR
- [ ] AC-5 중복 판정 (libs 승격 여부, FIN-BE-058 반박 포함)
- [ ] AC-6 계약 문서 선행 갱신

---

## Notes

- **분량**: large. 5개 프로젝트 + 공유 레지스트리.
- **dependency**: `선행` = `TASK-BE-542`(ecommerce 선별 배선 — 참조 구현이자 이 티켓이 수렴시킬 이탈). `형제` = `TASK-MONO-444`(같은 "선언↔진실" 클래스).
- **이 task 가 방어하는 실패 모드**: **한 서비스의 관행을 함대 규칙으로 승격하는 것은 무손실이 아니다.** `MONO-052` 가 `user-service` 의 catch-all 을 Platform-Common 으로 올렸지만 형제 배선은 따라오지 않았고, 그 결과 레지스트리는 **함대 대부분이 하지 않는 일을** 정경으로 적어 왔다. 승격 시점에 "이제 누가 이걸 지키는가" 를 세지 않으면 규칙은 문서에만 산다. [[project_enforcement_straggler_sibling_parity]] [[feedback_repo_knows_what_it_does_not_say]] [[feedback_workaround_becomes_the_contract]]
