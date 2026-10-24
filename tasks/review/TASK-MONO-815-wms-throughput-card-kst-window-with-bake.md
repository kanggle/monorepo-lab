# Task ID

TASK-MONO-815

# Title

WMS 처리량 카드를 한국 시간 창 · «한국 시간 기준» 문구로 — 🔴 `TASK-MONO-808` 의 admin V6 를 싣는 **굽기와 같은 창에** 머지

# Status

review

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

- [x] **AC-0** — 굽힌 AMI 가 `TASK-MONO-808` 머지 커밋을 조상으로 갖는지 확인(`git merge-base --is-ancestor`)하고 그 값을 이 파일에 적는다. 아니면 STOP.
  - → **닫힘 2026-10-24 UTC.** 34차 굽기 `ami-0d9f8da7d20d7a180`(태그 RepoCommit `d5bb28f14`, 핀 #4533). `git merge-base --is-ancestor 4335ed688 d5bb28f14` → **yes**.
  - 소유자 `terraform apply` 로 새 인스턴스 `i-0cebb64967faf3d33` 가 이 AMI 를 쓴다. `check-ami-generation.sh --with-aws` rc=0 «같은 세대» 다.
  - 신선 볼륨이라 입고 옛 행(UTC 날)은 없다.
- [x] **AC-1** — 00:30 KST(= 전날 15:30 UTC) 시각으로 창 끝이 KST 날짜인 시험 · 14일 길이 유지.
  - → 2026-10-24 UTC. 구현은 frontend-engineer(sonnet)가 했고, diff 는 기안자가 대조했다.
  - `shared/lib/kst-period.ts` 에 `kstDateOf(now)` 를 더했다. 기존 `KST_OFFSET_MS` 를 재사용하고 새 오프셋 상수는 없다.
  - `lib/throughput.ts` `utcThroughputWindow` → `kstThroughputWindow` 다. `to` = KST 오늘, `from` = to − 13일.
  - `wms-throughput-lib.test.ts`: `2026-10-24T15:30Z` → `{2026-10-12, 2026-10-25}`(14일). 대조군 `05:00Z` → `to=2026-10-24`. 연말 경계 `2027-01-05T20:00Z` → `{2026-12-24, 2027-01-06}`.
  - `wms-throughput-state.test.ts`: 15:30Z 면 `getThroughput` 을 그 창으로 부른다.
- [x] **AC-2** — 카드 문구 시험이 «한국 시간 기준» 을 단언하고 «UTC» 가 화면에 0 회.
  - → 문구: «최근 14일 · UTC 기준 (from ~ to)» → **«최근 14일 · 한국 시간 기준 (from ~ to)»**.
  - `WmsThroughputCard.test.tsx` 가 정확한 문구와 카드 전체 텍스트의 «UTC» 0 회를 단언한다.
  - 계약 `console-integration-contract.md` § 2.4.5.2 의 «UTC, not KST» 를 KST 로 정정했다.
  - 프록시(`app/api/wms/throughput`)는 날짜 형식만 검증해 무변경이다. 샘플 픽스처는 요청받은 from/to 를 그대로 답하므로 주석만 고쳤다.
  - **무는가:** `src` 만 되돌리면 대상 4파일에서 7건 빨강이다(sample 파일은 import 로 파일 단위 실패). 복구 뒤 56/56 이다. `tsc` rc=0 · `lint` rc=0.
  - 기록만: `ADR-MONO-084:108` 의 «다시 쌓기가 끝나기 전까지 화면은 «UTC 기준»» 문장은 ACCEPTED ADR 이라 고치지 않았다(이 티켓이 그 «끝난 뒤» 다).
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
