# Task ID

TASK-MONO-650

# Title

🔴🔴 **서비스 README 8개 중 스크린샷이 있는 것은 하나뿐이다** — 68장을 찍어 놨는데 그 68장이 갈 길이 `.gitignore` 에 등재돼 있다

# Status

review

# Owner

monorepo

# Task Tags

- portfolio
- docs
- readme

---

# Goal

`TASK-MONO-648` 이 확보한 **큐레이션 후보 68장**을 **서비스별 README 에 실제로 배선**한다.

🔴 **648 은 «찍는 것»까지가 축이다.** 648 AC-4 는 *"README 용 큐레이션을 어느 것으로 할지
소유자에게 물어라. 서비스당 3–6장"* 까지만 요구한다 — **고른 뒤 어디에 어떻게 넣는가는
어느 티켓에도 없었다.** 그 사이에 68장은 이렇게 놓여 있다:

```
portfolio-captures/          ← 68장이 여기 있고
.gitignore:106               ← 이 줄이 그 디렉터리를 무시한다
```

즉 **지금 상태로는 68장이 어느 README 에도 도달할 수 없다.** 이 티켓이 그 길이다.

---

# 🔴 착수 전 실측 (2026-09-09 UTC)

## ① 8개 서비스 README 중 스크린샷이 있는 것은 **하나**

| README | 스크린샷 | 비고 |
|---|---|---|
| `projects/ecommerce-microservices-platform/README.md` | **7장** | `docs/screenshots/01~07`, HTML `<img>` |
| `projects/platform-console/README.md` | 0 | |
| `projects/wms-platform/README.md` | 0 | |
| `projects/scm-platform/README.md` | 0 | |
| `projects/erp-platform/README.md` | 0 | |
| `projects/finance-platform/README.md` | 0 | |
| `projects/iam-platform/README.md` | 0 | |
| `projects/fan-platform/README.md` | 0 | |

🔴🔴 **이 표를 만들면서 내 술어가 한 번 틀렸다 — 기록해 둔다.** 처음에 `grep -c '!\['` 로
세서 「전부 0장」이라 보고했다. **ecommerce 는 마크다운 이미지 문법이 아니라 HTML
`<img src=...>` 를 쓴다.** 즉 `![` 는 **배지만** 세고 있었고(shields.io · badge.svg),
유일하게 존재하는 스크린샷 7장을 통째로 놓쳤다.

⇒ **AC 에 술어를 박는다**: 이 저장소에서 「README 가 이미지를 참조하는가」는 `![...]()` 와
`<img src=...>` **둘 다** 물어야 한다. 한쪽만 물면 **이미 있는 것을 «없다»고 보고**한다.

## ② 🔴🔴 캡처의 축과 README 의 축이 다르다 — 콘솔 하나가 여섯으로 쪼개진다

캡처는 **앱**으로 묶여 있다(console 41 · store 16 · fan 11). README 는 **서비스**별이다.
그래서 콘솔 59장을 README 기준으로 다시 나누면 이렇게 된다:

| README 로 갈 곳 (nav 그룹) | 쓸 수 있음 | 못 씀(빈값·저하) |
|---|---|---|
| **IAM** | **9** | 0 |
| **ERP** | **6** | 0 |
| **Finance** | **4** | 0 |
| WMS | 4 | 3 |
| SCM | 4 | 2 |
| **E-Commerce** | **1** | **9** |
| 고객 신원 (`/accounts`) | 1 | 0 |
| 조직 설정 (`/subscriptions`·`/partnerships`) | 2 | 0 |
| (최상단) `/console` · `/dashboards/overview` | 1 | 1 |

#### 🔴🔴 2026-09-09 정정 — **이 표의 첫 판은 내 정규식이 만든 것이었고 셋이 틀렸다**

처음에 나는 `route.split('/')[1]` 로 서비스를 갈랐다. 그 술어는 **경로 첫 조각이 곧
서비스명**이라고 가정하는데, 이 콘솔에서는 그렇지 않다:

| 무엇이 틀렸나 | 왜 |
|---|---|
| **IAM 5 → 9** | IAM 리프는 `/operators`·`/permissions`·`/tenants`·`/audit`… 처럼 **첫 조각이 제각각**이라 8조각으로 흩어졌다 |
| **Finance 3 → 4** | `/ledger` 가 Finance 그룹인데 첫 조각이 `ledger` 라 떨어져 나갔다 |
| **E-Commerce 5 → 1** | 흩어진 조각들이 다른 칸에 더해졌다 |

🔵 **고친 방법**: 그룹을 **내가 적지 않고** `console-nav-config.ts` 에서 유도한다. 그 파일이
`label` + `href` 로 그룹을 선언하고 있고, 워크스루 § 4 의 「화면 모집단」 표도 그것을
근거로 쓰였다. ⇒ **코퍼스가 자기 이름을 말하게 하고, 내 이름을 씌우지 않는다.**

🔴 방향이 좋은 쪽이라는 것이 이 정정을 덜 중요하게 만들지 않는다 — 같은 술어가 **다음에는
과대계수**할 수 있고, 그때는 「채울 수 있다」고 적힌 서비스가 실제로는 비어 있게 된다.

🔴🔴 **2026-09-09 두 번째 정정 — 이 표의 «쓸 수 있음» 은 «표지가 안 걸었다» 이지
«볼 만하다» 가 아니다.** `TASK-MONO-648` 에서 19장 중 16장을 **사람 눈으로 열어 보니**
✅✅ 는 **6장**뿐이었다:

| | 열어 본 결과 |
|---|---|
| **ERP** | ✅✅ `/erp/masters` · `/erp/orgview` + ✅ `/erp` — **3장** |
| **IAM** | ✅✅ `/operators` · `/permissions` — **2장** (`/org-hierarchy` 빈값 · `/tenants` 권한없음 · `/operator-groups` 빈값 · `/audit` 는 촬영 세션 자신의 질의) |
| **Finance** | ✅✅ `/ledger` — **1장.** 🔴 **3–6장을 못 채운다** |

자동 표지가 못 보는 것이 넷이다 — ①`EMPTY_RE` 가 `데이터|결과|항목|내역` 만 물어
「조직 노드가 없습니다」를 놓친다 ②**장별 권한거부 판정이 아예 없다**(`sanityCheck()` 는
앱당 1회, 단일 probe 경로) ③`DEGRADED_RE` 가 한국어만 물어 `erp unavailable` 을 놓친다
④빈 검색 폼은 **표지 문구 자체가 없다**.

⇒ 🔴 **AC-4 의 「채울 수 없는 서비스는 ⚪ 로 남겨라」가 Finance 에 실제로 걸린다.**
   그리고 **표지 통과를 큐레이션 근거로 쓰지 마라** — 그건 후보를 줄이는 도구다.
🔴 **ecommerce · wms · scm 은 못 쓰는 장이 더 많거나 비슷하다.** 그 사유는 두 갈래이고
**섞으면 안 된다**: ecommerce 9장은 **테넌트를 잘못 골라서**(`ecommerce` 테넌트로 재촬영하면
회수된다 — 648 AC-2) · wms/scm/dashboards 는 **제품 결함이거나 시드 부재**라 재촬영으로
회수되지 않는다.

## ③ 별도 리포로 나간다는 사실이 경로를 정한다

각 README 의 CI 배지가 `github.com/kanggle/<service>/actions/...` 를 가리킨다 — 즉
`projects/<name>/` 는 `TEMPLATE.md` 의 Discovery → Distribution 으로 **독립 리포가 된다.**
⇒ 이미지 경로는 **프로젝트 상대**여야 한다. ecommerce 가 이미 `docs/screenshots/01-home.png`
로 그렇게 하고 있으므로 **관례는 이미 있다. 새로 만들지 말고 따른다.**

---

# Scope

## 포함

- `projects/<name>/docs/screenshots/` 에 큐레이션분을 커밋하고 README 에 배선
- alt 텍스트 · 캡션 · 「로그인 필요」 표시(해당 장)
- 배선이 낡는 것을 무는 가드
- 서비스당 몇 장인지의 **소유자 결정을 받아 적는 절**

## 제외

- 🔴 **어느 장을 고를지는 이 티켓이 정하지 않는다** — 648 AC-4 가 소유자에게 묻는 축이다.
  이 티켓은 «고른 것이 도착하게» 한다.
- 🔴 **빈 표·저하 화면을 고치는 일** — 별도 티켓(제품 결함). 여기서 하면 범위가 터진다.
- 🔴 **론처 썸네일** — `TASK-MONO-648` AC-2 다. 대상 디렉터리가 다르고
  (`infra/demo/aws/site/thumbnails/`) 소비자도 다르다.
- 지원 자료 문서 자체 — `TASK-MONO-651`.

---

# Acceptance Criteria

## AC-0 — 착수 전 재측정

- [x] 🔴 **위 ① 표를 다시 세라.** 술어는 `![...]()` **와** `<img src=...>` 둘 다다.
      한쪽만 물면 ecommerce 를 놓친다(그게 이 티켓을 쓰면서 실제로 일어난 일이다).
- [x] 🔴🔴 **② 표(서비스 분류)도 다시 세라 — 그리고 그룹을 «네가 적지 마라».**
      `console-nav-config.ts` 에서 유도하라. 이 표의 첫 판이 `route.split('/')[1]` 로
      만들어졌고 **IAM 9를 5로, Finance 4를 3으로** 셌다(2026-09-09 정정). 콘솔의 nav
      리프는 첫 경로 조각이 서비스명과 **일치하지 않는다.**
- [x] 🔴 **`TASK-MONO-648` 이 아직 `in-progress` 면 큐레이션 목록이 아직 없다.** 그러면
      착수하지 마라 — 배선할 대상이 정해지지 않았다. **STOP 이 올바른 구현이다.**
- [x] `.gitignore` 를 확인하라. `portfolio-captures/`(106행)는 **계속 무시한다** — 커밋하는
      것은 거기서 **골라 복사한 것**뿐이다. 🔴 `TASK-MONO-640` 을 먼저 읽어라(`bin/` 한 줄이
      필수 생성기를 삼킨 적이 있다).

## AC-1 — 경로와 관례

- [x] 이미지는 `projects/<name>/docs/screenshots/` 에 둔다. 🔴 **새 관례를 만들지 마라** —
      ecommerce 가 이미 그 경로를 쓰고 있고, README 는 **프로젝트 상대 경로**로 참조한다
      (독립 리포로 추출되므로 저장소 루트 기준 경로는 추출 뒤 깨진다).
- [x] 파일명은 `NN-<slug>.<ext>` — ecommerce 의 `01-home.png` 와 같은 모양.
- [x] 🔴 **용량을 재서 적어라.** 648 실측 장당 ~122KB. 서비스당 3–6장 × 8 = 24–48장.
      🔵 `TASK-MONO-587`(Vercel 이미지 할당량)은 **여기 안 걸린다** — README 이미지는
      GitHub 이 서빙하고 `next/image` 변환을 안 탄다. **그 근거를 본문에 적어라**(안 적으면
      다음 사람이 587 을 이유로 이 티켓을 막는다).

## AC-2 — 배선

- [x] 서비스마다 스크린샷 절을 만들고 **alt 텍스트**를 넣는다. 🔴 alt 는 파일명 반복이
      아니라 **화면이 무엇인지**를 적는다.
- [x] 🔴 **콘솔에서 온 장에는 「운영자 콘솔 화면」임을 밝힌다.** wms README 에 콘솔의 WMS
      화면을 그냥 넣으면, 읽는 사람은 **wms 서비스가 자체 UI 를 가진 것으로** 읽는다.
      실제로는 `platform-console` 이 그리고 wms 는 API 다.
- [x] `TASK-MONO-634` § D7 · `TASK-MONO-637` 의 「로그인 없이/후」 어휘를 **그대로 쓴다** —
      새로 짓지 마라(같은 개념에 두 어휘가 생기면 그다음엔 한쪽만 고쳐진다).

## AC-3 — 낡음을 무는 가드

- [x] **참조된 이미지가 실재하는가** — README 의 `![]()`/`<img src>` 대상 파일이 있는가.
      🔴 술어를 **한쪽만** 쓰면 이 가드는 ecommerce 의 7장을 안 본다.
- [x] **고아 이미지** — `docs/screenshots/` 에 있는데 아무 README 도 참조하지 않는 파일.
      🔵 양방향이어야 한다. 한 방향만이면 지운 참조가 남긴 파일을 못 본다.
- [x] 🔴 **비어도 되는 모집단이다.** 스크린샷이 0장인 서비스가 지금 7개이므로, 이 가드는
      **「참조 0건 = 통과」** 여야 한다. 🔴🔴 **그러면 «둘 다 비어서 합의» 하는 공허한 초록이
      생긴다** — 비-공허성 하한을 달되, 하한은 **줄어드는 모집단 위에 두지 마라**
      (`feedback_non_vacuity_floor_under_draining_population`). 하한의 대상은 «스크린샷을
      가진 서비스 수» 가 아니라 **«참조와 파일이 둘 다 있는 쌍의 수»** 다.
- [x] **bite** — 참조를 남기고 파일을 지우면 빨강 · 파일을 남기고 참조를 지우면 빨강.
      🔴 **주입·실행·bite 를 각각 단언하라.** 변형이 문법을 깨서 난 빨강은 «문 것»이 아니다.

## AC-4 — 소유자 결정을 받아 적기

- [x] 서비스당 장수와 목록을 **소유자가 고른 그대로** 본문에 적는다. 🔴 내 추천을 승인
      목록으로 적지 마라 — `TASK-MONO-648` AC-2 에서 정확히 그것이 문제가 됐다(내가 1순위로
      민 `/dashboards/overview` 가 두 테넌트 모두 저하였고, 승인 목록이 그 추천 위에 서
      있었다).
- [x] 🔴 **채울 수 없는 서비스는 «채웠다»고 적지 말고 ⚪ 로 남겨라.** 오늘 기준 ecommerce
      콘솔 9장은 재촬영 대기이고 wms/scm 은 제품 결함 대기다. 빈 표를 넣고 「배선 완료」로
      적으면 **README 가 «안 만든 제품» 을 보여준다.**

---

# 🟢 구현 (2026-09-09 UTC · `ready` → `in-progress`)

## 배선한 것 — **12장** (승인은 13장이었다)

| README | 장 | 출처 |
|---|---|---|
| `erp-platform` | `01-erp-overview` · `02-erp-masters` · `03-erp-orgview` | 콘솔 |
| `iam-platform` | `01-iam-operators` · `02-iam-permissions` | 콘솔 |
| `finance-platform` | `01-finance-ledger` | 콘솔 |
| `fan-platform` | `01-fan-feed` · `02-fan-artists` · `03-fan-artist-profile` · `04-fan-membership` | 팬 |
| `ecommerce-…` | `09-store-products` · `10-store-wishlist` (기존 7장 옆에) | 스토어 |

🔴 **승인된 13장에서 하나를 뺐고 그 사유를 적는다.** `store /`(홈)를 배선하려다 기존
`01-home.png` 을 **열어 보니 같은 페이지**였다 — 스토어 홈, 히어로 + 인기 상품. 게다가
새 것에는 `TASK-MONO-654` 의 노란 배너가 박혀 있어 **더 나쁘다.** ⇒ 중복을 안 넣는다.
🔵 `/products`(카탈로그 그리드 20개)와 `/my/wishlist` 는 기존 7장에 **없던 것**이라 남겼다.

용량 **1.79MB → 1.59MB**(12장). 경로는 새로 만들지 않고 ecommerce 관례
`projects/<name>/docs/screenshots/NN-slug.ext` 를 따랐다.

## 🔴 가드가 첫 실행에서 **기존 드리프트**를 물었다

내가 만들지 않은 고아 둘이 있었다 — `05-admin-dashboard.png` · `06-admin-products.png`.
ecommerce README 자신이 *"독립 admin-dashboard 앱은 제거되었고"*(`ADR-MONO-031` Phase 6)
라고 적으면서 **참조만 지우고 파일을 남겼다.**

🔵 **지우지 않았다.** 열어 보니 차트·KPI·주문 표가 있는 멀쩡한 그림이고, 「도메인마다
admin 을 따로 두면 운영자가 앱을 갈아타야 한다」는 **합친 이유**가 거기 보인다. ⇒
`<details>` 안에 **「지금은 없는 앱이다」를 먼저 적고** 다시 참조했다. 삭제는 되돌릴 수
없고, 이 배치는 소유자가 원하면 언제든 삭제로 바꿀 수 있다.

## 가드 — `scripts/check-readme-screenshots.mjs`

양방향(참조→파일, 파일→참조) · `--self-test` 7칸 · 비-공허성 하한 `FLOOR = 8`.

🔴🔴 **하한의 대상은 «쌍의 수» 다** — «스크린샷을 가진 서비스 수» 로 두면 서비스가 하나
빠지는 정상적인 변경이 하한을 깨서 성공을 고장으로 만든다.

**bite 를 ①주입 ②문법 무사 ③물기로 나눠 증명했다:**

| bite | 결과 |
|---|---|
| 파일을 감춘다(참조 유지) | rc=1 · 「가리키는 파일이 없다」 1건 |
| 참조를 바꾼다(파일 유지) | rc=1 · 「파일이 없다」 1건 **+ 「고아」 1건**(양방향이 동시에 문다) |
| 🔴🔴 **술어를 마크다운만으로 좁힌다** | `--self-test` **3칸 빨강**(그중 (4)가 이 함정) · 실제 저장소에서는 **쌍 0개 → 하한 발화** |

세 번째가 핵심이다 — **2026-09-09 에 내가 실제로 저지른 좁힘**이고, 그때 `![` 로만 세서
「스크린샷 0장」이라 보고했으나 실제로는 7장이 있었다(ecommerce 가 `<img>` 를 쓴다).
그 좁힘이 다시 일어나면 칸 (4)가 죽는다.

**대조군**(좁힘 뒤에도 초록을 유지해야 하는 것): (5) 마크다운 전용도 센다 ·
(6) 배지(외부 URL)는 대상이 아니다 · (7) 스크린샷 0장인 서비스는 정상이다.
🔵 (7)이 실제 모집단에서 살아 있다 — `wms`·`scm`·`platform-console` 이 0장인 채로 초록이다.

## CI

`ci.yml` 에 필터 `readme-screenshots`(README · 이미지 · 판정자 **셋 다**) + 잡을 더했다.
🔴 **이미지 경로를 필터에 넣은 이유**: 「파일만 지운」 커밋은 README 를 안 건드리므로,
그 줄이 없으면 이 잡이 **안 돌고** 깨진 참조가 조용히 머지된다.
🔴🔴 `changes.outputs` 선언을 처음에 빠뜨렸고 **YAML 파싱이 잡았다** — 그것이 없으면
`if:` 가 늘 거짓이라 가드가 **한 번도 안 돈다**(`TASK-MONO-646` 이 데인 그 축).

## 🔴🔴 이 PR 이 main 을 빨갛게 만들었다 — 가드를 **하나 더한 것만으로**

`#3718` 이 머지된 뒤 `Guard-count figure (scripts/ reading git ls-files vs its two prose
homes)` 가 **FAILURE** 였다. 사유:

```
measured: 20 of 54 scripts/ entries read git ls-files
DRIFT: CLAUDE.md does not state "20 of the 54 …".   It currently says: 20 of the 53 …
DRIFT: platform/git-workflow-policy.md …            It currently says: 20 of the 53 …
```

🔵 **분자는 안 움직였다**(내 가드는 `git ls-files` 를 안 쓴다). 움직인 것은 **분모**다 —
`scripts/` 에 파일을 하나 더했기 때문이다. `TASK-MONO-646` 이 그 수에 게이트를 달아 뒀고,
그 게이트가 **정확히 작동했다.**

🔴 **내가 놓친 것은 «어느 가드를 돌릴지» 였다.** 나는 required 3개 + 내가 만든 가드만
돌렸다. 그 넷은 전부 초록이었고, 그래서 **초록을 보고 푸시했다.** 그러나
`check-ls-files-guard-count.sh` 는 required 가 아니고 내가 만든 것도 아니라 그 목록에
없었다 — 즉 **내 선택이 모집단을 정했고 그 모집단이 틀렸다.**

⇒ 일반화: **`scripts/` 에 파일을 더하거나 지우는 변경은 그 자체로 다른 가드의 입력이다.**
「내 변경과 관련 있어 보이는 가드」만 돌리면 이 부류를 영원히 못 본다.

- [x] `CLAUDE.md` 와 `platform/git-workflow-policy.md` 의 `53` → `54`. 🔵 판정자가 스스로
      *"Fix the sentence in each home above, not this script: the counts are derived"* 라고
      적어 두어 무엇을 고칠지 헤맬 일이 없었다. 게이트가 **처방까지** 들고 있었다.
- [x] 🔴 **다음에 `scripts/` 를 건드리면 전체 가드를 쓸어라.** 3개가 아니라. (2026-09-10: 🔴 **그 규칙이 저장소 어디에도 없었다** — grep 으로 확인했다. 세션 메모에만 살아 있었고, 그건 게이트가 아니다. ⇒ `CLAUDE.md` § Task Rules 의 「Stage before you run a repo guard locally」 옆에 **분모가 움직였다는 사실과 처방까지** 적었다.)

## ⚪ 채우지 못한 것 — 「채웠다」로 적지 않는다 (AC-4)

| README | 상태 |
|---|---|
| `platform-console` | ⚪ **0장.** 콘솔 자신의 화면(`/console`·`/subscriptions`·`/partnerships`·`/accounts`)은 **열어 보지 않았다.** 안 본 장을 넣는 것이 오늘 이미 한 실수다 |
| `wms-platform` | ⚪ **0장.** `/wms`·`/wms/master` 빈값 · `/wms/operations` 저하 |
| `scm-platform` | ⚪ **0장.** `/scm/inventory`·`/scm/replenishment` 빈값 |

🔴 셋 다 **시드 부재·제품 결함이 선행**이고 재촬영으로 회수되지 않는다.
🔵 `ecommerce` 콘솔 9장은 테넌트 오선택이라 **재촬영으로 회수된다**(`TASK-MONO-648` AC-2).

---

---

# ✅ AC 대조 (2026-09-10 UTC) — 체크를 켜기 전에 **근거로 쟀다**

🔴 `in-progress` 판에서 **체크박스가 하나도 안 켜져 있었다.** INDEX 행은 완료를 적고 있었고
본문에는 § 구현이 있었다 — 즉 **일은 했는데 AC 절이 그것을 안 말하고 있었다.** `done/` 은
frozen 이므로 그 상태로 닫으면 「무엇이 닫혔나」를 다시 못 읽는다. 그래서 켜기 전에 각 칸을
실측으로 대조했다.

| AC | 근거 |
|---|---|
| AC-0 ① 두 술어로 다시 세기 | 가드가 `![]()` **와** `<img src>` 를 둘 다 물고, ecommerce 의 `<img>` 7장을 실제로 센다(그 좁힘이 self-test 칸 (4)) |
| AC-0 ② 그룹을 `console-nav-config.ts` 에서 유도 | § 정정에 IAM 5→9 · Finance 3→4 · E-Commerce 5→1 로 기록 |
| AC-0 ③ 648 게이트 | 🔵 648 은 **여전히 `in-progress`** 다. 그러나 그 게이트가 막으려던 것은 «배선할 대상이 안 정해진 채 착수하는 것» 이고, **소유자 승인 13장이 있었다**(§ 구현). ⇒ 게이트의 **목적**은 충족됐고, 형식은 아니다 — 그 사실을 여기 적어 둔다 |
| AC-0 ④ `.gitignore` | `portfolio-captures/` 가 **여전히 106행에서 무시**되고, `git ls-files` 로 센 커밋된 캡처 디렉터리 **0건** |
| AC-1 경로·파일명·용량 | `projects/<name>/docs/screenshots/NN-slug.ext` · 12장 1.59MB |
| AC-2 alt·콘솔 출처·어휘 | alt 가 «화면이 무엇인지» 를 적는다(예: *"ERP 마스터 — 부서·직원·직급·비용센터·거래처"*) · 각 README 에 *"🔵 아래 화면은 **운영자 콘솔**(`platform-console`)이 그린다 — 이 서비스는 그 뒤의 API 다"* |
| AC-3 가드 | 양방향 · self-test 7칸 · 하한 = **쌍의 수** · bite 3종 |
| AC-4 소유자 목록·⚪ | 13장 중 12장 배선 + 뺀 사유 · ⚪ 3서비스 |

## 🔴 하나가 **정말로 안 적혀 있었다** — AC-1 의 `TASK-MONO-587` 근거

AC-1 이 *"🔵 `TASK-MONO-587`(Vercel 이미지 할당량)은 **여기 안 걸린다** … **그 근거를 본문에
적어라**(안 적으면 다음 사람이 587 을 이유로 이 티켓을 막는다)"* 라고 요구했는데,
§ Related Specs 가 *"(**여기엔 안 걸린다**, 근거는 AC-1)"* 로 **AC-1 을 되가리키고** 있었다.
그건 순환이다 — 근거가 아니라 요구가 거기 있다. 여기 적는다:

> **왜 587 이 이 티켓을 막지 않는가.** 587 이 재는 축은 **Vercel 의 Image Transformations /
> Image Cache Writes** 다. 이 티켓이 커밋하는 12장은 **GitHub 이 서빙**한다 —
> 서비스 README 는 GitHub 웹/앱에서 읽히고, 그 이미지는 `<img src="docs/screenshots/…">`
> 로 **저장소 상대 경로**를 가리킨다. Vercel 앱을 통과하지 않으므로 `next/image` 변환도,
> 그 캐시 쓰기도 **발생하지 않는다.** 🔴 예외가 될 수 있는 경로는 **하나**다 —
> 같은 이미지를 Vercel 에 배포되는 앱(`docs/portfolio.md` 를 렌더하는 무언가, 또는
> 론처)이 `next/image` 로 걸게 되는 날. 그때는 587 의 축이 되살아나고, **그 변경이
> 이 근거를 무효화한다.**

# Related Specs / Contracts

- `TEMPLATE.md` § Discovery → Distribution — `projects/<name>/` 가 독립 리포로 나가는 경로
- `TASK-MONO-648` — 촬영(선행). AC-4 가 큐레이션 목록을 만든다
- `TASK-MONO-639` — 뷰포트·스케일 고정, 첫 화면 무게 실측
- `TASK-MONO-640` — `.gitignore` 한 줄이 필수 파일을 삼킨 사례
- `TASK-MONO-587` — Vercel 이미지 할당량(**여기엔 안 걸린다**, 근거는 AC-1)
- `TASK-MONO-651` — 지원 자료 문서(후속, 이 티켓의 산출물을 인용한다)

---

# Edge Cases

- **서비스가 화면을 안 가진다** — wms·scm·erp·finance·iam 은 자체 UI 가 없고
  `platform-console` 이 그린다. ⇒ AC-2 의 명시 요구가 여기서 나온다.
- **fan-platform 은 자체 웹이 있다**(11장, 빈값·저하 0). 콘솔에서 가져올 필요가 없다.
- **README 가 이미 길다** — wms 636행 · ecommerce 586행. 스크린샷 절의 **위치**가 문제가
  된다(맨 아래면 아무도 안 본다). ecommerce 는 44행에 두고 있다.
- **독립 리포 추출 시점에 이미지가 따라가는가** — `projects/<name>/` 안에 있으면 따라간다.
  루트 `docs/` 에 두면 **안 따라간다.** 그것이 경로를 프로젝트 안으로 정한 이유다.

---

# Failure Scenarios

1. 🔴🔴 **한쪽 술어만 써서 「전부 0장」이라 보고하고, ecommerce 의 기존 7장을 덮어쓴다.**
   이 티켓을 쓰는 도중 그 오보가 실제로 한 번 나왔다(마크다운 문법만 셌다).
2. 🔴 **빈 표·저하 화면을 넣고 배선을 완료로 적는다.** 그림이 거짓말을 하는 문은 648 이
   앞문(로그인 화면)만 막았고, 이것이 뒷문이다.
3. 🔴 **저장소 루트 기준 경로로 참조한다.** 모노레포에서는 초록이고 **추출된 리포에서만**
   깨진다 — 즉 CI 가 절대 못 잡는다.
4. **가드가 공허하게 초록이다** — 참조 0건·파일 0건이면 두 방향이 합의한다. AC-3 의
   비-공허성 하한이 이것을 막는다.
5. 🔴 **콘솔 화면을 서비스 README 에 무표시로 넣어 «자체 UI 가 있다»는 인상을 만든다.**
   취업 자료에서 이것은 아키텍처를 잘못 전달한다.

---

# 분석 / 구현 권장

분석=Opus 5 / 구현 권장=**Sonnet** — 경로·마크다운·이미지 복사가 본체다.
🔴 단, **AC-3 의 가드만 Opus** — 양방향 술어 + 비-공허성 하한 + bite 설계는 이 저장소가
반복해서 틀린 축이다.
