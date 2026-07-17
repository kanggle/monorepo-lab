# TASK-PC-BE-012 — console-bff dead-code + 미배선 config 제거

**Status:** review
**Area:** platform-console / console-bff (Java, hexagonal)
**Type:** `TASK-PC-BE` (backend refactor — **행동 불변**)
**Lifecycle:** backlog(2026-07-18 발굴) → 착수-시 0-caller 재grep + 구현 → review

---

# Goal

리네임/디커플링 리팩토링이 남긴 미호출 심볼·미배선 config·stale 주석을 제거. **각 심볼은 착수 시 worktree 재grep 으로 0-caller 재확인**(스냅샷 승계 금지) 후 제거. 행동 불변.

# 제거 완료 (착수-시 재grep 0-caller 재확인)

| # | 심볼 | 처리 |
|---|---|---|
| 1 | `OperatorOverviewCompositionUseCase.compose(String)` 1-arg 오버로드 | 제거(0-arg·2-arg 보존). 재grep: 1-arg 호출 0 |
| 2 | `DomainReadPort.domainTarget()` + 6 @Override | 제거(선언 7·호출 0). 미사용된 `DomainTarget` import 5곳 정리(Iam/Wms 어댑터는 live `{@link ...selectFor(DomainTarget)}` javadoc 참조로 존치) |
| 3 | `CompositionEngine.routeLabel()` getter | getter 메서드만 제거. **필드 `routeLabel` 존치**(로그·메트릭 태그 live) |
| 4·5 | `DegradePolicy.isPartialFailure`/`countDegraded` | 둘 다 제거(호출 0). `isAllDown` 존치(`OperatorOverviewCompositionUseCase` 사용) |
| 6 | `CompositionEngine.CARD_ORDER` public static field | 제거. 유일 소비자 `CompositionEngineTest`(7개소 구조적 fixture)를 로컬 `DOMAINS`(동일 5-도메인 리스트)로 대체 → 테스트 행동 보존·필드 디커플 |

# config / doc

- `consolebff.gap.issuer-url` (`application.yml`) — `consolebff.gap.*` 바인더 0(재grep: `consolebff.notifications.*`=`@ConfigurationProperties`, `consolebff.outbound.*`=`@Value` 만 배선). **미배선 2줄 제거**. live `spring.security.oauth2.resourceserver.jwt.issuer-uri` + `consolebff.outbound.gap.base-url`(별개 IAM outbound 키)은 무접촉.
- `SecurityConfig.java` stale javadoc("skeleton only, TASK-PC-BE-001") → 3 live 컨트롤러(`DomainHealthController`·`NotificationAggregatorController`·`OperatorOverviewController`) + `CompositionEngine` 명시로 갱신.

# Acceptance Criteria

- [x] 전 후보 착수-시 0-caller 재grep 재확인 후 제거.
- [x] `routeLabel` 필드·`isAllDown`·live config 키 보존(과제거 0).
- [x] `CompositionEngineTest` 외 테스트 무접촉.
- [x] 검증: `./gradlew :projects:platform-console:apps:console-bff:check` **BUILD SUCCESSFUL**(tests=54, failures=0). 13 files, 29 ins/99 del(all console-bff).

# Related Specs

- 발굴: 2026-07-18 콘솔 리팩토링 스윕(dead-code 스캔). console-web features/shared 는 같은 날 audited dead-code-clean(0건).

# Edge Cases / Failure Scenarios (검증됨)

- `routeLabel` getter vs 필드 혼동 → getter 만 제거, 필드 8개소 존치 확인.
- `consolebff.gap`(top-level dead) vs `consolebff.outbound.gap`(live IAM base-url) 혼동 → diff 로 dead 2줄만 제거 확인.
- CARD_ORDER 를 단순 assertion 으로 오인 → 실제 구조적 fixture(7개소) → 로컬 상수 대체로 행동 보존.

# Review Notes

- 구현: backend-engineer(Sonnet) 위임 → 오케스트레이터 diff 검증(파일 범위·테스트 스코프·routeLabel 필드·yml diff).
