# console-web — Architecture

This document declares the internal architecture of `console-web`.
All implementation tasks targeting this service must follow this declaration
and `platform/architecture-decision-rule.md`.

> **Status: v1 skeleton (ADR-MONO-013 Phase 1, TASK-MONO-108).** Boot-able minimal shell only. The real shell (GAP OIDC SSO, data-driven catalog, tenant switcher, federated domain screens) is implemented by `TASK-PC-FE-001` (Phase 1→2) and later domain-section tasks. This declaration is the target architecture all those tasks follow.

---

## Identity

| Field | Value |
|---|---|
| Service name | `console-web` |
| Project | `platform-console` |
| Service Type | `frontend-app` (single — see Service Type Composition below) |
| Architecture Style | **Layered by Feature** |
| Domain | saas |
| Primary language / stack | TypeScript strict, Next.js 15 (App Router) |
| Bounded Context | Unified operator console over the enterprise suite (gap · wms · scm + future erp · finance) — Model B single UI |
| Deployable unit | `apps/console-web/` |
| Backend dependencies | GAP `auth-service` (OIDC Authorization Code + PKCE public client login/refresh); GAP product/tenant registry surface (catalog source, `TASK-BE-296`); per-domain `gateway`/`admin` REST APIs (federated screens, added per domain section) |

### Service Type Composition

`console-web` is a single-type `frontend-app` service per
`platform/service-types/INDEX.md`. 선언된 서비스 타입의
[platform/service-types/frontend-app.md](../../../../../platform/service-types/frontend-app.md)
요구사항을 전부 상속한다 — HttpOnly 쿠키 인증, 타입 API 클라이언트,
server-component 우선, 성능/접근성/web-vitals 예산.

---

## Architecture Style

**Layered by Feature** — 기능(도메인 섹션) 단위 폴더로 묶고 `shared/` 공통 레이어만 분리한다. Feature-Sliced Design 의 엄격한 5계층 분리는 본 도메인 규모에 비해 오버엔지니어링이라 채택하지 않는다.

참조 skill: [.claude/skills/frontend/architecture/layered-by-feature/SKILL.md](../../../../../.claude/skills/frontend/architecture/layered-by-feature/SKILL.md)

## Why This Architecture

- **운영자 단일 사용자 그룹** — 모든 화면이 동일 사용자(플랫폼 운영자)를 대상으로 한다. GAP `admin-web` 가 동일 trade-off 로 Layered by Feature 를 택했고, `console-web` 은 그 운영자 표면을 흡수(ADR-MONO-013 D4)하므로 동일 스타일 유지가 cross-stack 리뷰 학습 비용을 최소화한다.
- **도메인 섹션 = feature** — gap/wms/scm 각 도메인 운영 화면이 자연스러운 feature 경계다. 섹션 간 강한 격리(FSD `entities/`+`widgets/`)의 이득보다 ceremony 비용이 크다. 도메인 추가(erp/finance)는 feature 폴더 + 카탈로그 레지스트리 엔트리 추가로 끝난다.
- **list/detail/action CRUD 반복** — 계정 검색→상세→액션, 감사 조회 등 admin-web parity 표면이 list/detail 패턴 반복. admin-dashboard / admin-web 의 선택과 일치.
- **Server-component 우선** — 데이터 페칭은 서버에서. client features 격리의 가치는 leaf-level client component 에 한정.

## Tech Stack

- **Next.js 15** (App Router) · **React 19** · **TypeScript 5**
- **Tailwind CSS** + shadcn/ui-style primitives (`shared/ui/`)
- **React Query** (서버 상태 캐싱·변이; client-only) — *Phase 2 (TASK-PC-FE-001)*
- **zod** (env / API 응답 스키마 검증) — *Phase 2*
- **Vitest** + **@testing-library/react** · **Playwright** (E2E) — *Phase 2*

> Phase-1 skeleton 은 의도적으로 의존성을 최소화한다(next/react/tailwind만). React Query·zod·테스트 하네스는 `TASK-PC-FE-001` 에서 도입한다 (skeleton README/agent deviation 기록과 정합).

## Internal Structure Rule

```
apps/console-web/
├── src/
│   ├── app/                           # Next.js App Router routes
│   │   ├── (auth)/login/page.tsx      # GAP OIDC 진입 (Phase 2)
│   │   ├── (console)/                 # 인증 필수 레이아웃
│   │   │   ├── layout.tsx             # topbar + tenant switcher + catalog nav
│   │   │   ├── page.tsx               # data-driven 서비스 카탈로그 (현 skeleton: 정적 placeholder)
│   │   │   ├── gap/                   # GAP 운영자 parity 섹션 (Phase 2, admin-web 흡수)
│   │   │   ├── wms/                   # wms 운영 섹션 (Phase 4)
│   │   │   └── scm/                   # scm 운영 섹션 (Phase 4)
│   │   ├── api/                       # Route Handlers (auth proxy / health)
│   │   │   ├── auth/{login,logout,callback,refresh}/route.ts  # OIDC + HttpOnly cookie (Phase 2)
│   │   │   └── health/route.ts        # ✅ skeleton
│   │   └── layout.tsx                 # ✅ skeleton (root)
│   ├── features/                      # 도메인 섹션 단위 (Phase 2+)
│   │   ├── catalog/                   # 서비스 카탈로그 (레지스트리 소비)
│   │   ├── tenant/                    # 테넌트 스위처
│   │   ├── auth/                      # GAP OIDC 세션
│   │   └── gap-ops/ wms-ops/ scm-ops/ # 도메인별 운영 화면
│   └── shared/
│       ├── api/                       # typed API client (유일한 백엔드 진입점)
│       ├── ui/                        # primitives
│       ├── lib/
│       ├── observability/             # web-vitals / sentry (Phase 2)
│       └── config/
│           └── env.ts                 # ✅ skeleton (Phase 2: zod)
├── public/
├── tests/{unit,e2e}/                  # Phase 2
├── next.config.mjs · tsconfig.json · package.json   # ✅ skeleton
```

체크 ✅ = Phase-1 skeleton 에 존재. 나머지는 `TASK-PC-FE-001` 이후.

## Allowed Dependencies

| Layer | 허용 import |
|---|---|
| `app/` | `features/*`, `shared/*` |
| `features/<F>/` | 자체 폴더 내부, `shared/*` |
| `shared/` | 자체만 (features / app 금지) |

의존성은 **위에서 아래로만** 흐른다 (`app/ → features/ → shared/`). 같은 계층 `features/A → features/B` 상호 참조 금지(공유 가치는 `shared/` 로 승격).

## Forbidden Dependencies

- `features/A` → `features/B` 상호 참조
- 컴포넌트에서 `fetch()` 직접 호출 — 모든 백엔드 호출은 `shared/api/` 클라이언트 경유
- JWT / refresh token 을 `localStorage` / `sessionStorage` 에 저장 (HttpOnly 쿠키만 — `frontend-app.md` 위반)
- 빌드 타임 시크릿 주입 — 런타임 env 만
- 다른 앱(`admin-web`, `web-store`, …)의 `features/` 직접 import — 공유는 `@repo/` 레벨만

## Boundary Rules

- `app/` 라우트는 비즈니스 로직 없이 `features/*`+`shared/*` 합성에 한정.
- 도메인 섹션(`features/<domain>-ops/`)은 서로 직접 참조하지 않는다 — 공유 가치는 `shared/` 승격.
- `shared/api/` 는 **유일한** 백엔드 진입점 — cookie 자동 전달 + 401 refresh + per-domain 테넌트 헤더(`X-Tenant-Id`) 부착이 여기서 통과.
- 클라이언트 권한 가드는 UX 게이트일 뿐 — 실제 권한·격리는 도메인 백엔드(GAP/gateway)가 최종 책임 (cross-tenant 거부 포함).

## Server vs Client Components

- **Default: Server Component** — 데이터 페칭은 서버에서.
- **`'use client'`**: 폼·다이얼로그·스위처 등 상호작용 리프만.
- **React Query** 는 client component 한정; 서버는 `fetch` + `revalidate`/`cache`.

## Auth Flow

콘솔은 GAP OIDC **public client (`platform-console-web`, Authorization Code + PKCE)**. (Phase 2 / TASK-PC-FE-001)

1. `/login` → GAP `/oauth2/authorize` (PKCE `code_challenge`) 리다이렉트
2. GAP 콜백 → `/api/auth/callback` (Next.js route handler) 가 `code`+`code_verifier` 로 `/oauth2/token` 교환
3. 성공 시 `Set-Cookie: accessToken/refreshToken; HttpOnly; Secure; SameSite=Strict`
4. **서버측 operator-token 교환** (ADR-MONO-014 D2 / TASK-PC-FE-002a) — GAP access token 을 GAP `admin-service POST /api/admin/auth/token-exchange` (RFC 8693, subject_token=GAP access token) 으로 단명 operator token (`token_type=admin`, `iss=admin-service`) 으로 교환해 별도 HttpOnly operator 쿠키에 저장. GAP OIDC token 은 `/api/admin/**` 자격이 **아니며** subject token 입력으로만 사용된다. exchange `401`(미프로비저닝) → 강제 재로그인(`not_provisioned`); timeout/unreachable/5xx → 세션 불가(operator 쿠키 미설정, partial authed state 금지 — GAP token 으로 fallback 안 함).
5. 이후 모든 요청 쿠키 자동 전달; 401 → `/api/auth/refresh` → GAP 쿠키 회전 + **operator-token 재교환**(re-exchange 모델, operator-refresh state 없음) → 재시도
6. 로그아웃 → GAP revoke + GAP/operator 쿠키 삭제

GAP access token·operator token 모두 **절대 JavaScript 접근 불가** (HttpOnly) — `frontend-app.md` 필수. 교환 계약 상세는 GAP [`admin-api.md` §`POST /api/admin/auth/token-exchange`](../../../../global-account-platform/specs/contracts/http/admin-api.md) + [`admin-service/security.md` §GAP OIDC Subject-Token Validation](../../../../global-account-platform/specs/services/admin-service/security.md), 소비 의무는 [`console-integration-contract.md` § 2.1/§ 2.6](../../contracts/console-integration-contract.md).

## Performance Budget

- **/login**: 180 KB gzipped
- **(console)/* 내부 페이지**: 250 KB gzipped
- CI 번들 분석으로 회귀 차단 (Phase 2 wiring).

## Error Boundaries

- 각 라우트 `error.tsx`; `(console)/error.tsx` 는 인증 실패 시 `/login` redirect.
- 401 → refresh; 403 → "권한 없음"; 도메인 5xx/timeout → 해당 섹션만 degraded (콘솔 셸 유지 — `integration-heavy` resilience, 통합 계약 § 2.5).

## Integration Rules

`console-web` 은 `frontend-app` 으로서 **백엔드 컨트랙트의 소비자**이며 자체 컨트랙트·영속 상태를 소유하지 않는다.

- **통합 계약**: [specs/contracts/console-integration-contract.md](../../contracts/console-integration-contract.md) — OIDC public client / product·tenant 레지스트리 / per-domain console-facing API / resilience. 콘솔은 envelope 를 소유하지 않으며, 도메인 spec 선행 변경 후 follow.
- **인증/세션**: GAP `auth-service` (OIDC AS, ADR-001) — public client Auth Code+PKCE. GAP-side OIDC client + 레지스트리 surface 는 선행 task `TASK-BE-296` (GAP project-internal). `/api/admin/**` operator 자격은 GAP `admin-service POST /api/admin/auth/token-exchange` (RFC 8693, ADR-MONO-014) 으로 server-side 교환한 operator token — GAP OIDC token 은 subject token 입력으로만 쓰이며 결코 `/api/admin/**` 자격이 아니다 (선행 task `TASK-BE-298`, GAP project-internal, merged).
- **도메인 호출**: 각 도메인 `gateway`/`admin` REST API (server-side, 테넌트 스코프). 도메인별 endpoint 스키마는 그 도메인 `specs/contracts/` 소유, 섹션 빌드 시 cross-ref.
- **퍼시스턴스 / 이벤트 발행**: **없음** — DB·outbox·도메인 이벤트 미소유. 표현 계층에 한정.

## Testing Strategy

- **Unit/Component**: Vitest + Testing Library — `features/<F>/components/*` · `hooks/*` (Phase 2)
- **Contract tests**: `shared/api/*.test.ts` — 통합 계약·레지스트리 응답 스키마 정합
- **a11y**: axe-core, primitives 자동 검사
- **E2E (Playwright)**: 로그인 → 카탈로그 → 테넌트 전환 critical journey + multi-tenant 격리 회귀

## Observability

- **web-vitals**: LCP/INP/CLS/TTFB → route handler 전송 (Phase 2)
- **Sentry (옵션)**: 운영 환경, DSN 런타임 env
- **구조화 로그**: server component / route handler 는 `requestId`+`operatorId`+`tenantId` 컨텍스트 JSON 로그

## Change Rule

`console-web` 은 컨트랙트·도메인 로직·영속 상태를 소유하지 않으므로 본 Change Rule 은 **UI·소비 컨트랙트 호환성·번들 예산**을 규정한다.

1. 소비하는 통합 계약([console-integration-contract.md](../../contracts/console-integration-contract.md)) / 도메인 컨트랙트 변경은 **소유 측 spec 선행** — console-web 은 그 후 `shared/api/*` 타입+파서+contract test 동시 갱신 follow.
2. 라우트·feature 폴더 구조 변경은 § Internal Structure Rule + § Allowed Dependencies 정합 유지 (Layered by Feature 위반 차단).
3. 인증 흐름(OIDC public client / PKCE / HttpOnly 쿠키 / refresh route / **operator-token 교환** ADR-MONO-014) 변경은 [frontend-app.md](../../../../../platform/service-types/frontend-app.md) 필수 요구 + GAP `auth-service`·`admin-service` 호환성 확인 선행. GAP access token·operator token JS 접근 불가 + GAP OIDC token 이 `/api/admin/**` 자격이 아니라는 불변식 유지.
4. § Performance Budget 회귀 동반 변경 차단 (CI 강제).
5. 도메인 동작 변경 필요 시 소유 도메인 task 로 분리 — console-web 은 호출·표현만. GAP `admin-web` 폐기는 ADR-MONO-013 Phase 3 (parity-gated, GAP project-internal) — console-web task 아님.

## References

- [`platform/service-types/frontend-app.md`](../../../../../platform/service-types/frontend-app.md)
- [`console-integration-contract.md`](../../contracts/console-integration-contract.md) — 소비 통합 계약 (§ 2.1/§ 2.6 operator-token 교환)
- [`ADR-MONO-013`](../../../../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — 콘솔 foundation (D1 Model B · D4 admin-web 폐기 · D5 계약 · D6 roadmap)
- [`ADR-MONO-014`](../../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md) — operator-auth 교환 결정 (D2 re-exchange · D3 OIDC↔operator mapping · D4 spec-first)
- GAP [`admin-api.md` §`POST /api/admin/auth/token-exchange`](../../../../global-account-platform/specs/contracts/http/admin-api.md) + [`admin-service/security.md`](../../../../global-account-platform/specs/services/admin-service/security.md) — 교환 producer 계약 (authoritative, 소비만)
- [`admin-web/architecture.md`](../../../../global-account-platform/specs/services/admin-web/architecture.md) — parity 대상 sibling frontend-app (흡수 후 Phase 3 폐기)
- [`admin-dashboard/architecture.md`](../../../../ecommerce-microservices-platform/specs/services/admin-dashboard/architecture.md) — sibling frontend-app (Layered by Feature)
- [`.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`](../../../../../.claude/skills/frontend/architecture/layered-by-feature/SKILL.md)
