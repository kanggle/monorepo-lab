# Use Case: Abnormal Login Detection

---

## UC-8: 디바이스 변경 + 지리적 이례성 복합 탐지

### Actor
- 정상 사용자(탈취된 계정) 또는 공격자

### Precondition
- 계정 status=ACTIVE
- 지난 90일간 디바이스 A(Seoul)에서만 로그인
- 새 디바이스 B(New York)에서 로그인 성공

### Main Flow
1. auth-service가 `auth.login.succeeded` 이벤트를 발행한다 (geoCountry=US, deviceFingerprint=B)
2. security-service가 이벤트를 소비한다
3. **DeviceChangeRule**: Redis `security:device:seen:{account_id}`에 fingerprint B가 없음 → riskScore=50
4. **GeoAnomalyRule**: 이전 성공(KR, 2시간 전) → 현재(US) → 거리 11000km / 2시간 = 5500km/h > 900km/h → riskScore=92
5. `finalScore = max(50, 92) = 92` → ACTION = AUTO_LOCK
6. `suspicious_events` 저장 (ruleCode=GEO_ANOMALY, riskScore=92)
7. `security.suspicious.detected` + `security.auto.lock.triggered` 이벤트 발행
8. `POST /internal/accounts/{id}/lock` 호출 → account-service ACTIVE→LOCKED
9. 해당 계정 전체 세션 revoke

### Post-Condition
- 계정 LOCKED (reason=AUTO_DETECT)
- 실제 사용자는 운영자에게 문의하여 본인 확인 후 unlock 요청
- suspicious_events에 증거(geoCountry, timeDelta, distance)가 보존

---

## UC-9: 단독 디바이스 변경 (ALERT only, 잠금 안 함)

### Actor
- 정상 사용자 (새 노트북 등)

### Precondition
- 계정 status=ACTIVE
- 새 디바이스에서 로그인, 지리 위치는 동일 국가

### Main Flow
1. auth-service가 `auth.login.succeeded` 이벤트 발행 (geoCountry=KR, fingerprint=C)
2. security-service가 소비
3. **DeviceChangeRule**: fingerprint C 미등록 → riskScore=50
4. **GeoAnomalyRule**: 같은 국가 → riskScore=0
5. `finalScore = max(50, 0) = 50` → ACTION = ALERT
6. `suspicious_events` 저장 (ruleCode=DEVICE_CHANGE, action_taken=ALERT)
7. `security.suspicious.detected` 이벤트 발행 (actionTaken=ALERT)
8. 자동 잠금 **없음**
9. fingerprint C를 `security:device:seen:{account_id}` Set에 추가

### Post-Condition
- 계정 ACTIVE 유지
- suspicious_events에 기록 (향후 분석·추적 가능)
- 다음에 같은 디바이스에서 로그인하면 DeviceChangeRule 미발동

---

## UC-10: VelocityRule 단독 발동

### Actor
- 분산 IP 공격자 (credential stuffing)

### Precondition
- 동일 계정에 1시간 내 12회 실패 (임계치 10회)

### Main Flow
1. 12번째 `auth.login.failed` 이벤트 소비
2. **VelocityRule**: Redis 카운터 = 12 > 10 → riskScore = min(100, (12/10)*80) = 96
3. `finalScore = 96` → AUTO_LOCK
4. 잠금 흐름 실행 (UC-5 step 8-10과 동일)

---

## Related Contracts
- Events: [auth-events.md](../contracts/events/auth-events.md) (소비), [security-events.md](../contracts/events/security-events.md) (발행)
- Internal: [security-to-account.md](../contracts/http/internal/security-to-account.md)
- Feature: [abnormal-login-detection.md](../features/abnormal-login-detection.md)
