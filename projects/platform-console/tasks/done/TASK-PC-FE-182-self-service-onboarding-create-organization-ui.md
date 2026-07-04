# TASK-PC-FE-182 — 셀프서비스 온보딩 "조직 만들기" UI (로그인했지만 운영자 아님 → 새 테넌트 생성 → 그 관리자로 콘솔 입장)

**Status:** done
**Area:** platform-console / console-web · **Scope:** OIDC 콜백 fail-closed 분기 + `(onboarding)` 라우트 그룹 + 온보딩 프록시 + 재-교환
**Type:** 보안-민감 (fail-closed 콜백 완화 — "not_provisioned"을 재-로그인 대신 온보딩으로) + 신규 UI 슬라이스
**Implemented:** branch `pc-fe-182-onboarding-ui` → **PR #2206** (open). `tsc --noEmit` + `next lint` + 신규/영향 `vitest` 35/35 green(로컬 OperatorsScreen 1건은 clean main 재현 = 기존 로컬 flake, CI Linux 권위).
**Depends on:** TASK-BE-474 / TASK-BE-474-fix-001 (백엔드 `POST /api/admin/onboarding/organizations` — CI+라이브 검증 완료, `oidc_subject` 설정으로 온보딩 후 token-exchange 200 증명), [ADR-MONO-044](../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md) §3.4 step 3
**Analysis model:** Opus 4.8 · **Impl model:** Opus (보안-민감 콜백 완화)

## Goal

[ADR-MONO-044](../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md) §3.4 step 3 — 백엔드 온보딩 엔드포인트(BE-474)는 CI + 라이브로 완주 증명됐고, **UI만 남았다**. 현재 console-web 콜백은
운영자-토큰 교환 `401`(=not_provisioned, "이 계정은 어느 테넌트의 운영자도 아님")을 만나면 **모든 세션 쿠키를 폐기하고 `/login?error=not_provisioned`
로 튕겨** "관리자에게 권한 부여를 요청하세요"를 보여준다 — AWS/GCP "가입하면 내 워크스페이스를 만들어 바로 소유" 와 정반대(운영자-in-the-loop 강제).

이 task는 그 not_provisioned 지점을 **셀프서비스 온보딩 진입점**으로 바꾼다: 로그인은 됐지만 운영자가 아닌 방문자를 재-로그인 대신 **`/onboarding` 조직 만들기
화면**으로 보내고, 그 화면에서 새 테넌트를 만들면(BE-474 엔드포인트) **그 자리에서 운영자 토큰을 재-교환**해 새 테넌트의 관리자로 콘솔에 입장시킨다. 플랫폼 운영자
개입 0.

## 보안 설계 (fail-closed 완화의 정확한 경계 — 리뷰 핵심)

- **완화 대상은 `not_provisioned`(교환 `401`, fail_closed) 한 분기뿐.** `operator_exchange_unavailable`(400/5xx/timeout — 실제 장애)은 **기존 그대로 전체 폐기 + 재-로그인** 유지(온보딩으로 보내지 않는다 — 장애를 "워크스페이스 없음"으로 오인시키면 안 됨).
- **IAM access token은 여전히 `/api/admin/**` 자격증명이 될 수 없다** (session.ts § ACCESS_COOKIE 불변식 유지). not_provisioned에서 access+refresh 를 **보존**하는 이유는 단 하나 — 온보딩 엔드포인트의 `subjectToken` 입력(계약이 요구하는 그 용도) + 온보딩 성공 후 운영자-토큰 재-교환의 입력이기 때문. 그 외 어떤 admin 경계에서도 쓰이지 않는다.
- **`isAuthenticated()`는 불변**(access AND operator 둘 다 필요) → access만 가진 pre-operator는 `(console)` 어떤 페이지에도 도달 못 한다. "로그인했지만 아직 운영자 아님"은 `(onboarding)` 그룹으로만 접근 가능한 **명시적 중간 상태**(operator 쿠키 부재 ⇔ pre-operator).
- **자기-승격 blast-radius는 백엔드 D2가 구조적으로 봉인**(새로 만든 테넌트에만 grant; `*`/기존 테넌트 불가). console은 오케스트레이션만.
- **CSRF**: 온보딩 POST는 동일 출처 fetch(SameSite=Lax가 cross-site POST 차단) — 기존 operator 프록시와 동일 자세.

## Scope

**IN:**

- `src/shared/config/env.ts` — `CONSOLE_ONBOARDING_URL`(default `http://iam.local/api/admin/onboarding/organizations`) + `ONBOARDING_TIMEOUT_MS`(default 10000 — 온보딩은 다단계 saga) 추가 + `getServerEnv()` 배선.
- `src/shared/lib/session.ts` — `hasPreOperatorSession()`(access present && operator absent) 헬퍼. `isAuthenticated()`/쿠키 불변식은 무변화.
- `src/app/api/auth/callback/route.ts` — not_provisioned(fail_closed) 분기: access+refresh(+id) **보존**, operator 미설정 + tenant/assumed 방어적 삭제, `/onboarding` 로 302. unavailable 분기는 무변화(전체 폐기 + `operator_exchange_unavailable`).
- `src/shared/api/errors.ts` — `OnboardingUnavailableError`(5xx/timeout/network) + MESSAGES `TENANT_ALREADY_EXISTS` 추가.
- `src/features/onboarding/api/onboarding-client.ts` (신규) — 서버 전용 `createOrganization({tenantId, organizationName}, subjectToken)` → `POST CONSOLE_ONBOARDING_URL` (AbortController timeout). taxonomy: 201 파싱 / 400 VALIDATION_ERROR→ApiError / 401→ApiError(TOKEN_INVALID) / 409→ApiError(passthrough) / 5xx·timeout·network→OnboardingUnavailableError. **subjectToken 절대 로깅 안 함**.
- `src/features/onboarding/api/types.ts` (신규) — 응답 zod 스키마 + 타입.
- `src/app/api/onboarding/organizations/route.ts` (신규, POST) — access 쿠키 서버-읽기(없으면 401) → body zod(tenantId 슬러그 `^[a-z][a-z0-9-]{1,31}$`, organizationName 1..100) → `createOrganization` → **성공 시 `exchangeForOperatorToken(access)` 재-교환**하여 OPERATOR_COOKIE + TENANT_COOKIE(=새 tenantId) 설정 → `{tenantId, ready:true}` 201. 재-교환 실패 시 org는 이미 생성됨 → `{tenantId, ready:false}` 201(클라가 재-로그인 유도). 에러 매핑(401→401, 400/409 passthrough, unavailable→503).
- `src/app/(onboarding)/layout.tsx` (신규) — 가드: access 없음→`/login`, operator 있음(이미 운영자)→`/`, 그 외 pre-operator 렌더(사이드바 없는 중앙 카드 셸).
- `src/app/(onboarding)/onboarding/page.tsx` (신규) — 인트로 + `<CreateOrganizationForm/>`.
- `src/features/onboarding/components/CreateOrganizationForm.tsx` (신규, `'use client'`) — tenantId/organizationName 입력 + 클라 검증 + POST. ready:true→`/` 하드 내비(쿠키 재-읽기), ready:false→`/login`, 에러→inline(messageForCode).
- `src/features/onboarding/index.ts` (신규) — 배럴.
- `src/app/(auth)/login/page.tsx` — not_provisioned 메시지 갱신(콜백이 더는 이 코드를 안 보내므로 사실상 dead지만, 셀프서비스 현실과 모순되는 "관리자에게 요청" 문구를 "조직 만들기" 안내로 정정).
- 테스트: `tests/unit/auth-routes.test.ts` not_provisioned 케이스를 **신규 불변식**으로 갱신(access 보존·`/onboarding` 리다이렉트·operator 부재·tenant/assumed 삭제; unavailable은 전체 폐기 유지) + `tests/unit/onboarding-proxy.test.ts`(신규) + `tests/unit/onboarding-client.test.ts`(신규) + `tests/unit/features/onboarding/CreateOrganizationForm.test.tsx`(신규).

**OUT (ADR-044 deferred — 의도적 비범위):**

- D4 이메일-인증 강제, 승인 큐(D4-C), rate-limit/cap — 백엔드 슬라이스가 "인증만"; UI도 동일.
- D6-B 도메인 auto-subscribe 선택 UI, org 프로필 관리, multi-org 스위처.
- 백엔드/계약 변경 — 전부 소비만(BE-474에서 완료).

## Acceptance Criteria

- [ ] **AC-1** 콜백 운영자-교환 `401`(fail_closed) → access+refresh 쿠키 **보존**, operator 쿠키 미설정, `/onboarding` 로 302(재-로그인 아님). tenant/assumed는 방어적 삭제.
- [ ] **AC-2** 콜백 교환 `5xx`/timeout(unavailable) → **기존 그대로** 전체 세션 폐기 + `/login?error=operator_exchange_unavailable`(온보딩으로 안 감).
- [ ] **AC-3** `(onboarding)` 가드: operator 쿠키 있으면 `/`로, access 없으면 `/login`으로, access만 있으면(pre-operator) 온보딩 렌더.
- [ ] **AC-4** `POST /api/onboarding/organizations` — access 없으면 401; body 슬러그/이름 위반 시 400/422; 정상 시 백엔드 201 후 운영자-토큰 재-교환 성공하면 OPERATOR_COOKIE + TENANT_COOKIE(새 테넌트) 설정 + `{ready:true}`.
- [ ] **AC-5** 백엔드 201이지만 재-교환 실패(timeout 등) → org는 생성 유지, `{ready:false}` 반환(클라가 `/login`으로 유도) — 세션 파괴/오류 502 없음.
- [ ] **AC-6** 온보딩 클라이언트 taxonomy: 400→ApiError(VALIDATION_ERROR), 401→ApiError(TOKEN_INVALID), 409 TENANT_ALREADY_EXISTS/OPERATOR_EMAIL_CONFLICT→ApiError(passthrough), 5xx/timeout→OnboardingUnavailableError. subjectToken 미로깅.
- [ ] **AC-7** 폼: 슬러그/이름 클라 검증, 제출 성공 시 `/`로 하드 내비(운영자로 콘솔 입장), 에러 시 inline actionable(messageForCode), 이중 제출 방지(pending 비활성).
- [ ] **AC-8** `pnpm lint` + `pnpm exec tsc --noEmit` + `pnpm exec vitest run` green(기존 auth-routes 케이스는 신규 불변식으로 갱신, 나머지 회귀 0).

## Related Specs

- `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` — D2(새 테넌트 confine), D3(fail-closed saga), D5(born-unified), D6(TENANT_ADMIN+BILLING), D7(vertical slice, console-login-scoped 증명). §3.4 step 3 = 이 task.
- `projects/platform-console/apps/console-web` architecture.md § Forbidden Dependencies — 브라우저 직접 IAM 호출 금지 → 동일 출처 프록시 경유.
- console-integration-contract § 2.1(access token = subject_token only 불변식) / § 2.6(운영자-토큰 교환 재사용).

## Related Contracts

- `projects/iam-platform/specs/contracts/http/onboarding-api.md` § `POST /api/admin/onboarding/organizations` (BE-474 — 소비만; 요청 `{subjectToken, tenantId, organizationName}`, 201 `{tenantId, operatorId, roles, status}`, 에러 400/401/409/5xx).
- `projects/iam-platform/specs/contracts/http/admin-api.md` § `POST /api/admin/auth/token-exchange` (재-교환 재사용, 무변화).

## Edge Cases

- **재-교환 타이밍**: BE-474-fix-001이 신규 운영자 `oidc_subject=account_id` 설정 → 온보딩 201 직후 `findByOidcSubject` 성공(라이브 증명됨). 그래도 재-교환 실패는 fail-soft(ready:false).
- **이미 운영자인 사람이 /onboarding 수동 방문** → 가드가 `/`로. **비로그인 방문** → `/login`으로.
- **슬러그 중복(409 TENANT_ALREADY_EXISTS)** → inline "이미 사용 중인 조직 ID" (보상 불필요 — 백엔드가 아무것도 안 만듦).
- **access 토큰 만료 중 온보딩 제출** → 백엔드 401 TOKEN_INVALID → 프록시 401 → 클라가 재-로그인 유도.
- **비-ASCII organizationName** → 백엔드가 max 100만 강제(슬러그만 ASCII); 이름은 유니코드 허용.

## Failure Scenarios

- **온보딩 엔드포인트 도달 실패/5xx/timeout** → OnboardingUnavailableError → 프록시 503 → 폼 inline "일시적으로 조직을 만들 수 없습니다" + 재시도 가능(세션 유지). access 쿠키 안 지움.
- **백엔드 201 후 재-교환 5xx** (AC-5) → org 생성 유지, ready:false, 클라 `/login`(재-로그인 시 이제 운영자 → 콘솔). 데이터 유실 0.
- **콜백 완화가 기존 로그인 회귀?** → 정상 운영자(교환 200)는 코드 경로 무변화; unavailable(장애) 경로도 무변화. 오직 not_provisioned만 분기 변경 — auth-routes 테스트로 세 경로(200/401/5xx) 각각 확증.
- **pre-operator가 access 쿠키로 다른 admin 프록시 때리기?** → 그 프록시들은 `getOperatorToken()`(operator 쿠키) 요구 → 부재 시 401. access는 어디서도 admin 자격증명 아님(불변식 유지).
