# admin-web — Overview

## Purpose

운영자 전용 **웹 콘솔**. admin-service HTTP API를 소비하여 계정 관리·세션 관리·감사 조회·관측성 대시보드를 단일 SPA에서 제공한다. 내부 SRE/CS/운영자가 접속하는 사내 전용 도구이며, 일반 사용자는 이 앱의 존재조차 알 필요가 없다.

[platform/service-types/frontend-app.md](../../../platform/service-types/frontend-app.md)에 따른 Next.js App Router 기반 frontend-app. 인증·감사 흐름은 admin-service의 계약을 1:1로 미러링하고, 도메인 로직은 가지지 않는다 — **얇은 UI 레이어**.

## Users

- **SUPER_ADMIN** — 운영자 계정 관리, 역할 부여, 모든 명령 실행 가능
- **ACCOUNT_ADMIN** — 계정 lock/unlock/delete, 강제 로그아웃
- **AUDITOR** — 읽기 전용. 감사 조회·대시보드 열람만

일반 사용자 JWT와 **분리된 operator JWT**를 사용한다. 토큰 발급은 auth-service의 admin scope 엔드포인트 (별도 로그인 경로)에서 수행.

## Callees

| 대상 | 목적 | 경로 |
|---|---|---|
| `admin-service` | 모든 관리 명령(lock/unlock/revoke)·감사 조회 | gateway를 경유하는 HTTPS — `/api/admin/*` |
| Grafana (embed) | 대시보드 iframe 임베드 | 사내 Grafana URL, 운영자 SSO 별도 |

**admin-service 외 다른 백엔드 서비스를 직접 호출하지 않는다**. admin-service가 downstream 명령 게이트웨이이므로 UI 역시 같은 경계를 지킨다.

## Not This App

- ❌ **일반 사용자 기능** — 회원가입/로그인/마이페이지 없음. 공개 트래픽을 받지 않는다
- ❌ **도메인 상태** — 계정 상태, 토큰, 감사 원장은 전부 백엔드 소유. UI는 stateless
- ❌ **자동화된 의사 결정** — 자동 잠금/탐지는 security-service가 수행. UI는 조회만
- ❌ **관리자 계정 생성 플로우** — 운영자 가입·역할 부여는 SUPER_ADMIN이 auth-service와 별도 IdP로 처리 (초기에는 seed 스크립트 또는 수동)
- ❌ **외부 공개** — 사내 VPN·IP allowlist 안쪽에서만 접근

## Core Use Cases

| UC | 제목 | 역할 요건 |
|---|---|---|
| UC-W1 | 운영자 로그인 | — |
| UC-W2 | 계정 검색 (이메일/ID) | AUDITOR+ |
| UC-W3 | 계정 상세 조회 (profile, status, 최근 로그인) | AUDITOR+ |
| UC-W4 | 계정 잠금/해제 | ACCOUNT_ADMIN+ |
| UC-W5 | 계정 강제 삭제 (유예 안내) | ACCOUNT_ADMIN+ |
| UC-W6 | 활성 세션 목록 + 강제 로그아웃 | ACCOUNT_ADMIN+ |
| UC-W7 | 감사 로그 검색 (admin_actions 통합 뷰) | AUDITOR+ |
| UC-W8 | 로그인 이력 조회 (login_history) | AUDITOR+ |
| UC-W9 | 비정상 로그인 조회 (suspicious_events) | AUDITOR+ |
| UC-W10 | Grafana 대시보드 열람 (iframe) | AUDITOR+ |
| UC-W11 | 운영자 관리 (목록·역할 부여) | SUPER_ADMIN |

모든 쓰기 명령은 `X-Operator-Reason` 입력 폼을 통해 사유 수집 후 admin-service 호출. `Idempotency-Key`는 클라이언트에서 UUID v4 생성하여 전달.

## Change Drivers

1. **admin-service API 신규 명령 추가** — UI에 대응 화면/버튼 필요
2. **권한 모델 변경** — 역할별 화면 가시성 재계산
3. **감사 필드 추가** — 테이블/필터 UI 업데이트
4. **대시보드 추가** — Grafana 임베드 경로 확장

## Related Specs

- Architecture: [architecture.md](architecture.md)
- Dependencies: [dependencies.md](dependencies.md)
- Observability: [observability.md](observability.md)
- Consumed HTTP contract: [../../contracts/http/admin-api.md](../../contracts/http/admin-api.md)
- Admin backend: [../admin-service/overview.md](../admin-service/overview.md)
