# erp-platform

> 전사 기간계(erp) 백엔드 플랫폼. monorepo Phase 6 두 번째 Template 다운스트림 부트스트랩 (ADR-MONO-016, Option C).

| 항목 | 값 |
|---|---|
| Domain | `erp` ([rules/domains/erp.md](../../rules/domains/erp.md)) |
| Traits | `internal-system`, `transactional`, `audit-heavy` |
| Service Types | `rest-api`, `event-consumer` |
| IdP | IAM (`tenant_id=erp`) — [IAM integration](../iam-platform/PROJECT.md) |
| Hostname | `erp.local` (Traefik routing, ADR-MONO-001) |
| Status | ✅ **v1 — 4 domain services + gateway implemented**: `gateway-service` · `masterdata-service` · `read-model-service` · `approval-service` · `notification-service`. All in `settings.gradle`, ~330 `.java` files under `apps/*/src/main/java`, 40 completed tasks in `tasks/done/`. Gateway added by [TASK-MONO-357](../../tasks/done/TASK-MONO-357-finance-erp-gateways.md) — Traefik now labels only `gateway-service`. Console integration mature — platform-console renders live masters/org-view/approval/delegation UI. Not yet published to a standalone repo (see § Known Limitations). |

---

## Purpose

조직 마스터데이터(부서/직원/직급/비용센터/거래처) → 결재 워크플로 → 통합 조회 read model 의 erp 흐름을, 마스터데이터 무결성·결재 상태 전이의 추적가능성·통합 조회의 책임 경계와 함께 사내 임직원 전용으로 관리하는 백엔드 플랫폼.

자세한 도메인 정의·rationale·service map 은 [PROJECT.md](PROJECT.md) 참조.

---

## v1 Service Map (구현 완료)

| Service | Service Type | 핵심 책임 | `.java` 파일 수 |
|---|---|---|---:|
| `gateway-service` | `rest-api` (edge gateway role) | 엣지 라우팅, GAP RS256 JWT 검증 (OAuth2 Resource Server), `tenant_id=erp` 게이트, internal-only 경계 강제, Redis rate-limit ([TASK-MONO-357](../../tasks/done/TASK-MONO-357-finance-erp-gateways.md)) | 4 |
| `masterdata-service` | `rest-api` | 조직 마스터데이터 — 부서/직원/직급/비용센터/거래처 라이프사이클, 참조 무결성, 유효기간, 불변 audit_log (TASK-ERP-BE-001) | 84 |
| `read-model-service` | `rest-api` + `event-consumer` | 통합 조회 read model — masterdata 의 4 토픽(department/employee/jobgrade/costcenter changed) 구독 → projection → employee org-view REST (TASK-ERP-BE-007), 이후 approval/delegation fact projection 확장 (TASK-ERP-BE-010/015/018) | 95 |
| `approval-service` | `rest-api` | 결재 워크플로 — 다단계 상신/승인/반려 상태기계 (TASK-ERP-BE-009/012), 대결/위임(delegation) (TASK-ERP-BE-013/017) | 74 |
| `notification-service` | `event-consumer` (primary) + `rest-api` (in-app inbox read) | 결재 상신·위임·철회 이벤트 구독 → 알림 인박스 projection (TASK-ERP-BE-011/014/016) + 외부 채널 재시도 스케줄러 (TASK-ERP-BE-020) | 75 |

각 서비스의 내부 아키텍처는 `specs/services/<service>/architecture.md` 에 선언되어 있습니다.

v2 deferred (아직 미구현): `permission-service`(권한 매트릭스/데이터 범위), `admin-service`(운영 콘솔 백엔드 — 예외 결재 검토, 권한 이상, 마스터 충돌 큐). 상세는 [PROJECT.md § Service Map](PROJECT.md#service-map).

---

## Local Dev Quick Start

```bash
# 1. 공유 Traefik 인프라 기동 (한 번만)
pnpm traefik:up

# 2. hosts 파일에 erp.local 등록 (한 번만)
#    Linux/macOS: /etc/hosts
#    Windows: C:\Windows\System32\drivers\etc\hosts
echo "127.0.0.1  erp.local" | sudo tee -a /etc/hosts

# 3. erp-platform 전 서비스 기동 (gateway + masterdata + read-model
#    + approval + notification + mysql/redis/kafka backing services)
pnpm erp:up

# 4. 상태 확인
pnpm erp:ps
pnpm erp:logs

# 5. 정지
pnpm erp:down
```

dev 토큰 발급 (IAM `erp-platform-internal-services-client` 등록 완료, TASK-MONO-119 V0018):
```bash
curl -u erp-platform-internal-services-client:erp-dev \
     -d "grant_type=client_credentials&scope=erp.read" \
     http://iam.local/oauth2/token
```

---

## IAM IdP Integration

erp-platform 의 모든 서비스는 OAuth2 Resource Server 패턴으로 IAM RS256 JWT 를 검증하며 `tenant_id=erp` claim 만 통과시킨다 (internal-system 경계 — 외부 공개 트래픽 없음).

IAM 측 인프라 (TASK-MONO-119 V0018 시드):
- `tenants.tenant_id='erp'` (B2B_ENTERPRISE) — account-service V0018
- `oauth_clients.client_id='erp-platform-internal-services-client'` (client_credentials, scopes=`erp.read`/`erp.write`) — auth-service V0018
- `oauth_scopes` — `erp.read`, `erp.write` — auth-service V0018

상세는 [PROJECT.md § IAM IdP Integration](PROJECT.md#iam-idp-integration) + [specs/integration/iam-integration.md](specs/integration/iam-integration.md).

---

## Known Limitations

- **frontend 없음** — erp v1 = backend only. UI 는 통합 platform console 이 렌더한다 (ADR-MONO-013 §3.3) — `masters`/`orgview`/`approval`/`delegation` 화면이 이미 라이브 (`projects/platform-console/apps/console-web/src/app/(console)/erp/`). erp 자체 user-flow PKCE OIDC client 는 여전히 미발행 (console 은 GAP 자신의 콘솔 클라이언트 토큰으로 읽음).
- **standalone fork PENDING** — 외부 `kanggle/erp-platform` Template fork 는 classifier-blocked outward-facing op 으로 사용자 셸 hand-off PENDING (finance / TASK-MONO-116 동형). monorepo side(Option C) 만 landed — 이 README 는 monorepo source-of-truth 이며, standalone repo 는 별도 publish 결정 전까지 존재하지 않는다.
- **v2 deferred 서비스 미구현** — `permission-service`, `admin-service` 는 아직 부트스트랩되지 않았다 (§ Service Map 참조).

---

## References

- [PROJECT.md](PROJECT.md) — domain · traits · service map · IAM integration · trait rationale
- [tasks/INDEX.md](tasks/INDEX.md) — project task lifecycle
- [rules/domains/erp.md](../../rules/domains/erp.md) — erp 도메인 mandatory rules · bounded contexts · ubiquitous language
- [ADR-MONO-016](../../docs/adr/ADR-MONO-016-erp-platform-bootstrap.md) — erp-platform 부트스트랩 결정 (Option C)
- TASK-MONO-119 (부트스트랩 artifact) / TASK-ERP-BE-001 (masterdata-service 최초 구현) / [TASK-MONO-357](../../tasks/done/TASK-MONO-357-finance-erp-gateways.md) (gateway-service 추가 — GAP RS256 JWT + rate-limit 을 엣지로)
- [tasks/done/](tasks/done/) — 40개 완료 task 전체 이력 (masterdata → read-model → approval → notification → delegation → ADR-MONO-058 D1-D5 정렬)
- [TEMPLATE.md § Local Network Convention](../../TEMPLATE.md) — 신규 프로젝트 IAM 통합 + hostname routing 표준 절차
