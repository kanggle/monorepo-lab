# Task ID

TASK-FAN-INT-006

# Title

membership 게이트는 아직 **끌 수 있는 상태로 배포된다** — 그리고 남은 이유는 서비스가 아니라 **결제로만 생기는 ACTIVE 행**이다

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

`TASK-FAN-INT-005` 의 AC-0 이 두 탈출구를 각각 실측하고 **범위를 쪼갠 결과**다.
artist 쪽은 그 티켓이 지웠다(트리오에 iam 을 넣어 실제 토큰을 발급). 남은 것이 이것이다.

```
community.membership-service.enabled=false  →  AlwaysAllowMembershipChecker  (항상 통과)
```

## 🔴 남은 이유는 **iam 부재가 아니다** — 그건 INT-005 가 해소했다

두 탈출구를 하나로 묶어 읽으면 안 된다. INT-005 이후 트리오에는 iam 이 있고 워크로드
토큰이 나온다. membership 쪽이 남은 이유는 **둘 다** 있어야 하기 때문이다:

1. **membership-service 컨테이너** — 트리오(이제 콰르텟)에 없다. Postgres 에
   `fanplatform_membership` DB 는 init 스크립트가 이미 만든다(`01-create-databases.sh`)
2. 🔴 **ACTIVE 멤버십 행** — 그리고 이것이 진짜 비용이다. 제품상 그 행을 만드는 경로는
   `POST /api/fan/memberships` 이고, 그 앞에 **빌링키(PortOne)** 가 있다
   (`BillingKeyController`, `PortOneWebhookController`). 즉 e2e 가 게이트를 *통과*하는
   케이스를 만들려면 결제 평면을 세우거나 **DB 직접 시드를 도입**해야 한다

⇒ 그래서 INT-005 는 "iam 을 넣었으니 둘 다 지운다" 로 가지 않았다. 성질이 다르다.

## 🔴 이쪽 탈출구는 artist 쪽보다 **모양이 더 나쁘다**

| | artist (INT-005 이 삭제) | membership (이 티켓) |
|---|---|---|
| 허용 빈 선택 방식 | `@ConditionalOnProperty(havingValue="false")` — **명시적으로만** | `@ConditionalOnMissingBean` — **폴백** |
| 예상 못 한 설정이 떨어지는 곳 | 검증 ON | **허용** |

`ADR-004` § Decision Drivers 3 이 지목한 그 모양이다: 빈 등록이 어떤 이유로든 실패하면
게이트가 **조용히 꺼진 채** 서비스가 정상 기동하고 초록으로 보인다.

---

# Goal

`community.membership-service.enabled` 와 `AlwaysAllowMembershipChecker` 를 **삭제**한다 —
게이트가 꺼질 수 있는 경로 자체를 없앤다.

---

# Scope

## In Scope

- `FanPlatformE2ETestBase` 에 membership-service 추가 (+ `fanplatform_membership` DB 연결)
- e2e 시나리오가 MEMBERS_ONLY / PREMIUM 을 **통과**하는 데 필요한 ACTIVE 멤버십을 만드는 경로 확보
- `AlwaysAllowMembershipChecker` · `@ConditionalOnProperty` · env · 문서 언급 삭제
- `MembershipCheckerAutoConfigTest` 의 "껐을 때" 케이스 **반전**(삭제 아님)
- `VisibilityTierE2ETest` 단언을 stub 동작이 아니라 **실 게이트 동작**으로 정정
  (지금 단언은 `COMMUNITY_MEMBERSHIP_SERVICE_ENABLED=false` 를 전제로 쓰여 있다)

## Out of Scope

- PortOne 실 PG 연동 — 데모/로컬은 이미 `portone` 프로파일로 분리돼 있다

---

# Acceptance Criteria

- [ ] **AC-0 (ACTIVE 행을 만드는 방법을 먼저 정한다 — 착수 전 필수)** — 세 후보를 실측하고
      고른다: ① 결제 스텁을 붙여 제품 경로 그대로 가입 ② membership-service 에 e2e 전용
      시드(Flyway `migration-dev` 대역 등) ③ 테스트가 DB 에 직접 INSERT.
      🔴 판단 기준은 편의가 아니라 **무엇을 증명하느냐**다 — ③ 은 게이트는 증명하지만
      가입 경로는 증명하지 않고, ① 은 둘 다 증명하지만 스텁이 실물보다 관대하면 아무것도
      증명하지 않는다. 고른 이유를 적을 것
- [ ] **AC-1 (양쪽 판정이 다 살아 있다)** — 멤버십 **있는** 독자는 200, **없는** 독자는
      403 `MEMBERSHIP_REQUIRED`. 🔴 통과 케이스만 만들면 `AlwaysAllow` 와 구별되지 않는다 —
      **거부 케이스가 이 티켓의 진짜 산출물**이다
- [ ] **AC-2 (탈출구 삭제)** — 빈·property·env·문서 언급 잔존 `grep` 0건
- [ ] **AC-3 (되살아나지 않게)** — "껐을 때" 케이스를 **반전**시키고, INT-005 가
      `ArtistAccountCheckerConfigTest` 에 넣은 **구조 단언**(설정 클래스가 `MembershipChecker`
      `@Bean` 메서드를 정확히 하나만 선언)도 함께 둘 것. 속성 기반 케이스만으로는 **다른
      키 뒤로 되돌아온 탈출구를 못 잡는다** — INT-005 에서 bite 로 실측된 사실이다
      (주입 시 속성 케이스 7건 전부 초록, 구조 케이스 1건만 RED)
- [ ] **AC-4 (벽시계)** — membership-service 추가 전후 live e2e 잡의 벽시계를 기록한다.
      INT-005 가 남긴 기준선과 이어 붙일 것

---

# Related Specs

- `projects/fan-platform/tests/e2e/src/test/java/com/example/fanplatform/e2e/testsupport/FanPlatformE2ETestBase.java`
- `projects/fan-platform/apps/community-service/.../infrastructure/membership/MembershipCheckerAutoConfig.java`
- `projects/fan-platform/specs/integration/v1-e2e-scenarios.md` (§ Scenario 4 / § Two token planes)
- `projects/fan-platform/specs/services/community-service/dependencies.md`
- `projects/fan-platform/docs/adr/ADR-004-artist-account-existence-seam.md` (§ rider 의 답)

# Related Contracts

- `projects/fan-platform/specs/contracts/http/membership-api.md` — 읽기만 한다. 계약 변경 없음

# Edge Cases

- membership-service 는 Postgres 를 쓰지만 **DB 가 다르다**(`fanplatform_membership`).
  init 스크립트가 이미 만들고 있으니 추가 작업은 없을 것 — **확인만 할 것**
- membership-service 도 `/internal/**` 워크로드 체인을 갖고 있고 JWKS 를 **두 군데**서 읽는다
  (`infra/demo/fan-identity.override.yml` 헤더에 기록). INT-005 가 artist 쪽에 한 것과
  같은 배선(`INTERNAL_JWT_JWK_SET_URI` / `INTERNAL_JWT_ISSUER` → iam)이 필요하다.
  🔵 artist 와 달리 membership 은 그 두 키를 **이미 yml 에 선언**하고 있다
- 🔴 community 의 `MEMBERSHIP_SERVICE_BASE_URL` 기본값은 `http://membership-service:8080` 이라
  트리오의 별칭과 다르다. INT-005 가 artist 쪽에서 정확히 이걸 밟았다 — 게이트가 꺼져 있는
  동안에는 아무도 그 URL 을 다이얼하지 않아 **틀린 값이 무해하게 앉아 있었다**

# Failure Scenarios

- 🔴 **통과 케이스만 만들고 끝낸다** — `AlwaysAllow` 와 구별 불가. AC-1 이 그래서 양쪽을 요구한다
- 🔴 **"껐을 때" 테스트를 지운다** — 그 축이 감사에서 사라진다. 반전시킬 것
- 🔴 **AC-0 을 건너뛰고 DB 직접 INSERT 로 시작한다** — 가장 빠르지만 증명 범위가 가장 좁다.
  고르더라도 **고른 것으로** 골라야 한다

# Definition of Done

- [ ] AC-0 결정 + 사유
- [ ] 통과·거부 양쪽 e2e 케이스
- [ ] 탈출구 삭제 + grep 0건
- [ ] 반전 테스트 + 구조 단언(+ bite 확인)
- [ ] 벽시계 전후 기록
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=**Opus** — AC-0 이 증명 범위를 고르는 판단이고, 결제 평면이 걸려 있다.
