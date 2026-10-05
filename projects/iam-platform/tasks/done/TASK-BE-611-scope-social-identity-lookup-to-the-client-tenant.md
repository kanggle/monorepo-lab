# Task ID

TASK-BE-611

# Status

done

# Title

소셜 신원 조회를 **시작 client 의 테넌트로 한정**한다 — 한 공급자 신원이 다른 테넌트 client 로 들어가 그 테넌트의 계정인 척하는 것을 끝낸다 (`TASK-BE-605` 결정 ② (iii) 의 구현)

# Owner

iam-platform

# Task Tags

- auth-service
- social-login
- multi-tenant

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 조회 한정 · 한정 미스 시 가입 · 기존 교차 신원의 처분이 스펙 규칙(«하나의 provider_user_id 는 하나의 계정에만 연결»)과 부딪친다.

---

# Goal

소유자 결정(2026-09-26 UTC, `TASK-BE-605` § AC-0 ②) = **(iii) 소셜 신원 조회를 client 테넌트로 한정 — 모집단 먼저.** 모집단 측정은 `TASK-MONO-672` 항목 18 이 들었고, 그 항목이 이 티켓의 기안 의무를 가졌다.

**측정 (16차 AMI 창, 2026-09-26 UTC — `TASK-MONO-672` § 16차 창 수확)**
- 모집단: `social_identities` = **0**(측정 창 시작 시) ⇒ 데모에 실사용자 소셜 신원 없음 — «데모에 없다» 이지 «운영에 없다» 가 아니다.
- **구조적 재현(판정의 실질 입력)**: Kakao 스텁으로 한 신원(`provider_user_id=60218261625`)을 **스토어 client → 팬 client** 순서로 소셜 로그인 — 둘 다 코드 발급.
  결과 계정 **1개**(`ecommerce`) · 신원 **1행**(`ecommerce`). ⇒ 팬 client 로의 로그인이 **스토어 테넌트 계정**으로 들어간다(전역 조회 `findByProviderAndProviderUserId` — `OAuthLoginUseCase.java:250` · `SocialLoginSteps.java:47`).
  세션 테넌트는 시작 client(`fan-platform`)로 찍힌다(`SocialLoginBrowserController.java:185`) ⇒ fan-platform 세션에 ecommerce 계정.
- 스펙과 저장소는 이미 테넌트별이다: `multi-tenancy.md` § 적용 범위(`social_identities`) · `V0007__add_tenant_id_to_auth_tables.sql:49` unique `(tenant_id, provider, provider_user_id)`.

# Scope

## In Scope

- 조회를 `(tenant_id = 시작 client 테넌트, provider, provider_user_id)` 로 한정.
- 한정 미스 = **그 테넌트에서 가입**(폼 로그인의 BE-604 결과와 같은 모양 — 테넌트마다 한 계정).
- `TASK-BE-602` 후속 ① 의 경쟁 조건(서로 다른 테넌트 동시 첫 로그인 → 전역 조회가 두 행 → 영구 `IncorrectResultSizeDataAccessException`)이 한정으로 **사라지는지** 판정(테넌트별 조회는 유니크 키와 같은 모양이다).
- **AC-0 (🔴 소유자 결정)**: ① `oauth-social-login.md` Business Rules «하나의 provider_user_id 는 하나의 계정에만 연결» 을 «테넌트마다 하나» 로 개정할지 ② 기존 교차 신원(다른 테넌트 client 로 이미 들어오던 사람)의 처분 — 그대로 두면 다음 로그인에 그 테넌트 새 계정이 생긴다(데모 모집단 0, 운영 모집단 미상).

## Out of Scope

- 발급 토큰의 `tenant_id` 의미(ADR-006 옵션 1 유지).

# Acceptance Criteria

- [x] **AC-0** — 위 ①② 결정 + 스펙 개정(스펙 먼저). → § AC-0 소유자 결정 (2026-09-26 UTC) 참조.
- [x] **AC-1** — 구현 + 단위/IT (→ § 구현 결과 2026-09-29 UTC): 같은 신원으로 스토어 → 팬 = **계정 둘**(테넌트마다) · 같은 client 재로그인 = 같은 계정 · BE-507 이전 계정(신원 = ecommerce, 계정 = fan-platform)의 동작을 AC-0 ② 대로.
- [ ] **AC-2** — 🔴 **창 판정**: 위 구조적 재현을 다시 돌려 계정 2 · 신원 2(테넌트별)로 바뀌는지. 절차 = `TASK-MONO-672` § 16차 창 수확의 스텁 방식(`TASK-BE-602` § CORRECTION 2026-09-26 의 재생성 함정 셋 포함).

# Related Specs

- `specs/features/multi-tenancy.md` § 소셜 로그인 · `specs/features/oauth-social-login.md` § tenant 귀속 규칙 · Business Rules
- `TASK-BE-605` § AC-0 ② · `TASK-BE-602` § 후속 ① · `TASK-MONO-672` 항목 18

# Related Contracts

- 없음(이벤트 `tenantId` 는 BE-602 대로 계정의 실제 테넌트).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 같은 이메일의 폼 자격이 그 테넌트에 이미 있다 | AC-0 에서 정한다(연결 vs 별도 계정) — 🔴 자동 연결은 계정 탈취 경로가 될 수 있다 |
| 콘솔 client(`iam`) 로 소셜 | 콘솔은 소셜 대상이 아니다(현행 확인) |

# Failure Scenarios

1. **스펙 개정 없이 코드만 한정** → 스펙 규칙과 코드가 다시 갈린다.
2. **데모 모집단 0 을 «영향 없음» 으로 읽는다** → 운영 모집단은 미상이다.

---

## AC-0 소유자 결정 (2026-09-26 UTC)

**①** `oauth-social-login.md` Business Rules 의 «하나의 provider_user_id 는 하나의 계정에만 연결» 을 **«테넌트마다 하나»** 로 개정한다
(스펙 먼저 — 구현 전에 이 규칙을 고친다).

**②** 기존 교차 신원(다른 테넌트 client 로 이미 들어오던 사람)은 **이관하지 않는다.** 한정이 적용된 뒤 그 테넌트에서의 다음 로그인은 **새 계정**을
만든다 — 데모 모집단은 0(항목 18 § 유효성 술어)으로 측정됐다.

**기각**: 같은 이메일 계정으로 자동 연결 — 계정 탈취 경로가 될 수 있어 채택하지 않는다. 결정 보류도 기각 — 모집단이 이미 측정됐고(0, 구조적
재현으로 결함 모양 확인) 더 기다려도 새 정보가 없다.

---

## 구현 결과 (2026-09-29 UTC)

**스펙 먼저 (AC-0 ①)**
- `oauth-social-login.md` — Business Rules «하나의 provider_user_id 는 하나의 계정에만» → **«테넌트마다 하나»**(unique `(tenant_id, provider, provider_user_id)`).
  § 계정 연결 전략 1·3a · User Flow 5.d 의 조회 키에 `tenant_id` · § tenant 귀속 규칙의 «알려진 불일치» 를 규칙 문장으로 교체 · Edge Cases 에
  «신원이 다른 테넌트에만 있다» 한 줄.
- `multi-tenancy.md` § 소셜 로그인 — «구현은 측정 뒤» 절을 규칙 · BE-611 이전 동작 · 기존 교차 신원 처분(AC-0 ②)으로 재작성.

**코드 (auth-service)**
- `SocialIdentityRepository` — 전역 `findByProviderAndProviderUserId` 를 **없애고** `findByTenantIdAndProviderAndProviderUserId` 로 바꿨다(JPA ·
  구현체 동일). 테넌트 없는 조회 메서드가 남아 있으면 다음 사람이 다시 쓴다 — `multi-tenancy.md` § 격리 회귀 방지(«`tenant_id` 없는 조회 금지»)와도 맞는다.
- 조회 두 곳 모두 시작 client 테넌트로: `OAuthLoginUseCase.resolveSocialLogin`(계정 해소) · `SocialLoginSteps.upsertIdentity`(행 upsert) — 같은
  테넌트라서 해소에 쓴 행이 곧 upsert 되는 행이다. 미스 → 기존 `socialSignup(…, tenantId)` 가 그 테넌트에서 가입(BE-507 이 이미 테넌트를 보냄).
- 발급 토큰 · 세션 테넌트 · 이벤트 테넌트는 손대지 않았다(Out of Scope · BE-602 그대로).

**`TASK-BE-602` 후속 ① (서로 다른 테넌트 동시 첫 로그인 → 전역 조회 두 행 → 영구 `IncorrectResultSizeDataAccessException`) — 사라진다.**
조회 키가 unique 인덱스 `uk_social_tenant_provider_user`(V0007:49) 와 같은 컬럼이라 결과가 최대 한 행이다. 두 테넌트에 행이 있는 상태는 이제
정상 상태이고(계정 둘), 슬라이스 셀 `findByTenant_sameIdentityInTwoTenants_eachLookupReturnsItsOwnRow` 가 그 모양을 잰다.

**테스트**
| 셀 | 무엇을 |
|---|---|
| `OAuthLoginUseCaseSocialTenantTest.identityOnlyInAnotherTenant_signsUpInTheClientsTenant` | 스토어 신원이 있어도 팬 client 는 팬 테넌트에서 가입 · 저장 행 테넌트=팬 · 스토어 테넌트는 **묻지도 않음** |
| `…sameClientRelogin_resolvesTheSameAccount_noSignup` | 같은 client 재로그인 = 같은 계정 · 가입 호출 없음 |
| `…preBe507Account_fromTheFanClient_isNotMigrated_fanRowIsAdded` | AC-0 ② — 이관 없음 · 팬 client 로 오면 팬 신원 행이 추가된다 |
| `SocialIdentityJpaRepositoryTest` (실 MySQL, 5셀 — 3 개명 + 2 신규) | 다른 테넌트 행은 안 보임 · 두 테넌트 행이 공존하면 각 조회가 자기 행 하나 |

- 로컬: auth-service `test` 전체 **rc=0**. 대상 5클래스 XML — SocialTenant 13 · UseCase 17 · BrowserController 12 · StatusRule 7 · 실패 0.
  🔴 `SocialIdentityJpaRepositoryTest` 7셀은 로컬 Docker 없음으로 **skipped** — 판정은 CI(Docker 러너).
- bite ①: `OAuthLoginUseCase` 조회 테넌트를 `"ecommerce"` 고정 → 9 실패, BE-611 셀 둘 포함(나머지는 픽스처 테넌트가 `fan-platform` 인 기존 셀 —
  같은 결함에 걸린 것). bite ②: `SocialLoginSteps` 조회 테넌트를 `"ecommerce"` 고정 → SocialTenant 13 중 1 실패(`identityOnlyInAnotherTenant…`).
  두 파일 모두 scratchpad 백업으로 복원, md5 일치.

### 🔴 AC-0 ② 문장의 전제 정정 — «다음 로그인은 **새 계정**» 은 그 테넌트에 같은 이메일 계정이 **없을 때만** 참이다

한정 미스의 가입은 account-service `SocialSignupUseCase` 가 한다 — **그 테넌트 안에서** 이메일로 기존 계정을 찾아 있으면 돌려준다(`findByEmail(tenantId, …)`,
스펙 § 계정 연결 전략 3a 의 auto-link — BE-611 이전부터 있던 규칙). 그래서:
- 그 테넌트에 같은 이메일 계정이 없다 → 새 계정(결정문 그대로).
- 있다(예: 같은 이메일로 그 테넌트에 폼 가입했다 · BE-507 이전 계정이 그 테넌트에 산다) → **그 계정에 연결**되고 새 신원 행만 생긴다.

이 연결은 결정문이 기각한 «같은 이메일 계정으로 자동 연결» 과 **다른 것**으로 읽었다 — 기각은 기존 교차 신원의 **처분(이관)** 선택지였고, 이 연결은
모든 첫 소셜 로그인에 이미 적용되는 테넌트 내부 규칙이다. 이 티켓은 그 규칙을 바꾸지 않는다. Edge Cases 첫 행(«같은 이메일의 폼 자격이 그 테넌트에
이미 있다 → AC-0 에서 정한다»)도 AC-0 이 직접 답하지 않았으므로 **현행 auto-link 유지**로 닫는다. 🔴 단, 결정문이 든 우려(자동 연결 = 탈취 경로)는
테넌트 내부 auto-link 에도 그대로 해당한다 — provider 가 검증하지 않은 이메일로 남의 폼 계정에 붙을 수 있는지는 이 티켓 밖의 물음이고, 필요하면
소유자가 별도 티켓으로 연다.

### AC-2 창 런북 (재굽기 뒤 — 이 PR 의 squash 이후 커밋으로)
1. 16차 창과 같은 Kakao 스텁 방식(`TASK-MONO-672` § 16차 창 수확 · `TASK-BE-602` § CORRECTION 2026-09-26 의 재생성 함정 셋). 🔴 16차 창이 남긴 행
   (`provider_user_id=60218261625`, `ecommerce` 신원 1행)이 볼륨에 남아 있을 수 있다 — **새 `provider_user_id` · 새 이메일**로 시작하라.
2. 같은 신원으로 스토어 client → 팬 client 순서로 소셜 로그인 — 둘 다 코드 발급.
3. 판정: `auth_db.social_identities WHERE provider_user_id=<새 값>` = **2행**(`ecommerce` · `fan-platform`, `account_id` 서로 다름) ·
   `account_db.accounts WHERE email=<새 이메일>` = **2행**(테넌트별). BE-611 이전 값 = 1행 · 1행.
4. 대조: 같은 client(스토어)로 한 번 더 → 행 수 변화 없음(재로그인 = 같은 계정).

---

## CORRECTION (2026-10-02 UTC) — 18차 창에서 AC-2 는 재지 못했다 (review 유지)

창: 18차 AMI `ami-03fa427e858219e47`(RepoCommit `1feb9fc6d` — AMI 태그·Lambda `AMI_REPO_COMMIT`·`check-ami-generation.sh --with-aws` rc=0 세 곳 일치), 인스턴스 `i-05395a5a7baa23bb8`, 2026-10-02 09:16–10:19 UTC. 측정 대상 변경은 전부 `1feb9fc6d` 의 조상(이미지 시각 ≥ 머지 시각). 브라우저 측정 증거 = 세션 스크래치 `live18/`(스크린샷·로그), 인스턴스 측정 = SSM 읽기 + 일회용 계정 쓰기.

- 이 창은 재굽기 대기분 일괄 측정이었고, AC-2 의 소셜 스텁 재현(`TASK-MONO-672` § 16차 방식)은 돌리지 않았다 — 데모의 소셜 키가 가짜 기본값이라 실제 제공자 경로는 없고, 스텁 재현은 인스턴스 안 쓰기 절차가 길어 다음 창으로 미룬다.
- 🔵 관련 사실: `TASK-BE-617`(소셜을 풀로)은 보류, `TASK-BE-620` 이 «풀 계정 이메일로의 사이트 소셜 가입 거절» 을 넣었다 — 다음 창의 재현은 그 거절과 겹치지 않는 이메일로 한다.

---

## CORRECTION (2026-10-05 UTC) — 20차 창(2026-10-04 UTC · i-0c4859442f56d70e0 · ami-0d78d476824493d77 · f0927bcd0)에서도 AC-2 는 재지 않았다 (review 유지)

- 20차 창은 `TASK-MONO-697` · `758` · `759` · `TASK-FAN-BE-050` · `TASK-PC-FE-304` · `306` 판정에 썼고, AC-2 의 소셜 스텁 재현은 **미뤘다**. AC-2 는 여전히 `[ ]` 다. 위 § AC-2 창 런북과 18차 절의 재현 조건(스텁 방식 · 새 `provider_user_id`·이메일 · BE-620 거절과 겹치지 않는 이메일)은 그대로다.

---

## CORRECTION (2026-10-05 UTC) — 21차 창 판정 (i-0aa3180ae21de4445 · ami-0a7b20c97325be01d · 678b6d003) — AC-2 닫힘

> 분석=Opus 5.5. 덧붙이기만 한다. 전부 SSM 으로 했다(소유자 조작 없음). 16차 방식: 호스트 python Kakao 스텁(`172.20.0.1:18611` — iam 네트워크 게이트웨이) + auth-service 를 KAKAO URI 오버라이드로 재생성.

**재생성 안전장치**(`TASK-BE-602` § 재생성 함정): `DEMO_DOMAIN` 을 먼저 정하고 `infra/demo/demo.env` 를 읽은 뒤 `docker compose -p iam`(라벨의 4 파일 + 임시 오버라이드)로 `auth-service` 만. 재생성 **전** 환경 변수를 저장해 **후**와 비교했다 — 08:23:33Z: 차이 = `OAUTH_KAKAO_*` 5개 추가뿐 · 다른 값 변화 0 · 발급자 `https://auth.hubwang.com` · healthy. 허용 리다이렉트에 `https://auth.hubwang.com/login/oauth/kakao/callback` 을 더했다(BE-602 함정 ②).

**재현** — 새 `provider_user_id=611188613` · 새 이메일 `be611-082333@ex.io`(16차 `60218261625` 와 겹치지 않음). 쿠키 유지 curl + PKCE 로 authorize → `/login/oauth/kakao` 302(state) → 콜백 `?code=any&state=` → 저장된 authorize 재개 → client redirect_uri 로 **코드 발급**.

| 단계 (08:24:42Z) | `auth_db.social_identities` (provider_user_id) | `account_db.accounts` (email) |
|---|---|---|
| 시작 전 | 0 | 0 |
| 스토어 client 로그인 → 코드 발급 | 1 — `ecommerce` · `03242853-…` | 1 — `ecommerce` · ACTIVE |
| 팬 client 로그인 → 코드 발급 | **2** — `ecommerce` · `fan-platform`(`720df7bd-…`) — account_id **서로 다름** | **2** — 테넌트별 |
| 대조: 스토어 client 재로그인 → 코드 발급 | 2 (변화 없음 = 같은 계정) | 2 (변화 없음) |

- 바인딩 확인: 스텁 접근 로그 `POST /oauth/token` 3 · `GET /v2/user/me` 4(1건은 기동 확인 요청). BE-611 이전(16차 항목 18) 값은 1행 · 1행(팬 로그인이 스토어 계정으로 들어갔다).

**원상복구**(08:25:20Z) — 오버라이드 없이 같은 명령으로 재생성 → 환경 변수가 **재생성 전 스냅샷과 완전히 같다**(`cmp`) · 발급자 `https://auth.hubwang.com` · `OAUTH_KAKAO_*` 0 · 스텁 프로세스 0 · 임시 파일 삭제 · `/login` 200. 08:26:03Z 데모 계정 스토어·팬 토큰 발급 정상.

### 4차원 (close chore)

| 차원 | 결과 |
|---|---|
| (a) `gh pr view 4061` | `state=MERGED` · mergedAt 2026-09-29T07:45:36Z · mergeCommit `fa3940bf7` |
| (b) origin/main 조상 | 참 · 21차 AMI 커밋 `678b6d003` 의 조상 |
| (c) 머지 시점 실패 체크 | 67 중 **FAILURE 0** |
| (d) `# Acceptance Criteria` | AC-2 외 `[x]` · **AC-2 = 이 절에서 닫힘** (동사 «계정 2 · 신원 2(테넌트별)로 바뀌는지» = 위 표 · 대조군 포함) |

⇒ **`review/` → `done/`.**
