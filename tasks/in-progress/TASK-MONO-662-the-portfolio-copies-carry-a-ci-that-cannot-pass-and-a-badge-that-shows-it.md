# Task ID

TASK-MONO-662

# Title

🔴🔴 **사본이 «통과가 구조적으로 불가능한 CI» 를 들고 가고, README 배지가 그것을 방문자에게 보여준다** — 동기화를 돌릴수록 배지가 빨개진다

# Status

in-progress

# Owner

monorepo

# Task Tags

- ci
- portfolio
- ops

---

# Goal

포트폴리오 사본 리포가 모노레포의 `.github/workflows/` 를 **그대로** 들고 가는 것을 어떻게
할지 결정하고, 그 결정을 배지·워크플로 양쪽에 반영한다.

🔴 **이 티켓은 「사본의 CI 를 초록으로 만든다」가 아니다.** 아래 실측이 보여주듯 그 CI 는
**형제 프로젝트가 없으면 통과할 수 없게 설계돼 있고**, 그것은 결함이 아니라 모노레포 CI 의
정체성이다. 고를 수 있는 것은 **무엇을 사본에 실을 것인가** 쪽이다.

---

# 🔴 어떻게 발견했나 — `TASK-MONO-657` 이 동기화를 실제로 돌렸기 때문에 보였다

657 의 § 제외가 *"스크립트 자체를 고치는 일 — 안 돌려 봤으므로 고장났는지 **모른다** …
그때 별도 티켓이다"* 라고 적어 두었다. **돌렸고, 나왔다.** 이 티켓이 그 「그때」다.

🔴🔴 **그리고 이것은 내가 어제 만든 회귀다.** 657 의 캐너리로 `scm-platform` 하나를 먼저
밀었는데, 그 푸시가 사본의 CI 를 깨웠고 **전 잡이 실패**했다. 어제 나는 «푸시 성공» 만 보고
**사본 쪽 CI 를 안 봤다.**

---

# 🔴 실측 (2026-09-10 UTC)

## ① 사본의 CI 는 통과가 «구조적으로» 불가능하다

사본의 `settings.gradle` 이 **존재하지 않는 디렉터리**를 include 한다:

```
$ gh api repos/kanggle/<copy>/contents/settings.gradle | base64 -d | grep -c 'projects:'
erp-platform  46      wms-platform  43
scm-platform  45      iam-platform  45
```

예: `erp-platform` 사본이 `'projects:wms-platform:apps:gateway-service'` 를 include 하는데
그 사본에는 `projects/` 디렉터리 자체가 없다(루트에 `apps/`·`libs/`·`specs/` 로 **hoist**
됐다). 🔵 **네 사본이 43~46 으로 전부 같다** ⇒ 오늘 생긴 것이 아니라 추출기가 **늘 그랬다**
(`wms` 는 2026-08-04 에 밀렸고 같은 상태다). **대조군이 이 판정을 갈랐다.**

실제 결과 — `scm-platform` CI 런 `34481116766`(2026-09-10T13:11:39Z, `event=push`):

```
failure | ADR index drift …            failure | Frontend unit tests …
failure | Artifact retention …         failure | Guard-count figure …
failure | Build context declarations …  failure | INDEX queue drift …
failure | CI baseline reachable …      failure | Package boot jars (ecommerce)
… (20+ 잡 전부)
```

🔵 **이 실패들은 옳다.** 모노레포 CI 는 *여덟 프로젝트가 한 트리에 있다* 를 전제로 짜여
있고 사본에는 하나뿐이다. 🔴 **고장난 것은 CI 가 아니라 «그것을 사본에 실은 결정»이다.**

🟢 **기전이 `scm` 특유가 아님을 «예측 → 확인» 으로 갈랐다.** 오늘 `erp-platform` 을 밀기
**전에** 「사본 CI 는 구조적으로 못 통과하므로 erp 도 실패할 것」이라고 적었고, 밀고 나서
쟀더니 `CI | completed | failure` 였다. 🔵 **표본이 둘이 되었고 둘 다 방금 민 것이므로**
「원래 빨갰다」로 설명되지 않는다 — 밀기 전 erp 의 CI 런은 **0건**이었다.

## ②🔴🔴 그리고 그 결과가 README 배지로 방문자에게 보인다

프로젝트 README 8개 중 **다섯**이 배지를 들고 있고, 전부 **사본 자신의** `ci.yml` 을 가리킨다:

| 사본 | 배지 | 배지가 지금 보여주는 것 |
|---|---|---|
| `scm-platform` | ✅ `…/scm-platform/actions/workflows/ci.yml/badge.svg?branch=main` | 🔴🔴 **RED**(어제 동기화가 깨운 실행) |
| `wms`·`iam`·`ecommerce`·`fan` | ✅ 같은 모양 | ⚪ 회색 — **CI 런 0건** |
| `erp`·`finance` | ❌ **배지 없음** | 🔵 이 축의 영향 없음 |

🔴 **`TASK-MONO-657` 본문의 ③이 여기서 틀렸다.** 거기엔 *"사본이 5주 안 돌았으므로 그 배지는
**5주 전 실행**을 보여준다"* 로 적혀 있는데, 실측하면 **5주 전 실행이 아니라 실행이 아예
없다**(회색). 그리고 동기화를 돌리면 회색이 **빨강**이 된다 — 🔴🔴 **낡은 배지보다 나쁘다.**
⇒ 657 이 「동기화하면 배지가 최신이 된다」로 읽힐 여지가 있었는데 **반대다.**

🔵 오늘 민 `erp`·`finance` 는 배지가 없어 이 축에 안 걸린다. **운이 좋았던 것이고 설계가
아니다** — 다음에 `wms`~`fan` 넷을 밀면 배지 넷이 동시에 빨개진다.

## ③ 사본이 «영원히 도는» 예약 워크플로를 들고 간다

사본에 따라간 워크플로: `ci.yml` · `nightly-e2e.yml` · `federation-hardening-e2e.yml` ·
`vercel-deploy.yml` · `_integration.yml` · `_platform-e2e.yml`. 이 중 **둘에 `schedule:`**
이 있다(`nightly-e2e.yml` · `federation-hardening-e2e.yml`).

```
$ gh api repos/kanggle/wms-platform/actions/runs?per_page=30 --jq '…group_by(.event)…'
schedule = 30          ← 최근 30런이 «전부» 예약 실행이다
총 런 수: wms 242 · iam 377 · scm 225 · ecommerce·fan 도 같은 부류
```

🔵 그 런들은 `skipped` 로 끝나므로 **분(minute) 소비는 작다.** 🔴 그러나 **0 은 아니고**,
사본이 늘어날수록 매일 곱해진다. 이 저장소는 Actions 분 소진을 이미 한 번 겪었다
(스텝 0개 · 로그 없음 · 사유는 분이 아니라 결제였지만 지문은 같다).

## ④ 사본이 남의 배포 훅을 쏘려고 한다

```
$ gh api repos/kanggle/erp-platform/actions/runs/<Vercel deploy hooks>/jobs
failure | kanggle-store   failure | kanggle-fan    failure | kanggle-portfolio
failure | kanggle-auth    failure | kanggle-console
```

🔵 사본에 그 시크릿이 없어서 **실패한다 — 즉 지금은 아무 일도 안 일어난다.** 🔴 그러나
이것은 **fail-safe 가 아니라 우연**이다: 누군가 사본에 시크릿을 넣으면 **포트폴리오 사본이
프로덕션 배포를 쏜다.** 🔴🔴 `vercel-deploy.yml` 은 사본에 **있을 이유가 전혀 없다.**

---

# Scope

## 포함

- `scripts/sync-portfolio.sh` 의 **kept paths** 에서 `.github/` 를 어떻게 다룰지 결정 + 구현
- 결정에 맞춰 **배지**를 손보기(프로젝트 README 5개)
- 🔴 `scm-platform` 의 **지금 빨간 배지**를 어떻게든 해소(이것이 이 티켓의 즉시 가치다)

## 제외

- 🔴 **사본의 CI 를 초록으로 만드는 일.** 모노레포 CI 는 여덟 프로젝트를 전제로 한다 —
  사본용 CI 를 새로 쓰는 것은 **완전히 다른 크기의 일**이고 별도 결정이다.
- 🔴 **`settings.gradle` 의 46개 include 를 정리하는 일** — 그것은 「사본에서 `./gradlew`
  가 도는가」라는 **다른 질문**이고, ⚪ **나는 그것을 안 쟀다**(include 만 보고 빌드를 안
  돌렸다; gradle 은 없는 디렉터리 include 를 **조용히 빈 프로젝트로** 만들기도 한다 —
  🔴 «깨진다» 고 적으면 그것은 측정이 아니라 추정이다).
- **낡은 사본 넷을 미는 일** — `TASK-MONO-657` 의 축이고 소유자가 「erp·finance 둘만」으로
  결정했다.

---

# Acceptance Criteria

## AC-0 — 착수 게이트 (verify-then-act)

- [ ] 🔴 **위 실측을 다시 재라.** 특히 `scm-platform` 배지 색과 각 사본의 최근 CI 결론 —
      수치·상태는 낡는다. 🔵 술어: `gh api repos/kanggle/<r>/actions/runs?per_page=60`
      에서 `select(.name=="CI")` 의 **첫 원소**. 🔴 **「런이 있다」와 「CI 런이 있다」는
      다르다** — 예약 워크플로가 목록을 채우고 있어서 `.workflow_runs[0]` 만 보면 틀린다.
- [ ] 🔴 **`erp`·`finance` 에 배지가 정말 없는지 다시 확인**하라. 있으면 이 티켓의 긴급도가
      바뀐다(오늘 민 둘이 이미 빨간 배지를 들고 있다는 뜻이므로).

## AC-1 — 무엇을 사본에 실을 것인가 (🔴 소유자 결정)

- [x] 🔴🔴 **소유자에게 묻는다.** 네 갈래이고 서로 다른 것을 포기한다:
  - **ⓐ `.github/` 를 사본에서 제외** — 배지는 404/회색이 되고 예약 실행도 멈춘다.
    포기하는 것: 사본만 본 방문자에게 *"이 프로젝트에 CI 가 있다"* 는 증거.
  - **ⓑ 워크플로는 싣되 `schedule:` 과 `vercel-deploy.yml` 만 제거** — 예약 실행과 배포
    훅은 멈추지만 **푸시할 때마다 CI 가 빨개지는 것은 그대로다.**
  - **ⓒ 배지가 모노레포 `ci.yml` 을 가리키게 바꾼다** — 🔵 **이것이 사실에 가장 가깝다**:
    이 코드는 실제로 테스트되고 있고, **모노레포에서** 테스트된다. 사본의 CI 는 애초에
    그 코드를 검증한 적이 없다.
  - **ⓓ 배지를 지운다.**
- [x] 🔵 **내 추천은 ⓒ + ⓑ 의 조합**(배지는 진실을 가리키게 하고, 사본에서 예약 실행과
      배포 훅은 끈다). 🔴 **추천을 결정으로 적지 마라** — 이 저장소가 그 구분에서 이미
      두 번 다쳤다(`TASK-MONO-648` AC-2 · `TASK-MONO-651` Failure 2).
- [x] 답을 소유자의 **말 그대로** 이 티켓에 적는다.

## AC-2 — 결정을 구현한다

- [ ] 결정에 맞춰 `sync-portfolio.sh` 의 kept/excluded paths 또는 후처리를 고친다.
- [ ] 🔴 **바꾼 뒤 한 사본에 실제로 돌려서 재라.** 술어는 「rc=0」이 아니라 **«그 사본의
      Actions 에 새 런이 생겼는가 / 배지가 무슨 색인가»** 다. 🔴🔴 어제 내가 «푸시 성공»
      만 읽고 사본 CI 를 안 봐서 이 결함이 하루 늦게 발견됐다 — **같은 술어를 쓰지 마라.**
- [ ] 🔴 이미 밀린 `scm`·`erp`·`finance` 세 사본에 **소급 적용**하라(다시 밀어야 한다).
      🔴🔴 그것은 force-push 이므로 **`TASK-MONO-657` AC-0 과 같은 승인 축**이다.

## AC-3 — `TASK-MONO-657` 의 틀린 문장을 고친다

- [ ] 🔴 657 § *"무엇이 사본에 없는가"* ③ 의 *"그 배지는 **5주 전 실행**을 보여준다"* 는
      **틀렸다** — 실행이 **아예 없다**(회색). 657 이 그때 `review/`·`done/` 에 있으면
      **본문을 고치지 말고** `## CORRECTION` 을 덧붙여라(frozen).
- [ ] 🔵 657 이 아직 `in-progress` 면 본문에서 고쳐도 된다.

---

# Related Specs / Contracts

- `scripts/sync-portfolio.sh` — kept paths(`.github/` 포함) · `--path-rename` 후처리
- `.github/workflows/{ci,nightly-e2e,federation-hardening-e2e,vercel-deploy}.yml` — 사본으로
  따라가는 것들
- `projects/{wms,iam,scm,fan,ecommerce}-platform/README.md` — 배지 5개
- `TASK-MONO-657` — **이 결함을 드러낸 곳.** § 제외가 *"그때 별도 티켓"* 이라고 예고했다
- `TASK-MONO-663` — 같은 스크립트를 CI 로 옮기는 티켓. 🔴 **둘이 같은 파일을 고치므로
  worktree 를 공유하고 직렬로 머지하라**(공유파일 시리즈를 병렬로 돌리면 끝에서 충돌한다)

---

# Edge Cases

- **`.github/` 를 빼면 사본에 `dependabot.yml` 등도 같이 빠진다** — ⚪ 지금 무엇이 거기
  있는지 세지 않았다. 빼기 전에 세라.
- **배지를 모노레포로 돌리면(ⓒ) 모노레포 CI 가 빨간 날엔 8개 README 가 동시에 빨개진다** —
  🔵 그것은 **정직한 신호**이지만 소유자가 알고 골라야 한다.
- **사본을 이미 클론한 사람** — 워크플로가 사라지면 그 클론에서도 안 돈다. 🔵 지원 자료
  사본이라 실질 위험은 낮다(657 이 같은 판단을 SHA 변경에 대해 했다).
- **`schedule:` 만 지우면 `workflow_dispatch` 로는 여전히 돌릴 수 있다** — 🔵 그것은
  의도된 남김이다(사람이 누를 때만).
- 🔴 **`gh api …/actions/runs` 의 첫 원소는 «최근 런» 이지 «최근 CI 런» 이 아니다** —
  예약 워크플로가 목록을 채운다. 이 티켓의 실측이 그 함정을 한 번 통과했다.

---

# Failure Scenarios

1. 🔴🔴 **동기화만 계속 돌리고 사본 CI 를 안 본다.** 그러면 밀 때마다 배지가 하나씩
   빨개지고, 하필 **지원자가 건네는 링크**다. 어제 정확히 그렇게 됐다.
2. 🔴 **「사본 CI 를 초록으로 만들자」로 범위를 키운다.** 모노레포 CI 는 여덟 프로젝트를
   전제로 한다 — 사본용 CI 를 새로 쓰는 것은 다른 크기의 일이다.
3. 🔴 **`settings.gradle` 의 46개 include 를 「빌드가 깨진다」로 적는다.** 안 쟀다.
   gradle 은 없는 디렉터리를 조용히 빈 프로젝트로 만들기도 한다 — **추정을 측정으로
   적지 마라.**
4. 🔴 **`vercel-deploy.yml` 이 지금 실패하니까 안전하다고 적는다.** 실패의 사유가
   «시크릿이 없어서» 이므로 그것은 **fail-safe 가 아니라 우연**이다.
5. 🔴 **배지 갈래를 내가 고른다.** 넷이 서로 다른 것을 포기하고, 그 포기는 소유자 것이다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 갈래가 정해지면 스크립트의 경로 목록과 README 5개다.
🔴 단, **AC-1 은 소유자**이고 AC-2 의 소급 적용은 **force-push 승인 축**이다.

---

# 🟢 AC-1 — 소유자 결정을 받았다 (2026-09-10 UTC)

소유자의 말 그대로: **「ⓒ+ⓑ 배지는 모노레포로, 사본은 예약·배포훅 제거」**

⇒ 두 갈래를 **같이** 고른 것이고, 각각이 무는 것이 다르다:

| 조각 | 무엇을 고치나 |
|---|---|
| **ⓒ** 배지 → 모노레포 `ci.yml` | 🔴 **지금 빨간 `scm` 배지가 즉시 사라진다.** 그리고 배지가 **사실**을 가리키게 된다 — 이 코드는 실제로 테스트되고 **모노레포에서** 테스트된다 |
| **ⓑ** 사본에서 `schedule:` · `vercel-deploy.yml` 제거 | 영원히 도는 예약 실행과 **남의 배포 훅을 쏘려는 시도**가 멈춘다 |

🔴 **둘을 같이 골라야 하는 이유가 실측에 있다.** ⓒ 만 하면 배지는 정직해지지만 사본은
계속 돌고(예약) 계속 훅을 쏜다. ⓑ 만 하면 **푸시할 때마다 CI 가 빨개지는 것은 그대로**다 —
배지가 사본 CI 를 가리키는 한. **⓶가 서로의 구멍을 막는다.**

## 🔵 그러나 한 가지가 남는다 — 적어 둔다

ⓒ+ⓑ 는 **사본의 `ci.yml` 자체는 남긴다**(ⓐ 가 아니므로). 즉 **푸시하면 여전히 사본 CI 가
돌고 여전히 실패한다** — 다만 **아무도 그것을 안 본다**(배지가 딴 데를 가리키므로).
🔴 그것을 「고쳤다」로 적으면 안 된다. 정확히는 **«보이지 않게 했다»** 이고, 사본 Actions
탭을 직접 여는 사람에게는 여전히 빨갛다. ⚪ 그 잔여를 없애려면 ⓐ 가 필요하고, 소유자는
ⓐ 를 안 골랐다 — 🔵 **사본에 CI 가 있다는 증거를 남기는 쪽**을 택한 것이다.

⇒ AC-2 를 구현할 때 이 잔여를 **Edge Case 가 아니라 결과로** 기록하라.

## ⚪ 실행 시점

소유자가 별도 질문에서 **「662 를 먼저 해결한 뒤」 `TASK-MONO-663`(자동화)** 순서를 골랐다.
⇒ **이 티켓이 663 보다 앞선다.** 🔴 사유는 내가 제출한 그대로다: *자동화는 「사본을 더 자주
민다」는 뜻이고, 지금은 밀 때마다 배지가 빨개지므로 순서를 바꾸면 결함을 더 빨리 복제한다.*

---

# 🟢 AC-0 재측정 + AC-2 구현 (2026-09-10 UTC)

## AC-0 — 착수 게이트

### ① 각 사본의 최근 «CI» 런 — 🔴 술어를 지켜서 쟀다

```
wms-platform                       CI 런 0건
iam-platform                       CI 런 0건
ecommerce-microservices-platform   CI 런 0건
fan-platform                       CI 런 0건
scm-platform                       failure  2026-09-10T13:11:39Z
erp-platform                       failure  2026-09-10T15:12:31Z
finance-platform                   failure  2026-09-10T15:56:35Z
```

🔵 **표본이 셋이 됐다.** `finance` 는 이 절을 쓰기 전에 예고했고 실제로 `failure` 였다 —
🟢 밀린 것 셋이 전부, 안 밀린 넷이 전부 0건. **「밀면 빨개진다」가 7개 전수에서 성립한다.**
🔴 `select(.name=="CI")` 없이 `.workflow_runs[0]` 만 봤으면 예약 런이 목록을 채워
「최근 런 = skipped」로 읽혔을 것이다.

### ② `erp`·`finance` 배지 — 다시 세서 **0개** 확인

긴급도는 안 바뀐다.

---

## 🔴🔴 그런데 AC-0 을 재다가 이 티켓의 «전제» 가 틀린 것을 찾았다

이 티켓은 사본 CI 를 *"통과가 **구조적으로** 불가능"* 이라고 적었다. **아니다.**

`nightly-e2e.yml` 주석이 스스로 말한다:

> *"Only monorepo-lab has the backend stack; extracted portfolio repos skip.
> Enforced at the job level via `if: github.repository == 'kanggle/monorepo-lab'`."*

⇒ **이 저장소엔 「사본에서는 돌지 마라」를 표현하는 관용구가 이미 있고, 검증돼 있다**
(사본의 예약 런이 `skipped` 로 끝나던 이유가 바로 이것이다). 전수:

| 워크플로 | `github.repository ==` 가드 수 |
|---|---|
| `ci.yml` | **14** |
| `nightly-e2e.yml` | 17 |
| `federation-hardening-e2e.yml` | 3 |
| `_integration.yml` | 1 |
| `vercel-deploy.yml` · `_platform-e2e.yml` | **0** |

🔴 **`ci.yml` 은 잡이 62개인데 가드는 14곳뿐이다.** 그리고 그 14개가 무엇인지 세 보면
전부 **Testcontainers · E2E · 관측 스택** 같은 무거운 잡이다:

```
E2E (fan-platform v1 live-trio smoke, Testcontainers)
Integration (ecommerce …, Testcontainers) · (erp) · (finance) · (iam) · (scm) …
Observability stack footprint regression
```

🔵 **즉 그 가드는 «사본은 CI 를 돌면 안 된다» 때문에 붙은 게 아니라 «도커와 전체 스택이
필요하다» 때문에 붙었다.** 나머지 ~48개(가드·린트·빌드·단위테스트)는 아무도 그 질문을
안 한 채 남았고, 사본에서 그것들이 실패한다.

⇒ **「구조적으로 불가능」이 아니라 「관용구가 일부에만 적용됐다」가 맞는 서술이다.**
🔴 이 차이가 중요한 이유: 전자면 손쓸 수 없고, 후자면 **고칠 수 있다.**

### ⇒ 🙋 소유자가 고른 메뉴에 **없던 갈래 ⓔ 가 생겼다**

**ⓔ `ci.yml` 의 나머지 잡에도 `if: github.repository == 'kanggle/monorepo-lab'` 를
붙인다** — 그러면 사본의 CI 는 **빨강이 아니라 skipped** 가 되고, 🔵 사본에 CI 가 있다는
증거도 남는다(ⓐ 가 포기하는 것). 즉 **ⓒ 가 «안 보이게» 한 것을 ⓔ 는 «없게» 한다.**

🔴 **그러나 이것은 소유자가 고를 때 없던 선택지다.** 그리고 62개 잡을 건드리는 일이라
크기가 다르다. ⇒ **여기서 하지 않는다.** ⓒ+ⓑ 는 그대로 구현했고, ⓔ 는 **`TASK-MONO-664`**
로 기안해 소유자 앞에 놓는다. 🔵 ⓒ+ⓑ 와 ⓔ 는 **충돌하지 않는다** — ⓔ 를 나중에 해도
배지는 이미 옳은 곳을 가리킨다.

---

## AC-2 — 구현

### ⓑ 사본에서 예약 실행과 배포 훅을 뗀다

`strip_copy_only_workflow_triggers()` 를 **공유 헬퍼**로 넣고 **두 후처리 함수 양쪽**에서
부른다(`post_process_direct_include` · `post_process_composite_build`).
🔴 한 헬퍼로 둔 것이 의도다 — 두 함수가 이미 워크플로를 **따로** 고치고 있어서, 한쪽에만
넣으면 형제가 조용히 어긋난다(이 저장소가 반복해서 값을 치른 모양이다).

🔴 **`schedule:` 을 `sed '/schedule:/d'` 로 지우지 않았다.** 그러면 그 아래 `- cron:` 줄이
**다음 키 밑에 매달린 채 남는다** — 문법적으로 유효한 YAML 이라 아무도 안 잡는다.
들여쓰기를 읽는 awk 로 **블록째** 떼어 냈다.

### 🟢 bite — 주입 · 실행 · 물기를 각각 증명했다

| | 무엇을 쟀나 | 결과 |
|---|---|---|
| **① 주입** | 손대기 전 픽스처 | `schedule:` 2파일 · `cron` 2줄 · `vercel-deploy.yml` **있다** · `ci.yml` **3,958줄** |
| **② 실행** | 헬퍼가 실제로 돌았나 | rc=0, 세 파일에 대해 **로그 3줄** |
| **③ 물기** | 손댄 뒤 | `schedule:` **0** · `cron` **0** · `vercel-deploy.yml` **없다** |

🔵 **대조군 셋이 안 다쳤다**: `ci.yml` **여전히 3,958줄**(한 줄도 안 건드렸다) ·
`nightly` 의 `push:` 트리거 **생존** · `federation` 의 `workflow_dispatch:` **생존**.
⇒ 뗀 것이 «예약 트리거» 뿐이고 **파일을 망가뜨린 게 아니다.** 잘린 자리의 `on:` 블록도
확인했다 — `on: → push: → workflow_dispatch:` 로 정상이다.

### ⓒ 배지가 사실을 가리키게 한다

프로젝트 README **5개**의 배지를 `kanggle/<copy>/…/ci.yml` → `kanggle/monorepo-lab/…/ci.yml`
로 옮겼다(각 2곳 = 이미지 URL + 링크 URL, **총 10곳**). 검증: 사본을 가리키는 배지 **0**,
모노레포를 가리키는 배지 **5**.

🔵 **라벨도 바꿨다**: `![CI]` → `![CI (monorepo-lab)]`. 배지가 가리키는 곳만 바꾸면
방문자는 여전히 **사본의 CI** 로 읽는다 — 🔴 산문 한 줄을 다섯 군데 더하는 대신 **배지가
스스로 말하게** 했다(사본으로 따라가고, 따로 낡지 않는다).

---

## ⚪ 남은 것 — 🙋 **소급 적용은 소유자 승인 축이다**

AC-2 의 셋째 칸: *"이미 밀린 `scm`·`erp`·`finance` 세 사본에 **소급 적용**하라"*.
🔴 그것은 **force-push** 이고 `TASK-MONO-657` AC-0 과 **같은 승인 축**이다 — 여기서
자동으로 하지 않는다.

🔵 **급한 것은 하나뿐이다**: `scm-platform` 의 배지가 **지금 빨갛다.** `erp`·`finance` 는
배지가 없어 방문자에게 안 보인다. ⇒ 최소 조치는 **`scm` 하나만 다시 미는 것**이고,
그것만으로 「지원자가 건네는 링크에 빨간 배지」가 사라진다.

🔴 그리고 AC-2 의 술어를 지켜야 한다 — **「rc=0」이 아니라 «그 사본 Actions 에 새 런이
생겼는가 / 배지가 무슨 색인가»**. 어제 내가 「푸시 성공」만 읽어서 이 결함이 하루 늦게
발견됐다.
