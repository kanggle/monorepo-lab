# Task ID

TASK-MONO-730

# Status

ready — ⏳ **SCHEDULED / DO NOT START before the next AMI re-bake** (조건 게이트, 날짜 아님 — AC-0)

# Title

론처에 무권한 데모 계정(`viewer@demo.com`)을 표시하고 (z11) 가드가 그 계정도 대조하게 한다 — **재굽기 뒤에만**

# Owner

monorepo

# Task Tags

- demo
- launcher
- guard

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet 5 — 론처 마크업 한 행 + (z11) 대조 한 칸 + 대조군.

---

# Goal

`TASK-BE-597`(#4001)이 권한 부족 화면 시연용 무권한 운영자 `viewer@demo.com` / `Demo1234!` 를 시드했다
(admin-service `R__seed_demo_viewer_operator.sql` · auth-service `R__seed_demo_viewer_operator_credential.sql`).
방문자가 그 계정을 알 수 있게 론처 「로그인 계정」 카드에 표시하고, 표시된 값이 시드와 갈라지지 않게 (z11) 가
대조하게 한다.

🔴 **지금 하면 안 된다.** 론처(`infra/demo/aws/site/index.html`)는 머지 즉시 Vercel 로 나가지만 계정은 AMI 안의
클론에만 있고 부팅 시 `git pull` 이 없다(`infra/demo/aws/README.md:87-97`). 현재 배포 AMI 는
`REPO_COMMIT=f1da21800`(`infra/demo/aws/deployed-ami.env`)으로 #4001 이전이다 ⇒ 지금 표시하면 **로그인이 실패하는
계정을 방문자에게 안내**하게 된다.

# Scope

## In
- 론처 「로그인 계정」 카드에 viewer 행(`id="c-viewer-email"` 등) + 「권한 부족 화면 시연용 — 대부분 메뉴에서 403」 설명.
- (z11) 확장: 론처에 표시된 viewer 이메일 ↔ auth-service credential 시드의 이메일 열 대조, 대조군(값을 틀리게 주입 → rc=1) 포함.
- 기존 `demo@demo.com` ↔ `seed/lib.sh` `user_token()` 대조는 그대로.

## Out
- 계정·권한 자체의 변경(BE-597 에서 끝남). `/partnerships` 403 은 소유자 결정으로 유지(ADR-MONO-045).

# Acceptance Criteria

- [ ] **AC-0 (verify-then-act)** — 배포 AMI 의 `REPO_COMMIT` 이 `a3f5d52ec`(#4001 머지)의 **자손**인가(`git merge-base --is-ancestor a3f5d52ec <REPO_COMMIT>`). 아니면 **멈추고 ready/ 에 남는다** — 측정한 날짜와 커밋만 한 줄 적는다.
- [ ] **AC-1** — 창에서 viewer 로 콘솔 로그인 → `/api/admin/me` 200 · `roles=[]` · 게이트된 화면(운영자·감사·테넌트) 403 「권한 없음」 렌더(스크린샷).
- [ ] **AC-2** — 론처 viewer 행 추가, `bash infra/demo/verify-demo-wrapper.sh` rc=0, (z11) 새 칸 bite rc=1 → 복원 rc=0.
- [ ] **AC-3** — 콘솔 전역 가이드 「권한 및 테스트 계정」 탭(`TASK-PC-FE-298`)이 viewer 를 언급하지 않는다면 한 줄 추가(비밀번호는 론처·데모 로그인 화면에만 — 298 의 결정).

# Related Specs

- `projects/iam-platform/tasks/review/TASK-BE-597-demo-account-read-coverage-and-restricted-account.md` § 구현 기록 · 후속
- `infra/demo/aws/README.md` § 배포 층

# Related Contracts

- 없음(계약 변경 없음).

# Edge Cases

| 상황 | 기대 |
|---|---|
| 재굽기 뒤 누군가 `demo@demo.com`(SUPER_ADMIN)으로 viewer 에게 역할을 부여 | 공개 데모의 알려진 한계(BE-597 기록). 시드 재적용이 되돌리지 않는다 — 론처 문구는 «시연용» 이지 보장이 아니다 |
| 방문자가 셀프 온보딩으로 `demo-viewer` 슬러그를 선점 | 같은 한계. 막으려면 account-service 시드에 `demo-viewer` SUSPENDED 등록 한 줄(BE-597 제안) — 별도 소유자 결정 |

# Failure Scenarios

1. AC-0 을 건너뛰고 표시 → 방문자가 로그인 실패 계정을 본다.
2. (z11) 를 «이메일 문자열이 론처에 있는가» 로만 짠다 → 시드와 갈라져도 초록(대조가 아니라 존재 확인).
