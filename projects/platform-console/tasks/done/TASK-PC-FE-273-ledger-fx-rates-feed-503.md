# Task ID

TASK-PC-FE-273

# Title

콘솔 `/ledger` fx-rates 피드 503 — **조사 결과 결함 아님**(콜드스타트 일시현상 + 의도된 기본값)

# Status

done

# Owner

platform-console

# Task Tags

- frontend
- bff
- investigation
- not-a-defect

---

# 결론 (2026-08-07, 조사 완료)

**고칠 것이 없다.** 이 티켓이 근거로 삼은 503 은 **재현되지 않았고**, 재현되는 상태
(`feedEnabled:false`)는 **문서화된 의도적 기본값**이며 **콘솔은 이미 그것을 화면에
말하고 있다**. 세 가설을 전부 실측으로 갈랐고 결과는 아래.

## AC-0 — 세 가설 판별 (실측, 추측 아님)

| # | 가설 | 판정 | 근거 |
|---|---|---|---|
| 1 | 외부 provider 미도달 | ❌ | `fx_rate_quote` **0행**인데 ledger 로그에 fetch 실패 **0건**. 애초에 **폴러 빈이 생성되지 않는다**(아래 3번) — 못 간 게 아니라 **가지 않는다** |
| 2 | BFF 5s 합성 타임아웃 | ❌ | 그 시각 BFF 로그의 유일한 타임아웃은 `route=operator-overview, tenant=iam` — **다른 라우트, 다른 테넌트**다 |
| 3 | 프로파일상 비활성 | ✅ | `FxRateFeedProperties` 가 자기 Javadoc 에 적어 뒀다 — *"**Default net-zero**: `enabled=false` (the poller bean is not created) + `mode=noop` (the noop adapter is wired, making **zero external calls**)"* |

## 🔴🔴 503 은 결함이 아니라 **콜드스타트**였다 — 단일 표본을 성질로 승격했다

이 티켓은 `TASK-FIN-BE-068` AC-4 측정 중 **딱 한 번** 본 503 으로 파일됐다. 같은
엔드포인트를 다시 재면:

```
콜드 1회차   GET /api/finance/ledger/fx-rates → 200 in 3126ms
웜  2회차                                     → 200 in  696ms
웜  3회차                                     → 200 in  520ms
웜  4회차                                     → 200 in  569ms
콘솔 BFF(웜)                                  → 200 {"feedEnabled":false,"rates":[]}
```

첫 호출이 3.1초였고 그게 BFF 레그 타임아웃을 넘겨 503 이 됐다. **웜에서는 503 이
한 번도 나오지 않는다.**

🔴 **내가 한 실수가 정확히 이것이다** — 한 번 측정한 값을 *성질* 로 승격시켜 티켓을
냈다. AC-4 를 만족시킨 그 측정 회차는 스택을 막 띄운 직후였고, 나는 그 사실을
**같은 티켓 본문에 "테넌트 전환 선행조건" 으로는 적으면서 "콜드스타트" 는 안 적었다.**
선행조건을 하나 알아챘다고 해서 다른 하나를 알아챈 게 아니다.

## 🔵 그리고 화면은 이미 말하고 있었다

`FxRatesTable.tsx` 가 `feedEnabled=false` 분기에서 경고 배지를 렌더한다:

```
피드 비활성 — 환율 폴백이 꺼져 있습니다
```

이 티켓의 AC-1(*"환경 제약이면 화면이 그 사실을 말한다"*)은 **이미 충족돼 있었다.**
티켓을 쓸 때 나는 BFF 응답만 보고 **컴포넌트를 열어 보지 않았다** — 200 본문이
`rates:[]` 인 것과 *화면이 그 이유를 말하지 않는 것*은 다른 주장인데 후자를 확인 없이
전제했다.

## 남는 선택지 (결함 아님 — 원한다면 데모 개선)

데모에서 FX 화면에 **데이터를 보이고 싶다면** `financeplatform.ledger.fxrate.enabled=true`
+ `mode=stub` 을 데모 오버레이에 주면 된다(`mode=real` 은 외부 Frankfurter API 호출이라
로컬 데모엔 부적합). **이 티켓의 범위가 아니고**, 안 해도 화면은 정직하다 — 비활성
상태를 비활성이라고 표시하는 것이 빈 표를 채우는 것보다 정확하다.

# Acceptance Criteria

- [x] **AC-0** — 세 가설을 로그 / 캐시 행 수 / 직접 호출로 갈랐다. 답은 3번이며,
      그 근거는 추론이 아니라 **속성 클래스 자신의 Javadoc**
- [x] **AC-1** — 원인이 환경 제약(의도된 기본값)이고, **화면은 이미 그 사실을 말한다**
      (`FxRatesTable` 경고 배지). 새로 만들 것 없음
- [x] **AC-2** — 판정을 BFF 원소 수 + `feedEnabled` 로 했다. 200 만으로 닫지 않았다:
      `feedEnabled=false` / `rates=[]` 를 각각 확인
- [x] **AC-3** — 재현 절차에 선행조건 기록: **테넌트 전환**(`demo-corp`) + 🔴 **웜업**.
      둘 다 없으면 각각 403 / 503 이 나오고 둘 다 "빈 화면" 처럼 읽힌다

# Definition of Done

- [x] AC-0~AC-3 — **결함 아님으로 종결**, 코드 변경 0
