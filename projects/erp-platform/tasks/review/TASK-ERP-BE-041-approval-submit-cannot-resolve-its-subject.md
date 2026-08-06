# Task ID

TASK-ERP-BE-041

# Title

결재 상신이 **항상** 실패한다 — 주체 조회 내부 호출이 인증 헤더 없이 나가 401 을 받고, 그 401 이 "마스터가 ACTIVE 아님" 으로 번역된다

# Status

review

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

- [x] **AC-0 (재현)** — 착수 시 위 401 을 **컨테이너 안에서** 다시 관측한다. 재현되지
      않으면(예: 그 사이 다른 티켓이 고쳤다면) 그 사실을 수치와 함께 적고 STOP 한다
- [x] **AC-1 (신원)** — 내부 호출이 유효한 자격을 싣는다. **두 안 중 하나를 고르고 근거를
      적는다**: ① 호출자의 토큰 전파(테넌트가 자동으로 맞고 데이터 스코프가 보존된다),
      ② `client_credentials` 워크로드 토큰(호출자와 무관하게 동작하지만 **테넌트가
      `erp` 로 고정**되어 `demo-corp` 같은 다른 테넌트의 마스터를 못 본다 — 실측:
      워크로드 토큰으로 `GET /masterdata/departments` 는 200 인데 `totalElements=0`).
      🔴 ②를 고르면 이 티켓은 **해결되지 않는다** — 반드시 ①의 성질을 먼저 확인할 것
- [x] **AC-2 (술어 정정)** — 401/403 은 "주체 없음" 으로 접히지 않는다. 인증/인가 실패는
      `approval_subject_resolve_failures_total` 로 계수되고 로그에 남는다.
      404 만 "주체 없음" 이다
- [x] **AC-3 (회귀 가드)** — 스텁이 **401 을 내는** 케이스를 추가하고, 그 상황에서
      상신이 "ACTIVE 아님(422 subject_unresolved)" 이 아니라 **인증 실패 경로**로
      갈리는지 단언한다. 🔴 가드가 **무는지** 확인한다(헤더 주입을 되돌리면 RED)
- [x] **AC-4 (라이브 검증)** — `bash infra/demo/demo-up.sh iam erp console` 후
      `seed-erp.sh` 의 `⛔ 차단 … TASK-ERP-BE-041` 두 줄이 **사라지고** 결재 2건이
      `SUBMITTED` 가 된다. 콘솔 `/erp/approval` 의 BFF 원소 수로 확인한다(HTML 아님)

---

# 구현 기록 (2026-08-07)

## AC-0 — 재현됨

approval 컨테이너 안에서 인증 없이 실재 부서를 조회 → **`HTTP/1.1 401`**. 같은 순간
같은 컨테이너에서 **운영자 토큰(assume `demo-corp`)을 실어** 같은 URL 을 부르면
**`200` + `"status":"ACTIVE"`**. 두 호출의 차이는 `Authorization` 헤더 하나뿐이므로
"주체가 ACTIVE 가 아니다" 라는 판정은 처음부터 성립하지 않았다.

## AC-1 — **①(호출자 토큰 전파)** 채택

②(워크로드 토큰)는 티켓이 이미 실측으로 배제해 뒀고, ①은 착수 시 **직접 확인했다**:
위 200 응답이 그 확인이다 — masterdata-service 는 게이트웨이를 거치지 않은
**컨테이너 내부 직통 호출**에서도 `demo-corp` 운영자 토큰을 그대로 받아들인다. 즉
전파에는 토큰 재발급도, audience 조정도 필요 없다.

구현은 `MasterDataRestAdapter` 가 요청 스코프의 `JwtAuthenticationToken`(erp 의
`ActorAuthenticationToken` 이 그 하위 타입)에서 원문 토큰을 읽어 헤더로 싣는다.
`tenantId` 인자는 **더 이상 장식이 아니다** — 전파 토큰의 `tenant_id` 클레임과
대조해 어긋나면 호출 자체를 거부한다(`cause=tenant_mismatch`).

🔵 이 6줄을 `libs/java-security-servlet` 으로 승격하지 않았다. **승격 트리거는 "두 번째
서비스가 같은 전파를 필요로 할 때"** — 지금은 approval-service 하나뿐이다.

## AC-2 — 술어 정정

`onStatus(4xxClientError, 삼킴)` 을 **`onStatus(isError, 상태코드를 던짐)`** 으로 바꿔
본문을 건드리기 **전에** 상태를 분류한다(에러 본문은 `MasterEnvelope` 가 아니므로,
디코더까지 흘려보내면 모든 4xx 가 "디코드 실패" 로 재라벨된다).

- **404 만** 조용한 `false` — 이건 *답* 이다(주체가 없다).
- 401/403 → `cause=auth`, 그 밖의 4xx → `client_error`, 5xx/타임아웃 → `unreachable`,
  컨텍스트에 토큰 없음 → `no_credentials`, 테넌트 불일치 → `tenant_mismatch`.

계측기는 **스펙이 이미 요구하고 있던 것**이다 — `architecture.md` § Observability 는
`approval_subject_resolve_failures_total{cause}` 라고 **`cause` 태그까지 적어 뒀는데
구현은 태그 없는 카운터 하나였다.** 이 PR 은 새 규약을 만든 게 아니라 스펙에
맞춘 것이다(스펙에는 `cause` 값 목록을 명시했다).

와이어 응답은 **바꾸지 않았다** — 두 거절 모두 422 `APPROVAL_ROUTE_INVALID`
(`details.cause=subject_unresolved`) 다. `architecture.md` § Reference Integrity 가
"masterdata unreachable 도 같은 코드" 라고 명시하고, 계약의 `details.cause` 는 세 값으로
닫혀 있다. 티켓이 요구한 것은 **구별되는 관측**이고, 그것은 태그·로그로 성립한다.

## AC-3 — 가드 + **물기 확인**

🔴 **테스트의 술어부터 고쳤다.** 이 결함이 초록 CI 를 통과한 이유는 IT 의 masterdata
`MockWebServer` 스텁이 **어떤 요청에도 200 을 주고 있었기 때문**이다 — 실물은 독립
리소스 서버인데 스텁은 무인증을 허용했다. 스텁의 술어를 리소스 서버의 술어로 바꿨다
(`Authorization` 이 없으면 401).

새 IT `SubjectResolveIdentityIntegrationTest` 3건:

1. 전파된 헤더가 **호출자의 정확한 토큰**인지(단순 non-null 이면 워크로드 토큰도 통과한다)
2. masterdata 401 → 거절 + `{cause=auth}` **+1**
3. masterdata 404 → 거절 + **모든 cause 합계 불변** ← 대조군. "카운터가 올랐다" 만
   단언하는 테스트는 우연히 통과할 수 있다

**물기 실측**: 헤더 주입 한 줄을 지우고 재실행 → **3건 중 2건 RED**(전파 단언 +
404-대조군; 404 대조군이 무너지는 이유는 헤더가 없으면 스텁이 401 을 내 카운터가
움직이기 때문이다). 복구 후 재실행 GREEN.

기존 IT 36건(lifecycle 21 · delegation 15)도 **인증을 요구하는 스텁 아래에서 그대로
통과**한다 — 전파가 새 테스트에서만이 아니라 모든 경로에서 동작한다는 뜻이다.

- `:approval-service:test` — **192건 / 실패 0**
- `:approval-service:integrationTest` — **43건 / 실패 0**(5개 클래스 전부)

## AC-4 — 라이브 검증

approval-service 만 재배포(`--no-deps`) 후:

- `seed-erp.sh` → `요약 — 생성 0 · 기존 20 · 실패 0 · **차단 0**`. `⛔ 차단 …
  TASK-ERP-BE-041` 두 줄이 사라지고 `진행 결재 … → 상신` 두 줄로 바뀌었다
- 프로듀서 API: `DRAFT 1 · SUBMITTED 2`
- **콘솔 BFF**(`/api/erp/approval/requests`, 헤드리스 로그인 + `demo-corp` 테넌트 선택)
  → 200, 원소 3 = `DRAFT 1 · SUBMITTED 2`. 🔵 BFF 는 운영자 토큰이 아니라 **도메인
  대면 GAP 토큰**을 쓰므로 curl 과 다른 신원 경로다
- `/actuator/prometheus` — cause 5계열 전부 존재, `auth`=**0.0**(전파가 실제로 먹혔다는
  뜻이다. 안 먹혔다면 상신 2건이 그대로 2 를 만들었을 것)

🔵 **AC-4 가 지정한 술어(BFF 원소 수)는 이 변경에 둔감하다** — 원소 수는 고침 전후
모두 3 이다(DRAFT 도 목록에 뜬다). 실제로 움직인 것은 **상태 분포**이고, 위 판정은
그것으로 냈다.

## 이 수정이 되살리지 **못한** 것

BE-041 이 풀리자 상신 2건이 실제로 발행됐고 — 그 이벤트는 **곧바로 DLT 로 갔다**.
같은 순간 브로커 실측:

| 토픽 | end-offset | DLT |
|---|---|---|
| `erp.approval.submitted.v1` | **0 → 2** | **4**(소비자 2개 × 2건) |
| `erp.approval.delegated.v1` | 1 | 2 |

retry 토픽은 전부 0 — 재시도 없이 직행이다. 이는 `TASK-ERP-BE-043`(approval 봉투가
`aggregateId`/`tenantId` 를 안 싣는다)의 지문이고, **그 티켓이 "나머지 다섯 토픽도 같은
운명인데 상신이 BE-041 로 막혀 보이지 않을 뿐" 이라고 적어 둔 예측을 실측으로 확인한
것**이다. 범위·검증이 다르므로 여기서 고치지 않는다.

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

- [x] AC-0~AC-4 충족
- [x] `./gradlew :projects:erp-platform:apps:approval-service:test` GREEN (192/0) —
      🔴 이 task 만으로는 부족하다: `test` 는 `excludeTags 'integration'` 이라 이 티켓의
      가드를 **한 건도 실행하지 않는다.** `integrationTest` (43/0) 를 함께 돌려야 한다
- [x] Ready for review
