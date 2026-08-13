# Task ID

TASK-MONO-522

# Title

아티스트 디렉터리(아티스트·그룹·팬덤)에는 **API 호출자가 존재하지 않는다 — 결정에 의해서다.** 운영자 평면을 열지 않기로 한 뒤 그 세 리소스를 누가 관리하는지는 아무도 정하지 않았다

# Status

done

# Owner

monorepo

# Task Tags

- iam
- demo
- security

---

# 배경

`TASK-MONO-512` 가 닫히면서 남긴 것이다. 그 티켓은 `ADR-MONO-059` **ACCEPTED — A** 아래에서
`ARTIST` 역할 발급을 열어 **저작**(`ARTIST_POST`)을 도달 가능하게 만들었고, 시드의 게시물
블록을 API 로 회수했다. 그런데 같은 결정이 **다른 절반을 영구히 닫았다**:

```java
// artist-service config/SecurityConfig.java
ADMIN_ROLES = { "ADMIN", "OPERATOR", "SUPER_ADMIN", "FAN_OPERATOR" };
.requestMatchers(HttpMethod.POST, "/api/artists/**", "/api/artists").hasAnyRole(ADMIN_ROLES)
// artist-groups · fandoms 의 POST/PATCH/DELETE 도 같은 상수를 쓴다
```

- `FAN_OPERATOR` 는 `tenant_domain_subscription(*, 'fan')` 에서 **파생**되는데 그 행은
  전 테넌트 **0/18** 이고, `fan-platform` 은 `operator_tenant_assignment` 에도 없다.
- `ADR-MONO-059` § Decision 이 B(운영자 대리 저작)를 배제하며
  *"`B2C_CONSUMER` 테넌트를 운영자가 assume 하는 새 조합은 **열지 않는다**"* 를
  **binding** 으로 확정했다. ⇒ 그 두 행을 넣는 것은 **미완의 작업이 아니라 결정에 반하는 작업**이다.
- `ADMIN`/`OPERATOR`/`SUPER_ADMIN` 은 파생이 아니라 **부여** 역할이라 `account_roles` 행
  하나로 발급 가능하지만, `fan-platform` 계정에 그것을 부여하면 community 의
  `ActorContext.isOperator()` 가 참이 되어 **B 를 옆문으로 다시 여는 것**이 된다
  (`PublishPostUseCase` 통과 + `owns()` 로 테넌트 내 모든 저자의 게이팅 우회).

⇒ 세 리소스는 **어떤 호출자로도 만들 수 없다.** `infra/demo/seed/seed-fan.sh` 의 첫
`dbexec --why` 가 직접-DB 로 남은 이유이고, 그 `--why` 는 `TASK-MONO-512` 를 사유로 들던
문장을 **이 티켓으로 옮겨 다시 썼다**(고쳐진 결함의 면제를 그대로 두면 회귀를 가린다).

## 🔴 이것이 결함인지 사양인지 아직 아무도 판정하지 않았다

포트폴리오 데모로서는 *"아티스트 등록은 v1 범위 밖"* 이 완전히 정당한 답이다. 그러나
지금 상태는 그 답을 **적어 두지 않은 채** 쓰기 라우트만 남겨 둔 것이고, 그것이
`TASK-MONO-512` 가 처음 제기했던 문제(**받는 쪽은 있는데 주는 쪽이 없다**)와 정확히 같은
모양이다 — 한 층 위에서 반복될 뿐이다.

---

# Goal

아티스트 디렉터리의 쓰기 표면에 대해 **명시적 판정**을 남긴다: 관리 경로를 열든,
"v1 범위 밖" 을 코드와 스펙에 적든. 어느 쪽이든 *말없이 도달 불가한 라우트*로 남지 않는다.

---

# Scope

## In Scope

- `artist-service` 의 `ADMIN_ROLES` 기반 9개 매처(artists/groups/fandoms × POST/PATCH/DELETE)에
  대한 결정과 그 기록
- 결정이 "연다" 라면 그 역할을 **어떤 평면으로** 발급하는지 (🔴 `ADR-MONO-059` 의 binding 을
  건드리므로 **새 ADR 필요** — HARDSTOP-09)
- `seed-fan.sh` 첫 `dbexec --why` 블록의 최종 처리

## Out of Scope

- `ARTIST` 역할 발급 · `ARTIST_POST` 저작 — `TASK-MONO-512` 에서 **완료**
- `FAN_OPERATOR` 수용부 3곳의 제거 — `ADR-MONO-059` 가 D 를 채택하지 않았다.
  이 티켓이 D 를 다시 여는 것이라면 그것 자체가 ADR 사안이다

---

# Acceptance Criteria

- [x] **AC-0 (실측)** — ✅ **완료 2026-08-13. 세 숫자 전부 `0` ⇒ 전제가 산다**(되돌리는 작업 아님).
      전문 기록: [`ADR-MONO-063` § 실측](../../docs/adr/ADR-MONO-063-artist-directory-write-plane.md).
      **소스**(마이그레이션·시드 전수)와 **라이브 DB**(`iam_mysql-data` 볼륨) 양면으로 쟀고 각
      숫자에 대조군을 붙였다 — `tenants=12 subs=18` 은 `ADR-MONO-059` 의 2026-08-07 모집단과
      **정확히 일치**(그 사이 아무도 열지 않았다) · 5개 도메인 18행 중 `fan` 만 부재 ·
      fan-platform 의 `account_roles` 는 `ARTIST 3 · FAN 3` 으로 **읽힌다**(⇒ 이 0 은 조회
      실패가 아니다). 🔵 곁다리 확증: 라이브 `oauth_clients` **10 cc / 16 total** 이
      `WorkloadRoleCatalog` 키 집합과 정확히 일치하고 admin-tier 는 어느 워크로드에도 없다 —
      `ADR-MONO-061` 의 fail-closed 기본값이 실제로 잠겨 있다.
      ↓ 원문 AC (판정 근거로 보존)
- [ ] ~~**AC-0 (실측)** — 착수 시점에 다시 잰다: `tenant_domain_subscription` 의 `fan` 행 수,
      `fan-platform` 의 `operator_tenant_assignment` 행, `fan-platform` 테넌트 계정 중
      admin-tier `account_roles` 를 든 것. 🔴 셋 다 **0 이어야 이 티켓의 전제가 산다** —
      누군가 이미 열었다면 그것은 `ADR-MONO-059` 위반이고 이 티켓은 **되돌리는 작업**이 된다~~
- [x] **AC-1 (결정)** — ✅ **완료 2026-08-13**:
      [`ADR-MONO-063`](../../docs/adr/ADR-MONO-063-artist-directory-write-plane.md)
      **ACCEPTED — D1** (소유자 정확형 `ADR-MONO-063 ACCEPTED — D1` 직접 타이핑).
      ⇒ **닫는다**: 아티스트 디렉터리의 쓰기 평면은 **v1 제품 범위 밖**. A·B·C·D2 배제.
      🔴 이 ADR 은 `ADR-MONO-061` 이 명시 인계한 rider(*"fan artist-service 의 `ADMIN_ROLES`
      매처를 워크로드 신원에 여는가"*)를 함께 답했다 — **아니오**. 어떤 cc 클라이언트도
      admin-tier 를 받지 않으며, 그 근거가 *"522 를 기다리는 보류"* 에서 **"결정"** 으로 바뀌었다.
      🔵 D2 를 안 골랐으므로 매처를 제거하지 않는다 ⇒ `ADR-MONO-059` 의 어떤 binding 도
      건드리지 않았다
- [x] **AC-2 (열기로 했다면)** — ⛔ **해당 없음** — D1 은 닫는 결정이라 발급 평면도, `201` 실측도
      존재하지 않는다. 🔵 다만 이 AC 가 요구한 판정(*"그 역할이 `isOperator()` 를 참으로
      만드는가"*)은 **선택지 심사 단계에서 수행됐고 그것이 D1 을 고른 핵심 근거다**:
      `ADMIN_ROLES` 의 네 이름이 community-service `isOperator()` 가 받는 바로 그 넷이라,
      사람이든 기계든 발급하면 `owns()` 로 테넌트 내 전 저자의 게이팅 콘텐츠가 열린다
      (= `ADR-MONO-059` 가 배제한 B). `ADR-MONO-063` § 폭발반경 에 기록
- [x] **AC-3 (닫기로 했다면)** — ✅ **유지 + 사유 갱신**. 9개 매처와 `ADMIN_ROLES` 를 **유지**한다
      (제거 = D2, 미채택). `TASK-MONO-512`/`TASK-MONO-514` 가 적어 둔 주석을 이 결정으로 갱신했고,
      🔴 **면제 회수 지점 3곳**을 함께 처리했다 — `WorkloadRoleCatalog` javadoc · 그 테스트의
      `noWorkloadClientHoldsAnAdminTierRole` DisplayName · `SecurityConfig` 주석. 셋 다
      동작 무변경이고 **근거만** "보류" → "결정" 으로 바뀌었다(보류는 티켓이 닫히면 조용히
      만료되지만 결정은 그러지 않는다). `ActorContext.isOperator()` 주석도 같은 결정으로 정합
- [x] **AC-4 (시드)** — ✅ 첫 `dbexec --why` 의 **잠정성 제거 완료**. *"...는 TASK-MONO-522
      소관이다"* → *"**결정에 의해 영구히 직접-DB 다** (ADR-MONO-063 ACCEPTED — D1)"*.
      🔵 되돌리려면 ADR 개정이 필요하다는 것과, 근거(*"호출자가 없다"*)가 콘솔에 fan 관리
      화면이 생기면 사라진다는 것을 함께 적었다

---

# Related Specs

- `projects/fan-platform/apps/artist-service/.../config/SecurityConfig.java` (`ADMIN_ROLES`)
- `projects/fan-platform/apps/community-service/.../application/ActorContext.java` (`isOperator()`)
- `projects/iam-platform/apps/auth-service/.../oauth2/OperatorRoleDerivation.java`
  (`case "fan", "fan-platform"`)
- [`docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md`](../../docs/adr/ADR-MONO-059-fan-authoring-identity-plane.md)
- `infra/demo/seed/seed-fan.sh` (첫 `dbexec --why`)

# Edge Cases

- `ProductCatalog` 에 `fan` 엔트리가 없다 — 구독 행을 넣어도 콘솔 제품은 늘지 않는다.
  즉 "콘솔에서 아티스트를 관리한다" 는 이 티켓보다 넓은 작업이다
- `artist-service` 는 `/internal/**` 워크로드 체인을 이미 갖고 있다(`FAN-BE-045`/`ADR-004`).
  "관리 API 를 워크로드 신원으로 연다" 는 형태가 가능한데, 그러면 **사람이 아닌 것이
  아티스트를 만든다** — 그것이 원하는 모양인지가 결정의 일부다

# Failure Scenarios

- 🔴 **`account_roles` 에 `ADMIN` 한 줄을 넣고 "열었다" 고 한다** — `ADR-MONO-059` 가 배제한
  B 를 옆문으로 여는 것이다. `isOperator()` 가 참이 되는 순간 그 계정은 테넌트 내 모든
  저자의 MEMBERS_ONLY/PREMIUM 글을 `owns()` 로 통과한다
- 🔴 **매처만 지우고 결정을 안 적는다** — 다음 사람이 "왜 아티스트를 API 로 못 만드나" 를
  처음부터 다시 조사한다. 이 티켓의 산출물은 코드가 아니라 **판정**이다

# Definition of Done

- [x] AC-0 실측 기록 — `ADR-MONO-063` § 실측 (세 숫자 전부 0, 대조군 포함)
- [x] ADR 승인(정확형) — **닫기**. `ADR-MONO-063 ACCEPTED — D1` (2026-08-13, 소유자 타이핑)
- [x] 구현 또는 명시적 기록 — **명시적 기록**(코드 동작 변경 0). 매처 9개 유지 · 주석 5곳 정합
      (`SecurityConfig` · `ActorContext` · `WorkloadRoleCatalog` javadoc 2곳 · 그 테스트
      DisplayName) · `build.gradle` 의 잘못된 소유권 주장 정정 · 워크스루 § 6 행 갱신
- [x] `seed-fan.sh` `--why` 의 잠정성 제거
- [x] Ready for review

---

## 착수 기록 (2026-08-13)

**산출물은 코드가 아니라 판정이다** — 티켓 본문이 그렇게 적었고, 실제로 그렇게 끝났다.
동작 변경 **0**, 바뀐 것은 **왜 그런지에 대한 기록**이다.

🔴 **이번 작업의 핵심은 "면제 회수" 였다.** `TASK-MONO-514` 가 남긴 세 지점은 전부
*"`TASK-MONO-522` 가 답할 때까지"* 라는 **보류**로 쓰여 있었다. 보류는 그 티켓이 닫히는
순간 **근거가 만료되고**, 다음 사람에게는 "이미 끝난 것을 기다리는 제약" = 지워도 되는 것으로
읽힌다. D1 은 그 셋을 **결정**으로 바꿨다(같은 바이트, 다른 지위). 회수 지점:

| 지점 | 이전 | 이후 |
|---|---|---|
| `WorkloadRoleCatalog` javadoc | "522 가 답할 때까지 admin-tier 금지" | ADR-063 D1 이 **아니오**로 답함 |
| `WorkloadRoleCatalogTest` DisplayName | "522 has not answered…" | "ADR-MONO-063 (D1) closed…" |
| `SecurityConfig` 주석 | "TASK-MONO-522 owns that gap" | "그 갭은 **판정됐다** — v1 범위 밖" |

🔴 **그리고 반대 방향의 오류도 하나 고쳤다.** `auth-service/build.gradle` 주석이
*"`TASK-MONO-522` inherits the question of where a cross-tree agreement guard should be
scheduled"* 라고 적고 있었는데, **D1 은 그것을 다루지 않았다**(CI 경로필터는 다른 축이다).
닫힌 티켓을 소유자로 남겨 두면 **커버된 것처럼 읽히고**, 그게 둘 중 더 비싼 오류다
(할당된 것처럼 보이는 일은 아무도 다시 파일하지 않는다) ⇒ **무소유로 명시 정정**했고
`ADR-MONO-063` § rider 대조 에도 미결로 기록했다.

🔵 **미결로 남긴 것 2건**(ADR § rider 대조 에 명시): `FAN_OPERATOR` 수용부 나머지 3곳 정리
(별도 사안 — `ADR-MONO-059` 의 D 미채택은 불변) · 위 CI 경로필터 갭(무소유, 티켓 없음).

🔵 **이 결정에는 만료 조건이 있다** — 근거가 *"호출자가 없다"* 이므로(콘솔에 `fan` 제품
엔트리 없음), 콘솔에 fan 관리 화면이 생기는 날 근거가 사라진다. ADR·`SecurityConfig`·시드
`--why` 세 곳에 그 조건을 적어 두었다.

분석·구현=Opus 5 — 신원 평면 결정이고 ADR 게이트가 앞에 있었다.
