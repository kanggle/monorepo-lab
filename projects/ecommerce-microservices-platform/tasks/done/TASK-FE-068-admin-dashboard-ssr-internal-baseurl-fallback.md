# Task ID

TASK-FE-068

# Title

admin-dashboard 의 SSR `API_URL_INTERNAL` fallback 추가 — web-store 와 정합성

# Status

ready

# Owner

frontend

# Task Tags

- code
- bug

---

# Required Sections

- Goal
- Scope (in/out)
- Acceptance Criteria
- Related Specs
- Related Contracts
- Edge Cases
- Failure Scenarios

---

# Goal

`admin-dashboard` 의 axios 클라이언트 `src/shared/config/api.ts` 가 SSR (server-side fetch) 시 컨테이너 내부 hostname (`API_URL_INTERNAL`) 을 사용하도록 수정한다. 현재 `web-store` 와 다르게 항상 `NEXT_PUBLIC_API_URL` 만 사용 → docker compose 환경에서 SSR fetch 가 외부 hostname (`http://ecommerce.local`) 으로 시도되어 컨테이너 네트워크 안에서 도달 못 함.

[TASK-FE-067](../done/TASK-FE-067-frontend-gap-oauth-cutover.md) 머지 시 식별된 follow-up — done 표 항목에 "TASK-FE-068 candidate" 로 명시. cutover 전체 흐름 안에서 admin-dashboard 측은 SSR fetch 자체가 적어 즉시 깨지진 않았으나, web-store 와 admin-dashboard 의 baseURL 결정 로직이 drift 한 상태로 남아 있음.

이 task 완료 후:
- admin-dashboard 의 `apiClient.baseURL` 결정 로직이 web-store 와 1:1 동일
- SSR (Next.js Server Component, route handler, middleware) 에서 `API_URL_INTERNAL` 우선, 없으면 `NEXT_PUBLIC_API_URL`, 없으면 localhost fallback
- client-side (브라우저) 는 그대로 `NEXT_PUBLIC_API_URL` 만 사용 (브라우저는 docker 내부 네트워크에 접근 불가)
- docker-compose env / k8s configmap / `.env.example` 의 `API_URL_INTERNAL` 변수가 admin-dashboard 에도 명시 노출

---

# Scope

## In Scope

### 1. `apps/admin-dashboard/src/shared/config/api.ts` 수정

web-store 의 동일 파일과 1:1 일치 (export 명/주석 포함):

```typescript
const isServer = typeof window === 'undefined';
const baseURL = isServer
  ? process.env.API_URL_INTERNAL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'
  : process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export const apiClient = new ApiClient({
  baseURL,
  // ... (기존 getAccessToken / getRefreshToken / onAuthError 유지)
});
```

### 2. docker-compose env 노출

- `projects/ecommerce-microservices-platform/docker-compose.yml` 의 `admin-dashboard` 서비스 environment 에 `API_URL_INTERNAL: http://gateway-service:8080` 추가
- web-store 는 이미 동일 패턴 — 두 frontend 가 동일 환경변수 셋

### 3. `.env.example` 갱신

- `projects/ecommerce-microservices-platform/.env.example` 의 frontend 섹션에 `API_URL_INTERNAL` 가 admin-dashboard 에도 적용된다는 주석 추가 (이미 web-store 용으로 선언되어 있다면 주석만 갱신)

### 4. k8s manifest 갱신 (있는 경우)

- `infra/k8s/admin-dashboard/deployment.yaml` (또는 동등 위치) 의 env 에 `API_URL_INTERNAL` 추가
- web-store 와 동일 source (configmap 공유 가능)

### 5. 단위 테스트 (vitest)

- `apps/admin-dashboard/src/shared/config/__tests__/api.test.ts` (신규):
  - SSR 환경 + `API_URL_INTERNAL` 설정 → baseURL = internal URL
  - SSR 환경 + `API_URL_INTERNAL` 미설정 → fallback to `NEXT_PUBLIC_API_URL`
  - SSR 환경 + 둘 다 미설정 → fallback to `http://localhost:8080`
  - client 환경 (`window` 정의됨) → `NEXT_PUBLIC_API_URL` 만 사용 (internal 무시)
- web-store 의 같은 테스트가 있다면 답습. 없다면 web-store 측에도 같은 테스트 추가 (drift 회귀 방지) — 본 task 범위에 포함

### 6. CI

- 별도 CI 변경 없음 (frontend-unit-tests / frontend-checks job 이 기존 패턴으로 본 변경 검증)

## Out of Scope

- web-store 의 `api.ts` 변경 (이미 정상)
- `@repo/api-client` 라이브러리 자체 변경 (axios 클라이언트 추상화는 그대로)
- 다른 NextAuth / signIn callback / token refresh 흐름 (TASK-FE-067 영역, 본 task 와 무관)
- 다른 frontend (fan-platform-web 등) — 별도 프로젝트라 본 task 범위 밖
- prod CDN / edge fetch 패턴 — 현재 dev / docker compose / k8s 만 대상

---

# Acceptance Criteria

1. `apps/admin-dashboard/src/shared/config/api.ts` 의 baseURL 결정 로직이 web-store 의 같은 파일과 textual diff 0 (export name / 주석 / 변수명 1:1 동일)
2. `pnpm --filter admin-dashboard test` — 단위 테스트 4개 (SSR with internal / SSR fallback / SSR localhost / client) 모두 PASS
3. `pnpm --filter web-store test` — 같은 테스트 추가됐다면 (5번 항목) 4개 PASS
4. `docker compose --project-directory projects/ecommerce-microservices-platform config` — 새 `API_URL_INTERNAL` env 가 admin-dashboard 서비스에 노출
5. `pnpm ecommerce:up` 후 admin-dashboard SSR 페이지가 docker 네트워크 내부에서 `gateway-service:8080` 으로 fetch (브라우저 devtools network 탭 이전에 server log 로 확인)
6. 브라우저 (client) 에서는 그대로 `http://ecommerce.local/api/...` 호출 — internal URL 누설 없음
7. k8s manifest 에 `API_URL_INTERNAL` configmap 참조 추가 (있는 경우)
8. `frontend-unit-tests` / `frontend-checks` CI job 통과
9. `frontend-e2e-smoke` CI job 통과 (smoke 는 closed loopback 이라 본 변경에 영향받지 않음)

---

# Related Specs

- [TASK-FE-067](../done/TASK-FE-067-frontend-gap-oauth-cutover.md) (done) — 본 task 의 candidate 가 done 표 항목에 명시
- [TASK-MONO-027](../../../../tasks/done/TASK-MONO-027-ecommerce-gap-oidc-integration.md) (done) — gateway issuer-uri / validators 셋업 (frontend cutover 의 backend 측 선행)
- [`projects/ecommerce-microservices-platform/apps/web-store/src/shared/config/api.ts`](../../apps/web-store/src/shared/config/api.ts) — reference 패턴 (정합성 대상)
- `platform/architecture-decision-rule.md` — frontend 측 service-types/frontend-app 적용

# Related Skills

- `.claude/skills/frontend/nextjs-config/SKILL.md` (있다면)
- `.claude/skills/testing/vitest/SKILL.md` (있다면)

---

# Related Contracts

- 변경 없음. 본 task 는 frontend config 만 수정 — API contract / event contract 영향 0.

---

# Edge Cases

1. **`NEXT_PUBLIC_API_URL` 만 설정된 dev 환경**: `API_URL_INTERNAL` 누락 시 SSR 도 그대로 `NEXT_PUBLIC_API_URL` 사용 (현재 admin-dashboard 동작과 동일) — 회귀 없음.
2. **prod 환경에서 `API_URL_INTERNAL` 미설정**: 프로덕션 k8s 는 service mesh / ingress 통해 외부 hostname 으로도 접근 가능 — fallback 동작이 prod 에서 깨지지 않음. 단 권장은 prod 에서도 internal 명시.
3. **NEXT_PUBLIC_* 변수의 build-time 베이킹**: `NEXT_PUBLIC_API_URL` 은 빌드 시점에 클라이언트 번들에 박힘 → 빌드 후 런타임 변경 불가. `API_URL_INTERNAL` 은 server-only 라 런타임 env 로 변경 가능. 본 task 는 이 차이를 그대로 수용 (web-store 와 동일).
4. **smoke E2E (`playwright.smoke.config.ts` 강제 closed loopback)**: smoke 는 `NEXT_PUBLIC_API_URL=http://127.0.0.1:1` 로 빌드 → 의도적 SSR / fetch 실패 경로 검증. 본 변경은 closed loopback 시나리오에 영향 없음 (internal 도 동일하게 도달 불가).
5. **단위 테스트의 `process.env` mutation**: vitest 는 ESM 환경이라 `process.env` 변경이 module-level const 캡처 후에는 반영 안 됨. 테스트는 `import.meta.env` 가 아닌 `process.env` 를 다루므로 `vi.resetModules()` + dynamic import 패턴 필요 (web-store 의 같은 테스트가 있다면 그 패턴 답습).

---

# Failure Scenarios

## A. docker compose 변경 시 admin-dashboard 컨테이너 재빌드 누락

`API_URL_INTERNAL` 은 server-only env 라 런타임 주입이지만, docker-compose env 변경 시 기존 컨테이너 재시작 필요. mitigation: `pnpm ecommerce:up --build` 또는 `docker compose restart admin-dashboard` 수동 실행. 영향 범위 작음 (dev only).

## B. k8s manifest 누락 → prod 배포 시 SSR 가 외부 hostname 사용

prod 에서 admin-dashboard 가 k8s 내부 service DNS 가 아닌 외부 ingress hostname 으로 fetch — 추가 hop / TLS termination / latency. mitigation: 본 task 의 #4 (k8s manifest 갱신) 가 fail-safe.

## C. web-store 측 같은 변경 추가 시 export 명 충돌

web-store 의 `__tests__/api.test.ts` 가 이미 존재할 수 있음 (TASK-FE-067 시점에 추가됐을 가능성). 추가하려면 먼저 grep 으로 확인 → 있으면 답습, 없으면 양쪽 동시 추가. 충돌 위험 낮음.

## D. PR 머지 후 dev 환경에서 SSR fetch 재현 안 됨

admin-dashboard 가 SSR fetch 를 거의 안 하면 (대부분 client-side query) 본 변경의 효과가 가시화되지 않음. 단위 테스트 + 한 SSR 페이지 (예: `/admin/dashboard` 의 server component 가 product 통계 fetch) 의 server log 검증으로 보완.

---

# Notes

- **Recommended impl model**: **Sonnet 4.6** — 단일 파일 textual diff + docker-compose env 1줄 + 단위 테스트 4개. 분석=Opus 4.7 / 구현 권장=Sonnet 4.6.
- **분량 추정**: production code 변경 ~10 줄, 테스트 ~80 줄, docker-compose / .env.example / k8s manifest 변경 각 1~3 줄. 단일 PR.
- **dependency 표현**:
  - `선행`: TASK-FE-067 (done) — cutover 본체. 본 task 의 candidate 가 그 done 표 항목에 명시
  - `후속`: 없음. 본 task 는 cutover follow-up 의 자체 종결.
- **리스크**: 매우 낮음. textual diff + 단위 테스트로 회귀 격리. e2e-smoke 는 closed loopback 이라 영향 없음.
