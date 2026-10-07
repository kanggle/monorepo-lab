# Task ID

TASK-MONO-769

# Title

`ADR-MONO-080` PROPOSED 보강 — **D9 테넌트 생성의 입구**: «조직 만들기»(`/onboarding`)와 «테넌트 등록»(`/tenants`)을 하나로 할지, 첫 관리자를 누가 정하나

# Status

done

# Owner

monorepo

# Task Tags

- adr
- identity
- console

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (문서만 — ADR 결정 항목 추가)

---

# Dependency Markers

- 선행: `TASK-MONO-746`(ADR-080 PROPOSED 기안, #4205 머지 — `review/`). 그 티켓 파일은 동결이라 보강은 이 티켓으로 한다.
- 후속: ADR-080 ACCEPT(소유자 정확형 수락) — 수락 문장에 `· 테넌트생성 <T1|T2|T3>` 가 있으면 D9 가 정해진다.

# Goal

소유자 대화(2026-10-07 UTC)에서 나온 결정 항목을 ACCEPT 전에 ADR-080 에 올려, 소유자가 갈래 A/B/C 와 함께 볼 수 있게 한다:

- «조직 만들기» 와 «테넌트 등록» 은 같은 행위(테넌트 생성)인데 결과가 다르다 — 앞은 «테넌트 + 관리자», 뒤는 «테넌트만».
- 소유자 제안: 둘을 «테넌트 생성» 하나로 합치고, **들어온 계정**에 따라 첫 관리자가 정해진다(대표 → 본인, 플랫폼 관리자 → 지정한 사람). 운영자가 아닌 계정도 콘솔 셸 안에서(사이드바 · 운영자 토큰 없는 상태여야 할 이유가 없다).

# Scope

## In Scope

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` — D9 절(실측 · 선택지 T1/T2/T3 · 추천 T1 · 갈래 의존) + 머리말 · Decision 머리 · 갈래 절 · 새로 생기는 위험(대조군 술어) · Outstanding · 수락 형식(선택 `· 테넌트생성`) · History 한 행.
- `docs/adr/INDEX.md` ADR-080 행에 D9 요약.

## Out of Scope

- ACCEPT · 구현 · 단계 티켓 — ADR 이 정하는 대로.
- D1~D8 · 갈래 · 라이더 R1~R4 의 내용 변경.
- 조직 계층(`/org-hierarchy`)에 테넌트를 넣는 기능 부재 — 같은 대화에서 나왔지만 `ADR-MONO-047` 의 질문이라 별도(권한 규칙 결정 대기).

# Acceptance Criteria

- [x] **AC-1** — D9 의 실측 셋이 file:line 과 함께 적혀 있다: ① `/tenants` B2B 테넌트에 `SUPER_ADMIN` 도 운영자를 못 앉힌다 ② D6 셋째 줄의 자기모순 ③ 두 번째 회사 입구 없음.
- [x] **AC-2** — 선택지 T1/T2/T3 과 각 대가, 추천은 **구현자 선호**로 표기, 갈래 의존(남을 첫 관리자로 지정 = 갈래 A 에서만)이 적혀 있다.
- [x] **AC-3** — 수락 형식에 선택 `· 테넌트생성 <T1|T2|T3>` 가 있고, **없으면 열린 채** · 추천을 기본값으로 읽지 않는다고 적혀 있다.
- [x] **AC-4** — D1~D8 · 갈래 · 라이더 본문 무변경(추가만). `git diff` 로 확인.

# Related Specs

- `docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` · `docs/adr/ADR-MONO-044-self-service-tenant-onboarding.md` (D3 · D4 · D7)
- `projects/iam-platform/specs/contracts/http/onboarding-api.md`

# Related Contracts

- 없음(문서만). 구현 시 `onboarding-api.md` · `admin-api.md` § tenants 가 바뀐다.

# Edge Cases

- 갈래 B 로 수락되면 T1 은 «본인 = 첫 관리자» 경로만 완성된다(D6 초대가 없으므로) — D9 본문에 적었다.
- B2C 테넌트 생성은 T1 에서도 `SUPER_ADMIN` 전용으로 둔다.

# Failure Scenarios

1. 추천(T1)을 소유자 결정으로 읽는다 — 수락 형식이 «없으면 열린 채» 로 막는다.
2. D9 를 넣으면서 D6 문장을 고친다 — 다른 세션의 PROPOSED 본문을 수락 전에 바꾸는 일이다. D6 은 손대지 않고 D9 에서 모순을 **지적**만 한다.

---

# 작업 기록 (2026-10-07 UTC)

> 분석 · 작성 = Opus 5.5.

## 착수 전 확인

- ADR-080 은 다른 세션이 이미 PROPOSED 로 머지했다(`5a6eb6a81`, #4205). 같은 기안을 다시 하지 않고 **빠진 항목만** 보탰다. 열린 PR · 진행 중 worktree 없음(`gh pr list` · `git worktree list`).
- 소유자 대화의 네 항목 대조: ① 새 조직 직원 = **D6 에 있음** · ② 테넌트 생성 통합 = 없음 · ③ 비운영자 셸 진입 = **반대로 적혀 있음**(대조군 «못 들어온다») · ④ 두 번째 회사 = 없음 ⇒ ②③④ 를 D9 로.

## 실측 (코드 읽기 — 라이브 아님)

| 사실 | 근거 |
|---|---|
| `/onboarding` 가드: 운영자 쿠키 → `/`, IAM 세션 없음 → `/login` | `projects/platform-console/apps/console-web/src/app/(onboarding)/layout.tsx:24-25` |
| 온보딩 = 테넌트(`B2B_ENTERPRISE`) + 본인 관리자 둘 + 배정 | `projects/iam-platform/specs/contracts/http/onboarding-api.md:48-55` |
| `/tenants` 생성 입력 = `tenantId` · `displayName` · `tenantType` · `reason` (관리자 없음) | `…/console-web/src/app/api/tenants/_proxy.ts:22-30` |
| `/tenants` = `SUPER_ADMIN` 전용 | `…/(console)/tenants/page.tsx:42,63` |
| 계정 확인 면제는 대상 테넌트 `'*'` 일 때만 — 만드는 사람이 `SUPER_ADMIN` 이어도 대상이 일반 테넌트면 확인 | `projects/iam-platform/apps/admin-service/src/main/java/com/example/admin/application/CreateOperatorUseCase.java:100-107` |
| 운영자 토큰 없는 셸의 선례(샘플 방문자) | `…/(console)/layout.tsx:148-160` |

## 검증

- AC-1~AC-3: ADR-080 § D9 · § 수락 형식 · § Outstanding · § History 에 들어갔다(같은 PR 의 diff).
- AC-4: `git diff origin/main -- docs/adr/ADR-MONO-080-workforce-on-the-consumer-pool.md` = **+36 / −4**. `−` 네 줄(머리말 · Decision 머리 · 갈래 절 마지막 줄 · 대조군 bullet)은 각각 `+` 줄 **앞부분에 글자 그대로** 남아 있다(문장 끝에 덧붙임) — 스크립트로 4/4 확인. D1~D8 · 갈래 표 · 라이더 표 본문 변경 0.
- 가드: 스테이지 뒤 필수 3종(`check-index-queue-drift` · `check-task-id-collision` · `check-walkthrough-ledger-drift`).

---

## 닫기 기록 (2026-10-07 UTC) — 4차원

| 차원 | 결과 |
|---|---|
| (a) `gh pr view 4207` | `state=MERGED` · mergeCommit `e8ff3335c` |
| (b) origin/main 조상 | 참 |
| (c) 머지 시점 실패 체크 | 66 중 **FAILURE 0** |
| (d) `# Acceptance Criteria` | AC-1 ~ AC-4 `[x]` — 동사(«적혀 있다» · «무변경») = PROPOSED 보강 문서로 닫힘 |

- 후속: ADR-080 이 같은 날 **ACCEPTED — A · 직원연결 E1 · 테넌트생성 T1** (#4209, `f7e274ed1`). D9 = T1 의 구현은 `TASK-MONO-773`.
