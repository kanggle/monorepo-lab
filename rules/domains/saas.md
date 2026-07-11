# Domain: saas

> **Activated when**: `PROJECT.md` declares `domain: saas`.

---

## Scope

일반 B2B/B2C SaaS 플랫폼. 특정 산업(금융·의료·이커머스 등)에 귀속되지 않는 가로축 SaaS 인프라 — 계정·권한·감사·요금 같은 교차 기능이 제품 본연의 가치를 이루는 시스템.

이 도메인은 개별 제품(예: 커뮤니티, 스트리밍, LMS)이 *위에 얹히는* **플랫폼 레이어**를 대상으로 한다. 제품 고유의 도메인 객체(예: Order, Post, Video)는 각 제품 팀이 별도 서비스로 소유한다.

---

## Bounded Contexts (표준)

SaaS 도메인 프로젝트가 일반적으로 가지는 bounded context 묶음. 실제 서비스 분할은 트래픽·소유권·변경 빈도에 따라 달라질 수 있다.

| Bounded Context | 책임 |
|---|---|
| **Identity** | 사용자 등록, 자격 증명(credentials) 저장, 로그인, 토큰 발급·회전 |
| **Account / Profile** | 계정 상태(active/locked/dormant/deleted), 프로필 메타데이터, 선호 설정 |
| **Access / Authorization** | 역할, 권한, 리소스 스코프, 정책 평가 |
| **Security Analytics** | 로그인 이력, 비정상 행위 탐지, 리스크 스코어, 자동 잠금 |
| **Audit** | 불변 감사 로그, 접근·변경 추적, 관리자 작업 기록 |
| **Admin / Operations** | 운영자 대시보드, lock/unlock, 강제 로그아웃, 감사 조회 |
| **Notification** (선택) | 이메일/SMS/푸시 등 계정 관련 메시지 전송 채널 |
| **Billing / Metering** (선택) | 요금 계획, 사용량 계측, 청구 (플랫폼이 과금형인 경우) |

각 context는 자체 데이터 저장소를 가지며, context 간 통신은 **이벤트** 또는 **잘 정의된 내부 HTTP**로만 이루어진다.

---

## Ubiquitous Language

- **Account** — 플랫폼에 등록된 하나의 사용자·조직 레코드. 자격 증명과 프로필을 모두 포함한 개념이 아니라, **논리적 ID**만을 가리킨다.
- **Credentials** — 비밀번호 해시·OAuth 연결·2FA 시크릿 등 인증에 사용되는 비밀 데이터. Account와 **물리적으로 분리** 저장.
- **Profile** — 이름·이메일·전화·선호 설정 등 비밀이 아닌 계정 속성. Account와 함께 저장될 수 있으나 credentials와는 분리.
- **Session** — 로그인 성공 후 발급된 access/refresh 토큰 쌍 + 클라이언트 컨텍스트(device, ip). 명시적 또는 암묵적으로 만료.
- **Lockout** — 로그인 실패·운영자 명령·자동 탐지 등으로 계정이 일시 또는 영구적으로 인증을 거부하는 상태.
- **Login Attempt** — 성공·실패를 불문한 모든 로그인 시도 기록. Security Analytics의 원본 데이터.
- **Suspicious Activity** — 정상 사용자 행동과 괴리된 시도(지리적 이례성, 속도 이상, 디바이스 변경 등). 자동 잠금 또는 알림 대상.
- **Audit Event** — 불변 감사 로그에 기록되는 상태 변경 또는 접근. Security Analytics와 달리 **규제·책임 추적** 목적.

이 용어들은 코드·API·문서에서 일관되게 사용되어야 한다. 예를 들어 `User` 대신 `Account` 또는 `Profile`을 명시적으로 선택한다.

---

## Standard Error Codes

SaaS 도메인에서 공통으로 발생하는 에러는 [../../platform/error-handling.md](../../platform/error-handling.md)의 전역 카탈로그에 등록되어야 한다. 이 도메인 특유의 코드:

- `ACCOUNT_NOT_FOUND` — 조회 대상 계정이 존재하지 않음
- `INVALID_CREDENTIALS` — 로그인 자격 증명 불일치 (platform-common canonical; HTTP 응답 code. login-failed 이벤트의 `failureReason` enum 은 별도 계약으로 `CREDENTIALS_INVALID` 사용 — TASK-MONO-246)
- `ACCOUNT_LOCKED` — 계정이 잠김 상태 (수동 또는 자동)
- `ACCOUNT_DORMANT` — 장기 미사용으로 휴면 처리됨
- `ACCOUNT_DELETED` — 삭제된 계정 (복구 불가 또는 유예 기간 중)
- `TOKEN_EXPIRED` — access/refresh 토큰 만료
- `TOKEN_REUSE_DETECTED` — 회전된 refresh token이 재사용됨 (세션 전체 무효화 대상)
- `SESSION_REVOKED` — 세션이 명시적으로 폐기됨
- `LOGIN_RATE_LIMITED` — 로그인 시도가 rate limit 초과
- `PERMISSION_DENIED` — 인증은 성공했으나 해당 리소스에 대한 권한 부재

### Admin Operations (admin-service 전용)

다음 코드는 [../../platform/error-handling.md](../../platform/error-handling.md)의 `Admin Operations [domain: saas]` 섹션에 등록되어 있다. admin 경로에서만 발생하며 공개 API에서는 사용하지 않는다.

- `BATCH_SIZE_EXCEEDED` — bulk 명령의 `accountIds`가 배치 상한(100) 초과 (422)
- `IDEMPOTENCY_KEY_CONFLICT` — 동일 `Idempotency-Key`로 다른 payload 재전송 (409)
- `AUDIT_FAILURE` — 감사 row 기록 실패 시 명령 중단 (S5·audit-heavy 교차) (500)
- `ACCOUNT_NOT_FOUND` — 대상 계정 미존재 (admin 경로 전용 맥락; 공개 account-api와 의미 동일하나 등록 맥락이 admin) (404)
- `STATE_TRANSITION_INVALID` — 현재 상태에서 허용되지 않는 상태 전이 (admin 경로; S3 상태 기계와 교차) (422)
- `TOTP_NOT_ENROLLED` — TOTP 미등록 운영자가 복구 코드 재발급 요청 시 (admin 경로; 재발급 전 `/api/admin/auth/2fa/enroll` 선행 필요) (404)
- `ROLE_GRANT_FORBIDDEN` — 부여자가 자기 수준을 넘는 role 을 부여 시도 (no-escalation 가드, ADR-MONO-024 D3) (403)
- `ASSIGNMENT_ALREADY_EXISTS` — 해당 테넌트/도메인에 이미 배정된 운영자 (409)
- `ACCESS_CONDITION_UNMET` — 엔드포인트에 걸린 접근 조건 미충족 (`SOURCE_IP`·`TIME_WINDOW`·`RESOURCE_TAG` AND 결합, ADR-MONO-026/028/029). restriction-only — 이미 인가된 행위를 **좁힐 뿐 부여하지 않는다** (403)
- `OPERATOR_ACCOUNT_NOT_FOUND` — 연결 대상 account 신원 미존재 (422)
- `OPERATOR_ALREADY_LINKED` — 이미 account 신원에 연결된 운영자 (409)
- `IDENTITY_LINK_EMAIL_MISMATCH` — 운영자 이메일과 연결 대상 신원의 이메일 불일치 (422)
- `ACCOUNT_IDENTITY_UNRESOLVABLE` — 권위 서비스에서 운영자의 account 신원을 해석 실패 (422)
- `OPTIMISTIC_LOCK_CONFLICT` — `admin_operators.version` 낙관적 락 충돌 (409). Platform-Common `CONFLICT`/`CONCURRENT_MODIFICATION` 의 **등록된 의도적 별칭** — admin-service 가 부모 핸들러 매핑을 오버라이드해 `admin-api.md` 가 요구하는 canonical 코드를 낸다 (TASK-BE-306)

### Tenant 축 — 도메인 구독 / org-node / 파트너십

다음 코드는 [../../platform/error-handling.md](../../platform/error-handling.md) 의 `Tenant  [domain: saas]` 섹션에 등록되어 있다. 권위는 `account-service` 이고 `admin-service` 는 얇은 커맨드 게이트웨이로서 권위의 코드를 **그대로 통과**시킨다 — 불변식을 재구현하지 않는다(중복 검사는 반드시 드리프트한다).

**도메인 구독** (ADR-MONO-019 / ADR-MONO-047)

- `SUBSCRIPTION_ALREADY_EXISTS` — 이미 구독 중인 도메인 (409)
- `SUBSCRIPTION_TRANSITION_INVALID` — 허용되지 않는 구독 상태 전이 (409)
- `SUBSCRIPTION_DOMAIN_OUT_OF_CEILING` — 요청 도메인이 테넌트의 org-node **entitlement ceiling** 밖 (422). `BOUNDED` 는 열거된 도메인만 허용하고 `UNBOUNDED` 는 천장 없음(교집합 항등원 — **"오늘 아는 도메인 전부" 가 아니다**)

**org-node 계층** (ADR-MONO-047) — `org_node` 는 tenant **위**의 data-less 그룹핑 노드다. tenant 를 **묶을 뿐 중첩하지 않으므로** `tenant_id` 는 여전히 단일 flat 격리키다.

- `ORG_NODE_NOT_FOUND` — 노드 미존재 또는 호출자 subtree 밖 (404)
- `ORG_NODE_CYCLE` — 요청한 `parent_id` 가 트리에 사이클을 만든다 (422)
- `ORG_NODE_DEPTH_EXCEEDED` — 최대 깊이(5) 초과 (422)
- `ORG_NODE_CEILING_NOT_SUBSET` — 자식 ceiling 이 부모의 부분집합이 아니다 — 노드는 상속받은 것을 **좁히기만** 할 수 있고 넓힐 수 없다 (422)
- `ORG_NODE_NOT_EMPTY` — 자식 노드/테넌트가 남아 있어 삭제 거부 (422)
- `ORG_NODE_SELF_CEILING_DENIED` — `ORG_ADMIN` 이 **자기 노드**의 ceiling 을 편집 시도 (아래 노드만 좁힐 수 있다 — no-self-escalation) (403)
- `ORG_NODE_INVARIANT_VIOLATION` — **폴백 전용** (422). 권위가 코드를 비워 보낼 때만 나타나므로 **이 코드가 보이면 두 서비스가 드리프트했다는 뜻**이다
- `ORG_ADMIN_GRANT_OUT_OF_CEILING` — `ORG_ADMIN` 이 자기 노드 ceiling 밖 도메인을 부여 시도 (422)

**cross-org 파트너십** (ADR-MONO-045)

- `PARTNERSHIP_NOT_FOUND` (404) / `PARTNERSHIP_ALREADY_EXISTS` (409) / `PARTNERSHIP_TRANSITION_INVALID` (409)
- `PARTNERSHIP_SCOPE_DENIED` — 호출자 테넌트가 해당 파트너십의 당사자가 아님 (403)
- `PARTNERSHIP_SCOPE_INVALID` — 요청 스코프가 부정형이거나 허용되지 않음 (422)
- `PARTICIPANT_NOT_FOUND` (404)
- `PARTICIPANT_NOT_OWN_OPERATOR` — 참가자로 추가하려는 운영자가 호출 테넌트 소속이 아님 (422)
- `PARTICIPANT_SCOPE_EXCEEDS_DELEGATION` — 참가자 스코프가 파트너십이 위임한 범위보다 넓다 — 참가자는 **부분집합만** 받을 수 있다 (422)

**기타**

- `TENANT_SCOPE_MISMATCH` — 요청 테넌트가 운영자가 현재 행위 중인 테넌트와 불일치 (403). 토큰 **스코프**에 관한 `TENANT_SCOPE_DENIED` 와 구분된다

### Email Verification (account-service 전용)

다음 코드는 [../../platform/error-handling.md](../../platform/error-handling.md)의 `Email Verification [domain: saas]` 섹션에 등록되어 있다. account-service 이메일 인증 플로우(`POST /api/accounts/signup/verify-email`, `POST /api/accounts/signup/resend-verification-email`)에서만 발생한다.

- `TOKEN_EXPIRED_OR_INVALID` — 이메일 인증 토큰 만료·미존재·이미 소비됨 (400)
- `EMAIL_ALREADY_VERIFIED` — 해당 계정의 이메일이 이미 인증된 상태 (409)
- `RATE_LIMITED` — 이메일 재발송 rate limit 초과 (5분 내 1회). `RATE_LIMIT_EXCEEDED`(로그인 rate limit, gateway/auth-service)와 구분. (429)

---

## Integration Boundaries

### 외부(플랫폼 경계 바깥)
- **이메일/SMS 프로바이더** — 가입 확인, 비밀번호 재설정, 비정상 로그인 알림 (`integration-heavy` trait 규칙을 따른다)
- **OAuth 제공자** — 소셜 로그인 (Google, Apple, Kakao 등)
- **IdP 페더레이션** — 엔터프라이즈 SSO (SAML, OIDC)
- **리스크 인텔리전스** (선택) — IP 평판, 디바이스 지문, 봇 탐지 서비스

### 내부(같은 프로젝트 내 다른 서비스)
- Identity ↔ Profile은 내부 HTTP로 credential lookup 수행. 절대 공개 API로 노출 금지.
- Security Analytics는 Identity의 이벤트 스트림(`*.login.*`)을 구독. 동기 호출 없음.
- Admin은 Identity·Profile·Audit에 대한 **read + 특권 command** 경로를 가지지만, 별도 인증 경계(운영자 전용 토큰) 뒤에 숨겨야 한다.

### 내부 이벤트 카탈로그 (권장)
- `<prefix>.login.attempted` / `.succeeded` / `.failed`
- `<prefix>.token.refreshed` / `.reuse.detected`
- `<prefix>.account.created` / `.status.changed` / `.deleted`
- `<prefix>.session.revoked`
- `<prefix>.suspicious.detected`
- `<prefix>.admin.action.performed`

---

## Mandatory Rules

### S1. Credentials와 Profile의 물리적 분리
비밀번호 해시·2FA 시크릿·OAuth 토큰은 프로필 테이블과 **별도 테이블(또는 별도 서비스)** 에 저장한다. 프로필 조회가 자격 증명에 접근할 수 없어야 한다.

### S2. 내부 API는 공개 API와 분리
서비스 간 credential lookup, 강제 로그아웃, 관리자 명령 같은 특권 엔드포인트는 **공개 게이트웨이를 거치지 않는** 내부 경로로만 노출한다. 공개 API 스펙에는 등장하지 않는다.

### S3. 계정 상태는 명시적 상태 기계로 관리
`active / locked / dormant / deleted` 같은 상태 전이는 사전 정의된 상태 기계(transactional trait T4)를 따른다. 직접 `UPDATE` 금지.

### S4. 모든 인증 경로는 로그 이벤트를 발행
성공·실패·rate limit·토큰 재사용 탐지 등 모든 인증 분기는 해당 시점에 이벤트를 발행한다. Security Analytics와 Audit이 이를 소비한다. (`audit-heavy` trait과 교차)

### S5. Admin 경로는 이중 인증 + 감사 로그 필수
운영자가 수행하는 모든 상태 변경·조회는 (1) 별도 인증 경계를 통과하고 (2) 감사 로그에 operator ID와 사유가 기록되어야 한다.

### S6. 탈퇴/삭제는 즉시 삭제 아닌 유예 + 익명화 경로
GDPR·PIPA 요건에 맞춰 계정 삭제는 **즉시 물리 삭제가 아닌 논리 삭제 + 유예 기간 + 개인정보 익명화** 경로로 설계한다. (`regulated` trait과 교차)

---

## Forbidden Patterns

- ❌ **Profile 테이블에 password_hash 컬럼을 두는 것**
- ❌ **credential lookup 엔드포인트를 공개 게이트웨이에 노출**
- ❌ **계정 상태를 직접 `UPDATE`로 변경** (상태 기계 우회)
- ❌ **관리자 작업이 감사 로그 없이 수행됨**
- ❌ **삭제 요청이 즉시 `DELETE` 쿼리로 수행됨**
- ❌ **로그인 실패 원인을 구체적으로 응답에 노출** (credential stuffing 용이화) — 일반화된 `INVALID_CREDENTIALS`만 반환

---

## Required Artifacts

1. **Bounded context 맵** — 각 context의 책임·소유 데이터·통신 방향. 위치: `specs/services/` 전반 또는 `platform/service-boundaries.md`
2. **계정 상태 기계 다이어그램** — `specs/services/<account-service>/state-machines/account-status.md`
3. **에러 코드 등록** — 위 Standard Error Codes가 [../../platform/error-handling.md](../../platform/error-handling.md)에 존재
4. **내부 vs 공개 API 구분 표** — 어떤 엔드포인트가 내부 전용인지
5. **감사 이벤트 카탈로그** — 어떤 액션이 감사 로그에 기록되는지 (`audit-heavy` trait과 공동 소유)
6. **삭제/익명화 절차 문서** — 유예 기간, 어떤 필드가 익명화되는지, 복구 가능 여부 (`regulated` trait과 공동 소유)

---

## Interaction with Common Rules

- [../../platform/architecture.md](../../platform/architecture.md)의 서비스 경계 원칙을 따르되, 위 bounded context 구분을 참조한다.
- [../../platform/security-rules.md](../../platform/security-rules.md)의 비밀 관리 규칙이 credentials 저장·전송에 엄격하게 적용된다.
- [../../platform/error-handling.md](../../platform/error-handling.md)에 위 Standard Error Codes가 등록되어야 한다.
- [../traits/regulated.md](../traits/regulated.md) 및 [../traits/audit-heavy.md](../traits/audit-heavy.md)와 함께 선언되는 경우가 많으며, 규칙은 누적 적용된다.

---

## Checklist (Review Gate)

- [ ] Credentials 저장소가 Profile과 물리적으로 분리되어 있는가? (S1)
- [ ] 공개 API와 내부 API가 경로·인증 경계로 분리되어 있는가? (S2)
- [ ] 계정 상태가 상태 기계로 관리되고 직접 UPDATE가 없는가? (S3)
- [ ] 모든 인증 분기가 이벤트로 발행되는가? (S4)
- [ ] Admin 경로가 별도 인증 + 감사 로그를 거치는가? (S5)
- [ ] 삭제가 유예 + 익명화 경로로 설계되어 있는가? (S6)
- [ ] Bounded context 맵과 상태 기계 문서가 존재하는가?
- [ ] 표준 에러 코드가 플랫폼 카탈로그에 등록되어 있는가?
- [ ] `ACCOUNT_LOCKED` 같은 상태를 응답에서 credential stuffing 없이 노출하는가? (메시지 균일화 확인)
