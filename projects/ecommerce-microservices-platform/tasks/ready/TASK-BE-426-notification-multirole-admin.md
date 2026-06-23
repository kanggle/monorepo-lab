# TASK-BE-426 — notification-service 템플릿 admin 게이트가 멀티 role(콤마조인) X-User-Role 을 거부하는 버그

- **Status**: ready
- **Project**: ecommerce-microservices-platform
- **Service**: notification-service
- **Analysis model**: Opus 4.8 / **Implementation model**: Opus 4.8 (authz 버그픽스)

## Goal

콘솔 E-Commerce > 알림 템플릿(`/api/notifications/templates`) 조회/생성/수정이, **둘 이상의 role 을 가진 운영자**(예: ecommerce + wms 동시 entitled → `roles=["ADMIN","WMS_OPERATOR"]`)에게 **403 ACCESS_DENIED** 로 거부되는 버그를 고친다.

근본 원인: API 게이트웨이(`gateway-service` 의 `JwtHeaderEnrichmentFilter`, ADR-MONO-035 4b-2a)는 토큰 `roles` 클레임을 **콤마로 조인**해 `X-User-Role` 헤더로 전달한다(예: `"ADMIN,WMS_OPERATOR"`). 그런데 `TemplateController.validateAdminRole` 이 `ADMIN_ROLE.equals(userRole)` 로 **정확일치** 검사를 하여, role 이 하나(`"ADMIN"`)일 때만 통과하고 멀티 role 조인 문자열은 거부한다. 단일 role 운영자에서는 우연히 동작하던 잠복 버그.

## Scope

**In scope** (notification-service only):

1. `src/main/java/.../adapter/in/rest/TemplateController.java` — `validateAdminRole` 을 콤마조인 `X-User-Role` 에서 `ADMIN` **멤버십**을 검사하도록 변경(`split(",")` 후 trim 비교). 단일 role/누락/USER 케이스 동작은 보존.
2. `src/test/java/.../TemplateControllerTest.java` — 멀티 role(`"ADMIN,WMS_OPERATOR"`) → 200 회귀 테스트 추가.

**Out of scope**: 게이트웨이 헤더 형식 변경(콤마조인은 의도된 계약), 다른 서비스의 X-User-Role 처리, role 파생 로직(auth-service `OperatorRoleDerivation`).

## Acceptance Criteria

- **AC-1 — 멀티 role 통과.** `X-User-Role: "ADMIN,WMS_OPERATOR"` 로 `GET/POST/PUT /api/notifications/templates` 호출 시 admin 게이트 통과(정상 처리).
- **AC-2 — 거부 동작 보존.** `X-User-Role` 누락 → 403, `"USER"`(또는 ADMIN 미포함) → 403 (ACCESS_DENIED) 유지.
- **AC-3 — 단일 role 회귀 없음.** `X-User-Role: "ADMIN"` → 종전대로 통과.
- **AC-4 — 게이트.** notification-service `:test` GREEN(기존 + 신규 멀티role 테스트). Docker-free `:check` 통과(본 변경은 wiring 무관 순수 컨트롤러 로직).

## Related Specs

- ADR-MONO-035 (4b-2a) — 게이트웨이 verified identity 헤더(`X-User-Role` ← roles 콤마조인).

## Related Contracts

- gateway-service `JwtHeaderEnrichmentFilter` — `X-User-Role` = `roles` 배열 콤마조인(또는 `role` 문자열). notification-service 는 이 형식을 소비하는 쪽.

## Edge Cases

- 공백 포함 조인(`"ADMIN, WMS_OPERATOR"`) 대비 `trim()` 적용.
- 빈 문자열/`null` X-User-Role → false(403) — 게이트웨이가 role 클레임 부재 시 `""` 를 보내는 계약과 정합.
- ADMIN 이 아닌 단일/멀티 role(예: `"WMS_OPERATOR"`, `"SCM_OPERATOR,ERP_OPERATOR"`) → 여전히 403(최소권한).

## Failure Scenarios

- 멤버십 검사가 부분문자열(`contains`)로 잘못 구현되면 `"SUPERADMIN"` 같은 값이 오통과할 수 있음 → 반드시 `split(",")` + 토큰 단위 `equals` 로 구현(부분일치 금지). 본 구현은 토큰 equals 라 안전.
