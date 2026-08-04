# finance-platform

> 비은행 금융 서비스(fintech) 백엔드 플랫폼. monorepo Phase 6 첫 Template 다운스트림 부트스트랩 (ADR-MONO-008, Option C).

| 항목 | 값 |
|---|---|
| Domain | `fintech` ([rules/domains/fintech.md](../../rules/domains/fintech.md)) |
| Traits | `transactional`, `regulated`, `audit-heavy` |
| Service Types | `rest-api`, `event-consumer` |
| IdP | IAM (`tenant_id=finance`) — [IAM integration](../iam-platform/PROJECT.md) |
| Hostname | `finance.local` (Traefik routing, ADR-MONO-001) |
| Status | **3 services shipped** — `account-service`, `ledger-service`, `gateway-service` (all registered in `settings.gradle`). Gateway added by TASK-MONO-357 (ADR-MONO-048 D7 step 4) to close an edge-security gap — Traefik previously routed straight to `account-service` with no JWT validation or rate limiting at the edge. |

---

## Purpose

계좌 개설 → KYC → 잔액 보유(hold)/해제(release)/capture → 자금 이동 → 정산 대조의 fintech 흐름을, 모든 자금 영향 연산의 멱등성·불변 감사 기록·규제(KYC/AML) 선행 게이트와 함께 관리하는 백엔드 플랫폼.

자세한 도메인 정의·rationale·service map 은 [PROJECT.md](PROJECT.md) 참조.

---

## Service Map

`gateway-service` 가 엣지에서 GAP RS256 JWT 검증 + `tenant_id=finance` gate + Redis 기반 rate limit 을 수행한 뒤 요청을 라우팅한다(TASK-MONO-357). `account-service`/`ledger-service` 는 Traefik 라벨이 없는 `expose:`-only 서비스로, gateway-service 를 통해서만 도달 가능하다.

| Service | 역할 | 구현 Task |
|---|---|---|
| `gateway-service` | 엣지 라우팅(`Path=/api/finance/accounts/**` → account-service, `Path=/api/finance/ledger/**` → ledger-service), GAP RS256 JWT 검증, `tenant_id=finance` gate, Redis rate limit | TASK-MONO-357 |
| `account-service` | Account 라이프사이클 — KYC / 잔액 hold·release·capture / 계좌 상태기계 / 자금 이동 멱등 / 불변 audit_log | TASK-FIN-BE-001 |
| `ledger-service` | 복식부기 원장(GL) — account-service 의 거래 이벤트(`finance.transaction.*`) 소비, 분개·잔액 reconciliation | TASK-FIN-BE-007 |

미착수(추가 v2 후보): wallet-service, kyc-service, notification-service, admin-service.

---

## Local Dev Quick Start

> `pnpm finance:up` 이 `gateway-service` · `account-service` · `ledger-service` 와 백킹 인프라(mysql ×2 / redis / kafka)를 모두 기동한다. `http://finance.local/` 은 `gateway-service` 를 통해서만 진입하며, 두 백엔드는 Traefik 라벨이 없는 내부 전용 서비스다(TASK-MONO-357).

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

## Known Limitations

- **frontend 없음** — finance v1 = backend only. UI 는 통합 platform console 이 렌더 (ADR-MONO-013 §3.3). user-flow PKCE OIDC client 도 미발행.
- **게이트웨이 후행 도입** — `gateway-service` 는 account-service/ledger-service 보다 나중에 추가됐다(TASK-MONO-357, ADR-MONO-048 D7 step 4). 그 전에는 Traefik 이 `account-service` 로 직접 라우팅했고, 이는 `platform/api-gateway-policy.md` (엣지 미경유 백엔드 금지) 위반이었다 — 이제 해소됨. 두 백엔드는 여전히 자체 `ServiceLevelOAuth2Config` 로 JWT 검증을 이중 방어로 유지한다.
- **v2 후보 서비스 미착수** — wallet-service, kyc-service, notification-service, admin-service 는 아직 구현되지 않았다 ([PROJECT.md § Service Map](PROJECT.md#service-map) 참조).

---

## References

- [PROJECT.md](PROJECT.md) — domain · traits · service map · IAM integration · trait rationale
- [tasks/INDEX.md](tasks/INDEX.md) — project task lifecycle
- [rules/domains/fintech.md](../../rules/domains/fintech.md) — fintech 도메인 mandatory rules · bounded contexts · ubiquitous language
- [ADR-MONO-008](../../docs/adr/ADR-MONO-008-finance-platform-bootstrap.md) — finance-platform 부트스트랩 결정 (Option C)
- [TASK-MONO-114](../../tasks/ready/) (본 부트스트랩 artifact) / TASK-FIN-BE-001 (account-service 구현) / TASK-FIN-BE-007 (ledger-service 구현) / [TASK-MONO-357](../../tasks/done/TASK-MONO-357-finance-erp-gateways.md) (gateway-service 신설, ADR-MONO-048 D7 step 4)
- [TEMPLATE.md § Local Network Convention](../../TEMPLATE.md) — 신규 프로젝트 IAM 통합 + hostname routing 표준 절차
