# Task ID

TASK-MONO-571

# Title

ADR-MONO-067 **AC-0 ②** 를 실측한다 — Vercel 함수에서 **평문 HTTP 업스트림 호출**이 성공하는가. 거짓이면 그 ADR 이 무너진다.

# Status

done

> **✅ DONE — 3축 검증 완료 (2026-08-25).** impl PR #3450 squash `02cf68fc9` ·
> `state=MERGED` · squash 커밋이 `origin/main` 에 실재 · **머지 시점 CheckRun 48개 중 실패 0**.

> **✅ AC-1~6 완료 (2026-08-25). 판정 = `PLAINTEXT_HTTP_EGRESS_WORKS`.**
> 프로브는 AC-5 로 **회수했다**. 결과는 `ADR-MONO-067` § AC-0 2번에 기록.

# Owner

monorepo

# Task Tags

- adr
- measurement
- infra

---

# Goal

`ADR-MONO-067` § AC-0 의 2번 항목을 **라이브에서 1건으로** 확인한다.

> **(B) 가 실제로 되는가** — Vercel 함수에서 평문 HTTP 업스트림 호출이 성공하는지 1건으로 확인.
> 실패하면 이 ADR 의 추천이 통째로 무너진다.

이 ADR 이 고른 (B) 는 *"브라우저는 Vercel(HTTPS)만 부르고, 백엔드 호출은 Next 서버 라우트가
평문 HTTP 로 프록시한다"* 이다. 그 **프록시 절반이 아직 한 번도 행사된 적이 없다.**

🔴 **기동 론처가 도는 것은 이 항목의 증거가 아니다.** 론처는 `/status` 로 IP 를 조회해 **링크를
만들 뿐**이고, 그 다음은 브라우저의 **최상위 내비게이션**이라 애초에 mixed content 규칙 밖이다.
론처가 증명한 것은 *조회 경로가 동작한다*이지 *서버가 그 주소로 프록시한다*가 아니다.

---

# Scope

## 포함

- `projects/fan-platform/web/fan-platform-web/` 에 **한시적** 프로브 라우트 1개.
- 그 라우트가 미들웨어 인증 게이트를 통과하도록 공개 경로 목록에 1줄.
- **프리뷰 배포에서** 측정하고 결과를 `ADR-MONO-067` § AC-0 에 기록.
- 측정 후 프로브 + 미들웨어 1줄 **제거**(AC-5).

## 제외

- AC-0 ③(OIDC 왕복) · ④(무료 플랜 한도) — 별도.
- 어떤 앱의 실제 이관도 하지 않는다(단계 2~4).
- EC2 보안그룹 변경. 이 티켓은 **Vercel 쪽 능력**만 잰다.

## 왜 fan 인가

| 후보 | 판단 |
|---|---|
| `kanggle-fan` (fan-platform-web) | ✅ **Next.js route handler = 확실히 함수다.** 배포가 존재하고 **아무도 링크하지 않아** 방문자에게 안 보인다. |
| `kanggle-portfolio` (`infra/demo/aws/site`) | ❌ `framework: null` + 커스텀 빌드라 `api/` 픽업이 불확실하고, **살아있는 포트폴리오 페이지**다. |

---

# Acceptance Criteria

### AC-1 — 프로브가 세 칸을 **각각** 보고한다

한 요청에 세 개의 상류를 **독립적으로** 부르고 각각의 결과를 반환한다.

| 칸 | 상류 | 역할 |
|---|---|---|
| `plaintextA` | `http://neverssl.com/` | **주제** — 평문 HTTP |
| `plaintextB` | `http://example.com/` | 주제의 **두 번째 출처**. A 만 쓰면 "그 호스트가 죽었다"와 구별 불가 |
| `httpsControl` | `https://example.com/` | 🔴 **양성 대조군.** 이게 같이 죽으면 판정은 *"평문이 막혔다"* 가 아니라 *"이그레스가 아예 없다"* 다 |

🔴 **대조군이 없으면 이 측정은 판정 불가다.** 네트워크가 통째로 막힌 런타임과 평문만 막힌
런타임이 **같은 출력**을 낸다.

### AC-2 — 성공 판정에 **리다이렉트 술어**가 있다

`redirect: 'manual'` 로 부르고 **최종 status 와 `location` 헤더를 그대로** 보고한다.

🔴 서버가 `301 → https://` 로 올려 보내면 그건 *"평문 HTTP 업스트림이 됐다"* 가 **아니다**.
`fetch` 가 자동 추적하면 200 이 돌아와 **성공으로 오독된다**. 판정은 다음일 때만 참이다:

> `http://` 로 부른 요청이 **리다이렉트 없이** 2xx 를 반환했다.

### AC-3 — 실패는 **원인이 구별되게** 보고된다

`fetch` 가 던지면 `error.name` / `error.message` / `error.cause?.code` 를 그대로 싣는다.
`ENOTFOUND`(DNS) · `ECONNREFUSED`(연결) · 정책 차단은 **다른 결함**이고 후속 조치가 다르다.

### AC-4 — 프리뷰 배포에서 실제로 응답을 받았다

PR 프리뷰 URL 로 `GET /api/ac0-probe` 를 호출한 **원문 JSON** 을 티켓에 붙인다.

⚠️ Vercel Deployment Protection 이 프리뷰를 막으면 그 사실을 기록하고, 프로덕션 배포로 잰다
(`kanggle-fan` 은 아무도 링크하지 않으므로 노출 위험이 낮다).

### AC-5 — 측정 후 프로브를 **제거**한다

라우트 파일과 미들웨어의 공개 경로 1줄을 되돌린다. 진단 부산물이 앱에 남으면 안 된다.
🔴 제거 커밋이 이 티켓의 **일부**다. AC-4 만 하고 닫지 않는다.

### AC-6 — 결과를 `ADR-MONO-067` § AC-0 에 기록한다

2번 항목을 `✅ 완료` 또는 `❌ 거짓 — SUPERSEDE 필요` 로 갱신하고, 잰 날짜·프로브 원문 응답·
**측정하지 못한 잔여**(sslip.io DNS, EC2 보안그룹, 포트)를 명시한다.

🔴 **이 프로브가 통과해도 (B) 가 성립한다는 뜻은 아니다.** 잰 것은 *"Vercel 함수가 평문 HTTP 로
나갈 수 있다"* 이고, 남은 것은 *"그 주소가 우리 EC2 이고 SG 가 그 출발지를 허용한다"* 이다.
과대주장하지 않는다.

---

# Related Specs

- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md` § AC-0 (2번 항목이 이 티켓의 본체)

# Related Contracts

없음 — 이 티켓은 어떤 계약도 바꾸지 않는다. 측정만 한다.

---

# Edge Cases

- **`neverssl.com` 이 죽어 있다** → `plaintextB`(example.com)가 갈라 준다. 둘 다 죽으면 판정 불가로
  보고하고 다른 평문 호스트로 재시도한다. **"둘 다 실패 = 평문 차단"으로 결론짓지 않는다.**
- **`example.com` 이 `http` 를 `https` 로 301 한다** → AC-2 의 술어가 그것을 성공으로 세지 않는다.
  그 경우 `plaintextA` 만이 주제의 유효한 표본이다.
- **미들웨어가 프로브를 삼킨다** → 로그인 리다이렉트(307)가 돌아오고 JSON 이 아니다. 응답이
  JSON 이 아니면 **측정 실패**로 보고한다(200 이 아닌 것을 성공으로 세지 않는다).
- **Vercel 빌드가 건너뛰어진다** → `vercel-ignore.sh` 의 `SPECS` 에 `:/projects/fan-platform/web` 이
  있으므로 이 변경은 빌드를 튼다. 배포가 `pending` 을 **거쳤는지**로 확인한다(rate limit 판별자).
- **런타임이 edge 로 잡힌다** → edge 런타임의 fetch 는 제약이 다르다. `export const runtime =
  'nodejs'` 를 명시한다. (B) 가 쓸 프록시도 nodejs 이므로 **재는 것과 쓸 것을 맞춘다.**

---

# Failure Scenarios

- **평문이 막혀 있다(주제 실패 + 대조군 성공)** → `ADR-MONO-067` 은 (B) 로 성립하지 않는다.
  SUPERSEDE 하는 후속 ADR 이 필요하고, 유력 후보는 **EC2 쪽에 TLS 종단을 세우는 축**이다
  (Traefik ACME + `sslip.io`). 단계 2~4 는 **전부 멈춘다**.
- **대조군도 같이 실패한다** → 판정 불가. 프로브 자체가 고장 났거나 배포가 안 됐다.
  "평문 차단"으로 **결론짓지 않는다**.
- **프로브가 응답하는데 JSON 이 아니다** → 미들웨어 게이트를 못 뚫은 것이다. AC-1 이 아니라
  배선 문제이므로 공개 경로 목록을 먼저 고친다.
- **프로브를 지우는 것을 잊는다** → 앱에 인증 없는 아웃바운드 호출 라우트가 남는다.
  대상 URL 이 **하드코딩**이라 SSRF 는 아니지만 부산물이다. AC-5 가 이것을 막는다.

---

# 진행 기록 (2026-08-23 UTC)

## ✅ AC-1 · AC-2 · AC-3 — 완료 (PR #3438, squash `dca2408f2`)

세 칸 프로브 + 리다이렉트 술어 + 원인별 오류 보고를 구현했다. 판정 로직은 `route.ts` 가 아니라
`verdict.ts` 에 있다 — 🔴 Next 라우트 모듈은 **HTTP 핸들러와 라우트 config 외의 값 export 를
금지**하고, `decideVerdict` 를 route 에서 내보내면 생성된 라우트 타입검사가
(`OmitWithTag<…>` → `never`) 실패해 **lint 가 아니라 `next build` 가 깨진다.**

### 🔴 판정 술어를 라이브 출력보다 **먼저** 증명했다

프로브의 JSON 은 술어가 맞든 틀리든 **똑같이 권위 있어 보인다.** 그래서 합성 칸으로 bite 했고,
주입 여부를 매번 **파일을 되읽어** 확인했다.

| bite | 주입 | 결과 |
|---|---|---|
| BITE-1 | `cleanPlaintext` 에서 `location === null` 제거 | 1 failed — *"a 2xx that carries a location header is not a clean plaintext success either"* |
| BITE-2 | `httpsControl` 가드 무력화 | 1 failed — *"refuses to judge when the https control also failed"* |

🔴🔴 **BITE-2 가 이 티켓의 존재 이유를 보여줬다.** 대조군 가드를 끄자 *이그레스 전무* 상태가
`PLAINTEXT_HTTP_EGRESS_BLOCKED` 로 보고됐다 — **평문에 대해 아무 말도 하지 않는 증거로 ADR 을
침몰시키는 거짓 판정**이다.

🔵 **301 케이스는 BITE-1 에 물지 않았다**(그 칸은 `ok:false` 라 양쪽 다 실패). 두 테스트가 술어의
**서로 다른 절반**을 지킨다는 뜻이고, 그래서 `200 + location` 칸을 따로 둔 것이다.

**게이트는 bite 이후 최종 트리에서 다시 돌렸다**: `tsc --noEmit` 0 · `pnpm lint` clean ·
`vitest` **139/139**(22 파일) · `pnpm build` 완주 + 산출물에 `ƒ /api/ac0-probe`.

## ⏸️ AC-4 — **측정하지 못했다**

| 시도 | 결과 |
|---|---|
| 프리뷰 배포(`kanggle-l7sv54tjt-…vercel.app`) | ❌ **Vercel Deployment Protection** — `302 → vercel.com/sso-api`. 앱이 실행조차 안 됐다 |
| 프로덕션 별칭(`kanggle-fan.vercel.app`) | 🔵 **공개다** — 기존 `/api/payment-config` 가 `200`. 보호는 **배포 URL 에만** 걸려 있고 별칭엔 없다 |
| 머지 후 프로덕션 배포 | ❌ **배포가 생기지 않았다** — `Deployment rate limited — retry in 24 hours` |

🔴 **rate limit 판별자가 다시 참이었다**: `kanggle-fan` 은 `pending` **없이 단발 failure**,
같은 초에 `kanggle-portfolio` 는 `pending → success` 를 거쳤다. **문구가 아니라 `pending` 통과
여부**가 판별자다.

⇒ 프로브는 `main` 에 있으나 **한 번도 배포된 적이 없다.** 24시간 뒤 fan 경로를 건드리는 커밋이
생기면 배포되고, 그때 `https://kanggle-fan.vercel.app/api/ac0-probe` 를 부르면 된다.

## 🔴 부수 발견 — `scripts/vercel-should-build.sh` 의 판정 창이 한 커밋이다

판정이 `git diff HEAD^ HEAD` 라, **여러 커밋을 한 번에 push 하면 앞 커밋의 앱 변경이 창 밖으로
나가** 배포가 조용히 건너뛰어진다. 이 세션에서 실제로 발생했다(앱 변경 커밋 + INDEX 커밋을 함께
push → `Canceled by Ignored Build Step`, 프리뷰에 프로브가 없었다).

그 스크립트 헤더가 스스로 경고한 모양 그대로다 — *"고장은 반드시 더 굽는 쪽으로 나야 한다…
증상은 배포가 조용히 건너뛰어졌다"* — 인데, **작성자가 고려하지 않은 문으로 들어왔다.**
🔵 `main` 은 squash 머지라 한 커밋에 전부 담기므로 **프로덕션은 안전하고, 프리뷰만 뚫린다.**
별도 티켓 사안(이 티켓의 범위 밖).

---

# ✅ AC-4 완료 (2026-08-25 UTC) — **`PLAINTEXT_HTTP_EGRESS_WORKS`**

`https://kanggle-fan.vercel.app/api/ac0-probe` **HTTP 200**, 원문:

```json
{ "task": "TASK-MONO-571", "adr": "ADR-MONO-067 AC-0 (2)",
  "verdict": "PLAINTEXT_HTTP_EGRESS_WORKS",
  "notMeasured": ["sslip.io DNS resolution",
                  "EC2 security-group ingress from Vercel egress",
                  "non-80 ports"],
  "cells": {
    "plaintextA":   { "url": "http://neverssl.com/", "ok": true, "status": 200, "location": null, "error": null },
    "plaintextB":   { "url": "http://example.com/",  "ok": true, "status": 200, "location": null, "error": null },
    "httpsControl": { "url": "https://example.com/", "ok": true, "status": 200, "location": null, "error": null } } }
```

## 왜 이 판정이 유효한가

| 설계 요소 | 이 결과에서 무엇을 했나 |
|---|---|
| **대조군** `httpsControl` | 통과했다 ⇒ 판정 가능. 같이 죽었다면 *이그레스 전무*와 *평문만 차단*이 **같은 출력**이라 판정 불가였다 |
| **리다이렉트 술어** | 두 평문 칸이 `location: null` 인 2xx ⇒ `301 → https` 승격을 성공으로 **오독한 것이 아니다** |
| **두 번째 출처** | `neverssl.com` 과 `example.com` 이 **일치** ⇒ "그 호스트가 죽었다"와 구별됨 |

## 🔴 통과해도 (B) 성립은 아니다

잰 것은 *"Vercel 함수가 평문 HTTP 로 나갈 수 있다"* 뿐이다. 남은 것은 프로브 자신이
`notMeasured` 로 적은 **셋**: `sslip.io` DNS · EC2 보안그룹의 Vercel 이그레스 허용 · 80 이외 포트.

## 이 측정이 왜 이렇게 오래 걸렸나 — 두 겹의 차단

| 차단 | 해소 |
|---|---|
| ① Vercel **배포 rate limit** (2026-08-23, 하루 두 번) | 시간 경과 |
| ② **프로브가 배포되지 않음** — 프로브 머지 후의 커밋들이 fan 감시 경로에 안 닿았다 | `TASK-MONO-572` 가 `scripts/` 를 고치면서 **그 배포가 프로브를 실어 날랐다** |

🔵 ②는 결함이 아니라 무시 규칙이 **설계대로** 동작한 것이다. 다만 *"머지했으니 배포됐겠지"* 가
거짓이라는 사례가 하나 더 늘었다.

## ✅ AC-5 — 프로브 회수

`route.ts` · `verdict.ts` · `ac0-probe-verdict.test.ts` 삭제, 미들웨어 공개 경로 1줄 원복.
`grep -rn "ac0-probe" projects/fan-platform/` **0건**으로 확인.

## ✅ AC-6 — ADR 기록

`ADR-MONO-067` § AC-0 2번을 ✅ 로 갱신하고 원문 응답·유효성 근거·**측정하지 못한 잔여 셋**을
함께 적었다.
