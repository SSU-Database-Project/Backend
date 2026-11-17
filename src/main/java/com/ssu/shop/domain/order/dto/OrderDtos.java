package com.ssu.shop.domain.order.dto;

import com.ssu.shop.domain.order.entity.Order;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.product.entity.Product;
import io.swagger.v3.oas.annotations.media.Schema;
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
            @Size(max = 255) String addressDetail) {
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
            List<ShipmentResponse> shipments) {

        /** items: 이 주문의 모든 주문 줄 (배송 묶음과 옵션·상품이 이미 로딩된 것). 배송 묶음별로 나눠 담는다. */
        public static OrderResponse of(Order order, List<OrderItem> items) {
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
                    shipments.values().stream().map(s -> ShipmentResponse.of(s, byShipment.get(s.getId()))).toList());
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
