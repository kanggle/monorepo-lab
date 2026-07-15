# Task ID

TASK-MONO-420

# Title

MONO-415 가 놓친 형제 함대 — 독립 `GlobalExceptionHandler` 사본 17개가 여전히 404(NoResourceFound)를 500 으로 변질 (erp/scm/fan/finance/wms)

# Status

ready

# Owner

backend / platform

# Task Tags

- code
- cross-project

---

# 🔴 발견 경위 (2026-07-16 형제 게이트웨이 라이브 실검증 스윕)

IAM 라이브 스윕의 후속으로 **형제 게이트웨이(erp/scm/fan/finance) 실검증**을 돌리던 중, 어제 머지된 게이트웨이 admission 시리즈(MONO-416/417/419)는 커버리지와 함께 **깨끗이 착지**했음을 확인했다(admission `@Bean` 배선 + 테스트가 4개 게이트웨이 + wms 전부에 존재 — scm 은 `clientCredentialsTokenPassesThroughToDownstream`(scope-only→200) + `authenticatedTokenWithoutRoleOrScope→403` 양쪽 leg 커버, 통합테스트 아키텍처라 클래스명 grep 엔 안 잡혔을 뿐). **admission 층에는 결함 없음.**

그러나 그 과정에서 **MONO-415 가 고친 결함 클래스(404→500)가 형제 함대에는 도달하지 않았음**을 발견했다.

- **MONO-415 는 모집단을 "`CommonGlobalExceptionHandler`(공유 lib) 소비자"로 정의**했다 → iam 4개(상속) + ecommerce 11개(분기 사본, EC-504 로 10개 수정, auth 폐기 1). 
- 그런데 **erp/scm/fan/finance/wms 의 downstream 서비스들은 공유 lib 을 상속하지 않고 각자 독립 `@RestControllerAdvice` 사본**을 갖는다 → **MONO-415 의 시야에 아예 없었다**("Out of Scope"조차 아님). 
- 이것은 이 저장소가 반복해서 밟은 **"범위를 물려받지 말고 모집단을 다시 세라"** 패턴 그 자체다: 결함의 실제 모집단은 "공유 lib 소비자"가 아니라 **"unscoped `@RestControllerAdvice` + 포괄 `@ExceptionHandler(Exception.class)` + `NoResourceFound` 핸들러 없음"** 인 모든 서비스다.

**실검증 상태(정직한 기록)**: 정적으로 결함 shape 를 17개 서비스 전수 확정했다. 라이브 500 재현은 **직접 몰지 못했다** — 실행 중이던 33컨테이너 스택(federation-hardening-e2e = iam+ecommerce+console, **대상 형제 4개 미포함**) 위에 형제 스택을 더 띄우면 이 호스트의 알려진 OOM 캐스케이드 위험이 있었고, 그 스택엔 이미 수정된(iam/ecommerce) 또는 스코프된(console-bff) 서비스만 있어 결함 서비스를 산 채로 칠 수 없었다. curl 로 얻은 라이브 신호: console-bff 는 unmatched 경로에 **401**(인증 필터가 핸들러 도달 전 차단) → **이 결함은 *인증된* unmatched 요청에만 표면화**한다. **그래서 AC-0 에 착수 시 라이브 500 재현을 못박는다(MONO-415 선례 동일).**

---

# Goal

erp/scm/fan/finance/wms 의 독립 `GlobalExceptionHandler` 사본들이 **매핑 없는 경로**(Spring 의 `NoResourceFoundException` / `NoHandlerFoundException`)를 **404** 로 응답하도록 고친다. 현재는 각 사본의 포괄 `@ExceptionHandler(Exception.class)` 가 이를 삼켜 **500 INTERNAL_ERROR** 로 변질시킨다 — MONO-415/EC-504 가 iam/ecommerce 에서 고친 것과 **정확히 동일한 결함**이되, 공유 lib 을 상속하지 않는 사본들이라 그 수정이 도달하지 못했다.

## Root Cause (실측, 재검증 대상)

각 서비스가 unscoped `@RestControllerAdvice` + 아래 포괄 핸들러를 갖고, `NoResourceFoundException`/`NoHandlerFoundException` 전용 핸들러가 **없다**:

```java
@RestControllerAdvice                       // ← basePackages 스코프 없음 = 앱 전역
public class GlobalExceptionHandler {
    ...
    @ExceptionHandler(Exception.class)      // NoResourceFoundException 도 여기로 삼켜짐
    public ResponseEntity<...> handleGeneral(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR) // 404 여야 할 것이 500
                .body(...of("INTERNAL_ERROR", "An unexpected error occurred"));
    }
}
```

**메커니즘은 이미 실증됨**: EC-504(같은 monorepo, Spring Boot **3.4.1**, 동일한 unscoped-advice-catches-`NoResourceFound` 동작)가 `GlobalExceptionHandlerNotFoundTest` 로 이 shape 가 500 을 냄을 증명하고 404 핸들러로 고쳤다. 설정 오버라이드(`spring.web.resources.add-mappings=false`, 커스텀 `ErrorController`/`ErrorAttributes`) 는 형제 4개 프로젝트에 **0건**(grep) → 기본 동작(unmatched→`NoResourceFoundException`→포괄 핸들러→500) 도달 확정.

## 결함 모집단 (정적 확정, AC-0 재계수 대상 — **물려받은 가설**)

unscoped `@RestControllerAdvice` + 포괄 `Exception→500` + `NoResourceFound` 핸들러 없음:

| 프로젝트 | 서비스 |
|---|---|
| erp | approval, masterdata, notification, read-model (4) |
| fan | artist, community, membership, notification (4 — 각 서비스의 `AbstractDomainExceptionHandler` 기저에 포괄 핸들러) |
| scm | demand-planning, inventory-visibility, procurement (3) |
| finance | account, ledger (2) |
| wms | admin, inbound, inventory, outbound (4) |

**= 17 서비스.** 

**제외(확인 완료)**:
- **wms master-service** — 이미 `NoResourceFound` 핸들러 보유(수정됨). 
- **console-bff** — `@RestControllerAdvice(basePackages = "...adapter.inbound.web")` 로 **의도적 스코프**(actuator/prometheus 500 회피, PR #669). 스코프된 advice 는 컨트롤러에 매칭 안 되는 `NoResourceFoundException` 을 안 잡고 Spring 기본 처리(404)로 흘려보냄 → **안전**. (이 스코핑이 수정 대안 B 의 살아있는 예시.)
- **ecommerce** — EC-504 로 10/11 수정, auth-service 폐기(TASK-BE-132).
- **iam** — 공유 `CommonGlobalExceptionHandler` 상속, MONO-415 로 수정.

---

# Scope

## In Scope (per-project adaptation — `projects/<name>/`)

- 위 17개 서비스의 `GlobalExceptionHandler`(fan 은 `AbstractDomainExceptionHandler` 기저) 에 `NoResourceFoundException`(+ `NoHandlerFoundException`) 전용 `@ExceptionHandler` 추가 → **404** + **그 프로젝트가 이미 쓰는 에러 바디 형태**(erp/scm `ApiErrorBody`, fan `ApiErrorBody`, finance/wms 각자의 타입). 포괄 `Exception.class` 보다 구체적이므로 우선 매칭. EC-504 가 ecommerce 에서 한 것과 동형 — **각 프로젝트의 기존 에러 코드/바디 컨벤션 보존**(공통 타입으로 통일하지 말 것 = 계약 변경 회피).
- **수정 방식 두 가지 중 택1(프로젝트 일관성 우선)**: (A) 전용 404 핸들러 추가(EC-504 선례, 권장 — 명시적) 또는 (B) `@RestControllerAdvice(basePackages=...)` 스코핑(console-bff 선례). **A 를 기본**으로 하되, 이미 스코핑 컨벤션이 확립된 서비스가 있으면 그에 맞춤.
- 각 서비스에 **"매핑 없는 경로 → 404" 단위/슬라이스 테스트** 추가(EC-504 의 `GlobalExceptionHandlerNotFoundTest` 동형) — 이 결함 클래스가 다시 드리프트하면 red 가 되도록(가드=술어가 실패모드에 맞아야; 이번 클래스의 화석은 "없는 경로→500" 단언이므로 그런 테스트가 있으면 정정).

## Out of Scope

- 각 서비스의 도메인 예외 핸들러(정상 동작).
- 405/415 등 다른 프레임워크 예외의 500 변질 — **동반 점검만** 하되(포괄 핸들러가 삼키는지 확인), 스코프 폭주 방지를 위해 최소한 404 는 확실히. 405/415 가 실제로 삼켜지면 별도 판단(같은 PR 에 넣을지 후속 티켓으로 끊을지).
- console-bff / wms master / ecommerce / iam(이미 안전 또는 수정됨).
- `CommonGlobalExceptionHandler`(공유 lib) 변경 — 이미 MONO-415 로 수정됨. 이 티켓은 **상속하지 않는 사본들**만 다룬다.

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정, 코드가 이긴다)**: 
  - (a) 모집단 **재계수** — `@RestControllerAdvice` unscoped + 포괄 `Exception→500` + `NoResourceFound` 핸들러 부재를 전 프로젝트에서 다시 grep 해 위 17개 목록과 대조(이 표는 **인계된 가설**이지 출처가 아니다). wms master / console-bff 가 여전히 안전한지 재확인.
  - (b) **라이브 500 재현** — 대상 프로젝트를 (호스트 메모리 여유 확인 후) 풀스택 기동, **인증된** 토큰으로 게이트웨이 경유 매핑 없는 경로(예: `/api/v1/procurement/definitely-not-real`) 호출 → **현재 500 INTERNAL_ERROR 재현**(프로젝트당 최소 1개 대표 서비스). 인증 없이는 401 게이트에 막히므로 반드시 유효 토큰으로.
- [ ] 매핑 없는 경로가 **404** + 각 프로젝트 표준 에러 바디 반환(프로젝트당 대표 서비스 ≥1 에서 라이브 확인).
- [ ] 기존 동작 회귀 0: `VALIDATION_ERROR`(400/422), `CONFLICT`(409), 진짜 서버 오류(500)는 그대로.
- [ ] 각 수정 서비스에 "없는 경로 → 404" 테스트 추가(드리프트 재발 시 red).
- [ ] **cross-project 원자 PR** — 5개 프로젝트 수정을 한 PR 에서 GREEN(CLAUDE.md § Cross-Project Changes). PR 묶음은 케이스별 자유지만, **모집단을 부분만 고쳐 survivor 를 남기지 말 것**(이 티켓의 존재 이유).
- [ ] 루트 `./gradlew check` GREEN(전 수정 서비스 빌드/테스트).

# Related Specs

> Before reading: 루트 `tasks/INDEX.md` § "When to Use Root vs Project Tasks"(이 변경은 5개 프로젝트 횡단이므로 monorepo-level).

- `platform/error-handling.md` — `NOT_FOUND`(404) 표준 에러 코드/바디 규약.
- `tasks/done/TASK-MONO-415-shared-exception-handler-turns-404-into-500.md` — 공유 lib 수정 선례 + 모집단 재계수 교훈(§ "11" 혼동).
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-504*` — EC-504, 분기 사본 10개에 404 핸들러 추가한 동형 선례(수정 방식 A 의 참조 구현 + `GlobalExceptionHandlerNotFoundTest`).
- 각 수정 서비스의 `specs/services/<svc>/architecture.md` 에러 응답 규약.

# Related Contracts

- 각 프로젝트의 HTTP 계약이 404 응답 형태를 어떻게 규정하는지(정합 확인). 404 바디 형태 변경이 기존 계약/테스트를 깨지 않을 것(특히 "없는 경로 → 500" 을 단언하는 테스트가 있으면 그 자체가 결함의 화석 → 정정).

# Edge Cases

- **인증 게이트 우선**: 다운스트림 서비스가 자체 인증을 요구하면 unauth unmatched 요청은 401 로 막혀 결함이 안 보인다 — **인증된 요청**에서만 표면화. 게이트웨이 뒤 서비스는 보통 게이트웨이 신원 헤더를 신뢰하므로, 게이트웨이 경유 인증 요청 + 유효 라우트 접두 + 미매핑 경로(예: `/api/v1/procurement/<존재하는 접두>/nonexistent`)가 재현 경로.
- **게이트웨이 자체 404 vs 서비스 404**: 게이트웨이 라우트에 매칭 안 되는 경로(`/totally/unknown`)는 **게이트웨이 리액티브** 핸들러가 404 — 이 티켓 무관. 라우트 접두는 매칭되나 다운스트림에 컨트롤러가 없는 경로만 이 결함 대상.
- **fan `AbstractDomainExceptionHandler`**: 포괄 핸들러가 기저 클래스에 있음 → 404 핸들러를 기저에 넣으면 4개 서비스 일괄 커버(단 4개가 각자 사본이므로 4곳 편집). 어디에 넣든 각 concrete advice 가 실제로 상속받는지 테스트로 확인.
- **405/415 동반**: 포괄 핸들러가 `HttpRequestMethodNotSupportedException`(405), `HttpMediaTypeNotSupportedException`(415)도 500 으로 삼키는지 확인 — 삼키면 최소 404 는 이 PR, 나머지는 별도 판단.

# Failure Scenarios

- **부분 수정 → survivor**: 17개 중 일부만 고치면 남은 서비스가 계속 500 을 내고, "고쳤다"는 인상이 커버 안 된 표면을 가린다(MONO-415 가 정확히 이래서 형제를 놓쳤다). AC-0 재계수 + 원자 PR 로 방지.
- **공통 타입으로 통일 시도 → 계약 변경**: 각 프로젝트 에러 바디를 하나로 합치려다 HTTP 계약/기존 테스트를 깬다. **각자 형태 보존**이 최소 침습.
- **테스트 없이 수정 → 재드리프트**: 404 테스트를 안 넣으면 다음 리팩터가 포괄 핸들러를 되살려도 아무도 모른다. 술어는 "없는 경로가 404 인가"여야(500 이 아닌가가 아니라).
- **로컬 초록에 속음**: 이 호스트 로컬 IT 는 flaky, Docker 다운 시 전건 SKIPPED 이 `BUILD SUCCESSFUL` 로 보임 → CI(Linux)가 IT 권위. 라이브 재현은 실제 기동 확인(`docker ps`) 후.

# Target

- `projects/erp-platform/apps/{approval,masterdata,notification,read-model}-service/.../GlobalExceptionHandler.java`
- `projects/fan-platform/apps/{artist,community,membership,notification}-service/.../AbstractDomainExceptionHandler.java`(+ 필요 시 concrete `GlobalExceptionHandler.java`)
- `projects/scm-platform/apps/{demand-planning,inventory-visibility,procurement}-service/.../GlobalExceptionHandler.java`
- `projects/finance-platform/apps/{account,ledger}-service/.../GlobalExceptionHandler.java`
- `projects/wms-platform/apps/{admin,inbound,inventory,outbound}-service/.../GlobalExceptionHandler.java`
- 각 서비스 test 디렉터리에 "없는 경로 → 404" 테스트.

# 분석/구현 권장 모델

- 분석 = Opus 4.8 (1M) — 이 스윕
- 구현 권장 = **Sonnet** — 결함 클래스가 단일·기계적(EC-504 참조 구현 존재)이고 17개 반복. 단 AC-0 라이브 재현 + 프로젝트별 에러 바디 컨벤션 판별은 주의 요.
