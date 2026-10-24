# Task ID

TASK-MONO-808

# Title

`ADR-MONO-084` P3 — WMS 실적 지표: 일별 집계를 한국 시간 날짜로 옮기고 읽기 모델을 다시 쌓는다 · `metrics` (입고 수량 · 출고 수량 · 출고 건수) · 콘솔 개요 실적 줄

# Status

done

# Owner

monorepo

# Task Tags

- code
- api
- data-migration
- wms
- platform-console
- test

---

> **분석 모델:** Opus 5.5 / **구현 권장:** Opus (읽기 모델 다시 쌓기 = 운영 데이터 작업 · 투영 변경)
> 🔴 **이 티켓의 본체는 코드가 아니라 데이터다**(ADR-084 D3). 화면만 KST 로 바꾸면 숫자가 틀린 날에 붙는다.

# Dependency Markers

- **선행**: `TASK-MONO-806`(P1) `main` 머지.
- 형제: `TASK-MONO-807` · `809`~`812`.

# Goal

WMS 의 일별 입고 · 출고 집계는 지금 UTC 날짜로 잘려 저장된다(`InboundProjectionService.java:246` · `OutboundProjectionService.java:230`). 그래서 00:00–09:00 KST 의 작업이 전날에 붙는다. 이것을 KST 날짜로 옮기고, P1 공용 모양의 `metrics` 를 내려주고, 콘솔 개요 맨 위에 실적 줄을 붙인다.

# Scope

## In Scope

- 계약 먼저: `admin-service-api.md` 에 `GET /api/v1/admin/dashboard/metrics?period=week|month[&warehouseId=]` · 지표 `qtyReceived` · `qtyShipped` · `shipmentCount`(throughput 정의 그대로) · 경계 = KST. 기존 `/throughput` 의 날짜 의미가 KST 로 바뀐다는 것도 그 절에 적는다.
- 투영: 날짜 자르기를 `KstPeriodBounds`(P1)로 · 서비스 안 `ZoneOffset.UTC` 날짜 자르기 제거.
- 🔴 읽기 모델 다시 쌓기: 기존 `throughput_*_daily` 행을 KST 날짜로 다시 만든다. 방법(원천 이벤트 재생 · 원천 테이블에서 재계산 · 마이그레이션 SQL)은 AC-0 이 정한다.
- 콘솔: `features/wms-ops` 개요에 P1 «실적 줄» · 추세 카드의 «UTC 기준» 문구를 다시 쌓기가 끝난 뒤 제거(`lib/throughput.ts:11-17` 의 이유 주석도 함께) · 콘솔이 출고 목록을 잘라 세던 오늘/주간/월간(`kst-period.ts` 사용처)을 새 응답으로 대체할지 AC-0 에서 정한다.

## Out of Scope

- 새 WMS 지표(재고 조정 사유별 등) — 계약 변경으로 나중에.
- 창고 간 비교 화면.

## AC-0 기록 (2026-10-24 UTC, origin/main `777d0c571`)

🔴 **ADR-084 D3 의 전제 둘이 그대로는 성립하지 않았다** — 그래서 방법을 소유자에게 물었고(선택지 셋), 소유자가 **「건별 기록 추가」** 를 골랐다(2026-10-24).

| 질문 | 실측 (근거) |
|---|---|
| 일별 행만으로 다시 자를 수 있나 | **없다.** `admin_throughput_{inbound,outbound}_daily` = `(date, warehouse_id)` + 합계뿐, 시각 없음(`V2__init_readmodel.sql:371-398`) → UTC 15:00–24:00 부분을 다음 날로 떼어 낼 수 없다 |
| 출고 원천 | `admin_shipment_summary` — 출고 1건 1행, 투영이 날을 자른 그 `shipped_at` · 더한 그 `total_qty`(`OutboundProjectionService.java:175-233`) ⇒ **admin_db 안 SQL 로 정확히 재계산** |
| 입고 원천 | admin 에 건별 행 **없음**(dedupe 표는 시각·수량 없음 `V1:183-194` · ASN/검수 요약은 다른 사건). 정확한 원천은 `inbound_db.inbound_outbox` 뿐 — 행 id = eventId · 엔벨로프 전체 JSONB · DELETE 트리거로 막힘(`inbound-service V5` · `V7__role_grants.sql:110-126`) ⇒ **다른 DB 라 Flyway 가 못 읽는다** |
| Kafka 재생 | 토픽 보존 **7 일**(`docker-compose.yml` `MAIN_RETENTION_MS=604800000`) ⇒ 런북의 «비우고 재생» 은 7 일보다 오래된 날을 **지운다** |
| 직전 구간 | 공용 `previousWindow` 는 «같은 경과 시간» 까지(시각 단위) ⇒ **일별 행으로는 못 낸다** — 건별 행이 필요 |
| 데모 시드 | 일별 표에 직접 쓰는 시드 **0**(`R__seed_dev_masterref.sql:61-65`). `seed-wms.sh` 는 실제 API 를 몰아 이벤트를 낸다(입고 87건 · 출고 1건 + 스토어 주문) · AMI 는 이미지만 굽고 데이터는 안 담는다 ⇒ **다음 굽기 = 인스턴스 교체 = 신선 볼륨 = 옛 행 0** |
| 행 수(로컬 · 데모 볼륨) | ⚪ **못 쟀다** — 이 호스트에 Docker 가 없고 데모 창이 열려 있지 않다. 위 판단은 «행이 있으면 어떻게 되나» 로 세웠으므로 수에 의존하지 않는다 |
| `/throughput` 소비자 | 콘솔 `wms-inventory-api.ts:57-71` · `throughput-state.ts` · BFF `app/api/wms/throughput/route.ts` · `use-wms-throughput.ts` · `WmsThroughputCard.tsx`(«UTC 기준») · `lib/throughput.ts:11-17` · 샘플 `fixtures/wms.ts` · 단위 시험 6 파일 · e2e **0**. 계약 `console-integration-contract.md` «UTC, not KST» |

**고른 방법 (소유자 결정 2026-10-24 «건별 기록 추가»)**

1. 입고 건별 표 `admin_putaway_fact` 를 새로 두고 투영이 **같은 트랜잭션**에서 쓴다(V6). 출고는 이미 `admin_shipment_summary` 가 그 역할.
2. 실적(`/metrics`)은 **건별 행**에서 계산 — 직전 구간이 정확하다.
3. 출고 일별 표는 **V6 가 그 자리에서** KST 로 다시 쌓는다(`admin_rebuild_outbound_daily_kst()`).
4. 입고 일별 표는 운영자 스크립트(`projects/wms-platform/scripts/admin-throughput-kst-backfill.sh`)가 outbox → 건별 표로 복사한 뒤 `admin_rebuild_inbound_daily_kst()` 로 다시 쌓는다. 🔴 함수는 **건별 행이 일별 행보다 적으면 거부**한다(옛 볼륨에서 바로 부르면 지난 날을 전부 지우기 때문).
5. 🔴 복사 대상은 admin 이 **이미 APPLIED 한** 이벤트만 — 발행됐지만 아직 안 먹은 이벤트까지 넣으면 나중에 투영이 일별 행에 또 더해 두 번 센다(Edge Case 「다시 쌓는 도중 들어오는 새 이벤트」의 답). 다시 쌓기는 표를 잠가 동시 투영과 섞이지 않는다.

**곁에서 정한 것**

- 실적은 **창고 전체(warehouse-global)** — 계약 § 1.0(ADR-MONO-065): throughput 과 같은 부류. 입고는 원천에 테넌트가 없다. ⇒ AC-3 의 «테넌트 격리» 는 «테넌트 claim 이 결과를 나누지 **않는다**» 로 고정한다(두 테넌트 → 같은 본문).
- 콘솔이 출고 목록을 잘라 세던 오늘/주간/월간 건수는 **대체하지 않는다** — 그것은 테넌트 소유 부류(`/dashboard/shipments`)의 수라 뜻이 다르다.
- «UTC 기준» 문구는 **남긴다**(AC-4) — 콘솔은 Vercel 로 먼저 나가고 백엔드는 굽기 뒤라, 지금 지우면 화면이 백엔드보다 먼저 거짓말을 한다. 지우는 것은 **굽기와 같은 창**의 후속 `TASK-MONO-815` — 굽기 뒤에는 반대로 «UTC 기준» 이 거짓이 되기 때문(콘솔 계약 § 2.4.5 에 적음).
- 🔵 출고는 기존 볼륨에서 **합계가 바뀔 수 있다** — TASK-BE-643(10-22) 이전 카운터가 버린 출고 · 같은 출고가 새 eventId 로 다시 온 경우. 요약(건별 진실)을 따르고 차이는 NOTICE 로 남긴다.

**곁에서 본 결함(이 티켓 밖, 기록만)**: 런북의 대조 SQL 이 없는 표 `inbound_putaway_completed_event_log` 를 읽는다(`read-model-rebuild.md` Step 8) · DLQ 토픽 이름 `.dlq` vs admin 이 쓰는 `.DLT`(이미 `TASK-MONO-813` 으로 기안됨) · outbound `generateShipmentNo` 도 UTC 날짜.

# Acceptance Criteria

- [x] **AC-0** — 🔴 **데이터 질문을 코드 질문보다 먼저**: 읽기 모델 행의 원천(투영이 받는 이벤트 · 그 이벤트가 아직 남아 있는가 · 원천 서비스 테이블에서 다시 셀 수 있는가) · 행 수(로컬 · 데모 시드) · 데모 구워진 시드에 이 행이 들어 있는가 · `/throughput` 소비자 전수. 방법을 하나 고르고 이유를 이 파일에 적는다.
- [x] **AC-1** — 투영 시험: 15:30 UTC(= 다음 날 00:30 KST) 이벤트가 KST 다음 날에 붙는다(`Clock` 주입, `TZ=UTC` · `TZ=Asia/Seoul` 둘 다).
- [x] **AC-2** — 🔴 보존 대조군: 다시 쌓기 전후로 **같은 전체 기간의 합계가 같다**(날 경계만 옮겨지고 총량은 보존 — ADR-084 Verification).
- [x] **AC-3** — `metrics` 시험: 현재/직전 구간 · 일별 0 채우기 · 창고 필터 · 테넌트 격리.
- [x] **AC-4** — 콘솔: 실적 줄 렌더 · 칸 실패 · 401 · 샘플 모드 응답. 다시 쌓기 전에는 «UTC 기준» 문구가 남는다.
- [ ] **AC-5** — 배포 상태(`main` · AMI · 데모 창 라이브 · 다시 쌓기를 데모에서 언제 돌리나)를 따로 적는다.

# Related Specs

- `docs/adr/ADR-MONO-084-domain-overview-performance-metrics.md` D3 · Consequences(🔴 다시 쌓기)
- `projects/wms-platform/specs/services/admin-service/architecture.md`

# Related Contracts

- `projects/wms-platform/specs/contracts/http/admin-service-api.md` § `GET /api/v1/admin/dashboard/throughput` · 새 `/metrics`
- P1 공용 실적 응답 모양

# Edge Cases

- 다시 쌓는 도중 들어오는 새 이벤트 → 같은 날짜 행을 두 번 세지 않게(AC-0 방법에서 정한다).
- 이벤트 시각이 없는 옛 행 → AC-0 에서 수를 세고 처리를 적는다.

# Failure Scenarios

- 투영만 바꾸고 옛 행을 그대로 둔다 → 바꾼 날 이전은 UTC 날짜, 이후는 KST 날짜인 섞인 차트.
- 다시 쌓기를 기존 볼륨에서 안 돌린다 → 신선 볼륨인 CI 는 영원히 초록이다(신선 볼륨은 옛 행이 없다).

## 검증 (2026-10-24 UTC, 로컬 · Windows 호스트 · Docker 없음)

| 무엇 | 명령 | 결과 |
|---|---|---|
| admin-service 단위 · 슬라이스 | `gradlew :…:admin-service:test` | rc=0 · **354 / 354** (새 `MetricsDashboardControllerSliceTest` 7 · `InboundProjectionServiceTest` 14 · `OutboundProjectionServiceTest` 9) |
| 🔴 bite (AC-1) | 입고 투영 날 자르기를 `atZone(UTC)` 로 되돌림 | `putawayCompleted_bucketsOnTheKstDay` **두 시간대(UTC · Asia/Seoul) 다 빨강** + `withoutCompletedAt` 빨강 · 경계 직전 대조군(23:59:59 KST) 초록 → 복원 후 rc=0 |
| 백필 스크립트 | `bash -n` | rc=0 · 실행 비트 `100755` |
| 콘솔 타입 | `tsc --noEmit` | rc=0 |
| 콘솔 관련 시험 | `TZ=UTC vitest run` wms- · Wms · sample- · f5- · performance- · ecommerce-overview/page · overview-consistency | rc=0 · **56 파일 811** |
| 콘솔 전체 (에이전트 실행) | `TZ=UTC vitest run --maxWorkers=2 --minWorkers=1` | 5302 / 5303 — 빨강 1 = `erp-ops/DelegationFactCard.test.tsx`(이 변경과 무관 · 단독 34 / 34) |
| 콘솔 빌드 (에이전트 실행) | `next build` | rc=0 (PageProps 검사 포함 · `ƒ /wms`) |
| ⚪ Testcontainers IT | `ThroughputKstRebuildIntegrationTest` · Kafka IT 2 · Flyway IT | **CI 가 첫 판정** — 이 호스트에 Docker 없음 |

**AC 판정 메모**

- AC-1 «`Clock` 주입» — 날짜는 **사건의 시각**(`completedAt`/`shippedAt`)에서 나오지 서버 시계에서 나오지 않는다. 그래서 문 것은 시계가 아니라 **JVM 기본 시간대**(UTC · Asia/Seoul 두 판). 회귀가 나올 자리가 그쪽이다.
- AC-2 — `ThroughputKstRebuildIntegrationTest` 가 옛 투영 모양(UTC 날)으로 심고 함수를 불러 **전후 합계 같음 + 날만 이동**(출고 · 입고) · 달 경계 · 거부 경로 · 카운터가 버렸던 옛 볼륨(합계가 바뀌고 그 차이를 돌려줌)을 잰다. 🟢 **CI 판정으로 닫힘** — 아래 «CI 판정».
- AC-3 — 현재/직전 창(같은 경과 시간) · 0 채우기 · 창고 필터 · 🔵 «테넌트 격리» 는 계약 § 1.0 에 맞춰 **«나누지 않는다»** 로 닫았다(두 테넌트 → 같은 본문) — 실적은 throughput 과 같은 창고 전체 부류. `[from, to)` 와 `warehouseId IS NULL` UUID 바인딩은 IT 에서(⚪ CI).
- AC-4 — «UTC 기준» 문구 · 주석 그대로(`WmsThroughputCard.test.tsx` 기존 단언 통과). 문구를 바꾸는 일은 `TASK-MONO-815`(굽기와 같은 창).
- Edge «이벤트 시각이 없는 옛 행» — 일별 행엔 늘 `date` 가 있고, 사건엔 `completedAt` 이 없으면 엔벨로프 `occurredAt`(늘 있음)을 쓴다 — 투영 · 백필 스크립트 같은 규칙. 0 건이 아니라 **생길 수 없는 경우**.
- Failure «신선 볼륨 CI 는 영원히 초록» — IT 가 **옛 볼륨의 모양을 손으로 심어** 다시 쌓기를 잰다. 남은 공백: Flyway V6 자체를 «이미 행이 있는 볼륨» 에서 돌리는 판은 IT 가 아니라 함수 호출로 대신 쟀다(V6 의 마지막 문장이 같은 함수다).

### 🔴 CI 첫 판(PR #4506, `2d1d4e4a1`)이 잡은 것 — 내 시험 고침이 반쪽이었다

`Integration (master + notification + outbound)` 묶음(admin-service IT 가 여기 산다) 92 개 중 **2 빨강**: `InboundProjectionKafkaIT.putawayCompleted_reverseOrderAcrossAsns_countsBoth` · `OutboundProjectionKafkaIT.shippingConfirmed_reverseOrderAcrossShipments_countsBoth`. 두 시험은 «같은 날 행에 두 건, 늦은 쪽이 먼저 도착» 을 재는데 늦은 쪽 시각이 `15:00Z` · `16:00Z` — **UTC 로는 05-10, KST 로는 05-11** 이라 두 건이 다른 행으로 갈라졌다. 코드 결함이 아니라 **날 경계가 옮겨 간 것을 시험이 정확히 문 것**이다. 고침: 늦은 쪽을 `14:00Z`(23:00 KST)로 — 시험의 뜻(같은 날 · 늦은 쪽 먼저)은 그대로.

🔴 **놓친 이유**: Kafka IT 를 고칠 때 `atZone(ZoneOffset.UTC)` 만 grep 했다. 이 두 시험은 계산이 아니라 **날짜를 손으로 박아(`LocalDate.of`)** 그 grep 에 안 걸린다. 단위 시험에서 같은 모양(`reverseOrderAcrossAsns` 의 `15:00Z`)을 이미 한 번 고쳤으면서 IT 쪽 쌍둥이를 안 찾았다. ⇒ 고친 뒤 `T(1[5-9]|2[0-3]):..:..Z` 로 admin-service 시험 전체를 다시 훑었다 — 나머지는 의도된 경계 시험이거나 날짜를 단언하지 않는다.

## 배포 상태

- `main` ✅ `4335ed688`(PR #4506, 2026-10-23T16:31:57Z, 머지 직전 실패 0 · 28 pass / 39 skip).
- AMI ⚪ 다음 굽기(admin-service 새 이미지 + V6). 🔵 굽기 = 인스턴스 교체 = **신선 볼륨** ⇒ 데모에선 입고 백필이 **필요 없다**(옛 행 0). 옛 볼륨(오래 쓴 로컬)만 런북 § KST day rebuild.
- 콘솔 = Vercel, 머지 즉시. 굽기 전 데모에서 실적 줄은 «불러올 수 없음»(엔드포인트 없음 → degraded), 다른 블록은 그대로.
- 🔴 **`TASK-MONO-815` 는 그 굽기와 같은 창에 머지** — 처리량 카드 KST 창 · 문구. 먼저도 뒤도 거짓이다(콘솔 계약 § 2.4.5).
- 라이브 ⚪ 데모 창: `/wms` 실적 줄 숫자 · `?period=week` · 처리량 카드 마지막 날.

## CI 판정 (PR #4506 둘째 판 `d61606e94`, run 39986296380)

- admin-service `integrationTest` = `@Tag("integration")` 클래스 + Kafka IT 의 시험 **92 개**(소스에서 셈: 다시 쌓기 IT 6 · Flyway 7 · Kafka 4 파일 35 · 나머지 44). 첫 판 로그가 정확히 **«92 tests completed, 2 failed»**(실패 = 위 Kafka IT 둘)였고 둘째 판은 그 job 이 초록 ⇒ `ThroughputKstRebuildIntegrationTest` 6 개(보존 대조군 출고 · 입고 · 옛 볼륨 합계 변화 · 거부 경로 · 건별 멱등 · `[from,to)` + null UUID 바인딩)와 Flyway V6 확인이 **실 Postgres 에서 통과**.
- 🔵 이름이 아니라 **수로** 확인했다 — Gradle 은 통과 시험 이름을 로그에 안 찍고 시험 보고서 아티팩트도 올리지 않는다(이 런의 아티팩트는 `wms-boot-jars` 하나).

---

## CORRECTION — 34차 창 라이브 결과 (2026-10-24 UTC)

- **AC-5 — 닫힘.** 34차 창(2026-10-24 UTC): `main` = #4506 `4335ed688` · AMI = `ami-0d9f8da7d20d7a180`(`d5bb28f14` ⊇ `4335ed688`) · 라이브 = wms 지표 API 200, 경계 `Asia/Seoul`. 입고 수량 8,695 · 출고 수량 10 · 출고 건수 1, 전부 KST 10/25 칸이다(이벤트 16:05Z = 01:05 KST, UTC 날짜로 잘랐다면 10/24 에 붙었을 시각). 소유자 화면 `/wms` 실적 줄이 같은 값이고 월간 일별 막대 10/25 = 10. 다시 쌓기(백필)는 필요 없었다 — 신선 볼륨이라 옛 행이 0 이다. 창고별 대조: 입고 WH-MAIN 86건/8,600 + WH01 1건/95, 출고 WH01 1건/10(`admin_throughput_*_daily`, o12) = 실적 줄 합계. (증거 o04 · o12)
- (원문 체크박스는 동결 규칙(HARDSTOP-05)상 바꾸지 않았다 — 이 절이 그 AC 의 닫힘 기록이다.)
