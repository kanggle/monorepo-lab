# Task ID

TASK-MONO-514

# Title

WMS 마스터 데이터를 쓸 수 있는 자격증명이 이 플랫폼에 없다 — `MASTER_WRITE` 를 발급하는 경로가 없고, 워크로드 클라이언트는 scope 만 싣는다

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

`TASK-MONO-510`(백오피스 시드) AC-0 이 발굴했다.

master-service 의 쓰기는 전부 이렇게 인가한다:

```java
@PreAuthorize("hasRole('MASTER_WRITE') or hasRole('MASTER_ADMIN')")
```

그런데 그 역할을 **주는 경로가 없다.**

## 실측 (2026-08-05, 로컬 `iam wms console` 슬라이스)

**(1) 운영자 토큰** — 콘솔 로그인 → RFC 8693 assume `demo-corp`:

```
roles = [ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR,
         WMS_OPERATOR, OUTBOUND_READ, OUTBOUND_WRITE, INBOUND_READ, INBOUND_WRITE,
         INVENTORY_READ, INVENTORY_WRITE, MASTER_READ]

POST /api/v1/master/warehouses  403 {"code":"FORBIDDEN",
                                     "message":"Insufficient privileges for this operation"}
```

🔴 **비대칭에 주목하라**: `OperatorRoleDerivation.WMS_OPERATOR_ROLES` 는 outbound ·
inbound · inventory 에 **READ 와 WRITE 를 둘 다** 주는데 master 만 **READ 뿐**이다.
그 클래스의 주석은 `*_ADMIN` 을 제외한 이유는 적어 두었지만(취소 · 강제 사가 실패 ·
마스터 데이터 쓰기), `MASTER_WRITE` 자체가 왜 빠졌는지는 적혀 있지 않다. 주석은
"master-data writes" 를 ADMIN 티어로 분류하는데 **코드의 술어는 `MASTER_WRITE` 이지
`MASTER_ADMIN` 이 아니다** — 분류와 술어가 어긋나 있다.

**(2) 워크로드 클라이언트** — `wms-internal-services-client`, `client_credentials`:

```
scope=internal.invoke              → invalid_scope (등록돼 있지 않다)
scope=wms.master.write             → 발급됨. tenant_id=wms, roles 클레임 **없음**
POST /api/v1/master/warehouses     403 (동일)
```

🔴 **scope 는 아무것도 열지 못한다** — master-service 는 **role** 로 인가하기 때문이다.
워크로드 토큰에 role 클레임이 실리지 않으므로, `wms.master.write` scope 를 들고도
`hasRole('MASTER_WRITE')` 는 거짓이다. **이름이 맞는 scope 가 존재한다는 사실이
그 scope 가 무언가를 연다는 증거가 아니다.**

## 🔴 재측정 시 반드시 키를 바꿔라

wms 의 변이 엔드포인트는 `Idempotency-Key`(UUID)를 요구하고, **같은 키로 다시 부르면
실패 응답까지 그대로 재생한다** — 두 번째 403 의 타임스탬프가 첫 번째와 **바이트 단위로
동일**했다. 그 상태로 "워크로드 클라이언트도 막혔다" 를 결론 낼 뻔했고, 키를 바꿔 다시
받고서야 실측이 됐다(결론은 같았지만 근거는 그때 처음 생겼다).

---

# Goal

WMS 마스터 데이터를 **API 로** 만들 수 있는 자격증명이 존재한다 — 또는 존재하지 않는 것이
의도라면 그 사실이 코드에 적혀 있고, 데모/운영이 그 전제 위에서 동작한다.

---

# Scope

## In Scope

- `MASTER_WRITE` 발급 경로 결정(운영자 엔타이틀먼트 확장 / 전용 역할 / 워크로드 role 클레임)
- 위 비대칭에 대한 근거를 코드에 남기기
- `OperatorRoleDerivation` 주석의 "master-data writes = ADMIN 티어" 진술과 실제 술어의 정합

## Out of Scope

- 데모 마스터 데이터 자체 — `infra/demo/wms-devseed.override.yml` 이 저장소의 기존
  Flyway dev 시드를 켜서 해결한다(`TASK-MONO-510`). 이 티켓은 **API 도달 가능성**이다

---

# Acceptance Criteria

- [ ] **AC-0 (재측정)** — 위 두 403 을 **새 Idempotency-Key 로** 다시 받는다.
      그리고 `MASTER_WRITE` / `MASTER_ADMIN` 을 **요구하는 곳**과 **발급하는 곳**을
      각각 전수로 센다. 발급처가 0이 아니면 이 티켓의 전제가 틀린 것이다
- [ ] **AC-1 (결정)** — 세 안 중 택1하고 근거를 남긴다:
      ① 운영자 엔타이틀먼트에 `MASTER_WRITE` 추가(다른 세 서비스와 대칭)
      ② 마스터 전용 운영 역할 신설
      ③ 워크로드 클라이언트 토큰에 role 클레임 부여(scope↔role 간극 자체를 메운다)
      역할 모델 변경이면 **ADR**
- [ ] **AC-2 (도달 가능성)** — 실제 호출자가 `POST /api/v1/master/warehouses` 로 201 을
      받는다. 토큰 발급 성공만으로는 부족하다(그것이 이 결함의 모양이다)
- [ ] **AC-3 (음성 대조)** — 그 자격증명이 **없는** 호출자는 여전히 403 이어야 한다.
      양성만으로는 "열렸다" 와 "게이트가 사라졌다" 를 구별할 수 없다
- [ ] **AC-4 (주석 정합)** — `OperatorRoleDerivation` 의 분류 문장과 서비스의 실제 술어가
      일치하거나, 어긋나는 이유가 적혀 있다

---

# Related Specs

- `projects/iam-platform/apps/auth-service/.../OperatorRoleDerivation.java`
- `projects/wms-platform/apps/master-service/.../application/service/*Service.java` (`@PreAuthorize`)
- `infra/demo/wms-devseed.override.yml` (사유 원문 + 실측)

# Edge Cases

- wms 는 **데이터에 테넌트가 거의 없다** — `tenant_id` 컬럼을 가진 테이블은 5개 DB 통틀어
  `outbound_db.outbound_order` 하나뿐이다(실측). 역할을 넓히면 테넌트 격리로는 좁혀지지
  않는다는 뜻이므로, 권한 범위를 정할 때 이 사실을 전제로 삼아야 한다
- `INVENTORY_RESERVE` 도 같은 부류다 — 운영자 엔타이틀먼트가 주지 않아 출고 사가의
  예약 단계가 막힌다(실측). 같은 결정으로 함께 다룰지 정할 것

# Failure Scenarios

- **scope 를 추가하고 끝낸다** — 이름은 맞는데 여전히 403 이다. 인가는 role 로 한다
- **AC-2 없이 토큰 발급만 확인한다** — 이 결함이 정확히 그 모양이다

# Definition of Done

- [ ] 결정 + (필요시) ADR
- [ ] AC-2/AC-3 실측 증거
- [ ] Ready for review
