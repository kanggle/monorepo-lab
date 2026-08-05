# Task ID

TASK-MONO-508

# Title

콘솔 org-hierarchy 레그가 IAM admin API 에서 401 `TOKEN_INVALID` 로 죽는다 — operator 토큰이 수락되지 않는다

# Status

done

# Owner

monorepo

# Task Tags

- code
- infra
- demo

---

# 배경 — `TASK-MONO-507` 이 분리해 남긴 것

`MONO-507` 은 "도메인 게이트웨이가 assumed 토큰을 401 로 거부한다" 를 추적해, 원인이
**도메인 백엔드의 JWKS 도달 불가**(fail-closed 가 연결 결함을 인증 판정으로 위장)임을
밝히고 5개 프로젝트 19개 서비스를 고쳤다. 그 뒤 콘솔의 도메인 섹션은 열린다.

**남은 401 은 그것과 다른 층이다.** 콘솔이 뜬 상태에서 계속 나오는 로그:

```
org_nodes_unauthorized  status=401  code=TOKEN_INVALID  path=/api/admin/org-nodes
```

`MONO-507` 과 무엇이 다른가 — 셋 다 다르다:

| | MONO-507 (해결됨) | 이 티켓 |
|---|---|---|
| 대상 | 도메인 게이트웨이 뒤 백엔드 | **IAM admin API** |
| 토큰 | assumed 토큰 (SAS, `kid=key-2026-04-01`) | **operator 토큰** (`kid=v1`) |
| 도달 | 이름이 해소 안 됨 (NXDOMAIN) | **정상 도달** — DNS 무관 |
| 응답 | `401 UNAUTHORIZED "Authentication required"` | `401 TOKEN_INVALID` |

## 실측 (MONO-507 세션, 통합 데모 라이브)

콘솔이 부르는 그대로 상류를 직접 때렸다(`IAM_ADMIN_API_BASE=http://iam.local`):

```
operator 토큰 header: {"kid":"v1","alg":"RS256"}
operator 토큰 claims: {"sub":"demo-operator","iss":"admin-service","token_type":"admin",
                       "jti":"019fd02c-…","iat":1785903995,"exp":1785907595}

GET http://iam.local/api/admin/org-nodes   [operator 토큰]
  401  {"code":"TOKEN_INVALID",
        "message":"Access token is missing, expired, or has an invalid signature"}

GET http://iam.local/api/admin/org-nodes   [assumed 토큰]
  401  {"code":"TOKEN_INVALID","message":"Operator token invalid"}
```

두 토큰 모두 거부되고 **메시지가 서로 다르다** — 즉 검증기는 두 경우를 구분하고 있다.
`www-authenticate` 헤더는 양쪽 모두 없다.

## 눈에 띄는 비대칭 (가설 — 확인 전까지 가설이다)

- operator 토큰은 `kid=v1` / `iss=admin-service` 인 **admin-service 자체 발급 토큰**이고,
  SAS 토큰은 `kid=key-2026-04-01` / `iss=http://iam.local` 이다. **서명 키 도메인이 다르다.**
- 콘솔 로그인 자체는 `operator_exchange_ok` 로 성공한다 — 즉 admin-service 는 이 토큰을
  **발급**했다. 그런데 같은 서비스(또는 그 앞단)가 그것을 **검증**하지 못한다.
- 전환 전/후 모두 401 이다(assume-tenant 와 무관).

**착수 시 증거로 확인할 것.** 어느 컴포넌트가 `TOKEN_INVALID` 를 내는지(iam gateway 인지
admin-service 의 필터인지)부터 로그로 특정하고, 그 검증기가 어떤 키/issuer 를 기대하는지
읽어라. 위 표는 관측이지 결론이 아니다.

---

# Goal

통합 데모에서 `demo@demo.com` 으로 로그인한 운영자가 콘솔의 조직 계층(org-hierarchy)
섹션을 401 없이 연다. 원인이 검증 규칙이면 규칙을, 데모 배선이면 배선을 고친다.

---

# Scope

## In Scope

- `/api/admin/org-nodes` 가 401 `TOKEN_INVALID` 를 내는 **정확한 지점** 규명
- 그 원인에 대한 최소 수정 (검증 배선 / 토큰 발급 / 데모 env 중 하나)
- **형제 파리티** — 콘솔이 operator 토큰으로 부르는 **다른 IAM admin 레그**도 같은 원인인지
  확인하고 결과를 기록한다(`/api/admin/console/registry` 는 `registry_ok` 로 **성공**하고
  있으므로, 같은 토큰인데 되는 레그와 안 되는 레그가 공존한다 — 그 차이가 단서다)
- 재발 방지 가드 또는 테스트

## Out of Scope

- 도메인 백엔드의 JWKS 도달성 — `TASK-MONO-507` 에서 완료
- 콘솔 UI 변경
- 도메인 데이터 시드 — `TASK-MONO-506`

---

# Acceptance Criteria

- [x] **AC-0 (착수 = 재측정)** — 재현했고, 거부 지점을 **메시지 문자열로** 특정했다. 두 401 은
      서로 **다른 필터**였다: `"Access token is missing, expired, or has an invalid signature"` 는
      iam **gateway** 의 `JwtAuthenticationFilter`, `"Operator token invalid"` 는 admin-service 의
      `OperatorAuthenticationFilter`. 즉 operator 토큰은 admin-service 에 **도달조차 못 했다**.
      서명 키 도메인 가설은 **원인이 아니라 결과**였다 — 게이트웨이가 그 토큰을 account JWT 로
      검증하려 든 것이 원인이고, 키가 다른 건 그래서 실패한 이유일 뿐이다.
- [x] **AC-1 (원인 한 문장)** — **iam gateway 의 `public-paths` 가 operator 엔드포인트를 하나씩
      열거하고 있었고 그 목록이 admin-service 표면보다 뒤처져, 열거되지 않은 경로에서
      게이트웨이가 admin-service 서명 토큰을 account JWKS 로 검증하려다 401 을 냈다.**
      `gateway-api.md § Admin Routes` 는 이 서브트리 전체 위임을 **플랫폼 불변식**으로 못 박고
      있으므로 새 결정이 아니라 **설정 드리프트**이며, ADR 을 요구하는 방향은 **열거 쪽**이었다.
- [x] **AC-2** — `finalPath` 동일성 술어로 통과. 10개 화면 전부 `status=200 finalPath==요청경로`,
      바운스 0건. 수정 **전** 실측과 대비된다(아래 실주행 증거).
- [x] **AC-3** — 마커 0건. 마커는 컴포넌트에서 복사한 사람이 읽는 텍스트만 사용
      (`권한 없음` / `일시적으로` / `불러오지 못` / `불러올 수 없` / `연결할 수 없` / `다시 로그인`).
- [x] **AC-4 (형제 파리티)** — **모집단을 다시 세니 org-nodes 만의 문제가 아니었다.** operator
      토큰으로 11개 레그를 전수 측정: **허용목록에 있는 3/3 은 200, 없는 8/8 은 401** — 완전한
      상관관계. 콘솔의 **IAM 관리 섹션 전체**가 죽어 있었고 티켓 이름이 범위를 크게 축소하고
      있었다. 서브트리 위임으로 11/11 이 도달한다.
- [x] **AC-5** — `RouteConfigTest` 를 **실제 `application.yml` 을 읽도록** 바꾸고(손 복사 픽스처
      3개 제거) 서브트리 불변식을 단언한다. 네거티브: yml 을 옛 열거로 되돌리면 **8+ 케이스 RED**,
      복구하면 GREEN. yml 추출 0건도 FAIL 로 둔다.
- [x] **AC-6** — gateway-service `test` 113 / `integrationTest` 35, **실패 0 · 스킵 0**
      (XML 리포트로 확인 — `BUILD SUCCESSFUL` 만으로 판단하지 않았다). CI 는 PR 에서 확인.

---

# Related Specs

> monorepo-level task — `CLAUDE.md` § Required Workflow 의 monorepo-level 경로를 따른다.

- `projects/iam-platform/apps/admin-service/src/main/resources/application.yml`
- `projects/platform-console/apps/console-web/src/app/api/org-nodes/route.ts`
- `projects/platform-console/specs/services/console-bff/architecture.md`
- `infra/demo/README.md` § 신원 평면 (MONO-507 실측 기록)
- `infra/demo/iam-traefik.override.yml` (`ADMIN_OIDC_*` — 가드 (v) 가 지키는 env)

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` — `TOKEN_INVALID` 규약

---

# Target Service

- iam `admin-service` (검증 측) + `platform-console` console-web (호출 측, 원인에 따라)

---

# Edge Cases

- 콘솔은 **두 종류의 토큰**을 동시에 들고 있다(operator / assumed). 어느 레그가 어느 것을
  보내는지부터 확인할 것 — 로그의 401 하나로 토큰을 단정하지 말 것
- `registry_ok` 가 성공한다는 사실이 "operator 토큰은 유효하다" 를 뜻하지는 않는다.
  그 레그가 **같은 검증기를 지나는지** 부터 확인할 것
- 콘솔의 operator 교환은 5s 타임아웃 — 스택 부하 시 false `unavailable`

---

# Failure Scenarios

- **한 레그만 낫는다** — AC-4 (형제 파리티)
- **느슨한 술어로 초록** — 바운스한 페이지에는 나쁜 단어가 없다. AC-2/AC-3
- **로컬만 낫는다** — 로컬은 hosts + Traefik alias 로 관대하다. 가드 (i)(u)(w) 를 함께 볼 것

---

# Test Requirements

- 슬라이스 실기동 재현 → 수정 → 재현 불가 확인
- `verify-demo-wrapper.sh` 전체
- 추가한 가드/테스트의 네거티브 확인

---

# Definition of Done

- [x] 원인 규명 + 수정
- [x] 형제 레그 파리티 확인 기록 (org-nodes 1개가 아니라 8개였다)
- [x] 실주행 증거 기록
- [x] Ready for review

---

# 실주행 증거 — 수정 전/후 (같은 스택, 같은 계정)

**API 레그 (operator 토큰 직접)**

```
                                   before   after
/api/admin/console/registry   [열거]  200      200
/api/admin/audit              [열거]  200      200
/api/admin/accounts           [열거]  200      200
/api/admin/org-nodes                 401      200
/api/admin/me                        401      200
/api/admin/operators                 401      200
/api/admin/operators/grantable-roles 401      200
/api/admin/roles                     401      200
/api/admin/permissions               401      200
/api/admin/groups                    401      200
/api/admin/partnerships              401      403 PERMISSION_DENIED
```

🔵 `partnerships` 의 **401 → 403** 이 위임이 옳게 작동한다는 증거다: 요청이 admin-service 까지
가서 **인증은 통과하고 RBAC 이 거부**했다. 401 = "누구인지 확인 불가"(엣지),
403 = "누구인지 알고, 권한이 없다"(서비스).

**콘솔 화면 (브라우저 경로 — 쿠키로 SSR fetch)**

수정 **전** (게이트웨이를 옛 설정으로 되돌려 실측):

```
7/12 FAILED
/org-hierarchy   307 -> /login 307 -> /console 200   finalPath=/console  bytes=31505
/operators       307 -> /login 307 -> /console 200   finalPath=/console  bytes=31505
/operator-groups 307 -> /login 307 -> /console 200   finalPath=/console  bytes=31505
/permissions     307 -> /login 307 -> /console 200   finalPath=/console  bytes=31505
/permission-sets 307 -> /login 307 -> /console 200   finalPath=/console  bytes=31505
/tenants         307 -> /login 307 -> /console 200   finalPath=/console  bytes=31505
/partnerships    307 -> /login 307 -> /console 200   finalPath=/console  bytes=31505
```

수정 **후**: `ALL 12 CHECKS PASS` — 10개 화면 전부 `finalPath == 요청 경로`, 마커 0건.

🔴 튕긴 7개가 **전부 `status=200` 이고 바이트 수까지 같다**(31,505 = `/console` 페이지).
"200 이면 OK" 나 "나쁜 단어 없으면 OK" 술어는 이걸 통과시킨다 — `MONO-505` 에서 실제로
통과시켰다. `finalPath` 동일성만이 이걸 잡는다.

**고친 뒤 초록은 고치기 전 빨강을 증명하지 않는다** — 그래서 옛 설정으로 되돌려 위 before 를
따로 측정했다(jar 재빌드 + 컨테이너 재배포 2회).
