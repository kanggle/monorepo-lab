# Feature: Password Management

## Purpose

패스워드의 생성·검증·변경·재설정 정책과 해시 전략을 정의한다. 보안 인시던트 시 긴급 변경 경로도 포함.

## Related Services

| Service | Role |
|---|---|
| auth-service | 패스워드 해시 저장·검증·변경 소유 |
| account-service | 계정 상태 확인 (변경 전 활성 여부) |

## Password Policy

| 규칙 | 값 | 설명 |
|---|---|---|
| 최소 길이 | 8자 | — |
| 최대 길이 | 128자 | 긴 패스프레이즈 허용 |
| 복잡도 | 대문자·소문자·숫자·특수문자 중 **3종 이상** | — |
| 이메일 일치 금지 | 이메일과 동일하면 거부 | — |
| 이전 패스워드 재사용 금지 | 최근 5개와 동일하면 거부 (선택, 백로그) | — |
| 사전 단어 검사 | 초기 스코프 미포함 | 백로그 |

## Hash Strategy

| 항목 | 값 |
|---|---|
| 알고리즘 | argon2id |
| memory | 65536 KB (64MB) |
| iterations | 3 |
| parallelism | 1 |
| salt | 16 bytes (랜덤) |
| output length | 32 bytes |
| 저장 형식 | `$argon2id$v=19$m=65536,t=3,p=1$<salt>$<hash>` |

**알고리즘 업그레이드 경로**: `credentials.hash_algorithm` 컬럼으로 구분. 새 알고리즘 도입 시 기존 해시는 로그인 성공 시 자동 rehash (lazy migration).

## User Flows

### 패스워드 변경

1. 인증된 사용자가 `PATCH /api/auth/password` (미래 엔드포인트) 에 현재 패스워드 + 새 패스워드 전송
2. 현재 패스워드 검증 (argon2id 비교)
3. 새 패스워드 PasswordPolicy 검증
4. 새 해시 생성 → `credentials.credential_hash` UPDATE + version 증가
5. 현재 세션은 유지, 다른 모든 세션은 revoke (선택적, 보안 강화 모드)

### 패스워드 재설정 (초기 스코프 미포함, 백로그)

1. 사용자가 `POST /api/auth/password-reset/request` 에 이메일 전송
2. 계정 존재 여부와 **관계없이** 동일 응답 (이메일 존재 여부 유출 방지)
3. 존재하면 재설정 토큰 생성 → Redis TTL 1시간 + 이메일 발송
4. 사용자가 `POST /api/auth/password-reset/confirm` 에 토큰 + 새 패스워드 전송
5. 토큰 검증 → 패스워드 변경 → 모든 세션 revoke

## Business Rules

- credential hash는 `restricted` 분류 등급 ([data-model.md](../services/auth-service/data-model.md))
- 해시 검증 시간(argon2id p99)은 1초 미만 유지 → 초과 시 파라미터 튜닝 알림
- 패스워드 평문은 **어디에도 저장·로깅·전송하지 않음** (R4)
- 변경 이력: credential hash 자체를 이력으로 보관하지 않음. `credentials.updated_at`만 갱신

## Related Contracts

- HTTP: [auth-api.md](../contracts/http/auth-api.md) (현재는 login/refresh만, 패스워드 변경은 미래 엔드포인트)
- Internal: [auth-to-account.md](../contracts/http/internal/auth-to-account.md) (상태 확인)
