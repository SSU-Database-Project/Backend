package com.ssu.shop.domain.order.service;

import com.ssu.shop.domain.order.dto.OrderDtos.SellerShipmentResponse;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.order.entity.Shipment;
import com.ssu.shop.domain.order.entity.ShipmentStatus;
import com.ssu.shop.domain.order.repository.OrderItemRepository;
import com.ssu.shop.domain.order.repository.ShipmentRepository;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 판매자 배송 관리: 내 배송 묶음 목록과 배송 상태 변경. 취소와 구매확정은 구매자만 한다(OrderService). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerShipmentService {

    private final ShipmentRepository shipmentRepository;
    private final OrderItemRepository orderItemRepository;
    private final SellerService sellerService;

    /**
     * 내 배송 묶음 목록 (최신순). status 를 주면 그 상태만 — 예: CONFIRMED 로 확정된 주문만 따로 본다.
     * 쿼리는 건수와 상관없이 일정하다: 판매자 확인 → 배송 묶음 한 페이지(주문 포함) → 전체 건수 → 그 페이지의 주문 줄 전부.
     */
    public PageResponse<SellerShipmentResponse> myShipments(Long userId, ShipmentStatus status, int page, int size) {
        Long sellerId = sellerService.getSeller(userId).getId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        Page<Shipment> shipments = status == null
                ? shipmentRepository.findBySeller_Id(sellerId, pageable)
                : shipmentRepository.findBySeller_IdAndStatus(sellerId, status, pageable);
        if (shipments.isEmpty()) {
            return PageResponse.of(shipments, List.of());
        }
        Map<Long, List<OrderItem>> itemsByShipment = orderItemRepository
                .findAllWithProductByShipmentIds(shipments.map(Shipment::getId).getContent()).stream()
                .collect(Collectors.groupingBy(item -> item.getShipment().getId()));
        return PageResponse.of(shipments, s -> SellerShipmentResponse.of(s, itemsByShipment.get(s.getId())));
    }

    /**
     * 배송 상태를 한 칸 앞으로: ORDERED → PREPARING → SHIPPING → DELIVERED.
     * 바꾸려는 상태의 바로 앞 상태일 때만 바뀌는 조건부 UPDATE 라서, 건너뛰기·되돌리기는 물론
     * 구매자의 취소와 동시에 와도 하나만 성공한다.
     */
    @Transactional
    public SellerShipmentResponse changeStatus(Long userId, Long shipmentId, ShipmentStatus target) {
        Long sellerId = sellerService.getSeller(userId).getId();
        Shipment shipment = shipmentRepository.findById(shipmentId)
                .filter(s -> s.getSeller().getId().equals(sellerId)) // 남의 배송 묶음은 없는 것처럼
                .orElseThrow(() -> BusinessException.notFound("배송"));
        if (shipmentRepository.transit(shipment.getId(), previousOf(target), target) == 0) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "배송 상태는 주문완료 → 배송준비 → 배송중 → 배송완료 순서로 한 단계씩만 바꿀 수 있습니다.");
        }
        Shipment updated = shipmentRepository.findWithOrderById(shipmentId).orElseThrow();
        return SellerShipmentResponse.of(updated,
                orderItemRepository.findAllWithProductByShipmentIds(List.of(shipmentId)));
    }

    /** 판매자가 지정할 수 있는 상태와 그 바로 앞 상태 */
    private static ShipmentStatus previousOf(ShipmentStatus target) {
        return switch (target) {
            case PREPARING -> ShipmentStatus.ORDERED;
            case SHIPPING -> ShipmentStatus.PREPARING;
            case DELIVERED -> ShipmentStatus.SHIPPING;
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "판매자는 PREPARING, SHIPPING, DELIVERED 로만 바꿀 수 있습니다.");
        };
    }
}
