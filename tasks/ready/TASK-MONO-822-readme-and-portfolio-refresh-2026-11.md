# Task ID

TASK-MONO-822

# Title

루트 `README.md` 와 `docs/portfolio.md` 를 2026-11 현재 상태로 맞춘다 — 낡은 «DNS 미연결» 문구 · 기동 시간 모순 · 이미 있는 WMS/SCM/콘솔 화면을 «없음» 이라 적은 칸

# Status

ready

# Owner

monorepo

# Task Tags

- docs
- portfolio

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (문서 두 개, 코드 변경 없음)
> 기안 2026-11-01 UTC. 소유자 요청: «모노레포 리드미 업데이트 및 포트폴리오 업데이트». 사실은 origin/main `dec6aec0c` 에서 읽었다.

# Dependency Markers

- 관련: `TASK-MONO-648`(in-progress — 스크린샷 촬영·큐레이션. 이 티켓은 **이미 커밋된** 이미지만 배선하고 새로 찍지 않는다) · `TASK-MONO-651`(portfolio.md 최초 작성) · `TASK-MONO-503`(README 대조 선례).
- 선행 없음.

# Goal

처음 저장소를 여는 사람(채용 담당자 포함)이 읽는 두 문서가 **지금 사실**을 말한다.

# Scope

## In Scope

**AC-0 사실 (기안 중 실측):**

| # | 문서 | 지금 적힌 것 | 실제 |
|---|---|---|---|
| F1 | README § Live demo | *"도메인은 등록됐고 DNS/Vercel 연결은 아직"* | `TEMPLATE.md` § 공개 호스트명 배분 정본 표: apex·`fan.`·`store.`·`console.`·`auth.` 전부 **✅ 연결됨** |
| F2 | README § Live demo | 스택 기동 *"~2–4 min"* | `docs/portfolio.md`: **약 10분**(2026-09-09 실측 콜드 9분 14초 · 웜 9분 16초) — 두 문서가 서로 다르다 |
| F3 | portfolio.md WMS·SCM | *"대표 화면 없음 — 시드에 창고 데이터가 없어"* | `projects/wms-platform/docs/screenshots/` 3장 · `projects/scm-platform/docs/screenshots/` 1장이 커밋돼 있고 **데이터가 찍혀 있다**(눈 확인: WMS 출고 SHIPPED 1건 + 택배 행 · SCM 발주 3건) |
| F4 | portfolio.md 콘솔 | *"콘솔 자신의 화면은 아직 고르지 않았습니다"* | `projects/platform-console/docs/screenshots/` 6장, 프로젝트 README 에 배선됨(`TASK-MONO-648` 소유자 큐레이션) |
| F5 | portfolio.md 사본 표 | scm 을 2026-09-10 으로 묶음 | GitHub `pushed_at`: scm **2026-09-11** · erp/finance 2026-09-10 · 나머지 넷 2026-08-04 |
| F6 | README 전체 | 2026-10-03 이후 변경 미반영 | ADR-MONO-083(테넌트별 업무 권한 세트) · 084(도메인 실적 지표) · 085(데드레터 `<topic>.DLT`) · 주문 취소 → 출고 취소(TASK-BE-649/650) · 테넌트 셀프 생성 PENDING+초대(TASK-MONO-773) · 시드 4주 이력(TASK-MONO-820/821) |
| F7 | README | `docs/portfolio.md` 로 가는 링크가 **없다** | — |
| F8 | README § AI | *"80+ skills"* | `.claude/skills/**/SKILL.md` = **74** |

- README: F1·F2·F6·F7·F8 고침. 프로젝트 표 행에 최근 기능을 한 구절씩.
- portfolio.md: F3·F4·F5 고침 + «최근에 더한 것» 짧은 절.

## Out of Scope

- 새 스크린샷 촬영(`TASK-MONO-648`).
- 독립 리포 사본 동기화(`portfolio-sync.yml` — force-push 라 별도 소유자 승인 축).
- 프로젝트별 README.

# Acceptance Criteria

- **AC-1** F1–F8 각 칸이 고쳐졌다(F8 은 실측 수로).
- **AC-2** `node scripts/check-readme-screenshots.mjs` rc=0(새로 거는 이미지가 실재 · 고아 없음).
- **AC-3** `bash scripts/check-public-domains.sh` rc=0(README 에 적힌 호스트명이 전부 정본 표에 선언됨).
- **AC-4** `infra/demo/verify-demo-wrapper.sh` 의 «휘발성 엔드포인트 커밋 금지» 검사가 README 에 걸리지 않는다(API Gateway id · `*.cloudfront.net` 0건).

# Related Specs

- `TEMPLATE.md` § 공개 호스트명 배분 (정본)
- `docs/guides/interview-demo-walkthrough.md` § 6

# Related Contracts

없음.

# Edge Cases

- `kanggle-portfolio.vercel.app` 는 아직 CORS 목록에 있다(`infra/demo/aws/terraform/variables.tf`) — «옛 주소 폐기» 로 쓰면 거짓.
- 이미지 경로: portfolio.md 는 `docs/` 기준 상대경로(`../projects/...`).

# Failure Scenarios

- 숫자를 손으로 옮기다 틀림 → 수치는 이 티켓 AC-0 표의 실측값만 쓴다.
- 이미지를 걸었는데 빈 화면 → 거는 장은 기안 중 눈으로 연 것만(WMS 출고 · SCM 조달 · 콘솔 주문).
