# Screenshots

이 디렉토리는 포트폴리오용 실행 화면 스크린샷을 보관합니다.

## 캡처 가이드

### 사전 준비

```bash
# 전체 스택 기동
docker compose up --build -d

# 기동 대기 (약 1~2분)
docker compose ps | grep "healthy\|Up"
```

주요 URL:
- Web Store: http://localhost:3000
- Admin Dashboard: http://localhost:3001
- Gateway: http://localhost:8080
- Grafana: http://localhost:3100 (admin/admin)
- Jaeger: http://localhost:16686

### 캡처 목록

| # | 파일명 | URL | 권장 내용 |
|---|--------|-----|----------|
| 1 | `01-home.png` | http://localhost:3000/ | 홈 화면 — 상품 그리드 (Unsplash 실제 이미지) |
| 2 | `02-product-detail.png` | http://localhost:3000/products/b0000000-0000-0000-0000-000000000004 | 맥북 상품 상세 (옵션/리뷰 포함) |
| 3 | `03-checkout.png` | http://localhost:3000/checkout | 체크아웃 페이지 (장바구니 → 결제 진입) |
| 4 | `04-my-orders.png` | http://localhost:3000/my/orders | 주문 내역 (상태 추적) |
| 7 | `07-grafana.png` | http://localhost:3100/ | Grafana 대시보드 (서비스 메트릭) |

### 캡처 규격

- **해상도**: 1440 x 900 이상 (브라우저 창)
- **포맷**: PNG
- **파일 크기**: 가능하면 500KB 이하 (PNG 최적화)
- **개인정보**: 실제 이메일/전화번호가 노출되지 않도록 주의

### 이미지 최적화 (선택)

```bash
# TinyPNG CLI 또는 온라인 도구 활용 권장
# 또는 ImageMagick:
magick input.png -quality 85 -resize 1600x output.png
```

### 캡처 완료 후

`README.md`의 Screenshots 섹션이 이 파일들을 자동 참조하도록 이미 설정되어 있습니다. 파일을 이 디렉토리에 저장만 하면 됩니다.
