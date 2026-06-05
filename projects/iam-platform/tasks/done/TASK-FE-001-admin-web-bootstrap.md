# Task ID

TASK-FE-001

# Title

admin-web 부트스트랩 — Next.js 15 운영자 콘솔, 로그인·계정 검색·잠금/해제·감사·대시보드 골든패스

# Status

ready

# Owner

frontend

# Task Tags

- code
- api

# depends_on

- TASK-BE-010 (admin-service bootstrap)

---

# Goal

admin-service API를 소비하는 운영자 전용 Next.js 15 (App Router) 웹 콘솔을 초기화한다. 로그인, 계정 검색/상세, 잠금/해제, 강제 로그아웃, 감사 조회, 대시보드 임베드의 최소 골든패스를 구현한다.

---

# Scope

## In Scope

- `apps/admin-web/` 모듈 생성 (pnpm 워크스페이스에 포함)
- `settings.gradle`은 JVM 전용이므로 수정 불필요 — 모노레포 인식은 `pnpm-workspace.yaml` 추가 (없으면 신설)
- Next.js 15 (App Router) + React 19 + TypeScript 5 + Tailwind + shadcn/ui + React Query + zod + react-hook-form 설정
- 폴더 구조 (`specs/services/admin-web/architecture.md` 준수):
  - `src/app/(auth)/login/page.tsx`
  - `src/app/(console)/layout.tsx` (sidebar + topbar + RoleGuard)
  - `src/app/(console)/accounts/page.tsx` — 검색
  - `src/app/(console)/accounts/[id]/page.tsx` — 상세 + lock/unlock 다이얼로그
  - `src/app/(console)/audit/page.tsx`
  - `src/app/(console)/security/login-history/page.tsx`
  - `src/app/(console)/security/suspicious/page.tsx`
  - `src/app/(console)/dashboards/page.tsx` — Grafana iframe
  - `src/app/api/auth/login/route.ts` (HttpOnly 쿠키 설정)
  - `src/app/api/auth/logout/route.ts`
  - `src/app/api/auth/refresh/route.ts`
  - `src/app/api/web-vitals/route.ts`
- `src/features/auth/` — LoginForm, useOperatorSession, RoleGuard
- `src/features/accounts/` — 검색, 상세, LockDialog, UnlockDialog, DeleteDialog
- `src/features/sessions/` — 세션 목록, RevokeDialog
- `src/features/audit/` — admin_actions 테이블, 필터
- `src/features/security/` — login_history, suspicious_events
- `src/shared/api/client.ts` — fetch wrapper (credentials: 'include', 401 시 refresh)
- `src/shared/api/admin-api.ts` — admin-api.md 기반 수동 TypeScript 타입
- `src/shared/ui/` — shadcn/ui primitives (Button, Dialog, Form, Table, Input, Select, Toast 등)
- `src/shared/config/env.ts` — zod 검증
- `src/shared/lib/idempotency.ts` — UUID v4 생성 헬퍼
- `src/shared/observability/web-vitals.ts`
- 모든 쓰기 명령 폼에 `X-Operator-Reason` 입력 필드 + `Idempotency-Key` 자동 생성
- 역할 기반 가시성 (SUPER_ADMIN / ACCOUNT_ADMIN / AUDITOR)

## Out of Scope

- 운영자 계정 생성/관리 UI (TASK-FE-002 백로그)
- Playwright E2E 전체 (critical path 1개만 이 태스크에서)
- CI 번들 예산·a11y 자동 검사 게이트 (TASK-FE-003 observability 연동에서)
- Sentry 실제 DSN 연동 (런타임 env 프로비저닝만)
- 외부 공개 URL, VPN/IP allowlist 설정

---

# Acceptance Criteria

- [ ] `cd apps/admin-web && pnpm dev` → 개발 서버 기동, 브라우저에서 `/login` 접근 가능
- [ ] `pnpm build` 성공, `.next/` 생성
- [ ] `pnpm test` 성공 (Vitest)
- [ ] `POST /api/auth/login` → 쿠키 `accessToken`·`refreshToken`이 `HttpOnly; Secure; SameSite=Strict`로 설정됨
- [ ] 일반 사용자 JWT (scope ≠ admin)로 로그인 시도 → 403 + 로그인 폼에 에러 표시
- [ ] 로그인 성공 후 `/accounts` 리다이렉트
- [ ] `/accounts` 에서 이메일로 검색 → 결과 표시
- [ ] `/accounts/[id]` 상세 페이지에서 status·profile·최근 로그인 표시
- [ ] "잠금" 버튼 클릭 → `X-Operator-Reason` 다이얼로그 → admin-service `/api/admin/accounts/{id}/lock` 호출 → 성공 토스트 + status 갱신
- [ ] AUDITOR 역할로 로그인 시 "잠금"·"해제" 버튼 비활성화 (or 비노출)
- [ ] `/audit` 페이지에서 admin_actions 목록 표시 + 기간/operator/action_code 필터
- [ ] `/dashboards` 페이지에 Grafana iframe 로드 (CSP `frame-src` 설정 포함)
- [ ] 401 응답 수신 시 `/api/auth/refresh` 자동 호출 → 재시도 → 실패 시 `/login` 리다이렉트
- [ ] 서버 로그가 JSON 구조화 + requestId 포함
- [ ] `/api/web-vitals` 가 LCP/INP/CLS/TTFB 수신
- [ ] 적어도 1개 Playwright E2E: 로그인 → 계정 검색 → lock → 감사 조회 확인
- [ ] Vitest 컴포넌트 테스트: LoginForm, AccountSearchForm, LockDialog, RoleGuard
- [ ] 컴포넌트에서 `fetch()` 직접 호출 없음 (전부 `shared/api/client.ts` 경유)
- [ ] localStorage/sessionStorage에 토큰 저장 없음

---

# Related Specs

- `specs/services/admin-web/overview.md`
- `specs/services/admin-web/architecture.md`
- `specs/services/admin-web/dependencies.md`
- `specs/services/admin-web/observability.md`
- `platform/service-types/frontend-app.md`

# Related Skills

- `.claude/skills/service-types/frontend-app-setup/SKILL.md`
- `.claude/skills/frontend/implementation-workflow/SKILL.md`
- `.claude/skills/frontend/architecture/layered-by-feature/SKILL.md`
- `.claude/skills/frontend/api-client/SKILL.md`
- `.claude/skills/frontend/state-management/SKILL.md`
- `.claude/skills/frontend/form-handling/SKILL.md`
- `.claude/skills/frontend/loading-error-handling/SKILL.md`
- `.claude/skills/frontend/component-library/SKILL.md`
- `.claude/skills/frontend/auth-client/SKILL.md`
- `.claude/skills/frontend/testing-frontend/SKILL.md`

---

# Related Contracts

- `specs/contracts/http/admin-api.md` (소비)
- `specs/contracts/http/auth-api.md` (로그인 경로만)

---

# Target Service

- `apps/admin-web`

---

# Architecture

- Service Type: `frontend-app`
- Architecture pattern: Layered by Feature
- Server Component 기본, `'use client'`는 리프 상호작용에만
- HttpOnly 쿠키 기반 auth, 클라이언트는 토큰 미접근
- `shared/api/client.ts`는 credentials: 'include' + 401 refresh proxy

---

# Implementation Notes

- **패키지 매니저**: pnpm (workspace). 루트에 `pnpm-workspace.yaml` 없으면 생성: `packages: ['apps/admin-web']`
- **Next.js 15 App Router 최신 패턴** 사용 (async server components, Route Handlers, `cookies()` from `next/headers`)
- **shadcn/ui 설치**: `npx shadcn@latest init` 후 필요한 primitive만 추가 (Button, Dialog, Form, Table, Input, Select, Toast, Badge)
- **Tailwind v3** (v4는 아직 생태계 과도기)
- **zod 스키마**는 `features/<F>/schemas.ts` 에 colocate
- **React Query**: 한 앱 하나의 `QueryClient` 인스턴스를 `app/providers.tsx` (client component)에서 제공
- **Route Handler에서 accessToken 검증**: 토큰 검증은 gateway·admin-service가 최종 책임이지만, route handler에서도 만료 시간만 빠르게 확인하여 불필요한 백엔드 호출 회피 (옵션)
- **CSP 헤더**: `next.config.mjs`에 `Content-Security-Policy` 설정 — `frame-src 'self' https://grafana.internal`, `connect-src 'self' https://gw.internal`
- **에러 메시지 매핑**: admin-api.md의 에러 코드를 `shared/api/errors.ts`에서 i18n-ready 메시지로 변환 (초기는 한국어 상수)
- **Idempotency-Key**: `crypto.randomUUID()` (브라우저 native) — 쓰기 명령 submit 직전 생성
- **Reason 수집**: 전용 `ReasonInputDialog` 컴포넌트로 재사용
- Mock/stub 불필요 — 로컬 개발 시 `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080` 설정하고 백엔드 docker-compose + admin-service 실제 기동

---

# Edge Cases

- Refresh token 만료 → refresh 실패 → `/login` 리다이렉트 + 원 URL을 `?redirect=` 쿼리로 저장
- 네트워크 오프라인 → `navigator.onLine` 감지 후 상단 banner
- Grafana 도메인 접근 불가 → iframe `onError` 감지 후 placeholder 표시
- 같은 lock 버튼을 두 번 클릭 → Idempotency-Key 동일 → admin-service에서 멱등 200
- 브라우저 뒤로가기 시 폼 값 유지 (React Hook Form persist)
- 세션 중 role이 revoke됨 → 다음 API 호출에서 403 → "권한이 변경되었습니다" 안내 후 재로그인

---

# Failure Scenarios

- admin-service 장애 → 502 토스트 + 액션 버튼 재시도 가능 상태 유지
- auth-service 로그인 장애 → "로그인 서비스 일시 장애" 메시지
- 쿠키 설정 실패 (브라우저 서드파티 쿠키 차단) → 로그인 경로가 같은 도메인이므로 first-party 쿠키로 동작, 경고 불필요
- Grafana 불가 → dashboards 페이지 placeholder, 다른 페이지는 정상 동작

---

# Test Requirements

- **Vitest 컴포넌트**:
  - `LoginForm` — 폼 validation, submit 성공/실패 분기
  - `AccountSearchForm` — 이메일 validation, 결과 렌더
  - `LockDialog` — reason 필수, Idempotency-Key 포함 호출 검증
  - `RoleGuard` — AUDITOR에게 금지 버튼 비가시
- **API 클라이언트 테스트**:
  - 401 → refresh → 재시도 successful flow
  - refresh 실패 → redirect
- **Contract 타입 테스트**: admin-api.md의 LockResponse 스키마를 zod로 파싱 → 성공
- **a11y**: LoginForm, AccountDetail, Dialog에 axe-core 검사
- **Playwright E2E (1개)**:
  - 로그인 → `/accounts` 진입 → 이메일 검색 → 상세 → 잠금 → 감사 페이지에서 action_code=ACCOUNT_LOCK 확인

---

# Definition of Done

- [ ] `pnpm install && pnpm build` 성공
- [ ] `pnpm test` 성공 (Vitest + Playwright E2E 1개)
- [ ] admin-service가 기동된 상태에서 수동 smoke: 로그인 → 검색 → lock → 감사 확인
- [ ] 번들 크기: /login < 180KB, /(console)/* < 250KB
- [ ] 컴포넌트/훅 컴포넌트 테스트 존재
- [ ] CSP·HttpOnly 쿠키 설정 확인
- [ ] Ready for review
