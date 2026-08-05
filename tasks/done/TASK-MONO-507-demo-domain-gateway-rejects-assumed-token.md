# Task ID

TASK-MONO-507

# Title

통합 데모에서 도메인 게이트웨이가 assumed 토큰을 401 로 거부한다 — 콘솔 도메인 섹션이 열리지 않는다

# Status

done

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

> **⛔ 이 가설은 반증되었다 (AC-0).** `sub` 는 무관했다. 게이트웨이는 이 토큰을 **수락**했고
> 401 은 그 뒤 서비스가 JWKS 를 못 가져와서 낸 것이다. 위 "배경" 은 착수 시점의 관측으로
> 그대로 남겨 둔다 — 어디서 잘못 읽혔는지가 이 티켓의 교훈이기 때문이다. **결론은 AC-0/AC-1 을
> 보라.**

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

- [x] **AC-0 (착수 = 재측정)** — 재현했고, **가설은 틀렸다.** `sub=platform-console-web` 은
      원인이 아니었다. 게이트웨이는 토큰을 **수락했고**, 401 은 그 뒤 서비스가 냈다.
      결정적 측정은 같은 게이트웨이에 두 토큰을 던져 상태 코드를 나란히 놓은 것이다:
      base(`tenant_id=iam`) → **403** `TENANT_FORBIDDEN "tenant_id 'iam' is not allowed"` /
      assumed(`tenant_id=demo-corp`) → **401** `UNAUTHORIZED "Authentication required"`.
      403 은 엣지의 테넌트 게이트가 내는 것이므로, 401 을 받았다는 것은 **엣지를 통과했다**는
      뜻이다. 거부 지점은 `erp-platform-masterdata` 로그가 그대로 말해 주었다:
      `Couldn't retrieve remote JWK set: I/O error on GET
      "http://iam-auth-service:8081/oauth2/jwks": iam-auth-service`
- [x] **AC-1 (원인 한 문장)** — **도메인 백엔드 리소스 서버가 IdP 의 JWKS 호스트를 해소하지
      못했고(그 이름은 traefik-net 위에만 있는 alias 인데 백엔드는 프로젝트 사설망에만 있었다),
      Spring 이 그 UnknownHost 를 fail-closed 로 401 "Authentication required" 로 바꿨다.**
      즉 클레임 거부가 아니라 **연결 결함이 인증 판정으로 위장**한 것이다.
- [x] **AC-2** — `finalPath === '/erp'` 술어로 통과. `console.local/erp 200` (바운스 없음,
      31,056 bytes). 수정 전 궤적은 `/erp 307 → /login 307 → /console 200` 이었다.
      드라이버 **9/9 PASS**.
- [x] **AC-3** — degraded 마커 0건. 마커는 컴포넌트에서 복사한 사람이 읽는 문자열
      (`권한 없음` / `일시적으로` / `불러오지 못` / `불러올 수 없` / `연결할 수 없`)을 쓴다.
      콘솔 로그도 `erp_unauthorized` **0건**, 7개 엔드포인트 전부 200.
- [x] **AC-4 (형제 파리티)** — **erp 만의 문제가 아니었다.** 전수 재측정 결과 영향 범위는
      **5개 프로젝트 19개 백엔드**다(erp 4 · scm 4 · wms 5 · fan 4 · finance 2). 판정 기준은
      `application.yml` 의 `jwk-set-uri` 선언 + compose 의 JWKS env 주입 + `networks` 에
      traefik-net 부재. 전부 함께 고쳤다. 개별 기록:
      · **wms** 가 가장 오래 위장돼 있었다 — `docker-compose.e2e.yml` 의 **기본값 자체**가
        `iam-auth-service` 라, compose 가 이미 alias 해소를 전제하면서 정작 서비스를
        traefik-net 에 붙이지 않았다(그 파일이 태어난 단일-compose 하네스에서는 전제가 참이었다).
      · **fan** 은 `TASK-FAN-FE-014` 의 라이브 검증을 통과한 채 남아 있었다 — 그 술어는
        로그인까지이고, 로그인은 fan 백엔드의 JWKS 를 한 번도 쓰지 않기 때문이다.
      · **ecommerce 는 제외**했다(백엔드가 게이트웨이 헤더를 신뢰, 자체 JWKS 체인 없음).
        `order-service` 의 `/api/internal/**` 만 예외인데 그 체인이 기대하는
        `ecommerce-internal-services-client` 는 **IAM 에 등록조차 없다** → DNS 와 무관한 별개 결함.
- [x] **AC-5** — `verify-demo-wrapper.sh` **가드 (w)** 추가. 술어는 "오버레이 파일이 있는가"
      (대리지표)가 아니라 **"각 리소스 서버가 자기 JWKS URL 의 호스트를 자기 네트워크에서
      해소할 수 있는가"** 다. 네거티브 2종 확인:
      · `COMPOSE[erp]` 에서 오버레이 제거 → rc=1, erp 4개 서비스 **정확히** 지목
      · wms 오버레이에서 `master-service` **한 개만** 누락 → rc=1, 그 하나만 지목
      복구 후 rc=0. 0-추출도 FAIL 로 둔다(모듈 0개 / 매칭 0개 각각 별도 FAIL).
- [x] **AC-6** — `verify-demo-wrapper.sh` 정적 전체 PASS (가드 (w): 리소스 서버 25개 검사).
      CI 는 PR 에서 확인.

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

- [x] 원인 규명 + 수정
- [x] 4도메인 파리티 확인 기록 (실제 범위는 5프로젝트 19서비스였다)
- [x] 실주행 증거 기록
- [x] Ready for review

---

# 실주행 증거

**erp (수정 대상 도메인, 콘솔 경로 전체)**

```
POST /api/tenant {"tenant":"demo-corp"} → 200 {"ok":true,"activeTenant":"demo-corp"}
console.local/erp 200   finalPath=/erp   bytes=31056   (수정 전: /erp 307 → /login 307 → /console)
console-web 로그: erp_ok ×6 + erp_approval_ok ×1, erp_unauthorized 0건
드라이버 ALL 9 CHECKS PASS
```

**fan (형제 파리티 — 잠재가 아니라 실제였음을 라이브 변이로 확정)**

```
수정 후:  4개 백엔드 전부 http://iam-auth-service:8081/oauth2/jwks → {"keys":[{... "kid":"key-2026-04-01" ...
          드라이버 ALL 14 CHECKS PASS (errorBranch=false — SSR 200 이 아니라 '게이트웨이 fetch 성공' 술어)

변이:     docker network disconnect traefik-net fan-platform-artist && docker restart
          → /artists errorBranch=true, 2/14 FAILED
          → artist-service 로그: Couldn't retrieve remote JWK set ... "http://iam-auth-service:8081/oauth2/jwks"
복구:     reconnect + restart → ALL 14 CHECKS PASS
```

**수정 후 도달성 (erp 예)**

```
$ docker exec erp-platform-masterdata getent hosts iam-auth-service
172.21.0.5   iam-auth-service
$ docker exec erp-platform-masterdata wget -O- http://iam-auth-service:8081/oauth2/jwks
{"keys":[{"kty":"RSA","e":"AQAB","use":"sig","kid":"key-2026-04-01","alg":"RS256", ...
```

`kid` 가 assumed 토큰 헤더(`{"kid":"key-2026-04-01","alg":"RS256"}`)와 일치한다.

---

# 남긴 것 — `TASK-MONO-508`

콘솔의 org-hierarchy 레그(`/api/admin/org-nodes`)는 여전히 401 이다. **같은 결함이 아니다**:
도메인 게이트웨이가 아니라 IAM admin API 이고, 토큰도 다르며(operator 토큰: `kid=v1`,
`iss=admin-service`, `token_type=admin`), 요청은 IAM 에 **정상 도달**한다(DNS 무관).
상류 원문까지 떠서 `TASK-MONO-508` 에 넣었다.
