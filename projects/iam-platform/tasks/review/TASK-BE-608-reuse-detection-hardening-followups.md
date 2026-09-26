# Task ID

TASK-BE-608

# Status

review

# Title

재사용 탐지 후속 — Redis 카운터 원자성 · 이벤트에 원문 토큰 · 잠금 해제 경로 · 늦은 재제출 오탐의 크기

# Owner

iam-platform

# Task Tags

- auth-service
- security-service
- security

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet(①②) · 판독/측정(③④)

---

# Goal

`TASK-BE-606`(#4036, 2026-09-26 UTC)이 SAS 재사용 탐지를 실제로 켜고, 재사용 **횟수**가 잠금을 가르게 했다(1회 = ALERT, 1시간 안 2회 이상 = AUTO_LOCK).
그 결과 전에는 가려져 있던 넷이 드러났다(BE-606 § 후속):

1. 🔴 **카운터 원자성** — `RedisTokenReuseCounter` 가 `INCR` 과 `EXPIRE` 를 따로 부른다. `EXPIRE` 가 실패하면 키가 **영구**가 되고, 그 계정의 이후 재사용은 전부 2회 이상 = 잠금이다.
   이제 카운트가 점수를 가르므로 영향이 커졌다. (`INCR` + `EXPIRE NX` 를 한 스크립트/트랜잭션으로.)
2. **이벤트에 원문 토큰** — `auth.token.reuse.detected` 의 `reusedJti` 가 SAS refresh 토큰 **원문**이다. identity-platform 의 «refresh 토큰을 로그에 남기지 않는다» 와 충돌한다
   (이벤트는 security-service 에 적재된다). 해시로 바꾸려면 **계약 먼저**(`auth-events.md`) + 소비자 확인.
3. **잠금 해제 경로** — 자동 잠금 뒤 사용자가 어떻게 풀리는가(운영자 수동 · 시간 경과 · 셀프 복구)가 확인되지 않았다. 탐지가 켜졌으므로 확인이 필요하다.
4. **늦은 재제출 오탐의 크기** — 콘솔이 응답을 잃으면 쿠키를 그대로 두고(`console-web/src/shared/lib/session-refresh.ts:229-231`) 몇 분 뒤 같은 토큰을 다시 낸다 ⇒ 30초 유예 밖 = 재사용
   (패밀리 폐기 + ALERT). 잠금은 아니지만 사용자는 로그아웃된다. 빈도는 ⚪ 미측정.

# Scope

## 포함

- ① 원자화 + 단위 테스트(`EXPIRE` 실패 모사 → 키 TTL 이 남는다).
- ② 계약 결정 → 해시화(또는 필드 제거) + 소비자 영향 확인.
- ③ 코드·스펙 판독으로 해제 경로 표 — 없으면 소유자 결정 항목.
- ④ 데모 로그의 `refresh_error`/`refresh_failed` 빈도 측정(창 항목 — `TASK-MONO-672` 로 넘겨도 된다).

## 제외

- 유예 정책 자체(BE-606 결정) · 콘솔 POST 경로(`TASK-PC-FE-300`).

# Acceptance Criteria

- [x] **AC-1** — 카운터 증가와 만료가 원자적이다(테스트로). → § AC-1 구현·검증
- [x] **AC-2** — `reusedJti` 가 원문이 아니다 — 계약·구현·소비자 일치. → § AC-2 소비자 전수·구현·검증
- [x] **AC-3** — 잠금 해제 경로 표(경로 · 주체 · file:line) — 부재면 소유자 결정 요청. → § AC-3 해제 경로 표
- [ ] **AC-4** — 늦은 재제출 빈도: 측정값 또는 «측정 불가 + 이유». → **`TASK-MONO-672` 항목 17 로 이관**(창이 서야 잴 수 있는 측정 — § AC-4 이관 참조). 이 티켓에서는 닫지 않는다(측정도 «측정 불가 + 이유»도 아직 없다 — 창이 그 답을 낸다).

---

# AC-1 구현·검증 (2026-09-26 UTC, 이 worktree)

## 판독 (착수 시 재확인)

기존 `RedisTokenReuseCounter.incrementAndGet`(BE-606 이전 코드)은 `redisTemplate.opsForValue().increment(key)` 뒤
`value == 1L` 이면 별도로 `redisTemplate.expire(key, TTL)` 를 불렀다 — **두 개의 독립된 Redis 라운드트립**. INCR 은
성공했는데 그 사이(프로세스 종료·커넥션 끊김·Redis 장애)에 EXPIRE 가 안 가면 그 키는 **TTL 없이 영구화**된다.
BE-606 이후 카운트가 점수를 가르므로(1건=ALERT·2건+=AUTO_LOCK), 영구화된 키를 가진 계정은 이후 재사용마다
"2건째 이상"으로 읽혀 **매번 AUTO_LOCK** 이 된다.

## 구현 (file:line — 이 브랜치)

- `RedisTokenReuseCounter.java:49-61` — `INCREMENT_SCRIPT`(`DefaultRedisScript<Long>`): `INCR` 하고 결과가 1이면
  같은 스크립트 안에서 `EXPIRE` 를 건다. 이 저장소의 `TokenBucketRateLimiter`(gateway-service,
  `apps/gateway-service/.../ratelimit/TokenBucketRateLimiter.java:20-32`)가 이미 쓰던 것과 **같은 모양** — Redis 는
  Lua 스크립트를 단일 명령처럼 원자적으로 실행하므로, INCR 이 반영되고 EXPIRE 가 안 걸린 채로 관측되는 창이 구조적으로
  없다(둘 다 같은 서버 호출 안에 있다 — 네트워크가 끊기면 스크립트 자체가 안 갔을 뿐, "INCR만 갔다"는 일어나지 않는다).
- `incrementAndGet` :65-77 — 두 호출을 `redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), ttlSeconds)` 한 호출로
  교체(:69-70). `peek`(:79-89)은 손대지 않음 — 단순 GET 이라 원자성 문제가 없다.
- **카운팅 의미는 그대로다** — "첫 증가에만 TTL, 이후 증가는 TTL 재연장 안 함"(고정 창, BE-606 이 전제한 "첫 증가 시각부터
  TTL 1h")은 스크립트의 `if current == 1` 분기가 그대로 보존한다. 슬라이딩 윈도우로 바뀌지 않았다.

## 테스트

- 단위 `RedisTokenReuseCounterUnitTest.java`(10개, 전면 재작성) — `execute()` 모킹으로 값 전달(1/5/null/예외) 확인 +
  **회귀 방어 전용 테스트**(`incrementAndGet_neverSplitsIntoTwoRoundTrips`, :117-135): `incrementAndGet` 호출 뒤
  `redisTemplate.expire(...)` 와 `redisTemplate.opsForValue()` 가 **전혀 불리지 않았음**을 검증 — 옛 두 단계 모양이
  되돌아오면 이 테스트가 바로 잡는다.
- 신규 IT `RedisTokenReuseCounterIntegrationTest.java`(4개) — 실제 Redis(Testcontainers `redis:7-alpine`), 보안-서비스
  전체 Spring 컨텍스트 없이 `StringRedisTemplate` 을 직접 실제 Redis 에 연결해 `RedisTokenReuseCounter` 를 손으로 생성.
  ① 첫 증가 → 값 1 + TTL 이 1~3600초 사이(영구 아님) ② 20회 연속 증가 매번 TTL 이 살아있음(영구화가 어느 시점에도 없음)
  ③ TTL 은 첫 증가에만 걸리고 재연장되지 않음(1.1초 대기 뒤 TTL 감소 확인) ④ 서로 다른 (tenantId,accountId) 는 독립.
  🔴 **⚪ 로컬 미실행** — `docker info` 부재(이 Windows 호스트), `DockerAvailableCondition` 이 클래스 전체를 스킵한다
  (이 저장소의 다른 Testcontainers IT 와 동일한 스킵 모양 — Gradle 테스트 리포트에 항목 자체가 안 뜬다, 다른 IT 도 같음).
  CI(Docker 있는 러너)가 판정한다.
- 실행: `./gradlew :projects:iam-platform:apps:security-service:test` — 아래 § 테스트(전체) 참조.

## bite

| 깨뜨린 것 | 결과 |
|---|---|
| `incrementAndGet` 을 옛 두 단계(`opsForValue().increment` + 조건부 `expire`) 로 되돌림 | 단위 10개 중 **7개 실패**(값 전달 4종 + 회귀 방어 테스트 자체 — `NeverWantedButInvoked`) → 복구 후 10/10 green |

## 이미 영구화된 키가 있을 수 있는가 — 판단 (배포 전 정리 필요 여부)

🔵 **이 결함이 실제로 발동했을 가능성은 낮지만 배제할 수 없다.** 좁은 경쟁 창(INCR 성공~EXPIRE 실패 사이의 프로세스/커넥션
장애)이 필요하고, `TASK-BE-606`(카운트가 점수를 가르게 된 시점)이 이 워크트리 기준 방금 머지됐다(같은 날) — 실제
트래픽에 노출된 기간이 매우 짧다. 다만 **이 수정 자체는 이미 영구화된 키를 치료하지 않는다**: 새 스크립트도
`if current == 1` 일 때만 EXPIRE 를 걸므로, TTL 없이 값 ≥2 인 기존 키는 계속 증가만 하고 TTL 은 영원히 안 걸린다(그
키가 다시 1이 되는 경우는 자연 만료 — 없으므로 — 뿐이다). ⇒ **배포 전에 한 줄짜리 운영 점검을 권고한다**: Redis 에서
`SCAN` 으로 `reuse:*` 키를 순회해 `TTL key` 가 `-1`(영구)인 것을 찾아 `DEL`(다음 재사용부터 새 창으로 다시 시작 —
카운트를 잃는 것은 안전한 방향이다, 잘못 잠그는 쪽이 아니라 안 잠그는 쪽으로 실패)한다. **데이터 마이그레이션(스키마
변경)은 아니므로 이 티켓에서 구현하지 않는다** — 운영 점검 스크립트 한 줄(`redis-cli --scan --pattern 'reuse:*' | xargs -L1 redis-cli ttl`
류)이면 충분하고, 데모/포트폴리오 스케일이라 실제 발동 여부는 그 점검이 답한다.

---

# AC-2 소비자 전수·구현·검증 (2026-09-26 UTC)

## 소비자 전수 (grep, `projects/*/apps` 전체)

`reusedJti` 는 이벤트 필드로 정의된 것 외에는 아래에만 등장한다 — **`projects/*/apps` 전체에서 다른 프로젝트는 이
이벤트를 소비하지 않는다**(iam-platform 만):

| 파일 | 역할 | 값을 읽는가 |
|---|---|---|
| `auth-service` `AuthEventPublisher.java:74` (인터페이스) | 시그니처 선언 | — |
| `auth-service` `OutboxAuthEventPublisher.java:204-221` | outbox JSON 에 `reusedJti` 를 그대로 적음(통과) | **쓰기만, 읽지 않음** |
| `auth-service` `SasRefreshTokenAuthenticationProvider.java:734-735` | `publishTokenReuseDetected` 호출(값 생성) | 생성만 |
| `auth-service` `RefreshTokenUseCase.java:219-229` | 같은 호출(레거시 경로) | 생성만 — 이미 원문이 아님(아래 참조) |
| `security-service` `TokenReuseDetectedConsumer.java:24-27` → `AbstractAuthEventConsumer.processEvent` | Kafka 소비 | **`accountId`·`tenantId`·`ipMasked`·`timestamp` 만 읽는다 — `reusedJti` 는 읽지 않음**(`TokenReuseDetectedConsumerUnitTest.java` 전 케이스가 이를 확인 — payload 에 `reusedJti` 를 넣은 케이스가 없다) |
| `security-service` `TokenReuseRule.java` / `EvaluationContext` | 탐지 규칙 | `EvaluationContext` 에 `reusedJti` 필드 자체가 없다 — 애초에 배선되지 않음 |
| `security-service` `login_history` 테이블 | 영속화 | outcome=TOKEN_REUSE 행에 `reusedJti` 컬럼 없음(스키마에 그 필드가 없다) |

**결론 — 소비자가 없다.** 이벤트는 Kafka 로 나가고 security-service 가 소비하지만, `reusedJti` **값 자체를 읽는 코드가
존재하지 않는다** — auth-service outbox 테이블에 통과 쓰기되고 Kafka 페이로드에 실릴 뿐이다. AC-2 의 STOP 조건(소비자가
실제로 원문을 필요로 함)에 해당하지 않으므로 **다이제스트화를 진행**했다.

## 왜 SAS 경로만 고쳤는가 — 레거시 `RefreshTokenUseCase` 는 이미 원문이 아니었다

`SasRefreshTokenAuthenticationProvider.java:735`(수정 전)이 넘긴 `reusedToken` = `submittedTokenValue` =
`refreshTokenAuthentication.getRefreshToken()`(:185) — **클라이언트가 실제로 제출한 SAS refresh 토큰 원문**.
반면 `RefreshTokenUseCase.java:219-229`가 넘기는 `jti` 는 `tokenGeneratorPort.extractJti(command.refreshToken())`
(:60) = `JwtTokenGenerator.extractJti` → `parseClaims(refreshToken).getId()`(`JwtTokenGenerator.java:129-131`) —
JWT 의 `jti` **클레임**(비가역 식별자)이지 토큰 원문이 아니다. 그래서 이 티켓은 **SAS 경로만** 고쳤다 — 레거시 경로는
Goal 이 지목한 결함(원문 노출)이 원래 없었다.

## 계약 (코드보다 먼저)

`specs/contracts/events/auth-events.md` § `auth.token.reuse.detected` — payload 예시의 `reusedJti` 설명을
"SHA-256 hex 다이제스트, 원문 아님" 으로 교정 + 필드 노트 신설(TASK-BE-608, 왜/어떻게/레거시 경로 무관 이유). **필드명은
유지**(리네임 대신 값의 의미만 교정 — BE-603/BE-604 가 `accountId`/`tenantId` 에 쓴 것과 같은 패턴, breaking 아님).

## 구현 (file:line — 이 브랜치)

- `SasRefreshTokenAuthenticationProvider.java:771-779` — `static String reuseTokenDigest(String rawToken)`
  (package-private): `MessageDigest.getInstance("SHA-256")` → `HexFormat` hex(64자). 프레임워크 의존 없음.
- `handleReuse` 안의 발행 호출 :734-735 — `reusedToken` → `reuseTokenDigest(reusedToken)`. 원문은 이 시점 이후로
  이벤트 인자로 넘어가지 않는다.
- 주석 갱신 :685-688 — "이벤트는 원문을 싣는다" → "이벤트는 SHA-256 다이제스트를 싣는다, 원문 아님".
- `RefreshTokenUseCase.java` 는 **변경 없음**(위 이유).

## 테스트

- `SasRefreshTokenAuthenticationProviderTest.java` — 기존 6개 검증(`publishTokenReuseDetected` 의 세 번째 인자를
  `eq(tokenValue)`/`eq(tokenA)` 로 단언하던 것)을 `eq(SasRefreshTokenAuthenticationProvider.reuseTokenDigest(tokenValue))`
  로 교정(:294-296·503-505·689-692·736-738·760-763·830-832). 테스트가 프로덕션과 **같은 파생 함수**를 호출해 기대값을
  만들지만, bite(아래)가 확인하듯 **호출부(콜사이트)가 원문을 넘기면 실패한다** — 함수 자체가 아니라 "원문이 이벤트로
  안 나간다"를 잡는 테스트다.
- 실행: `./gradlew :projects:iam-platform:apps:auth-service:test` — 아래 § 테스트(전체) 참조.

## bite

| 깨뜨린 것 | 결과 |
|---|---|
| 발행 호출의 세 번째 인자를 `reuseTokenDigest(reusedToken)` → `reusedToken`(원문) 으로 되돌림 — 테스트의 기대값 계산은 그대로 둠(같은 함수를 그대로 호출) | 24개 중 **6개 실패**(`ArgumentsAreDifferent`/`WantedButNotInvoked`) → 복구 후 24/24 green |

이 bite 는 "테스트가 프로덕션 함수를 그대로 재사용해서 자기 자신과만 비교하는" 함정을 피한다 — 콜사이트만 원문으로
되돌렸고 테스트의 기대값 계산은 안 건드렸으므로, 실패는 "콜사이트가 다이제스트를 안 넘긴다"를 정확히 잡는다.

## 다른 이벤트의 같은 필드 — 관찰만, 이 티켓 범위 밖

`auth.token.tenant.mismatch` 도 `reusedJti` 필드를 갖고 그 호출부(`SasRefreshTokenAuthenticationProvider.java:269`·
`RefreshTokenUseCase.java:91`)도 원문 토큰을 넘긴다. `TASK-BE-608` 의 Scope·Related Contracts 는 `auth.token.reuse.detected`
**만** 지목했으므로 손대지 않았다 — 같은 패턴이 다른 이벤트에도 있다는 사실만 기록한다(후속 후보).

---

# AC-3 해제 경로 표 (읽기 전용 판독, 2026-09-26 UTC)

| 경로 | 주체 | 존재? | file:line |
|---|---|---|---|
| 운영자 수동 unlock (플랫폼 콘솔 → admin-service → account-service) | ADMIN | ✅ **존재 — 유일하게 살아있는 경로** | `platform-console/apps/console-web/src/app/api/accounts/[accountId]/unlock/route.ts:13-35` → admin-service `AccountAdminController.java:164-183`(`@RequiresPermission(Permission.ACCOUNT_UNLOCK)`) → account-service `AccountLockController.java:63-85`(`unlockAccount`) → `AccountStatusMachine.java:32-38`(LOCKED→ACTIVE, reason ∈ {`ADMIN_UNLOCK`,`USER_RECOVERY`,`OPERATOR_PROVISIONING_STATUS_CHANGE`}) |
| 시간 경과(자동 잠금 해제, TTL/스케줄) | SYSTEM | ❌ **부재** | 계정 테이블·Redis 어디에도 잠금 만료/TTL 필드가 없다. `account-service` 의 `@Scheduled` 잡은 `AccountDormantScheduler`·`AccountAnonymizationScheduler` 둘뿐(`infrastructure/scheduler/`) — LOCKED 대상 스케줄러는 없다 |
| 셀프서비스 복구(비밀번호 재설정 등) | USER | ❌ **부재 — 배선 안 된 이름뿐인 값** | `StatusChangeReason.USER_RECOVERY`(`StatusChangeReason.java:8`)가 상태기계 허용 집합(`AccountStatusMachine.java:35`)에 **선언은 되어 있으나**, 그 reason 으로 `changeStatus`를 호출하는 프로덕션 코드가 없다(grep 전수 — 유일한 다른 매치는 그 enum 선언 자신과 `AccountStatusMachineTest`). auth-service 의 비밀번호 재설정(`RequestPasswordResetUseCase`/`ConfirmPasswordResetUseCase`, TASK-BE-607)은 SAS 인가·미러 행을 폐기할 뿐 account-service unlock 을 호출하지 않는다 |

**소유자 결정 필요**: 이제 AUTO_LOCK 이 실제로 발동하므로(`TASK-BE-606`), 위 두 부재 경로(시간 경과·셀프서비스) 중
어느 것을 만들지, 아니면 운영자 수동 unlock 만으로 충분한지는 이 티켓이 결정하지 않는다 — 판독만 제공한다.

---

# AC-4 이관 — `TASK-MONO-672` 항목 17

늦은 재제출 오탐 빈도는 실제 트래픽/응답 유실 패턴에 의존하는 측정이라 시드로 합성하기 어렵고(합성
`auth.token.reuse.detected` 로는 "유예 창 안/밖"이라는 원인을 못 구별한다), 창(살아있는 데모 스택)이 서야 잴 수 있는
항목이다. `tasks/ready/TASK-MONO-672-…` 에 **항목 17**로 이관했다(항목 14-16 이 쓴 것과 같은 이관 관례 — 무엇을 재는지·
창이 필요한 이유·유효성 술어를 적었다). 이 티켓은 AC-4 를 미해결로 남긴다(측정값도 "측정 불가 + 이유"도 아직 없다 —
그 답은 창의 것이다).

---

# 테스트(전체, 2026-09-26 UTC, 이 worktree)

| 명령 | rc | 결과 |
|---|---|---|
| `./gradlew :projects:iam-platform:apps:auth-service:test :projects:iam-platform:apps:security-service:test` | 0 | auth-service 853 tests·실패 0·skip 31 / security-service 247 tests·실패 0·skip 13(신규 `RedisTokenReuseCounterIntegrationTest` 는 Docker 부재로 **리포트 자체에 안 뜬다** — 이 저장소의 다른 Testcontainers IT 와 동일한 모양, BUILD SUCCESSFUL) |

`auth-service` 850→853·`security-service` 245→247 은 BE-606 이후 다른 머지(BE-607 등)가 얹은 기존 테스트 증가분을
포함한다(이 티켓이 auth 에 순증 0개 — 기존 테스트 6개 단언만 교정, security 에 순증 6개 — 단위 +2·IT +4).

# 이탈·판단 (소유자 확인 권장)

1. **AC-2 필드명 유지**(리네임 대신 값 의미만 교정) — BE-603/604 선례를 따랐다. 리네임(`reusedTokenDigest` 등)이 더
   명시적이라는 반론도 합리적 — 소유자 판단 대상.
2. **AC-1 사전 정리 스크립트를 이 티켓에서 실행하지 않았다** — 배포 파이프라인/운영 절차가 이 저장소 범위 밖(데모
   AMI/컨테이너 재생성 방식)이라, "무엇을 하면 되는지"만 적고 실행은 다음 배포 창의 몫으로 남겼다.
3. **AC-3 은 표만 냈다** — 두 부재 경로 중 어느 것을 만들지는 결정하지 않았다(Scope 가 판독만 요구).

# Related Specs

- `specs/contracts/events/auth-events.md` · `specs/features/abnormal-login-detection.md` · `platform/service-types/identity-platform.md`
- `TASK-BE-606`(§ 후속) · `TASK-PC-FE-300`

# Related Contracts

- `auth.token.reuse.detected` — `reusedJti` 의미 변경 시 계약 먼저.

# Edge Cases

| 상황 | 기대 |
|---|---|
| Redis 장애로 카운터를 못 읽음 | BE-606 결정대로 AUTO_LOCK(fail-closed) — 이 티켓은 바꾸지 않는다 |

# Failure Scenarios

1. **INCR 만 고치고 이미 영구화된 키를 둔다** → 배포 전 키가 남는다(정리 필요 여부를 판단).
2. **해시로 바꾸고 소비자를 안 본다** → security-service 가 원문을 기대하던 곳이 조용히 틀린다.
