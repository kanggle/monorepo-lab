# 운영자 인증/인가 토큰 모델 (Operator Auth Token Model)

> **이 문서는 human-reference 개념 가이드입니다 — source of truth 가 아닙니다.**
> 정확한 규약(claim shape, 상태코드, 검증 순서)은 각 스펙이 권위입니다. 이 가이드는
> "어느 토큰을 왜 쓰나" 의 **멘탈 모델**만 제공하고, 세부는 § 9 의 권위 스펙으로 링크합니다.
> 구현 판단은 항상 스펙을 확인하세요.

콘솔·도메인 서비스를 다루다 보면 반복적으로 부딪히는 질문: **"이 호출엔 어느 토큰을 써야 하지?"**
헷갈리는 이유는 토큰이 하나가 아니라, **하나의 로그인에서 목적별로 갈라져 나온 여러 개**이기 때문입니다.

---

## 1. 한눈에

```
                       ┌─────────────────────────────────────────┐
   회원가입/로그인  →  │  1축 · 로그인 토큰 (base IAM OIDC)        │   "너는 누구냐" = 인증(authn)
                       │  정체성만 담음 (권한·테넌트 운영정보 없음) │
                       └───────────────┬─────────────────────────┘
                                       │  (뿌리 — 여기서 목적별로 교환)
                    ┌──────────────────┴───────────────────┐
        (교환/ADR-014)                             (교환/ADR-020, RFC 8693)
                    ▼                                       ▼
   ┌──────────────────────────┐          ┌──────────────────────────────────┐
   │  operator token          │          │  2축 · assume-tenant 토큰          │  "이 테넌트를
   │  IAM 관리 백엔드 전용      │          │  선택 테넌트 + entitled_domains +  │   이 범위까지
   │  (/api/admin/**)          │          │  도메인 roles + org_scope          │   운영 가능"
   └──────────────────────────┘          └──────────────────────────────────┘
        IAM 관리 인가(authz)                       도메인 운영 인가(authz)
```

- **1축 = 인증**(authentication): 로그인으로 "누구인지"를 증명.
- **2축 = 인가**(authorization): 선택한 고객 테넌트를 어느 범위로 운영할지.
- **operator token**: 1축에서 갈라진 **또 다른 인가** — 대상이 테넌트 운영이 아니라 **IAM 관리 백엔드**.

핵심: **1축이 뿌리, operator token 과 2축(assume-tenant)은 그로부터 교환돼 나온 두 갈래**입니다.

---

## 2. 토큰 3종

| 토큰 | 성격 | 담긴 것 | 대상 | 수명 |
|---|---|---|---|---|
| **로그인 토큰** (base IAM OIDC access token) | 인증 | 정체성(`sub`=account_id) | 모든 교환의 입력(subject_token) | IAM 세션 수명 |
| **operator token** | 인가(IAM 관리) | IAM 관리 권한 | IAM `/api/admin/**` (accounts/audit/operators/dashboards/구독/파트너십) | 단명 |
| **assume-tenant 토큰** (2축) | 인가(도메인 운영) | 선택 `tenant_id` + `entitled_domains` + 도메인 `roles` + `org_scope` | 도메인 게이트웨이(wms/scm/finance/erp/ecommerce) | **단명, 선택마다 재발급**(refresh 없음) |

> 로그인 토큰은 `tenant_id` 가 IdP 플랫폼(예: `gap`)이고 도메인 권한이 **없습니다**. 그래서 그
> 자체로는 IAM 관리 백엔드도, 도메인 게이트웨이도 받아주지 않습니다 — 목적용 토큰으로 **교환**해야 합니다.

---

## 3. 교환(token exchange)이란

**교환 = 로그인 증명을 근거로, 목적에 맞는 권한 토큰을 새로 발급받는 것.** 은행 환전·여권→출입증과 같습니다.

```
여권(로그인 토큰) 만으론 특정 시설 입장 불가
   → 여권 제시 → 신원·권한 확인 → 출입증(operator / assume-tenant 토큰) 발급
```

- **왜 바로 안 쓰나**: 로그인 토큰엔 "누구"만 있고 권한·테넌트·도메인 정보가 없습니다. 백엔드는
  자기가 요구하는 정보가 담긴 토큰만 받습니다.
- **교환 시 서버가 검증**: operator 존재/ACTIVE 여부, 선택 테넌트에 대한 assignment(D1/D2),
  도메인 entitlement(D3) 등을 확인하고 발급 → **그래서 안전**.
- **fail-CLOSED**: assume-tenant 게이트는 admin-service 장애/미할당/timeout 이면 **토큰을 발급하지
  않습니다**(가용성에 기대 인가하지 않음 — 인가 게이트 원칙). account-service 의 entitled_domains
  fail-soft 와는 **정반대** 정책이며 절대 섞지 않습니다.

---

## 4. 어느 토큰을 언제 (결정표)

콘솔이 백엔드를 호출할 때 **대상 백엔드가 무엇을 요구하느냐**로 자격이 갈립니다(도메인별 계약).

| 호출 대상 | 자격 | 얻는 법 |
|---|---|---|
| **IAM 관리 백엔드** `/api/admin/**`(accounts·audit·operators·dashboards·구독·파트너십) | **operator token** | `getOperatorToken()` (서버측 교환) |
| **도메인 게이트웨이**(wms·scm·finance·erp·ecommerce) | **IAM OIDC 토큰**(로그인 토큰 직접, 또는 테넌트로 scope 한 assume-tenant 토큰) | `getAccessToken()` / `getDomainFacingToken()` |

**결정적 이유 = 백엔드마다 인증 모델이 다르다**:
- **IAM 백엔드**는 원본 OIDC 토큰을 **거부** → 교환된 operator token 만 받음(**#569 trust-boundary 불변식**:
  IAM OIDC 토큰은 IAM `/api/admin/**` 에 **결코** 도달하지 않는다).
- **도메인 게이트웨이**는 정반대로 IAM OIDC RS256 JWT 를 **요구**(교환 없음, `tenant_id` claim producer-side 강제).

> 충돌이 아니라 **per-domain 바인딩**입니다. 한쪽 규칙을 다른 쪽에 일괄 적용하면 보안 결함입니다.
> **console-side 자격 선택 규칙의 권위**는 platform-console 소유입니다 →
> [console-web architecture.md § Per-domain credential selection](../../../platform-console/specs/services/console-web/architecture.md).
> 이 가이드는 개념만 제공하고 규칙은 복제하지 않습니다.

---

## 5. 인증 vs 인가 한 줄 정리

| 축 | 무엇 | 질문 |
|---|---|---|
| **1축 · 로그인 토큰** | **인증**(authn) | "너는 누구냐" |
| **2축 · assume-tenant 토큰** | **인가**(authz) | "이 **테넌트**를 이 범위까지 운영 가능?" |
| **operator token** | **인가**(authz) | "이 **IAM 관리** 백엔드를 쓸 수 있나?" |

→ 1축(인증)이 "누구인지"를 세우면, 그걸 근거로 2축·operator(인가)가 "무엇을 할 수 있는지"를 정합니다.
operator token 도 성격상 인가이며, 다만 대상이 도메인 운영이 아니라 IAM 관리라 2축과 다른 갈래일 뿐입니다.

---

## 6. 하나의 계정, 4개의 모자

토큰이 목적별로 갈라지는 이유(§ 1~5)는 결국 **한 사람이 한 로그인으로 여러 관계를 오가기** 때문입니다. 통합 IAM 계정(`account_id`)은 하나지만, 그 계정이 **어떤 관계에 처하느냐**에 따라 얹히는 인가(모자)가 달라집니다 — 인증(로그인)은 항상 하나, 모자는 상황별로 바뀌는 **인가**입니다.

| 모자 | 관계 | 정체성 / 역할 | 저장 | 토큰 |
|---|---|---|---|---|
| **① 소비자** | 순수 회원(운영자 아님) | 도메인 롤·운영자 없음 | `account`(IAM)만, `admin_operators` row 없음 | **1축 로그인 토큰**만 |
| **② 내가 운영하는 회사** | 내가 owner인 테넌트 | `TENANT_ADMIN`·`TENANT_BILLING_ADMIN`(`subscription.manage`) | `admin_operators`(홈) + 테넌트 role grant | 1축 + **operator token** |
| **③ 내가 다니는 회사** | 그 테넌트의 배정 직원-운영자 | 도메인 롤(assume 시 파생) | `admin_operators` + `operator_tenant_assignment` | 1축 + operator token + **2축 assume-tenant** |
| **④ 내 회사가 운영하는 다른 회사** | cross-org 파트너십 참여자 | 위임 slice 내 도메인 롤 | partnership `delegated_scope` ∩ `participant_scope` | **2축 assume-tenant**(도메인·역할 cap, **admin 없음**) |

- **① 소비자** — B2C(예: web-store 쇼핑). 콘솔 진입 불가, 교환 없음. 로그인 토큰(1축)만.
- **② 내가 운영하는 회사** — 내가 소유자/관리자인 테넌트. 조직을 **세팅**한다: 도메인 구독 켜기·운영자 생성·협력사에 slice 위임. IAM 관리(`/api/admin/**`)는 operator token 으로.
- **③ 내가 다니는 회사** — 내가 직원-운영자로 **배정**된 테넌트. `operator_tenant_assignment` 로 assume 대상이 열리고, assume-tenant(2축) 진입 시 그 테넌트의 구독 도메인에서 도메인 롤(WMS_OPERATOR·ADMIN…)이 파생된다.
- **④ 내 회사가 운영하는 다른 회사** — 우리 회사(partner 테넌트 B)가 host 테넌트 A 에게서 받은 위임 slice 안에서, 내가 그 참여자(participant). A 를 assume 하되 2축 토큰이 `delegated_scope` 로 cap 되고 admin 권한은 조직 경계를 넘지 못한다(§ 7 상세).

**② ↔ ③ 구분** — 같은 "내 회사"라도 **owner(②, 조직을 세팅)** 냐 **assigned operator(③, 배정 범위를 운영)** 냐로 역할 티어가 다릅니다. ②는 구독·운영자·위임을 만들고, ③은 배정받은 도메인 범위에서 운영합니다.

**③ ↔ ④ 구분** — 둘 다 "테넌트를 assume 해 도메인 운영"이지만, ③은 **내 회사 테넌트**(intra-org, 자연 배정)이고 ④는 **남의 회사 테넌트**(cross-org, 파트너십 위임 slice · scope cap · admin 불가)입니다. 콘솔에서 ③은 운영자 관리의 **테넌트 배정**, ④는 **파트너십** 화면으로 관리됩니다.

> 공통: ① 로그인(인증)은 넷 다 **동일한 계정 하나**. 나머지 세 모자는 그 위에 얹히는 **인가**입니다(operator token = IAM 관리, 2축 assume-tenant = 도메인 운영). "어느 토큰"은 § 4, "인증 vs 인가"는 § 5 를 참고하세요.

### ② 만드는 법 — self-service 온보딩

"회원가입하고 내가 소유한 회사를 운영하려면?" = 모자 ②를 얻는 길입니다. 플랫폼 관리자(SUPER_ADMIN) 개입 없이 스스로 만듭니다 — AWS "회원가입 → 새 계정 → root" / GCP "새 프로젝트 → owner" parity([ADR-MONO-044](../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md)).

1. **회원가입** (`/signup`) → 통합 IAM 계정 = 모자 ①(소비자). 운영자 아님, 1축 로그인 토큰만. (온보딩 진입은 email 인증된 계정을 요구 — 트러스트 게이트, D4.)
2. **콘솔 로그인** (OIDC) → 아직 운영자 아님. 소속 워크스페이스가 없으면 콜백이 재로그인이 아니라 **`/onboarding`** 으로 보냅니다.
3. **조직 생성** (`/onboarding` → `POST /api/admin/onboarding/organizations`, 내 OIDC 토큰이 subject). **한 번의 atomic 트랜잭션**(D1; 실패 시 전체 롤백 → 관리자 없는 orphan 테넌트 방지 D3)이:
   - 새 테넌트(ACTIVE) 생성
   - 내 계정에 **운영자 facet** 추가(born-unified, [ADR-MONO-036](../../../../docs/adr/ADR-MONO-036-born-unified-identity-provisioning.md) `resolveOrCreate` — 기존 소비자 계정 그대로, 별도 계정 아님)
   - **`TENANT_ADMIN` + `TENANT_BILLING_ADMIN`** grant — **방금 만든 테넌트 scope 로만**(D2 confinement: 남의 테넌트·`'*'` 도달 불가)
   - `operator_tenant_assignment`(그 테넌트를 assume 가능)
   → 이 순간 모자 ①이 **②로 승격**됩니다.
4. **콘솔 운영**: operator token 으로 IAM 관리 화면 접근 —
   - `/subscriptions`(`TENANT_BILLING_ADMIN`): 새 테넌트는 **도메인 구독 0 으로 태어나므로**(D6, entitlement-empty), wms·ecommerce·erp… 를 **직접 켜야** 운영 화면이 열립니다.
   - `/operators`(`TENANT_ADMIN`): 우리 회사 직원 운영자를 생성·배정 = **모자 ③** 만들어주기(§ 6 표).
   - 도메인 켠 뒤 **assume-tenant(2축)**로 그 도메인 운영 화면 진입.

핵심: self-grant 는 **자기가 방금 만든 테넌트에만** 갇혀 안전합니다(D2 — SUPER_ADMIN net-zero·타 테넌트 격리 불변). 상세 규약은 [ADR-MONO-044](../../../../docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md)·[onboarding-api.md](../../specs/contracts/http/onboarding-api.md) 가 권위.

---

## 7. cross-org 확장 — 모자 ④ 상세 (ADR-MONO-045)

한 사람이 **협력사/공급사로서 다른 회사 테넌트를 bounded 하게 운영**하는 경우 —
host 테넌트 A 가 partner 테넌트 B 에게 위임한 `delegated_scope {domains, roles}` slice 안에서만.

- B 의 참여 operator 가 A 를 assume 할 때, **2축(assume-tenant) 토큰이 delegated slice 로 cap** 됩니다:
  `entitled_domains = host-ACTIVE ∩ delegated.domains`, `roles = delegated.roles` (verbatim).
- **admin scope 는 절대 확장되지 않습니다** — cross-org actor 는 host 에 admin 권한이 없어
  `/api/admin/**` 에서 403. 파트너십은 **도메인 운영 reach 만 넓히고 admin 권한은 조직 경계를 넘지 않습니다.**
- ≤-own 강제는 **invite 시점**(협력사에 위임할 때 host 보유 도메인·역할만 허용), request-time cap 은
  발급 시 위 교집합으로 이뤄집니다.

상세: [ADR-MONO-045](../../../../docs/adr/ADR-MONO-045-cross-org-partner-delegation.md),
[admin-service rbac.md § Cross-Org Partner Delegation Confinement](../../specs/services/admin-service/rbac.md),
[auth-to-admin.md § delegatedScope](../../specs/contracts/http/internal/auth-to-admin.md).

---

## 8. 자주 헷갈리는 지점

- **"operator token 이 2축인가?"** — 아니오. 2축은 assume-tenant 토큰(도메인 운영)입니다.
  operator token 은 1축에서 갈라진 **IAM 관리용 옆가지**입니다(§ 1 다이어그램).
- **"도메인엔 로그인 토큰? assume-tenant 토큰?"** — 둘 다 IAM OIDC 계열. 단순 조회 도메인은 로그인
  토큰 직접, 운영/쓰기·테넌트 scope 가 필요한 경우 assume-tenant 토큰. 정확한 매핑은 console-web 스펙(§ 4 링크).
- **"assume-tenant 토큰에 refresh 는?"** — 없음. 단명이라 테넌트 선택마다 재발급합니다(ADR-020 § 3.1).
- **"로그인 토큰을 IAM `/api/admin/**` 에 바로 쓰면?"** — 거부(#569). 반드시 operator token 으로 교환.

---

## 9. 권위 스펙 (source of truth)

이 가이드는 개념입니다. 정확한 규약은 아래가 권위입니다:

- **operator token 교환** — [admin-service security.md § IAM OIDC Subject-Token Validation](../../specs/services/admin-service/security.md), [ADR-MONO-014](../../../../docs/adr/ADR-MONO-014-platform-console-operator-auth-token-exchange.md).
- **assume-tenant 토큰(2축) 발급** — [auth-service architecture.md § Assume-Tenant Exchange](../../specs/services/auth-service/architecture.md), [ADR-MONO-020](../../../../docs/adr/ADR-MONO-020-operator-multitenant-assignment.md).
- **assignment 게이트 / delegatedScope** — [auth-to-admin.md](../../specs/contracts/http/internal/auth-to-admin.md).
- **console 자격 선택 규칙** — [console-web architecture.md § Per-domain credential selection](../../../platform-console/specs/services/console-web/architecture.md), console-integration-contract.md § 2.4/§ 2.6.
- **cross-org 위임 cap** — [ADR-MONO-045](../../../../docs/adr/ADR-MONO-045-cross-org-partner-delegation.md), [rbac.md § Cross-Org Partner Delegation Confinement](../../specs/services/admin-service/rbac.md).
- **평면 분리(entitlement/IAM)** — [ADR-MONO-023](../../../../docs/adr/ADR-MONO-023-entitlement-iam-plane-separation.md).
