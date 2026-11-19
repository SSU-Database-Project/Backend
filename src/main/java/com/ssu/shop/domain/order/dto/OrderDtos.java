package com.ssu.shop.domain.order.dto;

import com.ssu.shop.domain.order.entity.Order;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.payment.entity.Payment;
import com.ssu.shop.domain.payment.entity.PaymentMethod;
import com.ssu.shop.domain.payment.entity.PaymentRefund;
import com.ssu.shop.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderDtos {

    private OrderDtos() {
    }

    /**
     * 주문 요청 = 배송지. 주문할 상품은 따로 보내지 않는다 — 장바구니에서 체크한 항목이 주문된다.
     * 연락처는 가입과 같은 형식(010-1234-5678 또는 01012345678), 우편번호는 숫자 5자리.
     */
    public record OrderCreateRequest(
            @NotBlank @Size(max = 50) String receiverName,
            @NotBlank @Pattern(regexp = "^01[0-9]-?\\d{3,4}-?\\d{4}$", message = "휴대폰 번호 형식이 아닙니다.")
            String receiverPhone,
            @NotBlank @Pattern(regexp = "^\\d{5}$", message = "우편번호는 숫자 5자리여야 합니다.") String zipCode,
            @NotBlank @Size(max = 255) String address,
            @Size(max = 255) String addressDetail,
            @NotNull @Valid PaymentRequest payment) {
    }

    /**
     * 결제 정보 (모의 결제, V7). 금액은 보내지 않는다 — 서버가 주문 총액으로 승인한다.
     * cardNumber: 카드(CARD)일 때만, 숫자 16자리 (하이픈·공백 허용). 끝 4자리만 저장한다.
     * 시연용 거절: 카드번호가 0000 으로 끝나거나, 금액이 100만 원을 넘으면 402 PAYMENT_DECLINED.
     */
    public record PaymentRequest(
            @NotNull @Schema(allowableValues = {"CARD", "BANK_TRANSFER", "EASY_PAY"}) PaymentMethod method,
            @Schema(example = "1234-5678-9012-3456") String cardNumber) {

        @AssertTrue(message = "카드 결제는 카드번호 16자리가 필요하고, 다른 결제수단에는 카드번호를 보내지 않습니다.")
        @Schema(hidden = true)
        public boolean isCardNumberValid() {
            if (method == null) {
                return true; // @NotNull 이 따로 알린다
            }
            if (method != PaymentMethod.CARD) {
                return cardNumber == null || cardNumber.isBlank();
            }
            return cardNumber != null && cardNumber.matches("^\\d{4}([- ]?\\d{4}){3}$");
        }

        /** 숫자만 16자리 (카드가 아니면 null) */
        public String cardDigits() {
            return method == PaymentMethod.CARD ? cardNumber.replaceAll("[- ]", "") : null;
        }
    }

    /** 환불 1건 = 취소한 배송 묶음 1개 */
    public record RefundResponse(Long shipmentId, long amount, LocalDateTime refundedAt) {

        public static RefundResponse from(PaymentRefund refund) {
            return new RefundResponse(refund.getShipment().getId(), refund.getAmount(), refund.getRefundedAt());
        }
    }

    /**
     * 결제 정보. amount = 결제 금액(주문 총액), refundedAmount = 환불 합계, balance = 남은 결제 금액.
     * cardNumberMasked 는 카드일 때만 (****-****-****-1234).
     */
    public record PaymentResponse(
            Long paymentId,
            PaymentMethod method,
            long amount,
            String cardNumberMasked,
            String approvalNo,
            LocalDateTime paidAt,
            long refundedAmount,
            long balance,
            List<RefundResponse> refunds) {

        public static PaymentResponse of(Payment payment, List<PaymentRefund> refunds) {
            long refunded = refunds.stream().mapToLong(PaymentRefund::getAmount).sum();
            String masked = payment.getCardLast4() == null ? null : "****-****-****-" + payment.getCardLast4();
            return new PaymentResponse(payment.getId(), payment.getMethod(), payment.getAmount(), masked,
                    payment.getApprovalNo(), payment.getPaidAt(), refunded, payment.getAmount() - refunded,
                    refunds.stream().map(RefundResponse::from).toList());
        }
    }

    /** 주문 줄. 상품명·색상·사이즈·단가는 주문 당시 값이고, productId·imageUrl 은 상품 화면으로 가기 위한 현재 값이다. */
    public record OrderItemResponse(
            Long orderItemId,
            Long optionId,
            Long productId,
            String productName,
            String colorName,
            String sizeLabel,
            String imageUrl,
            int unitPrice,
            int quantity,
            long lineTotal) {

        public static OrderItemResponse from(OrderItem item) {
            Product product = item.getOption().getProduct();
            return new OrderItemResponse(item.getId(), item.getOption().getId(), product.getId(),
                    item.getProductName(), item.getColorName(), item.getSizeLabel(), product.getImageUrl(),
                    item.getUnitPrice(), item.getQuantity(), item.lineTotal());
        }
    }

    /**
     * 판매자별 배송 묶음. status: ORDERED 주문완료 → PREPARING 배송준비 → SHIPPING 배송중 → DELIVERED 배송완료
     * → CONFIRMED 구매확정, CANCELED 취소. subtotal 은 이 묶음의 줄 합계.
     */
    public record ShipmentResponse(
            Long shipmentId,
            Long sellerId,
            String sellerCompanyName,
            ShipmentStatus status,
            long subtotal,
            LocalDateTime confirmedAt,
            LocalDateTime canceledAt,
            List<OrderItemResponse> items) {

        public static ShipmentResponse of(Shipment shipment, List<OrderItem> items) {
            return new ShipmentResponse(shipment.getId(), shipment.getSeller().getId(),
                    shipment.getSeller().getCompanyName(), shipment.getStatus(),
                    items.stream().mapToLong(OrderItem::lineTotal).sum(),
                    shipment.getConfirmedAt(), shipment.getCanceledAt(),
                    items.stream().map(OrderItemResponse::from).toList());
        }
    }

    /** 주문 1건. totalPrice 는 주문 당시 전체 금액이며 일부가 취소돼도 바뀌지 않는다 (취소분은 shipments 의 status 로 구분). */
    public record OrderResponse(
            Long orderId,
            LocalDateTime createdAt,
            long totalPrice,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail,
            List<ShipmentResponse> shipments,
            PaymentResponse payment) {

        /**
         * items: 이 주문의 모든 주문 줄 (배송 묶음과 옵션·상품이 이미 로딩된 것). 배송 묶음별로 나눠 담는다.
         * payment: 결제 정보. V7 이전 주문은 결제가 없어 null
         */
        public static OrderResponse of(Order order, List<OrderItem> items, PaymentResponse payment) {
            Map<Long, List<OrderItem>> byShipment = new LinkedHashMap<>();
            Map<Long, Shipment> shipments = new LinkedHashMap<>();
            for (OrderItem item : items) {
                Shipment shipment = item.getShipment();
                shipments.putIfAbsent(shipment.getId(), shipment);
                byShipment.computeIfAbsent(shipment.getId(), id -> new ArrayList<>()).add(item);
            }
            return new OrderResponse(order.getId(), order.getCreatedAt(), order.getTotalPrice(),
                    order.getReceiverName(), order.getReceiverPhone(), order.getZipCode(), order.getAddress(),
                    order.getAddressDetail(),
                    shipments.values().stream().map(s -> ShipmentResponse.of(s, byShipment.get(s.getId()))).toList(),
                    payment);
        }
    }

    /** 판매자의 배송 상태 변경 요청. 한 칸씩 앞으로만: PREPARING(배송준비) → SHIPPING(배송중) → DELIVERED(배송완료) */
    public record ShipmentStatusRequest(
            @NotNull @Schema(allowableValues = {"PREPARING", "SHIPPING", "DELIVERED"}) ShipmentStatus status) {
    }

    /** 판매자가 보는 배송 묶음 한 건: 내 상품의 주문 줄과 배송지. subtotal 은 이 묶음의 줄 합계. */
    public record SellerShipmentResponse(
            Long shipmentId,
            Long orderId,
            ShipmentStatus status,
            LocalDateTime orderedAt,
            LocalDateTime confirmedAt,
            LocalDateTime canceledAt,
            long subtotal,
            String receiverName,
            String receiverPhone,
            String zipCode,
            String address,
            String addressDetail,
            List<OrderItemResponse> items) {

        /** shipment 의 주문(order)과 items 의 옵션·상품이 이미 로딩되어 있어야 한다 */
        public static SellerShipmentResponse of(Shipment shipment, List<OrderItem> items) {
            Order order = shipment.getOrder();
            return new SellerShipmentResponse(shipment.getId(), order.getId(), shipment.getStatus(),
                    shipment.getCreatedAt(), shipment.getConfirmedAt(), shipment.getCanceledAt(),
                    items.stream().mapToLong(OrderItem::lineTotal).sum(),
                    order.getReceiverName(), order.getReceiverPhone(), order.getZipCode(), order.getAddress(),
                    order.getAddressDetail(), items.stream().map(OrderItemResponse::from).toList());
        }
    }
}
