---
id: TASK-FAN-BE-019
title: Feed PREMIUM 게이트 fail-close (GetFeedUseCase.isLocked 정합)
status: ready
project: fan-platform
service: community-service
type: bugfix
created: 2026-06-19
---

# TASK-FAN-BE-019 — Feed PREMIUM 게이트 fail-close

## Goal

`community-service` 의 피드 조회 경로(`GetFeedUseCase.isLocked()`)가 `PREMIUM` 글을 **fail-open**(항상 `locked=false`, WARN 로그)으로 처리하는 잔존 결함을 제거하고, 형제 경로 `PostAccessGuard.ensureVisibilityAccessible()`(TASK-FAN-BE-010 에서 fail-close 로 전환됨)와 동일하게 `MembershipChecker` 기반 fail-close 로 정합한다.

## Background / Problem

- `PostAccessGuard`(상세/댓글/반응 경로)는 FAN-BE-010 에서 PREMIUM 을 `membershipChecker.hasAccess(accountId, "PREMIUM", tenantId)` 로 hard fail-close 전환 완료.
- 그러나 피드 목록 경로 `GetFeedUseCase.isLocked()` 는 누락됨 — PREMIUM 분기가 여전히:
  ```java
  if (post.getVisibility() == PostVisibility.PREMIUM) {
      // v1 PREMIUM = always allow (fail-open with WARN). Locked=false.
      // TODO(TASK-FAN-BE-MEMBERSHIP): hard-gate once membership-service exists.
      log.warn("PREMIUM gate bypassed in feed for post {} ...");
      return false;   // ← 비구독자에게 PREMIUM 글이 unlocked 로 노출
  }
  ```
- `MembershipChecker` 는 이미 `GetFeedUseCase` 에 주입돼 있음(MEMBERS_ONLY 분기에서 사용 중). membership-service 는 FAN-BE-008/009/010 으로 이미 존재 → TODO 의 전제("once membership-service exists")는 충족됨.
- **TASK-FAN-BE-018**(doc-only)이 community-service 스펙을 "PREMIUM 게이트 적용됨"으로 정정했으나 이 코드 경로는 미수정 → **코드가 정정된 스펙과 모순**. 본 task 가 그 drift 를 코드 측에서 해소한다.
- 영향: PREMIUM 글의 미리보기(preview/제목)가 피드 목록에서 비구독자에게 `locked=false` 로 노출. 상세 진입은 PostAccessGuard 가 막지만 피드 카드 단계에서 누설.

## Scope

- **IN**: `GetFeedUseCase.isLocked()` 의 PREMIUM 분기를 PostAccessGuard 와 동일한 fail-close 로직으로 교체. PREMIUM/MEMBERS_ONLY locked 동작을 검증하는 테스트 추가.
- **OUT**: `PostAccessGuard` 변경(이미 정상), membership-service 변경, MEMBERS_ONLY 동작(이미 fail-close), 피드 캐시/페이지네이션 로직, 프런트엔드.

## Acceptance Criteria

- [ ] **AC-1**: `GetFeedUseCase.isLocked()` 의 PREMIUM 분기가 `return !membershipChecker.hasAccess(actor.accountId(), PostVisibility.PREMIUM.name(), actor.tenantId());` 로 교체된다(작성자/operator 단축 분기는 유지). fail-open WARN 블록과 stale `TODO(TASK-FAN-BE-MEMBERSHIP)` 주석 제거.
- [ ] **AC-2**: PREMIUM tier hierarchy(PREMIUM ⊇ MEMBERS_ONLY)는 membership-service 가 서버사이드로 해석하므로 클라이언트는 required tier 만 전달한다(PostAccessGuard 의 주석 근거와 동일하게 PREMIUM 전달).
- [ ] **AC-3 (단위 테스트)**: `GetFeedUseCase` 단위 테스트(또는 신규 테스트)에서 — (a) 비구독자에게 PREMIUM 글은 `locked=true`, (b) 구독자(`hasAccess→true`)에게는 `locked=false`, (c) 작성자 본인/operator 는 항상 `locked=false`, (d) membershipChecker 가 PREMIUM 에 대해 호출됨을 검증.
- [ ] **AC-4 (통합 테스트)**: PREMIUM 가시성 글을 시드해 피드 응답의 `locked` 플래그가 멤버십 여부에 따라 올바른지 검증하는 케이스를 추가한다(`FeedQueryIntegrationTest` 는 현재 PUBLIC 만 시드 → 커버리지 공백 보완). membership escape-hatch/stub 정합은 기존 `MembershipGateIntegrationTest` 패턴을 따른다.
- [ ] **AC-5**: `MEMBERS_ONLY` 동작은 회귀 없이 유지(기존 fail-close 그대로).
- [ ] **AC-6**: `:community-service:test` GREEN (Docker-free `:check` 는 wiring 미적발 한계 인지 — Testcontainers IT 는 CI 권위).

## Related Specs

- `projects/fan-platform/specs/services/community-service/architecture.md` (PREMIUM visibility row — FAN-BE-018 에서 게이트 적용 명시)
- `projects/fan-platform/specs/services/community-service/overview.md`
- `projects/fan-platform/specs/services/membership-service/membership-api.md`

## Related Contracts

- 없음(내부 동작 정합, 외부 HTTP/이벤트 컨트랙트 무변경). 피드 응답 DTO 의 `locked` 필드는 기존과 동일(값만 정확해짐).

## Edge Cases

- membership-service 다운/오류 → `HttpMembershipChecker` 가 fail-closed(deny) → `locked=true`. (PostAccessGuard 와 동일 안전측 동작.)
- 작성자 본인의 PREMIUM 글 → `locked=false`(작성자 단축 분기, hasAccess 호출 전).
- operator → `locked=false`(operator 단축 분기).
- MEMBERS_ONLY 와 PREMIUM 이 동일 피드 페이지에 혼재 → 각각 올바른 tier 로 독립 평가.

## Deployment Note

- `GetFeedUseCase` 는 `FeedPage`(직렬화된 `locked` 필드 포함)를 Redis 에 5분 TTL 로 캐시한다(`feed:<tenant>:<account>:<page>:<size>`). 본 수정 배포 직후, 구버전(fail-open)으로 캐시된 PREMIUM `locked=false` 엔트리는 최대 5분간 캐시 단축경로로 계속 노출될 수 있다(아키텍처 스펙이 명시한 TTL-only 무효화 trade-off). 게이트를 즉시 강제하려면 배포 시 feed-cache 네임스페이스를 1회 flush(`DEL feed:fan-platform:*`)하거나 5분 TTL 소진을 대기한다. (코드 변경 불요 — 운영 절차 노트.)

## Failure Scenarios

- AC-1 만 적용하고 테스트 미보강 → 회귀 가드 부재. AC-3/AC-4 필수.
- `MembershipChecker` stub(`AlwaysAllowMembershipChecker`)이 단위/통합 테스트에서 PREMIUM 을 항상 통과시키면 fail-close 검증이 무의미 → 테스트는 명시적으로 deny 케이스를 스텁/모킹해야 한다.
