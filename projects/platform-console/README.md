# platform-console

> 포트폴리오 엔터프라이즈 스위트를 단일 AWS/GCP-콘솔식 화면으로 통합. ADR-MONO-013 부트스트랩.

| 항목 | 값 |
|---|---|
| Domain | `saas` ([rules/taxonomy.md](../../rules/taxonomy.md#saas)) |
| Traits | `multi-tenant`, `integration-heavy`, `audit-heavy` |
| Service Types | `frontend-app` (`console-web`); `rest-api` (`console-bff`, Phase 7 deferred) |
| IdP | GAP — OIDC **public client** (Auth Code + PKCE), 운영자 로그인 + product/tenant 레지스트리 소비 |
| Hostname | `console.local` (Traefik routing, ADR-MONO-001) |
| Status | **v1 bootstrap (TASK-MONO-108 / ADR-MONO-013 Phase 1)** — skeleton only |

---

## Purpose

운영자가 한 번 로그인하여 `gap` · `wms` · `scm` (+ 향후 `erp` · `finance`) 도메인을 **하나의 콘솔 안에서** 운영한다. 콘솔이 각 도메인의 gateway/admin REST API를 호출해 화면을 렌더하는 **Model B (단일 UI)** — 런처가 아니다. 도메인 정의·rationale·결정 근거는 [PROJECT.md](PROJECT.md) + [ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) 참조.

---

## Service Map (의도)

본 부트스트랩은 디렉토리 + spec skeleton + 부트 가능한 `console-web` 최소 골격만 — 실제 콘솔 셸(SSO·카탈로그·테넌트 스위처·도메인 화면)은 후속 task.

| Service | 역할 | 후속 Task |
|---|---|---|
| `console-web` | 단일 콘솔 UI (frontend-app) | TASK-PC-FE-001 (셸+GAP SSO+카탈로그) → Phase 2 GAP 운영자 parity |
| `console-bff` | 교차 도메인 집약 API (rest-api) | ADR-MONO-013 Phase 7 deferred |

---

## Local Dev Quick Start

> v1 부트스트랩 시점 `console-web`은 최소 골격(정적 placeholder 카탈로그)만 부트된다. 실제 SSO·data-driven 카탈로그·도메인 화면은 TASK-PC-FE-001 머지 후.

```bash
# 1. 공유 Traefik 인프라 기동 (한 번만)
pnpm traefik:up

# 2. hosts 파일에 console.local 등록 (한 번만)
#    Windows: C:\Windows\System32\drivers\etc\hosts
echo "127.0.0.1  console.local" | sudo tee -a /etc/hosts

# 3. console-web 기동
pnpm console:up      # docker compose (Traefik join)
#   또는 로컬 dev: pnpm console:dev

# 4. 확인
curl -i http://console.local/api/health   # → {"status":"ok"}
```

---

## GAP IdP Integration

콘솔은 다른 프로젝트(백엔드 Resource Server)와 달리 **OIDC public client (Auth Code + PKCE)** 로서 운영자 로그인을 수행하고, GAP의 product/tenant 레지스트리를 카탈로그 소스로 소비한다.

GAP 측 선행 작업 (spec-first, [TASK-BE-296](../iam-platform/tasks/ready/TASK-BE-296-platform-console-oidc-client-and-product-registry.md)):
- OIDC public client `platform-console-web` (Auth Code + PKCE, redirect `http://console.local/...`)
- 운영자 가시 product/tenant 레지스트리 조회 surface

통합 계약: [specs/contracts/console-integration-contract.md](specs/contracts/console-integration-contract.md).

---

## Known Limitations (v1 부트스트랩)

- **셸 미구현** — 본 PR은 디렉토리·spec·docker-compose·env·부트 가능한 최소 `console-web` 골격만. SSO·data-driven 카탈로그·테넌트 스위처·도메인 화면은 TASK-PC-FE-001.
- **GAP OIDC client 미발행** — TASK-BE-296 (GAP project-internal) 선행 필요.
- **CI / portfolio sync 미등록** — frontend lint/build CI 편입 + `scripts/sync-portfolio.sh` 등재는 첫 셸 task 시점 별도.
- **admin-web 미폐기** — GAP `admin-web` 폐기는 ADR-MONO-013 Phase 3 (콘솔이 GAP 운영자 parity 검증 후). 본 PR과 무관.

---

## References

- [PROJECT.md](PROJECT.md) — domain · traits · service map · rationale
- [tasks/INDEX.md](tasks/INDEX.md) — project task lifecycle
- [ADR-MONO-013](../../docs/adr/ADR-MONO-013-platform-console-foundation.md) — 콘솔 foundation (Model B · 배치 · admin-web 폐기 · BFF 계약 · 8-phase roadmap)
- [specs/contracts/console-integration-contract.md](specs/contracts/console-integration-contract.md) — 교차 프로젝트 BFF 통합 계약
- [TASK-MONO-108](../../tasks/ready/TASK-MONO-108-adr-mono-013-accepted-platform-console-bootstrap.md) — 본 부트스트랩 task
