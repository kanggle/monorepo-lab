# Feature: Abnormal Login Detection v2

## Purpose

기존 비정상 로그인 탐지 시스템(VelocityRule, GeoAnomalyRule, DeviceChangeRule, TokenReuseRule)을 확장하여 두 가지 신규 탐지 규칙을 추가한다.

## Related Services

| Service | Role |
|---|---|
| security-service | 신규 탐지 규칙 실행. 기존 전략 패턴(`SuspiciousActivityRule`) 확장 |

## New Detection Rules

### ImpossibleTravelRule
- **조건**: 연속된 성공 로그인에서 geoCountry가 다르고, 두 로그인 간 시간 간격이 설정된 임계치(기본 1시간) 미만인 경우
- **데이터**: `LoginHistoryRepository`에서 해당 계정의 직전 성공 로그인 조회
- **Risk score**: 고정 70 (ALERT 수준, 단독으로는 AUTO_LOCK 미발동)
- **설계 근거**: GeoAnomalyRule은 GeoIP DB 기반 좌표·속도 계산을 수행하지만, ImpossibleTravelRule은 GeoIP 없이도 국가 코드 비교만으로 탐지 가능한 경량 보완 규칙. 두 규칙은 독립적으로 평가되며, 동시 발동 시 max aggregation으로 더 높은 score가 채택됨.

### IpReputationRule
- **조건**: 로그인 IP가 설정된 의심 IP 대역(CIDR) 목록에 포함되는 경우
- **데이터**: `security.detection.ip-reputation.suspicious-cidrs` 설정 값 (application.yml)
- **Risk score**: 고정 60 (ALERT 수준)
- **설계 근거**: 외부 위협 인텔리전스 API 없이도 알려진 Tor/VPN/프록시 대역을 차단할 수 있는 경량 휴리스틱. 포트폴리오 목적으로 구현의 단순성 우선.

## Threshold Externalization

모든 신규 임계치는 기존 `DetectionThresholds` + `DetectionProperties` 패턴을 따라 `@ConfigurationProperties`로 주입. application.yml에 기본값 포함.

| 설정 키 | 기본값 | 설명 |
|---|---|---|
| `security.detection.impossible-travel.time-window-seconds` | 3600 | 국가 변경 판정 시간 윈도우 |
| `security.detection.impossible-travel.score` | 70 | 발동 시 고정 risk score |
| `security.detection.ip-reputation.suspicious-cidrs` | (빈 리스트) | 의심 IP CIDR 목록 |
| `security.detection.ip-reputation.score` | 60 | 발동 시 고정 risk score |

## Business Rules

- 기존 규칙과 동일하게 `SuspiciousActivityRule` 인터페이스 구현
- 규칙 파라미터는 코드 하드코딩 금지, 설정으로 주입
- `DetectionConfig`에 `@Bean` 등록으로 자동 수집 (use-case 변경 불필요)
- 기본 suspicious-cidrs가 빈 리스트이므로 IpReputationRule은 기본 상태에서 발동하지 않음 (false positive 방지)

## Related Contracts

- 기존 컨트랙트 변경 없음. 신규 규칙은 기존 `suspicious.detected` 이벤트로 발행.
