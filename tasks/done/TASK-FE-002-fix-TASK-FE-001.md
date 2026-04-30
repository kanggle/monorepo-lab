# Task ID

TASK-FE-002

# Title

Fix TASK-FE-001 — cross-feature dependency, direct fetch in LoginForm, env name mismatch, a11y tests, React types

# Status

ready

# Owner

frontend

# Task Tags

- code
- test

# depends_on

- TASK-FE-001

---

# Goal

TASK-FE-001 리뷰에서 발견된 Critical/Warning 이슈들을 수정한다.

---

# Scope

## In Scope (Critical)

1. **LoginForm 직접 fetch 제거**:
   - `apps/admin-web/src/features/auth/components/LoginForm.tsx`의 직접 `fetch('/api/auth/login', ...)`를 `apiClient.post('/api/auth/login', values, { skipAuthRetry: true })`로 교체
   - architecture.md Forbidden Patterns 위반

2. **RoleGuard 교차 feature 의존 제거**:
   - `RoleGuard`와 `hasAnyRole`을 `src/features/auth/guards/` → `src/shared/ui/RoleGuard.tsx`로 이동
   - `features/accounts/components/AccountDetail.tsx` 등 모든 import 경로 업데이트

3. **Grafana env 변수명 정합화**:
   - `GRAFANA_DASHBOARD_URL` → `NEXT_PUBLIC_GRAFANA_BASE_URL`로 변경 (spec 준수)
   - `shared/config/env.ts`의 client schema로 이동
   - `dashboards/page.tsx`에서 client-side로 접근 (Server Component에서도 public env는 `process.env.NEXT_PUBLIC_*`로 접근 가능하므로 코드 변경 최소)

## In Scope (Warning)

4. **axe-core a11y 테스트 추가**:
   - `@axe-core/react` devDependency 추가
   - `LoginForm.test.tsx`, `AccountDetail` 또는 상세 테스트, `LockDialog.test.tsx`에 axe 검사 추가
   - 컴포넌트 렌더 후 `axe(container)` 실행 → violations.length === 0

5. **@types/react를 19로**:
   - `package.json`의 `@types/react`, `@types/react-dom`을 `^19.x`로

6. **sentry.ts placeholder**:
   - `src/shared/observability/sentry.ts` — no-op init + TODO 주석 (SENTRY_DSN env 사용 안내)

7. **date-fns 의존성 추가 + 날짜 포맷**:
   - `package.json`에 `date-fns` 추가
   - AccountDetail, AuditTable 등의 ISO date 문자열을 `format(parseISO(v), 'yyyy-MM-dd HH:mm:ss')`로

## Out of Scope

- sessions/operators feature 구현 (별도 태스크 권장)
- CI 번들 예산·Lighthouse 자동 게이트 (TASK-FE-003로 분리)
- web-vitals → Prometheus 실제 relay (인프라 레이어)

---

# Acceptance Criteria

- [ ] `LoginForm.tsx`에 직접 `fetch()` 호출 없음 (apiClient 경유)
- [ ] `features/accounts` 어디서도 `features/auth` import 없음
- [ ] `RoleGuard`는 `shared/ui/`에 존재
- [ ] env 이름 `NEXT_PUBLIC_GRAFANA_BASE_URL` 일관 사용
- [ ] 컴포넌트 테스트 중 3개 이상에 axe 검사 포함, violations 0
- [ ] `@types/react@^19` + `@types/react-dom@^19`
- [ ] `src/shared/observability/sentry.ts` 존재
- [ ] 날짜 필드가 ISO 원문이 아닌 로컬라이즈 포맷으로 렌더
- [ ] `pnpm build` + `pnpm test` 모두 통과

---

# Related Specs

- `specs/services/admin-web/architecture.md` — Forbidden Patterns, package structure
- `specs/services/admin-web/dependencies.md` — Runtime Dependencies table, env vars
- `platform/service-types/frontend-app.md` — a11y 필수 요구

# Related Skills

- `.claude/skills/frontend/api-client/SKILL.md`
- `.claude/skills/frontend/testing-frontend/SKILL.md`
- `.claude/skills/frontend/component-library/SKILL.md`

---

# Related Contracts

없음 (내부 프론트엔드 수정)

---

# Target Service

- `apps/admin-web`

---

# Edge Cases

- apiClient로 교체 시 LoginForm은 `/api/auth/login`이 같은 origin Next.js route handler이므로 `skipAuthRetry: true` 플래그 필요 (401 refresh 로직 불필요)
- `NEXT_PUBLIC_*` env는 Next.js가 빌드 시점에 inline. 런타임 주입이 필요하다면 server-only 유지도 허용 — 그 경우 spec에 `## Overrides` 블록 추가 필요

---

# Failure Scenarios

- axe 검사가 기존 shadcn primitive에서 violation 감지 → 해당 primitive 먼저 수정 (최소: Dialog aria-labelledby, Table scope 속성)

---

# Test Requirements

- 기존 17개 vitest 테스트 모두 통과
- a11y violation = 0 새 테스트

---

# Definition of Done

- [ ] Critical 3개 + Warning 4개 수정
- [ ] `pnpm build` 통과
- [ ] `pnpm test` 통과
- [ ] Ready for review
