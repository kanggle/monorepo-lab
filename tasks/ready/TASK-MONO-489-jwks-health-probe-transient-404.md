# Task ID

TASK-MONO-489

# Title

`libs/java-gateway` `JwksHealthProbe` — 부팅 프로브가 4xx(404)를 항상 non-retryable로 취급해, 다운스트림 IdP가 재기동 중일 때 게이트웨이가 즉시 fail-fast 크래시루프에 빠진다

# Status

ready

# Owner

monorepo

# Task Tags

- code
- test
- shared-library

---

# Required Sections (must exist)

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

**동기 인시던트 (2026-07-29, fan-platform 로컬 스택)**: Rancher Desktop 강제 재시작으로 42개
컨테이너가 동시 콜드부팅하는 동안, `iam-e2e-auth-service`(fan-platform이 iam.local로 참조하는
JWKS 서빙 컨테이너)가 자체적으로 재기동(02:56:12 최초 기동 → 03:40:21 재시작, 195초 소요)을
겪었다. 이 창(03:36:51~03:43:56) 동안 `fan-platform-gateway`는 **5회 연속 fail-fast
재시작 루프**를 돌았고, 그 결과 이 창과 겹치는 시간대에 게이트웨이 경유 모든
`/api/v1/*` 호출이 엔드포인트 무관하게 전면 504/타임아웃으로 실패했다(도메인 서비스
자체는 건강했음 — 요청이 죽어있는 게이트웨이를 통과하지 못해 도달조차 안 함).

로그로 확인한 정확한 원인:

```
03:36:59.152 ERROR JwksHealthProbe - JWKS endpoint probe failed ... Cause:
  WebClientResponseException$NotFound: 404 Not Found from GET http://iam.local/oauth2/jwks
```

`libs/java-gateway`의 `JwksHealthProbe.isTransient()`(현재 구현, `probe()` 메서드가 소비)는
**모든 4xx를 "URI가 잘못 설정된 것이고 재시도해도 소용없다"고 판단해 즉시 종결
처리**한다(`JwksHealthProbe.java:96-105`). 이 가정은 "URL이 영구적으로 틀렸다"는
상황에는 맞지만, 이번 인시던트처럼 **URL은 옳고 라우팅도 성공했으나, 백엔드(auth-service)가
재기동 중이라 그 경로에 대한 매핑이 아직 등록되지 않아 일시적으로 404를 반환하는
상황**을 "영구 오설정"과 구분하지 못한다. 그 결과 프로브는 원래 설계된 지수백오프
재시도(1s→2s→4s→8s→16s, ~31s)를 전혀 활용하지 못하고 즉시 컨텍스트를 닫는다 — 오히려
connection-refused/timeout(WARN 재시도 로그가 실제로 찍혔던 케이스, transient로 이미
정상 분류됨)보다 더 나쁘게 처리된다.

**compose 레벨 `depends_on`으로 해결 불가**: fan-platform과 iam-platform은 서로 다른
`docker compose` 프로젝트/네트워크로 독립적으로 기동되며(각자 별도 compose invocation),
표준 Compose `depends_on: condition: service_healthy`는 같은 compose 스택 내에서만
작동한다 — 두 프로젝트를 하나로 강제 병합하지 않는 한 이 경로로는 조율할 수 없다.
따라서 실효성 있는 완화 지점은 프로브 자체의 재시도 분류 로직뿐이다.

이 태스크는 `isTransient()`를 **404(그리고 필요 시 근접한 4xx)를 제한적으로만
재시도 가능하게** 완화해, "실제로 옳은 URL인데 다운스트림이 막 재기동 중"인 경우를
버텨내면서도, "URL 자체가 영구적으로 틀렸다"는 원래 의도한 조기 실패는 그대로
보존한다.

# Scope

## In Scope

- `libs/java-gateway`의 `JwksHealthProbe`(`probe()`/`isTransient()`) 수정 — 404(및
  근거가 있다면 다른 특정 4xx)에 대해 **소수의 bounded 재시도**(예: 추가 2~3회, 짧은
  고정/backoff 간격, 총 추가 예산 수 초 이내)를 허용한 뒤에도 계속 4xx면 최종적으로는
  여전히 종결 처리(컨텍스트 닫음)하도록 정책을 정한다. **구체적 재시도 횟수/예산은
  이 task 안에서 결정하고 근거와 함께 명시 기록**(TASK-MONO-357 Edge Case ①의 "근거
  있는 쪽을 택하고 명시" 선례를 따름) — 이 문서가 특정 숫자를 강제하지 않는다.
- 기존 클래스 상단 javadoc(`isTransient` 관련 설명, `JwksHealthProbe.java:96-99`)을
  새 정책에 맞게 갱신.
- 이 변경으로 인한 회귀 여부를 기존 `JwksHealthProbe` 관련 테스트(라이브러리 자체
  단위테스트 + gateway-service/scm/finance/erp 각 소비처의 wiring 테스트) 전수
  확인.
- 신규 테스트: 이번 인시던트와 동일한 형태(1차 시도=TimeoutException으로 실패,
  2차 시도=404, 이후 성공)를 재현해 프로브가 컨텍스트를 닫지 않고 성공하는지 확인 +
  4xx가 예산 소진까지 계속되는 adversarial 케이스(진짜 오설정 흉내)는 여전히
  컨텍스트를 닫는지 확인(mutation 성격).

## Out of Scope

- 전체 `overallTimeout`/재시도 아키텍처 재설계 — 이 task는 4xx 분류 지점만 좁게
  건드린다.
- fail-fast 철학 자체 폐기(예: "절대 크래시하지 않는다") — 진짜 영구 오설정은
  여전히 빠르게 실패해야 한다.
- compose/Traefik 레벨 조율(위 Goal에서 설명한 대로 두 프로젝트가 별도 compose
  스택이라 구조적으로 불가) — 별건.
- `wms`에 이 프로브를 새로 배선하는 것 — `wms`는 여전히 이 빈을 갖지 않는다(opt-in
  설계 불변, TASK-MONO-357 AC-5 유지).
- `iam-platform` 쪽 auth-service의 재기동 속도 자체를 개선하는 것.

---

# Acceptance Criteria

- [ ] **AC-1** `isTransient()`가 404(최소한 이 인시던트가 관측한 상태 코드)를 무조건
      terminal로 취급하지 않고, bounded 재시도 예산 안에서는 transient로 취급한다.
      선택한 예산(횟수/간격/총 추가 시간)과 그 근거를 이 task 파일 또는 구현 PR
      설명에 명시 기록한다.
- [ ] **AC-2** 예산을 다 써도 계속 4xx면 프로브는 여전히 종결 처리하고 애플리케이션
      컨텍스트를 닫는다 — "영구 오설정은 빠르게 실패한다"는 원래 설계 의도가
      살아있음을 신규 adversarial 테스트로 고정.
- [ ] **AC-3** 신규 테스트가 이번 인시던트 재현 시나리오(TimeoutException → 404 →
      성공)에서 프로브가 컨텍스트를 닫지 않고 정상 완료됨을 확인한다.
- [ ] **AC-4** `libs/java-gateway`의 기존 `JwksHealthProbe*Test` 전부 무회귀, 그리고
      이 클래스를 `@Bean`으로 소비하는 gateway-service들(fan-platform, scm, finance,
      erp — grep으로 전수 확인) 각각의 관련 테스트 스위트 무회귀.
- [ ] **AC-5** `wms`는 여전히 이 빈을 배선하지 않는다 — 컴파일/스캔 확인(기존
      `JwksHealthProbeWiringTest` 계열 가드가 있다면 그대로 통과, 없다면 이 변경이
      새로 만들지 않음을 확인).
- [ ] **AC-6** 클래스 상단 javadoc의 `isTransient` 설명이 새 정책과 일치하도록
      갱신됨.

---

# Related Specs

- `platform/shared-library-policy.md` — 변경 대상이 신규 라이브러리가 아니라 기존
  라이브러리 클래스의 재시도 정책 조정이므로, 착수 전 이 정책의 Change Rule이
  이 규모의 변경에 ADR을 요구하는지 확인할 것(불확실하면 STOP하고 보고).
- [`ADR-MONO-048`](../../docs/adr/ADR-MONO-048-shared-reactive-gateway-library.md) —
  `libs/java-gateway`를 낳은 결정, `JwksHealthProbe`가 opt-in `@Bean`인 이유(§ D4/D6
  정정본).
- [`TASK-MONO-357`](../done/TASK-MONO-357-finance-erp-gateways.md) — `JwksHealthProbe`가
  라이브러리로 승격된 원 task, opt-in 설계와 wms 제외 근거.

# Related Contracts

없음 — 부팅 프로브 내부 재시도 정책 조정, API/이벤트 계약 표면 불변.

---

# Target Service

- `libs/java-gateway` (shared) — 소비처: `projects/fan-platform/apps/gateway-service`,
  `projects/scm-platform` 게이트웨이, `projects/finance-platform` 게이트웨이,
  `projects/erp-platform` 게이트웨이 (grep으로 실제 소비처 전수 재확인 후 착수 —
  TASK-MONO-357 이후 구성이 바뀌었을 수 있음).

---

# Edge Cases

- 재시도 예산을 너무 넉넉하게 주면, 진짜 영구 오설정(잘못된 JWKS URL)의 조기 발견이
  그만큼 늦어진다 — "URI가 확실히 틀렸다"는 신호를 상태 코드만으로는 완벽히 구분할
  수 없다는 것을 전제로, 예산은 작게(수 초 단위 추가) 유지해 이 tradeoff를 최소화할
  것.
- Traefik/ingress가 "백엔드는 있으나 앱이 라우트를 아직 등록 안 해 404"와 "라우팅
  자체가 없어 404"를 상태 코드만으로 구분하지 못하는 것은 이 task로 완전히 없앨 수
  없는 근본 한계 — 이 task는 완화(bounded 재시도)만 한다.
- 재시도 예산 증가가 게이트웨이 전체 부팅 시간에 미치는 영향(overallTimeout과의
  상호작용)을 확인 — 기존 `overallTimeout`(consumer마다 설정 가능)을 초과하지
  않도록 예산을 그 안에 포함시킬지, 별도로 둘지 결정하고 명시.

# Failure Scenarios

- 4xx 전체를 무조건 재시도 가능하게 완화하면(과도한 완화), 진짜 잘못된 URL도
  추가 재시도 시간만큼 실패 발견이 늦어지고 "설정 오류의 명확한 조기 실패"라는
  원래 의도가 사라진다 — 반드시 bounded(무제한 아님)로, 그리고 가능하면 404(또는
  근거가 명확한 좁은 범위)만 대상으로 한다.
- 이번 완화를 로컬 수동 재현(auth-service를 실제로 재기동시키며 curl)으로만
  검증하고 유닛테스트로 고정하지 않으면, 다음 리팩토링에서 조용히 재발한다 — 원래
  결함이 CI로 못 잡혔던 것과 동일한 패턴(부팅 시점 배선/타이밍은 일반 단위테스트로
  안 잡힘, `JwksHealthProbe`의 `probe()`가 순수 `Mono` 반환이라 `StepVerifier` 등으로
  직접 단위테스트 가능해야 함).
- wms가 이 변경으로 실수로 새 빈을 얻으면(예: 리팩토링 중 어노테이션 실수) 전에
  없던 IdP 부팅 의존성이 조용히 생긴다 — AC-5가 이를 막는다.

---

# Test Requirements

- `libs/java-gateway` 단위테스트: 이번 인시던트 재현 시나리오(WARN 재시도 후 404 →
  성공) + adversarial(4xx 지속 → 최종 종결) 최소 2케이스, `WebClient`
  mock(`MockWebServer` 또는 동등 수단)로 결정적 재현.
- 기존 `JwksHealthProbeTest`류 전체 무회귀 재실행.
- 소비처(fan-platform 최소, 가능하면 scm/finance/erp도) 관련 wiring/부팅 테스트
  무회귀.

---

# Definition of Done

- [ ] `isTransient()`/`probe()` 재시도 정책 조정 + 근거 기록
- [ ] javadoc 갱신
- [ ] 신규 테스트(정상화 시나리오 + adversarial) 추가·통과
- [ ] 기존 라이브러리·소비처 테스트 전수 무회귀 확인
- [ ] wms 무영향 확인
- [ ] 리뷰 준비 완료
