# 프론트엔드 ↔ 백엔드 통합 검증 가이드

이 문서는 docker compose 환경에서 web-store가 gateway-service를 통해 백엔드 API와 정상 연동되는지 수동 검증하는 절차를 정의한다. (운영자 UI 는 platform-console(hub)로 흡수 — ADR-MONO-031 Phase 6.)

---

## 사전 준비

### 1. 환경 변수 설정

`.env` 파일을 프로젝트 루트에 생성한다:

```env
JWT_SECRET=your-secret-key-must-be-at-least-32-characters-long
```

### 2. 빌드 및 실행

```bash
./gradlew bootJar
docker-compose up --build -d
```

### 3. 서비스 기동 확인

모든 서비스가 healthy 상태인지 확인한다:

```bash
docker-compose ps
```

| 서비스 | 포트 | 헬스체크 URL |
|---|---|---|
| gateway-service | 8080 | http://localhost:8080/actuator/health |
| auth-service | 8081 | http://localhost:8081/actuator/health |
| product-service | 8082 | http://localhost:8082/actuator/health |
| user-service | 8084 | http://localhost:8084/actuator/health |
| search-service | 8085 | http://localhost:8085/actuator/health |
| order-service | 8086 | http://localhost:8086/actuator/health |
| payment-service | 8087 | http://localhost:8087/actuator/health |
| web-store | 3000 | http://localhost:3000/ |

---

## 인프라 검증

### NEXT_PUBLIC_API_URL 확인

프론트엔드 빌드 시 `NEXT_PUBLIC_API_URL=http://localhost:8080`이 주입된다.
브라우저 개발자도구 > Network 탭에서 API 요청이 `http://localhost:8080`으로 나가는지 확인한다.

**주의:** `NEXT_PUBLIC_*` 변수는 빌드 시점에 클라이언트 JS에 인라인된다. Docker 내부 주소(`gateway-service:8080`)가 아닌 호스트 접근 가능한 주소(`localhost:8080`)를 사용해야 한다.

### CORS 정책 확인

gateway-service는 다음 오리진을 허용한다:
- `http://localhost:3000` (web-store)

검증 방법:
1. 브라우저에서 web-store(http://localhost:3000) 접속
2. 개발자도구 > Console 탭에서 CORS 에러가 없는지 확인
3. Network 탭에서 Preflight(OPTIONS) 요청이 200으로 응답하는지 확인

### Gateway 라우팅 확인

```bash
# 공개 API 테스트 (인증 불필요)
curl http://localhost:8080/api/products
curl http://localhost:8080/api/search/products?keyword=test

# 인증 필요 API 테스트 (401 응답 예상)
curl -v http://localhost:8080/api/orders
curl -v http://localhost:8080/api/users/me
```

---

## web-store 연동 검증

### 1. 상품 목록 페이지

- [ ] http://localhost:3000 접속
- [ ] 상품 목록이 실제 API 데이터로 렌더링되는지 확인
- [ ] Network 탭에서 `GET /api/products` 요청이 gateway(8080)를 통해 나가는지 확인
- [ ] 상품 상세 페이지 클릭 시 `GET /api/products/{id}` 호출 확인

### 2. 로그인/회원가입 플로우

- [ ] 회원가입 페이지에서 정상 가입 (POST /api/auth/signup)
- [ ] 로그인 페이지에서 정상 로그인 (POST /api/auth/login)
- [ ] 로그인 후 accessToken, refreshToken이 localStorage에 저장되는지 확인
- [ ] 로그인 후 인증 필요 API 호출 시 Authorization 헤더에 Bearer 토큰이 포함되는지 확인

### 3. 주문 생성 플로우

- [ ] 로그인 상태에서 주문 생성 (POST /api/orders)
- [ ] 주문 목록 조회 (GET /api/orders)
- [ ] 주문 상세 조회 (GET /api/orders/{id})
- [ ] 주문 취소 (POST /api/orders/{id}/cancel)

### 4. API 에러 핸들링

- [ ] 잘못된 요청 시 에러 메시지가 UI에 표시되는지 확인
- [ ] 토큰 만료 시 자동 갱신(refresh)이 동작하는지 확인
  - accessToken 만료 → 401 응답 → POST /api/auth/refresh → 새 토큰으로 재시도
- [ ] refresh 토큰도 만료 시 로그인 페이지로 리다이렉트되는지 확인

---

> 운영자(operator) 연동 검증은 통합 콘솔(platform-console, hub)로 이관되었다 (ADR-MONO-031 Phase 6). 독립 admin-dashboard 앱은 제거되었다.

---

## Edge Cases 검증

### 서비스 기동 중 접속

- [ ] gateway-service가 아직 기동 중일 때 프론트엔드 접속 시 적절한 에러 메시지 표시
- [ ] 개별 백엔드 서비스 하나를 중지 후 프론트엔드에서 해당 기능 사용 시 에러 표시 확인

```bash
# 예: product-service 중지
docker-compose stop product-service
# web-store에서 상품 목록 접근 시 에러 메시지 확인
# 복구
docker-compose start product-service
```

### 토큰 만료 시나리오

- [ ] accessToken이 만료된 상태에서 API 호출 시 자동 갱신 동작
- [ ] refreshToken도 만료된 상태에서 API 호출 시 로그인 페이지로 리다이렉트

---

## 문제 해결 (Troubleshooting)

### CORS 에러 발생 시

gateway-service의 CORS 설정을 확인한다:
- 파일: `apps/gateway-service/src/main/resources/application.yml`
- `allowed-origins`에 프론트엔드 오리진이 포함되어 있는지 확인
- `allow-credentials: true`가 설정되어 있는지 확인

### API 요청이 gateway에 도달하지 않을 때

1. `NEXT_PUBLIC_API_URL`이 `http://localhost:8080`인지 확인 (Docker 내부 주소가 아닌지)
2. gateway-service가 healthy 상태인지 확인
3. 브라우저 개발자도구 Network 탭에서 요청 URL 확인

### 인증 실패 시

1. `JWT_SECRET` 환경변수가 auth-service와 gateway-service에 동일하게 설정되었는지 확인
2. 토큰이 localStorage에 정상 저장되었는지 확인
3. Authorization 헤더 형식이 `Bearer {token}`인지 확인

### 서비스 간 통신 실패 시

```bash
# 서비스 로그 확인
docker-compose logs -f gateway-service
docker-compose logs -f auth-service

# 특정 서비스 재시작
docker-compose restart gateway-service
```

---

## 포트 맵핑 요약

| 구분 | 서비스 | 호스트 포트 |
|---|---|---|
| Frontend | web-store | 3000 |
| Gateway | gateway-service | 8080 |
| Backend | auth-service | 8081 |
| Backend | product-service | 8082 |
| Backend | user-service | 8084 |
| Backend | search-service | 8085 |
| Backend | order-service | 8086 |
| Backend | payment-service | 8087 |
| Backend | batch-worker | 8088 |
| DB | auth-postgres | 5432 |
| DB | product-postgres | 5433 |
| DB | order-postgres | 5434 |
| DB | payment-postgres | 5435 |
| DB | batch-postgres | 5436 |
| DB | user-postgres | 5437 |
| Infra | Redis | 6379 |
| Infra | Elasticsearch | 9200 |
| Infra | Kafka | 9093 |
| Infra | Jaeger UI | 16686 |
| Infra | Prometheus | 9090 |
