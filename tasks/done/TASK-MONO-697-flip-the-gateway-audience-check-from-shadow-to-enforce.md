# Task ID

TASK-MONO-697

# Title

⏳ 게이트웨이 audience 검사를 **섀도에서 거절로** 뒤집는다 — 6 게이트웨이, **실측 불일치 0 이 확인된 뒤에만** (TASK-MONO-696 2단계)

# Status

done

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

- 서비스 레벨 디코더 · iam gateway — `TASK-MONO-698` (🔵 2026-10-02 UTC: 이 줄에 있던 콘솔 BFF 엣지는 `TASK-MONO-757` 이 은퇴시켰다 — 더 이상 대상이 아니다)
- allowlist 를 IdP client 레지스트리에서 자동 도출하는 가드(Edge Case 로만 기록)

# Acceptance Criteria

- [x] **AC-0 (verify-then-act) — 실측 불일치 = 0, 그리고 «0» 이 공허하지 않다.** 게이트웨이마다 두 수를 잰다:
  - **분자** `gateway_jwt_audience_total{gateway="<g>",outcome="mismatch_shadowed"}` (Micrometer `gateway.jwt.audience`) 증가량 = **0**, 그리고 WARN 로그 `JWT audience not on allowlist: gateway=<g>` 줄 수 = **0**.
  - **분모** 같은 기간 `outcome="match"` 증가량 **> 0** — match 가 0 이면 «불일치 0» 은 트래픽이 없었다는 뜻이지 allowlist 가 맞았다는 뜻이 아니다. 분모가 0 인 게이트웨이는 **통과가 아니라 미측정**이다.
  - **어디서 읽나 (셋 다, 각각 기록):** ① 데모 스택 — 각 게이트웨이 `/actuator/prometheus`(노출 여부부터 확인) 또는 컨테이너 로그(`docker logs <gateway> 2>&1 | grep -c "JWT audience not on allowlist"`), 데모의 실제 사용 경로(콘솔 5 도메인 화면 순회 · web-store 로그인·주문 · fan 웹 로그인) 를 한 바퀴 돈 뒤. ② `nightly-e2e.yml` 의 fullstack 잡들 — 게이트웨이 컨테이너 로그를 아티팩트로 남기는지부터 확인(안 남기면 그것이 측정 공백이다 — 공백을 기록하고 ①로 판정). ③ Testcontainers IT(`:<project>:apps:gateway-service:integrationTest`, CI 통합 잡) — 헬퍼 픽스처는 이제 운영과 같은 `aud` 를 민팅하므로, 로그에 mismatch 가 찍히면 픽스처 또는 allowlist 결함이다.
  - **`TASK-MONO-696` § AC-1 (b) 의 ⚪ 칸(미측정 client)을 하나씩 판정한다:** `ecommerce-admin-dashboard-client` · `wms-user-flow-client` · `wms-internal-services-client` · `scm-platform-internal-services-client` · `erp-platform-internal-services-client` · `finance-platform-internal-services-client`. 섀도 로그에 나타나면 → 그 게이트웨이 allowlist 에 넣을지(정말 그 엣지를 쓰는 호출자인가) **소유자 결정** 후 이 티켓에서 추가; 안 나타나면 «측정 기간 동안 도달 0» 으로 기록(부재 증명이 아님을 명시).
  - 측정 결과가 0 이 아니면 **여기서 멈춘다** — 값·기간·출처를 이 파일에 덧붙이고 `ready/` 유지.
  - 🔵 **2026-10-04 UTC — 닫음 (소유자 결정 1: «CI 로그의 불일치 줄은 전부 테스트 픽스처 → 판정에서 제외하고 AC-0 을 닫는다.»).** 근거는 셋이다.
    - **데모 창 두 번이 6/6 `match>0 · mismatch=0`이다.** 18차(2026-10-02)와 19차(2026-10-04) 모두 그랬다. finance 의 분모는 두 창 다 1 이다.
    - **코드 전수에서 ⚪ client 6개가 모두 (i) 호출자 없음이다.** 그래서 allowlist 에 추가할 것이 없다.
    - **CI 의 불일치 줄은 전부 테스트 픽스처가 민팅했고, 소유자 결정으로 판정에서 뺐다.** 목록:
      - scm IT 1줄, `aud=[scm-platform-internal-services-client]`
      - scm e2e smoke 16줄, `aud=[]`
      - erp IT 1줄, `aud=[erp-internal-client]`
      - finance IT 1줄, `aud=[machine-client]`
      - nightly scm e2e 67줄, `aud=[]`
  - 🔴 **측정 공백 — 0 이 아니라 «안 쟀다» 다.**
    - ecommerce 게이트웨이 IT 는 테스트 logback 에 콘솔 appender 가 없어서 앱 로그가 안 찍힌다.
    - wms 게이트웨이 `integrationTest`·`e2eSmokeTest`·`e2eFullTest` 는 `FROM-CACHE` 였다(돌지 않았다).
    - nightly 는 게이트웨이 컨테이너 로그를 아티팩트로 남기지 않는다. fan e2e 도 게이트웨이 stdout 을 흘리지 않는다.
    - web-store·console fullstack 과 federation 스위트는 성공하면 컨테이너 로그를 안 남긴다.
  - 상세는 아래 **§ AC-0 판정 — 2026-10-04 UTC** 와 **§ 소유자 결정 2 (2026-10-04 UTC)** 에 있다.
- [x] **AC-1 — 오류 코드 이름 확정.** 🔵 2026-10-04 UTC 소유자 결정: `AUDIENCE_FORBIDDEN` 확정(`PERMISSION_DENIED` 재사용안 대신 추천안 채택). 계약서 문구 개정은 이 PR 에서 했다. 이름이 그대로라 rename 작업은 없다. 아래 § AC-0 판정 › 소유자 결정 참고. 소유자에게 `AUDIENCE_FORBIDDEN` 확정 또는 대체 이름을 묻고 그 답을 이 파일에 정확한 형태로 기록한다. 이름이 바뀌면 `GatewayErrorCodes.AUDIENCE_FORBIDDEN` 값 · `GatewayErrorCodesTest` 핀 · 두 enforce 테스트 · ecommerce `iam-integration.md` Error Responses 행 · 네 gateway architecture.md 를 같은 PR 에서 바꾼다. 계약서 § Error Handling 의 「not yet fixed … proposal」 문장을 확정 문장으로 개정한다(스펙 먼저).
- [x] **AC-2 — 6 게이트웨이 ENFORCE 출하.** 🟢 2026-10-04 UTC 뒤집기 PR — 증거는 아래 **§ 뒤집기 PR 기록 (2026-10-04 UTC)** 의 AC-2 절. 각 `application.yml` `audience-mode: ${OIDC_AUDIENCE_MODE:ENFORCE}`; 각 `AudienceShippedConfigTest#shipsShadow` → `shipsEnforce` 로 기대값 변경. 🔴 한 PR 에서 6 개 전부 — 일부만 뒤집으면 콘솔 한 토큰이 도메인마다 다르게 취급된다.
  - **되돌리기 레버 (소유자 결정 3, 2026-10-04 UTC):** 같은 PR 에서 6 게이트웨이 compose 의 `environment:` 에 `OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}` 를 넣는다. 그러면 재굽기 없이 `demo.env` 한 줄과 재생성으로 되돌릴 수 있다. compose 위치: ecommerce `docker-compose.yml:1181-`, wms `docker-compose.e2e.yml:76-`(데모도 이 파일로 앱을 띄운다 — `infra/demo/projects.sh:50`), scm `:39-`, erp `:49-`, finance `:50-`, fan `:27-`.
    - 🔴 `OIDC_ALLOWED_AUDIENCES` 는 넘기지 않는다. 값이 게이트웨이마다 다르기 때문이다.
    - 🔴 6 `AudienceShippedConfigTest$Shipped` ③(«프로젝트 `docker-compose*.yml`·`.env*` 에 ENFORCE override 줄 0»)은 이 줄이 들어가면 빨개진다. 같은 PR 에서 술어를 바꾼다: «compose 는 `OIDC_AUDIENCE_MODE` 를 `${OIDC_AUDIENCE_MODE:-ENFORCE}` 형태로만 넘긴다 · 다른 값을 고정하지 않는다 · `.env*` 에 `OIDC_AUDIENCE_MODE=SHADOW` 고정 없음».
    - 🔴 그 술어는 «전달 줄이 6/6 있다» 도 단언해야 한다. 안 그러면 레버가 하나 빠져도 초록이다.
- [x] **AC-3 — 1단계 섀도 칸의 뒤집기.** 🟢 2026-10-04 UTC — 증거는 아래 **§ 뒤집기 PR 기록** 의 AC-3 절. ecommerce·wms `SecurityConfigRealDecoderPathTest` 의 `…passesInShadow_andIsCounted` 칸은 출하 모드를 측정한다 — ENFORCE 출하 후에는 403 `AUDIENCE_FORBIDDEN` + `mismatch_rejected +1` 로 뒤집는다(또는 enforce 테스트와 합친다). 대조군(테넌트 없음 → `TENANT_FORBIDDEN`, audience 카운터 0)은 유지.
- [x] **AC-4 — 픽스처가 allowlist 밖으로 민팅하는 알려진 셋 정리.** 🟢 2026-10-04 UTC — (b) 를 집행했다. scope 다리 증명은 admission 단위 테스트로 옮겼고, e2e 헬퍼 둘을 고쳤다. 증거는 아래 **§ 뒤집기 PR 기록** 의 AC-4 절. scm `JwtTestHelper#signClientCredentialsToken`(`aud=scm-platform-internal-services-client`) · erp `#signClientCredentialsToken(clientId)`(`aud=clientId`) · finance `#signScopeOnlyToken`(`aud=subject`). 이 셋을 쓰는 IT 가 ENFORCE 에서 403 이 된다. AC-0 판정에 맞춰 (a) allowlist 에 해당 client 추가, 또는 (b) 해당 IT 의 기대값을 403 으로 — 둘 중 무엇인지 기록. **픽스처를 allowlist 값으로 바꿔 초록을 만들지 않는다**(운영 토큰은 그 `aud` 를 안 가진다).
  - **2026-10-04 UTC 확정 — (b).** AC-0 판정이 6/6 (i) 호출자 없음이므로, 위 세 IT 칸(scm `GatewayBootstrapIntegrationTest.java:44-54` · erp `GatewayRoutingIntegrationTest.java:42` · finance `GatewayEdgeIntegrationTest.java:77`)의 기대값을 **403 `AUDIENCE_FORBIDDEN`** 으로 바꾼다.
  - 🔴 **«scope 만 있는 토큰이 admission 을 통과한다» 증명은 다른 방법으로 남긴다.** 이 세 칸은 rule-6 admission «role OR scope» 중 scope 다리의 유일한 IT 증인이었다. 둘 중 하나로 대체하고, 그 칸 이름을 이 티켓에 기록한다: 그 칸만 테스트 한정 SHADOW override 로 돌리거나, admission 단위 테스트로 옮긴다.
  - **확장 (소유자 결정 2, 2026-10-04 UTC): «전환 PR 에서 scm·fan `tests/e2e` 헬퍼도 운영과 같은 `aud` 를 민팅하도록 함께 고친다.»** 대상과 근거:
    - 대상: `projects/scm-platform/tests/e2e/.../JwtTestHelper.java:69-92` · `projects/fan-platform/tests/e2e/.../JwtTestHelper.java:70-126`. 둘 다 지금 `aud` 를 아예 넣지 않는다.
    - scm 의 사람 토큰(BUYER·OPERATOR)은 콘솔 경유 → `platform-console-web`. fan 의 사람 토큰 → `fan-platform-user-flow-client`.
    - 이것은 사람 사용자 토큰이 운영에서 실제로 싣는 값이다. 그러므로 «allowlist 값으로 초록 만들기» 금지에 걸리지 않는다(`TASK-MONO-696` AC-5 원칙의 연장). 워크로드 모양 토큰에는 적용하지 않는다.
    - 고치지 않으면 ENFORCE 뒤에 scm smoke(`ci.yml`) · scm/fan e2e full(nightly) · fan smoke 가 403 이 된다.
- [ ] **AC-5 — 검증.** (🔵 2026-10-04 UTC: 로컬 `:check` 7개 rc=0 은 § 뒤집기 PR 기록에 있다. 데모 창 확인이 남아 있어 이 AC 는 열려 있다.) `:libs:java-gateway:check` + 6 게이트웨이 `:check` rc=0(파이프 금지, rc 명시). 통합 잡(Testcontainers)은 CI 가 권위. 배포 후 데모에서 콘솔 5 도메인 · web-store · fan 각 1회 200 확인 + `mismatch_rejected` 0.
  - **창 = 다음 AMI 재굽기(`TASK-FAN-BE-050` 과 함께) 뒤의 데모 창**(소유자 결정, 2026-10-04 UTC). 그 창에서 다음을 확인한다.
  - 마지막 요약 줄이 6/6 `mode=ENFORCE` 이고 `mismatch=0` 이다.
  - **되돌리기 레버 확인**(소유자 결정 3):
    - (최소) 6 게이트웨이마다 `docker inspect <gateway> --format '{{range .Config.Env}}{{println .}}{{end}}' | grep OIDC_AUDIENCE_MODE` 가 `OIDC_AUDIENCE_MODE=ENFORCE` 를 보인다. env 가 컨테이너에 **도달한다**는 증거다.
    - (가능하면) 게이트웨이 하나에서 `OIDC_AUDIENCE_MODE=SHADOW` 로 재생성한다(`docker compose up -d --force-recreate gateway-service`). 그 뒤 요약 줄 `mode=SHADOW` 를 확인하고 ENFORCE 로 되돌린다.
    - 🔴 레버를 실제로 당기는 것은 데모 상태를 바꾸므로 창 안에서 소유자가 승인한다.

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

---

# 🔴🔴 2026-09-22 15차 창 — **위 ⚪ 절의 내 판정을 정정한다. 그리고 AC-0 은 여전히 열려 있다**

## 정정 ① — *"`actuator/prometheus` 에 `gateway_jwt_audience*` 없음"* 은 **부재 판정이 아니었다**

14차 창에서 나는 그렇게 적었다. 이번에 유효성 술어를 세워 다시 쟀다:

```
docker exec <gw> curl -s -o /dev/null -w '%{http_code}' localhost:8080/actuator/health      ->  200
docker exec <gw> curl -s -o /tmp/m    -w '%{http_code}' localhost:8080/actuator/prometheus  ->  401
                                                        (wms · ecommerce · scm 게이트웨이 셋 다)
```

⇒ 액추에이터는 **살아 있고**, 스크레이프 엔드포인트가 **401** 이다. 그때 내가 센 「0건」은
**401 오류 본문을 grep 한 0건**이다 — 「메트릭이 없다」와 「못 읽었다」를 안 가른 것이고,
이 저장소가 이름 붙인 «부재 판정에 대리지표 금지» 를 내가 다시 밟았다.

🔴 **그러므로 이 티켓의 전제(「섀도가 돌고 불일치가 없다」)는 여전히 미측정이고, 그 이유가 바뀌었다.**

## 정정 ② — *"경로부터 계약서에서 읽어라"* 는 내 지적이 맞았다. 읽으니 **200** 이다

14차 창에서 내가 쓴 `/api/wms/inventory` 는 콘솔 토큰으로도 404 였다. 계약서
(`projects/wms-platform/specs/contracts/http/admin-service-api.md` — Base path `/api/v1/admin`)에서
읽은 경로로 다시 쟀다:

```
GET http://wms.<DEMO_DOMAIN>/api/v1/admin/dashboard/inventory?page=0&size=1
  A) 콘솔 운영자 토큰 (aud="platform-console-web")   ->  200  {"content":[{"locationId":…}]}
  C) 토큰 없음                                        ->  401  {"code":"UNAUTHORIZED"}
  D) 쓰레기 토큰 ("Bearer not.a.jwt")                 ->  401  {"code":"UNAUTHORIZED"}
```

🔵 이로써 탐침 ①의 **전반부**가 닫힌다 — 「콘솔 토큰이 200 을 받는 wms 경로」가 확정됐다.
🔵 C·D 가 401 인 것도 값이 있다: **토큰 없음과 파싱 불가가 같은 답**이므로, 14차 창에서 본
「fan 토큰 → 403」은 **그 401 들과 다른 층**에서 났다는 뜻이다(적어도 디코딩은 지났다).

⚪ **후반부(allowlist 밖 `aud` 토큰을 그 경로에 보내기)는 못 했다** — `fan-platform-user-flow-client`
로 토큰을 만들려다 `{"error":"invalid_client"}` 로 막혔다. 🔴 사유를 «fan 클라이언트가 막혔다» 로
적지 않는다: 리다이렉트 URI 후보를 저널의 등록 목록에서 뽑았는데 내가 집은 것이 `…local` 판이었고,
**클라이언트 설정과 내 인자 중 어느 쪽 문제인지 안 갈렸다.**

## 🔴🔴 정정 ③ — **분모는 로그로는 영영 못 센다**

```
docker logs wms-gateway-service  ->  3273 줄
  'audience' 포함 줄              ->  0
  'dashboard/inventory' 포함 줄   ->  0     (방금 200 을 받은 그 요청이다)
```

⇒ 게이트웨이는 **요청당 액세스 로그를 내지 않는다.** 그러므로 이 티켓 AC-0 이 요구하는
*"분모 `outcome="match"` > 0"* 은 **로그를 세는 방법으로는 원리적으로 만족될 수 없다.**

## ⇒ AC-0 의 상태와 **다음 탐침 하나**

| 채널 | 상태 | 이유 |
|---|---|---|
| 분자 — `gateway_jwt_audience_total` | ⚪ 못 읽음 | `/actuator/prometheus` **401** |
| 분모 — 액세스 로그 | 🔴 **원리적으로 없음** | 요청당 로그를 안 낸다 |

🔵 **다음 탐침은 하나다**: 각 도메인 스택에 **`<domain>-prometheus` 컨테이너가 이미 떠 있고**
그것은 게이트웨이를 스크레이프하도록 설정돼 있다 ⇒ 내가 401 로 막힌 바로 그 자격을 **그쪽이 갖고
있다.** 다음 창에서:

```bash
docker exec wms-prometheus sh -lc \
  "wget -qO- 'http://localhost:9090/api/v1/query?query=gateway_jwt_audience_total'"
# 기대: {"status":"success","data":{"result":[ … {gateway,outcome} … ]}}
#  🔴 result:[] 는 «불일치 0» 이 아니라 «메트릭이 아직 한 번도 발행되지 않았다» 다.
#     그 둘을 가르려면 outcome="match" 시계열이 있어야 한다 — 그것이 이 티켓의 분모다.
```

🔴 **그 질의가 빈 결과를 내면 결론은 «섀도가 관측 불가능하게 출하됐다» 이고, 그것은 이 티켓이
아니라 새 티켓(계측기를 노출하는 일)이다.** 「불일치 0」으로 읽고 ENFORCE 로 넘기지 마라 —
`TASK-MONO-696` 2단계의 전환 조건은 **실측**이지 «빨간 게 안 보인다» 가 아니다.

---

# ⚪ 2026-09-26 UTC 16차 AMI 창 — 정정 ③ 의 탐침을 돌렸다: **섀도는 관측 불가능하게 출하됐다** → `TASK-MONO-736`

트래픽: 소유자가 콘솔(`demo-corp`) · web-store · fan 웹에 로그인. 그 뒤(16:1xZ) 도메인 prometheus 에 질의:

| prometheus | `gateway-service` 타깃 | `gateway_jwt_audience_total` · `{__name__=~"gateway_jwt.*"}` |
|---|---|---|
| `ecommerce-prometheus` | **down — `server returned HTTP status 401 Unauthorized`** | `result: []` |
| `wms-prometheus` | **down** (연결 실패) | `result: []` |
| `iam-prometheus` | **down** (연결 실패) | `result: []` |

게이트웨이 7개 `JWT audience not on allowlist` WARN 은 전부 **0줄** — 정정 ③ 대로 분모가 없어 판정이 아니다.
⇒ 정정 ③ 의 마지막 문장 그대로다: *«그 질의가 빈 결과를 내면 결론은 «섀도가 관측 불가능하게 출하됐다» 이고, 그것은 이 티켓이 아니라 새 티켓(계측기를 노출하는 일)이다.»*
→ 새 티켓 **`TASK-MONO-736`** 기안. **AC-0 은 여전히 열려 있다 — 이 티켓은 `ready/` 에 그대로.**

## 정정 ④ (2026-09-29 UTC) — AC-0 을 읽는 채널이 생겼다: 게이트웨이 로그의 **요약 줄** (`TASK-MONO-736` AC-0 ②, 소유자 결정)

`TASK-MONO-736` 이 prometheus 스크레이프를 고치는 대신(①) 검증기 자신이 로그로 보고하게 했다(②). 공유 `AllowedAudiencesValidator`(`libs/java-security`)가 토큰이 들어오는 동안 **최대 1분에 한 번**, 기동 이후 **누적** 값으로 한 줄을 낸다:

```
JWT audience summary: gateway=<g> mode=SHADOW match=<n> mismatch=<m>
```

- **분자** = 마지막 줄의 `mismatch` · **분모** = 같은 줄의 `match`. 한 줄에 둘 다 있으므로 «분모는 로그로는 영영 못 센다»(정정 ③)가 해소된다.
- 🔴 **줄이 없으면 «검사 0회» 다 — «불일치 0» 이 아니다.** 판정 술어: 게이트웨이마다 마지막 요약 줄이 **있고** `match > 0` **이고** `mismatch = 0`. 분모 0 = 미측정(위 AC-0 그대로).
- 🔴 누적은 **기동 이후** 다 — 컨테이너가 재시작되면 0 부터 다시 센다. 창에서 트래픽을 돈 **뒤에** 읽고, 재시작 여부(`docker ps` 의 Up 시간)를 같이 기록한다.
- 읽는 법(데모 인스턴스, 게이트웨이마다): `docker logs <gateway-container> 2>&1 | grep "JWT audience summary" | tail -1`
- 이 채널은 **재굽기된 AMI 에서만** 존재한다(검증기는 앱 소스 — `infra/demo/aws/README.md` 배포 층). 736 머지 이후 커밋으로 구운 AMI 인지 먼저 확인한다.
- 위 AC-0 본문(prometheus·WARN 줄 수)은 역사 기록으로 둔다. 이 절이 읽는 법의 현재판이다.

---

## 측정 표본 (2026-10-02 UTC, 18차 창 — `TASK-MONO-736` AC-2 채널) — AC-0 의 ① 데모 스택 칸 한 표본

10:06:30Z, 각 게이트웨이 컨테이너 로그(기동 09:23–09:26Z 이후 누적). 트래픽 = 콘솔 5 도메인 화면 · 스토어·팬 로그인.

| 게이트웨이 | match | mismatch(요약 줄) | `JWT audience not on allowlist` WARN |
|---|---|---|---|
| ecommerce | 181 | 0 | 0 |
| fan | 69 | 0 | 0 |
| scm | 60 | 0 | 0 |
| erp | 29 | 0 | 0 |
| wms | 26 | 0 | 0 |
| finance | 1 | 0 | 0 |

- 분모가 전부 > 0 → 이 창의 «0» 은 공허하지 않다. 🔴 단 **한 창 · 한 표본**이고 finance 분모는 1 이다. AC-0 의 나머지 — `/actuator/prometheus` 경로 · client 별 ⚪ 칸(`TASK-MONO-696` § AC-1 (b)) 판정 — 는 이 표본으로 채워지지 않는다. 이 티켓은 여전히 보류다.

# 표본 — 19차 창 (2026-10-04 UTC · 인스턴스 i-08d452973ebf789be · AMI ami-00815e1f9614cda90 · 커밋 2a49dfb48)

소유자가 콘솔(5 도메인 화면 · 알림 · 팬 디렉터리 시도)·웹스토어·팬 웹에 로그인해 트래픽을 낸 뒤, 게이트웨이마다 `docker logs <gw> | grep "JWT audience summary" | tail -1`(SSM 읽기):

| 게이트웨이 | match | mismatch | 마지막 요약(UTC) | 요약 줄 수 |
|---|---|---|---|---|
| ecommerce | 63 | 0 | 06:59:54 | 5 |
| erp | 89 | 0 | 06:52:13 | 9 |
| fan | 11 | 0 | 07:00:43 | 4 |
| finance | 1 | 0 | 06:22:19 | 1 |
| scm | 42 | 0 | 06:38:11 | 6 |
| wms | 19 | 0 | 06:38:11 | 6 |

⇒ 6/6 `match>0 · mismatch=0`. 18차에 이은 **두 번째 창**의 표본이다. 🔴 AC-0 은 아직 참이 아니다: 696 §AC-1(b) 의 ⚪ 미측정 client 6개를 하나씩 판정하는 일이 남아 있다(이번 창은 그 판정을 하지 않았다). finance 는 표본이 1건뿐이다(콘솔 Finance 카드가 `MISSING_PREREQUISITE` 로 호출 자체를 안 해서 트래픽이 적다).

---

# AC-0 판정 — 2026-10-04 UTC (코드 전수 · CI 채널 · 소유자 결정)

> 데모 없이 했다. 읽은 트리 = `origin/main` `eebeda112`(worktree `wt-697`). CI 런 = 같은 커밋의 `ci.yml` 런 `37186784071`(2026-10-04T07:45:57Z), nightly 런 `37185330969`(2026-10-04T07:17:24Z, `ce3c59ebf`). 분석=Opus 5.5.

## 소유자 결정 (2026-10-04 UTC) — 받은 그대로 적는다

| 항목 | 결정 |
|---|---|
| AC-1 오류 코드 이름 | **`AUDIENCE_FORBIDDEN` 확정.** 추천안을 골랐다(대안이던 `PERMISSION_DENIED` 재사용은 택하지 않음). |
| 뒤집기 시점 | **ENFORCE 뒤집기는 다음 AMI 재굽기에 `TASK-FAN-BE-050` 과 함께 싣는다. AC-5 는 그 데모 창에서 확인한다.** |

- AC-1 집행: `platform/contracts/jwt-standard-claims.md` § Error Handling 의 «not yet fixed … (proposal: `AUDIENCE_FORBIDDEN` …)» 문장을 확정 문장으로 바꿨고, Change log 에 2026-10-04 항목을 추가했다(문구만 바꿨다. 상태코드·규칙·클레임은 그대로다. 프로젝트 이름과 client id 는 넣지 않았다 — HARDSTOP-03). 이름이 그대로라 `GatewayErrorCodes` 값·`GatewayErrorCodesTest` 핀·enforce 테스트·`iam-integration.md`·architecture.md 는 **바꿀 것이 없다**.
- 🔴 **남은 한 줄:** `libs/java-gateway/src/main/java/com/example/apigateway/security/GatewayErrorCodes.java:57-58` Javadoc 의 «Still a proposal» 은 이 PR 에서 지우지 않았다. 이 PR 은 측정·기록만 하고 코드는 건드리지 않는다. 그 줄은 뒤집기 PR(AC-2)에서 함께 지운다. 그 사이에는 Javadoc 이 계약서보다 한 단계 뒤처진다.

## 1. ⚪ client 6개 — 코드 전수 판정

**찾아본 곳.** 다음 범위에서 client id 문자열 6개를 grep 했다: 저장소 전체에서 `*.md` 를 뺀 것, `**/src/main/**`, 모든 `*.yml`, `.env*`, `*.ts`/`*.tsx`, `*.sh`. 토큰을 실제로 얻는 쪽도 따로 찾았다: `/oauth2/token`, `grant_type=client_credentials`, `AuthorizationGrantType.CLIENT_CREDENTIALS`, `client-id:`/`CLIENT_ID`, `OIDC_INTERNAL_CLIENT*`/`internal-client`, `TokenProvider`/`OAuth2AuthorizedClientManager`/`setBearerAuth`/`"Bearer "`. 범위는 `projects/*/apps` · `projects/*/web` · `infra/**` · `projects/*/docker-compose*.yml` · `.github/workflows/*.yml` · `tests/**` 다.
🔴 grep 의 부재는 증명이 아니다. 아래 (i) 판정의 뜻은 «이 트리에 **설정된** 호출자가 없다» 이다. 수동 curl 이나 저장소 밖 클라이언트는 이 방법으로 잡히지 않는다.

**토큰을 얻는 컴포넌트 전수.** 출처는 `/oauth2/token` · `client-id:` grep 이다:

- ecommerce: `batch-worker`(`ecommerce-internal-services-client`, `batch-worker/src/main/resources/application.yml:89-90`), `product-service`(`product-service-client`, `application.yml:74-75`), web-store(`ecommerce-web-store-client`, `docker-compose.yml:1317`), shipping(외부 Delivery Tracker).
- fan: `community-service`(`community-service-client`, `application.yml:138-139`), `artist-service`(`artist-service-client`, `application.yml:125-126`), fan-platform-web(`fan-platform-user-flow-client`, `docker-compose.yml:336`).
- iam: auth/account/security/admin 각자의 `*-service-client`.
- console-web: `platform-console-web`(`projects/platform-console/docker-compose.yml:56`).
- 데모 시드: `infra/demo/seed/lib.sh` 의 `user_token`·`operator_token`. 쓰는 client 는 `platform-console-web` · `ecommerce-web-store-client` · `fan-platform-user-flow-client` 뿐이다(`seed-*.sh`).

🔵 **wms · scm · erp · finance 의 `src/main` 에서 토큰을 얻는 코드는 0건이다** (`INTERNAL_CLIENT|internal-client|internal_client` 0건, `TokenProvider|…|"Bearer "` 는 erp 1건). 그 1건은 `erp-platform/apps/approval-service/.../MasterDataRestAdapter.java:93,125` 이고, **호출자의 토큰을 그대로** `masterdata-service:8080` 에 직접 넘긴다. 게이트웨이를 안 거치고, 이 6 client 도 아니다.

| client | IdP 등록 (grant) | 토큰을 얻는 쪽 | 판정 | ENFORCE 영향 |
|---|---|---|---|---|
| `ecommerce-admin-dashboard-client` | `V0012__seed_ecommerce_oidc_clients.sql:79-116` — `authorization_code`·`refresh_token`, 콜백 `localhost:3001`·`admin.ecommerce.local`(`:110`). 그 뒤 손댄 마이그레이션은 콜백·로그아웃 URI 만 바꿨다(`V0016:150-158`, `V0024:36-41`). | **없다.** 앱 `apps/admin-dashboard` 는 `TASK-MONO-259`(`705f74acc`, #1561)에서 은퇴했다. `infra/demo/verify-demo-wrapper.sh:2777-2778` 에도 «IAM 시드에 콜백이 남아 있으나 그 앱은 제거됐고» 라고 적혀 있다. 이 id 를 담은 env 키나 설정 기본값은 0건이다. | **(i)** | 이 토큰은 `aud=ecommerce-admin-dashboard-client` 를 싣는다. ecommerce allowlist(`platform-console-web,ecommerce-web-store-client,artist-service-client` — `ecommerce…/gateway-service/src/main/resources/application.yml:272`)에 없으므로 ENFORCE 에서 403 이 된다. 다만 **이 토큰을 만드는 호출자가 없다.** |
| `wms-user-flow-client` | `V0010__seed_wms_oauth_clients_and_scopes.sql:27-64` — `authorization_code`·`refresh_token`(cc 아님; `WorkloadRoleCatalog.java:128-130` 도 같은 정정을 적었다), 콜백은 자리표시자 `http://localhost:9001/callback`(`:58`, 주석 `:31-32` «until the wms web app declares its real callback»). | **없다.** wms 웹 앱이 저장소에 없다(`projects/wms-platform/apps` 에 web 없음, `projects/*/web` 은 fan 뿐). `localhost:9001` 을 쓰는 곳도 V0010 뿐이다. | **(i)** | `aud=wms-user-flow-client` 는 wms allowlist(`platform-console-web` — `wms…/application.yml:168`)에 없다. 호출자가 없다. |
| `wms-internal-services-client` | `V0010:66-104` — `client_credentials`. 주석 `:69-72` 에 «Currently wms internal calls are Kafka-based — pre-registering». `WorkloadRoleCatalog.java:147` 이 role 을 준다. | **없다.** wms `src/main` 의 토큰 획득 코드는 0건이고, `AdminSettingsConsumer.java:28` 에 «documented-but-unbuilt `wms-internal-services-client`» 라고 적혀 있다. `infra/demo/seed/lib.sh:124-132` 의 `client_token()` 은 **정의만 있고 호출하는 곳이 없다**(`client_token\b` grep = 정의 1건). 남은 것은 **수동 1회 탐침 기록**뿐이다(`infra/demo/wms-devseed.override.yml:23-24`, `POST /api/v1/master/warehouses` → 403). 설정된 트래픽이 아니다. | **(i)** | ENFORCE 에서 이 토큰은 wms 게이트웨이에서 403 `AUDIENCE_FORBIDDEN` 이 된다. 위 수동 탐침은 원래도 master-service 의 role 인가에서 403 이었으므로(같은 파일 `:25-26`) 사용자가 보는 결과는 그대로다. |
| `scm-platform-internal-services-client` | `V0013__seed_scm_oidc_clients.sql:37-74` — `client_credentials`, `scm.read scm.write`. | **없다.** `projects/scm-platform/docker-compose.yml:54-55` 가 이 id/secret 을 **scm 게이트웨이 컨테이너** env(`OIDC_INTERNAL_CLIENT_ID`)로 넘긴다. 그러나 scm `src/main` 에서 그 키를 읽는 코드는 0건이다(게이트웨이는 릴라잉 파티라 토큰을 발급하지 않는다). `.env.example:20-29` 는 그 값의 예시일 뿐이다. `procurement-service` `V5__…sql:9` · `PurchaseOrder.java:66` 은 그런 토큰을 **받는 쪽**의 컬럼 폭을 다룬 주석이다. | **(i)** | `aud=scm-platform-internal-services-client` 는 scm allowlist(`platform-console-web` — `scm…/application.yml:169`)에 없다. 호출자가 없다. 이 id 를 민팅하는 것은 테스트 픽스처뿐이다(아래 ③, AC-4). |
| `erp-platform-internal-services-client` | `V0018__seed_erp_oidc_client.sql:42-79` — `client_credentials`, `erp.read erp.write`. `V0023:9` 이 `erp.write` 를 준다. `WorkloadRoleCatalog.java:212` 의 role 은 빈 값이다. | **없다.** `projects/erp-platform/.env.example:20-30` 에만 있다. compose 는 이 값을 넘기지도 않는다(`OIDC_INTERNAL_CLIENT` grep 에서 erp compose 0건). | **(i)** | 허용 목록(`erp…/application.yml:152`) 밖이고 호출자가 없다. |
| `finance-platform-internal-services-client` | `V0017__seed_finance_oidc_client.sql:40-77` — `client_credentials`, `finance.read finance.write`. `WorkloadRoleCatalog.java:213`. | **없다.** `projects/finance-platform/.env.example:20-30` 에만 있다. compose 전달도 0건이다. | **(i)** | 허용 목록(`finance…/application.yml:127`) 밖이고 호출자가 없다. |

⇒ **6/6 (i).** (ii)와 (iii)은 없다. 따라서 **allowlist 에 추가할지 정해야 하는 client 는 없다.** 이 판정은 `TASK-MONO-696` § AC-1 (b)의 ⚪ 칸을 «안 온다» 쪽으로 바꾼다. 근거는 «측정 기간 동안 도달 0» 이 아니라 «이 트리에 호출자가 없다» 이다. 데모 창 두 번(18차·19차)의 6/6 `mismatch=0` 과도 맞는다. 다만 데모 창은 client 별로 나눠 세지 않으므로, 두 근거는 **독립적인 두 측정**이다.
🔵 Edge Case «새 client 등록»: 6 client 가 전부 «등록만 되고 호출자가 없는» 상태였다는 사실 자체가 IdP 시드 ↔ allowlist 대조 가드를 둘 이유다. 그런 가드가 있으면 ENFORCE 뒤에 누가 이 client 를 실제로 쓰기 시작하는 순간을 잡는다. 구현은 별도 티켓이다(이 티켓 Out of Scope).

## 2. 채널 ② — nightly-e2e

- **게이트웨이 컨테이너 로그를 아티팩트로 남기는 잡은 없다.** `.github/workflows/nightly-e2e.yml` 의 `upload-artifact` 는 넷이다: Playwright 리포트(`:728-735`, `:866-872`, `:1415-1423`)와 Gradle 리포트(`:1058-1064`, `:1144-1150`). `_platform-e2e.yml:210-213` 은 `report-paths` 만 올린다. 게이트웨이 로그는 **실패할 때만** 스텝 출력으로 찍힌다(`:657` 헬스 실패, `:1393-1400`). 런 `37185330969` 의 아티팩트 4개(`platform-console-playwright-report-nightly`, boot-jars 2개, `playwright-report-fullstack-nightly`)에도 로그는 없다. ⇒ **측정 공백이다.**
- 🔵 대신 **잡 로그**(`gh run view 37185330969 --log`, 38,981줄)를 grep 했다. Testcontainers 가 컨테이너 stdout 을 흘리는 스위트만 이렇게 보인다:

| 스위트 | 게이트웨이 stdout 이 로그에 오나 | `not on allowlist` | 요약 줄 |
|---|---|---|---|
| scm e2e full | **온다** — `ScmPlatformE2ETestBase.java:334` `withLogConsumer(scm-e2e.gateway)` | **67** (전부 `gateway=scm mode=SHADOW aud=[]`) | 6 (전부 `match=0 mismatch=1`) |
| fan e2e full | **안 온다.** 게이트웨이에 log consumer 가 없고 실패 시에만 덤프한다(`FanPlatformE2ETestBase.java:644`) | 0 — **미측정** | 0 |
| wms gateway e2e full | `:projects:wms-platform:apps:gateway-service:e2eFullTest FROM-CACHE` — **돌지 않았다** | 0 — 미측정 | 0 |
| ecommerce · iam e2e full | `FROM-CACHE` | 0 — 미측정 | 0 |
| web-store · console fullstack (compose) | 성공 시 로그 덤프가 없다 | 0 — 미측정 | 0 |
| (참고) federation-hardening nightly `37157024978` | 컨테이너 로그가 없다 | 0 — 미측정 | 0 |

- 🔴 **scm e2e 의 67줄은 운영 토큰이 아니다.** `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/testsupport/JwtTestHelper.java:69-92` 의 `signToken` 은 **`aud` 를 아예 넣지 않는다.** `TASK-MONO-696` AC-5 는 게이트웨이 모듈의 헬퍼 6개만 고쳤고 `tests/e2e` 헬퍼는 범위 밖이었다. `projects/fan-platform/tests/e2e/.../JwtTestHelper.java:70-126` 도 같은 모양이다(`aud` 없음). fan 스위트는 게이트웨이를 경유하므로(`ArtistAndPostFlowE2ETest.java:130` 이하 `gatewayBaseUri()`) 같은 불일치를 냈을 것이다. 다만 로그가 안 남아 **센 값은 없다.**

## 3. 채널 ③ — CI 통합·e2e 잡 (`ci.yml` 런 `37186784071`, `eebeda112`)

🔴 `gh run list --limit 6` 은 9월 29일 런을 맨 위에 보여 줬다. `--limit 100` 으로 다시 받아 `createdAt` 으로 정렬해서 최신 런을 골랐다(알려진 함정). 잡 로그는 잡마다 `gh run view --job <id> --log` 로 받았다.

| 게이트웨이 | 잡 (job id) | 게이트웨이 테스트가 실제로 돌았나 | `not on allowlist` | 마지막 요약 | 줄의 출처 |
|---|---|---|---|---|---|
| ecommerce | Integration (ecommerce C) `111390306404` | `gateway-service:integrationTest` **실행** | 0 | 없음 | 🔴 **유효하지 않음** — 테스트 프로필 logback 이 `Appender named [CONSOLE] not referenced` 이다. 배너 뒤로 앱 로그가 한 줄도 콘솔에 안 나온다 ⇒ 0 은 **미측정**이다 |
| wms | Integration (inventory + inbound + gateway-service) `111390306296` · E2E gateway-master smoke `111390562950` | **`FROM-CACHE` 둘 다** — 돌지 않았다. 직전 실행 런 `37101381053` 도 `FROM-CACHE` 였다 | 0 | 없음 | 미측정 |
| scm | Integration (scm) `111390306258` | 실행 | **1** — `aud=[scm-platform-internal-services-client]` | `match=1 mismatch=0` (불일치 앞에 찍힌 줄) | 픽스처 `scm…/gateway-service/src/test/.../JwtTestHelper.java:120-126` ← `GatewayBootstrapIntegrationTest.java:44-54` (AC-4 에 이미 있는 셋) |
| scm | E2E scm smoke `111390527562` | 실행 | **16** — 전부 `aud=[]` | `match=0 mismatch=1` ×4 | 픽스처 `scm-platform/tests/e2e/.../JwtTestHelper.java:69-92` (**AC-4 목록 밖, 새로 찾음**) |
| erp | Integration (erp) `111390306218` | 실행 | **1** — `aud=[erp-internal-client]` | `match=1 mismatch=0` | 픽스처 `erp…/JwtTestHelper.java:109-114` ← `GatewayRoutingIntegrationTest.java:42`. 🔵 `erp-internal-client` 는 **IdP 에 등록되지도 않은** id 다 |
| finance | Integration (finance) `111390306315` | 실행 | **1** — `aud=[machine-client]` | `match=1 mismatch=0` | 픽스처 `finance…/JwtTestHelper.java:111-116` ← `GatewayEdgeIntegrationTest.java:77`. `machine-client` 도 등록되지 않은 id 다 |
| fan | Integration (fan) `111390306273` · E2E fan smoke `111390515052` | IT 실행 / smoke 는 게이트웨이 stdout 이 없다 | 0 | IT `match=1 mismatch=0` | IT 는 깨끗하다. smoke 는 미측정(위 ② fan 과 같은 이유) |

⇒ **채널 ③의 불일치 줄은 전부 테스트 픽스처에서 나왔고, 운영 client 에서 나온 줄은 0이다.** 줄마다 민팅한 헬퍼까지 추적했다. 그래도 «③에 불일치 줄이 없다» 는 **거짓**이다. 그리고 ecommerce·wms 는 이 채널에서 **재지 못했다.**

## 4. 롤백 레버 — `OIDC_AUDIENCE_MODE` 는 컨테이너에 전달되지 않는다

- `AUDIENCE_MODE|ALLOWED_AUDIENCES` 를 `*.md` 와 `src/**` 밖의 모든 파일에서 grep 하면 **0건**이다. 대상에는 `projects/*/docker-compose*.yml`, `infra/demo/*.override.yml`·`demo.env`, `.github/workflows/*` 가 모두 들어간다. 게이트웨이 서비스에는 `env_file:` 도 없다(compose·`infra/**` 에서 `env_file` 은 `provision-demo-env.sh` 의 셸 변수뿐). 예: scm 게이트웨이 `environment:` 블록 `projects/scm-platform/docker-compose.yml:48-59`.
- ⇒ **데모 호스트의 `.env`/`demo.env` 에 `OIDC_AUDIENCE_MODE=SHADOW` 를 넣어도 게이트웨이에는 도달하지 않는다.** env 하나로 하는 롤백은 **지금은 없다.**
- 재굽기 없이 할 수 있는 비상 롤백은 하나다. 인스턴스 안에서 6 게이트웨이의 compose `environment:` 에 `OIDC_AUDIENCE_MODE: SHADOW` 줄을 넣고 `docker compose up -d gateway-service` 를 한다. 이미지는 그대로다(`infra/demo/aws/README.md:118-119` 의 «compose 의 값 하나 … 인스턴스 안에서 그 줄을 고치고 `docker compose up -d`»). 이것은 굽힌 트리와 어긋나는 **대역 밖 수정**이다. 정식 경로는 PR 과 재굽기다(`README.md:100` — compose 는 AMI 층).
- 🔵 **권고(뒤집기 PR 범위):** 6 게이트웨이 compose `environment:` 에 `OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}` 전달 줄을 넣는다. 그러면 롤백은 `demo.env` 한 줄과 재기동이 된다. `TASK-MONO-696` § 데모/compose env 가 경고한 «generic 이름을 전역으로 정의하면 6개가 한 값을 공유» 는 **mode 에서는 오히려 원하는 성질이다**(AC-2: 6개를 함께). 같은 일을 `OIDC_ALLOWED_AUDIENCES` 에 하면 안 된다 — 그쪽은 게이트웨이마다 값이 다르다. 🔴 이 줄을 넣으면 `AudienceShippedConfigTest$Shipped` ③(«compose 에 ENFORCE override 줄 0»)에 걸린다. 뒤집기 PR 에서 그 단언도 함께 바꿔야 한다.

## 5. AC-4 — 픽스처 셋과 따라 나오는 선택지

| 픽스처 | 민팅하는 `aud` | 쓰는 IT | 운영 호출자 |
|---|---|---|---|
| scm `gateway-service/src/test/.../JwtTestHelper.java:120-126` `signClientCredentialsToken()` | `scm-platform-internal-services-client` | `GatewayBootstrapIntegrationTest.java:44-54` (+ `JwtHeaderEnrichmentFilterTest`, `JwtTestHelperTest:43` — 디코더를 거치지 않는 단위 칸) | 없다 (§ 1, (i)) |
| erp `…/JwtTestHelper.java:109-114` `signClientCredentialsToken(clientId)` | 인자 = `erp-internal-client`(미등록 id) | `GatewayRoutingIntegrationTest.java:42` | 없다 |
| finance `…/JwtTestHelper.java:111-116` `signScopeOnlyToken(subject)` | 인자 = `machine-client`(미등록 id) | `GatewayEdgeIntegrationTest.java:77` | 없다 |

⇒ **(b) 해당 IT 의 기대값을 403 `AUDIENCE_FORBIDDEN` 으로 바꾼다.** 6/6 (i)이므로 allowlist 에 넣을 운영 호출자가 없고, (a)는 없는 호출자를 위해 엣지를 넓히는 일이 된다. 🔴 (b)의 대가도 있다. 이 세 칸은 rule-6 admission 의 «role OR scope» 중 **scope 다리가 200 을 낸다는 유일한 IT 증인**이다. 403 으로 뒤집으면 그 증인이 사라진다. 뒤집기 PR 에서 scope 다리의 증인을 따로 남길 것: 그 칸만 테스트 한정 SHADOW override 로 돌리거나, admission 단위 테스트에 둔다. 픽스처를 allowlist 값으로 바꾸는 방법은 AC-4 가 금지한다.
🔴 **AC-4 목록 밖에서 새로 찾은 것 — `tests/e2e` 헬퍼 둘.** `scm-platform/tests/e2e/.../JwtTestHelper.java:69-92` 와 `fan-platform/tests/e2e/.../JwtTestHelper.java:70-126` 은 `aud` 가 **없다.** ENFORCE 가 되면 이 헬퍼로 게이트웨이를 부르는 칸이 전부 403 이 된다: scm smoke(`ci.yml`, scm 변경 시) · scm/fan e2e full(nightly) · fan smoke. 이 둘은 위 셋과 성격이 다르다. 사람 사용자 토큰(BUYER·OPERATOR·FAN)이고, 운영에서는 실제로 `aud` 를 싣는다: scm 은 콘솔 경유 `platform-console-web`, fan 은 `fan-platform-user-flow-client`. 그러므로 운영과 같은 `aud` 를 넣는 것이 `TASK-MONO-696` AC-5 원칙(«헬퍼는 운영과 같은 `aud` 를 민팅»)을 따르는 수정이다. AC-4 가 금지하는 «allowlist 값으로 초록 만들기» 와는 다르다.

## 판정 — AC-0 은 `[ ]` 로 둔다

AC-0 을 닫는 조건은 «client 6/6 이 (i)/(ii)» **그리고** «채널 ②③에 불일치 줄 0» **그리고** 데모 창 표본이다. 첫째와 셋째는 참이다. **둘째는 거짓이다**(③ 에 scm 1+16 · erp 1 · finance 1줄, ② 에 scm 67줄). 줄마다 출처를 픽스처로 추적했다는 것은 내 판정이다. 이것을 AC-0 의 «불일치 0» 으로 읽어도 되는지는 이 티켓이 정하지 않았다.

**소유자 결정 필요 (이것만 남았다):**

1. **AC-0 술어 해석** — 채널 ②③의 불일치 줄 중 **테스트 픽스처가 민팅했다고 추적된 줄**은 AC-0 의 분자에서 뺄지. 🔵 추천: 뺀다. AC-0 이 지키려는 것은 «운영 토큰이 403 이 되지 않는다» 이고, 운영 경로 측정(데모 2창 6/6 `mismatch=0`)과 코드 전수(6/6 (i))가 둘 다 그것을 말한다. 픽스처 줄은 AC-4 와 아래 2번이 처리한다. 대신 «② ecommerce·wms·fan 미측정, ③ ecommerce·wms 미측정» 을 공백으로 남겨 둔다.
2. **범위 확장 확인** — `scm`·`fan` `tests/e2e` 헬퍼에 운영 `aud` 를 넣는 일을 뒤집기 PR(AC-4 확장)에 넣을지. 🔵 추천: 넣는다. 안 넣으면 뒤집기 PR 머지 직후 nightly 와 scm 변경 PR 의 e2e 가 빨개진다.

(allowlist 에 client 를 추가할지 정할 일은 **없다** — § 1.)

🔵 이 PR 에서 하지 않은 것: ENFORCE 뒤집기(AC-2), 테스트 기대값(AC-3/4), `GatewayErrorCodes` Javadoc, compose 전달 줄. 전부 다음 AMI 재굽기에 실릴 뒤집기 PR 의 일이다(소유자 결정 «`TASK-FAN-BE-050` 과 함께»).

🔵 위 «`[ ]` 로 둔다» 절은 결정 전 기록으로 그대로 둔다. 아래가 그 결정이다.

## 소유자 결정 2 (2026-10-04 UTC) — 세 건 모두 추천안. 받은 그대로 적는다

| # | 결정 (원문) | 반영 위치 |
|---|---|---|
| 1 | «CI 로그의 불일치 줄은 전부 테스트 픽스처 → 판정에서 제외하고 AC-0 을 닫는다.» | AC-0 `[x]` — 근거 요약·제외한 줄 목록·측정 공백 목록을 AC-0 항목에 적었다 |
| 2 | «전환 PR 에서 scm·fan `tests/e2e` 헬퍼도 운영과 같은 `aud` 를 민팅하도록 함께 고친다.» | AC-4 본문 확장: (b) 403 기대 + e2e 헬퍼 수정 + scope 다리 증명 보존 요건 |
| 3 | «각 게이트웨이 compose 에 `OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}` 를 넘기는 줄을 전환 PR 에서 추가한다(재굽기 없는 되돌리기 레버).» | AC-2 본문(전달 줄 + `AudienceShippedConfigTest$Shipped` ③ 술어 변경), AC-5 본문(레버 확인) |

⇒ **AC-0 · AC-1 닫힘.** 이 티켓은 **뒤집기 PR 을 받을 준비가 됐다**(AC-2~AC-5). 그 PR 은 다음 AMI 재굽기에 `TASK-FAN-BE-050` 과 함께 싣는다. ⏳ 머리말의 «DO NOT START — AC-0 이 참이 되기 전에는» 조건은 이제 충족됐다.

---

# 뒤집기 PR 기록 (2026-10-04 UTC)

> 브랜치 `feat/mono-697-enforce`(base `origin/main` `8a31e5ef5`). 소유자 결정 1·2·3 과 AC-2~4 본문을 글자 그대로 따랐다. 어긋난 곳은 맨 아래 § 티켓 문구와 다른 점에 따로 적었다. 분석=Opus 5.5 / 구현=Opus 5.5.

## AC-2 — 6 게이트웨이 ENFORCE 출하 + 되돌리기 레버

**출하값.** 여섯 `application.yml` 의 `audience-mode` 를 `${OIDC_AUDIENCE_MODE:ENFORCE}` 로 바꿨다. 같은 블록 주석은 «SHADOW 가 출하값» 문장을 «ENFORCE 출하, SHADOW 는 env 로 되돌리는 수단» 으로 고쳤다. allowlist 주석에 있던 «섀도에서 세어진다» 문장도 «ENFORCE 에서는 403, 추가는 리뷰를 거치는 allowlist 변경» 으로 고쳤다. `allowed-audiences` 값은 하나도 바꾸지 않았다.

**compose 전달 줄 (소유자 결정 3).** 여섯 게이트웨이 서비스의 `environment:` 에 넣었다. 파일은 데모가 실제로 쓰는 것이다(`infra/demo/projects.sh`).

| 게이트웨이 | 파일 | 형태 |
|---|---|---|
| ecommerce | `projects/ecommerce-microservices-platform/docker-compose.yml` `gateway-service` | `- OIDC_AUDIENCE_MODE=${OIDC_AUDIENCE_MODE:-ENFORCE}` (이 블록은 리스트 형식이다) |
| wms | `projects/wms-platform/docker-compose.e2e.yml` `gateway-service` | `OIDC_AUDIENCE_MODE: ${OIDC_AUDIENCE_MODE:-ENFORCE}` |
| scm | `projects/scm-platform/docker-compose.yml` `gateway-service` | 같음 |
| erp | `projects/erp-platform/docker-compose.yml` `gateway-service` | 같음 |
| finance | `projects/finance-platform/docker-compose.yml` `gateway-service` | 같음 |
| fan | `projects/fan-platform/docker-compose.yml` `gateway-service` | 같음 |

- wms 는 줄을 공용 앵커(`x-wms-oidc-env`)가 아니라 **게이트웨이 블록에 직접** 넣었다. 앵커에 넣으면 wms 서비스 전부가 이 변수를 받는다.
- `OIDC_ALLOWED_AUDIENCES` 는 넘기지 않았다. 각 줄 위 주석에 그 이유도 적었다.

**술어 교체 (`AudienceShippedConfigTest$Shipped` ③).** 공유 픽스처 `libs/java-gateway/src/testFixtures/.../ShippedAudienceConfig.java` 를 바꿨다.

- `enforceOverrides` 를 지우고 `modeOverrides(projectDir)` 를 넣었다. compose 에서는 전달 줄 형태(맵·리스트)만 허용한다. 고정 `SHADOW`, 고정 `ENFORCE`, 다른 기본값의 전달 줄은 전부 적발한다. `.env*` 에서는 `ENFORCE` 가 아닌 대입(곧 `SHADOW` 고정)을 적발한다. 주석 줄은 건너뛴다.
- `servicesPassingModeThrough(composeFile)` 를 새로 넣었다. compose 를 YAML 로 파싱하고(merge key 포함) `environment` 의 `OIDC_AUDIENCE_MODE` 가 정확히 전달 줄인 서비스 이름을 돌려준다. 주석 처리된 줄이나 엉뚱한 서비스 아래의 줄은 세지 않는다.
- 게이트웨이마다 `$Shipped` 칸은 넷이 됐다.
  - `shipsEnforce` — `shipsShadow` 에서 바꿨다.
  - `shipsMeasuredAllowlist` — 그대로다.
  - `deploymentFilesOnlyPassTheModeThrough` — 바꾼 술어 ③.
  - `runningComposePassesTheModeThroughToTheGateway` — **전달 줄 존재 단언**이다. `containsExactly("gateway-service")`.
- 🔴 «전달 줄이 6/6 있다» 는 **게이트웨이 여섯 스위트가 각자 자기 파일을 단언한 합**이다. 한 모듈이 다른 프로젝트 파일을 읽으면 Gradle 입력과 PR 경로 필터에서 안 보이게 된다(`TASK-MONO-695`). 그래서 이 방법을 골랐다. 프로젝트 compose 는 이미 각 게이트웨이 `test` 태스크의 입력으로 선언돼 있다(`build.gradle` `audienceModeDeploymentFiles`).
- 공유 픽스처의 단위 테스트는 `libs/java-gateway/src/test/.../testfixtures/ShippedAudienceConfigTest.java` 다(8칸). 적발해야 하는 칸 다섯이 bite 다. 게이트웨이 스위트는 깨끗한 자기 파일만 보므로, 술어가 아무것도 못 잡게 되면 이 칸들만 그것을 드러낸다.
- **bite 실측(scm):** compose 줄을 `OIDC_AUDIENCE_MODE: SHADOW` 로 바꾸고 `:projects:scm-platform:apps:gateway-service:test --tests '*AudienceShippedConfigTest*'` 를 돌렸다 → **rc=1**. ③ `deploymentFilesOnlyPassTheModeThrough` 와 ④ `runningComposePassesTheModeThroughToTheGateway` **둘 다 FAILED** 였다. 원복한 뒤 `:check` → rc=0.

## AC-3 — 1단계 섀도 칸의 뒤집기

- ecommerce `SecurityConfigRealDecoderPathTest`:
  - `$AudienceShadowed` 를 `$AudienceEnforcedAsShipped` 로 바꿨다(6칸).
  - (i) `noAudience_ecommerceTenant_isRejected_andCounted` 와 (ii) `foreignAudience_ecommerceTenant_isRejected_andCounted` 는 이제 **403 `AUDIENCE_FORBIDDEN` + `mismatch_rejected` +1** 이고 `mismatch_shadowed` 는 0 이다.
  - 대조군 `noAudience_control_withoutTenant_is403` · `foreignAudience_control_withoutTenant_is403` 은 그대로 `TENANT_FORBIDDEN` 이고 audience 카운터는 0 이다.
  - (iii) 허용 aud 칸은 200 + match +1 이다.
- wms `SecurityConfigRealDecoderPathTest` 도 같은 모양이다. `noAudience_wmsTenant_isRejected_andCounted` · `foreignAudience_isRejected_andCounted` 가 403 이고, 대조군과 콘솔 aud 200 칸은 남겼다.
- 🔵 두 스위트 모두 디코더를 만들 때 쓰는 mode 를 **리터럴 `"SHADOW"` 대신 `ShippedAudienceConfig.shippedValue("<prefix>.oauth2.audience-mode")`** 로 읽는다. 티켓 문장(«출하 모드를 측정한다»)을 코드가 그대로 따르게 하려는 것이다. 칸 `decoderIsBuiltWithTheShippedEnforceMode` 가 그 전제를 단언한다.
- 두 `SecurityConfigAudienceEnforceRealDecoderPathTest` 는 그대로 `"ENFORCE"` 리터럴을 쓴다. 출하값이 언젠가 다시 바뀌어도 ENFORCE 동작 행렬을 계속 재게 하려는 것이다. Javadoc 의 «ENFORCE 로 출하하지 않는다» 문장만 고쳤다.

## AC-4 — 픽스처 셋 (b) · scope 다리 증명 · e2e 헬퍼

**(b) 집행 — IT 기대값을 403 `AUDIENCE_FORBIDDEN` 로 바꿨다.** 픽스처의 `aud` 는 하나도 바꾸지 않았다.

| 게이트웨이 | 칸 (새 이름) | 픽스처 |
|---|---|---|
| scm | `GatewayBootstrapIntegrationTest#clientCredentialsTokenIsRejectedWith403AudienceForbidden` | `signClientCredentialsToken()` 그대로(`aud=scm-platform-internal-services-client`) |
| erp | `GatewayRoutingIntegrationTest#clientCredentialsTokenOutsideTheAudienceAllowlistIsRejectedWith403` | `signClientCredentialsToken("erp-internal-client")` 그대로 |
| finance | `GatewayEdgeIntegrationTest#scopeOnlyMachineTokenOutsideTheAudienceAllowlistIsRejectedWith403` | `signScopeOnlyToken("machine-client")` 그대로 |

- scm·finance 칸이 넣던 `downstream.enqueue(200)` 를 뺐다. 403 은 엣지에서 끝나므로 큐에 남은 응답이 공유 MockWebServer 의 **다음 칸**을 오염시킨다.
- 세 헬퍼 Javadoc 의 «shadow-mode mismatch» 주석을 «ENFORCE 에서 403 — allowlist 값으로 바꿔 초록 만들기 금지» 로 고쳤다.

**scope 다리 증명 — admission 단위 테스트로 옮겼다(티켓의 둘째 방법).** 출하 `GatewayIdentityConfig#roleAdmissionFilter` 빈으로 만든 필터에 scope 만 있는 토큰을 넣어 통과를 단언한다. 디코더 아래라 audience 게이트가 적용되지 않는다.

| 게이트웨이 | 증인 칸 |
|---|---|
| scm | **새 파일** `projects/scm-platform/apps/gateway-service/src/test/java/com/example/scmplatform/gateway/filter/RoleAdmissionFilterTest.java` `#admitsScopeOnlyMachineToken`. 대조군 `#rejectsNoRoleNoScopeWith403` 이 짝이다. scm 에는 이 파일이 없었다 |
| erp | 기존 `projects/erp-platform/apps/gateway-service/src/test/java/com/example/erp/gateway/filter/RoleAdmissionFilterTest.java#admitsScopeOnlyMachineToken` |
| finance | 기존 `projects/finance-platform/apps/gateway-service/src/test/java/com/example/finance/gateway/filter/RoleAdmissionFilterTest.java#admitsScopeOnlyMachineToken` |

- 셋 다 로컬 `:check` 에서 4/4 통과했다.
- 테스트 한정 SHADOW override 방법은 고르지 않았다. 그러려면 Testcontainers 컨텍스트가 하나 더 필요하고, 이 호스트에서는 돌려 볼 수 없다.
- 🔴 이 대체로 잃는 것이 하나 있다. 이제 «scope 만 있는 토큰이 **게이트웨이 HTTP 사슬 전체**를 200 으로 통과한다» 를 재는 IT 칸은 없다. 운영에도 그런 토큰은 없다. allowlist 에 든 `aud` 를 가진 scope-only 토큰을 IdP 가 발급하지 않기 때문이다.

**e2e 헬퍼 (소유자 결정 2).** 대상 `aud` 는 각 게이트웨이 출하 allowlist 에서 확인했다. scm 은 `platform-console-web` 이고(`scm…/application.yml` `allowed-audiences`), fan 은 `fan-platform-user-flow-client,platform-console-web` 이다.

| 헬퍼 | 변경 |
|---|---|
| `projects/scm-platform/tests/e2e/src/test/java/com/example/scmplatform/e2e/testsupport/JwtTestHelper.java` | `DEFAULT_AUDIENCE = "platform-console-web"`. `signToken` 이 `.audience(List.of(DEFAULT_AUDIENCE))` 를 민팅한다. 추가 클레임 `"aud"` 로 덮을 수 있다 |
| `projects/fan-platform/tests/e2e/src/test/java/com/example/fanplatform/e2e/testsupport/JwtTestHelper.java` | `DEFAULT_AUDIENCE = "fan-platform-user-flow-client"`. 같은 방식이다 |

- 두 스위트 모두 토큰을 편의 메서드로만 만든다(`signToken` 직접 호출 0건, grep). 그러니 사람 토큰 전부에 적용된다. 워크로드 모양 토큰은 두 헬퍼에 없다.
- 컴파일: `:projects:scm-platform:tests:e2e:compileTestJava` **rc=0**, `:projects:fan-platform:tests:e2e:compileTestJava` **rc=0**. 스위트 실행은 Docker 가 필요해서 못 했다. CI(scm smoke `ci.yml` · nightly full)가 권위다.

**그 밖의 토큰 경로를 조사했다(지시받은 항목).** 결론은 **고칠 것 없음**이다.

- `tests/federation-hardening-e2e`:
  - 토큰을 직접 만들지 않는다. `fixtures/login.ts` 는 실제 SAS PKCE 로그인이다(«no programmatic token mint»).
  - 게이트웨이로 가는 것은 콘솔의 base/assumed 토큰이다. 이 토큰의 `aud` 는 `platform-console-web` 이다. assumed 쪽은 `AssumeTenantExchangeIntegrationTest` AC-6 이 핀으로 고정한다(fan `application.yml` 주석).
  - 그 스택의 `scm-gateway-service` 는 소스에서 빌드되고 allowlist 기본값이 `platform-console-web` 이다.
  - `console_operator_token` 은 `/api/admin/**`(iam 게이트웨이, 범위 밖)로만 간다.
- nightly web-store fullstack 두 잡(`nightly-e2e.yml` `frontend-e2e-fullstack` 과 lean sibling)은 실제 IdP 로그인을 쓰고 `ECOMMERCE_WEB_STORE_CLIENT_ID: ecommerce-web-store-client` 를 쓴다(`:702`, `:833`). allowlist 안이다.
- platform-console fullstack 은 `console-web/tests/e2e/fixtures/login.ts` 를 쓴다. 실제 SAS 로그인이고 `aud=platform-console-web` 이다.
- wms 게이트웨이 `src/e2eTest` 는 `signWmsOperatorToken` 을 쓰고 이미 `aud=platform-console-web` 이다. 컴파일 `:projects:wms-platform:apps:gateway-service:compileE2eTestJava` **rc=0**.
- ecommerce·iam `tests/e2e` 는 domain 게이트웨이 토큰을 만들지 않는다(iam 게이트웨이는 범위 밖이다, `TASK-MONO-698`).
- ⚪ 위는 **코드를 읽어서 낸 판정**이다. 이 스위트들을 ENFORCE 로 실제로 돌려 보지는 않았다. 첫 nightly 가 판정한다.

## 공유 lib · 스펙

- `GatewayErrorCodes.AUDIENCE_FORBIDDEN` Javadoc 의 «Still a proposal» 문단을 확정 문구로 바꿨다(§ AC-0 판정의 «남은 한 줄»). `GatewayErrorCodesTest` 주석도 같이 고쳤다. 값과 핀은 그대로다.
- `libs/java-gateway/build.gradle`: `testFixturesImplementation 'org.yaml:snakeyaml'` 을 넣었다(새 YAML 파싱용, Boot BOM 관리). HARDSTOP-03: 공유 파일에 프로젝트 이름·서비스 이름·client id 를 넣지 않았다(픽스처 테스트의 서비스 이름은 `edge`·`a-map` 같은 합성값이다).
- 스펙 동기화. 문장만 고쳤고 규칙은 바꾸지 않았다.
  - `platform/contracts/jwt-standard-claims.md` rule 5 «Implementation status» 를 2026-10-04 판으로 고쳤다. 내용은 «여섯 게이트웨이 rejection 출하 · 섀도는 환경 override 로 되돌리는 수단» 이다. Change log 에 AC-2 항목을 넣었다. 프로젝트 이름은 넣지 않았다.
  - ecommerce gateway `architecture.md` · `overview.md` · `dependencies.md` · `iam-integration.md`(설정 예시 · 4단계 설명 · Error Responses 행)를 고쳤다.
  - wms·scm gateway `overview.md` 와 erp·finance·fan·scm gateway `architecture.md` 의 «ships **SHADOW**» 문장을 ENFORCE 로 바꿨다.

## 검증 (로컬, Windows · Git Bash — 각 명령 단독 실행, 파일로 리다이렉트, `rc=$?` 직접 출력)

| 명령 | rc |
|---|---|
| `./gradlew :libs:java-gateway:check` | **0** (`ShippedAudienceConfigTest$ModeOverrides` 6/6 · `$PassThrough` 2/2) |
| `./gradlew :projects:ecommerce-microservices-platform:apps:gateway-service:check` | **0** (`$AudienceEnforcedAsShipped` 6/6 · `AudienceShippedConfigTest$Shipped` 4/4) |
| `./gradlew :projects:wms-platform:apps:gateway-service:check` | **0** (`SecurityConfigRealDecoderPathTest` 5/5 · `$Shipped` 4/4) |
| `./gradlew :projects:scm-platform:apps:gateway-service:check` | **0** (bite 원복 뒤 재실행도 0 · `RoleAdmissionFilterTest` 4/4) |
| `./gradlew :projects:erp-platform:apps:gateway-service:check` | **0** |
| `./gradlew :projects:finance-platform:apps:gateway-service:check` | **0** |
| `./gradlew :projects:fan-platform:apps:gateway-service:check` | **0** |

- 각 로그에서 `:test` 태스크가 **실행됐는지**(캐시 아님) 확인했다.
- 🔴 **Testcontainers 통합 테스트(`@Tag("integration")`, 위 AC-4 의 세 IT 칸 포함)는 로컬에서 돌지 않았다.** 이 호스트에는 Docker 가 없다. `:check` 는 그 칸들을 **컴파일만** 했다. 403 기대가 맞는지는 **CI 통합 잡이 권위**다. e2e(scm smoke · scm/fan full · federation · fullstack)도 같다.

## 티켓 문구와 다른 점

1. ecommerce compose 의 `environment:` 는 리스트 형식이라, 전달 줄을 `- OIDC_AUDIENCE_MODE=${OIDC_AUDIENCE_MODE:-ENFORCE}` 로 썼다. 의미는 같고, 술어가 두 형태를 모두 인정한다.
2. «전달 줄 6/6» 은 한 테스트가 여섯 파일을 세는 방식이 아니다. 여섯 스위트가 각자 자기 파일을 단언한다(이유는 AC-2 절).
3. 공유 픽스처 메서드 `enforceOverrides` 를 지우고 `modeOverrides` + `servicesPassingModeThrough` 를 넣었다. 그에 맞춰 lib 단위 테스트와 `snakeyaml` testFixtures 의존을 새로 추가했다.
4. 티켓 In Scope 목록에 없는 **스펙 문장 동기화**(계약서 Implementation status 와 프로젝트 gateway 스펙들)를 같은 PR 에서 했다. 스펙이 «SHADOW 출하» 라고 말하는 채로 코드만 ENFORCE 가 되면 스펙과 코드가 충돌하기 때문이다.
5. AC-3 칸의 디코더 mode 를 출하값에서 읽게 했다(리터럴 대신).

---

## CORRECTION (2026-10-05 UTC) — 20차 창 판정 (2026-10-04 UTC · i-0c4859442f56d70e0 · ami-0d78d476824493d77 · f0927bcd0)

> 위 본문의 «AC-5 `[ ]` — 데모 창 확인이 남아 있어 열려 있다» 와 Definition of Done 의 `[ ]` 두 칸은 **이제 사실이 아니다.** 체크박스는 고치지 않고(동결 파일 — 덧붙이기만) 여기서 닫는다. 이 절이 현재 상태다. 분석=Opus 5.5.

**창.** 20차 AMI 창, 2026-10-04 UTC 14:5x–15:49. 인스턴스 `i-0c4859442f56d70e0` · AMI `ami-0d78d476824493d77` · 구운 커밋 `f0927bcd0`(핀 PR #4151, provenance `ami-tag`). 뒤집기 PR #4143 의 머지 `c32e56e7b` 는 `f0927bcd0` 의 조상이다(`git merge-base --is-ancestor` 참) ⇒ 이 창의 게이트웨이는 ENFORCE 출하본이다.

### AC-5 — 항목별

| AC-5 요구 | 판정 | 근거 |
|---|---|---|
| `:libs:java-gateway:check` + 6 게이트웨이 `:check` rc=0 | ✅ | § 뒤집기 PR 기록 › 검증 (7개 rc=0, 2026-10-04) |
| 통합 잡(Testcontainers)은 CI 가 권위 | ✅ | #4143 `statusCheckRollup` 68건 = SUCCESS 43 · SKIPPED 25 · **FAILURE 0** |
| 마지막 요약 줄 6/6 `mode=ENFORCE` · `mismatch=0` | ✅ | 아래 표 (~15:43 UTC, SSM 읽기) |
| 콘솔 5 도메인 · web-store · fan 각 1회 200 | ✅ | 콘솔 5 도메인 화면 200(overview 15:36:07 의 leg 들 ok — scm 은 그 순간 `TASK-MONO-758` 대조군으로 **일부러 내려 둠**, 재기동 15:38 뒤 scm 화면 200 · health 라우트는 그 전에 전 도메인 ok) · web-store 로그인 성공 · fan 웹 로그인 성공 · 콘솔 팬 디렉터리(fan 게이트웨이) 목록 표시 |
| `mismatch_rejected` 0 | ✅ | 6 게이트웨이 모두 `grep -ciE "AUDIENCE_FORBIDDEN\|audience not on allowlist\|mismatch_rejected"` = **0** |
| 되돌리기 레버 (최소) — env 가 컨테이너에 도달 | ✅ | 6/6 `docker exec <gw> printenv OIDC_AUDIENCE_MODE` = `ENFORCE`(SSM 읽기). 🔵 AC 문구의 `docker inspect … Config.Env` 대신 **실행 중 프로세스 환경**을 읽었다 — 같은 사실의 더 직접적인 관측이다 |
| 되돌리기 레버 (가능하면) — SHADOW 로 재생성 후 `mode=SHADOW` 확인 | ⚪ **안 했다** | 데모 호스트 원격 쓰기(컨테이너 재생성)라 이번 창에서는 하지 않았다. AC 가 «가능하면» 으로 둔 칸이므로 닫힘을 막지 않는다. 🔴 그러므로 «레버를 당기면 실제로 SHADOW 가 된다» 는 **미측정**이다 — 아는 것은 «env 가 컨테이너에 도달한다» 와 출하 스위트의 전달 줄 단언(`runningComposePassesTheModeThroughToTheGateway`)까지다 |

**마지막 요약 줄 (~15:43 UTC, 소유자 트래픽 뒤 — 콘솔 5 도메인 · scm 재기동 뒤 재방문 · web-store 로그인 · fan 웹 로그인 · 콘솔 팬 디렉터리)**

| 게이트웨이 | mode | match | mismatch | 거절 grep |
|---|---|---|---|---|
| ecommerce | ENFORCE | 288 | 0 | 0 |
| erp | ENFORCE | 83 | 0 | 0 |
| fan | ENFORCE | 33 | 0 | 0 |
| finance | ENFORCE | 46 | 0 | 0 |
| scm | ENFORCE | 38 | 0 | 0 |
| wms | ENFORCE | 36 | 0 | 0 |

- 🔴 scm 은 15:38 에 재기동됐다(758 대조군) ⇒ 그 줄의 누적은 **재기동 이후분**만이다(정정 ④ 의 «누적은 기동 이후»). 분모 38 > 0 이므로 공허하지 않다.
- 🔵 finance 분모가 처음으로 1 을 넘었다(18·19차는 1). 
- 🔵 ecommerce 의 match 에는 `artist-service-client` 워크로드 토큰(`TASK-MONO-759` 의 `/internal/sellers/default` 조회, 15:27:22Z)도 들어 있다 — 그 줄에서 요약이 121→122, mismatch 0.

### 4차원 (close chore)

| 차원 | 결과 |
|---|---|
| (a) `gh pr view 4143` | `state=MERGED` · mergedAt 2026-10-04T09:13:16Z · mergeCommit `c32e56e7b` |
| (b) origin/main 조상 | `git merge-base --is-ancestor c32e56e7b origin/main` 참 (origin/main = `92a6320eb`) |
| (c) 머지 시점 실패 체크 | `statusCheckRollup` FAILURE/CANCELLED/TIMED_OUT **0** (68건 중 SUCCESS 43 · SKIPPED 25) |
| (d) `# Acceptance Criteria` | AC-0 · AC-1 · AC-2 · AC-3 · AC-4 `[x]`(본문) · **AC-5 = 이 절에서 닫힘**. AC-5 의 동사는 «확인» 이고, 필수 칸은 전부 관측으로 확인했다. 선택 칸(SHADOW 재생성)은 «가능하면» 이라 ⚪ 로 기록했다. DoD 두 칸(AC-0~5 · 출하 ENFORCE + 오류 코드 확정)도 참이다 |

⇒ **`review/` → `done/`.**
