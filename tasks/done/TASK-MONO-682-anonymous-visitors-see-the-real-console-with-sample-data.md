# Task ID

TASK-MONO-682

# Title

익명 방문자에게 `/demo` 대신 **로그인 후의 실제 콘솔 화면**을 샘플 데이터로 보여 준다 — 먼저 `ADR-MONO-074` 결정을 받는다

# Status

done

# Owner

monorepo

# Task Tags

- adr
- platform-console
- demo

---

# Goal

소유자 요청(2026-09-15, 원문):

> *"콘솔에서 로그인 전에 데모페이지를 보여주는것 대신 로그인후 보여지는 실제 페이지를 보여주고 데이터만 샘플로 넣어서
> 보여주는 형식으로 진행 데이터는 (샘플)로 표현"*

이 요청은 `ADR-MONO-070` D6 이 정한 «콘솔 둘러보기 = 별도 화면» 과 `(console)` 인증 가드의 의미를 바꾼다. 구현 갈래가
셋(A 게이트웨이 샘플 모드 / B 별도 샘플 트리 / C `/demo` 재도장)이고 보안 경계가 걸려 있어
`platform/architecture-decision-rule.md` § The ACCEPTED Gate 에 따라 **결정을 먼저 받는다.**

이 티켓의 산출물은 **결정과 실행 티켓**이다. 코드는 바꾸지 않는다.

---

# Scope

## In Scope

- `docs/adr/ADR-MONO-074-anonymous-visitors-see-the-real-console-with-sample-data.md` 발행(PROPOSED) + `docs/adr/INDEX.md` 행
- 소유자 정확형 accept 수신 후: Status 전환 · § History 기록 · `ADR-MONO-070` 에 «D6 일부 대체» 포인터(A/B 일 때)
- **같은 ACCEPT PR 안에서** § Roadmap 의 실행 티켓 기안(A: 기반 1 + 도메인 6 + 은퇴 1 / C: 단일 1)

## Out of Scope

- 샘플 모드·픽스처·셸 변경 등 **모든 구현** (실행 티켓 몫)
- `TASK-MONO-680` 이 진행 중인 `/demo` 버튼 문구 — 막지도 합치지도 않는다
- 팬·스토어의 공개 봉투(`ADR-MONO-070` D1~D5) — 이 결정과 무관하게 그대로

---

# Acceptance Criteria

- [x] **AC-1** `ADR-MONO-074` 가 `PROPOSED` 로 머지되고 `docs/adr/INDEX.md` 에 행이 있다 (`scripts/check-adr-index-drift.sh` 초록).
      ✅ PR [#3815](https://github.com/kanggle/monorepo-lab/pull/3815) squash `c7ef72767` · 로컬 `check-adr-index-drift.sh` rc=0(78) · CI `ADR index drift` SUCCESS.
- [x] **AC-2** 소유자에게서 **정확형** 줄을 받는다:
      `ADR-MONO-074 ACCEPTED — <A|B|C> · R1<ⓐ|ⓑ> · R2<ⓐ|ⓑ> · R3<ⓐ|ⓑ>` (C 는 라이더 불필요).
      🔴 «진행»·글자만·ADR 이름 없는 줄·플레이스홀더가 남은 줄·«추천대로» 는 **통과가 아니다** — 다시 묻는다.
      🔴 A/B 인데 라이더 글자가 빠졌으면 그 라이더는 **미결로 기록**하고 해당 부분은 기안하지 않는다.
      ✅ 받은 원문 `ADR-MONO-074 ACCEPTED — A · R1<ⓐ> · R2<ⓐ> · R3<ⓐ>` (2026-09-15). 이름·ACCEPTED·갈래·라이더 셋을 각각 확인, 미결 0.
      🔴 소유자가 직전에 추천을 **요청**했고 받은 글자가 추천과 같다 — 통과 판정 근거는 ADR § History 에 적었다.
- [x] **AC-3** ACCEPT PR: Status 전환 + § History(받은 원문 인용) + 결정 본문 **byte-unchanged** + (A/B) `ADR-MONO-070` 헤더에 부분 대체 포인터.
      ✅ 이 PR. 본문 불변은 diff 로 확인(§ History 와 `Status` 줄만 바뀐다).
- [x] **AC-4** 같은 ACCEPT PR 에서 § Roadmap 의 실행 티켓을 `ready/` 에 기안한다. A 의 기반 티켓은 최소한 다음을 AC 로 갖는다:
      ① 엔드포인트 **재인벤토리**(ADR 의 94/99 는 코드 읽기 수) ② 코어 6 + 코어 밖 구멍 5 전부의 샘플 분기
      ③ `(console)` 트리에서 코어·샘플 라우터 밖 `fetch` 금지 가드 ④ 샘플 라우터 금지 임포트 가드
      ⑤ 가드 테스트 3개(`demo-tour-console-guard-regression`·`root-redirect.spec`·`demo-tour.spec`)의 기대값을 **ADR 인용과 함께** 교체
      ⑥ «익명으로 연 뒤 로그인한 같은 브라우저에 샘플 문자열 0» e2e.
      ✅ `TASK-PC-FE-282`(①=AC-0 · ②=AC-2 · ③=AC-5 · ④=AC-4 · ⑤=AC-11 · ⑥=AC-14, 🔴 smoke 는 세션을 못 만들어 nightly 또는 ⚪) ·
      `TASK-PC-FE-283`~`288`(도메인) · `TASK-MONO-686`(은퇴, ⏳ 게이트). ①~⑥ 에 더해 기반 티켓이 **코어 넷의 403 메시지 덮어쓰기**
      (실측)를 AC-3 으로 갖는다.
- [x] **AC-5** 이 티켓의 PR 은 `docs/adr/**` 와 `tasks/**` 만 바꾼다(코드 diff 0). ✅ 두 PR 모두 `docs/adr/**` · `tasks/**` · `projects/platform-console/tasks/**` 뿐.

---

# Related Specs

- `docs/adr/ADR-MONO-070-public-browsing-served-from-a-versioned-vercel-snapshot.md` (D3 · D6)
- `docs/adr/ADR-MONO-071-boot-the-bundle-the-visitor-chose.md` (D8)
- `platform/architecture-decision-rule.md` § The ACCEPTED Gate
- `projects/platform-console/PROJECT.md` (saas · multi-tenant · integration-heavy · audit-heavy)
- `projects/platform-console/specs/services/console-web/architecture.md`
- `projects/platform-console/docs/conventions/frontend-ui.md`

# Related Contracts

- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.1(신뢰 경계) · § 2.5(섹션 degrade) · § 2.6(운영자 토큰)

---

# Edge Cases

- 소유자가 «진행» 이나 글자 하나만 보낸다 → 게이트 미통과. 정확형 줄 **전문**을 다시 준다(줄여서 묻지 않는다).
- A 를 고르고 R2 만 빠뜨린다 → R2 미결 기록, 기반 티켓의 «(샘플) 표기» AC 는 «R2 결정 대기» 로 기안.
- 결정 전에 `TASK-MONO-680` 이 머지된다 → 영향 없음(그 변경은 `/demo` 문구이고 A/B 에서는 은퇴 티켓이 함께 걷어 낸다).
- 결정이 C 다 → `ADR-MONO-070` 은 건드리지 않는다.

# Failure Scenarios

- 재인벤토리에서 코어 밖 `fetch` 가 **다섯보다 많이** 나온다 → 기반 티켓의 AC-② 범위가 늘 뿐 갈래 판단은 그대로다. 🔴 단 그 수를 ADR 본문에 되돌려 적지 않는다(ACCEPT 후 본문 불변) — 실행 티켓에 적는다.
- 코어 분기로 막을 수 없는 호출 경로(예: 클라이언트가 백엔드를 직접 부르는 곳)가 발견된다 → A 의 전제가 깨진 것이므로 **구현하지 않고** 소유자에게 되돌린다.
- 동시 세션이 같은 ID 를 가져간다 → 먼저 머지된 쪽이 번호를 갖는다(`tasks/INDEX.md` § Task ID Allocation 4). 이 티켓은 이미 한 번 그 규칙으로 680 → 682 로 옮겼다.

---

# Test Requirements

- 이 티켓: `scripts/check-adr-index-drift.sh` · 필수 가드 3종(INDEX queue drift · Task ID collision · Walkthrough ledger drift)
- 실행 티켓의 테스트 요구는 AC-4 에 적은 대로 그 티켓들이 갖는다.

---

# Definition of Done

- [ ] ADR PROPOSED 머지
- [ ] 정확형 accept 수신 또는 «결정 대기» 로 명시 보류
- [ ] ACCEPT PR 에서 실행 티켓 기안 완료

분석=Opus 5 / 구현 권장=Opus 5 (보안 경계 이동 — 기반 티켓), 도메인 픽스처 티켓은 Sonnet 가능.
