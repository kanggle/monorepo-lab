# Task ID

TASK-MONO-371

# Title

플랫폼 JWT 계약이 **테넌트 격리 축을 안 적고 있다** — IdP 가 심는 4개 클레임(`tenant_id` · `tenant_type` · `entitled_domains` · `org_scope`)이 "MUST include" 표에 0회 등장. 등재 + 재발 가드

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- guard
- ci
- security

---

# Dependency Markers

- **발굴 출처**: 2026-07-12 미티켓 3차원 스윕(선언↔진실 축). `MONO-363`/`369` 종결 후 정식 큐 소진 상태에서 수행.
- **같은 계열 (가드 형태의 참조 구현)**: `MONO-345`(service map) · `MONO-352`(error registry) · `MONO-360`(gateway) · `MONO-363`(ADR index). **`scripts/check-error-code-registry.sh` 가 가장 가까운 형태** — 코드가 방출하는 것 ↔ 문서가 등재한 것, 정방향.

---

# Goal

`platform/contracts/jwt-standard-claims.md` 는 **스스로 권위를 선언한다**:

> `platform/contracts/jwt-standard-claims.md:50` — *"All access tokens issued by the identity-platform service **MUST include** the following claims:"*

그리고 9개를 나열한다(`sub` `aud` `roles` `email` `iss` `iat` `exp` `jti` `kid`).

## 착수 전 실측 (2026-07-12, `875e583cd`) — 코드가 권위

IdP(`projects/iam-platform/apps/auth-service`)의 토큰 커스터마이저가 실제로 심는 클레임을 `.claim(` 전수 grep(비-테스트):

| 클레임 | 계약서 내 언급 횟수 | 실제 역할 |
|---|---|---|
| `roles` | 39 | ✅ 등재됨 |
| `sub` | 13 | ✅ |
| `email` | 13 | ✅ |
| **`tenant_id`** | **0** | **멀티테넌시 축 — 모든 게이트웨이가 없으면 토큰을 거부한다** |
| **`tenant_type`** | **0** | 테넌트 종류 분기 |
| **`entitled_domains`** | **0** | `ADR-MONO-023` 엔타이틀먼트 평면 |
| **`org_scope`** | **0** | `ADR-MONO-047` org-node 계층 (2026-07-11 완주) |

**⇒ 자기를 "모든 액세스 토큰이 MUST include 하는 것" 의 권위라 선언한 문서가, 정작 이 플랫폼의 격리 축 4개를 한 글자도 안 적었다.**

## 왜 이게 진짜 비용인가

`tenant_id` 는 장식이 아니다 — **`libs/java-gateway` 의 `TenantClaimValidator` 와 wms/erp/finance/scm/iam/ecommerce 의 사본들이 이 클레임이 없는 토큰을 거부한다.**

**이 계약서만 읽고 리소스 서버를 구현하는 사람(또는 에이전트)은 테넌트 격리가 없는 서비스를 만든다.** 그리고 계약서는 자기가 완전하다고 말하고 있으므로, 그가 뭘 놓쳤는지 알 방법이 없다. 문서라서 **아무것도 실패하지 않는다.**

이 저장소가 **일곱 번째로** 만나는 같은 결함이다 — 손-유지 선언이 기계가 아는 진실에서 조용히 갈라진다(`MONO-339` `341` `345` `352` `360` `363`/`369`).

---

# Scope

## In Scope

1. **등재** — `platform/contracts/jwt-standard-claims.md § Standard Claims` 에 4개 클레임 행 추가. 각 행의 타입·필수여부·의미·예시는 **코드에서 읽는다**(`TenantClaimTokenCustomizer` 등 실제 mint 지점 + 소비 측 validator). 추측 금지.
2. **가드** — `scripts/check-jwt-claims-registry.sh` + CI 잡. **정방향 전용**:
   - IdP 의 비-테스트 소스에서 `.claim("X")` / `.claim(CONST)` 로 mint 되는 모든 클레임 X 가 계약서 표에 행을 갖는가.
3. **CI 배선** — `dorny/paths-filter` **pure-positive**(negation 금지 — MONO-074/075 quirk). **`code-changed` 와 AND 할 것**: 이 드리프트는 **새 `.claim(...)` 을 추가하는 자바 변경으로 도착한다**(문서-only 로는 도착하지 않는다). 필터 경로 = IdP 토큰 커스터마이저 소스 + 계약서 + 스크립트.
   - **주의**: `MONO-363` 의 ADR 가드는 정반대로 **AND 금지**였다(그건 markdown-only 로 도착하니까). **트리거는 결함이 도착하는 경로를 따라간다 — 습관이 아니라.**

## Out of Scope

- **역방향(계약서 → 코드) 검사** — `iss`/`iat`/`exp`/`jti`/`aud`/`kid` 는 **Spring Authorization Server 가 프레임워크 기본으로 방출**하며 `.claim(...)` 호출이 없다. 역방향을 만들면 **첫날부터 6행이 RED** — `MONO-360` 이 못박은 실패 모드(*첫날 RED 인 가드는 꺼지고, 꺼진 가드는 없는 가드보다 나쁘다 — skip 이 초록으로 보고되니까*).
- **클레임을 추가/제거하는 코드 변경** — 이 task 는 **문서를 코드에 맞춘다**. 반대가 아니다. 코드가 권위.
- **`account_id`** — 계약서에 **이미 취소선으로 REMOVED** 등재됨(`ADR-MONO-040`). 되살리지 말 것.
- **`platform/abac-data-scope.md`** 등 다른 문서와의 정합 — 별건. 이 task 는 **JWT 계약서 ↔ IdP mint 지점** 한 축만 본다.

---

# Acceptance Criteria

- [ ] **AC-1 — 등재**: 4개 클레임이 § Standard Claims 표에 행을 갖는다. **값은 코드에서 읽는다** — 특히 `tenant_id` 의 **필수 여부**는 소비 측(`TenantClaimValidator`)이 결정한다: **없으면 거부하는가?** 그 답이 `Yes`/`Recommended` 를 정한다. **추측 금지.**
- [ ] **AC-2 — 가드**: `scripts/check-jwt-claims-registry.sh` 가 IdP 가 mint 하는 모든 클레임이 표에 있는지 **정방향**으로 검사한다. 현 트리 GREEN(**비-vacuous** — mint 지점 0개나 표 행 0개를 파싱하면 통과가 아니라 exit 2).
- [ ] **AC-3 — mutation, 전부 물어야 함** (**주입이 적용됐는지 먼저 확인** — 이 저장소가 세 번 당했다):
  1. 계약서에서 `tenant_id` 행 삭제 → **RED**(그 클레임을 지목)
  2. IdP 에 새 `.claim("some_new_claim", …)` 주입 → **RED**(등재 강제)
  3. **상수 참조 형태**(`.claim(CLAIM_ORG_SCOPE, …)`)도 잡는가 → 상수 행 삭제 시 **RED**. **리터럴만 파싱하면 4개 중 2개를 놓친다**(`entitled_domains`·`org_scope` 는 상수로 mint 된다). 이게 이 가드의 핵심 파싱 난점이다.
  4. **vacuity**: baseline exit=0.
- [ ] **AC-4 — 오탐 0**: 현 트리에서 GREEN. 프레임워크 기본 클레임(`iss` 등)을 요구하지 않는다(§ Out of Scope).
- [ ] **AC-5 — 도달 가능성**: 필터가 IdP 소스 + 계약서 + 스크립트를 포함하고 **`code-changed` 와 AND 된다**(이 드리프트는 자바 변경으로 도착). **`MONO-363` 처럼 비-AND 로 하지 말 것 — 트리거는 결함의 도착 경로를 따른다.**
- [ ] **AC-6** — 코드 변경 0(`.claim(...)` 추가/삭제 없음) → `./gradlew check` 무영향.

---

# Related Specs

- `platform/contracts/jwt-standard-claims.md` — **고칠 대상이자, 스스로 권위를 선언한 문서**
- `projects/iam-platform/apps/auth-service/.../oauth2/TenantClaimTokenCustomizer.java` — mint 지점(권위)
- `libs/java-gateway/.../TenantClaimValidator.java` — `tenant_id` 필수 여부를 결정하는 소비 측
- `scripts/check-error-code-registry.sh` (MONO-352) — **가드의 참조 구현**(코드가 방출 ↔ 문서가 등재, 정방향)
- `docs/adr/ADR-MONO-023` (entitled_domains) · `ADR-MONO-047` (org_scope) · `ADR-MONO-040` (account_id 제거)

# Related Contracts

- `platform/contracts/jwt-standard-claims.md` — 이 task 가 고치는 계약 자체.

---

# Edge Cases

- **상수로 mint 되는 클레임** — `CLAIM_ENTITLED_DOMAINS` · `CLAIM_ORG_SCOPE` · `CLAIM_ROLES` 는 **상수 참조**다. 파서가 문자열 리터럴만 보면 **조용히 놓치고 가드는 vacuous 해진다.** AC-3-3.
- **`tenant_id` 를 여러 곳에서 mint** — 4개 지점(`selectedTenantId` · `tenantId` · `tenantInfo.tenantId()` · `tidStr`). 중복 제거 필요.
- **테스트 소스 제외** — 테스트가 심는 클레임은 계약이 아니다. 포함하면 첫날 RED.
- **`sub` 는 `.claim()` 이 아니라 principal 에서 온다** — 정방향 파서가 못 보지만 **이미 등재돼 있으므로 무해**. 역방향을 안 만드는 이유 중 하나.

# Failure Scenarios

- **F1 — 역방향까지 만든다** → 프레임워크 기본 클레임 6개에서 **첫날 RED** → 가드가 꺼진다. Guard: § Out of Scope + AC-4.
- **F2 — 리터럴만 파싱** → `entitled_domains`·`org_scope` 를 놓치고, **가드가 있다고 믿으면서 절반만 지킨다**. Guard: AC-3-3.
- **F3 — 문서에 맞춰 코드를 고친다**(클레임 제거) → **라이브 인증이 죽는다.** 코드가 권위. Guard: § Out of Scope.
- **F4 — `code-changed` 와 AND 하지 않는다** → 무해하지만 불필요한 실행. **반대로, MONO-363 을 흉내내 비-AND 로 하고 "일관성" 이라 부르지 말 것** — 두 가드의 결함은 **다른 경로로 도착한다**.

---

# Test Requirements

- `bash scripts/check-jwt-claims-registry.sh` — 현 트리 GREEN(비-vacuous), mutation 3방향 RED.
- CI 잡이 이 변경에서 실행되어 GREEN.

---

# Definition of Done

- [ ] AC-1 ~ AC-6.
- [ ] `tasks/INDEX.md` done entry.

---

# Provenance

발굴 2026-07-12 — 정식 큐 소진 후 미티켓 3차원 스윕(ADR 후속 / code-marker / **선언↔진실**). 선언↔진실 축의 최고 수확.

**이 결함이 왜 오래 살아남았나**: 토큰은 **실제로** `tenant_id` 를 싣고 있고 게이트웨이는 **실제로** 강제하고 있다 — **런타임은 멀쩡하다.** 틀린 것은 *문서뿐*이고, 문서는 실패하지 않는다. 비용은 **다음 서비스를 만드는 사람**이 낸다.

분석=Opus 4.8 / 구현 권장=**Opus**(필수여부 판정이 소비 측 코드 독해에 달렸고, 상수-참조 파싱을 놓치면 가드가 조용히 절반만 지킨다).
