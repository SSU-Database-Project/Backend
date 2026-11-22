-- =====================================================================
-- 데이터 정합성 검사. 모든 행의 bad 가 0 이어야 정상이다.
--   mysql -h 127.0.0.1 -P 3308 -u shop -pshop1234 shop < scripts/check-integrity.sql
-- DB 제약(CHECK/FK/UNIQUE)으로는 표현할 수 없는, 여러 행·테이블에 걸친 규칙을 확인한다.
-- 시연 데이터를 바꾸거나 주문·재고 로직을 고친 뒤에 돌린다.
-- =====================================================================

SELECT '01 옵션 재고 = 마지막 재고 이력의 quantity_after' AS rule_name, COUNT(*) AS bad
FROM product_options o
JOIN stock_history h ON h.id = (SELECT MAX(id) FROM stock_history WHERE option_id = o.id)
WHERE o.stock_quantity <> h.quantity_after

UNION ALL
SELECT '02 재고가 있는 옵션은 재고 이력이 있다', COUNT(*)
FROM product_options o
WHERE o.stock_quantity > 0 AND NOT EXISTS (SELECT 1 FROM stock_history h WHERE h.option_id = o.id)

UNION ALL
SELECT '03 재고 이력 누적합 = quantity_after (이력이 끊기지 않음)', COUNT(*)
FROM (SELECT h.quantity_after,
             FIRST_VALUE(h.quantity_after - h.change_qty) OVER w + SUM(h.change_qty) OVER w AS running
      FROM stock_history h
      WINDOW w AS (PARTITION BY h.option_id ORDER BY h.id ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)) x
WHERE x.quantity_after <> x.running

UNION ALL
SELECT '04 상품마다 옵션이 1개 이상', COUNT(*)
FROM products p
WHERE NOT EXISTS (SELECT 1 FROM product_options o WHERE o.product_id = p.id)

UNION ALL
SELECT '05 옵션 사이즈 = 카테고리 사이즈 체계', COUNT(*)
FROM product_options o
JOIN products p   ON p.id = o.product_id
JOIN categories c ON c.id = p.category_id
JOIN sizes s      ON s.id = o.size_id
WHERE s.size_group <> c.size_group

UNION ALL
SELECT '06 상품은 소분류에만', COUNT(*)
FROM products p JOIN categories c ON c.id = p.category_id
WHERE c.parent_id IS NULL

UNION ALL
SELECT '07 role = SELLER 이면 판매자 정보가 있다', COUNT(*)
FROM users u
WHERE u.role = 'SELLER' AND NOT EXISTS (SELECT 1 FROM sellers s WHERE s.user_id = u.id)

UNION ALL
SELECT '08 판매자 정보가 있으면 role = SELLER', COUNT(*)
FROM sellers s JOIN users u ON u.id = s.user_id
WHERE u.role <> 'SELLER'

UNION ALL
SELECT '09 주문 총액 = 주문 라인 합계', COUNT(*)
FROM (SELECT o.id, o.total_price, SUM(oi.unit_price * oi.quantity) AS line_total
      FROM orders o
      JOIN shipments sh  ON sh.order_id = o.id
      JOIN order_items oi ON oi.shipment_id = sh.id
      GROUP BY o.id, o.total_price) t
WHERE t.total_price <> t.line_total

UNION ALL
SELECT '10 주문마다 배송 묶음 1개 이상, 배송 묶음마다 라인 1개 이상', COUNT(*)
FROM (SELECT o.id FROM orders o WHERE NOT EXISTS (SELECT 1 FROM shipments sh WHERE sh.order_id = o.id)
      UNION ALL
      SELECT sh.id FROM shipments sh WHERE NOT EXISTS (SELECT 1 FROM order_items oi WHERE oi.shipment_id = sh.id)) t

UNION ALL
SELECT '11 배송 묶음의 판매자 = 담긴 옵션의 판매자', COUNT(*)
FROM order_items oi
JOIN shipments sh      ON sh.id = oi.shipment_id
JOIN product_options o ON o.id = oi.option_id
JOIN products p        ON p.id = o.product_id
JOIN brands b          ON b.id = p.brand_id
WHERE b.seller_id <> sh.seller_id

UNION ALL
SELECT '12 주문은 상품 등록 이후', COUNT(*)
FROM order_items oi
JOIN shipments sh      ON sh.id = oi.shipment_id
JOIN orders od         ON od.id = sh.order_id
JOIN product_options o ON o.id = oi.option_id
JOIN products p        ON p.id = o.product_id
WHERE od.created_at < p.created_at

UNION ALL
SELECT '13 주문 라인마다 ORDER 재고 이력 1건', COUNT(*)
FROM order_items oi
WHERE (SELECT COUNT(*) FROM stock_history h WHERE h.order_item_id = oi.id AND h.type = 'ORDER') <> 1

UNION ALL
SELECT '14 취소된 배송의 라인은 CANCEL 이력 1건, 아니면 0건', COUNT(*)
FROM order_items oi
JOIN shipments sh ON sh.id = oi.shipment_id
WHERE (SELECT COUNT(*) FROM stock_history h WHERE h.order_item_id = oi.id AND h.type = 'CANCEL')
      <> IF(sh.status = 'CANCELED', 1, 0)

UNION ALL
SELECT '15 구매확정·취소 시각은 주문 이후, 지금 이전', COUNT(*)
FROM shipments sh JOIN orders o ON o.id = sh.order_id
WHERE COALESCE(sh.confirmed_at, sh.canceled_at, o.created_at) < o.created_at
   OR COALESCE(sh.confirmed_at, sh.canceled_at, o.created_at) > NOW(6)

-- ----- 모의 결제 (V7). V7 이전 주문은 결제가 없을 수 있어, 결제가 있는 주문만 검사한다 -----
UNION ALL
SELECT '16 결제 금액 = 주문 총액', COUNT(*)
FROM payments p JOIN orders o ON o.id = p.order_id
WHERE p.amount <> o.total_price

UNION ALL
SELECT '17 결제 시각 = 주문 시각', COUNT(*)
FROM payments p JOIN orders o ON o.id = p.order_id
WHERE p.paid_at <> o.created_at

UNION ALL
SELECT '18 결제한 주문의 취소된 배송 묶음은 환불 1건, 아니면 0건', COUNT(*)
FROM shipments sh
JOIN payments p ON p.order_id = sh.order_id
WHERE (SELECT COUNT(*) FROM payment_refunds r WHERE r.shipment_id = sh.id) <> IF(sh.status = 'CANCELED', 1, 0)

UNION ALL
SELECT '19 환불 금액 = 그 배송 묶음 주문 라인 합계, 환불 시각 = 취소 시각', COUNT(*)
FROM payment_refunds r
JOIN shipments sh ON sh.id = r.shipment_id
WHERE r.amount <> (SELECT SUM(oi.unit_price * oi.quantity) FROM order_items oi WHERE oi.shipment_id = sh.id)
   OR r.refunded_at <> sh.canceled_at

UNION ALL
SELECT '20 환불은 같은 주문의 결제에 대한 것', COUNT(*)
FROM payment_refunds r
JOIN payments p   ON p.id = r.payment_id
JOIN shipments sh ON sh.id = r.shipment_id
WHERE p.order_id <> sh.order_id

UNION ALL
SELECT '21 환불 합계 <= 결제 금액', COUNT(*)
FROM payments p
WHERE p.amount < (SELECT COALESCE(SUM(r.amount), 0) FROM payment_refunds r WHERE r.payment_id = p.id);
