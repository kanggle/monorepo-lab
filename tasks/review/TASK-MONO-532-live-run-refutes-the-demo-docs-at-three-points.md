# Task ID

TASK-MONO-532

# Title

라이브 기동이 데모 문서를 세 지점에서 반증했다 — 면접관은 스토어프런트에 **도달조차 못 하고**, 콘솔 **첫 로그인이 실패**하며, 시드는 §5 가 약속한 배송·리뷰를 **만들지 않는다**

# Status

review

# Owner

monorepo

# Task Tags

- docs
- infra
- demo

---

# 배경 — 정적 게이트가 전부 초록인 채로 셋이 통과했다

2026-08-15, `demo-up.sh iam ecommerce console` 을 **실제로 띄워** 워크스루의 클릭 경로를
끝까지 밟았다. 도메인 동작 자체는 문서가 적은 대로였다 — 구매 완주 → 5행 사가 연쇄
(`payments COMPLETED` · `payment_outbox` published · `orders CONFIRMED` · `shippings PREPARING`
· `commission_accrual` 1행), 콘솔 `demo-corp` assume 시 `*_OPERATOR` 5개 파생, `ecommerce`
assume 시 상품 8 · 주문 1 · 배송 1(대조군 `demo-corp` 는 0 · 0 · 0).

🔴 **문제는 도메인이 아니라 그 앞이었다.** 아래 셋은 전부 **면접관이 첫 5분에 부딪히는**
지점인데, `verify-demo-wrapper.sh` 정적 검증은 **PASS** 였고 CI 도 초록이었다. 어느 것도
기존 게이트의 술어에 걸리지 않는다 — 셋 다 *"문서·설정이 약속한 것"* 과 *"기동한 것"* 의
차이이고, 그 둘을 대조하는 술어가 없었다.

🔵 이 태스크는 세 지점을 **각각 고치는 것**이지 새 가드를 세우는 것이 아니다(가드 필요성은
§ Out of Scope 참조).

---

# Goal

문서대로 셋업한 사람이 **문서대로 클릭해서** 세 표면에 도달하고, 콘솔에 한 번에 로그인하며,
§5·§6 이 서술하는 상태와 실제 시드 결과가 일치한다.

---

# 발견 ① — 진입 URL 3개 중 2개가 hosts 스니펫에 없다

[`TEMPLATE.md`](../../TEMPLATE.md) § One-time developer setup 의 스니펫은 8개 호스트명을 준다:

```
127.0.0.1  ecommerce.local wms.local iam.local fan-platform.local scm.local erp.local finance.local console.local
```

**`web.ecommerce.local` 과 `web.fan-platform.local` 이 없다.** 그런데 그 둘이 워크스루 §1
「진입 URL」 표가 주는 **스토어프런트·팬 웹의 주소**이고, compose 의 실제 Traefik 라우터도
그 이름이며(`traefik.http.routers.ecommerce-web.rule=Host(\`web.ecommerce.${DEMO_DOMAIN}\`)`),
`demo-up.sh` 의 마지막 출력 줄도 그 이름을 안내한다.

🔴 **같은 파일이 자기 자신과 어긋난다** — `TEMPLATE.md` § per-project bring-up matrix 의
ecommerce 행은 primary hostname 을 `ecommerce.local, web.ecommerce.local` 로 **정확히** 적어
두었다. 스니펫만 낡았다.

실측 (2026-08-15, 이 저장소 규약대로 셋업된 Windows 호스트):

```
console.local            127.0.0.1     ← 해소됨
ecommerce.local          127.0.0.1     ← 해소됨
web.ecommerce.local      해소 실패      ← 브라우저가 스토어프런트에 못 감
web.fan-platform.local   해소 실패      ← 팬 웹도 마찬가지
```

🔵 **Traefik 은 멀쩡했다.** `curl -H 'Host: web.ecommerce.local' http://127.0.0.1/` 는 **200**
이다. 즉 결함은 라우팅이 아니라 **문서가 알려주지 않은 이름**이고, 증상은 브라우저의
"사이트에 연결할 수 없음" 이라 **데모가 안 뜬 것처럼 보인다**. hosts 두 줄을 넣자 즉시
`web.ecommerce.local/products` **200 · 상품 카드 8개**가 렌더됐다.

---

# 발견 ② — 콘솔 첫 로그인이 콜드스타트에서 실패한다

콜드 기동 직후 `console.local` 로그인을 완주시키면 다음으로 끝난다:

```
http://console.local/login?error=operator_exchange_unavailable
```

console-web 로그가 원인을 정확히 말한다:

```
{"level":"warn","msg":"operator_exchange_timeout","timeoutMs":5000}
{"level":"warn","msg":"operator_exchange_unavailable_on_callback"}
```

`TOKEN_EXCHANGE_TIMEOUT_MS` 는 `console-web` 의 `env.ts` 에서 **기본 5000** 이고,
[`infra/demo/demo.env`](../../infra/demo/demo.env) 는 **이 값을 설정하지 않는다.** 콜드 IAM
(Spring Boot 첫 요청)은 그 5초를 넘긴다.

🔴 **같은 override 가 이미 저장소에 있다 — 데모에만 없다.** federation-hardening-e2e 쪽은
`TOKEN_EXCHANGE_TIMEOUT_MS: 30000` 오버레이를 갖고 있다(콘솔 콜드스타트 캐스케이드 대응으로
이미 한 번 겪은 문제다). 통합 데모는 그 학습을 물려받지 못했다.

**웜에서는 성공한다** — 같은 플로우를 한 번 더 밟으면 `http://console.local/console` 200 이고,
`POST /api/tenant {"tenant":"ecommerce"}` → `{"ok":true,"activeTenant":"ecommerce"}` 이후
프록시 원소 수가 products 8 / orders 1 / shippings 1 / sellers 2 / users 2 로 전부 찬다.

🔴 **그런데 화면은 "다시 시도하세요" 라고 말하지 않는다.** 면접관은 에러 페이지를 보고
데모가 고장났다고 읽는다. 그리고 워크스루 §7 트러블슈팅 표의

| 증상 | 먼저 볼 것 |
|---|---|
| 로그인 후 되돌아오지 못한다 | `infra/demo/seed-demo-domain.sh` 로그 — redirect_uri 가 데모 도메인에 등록됐는지 |

행이 **정확히 반대 방향으로 유도한다** — redirect_uri 는 멀쩡했다(실측). §6 은 콜드스타트를
`/ledger` **화면**의 503 으로만 적어 두었고, **로그인 자체가 실패한다**는 기록은 없다.

---

# 발견 ③ — 신선 볼륨에서 시드는 배송·리뷰를 만들지 않는다

깨끗한 볼륨에서 `seed.sh iam ecommerce` 를 돌린 실제 출력:

```
[seed:ecommerce] ⚠ 데모 계정의 주문이 없습니다 — 배송 진행과 리뷰 시드를 건너뜁니다
[seed:ecommerce] 진행할 배송 건이 없습니다 — 리뷰 자격(DELIVERED)을 만들지 않습니다
[seed:ecommerce] 요약 — 생성 12 · 기존 0 · 실패 0
```

**이것은 시드의 결함이 아니다** — `seed-ecommerce.sh` 는 주문을 만들지 않기로 **의도적으로**
설계됐고("아직 아무도 사지 않았다" 는 결함이 아니므로 경고만 남긴다), 그 판단은 옳다.

🔴 **틀린 것은 워크스루 §5 의 서술이다.** §5 는 이렇게 단언한다:

> 시드는 **도메인 규칙을 우회하지 않는다.** 리뷰는 배송 완료된 주문에만 쓸 수 있으므로,
> 시드는 리뷰 행을 직접 넣는 대신 **배송을 실제로 진행시켜**(PREPARING → SHIPPED →
> IN_TRANSIT → DELIVERED) 자격을 만든다. 그 과정에서 콘솔 「배송」 탭도 함께 찬다.

이 문장은 **구매 후 시드를 다시 돌렸을 때만** 참이다. 면접관이 처음 여는 데모에서
「내 리뷰」와 콘솔 「배송」은 **비어 있다**(그리고 §2 의 마이페이지 표는 「내 리뷰」를
아무 단서 없이 나열한다).

🔵 실측으로 이어붙이면: 구매를 완주시킨 **뒤** 시드를 재실행하면 §5 의 서술대로 배송이
진행되고 리뷰 자격이 생긴다. 즉 문장을 지울 게 아니라 **전제를 적어야 한다.**

---

# Scope

## In Scope

- **①** `TEMPLATE.md` § One-time developer setup 의 hosts 스니펫에 `web.ecommerce.local` ·
  `web.fan-platform.local` 추가. 워크스루 §1 이 "hosts 파일에 등록돼 있어야 한다" 며
  `TEMPLATE.md` 를 가리키므로, 고칠 곳은 **스니펫 한 곳**이다.
- **②** `infra/demo/demo.env` 에 `TOKEN_EXCHANGE_TIMEOUT_MS` 를 콜드스타트가 통과하는 값으로
  설정 + 워크스루 §7 트러블슈팅 표에 **이 증상의 행을 추가**(`/login?error=operator_exchange_unavailable`
  → console-web 로그의 `operator_exchange_timeout` 을 보라 / 재시도가 고친다).
  🔴 기존 "로그인 후 되돌아오지 못한다" 행은 **지우지 않는다** — 그 행이 서술하는
  redirect_uri 사건은 별개로 실재한다. 두 증상을 **구별하는 문구**를 넣는 것이 목표다.
- **③** 워크스루 §5 의 배송·리뷰 문단에 **전제를 명시**("구매가 선행돼야 한다 — 신선 볼륨
  첫 기동에서는 이 블록이 건너뛰어진다") + §2 마이페이지 표의 「내 리뷰」 행에 같은 단서.
- 세 건 모두 **한 PR**(전부 문서·설정이고 같은 라이브 런의 산물이라 리뷰 맥락이 하나다).

## Out of Scope

- 🔴 **"문서가 약속한 URL 이 실제로 열리는가" 가드 신설** — 유혹적이지만 술어가 서지 않는다.
  hosts 는 **개발자 머신의 상태**라 CI 러너에서 재기 불가능하고(러너에는 그 항목이 없다),
  Traefik 라우터 존재 여부는 이미 wrapper 가드 (i)/(v) 가 본다. 실제로 빠진 것은
  *"스니펫과 compose 라우터 목록이 일치하는가"* 이고, 그건 **별건**으로 세울 값어치가 있다
  (compose 의 `Host(...)` 를 전수 추출해 `TEMPLATE.md` 스니펫과 대조하는 술어). 이 태스크는
  먼저 **사실을 고친다**.
- **콘솔 콜드스타트 자체의 성능 개선** — 타임아웃 상향은 증상 완화다. JVM 워밍/프리로드는
  다른 축이고 이미 별도 계보(PC-FE-117/118/120)가 있다.
- **시드가 주문을 만들게 바꾸는 것** — §5 의 설계 판단("아무도 사지 않았다"는 결함이 아니다)을
  뒤집는 일이라 ADR 급이다. 여기서는 문서를 사실에 맞춘다.
- 팬 표면 실기동 검증 — 이번 런은 메모리 제약으로 `iam+ecommerce+console` 슬라이스만 띄웠다
  (스토어와 팬은 동시 기동 불가, §6). `web.fan-platform.local` 은 **DNS 해소 실패만** 확인했고
  그 뒤 화면은 안 봤다.

---

# Acceptance Criteria

- [ ] **AC-1** `TEMPLATE.md` 의 hosts 스니펫이 `web.ecommerce.local` 과 `web.fan-platform.local`
      을 포함한다. 같은 파일의 bring-up matrix(ecommerce 행)와 **불일치가 없다**.
- [ ] **AC-2** 저장소의 모든 compose 파일에서 `Host(\`...\`)` 로 선언된 **브라우저용** 호스트명을
      전수 추출했을 때, 그 집합이 `TEMPLATE.md` 스니펫에 **전부 들어 있다**(수동 대조 1회,
      결과를 PR 본문에 표로 남긴다 — 가드 신설은 Out of Scope 이므로 이 대조가 그 자리를 대신한다).
- [ ] **AC-3** `infra/demo/demo.env` 가 `TOKEN_EXCHANGE_TIMEOUT_MS` 를 설정하고, 그 값이
      console-web 컨테이너 환경에 **실제로 도달**한다(`docker compose config` 렌더 또는
      `docker exec … env` 로 확인 — 선언만으로 판정하지 않는다).
- [ ] **AC-4** 🔴 **콜드에서 재현 → 수정 후 소거**: 볼륨/컨테이너를 새로 띄운 **콜드 상태**에서
      로그인 1회차가 성공한다. 판정은 최종 URL 이 `/console`(에러 파라미터 없음)이고
      console-web 로그에 `operator_exchange_timeout` 이 **없는** 것. 🔴 웜 상태 측정으로
      대체하지 말 것 — 웜은 수정 전에도 통과한다(이 티켓의 실측이 그랬다).
- [ ] **AC-5** 워크스루 §7 에 `operator_exchange_unavailable` 증상 행이 있고, 기존
      redirect_uri 행과 **구별되는 판정 근거**(console-web 로그의 `operator_exchange_timeout`
      유무)를 적는다.
- [ ] **AC-6** 워크스루 §5 배송·리뷰 문단과 §2 「내 리뷰」 행이 **구매 선행 전제**를 명시한다.
- [ ] **AC-7** `bash infra/demo/verify-demo-wrapper.sh` 가 여전히 PASS(문서·env 변경이 기존
      가드를 깨지 않는다 — 특히 (g) 미설정 compose 변수 · (x) 결제 mock 정합).

---

# Related Specs

> **Before reading Related Specs**: 이 태스크는 monorepo-level 이다 — `tasks/INDEX.md`
> § "When to Use Root vs Project Tasks" 를 따른다. `TEMPLATE.md` · `infra/demo/` ·
> `docs/guides/` 는 전부 shared 경로이므로 **HARDSTOP-03**(프로젝트 고유 내용 금지)을
> 유의한다 — 단 `infra/demo/` 는 데모 조립 지점이라 도메인 이름이 이미 들어 있다.

- [`TEMPLATE.md`](../../TEMPLATE.md) § Local Network Convention / § One-time developer setup
- [`docs/guides/interview-demo-walkthrough.md`](../../docs/guides/interview-demo-walkthrough.md)
  §1 · §2 · §5 · §6 · §7 — 🔵 이 문서는 **사람용 참조**이고 AI 의 소스오브트루스가 아니다
  (`CLAUDE.md` § Source of Truth Priority). 여기서는 **수정 대상**이지 근거가 아니다.
- [`infra/demo/demo.env`](../../infra/demo/demo.env) · [`infra/demo/verify-demo-wrapper.sh`](../../infra/demo/verify-demo-wrapper.sh)
- [`docs/adr/ADR-MONO-001-port-prefix-scaling.md`](../../docs/adr/ADR-MONO-001-port-prefix-scaling.md) — 호스트명 라우팅의 근거

# Related Contracts

- 없음 (문서 · 데모 설정 변경. API/이벤트 계약 무변경)

---

# Edge Cases

- **`DEMO_DOMAIN` 이 `local` 이 아닌 경우(EC2 `<ip>.sslip.io`)** — hosts 등록이 필요 없다.
  스니펫 수정은 로컬 경로에만 영향을 준다. §1 이 이미 그렇게 적고 있으므로 그 서술을 깨지 않을 것.
- **타임아웃을 올리면 진짜 장애가 늦게 드러난다** — IAM 이 죽어 있으면 5초 대신 올린 값만큼
  매달린다. 값 선택 시 이 트레이드오프를 PR 본문에 적을 것(무한정 크게 잡지 말 것).
- **hosts 스니펫을 늘리면 붙여넣기 한 줄이 길어진다** — 한 줄에 몰지 말고 가독성 있게 나눌 것.
- **`web.fan-platform.local` 은 팬 스택이 떠 있을 때만 응답한다** — hosts 등록은 이름 해소일
  뿐이고, 스택이 없으면 Traefik 404 다. 스니펫에 넣는 것과 별개이니 혼동하지 말 것.

---

# Failure Scenarios

- 🔴 **AC-4 를 웜에서 재고 통과로 적는다** — 이 티켓이 실측한 그대로다: 1회차 실패 / 2회차 성공.
  콜드가 아니면 **수정 전에도 초록**이라 그 측정은 아무것도 증명하지 않는다.
- 🔴 **§7 의 기존 redirect_uri 행을 새 행으로 덮어쓴다** — 두 증상은 다른 사건이다. 덮으면
  이번엔 반대 방향의 오진을 만든다.
- 🔴 **스니펫만 고치고 bring-up matrix 와의 정합을 안 본다** — 이 결함의 정체가 *"같은 파일이
  자기와 어긋났다"* 이므로, 한쪽만 고치면 같은 종류의 드리프트가 남는다.
- **`demo.env` 에 값을 넣었는데 console-web 이 그것을 안 읽는다** — compose 가 그 변수를
  console-web 서비스에 전달하도록 배선돼 있는지 확인하지 않으면 "선언은 했는데 도달하지 않는"
  상태가 된다. AC-3 이 이것을 막는다.

---

# Verification (이 태스크의 "테스트")

순수 문서·설정 변경이라 유닛 테스트가 없다. 대신 **라이브 판정**을 DoD 로 삼는다:

1. `bash infra/demo/verify-demo-wrapper.sh` → PASS (AC-7)
2. 콜드 기동(`demo-down.sh` 후 `demo-up.sh iam ecommerce console`) → 콘솔 로그인 **1회차** 성공 (AC-4)
3. `docker exec platform-console-web env | grep TOKEN_EXCHANGE_TIMEOUT_MS` → 값 존재 (AC-3)
4. compose `Host(...)` 전수 추출 ↔ 스니펫 대조표를 PR 본문에 (AC-2)

---

# AC-2 실행 결과 (2026-08-15) — 🔴 누락이 2개가 아니라 **6개**였고, 반대 방향 드리프트도 하나 나왔다

전 compose 의 `Host(...)` 를 전수 추출해 `TEMPLATE.md` 스니펫과 대조했다.
**티켓 작성 시점에 알던 것은 2건뿐이었다.**

| 라우터 호스트명 | 소유 compose | 수정 전 스니펫 | 판정 |
|---|---|---|---|
| `ecommerce.local` · `wms.local` · `iam.local` · `fan-platform.local` · `scm.local` · `erp.local` · `finance.local` · `console.local` | 각 프로젝트 게이트웨이 | ✅ 있음 | 확인함 |
| **`web.ecommerce.local`** | ecommerce (web-store) | ❌ **없음** | 추가 |
| **`web.fan-platform.local`** | fan (fan-platform-web) | ❌ **없음** | 추가 |
| **`grafana.iam.local`** | iam | ❌ **없음** | 추가 |
| **`grafana.wms.local`** | wms | ❌ **없음** | 추가 |
| **`kafka.iam.local`** | iam | ❌ **없음** | 추가 |
| **`kafka.wms.local`** | wms | ❌ **없음** | 추가 |

🔵 뒤 넷(grafana / kafka UI)은 **브라우저용 HTTP UI** 인데 스니펫에 없었다. `TEMPLATE.md`
§ Database / queue tools 는 **TCP 도구**(DBeaver · Redis Insight)만 다루고 이 HTTP UI 들은
어디에도 적혀 있지 않았다 ⇒ 같은 결함의 덜 아픈 사례. 선택 항목으로 표시해 추가했다.

## 🔴 반대 방향 — 문서가 **없는 라우터**를 약속한다 (`ledger.local`)

추출 집합에 `ledger.local` 이 **없다**. 그런데 두 문서가 그것을 진입 호스트명으로 적고 있었다:

- `TEMPLATE.md` bring-up matrix, finance 행 → `finance.local, ledger.local`
- `projects/finance-platform/docs/onboarding/local-dev.md` 2곳

`TASK-MONO-357` / `ADR-MONO-048` 이 finance 에 게이트웨이를 세우면서 `ledger-service` 를
`expose:` 전용으로 바꿨다(백엔드가 공유 엣지에 라우터를 갖는 것은 `api-gateway-policy.md`
L13/L14 위반). **라우터는 그때 사라졌고 문서만 남았다.**

⇒ 이 방향도 함께 고쳤다. 🔵 프로젝트 문서(`projects/finance-platform/docs/`)를 root 태스크가
건드리는 근거는 `CLAUDE.md` § Required Workflow(monorepo-level)의 *"enumerate any
`projects/<name>/` impact"* 다 — 같은 사실의 두 사본이라 한쪽만 고치면 드리프트가 남는다.

🔵 **교훈**: 이 티켓의 발단은 *"2개 빠졌다"* 였는데 세어 보니 **6개 + 반대 방향 1개**였다.
하나를 발견한 것은 다 발견한 것이 아니다 — 모집단을 **다시 세는** 단계가 AC 에 있어서 잡혔다.

---

# 구현 노트 (2026-08-15)

- **①** `TEMPLATE.md` 스니펫을 3블록(게이트웨이 / 브라우저 프런트엔드 / 도구 UI)으로 나누고,
  `web.*` 가 게이트웨이와 **다른 라우터**라는 것과 판별법(Host 헤더 200 ↔ 브라우저 실패)을
  적었다. bring-up matrix 의 fan 행에 `web.fan-platform.local` 추가, finance 행에서
  `ledger.local` 제거(각주 ² 로 사유 보존). 워크스루 §1 에도 같은 함정을 경고로 넣었다.
- **②** compose 는 이미 `${TOKEN_EXCHANGE_TIMEOUT_MS:-5000}` / `${REGISTRY_TIMEOUT_MS:-5000}`
  를 전달하고 있었다 — **배선은 있었고 값만 없었다**. `demo.env` 에 둘 다 `30000` 설정.
  값 근거와 트레이드오프(진짜 장애 시 30초 매달림)를 주석에 남겼다.
  🔵 `REGISTRY_TIMEOUT_MS` 도 함께 올린 이유: 콜드 캐스케이드는 두 교환을 **모두** 친다
  (registry + operator-exchange). 하나만 올리면 나머지가 같은 자리에서 끊는다.
- **③** §5 에 전제 블록(시드 로그 2줄 + 재시드 명령), §2 「내 리뷰」 행에 단서, §7 에 증상
  2행 추가. 🔴 기존 `redirect_uri` 행은 **지우지 않고** 구별 문구를 달았다.

---

# AC-3 / AC-4 실행 결과 (2026-08-15) — 🔴 콜드에서 재현·소거했다

**AC-3 (값이 도달하는가)** — 선언이 아니라 컨테이너에서 확인:

```
수정 전 (라이브 컨테이너)  TOKEN_EXCHANGE_TIMEOUT_MS=5000   REGISTRY_TIMEOUT_MS=5000
수정 후 (재생성 후)        TOKEN_EXCHANGE_TIMEOUT_MS=30000  REGISTRY_TIMEOUT_MS=30000
```

**AC-4 (콜드 1회차)** — `iam-admin-service` 를 재시작해 콜드를 만들고(health UP 까지 **39초**),
UP 직후 **첫 로그인**을 쟀다:

```
1회차 최종 URL : /dashboards/overview        ← 에러 파라미터 없음
1회차 소요     : 16,907 ms
console-web 로그: operator_exchange_ok       (operator_exchange_timeout 건수 = 0)
POST /api/tenant {"tenant":"ecommerce"} → 200
```

🔴 **이 숫자가 판정의 전부다: 16.9초는 옛 한계 5초의 3배가 넘는다.** 즉 이 런은 수정 전이었다면
`operator_exchange_timeout` 으로 **반드시 실패했다**. 오늘 오전 같은 스택에서 잰 수정 전 1회차가
정확히 그랬다(`/login?error=operator_exchange_unavailable`). 웜 재시도는 양쪽 다 성공하므로
**웜으로는 이 차이를 볼 수 없다** — AC 가 콜드를 요구한 이유다.

🔵 **판정 중 내 술어가 두 번 틀렸다** (둘 다 결함이 아니었다):
- admin-service 프로브를 **8080** 으로 짰다 — 실제 포트는 **8085**(컨테이너 healthcheck 가
  권위다). "연결 거부" 를 "아직 안 떴다" 로 읽고 무한 대기했다.
- 세션 쿠키를 `authjs.session-token` 이름으로 찾았다 — 그건 **스토어프런트**의 이름이고
  콘솔은 다르다. 로그인은 `/dashboards/overview` 도달과 `POST /api/tenant` **200** 으로
  이미 증명돼 있었다.

🔵 **회귀 아님 확인**: 직후 `/api/ecommerce/shippings` 가 **503** 을 냈으나 웜 3회 연속
**200 · `totalElements` 1** 이고 게이트웨이 직결(운영자 토큰)도 **1** 로 일치한다 ⇒ 워크스루
§6 이 이미 적은 **콜드 BFF 레그**이지 이번 변경의 산물이 아니다.

**AC-7** — `bash infra/demo/verify-demo-wrapper.sh` → **정적 검증 PASS**(rc=0, FAIL 0건).

---

# Definition of Done

- [x] AC-1 ~ AC-7 충족
- [x] AC-2 전수 대조 실행 + 결과 기록(위 표)
- [x] `tasks/INDEX.md` 갱신
- [x] Ready for review
