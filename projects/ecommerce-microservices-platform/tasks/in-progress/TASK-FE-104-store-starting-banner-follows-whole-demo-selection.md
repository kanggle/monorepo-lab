# Task ID

TASK-FE-104

# Status

in-progress

# Title

web-store 의 «데모 서버가 켜지는 중입니다…» 배너가 **데모 선택 전체**의 준비 상태(`/status` `selection_ready`)를 따른다 — 스토어 묶음이 ready 인데도 무관한 묶음(scm) 재기동 중에 뜬다. 스토어 배너가 **스토어 자신의 묶음**을 따를지 정한다

# Owner

ecommerce-microservices-platform

# Task Tags

- web-store
- demo
- frontend

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus — 결정은 «무엇이 스토어의 준비인가» 이고, 그 신호를 읽는 해석기(`infra/demo/backend-resolver`)는 **세 앱이 공유**한다. 어느 층을 고칠지가 범위를 바꾼다(AC-0). 앱 안에서 끝나면 Sonnet 으로 충분하다.
>
> ⏳ **소유자 우선순위 결정 대기** — 착수 순서와 AC-0 의 결정은 소유자 몫이다. 프런트(Vercel)만 바뀌면 재굽기 없이 배포된다. 🔴 해석기나 Lambda `/status` 를 바꾸는 갈래는 루트 티켓으로 다시 기안한다(아래 § 범위가 바뀌는 경우).

---

# Dependency Markers

- 출처: 20차 AMI 창(2026-10-04 UTC · `i-0c4859442f56d70e0` · `ami-0d78d476824493d77` · `f0927bcd0`). `TASK-MONO-758` AC-2 대조군으로 scm 묶음을 15:28:28 에 내리고 15:36–15:38 에 다시 올리는 동안, 스토어 묶음은 ready 였는데 web-store 에 «데모 서버가 켜지는 중입니다…» 배너가 떴다(소유자 관찰).
- 배경 결정: `TASK-MONO-668` · `docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md` § D5.1 — `/status` 의 `selection_ready` = «선택 묶음의 `/bundles` state 가 **전부** ready». 해석기는 `running + ip + selection_ready === false` 를 `starting` 으로 낸다(`infra/demo/backend-resolver/README.md:31`).
- 선행/후속: 없음.

# Goal

데모 방문자가 web-store 를 쓰는 동안 «켜지는 중» 배너는 **스토어가 실제로 준비되지 않았을 때만** 뜬다. 다른 도메인 묶음의 기동·재기동은 스토어 배너를 띄우지 않는다 — 또는, 소유자가 «선택 전체 기준» 을 의도로 확인하면 그 사실과 이유를 계약/주석에 적고 닫는다.

# Scope

## In Scope

- **AC-0 결정 입력 측정**(코드 0줄): 세 앱(web-store · console-web · fan-platform-web)의 «켜지는 중» 배너가 무엇을 읽는지, 무관한 묶음 재기동 때 각각 뜨는지.
- 소유자 결정에 따른 web-store 배너의 신호 변경(예: `/bundles` 에서 스토어 묶음(`ecommerce`/`console-ecommerce` 중 스토어가 쓰는 것)의 state 를 읽는다) + 단위 시험 + bite.
- 같은 결정이 console/fan 에도 적용되는지의 판단 기록(적용은 각 프로젝트 티켓 — 이 티켓은 web-store 만 고친다).

## Out of Scope

- 공유 해석기 `infra/demo/backend-resolver` · Lambda `handler.py` 의 `/status`·`/bundles` 계약 변경 — 필요하면 루트 티켓(`tasks/ready/`)으로 다시 기안한다.
- «꺼져 있어»(`unavailable`) 배너 — 이 티켓은 `starting` 판정만 본다.

## 범위가 바뀌는 경우

해석기가 묶음별 상태를 노출하지 않아 앱이 `/bundles` 를 직접 읽어야 한다면, 그것은 `scripts/check-demo-resolver-copies.sh` 의 취지(앱이 자기 데모 상태 구현을 갖지 않는다)와 부딪친다. 그때는 이 티켓을 멈추고 해석기에 묶음별 판정을 더하는 **루트 티켓**을 기안해 이 티켓의 선행으로 둔다(세 앱 공통).

# Acceptance Criteria

- [x] **AC-0 (측정 + 소유자 결정)** — ① web-store `widgets/demo-notice/DemoBackendNoticeClient.tsx`·console-web `widgets/demo-notice/DemoBackendNotice.tsx`·fan-platform-web `widgets/demo-notice/DemoBackendNotice.tsx` 가 «켜지는 중» 을 내는 신호의 출처를 file:line 으로 적는다(전부 공유 해석기의 `starting` 인지). ② 해석기가 묶음별 상태를 줄 수 있는지(`infra/demo/backend-resolver/src/index.ts` 가 `/bundles` 를 읽는가) 적는다. ③ 소유자에게 묻는다: (가) 스토어 배너는 **스토어 묶음**만 따른다 (나) 지금처럼 **선택 전체**를 따른다(의도 — 문구/주석만 보강). 답을 원문 그대로 이 파일에 적는다.
- [ ] **AC-1** — (가) 이면: 스토어 묶음이 ready 이고 다른 선택 묶음이 booting 인 상태 → web-store 에 «켜지는 중» 배너 **없음**. 스토어 묶음이 booting → 배너 있음. 둘 다 렌더된 DOM 단언. (나) 이면: 배너 문구나 주석이 «선택한 묶음 전체» 기준임을 밝히고 그 이유를 적는다.
- [ ] **AC-2 (대조군)** — `unavailable`(꺼짐) · `running` + 전체 ready · `not-demo` 의 기존 화면이 그대로다(`DemoBackendNotice.test.tsx` 기존 칸 초록).
- [ ] **AC-3** — bite: 새 판정을 «선택 전체» 로 되돌리면 AC-1 의 칸만 빨강.
- [ ] **AC-4 (라이브, ⚪)** — 다음 데모 창: 스토어가 ready 인 동안 scm 묶음 하나를 내렸다 올려도 web-store 배너가 안 뜬다(또는 (나) 의 새 문구가 뜬다).

# Related Specs

- `docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md` § D5.1 (`selection_ready`)
- `infra/demo/backend-resolver/README.md` (상태 표 `starting`/`running`)
- `projects/ecommerce-microservices-platform/specs/` web-store 데모 안내(`TASK-FE-103` 이 손댄 배너 문구 규칙)

# Related Contracts

- 데모 컨트롤 플레인 `/status`(`selection_ready`) · `/bundles`(묶음별 state) — `infra/demo/aws` Lambda `handler.py` (변경하지 않는다 — 읽기만)

# Edge Cases

- 스토어가 쓰는 백엔드가 **둘 이상의 묶음**에 걸쳐 있다(예: 로그인은 iam, 상품·주문은 ecommerce) — «스토어 묶음» 이 하나가 아니면 그 집합을 AC-0 에서 정한다.
- `/bundles` 가 실패하거나 모르는 묶음 이름을 준다 — `starting` 으로 추정하지 않는다(해석기 README 의 «null·없음은 starting 이 아니다» 원칙).
- 같은 렌더에서 배너와 장바구니/로그인 동작이 다른 신호를 읽어 엇갈린다 — 배너만 바꾸면 «배너 없음 + 로그인 실패» 가 될 수 있다(그 때 무엇이 실제로 실패하는지 AC-0 ① 에서 같이 적는다).

# Failure Scenarios

1. **앱이 `/status`·`/bundles` 를 직접 부르는 자기 구현을 만든다** — 해석기 사본 가드의 취지를 깨고 15 s 캐시도 못 탄다(§ 범위가 바뀌는 경우 → 루트 티켓).
2. **배너를 없애기만 하고 실제 준비는 안 본다** — 스토어 묶음이 booting 인데 배너가 안 떠 방문자가 실패를 «고장» 으로 읽는다(AC-1 의 둘째 칸이 막는다).
3. **세 앱이 서로 다른 기준을 갖게 된다** — web-store 만 바꾸고 그 사실을 console/fan 쪽에 남기지 않는다(In Scope 의 판단 기록).

---

# AC-0 기록 (2026-10-04 UTC · `2e5567050` · 코드 0줄)

> 분석=Opus 5.5. 소유자 결정(2026-10-04): 새 티켓 3건 중 세 번째로 착수.

## ① «켜지는 중» 의 출처 — 세 앱 모두 같은 하나

| 앱 | 배너 | 신호 |
|---|---|---|
| web-store | `src/widgets/demo-notice/DemoBackendNoticeClient.tsx:54,59` — 브라우저가 `/api/demo/backend-state` 를 물어 `state === 'starting'` | 그 라우트 `src/app/api/demo/backend-state/route.ts:46` 가 `resolveDemoBackendState()` |
| console-web | `src/widgets/demo-notice/DemoBackendNotice.tsx:56` (서버 컴포넌트) | `resolveDemoBackendState()` |
| fan-platform-web | `projects/fan-platform/web/fan-platform-web/src/widgets/demo-notice/DemoBackendNotice.tsx:37` (티켓의 경로 `apps/fan-platform-web` 는 틀렸다 — 실제는 `web/`) | `resolveDemoBackendState()` |

공유 해석기 `infra/demo/backend-resolver/src/index.ts:228` — `starting = status.selection_ready === false`(`/status` 한 번, 15 s 캐시). 세 앱 모두 **같은 판정**이다.

## ② 해석기가 묶음별 상태를 줄 수 있는가 — 없다, 그리고 «없음» 이 설계다

- 해석기는 `/bundles` 를 **읽지 않는다**(`/status` 만 — `index.ts:178`).
- `index.ts:56-57`: «어느 묶음이 내 것인가 를 이 모듈이 알게 되는 순간 그것이 넷째 설정이고 ADR 재개봉이다.» · `:100-101`: «넷째 축이 생기면 … `ADR-MONO-068` 을 다시 열어라.»
- 🔴🔴 **이 동작은 이미 소유자가 수용한 트레이드오프다.** Lambda `infra/demo/aws/terraform/lambda/handler.py` `_selection_ready` docstring(`:792` 이하): «**«전부» 는 선택된 묶음 전부다(소유자 결정 ⓑ, 2026-09-15).** 그 앱이 쓰는 묶음만 보려면 해석기가 «내 묶음 이름» 을 알아야 하고 그것은 `ADR-MONO-068` 재개봉이다. 대가로, 자기 묶음이 ready 여도 **다른 선택 묶음이 booting 이면 False** 다 — 보수 쪽 오차로 수용했다.»
- 20차 창에서 내린 묶음은 `console-scm`(콘솔 애드온 — `TASK-MONO-758` 표 `:105` `POST /bundle/stop {"bundles":["console-scm"]}`)이었다. 스토어 묶음 `store` = `ecommerce`(+`iam`, `handler.py:140-161`)와 **무관**하다 ⇒ 관찰 자체는 맞다. (스토어 애드온 `store-fulfillment` = wms·scm 이었다면 무관하지 않았을 것이다.)
- Edge Case «스토어 묶음이 둘 이상» — `store` 는 `ecommerce`+`iam` 하나이고, 애드온 `store-fulfillment` 는 선택적이다(이 결정 아래에선 정할 필요가 없어졌다).

## ③ 소유자 결정 (원문)

> 질문: «스토어 «켜지는 중» 배너의 기준을 어떻게 할까요?»
> **답: «(나) 선택 전체 유지 + 문구 정확히 (Recommended)»** — 09-15 결정 ⓑ 유지. web-store 배너 문구를 «선택한 데모 화면 일부가 아직 켜지는 중» 쪽으로 바로잡고 주석에 그 결정을 적는다. 앱 안에서 끝 · Vercel · 재굽기 무관. 콘솔·팬 적용은 판단만 기록.

(대안이던 (가) = 09-15 결정 번복 + `ADR-MONO-068` 재개봉 + 해석기에 «내 묶음» 설정을 더하는 루트 티켓 선행.)

## Edge Case «배너와 다른 동작이 엇갈린다»

로그인 포워더·BFF 는 `starting` 에서도 주소를 받는다(`index.ts:87-90` — «말하기 용이지 막기 용이 아니다»). 그러니 스토어 묶음이 ready 이면 배너가 떠 있어도 로그인·주문은 **된다**. 옛 문구 «데모 서버가 켜지는 중입니다 … 장바구니·로그인 … 동작하지 않을 수 있습니다» 는 그 경우 사실과 어긋났다. 새 문구는 «이 스토어는 이미 준비됐을 수 있지만, 그 전에는 로그인·주문이 실패할 수 있습니다» 다.
