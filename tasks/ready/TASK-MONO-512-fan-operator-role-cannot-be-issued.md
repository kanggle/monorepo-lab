# Task ID

TASK-MONO-512

# Title

`FAN_OPERATOR` 는 IdP 가 발급할 수 없는 역할이다 — 팬 도메인의 운영자 평면이 세 서비스에 배선돼 있는데 그 역할에 도달하는 테넌트가 하나도 없다

# Status

ready

# Owner

monorepo

# Task Tags

- iam
- security
- demo

---

# 배경

`TASK-MONO-509`(팬 시드) AC-8 이 발굴했다.

팬 도메인의 **쓰기 표면은 전부 운영자 역할을 기다리고 있다**:

| 어디 | 무엇 |
|---|---|
| `iam` `OperatorRoleDerivation` | `case "fan", "fan-platform" -> List.of("FAN_OPERATOR")` |
| `artist-service` `SecurityConfig` | `ADMIN_ROLES = {ADMIN, OPERATOR, SUPER_ADMIN, FAN_OPERATOR}` — 아티스트·그룹·팬덤의 POST/PATCH/DELETE 전부 |
| `community-service` `ActorContext.isOperator()` | `FAN_OPERATOR` 를 명시적으로 받는다(`TASK-MONO-417` 이 추가) |

세 곳 모두 `FAN_OPERATOR` 를 **받는다**. 그런데 그것을 **주는** 경로가 없다.

## 실측 (2026-08-05, 로컬 `iam fan console` 슬라이스)

`OperatorRoleDerivation` 은 **선택된 테넌트의 ACTIVE 구독**을 키로 삼는다. 그 arm 에
도달하려면 어떤 테넌트가 `fan`(또는 `fan-platform`) 을 구독해야 하는데:

```
demo-corp 구독 = [ecommerce, wms, scm, erp, finance]     ← fan 없음
fan-platform  = tenant_domain_subscription 행 자체가 없음
```

그래서 두 갈래가 모두 막힌다:

```
# (1) demo-corp 를 assume — 역할에 FAN_OPERATOR 가 없고, 애초에 게이트웨이가 막는다
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_*, INBOUND_*, INVENTORY_*, MASTER_READ]
GET  /api/v1/artists   403 {"code":"TENANT_FORBIDDEN",
                            "message":"tenant_id 'demo-corp' is not allowed"}
POST /api/v1/artists   403 (동일)

# (2) fan-platform 을 assume — IAM 이 거절한다
{"error":"invalid_grant",
 "error_description":"operator is not assigned to the selected tenant"}
```

즉 **두 개의 독립된 결여**가 겹쳐 있다:

1. `R__seed_demo_operator.sql` 에 `operator_tenant_assignment → fan-platform` 행이 없다
2. 있더라도 `tenant_domain_subscription (fan-platform, 'fan')` 행이 없어 역할이 파생되지 않는다

# Goal

팬 도메인의 운영자 표면이 **실제 호출자에게 도달 가능**해진다 — 즉 콘솔 로그인 →
assume → `POST /api/v1/artists` 가 201 을 내는 경로가 존재한다. 그리고 그 경로가
없어야 한다면, **그 사실이 코드에 적혀 있어야 한다**(받는 쪽 세 곳이 존재하지 않는
역할을 기다리는 상태로 남지 않는다).

---

# Scope

## In Scope

- 어느 테넌트가 `fan` 도메인을 구독하는가에 대한 결정 + 그 시드
- `demo-operator` 의 `fan-platform` assume 배정(필요하다면)
- 반대 결정(팬은 운영자 평면을 갖지 않는다)일 경우 세 곳의 `FAN_OPERATOR` 처리 정리

## Out of Scope

- `ARTIST_POST` 저자 모델 — `TASK-FAN-BE-045`. **(가) 를 고쳐도 (나) 는 남는다**
- 콘솔에 팬 운영 섹션 추가 — `ProductCatalog` 에 `fan` 엔트리가 없다(확인함).
  이 티켓은 **API 도달 가능성**만 다룬다

---

# 🔴 결정이 필요한 지점 — 그래서 ADR 이 필요할 수 있다

`TASK-BE-576` 이 확립한 구분이 여기서 그대로 재현된다:

> `demo-corp` = **권한**(구독에서 파생되는 역할) / 도메인 테넌트 = **가시성**(행이 사는 곳)

팬의 행은 전부 `tenant_id = 'fan-platform'` 이고 게이트웨이가
`required-tenant-id: fan-platform` 으로 **다른 테넌트의 토큰을 아예 거절한다**
(ecommerce 는 거절하지 않고 통과시킨 뒤 행 필터에서 비웠다 — 팬은 엣지에서 잘린다).
따라서 `demo-corp` 에 `fan` 구독을 얹는 것만으로는 **아무것도 열리지 않는다**.
운영자가 `fan-platform` 에 서야 한다.

그것이 함의하는 것: `fan-platform` 은 B2C_CONSUMER 테넌트인데 **운영자가 assume 하는
테넌트**가 된다. 이 저장소가 그런 조합을 허용하는지가 이 티켓의 실제 질문이다.

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 위 실측 두 갈래를 **다시 받아 본다**. 응답 문구가 바뀌었으면
      실제가 이긴다. 그리고 `FAN_OPERATOR` 를 **받는** 곳을 전수로 다시 센다(현재 3곳)
- [ ] **AC-1 (결정)** — 팬이 운영자 평면을 갖는지 결정하고 근거를 남긴다.
      B2C 테넌트를 운영자가 assume 하는 것이 새 패턴이면 **ADR** 로 올린다
- [ ] **AC-2 (도달 가능성)** — "연다" 로 결정했다면 콘솔 로그인 → assume →
      `POST /api/v1/artists` 201 을 **실측**한다. 토큰 스모크가 아니라 실제 생성이다
- [ ] **AC-3 (음성 대조)** — 같은 요청이 assume 없이는 여전히 403 이어야 한다.
      양성만으로는 "열렸다" 와 "게이트가 사라졌다" 를 구별할 수 없다
- [ ] **AC-4 (반대 결정의 비용)** — "닫는다" 로 결정했다면 세 곳의 `FAN_OPERATOR`
      분기를 **지우거나** 왜 남기는지 코드에 적는다. 지금처럼 말없이 남기지 않는다
- [ ] **AC-5 (시드 회수)** — `infra/demo/seed/seed-fan.sh` 의 첫 `dbexec --why` 는
      이 결함을 사유로 든다. 열렸다면 그 블록의 아티스트·그룹·팬덤을 **API 로 옮긴다**

---

# Related Specs

- `projects/iam-platform/apps/auth-service/.../OperatorRoleDerivation.java`
- `projects/iam-platform/apps/admin-service/src/main/resources/db/migration-dev/R__seed_demo_operator.sql`
  (특히 `TASK-BE-576` 이 남긴 "outside writer 가 있으면 행 하나 더" 주석)
- `projects/fan-platform/apps/artist-service/.../config/SecurityConfig.java`
- `projects/fan-platform/apps/community-service/.../application/ActorContext.java`
- `infra/demo/seed/seed-fan.sh` (사유 원문)

# Edge Cases

- `OperatorRoleDerivation` 은 `fan` 과 `fan-platform` **둘 다** 받는다 — 어느 키를
  구독 행에 쓰는지에 따라 다른 곳(콘솔 카탈로그 파생)이 반응할 수 있다
- `ProductCatalog` 에 `fan` 이 없으므로 구독 행을 넣어도 콘솔 제품은 늘지 않는다(확인함).
  이것이 "안전하다" 의 근거이자, 동시에 "콘솔에서는 여전히 보이지 않는다" 의 이유다

# Failure Scenarios

- **구독만 넣고 배정을 빠뜨린다** — assume 이 `invalid_grant` 로 막힌다(위 실측 (2))
- **배정만 넣고 구독을 빠뜨린다** — assume 은 되는데 `roles` 가 비어 게이트웨이가 403 한다
- **`demo-corp` 에 `fan` 을 얹고 끝낸다** — 게이트웨이가 `TENANT_FORBIDDEN` 으로 자른다.
  ecommerce 의 "200 + 빈 배열" 과 달리 **엣지에서 잘리므로 증상이 다르다**

# Definition of Done

- [ ] AC-0 재측정 기록
- [ ] 결정 + (필요시) ADR
- [ ] 실측 증거(양성 + 음성)
- [ ] `seed-fan.sh` 회수 여부 명시
- [ ] Ready for review
