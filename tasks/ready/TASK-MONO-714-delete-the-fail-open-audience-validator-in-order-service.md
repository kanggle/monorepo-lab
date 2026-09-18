# Task ID

TASK-MONO-714

# Title

🔴 ecommerce `order-service` 의 fail-open `AudienceValidator` 를 **삭제한다** — fail-closed 로 바꾸지 않는다(같은 사슬의 `sub` 축이 이미 그것을 잰다). 🔴 `"order-service"` 를 설정으로 승격하는 선택지는 **없다**

# Status

ready

# Owner

monorepo

# Task Tags

- security
- code
- test

---

> **분석 모델:** Opus 5 / **구현 권장:** Sonnet (삭제 + 잔여물 제거. 판단은 이미 `TASK-MONO-698` § AC-3 항목 5 가 내렸다 — 남은 것은 «어디까지가 잔여물인가» 를 빠짐없이 지우는 기계적 작업이고, 그 목록은 아래에 이름으로 적혀 있다)
>
> 📎 **선행 결정**: `TASK-MONO-698` § AC-3 항목 5 (소유자 결정, 2026-09-18 UTC) — **삭제**. fail-closed 교체가 **아니다**.

# Goal

`order-service` 의 `/api/internal/**` 사슬에는 `AudienceValidator` 가 꽂혀 있다. 그 검증기의 기대값은 `order.internal.oauth2.audience` 이고, 기본값이 **빈 문자열**이며 **저장소 어디에서도 설정되지 않는다** ⇒ `validate()` 첫 줄에서 무조건 `success()`(`AudienceValidator.java:32-34`). **운영에서는 fail-open** 이다.

소유자 결정은 **삭제**다. 사유(결정문 그대로):

> `client_credentials` 토큰에서 `sub` == `aud` == client id 이므로, **같은 사슬의 `SystemClientSubjectValidator` 가 이미 그 축을 fail-closed 로 재고 있다**(`OrderSecurityConfig.java:72,93` — `sub ∈ {ecommerce-internal-services-client}`). 두 번째 사본을 남기면 **같은 것을 두 번 판정**하면서 값이 두 곳으로 갈라진다.

🔴 **그리고 승격은 선택지가 아니다.** 테스트 헬퍼가 쓰는 값은 `AUDIENCE = "order-service"` — **서비스 이름**이고, 운영 호출자(batch-worker)의 토큰 `aud` 는 발급 client id **`ecommerce-internal-services-client`** 다. 이 값을 `application.yml` 기본값으로 올리면 **운영 내부 호출이 전량 401** 된다. 픽스처 값을 설정에 넣어 테스트를 초록으로 만드는 것은 `TASK-MONO-696` AC-5 가 **명시적으로 금지한** 동작이다.

이 티켓이 끝나면: 이 사슬에 fail-open 검증기가 없고, **«설정돼 있는데 아무도 안 읽는» 잔여물도 없다**.

# Scope

## In Scope

지울 것(전부 — 하나라도 남기면 «설정은 있는데 안 읽힘» 이 남는다):

1. `apps/order-service/src/main/java/com/example/order/infrastructure/config/AudienceValidator.java` — 클래스 자체
2. `OrderSecurityConfig.java:64-65` `@Value("${order.internal.oauth2.audience:}") private String audience;` + `:88` `validators.add(new AudienceValidator(audience));`
3. `src/main/resources/application.yml:82` `audience: ${ORDER_INTERNAL_OAUTH2_AUDIENCE:}` (+ `:78` 의 주석에서 audience 를 말하는 부분)
4. `OrderExistenceIT.java:82` · `ConfirmPaidStaleIT.java:103` 의 `registry.add("order.internal.oauth2.audience", …)` `DynamicPropertySource` 줄
5. `support/InternalJwtTestHelper.java:46` `AUDIENCE = "order-service"` 상수 + 그 상수의 사용처
6. 🔴 **`ConfirmPaidStaleIT#wrongAudienceBearer_returns401`(`:183-192`) — 테스트 칸 자체** (아래 AC-2)
7. Javadoc 의 «audience» 언급(`OrderSecurityConfig.java:40-41,49,80` · `InternalOrderController.java:32` · `SystemClientSubjectValidator.java:18,42`) — 사슬 설명이 **거짓이 되지 않도록** 같이 고친다

## Out of Scope

- `SystemClientSubjectValidator` — **건드리지 않는다**. 이 티켓의 삭제 근거가 그 검증기의 존재다
- 서블릿 엔드유저 사슬 19 · console-bff · iam gateway — 각각 `TASK-MONO-698` § AC-3 / `712` / `713`
- ecommerce 게이트웨이의 audience allowlist — `TASK-MONO-696`/`697`
- `ORDER_INTERNAL_OAUTH2_ISSUER` · `…_JWK_SET_URI` — 🔵 698 § AC-0 (c) 의 대조군이 이 둘도 어디서도 설정되지 않음을 보였지만, **그건 「출하 기본값으로 돈다」이지 fail-open 이 아니다**(issuer 는 실제로 검사된다). 이 티켓의 대상이 아니다

# Acceptance Criteria

- [ ] **AC-0 — 재확인.** 착수 시점 `origin/main` 에서 (a) `ORDER_INTERNAL_OAUTH2_AUDIENCE` 가 여전히 **어디에서도 설정되지 않는지**(698 § AC-0 (c) 가 본 곳: `infra/**` · `.github/**` · `projects/**/*.yml` · compose/env 39개 · `infra/demo/*.override.yml` 14개 · k8s configmap), (b) `SystemClientSubjectValidator` 가 여전히 `sub` 를 fail-closed 로 핀하는지. 🔴 (b) 가 거짓이면 **이 티켓의 삭제 근거가 사라진다** — 멈추고 소유자에게 되묻는다. 🔵 (a) 의 모집단 한계도 그대로 물려받는다: ripgrep 은 `.gitignore` 된 파일을 안 본다 ⇒ 「저장소가 이 값을 설정하지 않는다」가 잰 것이고 「어떤 런타임에도 설정돼 있지 않다」는 **재지 않았다**.
- [ ] **AC-1 — 삭제.** § In Scope 1~5, 7 을 지운다. 🔴 **부분 삭제 금지** — 속성만 지우고 검증기를 남기거나(사슬에 `new AudienceValidator(null)` 이 남는다), 검증기만 지우고 속성을 남기면(「설정은 있는데 아무도 안 읽음」 = 696 이 고친 그 결함의 재생산) 이 AC 는 안 닫힌다.
- [ ] **AC-2 — 사라진 것은 «깨진 테스트» 가 아니라 «대상이 없어진 테스트» 임을 명시하고 지운다.** `ConfirmPaidStaleIT#wrongAudienceBearer_returns401` 은 `aud="some-other-service"` 토큰이 **401** 이 되는 것을 단언한다. 🔴 **그 칸이 초록인 유일한 이유는 같은 IT 가 `DynamicPropertySource` 로 속성을 켜 주기 때문**이고, **운영에서는 그 속성이 비어 있어 이 칸이 재는 동작이 아예 존재하지 않는다** — 즉 이 테스트는 **운영에 없는 동작을 지키고 있었다**. 삭제하고, 커밋 메시지/PR 에 그 이유를 **한 줄로** 적는다(조용히 지우면 «삭제가 커버리지를 줄였다» 로 읽힌다). 🔵 **대조군은 남는다**: 같은 IT 의 `wrongIssuerBearer_returns401` · `validButNonSystemSubject_returns401_andSweepDoesNotRun` · `validButDifferentClientSubject_returns401` 은 **실제로 운영에서 도는 축**(issuer · sub)을 재므로 **손대지 않는다** — 그 셋이 남는다는 사실이 «이 사슬이 무방비가 되지 않았다» 의 증거다.
- [ ] **AC-3 — 승격하지 않았음을 확인.** 변경 후 트리에서 `"order-service"` 가 **audience 값으로** 쓰이는 곳이 **0건**이다. 🔴 `application.yml` 이든 테스트든, 이 값을 audience 로 쓰는 줄이 하나라도 새로 생기면 이 AC 는 실패다.
- [ ] **AC-4 — 검증.** `:projects:ecommerce-microservices-platform:apps:order-service:check` rc=0(파이프 금지, rc 명시). Testcontainers IT 는 CI 가 권위. 🔵 `/api/internal/**` 의 **거절 동작이 유지되는지**가 진짜 판정이다 — AC-2 의 대조군 셋이 그대로 통과해야 한다.

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 ***Behind the edge*** (엣지 뒤 리소스 서버가 allowlist 를 안 두는 이유 — 이 삭제의 근거가 그 문단이다)
- `platform/service-types/rest-api.md` § Authentication and Authorization (포인터)
- `tasks/review/TASK-MONO-698-audience-behind-the-gateway-and-at-the-other-edges.md` § AC-0 (c) · § AC-2 (order-service 처분 표) · § AC-3 항목 5
- `tasks/review/TASK-MONO-696-the-gateway-audience-is-configured-and-never-checked.md` § AC-5 (픽스처 값을 설정으로 승격하지 않는다)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md`

# Target Service

- ecommerce `order-service`

# Edge Cases

- **누군가 운영 환경에서 `ORDER_INTERNAL_OAUTH2_AUDIENCE` 를 설정해 두었다면** — 삭제 후 그 변수는 **아무 효과가 없는 변수**가 된다(에러는 아니다). AC-0 (a) 의 모집단 한계상 저장소 밖은 못 본다 ⇒ 배포 파이프라인 변수 목록을 한 번 훑고, 있으면 같이 지운다.
- **`sub` 와 `aud` 가 갈라지는 토큰** — 이 삭제의 근거는 `client_credentials` 에서 둘이 **같다**는 것이다. 이 표면이 언젠가 **사용자 토큰**(둘이 다르다)을 받게 되면 근거가 사라진다. 🔵 지금은 받지 않는다 — `/api/internal/**` 는 게이트웨이 라우팅에서 제외돼 있고(`OrderSecurityConfig` Javadoc `:36-38`) `SystemClientSubjectValidator` 가 사용자 `sub` 를 거절한다.
- **헬퍼 상수 제거의 파급** — `InternalJwtTestHelper.AUDIENCE` 는 `issueToken(...)` 호출 여러 곳에서 인자로 쓰인다(`ConfirmPaidStaleIT:176,205,219`). 상수를 지우면 그 호출들도 고쳐야 한다 — 🔴 **거기서 `null` 을 넘겨 «aud 없는 토큰» 으로 바꾸면 테스트의 의미가 바뀐다.** 운영과 같은 값(`ecommerce-internal-services-client`)을 넘기는 것이 기본이다.

# Failure Scenarios

- 🔴 **fail-closed 로 «개선»하기** — 소유자 결정을 뒤집는 것이고, 같은 판정이 두 곳에서 갈라지는 상태를 만든다(결정문의 사유 그 자체).
- 🔴 **`"order-service"` 를 설정 기본값으로 승격** — 운영 내부 호출 전량 401. 696 AC-5 가 금지. AC-3 이 막는다.
- 🔴 **부분 삭제** — 속성만 또는 클래스만 지우기. 「설정돼 있는데 안 읽힘」은 696 이 고친 결함의 재생산이다. AC-1 이 막는다.
- **대조군 테스트까지 지우기** — issuer·sub 축의 거절 칸은 **운영에서 실제로 도는 축**이다. 지우면 이 사슬이 진짜로 약해진다. AC-2 가 남길 목록을 이름으로 못박는 이유.

# Test Requirements

- AC-2 의 대조군 셋(issuer 거절 · 비-시스템 `sub` 거절 + sweep 미실행 · 다른 client `sub` 거절)이 **그대로 통과**
- 정상 토큰(`sub = ecommerce-internal-services-client`) → 200
- 🔵 새 테스트는 **필요 없다** — 이 티켓은 동작을 더하지 않고 **운영에서 한 번도 돈 적 없는 코드**를 지운다. 새 칸을 만들면 그 칸이 무엇을 지키는지 설명할 수 없다
- 🔴 파이프로 판정하지 않는다 — 출력은 파일로, rc 는 명시

# Definition of Done

- [ ] AC-0 ~ AC-4
- [ ] `AudienceValidator` · 속성 선언 · 두 `DynamicPropertySource` 줄 · 헬퍼 상수 · 대상이 사라진 테스트 칸이 **모두** 없다
- [ ] 사슬을 설명하는 Javadoc 이 **참**이다(«audience» 를 검사한다고 적힌 줄이 남아 있지 않다)
- [ ] `"order-service"` 가 audience 값으로 쓰이는 곳 **0건**
