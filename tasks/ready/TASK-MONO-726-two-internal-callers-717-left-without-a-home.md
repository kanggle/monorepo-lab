# Task ID

TASK-MONO-726

# Status

ready (2026-09-24 UTC — 기안. `TASK-MONO-717` 을 `done/` 으로 닫기 **전에** 그 티켓 AC-3 이 남긴 잔여 두 건의 집이 필요해 만들었다)

# Title

🔴 **717 이 «한계» 로 적고 떠난 내부 호출자 둘 — 집이 없었다**: `lockAccount` 의 iam 게이트웨이 라우트 부재 · batch-worker 의 미등록 client

# Owner

미지정

# Task Tags

- iam-platform
- ecommerce
- internal-caller
- fail-soft

---

> **분석 모델:** Opus 5.5 / **구현 권장:** AC-0 = Sonnet(측정) · 이후 갈래는 AC-0 결과를 보고 다시 정한다(인증 경로라면 Opus).

---

# Goal

`TASK-MONO-717` § AC-3 의 표가 «이 수정으로도 안 보이는 채로 남는 경로» 다섯 줄을 적었고, 그중 **앞의 둘만** 717 의 축(「설정/등록이 없어서 조용히 죽는다」)이라고 명시했다. 그 둘은 아래와 같다.

| 경로 | 717 이 적은 것 | 실제 집 (2026-09-24 확인) |
|---|---|---|
| ① `POST /internal/accounts/{a}/lock` (product-service `SellerAccountProvisioner.lockAccount`) | iam 게이트웨이에 **라우트가 없다** — `TASK-MONO-713` ⓑ 소관 | 🔴 **없음.** `tasks/done/TASK-MONO-713-…` 본문에 `lock` 이 **한 번도 안 나온다.** 게이트웨이 `application.yml` 의 내부 라우트는 지금도 `Path=/internal/tenants/**` 하나뿐 |
| ② batch-worker 의 `ecommerce-internal-services-client` | 그 client_id 가 **IdP 에 없다** — 실제로 쓰는지 안 쟀다 | 🔴 **없음.** 활성 큐 어디에도 그 이름이 없다(717 파일에만 있다) |

717 은 `TASK-MONO-721` 의 창 판정(2026-09-24 PASS)으로 닫힐 수 있게 됐는데, 닫히면 `done/`(frozen)이고 **이 두 줄은 다시 읽히지 않는다.** 이 티켓이 그 두 줄의 집이다.

🔴 **목표는 «고친다» 가 아니라 «먼저 잰다» 다.** 두 줄 모두 717 이 **측정하지 않고** 적은 한계다. 측정 전에 라우트를 뚫거나 client 를 등록하면, 717 AC-3 이 경고한 대로 «명단이 근거 없이 자란다».

# Scope

## 포함

- AC-0: 두 경로를 각각 **실측**한다(아래).
- 측정 결과에 따른 갈래 제안 → 소유자 결정.
- 결정된 갈래의 구현(별도 impl PR — 이 티켓의 spec PR 과 섞지 않는다).

## 제외

- 717 § AC-3 표의 나머지 셋(`AccountCreatedEventConsumer` · `NotificationSendService` · `OrderPiiAnonymizationService`/`AccountDeletedConsumer`) — 717 이 **의도된 fail-soft** 라고 가려 둔 것이다. 섞지 않는다.
- `ADR-MONO-076` 의 카탈로그 확장 — ②를 등록하게 되더라도 그 client 가 어느 테넌트를 assume 할 수 있는지는 ADR 의 규칙대로 따로 정한다.

# Acceptance Criteria

## AC-0 — 🔴 먼저 잰다 (고치기 전에)

- [ ] **①** 셀러를 정지(SUSPEND/CLOSE — `lockAccount` 를 부르는 전이)시켰을 때
      - product-service 가 어느 URL 을 부르는가(설정값 · 로그),
      - 응답이 **404(라우트 없음)** 인가, 그 밖의 무엇인가,
      - 그리고 🔴 **결과 상태** — `account_db.accounts.status` 가 바뀌었는가. 로그 침묵은 판정이 아니다(717 이 못박은 술어).
- [ ] **②** batch-worker 가 `/internal/**` 를 **실제로 부르는가**(코드 경로 + 설정 + 가능하면 라이브 로그). 🔴 안 부르면 등록하지 않는 것이 답이고, 그 판정을 적는 것으로 ②는 닫힌다.
- [ ] 두 결과를 이 파일에 적고, 갈래를 소유자에게 묻는다.

## AC-1 — (AC-0 이 결함으로 판정한 것만) 고친다 + bite

- [ ] 고친 뒤 결과 상태(①이면 `accounts.status`)로 판정한다. 단위 초록으로 닫지 않는다 — 717·718·721 이 **연속 세 번** 단위/로컬 초록 → 창 판정을 거쳤다.

## AC-2 — 가드 범위

- [ ] `scripts/check-internal-caller-addresses.sh` 의 호출자 목록(지금 한 줄)에 넣을 대상이 생기면 그 파일 헤더의 규칙(«실제로 `/internal/**` 을 부르는가» 먼저)대로 넣는다.

# Related Specs

- `tasks/done/TASK-MONO-717-…` § AC-3 (출처 표) · § CORRECTION 두 절
- `tasks/done/TASK-MONO-713-apply-rule-5-at-the-iam-gateway-edge.md` (717 이 가리킨 곳 — lock 을 다루지 않았다)
- `docs/adr/ADR-MONO-076-which-workload-credential-may-act-on-which-tenant.md`
- `projects/iam-platform/apps/gateway-service/src/main/resources/application.yml` (내부 라우트)

# Related Contracts

- `projects/iam-platform/specs/contracts/` 의 account-service 내부 API(`/internal/accounts/**`) — 라우트를 여는 갈래라면 **계약 먼저**.
- `platform/contracts/jwt-standard-claims.md` § 워크로드 테넌트 축(721 AC-3).

# Edge Cases

| 상황 | 기대 |
|---|---|
| ① 이 404 인데 account-service 쪽 상태가 다른 경로(이벤트)로 이미 바뀐다 | 결함이 아니라 **이중 경로**다 — 결과 상태로 판정하고, 죽은 HTTP 호출을 지울지가 질문이 된다 |
| ② 의 client 를 batch-worker 가 설정에만 들고 호출하지 않는다 | 등록하지 않는다. 설정에서 지울지는 별개 판단 |
| 데모 창 없이 ①을 재야 한다 | 로컬 compose 로 가능한지 먼저 본다. 안 되면 `TASK-MONO-672` 가 아니라 **이 티켓**에 창 항목을 적는다(672 는 «닫히면서 집을 잃는» 의무만 받는다) |

# Failure Scenarios

1. 🔴 **재지 않고 게이트웨이에 `/internal/accounts/**` 라우트를 연다** → 717 이 밟은 그 모양이다(게이트웨이 경유는 `TenantScopeGuard` 와 부딪힐 수 있다 — 717 § CORRECTION 의 403 `TENANT_SCOPE_DENIED`). 라우트가 답인지부터 잰다.
2. 🔴 **②를 「IdP 에 없으니 등록」 으로 닫는다** → 쓰지 않는 자격이 명단에 생긴다. 717 AC-3 이 이미 경고했다.
3. **717 을 done 으로 옮기면서 이 두 줄을 그 파일에만 남긴다** → 이 티켓이 없었을 때의 상태. 이 티켓의 존재 이유다.
