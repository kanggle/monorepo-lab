# 모니터링 알림 규칙

AlertManager를 통한 운영 이상 상황 자동 감지 알림 정책을 정의한다.

---

## 아키텍처

```
서비스 → Prometheus (메트릭 수집 + 알림 규칙 평가) → AlertManager (알림 라우팅) → 알림 채널
                                                           ↓
                                                     Grafana (알림 상태 시각화)
```

- **Prometheus**: 15초 간격으로 메트릭 수집, 알림 규칙 평가
- **AlertManager**: 알림 그룹핑, 중복 제거, 라우팅 처리
- **Grafana**: Alert Overview 대시보드에서 알림 상태 실시간 확인

---

## 알림 카테고리

### 1. 높은 에러율 (5xx 비율)

| 알림명 | 조건 | 지속시간 | 심각도 |
|---|---|---|---|
| HighErrorRate | 5xx 비율 > 5% | 2분 | CRITICAL |
| ElevatedErrorRate | 5xx 비율 > 1% | 5분 | WARNING |

### 2. 느린 응답 시간

| 알림명 | 조건 | 지속시간 | 심각도 |
|---|---|---|---|
| CriticalLatency | P99 > 5초 | 2분 | CRITICAL |
| HighP99Latency | P99 > 2초 | 5분 | WARNING |
| HighP95Latency | P95 > 1초 | 5분 | WARNING |

### 3. 서비스 다운

| 알림명 | 조건 | 지속시간 | 심각도 |
|---|---|---|---|
| ServiceDown | up == 0 | 1분 | CRITICAL |
| ServiceHealthCheckFailing | Spring Boot 헬스체크 != UP | 2분 | CRITICAL |

### 4. Kafka 소비 지연

| 알림명 | 조건 | 지속시간 | 심각도 |
|---|---|---|---|
| KafkaConsumerLagCritical | consumer lag > 50,000 | 5분 | CRITICAL |
| KafkaConsumerLagHigh | consumer lag > 10,000 | 5분 | WARNING |
| KafkaConsumerNotConsuming | 소비율 0 + lag 존재 | 10분 | CRITICAL |

### 5. DB 커넥션 풀 고갈

| 알림명 | 조건 | 지속시간 | 심각도 |
|---|---|---|---|
| DBConnectionPoolExhausted | 사용률 > 95% | 2분 | CRITICAL |
| DBConnectionPoolNearExhaustion | 사용률 > 80% | 5분 | WARNING |
| DBConnectionPoolPendingHigh | 대기 요청 > 5 | 2분 | WARNING |

---

## 알림 라우팅 정책

- **그룹핑**: `alertname` + `service` 기준으로 그룹화
- **CRITICAL**: 그룹 대기 10초, 반복 주기 1시간
- **WARNING**: 그룹 대기 30초, 반복 주기 4시간
- **억제 규칙**: 동일 서비스/알림에 대해 CRITICAL이 발생하면 WARNING 억제

---

## 알림 노이즈 방지

- 모든 알림에 `for` 지속시간 설정 (1~10분)으로 일시적 스파이크 무시
- 서비스 재시작 시 일시적 알림 발생은 `for` 절이 자연 억제
- inhibit_rules로 CRITICAL/WARNING 중복 알림 방지

---

## 외부 알림 채널 연동

AlertManager 설정 파일(`infra/alertmanager/alertmanager.yml`)에서 receiver를 구성하여 연동한다.

지원 채널 (설정 포인트 제공):
- Slack (`slack_configs`)
- PagerDuty (`pagerduty_configs`)
- Email (`email_configs`)
- Webhook (`webhook_configs`)

---

## 파일 구조

```
infra/
├── alertmanager/
│   └── alertmanager.yml          # AlertManager 설정
├── prometheus/
│   ├── prometheus.yml            # Prometheus 설정 (alerting 섹션 포함)
│   └── alert-rules.yml           # 알림 규칙 정의
└── grafana/
    ├── dashboards/
    │   └── alerts.json           # Alert Overview 대시보드
    └── provisioning/
        └── datasources/
            └── alertmanager.yml  # AlertManager 데이터소스
```

---

## 접근 방법

| 도구 | URL | 용도 |
|---|---|---|
| AlertManager | http://localhost:9094 | 알림 상태 확인, 음소거 설정 |
| Prometheus Alerts | http://localhost:9090/alerts | 알림 규칙 평가 상태 확인 |
| Grafana Alert Dashboard | http://localhost:3100 → Alert Overview | 알림 시각화 대시보드 |
