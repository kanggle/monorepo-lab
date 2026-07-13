# Task ID

TASK-MONO-394

# Title

`iam-platform` 의 `community-service` · `membership-service` 를 **은퇴시킨다** — 이들은 `PROJECT.md` 가 선언한 **FROZEN 전시물**이고, `fan-platform` 이 같은 도메인을 **실제로 배포**하면서 전시물은 중복이 되었다. 덤으로 **보안 정책이 운영본과 반대로 갈라져 있다**

# Status

in-progress

# Owner

monorepo

# Task Tags

- decision
- cleanup
- security
- adr-followup

---

# Dependency Markers

- **발굴**: `TASK-MONO-392`(D5-8) 수행 중. [`ADR-MONO-049` § 1.12](../../docs/adr/ADR-MONO-049-framework-neutral-security-library.md) 에 관찰만 기록하고 **고치지 않았다** — 행동 변경이고, 추출은 행동을 바꾸지 않기 때문이다.
- **선행 (머지됨)**: `TASK-MONO-392`(D5 완결, 사본 49 → 0).
- **연관**: `TASK-MONO-179`(`global-account-platform` → `iam-platform` 개명) — 이 서비스들의 출신을 설명한다.
- **선례**: `TASK-BE-299` (`admin-web` RETIRED 2026-05-18, [`ADR-MONO-013`](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) § D4) — **은퇴의 확립된 형태**: 서비스 맵에서 행을 지우지 않고 `~~취소선~~` + RETIRED 로 남긴다.
- **후속**: 없음(이 task 가 결정하고 실행한다).

---

# ⚠️ 이 티켓의 **초안 실측은 틀렸다** (2026-07-14 재측정으로 정정)

**착수 시 재측정이 초안의 5개 실측 중 3개를 반증했다.** 초안을 그대로 믿었다면 잘못된 전제 위에서 작업했을 것이다. **틀렸던 주장을 지우지 않고 남긴다** — 이 티켓이 어떻게 틀렸는지가 이 티켓의 교훈이기 때문이다.

| 초안 주장 | 재측정 | 판정 |
|---|---|---|
| *"`iam-platform/docker-compose.yml` 이 auth·account·security·admin·gateway 5개를 띄운다"* | 그 파일은 **인프라 전용**(mysql·redis·kafka·prometheus…). 앱 5개는 **`docker-compose.e2e.yml`** 에 있다 | ❌ **파일을 잘못 지목** |
| *"참조하는 곳은 `settings.gradle` 과 `ci.yml` 뿐"* | **스펙 16개 + `PROJECT.md` + `docs/project-overview.md` + iam `ADR-001` + `docker/mysql/init.sh` + `docker-compose.yml` + 드리프트 가드 + CI 4줄** | ❌ **대폭 과소** |
| *"스윕하는 사람은 이게 죽은 코드인 줄 몰랐다"* | 저장소는 **정확히 알고 있고 6곳에 `FROZEN` 이라 적어놨다**. `check-service-map-drift.sh` 는 그 면제를 **가드에 인코딩**까지 해뒀다 | ❌ **거짓** |

**근본 원인은 익숙한 것이다**: 초안을 쓴 세션이 **자기 앞 단계(`MONO-392`)의 관찰을 출처로 삼고 모집단을 다시 세지 않았다.** *선행 문서의 숫자는 출처가 아니라 가설이다. 착수 = 재측정.*

---

# 실측 (정정본 — 2026-07-14, `origin/main` `db82d7b6b`)

## 실측 1 — 배포되지 않는다 ✅ (결론 유지, 근거 정정)

**탐지식 자기검증부터**: 같은 술어가 `auth-service` 를 **찾아내야** 한다(빈 결과 = 부재가 아니다).

| compose | 띄우는 앱 |
|---|---|
| `iam-platform/docker-compose.yml` | **없음** — mysql·redis·kafka·prometheus 등 **인프라 전용** |
| `iam-platform/docker-compose.bootrun.yml` | 없음 (mysql·redis·kafka) |
| **`iam-platform/docker-compose.e2e.yml`** | `auth` · `account` · `security` · `admin` · `gateway` — **5개. community/membership 없음** ← *술어 자기검증 통과* |
| `fan-platform/docker-compose.yml` | `image: fan-platform/community-service:local` · `membership-service:local`, **build context = `./apps/…` (fan 자기 소스)** |

- **저장소 전체 compose 25개 중 이 둘의 iam 사본을 띄우는 것: 0**
- **iam 게이트웨이 라우트: 0**
- `platform-console/docker-compose.e2e.yml` 의 언급은 **주석 한 줄**("not federated by the console v1.")

## 실측 2 — `prometheus.yml` 이 거짓말을 한다 ✅ (유지)

`iam-platform/infra/prometheus/prometheus.yml` 이 `community-service`(`:8086`) · `membership-service`(`:8087`) 를 **여전히 스크레이프한다** — **자기 compose 가 띄우지도 않는 서비스를.** 선언↔진실 드리프트.

## 실측 3 — 🔴 이들은 "발견된 죽은 코드" 가 아니라 **"선언된 FROZEN 전시물"** 이다 (초안이 완전히 놓친 것)

**6곳이 이미 말하고 있었다:**

| 위치 | 문구 |
|---|---|
| `iam-platform/PROJECT.md` § Product-layer services (frozen) | **"FROZEN — 플랫폼의 product-layer demo consumer. 신규 기능 태스크 발행 금지"** |
| `iam-platform/PROJECT.md` L89 | **"포트폴리오의 integration 예시 역할만 수행한다"** ← **존재 목적** |
| `docs/project-overview.md` L49 | **"service map (5 active + 2 frozen demo)"** |
| `specs/services/{community,membership}-service/overview.md` L3 | **`Status: FROZEN`** — *"리뷰에서 생성된 fix 태스크만 수용한다"* |
| `iam-platform/docs/adr/ADR-001` L46·L244 | **"frozen demo"** |
| `scripts/check-service-map-drift.sh` L36 | **가드가 FROZEN 면제를 인코딩** |

⇒ **이 티켓의 질문은 "모르는 죽은 코드를 지울까?" 가 아니라 "선언된 전시물이 아직 값을 하는가?" 였다.**

## 실측 4 — 🔴 보안 정책이 운영본과 반대다 ✅ (유지 — 그리고 **`MONO-392` 가 만든 게 아니다**)

| | 핀 테넌트 | SUPER_ADMIN `*` | entitlement |
|---|---|---|---|
| **fan** community · membership (**배포됨**) | `fan-platform` | ✅ **허용** | ✗ |
| **iam** community · membership (**FROZEN**) | `fan-platform` | ❌ **거부** | ✗ |

**`MONO-392` 의 "행동 불변" 주장을 검증했다** — pre-392 커밋(`82a5859e5`)의 손수 작성 `TenantClaimValidator` 를 꺼내 읽으니 **순수 equality**(`!expectedTenantId.equals(tenantId) → failure`), **와일드카드 분기 없음**. ⇒ **거부는 원래부터 있었고 `MONO-392` 는 정확히 보존했다.**

**드리프트는 fan 부트스트랩(2026-05-03, `ba916c9d3`)에서 태어났다**: fan 은 GAP 사본에서 갈라져 나오면서 **와일드카드를 새로 넣었고**, GAP/iam 원본은 끝내 받지 못했다.

## 실측 5 — **AC-2 (D1 출신)**: fan 은 GAP 포크가 맞다, 그러나 **이미 독립체다**

**탐지식 자기검증**: 같은 copy-detection 을 **알려진 순수 rename**(gap→iam, `a8515772b`)에 먼저 돌려 **R 1923건** 확인 → 탐지식이 rename/copy 를 볼 수 있음을 증명한 뒤 측정.

| | 측정 |
|---|---|
| fan 부트스트랩(`ba916c9d3`)의 copy 출처 | **`global-account-platform` 30건** (+ fan 자체 9, wms 3) |
| 그 copy 들의 유사도 | **C058 ~ C078 (58~78%)** — **verbatim 사본이 아니라 처음부터 상당히 다시 쓰였다** |
| 오늘 byte-identical 인 동명 파일 | **0** |
| 커밋 수 | fan **24** vs iam **6** |
| 패키지 | `com.example.fanplatform.community` vs `com.example.community` |
| fan 만 가진 것 | `TenantClaimEnforcer` · `ActorContextResolver` · `WorkloadIdentityAuthoritiesConverter` |

⇒ **iam 사본을 지워도 fan 은 아무것도 잃지 않는다.**

## 실측 6 — **AC-5 (얽힘)**: 깨끗하다, 단 하나 예외

| 축 | 결과 |
|---|---|
| **DB** | `community_db` · `membership_db` = iam MySQL 안에서 **자기 Flyway 로 자족**(6 + 7 마이그레이션). 다른 서비스가 읽지 않음 ✅ |
| **Kafka** | iam = **무버전** 토픽(`community.post.published`, `membership.subscription.activated`…) / fan = **`.v1`·`.v2`**. **이름도 클러스터도 다르고, 살아있는 소비자 0** ✅ |
| **살아있는 iam 서비스 5개** | 소스 참조 **0** ✅ |
| **계약** | iam 의 community/membership 계약을 **소비하는 살아있는 서비스 0** ✅ |
| ⚠️ **`auth-service` V0009** | `V0009__seed_community_membership_oauth_clients.sql` 이 이들의 OAuth 클라이언트 + `membership.read` 스코프를 시드. **Flyway 마이그레이션은 불변 — 삭제 금지.** 은퇴 후 **고아 클라이언트 행**으로 남는다(무해). |

---

# 🔴 결정 (사람, 2026-07-14) — **(A) 은퇴시킨다**

**선택: (A).** 근거:

1. **전시물의 목적이 이미 더 잘 수행되고 있다.** `PROJECT.md` 는 이들이 *"product-layer 소비자가 account-service 내부 API 를 호출하는 포트폴리오 예시"* 로 존재한다고 말한다. **`fan-platform` 이 정확히 그것을 실제 배포로 하고 있다.** ⇒ 전시물은 **중복**이다.
2. **반대로 갈라진 보안 정책을 든 채로 얼어 있는 코드는 그냥 얼어 있는 코드보다 나쁘다** — 누가 언젠가 되살리면 어느 쪽이 옳은지 알 수 없다.
3. 모든 플랫폼-전역 스윕이 이들을 끌고 다닌다(`BE-454/455` outbox v2, `MONO-392` D5-8).

**기각된 대안**:

- **(B) 유지 + 정책 정렬** — FROZEN 규칙이 *"리뷰에서 생성된 fix 태스크는 수용"* 이라 **적법하긴 했다**. 그러나 중복 전시물의 정책을 고치는 것은 중복을 유지하는 비용을 계속 지불한다.
- **(C) 유지 + 발산 명문화** — **아무것도 지키지 않는다.** 배포되지 않는 서비스의 정책은 **관측될 수 없고, 관측할 수 없는 성질은 아무도 지키고 있지 않은 성질이다**(`MONO-355`·`387`·`392` 가 세 번 만난 실패 모드).

---

# Goal

`iam-platform` 의 `community-service` · `membership-service` 를 **`admin-web` 선례(`TASK-BE-299`)와 같은 형태로 은퇴**시키고, 은퇴가 남기는 **거짓 선언을 전부 진실로 되돌린다.**

# Scope

**포함:**

1. **소스·빌드 은퇴** — `settings.gradle` 모듈 2개 제거 + `apps/{community,membership}-service/` 소스 트리 삭제 + `ci.yml` 참조 4줄 제거.
2. **인프라 선언 정리** — `infra/prometheus/prometheus.yml` 스크레이프 잡 2개(**AC-4: 무조건**), `docker-compose.yml` 의 MySQL 비밀번호 env · Kafka 토픽 6개 · 주석, `docker/mysql/init.sh` 의 DB·유저 2쌍.
3. **스펙 은퇴** — `specs/services/{community,membership}-service/` + `specs/contracts/http/{community,membership}-api.md` + `specs/contracts/http/internal/community-to-{account,membership}.md` + `specs/contracts/events/{community,membership}-events.md`.
4. **선언 정정** — `PROJECT.md`(FROZEN 섹션 → RETIRED 기록), `docs/project-overview.md`(**`admin-web` 선례대로 `~~취소선~~` RETIRED 행으로 전환 — 행을 지우지 않는다**), `scripts/check-service-map-drift.sh` 의 FROZEN 면제 주석, `scripts/verify-template-readiness.sh` 의 정규식.
5. **기록** — `ADR-MONO-049` § 1.12 에 **답**을 적는다(AC-8). iam `ADR-001` 에는 **additive note**(ADR 은 불변 — 본문 재작성 금지).

**제외:**

- **`fan-platform` 은 한 글자도 건드리지 않는다.** 그쪽이 운영본이다.
- **fan 의 와일드카드 정책 변경 금지** — 별개 판단이고 이 task 의 질문이 아니다.
- **`auth-service` 의 Flyway `V0009` 삭제 금지** — 적용된 마이그레이션은 불변이다.
- `libs/java-messaging` 의 `OutboxMetricsAutoConfigurationTest` 는 서비스명→prefix 파생 규칙의 **문자열 파라미터**로 이 이름들을 쓴다. 의존이 아니라 테스트 데이터다 — **건드리지 않는다.**

---

# Acceptance Criteria

- **AC-1 (실측 재확인)** — ✅ **완료** (위 § 실측 정정본). 탐지식은 **아는 답에 먼저 돌려 자기검증**했다(compose 술어 → `auth-service` 검출 / copy-detection → 알려진 rename 에서 R 1923건).
- **AC-2 (D1 출신 확인)** — ✅ **완료**. fan 은 GAP 에서 파생됐으나 **이미 독립 진화**(byte-identical 0, 24 vs 6 커밋). **삭제해도 fan 이 잃는 것은 없다.**
- **AC-3 (사람 결정)** — ✅ **완료**. **(A) 은퇴** 선택됨(2026-07-14).
- **AC-4 (`prometheus.yml` 은 무조건 고친다)** — 결정과 무관하게, **자기 compose 가 띄우지 않는 서비스를 스크레이프하는 설정은 거짓말이다.**
- **AC-5 (얽힘 확인)** — ✅ **완료** (실측 6). DB·Kafka·계약·살아있는 서비스 **전부 격리**. **`auth-service` V0009 는 불변이므로 유지**하고, 고아 클라이언트 행이 남는다는 사실을 기록한다.
- **AC-6 (참조 0 + 빌드 초록)** — `settings.gradle` · `ci.yml` · `docker-compose*` · `prometheus.yml` · `init.sh` · 스펙 · 드리프트 가드에서 **iam 사본 참조가 0** 이고 `./gradlew check` 가 통과한다. **모든 참조 grep 은 `iam-platform` 경로로 한정**할 것(**fan 쪽을 잡으면 운영본을 지운다**).
- **AC-7 (드리프트 가드가 실제로 통과)** — `bash scripts/check-service-map-drift.sh` **exit 0**. 이 가드는 서비스 맵↔`settings.gradle` **양방향**을 검사하므로 **은퇴가 절반만 되면 여기서 걸린다.** *가드가 RED 라고 가드를 고치지 말 것 — 가드가 옳으면 코드를 고친다.*
- **AC-8 (기록)** — `ADR-MONO-049` § 1.12 의 *"후속이 먼저 물어야 할 것"* 에 **답을 적는다**. 이 task 가 그 질문을 닫는다.
- **AC-9 (fan 무결성)** — `git diff --stat` 에 **`projects/fan-platform/` 경로가 단 한 줄도 없어야 한다.**

---

# Related Specs

- **`docs/adr/ADR-MONO-049-framework-neutral-security-library.md` § 1.12** — 이 발산을 기록하고 이 task 를 지목한 곳
- `docs/adr/ADR-MONO-049-...` § 1.7 — 6개 프로젝트 정책 표(**두 프로젝트를 각각 적었을 뿐 연결하지 않았다**)
- `docs/adr/ADR-MONO-013` § D4 — **parity-gated retirement**(`admin-web` 선례)
- `projects/iam-platform/PROJECT.md` § Product-layer services (frozen) — **은퇴 대상 선언**
- `projects/fan-platform/apps/{community,membership}-service/` — **운영본. 건드리지 말 것.**
- `TASK-MONO-179` — GAP → iam-platform 개명(출신 단서)

# Related Contracts

**은퇴 대상 계약**: `iam-platform/specs/contracts/http/{community,membership}-api.md` · `http/internal/community-to-{account,membership}.md` · `events/{community,membership}-events.md`.
**살아있는 소비자 0** (실측 6 이 확인). **`auth-service` V0009 가 시드한 OAuth 클라이언트만 불변으로 잔존**한다.

---

# Edge Cases

- **`fan-platform` 에 같은 이름의 서비스가 있다.** 경로를 한정하지 않고 grep/삭제하면 **운영본을 지운다.** Guard = AC-9.
- **`prometheus.yml` 이 살아 있는 척한다** — 초안 조사에서 "배포됨" 으로 오독할 뻔한 원인.
- **`TASK-BE-454/455`(outbox v2) 가 이 서비스들을 이미 손봤다** — "유지 중" 으로 오해할 근거가 된다. **스윕에 딸려 온 것이지 의도적 유지가 아니다.**
- **`auth-service` V0009 는 지우면 안 된다** — 적용된 Flyway 마이그레이션은 불변. 고아 행은 무해하다.
- **iam `ADR-001` 은 재작성하지 않는다** — ADR 은 불변 기록. **additive note** 만 붙인다.
- **`docs/project-overview.md` 의 행은 지우지 않는다** — `admin-web` 선례대로 **`~~취소선~~` + RETIRED**. 드리프트 가드의 reverse 검사가 그 형태를 면제한다.

# Failure Scenarios

- **`fan-platform` 쪽을 건드린다** → **운영 중인 서비스를 깨뜨린다.** Guard = AC-9(`git diff --stat` 에 fan 경로 0줄).
- **은퇴를 절반만 한다** → 서비스 맵 / `settings.gradle` / CI 가 서로 다른 이야기를 한다. Guard = **AC-7 드리프트 가드 exit 0**.
- **드리프트 가드가 RED 라고 가드를 고친다** → 가드가 옳다. **코드를 고칠 것.**
- **탐지식의 빈 출력을 "참조 없음" 으로 읽는다** → 이 저장소에서 **여러 번** 대가를 치른 함정. **이 티켓의 초안 자체가 그 대가다.** Guard = AC-1 의 자기검증.
- **`V0009` 를 지운다** → Flyway 체크섬 불일치로 **살아있는 auth-service 가 기동 실패**한다.

---

# Provenance

`TASK-MONO-392`(D5-8) 가 iam 의 두 servlet 서비스를 공유 보안 클래스로 옮기던 중 발굴. **정책 핀을 쓰려고 "이 서비스의 테넌트가 뭐지?" 를 물었더니 `fan-platform` 이었고, 그제서야 fan-platform 프로젝트에 같은 이름의 서비스가 있다는 게 눈에 들어왔다.**

**이 티켓의 진짜 교훈은 발굴이 아니라 초안이 틀렸다는 것이다.** 초안은 *"아무도 모르는 죽은 코드"* 라고 썼지만 저장소는 **6곳에서 `FROZEN` 이라고 말하고 있었고 가드까지 달아뒀다.** 초안을 쓴 세션은 **자기 앞 단계의 관찰을 출처로 삼고 모집단을 다시 세지 않았다.** ⇒ **선행 문서의 숫자는 출처가 아니라 가설이다. 착수 = 재측정.**

**그리고 정직하게: 이것은 보안 결함이 아니었다.** iam 쪽은 뜨지 않으니 뚫린 곳은 없다. **은퇴하는 이유는 전시물이 중복이 되었기 때문이고**(fan 이 같은 것을 실제로 한다), 덤으로 **거짓말 두 개**(prometheus 스크레이프, 반대로 갈라진 정책)가 함께 사라진다.

분석=Opus 4.8 / 구현 권장=**Opus** (파괴적 — 모듈·소스·스펙 삭제. **모든 술어를 `iam-platform` 로 한정**하고, **드리프트 가드 exit 0** 과 **fan diff 0줄** 을 통과 조건으로 삼을 것.)
