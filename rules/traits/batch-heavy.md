# Trait: batch-heavy

> **Activated when**: `PROJECT.md` includes `batch-heavy` in `traits:`.

---

## Scope

대량 배치 처리가 시스템 가치의 핵심이며, 실시간 처리보다 스케줄 기반 일괄 처리가 지배적인 시스템에 적용된다. 야간 ETL, 월 결산, 빌링, 정산, 대량 리포트 생성, cross-system 동기화, 정기 데이터 정합성 검증 등.

"실시간 응답 < 정확한 종결" 의 가치 우선순위. 한 번의 잘못된 계산이 회복 불가능한 결과를 만드는 영역이라 **재실행 가능성 (idempotency)** 과 **체크포인트 회복** 이 설계의 중심이 된다.

적용 범위:
- **필수 적용**: 스케줄 기반 잡 (`@Scheduled`, cron, ShedLock 등), 외부 정산 / 대량 export / 데이터 보정
- **조건부**: 비동기 워커 (실시간성이 약한 worker 가 사실상 micro-batch 인 경우)
- **제외**: 순수 실시간 요청 처리 (REST / event consumer 의 1건 처리)

전형적으로 `data-intensive` 또는 `integration-heavy` 와 함께 선언된다.

---

## Mandatory Rules

### B1. 모든 배치 잡은 **idempotent** + 재실행 가능

배치 재실행 시 결과가 동일해야 한다 (또는 재실행이 안전한 보강만 일어난다):

- 입력 dataset = `(period, dataset_id)` 같은 key 로 결정적
- 출력 = upsert 또는 단일 commit 의 atomic write (partial fail 후 재시도 시 중복 생성 0)
- "이미 처리된 row" 식별 마커 (`processed_at`, `batch_run_id`) 또는 dedupe table 보유
- 재실행 시 자동으로 partial state 복구 (반드시 from-scratch 시작 안 함)

회귀 가드: 같은 잡을 같은 입력으로 2번 연속 실행 → 두 번째 run 의 side-effect = 0 (또는 idempotent upsert 로 결과 동일).

### B2. **Checkpoint** + 부분 실패 회복

긴 배치는 진행 상황을 영속화하여 partial 회복 가능:

- 처리 단위 (예: 10K rows / chunk) 마다 checkpoint commit
- 잡 재기동 시 마지막 checkpoint 부터 재개
- 진행률 메트릭 (`<batch>.progress.percent`, `<batch>.rows.processed.count`)
- 잡 미완료 상태 누락 (예: pod kill) 후 자동 재기동 시 cleaner 가 stale partial 식별 + 회복

체크포인트 구조 표준 (예시 schema):
```
batch_runs (run_id PK, batch_name, started_at, completed_at NULL, last_checkpoint, status)
batch_run_items (run_id FK, item_id, processed_at NULL)
```

### B3. **재시도 정책** 명시

배치 step 이 trans error 시:

- 재시도 가능한 오류 (네트워크 timeout, 503 transient) — exponential backoff 3회, 그 후 `<batch>.alert.recovery.exhausted` 알림 + 잡 종결 (다음 스케줄에 재실행 자연 가능)
- 재시도 불가능 오류 (validation, integrity violation) — 즉시 실패 + dead-letter row + 운영자 검토 큐 (자동 close 금지)
- per-row 실패 vs per-batch 실패 분리 — per-row 실패는 batch 자체를 죽이지 않음 (DLT-style)

### B4. **배치-온라인 경합** 관리

배치가 OLTP 트래픽과 같은 DB 를 공유할 때:

- 대량 SELECT 는 read replica (read-heavy trait 와 자연 부합)
- 대량 UPDATE/INSERT 는 chunk 분할 + sleep / yield (`Thread.sleep(...)` 또는 `LIMIT` 단위 commit)
- LOCK 보호된 row 는 `FOR UPDATE SKIP LOCKED` 또는 우선순위 고려한 lock 순서
- 배치 윈도우 (off-peak hours) 명시 — peak hour 배치 금지 또는 자동 reschedule

### B5. **분산 lock** for cluster-wide singleton

다중 인스턴스 배포에서 **잡당 1 인스턴스만 실행** 보장:

- ShedLock + DB lock provider (PostgreSQL pg_advisory_lock 또는 lock 테이블) 표준
- Redis lock 은 lock liveness 가 핵심 잡 에는 부적합 (timeout 시 동시 실행 위험)
- lock 획득 실패 시 잡 silent skip + 메트릭 (`<batch>.lock.acquired.count`) — error 아님

### B6. **관찰 + 알림** 표준

각 잡 마다 다음 메트릭 강제:

- `<batch>.run.count{result=success|failed|skipped|in_progress}` (counter / gauge)
- `<batch>.duration.seconds` (histogram, p50/p95/p99)
- `<batch>.rows.processed.count` (counter)
- `<batch>.lag.seconds` (gauge — 마지막 성공 run 으로부터 경과 시간; SLO 알림 기반)
- `<batch>.failure.count` (counter)
- `<batch>.alert.recovery.exhausted` 이벤트 (재시도 cap 도달 시)

`lag.seconds > 2 * scheduled_interval` 이면 알림 fire — 잡이 멎은 신호.

### B7. **리소스 격리** (배치 ↔ OLTP)

- 배치 전용 connection pool 또는 thread executor — OLTP 풀 고갈 차단
- DB connection 의 read-only 모드 사용 (write 가 적은 경우)
- 메모리 스트리밍 (cursor / chunk iterator) — 전체 dataset 메모리 적재 금지

### B8. **재현성** (data-intensive 와 함께 declared 시 강제)

배치 결과의 재현 가능성:

- 입력 hash + 모델 버전 (해당하는 경우) 함께 저장
- "이 잡은 이 입력으로 이 결과를 냈다" 가 audit 가능
- 결과 immutability — 한 번 commit 된 결과는 재계산으로만 변경 (직접 UPDATE 금지)

---

## Overrides

해당 없음. common rule 과 충돌 없음. `transactional` T3 (outbox) 와는 자연 부합 — 배치가 발생시키는 cross-service 이벤트는 outbox 패턴 그대로 사용.

---

## Anti-patterns

- "스케줄러 만 돌려두면 끝" — 잡 실패 시 알림 없음, 미완료 상태 방치
- 배치 잡이 single-replica 가정으로 작성됨 — 다중 인스턴스 배포 시 같은 잡 동시 실행 → race / 중복 side-effect
- 매번 from-scratch 처리 — partial 실패 시 모든 작업 되감기 (분 단위 잡 → 시간 소요)
- 배치 중간에 직접 commit 안 함 → JVM kill 시 모든 진행 손실
- 한 row 실패가 전체 batch 죽임 — per-row vs per-batch 실패 구분 부재
- OLTP DB 에 직접 100K row UPDATE — peak hour OLTP latency 폭증
- 잡 실패가 silent — lag 메트릭 없으면 운영자가 알 수 없음
- 재시도 cap 없이 무한 재시도 — vendor outage 시 모든 시스템 자원 소진
- "재실행하면 회복됨" 가정인데 idempotent 보장 없음 — 같은 결제 / 정산 row 가 N 번 생성
- ShedLock 누락 + cron 만 사용 — kubernetes pod 다중 replica 환경에서 잡 N 번 동시 실행
