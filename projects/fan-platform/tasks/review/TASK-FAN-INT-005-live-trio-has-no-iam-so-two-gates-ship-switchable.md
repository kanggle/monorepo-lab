# Task ID

TASK-FAN-INT-005

# Title

live-trio e2e 에 **iam 이 없어서** 게이트 두 개가 "끌 수 있는 상태"로 배포된다 — 탈출구의 원인은 서비스가 아니라 **토큰 발급처**다

# Status

review

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

> 🔴 아래는 **AC-0 실측 후 확정된 범위**다. 착수 시점의 원문("두 checker 의 탈출구를
> 제거")은 실측이 뒤집었다 — § AC-0 실측 결과 ①.

## In Scope

- `FanPlatformE2ETestBase` 에 **MySQL + iam(auth-service)** 추가 — 서명키 마운트,
  ENTRYPOINT 가 요구하는 DNS 별칭, 두 토큰 평면 배선
- `artist-service` 의 `fanplatform.internal.jwt.{jwk-set-uri,issuer}` 선언
  (형제 membership-service 와 parity — 없으면 내부 디코더를 실 iam 으로 못 가리킨다)
- **artist 쪽** 탈출구 삭제: `UnverifiedArtistAccountChecker` ·
  `community.artist-service.enabled` · e2e env · 문서 언급
- `ArtistAccountCheckerConfigTest` 반전 + 구조 단언 신설
- fan 잡 블록의 CI 배선(`ci.yml` / `nightly-e2e.yml`) — iam jar 빌드·별도 아티팩트·
  서비스 항목·`iam` 트리거·타임아웃
- 공유 재사용 워크플로 일반화는 **`TASK-MONO-523`(root)** 이 소유하고 **같은 PR** 에 실린다

## Out of Scope

- **membership 쪽 탈출구** → `TASK-FAN-INT-006`. AC-0 이 실측한 대로 iam 만으로는 닫히지
  않는다 — membership-service 와 **ACTIVE 멤버십 행**(제품상 PortOne 결제 경로로만 생성)이
  함께 필요하고, 그건 성질이 다른 일이다
- 다른 프로젝트의 e2e 스택 — `_platform-e2e.yml` 변경은 **후방 호환**이며 wms/scm 이
  만들어 내는 명령 문자열은 변하지 않는다(MONO-523 AC-1 이 문자열 단위로 대조)

---

# Acceptance Criteria

- [x] **AC-0 (범위 실측 — 착수 전 필수)** — 두 탈출구를 **각각** 지우려면 트리오에 무엇이 더
      필요한지 실측한다. iam 만으로 artist 쪽이 닫히는가? membership 쪽은 membership-service
      까지 필요한가? 🔴 그 답에 따라 **이 티켓의 범위를 줄이거나 쪼갠다** — 둘 다 지운다고
      가정하고 시작하지 말 것
      → **§ AC-0 실측 결과**. 범위를 **artist 쪽 하나로 줄이고** membership 쪽은
      `TASK-FAN-INT-006`, 공유 CI 재사용 워크플로는 `TASK-MONO-523` 으로 쪼갬
- [x] **AC-1 (토큰이 실제로 나온다)** — 트리오 안에서 community 가 iam 으로부터
      `client_credentials` 토큰을 **실제로** 받아 내부 호출에 쓰는 것을 확인한다.
      🔴 판정은 "iam 컨테이너가 떴다" 가 아니라 **검증이 켜진 채 e2e 시나리오가 통과**하는 것
      → 판정 = `fan-platform-e2e` 레인 GREEN. 그 레인은 `COMMUNITY_ARTIST_SERVICE_ENABLED` 를
      더 이상 설정하지 않으므로 `ArtistAndPostFlowE2ETest` 의 팔로우 201 은 **실제 토큰이
      발급되고 `artist.read` 를 실어 `/internal/artists/exists` 를 통과했을 때만** 성립한다
      (fail-closed 라 토큰이 없으면 그 단계가 곧바로 RED — 판정이 대리지표가 아니다)
- [x] **AC-2 (탈출구 삭제)** — 범위에 든 탈출구의 빈·property·env·문서 언급을 전부 지운다.
      🔴 `grep` 으로 잔존 0건 확인 — 빈만 지우고 property 문서가 남으면 다음 사람이 되살린다
      → 삭제: `UnverifiedArtistAccountChecker.java` · `unverifiedArtistAccountChecker()` 빈 ·
      `community.artist-service.enabled` 속성 · e2e env. 남은 `grep` 히트는 **전부 "지웠다"고
      말하는 산문**(설정 javadoc · yml 주석 · e2e 베이스 javadoc · ADR-004 § rider 의 답)이고
      살아 있는 스위치는 0건
- [x] **AC-3 (되살아나지 않게)** — 탈출구가 없다는 것을 **테스트가 단언**한다
      (예: checker 빈이 항상 실제 구현이라는 컨텍스트 테스트). 기존
      `ArtistAccountCheckerConfigTest`/`MembershipCheckerAutoConfigTest` 의 "껐을 때" 케이스는
      **삭제가 아니라 반전**시킬 것 — 지우면 아무도 그 축을 다시 안 본다
      → 반전 2건 + **구조 단언 1건 신설**. § AC-3 bite 결과
- [ ] **AC-4 (e2e 가 느려지는 대가를 잰다)** — iam 추가 전후 live-trio 잡의 벽시계를
      기록한다. 🔴 크게 늘면 그 자체가 판단 재료다(`project_ci_wallclock_playbook`)
      → before 는 § AC-4 에 기록. after 는 이 PR 의 CI 에서 채운다

---

# AC-0 실측 결과 — 범위는 **둘이 아니라 셋으로** 쪼개진다

## ① 두 탈출구는 비용이 다르다 (티켓의 예상대로, 근거는 더 무겁다)

| 탈출구 | 지우려면 스택에 무엇이 더 필요한가 | 판정 |
|---|---|---|
| `community.artist-service.enabled` | **iam(auth-service) + MySQL** 뿐. artist-service 는 이미 트리오에 있다 | ✅ 이 티켓 |
| `community.membership-service.enabled` | iam + **membership-service** + **ACTIVE 멤버십 행** | ❌ `TASK-FAN-INT-006` |

멤버십 쪽이 큰 이유는 **서비스 부재가 아니라 데이터**다. 가입 경로가
`POST /api/fan/memberships` + **빌링키(PortOne)** 를 거치므로(`BillingKeyController`),
게이트를 *통과*하는 e2e 케이스를 만들려면 결제 평면을 세우거나 DB 직접 시드를 도입해야
한다. 성질이 다른 일이라 묶지 않는다.

## ② 🔴 티켓이 몰랐던 세 번째 조각 — 공유 CI 재사용 워크플로

`.github/workflows/_platform-e2e.yml` 은 서비스 하나하나를 `$PROJECT_DIR/apps/$name/` 로
**하드코딩**한다(jar 복원 경로 · Dockerfile · 빌드 컨텍스트 · `-x …:bootJar` 전부). 다른
프로젝트의 서비스는 표현할 방법이 없다. 게다가 `upload-artifact@v4` 가 **최장 공통 접두사를
잘라내므로** 기존 fan 아티팩트에 iam jar 를 한 줄 얹으면 fan 3종의 복원 경로가 **전부
깨진다**(그리고 그건 경로 버그로 보고되지 않는다 — 이미지가 그냥 없다).
⇒ `TASK-MONO-523`(root) 신설, CLAUDE.md § Cross-Project Changes 대로 **한 PR 에 원자적으로**.

선례가 그 경계를 뒷받침한다 — `_platform-e2e.yml` 은 root 만 건드렸고(MONO-326/330/374),
`ci.yml` 의 *자기 잡 블록*은 프로젝트 티켓이 건드려 왔다(TASK-FAN-INT-001 이 fan 잡을 만듦).

## ③ 🔴🔴 CI 왕복을 아낀 세 개의 지뢰 (전부 코드 결함처럼 보였을 것)

1. **auth-service 의 도커 빌드 컨텍스트는 서비스 디렉터리가 아니라 프로젝트 루트다** —
   `COPY apps/auth-service/build/libs/`. 재사용 워크플로가 넘기던 컨텍스트로는 `COPY` 실패
2. **auth-service ENTRYPOINT 가 `getent hosts mysql/kafka/redis` 로 DNS 를 하드코딩해
   블로킹한다**(TASK-BE-048, Hikari/Flyway 가 부정 캐시를 물지 않게 하려고 존재). 트리오
   별칭은 `fan-e2e-*` 라 그대로 두면 **영원히 기동 대기**하고 증상은 평범한 타임아웃이다 —
   Spring 로그가 한 줄도 없다. ⇒ 백킹 컨테이너에 **두 번째 별칭**(`mysql`/`kafka`/`redis`)
3. **프로덕션 auth-service 이미지에는 서명 키가 없다** — `auth.jwt.*-key-path` 가
   `classpath:keys/*.pem` 인데 체크인된 것은 `.example` 뿐이고 실 dev 키는
   `src/test/resources/keys` 에 있어 `bootJar` 가 포장하지 않는다 ⇒ 마운트 필요
   (iam 자신의 `docker-compose.e2e.yml` 도 같은 처리 — TASK-MONO-082 cycle 3)

## ④ 두 토큰 평면은 **설정만으로 분리 가능**했다 — 다만 artist 쪽에 구멍이 있었다

`ServiceLevelOAuth2Config` 는 `/internal/**` 디코더를 `fanplatform.internal.jwt.*` 에서
읽는다 ⇒ 엔드유저 토큰은 목 JWKS, 워크로드 토큰은 실 iam 으로 **갈라놓을 수 있다**(JWKS
병합 같은 꼼수 불필요). 🔴 그런데 artist-service 는 그 두 키를 **yml 에 선언하지 않아**
형제인 membership-service 가 문서화한 env(`INTERNAL_JWT_JWK_SET_URI` / `INTERNAL_JWT_ISSUER`)
가 **아무것에도 닿지 않았다** — 이 서비스는 애초에 다른 내부 issuer 로 가리킬 수가 없었다.
형제와 같은 모양으로 선언을 추가했다(sibling parity).

## ⑤ 🔴 게이트를 끄고 있어서 **틀린 값이 무해하게 앉아 있었다**

community 의 `ARTIST_SERVICE_BASE_URL` 기본값은 `http://artist-service:8080` 인데 트리오의
별칭은 `fan-e2e-artist` 다. 게이트가 꺼져 있는 동안 아무도 그 URL 을 다이얼하지 않아 드러나지
않았고, 켜는 순간 **모든 팔로우가 fail-closed 로 거부**됐을 값이다. e2e 에서 명시 설정했다.
🔵 같은 함정이 membership 쪽 `MEMBERSHIP_SERVICE_BASE_URL` 에도 그대로 있다 — INT-006 에 기재.

## ⑥ 🔴🔴 첫 CI 런이 잡은 것 — **탈출구가 e2e 자신의 낡은 전제를 가리고 있었다**

첫 실행에서 `ArtistAndPostFlowE2ETest` 의 팔로우가 **422** 로 떨어졌다. 배선 결함이 아니라
**게이트가 옳게 동작한 것**이다.

테스트 원문(주석 포함):

```java
String artistAccountId = randomAccountId(); // followed via this id
// v1 has no enforcement that artistAccountId resolves to a real artist
// account on artist-service ... there's no cross-service join in the v1 follow path
```

⇒ 이 테스트는 **1단계에서 등록한 아티스트가 아니라 합성 UUID 를 팔로우**하고 있었다.
그 전제는 `TASK-FAN-BE-045` 가 **정확히 그 조인을 추가하면서 무효**가 됐는데, 같은 티켓이
같은 스위트에 탈출구를 켜 둔 탓에 **낡은 주석이 계속 참인 것처럼 보였다.** 탈출구를 지운
첫 실행이 그것을 드러냈다.

🔵 **판정을 로그의 부재로 확정했다**(대리지표 아님): `HttpArtistAccountChecker` 는 **어떤
예외에도** WARN 을 남기는데 잡 로그 1,861줄에서 그 WARN 이 **0건**이다 ⇒ 토큰 취득 ·
`/internal/**` 인증 · HTTP 왕복이 **전부 성공**했고 artist-service 가 `200 {exists:false}` 를
돌려준 것이다. **즉 AC-1 은 이 실패 런에서 이미 증명됐다** — 실패한 것은 대상 선택이지
배선이 아니다.

**수정 두 가지**:
1. 등록한 아티스트의 `accountId` 를 팔로우한다 ⇒ 이 단언이 이제 **크로스서비스 조인을 증명**한다
2. 🔴 **거부 케이스를 함께 넣었다** — 통과만 단언하면 "동작하는 게이트" 와 "허용 checker" 를
   구별할 수 없고, 그게 방금까지 이 스위트가 있던 상태다. 같은 경로·같은 토큰·대상만 다르게
   해서 **미등록 계정 → 422 `UNKNOWN_ARTIST_ACCOUNT`** 를 단언한다

🔵 형제 점검: 팔로우를 수행하는 e2e 는 이 클래스 하나뿐(`VisibilityTierE2ETest` ·
`MultiTenantIsolationE2ETest` 은 0건) — 낙오 없음.

---

# AC-3 bite 결과 — 속성 기반 케이스만으로는 **못 잡는다** (실측)

`ArtistAccountCheckerConfig` 에 **다른 키**(`community.artist-service.bypass`) 뒤로 숨은
허용 빈을 주입하고 `--rerun-tasks` 로 강제 재실행:

```
주입 후: tests="8" skipped="0" failures="1"   ← 구조 단언 1건만 RED
         속성 기반 7건은 전부 GREEN            ← 그래서 구조 단언이 필요하다
복원 후: tests="8" skipped="0" failures="0"
```

⇒ 신설한 *"이 설정 클래스는 `ArtistAccountChecker` `@Bean` 메서드를 정확히 하나만 선언한다"*
가 없었다면, 다른 키로 되돌아온 탈출구는 **기존 감사를 전부 통과**했을 것이다.
🔴 `--rerun-tasks` 를 쓴 이유도 같은 규율이다 — 강제 없이 물리면 빌드가 건너뛴 초록을
"물었다" 로 오독할 수 있다.

---

# AC-4 — 벽시계

**before (기준선)** — `origin/main` 최근 3회 `E2E (fan-platform v1 live-trio smoke)` 잡:

| 실행 | 벽시계 |
|---|---|
| 2026-08-11 21:12:34→21:15:21 | 2m47s |
| 2026-08-11 15:59:49→16:02:52 | 3m03s |
| 2026-08-11 12:05:38→12:07:52 | 2m14s |

평균 ≈ **2m41s** (n=3), 잡 타임아웃 10m.

🔴 이 숫자가 **무엇을 잰 것인지** 명시한다 — **잡 전체**의 벽시계다(체크아웃 + JDK 셋업 +
도커 이미지 빌드 + 스위트). 컨테이너 기동만의 시간이 아니고, after 도 같은 축으로 잰다.
🔴 그리고 n=3 은 표본이지 상수가 아니다(2m14s ~ 3m03s 로 이미 40% 흔들린다) — after 를
단일 실행 하나로 "성질" 처럼 말하지 않는다.

타임아웃은 10m → **14m**. 이건 기대치가 아니라 **상한**이다 — MySQL 첫 부팅과 Flyway 가
iam 마이그레이션 전량을 재생하는 시간을 흡수하기 위한 것이고, 실제 델타는 아래가 말한다.

**after** — (이 PR 의 CI 에서 채움)

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

- [x] AC-0 실측 + 범위 확정(줄였다면 사유) — artist 하나로 축소, `TASK-FAN-INT-006` +
      `TASK-MONO-523` 분리 기재
- [x] iam 이 스택에 들어가고 토큰이 실제로 발급됨 — 판정은 `fan-platform-e2e` GREEN
- [x] 탈출구 삭제 + grep 잔존 0건(살아 있는 스위치 기준)
- [x] 탈출구 부재를 단언하는 테스트 — 반전 2건 + 구조 단언 1건, **bite 로 물리는 것 확인**
- [ ] 벽시계 전후 기록 — before 완료, after 는 CI 후
- [ ] Ready for review

---

분석=Opus 5 / 구현 권장=**Opus** — 스택 구성 + 보안 게이트 삭제라 범위 판단이 계속 필요하다.
