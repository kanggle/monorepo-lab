# Task ID

TASK-MONO-744

# Title

전역 소비자 계정 7단계 — 데모 시드 · 안내 문서 · 라이브 검증 (`ADR-MONO-078` A)

# Status

in-progress

# Owner

monorepo

# Task Tags

- demo
- docs
- verification

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 (시드·문서·측정 — 결정은 앞 단계가 했다)

---

# Dependency Markers

- **선행**: `TASK-BE-614` · `TASK-BE-615` · `TASK-BE-616` · `TASK-BE-618` — 전부 done (~~`TASK-BE-617`~~ — 2026-10-02 ⏳ 보류, 소유자 결정: 실제 소셜 키가 어디에도 없다 ⇒ 선행에서 뺀다. 데모 계정은 폼 로그인만 쓴다) (~~`TASK-MONO-743`~~ — 2026-10-02 보류, 아래 «743 보류 인계») (~~`TASK-MONO-745`~~ — 2026-10-02 구현 없이 닫힘: 셀러 계정은 기계 계정이라 풀로 옮기지 않는다 · `ADR-MONO-079` 로 흡수)

# Goal

데모에서 «한 번 로그인으로 팬 ↔ 스토어» 가 첫 화면부터 보이게 하고, 안내 문서가 새 동작을 말하게 하고, 라이브(재굽기 뒤)에서 그것을 잰다.

# Scope

## In Scope

- dev 시드: `demo@demo.com` 의 팬·스토어 계정을 풀 계정 하나로(R3) — `auth-service` `R__01_seed_demo_single_identity_credentials.sql` · `account-service` `R__05` · `infra/demo/seed/seed-fan.sh`(`DEMO_SUB`)
- `docs/guides/interview-demo-walkthrough.md` § 0 · 로그인 순서 안내 정리
- 데모 서버 재굽기 필요 기록 + 라이브 측정

## Out of Scope

- 인증 코드(앞 단계)

# Acceptance Criteria

- [x] **AC-1 (R3 — 구현자 기본값)** — 데모 계정은 시드에서 **미리 묶여** 있다. 소유자가 「데모도 묶기 흐름을 타게」 로 뒤집을 수 있다.
  → ✅ 2026-10-02 UTC (구성으로) — account `R__05` 가 `…ec01` 을 `consumer-pool` 계정 하나 + `fan-platform`·`ecommerce` ACTIVE 멤버십 둘로 시드. `DemoSeedCredentialTest#poolAccountIsPreJoinedToBothSites` 로컬 통과(bite: 멤버십 하나를 바꾸면 빨강). 실 MySQL 실행은 `DemoConsumerPoolSeedIntegrationTest`(CI). 뒤집기 = 멤버십 문 하나 삭제(R__05 머리).
- [ ] **AC-2** — 데모 계정의 팬 데이터(팔로우·멤버십)와 스토어 데이터(주문)가 같은 `sub` 로 보인다 — 시드 id 를 바꿨다면 `seed-fan.sh` · 이커머스 주문 시드가 같이 바뀌었다.
- [x] **AC-3** — 안내 문서가 «사이트마다 따로 로그인» 을 더 말하지 않고, 콘솔은 그대로 운영자 로그인임을 말한다.
  → ✅ 2026-10-02 UTC — `docs/guides/interview-demo-walkthrough.md` § 0 (커밋 `e6b2928f2`). 09-11 «먼저 연 표면» 절·§ 6 행은 측정 기록으로 두고 재굽기 전/후 주석만 붙였다.
- [ ] **AC-4** — 라이브(재굽기 뒤): 팬 로그인 → 스토어 이동 시 비밀번호 입력 없음(첫 방문이면 동의 화면). 측정 전 **이미지 시각 vs 머지 시각**을 대조한다(옛 이미지를 재면 무효).
- [ ] **AC-5** — 라이브: 스토어 쇼핑객 주문이 콘솔 `ecommerce` 전환에서 여전히 보인다(`ADR-MONO-078` § Verification).

# Related Specs

- `docs/adr/ADR-MONO-078-one-consumer-login-across-fan-and-store.md`
- `docs/guides/interview-demo-walkthrough.md`

# Related Contracts

- 없음(앞 단계의 계약을 쓴다)

# Edge Cases

- 데모 계정의 `iam`(콘솔) 행은 묶지 않는다(D1).

# Failure Scenarios

1. 시드 id 를 한쪽만 바꿔 데모 계정의 팬 팔로우나 스토어 주문이 사라진다.
2. 재굽기 전 이미지에서 «안 된다» 를 재고 결함으로 보고한다.

---

# 743 보류 인계 (2026-10-02 UTC)

`TASK-MONO-743`(기존 두 계정 묶기)이 보류됐다 — 대상이 데모 계정 `demo@demo.com` 한 명뿐이라서다(743 «보류 기록»). 그 한 명은 이 티켓이 **시드로** 해결한다:

- [x] `demo@demo.com` 을 **풀 계정 하나**로 시드한다 — `consumer-pool` 테넌트의 계정·신원·자격 1개 + `fan-platform` · `ecommerce` 멤버십 둘(ACTIVE). 지금의 사이트 계정 둘(`account-service` `R__05` 의 `…ec01` · `…fa02`, `auth-service` `R__01`)을 대체한다. 콘솔(`iam`) 쪽 `…ad03` 운영자 계정은 그대로(D1).
- [x] 살아남는 id 를 하나 고른다 — 데모 데이터는 시드 스크립트가 로그인해 API 로 만드므로(`infra/demo/seed/seed-ecommerce.sh` · `seed-fan.sh`) 어느 id 든 데이터가 따라간다. 그 id 를 하드코딩한 곳(시드 SQL 주석 · e2e · 문서)을 grep 해 함께 고친다.
- [ ] 시드 뒤 확인: 같은 `sub` 로 팬·스토어 둘 다 로그인되고, 팬 → 스토어 이동에 동의 화면이 **안** 나온다(멤버십이 이미 둘 다 있으므로).
- [x] R__ 시드는 체크섬이 바뀌면 기존 볼륨에서도 다시 돈다 — 옛 사이트 계정 행이 남은 볼륨에서는 풀 계정 INSERT 가 통과해도 같은 이메일의 사이트 계정이 남아 § 2 의 공존 금지가 깨진다. 옛 행을 지우거나, 재굽기가 새 볼륨이라는 것을 확인한다.

---

# 구현 기록 (2026-10-02 UTC)

> 분석·구현 = Opus 5.5. 커밋: `6f1b8dbb8`(착수) · `4abeaee00`(시드·시험) · `e6b2928f2`(문서) · 이 기록.

## 바꾼 파일

| 파일 | 무엇 |
|---|---|
| `projects/iam-platform/apps/account-service/src/main/resources/db/migration-dev/R__05_seed_demo_corp_tenant_and_consumer_accounts.sql` | `identities`·`accounts`: `…ec01` 한 행씩, 테넌트 `consumer-pool`. `…fa02` 행 **삭제**. `consumer_site_memberships` 두 문(`fan-platform`·`ecommerce`, ACTIVE, `consented_at = accounts.created_at`) — **`tenant_id = 'consumer-pool'` 인 계정에만**(`INSERT IGNORE … SELECT … WHERE`). 저장 역할 없음(시드 역할은 발급 때 사이트별로 붙는다; `account_roles` 는 복합 FK 때문에 풀 계정을 담을 수 없다). demo-corp 부분 무변경 |
| `…/account-service/…/migration-dev/R__06_seed_fan_artist_accounts_and_artist_role.sql` | 아티스트 여섯(`…a001`~`a006`, **id 그대로**): `identities`·`accounts` 테넌트 → `consumer-pool` · `fan-platform` 멤버십 한 문(같은 consumer-pool 가드) · `account_roles` → `consumer_site_roles(id, 'fan-platform', 'FAN'|'ARTIST', NULL, NOW(6))` 12행 |
| `…/auth-service/…/migration-dev/R__01_seed_demo_single_identity_credentials.sql` | 자격 3행 → 2행: `('consumer-pool', …ec01)` + `('iam', …ad03)`(무변경, D1). `ecommerce`·`fan-platform`(`…fa02`) 행 제거. 같은 해시 |
| `…/auth-service/…/migration-dev/R__02_seed_fan_artist_credentials.sql` | 아티스트 자격 6행 테넌트 → `consumer-pool` |
| `infra/demo/seed/seed-fan.sh` | `DEMO_SUB` 기본값 `…fa02` → `…ec01`(토큰 sub 우선 폴백 유지) · ARTIST 역할 실패 문구가 `consumer_site_roles`·멤버십을 가리키게 |
| `…/auth-service/build.gradle` | `R__05` 를 `test` 입력으로 선언(형제 파일만 바뀌어도 시험이 다시 돈다) |
| `…/auth-service/src/test/…/demoseed/DemoSeedCredentialTest.java` | 2행(풀+iam, 소비자 사이트 행 0) · 풀 자격 `account_id` = R__05 풀 계정 · 두 사이트 멤버십 + consumer-pool 가드 · `seed-fan.sh` 기본 sub = 풀 계정 id |
| `…/demoseed/FanArtistDemoSeedTest.java` | 풀 테넌트 · `consumer_site_roles` 튜플 파싱 · 멤버십 문 셀 신설 |
| `…/demoseed/DemoViewerOperatorSeedTest.java` · `DemoSecondOperatorSeedTest.java` | R__01 행 수 대조군 4→3 · javadoc |
| `…/account-service/src/test/…/integration/FanArtistRoleSeedIntegrationTest.java` | 풀 모양으로 재작성: 여섯 계정 · consumer-members(발급 조회) `ACTIVE`+`[FAN, ARTIST]` · roles GET(BE-618 확장) `[FAN, ARTIST]` · ecommerce 비멤버 · 멱등(6/6/12) · 비아티스트 풀 팬 대조군. 자체 컨텍스트 대신 `AbstractConsumerPoolIntegrationTest` 상속(컨텍스트 하나 감소) |
| `…/integration/DemoConsumerPoolSeedIntegrationTest.java` (신설) | R__05 의 소비자 문만 실행(demo-corp 문은 세어서 건너뜀): 풀 계정 1·사이트 계정 0 · 두 사이트 ACTIVE · 콘솔 검색(`/internal/accounts`)이 ecommerce·fan-platform 양쪽에서 찾음 · 멱등 · **옛 볼륨 대조군**(`…ec01` 이 ecommerce 사이트 계정이면 멤버십 0행) |
| `docs/guides/interview-demo-walkthrough.md` | § 0 · § 6 행(추적 칸에 `TASK-MONO-744`) |

## 살아남는 id = `…ec01` (왜)

`seed-ecommerce.sh` 는 스토어 프로필을 **리터럴 `…ec01` 로 직접-DB INSERT** 하고 쿠폰 발급에도 그 값을 쓴다(user-service 에 프로필 생성 엔드포인트 없음 — `TASK-BE-575`). `seed-fan.sh` 는 이미 **토큰의 sub 을 기본값보다 믿는다**. 그래서 ec01 을 살리면 하드코딩된 소비자는 그대로 맞고, 팬 쪽은 기본값만 옮기면 된다. 팬 데이터(팔로우·멤버십 구독·FAN_POST)는 전부 토큰 sub 로 API 생성이라 따라온다.

## 🔴 기존 로컬 볼륨 — 옛 모양 유지 (743 인계 4번째 칸의 전제 정정)

인계 칸은 «풀 계정 INSERT 가 통과해도 같은 이메일의 사이트 계정이 남아 공존이 깨진다» 였는데, **풀 INSERT 는 통과하지 않는다**: 풀 계정이 **같은 id `…ec01`** 을 쓰므로 `accounts` PK 에서, 풀 자격은 `credentials.account_id` 전역 유니크에서 부딪혀 `INSERT IGNORE` 로 무시된다 ⇒ 옛 볼륨은 **옛 세 행 모양 그대로**(공존 없음, 팬↔스토어 재로그인). 멤버십 문은 consumer-pool 가드라 사이트 계정 위에 아무것도 쓰지 않는다(쓰면 BE-618 이동기 — 평범한 INSERT — 가 그 계정에서 실패). 아티스트 역할 튜플은 멤버십이 없으면 FK 미스 → IGNORE 가 건너뛴다. **DELETE 는 넣지 않았다**(`account_status_history` append-only 트리거 · FK 연쇄로 남의 볼륨에서 반복 시드가 실패할 수 있음). 새 모양이 필요하면 **account_db + auth_db 볼륨을 함께** 새로(한쪽만 새로 하면 팬 자격 `…fa02` 가 없는 계정을 가리킨다). 옛 볼륨의 아티스트는 BE-618 `POST /internal/consumer-pool/legacy-moves` 로도 옮길 수 있다. 데모 서버: 재굽기 = 이미지만 굽고 시드는 첫 부팅에 빈 볼륨에서 돈다(리드 실측 전제 — 이 티켓에서 다시 재지 않았다).

## 항목 D — 무엇을 확인했나

- `00000000fa02` grep(`tasks/done/**` 제외): R__05 · R__01 · `seed-fan.sh` 3곳뿐 → 셋 다 고침. e2e·scripts·문서에 0.
- `ec01`·`ad03`: `seed-ecommerce.sh`(ec01 — 유지가 맞음, 팬/스토어 전용 가정 없음: 소비자 토큰은 `ecommerce-web-store-client` 폼 로그인 → 풀 자격 → `sub=ec01`·`[CUSTOMER]`), admin `R__seed_demo_operator.sql`·`DemoOperatorSeedIntegrationTest`·`TenantClaimTokenCustomizerTest`(ad03 — 무변경, D1).
- 아티스트 id `…a00x`: `artists.account_id`·`infra/demo/public-data/**`·`seed-fan.sh` ARTIST_* — id 불변이라 무변경.
- 시드를 grep 하는 테스트: auth `demoseed/*` 4개 갱신, account `FanArtistRoleSeedIntegrationTest` 갱신. `OAuthClientTenantReferenceIntegrationTest` 는 account dev 마이그레이션을 **Flyway 로 실제 적용**한다(CI) — 새 SQL 의 두 번째 실실행 지점. `consumer-pool` 은 V0029 라 테넌트 집합 판정 무영향.
- 풀 동작의 스위치: `iam.consumer-pool.enabled` 기본 `true`, 어느 compose 도 끄지 않음(grep 0). auth 의 풀-먼저 조회는 플래그가 아니라 풀 자격 존재로 동작.
- 토큰 경로: `customizeForPoolPrincipal` 은 `LinkedHashSet(seed) ∪ siteRoles` — 저장된 FAN 과 시드 FAN 은 중복 없이 합쳐진다. 멤버십 없는 사이트는 토큰 거절 → 시드가 두 사이트 멤버십을 넣는 이유.
- 콘솔 계정 목록/검색: `AccountSearchQueryService` 가 플래그 on 이면 `findAllIncludingPoolMembers`/`findByEmailIncludingPoolMembers`(BE-614/616) — 사이트별 건수는 이전과 같다(ecommerce 1 · fan-platform 7). `DemoConsumerPoolSeedIntegrationTest` 가 데모 계정으로 고정.
- e2e 시드(`web-store/e2e/fixtures/iam-consumer-seed.sql` · console `tests/e2e/fixtures/seed.sql`)는 자기 이메일을 쓰고 이 R__ 행과 겹치지 않는다. `projects/**/e2e` 에 `demo@demo.com` 0건. `scripts/capture-portfolio.mjs` 는 앱마다 별도 브라우저 컨텍스트라 순서 무관.
- 다른 서비스 시드에 fa02 키: 없음. product-service 셀러 프로비저닝은 기계 이메일(사이트 계정) — 무관.
- 직접 IAM `/login`(시작 client 없음)은 여전히 교차 조회 2행 → 실패(R__01 머리 «Always start from the app» 유지).

## 로컬 실행 (rc 는 파일 리다이렉트 후 `$?`)

- `./gradlew :projects:iam-platform:apps:account-service:check :projects:iam-platform:apps:auth-service:check --continue` → **rc=0** (BUILD SUCCESSFUL). demoseed 스위트: DemoSeedCredentialTest 7/0 · FanArtistDemoSeedTest 7/0 · DemoSecondOperator 4/0 · DemoViewerOperator 4/0.
- **bite**: seed-fan 기본값을 fa02 로 · R__05 의 ecommerce 멤버십을 fan-platform 으로 · R__06 가드를 `1 = 1` 로 → `demoseed.*` rc=1, **정확히 그 3셀**만 빨강(22 중 3) → 원복 후 rc=0.
- `bash -n infra/demo/seed/seed-fan.sh` → rc=0.
- 스테이지 후: `check-index-queue-drift.sh` rc=0 · `check-task-id-collision.sh` rc=0(활성 21, 중복 0) · `check-walkthrough-ledger-drift.sh` rc=0(§ 6 51행, 미소유 0).
- SQL 가드: `check-dev-seed-migration-band.sh` rc=0 · `check-flyway-version-collision.sh` rc=0 · `check-flyway-unresolvable-placeholder.sh` rc=0(366개, 0건).

## ⚪ 미측정

- **Testcontainers IT 전부**(`DemoConsumerPoolSeedIntegrationTest` 신설 5 · `FanArtistRoleSeedIntegrationTest` 6 · `OAuthClientTenantReferenceIntegrationTest`): 이 호스트에 Docker 없음 → **CI 판정**. PR 의 CI 건수로 대조할 것.
- **AC-2** — 구성으로는 맞다(풀 계정 하나 → 두 사이트 토큰 `sub` 동일은 BE-615 IT 가 고정 · 스토어 프로필/쿠폰 = ec01 · 팬 데이터 = 토큰 sub). 그러나 «팬 데이터와 스토어 주문이 같은 sub 로 **보인다**» 는 시드가 실제 스택에서 돈 뒤에만 잴 수 있다 ⇒ **재굽기 뒤 라이브**(시드 로그에 `토큰 sub(…) != DEMO_FAN_SUB` 경고가 **없어야** 하고, 팬 팔로우·스토어 주문이 같은 계정에 보여야 한다). 미체크 유지.
- **AC-4 · AC-5 · 743 인계 3번째 칸** — 라이브. 측정 전 이미지 시각 vs 머지 시각 대조.

## ⏳ 재굽기 의무 (소유자 몫)

현재 핀: `infra/demo/aws/deployed-ami.env` `REPO_COMMIT=a6f0ab791` (17차, 2026-09-27). `git log --oneline a6f0ab791..HEAD -- projects/ libs/ infra/demo/seed/` 중 **구워지는 백엔드/시드 변경**(chore·docs·Vercel 프런트 제외):

- `0c74fd527` TASK-MONO-737 — 잠금 404 두 원인(IAM 게이트웨이 `X-Tenant-Id` · 셀러 잠금 경로)
- `3bd595b9c` TASK-BE-609 — 비밀번호 변경·재설정 게이트웨이 401
- `b80e028a1` TASK-BE-612 — 재설정 확인이 AUTO_DETECT 잠금 해제
- `fa3940bf7` TASK-BE-611 — 소셜 신원 조회를 시작 client 테넌트로
- `7b42609bc` TASK-BE-610 — 콘솔 SSO 재인증
- `b3c4d8c67` TASK-MONO-736 — audience 요약 로그(lib)
- `c23927786` TASK-BE-613 — 로그인·가입 화면 client 별 브랜딩(auth-service)
- `98e6c6dbe` TASK-BE-614 · `3d732a21b` TASK-BE-615 · `2e7e2951f` TASK-BE-616 · `ecff742a7` TASK-BE-618 · `f5b436669` TASK-BE-620 — 소비자 풀 전 단계
- 이 티켓 `4abeaee00` — 풀 모양 데모 시드

Vercel 쪽(굽기 무관): TASK-FE-102 · FE-103(web-store) · FAN-FE-025 · FAN-FE-026(fan web). 재굽기 한 번이 위 백엔드 묶음 전부와 이 시드를 산다 — AC-4/AC-5 는 그 창에서.

## 추가 발견 (2026-10-02 UTC) — 데모 엣지가 `/consent` 를 라우팅하지 않았다

- 이 PR 의 첫 CI 에서 `Demo wrapper smoke` 의 가드 (p) 가 빨강: `TASK-BE-616` 이 만든 사이트 이용 동의 화면 `/consent` 가 `infra/demo/iam-traefik.override.yml` 의 `iam-oidc` 라우터 경로 목록에 없었다 → 데모에서 그 요청은 iam 게이트웨이로 떨어져 **404** — 재굽기 뒤 팬 ↔ 스토어 첫 방문(멤버가 아닌 풀 계정)이 막혔을 것이다.
- 616 은 `infra/demo` 를 건드리지 않아 그 PR 에서는 이 가드가 돌지 않았다(경로 필터). 이 티켓이 시드 때문에 `infra/demo` 를 건드리자 처음 돌았다. 같은 파일 주석이 «손으로 열거하는 한 세 번째가 난다» 고 예고한 그 **세 번째**다(`/signup` MONO-380 · `/connect` MONO-615).
- 고침: 규칙에 `PathPrefix(/consent)` 추가 + 주석. 로컬 재현: 가드 (p) 의 추출 논리로 고치기 전 `/consent` 누락 → 고친 뒤 `/consent` · `/login` · `/signup` 전부 덮임.
- 🔵 데모 계정은 두 사이트 멤버가 미리 들어가 있어 동의 화면을 안 거친다 — 이 결함은 **새로 가입한 사람**이 다른 사이트로 넘어갈 때 드러났을 것이다.
