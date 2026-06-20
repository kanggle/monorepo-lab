---
id: TASK-BE-411
title: dailySalesAggregationJob — 차단 해소(출력스키마·데이터소스·소비자·중복) spec-clarification 선행
status: ready
project: ecommerce-microservices-platform
service: batch-worker
type: spec
created: 2026-06-20
---

# TASK-BE-411 — dailySalesAggregationJob spec-clarification (구현 차단 해소)

> ⛔ **구현 차단 task** — 3개 batch 잡 중 가장 심하게 under-specified. 아래 AC 전부 해소 전 구현 금지. 본 task 는 결정/문서화만. (2026-06-20 BE-409 스코핑에서 HARDSTOP-06/08/09 로 식별.)

## Goal

batch-worker `dailySalesAggregationJob`(`@Scheduled(cron="0 0 1 * * *")`, order/payment 일일 합산 요약)은 출력 테이블·데이터소스·소비자가 전혀 정의되지 않았다. 구현 전 필요한 spec/contract 결정을 확정한다.

## Background (차단 사유)

- 출력 스키마 미정: `overview.md`/`architecture.md` 의 Owned Data 는 `batch_job_execution_history` 만 — `daily_sales_summary` 류 집계 출력 테이블·Flyway 없음.
- 데이터소스 미정: "order/payment 합산" 이라지만 batch-worker `dependencies.md` 는 order/payment-service 미인가, cross-service DB 직접 read 는 boundary 규칙 위반. HTTP/이벤트 컨트랙트 미발행.
- 소비자 미정: "downstream 분석 dashboard 용" 이라지만 어떤 소비자(platform-console? 분석툴?)도 컨트랙트 없음.
- 중복 미해결: settlement-service(BE-365)가 이미 order/payment 이벤트로 seller 별 정산 accrual 계산 → 일일 집계와 중복/관계 미정.

## Acceptance Criteria (전부 해소 전 impl 금지)

- [ ] **AC-1**: 출력 테이블 스키마 확정 + `architecture.md § Owned Data` 반영(예: `daily_sales_summary(id, summary_date, total_orders, total_revenue, total_payments, created_at)`) + Flyway 마이그레이션 명세.
- [ ] **AC-2**: 데이터소스 확정 — order/payment HTTP 호출(→dependencies.md 인가 + 필요 admin read 컨트랙트 발행) vs Kafka 이벤트 소비(→소비 컨트랙트 정의) 중 택1.
- [ ] **AC-3**: downstream 소비자 컨트랙트 정의(요약 테이블을 누가 어떻게 읽는지). 소비자 부재면 잡의 가치 재평가.
- [ ] **AC-4**: settlement-service(BE-365) 와 관계 정리 — 중복 계산인지, 그 위에 빌드하는지, 별개 차원인지 확정.
- [ ] **AC-5**: 위 해소 후 구현 impl task(신규 BE-id)를 ready/ 에 작성하고 본 task 는 done/ 로 이관.

## Related Specs / Contracts

- `specs/services/batch-worker/{overview,architecture,dependencies}.md`
- settlement-service(BE-365) 정산 모델 — 중복 평가 참조
- `specs/contracts/` (신규 데이터소스/소비자 컨트랙트 가능)

## Failure Scenarios

- AC 미해소 상태로 구현 강행 → 출력 스키마 임의 결정 / boundary 위반 DB read / settlement 중복. 반드시 결정 선행.
