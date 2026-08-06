# Task ID

TASK-ERP-BE-041

# Title

결재 상신이 **항상** 실패한다 — 주체 조회 내부 호출이 인증 헤더 없이 나가 401 을 받고, 그 401 이 "마스터가 ACTIVE 아님" 으로 번역된다

# Status

ready

# Owner

erp-platform

# Task Tags

- bug
- security
- integration

---

# 배경 — `TASK-MONO-510` 이 데모 시드를 만들다 발견

erp 데모 시드(`infra/demo/seed/seed-erp.sh`)가 실재하는 ACTIVE 부서를 주체로 결재를
상신했는데 **항상** 거절됐다:

```
POST /api/erp/approval/requests/<id>/submit   (운영자 토큰, assume demo-corp)
→ 422 {"code":"APPROVAL_ROUTE_INVALID",
       "message":"subject DEPARTMENT '019fd739-…' does not resolve to an ACTIVE master (E1)",
       "details":{"cause":"subject_unresolved"}}
```

그런데 같은 토큰으로 그 부서를 직접 조회하면 **200 · `status=ACTIVE`** 다. 즉 주체는
실재하고 활성이다.

## 원인 (실측)

`MasterDataRestAdapter.isSubjectActive` 는 masterdata-service 를 **Authorization 헤더
없이** 부른다 — `RestClient.Builder` 에 baseUrl 만 주고, 호출부에 헤더 설정이 없다.
그런데 masterdata-service 는 게이트웨이 뒤의 **독립 OIDC 리소스 서버**다
(`ServiceLevelOAuth2Config`). 컨테이너 안에서 직접 재현:

```
$ docker exec erp-platform-approval sh -c \
    'wget -S -q -O /dev/null http://masterdata-service:8080/api/erp/masterdata/departments/<실재 id>'
  HTTP/1.1 401
```

그리고 어댑터는 `onStatus(HttpStatusCode::is4xxClientError, (req,res) -> {})` 로 4xx 를
**삼킨다**. 주석은 그 분기를 *"a 404 means the subject is absent"* 라고 설명하지만,
술어는 404 가 아니라 **모든 4xx** 다. 그래서 401 이 `envelope == null` → `false` →
*"ACTIVE 아님"* 이 된다.

🔴 **인증 실패가 도메인 판정으로 번역된 것**이 이 결함의 본질이다. 401 은 "주체가 없다"
가 아니라 "물어보지도 못했다" 인데, 두 경우가 같은 값(`false`)으로 접힌다.

🔵 `tenantId` 파라미터를 받아 놓고 **쓰지 않는다**는 점도 같은 자리에 있다 — 서명은
테넌트를 의식하는데 요청은 아무 테넌트도 싣지 않는다.

## 왜 테스트가 잡지 못했나 (착수 시 재확인할 것)

착수자는 **먼저** `MasterDataRestAdapterTest` / approval IT 를 열어 확인하라 — 스텁이
인증 없는 요청에 200 을 주도록 돼 있다면 그것이 이 결함을 통과시킨 이유다. 그렇다면
프로덕션 코드보다 **테스트의 술어**를 먼저 고쳐야 한다(스텁이 401 을 내는 케이스 추가).

# Goal

실재하고 ACTIVE 한 마스터를 주체로 한 상신이 **성공한다.** 그리고 인증 실패는
"주체 없음" 과 **구별되어** 관측된다.

# Scope

## In Scope

- `approval-service` `MasterDataRestAdapter` — 내부 호출의 신원 부여
- 4xx 삼킴 술어의 정정 (401/403 ≠ 404)
- 해당 단위/통합 테스트

## Out of Scope

- 결재함(inbox)이 콘솔 운영자에게 비는 문제 → **`TASK-MONO-515`** (별개 원인)
- 아웃박스 릴레이 미기동 → **`TASK-ERP-BE-042`**
- masterdata-service 쪽 인증 정책 완화 (내부 경로를 열어 주는 방향은 **채택 금지** —
  아래 Edge Cases 참조)

# Acceptance Criteria

- [ ] **AC-0 (재현)** — 착수 시 위 401 을 **컨테이너 안에서** 다시 관측한다. 재현되지
      않으면(예: 그 사이 다른 티켓이 고쳤다면) 그 사실을 수치와 함께 적고 STOP 한다
- [ ] **AC-1 (신원)** — 내부 호출이 유효한 자격을 싣는다. **두 안 중 하나를 고르고 근거를
      적는다**: ① 호출자의 토큰 전파(테넌트가 자동으로 맞고 데이터 스코프가 보존된다),
      ② `client_credentials` 워크로드 토큰(호출자와 무관하게 동작하지만 **테넌트가
      `erp` 로 고정**되어 `demo-corp` 같은 다른 테넌트의 마스터를 못 본다 — 실측:
      워크로드 토큰으로 `GET /masterdata/departments` 는 200 인데 `totalElements=0`).
      🔴 ②를 고르면 이 티켓은 **해결되지 않는다** — 반드시 ①의 성질을 먼저 확인할 것
- [ ] **AC-2 (술어 정정)** — 401/403 은 "주체 없음" 으로 접히지 않는다. 인증/인가 실패는
      `approval_subject_resolve_failures_total` 로 계수되고 로그에 남는다.
      404 만 "주체 없음" 이다
- [ ] **AC-3 (회귀 가드)** — 스텁이 **401 을 내는** 케이스를 추가하고, 그 상황에서
      상신이 "ACTIVE 아님(422 subject_unresolved)" 이 아니라 **인증 실패 경로**로
      갈리는지 단언한다. 🔴 가드가 **무는지** 확인한다(헤더 주입을 되돌리면 RED)
- [ ] **AC-4 (라이브 검증)** — `bash infra/demo/demo-up.sh iam erp console` 후
      `seed-erp.sh` 의 `⛔ 차단 … TASK-ERP-BE-041` 두 줄이 **사라지고** 결재 2건이
      `SUBMITTED` 가 된다. 콘솔 `/erp/approval` 의 BFF 원소 수로 확인한다(HTML 아님)

# Related Specs

- `projects/erp-platform/specs/services/approval-service/architecture.md` § Reference Integrity model
- `projects/erp-platform/specs/services/masterdata-service/architecture.md` (리소스 서버 선언)

# Related Contracts

- `projects/erp-platform/specs/contracts/http/approval-api.md` — `APPROVAL_ROUTE_INVALID` / `subject_unresolved`
- `platform/contracts/jwt-standard-claims.md`

# Edge Cases

- **masterdata-service 를 내부 경로에서 인증 면제하는 것은 안 된다** — 같은 포트/경로가
  게이트웨이를 통해서도 서빙되므로 면제는 도메인 전체의 인가를 뚫는다. 신원을 **싣는**
  방향이어야 한다
- 토큰 전파(①)를 고르면 **만료된 토큰**으로 상신이 오래 걸리는 경우가 생긴다 — 그때의
  401 은 "주체 없음" 이 아니라 재인증 신호다(AC-2 가 이것을 가른다)
- 전파 방식이 리액티브/서블릿 경계를 넘으면 `SecurityContext` 가 비어 있을 수 있다 —
  `ActorContext` 는 이미 요청 스코프에 있으므로 그것을 경유하는 편이 안전하다

# Failure Scenarios

- **401 을 404 처럼 계속 접어 둔 채 헤더만 추가** → 나중에 토큰이 깨지면 다시 조용히
  "주체 없음" 이 된다. AC-2 가 막는다
- **워크로드 토큰(②)으로 서둘러 닫기** → 단일 테넌트 테스트에서는 초록인데 데모
  (`demo-corp`)에서는 그대로 실패한다. AC-1 이 그 수치를 요구한다

# Definition of Done

- [ ] AC-0~AC-4 충족
- [ ] `./gradlew :projects:erp-platform:apps:approval-service:test` GREEN
- [ ] Ready for review
