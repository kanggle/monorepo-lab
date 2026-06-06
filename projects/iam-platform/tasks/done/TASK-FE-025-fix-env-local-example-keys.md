# TASK-FE-025: fix — admin-web `.env.local.example` 키 정합 (env.ts 스키마와 일치)

## Goal

`apps/admin-web/.env.local.example`이 실제 코드가 사용하는 환경변수 키와 일치하지 않아, 신규 개발자가 예제를 그대로 복사하면 dashboard 페이지의 Grafana iframe이 디폴트 `https://grafana.internal/...`로 떨어져 로컬에서 resolve 실패한다.

### 현재 차이

| 코드(`src/shared/config/env.ts`)가 검증하는 키 | `.env.local.example`에 있는 키 |
|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | `NEXT_PUBLIC_API_BASE_URL` ✅ |
| `NEXT_PUBLIC_APP_URL` | (누락) |
| `NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL` | (누락) |
| `NEXT_PUBLIC_GRAFANA_SECURITY_URL` | (누락) |
| `NEXT_PUBLIC_GRAFANA_SYSTEM_URL` | (누락) |
| (없음) | `GRAFANA_DASHBOARD_URL` (잘못된 키) |
| (없음) | `LOG_LEVEL` (env.ts 스키마에 없음) |

### 영향

- 신규 개발자가 `cp .env.local.example .env.local` 후 `npm run dev` → 정상 동작하는 듯 보이지만 dashboard 페이지에서 iframe이 `grafana.internal`로 향하며 broken
- 2026-04-30 e2e 검증 중 본 결함 발견됨 (`apps/admin-web/.env.local`에 동일하게 잘못된 키 사용 중이었음)

## Scope

**In:**
- `apps/admin-web/.env.local.example` 갱신:
  - `NEXT_PUBLIC_API_BASE_URL` 유지 (디폴트 `http://localhost:8080` 유지하되 e2e profile 설명 코멘트 추가)
  - `NEXT_PUBLIC_APP_URL` 추가 (디폴트 `http://localhost:3000`)
  - `NEXT_PUBLIC_GRAFANA_ACCOUNTS_URL` / `_SECURITY_URL` / `_SYSTEM_URL` 추가 — 로컬에 Grafana 없을 때 placeholder URL(예: `http://localhost:9999/grafana-not-configured`) 사용 권장
  - `GRAFANA_DASHBOARD_URL`, `LOG_LEVEL` 삭제 (코드 미사용)
  - 파일 상단에 사용 안내 코멘트 추가 (e2e 환경 vs 운영 환경 분기 가이드)
- `apps/admin-web/.env.local` (있다면 git에 미반영이므로 직접 수정 불가하나 docs/guides에 가이드 갱신)

**Out:**
- `env.ts` 스키마 변경 없음
- 코드 변경 없음
- 실제 Grafana 인스턴스 셋업은 별도 운영 작업

## Acceptance Criteria

- [ ] `.env.local.example`의 모든 키가 `env.ts` 스키마의 키와 1:1 매핑
- [ ] 코드에서 사용하지 않는 키 제거 (`GRAFANA_DASHBOARD_URL`, `LOG_LEVEL`)
- [ ] 신규 개발자가 `cp .env.local.example .env.local` 후 `npm run dev`로 dashboard 페이지 진입 시 Grafana iframe이 endpoint를 명확히 식별할 수 있는 placeholder를 가리킴 (broken 상태 즉시 인지 가능)
- [ ] `npm run dev` 시 zod validation 에러 없음 (모든 필수 키 존재)
- [ ] `apps/admin-web/CLAUDE.md` 또는 README에 "로컬 개발 시 Grafana iframe 동작" 안내 추가 (선택)

## Related Specs

- 없음 (FE 환경 설정 정리)

## Related Contracts

- 없음

## Edge Cases

- e2e profile에서는 API_BASE_URL이 `http://localhost:18080`을 가리켜야 함 — `.env.local.example`에는 일반 dev `localhost:8080`을 두고, e2e용은 코멘트로 안내
- `NEXT_PUBLIC_*` prefix가 누락된 경우 Next.js 클라이언트 번들에 포함 안 됨 — 모든 브라우저 노출 변수 prefix 정합 점검

## Failure Scenarios

- `.env.local`이 기존 개발자 머신에 이미 잘못된 키로 존재 → example 갱신 후에도 자동 마이그레이션 안 됨. README에 "기존 .env.local 갱신 필요" 안내 권장
- `env.ts` 스키마가 새 키를 추가했는데 example만 갱신되고 .env.local은 그대로 → 동일 결함 재현 위험. CI에 example/스키마 정합 lint 추가 고려 (별도 태스크)

## Implementation Notes

- 이번 e2e 검증 중 `.env.local`은 이미 fix 적용됨 (커밋 안 됐음 — `.env.local`은 .gitignore 대상). example만 갱신하면 됨
- 추후 lint 자동화: `tools/scripts/check-env-example.ts`를 만들어 zod 스키마와 example의 키 집합을 비교하는 스크립트가 있으면 재발 방지에 도움 — 별도 태스크로 분리

## Risk Assessment

**낮은 위험도** — 예제 파일 변경만. 런타임 영향 없음. 신규 개발자 onboarding UX 개선.
