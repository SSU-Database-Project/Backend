package com.ssu.shop.domain.inventory.service;

import com.ssu.shop.domain.inventory.dto.StockDtos.StockChangeRequest;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockHistoryResponse;
import com.ssu.shop.domain.inventory.dto.StockDtos.StockResponse;
import com.ssu.shop.domain.inventory.repository.StockHistoryRepository;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.repository.ProductOptionRepository;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 판매자 재고 API: 본인 상품의 옵션인지 확인한 뒤 StockService 에 맡긴다.
 * 재고를 실제로 바꾸는 곳은 StockService 하나뿐이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerStockService {

    private final StockService stockService;
    private final ProductOptionRepository optionRepository;
    private final StockHistoryRepository stockHistoryRepository;
    private final SellerService sellerService;

    /** 입고(IN) 또는 조정(ADJUST). 소유권 확인과 재고 변경이 한 트랜잭션이다. */
    @Transactional
    public StockResponse change(Long userId, Long optionId, StockChangeRequest request) {
        ProductOption option = getOwnedOption(userId, optionId);
        if (option.getProduct().getStatus() == ProductStatus.DELETED) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_EDITABLE, "삭제된 상품의 재고는 변경할 수 없습니다.");
        }
        switch (request.type()) {
            case IN -> stockService.inbound(optionId, request.quantity(), request.memo());
            case ADJUST -> stockService.adjust(optionId, request.quantity(), request.memo());
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "type 은 IN 또는 ADJUST 여야 합니다.");
        }
        // StockService 가 같은 옵션 객체를 최신 값으로 다시 읽고 바꿨으므로 여기서 읽는 값이 변경 후 재고다
        return new StockResponse(optionId, option.getStockQuantity());
    }

    /** 옵션의 재고 이력 (최신순) */
    public PageResponse<StockHistoryResponse> history(Long userId, Long optionId, int page, int size) {
        getOwnedOption(userId, optionId);
        return PageResponse.of(
                stockHistoryRepository.findByOption_IdOrderByIdDesc(optionId, PageRequest.of(page, size)),
                StockHistoryResponse::from);
    }

    /** 옵션 → 상품 → 브랜드 → 판매자가 나인지 확인한다 */
    private ProductOption getOwnedOption(Long userId, Long optionId) {
        Seller seller = sellerService.getSeller(userId);
        ProductOption option = optionRepository.findById(optionId)
                .orElseThrow(() -> BusinessException.notFound("상품 옵션"));
        if (!option.getProduct().isOwnedBy(seller.getId())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_OWNED);
        }
        return option;
    }
}
