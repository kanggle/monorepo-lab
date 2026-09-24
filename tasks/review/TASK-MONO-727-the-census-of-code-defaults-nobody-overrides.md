# Task ID

TASK-MONO-727

# Status

review (2026-09-24 UTC — AC-1 · AC-2 닫힘. 🔴 AC-3(결과 상태)은 창 — `TASK-MONO-672` 항목 15)

# Title

🔴 **«설정 없음 → 코드 기본값» 전수조사 — 726 의 형제 둘**: batch-worker 의 product-service 포트 · security-service 의 자동 잠금 주소

# Owner

monorepo

# Task Tags

- demo
- config-drift
- internal-caller

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet — 각각 compose 한 줄 + 가드 목록 한 줄. 새 자격증명 없음.

---

# Goal

`TASK-MONO-726` 에서 한 경로가 **다섯 곳**에서 같은 모양으로 끊겨 있었다 — «compose 가 값을 안 넘김 → application.yml 의 코드 기본값(없는 호스트 · 틀린 포트 · localhost) → 조용한 실패». `TASK-MONO-717` 도 같은 모양이었다. ⇒ 형제가 더 있는지 **데모 조합 전체**를 셌다.

# 전수조사 (2026-09-24 UTC · 창 없음 · 저장소만)

## 방법

- 모집단: `infra/demo/projects.sh` 의 `COMPOSE` 조합(도메인 8 · 데모가 실제로 쓰는 base + 오버레이). 서비스별 `environment` 를 파일 순서대로 합쳤다(YAML 앵커 병합 포함).
- 서비스 → 앱: `build.context` + `build.dockerfile` 로 `apps/<app>` 을 찾고, `application.yml` + 그 서비스의 `SPRING_PROFILES_ACTIVE` 프로필 파일을 읽었다.
- 술어: `${VAR:http…}` 기본값 중 **compose 가 `VAR` 를 넘기지 않는 것**, 그리고 기본값 호스트가 `localhost` · 조합에 없는 호스트 · 조합 안 서비스지만 **포트가 다른 것**.

## 결과 — 58건 중 47건이 술어에 걸렸고(26+8+9+2+1+1), 걸러 내면 **결함 2건**

| 부류 | 건수 | 판정 |
|---|---|---|
| OTLP `http://localhost:4318/v1/traces` | 26 | ⚪ 기능 결함 아님 — 이미 이름 붙은 관측 스팸(`env_docker_container_json_log_unbounded_otlp_spam`). 이 티켓 범위 밖 |
| 외부 API(Toss · tracker.delivery · PortOne · EasyPost · Goodsflow · Google/Microsoft JWKS) | 8 | ⚪ 기본값이 실제 외부 호스트 — 맞다 |
| iam 소셜 로그인 redirect(8) · 게이트웨이 CORS(1) 의 localhost | 9 | ⚪ 데모 경로가 아님(소셜 공급자 미등록). 이 티켓 범위 밖 |
| iam admin-service `OIDC_JWKS_URI` / `OIDC_ISSUER` | 2 | ⚪ **오탐** — 오버레이가 `ADMIN_OIDC_ISSUER`·`ADMIN_OIDC_JWKS_URI` 로 속성(`admin.oidc.*`)을 **직접** 채운다(relaxed binding). 2026-09-24 창에서 콘솔 로그인(= 그 토큰 교환)이 실제로 동작했다 |
| 🔴 **ecommerce batch-worker `PRODUCT_SERVICE_BASE_URL`** | 1 | **결함** — 아래 ① |
| 🔴 **iam security-service `ACCOUNT_SERVICE_BASE_URL`** | 1 | **결함** — 아래 ② |

### ① batch-worker → product-service: 포트가 틀렸다

- `batch-worker/application.yml:76` `product-service.base-url: ${PRODUCT_SERVICE_BASE_URL:http://product-service:8081}` — compose 가 안 넘김.
- product-service 는 **8082**(compose `expose` · `SERVER_PORT`). 8081 에는 아무것도 없다.
- 소비자: `SearchIndexConsistencyJob`(`ProductServiceClient`, `GET /api/products` — 공개, 인증 없음). ⇒ 검색 색인 정합성 잡이 데모에서 **매번** 연결 실패.
- 🔵 **726 과 같은 서비스의 셋째 틀린 기본값**이다(726: order 포트 8082 · 토큰 주소). batch-worker 의 주소 기본값 **넷 중 셋**이 틀렸다(`SEARCH_SERVICE_BASE_URL` 만 맞다).

### ② security-service → account-service: 자동 잠금이 자기 자신 쪽을 부른다

- `security-service/application.yml:128` `security.detection.auto-lock.account-service-base-url: ${ACCOUNT_SERVICE_BASE_URL:http://localhost:8081}` — iam 데모 조합(base + e2e + 오버레이 둘) 어디에도 이 변수도, relaxed 이름(`SECURITY_DETECTION_AUTO_LOCK_ACCOUNT_SERVICE_BASE_URL`)도 없다.
- security-service 는 **8084** 에서 돈다 ⇒ `localhost:8081` 은 연결 거부.
- 소비자: `AccountServiceClient.lock` — `POST /internal/accounts/{id}/lock`(의심 로그인 탐지 → 자동 잠금, TASK-BE-221). ⇒ 데모에서 **자동 잠금이 한 번도 적용될 수 없다**. 실패는 `security_auto_lock_failures_total` 카운터와 WARN 로그뿐.
- 🔵 형제의 값: 같은 파일의 admin-service 는 `ACCOUNT_SERVICE_URL: http://account-service:8082` 를 받는다(같은 iam-e2e 네트워크).
- 🔵 `TASK-MONO-726` ① 과 **같은 엔드포인트**(`/internal/accounts/{id}/lock`)를 다른 호출자가 부른다 — 726 ① 은 product-service 가 iam 게이트웨이를 거쳐(라우트 없음), 이것은 security-service 가 **직접**. 서로 다른 경로이므로 한쪽 고침이 다른 쪽을 고치지 않는다.

## ⚪ 이 전수조사가 **못 보는 것** (선언된 공백)

- `${VAR:…}` 가 아닌 **하드코딩 주소**(`base-url: http://x:1234`) — 플레이스홀더가 없으면 compose 로 덮을 수도 없다.
- `http` 가 아닌 기본값(`jdbc:` · `localhost:9092` 류 Kafka · Redis).
- relaxed binding 으로 **다른 이름**이 속성을 채우는 경우는 사람이 걸러야 한다(admin-service 가 그 사례). ⇒ 술어의 «걸림» 은 후보이지 판정이 아니다.
- 데모 조합 밖(nightly e2e 전용 compose · 로컬 bootrun).

# Scope

## 포함
- ① ecommerce compose batch-worker 에 `PRODUCT_SERVICE_BASE_URL=http://product-service:8082`.
- ② iam `docker-compose.e2e.yml` security-service 에 `ACCOUNT_SERVICE_BASE_URL: http://account-service:8082`.
- `scripts/check-internal-caller-addresses.sh` 목록에 두 행(각 서비스 · 그 키) + 두 행 bite.

## 제외
- OTLP 기본값 26건(별건 — 관측 스팸). 소셜 로그인·CORS localhost.
- 전수조사 술어를 가드로 만드는 것 — relaxed binding 오탐 때문에 «걸림 = 결함» 이 아니다. 가드는 **확인된 호출자 목록**만 든다(717 가드의 헤더 규칙).

# Acceptance Criteria

- [ ] **AC-1** — ① · ② 를 compose 에 싣는다. 🔴 값은 형제에서 가져온다(product-service 8082 · `account-service:8082`), 추측 금지.
- [ ] **AC-2** — 가드 목록 +2행, 각 행 키 삭제 bite → rc=1, 복원 → rc=0.
- [ ] **AC-3** — 🔴 **결과 상태 판정은 창**: ① 검색 색인 정합성 잡 로그에 연결 실패가 없고 잡이 완료를 기록하는가 · ② 의심 로그인을 일으켜 `account_db.accounts.status` 가 LOCKED 가 되는가(로그 침묵은 판정이 아니다). 창이 없으면 `TASK-MONO-672` 로.

# Related Specs

- `tasks/review/TASK-MONO-726-…` § AC-0 ② · 구현 기록(같은 모양의 다섯 겹)
- `tasks/done/TASK-MONO-717-…` (원형)
- `projects/iam-platform/tasks/done/TASK-BE-221-security-issue-autolock-usecase.md` (자동 잠금)
- `projects/ecommerce-microservices-platform/tasks/done/TASK-BE-409-batch-search-index-consistency-job.md`

# Related Contracts

- account-service 내부 API `POST /internal/accounts/{id}/lock` — 바꾸지 않는다(호출 주소만).
- product-api `GET /api/products` — 바꾸지 않는다.

# Edge Cases

| 상황 | 기대 |
|---|---|
| security-service 가 account-service 를 부를 때 토큰이 필요 | 이미 GAP client_credentials(BE-318)로 붙인다 — 주소만 고치면 된다. 창에서 401 이 나오면 **그때** 별건 |
| 데모가 아닌 nightly e2e 에서 자동 잠금을 검증하는 테스트가 있다 | 같은 e2e compose 를 쓰므로 그 테스트도 이 값을 받게 된다 — 지금까지 통과했다면 그 테스트가 lock 호출 **결과**를 안 봤다는 뜻(BE-505 가 «미대기 검증» 을 다뤘다). 창 판정과 별개로 기록만 |

# Failure Scenarios

1. **전수조사 술어를 그대로 가드로 만든다** → admin-service 같은 relaxed-binding 오탐이 빨강이 되고, 소음 가드는 꺼진다.
2. **값을 추측한다**(예: security-service 를 iam 게이트웨이로) → 717 이 밟은 그 모양(게이트웨이 경유 = 다른 규칙). 형제 값을 쓴다.
3. **배선을 고쳤다고 닫는다** → 717·718·721 이 연속으로 보인 대로 배선은 판정이 아니다. AC-3 이 창.

---

# 구현 기록 (2026-09-24 UTC · 분석=Opus 5.5)

## AC-1 — ✅ 형제 값으로 실었다

| # | 파일 | 추가 |
|---|---|---|
| ① | 에코머스 `docker-compose.yml` § batch-worker | `PRODUCT_SERVICE_BASE_URL=http://product-service:8082` (product-service `expose`/`SERVER_PORT` = 8082) |
| ② | iam `docker-compose.e2e.yml` § security-service | `ACCOUNT_SERVICE_BASE_URL: http://account-service:8082` (같은 파일 admin-service 의 `ACCOUNT_SERVICE_URL` 과 같은 값) |

## AC-2 — ✅ 가드 +2키 · bite

`check-internal-caller-addresses.sh`: batch-worker 행에 `PRODUCT_SERVICE_BASE_URL` 추가, security-service 행 신설(iam **e2e** compose — 이 서비스가 정의된 유일한 파일).

```
self-test 3칸 통과 · real checked=8 rc=0
bite batch-worker PRODUCT_SERVICE_BASE_URL 삭제      → rc=1 · DRIFT § batch-worker
bite security-service ACCOUNT_SERVICE_BASE_URL 삭제  → rc=1 · DRIFT § security-service
복원 → rc=0 (스테이지와 차이 0)
```

## 🔵 전수조사를 고친 트리에서 다시 쟀다 — 그리고 한 번 틀린 트리를 쟀다

- 고친 트리: **56건 · 수상 45건**, 두 결함이 목록에서 사라짐. main 트리 58 · 47 과 정확히 2씩 차이 ⇒ 대조군.
- 🔴 **첫 재측정은 main 체크아웃을 쟀다** — 스크립트의 `ROOT` 를 sed 로 바꾸려 했는데 역슬래시 이스케이프가 맞지 않아 치환이 **조용히 0건**이었고, 출력에 두 결함이 «그대로» 나왔다. `ROOT` 줄을 출력해 보고서야 알았다(이 저장소가 이름 붙인 «하네스가 어느 트리를 재는지 먼저 확정» 함정). Python 으로 치환하고 `assert count==1` 을 걸어 다시 쟀다.

## 게이트 기록

| 게이트 | 결과 |
|---|---|
| 두 compose YAML 파싱 | 🟢 |
| `check-internal-caller-addresses.sh` | 🟢 self-test · 실제 8키 · 새 두 키 bite |
| `infra/demo/verify-demo-wrapper.sh` (정적) | 🟢 rc=0 (정적 검증 PASS) |
| 필수 3종 | 🟢 rc=0 (스테이지 후) |

🔴 **안 돌린 것**: 통합 · e2e · 데모 창. 코드 변경은 없다(설정만).

## ⏳ AC-3 → `TASK-MONO-672` 항목 15

① 검색 색인 정합성 잡이 연결 실패 없이 완료를 기록하는가 · ② 의심 로그인을 일으켜 `account_db.accounts.status` 가 LOCKED 가 되는가. 🔵 **재굽기 불필요** — 둘 다 compose(클론) 변경이라 SSM 으로 `git pull` + 두 컨테이너 재생성이면 창에 올라간다.
