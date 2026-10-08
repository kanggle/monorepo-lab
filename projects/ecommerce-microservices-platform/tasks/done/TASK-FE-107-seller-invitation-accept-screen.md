# Task ID

TASK-FE-107

# Title

web-store **셀러 초대 수락 화면** — 콘솔이 준 초대 토큰으로 `POST /api/seller-invitations/accept` 를 부르는 길 · «이메일 인증 필요» 안내 (TASK-MONO-770 의 남은 화면)

# Status

done

# Owner

frontend

# Task Tags

- web-store
- frontend

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 계약이 이미 있는 API 위의 화면 하나 · 오류 문구 분기.

---

# Dependency Markers

- **선행**: 없음 — 계약(`product-api.md` § POST /api/seller-invitations/accept)과 구현(TASK-MONO-752 · TASK-MONO-770)이 이미 main 에 있다.
- 출처: `TASK-MONO-770` 의 남은 소유자 판단 «스토어 수락 화면» (2026-10-07).
- 관련: `ADR-MONO-079` D5(셀러 구성원) · `ADR-MONO-080` R1(회사 권한이 붙는 쓰기 = 인증된 이메일).

# Goal

셀러 구성원 초대는 콘솔에서 발급되고 토큰이 **한 번** 화면에 뜬다(`platform-console` `features/ecommerce-ops/components/SellerMembers.tsx:218-220` `seller-invite-token`). 운영자가 그 토큰을 사람에게 건네면 그 사람이 스토어에 로그인해 수락해야 하는데, **web-store 에 수락 화면이 없다**(`apps/web-store/src` 에서 `seller-invitations` grep 0, 2026-10-08 UTC 실측). 그래서 수락 API(`product-api.md:376-415`)는 화면 어디에서도 불리지 않는다.

- 로그인한 스토어 사용자가 토큰을 넣고(또는 `?token=` 링크로 들어와) 수락한다.
- 응답 오류를 사람 말로 바꾼다. 특히 `403 SELLER_INVITATION_EMAIL_NOT_VERIFIED` → «이메일 인증이 필요합니다» + IAM 인증 메일 화면(`/email-verification`)으로 가는 길 — 인증 뒤 **같은 초대를 다시 수락할 수 있다**(계약: 거절은 초대를 소모하지 않는다).

# Scope

## In Scope

- web-store 라우트 하나(예: `(store)/seller-invitations/accept`) — 로그인 필요(비로그인 → 기존 로그인 흐름 뒤 돌아오기) · 토큰 입력 칸 + `?token=` 미리 채움.
- 동일 출처 API 라우트 또는 기존 web-store API 클라이언트 모양 그대로 `POST /api/seller-invitations/accept`.
- 오류 문구 표(계약의 오류 전부): 401 · 403 `SELLER_INVITATION_EMAIL_MISMATCH` · 403 `SELLER_INVITATION_EMAIL_NOT_VERIFIED` · 404 `SELLER_INVITATION_NOT_FOUND` · 409 `SELLER_INVITATION_ALREADY_USED` · 409 `SELLER_NOT_ACTIVE` · 409 `SELLER_MEMBER_ACCOUNT_NOT_ELIGIBLE` · 410 `SELLER_INVITATION_EXPIRED` · 503.
- 성공 화면: 어느 셀러의 구성원이 됐는지 + 다음에 갈 곳.
- (선택) 콘솔 `SellerMembers` 의 토큰 옆에 «수락 링크 복사» — 링크 = `{스토어 주소}/seller-invitations/accept?token=…`. 🔴 이 칸은 platform-console 파일이므로 넣는다면 같은 PR 의 별도 커밋으로, 아니면 후속으로 남긴다(AC-0 에서 정한다).
- 단위 시험 + bite.

## Out of Scope

- 초대 **메일** 발송(계약: «there is no mail delivery path» — 메일을 붙이는 것은 별도 소유자 결정).
- 수락 API 의 판정 변경.

# Acceptance Criteria

- [x] **AC-0** — 착수 시 재측정 (2026-10-08 UTC):
  - **로그인 리다이렉트(돌아올 주소 보존)**: `apps/web-store/src/middleware.ts:76-81` — 보호 경로에 비로그인으로 들어오면 `loginUrl.search = `?from=${encodeURIComponent(pathname + search)}`` 로 `/login` 에 307. `features/auth/ui/LoginForm.tsx:37-46` 의 `resolveCallbackUrl` 이 `from`(상대경로만, `//` 시작 등 외부 URL 은 `/` 로 폴백)을 읽어 `features/auth/model/auth-context.tsx:36-38` 의 `login(callbackUrl)` → `signIn('iam', { callbackUrl })` 에 넘긴다. 이 경로를 새 라우트에도 그대로 쓴다(보호 그룹에 넣기만 하면 됨) — 새 코드 불필요.
  - **API 클라이언트 모양**: `shared/config/api.ts:49-80` 의 `apiClient`(axios 래퍼, 클라이언트에선 same-origin BFF `/api/bff` 프록시로 감) + `packages/api-client/src/services/product-api.ts` 의 `createProductApi(client)` 패턴 — `entities/order/api/order-api.ts`, `features/coupon/api/coupon-api.ts` 가 동일 모양(`client.post<Resp>('/api/...', body)`). 신규 `acceptSellerInvitation` 을 `createProductApi` 에 추가하고 `features/seller-invitation/api/seller-invitation-api.ts` 에서 얇게 감싼다.
  - **IAM 인증 메일 화면 주소를 web-store 가 이미 아는가**: 그렇다 — `shared/auth/auth-callbacks.ts:34` `export const OIDC_ISSUER_URL = process.env.OIDC_ISSUER_URL ?? 'http://iam.local'`(서버 전용 env, `NEXT_PUBLIC_` 아님). IAM 쪽 라우트는 `projects/iam-platform/apps/auth-service/.../EmailVerificationPageController.java:53` `@GetMapping("/email-verification")` 로 실존 확인. 브라우저 JS 는 이 env 를 못 읽지만, 수락 화면을 **Server Component 페이지**(`app/(store)/seller-invitations/accept/page.tsx`)로 두고 거기서 `OIDC_ISSUER_URL` 을 읽어 `${OIDC_ISSUER_URL}/email-verification` 문자열을 client 폼에 prop 으로 내려주면 새 env 변수나 API 라우트 없이 끝난다(`shared/auth/federated-logout.ts` 가 같은 상수로 서버에서 URL 을 조립해 클라이언트에 건네는 것과 같은 패턴). **하드코딩 아님 — 새 config 도 불필요.**
  - **콘솔 «수락 링크 복사»**: 이 티켓에 넣지 않는다. 그 칸은 `projects/platform-console/apps/console-web/src/features/ecommerce-ops/components/SellerMembers.tsx:205-226`(`data-testid="seller-invite-token"`, 현재 토큰 원문만 보여줌)에 있고 **다른 프로젝트**다. 링크를 만들려면 콘솔이 web-store 의 **공개 주소**(배포마다 바뀜 — `VERCEL.md` 참조)를 알아야 하는데 그 설정이 콘솔 쪽에 없다. 범위를 프로젝트 경계 밖으로 넓히는 별도 결정이 필요하므로 후속 과제로 남긴다.
- [x] **AC-1** — `features/seller-invitation/ui/AcceptSellerInvitationForm.tsx` — 성공 시 `sellerId`·`role`·`joinedAt` 을 보여주는 화면으로 바뀐다(`accept-seller-invitation-form.test.tsx` "유효한 토큰으로 수락하면…"). `app/(store)/seller-invitations/accept/page.tsx` 가 `?token=` 을 트리밍해 `initialToken` 으로 넘기고 폼이 입력칸을 미리 채운다(`accept-seller-invitation-form.test.tsx` "?token= 값으로…", `seller-invitation-accept-page.test.tsx`).
- [x] **AC-2** — `SELLER_INVITATION_EMAIL_NOT_VERIFIED` 를 일반 오류 표와 분리해 전용 분기로 처리 — «이메일 인증이 필요합니다…» 문구 + `emailVerificationUrl`(페이지가 서버에서 `OIDC_ISSUER_URL` 로 조립해 prop 으로 내려줌) 링크를 보여주고, `isSubmitting` 만 false 로 돌아가 수락 버튼이 다시 활성화된다(초대 미소모 — 상태를 "소비됨"으로 바꾸는 코드가 없다). 시험: `accept-seller-invitation-form.test.tsx` AC-2 describe 블록 2건(문구+링크+버튼 활성, 재수락 성공).
- [x] **AC-3** — `packages/types/src/guards.ts` `ERROR_MESSAGES` 에 7개 코드(이메일 불일치·초대 없음·이미 사용·셀러 비활성·계정 부적격·만료·서비스 불가) + `UNAUTHORIZED` 를 추가, 전부 서로 다른 문구(표 유일성은 `new Set(messages).size === messages.length` 로 코드 시험에서도 고정). 503 문구는 "잠시 후 다시 시도해 주세요." 뿐이고 "초대/코드/토큰" 어휘를 포함하지 않음을 별도 단언. 시험: `accept-seller-invitation-form.test.tsx` AC-3 describe 블록(`it.each` 8코드 + 유일성 + 503 비난-안함 + 알수없는에러 폴백).
- [x] **AC-4** — 새 라우트는 `middleware.ts` 의 공개 경로 허용목록에 없으므로 기존 보호 경로와 동일하게 비로그인 접근 시 `/login?from=%2Fseller-invitations%2Faccept%3Ftoken%3D...` 로 307 되고, 로그인 후 같은 경로(+쿼리)로 돌아온다 — AC-0 에서 확인한 기존 `middleware.ts`/`LoginForm.tsx`/`auth-context.tsx` 경로를 그대로 타며 새 코드가 없다. e2e `auth-redirect.spec.ts` 의 `protectedPaths` 표에 `/seller-invitations/accept` 행을 추가해 같은 자동화 시험 대상에 편입시켰다(풀스택 e2e 는 nightly 전용이라 AC-6 의 ⚪ 항목으로 넘어간다).
- [x] **AC-5** — bite 실행(2026-10-08 UTC): `AcceptSellerInvitationForm.tsx` 의 `err.code === EMAIL_NOT_VERIFIED_CODE` 분기를 지우고(일반 `ERROR_MESSAGES[err.code] ?? err.message` 분기로 합침) `npx vitest run src/__tests__/accept-seller-invitation-form.test.tsx` 재시도 → **로컬 vitest 자체가 기동 안 됨**(`ERR_PACKAGE_IMPORT_NOT_DEFINED #module-evaluator`, Node v24.14.0 — 이 호스트의 기존 한계, `[[env_webstore_vitest4_node24_module_evaluator]]`, TASK-FE-097/BE-572 에서도 동일하게 기록됨; TASK-MONO-655 가 Node20 다운시프트까지 시도해 봤고 pnpm 심링크 해석에서 다시 막힌다는 것까지 확인되어 있음). 그래서 **코드 검사로 대신 판정**했다: `ERROR_MESSAGES` 에는 `SELLER_INVITATION_EMAIL_NOT_VERIFIED` 항목이 의도적으로 없으므로 분기를 지우면 이 코드의 메시지가 `err.message`(테스트 헬퍼 `apiError()` 의 기본값 `'error'`)로 떨어지고, `emailNotVerified` 상태가 전혀 `true` 가 되지 않아 `data-testid="email-not-verified"` 블록이 렌더되지 않는다 — AC-2 describe 블록의 두 테스트(`getByTestId('email-not-verified')` 대기)만 실패하고, 코드·메시지가 다른 AC-3 표/성공/검증/트림 테스트는 전부 그대로 통과한다(어느 것도 이 상수나 분기를 참조하지 않음). 이 추론대로 수정했다가 **Edit 으로 원복**했고(`git checkout` 미사용), 원복 후 `tsc --noEmit` rc=0 재확인. 로컬에서 실제 레드/그린 전환을 vitest 로 못 본 것은 이 한 가지 bite 뿐 아니라 이 세션 전체 vitest 실행에 적용되는 환경 한계이며, 실제 실행 증거는 CI `Frontend unit tests`(Node 20)가 권위다(AC-6 참조, 이 세션은 push 범위 밖).
- [x] **AC-6** — `apps/web-store` 기준: `npx tsc --noEmit` rc=0 · `npx next lint` rc=0 · `npx vitest run` rc=1 **이지만 테스트 실패가 아니라 기동 실패**(`ERR_PACKAGE_IMPORT_NOT_DEFINED #module-evaluator`, Node v24.14.0 — `[[env_webstore_vitest4_node24_module_evaluator]]`, CI 는 Node 20 이라 통과해 왔다; 이 세션은 push 하지 않으므로 CI 확인은 이 티켓 작업을 넘겨받는 다음 단계의 몫). e2e 디렉터리 grep: `seller-invitation` 0건(신규라 당연) · `middleware`/`/login?from=` 패턴은 `auth-redirect.spec.ts` 에서 매치(그렙 자체가 공집합을 낼 도구가 아님을 확인) → 영향 지점은 `auth-redirect.spec.ts` 하나로 식별, `protectedPaths` 표에 새 라우트를 추가함(본문은 AC-4 참조). ⚪ 머지 뒤 첫 nightly web-store 잡 확인 — 이 라우트는 아직 `main` 에 없고 이 세션은 push/PR 을 만들지 않으므로 측정 불가(머지 이후 수행 필요). ⚪ 라이브 수락 1회 — 다음 데모 창에서 수행(콘솔이 발급한 실제 토큰 + 실제 IAM 계정 필요, 이 세션 범위 밖).

# Related Specs

- `specs/services/web-store/architecture.md`
- `docs/adr/ADR-MONO-079-agencies-sellers-and-console-fan-management.md` D5 · § 부분 개정 (2026-10-07) · `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` R1

# Related Contracts

- `specs/contracts/http/product-api.md` § POST /api/seller-invitations/accept (`:376-415`)
- `specs/contracts/http/internal/product-to-account.md:137`

# Edge Cases

- 같은 계정이 이미 수락한 토큰을 다시 제출 → 계약상 200(이중 제출은 오류 아님) — 성공 화면.
- 토큰 앞뒤 공백 · 줄바꿈(복사·붙여넣기) — 다듬어서 보낸다.
- 토큰을 주소창에 남기지 않기: 수락 성공 뒤 `?token=` 을 주소에서 지운다(브라우저 기록 · 화면 공유로 새지 않게).

# Failure Scenarios

1. `EMAIL_NOT_VERIFIED` 를 일반 «수락 실패» 로 뭉갠다 — 사용자가 인증만 하면 되는 것을 모르고 운영자에게 새 초대를 요구한다.
2. 503 을 «초대가 잘못됨» 으로 말한다 — 멀쩡한 초대를 버리게 한다.
3. 로그인 리다이렉트에서 토큰을 잃는다 — 링크로 들어온 사람이 토큰을 다시 받아야 한다.

# 닫기 — 4차원 검증 (2026-10-08 UTC, `date -u` 실측)

- (a) PR **#4229** `state=MERGED` · (b) `origin/main` 에 스쿼시 **`0fe56907f`** · (c) 머지 시점 `statusCheckRollup` 실패 **0**.
- (d) AC-1~6 `[x]`. 라이브를 24차 데모 창(ami-01f1b4b56e4f9e51a · 1c8e203aa · 인스턴스 i-0445d76661ef0013d) 에서 소유자가 브라우저로 한 바퀴 돌았다:
  - 새 스토어 계정 가입(미인증) → 콘솔(tenant=ecommerce) demo-seller 구성원 초대 → 초대 코드 한 번 표시 · 서버 로그 `seller invitation issued tenant=ecommerce seller=demo-seller` 17:05:11Z.
  - `store.hubwang.com/seller-invitations/accept?token=…` 수락 → **«이메일 인증이 필요합니다» + 인증 화면 링크**(소유자 확인).
  - 링크 → IdP 인증 메일 → Mailpit 17:06:55Z → 인증 완료 → **같은 초대** 재수락 성공 «셀러 demo-seller 의 구성원이 되었습니다 · 가입일 2026. 10. 9. 02:08:50(KST)» = 17:08:50Z.
