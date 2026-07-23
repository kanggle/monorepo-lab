# TASK-PC-FE-253 — 콘솔 로그인 인증 표면 정리 2건 (session 판정 불일치 · redirect sanitize 이중화)

**Status:** done

**Type:** TASK-PC-FE
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (판단 가벼움 — 죽은 엔드포인트 결정 + 한 줄 방어층. 다만 § F1 의 "삭제 vs 의미 정합" 결정이 실질)

> 로그인 기능 코드 리뷰(2026-07-21, 세션)에서 나온 저심각도 2건. **둘 다 실제 유저 경로에서 재현되는 결함은 아니다** — 하나는 현재 소비자가 없는 dead endpoint 의 의미 불일치, 하나는 이미 route 가 재차단하므로 악용 불가한 방어심층 nit. 그래서 P2 이고 한 PR 로 묶는다. 근처 인증 파일을 만질 때 함께 청소하는 용도.

---

## Goal

콘솔 로그인 흐름(login → callback → refresh → logout + 가드) 전반은 성숙하고 견고하다. 이 티켓은 그 표면의 **두 가지 정합성 구멍**을 닫는다. 서로 독립이며 어느 하나만 해도 된다.

### 항목 A — `/api/auth/session` 의 `authenticated` 가 실제 가드와 다르다 (+ 소비자 없음)

[`src/app/api/auth/session/route.ts`](../../apps/console-web/src/app/api/auth/session/route.ts) 는:

```ts
const authenticated = (await getAccessToken()) !== null;
```

로 판정한다. 그러나 실제 접근을 결정하는 가드 [`isAuthenticated()`](../../apps/console-web/src/shared/lib/session.ts) 는 **access + operator 두 쿠키 모두**를 요구한다:

```ts
export async function isAuthenticated(): Promise<boolean> {
  return (await getAccessToken()) !== null && (await getOperatorToken()) !== null;
}
```

⇒ **pre-operator 세션**(IAM 로그인은 됐으나 아직 어느 테넌트의 operator 도 아닌 상태 — operator 토큰 부재, `(onboarding)` 만 진입 가능)에게 이 엔드포인트는 `authenticated: true` 를 반환한다. 하지만 그 사용자는 `(console)` 셸에 들어가면 `/login`(정확히는 `/onboarding`) 으로 튕겨난다. **즉 "authenticated" 라고 답하는데 콘솔에는 못 들어가는 상태가 존재한다.**

- **현재 실질 영향 = 없음**: 소스 전체 grep 결과 이 엔드포인트를 호출하는 프로덕션 코드가 **하나도 없다**(소비자 0). 즉 지금은 dead-ish endpoint 다.
- **리스크 = 잠복 함정**: 향후 헤더/스위처 같은 클라 컴포넌트가 이 엔드포인트를 "로그인 여부" 판단에 쓰면, 반쪽 상태(access-only)를 "로그인됨" 으로 오판한다. 주석은 "non-sensitive status for header/switcher" 라고 용도를 좁혀 두었지만, 판정식 자체가 가드와 어긋나 있다.

### 항목 B — 로그인 페이지의 `redirect` 파라미터 sanitize 가 route 보다 약하다 (방어심층 nit)

[`src/app/(auth)/login/page.tsx`](<../../apps/console-web/src/app/(auth)/login/page.tsx>) 는:

```ts
const next = sp.redirect && sp.redirect.startsWith('/') ? sp.redirect : '/';
```

로 `startsWith('/')` 만 검사한다 ⇒ protocol-relative URL `//evil.com` 이 통과한다(그것도 `/` 로 시작하므로).

- **현재 악용 = 불가**: 이 값은 `loginHref = /api/auth/login?redirect=...` 로 넘어가고, [`login/route.ts`](../../apps/console-web/src/app/api/auth/login/route.ts) 가 거기서 **다시** `!rawRedirect.startsWith('//')` 로 걸러 `/` 로 강등한다. 그래서 실제 오픈리다이렉트는 성립하지 않는다.
- **문제 = 일관성 부재**: 같은 개념을 두 곳에서 검증하는데 한쪽(page)이 약하다. route 가 재차단을 멈추는 순간(리팩터·이관) 이 약한 검증이 유일한 방어가 되어 조용히 오픈리다이렉트가 열린다. 방어를 두 곳에 두었으면 **두 곳 모두 같은 술어**여야 한다.

---

## Scope

**In scope**

1. **항목 A** — `/api/auth/session` 를 다음 중 하나로 정리:
   - (a) **삭제** — 소비자가 없으면 dead code 이므로 라우트를 제거(가장 단순, 권장 후보).
   - (b) **의미 정합** — 남겨야 할 근거가 있으면 `authenticated` 를 `isAuthenticated()`(access + operator) 의미로 맞추고, "무엇을 authenticated 로 보는가" 를 주석에 명시.
   - 어느 쪽이든 **결정 근거를 PR 본문에 남긴다.**
2. **항목 B** — login page 의 `next` 검증을 login route 와 동일 술어로 강화(`startsWith('/') && !startsWith('//')`). 이상적으로는 두 곳이 공유하는 **단일 sanitize 헬퍼**(`shared/lib` 의 `sanitizeReturnPath()` 등)로 승격해 술어가 한 곳에만 존재하게 한다.

**Out of scope**

- **백엔드/계약 변경 0** — auth-service·admin-service·contract 무관. 순수 console-web 티켓.
- callback/refresh/logout 의 토큰 처리 로직 변경 0 — 그 경로는 리뷰에서 정상 확인됨.
- `SameSite=Lax` / `Secure` opt-out / operator-exchange fail-closed 등 **문서화된 의도적 결정 재론 0**.

---

## Acceptance Criteria

- **AC-0 (gate — 재측정, 코드가 이긴다)** — 착수 시 두 지점을 실측 재확인한다. **이 티켓 본문의 코드 인용은 2026-07-21 기준 가설이며, 그동안 이미 고쳐졌거나 소비자가 생겼을 수 있다:**
  - 항목 A: `session/route.ts` 가 여전히 `getAccessToken() !== null` 단독 판정인지, 그리고 `/api/auth/session` 소비자가 **여전히 0** 인지 grep 으로 재확인(소비자가 생겼으면 § Scope 1 이 (b) 로 강제된다).
  - 항목 B: `login/page.tsx` 의 `next` 가 여전히 `//` 를 통과시키는지, `login/route.ts` 가 여전히 재차단하는지 확인.
  - 둘 다 이미 해소됐으면 phantom 으로 기록하고 종료.
- **AC-1 (A)** — `/api/auth/session` 이 삭제되거나(소비자 0 확인 후), `authenticated` 가 access+operator 두 쿠키를 모두 요구하도록 정합된다. 채택 근거가 주석 + PR 본문에 있다.
- **AC-2 (A, 정합 선택 시)** — pre-operator 상태(access 有 / operator 無)에서 엔드포인트가 `authenticated: false` 를 반환함을 단언하는 테스트. (삭제 선택 시 이 AC 는 N/A — 대신 dead 라우트 참조가 어디에도 안 남았는지 확인.)
- **AC-3 (B)** — login page 의 `next` 가 `//evil.com`(및 `https://evil.com`, `/\evil.com`)을 `/` 로 강등함을 단언하는 테스트. login route 와 **같은 케이스 집합**을 통과해야 한다.
- **AC-4** — sanitize 를 헬퍼로 승격했다면, page·route 가 **동일 함수**를 호출함을 확인(술어가 한 곳). 승격하지 않았다면 두 곳의 술어가 문자 그대로 같은지 diff 로 확인.
- **AC-5** — `pnpm lint` + vitest GREEN. (tsc/vitest 가 못 잡는 CI 프런트 RED 가 있으므로 lint 생략 불가.)

---

## Related Specs

- `platform/service-types/frontend-app.md` § Authentication — HttpOnly 토큰 불변식, open-redirect 금지
- `projects/platform-console/specs/services/console-web/architecture.md` § Auth Flow — OIDC Auth Code + PKCE, 가드 정의
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1 / § 2.6 — IAM 토큰 ≠ operator 자격, fail-closed(항목 A 의 "authenticated = 두 쿠키" 근거)

---

## Related Contracts

- 없음 — 계약 변경 0. 콘솔 내부 표면 정리만.

---

## Edge Cases

1. **pre-operator 세션** — 항목 A 의 핵심 케이스. access 有 / operator 無 는 "로그인은 했지만 콘솔엔 못 들어감(온보딩 대상)". `/api/auth/session` 이 이걸 `true` 로 답하면 안 된다.
2. **소비자가 이미 생겼을 수 있음** — AC-0 이 잡는다. 소비자가 있으면 삭제(a)는 그 소비자를 깨뜨린다 → 정합(b) 강제.
3. **`//` 외의 우회형** — `/\evil.com`, `/%2Fevil.com`, `https:/evil.com` 등. login route 의 현재 술어(`startsWith('/') && !startsWith('//')`)가 이들을 이미 어떻게 처리하는지 확인하고 **page 를 route 와 정확히 일치**시켜라(더 강하게도, 더 약하게도 만들지 말 것 — 두 곳이 갈라지는 게 이 티켓이 닫으려는 병이다).
4. **삭제 시 잔여 참조** — 라우트를 지우면 e2e 픽스처·문서·OpenAPI 스텁 등에 `/api/auth/session` 참조가 남았는지 grep(삭제가 남긴 소비자 확인).

---

## Failure Scenarios

- **F1 — 항목 A 를 "고쳤다" 면서 삭제도 정합도 아닌 어정쩡한 상태로 둔다.** 삭제할지 의미를 맞출지 먼저 결정하라. 소비자 0 이면 삭제가 가장 정직하다.
- **F2 — 항목 B 를 page 에서만 강화하고 route 와 다시 갈라지게 한다.** 두 곳의 술어가 같아야 한다(가능하면 한 함수). 이 티켓의 병 자체가 "같은 검증이 두 곳에서 다름" 이다.
- **F3 — page 검증을 route 보다 과하게 만들어 정당한 상대경로 redirect 를 막는다.** `/dashboards/overview` 같은 정상 목적지는 반드시 통과해야 한다.
- **F4 — 테스트가 "authenticated 필드가 있다" 만 단언한다.** 값이 상태별로 옳은지(특히 pre-operator=false)를 봐야 한다.

---

## Definition of Done

- [ ] AC-0 재측정 (session 판정식 + 소비자 수 + page/route sanitize 술어)
- [ ] 항목 A: 삭제 또는 의미 정합 (근거 기록)
- [ ] 항목 B: page sanitize 를 route 와 동일 술어로(가능하면 공유 헬퍼)
- [ ] AC-2/AC-3 테스트 (pre-operator=false / `//` 강등)
- [ ] 잔여 참조 grep (삭제 선택 시)
- [ ] `pnpm lint` + vitest GREEN

---

## Notes

- **분량**: small. 두 항목 합쳐 파일 2~4개.
- **왜 한 티켓에 둘을 묶었나**: 각각 단독으로는 full-section 티켓을 정당화하기엔 작다(하나는 dead endpoint, 하나는 미악용 nit). 둘 다 로그인 인증 표면의 정합성 청소이고 한 작은 PR 로 랜딩 가능하다. 구현자가 원하면 두 커밋으로 나눠도 된다 — 서로 의존 없음.
- **이 task 가 방어하는 실패 모드**: **"같은 개념을 두 곳에서 판정하는데 한쪽이 다르다."** 항목 A 는 authenticated 를 두 정의로(엔드포인트 vs 가드), 항목 B 는 redirect 안전성을 두 술어로(page vs route) 판정한다. 지금은 각각 소비자 부재/route 재차단 덕에 무해하지만, 갈라진 판정은 한쪽 조건이 바뀌는 순간 결함이 된다.
- **dependency**: 없음. 독립 착수 가능.
