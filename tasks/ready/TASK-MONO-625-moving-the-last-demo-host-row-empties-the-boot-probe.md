# Task ID

TASK-MONO-625

# Title

🔴 **마지막 데모-호스트 행을 옮기면 부팅 프로브가 빈다** — `ADR-MONO-067` 단계 3 의 론처 몫

# Status

ready

# Owner

monorepo

# Task Tags

- adr
- demo
- launcher
- guard

---

# Goal

`ADR-MONO-067` 단계 3 이 요구하는 **론처의 console 행 이관**(`data-served`: `demo-host` → `vercel`)을,
**부팅 판정을 죽이지 않고** 수행한다.

# 🔴 이 티켓이 «한 단어 변경» 이 아닌 이유 (실측, 2026-09-05 UTC)

ADR 은 단계 3·4 의 론처 몫을 이렇게 적었다:

> *"console·fan 행을 옮길 때 필요한 것은 새 예외가 아니라 그 행의 `data-served` 를 `vercel`
> 로 바꾸는 것뿐이다."*

**형제 둘에서는 그것이 참이었다. console 에서는 거짓이다** — 이 행이 **마지막 데모-호스트 행**
이기 때문이다.

## 현재 세 행 (`infra/demo/aws/site/index.html:96-98`)

```
console    data-served="demo-host"  data-host="console"                      ← 남은 하나
ecommerce  data-served="vercel"     data-url="https://store.hubwang.com/"
fan        data-served="vercel"     data-url="https://fan.hubwang.com/"
```

## 🔴🔴 그 선언을 **부팅 판정도 읽는다**

`infra/demo/demo-up.sh:513-556` 이 같은 마크업에서 «찌를 표면» 을 유도한다
(*"선언의 출처는 여전히 한 벌이다 — 두 벌이면 하나만 고쳐진다"*). 그리고 하한이 있다:

```
SURFACE_ROW_FLOOR=3   # 론처가 약속하는 화면의 총 수 (추출이 깨졌는가)
SURFACE_FLOOR=1       # 부팅 때 실제로 찌를 표면의 수  ← provenance: "console 뿐"
```

**console 행을 옮기면 찌를 표면이 `0` 이 된다.**

| 하는 일 | 결과 |
|---|---|
| 행만 옮기고 `SURFACE_FLOOR=1` 그대로 | `surf_undecidable=("추출:0/1")` → **`final_rc=1`** ⇒ **모든 데모 부팅이 실패**한다. 그리고 그 실패는 *"데모가 안 떴다"* 로 읽힌다 |
| `SURFACE_FLOOR=0` 으로 내린다 | 루프가 **빈 채로 통과**한다 — `surf_ok` 가 비고 `✔ HTTP 표면` 줄이 아예 안 찍히며 `final_rc` 는 **무조건 0**. 🔴 **성공이 고장난 것과 구별되지 않는다** |

🔵 `demo-up.sh` 자신이 이 함정을 **이미 두 번 적어 뒀다**:

> 🔴🔴 `TASK-MONO-618` — **이 값을 억제와 같은 PR 에서 안 내리면 부팅이 영구 실패한다.**
> … `TASK-MONO-604` AC-3 이 *"583 이 없었다면 이 티켓이 부팅을 영구히 못 끝내게 만들었을 것"*
> 이라고 적은 그 자리다.

**이번이 그 계열의 세 번째이고, 앞의 둘과 다른 점은 「N=1 → N=0」이라는 것이다.**
형제들은 옮긴 뒤에도 console 이 남아 있어서 하한을 **내리면** 됐다. 여기서는 내릴 바닥이 없다.

## 🔴 진짜 원인 — **한 선언이 두 질문에 답하고 있고, 여기서 둘이 갈린다**

| 질문 | 지금 답하는 것 |
|---|---|
| «방문자는 어디로 가는가» | `data-served` |
| «부팅이 끝났는지 무엇으로 재는가» | **같은** `data-served` |

console 은 **방문자는 Vercel 로 가지만 데모 호스트도 여전히 서빙한다**(억제 오버레이가 없다 —
`infra/demo/console-vercel.override.yml` 부재, `projects.sh:59` 에 그 항목 없음).
⇒ **두 답이 처음으로 달라진다.** 이 티켓이 고를 것은 그 분기를 어떻게 표현하느냐다.

# Scope

**In**

- `infra/demo/aws/site/index.html` — console 행의 서빙 출처 선언
- `infra/demo/demo-up.sh` — 부팅 표면 판정(하한 · 유도 규칙)
- `infra/demo/verify-demo-wrapper.sh` — `(z14)` 가 새 모양을 물도록
- 필요하면 `infra/demo/aws/site/check-launcher-fresh.sh`

**Out**

- 🔴 **데모의 console 억제**(`console-vercel.override.yml`). 그것은 **별개이고 선행이 다르다** —
  `TASK-MONO-618` 이 fan 억제를 «왕복이 세션까지 성립한 뒤에» 얹은 것처럼, console 억제는
  `TASK-MONO-624`(홉 ②③④⑤) 뒤이고, 게다가 **585 가 기록한 알려진 한계**(`console-bff` 는
  Vercel 에서 안 닿는다)가 «억제해도 되는가» 자체를 여는 질문으로 만든다.
  🔵 **이 티켓은 억제를 전제하지 않는다** — 오히려 «데모 호스트가 아직 서빙 중» 이 이 티켓의
  전제다.
- 방문자 화면 코드. 이 티켓은 **론처와 부팅 판정**만 만진다.

# Acceptance Criteria

- [ ] **AC-0 (착수 시 재측정 + 분기 결정)**
      1. 세 행의 `data-served` 를 **다시 읽는다**(위 표를 상속하지 마라).
      2. `SURFACE_FLOOR` · `SURFACE_ROW_FLOOR` 의 **현재 값과 provenance 주석**을 읽는다.
      3. 🔴 **`console.<DEMO_DOMAIN>` 을 데모 호스트가 아직 서빙하는지 확인한다**
         (`projects.sh` 의 `[console]` 체인에 `*-vercel.override.yml` 이 있는가).
         **있으면 이 티켓의 전제가 무너진 것이므로 멈추고 보고한다.**
      4. 아래 세 갈래 중 하나를 고르고 **왜 나머지 둘이 아닌지** 적는다:

      | 갈래 | 내용 | 🔴 대가 |
      |---|---|---|
      | **A** | 마크업에 **부팅 프로브용 선언을 따로** 둔다(예: `vercel` 행에도 `data-demo-probe="console"`). 한 벌 유지, 파생만 둘 | 속성이 하나 늘고 `(z14)`·`demo-up.sh`·`check-launcher-fresh.sh` 셋이 그 규칙을 알아야 한다 |
      | **B** | 부팅 프로브를 **론처 마크업에서 떼어** `projects.sh`/체인에서 유도한다 | 🔴 «선언이 두 벌» 로 돌아간다 — ADR 이 명시적으로 피한 모양 |
      | **C** | 부팅 완료 판정에서 **HTTP 표면 축을 폐기**하고 도메인 헬스만 쓴다 | 🔴 축이 하나 줄어든다. 「컨테이너는 healthy 인데 화면은 502」를 못 잡게 된다 |

      🔵 **추천은 A** 이지만 **AC-0 이 골라라** — 위 표는 착수 전 추정이다.

- [ ] **AC-1 — 행을 옮긴다.** `data-served="vercel"` + `data-url="https://console.hubwang.com/"`,
      그리고 🔴 **`data-host` 를 반드시 제거**한다. 마크업 주석이 *"두 속성은 상호 배타"* 라고
      적었고 `(z14)` 가 `vercel` 행의 잔존 `data-host` 를 **실패로 문다**
      (`verify-demo-wrapper.sh:2651`).

- [ ] **AC-2 — 부팅 판정이 여전히 무언가를 잰다.**
      🔴🔴 **하한을 0 으로 내려서 통과시키지 마라.** 빈 루프의 `rc=0` 은 «표면이 정상» 과
      «측정이 죽었다» 를 **구별하지 못한다** — 이 저장소가 이름 붙인 실패다
      (*"줄어드는 모집단에 하한을 걸면 성공이 고장난다"*).
      통과 기준: **부팅 완료 지문에 표면이 하나 이상 이름으로 찍힌다**(예: `console=307`).

- [ ] **AC-3 — bite.** 고른 갈래를 **주입해서** 증명한다:
      (a) console 행의 선언을 지우면 → **판정 불가**(조용한 통과 아님)
      (b) 프로브 대상이 0이 되면 → **빨간다**
      (c) 정상 상태 → **초록**
      🔴 **주입이 실제로 됐는지 먼저 단언하라.** `(z14)` 는 이미
      `verify-demo-wrapper.sh:3082` 에서 프로브 행을 주입하는 선례를 갖고 있다.

- [ ] **AC-4 — 하한의 provenance 를 갱신한다.** `SURFACE_FLOOR` 주석이 지금
      *"= 1 (console 뿐)"* 이라고 적고 있다. 🔴 값을 바꾸든 안 바꾸든 **그 문장은 거짓이 된다** —
      같은 커밋에서 고쳐라(같은 사실이 두 곳에 있으면 한쪽만 고쳐진다).

- [ ] **AC-5 — 부팅 완료 지문이 바뀐 것을 적는다.** `demo-up.sh` 주석이 창 #3 의
      `console=307 web.fan-platform=307`(2/2) → `console=307`(1/1) 변화를 이미 기록했다.
      🔴 **다음 기동 창에서 옛 지문을 기다리면 창이 영원히 안 열린다** — 새 지문을 적어라.

- [ ] **AC-6 — `(z14)` 가 새 모양을 문다.** `z14_floor=3` 은 **그대로**다(론처가 약속하는 화면
      총 수는 안 줄었다). 🔵 `demo-host` 분기의 bite 는 주입 행이 유지하므로 공허해지지 않는다 —
      **그것을 확인하고 적어라**(모집단이 0이 되는 분기는 조용히 죽는다).

# Related Specs

- [`docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md)
  § 단계 표(단계 3) · § 단계 2 완료(`(z14)` 축 신설) · § 단계 3·4 는 이 자리도 함께 지난다

# Related Contracts

없음.

# Related Tasks

- `TASK-MONO-583` — `(z14)` 를 뒤집어 `data-served` 를 만든 티켓(단계 2 의 론처 몫)
- `TASK-MONO-603` — 링크가 활성인데 **컨테이너가 숨겨져** 있던 나머지 절반
- `TASK-MONO-604` · `TASK-MONO-618` — store · fan 의 **억제** 몫 (이 티켓의 Out)
- `TASK-MONO-585` — 단계 3 의 앱 몫. `done`. § 알려진 한계에 `console-bff` 미도달 기록
- `TASK-MONO-624` — console 왕복 실측. **억제**의 선행이지 이 티켓의 선행은 아니다

# Edge Cases

① 🔴 **`SURFACE_ROW_FLOOR`(3)와 `SURFACE_FLOOR`(1)는 서로 다른 것을 잰다.** 전자는 «추출이
   깨졌는가», 후자는 «찌를 것이 있는가». 하나를 고치며 다른 하나를 같이 만지면 두 축이 섞인다.

② **`(z14)` 의 self-test 주입 행**(`data-host="z15probe"`)은 `demo-host` 분기를 살려 두는
   유일한 모집단 원소가 될 수 있다. 🔴 그 행을 «이제 안 쓰니 지우자» 로 지우면 분기가
   **공허해진다** — 지우지 마라.

③ **론처는 배포본과 저장소 사본이 따로다.** 저장소를 고쳐도 `hubwang.com` 이 곧바로 바뀌지
   않는다. 🔵 라이브 확인은 배포 뒤에, 그리고 **서빙본**으로 하라
   (`check-launcher-fresh.sh --origin`).

④ 🔴 **데모가 꺼져 있을 때도 이 변경의 효과가 보여야 한다** — 그것이 이관의 목적이다.
   판정: 데모 `stopped` 에서 론처를 열어 console 링크가 **활성**인가.

# Failure Scenarios

① **하한을 0 으로 내리고 초록을 받는다** → 🔴 그것은 통과가 아니라 **계측기 상실**이다.
   AC-2 가 그것을 금지한다. 갈래 C 를 고른 경우에도 «표면 축을 폐기했다» 를 **명시**해야지
   하한만 0 으로 두고 넘어가면 안 된다.

② **`data-host` 를 남긴 채 `data-served="vercel"` 로 바꾼다** → `(z14)` 가 문다(설계대로).
   🔵 이 경우는 실패가 아니라 가드가 일한 것이다.

③ **부팅이 영구 실패한다** → AC-2 를 안 지킨 것이다. `demo-up.sh` 가 이 시나리오를 미리
   적어 뒀으니 그 주석을 읽어라.

# Definition of Done

- [ ] 론처 세 행이 전부 `data-served="vercel"` + `data-url`, `data-host` **0개**
- [ ] 부팅 완료 지문에 표면이 **이름으로** 찍힌다 (빈 통과 아님)
- [ ] `SURFACE_FLOOR` provenance 주석이 새 사실과 일치
- [ ] `(z14)` · `demo-up.sh` 양쪽에 bite 가 있고 **주입이 단언됐다**
- [ ] 🔵 남은 단계 3 조각(**억제**)이 무엇이고 무엇에 막혀 있는지 ADR 이나 이 티켓에 적혀 있다

---

분석=Opus 5 / 구현 권장=Opus (AC-0 이 **설계 분기**를 고르고, AC-2 가 «하한을 0 으로 내리면
안 되는 이유» 를 지켜야 한다 — 기계적 치환이 아니다).
