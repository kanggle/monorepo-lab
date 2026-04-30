# admin-web — Dependencies

## External Services

### admin-service (via gateway)
- **목적**: 모든 관리 명령·감사 조회
- **경로**: `https://<gateway-host>/api/admin/*`
- **인증**: operator JWT (HttpOnly cookie)
- **타임아웃**: 연결 3s / 응답 10s (조회) ~ 15s (쓰기 명령)
- **재시도**:
  - 읽기: 2회 재시도 + 지수 백오프 (네트워크 오류, 5xx만)
  - 쓰기: **재시도 금지** — Idempotency-Key로 방어하지만 사용자 혼란 방지 위해 클라이언트는 UI에서 재시도 버튼 노출
- **에러 처리**: `shared/api/errors.ts`가 contract error code를 사용자 메시지로 매핑
  - `401 TOKEN_INVALID` → refresh 시도 후 실패 시 `/login`
  - `403 PERMISSION_DENIED` → 권한 없음 페이지
  - `400 REASON_REQUIRED` → 폼 재입력
  - `400 VALIDATION_ERROR` → 필드별 에러 표시
  - `502 DOWNSTREAM_ERROR` → "일시적 문제, 재시도" 토스트
  - `500` → 장애 페이지

### auth-service (via gateway, 로그인만)
- **목적**: 운영자 로그인, 토큰 refresh, 로그아웃
- **경로**: `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`
- **경유**: Next.js route handler가 프록시 (쿠키 설정 책임)
- **scope 검증**: 응답 토큰에 `scope: "admin"` 포함 여부 route handler에서 확인 — 없으면 로그인 거부

### Grafana (iframe)
- **목적**: 대시보드 임베드
- **경로**: `NEXT_PUBLIC_GRAFANA_BASE_URL` (런타임 env)
- **인증**: Grafana anonymous 또는 사내 SSO (별도) — admin-web이 관리하지 않음
- **CSP**: `frame-src`에 Grafana 도메인 추가 필수

## Internal Packages

현재 monorepo에 TypeScript 공유 패키지 (`packages/`)가 없음. 초기 구현은 `apps/admin-web` 자체에 자기완결적으로 배치. 향후 web-store 같은 두 번째 frontend-app이 생기면 `packages/ui`, `packages/api-types`로 추출.

## Runtime Dependencies (npm)

| 패키지 | 버전 | 목적 |
|---|---|---|
| next | ^15.x | App Router |
| react | ^19.x | 프레임워크 |
| react-dom | ^19.x | — |
| typescript | ^5.x | 타입 |
| @tanstack/react-query | ^5.x | 서버 상태 |
| zod | ^3.x | validation |
| react-hook-form | ^7.x | 폼 |
| @hookform/resolvers | ^3.x | zod 연계 |
| tailwindcss | ^3.x | 스타일 |
| class-variance-authority | ^0.7.x | variant 유틸 |
| clsx | ^2.x | 클래스 결합 |
| lucide-react | ^0.4.x | 아이콘 |
| date-fns | ^3.x | 날짜 포맷 |
| web-vitals | ^4.x | LCP/INP/CLS 수집 |

## Dev Dependencies

| 패키지 | 목적 |
|---|---|
| vitest | 테스트 러너 |
| @testing-library/react | 컴포넌트 테스트 |
| @testing-library/user-event | 사용자 이벤트 시뮬 |
| @axe-core/react | a11y 자동 검사 |
| @playwright/test | E2E |
| @next/bundle-analyzer | 번들 크기 검사 (CI budget) |
| eslint, prettier | 코드 스타일 |

## Environment Variables

### Public (build-time baked, `NEXT_PUBLIC_` prefix)
- `NEXT_PUBLIC_API_BASE_URL` — gateway URL (예: `http://localhost:8080` 로컬, `https://gw.internal` 운영)
- `NEXT_PUBLIC_GRAFANA_BASE_URL` — Grafana 사내 URL

### Server-only (런타임 주입, 빌드 시 접근 불가)
- `COOKIE_DOMAIN` — 쿠키 scope (로컬: `localhost`, 운영: `.internal`)
- `NODE_ENV` — `development` / `production`

모든 env는 `shared/config/env.ts`에서 zod로 검증. 누락·잘못된 값은 서버 시작 시 즉시 실패.

## Build-time Dependencies

- **admin-api.md 기반 타입 생성** — 현 구현은 수동 TypeScript 선언 (`shared/api/admin-api.ts`). 향후 OpenAPI spec 생성 시 `openapi-typescript`로 자동화 가능

## Failure Mode Handling

| Downstream | 장애 시 UX |
|---|---|
| admin-service 502/504 | 재시도 토스트 + 폼 상태 보존 |
| auth-service (login) 장애 | "잠시 후 다시 시도" 메시지 + Grafana 링크는 여전히 노출 |
| Grafana 접근 불가 | iframe 로드 실패 → placeholder 텍스트 "대시보드 일시 중단" |
| 네트워크 오프라인 | 전체 앱 상단에 offline banner (navigator.onLine 감지) |
