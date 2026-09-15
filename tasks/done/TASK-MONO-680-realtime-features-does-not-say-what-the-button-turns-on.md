# Task ID

TASK-MONO-680

# Title

「실시간 기능」은 버튼이 무엇을 켜는지 말하지 않는다 — 동작은 「데모 서버 켜기」, 대상은 「로그인 후 기능」으로 가른다 (+ 론처 탭 아이콘)

# Status

done

# Owner

monorepo

# Task Tags

- demo
- copy
- platform-console
- ecommerce-microservices-platform
- fan-platform

---

# Goal

소유자 피드백(2026-09-15): *"실시간 기능 시작이라고 하니까 의미가 헷갈려 — 로그인 전/후로 쓰거나 안 쓰니까."*

「실시간 기능」이라는 한 낱말이 **두 가지를 동시에** 가리키고 있었다.

1. 버튼이 **하는 일** — EC2 위의 데모 백엔드를 켠다(약 10분).
2. 그 결과 **열리는 것** — 로그인 뒤에만 쓸 수 있는 기능(글쓰기·주문·운영).

방문자에게 «실시간» 은 둘 중 어느 쪽도 떠올리게 하지 않는다. 카드는 이미 `로그인 없이` / `로그인 후` 로
갈라 쓰고 있고 배너는 이미 「데모 서버가 꺼져 있어 … 서버를 켠 뒤」라고 쓰고 있어서, **버튼과 배지만 다른 말**을 하고 있었다.

⇒ 동작 = **「데모 서버 켜기」**, 대상 = **「로그인 후 기능」**. 소유자가 「로그인 기능」을 제안했고, 아래 두 이유로
이 조합을 추천해 승인받았다.

- 「로그인 기능 시작」은 **로그인을 시작하는 버튼**으로 읽힌다. 누르면 로그인 창이 아니라 10분짜리 기동이 시작된다.
- 배너 문장 「장바구니·로그인 같은 ○○ 기능」에 넣으면 **로그인 자신이 로그인 기능의 한 예**가 되어 문장이 성립하지 않는다.

같은 요청에서 **론처(hubwang.com) 탭 아이콘**도 바꾼다. 지금은 `<link rel="icon">` 이 없어 브라우저 기본 아이콘이 뜬다.

---

# Scope

## 바꾼다

| 파일 | 무엇 |
|---|---|
| `infra/demo/aws/site/index.html` | 버튼 3 · 버튼 켜짐 라벨 · 배지 이름 3 · `needs-boot` 안내 3 · 소개문 · 계정 카드 제목 · 고급 안내 · 제어 API 부재 문구 2 · 대기 상태 문구 · 주석 1 · **파비콘** |
| `projects/ecommerce-microservices-platform/apps/web-store/src/widgets/demo-notice/DemoBackendNoticeClient.tsx` | 배너 문장 + 용어 주석 |
| `projects/fan-platform/web/fan-platform-web/src/widgets/demo-notice/DemoBackendNotice.tsx` | 배너 문장 + 용어 주석 |
| `projects/platform-console/apps/console-web/src/features/demo-tour/lib/sample-actions.ts` | `DEMO_DISABLED_REASON` |
| `infra/demo/public-data/fixtures/console-sample.mjs` → `snapshots/console-sample.json` | 표 설명 3 (생성기로 재생성) |
| 위 문구를 고정하는 테스트 5 | web-store·fan 배너 unit · console unit · console e2e-smoke · fan e2e-smoke 테스트 이름 |
| `docs/portfolio.md` · `projects/ecommerce-microservices-platform/README.md` | 같은 용어 |

## 안 바꾼다

- `tasks/done/**` · `docs/adr/**` — 그때의 기록이다(frozen / 결정 서술).
- `infra/demo/aws/terraform/lambda/handler.py:465` docstring 의 「실시간 기능 상태」 — 주석뿐인데 고치면 람다 zip 해시가
  바뀌어 다음 `apply` 가 **아무 동작 변화 없이** 함수를 재배포한다. 방문자에게 안 보인다.
- 버튼의 「기동 중…」 — 배지(`BLABEL.booting`)가 같은 말을 쓰고 있어 둘을 함께 바꿀 이유가 없다.

## 프로젝트 영향

`platform-console` · `ecommerce-microservices-platform` · `fan-platform` — 방문자 문구와 그 문구를 고정한 테스트뿐. 동작 변화 0.

---

# Acceptance Criteria

- [x] **AC-1** 방문자에게 보이는 표면(론처·스토어 배너·팬 배너·콘솔 둘러보기 툴팁·콘솔 샘플 설명)에 「실시간 기능」이 **0건**이다.
- [x] **AC-2** 버튼은 **하는 일**(「데모 서버 켜기」 / 켜짐 「데모 서버 켜짐」)을, 배너는 **잠긴 것**(로그인 + 「로그인 후 기능」)을 말한다.
      배너는 로그인이 잠겼다는 사실을 **지우지 않는다**(`TASK-MONO-642` 가 지킨 성질).
- [x] **AC-3** 문구를 고정한 테스트가 새 문구를 고정한다 — 문구를 지우는 쪽으로 테스트를 느슨하게 만들지 않는다.
- [x] **AC-4** `console-sample.json` 은 손으로 고치지 않고 생성기로 만든다 — `build-bundled-snapshots.mjs --check` rc=0.
- [x] **AC-5** 론처에 탭 아이콘이 있고, **배포되는 방식으로** 들어간다(`build.sh` 는 `index.html`·`thumbnails/` 만 복사한다 ⇒ 별도 파일은 배포 안 됨).
- [ ] **AC-6** 머지 뒤 `hubwang.com` 이 **새 바이트를 서빙한다** — `build-info.json` 의 `commit` 과 서빙된 HTML 의 새 문구로 판정한다
      (머지 초록 ≠ 배포됨). close chore 에서 잰다.

---

# Related Specs

- `TASK-MONO-637` — 카드의 `로그인 없이` / `로그인 후` 구분과 `needs-boot` 안내를 넣은 티켓.
- `TASK-MONO-642` — 배너가 «잠긴 사실» 을 지우지 않게 한 티켓. 그 성질은 AC-2 로 이어받는다.

# Related Contracts

없음 — HTTP/이벤트 계약 변경 없음. `console-sample` 봉투의 **모양**은 그대로이고 `description` 문자열만 바뀐다.

---

# Edge Cases

- `verify-demo-wrapper.sh` (z35) 는 카드마다 `<dt>로그인 없이</dt>`·`<dt>로그인 후</dt>`·`class="needs-boot"` 를 센다 — 셋 다 그대로 남는다.
  (z39) 는 버튼 **잠금** 과 배지 문구의 **관계**만 본다 — 라벨 문자열에 매이지 않는다.
- 콘솔 툴팁은 `DEMO_DISABLED_REASON` 한 상수를 툴팁·안내문이 공유하므로 한 곳만 바꾸면 된다.
- 파비콘 SVG 는 `data:` URI 안에서 `#` 을 `%23` 으로 쓴다 — 안 쓰면 URI 가 거기서 끊겨 아이콘이 안 뜬다.

# Failure Scenarios

- 🔴 **배너에서 「로그인」까지 지우는 경우** — 방문자가 로그인이 왜 안 되는지 모른다(642). ⇒ 「로그인할 수 없고, … 로그인 후 기능도 잠겨 있습니다」로 둘 다 남긴다.
- 🔴 **테스트만 고치고 생성물을 안 만드는 경우** — 픽스처와 스냅숏이 갈라진 채 머지된다. ⇒ AC-4 의 `--check`.
- 🔴 **머지를 배포로 읽는 경우** — 론처는 Vercel 훅으로 나가고, 배포가 실패해도 옛 판을 계속 서빙한다. ⇒ AC-6.

---

# Implementation Notes (2026-09-15 UTC)

- 교체는 **기대 개수를 적은 치환표**로 했다 — 개수가 하나라도 다르면 아무 파일도 안 쓰고 죽는다(버튼·배지·안내 각 3, 나머지 1). 12 파일 전부 기대와 일치.
- 배너 문장:
  - store: 「데모 서버가 꺼져 있어 로그인할 수 없고, 장바구니·주문 같은 로그인 후 기능도 잠겨 있습니다.」
  - fan: 「데모 서버가 꺼져 있어 로그인할 수 없고, 글쓰기·멤버십 같은 로그인 후 기능도 잠겨 있습니다.」
- 파비콘: 파란(`#2563eb`, 「전체 스택 시작」 버튼과 같은 색) 둥근 사각형 + 흰 전원 기호. 전원 기호 = 「데모 서버 켜기」.
- 게이트(로컬):
  - `build-bundled-snapshots.mjs` 재생성 rc=0 · `--check` rc=0 (fan·store 드리프트 없음, console-sample 만 바뀜)
  - `GUARD-Z34` 구간 `node --check` rc=0
  - 워크트리 전체 grep 「실시간 기능」: 방문자 표면 0건. 남은 것 = `tasks/INDEX.md` done 행 2 · `handler.py` docstring 1(위 「안 바꾼다」) · 용어 변경을 기록한 주석 1.
  - 헤드리스 Chromium 으로 수정본 `index.html` 을 열어(file://, 제어 API 없음 ⇒ 호출·클릭 0) 버튼 라벨·`<dt>`·`needs-boot` 문구를 읽고, 파비콘을 16/32/64/96px · 밝은/어두운 바탕에 그려 눈으로 확인.
- ⚪ **로컬 미측정 → CI**: 세 프런트 앱 vitest(새 worktree 에 `node_modules` 없음) · `verify-demo-wrapper.sh --live`(z35·z39 포함).

분석=Opus 5 / 구현=Opus 5.

## CORRECTION — close chore (2026-09-15 UTC): AC-6 는 머지 뒤 실측으로 닫혔다

위 `- [ ] AC-6` 은 frozen 파일이라 켤 수 없어 미체크로 남는다. 이 절이 그 칸의 판정이다.

**4차원 검증**

- (a) PR [#3812](https://github.com/kanggle/monorepo-lab/pull/3812) `state=MERGED` 2026-09-15T07:46:10Z · merge commit `05360d8c5`
- (b) `origin/main` tip = `05360d8c5`
- (c) 머지 헤드(`dd19141bc`) 롤업 SUCCESS 23 · SKIPPED 40 · FAILURE 0 · 필수 4개 전부 SUCCESS
- (d) AC-1~5 = impl PR 에서 닫힘(위 본문) · AC-6 = 아래

**AC-6 — `hubwang.com` 이 새 바이트를 서빙한다: PASS**

| 무엇 | 값 |
|---|---|
| 서빙된 `https://hubwang.com/` md5 | `5009902f72ca2f655e165084935fc097` |
| `git show origin/main:infra/demo/aws/site/index.html` md5 | `5009902f72ca2f655e165084935fc097` (일치) |
| `build-info.json` (캐시버스트) | `commit=05360d8c5…` · `index_md5=5009902f…` |
| 서빙 HTML 의 「데모 서버 켜기」 / 「실시간 기능」 / `rel="icon"` | 8 / 0 / 1 |
| `vercel-deploy.yml` 런 | `05360d8c5` success (07:46:13Z) |

- 🔴 **첫 조회의 `build-info.json` 은 옛 커밋 `025a4ff29`(#3783)을 줬다.** 그 순간 서빙 HTML md5 는 이미 새 값이었다 — 배포가 막 끝나던 창의 캐시였고, 캐시버스트 재조회에서 두 값이 일치했다. ⇒ 한 값만 봤다면 「미배포」로 오판했을 것이다. 판정은 **두 계기의 일치**다.
- 🔵 곁측정: `fan.hubwang.com/build-info.json` commit = `05360d8c5`, SSR HTML 에 「로그인 후 기능」 2 · 「실시간 기능」 0.
- ⚪ store 배너: 방문 시점 클라이언트 렌더라 SSR HTML 에 배너가 없다(0) — HTML 로는 판정 불가, **미측정**. AC-6 의 대상(hubwang.com)이 아니라 닫는 것을 막지 않는다.

**CI 1회차 빨강 (기록)**: web-store `DemoBackendNotice.test.tsx` 의 껍데기 bite 테스트 — 첫 호출에서 `vi.doMock` 이 적용되지 않은 모양(`up` 빈 host). 이 PR 이 목으로 대체되는 컴포넌트만 바꿨고 직전 7회 success, 재실행 success. 🔴 rerun-초록은 원인 판정이 아니다 — 가설=모듈 목 순서 경합. **착수 신호: 같은 테스트가 무관한 diff 에서 재발하면 결함 티켓.**
