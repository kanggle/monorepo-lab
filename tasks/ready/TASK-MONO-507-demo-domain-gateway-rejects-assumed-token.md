# Task ID

TASK-MONO-507

# Title

통합 데모에서 도메인 게이트웨이가 assumed 토큰을 401 로 거부한다 — 콘솔 도메인 섹션이 열리지 않는다

# Status

ready

# Owner

monorepo

# Task Tags

- code
- infra
- demo

---

# 배경 — `TASK-MONO-505` AC-2 가 여기서 막혔다

`MONO-505` 가 콘솔의 per-domain federation env 를 승격하고, 테넌트 전환을 막던 배선 결함
(`ADMIN_SERVICE_URL` 부재 → fail-closed 가 "미할당" 으로 위장)을 고친 뒤 도달한 지점이다.

이제 테넌트 전환은 성립한다:

```
POST /api/tenant {"tenant":"demo-corp"}  →  200 {"ok":true,"activeTenant":"demo-corp"}
```

그런데 그 다음 도메인 호출이 **401** 로 죽는다(콘솔 로그, ERP 슬라이스 실측):

```
erp_unauthorized      status=401 code=UNAUTHORIZED  path=/api/erp/masterdata/departments
erp_approval_unauthorized status=401                path=/api/erp/approval/inbox
```

결과적으로 `/erp` 는 렌더되지 않고 `/erp → 307 /login → 307 /console` 로 바운스한다.

## 토큰은 정상이다 (쿠키에서 디코드한 실측)

```
console_assumed_token
  iss            = http://iam.local
  aud            = platform-console-web
  tenant_id      = demo-corp
  sub            = platform-console-web
  entitled_domains = ["ecommerce","erp","finance","scm","wms"]
  roles          = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
                    WMS_OPERATOR, OUTBOUND_READ/WRITE, INBOUND_READ/WRITE,
                    INVENTORY_READ/WRITE, MASTER_READ]
```

`entitled_domains` 에 `erp` 가 있고 `ERP_OPERATOR` 도 있다. **dual-accept 게이트가 기대하는
클레임은 전부 들어 있다.**

## 이미 배제한 것

- **env 배선** — 전환 **전에는 같은 요청이 `403 TENANT_FORBIDDEN`** 이었고 전환 **후 `401`**
  로 바뀌었다. 즉 요청은 ERP 게이트웨이에 **도달하고 있다**(base URL 은 맞다).
  같은 스택에서 `domain-health` 는 `erp: ok` 다.
- **JVM DNS / JWKS stale 캐시** — `MONO-505` 태스크 노트가 예고한 항목. auth-service 재생성
  후 `erp-platform-gateway` 를 재시작하고 재현했으나 **동일**했다.

## 주목할 만한 단서

`sub=platform-console-web` — assumed 토큰의 subject 가 **사용자가 아니라 클라이언트 ID** 다.
게이트웨이가 사용자 subject 를 요구한다면 이것이 401 의 원인일 수 있다. **가설이며,
착수 시 증거로 확인할 것**(추측을 결론으로 승격하지 말 것).

---

# Goal

통합 데모에서 `demo-corp` 를 선택한 운영자가 콘솔의 도메인 운영 섹션(최소 ERP 1개)을
401 없이 연다. 원인이 게이트웨이 검증 규칙이면 규칙을, 토큰 형태면 발급을 고친다.

---

# Scope

## In Scope

- ERP 게이트웨이가 assumed 토큰을 401 로 떨구는 **정확한 지점** 규명(어느 검증기, 어느 클레임)
- 그 원인에 대한 최소 수정 — 게이트웨이 검증 / assume-tenant 발급 / 데모 배선 중 하나
- 같은 원인이 wms · scm · finance · ecommerce 에도 해당하는지 **확인**(형제 파리티 —
  네 도메인 중 하나만 고치고 끝내지 말 것)
- 재발 방지 가드 또는 테스트

## Out of Scope

- 콘솔 UI 변경
- 도메인 데이터 시드 — `TASK-MONO-506`
- per-domain env 승격 — `TASK-MONO-505` 에서 완료

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — 위 실측을 그대로 믿지 않는다. 착수 시점에 슬라이스를 다시
      띄워 401 을 재현하고, **게이트웨이 쪽 로그/디버그로** 거부 지점을 특정한다.
      `sub=platform-console-web` 가설은 확인되기 전까지 가설이다
- [ ] **AC-1** — 원인을 한 문장으로 적을 수 있다(어느 컴포넌트가 어느 클레임을 왜 거부하는가)
- [ ] **AC-2** — 수정 후 콘솔에서 `demo-corp` 선택 → `/erp` 가 **`/erp` 에서** 200 으로
      렌더된다(바운스 금지 — 술어를 `finalPath === '/erp'` 로 둘 것. `MONO-505` 에서 느슨한
      술어가 바운스를 통과시킨 전례가 있다)
- [ ] **AC-3** — `권한 없음` / degraded 텍스트가 없다. **마커는 컴포넌트에서 복사**한다
      (`degraded` 문자열은 `data-testid` 에만 나오는 오탐이다 — 실측 확인됨)
- [ ] **AC-4 (형제 파리티)** — 나머지 4도메인에 같은 원인이 있는지 확인하고 결과를 기록한다.
      해당하면 함께 고친다
- [ ] **AC-5** — 재발 방지 수단을 추가하고 **네거티브 테스트로 무는 것을 확인**한다
- [ ] **AC-6** — `verify-demo-wrapper.sh` 전체 + CI green

---

# Related Specs

> monorepo-level task — `CLAUDE.md` § Required Workflow 의 monorepo-level 경로를 따른다.

- `projects/erp-platform/apps/gateway-service/src/main/resources/application.yml`
- `projects/iam-platform/specs/features/consumer-integration-guide.md`
- `ADR-MONO-019` (customer-tenant model, dual-accept), `ADR-MONO-020` D4 (assume-tenant)
- `infra/demo/README.md` § federation env 배선 (MONO-505 실측 기록)

# Related Contracts

- `projects/iam-platform/specs/contracts/http/auth-api.md` — assume-tenant 토큰 형태

---

# Target Service

- `erp-platform/gateway-service` (재현 도메인) + iam `auth-service`(발급 측, 원인에 따라)

---

# Edge Cases

- 401 과 403 은 서로 다른 층이다 — 403 은 테넌트 게이트, 401 은 토큰 검증. 이 티켓은 **401**
- 슬라이스에서 미기동 도메인이 `degraded` 로 보이는 것은 정상
- 콘솔의 operator 교환은 5s 타임아웃 — 스택 부하 시 false `unavailable`. idle 후 1회 확인

---

# Failure Scenarios

- **고쳤는데 ERP 만 낫는다** — 나머지 4도메인은 각자 게이트웨이다. AC-4
- **느슨한 술어로 초록** — 바운스한 페이지에는 나쁜 단어가 없다. AC-2/AC-3
- **로컬만 낫는다** — 로컬은 hosts+alias 로 관대하다. 가드 (i)(u) 를 함께 볼 것

---

# Test Requirements

- 슬라이스 실기동 재현 → 수정 → 재현 불가 확인
- `verify-demo-wrapper.sh` 전체
- 추가한 가드/테스트의 네거티브 확인

---

# Definition of Done

- [ ] 원인 규명 + 수정
- [ ] 4도메인 파리티 확인 기록
- [ ] 실주행 증거 기록
- [ ] Ready for review
