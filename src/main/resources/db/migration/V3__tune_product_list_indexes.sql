-- =====================================================================
-- V3: 상품 목록 인덱스 재설계 (근거: docs/BE-1_계획서.md 4.8, EXPLAIN 결과는 docs/BE-1_EXPLAIN.md)
--
-- 문제 (상품 5만 건 EXPLAIN):
--   status 는 공개 상태(ON_SALE/SOLD_OUT)가 대부분이라 선택도가 낮다.
--   그런데 인덱스 앞쪽에 status 를 두고, 쿼리는 status IN (2개 값)으로 조회하므로
--   인덱스의 정렬 순서(created_at / price)를 이어 쓰지 못해 filesort 가 발생했다.
--     - 전체 최신순 / 가격순: 풀스캔 50,000행 + filesort
--     - 카테고리 최신순: 해당 카테고리 전체를 읽은 뒤 filesort
--
-- 해결: "등호 조건 컬럼 → 정렬 컬럼" 순서로 두고, 선택도가 낮은 status 는 인덱스에서 뺀다.
--   ORDER BY ... LIMIT 20 을 인덱스 순서대로 읽다가 status 를 만족하는 20건을 찾으면 멈춘다.
--   InnoDB 보조 인덱스는 끝에 PK(id)를 포함하므로 (created_at) = 사실상 (created_at, id) 이고,
--   보조 정렬키 id 까지 인덱스 순서로 해결된다.
--
-- FK(fk_products_category)가 category_id 인덱스를 필요로 하므로 새 인덱스를 먼저 만들고 기존 것을 지운다.
-- =====================================================================

-- 카테고리 목록 최신순: WHERE category_id = ? ORDER BY created_at DESC, id DESC
CREATE INDEX idx_products_category_created ON products (category_id, created_at);
DROP INDEX idx_products_category_status_created ON products;

-- 전체 목록 최신순 (메인 화면 기본): ORDER BY created_at DESC, id DESC
CREATE INDEX idx_products_created ON products (created_at);

-- 전체 목록 가격순: ORDER BY price ASC/DESC, id ASC/DESC
CREATE INDEX idx_products_price ON products (price);
DROP INDEX idx_products_status_price ON products;
