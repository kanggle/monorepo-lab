# Task ID

TASK-MONO-508

# Title

콘솔 org-hierarchy 레그가 IAM admin API 에서 401 `TOKEN_INVALID` 로 죽는다 — operator 토큰이 수락되지 않는다

# Status

ready

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

- [ ] **AC-0 (착수 = 재측정)** — 위 실측을 그대로 믿지 않는다. 슬라이스를 다시 띄워 401 을
      재현하고, **검증기 쪽 로그로** 거부 지점을 특정한다. 서명 키 도메인 가설은 확인되기
      전까지 가설이다
- [ ] **AC-1** — 원인을 한 문장으로 적을 수 있다(어느 컴포넌트가 무엇을 왜 거부하는가)
- [ ] **AC-2** — 수정 후 콘솔에서 조직 계층 섹션이 **그 경로에서** 200 으로 렌더된다
      (바운스 금지 — 술어를 `finalPath` 동일성으로 둘 것. `MONO-505` 에서 느슨한 술어가
      바운스를 통과시킨 전례가 있다)
- [ ] **AC-3** — `권한 없음` / degraded 텍스트가 없다. **마커는 컴포넌트에서 복사**한다
      (`degraded` 문자열은 `data-testid` 에만 나오는 오탐이다 — 실측 확인됨)
- [ ] **AC-4 (형제 파리티)** — operator 토큰을 쓰는 다른 IAM admin 레그를 전수로 세고,
      되는 것/안 되는 것을 기록한다. **선행 숫자를 물려받지 말고 다시 셀 것**
- [ ] **AC-5** — 재발 방지 수단을 추가하고 **네거티브 테스트로 무는 것을 확인**한다
- [ ] **AC-6** — `verify-demo-wrapper.sh` 전체 + CI green

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

- [ ] 원인 규명 + 수정
- [ ] 형제 레그 파리티 확인 기록
- [ ] 실주행 증거 기록
- [ ] Ready for review
