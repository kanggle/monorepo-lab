# Task ID

TASK-MONO-697

# Title

⏳ 게이트웨이 audience 검사를 **섀도에서 거절로** 뒤집는다 — 6 게이트웨이, **실측 불일치 0 이 확인된 뒤에만** (TASK-MONO-696 2단계)

# Status

ready

# Owner

monorepo

# Task Tags

- security
- gateway
- contract

---

> **분석 모델:** Opus 5 / **구현 권장:** Sonnet (AC-0 이 통과한 뒤의 설정 뒤집기 + 테스트 기대값 수정. AC-0 의 측정·판정은 Opus 권장 — 무엇을 «불일치 0» 의 근거로 받아들일지가 이 티켓의 전부다)
>
> ⏳ **SCHEDULED / DO NOT START — AC-0 이 참이 되기 전에는 착수하지 않는다.** 날짜 조건이 아니다(`TASK-MONO-696` § 결정: 「섀도 기간: 미정, 불일치 0 실측이 전환 조건」). AC-0 은 **verify-then-act** 게이트다 — 재서 참이면 진행, 아니면 이 파일에 측정값과 날짜(UTC)를 덧붙이고 `ready/` 에 그대로 둔다.

# Goal

`TASK-MONO-696` 1단계는 6 게이트웨이(ecommerce · wms · scm · erp · finance · fan)에 audience allowlist 검사를 **SHADOW** 로 넣었다: `aud` ∩ allowlist = ∅ 인 토큰을 거절하지 않고 WARN 로그 + 메트릭으로 센다. 이 티켓은 그 측정이 **불일치 0** 을 보인 뒤, 6 게이트웨이의 출하 모드를 **ENFORCE**(불일치 → 403) 로 바꾼다.

1단계 PR 이 이미 한 것(그래서 이 티켓은 **설정 뒤집기**다):

- 공유 검증기 `libs/java-gateway` `AllowedAudiencesValidator` + `GatewayJwtDecoders.validatorChain(allowedIssuers, audienceGate, tenantGate)` — ENFORCE 동작까지 구현·테스트됨.
- 403 매핑: ecommerce `SecurityConfig`(자체) + 공유 `com.example.apigateway.config.SecurityConfig`(wms/scm/erp/finance/fan) — 원인 사슬에서 `audience_mismatch` 를 찾아 403 `AUDIENCE_FORBIDDEN`.
- ENFORCE 동작 실측 칸: ecommerce·wms `SecurityConfigAudienceEnforceRealDecoderPathTest`(테스트 한정 모드 override).
- 🔴 출하 가드: 게이트웨이마다 `AudienceShippedConfigTest` 가 `<prefix>.oauth2.audience-mode` 의 출하값이 `SHADOW` 가 아니면 빨갛다. **이 티켓은 그 기대값을 의도적으로 `ENFORCE` 로 바꾸는 유일한 변경이다.**

# Scope

## In Scope

- AC-0 측정(아래)과 그 기록
- 6 게이트웨이 `application.yml` 의 `audience-mode` 기본값 `SHADOW` → `ENFORCE`
- 6 `AudienceShippedConfigTest` 의 기대값, 그리고 AC-0 이 드러낸 client 를 allowlist 에 추가(필요 시 — **그 추가도 이 티켓의 측정이 근거**)
- 오류 코드 이름 `AUDIENCE_FORBIDDEN` 의 소유자 확정 → `platform/contracts/jwt-standard-claims.md` § Error Handling 의 「proposal」 문구를 확정 문구로, `GatewayErrorCodes.AUDIENCE_FORBIDDEN` Javadoc 의 「Still a proposal」 제거
- 테스트 헬퍼가 allowlist 밖 client 로 민팅하는 픽스처 정리(아래 Edge Cases 의 알려진 것 셋)

## Out of Scope

- 서비스 레벨 디코더 · console-bff · iam gateway — `TASK-MONO-698`
- allowlist 를 IdP client 레지스트리에서 자동 도출하는 가드(Edge Case 로만 기록)

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act) — 실측 불일치 = 0, 그리고 «0» 이 공허하지 않다.** 게이트웨이마다 두 수를 잰다:
  - **분자** `gateway_jwt_audience_total{gateway="<g>",outcome="mismatch_shadowed"}` (Micrometer `gateway.jwt.audience`) 증가량 = **0**, 그리고 WARN 로그 `JWT audience not on allowlist: gateway=<g>` 줄 수 = **0**.
  - **분모** 같은 기간 `outcome="match"` 증가량 **> 0** — match 가 0 이면 «불일치 0» 은 트래픽이 없었다는 뜻이지 allowlist 가 맞았다는 뜻이 아니다. 분모가 0 인 게이트웨이는 **통과가 아니라 미측정**이다.
  - **어디서 읽나 (셋 다, 각각 기록):** ① 데모 스택 — 각 게이트웨이 `/actuator/prometheus`(노출 여부부터 확인) 또는 컨테이너 로그(`docker logs <gateway> 2>&1 | grep -c "JWT audience not on allowlist"`), 데모의 실제 사용 경로(콘솔 5 도메인 화면 순회 · web-store 로그인·주문 · fan 웹 로그인) 를 한 바퀴 돈 뒤. ② `nightly-e2e.yml` 의 fullstack 잡들 — 게이트웨이 컨테이너 로그를 아티팩트로 남기는지부터 확인(안 남기면 그것이 측정 공백이다 — 공백을 기록하고 ①로 판정). ③ Testcontainers IT(`:<project>:apps:gateway-service:integrationTest`, CI 통합 잡) — 헬퍼 픽스처는 이제 운영과 같은 `aud` 를 민팅하므로, 로그에 mismatch 가 찍히면 픽스처 또는 allowlist 결함이다.
  - **`TASK-MONO-696` § AC-1 (b) 의 ⚪ 칸(미측정 client)을 하나씩 판정한다:** `ecommerce-admin-dashboard-client` · `wms-user-flow-client` · `wms-internal-services-client` · `scm-platform-internal-services-client` · `erp-platform-internal-services-client` · `finance-platform-internal-services-client`. 섀도 로그에 나타나면 → 그 게이트웨이 allowlist 에 넣을지(정말 그 엣지를 쓰는 호출자인가) **소유자 결정** 후 이 티켓에서 추가; 안 나타나면 «측정 기간 동안 도달 0» 으로 기록(부재 증명이 아님을 명시).
  - 측정 결과가 0 이 아니면 **여기서 멈춘다** — 값·기간·출처를 이 파일에 덧붙이고 `ready/` 유지.
- [ ] **AC-1 — 오류 코드 이름 확정.** 소유자에게 `AUDIENCE_FORBIDDEN` 확정 또는 대체 이름을 묻고 그 답을 이 파일에 정확한 형태로 기록한다. 이름이 바뀌면 `GatewayErrorCodes.AUDIENCE_FORBIDDEN` 값 · `GatewayErrorCodesTest` 핀 · 두 enforce 테스트 · ecommerce `iam-integration.md` Error Responses 행 · 네 gateway architecture.md 를 같은 PR 에서 바꾼다. 계약서 § Error Handling 의 「not yet fixed … proposal」 문장을 확정 문장으로 개정한다(스펙 먼저).
- [ ] **AC-2 — 6 게이트웨이 ENFORCE 출하.** 각 `application.yml` `audience-mode: ${OIDC_AUDIENCE_MODE:ENFORCE}`; 각 `AudienceShippedConfigTest#shipsShadow` → `shipsEnforce` 로 기대값 변경. 🔴 한 PR 에서 6 개 전부 — 일부만 뒤집으면 콘솔 한 토큰이 도메인마다 다르게 취급된다.
- [ ] **AC-3 — 1단계 섀도 칸의 뒤집기.** ecommerce·wms `SecurityConfigRealDecoderPathTest` 의 `…passesInShadow_andIsCounted` 칸은 출하 모드를 측정한다 — ENFORCE 출하 후에는 403 `AUDIENCE_FORBIDDEN` + `mismatch_rejected +1` 로 뒤집는다(또는 enforce 테스트와 합친다). 대조군(테넌트 없음 → `TENANT_FORBIDDEN`, audience 카운터 0)은 유지.
- [ ] **AC-4 — 픽스처가 allowlist 밖으로 민팅하는 알려진 셋 정리.** scm `JwtTestHelper#signClientCredentialsToken`(`aud=scm-platform-internal-services-client`) · erp `#signClientCredentialsToken(clientId)`(`aud=clientId`) · finance `#signScopeOnlyToken`(`aud=subject`). 이 셋을 쓰는 IT 가 ENFORCE 에서 403 이 된다. AC-0 판정에 맞춰 (a) allowlist 에 해당 client 추가, 또는 (b) 해당 IT 의 기대값을 403 으로 — 둘 중 무엇인지 기록. **픽스처를 allowlist 값으로 바꿔 초록을 만들지 않는다**(운영 토큰은 그 `aud` 를 안 가진다).
- [ ] **AC-5 — 검증.** `:libs:java-gateway:check` + 6 게이트웨이 `:check` rc=0(파이프 금지, rc 명시). 통합 잡(Testcontainers)은 CI 가 권위. 배포 후 데모에서 콘솔 5 도메인 · web-store · fan 각 1회 200 확인 + `mismatch_rejected` 0.

# Related Specs

- `platform/contracts/jwt-standard-claims.md` § JWT Validation rule 5 (섀도 단계, 전환 조건) · § Error Handling (`AUDIENCE_FORBIDDEN` 제안)
- `platform/service-types/identity-platform.md` `:225`, `:283`
- `tasks/review/TASK-MONO-696-the-gateway-audience-is-configured-and-never-checked.md` § 결정 · § AC-1 (b) · § AC-4/5 실측

# Related Contracts

- `platform/contracts/jwt-standard-claims.md`
- `projects/ecommerce-microservices-platform/specs/integration/iam-integration.md` § Error Responses

# Target Service

- `libs/java-gateway` (`GatewayErrorCodes` Javadoc 만 — 코드 경로는 1단계에서 완성)
- `gateway-service` × 6

# Edge Cases

- **분모 0** — 데모에서 어떤 게이트웨이도 트래픽을 안 받았으면 «불일치 0» 은 측정이 아니다. match > 0 을 같이 요구하는 이유.
- **fan 은 콘솔 팬아웃 대상이 아니다** — fan allowlist 는 `fan-platform-user-flow-client` 하나. 콘솔 토큰이 fan 에 나타나면 그 경로부터 조사(결정 전 추가 금지).
- **서비스 간 호출이 게이트웨이를 경유하는 경로** — 내부 `client_credentials` 토큰이 섀도 로그에 나타나면, allowlist 추가 전에 그 호출이 게이트웨이를 거쳐야 하는지부터 확인.
- **새 client 등록** — 이 티켓 이후에는 게이트웨이를 부르는 client 를 등록하는 PR 이 allowlist 도 같이 바꾸지 않으면 그 client 가 403. IdP 시드 client ↔ 게이트웨이 allowlist 대조 가드가 필요한지는 이 티켓에서 판단만 기록(구현은 별도).
- **env override** — `OIDC_AUDIENCE_MODE` / `OIDC_ALLOWED_AUDIENCES` 는 환경변수로 덮을 수 있다. `AudienceShippedConfigTest` 는 **프로젝트의** `docker-compose*.yml`·`.env*` 만 본다 — `infra/demo/*.override.yml` · `.github/workflows/*` 에서의 override 는 가드 밖(프로젝트 밖 파일을 읽는 테스트는 Gradle·PR 필터 둘 다에 안 보인다, `TASK-MONO-695`). ENFORCE 뒤집기 후 데모에서 실제 모드를 로그/설정으로 한 번 확인.

# Failure Scenarios

- 🔴 **측정 없이 뒤집기** — 콘솔의 한 도메인 화면 전체 또는 web-store/fan 로그인 사용자 전체가 403. AC-0 이 막는다.
- **일부 게이트웨이만 뒤집기** — 콘솔 팬아웃 토큰이 도메인마다 다른 판정. AC-2 가 한 PR 을 요구한다.
- **픽스처를 allowlist 값으로 바꿔 IT 를 초록으로** — 테스트 초록 · 운영 거절(`TASK-MONO-696` Failure Scenarios 1번의 재발). AC-4 가 금지한다.
- **403 이 아니라 401** — 매핑은 1단계에서 실측됐지만, 뒤집은 뒤 데모에서 콘솔이 «세션 만료» 로 읽으면 이 경로부터 확인.

# Test Requirements

- 6 `AudienceShippedConfigTest` 기대값 ENFORCE
- ecommerce·wms 실제 디코더 경로: 출하 모드에서 불일치 → 403 `AUDIENCE_FORBIDDEN`, 허용/배열 → 200, 비-audience 거절 회귀 칸 유지
- 영향받는 IT 의 기대값(AC-4)

# Definition of Done

- [ ] AC-0 ~ AC-5
- [ ] 6 게이트웨이 출하 모드 = ENFORCE, 계약서의 오류 코드 이름이 제안이 아니라 확정

---

# 🔵 창 실측 — 2026-09-18 UTC 둘째 창 (07:57:15Z–08:16:26Z · **17분** / 상한 30분) · AMI `ami-02613b0378621b124`(`af0018aa6`) · 인스턴스 `i-07ddb6b41233f2673` · 묶음 7종(console · console-ecommerce · console-wms · console-scm · console-erp · console-finance · fan) · 소유자 승인 «창 30분, 683 쓰기 포함»

## 2026-09-18 둘째 창 — AC-0 의 **분모는 만들었고**, 분자를 못 읽었다

🔵 **먼저: 이 티켓에 재굽기가 필요 없다는 것이 확정됐다.** 1단계 섀도 게이트
(`3c946e6c2`)가 **현재 핀 AMI(`af0018aa6`)의 조상**임을 `git merge-base --is-ancestor` 로
확인했다 ⇒ 지금 떠 있는 데모가 이미 SHADOW 로 재고 있다.

**분모(`outcome="match"` > 0)를 만들기 위해** 6 게이트웨이를 실제로 통과시켰다
(08:11–08:12, 콘솔 로그인 + 테넌트 `demo-corp` 적용 후):

| 게이트웨이 | 경로 | 응답 |
|---|---|---|
| ecommerce | `/ecommerce/products` | 200 |
| wms | `/wms/inventory` · `/wms/master` | 200 · 200 |
| scm | `/scm/procurement` · `/scm/inventory` | 200 · 200 |
| erp | `/erp/masters` | 200 |
| finance | `/ledger` · `/finance` | 200 · 200 |
| iam | `/operators` | 200 |

🔴 **`fan` 게이트웨이는 이 트래픽이 안 지난다** — 콘솔은 fan 도메인을 그리지 않는다.
`fan` 묶음은 띄웠지만 fan 웹에 로그인하지 않았으므로 **fan 은 분모 0 = 미측정**이다.
다음 창에서는 fan 웹 로그인을 한 번 넣어야 6/6 이 된다.

🔴 **분자(`mismatch_shadowed` · WARN 로그)는 못 읽었다** — 컨테이너 안이라 SSM 이 필요하고,
넘긴 명령의 출력이 이 세션 안에 돌아오지 않았다. ⇒ AC-0 은 열려 있다.
🔵 **다음 창의 순서가 이것으로 정해졌다**: ① 트래픽(콘솔 9경로 + **fan 로그인**) → ② SSM 으로
게이트웨이별 WARN 줄 수 + `/actuator/prometheus` 의 `gateway_jwt_audience_total`.
명령 전문은 `TASK-MONO-672` 항목 6.


---

# ⚪ 2026-09-22 데모 창 — **미측정이다. 「불일치 0」으로 읽지 마라** (분석=Opus 5)

게이트웨이 7개 전부 `JWT audience not on allowlist` **0줄**이었다. 그 0 을 이 티켓의 전제
(「섀도 모드가 돌고 있고 불일치가 없다」)로 쓰면 안 된다 — 유효성 술어를 세워 보니 갈리지 않는다:

- 컨테이너에 `curl` 이 **있다**(`/usr/bin/curl`) ⇒ 「도구가 없어 못 읽었다」는 배제.
- `actuator/prometheus` 에 `gateway_jwt_audience*` **없음** · `actuator/env` 는 **401**.
- 배포 이미지는 13차 굽기 산물(`created 2026-09-22T06:55Z`)이고 `java-security.jar` 를 들고 있다.
- 설정은 env 없이도 켜져 있어야 한다 — `allowed-audiences: ${OIDC_ALLOWED_AUDIENCES:platform-console-web}` ·
  `audience-mode: ${OIDC_AUDIENCE_MODE:SHADOW}` (wms·scm·erp·finance·fan·ecommerce 6개 게이트웨이 모두).
- 🔴 **주입 시험**: `aud=fan-platform-user-flow-client` 토큰(wms 허용목록 밖)으로 wms 를 호출 →
  **403**, 경고는 **여전히 0줄**. 그 게이트웨이의 **WARN 총 줄 수도 0** 이다.

⇒ 「검사가 돌고 전부 일치했다」와 「검사가 그 요청에 도달하지 못했다」를 **구별하지 못한다.**

🔵 **다음 탐침 (순서대로)**: ① 그 403 이 JWT 디코딩 **전**인지 후인지 — 콘솔 토큰이 200 을 받는
wms 경로를 먼저 찾고 거기에 fan 토큰을 보낸다(내가 쓴 `/api/wms/inventory` 는 콘솔 토큰으로도
**404** 였다 ⇒ 경로부터 계약서에서 읽어라). ② 게이트웨이가 INFO 액세스 로그를 내는지 확인해
**분모**를 세운다. ③ 그 뒤에야 「불일치 N」이 수치가 된다.

🔴 **이 티켓을 「섀도에서 불일치가 없으니 enforce 로 넘겨도 된다」로 진행시키지 마라** — 그
전제가 아직 측정되지 않았다.
