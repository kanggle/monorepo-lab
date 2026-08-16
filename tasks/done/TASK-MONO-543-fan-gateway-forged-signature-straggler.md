# Task ID

TASK-MONO-543

# Title

팬 게이트웨이의 "변조 토큰" 테스트는 **네 번에 한 번 유효한 토큰을 보낸다** — `TASK-MONO-458`/`461` 스윕이 놓친 마지막 낙오, 그리고 그 스윕은 지금도 진행 중인 main 을 빨갛게 만든다

# Status

done

# Owner

monorepo

# Task Tags

- ci
- testing

---

# 배경

`TASK-MONO-542` 가 scm 게이트웨이를 CI 레인에 넣다가 밟았고, **그 자리에서 팬의 것이
실제로 터지는 것을 관측했다.**

## 결함

`GatewayBootstrapIntegrationTest.tamperedTokenSignatureReturns401` 은 서명의
**마지막 base64url 문자**를 뒤집어 "변조" 한다(`'A'`→`'B'`, 그 외→`'A'`).

RS256/2048 에서 그 문자는 **거의 아무것도 담지 않는다**:

- 서명 256바이트 = **2048비트**, base64url 은 문자당 6비트
- 2048 = 6 × 341 + **2** ⇒ 342번째 문자는 **유의 2비트 + 패딩 4비트**
- `'A'`(`000000`) 와 `'B'`(`000001`) 는 **그 2비트가 같다**

⇒ 서명이 `'A'` 로 끝나면 변조가 **바이트 단위로 아무 일도 하지 않고**, 토큰은 **유효한
채** 남는다. 게이트웨이는 옳게 라우팅하고, 공유 MockWebServer 큐는 비어 있어
**블로킹**하며, 5초 뒤 `Timeout on blocking read` 로 죽는다.

**결함이 인프라 불안정의 옷을 입는다.**

## 🔴 이것은 새 발견이 아니다 — 스윕이 놓친 것이다

`TASK-MONO-458` 이 erp·wms 를, `TASK-MONO-461` 이 finance 를 이미 고쳤다.
세 프로젝트의 `JwtTestHelper` javadoc 이 이 인과를 **글자 그대로 적어 두고 있다**:

> *"Replaces a byte-flip tamper (TASK-MONO-458 residual, fixed here in the MONO-461 CI
> run): flipping the LAST base64url char of an RSA-2048 signature only touches padding
> bits ~25% of runs … erp/wms were fixed in MONO-458; finance passed then by key luck
> and kept the flaw."* — `finance-platform/…/JwtTestHelper.java`

낙오는 **scm 과 fan** 둘뿐이었고, `MONO-542` 가 scm 을 닫았다. **팬만 남았다.**

## 실측 (`TASK-MONO-542`, 2026-08-17)

scm 에서 400개 토큰으로 잰 값(팬도 같은 알고리즘·같은 키 크기):

```
sigLen=342  bytes=256
signatureBytesUNCHANGED = 107 / 400   (26.75%)
lastCharDistribution    = {A=107, Q=110, g=100, w=83}
lastCharWhenUnchanged   = {A=107}      ← 전부 'A'
```

**그리고 팬의 것이 실제로 터지는 것을 관측했다** — `MONO-542` 의 PR CI 런
`31966849259`, 팬을 **건드리지도 않은** PR 에서:

```
fan integration job   files=49 tests=130 failures=1 errors=0 skipped=0
FAILED  GatewayBootstrapIntegrationTest.tamperedTokenSignatureReturns401  time=6.357
        IllegalStateException: Timeout on blocking read for 5000000000 NANOSECONDS
```

🔴 **팬 레인은 `TASK-MONO-541` 로 이미 `main` 에 있다.** 즉 이것은 잠재 결함이 아니라
**지금 이 순간 팬 레인을 발동시키는 모든 PR 을 약 25% 확률로 빨갛게 만드는** 상태다.

🔵 `MONO-541` 은 이 적색을 보고 **flake 로 결론짓지 않았다** — 기준선이 없어 원인을
가릴 수 없다고 정직하게 적었다. **그 판단이 옳았다.**

---

# Goal

팬 게이트웨이의 변조 토큰이 **모든 실행에서** 검증에 실패하고, 이 결함 계열의 낙오가
저장소에 **0건**임을 측정으로 남긴다.

---

# Scope

1. `projects/fan-platform/apps/gateway-service/src/test/.../testsupport/JwtTestHelper.java`
   에 `foreignSigner`(JWKS 에 없는 두 번째 RSA-2048 키, **진짜 `kid` 를 그대로 광고**) +
   `signForgedSignatureToken(subject)` 를 추가한다. **형제 3개의 구현을 그대로 따른다** —
   새 패턴을 만들지 말 것(`MONO-542` 가 처음에 그 실수를 했다).
2. `GatewayBootstrapIntegrationTest` 의 인라인 변조를 그 호출로 교체하고, wms 의 문안대로
   *"do not reintroduce the flip"* 주석을 남긴다.
3. AC-3 의 전수 결과에 따라 남은 낙오를 처리한다.

## Out of Scope

- scm — `TASK-MONO-542` 가 닫았다.
- erp · wms · finance — `MONO-458`/`461` 이 닫았다. **다만 AC-3 이 다시 확인한다**
  (닫혔다는 기록과 현재 트리가 일치하는지는 별개 사실이다).
- 통합 스위트의 다른 결함 — 이 티켓은 이 결함 계열만 본다.

---

# Acceptance Criteria

- [ ] **AC-0 (전제 재확인)** — 착수 시 팬 헬퍼에 `signForgedSignatureToken` 이 **아직
      없고** 인라인 뒤집기가 **아직 있는지** 확인한다. 아니면 STOP 후 재측정.
- [ ] **AC-1 (결함 재현)** — 수정 **전에** 팬에서 직접 잰다: 토큰 N개(≥300)에 현재 변조를
      적용해 **디코딩된 서명 바이트가 바뀌지 않는 비율**을 적는다. 🔴 scm 의 26.75% 를
      **물려받지 말 것** — 다른 키, 다른 실행이다. 🔵 예상은 ~25% 이며, 크게 다르면
      그것 자체가 발견이다.
- [ ] **AC-2 (수정 + 가드)** — `signForgedSignatureToken` 도입 후, **루프 가드**를 둔다
      (형제 3개엔 가드가 없다 — 이것이 이 티켓이 형제보다 더 하는 부분이다).
      🔴 **단일 표본 가드 금지**: 확률적 변조로의 회귀를 단일 표본은 **약 73% 확률로
      놓친다**. 가드가 **왜** 루프인지를 테스트 javadoc 에 적을 것(다음 사람이 "단순화"
      한다).
- [ ] **AC-3 (bite)** — 옛 뒤집기 로직을 되살려 가드가 **실제로 빨개지는지** 확인하고
      되돌린다. 관측된 실패 수치를 적는다(scm 에서는 `73 of 300 still verified`).
- [ ] **AC-4 (kid 축)** — 위조 토큰이 **진짜 `kid` 를 유지**하는지 단언한다. 🔴 `kid` 가
      달라지면 리소스 서버가 **키를 못 고른 것**으로 401 이 나고, 테스트는 통과하면서
      **서명 검증을 더 이상 행사하지 않는다**(초록인 채로 무의미해진다).
- [ ] **AC-5 (형제 전수 — 고치기 전에)** — 이 결함 계열의 낙오를 **전수로** 센다.
      🔴 **철자 하나로 세지 말 것** — `MONO-542` 는 `endsWith("A")` 와
      `charAt(length()-1)` **두 축**으로 셌고, 두 번째 축이 게이트웨이 밖 후보를 하나 더
      찾았다: `fan-platform/apps/membership-service/…/BillingKeyEncryptorTest.java:43`
      (Base64 GCM 봉투에 같은 뒤집기). **성격이 다르므로**(거기선 조용히 통과하지 않고
      시끄럽게 실패한다) **재보고 판정할 것 — 가정으로 배제하지 말 것.**
      `iam`(다른 키로 서명) · `ecommerce` · `console-bff` 도 **실제로 열어서** 확인한다.
      **0건이면 0건이라고 적는다.**
- [ ] **AC-6 (팬 레인 초록)** — 수정 후 팬 통합 잡 아티팩트에서 네 값을 읽어 적는다.
      🔴 **초록 회차 수로 판정하지 말 것** — 결함이 확률적이라 수정 없이도 75% 는
      초록이다. 판정은 AC-3 의 bite 다.

---

# Related Specs

- `projects/fan-platform/apps/gateway-service/src/test/java/com/example/fanplatform/gateway/testsupport/JwtTestHelper.java`
- 선례 구현: `projects/finance-platform/…/JwtTestHelper.java` (`signForgedSignatureToken`)
- 선례 티켓: `TASK-MONO-458`(erp·wms) · `TASK-MONO-461`(finance) · `TASK-MONO-542`(scm)

# Related Contracts

없음 — 테스트 하네스이며 API·이벤트 계약을 건드리지 않는다.

---

# Edge Cases

- 🔴 **재현이 확률적이다.** 수정 전 상태에서 팬 스위트를 돌려 초록이 나와도 그것은
  결함 부재의 증거가 아니다(약 75%). AC-1 의 **비율 측정**과 AC-3 의 **bite** 만이 판정이다.
- 🔴 **`TASK-MONO-542` 가 여기서 한 번 헛디뎠다** — 같은 실패를 "컨텍스트 콜드스타트가
  첫 테스트에 청구된다"로 진단하고 워밍업을 만들었다. 위치 효과는 **실재하지만**(첫 테스트
  1.5~2.5s vs 이후 0.03~0.8s) 5초 예산 안이라 **원인이 아니었다**. 가설이 죽은 지점은
  워밍업이 실패 경로를 태웠을 때 **401 이 아니라 504 GATEWAY_TIMEOUT** 이 돌아온 것이다
  (= 그 토큰이 라우팅되고 있었다 = 여전히 유효하다). **검증 가능한 것과 원인인 것은 다르다.**
- 팬 스위트의 컨텍스트가 몇 개인지는 이 티켓과 무관하다 — 워밍업을 도입하지 않는다.
- `foreignSigner` 는 **같은 `kid`** 를 써야 한다(AC-4). 형제 3개가 그렇게 한다.

# Failure Scenarios

- **새 패턴을 만든다** → 저장소에 다섯 번째 방식이 생긴다. `MONO-542` 가 이 실수를 하고
  되돌렸다. 형제 구현을 복사할 것.
- **단일 표본 가드를 둔다** → 회귀를 4번에 3번 놓친다. 결함을 살려 둔 바로 그 동전던지기다.
- **bite 없이 닫는다** → 확률적 결함이라 초록은 아무것도 증명하지 못한다.
- **AC-5 를 건너뛴다** → 같은 두 프로젝트가 **두 스윕 연속**(CI 레인 · 변조 수정) 낙오했다.
  🔴 **세 번째를 만들지 않으려면 고치기 전에 세야 한다.**
- **AC-1 을 scm 수치로 대체한다** → 다른 키·다른 실행의 숫자를 팬의 사실로 보고하게 된다.
