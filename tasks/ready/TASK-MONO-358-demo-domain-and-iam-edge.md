# Task ID

TASK-MONO-358

# Title

데모 도메인 파라미터화(`${DEMO_DOMAIN:-local}`) + **iam 게이트웨이 Traefik 노출** — 통합 데모를 로컬 밖에서 도달·로그인 가능하게

# Status

ready

# Owner

monorepo

# Task Tags

- infra
- demo

---

# Goal

통합 데모(`infra/demo/demo-up.sh full`)는 **96 컨테이너가 전부 뜨지만 아무도 쓸 수 없다.** 두 개의 독립된 결함이 겹쳐 있다.

## 결함 A — 호스트명이 `.local` 하드코딩이라 클라우드에서 도달 불가

Traefik 라우터 규칙 **14개가 전부 `Host(\`x.local\`)` 하드코딩**이다. 로컬은 hosts 파일(`*.local → 127.0.0.1`)로 동작하지만, 데모 호스트를 EC2 에 올리면 방문자 브라우저는 공인 IP 로 접속하므로 `Host: 3.36.44.101` 헤더가 가고 **어떤 라우터에도 매치되지 않는다 → 전 도메인 404**. 방문자에게 `/etc/hosts` 편집을 요구할 수는 없다.

## 결함 B — **`iam.local` 은 아무도 서빙하지 않는 매달린 호스트명이다** (더 심각)

`OIDC_ISSUER_URL: ${OIDC_ISSUER_URL:-http://iam.local}` 이 **6개 compose 에서 기본값으로 참조**되는데, 전 저장소를 통틀어 **`Host(\`iam.local\`)` 라우터가 존재하지 않는다.** iam 이 가진 Traefik 라우터는 `kafka.iam.local` / `grafana.iam.local`(관측 도구) 둘뿐이고, **게이트웨이는 Traefik 에 노출돼 있지 않다** — `docker-compose.e2e.yml`(데모가 앱을 얻으려고 함께 올리는 오버레이, MONO-342)은 Traefik 라벨이 하나도 없고 호스트 포트(`18080` 등)만 발행하는 **CI e2e 용** 파일이다.

게다가 값이 서로 어긋난다:

| | 값 |
|---|---|
| iam 이 실제로 발급하는 issuer (`docker-compose.e2e.yml`) | `http://auth-service:8081` (컨테이너 DNS) |
| console-web 이 기대하는 issuer (기본값, demo.env 오버라이드 없음) | `http://iam.local` |

⇒ **통합 데모에서 콘솔 로그인은 원리적으로 불가능하다.** 아무도 로그인을 시도해본 적이 없어서 드러나지 않았다(96개가 healthy 로 뜨는 것과 무관하다).

이 두 결함을 함께 고친다. 같은 축("어떤 호스트명이 무엇을 가리키는가")이고, 해법 메커니즘도 같다.

## EC2 실기동에서 추가로 드러난 결함 C~F

A·B 를 고치고 **실제로 부팅했더니** 네 개가 더 나왔다. **전부 정적 검사로는 잡히지 않고, 새 호스트에서만 문다.** 이것이 이 task 에서 "실기동 로그인 증명" 을 AC 로 못박은 이유다.

### 결함 C — Traefik v3.2 는 Docker Engine 29 와 말이 통하지 않는다

라벨이 완벽해도 **라우터가 0개**였다. Traefik ≤ v3.5 의 Docker 프로바이더는 API 1.24 를 요구하는데 Docker 29 의 최소는 1.40 이다. 에러는 조용하다(프로바이더가 그냥 아무것도 못 찾는다). → `traefik:v3.6`.

### 결함 D — console-web 헬스체크가 `localhost` 를 찌른다 → **콘솔에 라우트가 아예 없었다**

`HOSTNAME=0.0.0.0` 은 Node 를 IPv4 전용으로 바인딩하는데, alpine 의 `localhost` 는 ::1 로도 해소되고 **busybox wget 은 ::1 실패 후 IPv4 로 폴백하지 않는다**. 앱은 멀쩡한데 프로브만 죽는다. 그리고 **Traefik 은 healthy 가 아닌 컨테이너를 조용히 건너뛴다** — 오타 하나가 콘솔 전체를 데모에서 사라지게 했다. → `127.0.0.1`.

### 결함 E — 데모 도메인 `redirect_uri` 는 **등록될 수 없다**

OAuth2 는 `redirect_uri` 를 **정확 일치**로 검증하는데, 브라우저용 클라이언트의 콜백은 **Flyway 마이그레이션에 리터럴로** 박혀 있다(`console.local`, `web.ecommerce.local`, `fan-platform.local`). 데모 도메인은 부팅 때 IP 에서 파생되므로 마이그레이션이 알 수 없는 값이다.

미등록 `redirect_uri` 에 auth-service 가 돌려주는 응답이 특히 나쁘다:

```
HTTP/1.1 401
{"code":"UNAUTHORIZED","message":"Missing or invalid internal credentials"}
```

자격증명 문제가 아닌데 자격증명을 가리킨다. **등록된 `console.local` 로 같은 요청을 replay 하니 302** — 차이는 `redirect_uri` 하나뿐임을 그렇게 격리했다.

### 결함 F — SAS 로그인 폼은 게이트웨이 뒤에서 도달 불가능하다

등록된 `redirect_uri` 로 authorize 를 치면 게이트웨이가 이렇게 답한다:

```
HTTP/1.1 302
Location: http://auth-service:8081/login      ← 내부 컨테이너 DNS
```

Spring Authorization Server 는 로그인 리다이렉트를 **자기가 보는 요청 호스트**로 만드는데, 게이트웨이가 `http://auth-service:8081` 로 프록시하므로 그 이름이 그대로 브라우저에 나간다. 게다가 게이트웨이는 `/login` 에 **라우트 자체가 없다**(404).

⇒ **`iam.local` Traefik 경로의 콘솔 로그인은 원래부터 동작한 적이 없다.** 동작하는 CI e2e 는 Traefik 을 아예 우회해 `auth-service:8081` 을 issuer 로 쓴다 — 그래서 아무도 눈치채지 못했다.

---

# 설계

## 1) 호스트 접미사 파라미터화

```yaml
- "traefik.http.routers.platform-console.rule=Host(`console.${DEMO_DOMAIN:-local}`)"
```

기본값 `local` 이므로 **로컬 렌더 결과는 바이트 단위로 보존**된다(개발자 무영향).

**여기서 `${VAR:-default}` 를 쓰는 것은 MONO-346 의 교훈과 충돌하지 않는다.** 거기서 위험했던 것은 **빈 비밀번호가 조용히 통과**하는 것이었다. 도메인 접미사는 정반대다 — 값이 틀리면 **즉시 눈에 보이는 404** 이고, 비밀이 아니며, 기본값이 현재 동작을 정확히 재현한다.

## 2) EC2 에서는 `DEMO_DOMAIN=<ip>.sslip.io`

`<anything>.1.2.3.4.sslip.io` → `1.2.3.4` 로 해석되는 공개 와일드카드 DNS. **도메인 구매·DNS 설정·비용 0.** 인스턴스에 EIP 가 없어 start 마다 공인 IP 가 바뀌지만 **부팅 시 IMDSv2 로 읽어 주입**하므로 자동으로 따라간다(EIP 를 붙이면 정지 중에도 ~$3.6/월 추가).

## 3) Traefik 에 network alias — **AWS 때문에 필수다**

`console-web` 은 OIDC 코드 교환을 **서버사이드**로 한다(`${OIDC_ISSUER_URL}/oauth2/token`). 그 URL 은 **컨테이너 안에서도 해석돼야** 한다.

공용 DNS 로 `iam.<ip>.sslip.io` → EC2 **공인 IP** 인데, **AWS 는 인스턴스가 자기 공인 IP 로 보내는 트래픽을 되돌려주지 않는다**(hairpin 미지원). 즉 컨테이너에서 그 이름으로 나가면 죽는다.

→ Traefik 컨테이너에 **network alias** 를 건다:

```yaml
    networks:
      traefik-net:
        aliases:
          - console.${DEMO_DOMAIN:-local}
          - iam.${DEMO_DOMAIN:-local}
          - ...
```

같은 호스트명이 **브라우저에서는 공용 DNS → 공인 IP → Traefik**, **컨테이너에서는 Docker 임베디드 DNS → Traefik 컨테이너 IP** 로 해소된다. 한 이름, 두 경로, 같은 목적지.

**alias 목록은 수기 열거라 드리프트한다** → 가드 (i)가 봉인한다(아래).

## 4) issuer 와 jwk-set-uri 를 분리한다 — 이것이 핵심 열쇠

iam `application.yml` 이 이미 둘을 나눠 갖고 있고, `docker-compose.e2e.yml` 주석이 그 성질을 명시한다:

> `*.jwt.issuer` (must equal token **iss**) + `jwk-set-uri` (must be **network-reachable**)

**issuer 는 문자열 비교, jwk-set-uri 는 실제 네트워크 fetch.** 따라서:

| 설정 | 값 | 이유 |
|---|---|---|
| `issuer` / `OIDC_ISSUER_URL` | `http://iam.${DEMO_DOMAIN}` | 브라우저가 인가 엔드포인트로 **가야** 한다 |
| `jwk-set-uri` | `http://iam-auth-service:8081/oauth2/jwks` | 서비스가 **fetch** 한다(컨테이너 DNS 가 가장 짧고 안전) |

## 5) iam 게이트웨이를 Traefik 에 노출 — **데모 전용 오버레이로**

`docker-compose.e2e.yml` 에 Traefik 라벨을 넣으면 **CI 가 traefik-net 을 요구하게 된다**(CI 는 Traefik 을 띄우지 않는다). 그 파일은 CI 소유다.

→ **`infra/demo/iam-traefik.override.yml` 신설**하고 `projects.sh` 의 `COMPOSE[iam]` 에 덧붙인다. "프로젝트 compose 는 그대로 두고 데모 토폴로지는 데모 파일이 책임진다" 는 `demo.env` 와 **정확히 같은 원칙**이다.

## 6) 브라우저용 OIDC 경로만 auth-service 로 직행 (결함 F)

게이트웨이가 X-Forwarded-* 를 이해하고 재발신하게 만드는 길(프록시 2홉)은 각 홉이 원본 Host 를 보존해야 해서 깨지기 쉽다. 대신 **`/oauth2`·`/login`·`/.well-known` 만 Traefik 에서 auth-service 로 직행**시킨다(1홉).

- 게이트웨이 라우터와 **같은 호스트명**(`iam.${DEMO_DOMAIN}`)을 쓰고 PathPrefix 로 좁힌 뒤 `priority` 를 높여 앞세운다. 호스트가 하나여야 SAS 세션 쿠키(JSESSIONID)가 `authorize → /login → authorize` 왕복 내내 유지된다.
- `SERVER_FORWARD_HEADERS_STRATEGY: FRAMEWORK` 가 **동작 조건**이다. 없으면 Spring 이 X-Forwarded-* 를 무시하고 다시 내부 호스트로 리다이렉트한다. auth-service 의 `application.yml` 에는 이 설정이 없으므로 **제품 코드를 건드리지 않고 데모 오버레이에서 env 로만** 켠다.

## 7) 데모 도메인 `redirect_uri` 를 런타임에 등록 (결함 E)

마이그레이션은 정적이고 도메인은 런타임에 정해진다 → **`infra/demo/seed-demo-domain.sh`** 가 부팅 후 등록한다.

- **클라이언트 목록을 하드코딩하지 않는다.** `.local/` 을 담은 **모든** 등록 URI 를 찾아 `.${DEMO_DOMAIN}/` 로 치환한 사본을 덧붙인다(원본 유지 — 같은 DB 를 로컬 `*.local` 로도 쓸 수 있어야 한다). 새 클라이언트가 `.local` 콜백을 들고 와도 스크립트 수정이 필요 없다.
- `post_logout_redirect_uris` 도 함께. 이 값은 `client_settings` JSON 안에 Jackson default-typing 형태(`["java.util.ArrayList", [...]]`)로 있어 **실제 배열은 `[1]`** 이다(V0016/V0021 의 교훈).
- 멱등. `DEMO_DOMAIN=local` 이면 no-op.

---

# Scope

## In Scope

- 9개 프로젝트 compose 의 Traefik 라우터 규칙 17개 → `${DEMO_DOMAIN:-local}`.
- `infra/traefik/docker-compose.yml` — network alias(전 호스트명) + **`traefik:v3.2` → `v3.6`**(결함 C).
- **`infra/demo/iam-traefik.override.yml` 신설** — iam gateway-service 를 `Host(\`iam.${DEMO_DOMAIN:-local}\`)` 로 노출 + traefik-net 합류 + **auth-service OIDC 라우터/forward-headers**(결함 F).
- **`infra/demo/seed-demo-domain.sh` 신설** — 데모 도메인 `redirect_uri` 런타임 등록(결함 E). `demo-up.sh` 가 호출.
- `infra/demo/projects.sh` — `COMPOSE[iam]` 에 오버레이 추가.
- `infra/demo/demo.env` — `DEMO_DOMAIN=local` + console/iam OIDC·CORS 값을 `${DEMO_DOMAIN}` 기반으로 **일관되게** 주입.
- `projects/platform-console/docker-compose{,.e2e}.yml` — console-web 헬스체크 `localhost` → `127.0.0.1`(결함 D).
- `infra/demo/verify-demo-wrapper.sh` — **가드 (i)(j)(k)(l)**.
- `infra/traefik/README.md` · `infra/demo/README.md` 갱신.

## Out of Scope

- TLS/HTTPS (sslip.io + HTTP 로 충분; ACM/Let's Encrypt 는 별개 증분).
- EIP · 커스텀 도메인.
- `projects/*/docker-compose.e2e.yml` 의 CI 동작(무변경).
- `wms-notification-service` unhealthy(별개 티켓).

---

# Acceptance Criteria

- [ ] `grep -rE 'Host\(`[^`]*\.local`\)' projects/ infra/` → **0건** (`tasks/done/` 의 역사적 기록 제외 — HARDSTOP-05 상 편집 대상이 아니다).
- [ ] **로컬 회귀 0**: 9개 프로젝트 compose 를 **단독 렌더**(`DEMO_DOMAIN` 미설정)했을 때 `docker compose config` 결과가 변경 전과 **바이트 동일**. ← 개발자 무영향의 유일한 증거.
      **정확히 말한다**: *데모* 렌더(`demo.env` 를 source 한 상태)는 **의도적으로 바뀐다** — iam 이 Traefik 엣지를 얻고, OIDC issuer/jwks 가 재배선된다. **그게 이 task 의 수정 내용이다.** 둘을 뭉뚱그려 "전부 동일" 이라 주장하면 거짓이 된다.
- [ ] `DEMO_DOMAIN=1-2-3-4.sslip.io` 시 렌더에 그 호스트가 나타나고, **Traefik alias 목록과 Host() 목록이 정확히 일치**.
- [ ] **가드 4종 — 전부 mutation-check 필수.** 통과만으로는 가드가 무는지 알 수 없다.
      - **(i)** 렌더된 모든 `Host(...)` 호스트명이 Traefik network alias 에 존재한다(그 역도).
      - **(j)** IPv4-only 바인딩(`HOSTNAME=0.0.0.0`) ∧ 헬스체크가 `localhost` → FAIL (결함 D).
      - **(k)** 마이그레이션의 `.local` 콜백을 시드 치환이 전부 덮는가 + `demo-up.sh` 가 시드를 호출하는가 (결함 E). **vacuity 가드 포함** — 0건 발견 시 통과가 아니라 FAIL(grep 이 깨진 것이다).
      - **(l)** OIDC 라우터와 `SERVER_FORWARD_HEADERS_STRATEGY` 가 분리되지 않는가 (결함 F). 한쪽만 있으면 **라우팅은 되는데 로그인만 죽는다** — 가장 진단하기 나쁜 모양.
- [ ] **iam 이 Traefik 으로 라우팅**되고 `http://iam.${DEMO_DOMAIN}/actuator/health` = `{"status":"UP"}`, `/oauth2/jwks` = 200, discovery 의 `issuer` 가 데모 도메인.
- [ ] **실기동 로그인 증명 (이것만이 진짜 검증이다)**: EC2 에서 `http://console.<ip>.sslip.io/` → **OIDC 로그인 왕복 성공**. 왕복의 각 홉을 명시적으로 확인한다:
      1. console `/api/auth/login` → 302 to `http://iam.<D>/oauth2/authorize` (`redirect_uri=http://console.<D>/api/auth/callback`)
      2. authorize → 302 to **`http://iam.<D>/login`** — `auth-service:8081` 이 새어나오면 실패 (결함 F)
      3. `/login` 200 (SAS HTML 폼) → POST → 302 back to authorize
      4. authorize → 302 to `http://console.<D>/api/auth/callback?code=…` — **`redirect_uri` 시드가 없으면 여기서 401** (결함 E)
      5. 콜백 → 콘솔 세션 성립
      `docker compose config` 렌더도, 컨테이너 healthy 도 **이것을 증명하지 못한다**.
- [ ] CI GREEN (demo wrapper smoke 포함).

---

# Edge Cases

- **IMDSv2 필수** — 토큰 없는 `curl 169.254.169.254` 는 401. `X-aws-ec2-metadata-token` 선행.
- **sslip.io 표기** — 점(`1.2.3.4.sslip.io`)/하이픈(`1-2-3-4.sslip.io`) 둘 다 지원. `web.ecommerce.${DEMO_DOMAIN}` 처럼 이미 2단인 것과 합쳐지면 레이블이 길어지므로 **하이픈 표기 우선 검토**.
- **CORS** — `CORS_ALLOWED_ORIGINS` 는 이미 `${VAR}` 로 파라미터화돼 있다(다행). `demo.env` 에서 `${DEMO_DOMAIN}` 기반으로 조립.
- **iam 은 호스트 포트를 발행한다**(e2e 오버레이: 18080 등). Traefik 노출과 공존하나 Local Network Convention 위반이므로 주석으로 명시.

# Failure Scenarios

- **alias 를 빠뜨리면** 브라우저는 되는데 **console-web 의 서버사이드 토큰 교환만 실패**한다 → "로그인 버튼을 눌렀는데 콜백에서 죽음". 렌더 검사로는 **절대 안 잡힌다**. → 가드 (i) + 실기동 로그인.
- **issuer 를 컨테이너 DNS 로 두면** 브라우저가 인가 엔드포인트에 도달하지 못한다(현재 상태). 반대로 **jwk-set-uri 를 sslip.io 로 두면** AWS hairpin 부재로 서비스가 JWKS 를 못 받는다. **둘을 반드시 분리**한다.
- `Host()` 를 고치고 alias 를 잊으면 **로컬(hosts 파일)에서는 멀쩡히 동작**하므로 로컬 검증이 통과한다 — EC2 에서만 터진다.

# Test Requirements

- 정적: `verify-demo-wrapper.sh` (a)~(e),(g),(h),(i) PASS + 가드 (i) mutation-check.
- 렌더 동등성: `DEMO_DOMAIN` 미설정 시 변경 전후 `config` 출력 **byte-identical**.
- 실기동: EC2 `terraform apply` → `http://console.<ip>.sslip.io/` **브라우저 로그인 왕복**.

# Definition of Done

- [ ] 위 AC 전부
- [ ] CI GREEN
- [ ] `tasks/INDEX.md` done entry

---

# Provenance

2026-07-11~12, 온디맨드 데모를 AWS 에서 처음 부팅하며 발견. 결함 A 는 "방문자가 어떤 URL 로 들어오지?" 를 따라가다 나왔고(PoC 의 `site/index.html` 에 `DEMO_URL = "http://demo.example.com/"` 이라는 플레이스홀더가 남아 있던 자리), **결함 B 는 그 조사 중에 딸려 나왔다** — `iam.local` 이 6개 compose 의 기본값인데 **서빙하는 라우터가 없다.** 96 컨테이너가 전부 healthy 로 떠도 로그인은 불가능하다. **healthy 는 usable 을 뜻하지 않는다.**

선행: `TASK-MONO-353`(bitnami/kafka 삭제) — 그게 고쳐지기 전에는 스택이 33/96 에서 멈춰 이 조사 자체가 불가능했다.

분석=Opus 4.8 / 구현 권장=Opus (기계적 치환처럼 보이지만 OIDC issuer↔jwks 분리·AWS hairpin·CI 소유 파일 경계가 얽혀 있고, 잘못 고치면 **로컬에서는 통과하고 EC2 에서만 터진다**)
