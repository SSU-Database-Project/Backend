-- =====================================================================
-- 시연 주문의 결제·환불 기록 (local 프로필에서만, R__demo_orders 다음에 실행 — 이름 순서)
--
-- V7(모의 결제) 전에 만든 시연 주문 300건에도 결제 기록을 채운다 (팀 결정 2026-11-19: 시연 주문에만 채움).
--  - 결제: 주문 시각에 주문 총액으로 승인. 수단은 주문 id 에 따라 카드·계좌이체·간편결제를 돌아가며
--  - 환불: 취소된 배송 묶음마다 1건, 금액 = 그 묶음 주문 라인 합계, 시각 = 취소 시각
-- 이미 있는 기록은 건드리지 않는다 (몇 번 실행해도 결과가 같다).
-- =====================================================================

INSERT INTO payments (order_id, method, amount, card_last4, approval_no, paid_at)
SELECT o.id,
       ELT(MOD(o.id, 3) + 1, 'CARD', 'BANK_TRANSFER', 'EASY_PAY'),
       o.total_price,
       IF(MOD(o.id, 3) = 0, LPAD(MOD(o.id * 7919, 9000) + 1000, 4, '0'), NULL),
       CONCAT('DEMO-', DATE_FORMAT(o.created_at, '%Y%m%d'), '-', LPAD(o.id, 8, '0')),
       o.created_at
FROM orders o
WHERE o.address_detail LIKE '시연 주문 %'
  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id);

INSERT INTO payment_refunds (payment_id, shipment_id, amount, refunded_at)
SELECT p.id, s.id, SUM(oi.unit_price * oi.quantity), s.canceled_at
FROM shipments s
JOIN orders o       ON o.id = s.order_id
JOIN payments p     ON p.order_id = o.id
JOIN order_items oi ON oi.shipment_id = s.id
WHERE o.address_detail LIKE '시연 주문 %'
  AND s.status = 'CANCELED'
  AND NOT EXISTS (SELECT 1 FROM payment_refunds r WHERE r.shipment_id = s.id)
GROUP BY p.id, s.id, s.canceled_at;
