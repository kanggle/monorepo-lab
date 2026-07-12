# Task ID

TASK-MONO-347

# Title

✅ RESOLVED (direction A) — 게이트웨이 정책 ↔ finance/erp 실제 구현 드리프트 해소: 정책 문서가 "모든 프로젝트에 게이트웨이"라고 선언하는데 두 도메인엔 모듈 자체가 없다

# Status

done

# Owner

monorepo

# Task Tags

- docs
- architecture

---

# ✅ RESOLUTION (2026-07-12) — direction A, `TASK-MONO-357`

**AC-0(방향 결정)은 사람이 내렸다**: 사용자가 `ADR-MONO-048` 을 **ACCEPT** 함으로써 **방향 A**(코드를 정책에 맞춘다)를 택했다. 에이전트의 자의적 선택이 아니며, 아래 DEFERRAL GUARD 가 우려하던 바로 그 실패("에이전트가 AC-0 을 건너뛰고 정책을 조용히 완화")는 발생하지 않았다.

**구현**: [`TASK-MONO-357`](TASK-MONO-357-finance-erp-gateways.md) (impl PR #2431, squash `7296d1ac6`). finance/erp 게이트웨이 신설(`libs/java-gateway` 소비, 각 자체 코드 4클래스 / 161줄), Traefik 호스트명(`finance.local` · `erp.local`) 이 게이트웨이로 이전, 백엔드는 라벨 제거 + `expose:` 전용. 3축(정책 · `PROJECT.md` · 코드)이 이제 같은 말을 한다.

## 이 task 의 원 진단이 틀렸던 곳 (기록)

§ 현재 상태는 **"런타임 외부 표면 0 — console-bff 내부 호출 전용"** 이라 적었다. **틀렸다.** 357 의 착수 전 실측에서 드러났듯, 두 프로젝트의 `docker-compose.yml` 은 이미 **Traefik 이 `finance.local` 을 `account-service` 로 직결**하고 **`erp.local` 을 `PathPrefix` 로 백엔드 4개에 분배**하고 있었다 — 즉 **리버스 프록시가 게이트웨이의 라우팅 일을 하면서 검증은 하나도 하지 않는** 상태였고, 이는 정책 L13/L14 위반이 **미발현이 아니라 이미 발현**해 있었다는 뜻이다. "위험이 아직 현실화되지 않았다" 는 이 task 의 안심 근거는 **compose 를 열어보지 않은 데서 나온 것**이었다.

**교훈**: "외부 노출 없음" 을 코드 호출 경로(BFF → 서비스)만 보고 판정했다. 로컬 네트워크 컨벤션에서 **외부 표면은 Traefik 라벨이 결정한다.** 착수 트리거 ①("외부 트래픽 노출 결정 시")은 이미 충족돼 있었고, 그걸 몰라서 이 task 가 백로그에 더 오래 앉아 있었다.

## 미충족 AC → `TASK-MONO-360` 으로 승계

**AC-3(재발 방지 가드)은 357 이 충족하지 않았다.** `MONO-345` 의 `scripts/check-service-map-drift.sh` 는 `settings.gradle` ↔ **`docs/project-overview.md`** 만 대조한다 — **`PROJECT.md` 도 `platform/api-gateway-policy.md` 도 읽지 않는다.** 그리고 Traefik 라벨(위 § 원 진단 오류가 가리키는 진짜 노출 표면)은 **어떤 가드도 보지 않는다.** ⇒ 이 task 가 지적한 드리프트는 **지금 이 순간에도 같은 방식으로 재발할 수 있다.** 조용히 떨어뜨리지 않고 [`TASK-MONO-360`](TASK-MONO-360-gateway-declaration-drift-guard.md) 으로 넘긴다.

---

# ⏳ DEFERRAL GUARD (해소됨 — 이력 보존용)

이 task 는 **의도적으로 보류된 백로그**였다. root 리프사이클에 `backlog/` 폴더가 없어 `ready/` 에 두었으나, **아래 AC-0 의 결정이 내려지기 전에는 착수하지 말 것** 이라는 조건이 붙어 있었다 (그 결정은 위 § RESOLUTION 대로 내려졌다).

이 드리프트는 **런타임 결함이 아니다** — finance/erp 는 서비스 레벨에서 게이트웨이의 검증 체인을 이미 복제해 두었고(§ 현재 상태), 관측된 보안 사고나 장애는 **0건**이다. 고쳐야 할 것은 **문서가 거짓말을 하고 있다는 사실**이며, 그 해소 방향은 두 갈래 중 하나를 **사람이 골라야** 한다(AC-0). 에이전트가 임의로 한쪽을 택해 문서를 고치면, 그게 곧 "정책을 코드에 맞춰 낮춘" 결정이 되어 버린다 — 그건 task 가 아니라 아키텍처 결정이다.

**착수 트리거** (둘 중 하나):

1. finance 또는 erp 를 **외부 트래픽에 노출**하기로 결정(현재는 console-bff 내부 호출 전용) — 그 순간 rate limiting / CORS / 에러 envelope 결손이 실제 위험이 된다.
2. 누군가 `platform/api-gateway-policy.md` 를 근거로 "finance/erp 에도 게이트웨이가 있다"고 **잘못 가정한 산출물**을 만든 사례가 관측됨(MONO-345 의 service-map 드리프트가 정확히 이 방식으로 피해를 냈다).

---

# Goal

**`platform/api-gateway-policy.md`(플랫폼 표준)와 finance/erp 의 실제 구현이 어긋나 있다.** 정책은 예외 없이 선언한다:

- L3 — "Every project that exposes HTTP traffic to external clients has a gateway service."
- L13 — "All external traffic **MUST** pass through the gateway."
- L14 — "**No** backend service may be directly exposed to external traffic."

그러나 `projects/finance-platform/apps/gateway-service/` 와 `projects/erp-platform/apps/gateway-service/` 는 **소스 트리 자체가 존재하지 않는다**(`settings.gradle` include 없음, `specs/services/` 에도 없음). 게이트웨이를 가진 도메인은 wms · scm · ecommerce · fan · iam **5개뿐**이다.

더 나쁜 것은 **계획 문서도 같이 거짓말을 한다**는 점이다. 두 프로젝트의 `PROJECT.md` 는 게이트웨이를 **"v1 OUT(v2 deferred)" 이 아니라 "v1 IN(범위 안)"** 으로 적어두었다:

- `projects/finance-platform/PROJECT.md:56` — Service Map **v1** 표에 `gateway-service` 행이 있다.
- `projects/finance-platform/PROJECT.md:100` — "**v1 IN (범위 안)**: … `gateway-service` 엣지 라우팅 (account-service 활성화와 함께)."
- `projects/erp-platform/PROJECT.md:59` / `:108` — 동일 구조.

즉 **정책 문서 · 계획 문서 · 실제 코드 3자가 서로 다른 말을 한다.** 문서만 읽는 사람(그리고 문서를 읽는 에이전트)에게 이 결함은 **원리적으로 비가시**다 — 이것이 `MONO-339`(fed-e2e 손-나열 목록) · `MONO-341`(데모 래퍼 맵) · `MONO-345`(service map)와 **동일 클래스**의 드리프트인 이유다.

---

# 현재 상태 (조사 결과 — 2026-07-11)

## 게이트웨이가 실제로 하는 일 (Spring Cloud Gateway, nginx 아님)

`projects/wms-platform/apps/gateway-service/` 기준(5개 도메인 동형):

1. **JWT/JWKS 검증** — IAM auth-service 의 JWKS 로 원격 검증(OAuth2 Resource Server).
2. **발급자·테넌트 검증** — `AllowedIssuersValidator`(화이트리스트) + `TenantClaimValidator`(`tenant_id` 또는 `entitled_domains` 매칭, ADR-MONO-019 § D5).
3. **신원 헤더 위조 차단** — `IdentityHeaderStripFilter`(클라이언트가 보낸 `X-User-*` 전량 제거) → `JwtHeaderEnrichmentFilter`(검증된 claim 으로 재설정). **이 스트립→재설정 순서 자체가 보안 경계**다.
4. **Rate limiting** — Redis `RequestRateLimiter`, 라우트별 tier, Redis 장애 시 fail-open.
5. **CORS 중앙 설정 + 통일 에러 envelope**(`GatewayErrorHandler`).

**게이트웨이는 인증(authentication)만 한다 — 인가(authorization)는 하지 않는다.** `platform/api-gateway-policy.md:86` 이 명시: *"the gateway handles authentication, not authorization."* 도메인 롤(`WMS_OPERATOR` 등) 판단은 **원래도** 각 서비스 몫이므로, 게이트웨이 유무로 인한 차이가 **아니다**.

## finance/erp 는 어떻게 버티고 있나

**게이트웨이의 검증 체인을 서비스 안에 복제해 두었다.** 두 도메인 모두 `ServiceLevelOAuth2Config` + `AllowedIssuersValidator` + `TenantClaimEnforcer` 를 갖는다. finance 의 주석이 이를 자기설명한다:

> "Mirrors the (v1-deferred) finance gateway-service validator chain inside account-service so any direct call gets the same `AllowedIssuersValidator` + `TenantClaimValidator` verdict (defense-in-depth)" — `apps/account-service/…/infrastructure/security/ServiceLevelOAuth2Config.java:18-24`

## 따라서 **실제로 결손된 것**은 (JWT 검증이 아니라)

| 항목 | 게이트웨이 도메인 | finance/erp |
|---|---|---|
| JWT/발급자/테넌트 검증 | 게이트웨이 + 서비스(이중) | **서비스 단독**(1겹) |
| 신원 헤더 위조 차단 | ✅ strip→enrich | **없음** — 서비스가 헤더를 신뢰한다면 위험(현재는 내부 호출 전용이라 미발현) |
| Rate limiting | ✅ Redis | **없음** |
| CORS 중앙 설정 | ✅ | **없음** |
| 통일 에러 envelope | ✅ | **없음** |
| 도메인 롤 인가 | 서비스 | 서비스 (**차이 없음**) |

**위험이 아직 현실화되지 않은 이유**: finance/erp 는 현재 **console-bff 내부 호출로만** 도달된다(`docker-compose.federation-e2e.yml:669-677` — `CONSOLE_BFF_OUTBOUND_{FINANCE,ERP}_BASE_URL` 이 서비스로 **직결**). 외부 클라이언트가 없으므로 rate limit / CORS 결손이 발현하지 않는다. (참고: 같은 스택에서 **wms·scm·iam 도** BFF 가 게이트웨이를 우회해 직결한다 — 내부 호출의 일반 패턴. 실제 게이트웨이를 타는 건 ecommerce 하나뿐이다.)

## 유령 `erp-gateway` 주의

fed-e2e 데모의 `erp-gateway` 컨테이너는 **정식 게이트웨이가 아니다** — `docker-compose.federation-e2e.erp-fullstack.yml`(파일 헤더에 "**UNCOMMITTED live-demo asset — do NOT commit**") 이 띄우는 `nginx:1.27-alpine` 리버스 프록시로, ERP 3개 프로듀서를 하나의 `ERP_BASE_URL` 로 묶기 위한 **데모용 땜질**이다. JWT 검증·rate limit·헤더 관리 전무. 이걸 "게이트웨이가 있다"의 근거로 삼지 말 것.

## 이 드리프트의 성격

- **의도된 부분**: `TASK-FIN-BE-001` / `TASK-ERP-BE-001` 이 게이트웨이 구현을 후속으로 미루고 그 공백을 서비스-레벨 검증기 복제로 메우기로 **명시적으로 결정**했다. `projects/{finance,erp}-platform/docker-compose.yml` 도 gateway 블록을 주석 처리하며 *"its Spring Boot module is not yet generated"* 라고 **정직하게 기록**했다.
- **드리프트인 부분**: 그 결정이 **ADR 레벨에 기록되지 않았고**(ADR-MONO-008 / ADR-MONO-016 본문에 "게이트웨이 v2 유예" 언급 없음), `platform/api-gateway-policy.md` 와 두 `PROJECT.md` 는 **갱신되지 않은 채** 여전히 "게이트웨이는 v1 범위"라고 말한다.

---

# Scope

## AC-0 — 방향 결정 (필수 선행, **사람이 결정**)

문서와 코드 중 **무엇을 진실로 삼을지** 먼저 정한다. 에이전트가 임의 선택 금지.

- **방향 A — 코드를 정책에 맞춘다**: finance/erp 에 `gateway-service` 를 실제로 구현한다. 정책·`PROJECT.md` 무수정. **비용 큼**(모듈 2개 + compose + CI + 스펙), 현재 외부 트래픽이 없어 **얻는 것이 작다**.
- **방향 B — 정책을 현실에 맞춘다**: 게이트웨이 부재를 **명시적 예외로 승격**한다. 이 경우 **ADR 이 필요하다**(정책 완화 = 아키텍처 결정). 문서 3곳을 정합화:
  1. `platform/api-gateway-policy.md` — "예외: 외부 트래픽을 노출하지 않고 내부(BFF) 호출로만 도달되는 도메인은 서비스-레벨 검증기로 갈음할 수 있다. **단 외부 노출 시점에 게이트웨이가 선행 조건**" 류의 조건부 예외 절 신설.
  2. `projects/{finance,erp}-platform/PROJECT.md` — `gateway-service` 를 **v1 IN → v1 OUT(v2 deferred)** 로 이동 + Service Map v1 표에서 제거(또는 "미구현" 명시).
  3. 신규 ADR — 유예 결정과 그 조건(내부 호출 전용), 재도입 트리거를 기록.

## In Scope (방향 확정 후)

- 위 AC-0 에서 택한 방향의 문서/코드 변경.
- **재발 방지 가드**(방향 무관, 권장): `settings.gradle` 의 gateway 모듈 목록 ↔ `api-gateway-policy.md` / `PROJECT.md` 선언을 대조하는 드리프트 검사. `MONO-345` 의 `scripts/check-service-map-drift.sh` 가 이미 gradle↔doc 양방향 대조를 하므로 **거기에 gateway 규칙을 얹는 것이 자연스럽다**(새 스크립트 신설보다 우선 검토).

## Out of Scope

- finance/erp 의 **서비스-레벨 검증기 제거** — 게이트웨이가 생기더라도 defense-in-depth 로 유지한다(게이트웨이 도메인도 서비스에서 재검증한다).
- `erp-gateway` nginx 오버레이 정식화 — 데모 자산이며 커밋 대상 아님.
- 도메인 롤 인가 로직 변경 — 게이트웨이 유무와 무관(위 표 참조).
- wms/scm/iam 이 fed-e2e 에서 게이트웨이를 우회하는 것 — 내부 BFF 호출의 정상 패턴이며 별개 논의.

---

# Acceptance Criteria

- [x] **AC-0** (필수 선행) 방향 A / B 중 하나를 **사람이** 선택하고, 그 근거를 이 task 에 기록한다. 미결 시 STOP. → **방향 A. 사용자의 `ADR-MONO-048 ACCEPTED` 가 곧 그 결정이다.**
- [x] **AC-1** 선택한 방향에 따라 `platform/api-gateway-policy.md` · `projects/{finance,erp}-platform/PROJECT.md` · 실제 코드 **3자가 일치**한다. → **정책 무수정 · `PROJECT.md` 무수정(v1 IN 이 이제 참) · 코드가 움직였다.** 3축 중 둘은 애초에 옳았고 **틀린 건 코드뿐이었다.**
- [x] **AC-2** 방향 B 인 경우, 예외를 **ADR 로 기록**한다. → **N/A** (방향 A 선택). 단 방향 A 자체가 `ADR-MONO-048` 로 기록됐다.
- [ ] **AC-3** 재발 방지 가드가 존재한다 — gateway 모듈 실재 ↔ 문서 선언 불일치 시 CI 가 RED. **음성 테스트 필수**. 필터를 `code-changed` 와 **AND 하지 말 것**(이 드리프트는 markdown-only 편집으로 도착한다). → **❌ 미충족. [`TASK-MONO-360`](TASK-MONO-360-gateway-declaration-drift-guard.md) 으로 승계** (§ RESOLUTION 참조). 357 은 드리프트의 **현재 인스턴스**를 닫았을 뿐, **재발 경로**는 그대로다.
- [x] **AC-4** 문서에 "게이트웨이는 인증만, 인가는 서비스" 구분이 유지된다(현 정책 L86). → **유지.** 357 은 `api-gateway-policy.md` 를 손대지 않았고, 새 게이트웨이 2개도 인가를 하지 않는다(도메인 롤 판단은 6개 서비스 `ServiceLevelOAuth2Config` 에 그대로).

---

# Related Specs

- `platform/api-gateway-policy.md` (**정책 원문** — L3 / L13 / L14 / L86)
- `projects/finance-platform/PROJECT.md` (L56 Service Map v1 표, L100 v1 IN)
- `projects/erp-platform/PROJECT.md` (L59, L108)
- `projects/wms-platform/apps/gateway-service/` (참조 구현 — 5개 도메인 동형)
- `projects/finance-platform/apps/account-service/.../ServiceLevelOAuth2Config.java` (현행 대체물, 자기설명 주석)
- `docs/adr/ADR-MONO-008` (finance bootstrap) · `ADR-MONO-016` (erp bootstrap) — **게이트웨이 유예 언급 없음**(드리프트의 뿌리)
- `docs/adr/ADR-MONO-019` § D5 (테넌트 클레임 dual-accept)

# Related Contracts

없음 — 문서/구조 정합 과제. 방향 A 를 택하면 게이트웨이 라우트 계약이 새로 필요하다.

---

# Target Service

`finance-platform` · `erp-platform` (+ 공유 `platform/`)

---

# Edge Cases

- **"서비스가 검증하니 괜찮다" 로 결론을 서두르지 말 것** — JWT 검증은 실제로 복제돼 있으나 **신원 헤더 strip→enrich 는 복제돼 있지 않다**. 현재는 내부 호출 전용이라 미발현이지만, 외부 노출 시 `X-User-Role` 위조가 곧바로 유효해진다. 방향 B 의 예외 절은 이 조건(내부 전용)을 **명시적 전제**로 못박아야 한다.
- 정책 완화 문구가 "게이트웨이는 선택사항"으로 읽히면 안 된다 — 조건부 예외(내부 전용)이지 일반 면제가 아니다.
- `PROJECT.md` 의 Service Map v1 표에서 행만 지우고 L100/L108 의 "v1 IN" 문장을 남기면 **드리프트가 절반만 해소**된다. 두 곳 모두 손댈 것.

---

# Failure Scenarios

- 에이전트가 AC-0 을 건너뛰고 방향 B 로 문서만 고침 → **정책 완화가 아무 결정 없이 일어난다**(가장 큰 위험). Guard: DEFERRAL GUARD + AC-0.
- 가드를 `code-changed` 와 AND → markdown-only 편집에서 가드가 꺼져 **존재 이유인 바로 그 변경을 놓친다**(MONO-345 에서 실증된 함정). Guard: AC-3.
- 게이트웨이를 "인가까지 하는 것"으로 문서에 적음 → 후속 작업이 도메인 롤 게이팅을 게이트웨이로 옮기려 시도. Guard: AC-4.
- `erp-gateway` nginx 를 근거로 "erp 는 게이트웨이 있음" 판정 → 커밋되지 않은 데모 자산이며 검증 기능 전무. Guard: § 유령 `erp-gateway` 주의.

---

# Provenance

발굴 2026-07-11 — 사용자 질문("플랫폼에서 게이트웨이가 있고 없고의 차이는 뭐야?")에 답하기 위해 5개 게이트웨이 도메인과 finance/erp 를 대조하다 발견. 정책 문서가 예외 없이 "모든 프로젝트에 게이트웨이"라고 선언하는데 두 도메인엔 모듈이 없고, 그 두 프로젝트의 `PROJECT.md` 조차 게이트웨이를 v1 범위로 적어둔 채였다. **관측된 런타임 피해는 0건**(내부 호출 전용) — 그래서 즉시 착수가 아니라 트리거 기반 백로그로 등록한다. `MONO-339`/`341`/`345` 와 같은 "사람이 손으로 유지하는 선언 ↔ 기계가 아는 진실" 드리프트 계열의 다음 항목.

분석=Opus 4.8 / 구현 권장=Opus (정책·ADR 판단 필요 — 기계적 문서 수정이 아님).
