# Task ID

TASK-PC-FE-322

# Status

review

# Title

전역 콘솔 가이드(/guide) 일곱 탭을 쉬운 말 · 짧은 문장으로 다시 쓰고, 화면의 출처 표시를 뺀다

# Owner

platform-console

# Task Tags

- console-web
- frontend
- guide

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 화면 문구 · 정적 데이터. 도메인 로직 없음.
>
> 🔵 **소유자 결정(2026-10-09 UTC):** «시스템 아키텍처 · 도메인별 서비스 구성 · 서버 구성 · 전체 메뉴 소개 · 권한 및 테스트 계정 · 대표 업무 흐름 · 서버 기동·종료 방식 — 원래 있던 가이드는 좀 더 쉽게 설명하고 보기 쉽게 수정. 간단 명료하게. 출처는 생략.»

---

# Dependency Markers

- 선행: `TASK-PC-FE-298`(전역 가이드 · 출처 인용 AC-6) · `TASK-PC-FE-321`(«도메인 한눈에» 탭 — 이번 범위 밖).
- 같은 시기: 다른 세션의 열린 worktree 둘(`chore/demo-ami-pin-24`, `chore/close-window24`)은 콘솔 가이드를 건드리지 않음(2026-10-09 확인).

# Goal

처음 보는 사람이 탭 하나를 1분 안에 읽는다 — 구현 용어(파일 경로 · 줄 번호 · 티켓 번호 · 테이블 이름)를 화면에서 빼고, 카드 본문은 한두 문장으로.

# Scope

## In Scope

- `features/global-guide/data.ts` — 일곱 탭의 문장을 쉬운 말로 다시 쓴다. `sources` 필드는 **데이터에 남긴다**(화면에는 안 보임) — 사실이 어느 파일에서 왔는지는 유지보수자에게 여전히 필요하고, 경로 실재 가드(298 AC-6)가 계속 문다.
- `features/global-guide/components/GlobalGuideScreen.tsx` — 출처 표시(`Sources`, 서비스 표의 «출처» 열, 메뉴 표의 «근거» 열) 제거. 아키텍처 탭에 «요청이 지나가는 길» 흐름 한 줄. 테스트 계정을 계정별 표로. 유지보수자용 절(«이 표가 스스로 지키는 것과 못 지키는 것») 제거.
- `tests/unit/GlobalGuideScreen.test.tsx` — 화면에 출처가 안 보이는지 단언 추가.

## Out of Scope

- «도메인 한눈에» 탭(321) · 도메인 가이드 6개 · `PermissionMapTable` 의 행 데이터(`permission-map.ts`).

# Acceptance Criteria

- AC-1: 일곱 탭의 제목 · 순서 · testid 가 그대로다(딥링크 해시 유지).
- AC-2: 전역 가이드 화면 어디에도 «출처» · «근거» 표시가 없다(단위 시험).
- AC-3: 화면 문장에 파일 경로 · 줄 번호 · 티켓 번호가 없다(단위 시험: `.md` · `.sql` · `.ts:` · `TASK-` 패턴 0).
- AC-4: `sources` 경로 실재 가드(298 AC-6)는 그대로 초록.
- AC-5: tsc · lint · vitest(GlobalGuideScreen) rc=0, axe 위반 0.
- AC-6: 소유자가 데모 창(또는 Vercel 미리보기)에서 «쉽고 보기 쉽다» 확인.

# Implementation Notes (2026-10-09 UTC)

- 🔵 **범위가 한 칸 넓어졌다** — AC-3 시험을 처음 돌리자 «권한 및 테스트 계정» 탭이 빨개졌다(`TASK-BE-486` · `rbac.md` · `:80`). 출처는 탭 문장이 아니라 공용 `shared/guide/permission-map.ts` 의 «알아 둘 예외»(`mismatch`) 문장이었다. 같은 문장을 IAM 가이드도 보여 주므로 그 자리에서 고쳤다. 메뉴 표 행의 화면 문장(`note` · `extra` · `crudNote` · `description` · `purpose`)에 있던 티켓 · ADR 번호와 파일 이름 9곳도 함께 걷어냈다 — 그래서 AC-3 은 «메뉴 소개» 탭까지 일곱 탭 전부에 건다(«도메인 한눈에» 는 321 의 데이터라 제외). 메뉴 표의 열 구성(라우트 · 권한 코드 · 연결 서비스)은 그대로다.
- 테스트 계정 = 계정별 표(데모 운영자 · `viewer@demo.com` · `platform@demo.com`). 유지보수자용 절 «이 표가 스스로 지키는 것과 못 지키는 것» 삭제. 아키텍처 탭에 «요청이 지나가는 길» 흐름 한 줄(`REQUEST_PATH`).

# AC Results (2026-10-09 UTC)

- AC-1 ✅ 탭 제목 · 순서 · id 불변 — 기존 «8 tabs» · 해시 딥링크 시험 초록.
- AC-2 ✅ «출처» · «근거» 표시 0 — 새 시험(열 머리 · 경로 문자열 누출 둘 다 단언).
- AC-3 ✅ 일곱 탭 문장에 `TASK-` · `ADR-` · 파일 확장자 · `:줄` 0 — 새 시험. **bite**: `data.ts` 의 MySQL 카드에 `(docker-compose.yml:55)` 주입 → 그 시험 하나만 빨강(`global-guide-servers: .yml`, `:55`), 원본 복원 `cmp` 일치.
- AC-4 ✅ `sources` 경로 실재 시험 초록(인용은 데이터에 그대로).
- AC-5 ✅ `tsc --noEmit` rc=0 · `next lint`(바꾼 파일) rc=0 · vitest 전체 **350/350 파일 · 3975/3975** rc=0 · axe 위반 0.
- AC-6 ⚪ 소유자 확인 대기(Vercel 배포 후 /guide).

# Related Specs

- `specs/services/console-web/architecture.md` (전역 가이드 정적 화면)

# Related Contracts

- 없음(정적 화면).

# Edge Cases

- 출처를 숨겨도 `sources` 가 데이터에 남으므로 사실이 바뀌면 사람이 함께 고쳐야 한다 — 298 과 같음.
- 메뉴 표(`PermissionMapTable`)는 도메인 가이드도 쓴다 → 전역 가이드에서만 `showSources={false}`.

# Failure Scenarios

- 문장을 줄이다 사실이 틀어짐 → 원문 사실(숫자 20분 · 180분 · 1800분 · 계정 이메일)을 그대로 옮기고, 새 사실을 지어내지 않는다.
