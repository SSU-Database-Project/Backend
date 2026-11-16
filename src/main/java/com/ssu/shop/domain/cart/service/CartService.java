package com.ssu.shop.domain.cart.service;

import com.ssu.shop.domain.cart.dto.CartDtos;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemAddRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartItemUpdateRequest;
import com.ssu.shop.domain.cart.dto.CartDtos.CartResponse;
import com.ssu.shop.domain.cart.entity.CartItem;
import com.ssu.shop.domain.cart.repository.CartItemRepository;
import com.ssu.shop.domain.cart.repository.CartRepository;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.repository.ProductOptionRepository;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니. 가격·재고는 저장하지 않고 조회할 때마다 현재 값으로 계산한다.
 * 담을 때는 재고를 검사하지 않는다 — 담는다고 재고가 잡히는 것이 아니라서, 재고 확인은 주문할 때 한 번만 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductOptionRepository optionRepository;

    /** 내 장바구니 (쿼리 1번). 한 번도 담은 적이 없으면 빈 목록 */
    public CartResponse getCart(Long userId) {
        return CartResponse.of(cartItemRepository.findAllWithDetailsByUserId(userId));
    }

    /** 담기. 구매자에게 보이는 상품(판매중·품절)의 옵션만 담을 수 있다. */
    @Transactional
    public CartResponse add(Long userId, CartItemAddRequest request) {
        // 장바구니 만들기를 트랜잭션의 맨 처음에 한다. MySQL 은 트랜잭션 안의 첫 일반 SELECT 시점의 모습을 계속 보여 주므로,
        // 옵션을 먼저 조회하면 그 뒤에 다른 요청이 만든 장바구니가 보이지 않는다 (처음 담기를 동시에 두 번 보내면 실패하던 원인).
        // INSERT IGNORE 는 다른 요청이 만들고 있으면 그 커밋을 기다리므로, 이 뒤의 첫 SELECT 에는 장바구니가 반드시 보인다.
        cartRepository.createIfAbsent(userId);
        ProductOption option = optionRepository.findById(request.optionId())
                .orElseThrow(() -> BusinessException.notFound("상품 옵션"));
        if (!option.getProduct().getStatus().isPublic()) {
            throw BusinessException.notFound("상품 옵션"); // 구매자에게 안 보이는 상품은 없는 것처럼 (만든 장바구니도 롤백)
        }
        Long cartId = cartRepository.findIdByUserId(userId).orElseThrow();
        cartItemRepository.addOrIncrease(cartId, request.optionId(), request.quantity());
        if (cartItemRepository.findQuantity(cartId, request.optionId()) > CartDtos.MAX_QUANTITY) {
            // 예외를 던지면 트랜잭션이 롤백되어 방금 더한 수량도 되돌아간다
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "한 옵션은 최대 " + CartDtos.MAX_QUANTITY + "개까지 담을 수 있습니다.");
        }
        return getCart(userId);
    }

    /** 수량·체크 변경 (null 인 필드는 그대로) */
    @Transactional
    public CartResponse update(Long userId, Long cartItemId, CartItemUpdateRequest request) {
        CartItem item = getMyItem(userId, cartItemId);
        if (request.quantity() != null) {
            item.changeQuantity(request.quantity());
        }
        if (request.selected() != null) {
            item.changeSelected(request.selected());
        }
        return getCart(userId);
    }

    @Transactional
    public CartResponse remove(Long userId, Long cartItemId) {
        cartItemRepository.delete(getMyItem(userId, cartItemId));
        return getCart(userId);
    }

    private CartItem getMyItem(Long userId, Long cartItemId) {
        return cartItemRepository.findByIdAndUserId(cartItemId, userId)
                .orElseThrow(() -> BusinessException.notFound("장바구니 항목"));
    }
}
