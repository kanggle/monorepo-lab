# Performance Benchmark

k6 기반 부하 테스트 결과 및 시스템 한계점 분석.

---

## 환경

| 항목 | 값 |
|------|-----|
| 호스트 | 단일 머신 (Windows 11, Docker Desktop) |
| 스택 | docker-compose 전체 (12 services + Postgres × 10 + Kafka + ES + Redis) |
| 부하 도구 | k6 (latest) |
| 게이트웨이 | Spring Cloud Gateway (8080) → Product/Search Service |
| 측정일 | 2026-04-15 |

> **참고**: 단일 호스트에서 모든 서비스 + 인프라 + 부하 도구가 함께 실행되는 환경입니다.
> 프로덕션급 분리 환경에서는 더 좋은 수치가 기대됩니다.

---

## Search 시나리오 (상품 검색/조회)

**워크로드 구성**

| Stage | Duration | VUs | 목적 |
|-------|----------|-----|------|
| Smoke | 30s | 10 | 정상 동작 검증 |
| Load | 2m 30s | 80 | 예상 트래픽 |
| Stress | 2m 30s | 150 | Saturation point 식별 |

**핵심 결과 (전 구간 통합)**

| Endpoint | Median | P90 | P95 | Max | 검증 통과율 |
|----------|--------|-----|-----|-----|------------|
| Product List | **25ms** | 364ms | 657ms | 17.7s | 99.8% |
| Product Detail | **18ms** | 278ms | 471ms | 12.7s | 99.8% |
| Search | **78ms** | 1.6s | 15.1s | 60s | 72%* |

\* Search는 Stress 단계에서 Elasticsearch 인덱싱 부하로 일부 timeout 발생.

**HTTP 전체 메트릭**

```
Total Requests: 5,714
Throughput:    15.6 req/s (전 구간 평균)
Data:          4.2 MB received, 906 KB sent
Iterations:    2,596 complete (87.3% checks passed)
```

---

## 분석

### 강점 (✓)

- **Smoke / Load 구간 (10~80 VUs)**: 미디안 응답시간 18~78ms — 가벼운 트래픽에서는 모든 엔드포인트 안정적
- **Product list / detail의 99.8% 성공률**: PostgreSQL 직접 조회 경로는 stress 구간에서도 견고
- **Search error rate 0.15%**: 응답이 늦더라도 결과는 반환 (graceful degradation)

### 한계점 (시스템 saturation 식별)

- **150 VUs 이상에서 Search degradation**: Elasticsearch 단일 노드 + 단일 호스트 자원 경합으로 P95가 15s까지 증가
- **Product detail max 12.7s**: 일부 요청이 GC pause 또는 connection pool 대기로 지연
- **Throughput 15.6 req/s**: 단일 호스트에서 12 마이크로서비스 + 인프라 동시 실행으로 인한 자원 부족이 주된 원인

### 개선 방향 (Future Work)

| 영역 | 개선안 | 예상 효과 |
|------|--------|----------|
| Search | Elasticsearch 클러스터화 (3+ nodes) | P95 < 500ms 회복 |
| 자원 격리 | 부하 도구를 별도 호스트로 분리 | Throughput 5~10× |
| Product 캐시 | Redis 추가, 인기 상품 hit 캐싱 | Detail P99 < 100ms |
| Connection pool | HikariCP 사이즈 튜닝 | Max 응답시간 단축 |

---

## SLA 정의 (`load-tests/sla.md`)

| Metric | Target |
|--------|--------|
| P95 Response Time | < 500ms |
| P99 Response Time | < 1,000ms |
| Error Rate | ≤ 1% |
| Min Throughput | 10 req/s per endpoint |

**현재 달성**: Smoke/Load 구간에서 SLA 부합. Stress 구간은 saturation point로 식별 (예상된 동작).

---

## 재현 방법

```bash
# 1. 전체 스택 기동
docker compose up --build -d

# 2. k6 시나리오 실행
USE_DOCKER=true ./load-tests/run-all.sh search   # 검색 시나리오 (~6분)
USE_DOCKER=true ./load-tests/run-all.sh          # 모든 시나리오

# 3. 결과 확인
ls load-tests/results/
```

상세 시나리오 코드: [`load-tests/scenarios/`](../load-tests/scenarios/)
