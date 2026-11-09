package com.ssu.shop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.repository.BrandRepository;
import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.category.service.CategoryService;
import com.ssu.shop.domain.inventory.entity.Inventory;
import com.ssu.shop.domain.inventory.entity.StockHistory;
import com.ssu.shop.domain.inventory.entity.StockType;
import com.ssu.shop.domain.inventory.repository.InventoryRepository;
import com.ssu.shop.domain.inventory.repository.StockHistoryRepository;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductCreateRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductUpdateRequest;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.entity.SellerStatus;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;
    @Mock
    InventoryRepository inventoryRepository;
    @Mock
    StockHistoryRepository stockHistoryRepository;
    @Mock
    BrandRepository brandRepository;
    @Mock
    CategoryService categoryService;
    @Mock
    SellerService sellerService;
    @InjectMocks
    ProductService productService;

    Seller me;
    Seller other;
    Category root;
    Category leaf;

    @BeforeEach
    void setUp() {
        me = Fixtures.seller(1L, Fixtures.user(1L, "me@test.com"), SellerStatus.APPROVED);
        other = Fixtures.seller(2L, Fixtures.user(2L, "other@test.com"), SellerStatus.APPROVED);
        root = Fixtures.rootCategory(1L);
        leaf = Fixtures.leafCategory(11L, root);
    }

    private ProductCreateRequest request(Long categoryId, Long brandId, int initialStock) {
        return new ProductCreateRequest(categoryId, brandId, "티셔츠", null, 15_000, null, initialStock);
    }

    @Test
    void 상품_등록시_재고행과_입고이력을_함께_만든다() {
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(categoryService.getCategory(11L)).thenReturn(leaf);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.create(100L, request(11L, null, 7));

        ArgumentCaptor<Inventory> inventory = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(inventory.capture());
        assertThat(inventory.getValue().getQuantity()).isEqualTo(7);

        ArgumentCaptor<StockHistory> history = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(history.capture());
        assertThat(history.getValue().getType()).isEqualTo(StockType.IN);
        assertThat(history.getValue().getChangeQty()).isEqualTo(7);
        assertThat(history.getValue().getQuantityAfter()).isEqualTo(7);
    }

    @Test
    void 초기재고가_0이면_재고행만_만들고_이력은_남기지_않는다() {
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(categoryService.getCategory(11L)).thenReturn(leaf);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        productService.create(100L, request(11L, null, 0));

        verify(inventoryRepository).save(any(Inventory.class));
        verify(stockHistoryRepository, never()).save(any());
    }

    @Test
    void 다른_판매자의_브랜드로는_등록할_수_없다() {
        Brand othersBrand = Fixtures.brand(5L, other, SellerStatus.APPROVED);
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(categoryService.getCategory(11L)).thenReturn(leaf);
        when(brandRepository.findById(5L)).thenReturn(Optional.of(othersBrand));

        assertThatThrownBy(() -> productService.create(100L, request(11L, 5L, 1)))
                .extracting("errorCode").isEqualTo(ErrorCode.BRAND_NOT_OWNED);
        verify(productRepository, never()).save(any());
    }

    @Test
    void 승인되지_않은_브랜드로는_등록할_수_없다() {
        Brand pending = Fixtures.brand(5L, me, SellerStatus.PENDING);
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(categoryService.getCategory(11L)).thenReturn(leaf);
        when(brandRepository.findById(5L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> productService.create(100L, request(11L, 5L, 1)))
                .extracting("errorCode").isEqualTo(ErrorCode.BRAND_NOT_APPROVED);
    }

    @Test
    void 대분류에는_상품을_등록할_수_없다() {
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(categoryService.getCategory(1L)).thenReturn(root);

        assertThatThrownBy(() -> productService.create(100L, request(1L, null, 1)))
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_NOT_LEAF);
    }

    @Test
    void 타인_상품_수정은_403() {
        Product othersProduct = Fixtures.product(50L, other, null, leaf);
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(othersProduct));

        assertThatThrownBy(() -> productService.update(100L, 50L,
                new ProductUpdateRequest(null, null, null, "변경", null, null, null, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_OWNED);
    }

    @Test
    void 관리자가_차단한_상품은_판매자가_상태를_바꿀_수_없다() {
        Product blocked = Fixtures.product(50L, me, null, leaf);
        blocked.changeStatus(ProductStatus.BLOCKED);
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> productService.update(100L, 50L,
                new ProductUpdateRequest(null, null, null, null, null, null, null, ProductStatus.ON_SALE)))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_BLOCKED);
        assertThat(blocked.getStatus()).isEqualTo(ProductStatus.BLOCKED);
    }

    @Test
    void 판매자는_BLOCKED를_직접_지정할_수_없다() {
        Product mine = Fixtures.product(50L, me, null, leaf);
        when(sellerService.getApprovedSeller(100L)).thenReturn(me);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(mine));

        assertThatThrownBy(() -> productService.update(100L, 50L,
                new ProductUpdateRequest(null, null, null, null, null, null, null, ProductStatus.BLOCKED)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
    }
}
