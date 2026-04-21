-- products 테이블에 thumbnail_url 컬럼 추가
ALTER TABLE products ADD COLUMN thumbnail_url VARCHAR(500);

-- 기존 시드 상품에 Unsplash 이미지 URL 매핑
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000001'; -- 베이직 코튼 티셔츠
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1542272604-787c3835535d?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000002'; -- 슬림핏 데님 청바지
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1610945415295-d9bbf067e59c?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000003'; -- 갤럭시 S25 울트라
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000004'; -- 맥북 프로 16인치 M4
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1556821840-3a63f95609a7?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000005'; -- 오버핏 후드 집업
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1599599810769-bcde5a160d32?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000006'; -- 프리미엄 견과류 선물세트
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1590658268037-6bf12165a8df?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000007'; -- 프로 무선 이어폰
UPDATE products SET thumbnail_url = 'https://images.unsplash.com/photo-1624378439575-d8705ad7ae80?w=600&q=80&auto=format&fit=crop' WHERE id = 'b0000000-0000-0000-0000-000000000008'; -- 와이드핏 슬랙스
