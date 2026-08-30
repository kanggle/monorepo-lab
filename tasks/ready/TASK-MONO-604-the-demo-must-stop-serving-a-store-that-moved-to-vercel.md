# Task ID

TASK-MONO-604

# Title

데모 호스트가 **Vercel 로 옮겨간 스토어를 계속 서빙**한다 — 그런데 그 compose 는 로컬·CI 도 쓴다

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- demo
- ci

---

# 배경 — `TASK-MONO-581` ⑤ 에서 분리됐다 (2026-08-29)

`ADR-MONO-067` 단계 2 가 web-store 를 Vercel 로 옮겼고, `TASK-MONO-583` 이 론처 링크를,
`TASK-MONO-603` 이 가시성을 넘겼다. **그런데 데모 호스트는 여전히 자기 사본을 서빙한다.**

실측 (2026-08-29T16:11Z, 데모 running):

| | |
|---|---|
| `http://web.ecommerce.3-38-176-240.sslip.io/` | **200** — 데모 호스트 사본 |
| `https://store.hubwang.com/products` | **200** — Vercel 판, 시드 상품 8개 |

⇒ **둘 다 살아 있다.** 방문자 경로는 Vercel 로 갔으므로 데모 사본은 이제 **아무도 안 보는
40여 개 컨테이너 중 하나**이고, 32GB 인스턴스에서 그 메모리는 공짜가 아니다
(`TASK-MONO-552` AC-0: 정상 상태 31.5 GiB 중 **약 29 GiB 사용 · MemAvailable 2.4 GiB**).

## 🔴 581 이 이 일의 크기를 과소평가했다

581 의 표는 ⑤ 를 *"재굽기 ✅ 기동 ✅"* 로 적었다 — 마치 재굽기 창 안에서 끝나는 일처럼.
**아니다.**

```
projects/ecommerce-microservices-platform/docker-compose.yml:1218   web-store:
```

그 파일은 **데모 전용이 아니다.** 같은 파일을 쓰는 다른 소비자:

| 소비자 | 근거 |
|---|---|
| 로컬 스택 | `docs/guides/interview-demo-walkthrough.md:63` — `http://web.ecommerce.local` 을 방문 경로로 적는다 |
| CI | 잡 `Frontend E2E smoke (web-store + fan-platform-web + console-web, Playwright)` |

⇒ **거기서 서비스를 지우면 데모만이 아니라 로컬 워크스루와 CI e2e 가 같이 죽는다.**

🔴 그리고 **라이브 `docker rm` 은 답이 아니다** — 다음 부팅에 AMI 안의 compose 가 다시 만든다.
이 일의 산출물은 **영속적인 선언**이어야 한다.

---

# Goal

**데모 프로파일에서만** web-store 를 띄우지 않는다. 로컬과 CI 는 그대로 둔다.
그리고 그 억제가 **어디에 선언됐는지** 한 곳에서 읽히게 한다.

---

# Scope

**In:**

- 데모 전용 억제 기전 (구현 판단 — 후보는 § Edge Cases)
- `infra/demo/verify-demo-wrapper.sh` — 억제가 **실제로 걸리는지** 실행 대조
- 억제 후 `web.ecommerce.<DEMO_DOMAIN>` 이 404 가 되는 것을 **정상으로** 문서화

**Out:**

- `projects/ecommerce-microservices-platform/docker-compose.yml` 에서 서비스 **삭제** — 금지.
  로컬·CI 가 죽는다.
- 론처 링크·가시성 — `TASK-MONO-583` · `TASK-MONO-603` 에서 끝났다
- console·fan 의 이관 — 단계 3·4

---

# Acceptance Criteria

## AC-0 — 전제 재측정 (**착수 전**)

1. `https://store.hubwang.com/products` 가 **2xx 이고 실데이터를 렌더**하는지 확인한다.
   🔴 **이것이 선행이다** — Vercel 판이 죽어 있는데 데모 사본을 끄면 방문자는 **어느 쪽에서도**
   스토어를 못 본다. (`TASK-MONO-581` § AC-4 정정이 이름 댄 술어이고, 조건은 「580 머지」가
   아니라 **「Vercel 판이 실제로 서빙된다」**이다.)
2. 그 compose 를 읽는 소비자를 **다시 전수한다** — 이 티켓이 적은 둘(로컬 워크스루 · CI e2e)이
   여전히 전부인지. 🔴 하나라도 늘었으면 억제 기전의 선택이 달라진다.

## AC-1 — 억제는 **데모 프로파일에서만** 건다

- 로컬 `docker compose up` 은 **영향 없음**
- CI 의 `Frontend E2E smoke` 는 **영향 없음**
- 🔴 억제가 어디에 선언됐는지 **한 곳**에서 읽혀야 한다. 두 벌이면 하나만 고쳐진다.

## AC-2 — 가드가 **실행해서** 대조한다

- 데모 렌더에 `web-store` 가 **없다**는 것을 `docker compose config` 로 확인한다
  (파일 grep 이 아니라 **렌더 결과**다 — 오버라이드는 병합 후에야 판정된다).
- 🔴 **대조군**: 같은 검사를 **로컬 프로파일**에 걸면 `web-store` 가 **있어야** 한다.
  한쪽만 보면 「전부 껐다」와 구별되지 않는다.
- 🔴 **bite**: 억제를 지우면 가드가 문다.

## AC-3 — 부팅 판정과의 정합

`TASK-MONO-583` 이 `demo-up.sh` 를 고쳐 **데모 호스트 행만** 찌르게 했고, `web.ecommerce` 는
이미 `data-served="vercel"` 이라 **찌르지 않는다**(2026-08-29 실기동 확인:
`[demo] ✔ HTTP 표면 2/2: console=307 web.fan-platform=307`).

🔵 **그러므로 이 티켓은 부팅 판정을 안 건드린다** — 583 이 먼저 가서 길을 냈다.
🔴 **반대로: 583 이 없었다면 이 티켓이 부팅을 영구히 못 끝내게 만들었을 것이다.**
그 순서를 기록해 둔다(다음 사람이 583 을 «불필요한 선행» 으로 읽지 않도록).

## AC-4 — 라이브 확인 (**다음 기동 창**)

- `web.ecommerce.<DEMO_DOMAIN>` → **404** (전환 완료의 신호)
- 부팅이 정상 종료하고 `HTTP 표면 2/2` 가 그대로인지
- 🔵 이 칸은 **재굽기가 선행**이다(억제는 AMI 의 compose 에 들어가야 효력이 있다).
  ⇒ 다음 재굽기 번들에 묶어라. **이 티켓만을 위해 창을 열지 마라.**

---

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) § 단계 2
- `TASK-MONO-581` § ⑤ — 이 티켓이 그 분리분이다
- `TASK-MONO-583` · `TASK-MONO-603` — 링크와 가시성. 이미 끝났다

# Related Contracts

없음.

---

# Edge Cases

- **억제 기전의 후보**: ⑴ `infra/demo/*.override.yml` 에서 `web-store` 를
  `deploy.replicas: 0`(compose v2 에서 무시될 수 있다 — 확인 필요) ⑵ `demo-up.sh` 가
  `--scale web-store=0` 을 붙인다 ⑶ 기본 compose 에 `profiles:` 를 달고 로컬·CI 만 그
  프로파일을 켠다. 🔴 ⑶ 은 **로컬 기본 동작을 바꾼다** — walkthrough 를 같이 고쳐야 한다.
- 🔴 **`--scale` 은 `demo-up.sh` 안에만 있으면 `docker compose config` 로 안 보인다.**
  AC-2 의 술어가 렌더 결과를 본다면 ⑵ 는 그 술어로 판정 불가다 — **기전과 술어를 같이 고르라.**
- 🔴 web-store 컨테이너가 사라지면 그 도메인의 **헬스 스냅샷 구성이 바뀐다.** 론처의
  `data-domain="ecommerce"` 행은 이제 `data-served="vercel"` 이라 헬스를 안 보지만,
  `demo-status.sh` 의 도메인 판정(`partial` 계산)은 여전히 그 스택을 센다 — **`partial` 이
  덜 뜨는지 더 뜨는지** 확인하라.
- ecommerce 스택의 다른 서비스가 web-store 에 `depends_on` 을 걸고 있으면 억제가 그 서비스를
  같이 막는다. **렌더로 확인하라.**

# Failure Scenarios

| 실패 | 증상 | 방어 |
|---|---|---|
| 기본 compose 에서 삭제 | 로컬 워크스루·CI e2e 가 죽는다 | Scope Out · AC-1 |
| 라이브 `docker rm` 으로 끝냄 | 다음 부팅에 되살아난다 | Goal — 영속 선언 |
| 억제만 하고 가드 없음 | 다음 사람이 되돌려도 **아무도 모른다** | AC-2 |
| 대조군 없이 「없음」만 확인 | 「전부 껐다」와 구별 불가 | AC-2 둘째 칸 |
| Vercel 판이 죽은 채로 억제 | 방문자가 **어느 쪽에서도** 못 본다 | AC-0 ① |
