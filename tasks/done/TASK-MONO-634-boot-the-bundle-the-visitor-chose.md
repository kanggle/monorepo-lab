# Task ID

TASK-MONO-634

# Title

부팅이 **방문자가 고른 묶음**을 올린다 — 론처 카드 · 묶음 제어 API · 선택의 영속화 (`ADR-MONO-071`)

# Status

done

# Owner

monorepo

# Task Tags

- infra
- demo
- launcher
- control-plane

---

# Goal

`TASK-MONO-477` 의 도메인 선택은 **부팅 뒤에만** 존재했다. 방문자가 «팬 플랫폼만» 을 고르려
해도 인스턴스가 `stopped` 면 `/domain/start` 가 409 를 내고, 남은 길은 「데모 시작」뿐이며 그
부팅은 systemd 유닛의 `DEMO_PROFILE=full` 을 읽어 **8개 프로젝트 96 컨테이너**를 올린다.

⇒ *"프런트엔드 버튼만 나누고 내부적으로 전체 스택을 시작하는 구현은 금지한다"* 가 금지한
상태가 **이미 존재했다.** 이 티켓이 그것을 고친다.

🔴🔴 이 결함은 **어떤 에러도 내지 않는다.** 96 컨테이너가 전부 정상적으로 뜬다 — 로그는
완벽하게 초록이고, 다른 점은 «방문자가 고른 것과 다르다» 뿐이다. 그래서 이 티켓의 검증은
「요청이 성공했나」가 아니라 **「무엇이 저장되고 무엇이 실행됐나」** 를 본다.

---

# Scope

## 포함

- `infra/demo/projects.sh` — `BUNDLES` / `BUNDLE_ADDONS` / `resolve_bundles()`
- `infra/demo/demo-selection.sh` (신규) — 부팅 시점에 SSM 에서 선택을 읽는다
- `infra/demo/demo-boot.sh` — `selection` 센티널 해석
- `infra/demo/demo-stack.service` — `DEMO_PROFILE=full` → `selection`
- `infra/demo/aws/terraform/lambda/handler.py` — `/bundles`, `/bundle/start`, `/bundle/stop`
- `infra/demo/aws/terraform/main.tf` — SSM 파라미터 2개 + IAM + 라우트 3개 + Lambda env
- `infra/demo/aws/site/index.html` — 카드 UI
- `infra/demo/verify-demo-wrapper.sh` — (z32)(z33) 신규, (z14) 축 교정
- `infra/demo/aws/tests/test_handler.py` — 신규 23칸

## 제외

- 서비스별 EC2 신설 · 요금제 변경 · 무관한 인프라 재구축 (소유자가 «단일 EC2 유지» 로 못 박음)
- 공개 데이터 저장본 — `TASK-MONO-635` (같은 요청의 **다른 축**)

---

# Acceptance Criteria

## AC-0 — 착수 전 실측 (완료)

- [x] `/domain/start` 가 `state != running` 에서 409 를 내는지 소스로 확인 — **참**
      (`handler.py domain_start()`).
- [x] systemd 유닛의 기본 프로파일 확인 — **`full`** 이었다.
- [x] 그 둘의 합이 «최초 부팅은 언제나 전부» 를 뜻하는지 — **참**.

## AC-1 — 최초 부팅부터 묶음이 선택된다

- [x] `stopped` 상태에서 `POST /bundle/start {"bundles":["fan"]}` 가 **선택을 저장하고** 인스턴스를 켠다.
- [x] 저장된 선택에 `full`·`console`·`store` 가 **들어 있지 않다**(고른 것만).
- [x] `stopped` 에서는 SSM 명령을 **안 보낸다**(보낼 수 없다 — 부팅이 저장된 선택을 읽는다).
- [x] `demo-boot.sh selection` 이 그 선택을 읽어 `resolve_bundles` 로 도메인을 푼다.
- [x] 유닛의 `DEMO_PROFILE` 이 `selection` 이다.

## AC-2 — 동시·중복·기동 중 요청

- [x] 동시 요청은 **합집합**이다(덮어쓰기가 아니다).
- [x] 같은 요청 반복은 **중복 실행하지 않는다**.
- [x] `pending` 중에 온 요청도 선택에 들어간다(유실 없음).
- [x] `stopping` 중 요청은 409 지만 **선택은 남는다**.
- [x] 선택 저장이 수렴하지 못하면 503 으로 **정직하게 실패**한다(성공으로 보고하지 않는다).

## AC-3 — 공유 서비스 · 종료

- [x] 묶음 종료 명령에 **`iam` 이 절대 안 들어간다**.
- [x] 종료는 잠금으로 직렬화되고, 그 잠금은 **만료**가 있다.
- [x] EC2 전체 종료(`/stop`)와 묶음 종료(`/bundle/stop`)가 **다른 동작**이다.

## AC-4 — 상태 구분

- [x] 7단계: `waiting`/`requested`/`booting`/`ready`/`partial`/`stopping`/`unknown`.
- [x] «EC2 running» ≠ «묶음 준비 완료».
- [x] `ready` 는 **iam 포함** 필수 도메인이 전부 up 일 때만.
- [x] `health_stale` 이면 어떤 상태도 안 믿고 `unknown`.

## AC-5 — 주입 방어

- [x] 묶음 이름은 **집합 소속**으로만 통과한다(패턴 검사가 아니다).
- [x] `fan; rm -rf /` · `$(id)` · `fan console` · `../../etc/passwd` · `FAN` 전부 400 이고
      `send_command` 가 **한 번도 안 불린다**.
- [x] 모르는 이름이 하나라도 섞이면 **전부 거절**한다(부분 수용 금지).

## AC-6 — 론처 UI

- [x] 데스크톱 3열 / 모바일 1열 카드.
- [x] 카드마다 미리보기 이미지 · 제목 · 설명 · 대표 기능 · **기동되는 서비스 목록**(서버가 말한다) ·
      공개 둘러보기 가능 여부 · 실시간 기능 상태 · 둘러보기 링크 · 실시간 기능 시작 버튼.
- [x] **이미지·제목·둘러보기는 언제나 정적 Vercel 주소**로 가고 기동을 겸하지 않는다.
- [x] 「전체 시작」이 기본 행동에서 빠지되 `<details>` 안에 남는다.
- [x] 기동 중에도 둘러보기가 계속된다(기동 함수가 링크를 안 건드린다).
- [ ] 🔴 **카드 썸네일이 아직 자리표시자다** — § 남은 것 ①. ⚪ **이 AC 는 열린 채로 둔다.**
      닫으려면 «공개 데모 데이터로 채운 실제 화면» 을 캡처해야 하고, 그것은 `TASK-MONO-635`
      의 공개 화면이 **배포되어 실제로 서 있을 때**만 가능하다(가짜 화면을 만들지 않는다는
      것이 요구사항이다). 지금 화면은 이모지 자리표시자가 뜨고, `build.sh` 가 매 빌드에
      그 사실을 로그로 말한다 — 0장인 상태가 «정상» 으로 굳지 않게.

## AC-7 — 기존 도구가 새 UI 와 함께 산다

- [x] `demo-up.sh` 의 `data-surface` / `data-demo-boot-probe` 추출이 그대로 동작한다.
- [x] (z14) 가 링크 판정을 **실행 대조**로 계속 집행한다.
- [x] **검사 하한을 낮추거나 검사를 제거해 통과시키지 않았다** — (z14) 의 display 단언은
      *완화가 아니라 축 교정*이다(값 `"block"` → «`none` 이 아닌가»). 대조군(vercel 행 0개 →
      `none`)은 그대로 살아 있으므로 칸이 줄지 않았다.
- [x] (z32) 묶음 표 두 집 대조 · (z33) 부팅 프로파일·폴백·파라미터 이름 3곳 대조 신규.

## AC-8 — 예산 가드 회귀 없음 + **발견한 결함 수정**

- [x] 🔴🔴 **반복 `/start` 가 최대 가동 시간을 리셋하던 결함을 고쳤다.** `start()` 가 상태와
      무관하게 `STARTED_PARAM` 을 찍어 `run_sec` 이 영원히 작았고, `max-runtime` 가드가
      **한 번도 물지 못했다.** `started` 는 이제 `stopped → start` 전이에서만 찍는다.
- [x] `beat` 는 계속 연장된다(대조군 — 둘을 같이 얼리면 유휴 판정이 깨진다).
- [x] 월 예산 가드가 `/bundle/start` 도 막는다.
- [x] 공개 둘러보기는 heartbeat 를 안 보낸다(세 앱의 라우트가 익명이면 204 no-op).

---

# Related Specs / Contracts

- [`ADR-MONO-071`](../../docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md) — 이 티켓의 결정문
- [`ADR-MONO-067`](../../docs/adr/ADR-MONO-067-demo-surfaces-served-from-vercel.md) — 방문자 화면 = Vercel
- `TASK-MONO-477` — 도메인별 선택 기동(이 티켓이 그 한계를 고친다)
- `TASK-MONO-627` — 부팅 프로브 원소(이 티켓이 **안 건드린다**)

---

# Edge Cases

| 상황 | 처리 |
|---|---|
| SSM 파라미터 부재/손상 | `demo-core` 로 폴백 + **사유를 말한다**. 🔴 `full` 이 아니다 |
| 폴백 이름이 묶음이 아님(`demo-core`) | `resolve_bundles` 에 안 넣는다 — 넣으면 폴백 경로가 자기가 만든 값에 걸려 죽는다 |
| aws CLI 부재 / IMDS 차단 | 폴백 + 사유. 부팅을 죽이지 않는다 |
| 동시 시작 요청 | 합집합 + 읽기 확인 재시도(G-Set 수렴) |
| 동시 종료 요청 | 만료 있는 잠금. 🔴 상호배제 **보장 아님** — 창의 크기를 코드에 적어 뒀다 |
| 잠금 소유자 사망 | TTL 120초 후 자동 해제 |
| 헬스 발행 중단 | 묶음 상태 `unknown`, 시작 버튼은 **활성 유지**(모르는 것은 «못 한다» 가 아니다) |
| `config.js` 부재 | 기동 컨트롤만 끄고 **둘러보기는 살린다** |

---

# Failure Scenarios

1. **유닛만 고치고 스크립트를 안 고친다** → `selection` 이 도메인 이름으로 해석돼 부팅이
   «알 수 없는 도메인» 으로 죽는다. → (z33) (2) 가 문다.
2. **폴백을 `full` 로 둔다** → 「선택이 저장되지 않는다」가 영영 안 보인다. → (z33) (3).
3. **두 묶음 표가 갈라진다** → Lambda 에만 있는 이름은 부팅이 죽고, projects.sh 에만 있는
   이름은 아무도 못 고른다. 둘 다 **에러 없이**. → (z32).
4. **디스패치 순서 회귀** → `/bundle/start` 가 `/start` 로 새어 선택 없이 인스턴스만 켜진다.
   그 실패는 **200 을 낸다.** → `test_bundle_start_path_is_not_swallowed_by_the_start_route`.
5. **`iam` 을 묶음 종료에 넣는다** → 남은 묶음의 로그인이 조용히 무너진다. → `test_bundle_stop_never_names_iam`.

---

# Verification — 실제로 돌린 것

```
python infra/demo/aws/tests/test_handler.py          rc=0   56 tests (기존 33 + 신규 23)
python <bite harness>                                 9/9 물림 + 복원 후 초록
bash -n infra/demo/demo-boot.sh                       rc=0
bash -n infra/demo/verify-demo-wrapper.sh             rc=0
terraform fmt -check                                  rc=0
terraform validate                                    rc=0  "Success! The configuration is valid."
bash infra/demo/verify-demo-wrapper.sh                (§ 결과는 PR 본문)
source projects.sh; resolve_bundles …                 fan→iam fan · store→iam ecommerce ·
                                                      console→iam console · console+wms→iam wms console ·
                                                      store+fulfillment→iam wms scm ecommerce · 오타→rc=1
```

🔴🔴 **bite 를 돌렸다** — 새 단언 9종을 각각 되돌려 실제로 무는지 확인했고, 매 칸마다 원본에서
다시 시작해 **누적 결함 위에서 돌지 않게** 했으며, 마지막에 복원 후 초록을 확인했다.
그중 한 칸(`max-runtime` 리셋 결함 복원)이 정확히 **이 티켓이 고친 기존 결함**이다.

## 🔴 실환경 미검증 (모의 통과와 구별해서 적는다)

- **AMI 재굽기 없이는 런타임에 도달하지 않는다.** `demo-boot.sh`·`demo-selection.sh`·
  `demo-stack.service` 는 AMI 에 구워진다(`infra/demo/aws/README.md` 도달 경로 표).
  **머지 = 반영이 아니다.**
- `terraform apply` 미실행 — SSM 파라미터 2개·IAM·라우트 3개가 아직 안 생겼다.
- 실제 EC2 부팅으로 «팬만 골랐을 때 fan+iam 만 뜨는가» 를 **관측하지 못했다.**
- 묶음 하나의 실제 웜업 시간 **미측정** — 그래서 론처에 숫자를 안 적었다.

---

# 남은 것

① **카드 썸네일**(AC-6 마지막 칸) — 공개 데모 데이터로 채운 **실제 화면 캡처**여야 한다.
   `TASK-MONO-635` 의 공개 화면이 먼저 서야 찍을 수 있으므로 순서상 그 뒤다. 🔴 가짜 제품
   화면을 생성하지 않는다 — 캡처 전까지는 `onerror` 자리표시자가 뜬다.

---

# 머지 검증 (4-dimension)

impl PR [#3681](https://github.com/kanggle/monorepo-lab/pull/3681) · 스쿼시 `9f0fcd2d6`

| 축 | 결과 |
|---|---|
| (a) `state=MERGED` | ✅ `mergedAt=2026-09-07T19:17:09Z` · `mergeCommit=9f0fcd2d6df70b3dee847163c82d4159cbe9d125` |
| (b) `origin/main` 팁 == 스쿼시 커밋 | ✅ `9f0fcd2d6` |
| (c) 머지 시점 실패 체크 | ✅ **0건** (SUCCESS 29 · SKIPPED 28 · FAILURE 0) · required 4/4 SUCCESS |
| (d) AC 가 닫혔나 | 🔴 **아래 § AC 참조 — 전부는 아니다.** 그래서 이 파일은 `review/` 에 남는다 |

🔴 (a)(b)(c) 는 **PR** 을 재고 (d) 만 **티켓** 을 잰다. (d) 가 안 닫혔으므로 `done/` 로
옮기지 않는다 — `done/` 는 frozen 이고, 거기 남긴 잔여는 다시 읽히지 않는다.

## CI 가 닫아 준 것 — 로컬에서 «미검증» 이라고 적었던 항목의 정정

🔵 **`web-store` 유닛 테스트가 CI 에서 통과했다** (`Frontend unit tests` = SUCCESS, 922 tests).
이 호스트에서는 vitest 4.1.0 이 Node 24.14 에서 `ERR_PACKAGE_IMPORT_NOT_DEFINED` 로 **기동
자체가 안 됐고**(대조군으로 우리 변경 이전에도 동일함을 확인), 그래서 커밋 메시지와 PR 본문에
「store 유닛 테스트는 안 돌았다」고 적었다. **그 문장은 CI 결과로 갱신된다** — 안 돈 것은
*이 호스트에서* 였고, 실제로는 돌았고 통과했다.
🔴 「로컬에서 못 쟀다」와 「검증되지 않았다」는 다른 사실이다. 앞의 것만 참이었다.

🔵 `Frontend E2E smoke` (세 앱 Playwright, 백엔드 차단) 도 SUCCESS · `Demo wrapper smoke` SUCCESS.

## AC — 무엇이 닫혔고 무엇이 안 닫혔나

- AC-0 · AC-1 · AC-2 · AC-3 · AC-4 · AC-5 · AC-7 · AC-8 — **닫혔다**(§ Verification 의 명령과
  bite 9/9, 그리고 CI 의 `Demo wrapper smoke` SUCCESS).
- AC-6 — **한 칸 열림**(썸네일). 나머지 칸은 닫혔다.

🔴 그리고 **AC 와 무관하게 열려 있는 것**이 둘 있다. 체크박스로 표현되지 않으므로 여기 적는다:
   ① `terraform apply` 미실행 ② **AMI 재굽기 미실행**. 부팅 스크립트는 AMI 에 구워지므로
   **머지가 런타임 반영이 아니다.** 이 둘이 끝나야 「최초 부팅부터 선택」이 실제로 성립한다.


---

# 배포 실측 (2026-09-08 UTC) — 「머지 = 반영이 아니다」의 구체적 값

서빙 중인 론처는 이 티켓의 판이 맞다: `build-info.json` 의 `commit` =
`9f0fcd2d6df70b3dee847163c82d4159cbe9d125`. **화면은 도달했다.**

제어 API(`https://r1tljg51qa.execute-api.ap-northeast-2.amazonaws.com`) 를 직접 찌른 결과:

| 엔드포인트 | 결과 |
|---|---|
| `GET /status` | **200** `{"state":"stopped","used_minutes":379,"budget_minutes":600}` |
| `GET /domains` | **200** |
| `GET /bundles` | 🔴 **404** `{"message":"Not Found"}` |
| `POST /bundle/start` | 🔴 **404** |

⇒ **AC-1 은 코드로는 닫혔지만 런타임에는 아직 없다.** 이 티켓이 만든 라우트 3개가 API
Gateway 에 없기 때문이고, 원인은 `terraform apply` 미실행이다(§ 남은 것에 이미 적혀 있다).

🔴 예산이 이미 **379/600분** 쓰였다는 것도 여기 적어 둔다 — 다음 세션이 기동 창을 잡을 때
남은 것은 **221분**이고, 그 창 안에서 AMI 재굽기·묶음 기동 검증·썸네일 캡처를 다 해야 한다면
순서를 미리 정해야 한다. (재측정 필수 — 이 값은 단일 표본이고 월 1일에 리셋된다.)

## 🔴 이 실측이 **새 결함 하나를 드러냈다** → `TASK-MONO-636`

`/bundles` 가 404 인 동안 론처의 「실시간 기능 시작」 버튼이 **잠긴 채 이유를 말하지 않는다**
(`pollBundles()` 의 `if (!ok) return;` 이 404 를 삼킨다). 방문자에게는 «고장난 버튼» 으로
읽히는데 실제로는 «아직 배포 안 된 기능» 이다.

🔴 **이것은 이 티켓의 미완이 아니라 별개의 코드 결함이다** — `terraform apply` 가 끝나면
증상은 사라지지만 같은 모양이 제어 API 장애 때 그대로 재발한다. 그래서 배포 항목에 묻지 않고
`tasks/ready/TASK-MONO-636` 으로 **따로 세웠다.**

## CORRECTION — `AC-6`(카드 썸네일)이 닫혔다. 조건이 실제로 성립했다 (2026-09-08 UTC)

`AC-6` 은 ⚪ 로 열어 두면서 닫는 조건을 이렇게 적었다:

> 닫으려면 «공개 데모 데이터로 채운 실제 화면» 을 캡처해야 하고, 그것은 `TASK-MONO-635`
> 의 공개 화면이 **배포되어 실제로 서 있을 때**만 가능하다.

**그 조건이 성립했고, `TASK-MONO-639` 가 그 일을 했다.**

- 캡처 8장 — `console.hubwang.com/demo`·`/demo/ecommerce`·`/demo/wms` · `store…/`·`/products`
  · `fan…/`·`/artists`·`/membership`. 전부 **그 주소가 그 순간 실제로 준 화면**이고
  200 이 아니면 캡처하지 않는다.
- 🔵 **그리고 이 AC 가 적어 둔 전제 하나는 틀렸다** — 「EC2 기동 창이 필요하다」. `ADR-MONO-070`
  이 세 표면을 백엔드 없이 공개로 세웠으므로 **Vercel 에서 · 비용 0 으로** 찍혔다.
- 라이브 확인(2026-09-08 UTC): `hubwang.com` 의 카드 캡처 8장이 전부 `naturalWidth=1280`
  으로 그려지고, 바이트가 커밋본과 **동일**하다.

🔴 체크박스는 `- [ ]` 로 남는다 — `review/` 는 frozen 이고, close chore 에는 그 칸을 켤 수
있는 경로가 없다. **판정은 이 절이다.**
