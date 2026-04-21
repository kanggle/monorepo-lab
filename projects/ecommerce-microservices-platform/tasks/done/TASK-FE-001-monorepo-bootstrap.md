# TASK-FE-001: 프론트엔드 모노레포 부트스트랩 — Turborepo + 공유 패키지 초기 구성

## Goal
프론트엔드 애플리케이션(web-store, admin-dashboard)의 기반이 되는 모노레포 구조와 공유 패키지를 초기 구성한다.
두 앱이 공유하는 `@repo/ui`, `@repo/api-client`, `@repo/types`, `@repo/utils` 패키지의 스켈레톤을 생성한다.

## Scope
- Turborepo 기반 모노레포 설정 (`turbo.json`, root `package.json`)
- 공유 패키지 스켈레톤 생성:
  - `packages/ui/`: 공통 UI 컴포넌트 라이브러리 (빈 export)
  - `packages/api-client/`: gateway-service API 호출 클라이언트 (기본 설정만)
  - `packages/types/`: 백엔드 API 계약 기반 공유 타입 정의
  - `packages/utils/`: 공통 유틸리티 함수
- TypeScript, ESLint, Prettier 공통 설정
- `apps/web-store/`: Next.js App Router 프로젝트 초기화 (FSD 구조 디렉터리만)
- `apps/admin-dashboard/`: Next.js App Router 프로젝트 초기화 (Layered by Feature 디렉터리만)

## Acceptance Criteria
- `pnpm install` 후 모든 패키지 의존성이 정상 해소된다
- `pnpm build` 로 전체 빌드가 성공한다
- `pnpm dev --filter web-store` 로 web-store가 localhost에서 기동된다
- `pnpm dev --filter admin-dashboard` 로 admin-dashboard가 localhost에서 기동된다
- 각 앱에서 `@repo/ui`, `@repo/types` 등 공유 패키지를 import할 수 있다
- web-store는 FSD 디렉터리 구조(`app/`, `pages/`, `widgets/`, `features/`, `entities/`, `shared/`)를 갖는다
- admin-dashboard는 Layered by Feature 구조(`app/`, `features/`, `shared/`)를 갖는다

## Related Specs
- `specs/services/web-store/architecture.md`
- `specs/services/admin-dashboard/architecture.md`
- `specs/contracts/http/` (API 타입 정의 참고)

## Related Contracts
- `specs/contracts/http/auth-api.md` (타입 정의 참고)
- `specs/contracts/http/product-api.md` (타입 정의 참고)
- `specs/contracts/http/order-api.md` (타입 정의 참고)
- `specs/contracts/http/search-api.md` (타입 정의 참고)
- `specs/contracts/http/payment-api.md` (타입 정의 참고)

## Edge Cases
- 기존 `apps/` 디렉터리에 백엔드 서비스가 이미 존재하므로 프론트엔드 앱과 충돌하지 않도록 구성
- pnpm workspace와 Gradle 빌드가 동일 루트에서 공존해야 한다
- Next.js 앱의 포트가 백엔드 서비스 포트(8080-8087)와 충돌하지 않아야 한다

## Failure Scenarios
- Node.js 버전 불일치 시 `.nvmrc` 또는 `engines` 필드로 명시
- 공유 패키지 빌드 순서가 잘못될 경우 Turborepo pipeline 설정으로 해결
