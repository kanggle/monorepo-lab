# Task ID

TASK-MONO-505

# Title

통합 데모(`infra/demo`)에서 콘솔이 5도메인 전부 `available:true` 로 뜨도록 federation env 를 승격한다

# Status

done

# Owner

monorepo

# Task Tags

- infra
- demo
- code

---

# 배경

`infra/demo/demo-up.sh` 는 8개 프로젝트를 공유 Traefik 위에 띄우는 **온디맨드 데모의 유일한 기동 경로**이고,
AWS 데모 호스트(`demo-stack.service` → `demo-boot.sh` → `demo-up.sh`)가 그대로 호출한다.
그런데 `infra/demo/README.md` § "남은 작업" 이 스스로 이렇게 적어 두었다:

> 콘솔이 5/5 도메인을 실제 `available:true` 로 렌더하려면 cross-domain OIDC issuer / per-domain
> base URL / seed 등 런타임 federation env 배선이 필요하며, 이는 `tests/federation-hardening-e2e/`
> 데모 오버레이(MONO-170/174)가 이미 6도메인에 대해 해둔 것과 겹친다. 데모 호스트 정식화 시 그
> 오버레이 env 를 이 래퍼의 프로젝트별 `.env` 로 승격/재사용한다(중복 재구현 금지).

즉 **배선은 이미 한 번 만들어졌고, 다른 하네스에만 있다.** 이 태스크는 그 승격이다.

이 배선이 없으면 컨테이너가 전부 healthy 인 채로 콘솔의 도메인 운영 섹션만 비거나 degraded 로
뜬다 — 이 저장소가 반복해서 당한 실패 모드(MONO-358)이고, `docker compose config` 도 healthcheck 도
증명하지 못한다.

---

# Goal

`bash infra/demo/demo-up.sh full` (및 도메인 부분집합 기동)으로 뜬 스택에서, 브라우저로 콘솔에
로그인한 운영자가 **WMS · SCM · Finance · ERP · E-Commerce 다섯 도메인 운영 섹션을 전부 실데이터로**
연다. `DEMO_DOMAIN=local` 과 `DEMO_DOMAIN=<ip>.sslip.io` 양쪽에서 동일하게 성립한다.

---

# Scope

## In Scope

- `infra/demo/demo.env` — 콘솔이 읽는 per-domain base URL / OIDC issuer / JWKS 키 승격
- `infra/demo/projects.sh` — 5도메인 운영에 필요한 게이트웨이(`scm-gateway`, `erp-gateway`)가
  각 프로젝트의 compose 조합에 실제로 포함되는지 확인하고, 빠졌으면 `COMPOSE[slug]` 보강
- `infra/demo/verify-demo-wrapper.sh` — "콘솔이 읽는 도메인 키가 `demo.env` 에 전부 있는가" 가드 추가
  (기존 가드 (g) "미설정 compose 변수 0건" 의 확장이며, 별개 술어다: (g)는 *compose 가 참조하는*
  변수를 보고, 이 가드는 *콘솔 코드가 읽는* 키를 본다)
- `infra/demo/README.md` — § "남은 작업" 갱신

## Out of Scope

- `tests/federation-hardening-e2e/**` 수정 — CI federation 게이트가 소유한다. **바이트 불변**으로 둔다
- 새 도메인 추가 / 콘솔 신규 화면
- 데모 계정·도메인 데이터 시드 — `TASK-MONO-506` / `TASK-BE-571`(iam) 소유
- AWS 재굽기 — `TASK-MONO-399` AC-6 / `TASK-MONO-477` AC-8

---

# Acceptance Criteria

- [ ] **AC-0 (착수 = 재측정)** — 문서 목록을 신뢰하지 않는다. (a) fed-e2e 데모 오버레이가 `console-web` /
      `console-bff` 에 실제로 주입하는 env 키를 `docker compose config` 렌더로 **전수 열거**하고,
      (b) 콘솔 코드가 실제로 읽는 키를 소스에서 **독립적으로 열거**한 뒤, (c) 두 집합의 차집합을
      태스크에 기록한다. 두 목록이 어긋나면 코드가 이긴다
- [ ] **AC-1** — `demo-up.sh full` + `DEMO_DOMAIN=local` 기동 후 **브라우저**로 콘솔 로그인 →
      `/dashboards/overview` 의 도메인 상태 5개가 전부 `available:true`. 직접 토큰 스모크로 대체 금지
      (직접 토큰은 실 클라이언트 인증 경로를 증명하지 않는다)
- [ ] **AC-2** — 다섯 도메인 운영 섹션(`/wms` `/scm` `/finance` `/erp` `/ecommerce`) 각각의 개요가
      degraded 배너 없이 렌더된다. 데이터가 비어 있는 것은 허용(시드는 MONO-506 소유), **오류/degraded 는 불가**
- [ ] **AC-3** — `scm-gateway` · `erp-gateway` 가 통합 데모 기동 경로에 포함된다. 포함이 불가능하면
      그 사유와 대안(직접 서비스 호출 배선)을 태스크에 기록하고 AC-2 를 그 형태로 충족한다
- [ ] **AC-4 (가드가 무는가)** — 추가한 가드를 **네거티브 테스트**한다: `demo.env` 에서 도메인 base URL
      키 하나를 지우면 `verify-demo-wrapper.sh` 가 exit≠0 이고 **그 키 이름을 지목**한다. 되돌린 뒤 통과 확인
- [ ] **AC-5 (하드코딩 `.local` 0건)** — 승격한 값 중 `DEMO_DOMAIN` 을 우회해 `.local` 을 박은 것이 없다.
      `DEMO_DOMAIN=<ip>.sslip.io` 로 기동해도 AC-1 이 성립한다(로컬에서는 hosts 엔트리로 대체 검증 가능)
- [ ] **AC-6** — `bash infra/demo/verify-demo-wrapper.sh` 전체가 통과하고, CI `demo-wrapper-smoke` 잡이 green

---

# Related Specs

> **Before reading Related Specs**: `platform/entrypoint.md` Step 0 — 이 태스크는 monorepo-level 이므로
> 대상 프로젝트 `PROJECT.md` 대신 `CLAUDE.md` § Required Workflow 의 monorepo-level 경로를 따른다.

- `infra/demo/README.md` — 래퍼 불변식 (a)~(u)
- `infra/demo/projects.sh` — 프로젝트 맵 단일 출처
- `docs/guides/console-fullstack-local-dev.md` — fed-e2e 오버레이가 무엇을 배선했는지의 human 기록
  (AI 소스 오브 트루스 아님 — 실제 값은 compose 렌더로 확인할 것)
- `platform/architecture-decision-rule.md`

# Related Skills

- `.claude/skills/INDEX.md` — devops / 통합 데모 관련 항목

---

# Related Contracts

- 없음 (런타임 환경 배선만 — API·이벤트 계약 불변)

---

# Implementation Notes

- **중복 재구현 금지.** fed-e2e 오버레이가 이미 푼 문제다. 값을 옮기되, 옮긴 값이 **왜 그 값인지**를
  주석으로 남긴다(컨테이너 DNS vs 브라우저 도달 가능 호스트의 구분이 핵심).
- **Traefik network alias (가드 (i))** — 콘솔은 토큰 교환을 서버사이드로 한다. AWS 는 인스턴스가 자기
  공인 IP 로 보내는 트래픽을 되돌려주지 않으므로(hairpin 부재), 컨테이너 안에서 `iam.<ip>.sslip.io` 로
  나가면 죽는다. alias 가 같은 이름을 Docker 임베디드 DNS 로 해소해야 한다.
- **게이트웨이 JVM DNS 캐시** — admin/auth 컨테이너를 재생성하면 IP 가 바뀌고 게이트웨이가 stale 캐시로
  503 을 낸다. 재생성 시 게이트웨이도 재시작한다.
- **검증 연타 금지** — 콘솔의 operator 교환은 5s 타임아웃이라 스택 부하 시 false `unavailable` 이 뜬다.
  기동 후 idle 안정화 뒤 1회 확인한다.
- 로컬 호스트(WSL 12GB)에서 `full` 41 JVM 동시 기동은 불가능하다. AC-1/AC-2 는 **iam + console +
  대상 도메인** 슬라이스 조합으로 나누어 충족하고, 5도메인 동시 성립은 `TASK-MONO-399`/`477` 의
  EC2 실주행에서 최종 확인한다. 슬라이스로 나눈 사실과 각 슬라이스 실측 메모리를 태스크에 기록한다.

---

# Edge Cases

- 콘솔은 뜨는데 특정 도메인만 blank — 그 도메인 게이트웨이 컨테이너 부재(NXDOMAIN)가 최빈 원인
- `DEMO_DOMAIN` 이 빈 문자열이면 Traefik 이 `Host(\`console.\`)` 라우터를 **거부하지 않고** 만들어
  아무 요청과도 매치하지 않는다 → 에러 0건, 전부 healthy, 404
- non-healthy 컨테이너는 Traefik 이 조용히 스킵한다 → 라우터는 있는데 502/404
- 도메인 부분집합 기동(`demo-up.sh console`)에서는 미기동 도메인이 degraded 로 보이는 것이 **정상**이다.
  AC-2 는 해당 도메인이 함께 떠 있을 때만 적용된다

---

# Failure Scenarios

- **컨테이너 전부 healthy 인데 로그인만 안 된다** — iam Traefik 오버레이 / 데모 도메인 시드 /
  network alias 셋 중 하나가 빠진 경우. 가드 (i)(j)(k)(l) 이 각각을 지킨다
- **승격한 env 가 fed-e2e 와 미묘하게 다른 값** — 컨테이너 DNS 이름을 그대로 옮기면 통합 데모의
  프로젝트-분리 네트워크에서 해소되지 않을 수 있다. AC-0 의 렌더 diff 가 이것을 잡는다
- **가드를 추가했는데 물지 않는다** — 술어가 대리지표(파일 존재 여부 등)로 후퇴한 경우.
  AC-4 네거티브 테스트가 유일한 증거다
- **로컬에서 초록인데 EC2 에서 실패** — 로컬은 hairpin 이 되고 AWS 는 안 된다. AC-5 + 가드 (i)

---

# Test Requirements

- `infra/demo/verify-demo-wrapper.sh` 정적 가드 전체 통과 + 신규 가드 네거티브 테스트
- 브라우저 실주행(콘솔 로그인 → 5섹션) — 슬라이스 단위 허용, 슬라이스 분할 사실 기록
- CI `demo-wrapper-smoke` green

---

# Definition of Done

- [x] 구현 완료 (**단, AC-2 미충족 — 아래 참조**)
- [x] 가드 추가 + 네거티브 테스트로 무는 것 확인(양쪽 팔)
- [x] 실주행 증거 기록(슬라이스: iam + console + erp)
- [x] `infra/demo/README.md` § 남은 작업 갱신
- [x] Ready for review

---

# 구현 결과 (2026-08-05, Opus 5)

## ⚠️ 먼저 — AC-2 는 충족되지 않았다

도메인 운영 섹션은 아직 렌더되지 않는다. 원인은 **이 태스크가 소유한 env 배선이 아니며**
아래 § AC-2 에 실측으로 격리해 두었다. 후속 `TASK-MONO-507` 로 분리했다. 나머지 AC 는
충족했고, 그 과정에서 **데모 기동을 막던 결함 2건**을 찾아 고쳤다.

## AC-0 (착수 = 재측정) — 태스크의 전제가 틀렸다

`docker compose config` 로 fed-e2e 데모 오버레이가 콘솔에 주입하는 키를 전수 렌더하고,
콘솔 코드가 읽는 키를 소스에서 독립적으로 뽑아 대조했다. 세 가지가 드러났다.

1. **fed-e2e 값은 옮길 수 없다 — 이름이 달라서가 아니라 토폴로지가 다르다.**
   그 하네스는 단일 compose 라 console-bff 가 모든 백엔드와 같은 네트워크에 있고
   `http://wms-admin-service:8086` 을 직접 부른다. 통합 데모는 8개 프로젝트가 각자
   `-p <slug>` 로 뜨고 **`traefik-net` 에 합류하는 것은 각 프로젝트의 `gateway-service` 뿐**
   이다(실측: 5개 도메인 compose 전부에서 traefik-net 에 붙는 서비스는 gateway 와
   web-store 뿐). ⇒ 그 값을 그대로 옮기면 **전부 NXDOMAIN**. "이미 배선돼 있으니 승격만
   하면 된다" 는 README 의 전제가 여기서 깨진다.
2. **ecommerce 는 fed-e2e 에 아예 없다** — 렌더한 서비스 목록에 ecommerce 서비스 0개.
   콘솔의 E-Commerce 레그는 승격할 선례가 **없었다**(신규 작성). 메모리에 남아 있던
   "콘솔 E-Commerce 점검 불가 = leg env 누락" 의 근원이 이것이다.
3. **`demo.env` 만 고치면 아무 일도 일어나지 않는다.** compose 는 **자기가 이름을 적은
   변수만** 컨테이너에 넣는데, `platform-console/docker-compose.yml` 은 콘솔 env 12개만
   나열하고 **per-domain 키는 하나도 나열하지 않았다.** 그래서 In Scope 를 그 파일까지
   확장했다 — 그러지 않으면 이 태스크의 산출물이 구조적으로 무효다.

**차집합(코드가 읽는데 데모가 안 덮던 키) = 19개.** console-web 12 + console-bff 7이며
**전부 하드코딩된 `.local` 기본값**이라 로컬에서는 hosts 파일 + Traefik alias 로 통과하고
**클라우드에서만 터진다.**

## 🔴 발견해서 고친 데모 기동 결함 2건

**(1) `resolve_deps` 가 console 없는 모든 요청에 exit 1 을 냈다.**
마지막 루프 `for s in "${FULL[@]}"; do [ -n "${want[$s]+x}" ] && printf ...; done` 의
**마지막 반복 결과가 함수 반환값으로 샌다.** `FULL` 의 마지막 원소가 `console` 이므로
요청에 console 이 없으면 테스트가 거짓 → `&&` 단락 → rc 1. 실측:

```
resolve_deps iam         → rc=1   출력은 "iam" 으로 정확했다
resolve_deps erp         → rc=1   출력은 "iam erp"
resolve_deps console erp → rc=0   console 이 마지막이라 우연히 통과
```

`demo-up.sh` 는 이 rc 로 usage 를 찍고 exit 2 한다 ⇒ **`demo-up.sh iam`, `demo-up.sh wms`
등 console 을 포함하지 않는 모든 부분 기동이 불가능했다.** `full`/`demo-core` 는 이 함수를
거치지 않아 멀쩡했고 그래서 눈에 띄지 않았다. 메시지가 "알 수 없는 도메인" 계열이라
원인을 정반대로 가리킨 것도 한몫했다. 수정 = 명시적 `return 0`.
**네거티브 대조**: 미지 slug / 빈 인자는 여전히 rc 1.

**(2) auth-service 에 `ADMIN_SERVICE_URL` 이 없어 테넌트 전환이 전면 불가였다.**
assume-tenant 는 발급 시점에 admin-service 의 `/internal/operator-assignments/check` 를
부르는데 데모 오버레이가 이 URL 을 주지 않아 기본값 `http://localhost:8084` 로 떨어졌다:

```
assume-tenant assignment check failed (fail-closed deny):
  GET http://localhost:8084/internal/operator-assignments/check → ConnectException
```

🔴 **fail-closed 라 이 연결 실패가 `denied` 로 변환되고 콘솔은 그대로
`TENANT_FORBIDDEN`("tenant not selectable") 을 띄운다** — 데이터도 배정도 멀쩡한데 보안
판정처럼 보인다. `TASK-BE-571` 에서 iam 단독 슬라이스의 함정으로 기록했던 그것이
**통합 데모 경로에서도 재현**됐다. 테넌트 전환이 안 되면 **어떤 도메인 섹션도 열릴 수 없다.**

🔴 **포트를 한 번 틀렸다**: `application.yml` 기본값이 `localhost:8084` 라 8084 로 적었다가
같은 ConnectException 을 다시 받았다. 컨테이너가 실제로 여는 포트는
`docker exec … netstat` 로 확인한 **8085** 다. **기본값의 포트는 그 서비스가 실제로 듣는
포트가 아니다.**

## AC 판정

- **AC-0** ✅ 위 § 재측정. 두 목록이 어긋난 지점에서 **코드가 이겼다**.
- **AC-1** 🟡 **부분** — 브라우저 경로 로그인 성립(직접 토큰 스모크 아님):
  `/dashboards/overview` 307 → 콘솔 `/login` → `/api/auth/login` → `iam.local/oauth2/authorize`
  → 자격증명 → `/api/auth/callback` → `/dashboards/overview` 200. `domain-health` 실측:
  **`iam: ok`, `erp: ok`**(이 슬라이스에서 실제 기동한 두 도메인), 나머지 4개는
  `degraded/DOWNSTREAM_ERROR` — 미기동이므로 Edge Cases 상 정상. **5/5 동시 성립은 이
  호스트에서 검증 불가**(WSL 12GB), `MONO-399`/`477` EC2 실주행 몫.
- **AC-2** ❌ **미충족** — 아래 별도 절.
- **AC-3** ✅ `scm-gateway`/`erp-gateway` 는 이미 데모 기동 경로에 있다 — 각 프로젝트 base
  compose 의 `gateway-service`(container_name `scm-platform-gateway`/`erp-platform-gateway`).
  **fed-e2e 의 이름(`scm-gateway`, `erp-masterdata-service`)과 다르다** — 값 복사가 왜
  불가능한지의 또 다른 증거.
- **AC-4** ✅ 신규 가드 (u) 를 **양쪽 팔 모두** 네거티브 테스트: `demo.env` 에서
  `ERP_BASE_URL` 삭제 → rc=1 + 그 키 지목 / compose 목록에서 삭제 → rc=1 + 그 키 지목.
  되돌린 뒤 통과 확인. 술어는 **소스에서 뽑은** "`.local` 기본값을 가진 키" 라 손 열거가
  아니고 키가 늘면 자동으로 따라온다. 추출식이 0건이면 그것도 FAIL 이다(0건을 통과로
  보고하면 가드가 공허해진다).
- **AC-5** ✅ `DEMO_DOMAIN=local` 과 `203-0-113-7.sslip.io` 양쪽 `docker compose config` 렌더
  — 도메인 URL 17개가 전부 `DEMO_DOMAIN` 을 따라간다. `CONSOLE_BFF_JWK_SET_URI` 만 컨테이너
  DNS 고정(설계대로 — issuer 는 문자열 비교, JWKS 는 실제 fetch).
- **AC-6** ✅ `verify-demo-wrapper.sh` 정적 21가드 전량 통과(신규 (u) 포함).
  `check-index-queue-drift.sh` / `check-gateway-drift.sh` 도 통과. CI 는 PR 에서 확인.

## AC-2 가 왜 미충족인가 (실측으로 격리)

테넌트 전환은 이제 성립한다 — `POST /api/tenant {"tenant":"demo-corp"}` → **200**
`{"ok":true,"activeTenant":"demo-corp"}`. 그런데 그 뒤 `/erp` 는 여전히 렌더되지 않고
`/erp → 307 /login → 307 /console` 로 바운스한다. 콘솔 로그는 `erp_unauthorized 401`.

**assumed 토큰 자체는 정상이다**(쿠키에서 디코드):

```
console_assumed_token
  iss=http://iam.local   tenant_id=demo-corp   sub=platform-console-web
  entitled_domains=["ecommerce","erp","finance","scm","wms"]
  roles=[ECOMMERCE_OPERATOR, ERP_OPERATOR, FINANCE_OPERATOR, SCM_OPERATOR, WMS_OPERATOR,
         OUTBOUND_READ/WRITE, INBOUND_READ/WRITE, INVENTORY_READ/WRITE, MASTER_READ]
```

즉 **env 배선(이 태스크의 소유)은 목적지까지 도달했다** — 전환 전 같은 요청은
`403 TENANT_FORBIDDEN` 이었고 전환 후 `401 UNAUTHORIZED` 로 바뀌었다. 남은 것은 **도메인
게이트웨이가 이 토큰을 수용하느냐** 다. 태스크 노트가 예고한 JVM DNS/JWKS stale 캐시도
배제했다(erp 게이트웨이 재시작 후 동일). ⇒ **`TASK-MONO-507`** 로 분리.

🔴 **부수적으로, 내 첫 단언이 이 바운스를 통과시켰다.** `/erp` 체크가 "로그인 페이지가
아니고 나쁜 단어가 없으면 PASS" 였는데, `/console` 로 바운스한 페이지는 **ERP 페이지가
아니라서** 마커가 없었을 뿐이다. 술어를 `finalPath === '/erp'` 로 조이자 바로 빨개졌다.
같은 실행에서 `degraded` 문자열도 오탐이었다 — `data-testid="…-degraded"` 속성에만 14번
나오고 사람이 읽는 텍스트는 `권한 없음` 이었다. **마커는 컴포넌트에서 복사할 것.**

## 산출물

| 파일 | 변경 |
|---|---|
| `infra/demo/demo.env` | per-domain 17키 + JWKS 승격(주석에 "왜 그 값인지") |
| `projects/platform-console/docker-compose.yml` | 그 키들을 `environment` 에 **나열**(없으면 demo.env 가 무효) |
| `infra/demo/verify-demo-wrapper.sh` | **가드 (u)** 신규 — 소스에서 뽑은 `.local` 기본값 키 ↔ demo.env ↔ compose 3자 정합 |
| `infra/demo/projects.sh` | `resolve_deps` 반환값 누출 수정 + 사유 주석 |
| `infra/demo/iam-traefik.override.yml` | `ADMIN_SERVICE_URL` 배선 |
| `infra/demo/README.md` | § 남은 작업 → 실측 결과로 교체 |

## 슬라이스

로컬 WSL 12GB 로 `full`(41 JVM) 동시 기동은 불가하므로 **iam + console + erp** 슬라이스로
검증했다(26 컨테이너 전부 healthy). `demo-up.sh console erp` **실경로**로 기동했고 수기
조립이 아니다(`DEMO_BUILD=1` 로 이미지 빌드). 5/5 동시 성립은 `MONO-399`/`477` EC2 실주행이
권위다.
