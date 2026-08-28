# Task ID

TASK-MONO-590

# Title

🔴 **한도의 ~73% 를 «빌드하지 않기로 한 배포» 가 쓰고 있다** — 판정을 «생성 후 취소» 에서
«생성 전» 으로 옮긴다. 🔴 **그 전에 전제 하나를 실측해야 한다.**

# Status

ready

# Owner

monorepo

# Task Tags

- vercel
- ci
- measurement
- adr-mono-067

---

# Goal

`ignoreCommand` 는 배포를 **만든 뒤** 취소한다. Vercel 의 일일 한도가 세는 것은
**Deployments *Created* per Day** 이므로, 취소된 배포도 슬롯을 그대로 먹는다.

판정을 생성 **앞**으로 옮긴다:

```
git.deploymentEnabled: false     자동 배포 0건 (프리뷰 + main 전부)
  +  Deploy Hook                  정말 필요할 때만 1건
  +  기존 vercel-ignore.sh         발사 조건 = 이미 있는 그 술어를 그대로
```

🔴 **그러나 이 설계의 전제가 미측정이다.** `TASK-MONO-588` 은 **객체 형태**(브랜치 이름을
직접 지정)만 실측했고 §AC-2 에서 스스로 못 박았다 — *"전역 off 형태는 `main` 을 포함하니
**이 결과를 그리로 옮기지 마라**"*. **AC-0 이 그것을 재기 전에는 AC-1 을 시작하지 않는다.**

---

# Context — 실측 (2026-08-27 UTC)

## ① 한도가 무엇을 세는지, 그리고 그것이 계정 단위라는 것 — 문서가 직접 말한다

`https://vercel.com/docs/limits` (2026-08-25 판):

| 행 | Hobby | Pro | scope |
|---|---:|---:|---|
| **Deployments *Created* per Day** | **100** | 6,000 | **`owner`** |
| Deployments per Hour (Free) | 100 | 450 | `owner` |
| Deployments per 5 min (Free) | 60 | 120 | `owner` |

- 🔵 이름이 **Created** 다 — `TASK-MONO-588` 이 실측한 *"건너뛴 배포도 먹는다"* 가 문서의
  낱말에 박혀 있다.
- 🔵 `scope=owner` — `TASK-MONO-575` 가 *"같은 초에 동시 빨강"* 으로 추론한 **계정 전체 공유
  카운터**를 독립 출처가 확인한다. **프로젝트별이 아니다.**

## ② 100 이 어디로 가는가 — 24h 실측

측정 창 `2026-08-26T08:00Z → 2026-08-27T09:00Z`:

| 출처 | 커밋 | × 프로젝트 | 이벤트 |
|---|---:|---:|---:|
| `korea-travel-guide` main | 39 | 1 | **39** |
| `monorepo-lab` main | 17 | 2 | **34** |
| mono PR 브랜치 | 10 | 2 | 20 |
| ktg PR 브랜치 (dependabot 6건 열림) | 7 | 1 | 7 |
| | | | **≈ 100** |

🔴 **측정기를 먼저 밝힌다** — Vercel 은 커밋이 아니라 **푸시**마다 배포한다. main 은 스쿼시
머지라 1:1 이므로 **73(=39+34) 은 단단하다**. 브랜치 칸은 여러 커밋이 한 푸시일 수 있어
**과대**, force-push · 수동 Redeploy 는 **과소** — 양방향 오차다. 합이 100 에 붙는 것을
정밀도로 읽지 마라. [[feedback_a_reported_figure_must_name_what_was_measured]]

🔴 **`korea-travel-guide` 가 같은 계정이고 같이 한도에 걸린다** — `08-27T08:07:02Z` 에
`Deployment rate limited`. 이 저장소만 조여도 충분하지 않을 수 있다는 뜻이고, AC-3 이 그
사실을 어떻게 다룰지 정한다.

## ③ 그 73건이 거의 전부 **아무것도 안 굽는다**

recent main 커밋의 Vercel status 는 전부 `success` / **`Canceled by Ignored Build Step`** 이다.
`ignoreCommand` 는 **정확히 작동하고 있다** — 그런데 그 정확한 판정이 슬롯을 먹은 **뒤**에
나온다. ⇒ 한도의 대부분을 «만들고, 안 굽기로 하고, 버린» 배포가 쓴다.

## ④ 🔵 술어는 이미 한 곳에 있다 — 새로 만들 것이 없다

```
projects/fan-platform/web/fan-platform-web/vercel-ignore.sh
  SPECS=( ':/projects/fan-platform/web' ':/projects/fan-platform/package.json' … )
  exec bash "$ROOT/scripts/vercel-should-build.sh" "${SPECS[@]}"
```

각 프로젝트의 `vercel-ignore.sh` 가 **pathspec 목록**을 들고, 공용
`scripts/vercel-should-build.sh` 가 **판정**을 한다. 훅 워크플로는 **그 스크립트를 그대로
호출**하면 된다.

🔴 **pathspec 을 워크플로에 옮겨 적지 마라.** 같은 사실이 두 곳에 살면 한쪽만 갱신된다 —
이 저장소가 반복해서 낸 비용이다. [[feedback_one_fact_in_two_sections_only_one_gets_fixed]]

🔵 **종료코드 규약이 훅 설계에 그대로 맞는다**: `rc=0` = 건너뜀 · `rc=1` = 빌드. 그리고 판정
불가(얕은 clone, 부모 커밋 없음, git 부재)일 때 **`rc=1`(빌드)로 fail-open** 한다 ⇒ 훅
쪽에서도 *"모르면 배포한다"* 가 되어 **안전한 쪽**이다. [[feedback_an_optimisation_must_fail_toward_more_work]]

## ⑤ 모집단 — `vercel.json` 은 셋, 프로젝트는 둘

| `vercel.json` | Vercel 프로젝트 | 상태 |
|---|---|---|
| `infra/demo/aws/site/vercel.json` | `kanggle-portfolio` | 살아 있음 (론처, `hubwang.com`) |
| `projects/fan-platform/web/fan-platform-web/vercel.json` | `kanggle-fan` | 살아 있음 (`fan.hubwang.com`) |
| `projects/ecommerce-microservices-platform/apps/web-store/vercel.json` | **미생성** | `TASK-MONO-582` 가 대기 중 |

`scripts/check-vercel-build-triggers.sh` 의 `FLOOR=3` 이 이 셋을 하한으로 지킨다.

## 🔴🔴 ⑥ `ignoreCommand` 는 **훅이 만든 배포에도 그대로 돈다** (2026-08-27 실측)

**이 티켓의 설계에 구멍이 하나 있고, 오늘 그것에 실제로 걸렸다.**

`ignoreCommand` 는 배포가 **어떻게 생겼는지** 를 묻지 않는다 — git push 든, 대시보드
Redeploy 든, **Deploy Hook 이든** 생성된 뒤에 똑같이 돈다. 그러니 AC-1 이 훅을 쏴도
**ignore 스텝이 그 배포를 취소할 수 있다.**

### 실측 — 오늘 재배포가 정확히 그렇게 죽었다

소유자가 `kanggle-fan` Production 에 OIDC env 4개를 넣고 대시보드에서 **Redeploy** 를
눌렀다. 결과: **취소**. 그리고 `/api/auth/session` 은 그대로 **500**.

원인은 가드가 아니라 **질문의 모양**이다:

> `ignoreCommand` 가 묻는 것은 **«이 프로젝트 파일이 바뀌었나»** 다.
> 그런데 **env 변경을 위한 재배포는 정의상 파일 변경이 0** 이다.
> ⇒ **env 를 반영하려는 배포를, ignore 스텝이 정확히 골라서 취소한다.**

당시 `main` tip 이 건드린 것은 `.claude/` · `platform/` · `tasks/` · `CLAUDE.md` ·
`.github/` 와 `projects/*/tasks/INDEX.md` 였고, 팬의 pathspec 은 `:/projects/fan-platform/web`
이하다. `projects/fan-platform/tasks/INDEX.md` 는 그 아래가 **아니다** → 건너뜀.
🔵 **가드는 정확히 작동했다.** 우리가 원한 판정이 그것이 아니었을 뿐이다.

### 🔵 그래서 AC-1 이 답해야 할 것이 하나 늘었다

훅으로 생성을 게이트하면 **판정이 이미 훅 쪽에서 났다**(같은 `vercel-ignore.sh` 술어를 쓴다).
그 뒤에 같은 술어를 **한 번 더** 돌리는 `ignoreCommand` 는 중복이고, 위 사례처럼
**해로울 수 있다** — «파일은 안 바뀌었지만 이 배포는 필요하다» 를 표현할 방법이 없기 때문이다.

⇒ AC-1 은 셋 중 하나를 **명시적으로 고른다**:

| # | 안 | 대가 |
|---|---|---|
| **1** | `deploymentEnabled:false` 인 프로젝트에서는 `ignoreCommand` 를 **뺀다** | 🔵 판정이 한 곳에만 산다. 🔴 훅이 잘못 쏘면 막을 것이 없다 |
| **2** | 둘 다 남기고, **훅이 쏘는 배포는 항상 빌드되게** 한다 | ignore 스크립트가 «훅이 쐈다» 를 알 방법이 필요하다(env 변수?) — **미조사** |
| **3** | 그대로 두고 «가끔 훅이 헛방» 을 수용 | 🔴 **env 변경은 영원히 이 경로로 못 간다** — 오늘 그것이 실제 비용이었다 |

🔴 **AC-3 의 `check-vercel-build-triggers.sh` 재작성도 이 선택에 걸린다** — 1번을 고르면
그 가드가 지키던 대상이 프로젝트마다 달라진다.

---

# Acceptance Criteria

## AC-0 — 🔴 **전제를 먼저 잰다. 이것이 이 티켓의 첫 항목이고, 실패하면 여기서 끝난다**

**묻는 것**: 불리언 `git.deploymentEnabled: false` 가 **production(main) 배포의 «생성»까지**
막는가?

🔴 **`TASK-MONO-588` 의 결과를 상속하지 마라.** 588 이 잰 것은 **객체 형태 + 브랜치 이름
직접 지정**이고, 그것은 `main` 을 건드리지 않았다. 588 자신이 그 이전(transfer)을 금지했다.

### 측정 설계 — 🔵 588 이 성공한 방식을 그대로 쓴다

🔴 **대조군을 «다른 프로젝트» 로 잡아 안전하게 만든다.** 불리언 키를 **`fan` 의
`vercel.json` 에만** 넣고, 론처(`infra/demo/aws/site/vercel.json`)는 **손대지 않는다.**

| | |
|---|---|
| **대상** | `kanggle-fan` — 🔵 지금 프로덕션이 어차피 `500`(OIDC env 미설정, `TASK-FAN-FE-018`)이라 배포가 한 번 빠져도 잃을 것이 없다 |
| **대조군** | `kanggle-portfolio` — 같은 푸시에 **배포가 붙는가**. 붙으면 «푸시가 정상이었다» 가 증명되고, fan 의 부재가 **그 키 때문**이라는 것이 좁혀진다 |
| **역방향 (A–B–A)** | 🔴🔴 **대조군 하나로는 부족하다.** 키를 **제거해 재푸시**했을 때 fan 의 배포가 **돌아와야** 한다. 그것이 «이 브랜치의 다른 무언가»·«그날 Vercel 이 느렸다» 를 배제한다 |
| **대기 시간** | 이력상의 도착 간격(fan 은 portfolio 보다 약 11초 뒤)의 **30배 이상**을 기다린 뒤에 «없다» 라고 말한다 |
| **계측기 2개** | commit **status** API 와 **check-runs** API 를 **교차** 확인한다 — 한쪽만 보고 «없다» 라고 하지 않는다 |

[[feedback_control_group_design_four_axes]] [[env_empty_detector_output_is_not_absence]]

🔴 **판정은 `main` 에서 나와야 한다.** 브랜치 프리뷰가 안 생기는 것은 이 질문의 답이 아니다
(588 이 이미 그 절반을 알고 있다). 측정을 위해 `main` 에 한 번 올리고 **같은 날 되돌린다**;
되돌린 뒤 론처가 낡지 않았는지 확인한다.

### 세 갈래 결과 — **어느 쪽이든 결정을 바꾼다**

| 결과 | 뜻 | 다음 |
|---|---|---|
| **A. `main` 생성까지 막힌다** | 설계의 전제가 참 | AC-1 로 (훅이 **필수**) |
| **B. 프리뷰만 막히고 `main` 은 산다** | 🟢 **더 좋다** | 훅이 **불필요**. AC-1 을 대폭 축소하고 그 사실을 티켓에 적는다 |
| **C. 아무것도 안 막힌다** | 불리언 형태가 이 계정/플랜에서 무효 | 🔴 **STOP.** 이 설계는 죽었다. 티켓을 «판정: 불가» 로 닫고 남는 선택지(Pro / 머지 묶기 / dependabot 축소)를 기록한다 |

🔴 **B 를 «A 의 약한 판» 으로 읽지 마라** — 정반대다. B 면 이 티켓의 대부분이 사라진다.

## AC-1 — Deploy Hook 배선 (AC-0 이 **A** 일 때만)

1. 🔴 **소유자**: 각 프로젝트 `Settings → Git → Deploy Hooks` 에서 훅 생성 → URL 을 GitHub
   Actions **secret** 으로 등록. (문서상 이 페이지의 컨트롤은 `Disconnect` · `Git LFS` ·
   `Deploy Hooks` · `Require Verified Commits` 넷뿐이다 — 프리뷰만 끄는 토글은 **없다**.)
2. `main` 푸시 워크플로에 스텝을 추가한다:
   - 판정 = **`vercel-ignore.sh` 를 그대로 실행** (④ 참조). `rc=1` → 훅 발사, `rc=0` → 안 함.
   - 🔴 pathspec 을 워크플로에 **복사하지 않는다.**
3. `web-store` 는 프로젝트가 **아직 없다** ⇒ 훅도 없다. `vercel.json` 을 어떻게 둘지
   **명시적으로 정하고 적는다**(그냥 두기 / 키 없이 두기). 🔴 «나중에 생기면 그때» 로
   비워 두지 마라 — `TASK-MONO-582` 가 프로젝트를 만드는 순간 자동 배포가 **다시 켜진다.**

## AC-2 — 🔴 **가드 — 훅을 안 쏘면 빨개져야 한다**

이 설계가 들여오는 **새 실패 모드는 조용한 낡음**이다.
`scripts/check-vercel-build-triggers.sh` 헤더가 이미 그 이름을 적어 두었다 —
*"진짜 피해는 색깔이 아니라 **그동안 론처가 낡은 판을 계속 서빙한 것**이다."*

- 🔴 **선언 파일 grep 금지.** 워크플로에 훅 스텝이 «있는지» 보는 가드는, 훅이 실패해도
  초록이다. **요청의 결과**를 봐야 한다. [[feedback_declaration_files_are_not_the_runtime_state]]
- 판정 축 = **배포된 커밋 vs 그 프로젝트 경로를 마지막으로 건드린 `origin/main` 커밋**.
  어긋나면 RED. 🔵 이 저장소가 이미 아는 모양이다 — *"라이브 검증 0단계 = 이미지 시각 vs
  머지 시각 대조"*. [[env_pulled_checkout_holds_a_stale_build]]
- 🔴 **어디서 도는지 먼저 정한다.** 러너 없는 가드는 썩는다. 그리고 **밤에만 도는 곳에
  두면 머지 시점에 안 잡힌다.** [[feedback_two_correct_exclusions_compose_into_a_hole]]

## AC-3 — 🔴 **`check-vercel-build-triggers.sh` 의 전제가 바뀐다 — 같이 고친다**

그 가드는 *"`ignoreCommand` 가 무관한 커밋을 건너뛰는가"* 를 임시 저장소에서 실제로 실행해
검증한다. AC-1 이 랜딩하면 **배포가 애초에 안 생기므로 `ignoreCommand` 는 거의 죽은 기전**이
된다. 그대로 두면 **아무도 안 쓰는 것을 계속 테스트하며 영원히 초록**이다.

- 지우지 말고 **무엇을 지키는지 다시 쓴다** — `web-store` 처럼 훅이 없는 프로젝트에서는
  `ignoreCommand` 가 여전히 유일한 방어다.
- `FLOOR=3` 은 그대로 유효하다(파일 개수 하한). 프로젝트 수와 혼동하지 마라 — 지금
  **파일 3 / 살아 있는 프로젝트 2** 다.

[[feedback_retract_the_exemption_when_the_defect_is_fixed]] [[feedback_a_pin_can_freeze_the_defect_it_was_written_to_guard]]

## AC-4 — **이 설계가 안 고치는 것을 적는다**

🔴 산문으로 흘리지 말고 표로 남긴다. 이걸 안 적으면 «배포 문제를 고쳤다» 가
«Vercel 문제를 고쳤다» 로 읽힌다. [[feedback_a_partial_deletion_reads_as_a_total_one]]

| 안 고쳐지는 것 | 근거 |
|---|---|
| **이미지 축** — Transformations 112% · Cache Writes 320% 초과 | `TASK-MONO-587`. 배포 수와 **무관한 축**이다. `web-store` 를 올리면 이미지가 **402** 로 죽는 위험은 그대로 |
| **Hobby 의 비상업 조항** | 문서: *"Hobby teams are restricted to non-commercial personal use only"* |
| **`korea-travel-guide` 의 39/일** | 같은 계정, 다른 저장소. 이 티켓 범위 밖 — 🔴 **그러나 계정 카운터를 공유하므로, 이 저장소를 0 으로 만들어도 ktg 단독으로 39% 를 쓴다** |
| **프리뷰 URL 상실** | 🔴 `TASK-MONO-588` AC-4 가 이미 적었다 — 프런트 e2e 가 `nightly-e2e.yml` 에만 있어 **머지 시점에 화면을 안 보는데**, 프리뷰까지 없으면 «머지 전에 화면 보는 수단» 이 **0** 이 된다. `TASK-FAN-FE-018` 이 같은 구멍의 다른 얼굴. 🔴🔴 **08-28 재계수가 이 행을 좁혔다** — 그날 브랜치 이벤트 32 개가 **프리뷰를 0 개** 만들었다(28 개는 ignore 스텝이 굽기 전에 취소). 대가는 «프리뷰 상실» 이 아니라 **«web 경로를 건드리는 PR 의 프리뷰 상실»** 이고 그런 PR 은 16 중 0 이었다 — 아래 §재계수 ③ |

## AC-5 — 효과를 **재고** 적는다, 추정하지 말고

랜딩 후 24h 창에서 ② 와 **같은 방법**으로 다시 센다. 🔴 **«이제 안전하다» 로 닫지 마라** —
계정 축이라 ktg 가 그대로면 여전히 닿을 수 있다. **잰 숫자와 그것이 무엇을 잰 것인지**를
함께 적는다.

## AC-6 — 🔴🔴 **브랜치 축을 별도로 정한다. AC-0 에 매달아 두지 마라**

08-28 재계수(아래 §)가 이 티켓의 전제 하나를 바꿨다: **브랜치 몫이 main 몫과 같은 32 이고,
그 32 가 프리뷰를 0 개 만들었다.** 위 표가 «미적용» 한 줄로 넘긴 행이 실제로는 **AC-0 없이
건드릴 수 있는 가장 큰 덩어리**다.

- 🔴 **AC-0 과 다른 질문이다.** AC-0 = **불리언** 이 production 생성을 막는가.
  AC-6 = **객체 형태 + `main: true`** 로 브랜치 생성만 끄는가. ktg 가 오늘 후자를 5칸
  대조군과 함께 실측했지만, 🔴 **그 결과를 이 계정의 다른 프로젝트로 이전하지 마라** —
  `TASK-MONO-588` 과 이 티켓이 두 번 못박은 그 금지가 여기에도 그대로 걸린다.
  **`kanggle-fan` · `kanggle-portfolio` 각각에서 다시 물어야 한다.**
- 🔴 **그래도 한도가 풀린 뒤에만 잴 수 있다** — 억제는 *부재*로 나타나고, 한도도 같은 부재를
  낸다. AC-0 과 **같은 착수 조건**(대조군 초록)을 쓴다. 🔵 다만 둘은 **같은 창에서 함께 잴 수
  있다** — 서로 다른 프로젝트/다른 키를 쓰므로.
- 결정 사항: 이 저장소가 **프리뷰를 아예 포기하는가**, 아니면 `preview/*` 같은
  **명시 접두사만 열어 두는가**(ktg 가 후자를 골랐다). 🔴 후자를 고르면 «화면을 봐야 하는 PR» 이
  그 접두사를 쓰도록 하는 **사람 규약**이 생기고, 규약은 지켜지지 않는다는 것을 전제로 적는다.
- 🔴 이 항목이 **AC-4 의 프리뷰 행을 소비한다** — 좁혀진 대가를 여기서 명시적으로 값 매긴다.

---

# Related Specs

- `scripts/vercel-should-build.sh` — 공용 판정기 (종료코드 규약 · fail-open)
- `projects/fan-platform/web/fan-platform-web/vercel-ignore.sh` — pathspec 정본 (fan)
- `projects/ecommerce-microservices-platform/apps/web-store/vercel-ignore.sh` — 동 (web-store)
- `infra/demo/aws/site/vercel.json` — 론처. 🔴 AC-0 의 **대조군**
- `scripts/check-vercel-build-triggers.sh` — AC-3 대상 (`FLOOR=3`)
- `.github/workflows/ci.yml` — `dorny/paths-filter` 기존 배선
- `tasks/review/TASK-MONO-588-does-disabling-previews-actually-stop-deployment-creation.md`
- `tasks/ready/TASK-MONO-587-the-image-quota-nobody-asked-about-is-already-over.md`
- `docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md`

# Related Contracts

없음.

---

# Edge Cases

| 케이스 | 처리 |
|---|---|
| AC-0 착수 시 **한도가 아직 안 풀렸다** | 🔴 **측정 불가**다. 배포가 거절되면 «키가 막았다» 와 «한도가 막았다» 가 **같은 부재**를 낸다 — 대조군(portfolio)이 배포를 **받는 것**을 먼저 확인하고 시작한다 |
| 대조군에도 배포가 안 붙는다 | 🔴 **STOP** — 그 런은 아무것도 못 잰다. 한도 / 인증 / webhook 을 먼저 배제한다 |
| AC-0 결과가 **B** | 🟢 훅 불필요. AC-1 을 «키만 넣기» 로 축소하고 AC-2 가드도 그만큼 줄인다. **닫지는 마라** — AC-3·AC-4·AC-5 는 그대로 필요 |
| AC-0 결과가 **C** | 🔴 설계 사망. 판정을 기록하고 닫는다. 남는 선택지는 Pro($20/월) / 머지 묶기 / ktg dependabot 축소 |
| 소유자가 그 사이 **Pro 로 올렸다** | 🔵 **닫지 마라, 그러나 축소하라** — 6,000/일이면 급하지 않다. 다만 «만들고 버리는 배포» 자체는 여전히 낭비이므로 AC-0 의 **측정만** 남기고 우선순위를 내린다 |
| 훅 URL 이 유출됐다 | 🔴 훅은 **인증 없이 배포를 트리거**한다. 즉시 회수·재발급. secret 으로만 다룬다 |
| `web-store` 프로젝트가 생겼다 | AC-1.3 이 정한 대로 그 파일을 처리한다 — 🔴 자동으로 켜지게 두지 마라 |

---

# Failure Scenarios

| 시나리오 | 징후 | 방지 |
|---|---|---|
| AC-0 을 건너뛰고 키부터 넣는다 | `main` 이 조용히 배포를 멈추고 **론처가 낡은 판을 서빙** | AC-0 을 첫 항목으로 못박음 + 대조군을 다른 프로젝트로 |
| 대조군 없이 «배포가 안 생겼다» 를 판정으로 쓴다 | 한도·인증·webhook 어느 것이든 같은 부재를 낸다 | AC-0 의 대조군 + **A–B–A 역방향** |
| pathspec 을 워크플로에 복사한다 | `vercel-ignore.sh` 만 갱신되고 훅은 옛 경로를 본다 → 배포가 조용히 안 나감 | AC-1.2 — 스크립트를 **호출**한다 |
| 가드를 «훅 스텝이 있는가» 로 만든다 | 훅이 401/404 로 실패해도 초록 | AC-2 — **배포된 커밋**을 본다 |
| `check-vercel-build-triggers.sh` 를 안 건드린다 | 죽은 기전을 테스트하며 영원히 초록 | AC-3 |
| «배포 문제를 고쳤다» 로 587 을 덮는다 | `web-store` 를 올리는 순간 이미지가 402 | AC-4 표 |
| 랜딩 후 효과를 추정으로 적는다 | ktg 가 그대로라 실제로는 여전히 한도에 닿는데 «해결» 로 기록 | AC-5 — 같은 방법으로 **다시 센다** |

---

# ✅ 외부 실측 — `korea-travel-guide` 가 **객체 형태 + 와일드카드**로 브랜치 배포를 0 으로 만들었다 (2026-08-28 UTC)

이 티켓이 이름으로 지목한 저장소(§Context ②·③ 의 「`korea-travel-guide` 가 같은 계정이고 같이
한도에 걸린다」)에서 오늘 랜딩했다. **이 티켓의 AC-0 을 대신하지 않는다** — 아래 §「무엇을 답하지
않는가」를 먼저 읽어라.

## 넣은 것

```json
// korea-travel-guide/vercel.json  (PR #781 → #782)
{ "git": { "deploymentEnabled": {
    "*": false, "**": false, "main": true, "preview/*": true } } }
```

🔴 **`*` 만으로는 거의 아무것도 못 잡는다.** minimatch 의 `*` 는 `/` 를 넘지 않고, 이 계정의
브랜치 이름은 대부분 슬래시를 갖는다(`content/…`, `docs/…`, dependabot 은 `dependabot/npm_and_yarn/
keystatic/core-0.6.8` 로 **셋**). `*` 만 넣었다면 사실상 전 브랜치가 기본값 `true` 로 남으면서
**설정은 들어간 것처럼 보였을 것**이다 — 아무것도 물지 않는 가드.
[[feedback_why_a_guard_does_not_bite]]

## 물린 것 — 네 규칙 전부, 대조군과 함께

| 대상 | 기대 | 관측 |
|---|---|---|
| `vercel-preview-off` (슬래시 없음) | 배포 없음 | Vercel status **none** |
| `probe/vercel-check` (슬래시 1개) | 배포 없음 | **none** (+4분, +15분 재확인) |
| `main` 머지 커밋 | 배포 **있음** | **`Vercel=success`** ← 양성 대조군 |
| `preview/hatch-check` | 배포 **있음** | `pending` → **`success`** (~2.5분) |
| `probe/hatch-control` (동시 푸시) | 배포 없음 | **none** ← 음성 대조군 |

🔵 **마지막 두 줄을 같은 시각에 푸시한 것이 판정의 근거다.** 하나만 봤다면 「배포가 생겼다」가
패턴 덕인지 Vercel 이 그냥 다시 다 굽기 시작한 건지 구별되지 않는다. 이 티켓의 Failure
Scenarios 가 「대조군 없이 «배포가 안 생겼다» 를 판정으로 쓴다」로 미리 적어둔 그 함정이다.

## 이 티켓의 산술에 미치는 영향 — **7 건, 그 이상은 아니다**

§Context ② 의 24h 표 기준:

| 행 | 이전 | 이후 | 비고 |
|---|---:|---:|---|
| ktg PR 브랜치 (dependabot 6건 포함) | 7 | **0** | 이 변경이 지운 전부 |
| ktg main | 39 | 39 | **그대로** — `main: true` 를 의도적으로 남겼다 |
| mono PR 브랜치 | 20 | 20 | 같은 객체 형태를 쓰면 0 이 될 수 있다(미적용) |
| mono main | 34 | 34 | **AC-0/AC-1 이 겨냥하는 진짜 덩어리** |

🔴 **73 은 손대지 않았다.** 이 티켓이 옳게 지적한 대로 한도의 대부분은 **main 커밋**이고, 그것을
줄이려면 여전히 불리언 + Deploy Hook 설계가 필요하다. 브랜치 억제는 **27 중 7 을 지웠을 뿐**이다.
AC-5 가 「랜딩 후 효과를 추정으로 적는다」를 실패 시나리오로 못박아 둔 만큼, 이 줄도 추정이 아니라
위 표의 행 하나로 읽어야 한다.

## 🔴 무엇을 답하지 **않는가** — AC-0 은 그대로 열려 있다

AC-0 이 묻는 것은 **불리언 `deploymentEnabled: false`** 가 production 생성까지 막는가이다.
위 실측은 **객체 형태**이고, `main: true` 를 **명시적으로 담고 있다.** 두 형태는 다른 것이며,
`TASK-MONO-588` 이 자기 결과의 이전을 금지한 것과 **같은 이유로** 이 결과도 이전 금지다.
[[feedback_a_verifiable_mechanism_is_not_the_cause]]

🔵 다만 **문서화된 동점 규칙 하나는 실측됐다** — 「여러 규칙에 걸리면 하나라도 `true` 면 배포」.
`main` 이 `**: false` 와 `main: true` 에 동시에 걸리고도 배포됐고, `preview/*` 도 같은 방식으로
열렸다. AC-1 이 객체 형태를 쓰기로 한다면 이 규칙에 기댈 수 있다.

## 🔵 §⑥ 의 삼지선다에 대한 실물 한 표 — ktg 는 **1번 안**의 작동 사례다

ktg 에는 `ignoreCommand` 가 **없다**. 게이트는 `git.deploymentEnabled` 하나뿐이고, 그래서
§⑥ 가 기록한 사고 — 「env 변경을 위한 재배포를 ignore 스텝이 정확히 골라서 취소한다」 — 가
구조적으로 일어나지 않는다. `git.deploymentEnabled` 는 **git 이 트리거한 배포만** 다스리므로
대시보드 Redeploy 와 Deploy Hook 은 지나간다.

🔴 대가도 같이 적는다: git 쪽 판정이 **경로를 모른다.** ktg 는 단일 앱이라 「main 이면 굽는다」로
충분하지만, 이 저장소는 프로젝트가 여럿이라 **어느 경로가 어느 프로젝트를 굽는가**가 필요하고
그 술어는 `vercel-ignore.sh` 에만 있다. 1번 안을 고르면 그 술어의 거처가 훅 쪽으로 옮겨간다 —
AC-1.2 가 이미 「스크립트를 **호출**한다」로 정해둔 그 지점이다.

## 재현

```bash
gh api repos/<owner>/<repo>/commits/<sha>/status \
  --jq '[.statuses[]|"\(.context)=\(.state)"]|join(",")'
```

브랜치 푸시 직후 ~30초부터 붙는다. **부재 판정에는 반드시 같은 창에서 배포가 붙는 대조군을
함께 둔다** — 부재는 한도·인증·webhook 어느 것으로도 만들어진다.
[[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]]

---

# 🔴🔴 재계수 (2026-08-28 UTC) — 이 저장소의 몫은 「34 + 20」이 아니라 **64 이고, 그 64 가 전부 아무것도 안 구웠다**

§Context ② 의 census 는 `08-26T08:00Z → 08-27T09:00Z` 창이다. **그 숫자를 상속하지 않고**
오늘(`08-28T00:00Z →` 현재) 같은 방법으로 다시 셌다. [[feedback_recount_population_dont_inherit_scope]]

## 계측기를 먼저 밝힌다 — 그리고 그것이 과금 축이 **아니라는 것**도

세는 것은 **GitHub commit-status 행 중 context 가 `Vercel – *` 인 것**이다. 한 행 = 한 번의
**생성 시도**. 🔴 **한도에 막혀 거절된 시도도 행을 낸다**(`state=failure`) — 그러니 이 수치는
«생성된 배포» 가 아니라 «시도» 이고, 거절분이 슬롯을 먹는지는 **이 계측기로 알 수 없다.**
잰 축과 과금 축이 다르다. [[feedback_a_reported_figure_must_name_what_was_measured]]

🔵 첫 집계기는 **0 행**을 냈다 — `gh api` 에 `--arg` 를 넘긴, 이 저장소가 이미 이름 붙여 둔 그
함정이고 `2>/dev/null` 이 사유를 삼켰다. 「0 행이면 exit 1」 가드가 그것을 «부재» 로 읽는 것을
막았다. [[env_bash_jq_absent_gh_checks_exit]] [[env_empty_detector_output_is_not_absence]]

## ① 몫 — main 과 **브랜치가 정확히 반반**이다

| 축 | 이벤트 | 비고 |
|---|---:|---|
| `monorepo-lab` main (16 커밋 × 2 프로젝트) | **32** | 스쿼시 머지라 1:1 |
| `monorepo-lab` PR 브랜치 (16 PR × 2 프로젝트) | **32** | PR 당 정확히 2 — 헤드 푸시 1회씩 |
| **저장소 합** | **64** | |
| `korea-travel-guide` main (12 커밋 × 1) | 12 | |
| `korea-travel-guide` PR 브랜치 | **0** | 오늘 랜딩한 객체 형태가 지웠다(위 §외부 실측) |

🔴 **§Context ② 의 「mono PR 브랜치 = 20」은 오늘 32 다** — 그 행은 «가끔 있는 잔돈» 이 아니라
**main 과 같은 크기**이고, 위 표가 «미적용» 한 줄로 넘긴 자리다.

## ② 🔴🔴 결과 분류 — **64 중 구워진 것 0**

| 결과 | main | 브랜치 | 합 |
|---|---:|---:|---:|
| `success :: Canceled by Ignored Build Step` | 27 | 28 | **55** |
| `failure :: Deployment rate limited — retry in 24 hours.` | 5 | 4 | **9** |
| **실제로 빌드된 것** | **0** | **0** | **0** |

§③ 이 «거의 전부» 라고 쓴 것이 오늘은 **정확히 전부**다. 🔵 론처가 낡지 않았다는 뜻이기도 하다 —
`infra/demo/aws/site` 가 안 바뀌었으니 안 구워진 것이 **옳은** 판정이다.

## ③ 🔴🔴 그래서 **AC-4 의 「프리뷰 URL 상실」 행은 오늘의 모집단에서 반증된다**

그 행은 «브랜치 배포를 끄면 머지 전에 화면 볼 수단이 0 이 된다» 로 적혀 있다. 그런데 오늘
브랜치 이벤트 **32 개 중 28 개가 굽기 전에 ignore 스텝에 취소**됐고 4 개는 한도에 거절됐다 ⇒
**오늘 만들어진 프리뷰 URL 은 0 개다.** 잃을 것이 이미 없다.

🔴 **행을 지우지 말고 좁혀라**: 진짜 대가는 «프리뷰를 잃는다» 가 아니라
**«web 경로를 건드리는 PR 의 프리뷰를 잃는다»** 이고, 오늘 그런 PR 은 **16 중 0** 이었다.
[[feedback_a_partial_deletion_reads_as_a_total_one]]

🔵 **그 «0» 은 취소 상태에서 추론한 것이 아니라 직접 확인했다** — 취소 행은 술어의 *출력*이고,
한도에 거절된 4 개는 **ignore 스텝이 돌기도 전에 죽어서** 아무 말도 하지 않는다(그 4 개를
취소분으로 뭉뚱그리면 대리지표로 부재를 판정하는 것이다). 그래서 **변경 파일 집합 ∩ pathspec**
을 따로 셌다: 오늘 바뀐 파일 **40 개 중 fan(`projects/fan-platform/web` 외 4) · 론처
(`infra/demo/aws/site`) pathspec 에 걸리는 것 0 개**. 🔵 **양성 대조군** — 같은 패턴을 최근
main 400 커밋에 돌리면 **83 건**이 걸린다(패턴이 0 을 내는 종류의 것이 아니다).
[[feedback_absence_verdict_from_a_proxy_is_not_a_measurement]] [[env_empty_detector_output_is_not_absence]]

## ④ 소진 경계 — `2026-08-28T10:32Z`, **한 커밋 안에서** 갈렸다

| 커밋 | 시각 | 상태 |
|---|---|---|
| `892349745` | 10:27:56Z / 10:28:04Z | portfolio **success** · fan **success** |
| `d8734d3c0` | 10:32:33Z / 10:32:40Z | portfolio **failure(rate limited)** · fan **success** ← **7 초 사이에 경계** |
| `3a658657b` 이후 | 10:58Z ~ | 둘 다 **failure** |

🔵 한도가 **밤사이 풀렸다**(08-27T13:58Z 빨강 → 08-28T07:17Z 초록). 🔴 **그러나 리셋 축을 고르지
않는다** — 「UTC 달력일 리셋」과 「롤링 24h」가 **둘 다 이 관측을 설명한다.** 이 창에서 두 저장소가
낸 시도는 76 이고 100 이 아니라서, 달력일이면 안 센 소비자가 24+ 있어야 하고 롤링이면 어제분이
합쳐진다 — **어느 쪽도 배제되지 않았다.** 추정 금지. [[feedback_measurement_needs_a_validity_predicate]]

## ⑤ 소비 법칙 — **티켓 처리량에 1:1 로 붙는다**

오늘 PR 16 건 ⇒ **PR 당 4 이벤트**(브랜치 2 + main 2), 닫은 티켓 7 건 ⇒ **티켓 당 ≈ 9 이벤트**.
🔴 즉 **큐를 빨리 비우는 세션 자체가 이 한도의 지배적 소비자**이고, 오늘 10:32Z 의 소진은 외부
요인이 아니라 **그 세션이 만든 것**이다. 그리고 그 한도는 `ADR-MONO-067` 의 라이브 검증
(`574` · `582` · `583` · `585` · `586`)이 **쓰려고 기다리는 바로 그 자원**이다.

## ⑥ AC-0 상태 — 🔴 **2026-08-28 현재 측정 불가** (Edge Case 가 미리 적어둔 그대로)

지금 대조군(`kanggle-portfolio`)이 배포를 **못 받는다** ⇒ 「키가 막았다」와 「한도가 막았다」가
같은 부재를 낸다. **착수 조건은 대조군이 초록인 것**이고, 그 확인은 배포를 새로 만들지 않고도 된다:

```bash
gh api repos/:owner/:repo/commits/$(git rev-parse origin/main)/status \
  --jq '.statuses[]|select(.context|startswith("Vercel"))|"\(.context)=\(.state) :: \(.description)"'
```

🔴 `--arg` 를 쓰지 마라 — `gh api` 에는 없다(위 §계측기).

