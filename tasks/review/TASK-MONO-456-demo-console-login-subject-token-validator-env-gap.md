# TASK-MONO-456 — 통합 데모가 admin-service subject-token validator env(`OIDC_ISSUER`/`OIDC_JWKS_URI`)를 주입하지 않아 콘솔 로그인이 셸에 도달하지 못한다

**Status:** review

**Type:** TASK-MONO
**Analysis model:** Opus 4.8 / **Recommended impl model:** Sonnet 4.6 (데모 env 2줄 주입 + 재현 검증. 다만 AC-0 의 "실제로 깨져 있나 / AMI 가 딴 데서 세팅하나" 재측정이 실질)

> 2026-07-21 콘솔 로그인 라이브 검증 중 발견. `infra/demo` compose 체인을 서브셋 수동 기동해 OIDC PKCE 로그인을 끝까지 구동하다, **operator-token 교환 단계에서 막혀** 콘솔 셸(`/dashboards/overview`)에 도달하지 못하는 것을 재현했다. 원인은 데모 토폴로지가 admin-service subject-token validator 가 읽는 env 두 개를 주입하지 않는 것.

---

## Goal

콘솔 로그인은 `console.local/api/auth/login` → SAS `/oauth2/authorize` → `/login` 폼 → code → `console.local/api/auth/callback` → **operator-token 교환(RFC 8693, admin-service)** → 콘솔 셸 순이다. 마지막 교환이 admin-service 의 subject-token 검증에서 실패한다.

admin-service 의 subject-token validator([`IamOidcJwksSubjectTokenValidator`](../../projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/infrastructure/security/IamOidcJwksSubjectTokenValidator.java))는 **전용 env 두 개**를 읽는다([application.yml](../../projects/iam-platform/apps/admin-service/src/main/resources/application.yml)):

```yaml
oidc:
  jwks-uri: ${OIDC_JWKS_URI:http://localhost:8081/internal/auth/jwks}   # line 127
  issuer:   ${OIDC_ISSUER:http://localhost:8081}                        # line 129
  audience: ${OIDC_CONSOLE_CLIENT_ID:platform-console-web}              # line 132
```

**데모가 실제로 쓰는 4개 compose/env 파일 어디에도 `OIDC_ISSUER`·`OIDC_JWKS_URI`·`OIDC_CONSOLE_CLIENT_ID` 가 없다** (전수 grep 확인):

- `projects/iam-platform/docker-compose.yml`
- `projects/iam-platform/docker-compose.e2e.yml`
- `infra/demo/iam-traefik.override.yml`
- `infra/demo/demo.env`

데모/override 는 `OIDC_ISSUER_URL`(= resource-server jwt, application.yml line 181)과 `OIDC_JWK_SET_URI`(override 의 `*iam-oidc` 앵커)만 설정한다. **validator 가 읽는 이름과 다르다.** 그 결과 validator 는 둘 다 `localhost:8081` 기본값으로 폴백한다:

1. `OIDC_JWKS_URI` → `http://localhost:8081/internal/auth/jwks` — admin-service 컨테이너의 `localhost:8081` 엔 아무것도 없어 **JWKS fetch 실패**(fail-closed). 로그: `GAP OIDC JWKS fetch failed (fail-closed): I/O error on GET "http://localhost:8081/internal/auth/jwks"`.
2. `OIDC_ISSUER` → `http://localhost:8081` — 토큰의 `iss=http://iam.local` 과 불일치 → `.requireIssuer()` 실패 → **401 `TOKEN_INVALID` "Subject token verification failed"**.

(audience 는 기본값 `platform-console-web` 이 우연히 토큰 aud 와 일치해 문제 없음 — 그래서 셋 중 두 개만 터진다.)

### 콘솔 측 관측 결과 (콘솔 코드는 정상 — fail-closed 설계대로 동작)

- JWKS fetch 실패 상태: 교환 401(fail_closed) → 콜백이 `/onboarding` 으로 (not_provisioned 경로).
- gateway 재시작 직후(circuit open): 교환 503 → 콜백이 `/login?error=operator_exchange_unavailable`.
- **두 env 주입 후: 교환 200(operator 토큰 발급) → `/dashboards/overview` 200, `Platform Console` 셸 렌더 — 로그인 성공.**

즉 **콘솔 로그인 코드에는 결함이 없다.** 이 티켓은 순수 **데모 토폴로지 env 배선** 문제다.

### 이게 왜 "healthy but unusable" 의 재발인가

`iam-traefik.override.yml` 헤더 스스로가 경고한다: *"96 컨테이너가 전부 healthy 로 떠도 로그인은 불가능하다 — healthy 는 usable 을 뜻하지 않는다."* 그 override 는 브라우저 OIDC 도달성(issuer/authorize/login 라우팅)은 고쳤지만, **operator-token 교환의 subject-token 검증 배선은 빠졌다.** 같은 개념(admin 이 신뢰하는 IAM issuer/JWKS)을 두 설정 이름으로 나눠 한쪽(`OIDC_ISSUER_URL`/`OIDC_JWK_SET_URI` = 리소스서버)만 배선하고 다른 쪽(`OIDC_ISSUER`/`OIDC_JWKS_URI` = subject-token validator)은 빠뜨린 **straggler** 다.

---

## Scope

**In scope**

1. 데모 토폴로지에 `OIDC_ISSUER` + `OIDC_JWKS_URI`(+ 필요시 `OIDC_CONSOLE_CLIENT_ID`)를 admin-service 로 주입한다. 후보 위치:
   - `infra/demo/iam-traefik.override.yml` 의 `admin-service.environment` (가장 국소적 — 이미 admin-service 를 만지는 파일).
   - 또는 `infra/demo/demo.env` (cross-project env 의 정경 위치, 다른 OIDC 값들과 같은 자리).
   - 값: `OIDC_JWKS_URI=http://auth-service:8081/internal/auth/jwks`, `OIDC_ISSUER=http://iam.${DEMO_DOMAIN:-local}` (issuer 는 토큰 iss 와 문자열 일치해야 하므로 공개 호스트명; JWKS 는 컨테이너 DNS fetch).
2. 주입 후 콘솔 로그인이 **셸까지** 도달함을 재현 검증(문서/PR 본문에 근거).

**Out of scope**

- **admin-service application.yml 변경 0** — 기본값 `localhost:8081` 은 로컬 단독 실행용으로 옳다. 문제는 데모가 override 하지 않는 것이지 기본값이 틀린 게 아니다.
- 콘솔 코드 변경 0 — 콜백/교환/가드 전부 정상.
- operator 시드(`oidc_subject`) 정합은 별개 이슈(§ Notes) — 이 티켓은 env 배선만.

---

## Acceptance Criteria

- **AC-0 (gate — 재측정, "정문"과 화해하라)** — 착수 시 다음을 실측 재확인한다. **이 티켓은 서브셋 수동 기동 + 혼용 시드로 재현했으므로, 표준 경로에서 진짜 깨지는지부터 확인한다:**
  1. `bash infra/demo/demo-up.sh demo-core`(또는 실제 콘솔 데모 기동 경로)로 띄운 뒤, `e2e-super-admin` 류 시드 operator 로 로그인해 **셸 도달 여부**를 본다. `/onboarding` 또는 `/login?error=operator_exchange_unavailable` 로 튕기면 재현 확정.
  2. **AWS AMI 경로 확인** — 메모리(`project_ondemand_demo_aws_poc`)는 AWS 데모 "✅정문"을 기록한다. 그 "정문"이 **로그인 폼 도달**인지 **콘솔 셸 도달**인지, 그리고 AMI user-data/packer(`infra/demo/aws/`)가 이 env 를 **딴 데서** 세팅하는지 확인한다. AMI 가 세팅한다면 갭은 로컬 `demo-up.sh` 한정이다.
  3. 이미 해소돼 있으면(누가 override 에 이미 넣었으면) phantom 으로 기록하고 종료.
- **AC-1** — 데모 토폴로지가 admin-service 에 `OIDC_ISSUER` + `OIDC_JWKS_URI` 를 주입한다(auth-service 로 해소). 콘솔 로그인이 셸까지 도달한다.
- **AC-2** — `verify-demo-wrapper.sh` 가드에 이 배선을 지키는 스모크가 있으면 갱신/추가 검토(브라우저 OIDC 도달성 가드는 있으나 operator-교환 도달성 가드는 없을 수 있다 — healthy≠usable 재발 방지).
- **AC-3** — 주입 근거(왜 issuer 는 공개 호스트명, JWKS 는 컨테이너 DNS 인지 — `demo.env` 의 issuer↔jwks 분리 원칙과 동일)를 주석/PR 에 남긴다.

---

## Related Specs / Contracts

- `projects/iam-platform/specs/services/admin-service/security.md` § GAP OIDC Subject-Token Validation (validator 계약의 정경)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.6 (operator-token 교환 소비자 계약)
- `infra/demo/iam-traefik.override.yml` 헤더(브라우저 OIDC 도달성을 고친 선행 작업 — 이 티켓은 그 자매편)
- 계약 변경 0 — 순수 데모 배선.

---

## Edge Cases

1. **issuer 문자열 일치** — `OIDC_ISSUER` 는 토큰 `iss` 와 **문자열 비교**된다. `DEMO_DOMAIN` 이 sslip.io 면 `http://iam.<ip>.sslip.io` 여야 한다(`OIDC_ISSUER_URL`/`IAM_PUBLIC_URL` 과 동일 파생). 하드코딩 `iam.local` 금지.
2. **JWKS 는 fetch 대상** — `OIDC_JWKS_URI` 는 컨테이너 DNS(`auth-service:8081` 또는 alias `iam-auth-service:8081`)여야 한다. 공개 호스트명으로 두면 AWS IGW hairpin 부재로 죽는다(`demo.env` 가 `IAM_JWKS_URL` 에서 이미 다루는 원칙).
3. **`/internal/auth/jwks` vs `/oauth2/jwks`** — subject-token validator 는 **`/internal/auth/jwks`** 를 쓴다(리소스서버 jwt 의 `/oauth2/jwks` 와 다른 엔드포인트지만 이 토폴로지에선 동일 kid `key-2026-04-01` 서명). 경로를 헷갈리지 말 것.

---

## Failure Scenarios

- **F1 — issuer 만 넣고 JWKS 를 빠뜨린다(또는 반대).** 둘 다 터진다(하나는 fetch 실패, 하나는 iss 불일치). 세트로 주입.
- **F2 — `OIDC_ISSUER_URL` 을 고치면 될 거라 착각.** validator 는 그 이름을 안 읽는다. **`OIDC_ISSUER`(언더스코어 URL 없음)** 가 정확한 이름이다 — 대리지표 금지, 실제 소비 env 를 물어라.
- **F3 — 로그인 폼 도달까지만 보고 "고쳤다".** 이 갭은 **폼 이후**(operator 교환)에서 터진다. 반드시 **셸 도달**(`/dashboards/overview` 200 + operator 쿠키)까지 확인.

---

## Definition of Done

- [x] AC-0 재측정 (표준 경로 조립증명 + AWS AMI 경로 정적 확인 — § 구현 노트)
- [x] subject-token validator env 데모 주입 — **`ADMIN_OIDC_ISSUER` + `ADMIN_OIDC_JWKS_URI`** (티켓의 `OIDC_ISSUER`/`OIDC_JWKS_URI` 대신 sibling-parity 채택, 근거 § 구현 노트)
- [~] 콘솔 로그인 셸 도달 — **console-e2e CI 가 동일 배선의 권위 증명** + 정적 render 증명. 로컬 라이브 드라이브는 호스트 안전상 유보(동시 세션 컨테이너 가동 중)
- [x] (검토→구현) verify-demo-wrapper 가드 (v) 추가 (operator-교환 도달성, mutation 으로 물림 증명)
- [x] 주입 근거 기록 (override 주석 + § 구현 노트)

---

## Notes

- **분량**: small(env 2줄) + 재현 검증이 실질.
- **자매 발견(별개 티켓 아님, 참고만)**: 검증 중 seed operator 의 `admin_operators.oidc_subject` 가 토큰 `sub`(account_id)와 불일치하는 것도 봤으나, 이는 `scripts/console-demo/seed/01-iam.sql`(시드 SQL) 을 `infra/demo` 기동에 **혼용**한 아티팩트일 수 있다(두 데모 시스템의 시드 규약 차이). 표준 경로 재현 시 이 부분도 함께 관찰할 것 — 단, `OperatorOidcSubjectResolver` 주석대로 `oidc_subject`=account_id 정합은 별도 backfill(TASK-MONO-298) 이 이미 다룬 영역이다.
- **이 task 가 방어하는 실패 모드**: **"같은 신뢰 앵커(IAM issuer/JWKS)를 두 설정 이름으로 나눠, 브라우저 도달성만 배선하고 subject-token 검증 배선은 빠뜨렸다."** 컨테이너는 전부 healthy, 로그인 폼도 뜨고, 코드도 옳지만 — operator 교환이 조용히 fail-closed 로 막혀 **아무도 콘솔 셸에 못 들어간다.** (TASK-PC-FE-253 이 콘솔 코드에서 잡은 "같은 개념 두 곳, 하나만 배선" 패턴의 인프라 판.)

---

## 구현 노트 (2026-07-21, 분석·구현 Opus 4.8)

### AC-0 재측정 (gate — 정적으로 화해, 라이브 풀-기동은 호스트 안전상 유보)

착수 시 전수 재측정했고 티켓 본문을 **가설로 취급**했다(범위 물려받기 금지). 호스트가 fork/spawn 스트레스(`docker.exe: Resource temporarily unavailable`) + **동시 세션 컨테이너 ~13개 가동 중**이라, 콘솔 데모 풀-기동(+15컨테이너)은 호스트/동시세션을 위험에 빠뜨리므로 **정적 조립 증명 + console-e2e CI 증명**으로 대체했다(메모리: 로컬 Windows docker 는 권위 아님, CI 가 권위).

1. **갭은 표준 경로에 내재 — subset-artifact 아님**: `infra/demo/projects.sh` 가 조립하는 파일(iam = `docker-compose.yml` + `docker-compose.e2e.yml` + `iam-traefik.override.yml`; console = base 만)을 전수 독해. 데모의 admin-service 는 `docker-compose.e2e.yml`(251-302, `admin.oidc.*` env 전무) + `iam-traefik.override.yml`(`*iam-oidc` = 리소스서버 `OIDC_ISSUER_URL`/`OIDC_JWK_SET_URI` 만) 로 조립되어 **`admin.oidc.*` 를 어디서도 안 받는다** ⇒ validator(`IamOidcProperties`, `@ConfigurationProperties(prefix="admin.oidc")`) 가 `localhost:8081` 기본값 폴백 ⇒ JWKS fetch 실패 + iss(`http://iam.local`) 불일치 → 401. **phantom 아님.**
2. **AWS AMI 경로(AC-0 item 2)**: `infra/demo/aws/`(user-data·packer·terraform) 전수 grep — 이 env 를 **딴 데서도 안 세팅**한다. AMI 는 같은 `demo.env`/compose 를 굽므로 **AWS 데모도 동일 갭**. ⇒ 메모리 `project_ondemand_demo_aws_poc` 의 "✅정문" 은 **콘솔 셸 도달이 아니라 로그인 폼/타 플로우**를 뜻한 것으로 화해.
3. **audience 는 안 터진다**: 기본값 `platform-console-web` 이 우연히 토큰 aud 와 일치(티켓 주장 확인).

### env 이름 결정 — `ADMIN_OIDC_*` (티켓의 `OIDC_ISSUER` 대신)

validator 는 `@ConfigurationProperties(prefix="admin.oidc")` 라, 속성 `admin.oidc.issuer`/`admin.oidc.jwks-uri` 를 채우는 **작동하는 env 이름이 둘**이다:
- `OIDC_ISSUER`/`OIDC_JWKS_URI` — application.yml 의 명시 placeholder `${OIDC_ISSUER:...}` 경유(티켓이 본 것).
- `ADMIN_OIDC_ISSUER`/`ADMIN_OIDC_JWKS_URI` — Spring **relaxed binding**(env source, 우선순위 높음) 경유 — **`platform-console/docker-compose.e2e.yml`(:271-272) 가 이 validator 를 배선하는 정경 이름**.

**후자 채택**: (a) **sibling parity** — 이 validator 가 콘솔 로그인용으로 외부 배선되는 **유일한 다른 자리**(console-e2e)와 이름 일치 ⇒ 드리프트 0, 그리고 그 하네스가 CI 에서 token-exchange 성공→셸 렌더를 이미 증명(=fix 정당성의 라이브 권위). (b) prefix-scoped 라 데모에 산재한 리소스서버 `OIDC_ISSUER_URL`(6곳)과 **비혼동**. (c) `@ConfigurationProperties` 의 idiomatic env 형태. — 티켓 F2("실제 소비 env 를 물어라")는 두 이름 모두 만족(둘 다 대리지표 아닌 실소비 속성 바인딩); 티켓 저자는 relaxed-binding 대안을 못 봤을 뿐. `OIDC_ISSUER` 로도 동작하나 parity 가 더 낫다.

### 변경 산출물 (2파일, 콘솔/application.yml 변경 0)

- `infra/demo/iam-traefik.override.yml` — admin-service.environment 를 `<<: *iam-oidc` merge 로 바꾸고 `ADMIN_OIDC_ISSUER: http://iam.${DEMO_DOMAIN:-local}`(공개 호스트=토큰 iss 문자열 일치) + `ADMIN_OIDC_JWKS_URI: http://auth-service:8081/internal/auth/jwks`(컨테이너 DNS, gateway-service 가 이미 fetch 하는 동일 엔드포인트) 추가. **PyYAML 렌더 검증**: 5키(추가 2 + anchor 3), 값 일치 확인. Edge Case 1(issuer 파생)·2(JWKS fetch 대상)·3(`/internal/auth/jwks`) 준수. F1(세트 주입)·F2(정확 이름) 회피.
- `infra/demo/verify-demo-wrapper.sh` — **가드 (v)** 추가(AC-2): override 가 admin-service subject-token validator env(issuer+jwks)를 배선하는지 정적 단언. (l)이 브라우저-OIDC 도달성(전반부)을 지키듯 operator-교환 도달성(후반부)을 지킨다. **술어는 YAML key 앵커**(`^\s*ADMIN_OIDC_ISSUER:\s`) — env 블록 주석이 `ADMIN_OIDC_*` 를 산문 언급하므로 substring grep 은 대리지표가 된다(커밋될 blob 을 물어야 함). **mutation 증명**: env 라인만 제거·주석 유지 → 가드가 both missing 정확 보고(오탐 없음).

### 미해소·유보

- **로컬 라이브 셸-도달 재현**: 호스트 안전상 유보(위). 안전 창 또는 다음 AWS 데모 기동(`TASK-MONO-399` 계열) 시 `demo-up.sh demo-core` → 시드 operator 로그인 → `/dashboards/overview` 200 확인 권장. console-e2e CI 가 동일 배선의 라이브 권위이므로 결함 잔존 위험은 낮다.
- **자매 발견(§ Notes, 별건)**: seed operator `oidc_subject` 정합은 이 티켓 범위 밖(env 배선만).
