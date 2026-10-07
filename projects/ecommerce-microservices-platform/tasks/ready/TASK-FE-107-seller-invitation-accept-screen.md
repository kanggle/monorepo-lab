# Task ID

TASK-FE-107

# Title

web-store **셀러 초대 수락 화면** — 콘솔이 준 초대 토큰으로 `POST /api/seller-invitations/accept` 를 부르는 길 · «이메일 인증 필요» 안내 (TASK-MONO-770 의 남은 화면)

# Status

ready

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

- [ ] **AC-0** — 착수 시 재측정: web-store 의 로그인 리다이렉트 방식(돌아올 주소 보존) · API 클라이언트 모양 · IAM 인증 메일 화면 주소를 web-store 가 이미 아는지(환경 변수) file:line. 콘솔 «수락 링크 복사» 를 이 티켓에 넣을지 정하고 이유를 적는다.
- [ ] **AC-1** — 로그인 사용자가 유효 토큰으로 수락 → 성공 화면(셀러 · 역할). `?token=` 으로 들어오면 칸이 채워져 있다.
- [ ] **AC-2** — `403 SELLER_INVITATION_EMAIL_NOT_VERIFIED` → «이메일 인증 필요» 문구 + 인증 메일 화면 링크. 같은 화면에서 다시 수락 버튼이 살아 있다(초대 미소모 — 계약).
- [ ] **AC-3** — 나머지 오류 코드 각각이 서로 다른 문구로 렌더된다(표 시험) · 503 은 «잠시 뒤 다시» (토큰 문제로 말하지 않는다).
- [ ] **AC-4** — 비로그인 진입 → 로그인 뒤 같은 주소(토큰 포함)로 돌아온다.
- [ ] **AC-5** — bite: `EMAIL_NOT_VERIFIED` 분기를 지우면 AC-2 칸만 빨강.
- [ ] **AC-6** — web-store `tsc` · `lint` · `vitest` rc=0 · web-store e2e 디렉터리 grep 후 영향 확인 · 머지 뒤 첫 nightly web-store 잡 확인 · 라이브 수락 1회는 다음 데모 창(⚪ 가능).

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
