# gateway-service — Overview

## Purpose

Global Account Platform의 **단일 공개 진입점**. 외부 클라이언트(브라우저, 모바일 앱, 3rd-party 통합)가 오는 모든 HTTP 트래픽을 받아 인증 검증·rate limit·요청 ID 주입·다운스트림 라우팅을 수행한다. 비즈니스 로직을 수행하지 않고, 정책 집행(policy enforcement)에만 집중한다.

## Callers

- **외부 사용자 클라이언트** — 웹·모바일·OAuth 콜백 등 공개 네트워크에서 오는 트래픽
- **3rd-party 통합** (선택) — 미래에 API 키 인증을 통한 외부 통합이 추가될 수 있음
- **없음**: 내부 서비스 간 호출은 gateway를 거치지 않고 내부 HTTP 경로를 사용 ([rules/domains/saas.md](../../../rules/domains/saas.md) S2)

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| `auth-service` | JWKS 조회(토큰 검증 키) | 내부 HTTP `GET /internal/auth/jwks` |
| `auth-service` | 로그인/로그아웃/refresh 공개 API 전달 | 퍼블릭 `/api/auth/*` 라우팅 |
| `account-service` | 회원가입/프로필 공개 API 전달 | 퍼블릭 `/api/accounts/*` 라우팅 |
| `admin-service` | 운영자 작업 공개 API 전달 | 퍼블릭 `/api/admin/*` 라우팅 (별도 인증 필터 적용) |
| Redis | Rate limit 토큰 버킷, JWKS 캐시 | 직접 접속 |

## Owned State

- **DB 없음**. 영속 도메인 데이터를 소유하지 않는다
- **Redis만**:
  - `jwks:cache` — auth-service에서 페치한 서명 키 세트, TTL 10분
  - `ratelimit:{scope}:{identifier}:{window}` — 토큰 버킷 카운터, TTL = window 길이
  - `request:dedup:{requestId}` (선택) — 재전송 방어용 일시 dedup

## Change Drivers

이 서비스가 바뀌는 전형적인 이유:

1. **새 업스트림 추가** — 새 서비스가 퍼블릭 API를 제공하기 시작
2. **인증 정책 변경** — JWT claim 요구사항, 만료 정책, 2FA 도입
3. **CORS 허용 목록 변경** — 새 프론트엔드 도메인 추가
4. **Rate limit 튜닝** — 트래픽 패턴 변화에 따른 버킷 크기 조정
5. **관측성 요구** — 새 메트릭 라벨, 요청 ID 포맷 변경

## Not This Service

명시적으로 gateway의 책임이 **아닌** 것들:

- ❌ 비즈니스 데이터 검증 (다운스트림이 판단)
- ❌ 권한(authorization) 결정 (각 서비스가 자신의 권한 정책 소유)
- ❌ 에러 메시지 해석 (투명 전달 또는 표준 포맷 감쌈만)
- ❌ 응답 캐싱 (GET 응답 캐시는 다운스트림 또는 CDN 레이어)
- ❌ 세션 상태 저장 (auth-service가 JWT·refresh token으로 관리)
- ❌ 사용자 대면 UI 또는 정적 파일 서빙

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- Redis keys: [redis-keys.md](redis-keys.md)
- HTTP contract: [../../contracts/http/gateway-api.md](../../contracts/http/)
