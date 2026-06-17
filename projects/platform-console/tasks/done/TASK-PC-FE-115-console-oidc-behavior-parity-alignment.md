# Task ID

TASK-PC-FE-115

# Title

console-web OIDC 프런트 동작 파리티 정렬 (consumer-integration-guide § Phase 4.5 F5/F6 준수)

# Status

done

# Owner

frontend

# Task Tags

- code
- frontend

---

# Goal

console-web(자작 OIDC Auth Code + PKCE route handler 기반)를 IAM consumer-integration-guide § Phase 4.5(프런트 OIDC 동작 파리티, TASK-BE-399)에 정렬한다. 진단(2026-06-17) 기준 console-web 은 다음 gap 을 가진다:

- **gap C (F5, 에러)**: 에러 코드 `not_provisioned` · `operator_exchange_unavailable` 가 `app/(auth)/login/page.tsx` 의 `ERROR_MESSAGES` 맵에 **없어 아무 메시지도 안 뜸**(무음 실패) + unknown-code **generic fallback 부재**. 계약은 모든 코드 메시지 + fallback 을 요구.
- **gap D (F6, 복귀)**: `(console)/layout.tsx` guard 가 미인증 시 **맨 `/login` 으로 바운스해 의도 목적지 유실**(`?redirect=` 미부여). 계약은 목적지 보존을 요구.

F2(토큰 server-only)·F3(silent refresh)·F1·F4·F7 은 이미 충족(console 이 더 강한 쪽) → 본 task 는 **C·D** 에 집중. 부수적으로 F4 의 `post_logout_redirect_uri` 표준 정합 확인(현재 `/login` — 계약상 등록 URI 면 OK, 유지).

> 선행: TASK-BE-399 (계약) 머지 후 착수 권장.

---

# Acceptance Criteria

- [ ] **F5/gap C**: `ERROR_MESSAGES` 에 `not_provisioned`·`operator_exchange_unavailable` 메시지 추가. 표준 어휘 정합(`role_denied` 계열로 `not_provisioned` 정렬 — 의미 보존하되 키 통일 검토). **unknown-code generic fallback** 추가(현재 미인식 코드 → `null` → 무음 → 위반 해소).
- [ ] **F6/gap D**: `(console)/layout.tsx` guard 가 미인증 바운스 시 `?redirect=<encodeURIComponent(pathname+search)>` 를 부여해 목적지 보존. `/api/auth/login` 이 이미 `redirect` 를 state 로 왕복하므로(login/route.ts) 로그인 후 해당 경로로 복귀.
- [ ] 복귀 경로 same-site 정규화(`//`·절대 URL 거부) — 기존 login/route.ts sanitize 와 일관.
- [ ] 회귀: 로그인/로그아웃/refresh/operator 토큰 교환 e2e + 단위 GREEN. `pnpm lint` + tsc + vitest 3종(next lint 가 CI 게이트).

---

# Scope

## In Scope

- `apps/console-web/src/app/(auth)/login/page.tsx` (`ERROR_MESSAGES` 확장 + generic fallback)
- `apps/console-web/src/app/(console)/layout.tsx` (guard 바운스에 `?redirect=` 부여)
- (검토) `app/api/auth/callback/route.ts` 의 에러 코드 키 — 표준 어휘 정합 시 매핑 갱신
- 관련 단위/e2e 테스트 갱신

## Out of Scope

- F2(토큰 server-only)·F3(silent refresh) — 이미 충족, 변경 없음.
- operator token(RFC8693)·assumed-tenant token 로직 — 도메인 특화, 파리티 대상 밖.
- 무-JS 로그인 페이지 perf 예산(/login 180KB) — server component 유지, 변경 없음.

---

# Related Specs

> **Before reading**: `projects/platform-console/PROJECT.md` → 선언 domain/traits 의 rule 레이어.

- `projects/iam-platform/specs/features/consumer-integration-guide.md` § Phase 4.5 (본 task 가 conform — F5/F6)
- `specs/contracts/console-integration-contract.md` (console ↔ IAM operator-auth)
- `docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md` (operator token — 에러 의미 정합)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` (`/oauth2/token`·`/connect/logout`)

---

# Target App

- `apps/console-web` (operator console, Next.js + 자작 OIDC route handlers)

---

# Edge Cases

- `not_provisioned`(운영자 미provisioning) 과 `operator_exchange_unavailable`(IAM 일시 장애)는 **의미가 다름** — 표준 어휘로 통일하되 사용자 메시지는 구분(전자=권한, 후자=재시도 유도). `role_denied` 단일 키로 합치면 의미 손실 — 메시지 레벨에서 분리 유지 권장.
- layout guard 의 `?redirect=` 부여 시 login/route.ts 의 state 왕복(`<state>|<postLoginPath>`)과 정확히 연결되는지 확인(파라미터 이름 `redirect` 일관).
- F6 복귀 경로는 `/api/auth/**` 등 비페이지 경로를 목적지로 잡지 않도록 정규화.

---

# Failure Scenarios

- generic fallback 추가를 빠뜨려 새 에러 코드 도입 시 또 무음 실패 재발 — fallback 을 코드 분기 말단에 둠.
- layout guard 가 `?redirect=` 에 raw query 를 그대로 실어 open-redirect — same-site 정규화 필수.
- 표준 어휘로 콜백 에러 키를 바꿀 때 callback/route.ts 와 login/page.tsx 매핑이 어긋남 → 무음 회귀. 양쪽 동시 갱신.

---

# Test Requirements

- 단위: `ERROR_MESSAGES` 가 전 코드 + unknown fallback 렌더, layout guard 가 `?redirect=` 부여.
- e2e: 미인증 보호경로 접근→로그인→해당 경로 복귀, 각 에러 코드 메시지 표시.
- `pnpm lint` + `tsc --noEmit` + `vitest` 3종 GREEN.

---

# Definition of Done

- [ ] C/D gap 정렬 완료 (F5 전 코드 메시지+fallback, F6 목적지 복귀)
- [ ] 3종 로컬 검증 + e2e GREEN
- [ ] Phase 4.5 파리티 체크리스트 console 열 충족
- [ ] Ready for review
