package com.ssu.shop.domain.inventory.service;

import com.ssu.shop.domain.inventory.entity.StockHistory;
import com.ssu.shop.domain.inventory.repository.StockHistoryRepository;
import com.ssu.shop.domain.order.entity.OrderItem;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.repository.ProductOptionRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 변경 서비스. 옵션 재고(product_options.stock_quantity)는 이 서비스에서만 바꾼다.
 *
 * 모든 메서드가 같은 순서로 동작한다: 옵션 행 잠금 → 최신 값 다시 읽기 → 검증 → 재고 변경 → 이력 저장.
 * 행을 잠근 채로 이력까지 남기므로 "옵션 재고 = 마지막 이력의 quantity_after" 가 항상 맞고,
 * 이력의 id 순서가 곧 재고가 바뀐 순서다.
 *
 * 트랜잭션: 호출한 쪽의 트랜잭션에 합류한다(없으면 새로 시작). 잠금은 그 트랜잭션이 끝날 때 풀린다.
 *
 * 호출하는 쪽(주문 서비스)이 지킬 약속 — 정합성 검사 "주문 줄마다 ORDER 이력 1건, 취소된 배송의 줄마다 CANCEL 이력 1건" 기준:
 *  - decrease 는 주문 줄 하나에 한 번만, 주문을 저장하는 것과 같은 트랜잭션에서 부른다.
 *  - restore 는 배송 묶음을 CANCELED 로 바꾸는 것과 같은 트랜잭션에서, 그 묶음의 모든 줄에 대해 한 번만 부른다.
 *  - 넘기는 OrderItem 은 이미 저장되어 id 가 있어야 한다 (이력이 order_item_id 로 참조한다).
 * 상품 판매 상태, 주문·배송 상태, 소유권 확인은 이 서비스의 일이 아니다 (호출하는 쪽에서 한다).
 */
@Service
@RequiredArgsConstructor
@Transactional
public class StockService {

    private final ProductOptionRepository optionRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final EntityManager entityManager;

    /** 주문 차감. 하나라도 재고가 모자라면 OUT_OF_STOCK — 트랜잭션이 롤백되어 앞에서 차감한 것도 되돌아간다. */
    public void decrease(List<OrderItem> orderItems) {
        Map<Long, ProductOption> options = lockOptions(optionIdsOf(orderItems));
        for (OrderItem item : orderItems) {
            ProductOption option = options.get(item.getOption().getId());
            if (option.getStockQuantity() < item.getQuantity()) {
                throw new BusinessException(ErrorCode.OUT_OF_STOCK);
            }
            int after = option.changeStock(-item.getQuantity());
            stockHistoryRepository.save(StockHistory.order(option, item.getQuantity(), after, item));
        }
    }

    /** 주문 취소 복원: 주문 줄의 수량만큼 재고를 되돌린다. */
    public void restore(List<OrderItem> orderItems) {
        Map<Long, ProductOption> options = lockOptions(optionIdsOf(orderItems));
        for (OrderItem item : orderItems) {
            ProductOption option = options.get(item.getOption().getId());
            int after = option.changeStock(item.getQuantity());
            stockHistoryRepository.save(StockHistory.cancel(option, item.getQuantity(), after, item));
        }
    }

    /** 입고: 재고를 quantity 만큼 늘린다. */
    public void inbound(Long optionId, int quantity, String memo) {
        if (quantity <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "입고 수량은 1 이상이어야 합니다.");
        }
        ProductOption option = lockOption(optionId);
        int after = option.changeStock(quantity);
        stockHistoryRepository.save(StockHistory.inbound(option, quantity, after, memo));
    }

    /** 조정: 실사 차이·파손 등으로 재고를 changeQty 만큼 늘리거나(+) 줄인다(-). 결과가 음수가 되는 조정은 거부한다. */
    public void adjust(Long optionId, int changeQty, String memo) {
        if (changeQty == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조정 수량은 0일 수 없습니다.");
        }
        ProductOption option = lockOption(optionId);
        if (option.getStockQuantity() + changeQty < 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "조정 후 재고는 0보다 작을 수 없습니다.");
        }
        int after = option.changeStock(changeQty);
        stockHistoryRepository.save(StockHistory.adjust(option, changeQty, after, memo));
    }

    private ProductOption lockOption(Long optionId) {
        return lockOptions(List.of(optionId)).get(optionId);
    }

    /**
     * 옵션 행을 id 오름차순으로 잠근 뒤, 잠근 상태에서 DB 의 최신 값으로 다시 읽는다.
     *
     * 다시 읽는 이유: 호출한 쪽이 같은 트랜잭션에서 옵션을 이미 읽어 두었다면, JPA 는 그 옵션을 다시 조회해도
     * 메모리에 있던 예전 재고를 그대로 돌려준다. 그 사이 다른 주문이 재고를 바꿨다면 틀린 값으로 계산하게 된다.
     * refresh 는 메모리의 값을 DB 의 현재 값으로 덮어쓴다.
     * 잠금 모드로 읽는 이유: MySQL 은 일반 SELECT 에 "트랜잭션이 처음 읽은 시점"의 값을 보여 주고,
     * FOR UPDATE 로 읽을 때만 가장 최근에 커밋된 값을 보여 준다.
     */
    private Map<Long, ProductOption> lockOptions(Collection<Long> optionIds) {
        Set<Long> ids = new HashSet<>(optionIds);
        if (ids.isEmpty()) {
            return Map.of();
        }
        List<Long> lockedIds = optionRepository.lockAllByIdInOrder(ids);
        if (lockedIds.size() != ids.size()) {
            throw BusinessException.notFound("상품 옵션");
        }
        Map<Long, ProductOption> options = new HashMap<>();
        // 옵션마다 SELECT 1번. 주문당 옵션은 몇 개라 충분하다. 수백 개를 한 번에 바꾸게 되면 한 쿼리로 합친다
        for (ProductOption option : optionRepository.findAllById(lockedIds)) {
            entityManager.refresh(option, LockModeType.PESSIMISTIC_WRITE);
            options.put(option.getId(), option);
        }
        return options;
    }

    private static List<Long> optionIdsOf(List<OrderItem> orderItems) {
        return orderItems.stream().map(item -> item.getOption().getId()).toList();
    }
}
