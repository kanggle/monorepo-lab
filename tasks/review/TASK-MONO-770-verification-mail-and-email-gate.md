# Task ID

TASK-MONO-770

# Title

`ADR-MONO-080` 단계 1 (D3 · R1) — **인증 메일을 실제로 보낸다** + 회사 권한이 붙는 쓰기에 **인증된 이메일 필수** (셀러 구성원 수락 포함)

# Status

review

# Owner

monorepo

# Task Tags

- iam
- ecommerce
- security
- demo

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (권한이 붙는 쓰기의 게이트 · 새 장애 지점 = 메일 발송)

---

# Dependency Markers

- **선행**: `ADR-MONO-080` ACCEPTED — A · 직원연결 E1 · 테넌트생성 T1 (2026-10-07 UTC).
- **후속**: `TASK-MONO-771`(단계 2) · `TASK-MONO-772`(단계 3)는 이 티켓이 `done/` 이어야 착수한다(ADR-080 D2).

# Goal

IAM 이 이메일 소유를 확인하게 한다. 두 부분이고 **순서가 있다**:

1. **메일이 실제로 나가야 한다.** 지금 구현체는 `LoggingEmailVerificationNotifier`(`!prod`, 토큰도 안 남김) 하나이고 SMTP·SES 어댑터가 0 이다(ADR-080 Context). prod 발송 어댑터 + 데모에서 사람이 그 메일을 **받아 볼 수 있는** 경로를 연다.
2. 그다음 **게이트**: 풀 계정에 회사 권한(운영자 측면 · 협력사 참여자 · 셀러 구성원 역할)이 **붙는 쓰기**는 `email_verified_at` 이 있어야 한다. 로그인 자체에는 걸지 않는다(소비자 이용 불변).

라이더 R1(기본값): `ADR-MONO-079` D5 셀러 구성원 수락에도 적용한다 — 지금 «초대 이메일 == 로그인 이메일» 만 보고 인증 여부를 안 본다(`ConsumerSiteRoleWriteUseCase.java:44-47, 78-83`).

# Scope

## In Scope

- auth/account-service: prod 프로필 메일 발송 어댑터(SMTP 또는 SES — 착수 시 결정, 계약 먼저) · 데모 수신 경로(예: 데모 전용 메일함 화면 또는 Mailpit 류 — 착수 시 결정) · 발송 실패의 분류(일시/영구)
- 게이트: 셀러 구성원 수락(지금 구현됨) · 이후 단계 3 의 운영자 초대 수락 · 협력사 참여자 붙이기 — 이 티켓은 **지금 존재하는 쓰기**(셀러 수락)에 걸고, 나머지는 공용 술어로 만들어 772 가 재사용
- 화면: 인증 안 된 계정이 수락하려 할 때 «이메일 인증 필요 + 재발송» 안내 · 발송 실패 표기(ADR-080 § 새로 생기는 위험)
- 계약 갱신: `account-api.md`(인증 메일 발송) · 셀러 수락 계약의 새 거절 코드
- 🆕 **(소유자 결정 2026-10-07 UTC 로 추가)** auth-service 비밀번호 재설정 메일 — 같은 공백(구현체가 로깅 스텁 `LoggingEmailSender` 하나 · `EmailSenderPort` · `RequestPasswordResetUseCase`)을 **같은 SMTP 장치 · 같은 설정**으로 메운다

## Out of Scope

- 로그인에 인증 필수(ADR-080 Alternatives 에서 기각)
- 2단계 인증(771) · 운영자 규칙(772)

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정: 발송 어댑터 0 · `email_verified_at` 을 읽는 곳(로그인·초대·운영자 생성 0) · 셀러 수락 술어 file:line. 발송 수단(SMTP/SES)과 데모 수신 경로를 정하고 이유를 적는다. — ✅ § 작업 기록 · AC-0 표 + 소유자 결정 + 결정 표(SMTP 범용 · 설정 `iam.mail.enabled` · 데모 = Mailpit, 소유자 전용 basic auth).
- [ ] **AC-1** — prod 프로필 기동 시 발송 어댑터가 빈으로 뜬다(지금은 fail-fast). 데모에서 가입 → 인증 메일 수신 → 링크 → `email_verified_at` 기록을 끝까지 한 번 돈다(라이브 ⚪ 가능). — 앞 절반 ✅ `EmailVerificationNotifierWiringTest.prod_enabled_smtp`(prod + `iam.mail.enabled=true` + `spring.mail.host` → `SmtpEmailVerificationNotifier` 단일 빈) · fail-fast 유지 ✅ `prod_disabled_failsFast` · 데모 모양 ✅ `e2eProfile_enabled_smtp` · 렌더 ✅(데모 체인 `docker compose config` — account/auth-service 에 `IAM_MAIL_ENABLED=true` · `SPRING_MAIL_HOST=iam-mailpit` · 링크 `https://auth.hubwang.com/verify-email`). 뒤 절반(라이브 한 바퀴) ⚪ — 재굽기 전에는 잴 수 없다(§ 라이브 ⚪ 절차).
- [x] **AC-2** — 🔴 대조군: 남의 이메일로 가입한(인증 안 된) 풀 계정이 그 이메일로 온 셀러 초대를 **수락하지 못한다**(실패 쪽을 먼저 단언) · 같은 시험에서 인증 후 수락은 된다. — ✅ IAM `ConsumerSiteRoleWriteUseCaseTest$Grant.controlGroup_unverifiedRefusedFirst_thenVerifiedAccepts`(같은 `Account` 객체 — 미인증 → `EmailNotVerifiedException` · 쓰기·감사 0 → `verifyEmail()` → 같은 요청 성공) · 스토어 `SellerMemberServiceTest.unverifiedRefused_invitationKept_thenAcceptedAfterVerification`(거절 · 구성원·역할 0 · 초대 `PENDING` 유지 → 인증 뒤 같은 초대로 수락). **bite** ✅ 게이트 한 줄(`VerifiedEmailRequirement.require(account)`)을 지우자 대조군의 실패 쪽 단언(`ConsumerSiteRoleWriteUseCaseTest.java:140`)과 «이미 SELLER» 칸(:179)이 빨강(20 중 2 실패, rc=1) → 복원 `cmp` rc=0(바이트 동일). MySQL IT `ConsumerSiteRoleWriteIntegrationTest.unverifiedRefusedFirst_thenVerifiedGranted` 는 **작성만 — 이 호스트에 Docker 없음으로 미실행**(CI integration 레인).
- [x] **AC-3** — 소비자 로그인 · 쇼핑 · 팬 이용은 인증 여부와 무관하게 그대로(회귀 시험). — ✅ 게이트의 호출자 = 1(`ConsumerSiteRoleWriteUseCase.grant`) · 로그인·토큰 발급·가입·사이트 동의·재설정 경로는 무변경이고 그 시험 전부 초록(auth-service 1069 · account-service 715, 실패 0) · 회수는 인증을 안 묻는다 `revoke_doesNotAskForVerification`. MySQL 회귀 IT `unverifiedAccount_consumerUseUnchanged`(미인증 풀 계정의 스토어 멤버십 읽기 · 팬 첫 방문 동의 · 상태 조회 = 그대로)는 **작성만 — Docker 없음으로 미실행**(CI).
- [x] **AC-4** — 발송 실패 시 화면이 «권한을 못 받음» 이 아니라 «메일을 보내지 못했다 · 재시도» 를 말한다. — ✅ IdP 화면 `/email-verification` · `EmailVerificationPageSliceTest.send_failed_saysCouldNotSend_offersRetry`(실 템플릿 렌더 — «메일을 보내지 못했습니다. 잠시 뒤 다시 시도해 주세요.» + «다시 시도» 버튼 · 메시지 요소에 «권한» 없음; 첫 판은 템플릿 **주석**에 걸려 빨강 → 메시지 요소로 술어를 좁혔다) · 그 전제인 «실패를 안다» ✅ `SendVerificationEmailUseCaseTest`(삼킴 → 503/422 · 토큰 삭제 · 슬롯 반환). 🔴 스토어 **수락 화면 자체가 없다** — § 편차 1(소유자 판단).
- [x] **AC-5** — 공용 술어(예: `requireVerifiedEmail(account)`)가 하나의 집에 있고, 772 가 운영자 초대 수락에 그대로 쓴다는 것을 이 티켓의 노트에 적는다. — ✅ `VerifiedEmailRequirement.require(Account)` · § AC-5 노트 · `VerifiedEmailRequirementTest`.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` D2 · D3 · R1 · § Verification
- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D5 · § 부분 개정 (2026-10-07)
- `projects/iam-platform/specs/features/signup.md` (이메일 검증 — 초기 선택사항)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/account-api.md` · 셀러 구성원 수락 계약(ecommerce product-service · IAM 사이트 역할 쓰기)

# Edge Cases

- 이미 셀러 구성원인데 인증 안 된 계정 — 소급해 회수하지 않는다(«인증은 붙일 때의 증거일 뿐 유지 조건이 아니다», ADR-080 D5).
- 재발송 남용 — 기존 재발송 거절(`SendVerificationEmailUseCase.java:81`)과 속도 제한을 확인한다.

# Failure Scenarios

1. 게이트만 켜고 발송 경로가 없다 — 아무도 셀러가 되지 못한다(ADR-080 D3 «게이트는 옳은데 길이 없는 상태»).
2. 로그인에 게이트를 건다 — 메일이 안 나가는 동안 모든 소비자가 막힌다.

---

# 작업 기록 (2026-10-07 UTC)

## AC-0 — 착수 재측정 (코드 · 2026-10-07 UTC, 티켓·ADR 표를 복사하지 않고 다시 쟀다)

| 잰 것 | 결과 | 출처 (착수 시점 = `9e502afe6^` 트리) |
|---|---|---|
| 인증 메일 발송 어댑터 | **0** — 구현체는 `LoggingEmailVerificationNotifier` 하나(`@Profile("!prod")` :60, 토큰 미기록). prod 는 빈 없음 → 기동 실패(설계된 fail-fast) | account-service `infrastructure/notifier/LoggingEmailVerificationNotifier.java:60` |
| 비밀번호 재설정 메일 어댑터 | **0** — `LoggingEmailSender` 하나(`@Profile("!prod")` :62). 같은 공백 | auth-service `infrastructure/email/LoggingEmailSender.java:62` |
| 발송 실패 처리 | **삼킨다** — `catch (Exception e)` → WARN, 응답 204. «보냈다» 고 말하고 메일은 안 나간다 | `SendVerificationEmailUseCase.java:103` |
| `email_verified_at` 을 읽는 곳 | 재발송 거절(`SendVerificationEmailUseCase.java:81`) · OIDC userinfo `email_verified`(`OidcUserInfoMapper.java:88`) · 프로비저닝 상세 응답(`ProvisionedAccountDetailResult.java:31`) — **로그인 · 초대 · 운영자 생성 0** (ADR-080 표와 일치) | `getEmailVerifiedAt\|emailVerified` grep, iam `apps/*/src/main` |
| 셀러 수락 술어 | «초대 이메일 == 계정 이메일» 만(`ConsumerSiteRoleWriteUseCase.java:44-47` 문서 · `:78-83` 코드) — **인증 여부 안 봄** | account-service |
| 셀러 수락 경로 전체 | product-service `POST /api/seller-invitations/accept` → `SellerMemberService.accept`(`:135` IAM grant 가 쓰기보다 먼저) → `AccountServiceSellerSiteRoleClient.grant` → IAM `PATCH …/site-roles:grant` | product-service |
| 🔴 **수락 화면** | **없다** — web-store 에 `seller-invitations` 참조 0, 콘솔 `SellerMembers.tsx` 는 운영자 초대(토큰 1회 표시)뿐. `TASK-MONO-752` § 구현 기록이 «web-store 수락 화면은 후속 티켓 — 소유자 판단» 으로 남겼다 | `seller-invitations` grep, web-store `src` 0건 |
| 인증 메일 **도착 화면** | **없다** — `/verify-email` 을 여는 화면이 저장소 어디에도 없다(account-service JSON 엔드포인트뿐). IAM 게이트웨이 public-paths 에도 `verify-email` 없음 → 브라우저가 직접 부르면 엣지 401 | `verify-email` grep · gateway `application.yml:131-145` |
| 메일을 **보내게 하는** 화면 | **없다** — `resend-verification-email` 호출자 0, 가입은 메일을 보내지 않는다 | 같은 grep |
| 데모 IAM 프로필 | `SPRING_PROFILES_ACTIVE: e2e` — `@Profile("prod")` 어댑터는 데모에서 영영 안 돈다(지시의 실측 재확인) | `projects/iam-platform/docker-compose.e2e.yml:20` |
| 메일 서버 | 저장소 전체 **0**. ecommerce notification-service 는 `JavaMailSender` 발송기가 있는데 `MAIL_HOST:localhost` · 1025 로 늘 연결 실패 | `notification-service/application.yml:34-44` |
| Traefik basicauth/forwardauth | **0** (관측 UI kafka.iam · grafana.iam 은 무인증) | `basicauth` grep, infra |

### 소유자 결정 (2026-10-07 UTC — 지시로 전달됨, 이 노트에 기록)

1. **데모 수신 = 데모 스택 안의 Mailpit 컨테이너**, 웹 UI 는 **Traefik basic auth 뒤(소유자만)** — 공개 아님. 운영 경로는 나중에 **아무 SMTP**(예: AWS SES SMTP)로 **설정만** 바꾼다 — 공급자별 코드 없음.
2. 코드 = **설정 프로퍼티로 켜는 범용 SMTP 발송기 하나** — 스프링 프로필이 아니다(근거: 위 표의 e2e 프로필). prod fail-fast 유지: `prod` + 메일 꺼짐 → notifier 빈 없음 → 기동 실패(오늘과 같다).
3. **범위 추가**: auth-service 비밀번호 재설정 메일도 같은 장치·같은 설정으로 메운다(Scope 에 한 줄 추가).

### 이 티켓에서 정한 것 (구현자 선택 — 뒤집을 수 있다)

| 결정 | 내용 | 이유 |
|---|---|---|
| 프로퍼티 이름 | `iam.mail.enabled`(env `IAM_MAIL_ENABLED`, 기본 `false`) · `iam.mail.from` · `iam.mail.verification-link-base-url` · `iam.mail.password-reset-link-base-url` · SMTP 연결은 **표준 `spring.mail.*`**(`SPRING_MAIL_HOST`/`PORT`/`USERNAME`/`PASSWORD`) | 연결 설정을 새로 만들지 않는다 — SES SMTP 로 갈 때 Spring 문서 그대로 |
| 배선 표 | `enabled=true` → SMTP(프로필 무관) · `false` + `!prod` → 로깅 스텁 · `false` + `prod` → 없음 = 기동 실패 · `true` + `spring.mail.host` 없음 → `JavaMailSender` 없음 = 기동 실패 | 두 조건이 서로소라 환경마다 정확히 하나(또는 0 = fail-fast). `@ConditionalOnMissingBean`(BE-236 이 피한 것) 미사용 |
| 🔴 `spring.mail.host` 기본값을 **두지 않음** | application.yml 에는 타임아웃(5s)만 | Boot 는 그 키가 **있기만 하면**(빈 값이어도) `JavaMailSender` 를 만든다 → «켜졌는데 설정 안 됨» 이 기동 실패가 아니라 매 발송 실패로 숨는다 |
| 발송 실패 = **응답** | 일시 → `503 VERIFICATION_EMAIL_SEND_FAILED` · 영구 → `422 VERIFICATION_EMAIL_UNDELIVERABLE` · 실패 시 토큰 삭제 + 재발송 슬롯 반환(즉시 재시도 가능) | AC-4 는 «보내지 못했다» 를 말하려면 그 사실을 알아야 한다 — 204 로 삼키는 한 어떤 화면도 말할 수 없다 |
| 분류 | 영구 = 주소가 문제라는 **양성 증거**(파싱 실패 · `AddressException` · 5xx 수신자 거부 `invalidAddresses`)뿐, 그 밖·처음 보는 것 = 일시 | 판정 불가를 영구로 말하면 될 일을 포기하라고 안내한다(signup.md BE-580 원칙) |
| 가입은 메일을 **안 보낸다** | 사용자가 IdP 화면 `/email-verification` 에서 요청 | 가입이 메일 서버 장애에 묶이지 않는다(로그인·가입에 걸지 않는다 — ADR-080 Alternatives). ⚠️ ADR 의 «가입 → 메일 수신» 순서를 «가입 → 요청 → 메일 수신» 으로 읽었다 — 아래 § 편차 |
| 브라우저 화면 = **IdP(auth-service) Thymeleaf** | `/email-verification`(보내기, IdP 세션의 계정) · `/verify-email`(링크 도착, GET 은 버튼만 · POST 가 소비) | `/signup` · `/consent` 와 같은 자리·같은 서버 측 프록시. 게이트웨이 public-paths 를 넓히지 않는다. GET 이 소비하면 메일 스캐너가 사람보다 먼저 토큰을 쓴다 |
| 게이트 위치 | `ConsumerSiteRoleWriteUseCase.grant` 규칙 4b — 이메일 일치(4) **뒤**, 멤버십·**멱등 지름길 앞** | 다른 주소가 더 구체적인 거절 · 이미 SELLER 인 계정의 «두 번째 셀러 초대» 도 회사 권한이 새로 붙는 순간이다 · 회수 안 함(D5) |
| 오류 코드 | IAM `403 EMAIL_NOT_VERIFIED`(공용 이름 — 772 가 재사용) · 스토어 `403 SELLER_INVITATION_EMAIL_NOT_VERIFIED` | 같은 «권한이 붙는 쓰기» 의 거절을 호출자마다 다른 이름으로 만들지 않는다 |
| 메일함 basic auth 자격 | **SSM SecureString** `/portfolio-demo/mailpit/ui-basicauth-users`(htpasswd 한 줄 `<user>:<bcrypt>`) → 부팅이 `fetch-oauth-secrets.sh` 로 읽어 `MAILPIT_UI_BASICAUTH_USERS` · **읽었을 때만** `MAILPIT_UI_ENABLED=true` | 저장소에 자격 없음(763 과 같은 길). 🔴 라우터 기본 **꺼짐**(`traefik.enable=${MAILPIT_UI_ENABLED:-false}`) — 없음·실패·모양 틀림이 «인증 없는 메일함» 으로 떨어지는 갈래가 없다. 새 파일이 아니라 기존 파일에 넣은 이유: ci.yml · (z24) 두 하네스가 부팅 계약을 **이 파일 단위로** 스텁한다 — 파일·함수를 늘리면 둘이 «No such file / command not found» 로 죽는다 |
| 메일함 위치 | Mailpit 서비스는 **base** `projects/iam-platform/docker-compose.yml`(iam-net, 라우터 없음) · 데모 오버라이드가 traefik-net + 라우터 + basic auth 만 얹는다 | `check-suppressed-containers.sh` 의 유도 전제 «데모 오버라이드는 서비스를 추가하지 않는다» 유지 · 로컬 개발도 메일 싱크를 얻는다 · 라우터가 base 에 없으므로 `check-gateway-drift.sh` I2 무관 |
| 호스트명 | `mail.iam.<데모도메인>` — sslip.io 와일드카드가 그대로 해소(DNS 작업 0). Traefik alias 에도 추가(가드 (i) 쌍) | kafka.iam · grafana.iam 과 같은 모양 |
| 링크 호스트 | `${IAM_PUBLIC_URL}`(= `https://auth.hubwang.com`, Vercel auth-forwarder 가 catch-all 로 IdP 에 넘긴다) + `/verify-email` | `iam.<sslip>` 로 조립하면 부팅마다 IP 가 바뀌어 **어제 보낸 링크가 죽는다** |
| ecommerce 알림 메일 | `infra/demo/ecommerce-mail.override.yml` — notification-service `MAIL_HOST=iam-mailpit` + traefik-net(라우터 없음) | 지시대로 같은 컨테이너. erp-identity 와 같은 모양·같은 대가 |
| terraform | `aws_iam_role_policy.ec2_health` 에 `parameter/${local.name}/mailpit/*` 읽기 추가 | 🔴 **apply 필요**(소유자). apply 전에는 «읽기 실패» = 메일함 UI 만 닫힘, 발송은 정상 |
| 분류기 복사 | `SmtpFailureClassifier` 는 account-service 에만. auth-service 재설정 발송기는 분류하지 않는다 | 재설정 엔드포인트는 존재 여부를 숨기려 **언제나 204** — 종류를 말해 줄 상대가 없다. 공용 모듈은 ADR 이 필요하다(CLAUDE.md § project-scoped shared modules) |

## AC-5 — 공용 술어의 집 (772 를 위한 노트)

- **하나의 집**: account-service `application/service/VerifiedEmailRequirement.require(Account)` → `EmailNotVerifiedException` → `403 EMAIL_NOT_VERIFIED`(`GlobalExceptionHandler`). 도메인 쪽 술어는 `Account.hasVerifiedEmail()`.
- 지금 호출자 = **1**(`ConsumerSiteRoleWriteUseCase.grant`, 셀러 구성원 수락).
- 🔴 **`TASK-MONO-772` 는 운영자 초대 수락(D6)과 협력사 참여자 붙이기에서 이 메서드를 그대로 부른다** — 같은 조건의 `if (account.getEmailVerifiedAt() == null)` 를 새로 쓰지 않는다(호출자마다 사본이 생기면 한쪽만 고쳐진다). 오류 코드도 같은 `EMAIL_NOT_VERIFIED` 를 쓴다. 772 의 대조군도 이 티켓의 모양(같은 계정 · 미인증 실패 먼저 단언 · 인증 뒤 성공)을 따른다.
- 소급 회수 없음(D5) — 772 의 «이미 운영자인 미인증 계정» 도 같은 원칙이다.

## 편차 · 열린 것 (소유자 판단 필요)

1. 🔴 **스토어 수락 화면이 없다** (AC-0 표). Scope 의 «인증 안 된 계정이 수락하려 할 때 «이메일 인증 필요 + 재발송» 안내» 는 **API 수준까지만** 됐다 — 스토어 수락 API 가 `403 SELLER_INVITATION_EMAIL_NOT_VERIFIED`(계약: «클라이언트는 이메일 인증 필요를 말하고 IAM 의 인증 메일 화면을 제시한다»)를 구별해 내고, 그 «재발송» 화면(`/email-verification`)은 IdP 에 있다. **web-store 에 수락 화면을 새로 만드는 일은 하지 않았다** — `TASK-MONO-752` 가 «후속 티켓 — 소유자 판단» 으로 남긴 화면이고, 이 티켓의 지시 범위(«수락 UI 가 있는 곳에 표기») 밖의 새 화면이다. ⇒ **후속 티켓 기안 여부 = 소유자 결정.**
2. **«가입 → 인증 메일 수신»** — 가입은 메일을 보내지 않는다(위 결정 표). AC-1 의 라이브 순서는 «가입(또는 기존 계정) → IdP `/email-verification` 에서 보내기 → Mailpit → 링크 → 완료». 가입 직후 자동 발송을 원하면 한 줄 결정이다(가입 화면이 성공 뒤 같은 요청을 부르면 된다 — 가입 응답과 분리된 best-effort).
3. **비밀번호 재설정 화면도 없다** — 메일은 이제 나가지만 링크(`/password-reset?token=`)가 갈 IdP 화면이 없고, 재설정을 **요청하는** 화면도 없다(공개 API 만). 소유자 결정 3 은 «같은 공백을 같은 장치로» 였으므로 메일까지만 했다. 화면이 필요하면 후속.
4. 🔴 **메일함 basic auth 는 평문 HTTP 위**다(데모 엣지 entrypoint `web` :80). 자격이 요청마다 base64 로 실린다 — 소유자가 고른 방식의 전제이고, 그래서 README 에 «데모 전용 비밀번호, 재사용 금지» 를 적었다. TLS 가 필요해지면 auth-forwarder 류 Vercel 앞단이 다음 수.
5. IAM 게이트웨이 public-paths 에 `POST /api/accounts/signup/verify-email` 이 없어(계약은 «Auth required: No») 브라우저 직접 호출은 엣지 401 이다 — 이 티켓은 IdP 화면의 **서버 측** 호출로 우회했고 게이트웨이는 바꾸지 않았다(넓히는 것은 별도 결정).

## 게이트 (2026-10-07 UTC · 이 worktree · 각 rc 는 파이프 없이 `cmd > log; echo rc`)

| 게이트 | 결과 |
|---|---|
| `:account-service:test` | rc=0 · 715 tests · 실패 0 · skip 47 (새/바뀐 클래스: WiringTest 5 · SmtpNotifier 5 · Classifier 4 · VerifiedEmailRequirement 1 · SiteRoleWrite$Grant 16 · SendVerification 9 · 슬라이스 10+9) |
| `:auth-service:test` | rc=0 · 1069 tests · 실패 0 · skip 33 (첫 실행 rc=1 — AC-4 술어가 템플릿 주석에 걸림 → 고침 → 새 4클래스 18 tests 초록 → 전체 재실행 초록) |
| `:product-service:test` | rc=0 · 440 tests · 실패 0 (SellerMember·SiteRole 6클래스 29) |
| 🔴 Testcontainers IT | **미실행** — 이 호스트에 Docker 데몬 없음(`docker info` → npipe 없음). `ConsumerSiteRoleWriteIntegrationTest` 의 새 칸 둘 + 기존 두 칸 보정(`markEmailVerified`)은 CI integration 레인이 처음 돌린다 |
| bite (게이트 제거) | rc=1 · 20 중 2 실패(:140 대조군 실패 쪽 · :179) → 복원 `cmp` rc=0 |
| `infra/demo/verify-demo-wrapper.sh`(정적) | rc=0 «정적 검증 PASS» — (h) 이미지 18/18(`axllent/mailpit:v1.31.4` 포함) · (i) Host↔alias 12 · (p) 라우팅 경로 `consent email-verification login signup verify-email` · (z43) · (z24) 통과 |
| `infra/demo/test-fetch-oauth-secrets.sh` | rc=0 — 메일함 자격 있음/없음/거부/모양 틀림 4갈래 · 자격 문자열 출력 0 |
| `infra/demo/check-suppressed-containers.sh --derive` | rc=0 · «오버라이드가 서비스를 추가» 경고 0 (억제 = web-store · fan-platform-web 그대로) |
| `infra/demo/check-env-preflight.sh iam ecommerce` | rc=1 — 이 worktree 에 `ecommerce/.env` 가 없어서(`MINIO_ROOT_PASSWORD`). 설계된 동작이고 이 변경과 무관(부팅은 provision-demo-env.sh 가 먼저 만든다) |
| `check-label-drift.sh` · `check-host-drift.sh` | 미실행 — 둘 다 **데모 호스트의 실행 상태**(docker inspect · 호스트 체크아웃)를 재는 도구라 이 worktree 에서는 판정 불가 |
| `scripts/check-*.sh` 38개 | 36 rc=0 · rc=1 둘 다 환경 의존: `check-erp-single-tenant-ratchet`(«컨테이너 erp-platform-mysql 이 떠 있지 않습니다» — Docker 없음) · `check-prerendered-demo-verdict`(`DEMO_API_BASE` 미설정 + web-store 빌드 필요). `check-error-code-registry` · `check-domain-error-code-registry` · `check-gateway-drift` · `check-controller-slice-naming` rc=0 |
| 프런트 | 건드리지 않음(tsc/lint/vitest 해당 없음) |

## 라이브 ⚪ — 다음 재굽기 뒤 확인 절차 (AC-1 뒤 절반 · 메일함 · ecommerce 알림)

**전제(소유자, 재굽기 전/후)**: ① `htpasswd -nbB owner '<데모 전용 비밀번호>'` 한 줄을 SSM SecureString `/portfolio-demo/mailpit/ui-basicauth-users` 에 등록 ② `terraform apply`(정책 `parameter/${local.name}/mailpit/*` 읽기) ③ 이 커밋을 포함해 재굽기(`infra/demo/*` · compose · IAM 이미지가 바뀌었다).

1. 부팅 저널: `journalctl -u demo-stack | grep '\[mail\]'` → **0줄**이어야 한다(경고가 있으면 자격 읽기 실패 = 메일함 닫힘). `docker inspect iam-mailpit --format '{{index .Config.Labels "traefik.enable"}}'` → `true`.
2. 메일함 잠김 확인: `curl -s -o /dev/null -w '%{http_code}' http://mail.iam.<데모도메인>/` → **401**(자격 없이) · 자격을 주면 200.
3. 발송 배선: `docker exec iam-account-service-1 env | grep -E '^(IAM_MAIL_ENABLED|SPRING_MAIL_HOST)='` → `true` · `iam-mailpit`. 로그 `Started` 뒤 `SmtpEmailVerificationNotifier` 빈 오류 없음.
4. 한 바퀴: 스토어(`https://store.hubwang.com`)에 새 이메일로 가입·로그인 → 같은 브라우저로 `https://auth.hubwang.com/email-verification` → «인증 메일 보내기» → «인증 메일을 보냈습니다».
5. 메일함 `http://mail.iam.<데모도메인>/` 에 제목 «[IAM] 이메일 주소를 인증해 주세요» · 링크가 `https://auth.hubwang.com/verify-email?token=…` 인가.
6. 링크 → «이메일 인증 완료» → «이메일이 인증되었습니다». DB: `SELECT email_verified_at FROM accounts WHERE email='<그 주소>'` → 값 있음.
7. 게이트 라이브(선택): 콘솔에서 셀러 초대(그 주소) → 인증 **전** 계정으로 `POST /api/seller-invitations/accept` → `403 SELLER_INVITATION_EMAIL_NOT_VERIFIED`, 인증 뒤 같은 토큰 → 200.
8. 발송 실패 화면(선택): `docker stop iam-mailpit` → `/email-verification` 보내기 → «메일을 보내지 못했습니다 · 다시 시도» (5초 안 — SMTP 타임아웃) → `docker start iam-mailpit`.
9. ecommerce 알림: 주문 한 건 뒤 메일함에 notification-service 발신 메일이 쌓이는가 · `docker logs ecommerce-notification-service` 에 `localhost:1025` 연결 거부가 없는가.

## 바뀐 파일 (요약)

- 계약·스펙(먼저, 커밋 `9e502afe6`): `account-api.md` · `auth-api.md` · `internal/consumer-site-roles.md` · ecommerce `product-api.md` · `internal/product-to-account.md` · `signup.md` · `use-cases/signup-and-login.md` · `platform/error-handling.md`(4코드) · `rules/domains/saas.md`
- account-service: `VerifiedEmailRequirement` · 예외 3(`EmailNotVerified` · `EmailDelivery` · `VerificationEmailSendFailed`) · `Account.hasVerifiedEmail` · `ConsumerSiteRoleWriteUseCase` 규칙 4b · `SendVerificationEmailUseCase`(실패 = 응답) · 토큰 저장소 `releaseResendSlot` · `SmtpEmailVerificationNotifier` · `SmtpFailureClassifier` · `RecipientMask` · 로깅 스텁 조건 · 핸들러 · `application.yml` · `build.gradle`(starter-mail)
- auth-service: `SmtpEmailSender` · `LoggingEmailSender` 조건 · `EmailVerificationPageController` + 템플릿 2 · `AccountServicePort`/`AccountServiceClient` 2메서드 · `WebLoginSecurityConfig` 4경로 · `application.yml` · `build.gradle`
- product-service: `SellerInvitationEmailNotVerifiedException` · 클라이언트 매핑 · 핸들러 · 포트 javadoc
- 데모: `projects/iam-platform/docker-compose.yml`(mailpit) · `infra/demo/iam-traefik.override.yml`(메일 env · mailpit 라우터/basic auth · iam-oidc 경로 2) · `infra/demo/ecommerce-mail.override.yml`(신규) · `infra/demo/projects.sh` · `infra/traefik/docker-compose.yml`(alias) · `infra/demo/fetch-oauth-secrets.sh` + 시험 · `infra/demo/aws/terraform/main.tf` · `infra/demo/aws/README.md`
