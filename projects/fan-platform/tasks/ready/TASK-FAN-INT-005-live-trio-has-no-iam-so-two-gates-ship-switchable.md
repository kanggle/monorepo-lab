# Task ID

TASK-FAN-INT-005

# Title

live-trio e2e 에 **iam 이 없어서** 게이트 두 개가 "끌 수 있는 상태"로 배포된다 — 탈출구의 원인은 서비스가 아니라 **토큰 발급처**다

# Status

ready

# Owner

fan-platform

# Task Tags

- integration
- e2e
- security

---

# 배경

`TASK-FAN-BE-045` AC-7 이 이 사실을 **잘못 읽었다가 구현 중에 정정**하면서 드러났다.

v1 live-trio e2e(`FanPlatformE2ETestBase`, `TASK-FAN-INT-001`)는
**gateway + community + artist** 만 띄운다. 원문:

```java
// The live-trio is gateway+community+artist only — membership-service
// and iam (the workload-identity token source) are out of scope, so
// HttpMembershipChecker would fail-closed on every ... read.
.withEnv("COMMUNITY_MEMBERSHIP_SERVICE_ENABLED", "false")
```

🔴 **핵심은 괄호 안이다.** 탈출구가 필요한 이유는 *피호출자가 없어서*가 아니라
**`client_credentials` 토큰을 발급할 iam 이 없어서**다. `FAN-BE-045` 는 처음에
*"artist-service 는 트리오에 떠 있으니 탈출구가 필요 없다"* 로 판단했는데, artist-service 가
떠 있는 것은 맞지만 **토큰을 못 얻으므로** 검증은 여전히 전부 fail-closed 로 닫힌다.

## 그 결과 지금 상태

community-service 에는 **끌 수 있는 게이트가 둘** 있고, 둘 다 e2e 때문에 존재한다:

| 스위치 | 켜졌을 때 | 껐을 때 |
|---|---|---|
| `community.membership-service.enabled` | `HttpMembershipChecker` | `AlwaysAllowMembershipChecker` — **항상 통과** |
| `community.artist-service.enabled` | `HttpArtistAccountChecker` | `UnverifiedArtistAccountChecker` — **항상 통과** |

🔵 두 번째 것은 `FAN-BE-045` 가 위험을 줄여서 넣었다 — 진짜 checker 를
`@ConditionalOnMissingBean` 폴백으로 두어 **예상 못 한 설정이 전부 검증 ON 으로** 떨어지게
했고, 기본값을 `ArtistAccountCheckerConfigTest` 가 고정한다. 첫 번째 것은 반대 모양이다
(허용 빈이 `@ConditionalOnMissingBean` ⇒ 사고로 선택될 수 있다).

그래도 **둘 다 남는 문제는 같다**: 운영 배포가 env 하나로 게이트를 끌 수 있고, 껐을 때
서비스는 정상 기동해 초록으로 보인다.

---

# Goal

live-trio 가 iam 을 포함해 **워크로드 토큰을 실제로 발급**하게 만들고, 그 결과
두 탈출구를 **삭제**한다 — 게이트가 꺼질 수 있는 경로 자체를 없앤다.

---

# Scope

## In Scope

- `FanPlatformE2ETestBase` 에 iam(auth-service) + 그 의존(DB/시드) 추가
- 두 checker 의 탈출구 빈·property·env 제거 (`AlwaysAllowMembershipChecker`,
  `UnverifiedArtistAccountChecker`, 각 `@ConditionalOnProperty`)
- 제거에 따라 갱신되는 테스트: `MembershipCheckerAutoConfigTest`,
  `ArtistAccountCheckerConfigTest`, 관련 e2e 시나리오

## Out of Scope

- membership-service 를 트리오에 넣을지 — **별개 결정**이다. 이 티켓은 *토큰 발급처*를 넣는
  것이고, membership 게이트의 탈출구를 지우려면 membership-service 도 필요하다.
  🔴 **착수 시 이것부터 판단할 것**(AC-0) — iam 만 넣어서는 membership 쪽 탈출구를 못 지운다면
  이 티켓의 범위는 **artist 쪽 하나로 줄어든다**. 줄이는 것이 맞다면 줄이고 사유를 적을 것
- 다른 프로젝트의 e2e 스택

---

# Acceptance Criteria

- [ ] **AC-0 (범위 실측 — 착수 전 필수)** — 두 탈출구를 **각각** 지우려면 트리오에 무엇이 더
      필요한지 실측한다. iam 만으로 artist 쪽이 닫히는가? membership 쪽은 membership-service
      까지 필요한가? 🔴 그 답에 따라 **이 티켓의 범위를 줄이거나 쪼갠다** — 둘 다 지운다고
      가정하고 시작하지 말 것
- [ ] **AC-1 (토큰이 실제로 나온다)** — 트리오 안에서 community 가 iam 으로부터
      `client_credentials` 토큰을 **실제로** 받아 내부 호출에 쓰는 것을 확인한다.
      🔴 판정은 "iam 컨테이너가 떴다" 가 아니라 **검증이 켜진 채 e2e 시나리오가 통과**하는 것
- [ ] **AC-2 (탈출구 삭제)** — 범위에 든 탈출구의 빈·property·env·문서 언급을 전부 지운다.
      🔴 `grep` 으로 잔존 0건 확인 — 빈만 지우고 property 문서가 남으면 다음 사람이 되살린다
- [ ] **AC-3 (되살아나지 않게)** — 탈출구가 없다는 것을 **테스트가 단언**한다
      (예: checker 빈이 항상 실제 구현이라는 컨텍스트 테스트). 기존
      `ArtistAccountCheckerConfigTest`/`MembershipCheckerAutoConfigTest` 의 "껐을 때" 케이스는
      **삭제가 아니라 반전**시킬 것 — 지우면 아무도 그 축을 다시 안 본다
- [ ] **AC-4 (e2e 가 느려지는 대가를 잰다)** — iam 추가 전후 live-trio 잡의 벽시계를
      기록한다. 🔴 크게 늘면 그 자체가 판단 재료다(`project_ci_wallclock_playbook`)

---

# Related Specs

- `projects/fan-platform/tests/e2e/src/test/java/com/example/fanplatform/e2e/testsupport/FanPlatformE2ETestBase.java`
- `projects/fan-platform/apps/community-service/.../infrastructure/membership/MembershipCheckerAutoConfig.java`
- `projects/fan-platform/apps/community-service/.../infrastructure/artist/ArtistAccountCheckerConfig.java`
- `projects/fan-platform/specs/integration/v1-e2e-scenarios.md`
- `docs/adr/ADR-MONO-005` (워크로드 아이덴티티)

# Related Contracts

- 없음 — 이 티켓은 배선과 테스트 스택만 바꾼다. HTTP 계약은 그대로다

# Edge Cases

- iam 은 자체 DB + Flyway 시드가 필요하다(클라이언트 행). 트리오에 DB 를 하나 더 띄우는지,
  기존 postgres 를 나눠 쓰는지 — iam 은 **MySQL** 레인이라는 점을 확인할 것
- `JwksMockServer` 가 지금 JWKS 를 대신하고 있다. iam 이 들어오면 **누가 JWKS 의 주인인지**
  겹친다 — 엔드유저 토큰은 계속 목으로 서명할지, iam 이 발급할지 정해야 한다
- 🔴 `TASK-BE-579`(iam) 와 겹친다 — 그쪽은 *발급 자체*를 iam 안에서 검증한다.
  **둘 다 필요하고 독립이다**: 저쪽이 초록이어도 트리오는 여전히 탈출구를 쓰고 있을 수 있다

# Failure Scenarios

- 🔴 **iam 만 넣고 탈출구는 남긴다** — e2e 는 계속 꺼진 채로 돌고, 스택만 무거워진다.
  탈출구 삭제가 이 티켓의 산출물이지 iam 추가가 아니다
- 🔴 **"껐을 때" 테스트를 그냥 지운다** — 그 축이 감사에서 사라진다. 반전시킬 것
- 🔴 **membership 쪽까지 한 번에 하려다 막힌다** — AC-0 이 그래서 있다. 쪼갤 것

# Definition of Done

- [ ] AC-0 실측 + 범위 확정(줄였다면 사유)
- [ ] iam 이 트리오에 들어가고 토큰이 실제로 발급됨
- [ ] 탈출구 삭제 + grep 잔존 0건
- [ ] 탈출구 부재를 단언하는 테스트
- [ ] 벽시계 전후 기록
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=**Opus** — 스택 구성 + 보안 게이트 삭제라 범위 판단이 계속 필요하다.
