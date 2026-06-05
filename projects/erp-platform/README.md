# erp-platform

> 전사 기간계(erp) 백엔드 플랫폼. monorepo Phase 6 두 번째 Template 다운스트림 부트스트랩 (ADR-MONO-016, Option C).

| 항목 | 값 |
|---|---|
| Domain | `erp` ([rules/domains/erp.md](../../rules/domains/erp.md)) |
| Traits | `internal-system`, `transactional`, `audit-heavy` |
| Service Types | `rest-api` |
| IdP | GAP (`tenant_id=erp`) — [GAP integration](../iam-platform/PROJECT.md) |
| Hostname | `erp.local` (Traefik routing, ADR-MONO-001) |
| Status | **v1 bootstrap (TASK-MONO-119)** — skeleton only, masterdata-service 미가동 |

---

## Purpose

조직 마스터데이터(부서/직원/직급/비용센터/거래처) → 결재 워크플로 → 통합 조회 read model 의 erp 흐름을, 마스터데이터 무결성·결재 상태 전이의 추적가능성·통합 조회의 책임 경계와 함께 사내 임직원 전용으로 관리하는 백엔드 플랫폼.

자세한 도메인 정의·rationale·service map 은 [PROJECT.md](PROJECT.md) 참조.

---

## v1 Service Map (의도)

본 부트스트랩은 디렉토리 + masterdata-service 최소 skeleton 만 — 서비스 구현은 후속 task 에서.

| Service | 역할 | 후속 Task |
|---|---|---|
| `gateway-service` | 엣지 라우팅, GAP RS256 JWT 검증, `tenant_id=erp` gate, internal-only 경계 | 후속 task |
| `masterdata-service` | 조직 마스터데이터 — 부서/직원/직급/비용센터/거래처 / 참조 무결성 / 유효기간 / 불변 audit_log | TASK-ERP-BE-001 |

v2 deferred: approval-service (결재 워크플로), read-model-service (통합 조회), permission-service, notification-service, admin-service.

---

## Local Dev Quick Start

> v1 부트스트랩 시점에는 service 컨테이너가 비어있어 `pnpm erp:up` 이 backing services (mysql / redis) 만 띄운다. masterdata-service 구현 (TASK-ERP-BE-001) 머지 후 gateway-service + masterdata-service 가 활성화된다.

```bash
# 1. 공유 Traefik 인프라 기동 (한 번만)
pnpm traefik:up

# 2. hosts 파일에 erp.local 등록 (한 번만)
#    Linux/macOS: /etc/hosts
#    Windows: C:\Windows\System32\drivers\etc\hosts
echo "127.0.0.1  erp.local" | sudo tee -a /etc/hosts

# 3. erp-platform 백킹 서비스 기동
pnpm erp:up

# 4. 상태 확인
pnpm erp:ps
pnpm erp:logs

# 5. 정지
pnpm erp:down
```

dev 토큰 발급 (GAP `erp-platform-internal-services-client` 등록 완료, TASK-MONO-119 V0018):
```bash
curl -u erp-platform-internal-services-client:erp-dev \
     -d "grant_type=client_credentials&scope=erp.read" \
     http://iam.local/oauth2/token
```

---

## GAP IdP Integration

erp-platform 의 모든 서비스는 OAuth2 Resource Server 패턴으로 GAP RS256 JWT 를 검증하며 `tenant_id=erp` claim 만 통과시킨다 (internal-system 경계 — 외부 공개 트래픽 없음).

GAP 측 인프라 (TASK-MONO-119 V0018 시드):
- `tenants.tenant_id='erp'` (B2B_ENTERPRISE) — account-service V0018
- `oauth_clients.client_id='erp-platform-internal-services-client'` (client_credentials, scopes=`erp.read`/`erp.write`) — auth-service V0018
- `oauth_scopes` — `erp.read`, `erp.write` — auth-service V0018

상세는 [PROJECT.md § GAP IdP Integration](PROJECT.md#iam-idp-integration) + [specs/integration/iam-integration.md](specs/integration/iam-integration.md).

---

## Known Limitations (v1 부트스트랩)

- **service 코드 최소** — 본 부트스트랩 PR 은 디렉토리·docker-compose·env·domain rule + masterdata-service 부트 가능 skeleton (비즈니스 로직 0) 만. 도메인 구현은 TASK-ERP-BE-001.
- **frontend 없음** — erp v1 = backend only. UI 는 통합 platform console 이 렌더 (ADR-MONO-013 §3.3). user-flow PKCE OIDC client 도 미발행.
- **standalone fork PENDING** — 외부 `kanggle/erp-platform` Template fork 는 classifier-blocked outward-facing op 으로 사용자 셸 hand-off PENDING (finance / TASK-MONO-116 동형). 본 PR-B 는 monorepo side(Option C)만 landed.
- **CI 미포함 확장** — masterdata-service skeleton 은 settings.gradle 에 등록되어 `:tasks` resolves; 첫 구현 (TASK-ERP-BE-001) 에서 테스트·CI 표면 확장.

---

## References

- [PROJECT.md](PROJECT.md) — domain · traits · service map · GAP integration · trait rationale
- [tasks/INDEX.md](tasks/INDEX.md) — project task lifecycle
- [rules/domains/erp.md](../../rules/domains/erp.md) — erp 도메인 mandatory rules · bounded contexts · ubiquitous language
- [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) — erp-platform 부트스트랩 결정 (Option C)
- [TASK-MONO-119](../../tasks/ready/) (본 부트스트랩 artifact) / TASK-ERP-BE-001 (masterdata-service 구현)
- [TEMPLATE.md § Local Network Convention](../../TEMPLATE.md) — 신규 프로젝트 GAP 통합 + hostname routing 표준 절차
