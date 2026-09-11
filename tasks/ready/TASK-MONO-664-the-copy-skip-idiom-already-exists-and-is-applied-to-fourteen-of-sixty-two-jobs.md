# Task ID

TASK-MONO-664

# Title

🔴 **「사본에서는 돌지 마라」 관용구가 이미 있는데 62개 잡 중 14곳에만 붙어 있다** — 그래서 사본 CI 는 「구조적으로 불가능」이 아니라 그냥 안 물어본 것이다

# Status

ready

# Owner

monorepo

# Task Tags

- ci
- portfolio

---

# Goal

`.github/workflows/ci.yml` 의 잡들에 `if: github.repository == 'kanggle/monorepo-lab'`
가드를 **어디까지 붙일지 소유자에게 묻고**, 그 결정을 구현한다.

🔴 **이 티켓은 「사본의 CI 를 초록으로 만든다」가 아니다.** 초록이 아니라 **`skipped`** 로
만드는 것이고, 그 둘은 다르다 — `skipped` 는 *"이 잡은 이 저장소의 질문이 아니다"* 라는
**참인 진술**이고, 초록은 사본에서 만들 수 없는 **거짓**이다.

---

# 🔴 어떻게 발견했나 — `TASK-MONO-662` 의 전제가 틀렸다

662 는 사본 CI 를 *"통과가 **구조적으로** 불가능"* 이라고 적고, 그 위에서 네 갈래
(ⓐ~ⓓ)를 소유자에게 물었다. 🔴🔴 **그 전제가 틀렸다.** 662 의 AC-0 을 재측정하다가
`nightly-e2e.yml` 이 **스스로 반증**하는 것을 읽었다:

> *"Only monorepo-lab has the backend stack; extracted portfolio repos skip.
> Enforced at the job level via `if: github.repository == 'kanggle/monorepo-lab'`."*

⇒ **관용구가 이미 있고, 이미 돌고 있다.** 사본의 예약 런이 전부 `skipped` 로 끝나던
이유가 바로 이것이다 — 662 는 그 사실을 「분 소비가 작다」로만 적고 **왜 skipped 인지는
안 물었다.**

🔵 **이것이 «주장은 출처 자신의 말과 대조하라» 의 실례다.** 662 의 「구조적으로 불가능」은
내가 **사본의 실패 로그를 보고 추론한 것**이고, 같은 저장소의 **워크플로 주석이 그
추론을 반박**하고 있었다.

## 🔴 실측 (2026-09-10 UTC) — 관용구가 어디에 붙어 있나

| 워크플로 | `github.repository ==` 가드 수 |
|---|---|
| `ci.yml` | **14** |
| `nightly-e2e.yml` | 17 |
| `federation-hardening-e2e.yml` | 3 |
| `_integration.yml` | 1 |
| `vercel-deploy.yml` | **0** (🔵 662 가 사본에서 **아예 제거**했다) |
| `_platform-e2e.yml` | **0** |

🔴 **`ci.yml` 은 잡이 62개다.** 가드가 붙은 14곳이 무엇인지 세면 전부 **무거운 잡**이다:

```
E2E (fan-platform v1 live-trio smoke, Testcontainers)
E2E (gateway-master live-pair smoke, Testcontainers)
E2E (scm-platform v1 cross-service smoke, Testcontainers)
E2E smoke (iam docker-compose)
Integration (ecommerce ${{ matrix.shard }}, Testcontainers)
Integration (erp-platform | fan-platform | finance-platform | iam | scm-platform, …)
Integration (inventory + inbound + gateway-service, Testcontainers)
Integration (master-service + notification-service + outbound-service, Testcontainers)
Integration (platform-console console-bff, Testcontainers + WireMock JWKS)
Observability stack footprint regression
```

🔵 **즉 그 가드는 «사본은 CI 를 돌면 안 된다» 때문이 아니라 «도커와 전체 스택이 필요하다»
때문에 붙었다.** 나머지 ~48개(가드 · 린트 · 빌드 · 단위 테스트)는 **아무도 그 질문을 안 한
채** 남았고, 사본에서 그것들이 실패한다:

```
ADR index drift …            Frontend unit tests …       INDEX queue drift …
Artifact retention …         Guard-count figure …        Package boot jars (ecommerce)
Build context declarations … Hook fixtures (Windows) …   Lifecycle stage dirs …
… (사본 scm-platform CI 런 34481116766 에서 20+ 잡 전부 failure)
```

🔴 **이 구분이 값지다**: *"구조적으로 불가능"* 이면 손쓸 수 없고, *"관용구가 일부에만
적용됐다"* 면 **고칠 수 있다.**

---

# Scope

## 포함

- `ci.yml` 의 **가드 없는 잡들**에 `if: github.repository == 'kanggle/monorepo-lab'` 를
  어디까지 붙일지 결정 + 구현
- 🔴 **붙이면 안 되는 잡이 있는지 먼저 세라** — 아래 § Edge Cases 의 첫 줄

## 제외

- 🔴 **사본용 CI 를 새로 쓰는 일.** 「사본에서 실제로 이 프로젝트를 빌드한다」는 완전히
  다른 크기의 일이고, 사본의 `settings.gradle` 이 없는 디렉터리 43~46개를 include 하는
  문제(662 § 제외)를 먼저 풀어야 한다.
- 🔴 **배지를 다시 손대는 일** — `TASK-MONO-662` 가 모노레포를 가리키게 바꿨고(라벨도
  `CI (monorepo-lab)`), 🔵 **이 티켓이 끝나도 그 결정은 유효하다**: 사본의 CI 가 skipped 가
  되면 배지는 *"실행 없음"*(회색)이 되고, 그건 **모노레포 CI 를 가리키는 것보다 덜 정확하다.**
- `nightly-e2e.yml` · `federation-hardening-e2e.yml` 의 `schedule:` — 662 가 **사본에서**
  뗐다. 🔵 모노레포 쪽은 그대로다(거기서는 돌아야 한다).

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act)

- [ ] 🔴 **위 수치를 다시 재라.** `ci.yml` 의 잡 수와 가드 수는 이 저장소에서 **거의 매주
      움직인다.** 술어: 잡 수 = `grep -cE "^  [a-z0-9_-]+:$"`, 가드 수 =
      `grep -c "github.repository =="`. 🔵 **둘의 차이가 이 티켓의 크기다.**
- [ ] 🔴 **`TASK-MONO-662` 가 머지됐는지 확인하라.** 안 됐으면 **STOP** — 같은 파일 계열을
      건드리고, 662 가 `vercel-deploy.yml` 을 사본에서 빼는 변경을 들고 있다.

## AC-1 — 어디까지 붙일 것인가 (🔴 소유자 결정)

- [ ] 🔴🔴 **소유자에게 묻는다.** 세 갈래다:
  - **ⓧ 전부** — `ci.yml` 의 62개 잡 전부에 가드. 사본 CI 는 **완전히 `skipped`**.
    🔵 포기하는 것: 사본에서 **아무것도** 안 돈다(그래도 워크플로 파일은 보인다).
  - **ⓨ 「사본에서 의미 있는 것」만 남기고 나머지에 가드** — 예: 사본에도 그 프로젝트의
    단위 테스트·빌드는 있으므로 **그것만 돌게** 한다. 🔴 그러려면 그 잡들이 사본에서
    **실제로 통과하는지** 재야 하고, ⚪ **안 쟀다**(`settings.gradle` 문제가 걸린다).
  - **ⓩ 안 한다** — 662 의 ⓒ 로 배지가 이미 딴 데를 가리키므로 **아무도 안 본다.**
    🔵 포기하는 것 없음. 🔴 얻는 것도 없다 — Actions 탭을 여는 사람에겐 여전히 빨갛다.
- [ ] 🔵 **내 추천은 ⓧ** 이고 사유는 *ⓨ 가 「사본에서 통과하는가」라는 **안 잰 전제**에
      매달려 있기* 때문이다. ⓨ 를 고르려면 AC-0 에 그 측정을 먼저 넣어야 한다.
      🔴 **추천을 결정으로 적지 마라.**
- [ ] 답을 소유자의 **말 그대로** 이 티켓에 적는다.

## AC-2 — 구현하고 «실제로 skipped 가 되는지» 재라

- [ ] 🔴 술어는 **「YAML 이 valid 하다」가 아니라 «사본 Actions 에서 그 잡이 `skipped` 로
      끝나는가»** 다. ⇒ 한 사본에 실제로 밀어서 확인해야 하고, 🔴🔴 **그것은 force-push 라
      `TASK-MONO-657` AC-0 과 같은 승인 축이다.**
- [ ] 🔴 **모노레포에서는 하나도 안 줄어야 한다.** 대조군: 이 PR 의 `ci.yml` 잡들이
      **전부 실행**되는지(SKIPPED 0). 🔴🔴 가드를 잘못 쓰면 **모노레포 자신의 CI 가 조용히
      전부 skipped 가 되고, 그것은 초록으로 보인다** — 이 저장소가 `skipped` 를 초록으로
      읽는 부류를 이미 여러 번 밟았다.
- [ ] ⚪ 못 쟀으면 **사유와 함께 ⚪** 로 적고, 그 항목이 갈 곳을 적어라.

---

# Related Specs / Contracts

- `.github/workflows/ci.yml` — 62개 잡 / 가드 14곳
- `.github/workflows/nightly-e2e.yml` — 🔵 **관용구의 출처이자 주석이 의도를 말하는 곳**
- `scripts/sync-portfolio.sh` — 사본이 워크플로를 들고 가는 경로
- `TASK-MONO-662` — **이 티켓을 낳은 곳.** 그 티켓의 「구조적으로 불가능」이 틀렸다
- `TASK-MONO-663` — 동기화를 CI 로. 🔴 소유자가 **662 다음** 순서로 정했다

---

# Edge Cases

- 🔴🔴 **붙이면 안 되는 잡이 있다** — `changes`(경로 필터)와 required 4종은 **사본에서도
  돌아야 의미가 있는가?** ⚪ 안 쟀다. 🔵 그러나 그 넷은 `main` 의 **브랜치 보호에 등록된
  이름**이고, 모노레포에서 `skipped` 가 되면 **required 가 만족된 것으로 읽힌다** —
  🔴 **가드를 잘못 붙이면 보호가 무력화된다.** 반드시 세고 나서 붙여라.
- **`if:` 가 이미 있는 잡** — 새 조건을 `&&` 로 합쳐야 하고, 🔴 기존 조건이
  `always()` 나 `failure()` 면 합치는 순간 의미가 바뀐다.
- **사본에서 `skipped` 인 잡이 60개면 Actions 탭이 회색 60줄이 된다** — 🔵 빨강보다는
  낫지만 «아무 일도 안 하는 CI» 로 보인다. ⓐ(워크플로 제외)와 비교해 **무엇이 더 정직한가**
  는 소유자 판단이다.
- 🔴 **이 저장소의 `ci.yml` 은 자주 바뀐다** — 새 잡이 추가될 때 가드가 안 붙으면 **사본이
  다시 빨개진다.** ⚪ 그것을 무는 가드가 필요한지는 이 티켓의 축이 아니지만, **필요하다는
  사실은 적어 둔다**(게이트 없는 규칙은 반드시 낡는다).

---

# Failure Scenarios

1. 🔴🔴 **required 4종에 가드를 붙인다.** 모노레포에서 `skipped` 가 되고, GitHub 는
   `skipped` 를 실패로 안 본다 ⇒ **브랜치 보호가 조용히 무력화된다.**
2. 🔴 **모노레포 CI 가 전부 skipped 가 된 것을 초록으로 읽는다.** 대조군(AC-2 둘째 칸)이
   그것을 막는다.
3. 🔴 **「YAML 이 valid 하다」를 판정으로 쓴다.** 술어는 사본 Actions 에서 실제로
   `skipped` 로 끝나는가다.
4. 🔴 **ⓨ 를 「사본에서 단위 테스트는 통과할 것」이라는 가정 위에 고른다.** 안 쟀다 —
   사본의 `settings.gradle` 이 없는 디렉터리 43~46개를 include 한다.
5. 🔴 **662 보다 먼저 한다.** 같은 파일 계열이고 662 가 `vercel-deploy.yml` 제거를 들고 있다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 관용구를 복제하는 일이고 모양이 이미 있다.
🔴 단, **AC-1 은 소유자**이고 AC-2 의 사본 확인은 **force-push 승인 축**이다.
🔴🔴 Edge Cases 첫 줄(required 4종)은 **Sonnet 에게 맡기지 말고 직접 세라** — 틀리면
브랜치 보호가 무력화된다.
