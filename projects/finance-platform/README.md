# finance-platform

> 비은행 금융 서비스(fintech) 백엔드 플랫폼. monorepo Phase 6 첫 Template 다운스트림 부트스트랩 (ADR-MONO-008, Option C).

| 항목 | 값 |
|---|---|
| Domain | `fintech` ([rules/domains/fintech.md](../../rules/domains/fintech.md)) |
| Traits | `transactional`, `regulated`, `audit-heavy` |
| Service Types | `rest-api`, `event-consumer` |
| IdP | IAM (`tenant_id=finance`) — [IAM integration](../iam-platform/PROJECT.md) |
| Hostname | `finance.local` (Traefik routing, ADR-MONO-001) |
| Status | **v1 bootstrap (TASK-MONO-114)** — skeleton only, account-service 미가동 |

---

## Purpose

계좌 개설 → KYC → 잔액 보유(hold)/해제(release)/capture → 자금 이동 → 정산 대조의 fintech 흐름을, 모든 자금 영향 연산의 멱등성·불변 감사 기록·규제(KYC/AML) 선행 게이트와 함께 관리하는 백엔드 플랫폼.

자세한 도메인 정의·rationale·service map 은 [PROJECT.md](PROJECT.md) 참조.

---

## v1 Service Map (의도)

본 부트스트랩은 디렉토리 + account-service 최소 skeleton 만 — 서비스 구현은 후속 task 에서.

| Service | 역할 | 후속 Task |
|---|---|---|
| `gateway-service` | 엣지 라우팅, IAM RS256 JWT 검증, `tenant_id=finance` gate | 후속 task |
| `account-service` | Account 라이프사이클 — KYC / 잔액 hold·release·capture / 계좌 상태기계 / 자금 이동 멱등 / 불변 audit_log | TASK-FIN-BE-001 |

v2 deferred: ledger-service (복식부기/GL/AP), wallet-service, kyc-service, notification-service, admin-service.

---

## Local Dev Quick Start

> v1 부트스트랩 시점에는 service 컨테이너가 비어있어 `pnpm finance:up` 이 backing services (mysql / redis) 만 띄운다. account-service 구현 (TASK-FIN-BE-001) 머지 후 gateway-service + account-service 가 활성화된다.

```bash
# 1. 공유 Traefik 인프라 기동 (한 번만)
pnpm traefik:up

# 2. hosts 파일에 finance.local 등록 (한 번만)
#    Linux/macOS: /etc/hosts
#    Windows: C:\Windows\System32\drivers\etc\hosts
echo "127.0.0.1  finance.local" | sudo tee -a /etc/hosts

# 3. finance-platform 백킹 서비스 기동
pnpm finance:up

# 4. 상태 확인
pnpm finance:ps
pnpm finance:logs

# 5. 정지
pnpm finance:down
```

dev 토큰 발급 (IAM `finance-platform-internal-services-client` 등록 완료, TASK-MONO-114 V0017):
```bash
curl -u finance-platform-internal-services-client:finance-dev \
     -d "grant_type=client_credentials&scope=finance.read" \
     http://iam.local/oauth2/token
```

---

## IAM IdP Integration

finance-platform 의 모든 서비스는 OAuth2 Resource Server 패턴으로 IAM RS256 JWT 를 검증하며 `tenant_id=finance` claim 만 통과시킨다.

IAM 측 인프라 (TASK-MONO-114 V0017 시드):
- `tenants.tenant_id='finance'` (B2B_ENTERPRISE) — account-service V0017
- `oauth_clients.client_id='finance-platform-internal-services-client'` (client_credentials, scopes=`finance.read`/`finance.write`) — auth-service V0017
- `oauth_scopes` — `finance.read`, `finance.write` — auth-service V0017

상세는 [PROJECT.md § IAM IdP Integration](PROJECT.md#iam-idp-integration) + [specs/integration/iam-integration.md](specs/integration/iam-integration.md).

---

## Known Limitations (v1 부트스트랩)

- **service 코드 최소** — 본 부트스트랩 PR 은 디렉토리·docker-compose·env·domain rule + account-service 부트 가능 skeleton (비즈니스 로직 0) 만. 도메인 구현은 TASK-FIN-BE-001.
- **frontend 없음** — finance v1 = backend only. UI 는 통합 platform console 이 렌더 (ADR-MONO-013 §3.3). user-flow PKCE OIDC client 도 미발행.
- **CI 미포함 확장** — account-service skeleton 은 settings.gradle 에 등록되어 `:tasks` resolves; 첫 구현 (TASK-FIN-BE-001) 에서 테스트·CI 표면 확장.

---

## References

- [PROJECT.md](PROJECT.md) — domain · traits · service map · IAM integration · trait rationale
- [tasks/INDEX.md](tasks/INDEX.md) — project task lifecycle
- [rules/domains/fintech.md](../../rules/domains/fintech.md) — fintech 도메인 mandatory rules · bounded contexts · ubiquitous language
- [ADR-MONO-008](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) — finance-platform 부트스트랩 결정 (Option C)
- [TASK-MONO-114](../../tasks/ready/) (본 부트스트랩 artifact) / TASK-FIN-BE-001 (account-service 구현)
- [TEMPLATE.md § Local Network Convention](../../TEMPLATE.md) — 신규 프로젝트 IAM 통합 + hostname routing 표준 절차
