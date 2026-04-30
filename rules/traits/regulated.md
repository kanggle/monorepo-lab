# Trait: regulated

> **Activated when**: `PROJECT.md` includes `regulated` in `traits:`.

---

## Scope

법적·규제적 요구(GDPR, PIPA, HIPAA, PCI-DSS, SOX 등)가 적용되거나, 개인식별정보(PII)·민감 데이터를 다루는 시스템에 적용된다. 포트폴리오 수준에서는 실제 인증 절차를 수행하지 않더라도 **구조적으로 준수 가능한 설계**를 강제한다.

적용 대상 데이터 예시:
- PII (이름, 이메일, 전화, 주소, 생년월일, IP, 디바이스 ID)
- Sensitive PII (패스워드 해시, 2FA 시크릿, 신분증 번호, 생체 정보)
- 금융 정보 (카드 번호, 계좌 정보 — 직접 저장 금지가 원칙)
- 건강 정보, 위치 정보 (해당하는 경우)

---

## Mandatory Rules

### R1. 데이터 분류(Data Classification) 명시
모든 저장 데이터는 **분류 등급**을 가진다: `public` / `internal` / `confidential` / `restricted`. 엔터티·컬럼 수준에서 분류가 문서화되어야 하며, 코드 주석 또는 전용 메타데이터 파일에 기록한다.

### R2. 저장 시 암호화 (Encryption at Rest)
`confidential` 이상 등급 데이터는 저장 시 암호화되어야 한다. 최소한:
- 패스워드: **salted hash** (bcrypt/argon2, 평문 저장 금지)
- 2FA 시크릿, OAuth refresh token: 대칭 암호화 후 저장
- PII 중 검색이 불필요한 필드: 전용 암호화 컬럼

DB 레벨 TDE는 하한이지, 상한이 아니다. 애플리케이션 레벨 암호화를 우선한다.

### R3. 전송 시 암호화 (Encryption in Transit)
서비스 간·외부 통신 모두 TLS 1.2 이상. HTTP 평문 통신 금지. 내부 네트워크라도 예외 없음.

### R4. 로그·이벤트·에러 메시지에서 PII 마스킹
로그 출력, 이벤트 페이로드, 에러 응답에 **원본 PII가 그대로 나타나면 안 된다**:
- 이메일: `j***@example.com`
- 전화: `010-****-1234`
- 이름: 첫 글자 + `*`
- 토큰·시크릿: 절대 출력 금지 (설령 디버그 레벨이라도)

마스킹은 중앙화된 유틸리티로 강제한다. 개별 로그 호출마다 마스킹 여부를 결정하지 않는다.

### R5. 접근 감사 (Access Audit)
`restricted` 등급 데이터에 대한 **읽기·쓰기 모두** 감사 로그에 기록한다. 누가(operator/service), 언제, 어떤 리소스에, 어떤 이유로 접근했는지. (`audit-heavy` trait과 교차)

### R6. 보존 기간 명시 (Retention Policy)
모든 PII·민감 데이터는 **보존 기간**을 문서에 명시한다. 기간 경과 후 자동 삭제 또는 익명화되는 경로가 존재해야 한다. "영구 보관"은 명시적 근거 없이 금지.

### R7. 삭제 권리 (Right to Erasure / Right to be Forgotten)
사용자의 삭제 요청은 수용 가능한 경로로 구현되어야 한다:
- 즉시 물리 삭제가 아닌 **논리 삭제 + 유예 기간 + 익명화**
- 유예 중 복구 가능
- 유예 종료 후 PII 필드는 복구 불가능하게 익명화(해시·NULL·토큰화)
- 삭제 완료 이벤트를 발행하여 다운스트림 서비스가 동기화

### R8. 데이터 이식성 (Right to Portability)
사용자 요청 시 본인 데이터를 **기계 가독 포맷(JSON 등)** 으로 내보낼 수 있는 경로를 제공한다. 포트폴리오 수준에서는 설계·스펙 수준으로 명시하고 실제 엔드포인트는 선택.

### R9. 비밀(Secrets) 관리
API 키·DB 비밀번호·JWT 서명 키·암호화 키는 소스 코드·환경 변수 평문에 포함 금지. 최소한 환경 분리 + `.env` gitignore. 운영은 Secret Manager/Vault 전제.

키 회전(rotation) 경로가 설계되어야 한다. "영구 키"는 금지.

### R10. 최소 수집 원칙 (Data Minimization)
서비스 운영에 불필요한 PII는 수집·저장하지 않는다. 수집되는 필드마다 "왜 필요한가"를 문서화할 수 있어야 한다. "나중에 쓸지도 모르니까"는 정당화가 아님.

---

## Forbidden Patterns

- ❌ **로그에 평문 이메일/전화/토큰 출력**
- ❌ **에러 응답에 PII 포함** (예: "이메일 `user@x.com`이 존재하지 않습니다")
- ❌ **패스워드 평문 저장** 또는 해시 없는 저장
- ❌ **삭제 요청이 즉시 물리 삭제로 수행됨**
- ❌ **비밀 값이 git 이력에 커밋됨**
- ❌ **"관리자라서" 감사 로그 없이 민감 데이터 조회**
- ❌ **서비스 간 내부 호출을 HTTP 평문으로**
- ❌ **보존 기간 없이 무기한 쌓이는 PII 로그·이벤트**

---

## Required Artifacts

1. **데이터 분류 표** — 엔터티·필드별 분류 등급. 위치: `specs/services/<service>/data-model.md` 또는 전용 `data-classification.md`
2. **PII 마스킹 유틸** — 중앙화된 마스킹 함수 + 적용 대상 로그 포맷
3. **보존 기간 표** — 각 데이터 종류별 보존 기간 + 삭제·익명화 절차. 위치: `specs/services/<service>/retention.md`
4. **삭제/익명화 절차 문서** — 유예 기간, 익명화 대상 필드, 복구 가능성, 이벤트 발행
5. **비밀 관리 정책** — 저장 위치, 접근 경로, 회전 주기. 위치: `platform/security-rules.md` 참조
6. **감사 로그 스키마** — (`audit-heavy` trait과 공동 소유)

---

## Interaction with Common Rules

- [../../platform/security-rules.md](../../platform/security-rules.md)의 비밀·TLS·접근 제어 규칙을 전부 수용. 이 trait은 그 위에 PII 관점을 추가한다.
- [../../platform/error-handling.md](../../platform/error-handling.md)의 에러 응답 포맷이 PII를 포함하지 않도록 검증.
- [../../platform/observability.md](../../platform/observability.md)의 로그·메트릭·트레이스 파이프라인에 마스킹이 적용되어야 한다.
- [./audit-heavy.md](./audit-heavy.md)와 함께 선언되는 경우가 일반적. 감사 로그 요구는 audit-heavy가 상세 규정.
- 도메인 [../domains/saas.md](../domains/saas.md)의 S6(삭제 유예+익명화)과 공동 소유.

---

## Checklist (Review Gate)

- [ ] 모든 저장 데이터에 분류 등급이 명시되어 있는가? (R1)
- [ ] `confidential`/`restricted` 데이터가 저장 시 암호화되는가? (R2)
- [ ] 모든 통신이 TLS인가? (R3)
- [ ] 로그·이벤트·에러 메시지에서 PII가 마스킹되는가? (R4)
- [ ] `restricted` 데이터 접근이 감사 로그에 기록되는가? (R5)
- [ ] 각 데이터 종류의 보존 기간이 문서화되어 있는가? (R6)
- [ ] 삭제 요청이 유예 + 익명화 경로로 처리되는가? (R7)
- [ ] 사용자 데이터 이식성 경로가 설계되어 있는가? (R8)
- [ ] 비밀 값이 소스 코드·커밋 이력에 없는가? 회전 경로가 있는가? (R9)
- [ ] 수집되는 PII 필드마다 정당화 근거가 있는가? (R10)
- [ ] 금지 패턴(평문 로그, 즉시 삭제, 평문 저장 등)이 코드베이스에 없는가?
