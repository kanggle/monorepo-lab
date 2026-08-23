# Task ID

TASK-MONO-571

# Title

ADR-MONO-067 **AC-0 ②** 를 실측한다 — Vercel 함수에서 **평문 HTTP 업스트림 호출**이 성공하는가. 거짓이면 그 ADR 이 무너진다.

# Status

ready

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
