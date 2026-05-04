# scm-platform

> Supply Chain Management 백엔드 플랫폼. monorepo Phase 4 catalyst 도메인.

| 항목 | 값 |
|---|---|
| Domain | `scm` ([rules/domains/scm.md](../../rules/domains/scm.md)) |
| Traits | `transactional`, `integration-heavy`, `batch-heavy` |
| Service Types | `rest-api`, `event-consumer`, `batch-job` |
| IdP | GAP (`tenant_id=scm`) — [GAP integration](../global-account-platform/PROJECT.md) |
| Hostname | `scm.local` (Traefik routing, ADR-MONO-001) |
| Status | **v1 bootstrap (TASK-MONO-040)** — skeleton only, 첫 service 미가동 |

---

## Purpose

조달(Procurement) → 운송(Logistics) → 정산(Settlement) 의 cross-functional 공급망 흐름을 다수 외부 시스템(supplier ERP / carrier API / bank / 자사 wms-platform) 과 연동하면서 일관된 상태 머신으로 관리하는 백엔드 플랫폼.

자세한 도메인 정의·rationale·service map 은 [PROJECT.md](PROJECT.md) 참조.

---

## v1 Service Map (의도)

본 부트스트랩은 디렉토리 + spec skeleton 만 — 서비스 코드는 후속 task 에서.

| Service | 역할 | 후속 Task |
|---|---|---|
| `gateway-service` | 엣지 라우팅, GAP RS256 JWT 검증, `tenant_id=scm` gate | TASK-SCM-BE-001 |
| `procurement-service` | PO / supplier ack / ASN 수신 처리 | TASK-SCM-BE-001+ |
| `inventory-visibility-service` | cross-node 재고 가시성 read-model | TASK-SCM-BE-002+ |

v2 deferred: supplier-service, demand-planning-service, logistics-service, settlement-service, notification-service, admin-service.

---

## Local Dev Quick Start

> v1 부트스트랩 시점에는 service 컨테이너가 비어있어 `pnpm scm:up` 이 backing services (postgres / redis / kafka) 만 띄운다. 첫 service skeleton (TASK-SCM-BE-001) 머지 후 gateway-service + procurement-service 가 활성화된다.

```bash
# 1. 공유 Traefik 인프라 기동 (한 번만)
pnpm traefik:up

# 2. hosts 파일에 scm.local 등록 (한 번만)
#    Linux/macOS: /etc/hosts
#    Windows: C:\Windows\System32\drivers\etc\hosts
echo "127.0.0.1  scm.local" | sudo tee -a /etc/hosts

# 3. scm-platform 백킹 서비스 기동
pnpm scm:up

# 4. 상태 확인
pnpm scm:ps
pnpm scm:logs

# 5. 정지
pnpm scm:down
```

활성화 후 (TASK-SCM-BE-001 머지 후):
```bash
curl -i http://scm.local/actuator/health
# → 200 OK from gateway-service
```

dev 토큰 발급 (GAP `scm-platform-internal-services-client` 등록 완료, TASK-MONO-042 V0013):
```bash
curl -u scm-platform-internal-services-client:scm-dev \
     -d "grant_type=client_credentials&scope=scm.read" \
     http://gap.local/oauth2/token
```

---

## GAP IdP Integration

scm-platform 의 모든 서비스는 OAuth2 Resource Server 패턴으로 GAP RS256 JWT 를 검증하며 `tenant_id=scm` claim 만 통과시킨다.

GAP 측 인프라 (TASK-MONO-042 머지 완료):
- `tenants.tenant_id='scm'` (B2B_ENTERPRISE) — account-service V0015
- `oauth_clients.client_id='scm-platform-internal-services-client'` (client_credentials, scopes=`scm.read`/`scm.write`) — auth-service V0013
- `oauth_scopes` — `scm.read`, `scm.write` — auth-service V0013

상세는 [PROJECT.md § GAP IdP Integration](PROJECT.md#gap-idp-integration) + (후속) `specs/integration/gap-integration.md`.

---

## Known Limitations (v1 부트스트랩)

- **service 코드 0** — 본 부트스트랩 PR 은 디렉토리·docker-compose·env·domain rule 만. 첫 service skeleton 은 TASK-SCM-BE-001.
- **frontend 없음** — scm v1 = backend only. user-flow PKCE OIDC client 도 미발행 (frontend 도입 시점에 별도 V slot).
- **portfolio sync 미등록** — `scripts/sync-portfolio.sh` 의 `PROJECT_REMOTES` 에 아직 등재 안 함. 첫 v1 publish 시점에 별도 task 에서.
- **CI 미포함** — root `.github/workflows/ci.yml` 의 Build & Test gradle 리스트에 scm 모듈 없음 (apps 가 비어있어 추가할 모듈도 없음). 첫 service skeleton 등장 시점에 함께.

---

## References

- [PROJECT.md](PROJECT.md) — domain · traits · service map · GAP integration · trait rationale
- [tasks/INDEX.md](tasks/INDEX.md) — project task lifecycle
- [rules/domains/scm.md](../../rules/domains/scm.md) — scm 도메인 mandatory rules · bounded contexts · ubiquitous language
- [ADR-MONO-002](../../docs/adr/ADR-MONO-002-phase-4-template-extraction-trigger.md) — Phase 4 catalyst 결정
- [TASK-MONO-040](../../tasks/done/) (본 부트스트랩) / [TASK-MONO-042](../../tasks/done/) (GAP V0013/V0015 시드)
- [TEMPLATE.md § GAP IdP Integration Pattern](../../TEMPLATE.md#gap-idp-integration-pattern-new-projects) — 신규 프로젝트의 GAP 통합 표준 절차
