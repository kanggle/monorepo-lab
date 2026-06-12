# Task ID

TASK-FAN-INT-002

# Title

community-service live-trio e2e — membership 게이트 escape-hatch 정렬. FAN-BE-010 이 `HttpMembershipChecker` 를 prod 기본 빈으로 만들면서 live-trio(gateway+community+artist, membership-service/iam 부재)에서 fail-closed deny → `VisibilityTierE2ETest` 가 2026-06-09 이후 매 nightly RED. e2e 가 inert v1 stub 을 선택하도록 property gate 추가 + 테스트 단언 현행화.

# Status

done

# Owner

backend (Opus 4.8 analysis + impl). 프로젝트 내부(`projects/fan-platform/`). community-service prod 1 빈 + e2e base 1 env + e2e 테스트 단언.

# Task Tags

- code
- test

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Dependency Markers

- **선행/원인**: TASK-FAN-BE-010 (`community HttpMembershipChecker adapter swap`, #1205, 2026-06-09 머지) — prod 기본 `MembershipChecker` 를 `AlwaysAllowMembershipChecker` → `HttpMembershipChecker` 로 교체하면서 PREMIUM 분기도 membership-service 호출(fail-closed)로 전환. live-trio e2e 는 PR-게이트가 아니라(nightly 전용) 회귀가 그대로 main 에 누출.
- **관련**: TASK-FAN-INT-001 (live-trio e2e 스위트). 본 task 가 예고된 `TASK-FAN-INT-002 candidate` (VisibilityTierE2ETest 클래스 javadoc 이 명시) 의 실현 — 단, 당시 구상한 deny-profile 이 아니라 stub-escape-hatch 정렬로 범위 조정.

# Goal

live-trio 스택은 gateway+community+artist 만 띄우며 membership-service 와 iam(워크로드 identity 토큰 발급처)은 범위 밖이다. FAN-BE-010 이후 community 의 `HttpMembershipChecker` 가 prod 기본이 되어, 이 스택에서 MEMBERS_ONLY/PREMIUM 읽기마다 `http://iam.local/oauth2/token` 토큰 발급 실패 → fail-closed deny → `VisibilityTierE2ETest` 의 200 기대가 깨진다.

community 가 문서화된 escape-hatch(`community.membership-service.enabled=false`)로 inert `AlwaysAllowMembershipChecker`(v1 stub)를 선택하도록 e2e 컨테이너를 구성하고, 테스트 단언을 현재 stub 동작(200 + stub WARN 로그)에 맞춘다. 실 HTTP 게이트(403 deny 분기 포함)는 이미 `MembershipGateIntegrationTest`(MockWebServer, PR-게이트) + federation-hardening-e2e 가 결정적으로 커버하므로 live-trio 는 cross-service 배선만 검증한다.

# Scope

## In Scope

- **`MembershipCheckerAutoConfig.java`** — `httpMembershipChecker` 빈에 `@ConditionalOnProperty(name="community.membership-service.enabled", havingValue="true", matchIfMissing=true)` 추가. default(미설정/true)=prod 그대로 `HttpMembershipChecker`; `false`=빈 제외 → 기존 `@ConditionalOnMissingBean` stub 이 선택. 클래스 javadoc 에 escape-hatch 문단 추가.
- **`FanPlatformE2ETestBase.java`** — community 컨테이너에 `.withEnv("COMMUNITY_MEMBERSHIP_SERVICE_ENABLED", "false")` + 사유 주석.
- **`VisibilityTierE2ETest.java`** — 클래스 javadoc 현행화(prod=Http, e2e=stub-opt-out) + PREMIUM/MEMBERS_ONLY 단언 정렬:
  - PREMIUM: status 200 유지(stub allow), WARN 로그 단언 `"PREMIUM gate bypassed"` → `"Membership gate bypassed (inert fallback stub selected"`, postId 단언 → `"tier=PREMIUM"`(stub 은 account/tier/tenant 로깅, postId 미로깅).
  - MEMBERS_ONLY: WARN 로그 단언 `"Membership gate bypassed (v1 stub)"` → `"Membership gate bypassed (inert fallback stub selected"` + `"tier=MEMBERS_ONLY"`.

## Out of Scope

- `HttpMembershipChecker` / `IamClientCredentialsTokenProvider` / `PostAccessGuard` 도메인 로직 불변.
- prod 기본 동작 불변(default true → Http 그대로). 계약·API·이벤트 무변경.
- live-trio 에 membership-service/iam 컨테이너 추가(과도 — full 게이트는 IT + federation-e2e 담당).
- deny(403 MEMBERSHIP_REQUIRED) e2e 경로(여전히 IT 가 권위; live-trio 미도입).
- nightly-e2e.yml platform-console jar-restore 경로(별건 — TASK-MONO-233).

# Acceptance Criteria

- [ ] `community.membership-service.enabled=false` 시 community 가 `AlwaysAllowMembershipChecker` 를 선택(빈 제외) — `COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false` env relaxed-binding 으로 `@ConditionalOnProperty` 가 인식.
- [ ] default(미설정) 시 prod 가 `HttpMembershipChecker` 유지(net-zero) — 기존 `MembershipGateIntegrationTest` 통과.
- [ ] live-trio `VisibilityTierE2ETest` 3 케이스(PUBLIC/MEMBERS_ONLY/PREMIUM) 전부 200 + 해당 stub WARN 로그 단언 통과.
- [ ] community-service unit/slice/IT + e2e 모듈 컴파일 GREEN.
- [ ] 다음 nightly-e2e 의 "E2E full (fan-platform v1 live-trio)" 잡 GREEN.

# Related Specs

- `projects/fan-platform/specs/services/community-service/` (membership 게이트 동작). 본 task 는 동작 변경 아님(테스트 환경 정렬 + escape-hatch) → 스펙 변경 불요.
- TASK-FAN-INT-001 (live-trio e2e 스위트 정의).

# Related Contracts

- 변경 없음. (API/이벤트 계약 무관 — 내부 config 토글 + 테스트.)

# Target Service

- community-service (fan-platform). Service Type 변경 없음.

# Architecture

- `MembershipCheckerAutoConfig` 는 단일 `@Configuration` 안에 real 빈(first) + `@ConditionalOnMissingBean` stub(second) 을 둬 같은-클래스 top-to-bottom 순서로 결정성을 확보(메모리 §19). `@ConditionalOnProperty(matchIfMissing=true)` 를 real 빈에 얹으면, 속성 false 일 때 real 빈이 미등록 → stub 의 `@ConditionalOnMissingBean` 이 비어있음을 보고 등록. Spring `SystemEnvironmentPropertySource` 가 `COMMUNITY_MEMBERSHIP_SERVICE_ENABLED` → `community.membership-service.enabled` relaxed lookup 을 제공하므로 컨테이너 env 로 조건이 평가된다. prod 는 속성 미설정 → real 빈 유지(net-zero).

# Edge Cases

- `community.getLogs()` 는 컨테이너 시작 이후 누적 stdout — 케이스 간 실행 순서 무관하게 각 테스트는 자기 read 후 자기 tier 라인을 단언(`contains`, 부재 단언 아님)하므로 교차 오염 없음.
- stub 은 account/tier/tenant 만 로깅(postId 미로깅) → postId 기반 단언 제거하고 tier 기반으로 교체.
- 토큰 provider(`IamClientCredentialsTokenProvider`, @Component)는 stub 활성 시 미사용(lazy, 무해) — 시작이 iam 가용성에 결합되지 않음.

# Failure Scenarios

- escape-hatch 만 켜고 테스트 단언 미정렬 → PREMIUM 의 `"PREMIUM gate bypassed"`/postId 단언이 stub 메시지와 불일치해 여전히 FAIL. AC 가 단언 정렬 명시.
- real 빈을 `@ConditionalOnProperty havingValue="false"` 로 잘못 거는 등 default 반전 → prod 가 stub 으로 fail-open(보안 회귀). AC 가 default=Http(net-zero) + IT 통과 명시.
- env 이름 오타(relaxed-binding 불일치) → 조건이 default(true)로 평가되어 컨테이너가 여전히 fail-closed. AC 가 라이브 e2e GREEN 으로 보증.

# Definition of Done

- [ ] `httpMembershipChecker` `@ConditionalOnProperty` gate + javadoc
- [ ] e2e community 컨테이너 `COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false`
- [ ] `VisibilityTierE2ETest` javadoc + PREMIUM/MEMBERS_ONLY 단언 정렬
- [ ] community-service test + e2e 컴파일 GREEN, live-trio VisibilityTier 라이브 GREEN
- [ ] Acceptance Criteria 충족
- [ ] Ready for review
