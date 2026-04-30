# TASK-BE-055: Abnormal Login Detection v2

## Goal

security-service에 ImpossibleTravelRule과 IpReputationRule 두 가지 신규 탐지 규칙을 추가하고, 관련 설정을 외부화한다.

## Scope

- `domain/detection/` 패키지에 `ImpossibleTravelRule`, `IpReputationRule` 추가
- `DetectionThresholds`에 신규 필드 추가
- `DetectionProperties`에 신규 설정 그룹 추가
- `DetectionConfig`에 `@Bean` 등록
- `LoginHistoryRepository`에 직전 성공 로그인 조회 메서드 추가
- `application.yml`에 기본값 추가
- 단위 테스트 작성

## Acceptance Criteria

- [ ] ImpossibleTravelRule이 SuspiciousActivityRule 인터페이스를 구현하고, 동일 국가 → 미발동, 다른 국가 + 시간 윈도우 내 → 발동, 다른 국가 + 시간 윈도우 외 → 미발동
- [ ] IpReputationRule이 SuspiciousActivityRule 인터페이스를 구현하고, 정상 IP → 미발동, 의심 CIDR 매칭 → 발동
- [ ] 모든 임계치가 application.yml로 외부화
- [ ] DetectionConfig에 @Bean 등록되어 자동 수집
- [ ] 단위 테스트 통과
- [ ] `./gradlew :apps:security-service:compileJava` 성공

## Related Specs

- `specs/features/abnormal-login-detection-v2.md`
- `specs/features/abnormal-login-detection.md`
- `specs/services/security-service/architecture.md`

## Related Contracts

- 기존 컨트랙트 변경 없음

## Edge Cases

- geoCountry가 null인 경우 ImpossibleTravelRule 미발동
- 직전 성공 로그인이 없는 경우 ImpossibleTravelRule 미발동
- suspicious-cidrs가 빈 리스트인 경우 IpReputationRule 미발동
- ipMasked가 null인 경우 IpReputationRule 미발동
- 잘못된 CIDR 형식은 무시 (로그 경고)

## Failure Scenarios

- LoginHistoryRepository 조회 실패 시 해당 규칙만 NONE 반환 (기존 DetectSuspiciousActivityUseCase의 try-catch에 의해 처리)
- CIDR 파싱 실패 시 해당 CIDR 항목 스킵, 로그 경고
