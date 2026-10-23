# Task ID

TASK-MONO-815

# Title

WMS 처리량 카드를 한국 시간 창 · «한국 시간 기준» 문구로 — 🔴 `TASK-MONO-808` 의 admin V6 를 싣는 **굽기와 같은 창에** 머지

# Status

ready

# Owner

monorepo

# Task Tags

- code
- platform-console
- wms
- deploy-coupled

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Sonnet (콘솔 한 카드 · 문구 · 시험)
> 🔵 **번호 변경 2026-10-24 UTC**: `TASK-MONO-814` 로 기안됐다가(#4506) 동시 세션의 `TASK-MONO-814`(데모 주소 해석기, PR #4508)와 겹쳐 815 로 옮겼다 — 이쪽은 착수 전이고 저쪽은 진행 중이라.
> ⏳ **DO NOT MERGE before** wms admin-service V6(`TASK-MONO-808`)를 담은 AMI 가 데모에 올라가는 창. 콘솔은 Vercel 이라 머지 즉시 나간다 — 먼저 나가면 화면이 «한국 시간» 이라 말하는데 데이터는 아직 UTC 날이다.

# Dependency Markers

- **선행**: `TASK-MONO-808` `main` 머지 + 그 커밋을 담은 굽기.
- 후속 아님 · 형제 없음.

# Goal

`TASK-MONO-808` 이 WMS 일별 처리량을 한국 시간 날짜로 옮겼다(admin V6). 콘솔 처리량 카드는 일부러 그대로 두었다 — 창은 UTC 오늘로 끝나고 화면엔 «최근 14일 · UTC 기준». 굽기 뒤에는 그 문구가 거짓이 되고, 00:00–09:00 KST 에는 창이 가장 새 날을 뺀다. 굽기와 같은 창에 카드를 한국 시간으로 바꾼다.

# Scope

## In Scope

- `features/wms-ops/api/throughput-state.ts` — 창 끝 = KST 오늘(`shared/lib/kst-period.ts`).
- `features/wms-ops/components/WmsThroughputCard.tsx` — «UTC 기준» → «한국 시간 기준».
- `features/wms-ops/lib/throughput.ts:11-17` 의 «WHY UTC» 주석 → 지금 사실로.
- 샘플 `shared/sample/fixtures/wms.ts` 처리량 창이 같은 규칙을 따르는지.
- `console-integration-contract.md` § 2.4.5 «UTC, not KST» 문단 정정.

## Out of Scope

- admin 백엔드(808 에서 끝남) · 옛 볼륨 입고 백필(런북 `read-model-rebuild.md` § KST day rebuild — 운영자 일).

# Acceptance Criteria

- [ ] **AC-0** — 굽힌 AMI 가 `TASK-MONO-808` 머지 커밋을 조상으로 갖는지 확인(`git merge-base --is-ancestor`)하고 그 값을 이 파일에 적는다. 아니면 STOP.
- [ ] **AC-1** — 00:30 KST(= 전날 15:30 UTC) 시각으로 창 끝이 KST 날짜인 시험 · 14일 길이 유지.
- [ ] **AC-2** — 카드 문구 시험이 «한국 시간 기준» 을 단언하고 «UTC» 가 화면에 0 회.
- [ ] **AC-3** — 데모 창에서 `/wms` 카드의 마지막 날이 KST 오늘인지 눈으로 확인(⚪ 이면 이유).

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D3
- `tasks/*/TASK-MONO-808-wms-overview-performance-metrics-kst-rebucket.md` § 배포 상태

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § 1.2 (Day boundary = KST)
- `projects/platform-console/specs/contracts/console-integration-contract.md` § 2.4.5

# Edge Cases

- 옛 볼륨에서 입고 백필 전 — 입고 V6 이전 날은 아직 UTC 날. 신선 볼륨(굽기 = 인스턴스 교체)에서는 없다.

# Failure Scenarios

- 굽기 전에 머지 → 화면이 «한국 시간» 이라 말하는데 데이터는 UTC 날(반대 방향 거짓).
- 굽기 뒤에도 안 머지 → «UTC 기준» 이 거짓 · 00–09 KST 에 새 날 누락.
