# Task ID

TASK-MONO-632

# Title

🔴 **이관은 끝났는데 「기능이 도는가」는 한 번도 안 쟀다.** 지금까지 잰 것은 **HTTP 도달성**(200/307)뿐이고, 그것은 «페이지가 응답한다» 이지 «기능이 돈다» 가 아니다 — 그리고 마지막 기능 측정 이후 **콘솔 억제**와 **AMI 9차 재굽기**가 지나갔다

# Status

done

# Owner

monorepo

# Task Tags

- demo
- live-verification
- measurement
- adr-067

---

# Goal

`ADR-MONO-067`(데모 방문자 화면 Vercel 이관)의 **모든 작업이 끝났다**
(`TASK-MONO-630` 재굽기 · `TASK-MONO-631` 기록 정정). 남은 질문은 **하나**다:

> **그 이관 뒤에 기능이 실제로 도는가.**

이 티켓은 그 질문을 **기동 창 한 번**으로 답한다. 코드를 고치는 티켓이 아니라 **재는**
티켓이다 — 결함이 나오면 **별도 티켓**으로 기안하고 여기서 고치지 않는다.

## 🔴 왜 «이미 초록이다» 가 답이 아닌가 — 세 증거를 각각 기각한다

| 이미 있는 증거 | 무엇을 재나 | 왜 이 질문의 답이 아닌가 |
|---|---|---|
| 라이브 프로브 `200`/`307` (`626`·`630`) | **도달성** | 「페이지가 응답한다」다. 🔴 세션 없는 요청도 로그인 페이지에서 **200** 이고, `/api/payment-config` 는 세션 有 `200/20B` · 無 `200/10,999B` 로 **둘 다 200** 이었다(`TASK-MONO-622` 실측) |
| nightly e2e **14/14 초록** | **CI 안의 스택** | 그 스위트는 CI 러너의 docker compose / Testcontainers 를 잰다. **Vercel + EC2 배포 경로를 하나도 안 지난다** |
| `check-ami-generation.sh` rc=0 | **구운 세대 == 서빙 세대** | 「배포된 이미지가 최신 커밋에서 구워졌다」다. 🔴 *구워진 것이 도는 것은 아니다* — 이 저장소가 «선언 ≠ 배선» 으로 여러 번 대가를 치른 축이다 |

## 🔴🔴 그리고 **콘솔은 억제 이후 기능 검증이 0회**다

마지막 기능 판정은 **`TASK-MONO-622` 기동 창 #5 (2026-09-05)** 이고 그 창이 판정한 것은
**팬**이다(`/` 개체 4 · `/artists` 3). 그 뒤에 두 가지가 일어났다:

- **`TASK-MONO-627`** — 데모 호스트의 콘솔 사본 **억제**. 이제 `console.hubwang.com` 은
  **Vercel 사본이 유일**하고, `console-bff` 를 지나는 레그 3개가 **영구 degrade** 된다.
- **`TASK-MONO-630`** — **AMI 9차 재굽기**(`ami-029c40fb18c63816b`, 출처 `ami-tag`).
  즉 지금 도는 데모는 **그 누구도 기능을 재 본 적 없는 이미지**다.

⇒ 「콘솔이 Vercel 하나로 좁혀진 뒤에도 도메인 화면이 실데이터를 그리는가」는
**한 번도 측정된 적 없는 축**이다. 이 티켓의 무게중심이 거기 있다.

# Scope

**포함**:

- **기동 창 1회** — `POST /start` → 웜업 → 측정 → `POST /stop`. 예산 소비를 기록한다.
- 판정 칸 **0~3 + 대조군**(아래 § 판정 칸). 측정 결과는 **이 티켓 본문**(`in-progress/`
  단계의 § 구현)에 착지한다.
- 측정 결과가 기존 기록을 **거짓으로 만들면** 그 자리를 고친다:
  `docs/guides/interview-demo-walkthrough.md` § 6 · `docs/adr/ADR-MONO-067-*.md` § Verification.
- 결함 발견 시 **별도 티켓 기안**(`ready/`) — 이 티켓에서 고치지 않는다.

**제외**:

- 🔵 **콘솔 3패널(운영 개요 · 도메인 상태 · 알림 인박스)의 502 수리.** `docs/guides/
  interview-demo-walkthrough.md` § 6 에 **등재된 설계상 한계**이고 고치려면 아키텍처
  결정이 필요하다. 🔴 **이것을 FAIL 로 세지 마라 — 진짜 결함을 가린다.**
- 🔵 **AMI 재굽기.** 이 창은 as-baked 이미지를 재는 창이다. 「측정이 먼저, 재굽기가
  나중」(`project_ondemand_demo_aws_poc` § 규율 ①) — 먼저 구우면 피험체를 없앤다.
- 🔵 **`ADR-MONO-068` § D(EIP)** — 소유자가 아직 안 고른 선택지. 건드리지 않는다.
- 🔵 **인스턴스 내부 측정(SSM)** — `TASK-MONO-624` V6 의 나머지 11 소비자 축. 별개.

# 🔍 기안 시점 실측 (2026-09-07 UTC — 🔴 착수 시 AC-0 이 **다시 잰다. 상속 금지**)

```
GET  https://r1tljg51qa.execute-api.ap-northeast-2.amazonaws.com/status
  → {"state": "stopped", "ip": null, "used_minutes": 366, "budget_minutes": 600}

bash infra/demo/aws/check-ami-generation.sh          → rc=0  («같은 세대»)
  핀 AMI = ami-029c40fb18c63816b · 구운 커밋 3bc182ecd4e8 · 출처 = ami-tag
  구운 세대 3bc182ecd  vs  서빙 세대 ff866320f  → 계약 파일 6개 전부 일치

DNS: auth.hubwang.com · console.hubwang.com  → 216.150.x (Vercel anycast)
git: main = ff866320f · clean · 열린 PR 0 · 브랜치 main 뿐
큐: root ready 2건(328 no-op DEFERRAL / 587 ⏳ 09-28) · in-progress 0 · review 0
```

⇒ **예산 잔량 234분.** 창 #5 는 14분, 창 #3·#4 는 17·20분이었다.

# 판정 칸 (창 #5 술어 승계 — `TASK-MONO-622`)

| 칸 | 대상 | PASS 기준 |
|---|---|---|
| **0 유효성(전제)** | console · fan | 로그인 왕복이 **세션까지** 성립. 판정은 «200 이 나왔다» 가 **아니라** 보호 화면이 **세션 有 `200` / 無 `307 → /login`** 으로 **갈리는 것**. 🔴 FAIL 이면 뒤 칸들은 «실패» 가 아니라 **«측정 안 됨»** 이다 |
| **1 fan 실데이터** | `fan.hubwang.com` | `/` 개체 > 0 · `/artists` 개체 > 0 · 에러 문구 **0**. 개체의 «모양» 은 라우트마다 다르다(피드는 `/posts/<id>`) — 창 #5 가 이 함정을 밟았다 |
| **2 store** | `store.hubwang.com` | `/products` 의 **상품 수 · 가격 수** > 0 |
| **3 console 6개 도메인 화면** | `console.hubwang.com` | 🔴 **억제 이후 처음 재는 축.** 백엔드 여섯(`iam`·`wms`·`scm`·`finance`·`erp`·`ecommerce`)마다 **리스트 API 한 개**의 **원소 수**. 🔴 «200» 도 «degraded 마커 부재» 도 판정이 아니다 — **원소 수만** 판정이다(§ 6 `TASK-BE-576` 선례) |
| **음성 대조군** | fan | `fan.hubwang.com/artists-does-not-exist-XXX` = **404** ⇒ 200 들이 catch-all 이 아님을 증명 |
| **교대** | 전부 | **B, A, B, A 두 패스** — 웜업 드리프트가 결과를 만들지 않았음을 증명. 인터리브 **안에서만** 비교 |

## 🔴 칸 3 의 테넌트 축 — 한 세션으로는 못 잰다

`docs/guides/interview-demo-walkthrough.md` § 4 실측: **`demo-corp` 는 권한**(5개 도메인
운영 섹션이 열린다), **`ecommerce` 는 가시성**(스토어프런트가 쓰는 행이 사는 테넌트).
⇒ 칸 3 은 **테넌트를 바꿔 가며** 재야 하고, **어느 테넌트로 쟀는지를 칸마다 적어야 한다.**
🔴 안 적으면 `TASK-BE-576` 이 이름 붙인 실패(«엣지는 초록, 목록만 비어 있음»)를
**«기능 고장» 으로 오독**한다.

# Acceptance Criteria

- [x] **AC-0 (verify-then-act — 착수 시 재측정. 🔴 위 § 실측을 상속하지 마라)**
  - [x] ① `GET /status` — `state` · `used_minutes`. 🔴 **잔량이 40분 미만이면 STOP**
        (웜업 10분 + 측정 + 정지 마진이 안 나온다). 소유자에게 보고하고 착수하지 않는다.
  - [x] ② `check-ami-generation.sh` rc — **rc≠0 이면 그 어긋남을 먼저 기록**한다.
        어긋난 채로 재도 되지만, **무엇을 잰 것인지가 달라진다**(구운 세대 ≠ 서빙 세대).
  - [x] ③ 🔴 **캡티브 포털 양성/음성 대조군**을 **창 전에** 잰다. `TASK-MONO-624` 창에서
        이 호스트의 캡티브 포털이 **평문 http 를 전부 먹어** `*.sslip.io` 4곳이 모두 404 였다
        (음성 대조군이 아니었으면 «데모 내부가 전부 죽었다» 로 오독). 측정: `neverssl.com`.
        🔴 먹고 있으면 **평문 경로는 이 창에서 측정 불가**로 미리 선언하고 HTTPS 경로만 쓴다.
  - [x] ④ `git fetch origin main` — 로컬이 낡았는지. 큐(`ready`/`review`) 재확인.
- [x] **AC-1 (칸 0 — 유효성 술어가 먼저다)** — 콘솔·팬 각각에서 로그인 왕복이 **세션까지**
      성립함을 **갈림**으로 판정한다(세션 有 200 / 無 307 → `/login`).
  - [x] 🔴 팬은 **fail-closed** 다 — 맨 `curl` 은 **FAIL 과 지문이 같다**(로그인 HTML 200).
        판정 앞에 술어 셋: ① 최종 URL 이 `/login` 이 아닐 것 ② 리다이렉트 체인의
        `Location` 에 `from=` 이 없을 것 ③ **그 뒤에야** 개체를 센다.
  - [x] 🔴 `Accept: text/html` 을 **반드시** 준다. `*/*`(curl 기본)면 Spring Security 가
        302 대신 **401** 을 던지고, 그것을 그대로 적으면 **없는 결함**이 만들어진다
        (`TASK-MONO-624` 가 실제로 그 직전까지 갔다).
  - [x] 🔴 **부팅 직후 첫 요청은 콜드 JVM** — 첫 로그인이 `INTERNAL_ERROR` 를 낼 수 있다.
        **재시도**하고, 재시도했다는 사실을 적는다.
  - [x] AC-1 이 FAIL 이면 AC-2·3·4 는 **「실패」가 아니라 「측정 안 됨」**으로 기록한다.
- [x] **AC-2 (칸 1 — fan)** `/` 와 `/artists` 의 개체 수 · 에러 문구 수. 창 #5 값(4 · 3 · 0)과
      **비교**하되, 🔴 차이가 나면 «회귀» 로 단정하기 전에 **추출기부터 의심**한다
      (창 #3·#5 연속으로 내 추출기가 가장 유력한 결함이었다).
- [x] **AC-3 (칸 2 — store)** `/products` 의 상품 수 · 가격 수.
- [x] **AC-4 (칸 3 — console 6 도메인. 🔴 이 티켓의 중심)**
  - [x] 백엔드 여섯마다 **리스트 API 한 개**를 골라 **원소 수**를 적는다. 고른 엔드포인트와
        **왜 그것인가**(게이트웨이 직결 + 시드된 데이터가 있는 자리)를 함께 적는다.
  - [x] 🔴 **테넌트를 칸마다 적는다.** `ecommerce` 축은 `ecommerce` 테넌트로,
        나머지는 `demo-corp` 로 재는 것이 § 4 의 실측이다.
  - [x] 🔵 3패널(운영 개요 · 도메인 상태 · 알림 인박스)은 **별도 줄로 «등재된 한계»**
        라고 적고 **FAIL 로 세지 않는다.** 🔴 다만 **502 가 아니면 그것도 정보**다 — 적는다.
  - [x] 🔴 콘솔은 **클라이언트 렌더**라 SSR HTML 로 판정 불가. 원소 수는 **API 에** 묻는다.
- [x] **AC-5 (대조군 — 4축 + 시점)**
  - [x] 음성 대조군(존재하지 않는 경로 = 404)이 **창 안에서** 성립.
  - [x] **교대 B, A, B, A 두 패스.** 순차 A/B 는 드리프트를 잰다.
  - [x] 🔴 **손 안 댄 시나리오 하나**를 매 패스에 포함해 노이즈 바닥을 만든다.
  - [x] 🔴 «기억된 빨강» 은 대조군이 아니다 — 비교는 **이 창 안에서만**.
- [x] **AC-6 (창 운영 규율)**
  - [x] 🔴 `running` + IP 는 **준비 신호가 아니다.** EC2 는 30초에 뜨지만 컨테이너 웜업이
        **약 10분**이다(실측 9분 32초). 준비 판정은 **표면 응답**으로 한다.
  - [x] 🔴 **웜업/측정 내내 `POST /heartbeat`**(30초 주기). idle-stop 은 **마지막 beat 로부터
        20분**이고, 과거에 유휴 가드가 웜업 중 인스턴스를 죽여 **kafka KRaft 로그를 망가뜨린**
        사고가 있다. 론처 탭이 없으므로 루프를 **직접** 돌린다.
  - [x] 🔴 끝나면 **`POST /stop` 으로 명시적으로 끈다.** 창 시작/종료 시각과 **예산 소비
        (before → after)** 를 적는다.
- [x] **AC-7 (결과 처리 — 결함과 미측정을 섞지 않는다)**
  - [x] 각 칸을 **PASS / FAIL / ⚪ 측정 안 됨** 셋 중 하나로 적는다. 🔴 **미측정을 FAIL 로
        적지 마라** — 전자는 기록이고 후자는 빚이다.
  - [x] FAIL 이 있으면 **별도 티켓**을 `ready/` 에 기안하고 이 티켓에서 고치지 않는다.
  - [x] ⚪ 가 있으면 **왜 못 쟀는지**를 적는다. 🔴 그 «왜» 가 처방을 정한다.
        측정 불가가 **이 호스트의 상태**(캡티브 포털 등)면 **티켓으로 만들지 않는다** —
        큐에 «잴 수 없는 것» 이 영구히 앉는다(`TASK-MONO-624` 선례).
  - [x] 🔴 이 창에서 **못 한 것**을 별도 표로 남긴다. 침묵하지 않는다.

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — 이관 결정. § Verification 이 측정 결과의 집 중 하나.
- [`docs/adr/ADR-MONO-069-oidc-login-across-the-scheme-and-scope-boundary.md`](../../docs/adr/ADR-MONO-069-oidc-login-across-the-scheme-and-scope-boundary.md) — `C2`(`auth.hubwang.com` 포워더). 칸 0 의 왕복이 지나는 구조.
- [`docs/guides/interview-demo-walkthrough.md`](../../docs/guides/interview-demo-walkthrough.md) § 4(콘솔 화면 모집단 · 테넌트 축) · § 6(등재된 한계). 🔴 **AI 는 이 문서를 SoT 로 읽지 않는다** — 여기서는 **§ 6 표의 드리프트 여부**를 판정하기 위한 대상 문서로만 쓴다.
- [`infra/demo/aws/README.md`](../../infra/demo/aws/README.md) — 기동 절차 · 재굽기 계약.

# Related Contracts

- 컨트롤 플레인 4개 라우트 — `POST /start` · `POST /stop` · `GET /status` · `POST /heartbeat` (+ `GET /domains`). 구현 `infra/demo/aws/terraform/lambda/handler.py`.
- 콘솔 route handler 계약 — `projects/platform-console/apps/console-web/src/app/api/**/route.ts`.

# Related Tasks

- `TASK-MONO-622` — **창 #5**(2026-09-05). 이 티켓의 판정 술어를 승계한다. 팬 판정의 마지막 실측.
- `TASK-MONO-624` — 콘솔 왕복(V1~V5·V7). 칸 0 의 방법론 + `Accept` 함정 + 캡티브 포털.
- `TASK-MONO-627` — 콘솔 억제. **이 티켓이 재는 변경 ①**.
- `TASK-MONO-630` — AMI 9차 재굽기. **이 티켓이 재는 변경 ②**.
- `TASK-MONO-631` — 기록 정정(이관 완료 반영). 이 티켓의 직전 작업.
- `TASK-MONO-585` § 알려진 한계 — 콘솔 3패널 502 의 근거.

# Edge Cases

- **웜업이 10분을 넘긴다** — 판정을 표면 응답으로 하되 **상한을 미리 정한다**(예: 20분).
  넘으면 `POST /stop` 하고 «부팅 실패» 를 **그 자체로** 기록한다. 🔴 무한정 기다리며
  heartbeat 만 쏘면 예산이 조용히 녹는다.
- **일부 도메인만 뜬다** — `GET /domains` 스냅샷으로 확인한다. 🔴 `health_stale: true` 면
  그 스냅샷은 **판정이 아니다**(발행자가 죽어도 마지막 값이 남는다 — `TASK-MONO-551` 결함 B).
- **콜드 JVM 첫 요청 실패** — 재시도. 재시도로 성공하면 «결함» 이 아니라 «콜드 스타트» 다.
- **캡티브 포털이 살아 있다** — 평문 경로 전부 측정 불가. HTTPS 경로만으로 재고,
  못 잰 축을 ⚪ 로 남긴다.
- **AMI 세대 어긋남(AC-0 ②가 rc≠0)** — 창을 취소하지 않는다. 다만 **무엇을 쟀는지**가
  «최신 코드» 가 아니라 «구운 세대» 임을 결과에 명시한다.
- **예산이 창 도중 바닥난다** — 컨트롤 플레인이 `429 monthly-budget-exhausted` 로 막는다.
  이미 뜬 인스턴스는 idle-stop/max-runtime 이 정리하지만 **명시적 `POST /stop`** 을 한다.

# Failure Scenarios

- 🔴 **`code == 200` 하나로 «살아 있다» 를 판정한다** — 세션 없는 요청도 로그인 페이지에서
  200 이다. ⇒ 갈림(有 200 / 無 307)으로만 판정한다.
- 🔴 **3패널 502 를 FAIL 로 센다** — 등재된 한계다. 실패로 적으면 **진짜 결함을 가린다**.
- 🔴 **추출기가 결함인데 표면을 결함으로 보고한다** — 세 창 연속으로 일어났다.
  ⇒ 0 이 내 가설을 지지하면 **추출기를 바꿔 다시 잰다**. 넓히는 것이 골대 옮기기가 아님은
  **음성 기준선이 여전히 0** 인 것으로 증명한다.
- 🔴 **웜업 중 heartbeat 를 안 쏴서 유휴 가드가 인스턴스를 죽인다** — 전례가 있고
  kafka KRaft 로그가 손상됐다.
- 🔴 **창을 끄지 않는다** — 예산이 녹는다. idle-stop 은 20분 뒤이므로 명시적 `stop` 이 싸다.
- 🔴 **테넌트를 안 적고 «목록이 비었다 = 고장» 으로 읽는다** — `TASK-BE-576` 이 이름 붙인
  실패. 엣지는 초록인데 목록만 비는 상태가 **정상일 수 있다**(테넌트 불일치).
- 🔴 **이 창에서 본 것을 «지금 데모» 로 일반화한다** — 잰 것은 **as-baked 9차 이미지**다.
  다음 재굽기 뒤에 이 결과는 다시 미측정이 된다. ⇒ 결과에 **AMI id 와 구운 커밋**을 함께 적는다.
- 🔴 **단일 표본을 성질로 승격한다** — 개체 수·응답 시간 전부 이 창의 한 표본이다.

# Definition of Done

- [x] AC-0~AC-7 전부 닫혔다 — **각 AC 가 쓴 동사에 대해**(«결정하라» 는 권고로 안 닫히고,
      «못 쟀으면 왜인지 적어라» 는 ⚪ 로 **닫힌다**).
- [x] 판정 칸 0~3 + 대조군이 **PASS / FAIL / ⚪** 로 전부 채워졌고, 각 칸에 **무엇을
      어떻게 쟀는지**(엔드포인트 · 테넌트 · 술어)가 적혀 있다.
- [x] 창 시작/종료 시각 · **예산 소비(before → after)** · **AMI id + 구운 커밋**이 적혀 있다.
- [x] `POST /stop` 이 실행됐고 `GET /status` 가 `stopped` 를 확인했다.
- [x] FAIL 이 있으면 **별도 티켓이 `ready/` 에 기안**됐다. 없으면 «결함 0건» 이라고 적혔다.
- [x] 측정이 기존 기록을 거짓으로 만들었으면 그 자리(§ 6 표 · ADR § Verification)가 고쳐졌다.
- [x] 🔴 **못 한 것**이 별도 표로 남았다.

---

# 구현 (2026-09-07 UTC) — **기동 창 #6. 기능은 돈다. 그리고 「6개 도메인 화면」은 억제 이후 처음으로 실측됐다**

## 창 요약

| | |
|---|---|
| 창 | **07:10:49Z → 07:27:38Z (16분 49초)** |
| 예산 | **366 → 379 / 600** (**13분** 소비 · 잔량 **221분**) |
| 인스턴스 | 공인 IP `43.203.142.160` · 파생 도메인 `43-203-142-160.sslip.io` |
| **AMI** | **`ami-029c40fb18c63816b`** · 구운 커밋 **`3bc182ecd4e8`**(출처 `ami-tag`) · 9차 |
| 서빙 세대 | `main` = `35d0a4185` — `check-ami-generation.sh` **rc=0**(같은 세대) |
| 웜업 | `POST /start` → 전 도메인 `up` = **약 10분 30초**(기록된 9분 32초와 같은 자리) |
| heartbeat | 30초 주기 **33회**. idle-stop 발화 0회 |
| 콜드 JVM 재시도 | **0회** — 첫 로그인이 첫 시도에 성립했다 |

🔴 **이 결과는 «as-baked 9차 이미지» 의 것이다.** 다음 재굽기 뒤에는 다시 미측정이 된다.

## 판정 — **칸 0~3 전부 PASS. 결함 0건.**

| 칸 | 결과 | 무엇을 어떻게 쟀나 |
|---|---|---|
| **0 유효성(console)** | ✅ **PASS** | 같은 URL 이 **無 307 → `/login?redirect=` / 有 200** 으로 갈렸다. `/operators` 無 `307/9786` ↔ 有 **`200/25276`** · `/dashboards/overview` 無 `307/10010` ↔ 有 **`200/28201`** |
| **0 유효성(fan)** | ✅ **PASS** | `/artists` 無 **307 → `/login?from=%2Fartists`** ↔ 有 `200`. `/api/auth/session` = `tenantId=fan-platform` · `roles=["FAN"]` · `accountId=0199de70-…fa02` |
| **1 fan 실데이터** | ✅ **PASS** | `/` 개체 **4**(`/posts/<id>`) + membership 1 · `/artists` 개체 **3** · **에러 문구 0** · `/api/payment-config` = `{"demoPayment":true}` (20 B) |
| **2 store** | ✅ **PASS** | `/products` 상품 **8** · 가격 **8** · `원` 단위 스팬 **8** · 에러 문구 **0**. 가격 표본 `59,000` `1,590,000` `3,490,000` `45,000` |
| **3 console 6개 도메인** | ✅ **PASS (6/6)** | 아래 표 — **전부 원소 수로** 판정 |
| **음성 대조군** | ✅ | `fan.hubwang.com/artists-does-not-exist-632xyz` **404** · `store…/products-does-not-exist-632xyz` **404** · `console…/api/definitely-not-a-route-632` **404** · `http://nosuchhost-negctl-632.<도메인>/` **404/19** |
| **교대 B,A,B,A** | ✅ | B1≡B2 · A1≡A2 — **판정 수치가 한 칸도 안 움직였다** |

### 칸 1 · 2 의 두 패스 (교대 안에서만 비교했다)

```
A1 07:24:22Z  store /products  products:8 priceNums:8 wonUnits:8 err:0
              fan /            posts:4 membership:1 err:0
              fan /artists     artists:3 membership:1 err:0
A2 07:26:12Z  store /products  products:8 priceNums:8 wonUnits:8 err:0
              fan /            posts:4 membership:1 err:0
              fan /artists     artists:3 membership:1 err:0
```

🟢 **창 #5(`TASK-MONO-622`, 2026-09-05)의 팬 값 `4 · 3 · 에러 0` 과 정확히 같다.**
🔴 그러나 그것은 **기억된 값과의 비교**이지 이 창의 대조군이 아니다 — 대조군은
같은 창 안의 A1↔A2 와 음성 기준선이다.

### 칸 3 — 백엔드 여섯, 각각 리스트 API 의 **원소 수**

🔴 «200» 도 «degraded 마커 부재» 도 판정이 아니다. 아래는 전부 **원소 수**다.

| # | 백엔드 | 엔드포인트 | 테넌트 | 원소 수 | 폭 확인(같은 백엔드 형제) |
|---|---|---|---|---|---|
| 1 | `iam` | `/api/operators` | `demo-corp` | **2** | — |
| 2 | `wms` | `/api/wms/inventory` | `demo-corp` | **1** | `inbound/asns` **1** · `outbound` **1** |
| 3 | `scm` | `/api/scm/po` | `demo-corp` | **3** | `nodes` 0 · `demand-planning/suggestions` 0 |
| 4 | `finance` | **`/api/ledger/trial-balance`** | `demo-corp` | **3** (`accounts`) | 🔴 아래 § 참조 |
| 5 | `erp` | `/api/erp/masterdata/cost-centers` | `demo-corp` | **3** | `departments` **3** · `approval/inbox` **2** |
| 6 | `ecommerce` | `/api/ecommerce/products` | **`ecommerce`** | **8** | 같은 URL, `demo-corp` 로는 **0** |

폭 스윕은 **두 패스**(P1 07:26:47Z / P2 07:27:01Z)를 돌렸고 **11개 값이 전부 동일**했다.

🔴🔴 **6번 행이 테넌트 축을 눈에 보이게 만들었다.** *같은 엔드포인트*가 `demo-corp` 에서
**0**, `ecommerce` 에서 **8** 이다. 테넌트를 안 적었으면 그 **0** 을 「E-Commerce 화면이
고장났다」로 읽었을 것이다 — `TASK-BE-576` 이 이름 붙인 실패 그대로다.
🟢 그리고 그 **8** 은 스토어프런트가 그리는 **8** 과 같은 수다(서로 다른 두 표면, 같은 행).

🔵 테넌트 전환 자체도 실측됐다 — `POST /api/tenant` 가 `demo-corp` · `ecommerce` 양방향
모두 `200 {"ok":true,"activeTenant":"…"}`. 쿠키 `console_active_tenant` +
`console_operator_token`(assume 토큰)이 **원자적으로** 갱신된다.

### 🔴 finance 의 엔드포인트를 바꾼 이유 — **「0」 이 «고장» 인지 «시드 없음» 인지 갈랐다**

첫 선택 `/api/ledger/periods` 는 `200` + `data: 0` 이었다. 그대로 적었으면 6개 중
하나가 FAIL 이 됐을 것이다. **같은 백엔드의 형제 엔드포인트로 넓혔다**:

```
/api/ledger/periods                        200  data:0
/api/ledger/fx-rates                       200  rates:0
/api/ledger/reconciliation/discrepancies   200  data:0
/api/ledger/trial-balance                  200  accounts:3   ← 실데이터
```

⇒ **finance 백엔드는 우리 토큰을 받아 실데이터를 낸다.** 앞의 셋이 0인 것은
**그 화면에 시드가 없는 것**이지 도메인이 죽은 것이 아니다.
🔵 판정 엔드포인트를 «시드된 자리» 로 고른 것은 골대 옮기기가 아니다 —
**음성 대조군 넷이 전부 404 로 유지**되고, 형제 셋의 0 도 지우지 않고 그대로 적었다.

### 🔵 등재된 한계 3패널 — **502 였고, FAIL 로 세지 않았다**

```
/api/console/dashboards/operator-overview  502  {"code":"BAD_GATEWAY","message":"console-bff unreachable"}
/api/console/dashboards/domain-health      502  (같음)
/api/console/notifications/inbox           502  (같음)
```

`docs/guides/interview-demo-walkthrough.md` § 6 이 예고한 **그대로**다 — 셋 다
`console-bff` 를 지나고 그 BFF 는 공개 호스트명이 없다. 🟢 **실패를 «상태» 로 표현**하는
것까지 § 6 의 서술과 일치한다(빈 화면이나 500 이 아니라 정의된 502 봉투).
⇒ **§ 6 표는 드리프트하지 않았다. 고칠 자리 없음.**

## 🟢 억제 3건이 라이브에서 확인됐다 (`604` · `618` · `627`)

데모 호스트가 방문자 웹 표면을 **하나도** 서빙하지 않는다 — 평문 http 직격 실측:

```
http://console.<도메인>/            404 / 19 B   ← Traefik 기본 404 (라우터 없음)
http://web.fan-platform.<도메인>/   404 / 19 B
http://store.<도메인>/              404 / 19 B
http://ecommerce.<도메인>/          401 / 104 B  ← 게이트웨이는 있다(있어야 한다)
http://iam.<도메인>/login           200 / 4247 B ← 억제 뒤 방문자가 밟는 유일한 홉
```

🟢 그리고 `https://auth.hubwang.com/login` 이 **바이트 동일한 4247 B** 를 냈다 ⇒
**Vercel 포워더가 그날의 IdP 로 실제로 포워딩하고 있다**(`ADR-MONO-069` `C2`).
`GET /domains` 도 같은 말을 한다: `console` `healthy 1/1`(= `console-bff` 만).

전 도메인 헬스(웜업 완료 시점, `health_age_seconds: 11` — stale 아님):

```
iam 15/15 up · wms 17/17 up · scm 9/9 up · finance 7/7 up · erp 8/8 up
ecommerce 33/33 up · fan 8/8 up · console 1/1 up · traefik 1/1 up
```

🔵 `fan` 8/8 과 `console` 1/1 은 **억제와 모순이 아니다** — 억제된 것은 방문자 **웹 앱**
(`fan-platform-web` · `console-web`)이고, 이 카운터는 그 도메인의 **백엔드 컨테이너**를 센다.

## 🔍 이 창이 잡은 계측 결함 셋 — **전부 내 하네스 쪽이었다**

🔴 **이번에도 「가장 유력한 결함은 내 술어」가 맞았다.** 셋 다 판정으로 적히기 전에 잡혔다.

1. 🔴🔴 **가격 정규식이 0 을 냈다 — 그런데 가격은 8개 다 있었다.**
   `[\d,]{3,}\s?원` 이 안 맞은 이유는 공백이 아니라 **숫자와 `원` 사이에 태그가 끼어
   있는 것**이었다(`…price__">59,000</span><span …>원</span>`). 창 #3 이 밟은 「가격
   정규식」 함정의 **변종**이다. ⇒ 천단위 텍스트 노드와 단위 스팬을 **따로** 세도록 넓혔고,
   **저장해 둔 같은 바이트에 다시 돌려** 8 을 얻었다(재요청 아님 — 두 측정의 차이가
   추출기 하나뿐임을 보장한다).
   🟢 넓힌 것이 골대 옮기기가 아님은 **음성 대조군 둘이 새 추출기에서도 0** 인 것으로 증명한다.
2. 🔴 **`bash` 의 `local a="$1" b="${a}"` 가 `set -u` 아래서 `a: unbound variable`.**
   같은 `local` 문 안에서 방금 선언한 변수를 참조했다(bash 는 이름을 먼저 전부 local 로
   표시한 뒤 대입한다). 측정 스크립트 **둘 다** 같은 줄에서 죽었다. 🔵 죽은 자리가
   **첫 항목 직전**이라 「데이터가 0」이 아니라 「아무것도 안 나옴」으로 보인 것이
   구별을 쉽게 했다 — 반대였으면 0 을 판정으로 적었을 것이다.
3. 🔴🔴 **준비 신호로 고른 `fan.hubwang.com/api/auth/providers` 가 판별력이 0 이었다.**
   데모가 **꺼져 있던** 폴 #1(부팅 34초 시점)에도 `200/167` 이었고 창 내내 같은 값이었다 —
   next-auth 의 **정적 설정**을 반영할 뿐 데모 백엔드에 안 닿는다.
   ⇒ 실효 게이트는 `auth.hubwang.com/login` + `store.hubwang.com/…` 둘이었다.
   🔴 **이 증거는 그 명제의 증거가 아니었다.** 「200 이니 팬이 준비됐다」로 읽었으면
   준비되기 전에 쟀을 것이고, 그 측정은 창 #4 처럼 «유효한 FAIL» 로 기록됐을 것이다.

## 🔵 부수 관측 — 이번 창에서 새로 얻은 것

- 🔵 **부팅 지문이 단계마다 바뀌고, 그 변화 자체가 진행 표시였다.** `iam` 이
  Traefik 404(**19 B** = `404 page not found\n`) → iam 자신의 404(**111 B**) →
  로그인 200(**4247 B**) 로 갔고, 같은 시각 **음성 대조군은 19 B 에 그대로 있었다**
  ⇒ 변화가 라우트별 실제 변화임이 증명된다. `store` 는 `404/19` → `401/65` 로 갔다.
  🔴 첫 폴에서 서로 다른 호스트 넷이 **전부 `404/19`** 였는데, 이것을 캡티브 포털로
  오독할 뻔했다 — 갈라 준 것은 **본문이 Traefik 의 것이라는 확인**이었다.
- 🔵 **캡티브 포털은 이 창에서 없었다.** `TASK-MONO-624` 창은 평문 요청이 전부 404 로
  먹혔는데, 이번엔 `http://example.com/`=200(진짜 본문) · 없는 경로=**404** · 없는 호스트=**000**
  으로 **셋이 갈렸다**. 그래서 **데모 내부를 평문으로 직접 찌를 수 있었고**, 억제 판정이
  거기서 나왔다. 🔴 이것은 **호스트의 그날 상태**이지 저장소의 성질이 아니다 —
  다음 창에서 같을 것이라고 가정하지 마라.
- 🔵 `GET /api/tenants` 가 `403 TENANT_SCOPE_DENIED — "Only SUPER_ADMIN (platform-scope)
  operators may manage tenants"` 를 냈다. 무세션이면 401 이므로 **인증 뒤의 인가 판정**이고,
  그 문구는 **우리 토큰의 클레임을 파싱한 데모 내부 리소스 서버만** 만들 수 있다
  (`TASK-MONO-624` V6 이 쓴 것과 같은 종류의 증거).
- 🔵 **노이즈 바닥**: 손 안 댄 시나리오(`auth.hubwang.com/login`)가 두 패스에서 `200/4217`
  로 동일. 두 페이지의 `rawBytes` 는 `74376→74093` · `19411→18135` 로 흔들렸지만
  **판정 수치(개체 수)는 한 칸도 안 움직였다** ⇒ 바이트는 드리프트하고 개체 수는 안 한다.
  🔵 이것이 「rawBytes 를 판정에 쓰지 않은」 이유의 사후 근거다.

## ⚠️ 이 창에서 **못 한 것** (침묵하지 않는다)

| 축 | 상태 | 왜 |
|---|---|---|
| 스토어 **구매 완주**(장바구니 → 주문 → 배송 → 리뷰) | ⚪ **측정 안 됨** | 스토어 로그인 왕복을 안 했다. `/api/bff/products` 가 무세션에 **401 `REAUTH_REQUIRED`** 를 낸 것이 그 경계다. 이 티켓의 칸 2 는 «상품·가격이 그려지나» 였고 그건 쟀다 |
| `/api/ecommerce/orders` = **0** | ⚪ **판정 안 함** | 구매를 안 했으니 0 이 정상인지 결함인지 이 창으로는 못 가른다. 위 축과 같은 뿌리 |
| `scm/nodes` · `scm/suggestions` · `ledger/periods`·`fx-rates`·`discrepancies` = 0 | ⚪ **시드 없음으로 기록** | 같은 백엔드 형제가 실데이터를 내므로 «도메인 고장» 은 아니다. 「시드가 어디까지 채우나」는 별개 축 |
| `TASK-MONO-624` V6 의 나머지 11 소비자 | ⚪ **미측정** | 이 티켓의 축이 아니다. 집은 `ADR-MONO-069` § Verification |
| 콘솔 nav 리프 **48개 전수** | ⚪ **미측정** | 칸 3 의 술어는 «백엔드 여섯이 실데이터를 내는가» 이지 «화면 48개 전수» 가 아니다. 전수는 별개 축이고 이 창의 예산으로는 못 한다 |

🔴 **다섯 다 «결함» 으로 단정하지 않았다. 미측정은 미측정이다.**
🔴 그리고 **위 여섯 도메인의 «6/6 PASS» 를 «콘솔 48개 화면이 다 돈다» 로 읽지 마라** —
잰 것은 **백엔드 여섯이 실데이터를 낸다**는 것이고, 그 둘은 다른 명제다.

## AC-7 — 결함 0건 ⇒ **별도 티켓 기안 없음**

측정된 축에서 FAIL 은 **0건**이다. 계측 결함 셋은 전부 **내 하네스** 쪽이었고 창 안에서
고쳐졌다. ⚪ 다섯은 **이 티켓의 축 밖이거나 예산 밖**이고, 그중 **티켓으로 만들 것은 없다** —
「구매 완주」와 「nav 48 전수」는 잴 수 있지만 **다른 창의 일**이고, 지금 큐에 앉히면
`TASK-MONO-624` 가 이름 붙인 실패(«잴 수 없는 것이 큐에 영구히 앉는다»)의 사촌이 된다.
🙋 **필요하면 소유자가 다음 창을 열 때 함께 잰다.**

## 기존 기록에 고칠 자리 — **없다**

- `docs/guides/interview-demo-walkthrough.md` § 6 — 3패널 502 가 **예고 그대로**. 드리프트 없음.
- `docs/adr/ADR-MONO-067` § Verification — 이관 완료 서술이 이 창과 일치.
- `infra/demo/aws/deployed-ami.env` / `check-ami-generation.sh` — 창 전후 **rc=0** 유지.

## AC 체크

- [x] **AC-0** ①~④ 전부 재측정(상속 안 함). 잔량 234분 ≥ 40 · 세대 일치 · 포털 없음 · fetch 완료.
- [x] **AC-1** 칸 0 — console·fan 둘 다 **갈림**으로 판정. `Accept: text/html` 사용.
      콜드 JVM 재시도는 **필요 없었다**(0회, 사실로 기록).
- [x] **AC-2** 칸 1 — fan `/` 4 · `/artists` 3 · 에러 0. 추출기 결함 1건을 창 안에서 잡았다.
- [x] **AC-3** 칸 2 — store 상품 8 · 가격 8.
- [x] **AC-4** 칸 3 — 백엔드 6/6, 엔드포인트와 **테넌트**를 칸마다 적었다. 3패널은 별도 줄.
- [x] **AC-5** 대조군 — 음성 4종 · 교대 B,A,B,A · 노이즈 바닥 포함.
- [x] **AC-6** 창 운영 — 표면 응답으로 준비 판정 · heartbeat 33회 · 명시적 `POST /stop`.
- [x] **AC-7** PASS/FAIL/⚪ 로 전부 분류. FAIL 0 ⇒ 별도 티켓 없음. ⚪ 5건은 «왜» 를 적었다.
