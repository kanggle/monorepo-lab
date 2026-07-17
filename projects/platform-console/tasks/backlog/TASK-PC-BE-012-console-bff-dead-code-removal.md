# TASK-PC-BE-012 — console-bff dead-code + 미배선 config 제거

**Status:** backlog — candidate (2026-07-18 리팩토링 스윕 발굴, 0-caller 실측 재검증)
**Area:** platform-console / console-bff (Java, hexagonal)
**Type:** `TASK-PC-BE` (backend refactor, 행동 불변)
**Confidence:** HIGH (아래 심볼은 발굴 스윕 + 본 세션이 repo-wide 로 호출 0을 직접 재grep)

## 발굴 근거

console-bff 는 전반적으로 깨끗하나(도메인·어댑터·유스케이스 1파일 1책임), 리네임/디커플링 리팩토링이 남긴 미호출 심볼이 잔존. **각 항목은 착수 시 재grep 으로 0-caller 재확인**(리플렉션·Spring 배선·test-only 누락 방지) 후 제거.

## 제거 후보 (2026-07-18 재검증 = 호출 0)

| # | 심볼 | 위치 | 검증 |
|---|---|---|---|
| 1 | `OperatorOverviewCompositionUseCase.compose(String)` 1-arg 오버로드 | `application/usecase/OperatorOverviewCompositionUseCase.java:106` | 모든 호출이 0-arg(`compose()`) 또는 2-arg(`compose(tenant, account)`); 1-arg 호출 0. Javadoc 스스로 "Backward-compatible 1-arg overload" |
| 2 | `DomainReadPort.domainTarget()` 인터페이스 메서드 + **6개 @Override 구현** | `application/port/outbound/DomainReadPort.java:24` (+ 6 어댑터) | 선언 7개, **호출 0**(테스트 포함) |
| 3 | `CompositionEngine.routeLabel()` **getter 메서드** | `application/composition/CompositionEngine.java:136` | getter **메서드** 호출 0. ⚠️ **필드** `routeLabel` 은 라이브(로그·메트릭 태그) — getter 만 제거, 필드 존치 |
| 4 | `DegradePolicy.isPartialFailure(List<LegOutcome>)` | `domain/composition/DegradePolicy.java:24` | 호출 0(형제 `isAllDown(...)` 이 실사용) |
| 5 | `DegradePolicy.countDegraded(List<LegOutcome>)` | `domain/composition/DegradePolicy.java:38` | 호출 0 |
| 6 | `CompositionEngine.CARD_ORDER` public static field | `application/composition/CompositionEngine.java:104` | Javadoc 이 `fanOut(...)` 가 "no longer iterates this constant"(TASK-MONO-241 디커플)라 명시. 유일 소비자=`CompositionEngineTest`. **medium — 테스트 먼저 손봐야 제거 가능** |

## 미배선 config / 문서 stale (동반 정리)

- **`consolebff.gap.issuer-url`** (`src/main/resources/application.yml:65-67, 발굴 스윕 보고)** — `@ConfigurationProperties`/`@Value` 로 `consolebff.gap.*` 를 바인딩하는 코드 없음(라이브 `spring.security.oauth2.resourceserver.jwt.issuer-uri` 와 같은 env `CONSOLE_BFF_IAM_ISSUER_URL` 을 가리키는 GAP→IAM 리네임 잔재로 추정, TASK-MONO-180/192). **⚠️ 본 세션의 grep 은 이 키를 재현하지 못함 — 착수 시 yml 직접 확인 필수**(발굴 보고와 실측 대조).
- **`SecurityConfig.java:40-42` stale javadoc** — *"No composition routes exist yet (skeleton only, TASK-PC-BE-001)"* 라 적혀 있으나 실제로는 3개 컨트롤러(`DomainHealthController`·`NotificationAggregatorController`·`OperatorOverviewController`) + 컴포지션 엔진(55 Java 파일)이 존재. 주석 갱신.

## backlog → ready 게이트

- [ ] 각 심볼 착수-시점 repo-wide 재grep 으로 0-caller 재확인(스냅샷 승계 금지). CARD_ORDER 는 테스트 동반 수정 계획.
- [ ] `consolebff.gap` yml 키 실재·미배선 직접 확인.
- [ ] AC: `./gradlew :projects:platform-console:apps:console-bff:check` GREEN, 행동 불변.

## Reference

- 발굴: 2026-07-18 콘솔 리팩토링 발굴 스윕(dead-code 스캔). console-web features/shared 는 같은 날 **audited-clean(0건)**.
