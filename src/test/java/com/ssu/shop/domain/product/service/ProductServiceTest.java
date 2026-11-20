package com.ssu.shop.domain.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.brand.repository.BrandRepository;
import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.category.service.CategoryService;
import com.ssu.shop.domain.inventory.entity.StockHistory;
import com.ssu.shop.domain.inventory.entity.StockType;
import com.ssu.shop.domain.inventory.repository.StockHistoryRepository;
import com.ssu.shop.domain.product.dto.ProductDtos.AdminProductStatusRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.OptionRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductCreateRequest;
import com.ssu.shop.domain.product.dto.ProductDtos.ProductUpdateRequest;
import com.ssu.shop.domain.product.entity.Gender;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductOption;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.entity.SizeGroup;
import com.ssu.shop.domain.product.repository.ColorRepository;
import com.ssu.shop.domain.product.repository.ProductOptionRepository;
import com.ssu.shop.domain.product.repository.ProductRepository;
import com.ssu.shop.domain.product.repository.SizeRepository;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.service.SellerService;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.global.error.ErrorCode;
import com.ssu.shop.support.Fixtures;
import java.util.List;
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
    ProductOptionRepository optionRepository;
    @Mock
    ColorRepository colorRepository;
    @Mock
    SizeRepository sizeRepository;
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
    Brand myBrand;
    Category root;
    Category leaf; // 의류 (CLOTHING)

    @BeforeEach
    void setUp() {
        me = Fixtures.seller(1L, Fixtures.user(1L, "me@test.com"));
        other = Fixtures.seller(2L, Fixtures.user(2L, "other@test.com"));
        myBrand = Fixtures.brand(10L, me, BrandStatus.APPROVED);
        root = Fixtures.rootCategory(1L, SizeGroup.CLOTHING);
        leaf = Fixtures.leafCategory(11L, root);
        lenient().when(sellerService.getSeller(100L)).thenReturn(me);
        lenient().when(colorRepository.findAllById(any())).thenReturn(List.of(Fixtures.color(1L, "블랙")));
        lenient().when(sizeRepository.findAllById(any())).thenReturn(List.of(
                Fixtures.size(3L, SizeGroup.CLOTHING, "M"),
                Fixtures.size(4L, SizeGroup.CLOTHING, "L"),
                Fixtures.size(14L, SizeGroup.SHOES, "250")));
        lenient().when(optionRepository.save(any(ProductOption.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(optionRepository.findAllWithColorAndSize(any())).thenReturn(List.of());
    }

    private ProductCreateRequest request(Long brandId, Long categoryId, List<OptionRequest> options) {
        return new ProductCreateRequest(brandId, categoryId, "UB-001", "반팔 티셔츠", null, 15_000, Gender.UNISEX,
                "24SS", 3, null, options);
    }

    private ProductUpdateRequest renameTo(String name) {
        return new ProductUpdateRequest(null, null, name, null, null, null, null, null, null, null);
    }

    @Test
    void 상품_등록시_승인대기로_만들고_옵션별_재고와_입고이력을_함께_만든다() {
        when(brandRepository.findByIdForShare(10L)).thenReturn(Optional.of(myBrand));
        when(categoryService.getCategory(11L)).thenReturn(leaf);

        productService.create(100L, request(10L, 11L,
                List.of(new OptionRequest(1L, 3L, 7), new OptionRequest(1L, 4L, 0))));

        ArgumentCaptor<Product> product = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).saveAndFlush(product.capture());
        assertThat(product.getValue().getStatus()).isEqualTo(ProductStatus.PENDING);

        verify(optionRepository, times(2)).save(any(ProductOption.class));
        ArgumentCaptor<StockHistory> history = ArgumentCaptor.forClass(StockHistory.class);
        verify(stockHistoryRepository).save(history.capture()); // 재고 0 옵션은 이력 없음
        assertThat(history.getValue().getType()).isEqualTo(StockType.IN);
        assertThat(history.getValue().getChangeQty()).isEqualTo(7);
        assertThat(history.getValue().getQuantityAfter()).isEqualTo(7);
    }

    @Test
    void 카테고리의_사이즈_체계와_다른_사이즈는_거부한다() {
        when(brandRepository.findByIdForShare(10L)).thenReturn(Optional.of(myBrand));
        when(categoryService.getCategory(11L)).thenReturn(leaf);

        assertThatThrownBy(() -> productService.create(100L, request(10L, 11L,
                List.of(new OptionRequest(1L, 14L, 5))))) // 의류 카테고리에 신발 사이즈 250
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_OPTION);
    }

    @Test
    void 같은_색상_사이즈_조합이_중복되면_거부한다() {
        when(brandRepository.findByIdForShare(10L)).thenReturn(Optional.of(myBrand));
        when(categoryService.getCategory(11L)).thenReturn(leaf);

        assertThatThrownBy(() -> productService.create(100L, request(10L, 11L,
                List.of(new OptionRequest(1L, 3L, 5), new OptionRequest(1L, 3L, 2)))))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_OPTION);
    }

    @Test
    void 브랜드_안에서_품번이_겹치면_거부한다() {
        when(brandRepository.findByIdForShare(10L)).thenReturn(Optional.of(myBrand));
        when(categoryService.getCategory(11L)).thenReturn(leaf);
        when(productRepository.existsByBrand_IdAndProductCode(10L, "UB-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(100L, request(10L, 11L, List.of(new OptionRequest(1L, 3L, 1)))))
                .extracting("errorCode").isEqualTo(ErrorCode.DUPLICATE_PRODUCT_CODE);
        verify(productRepository, never()).saveAndFlush(any());
    }

    @Test
    void 다른_판매자의_브랜드로는_등록할_수_없다() {
        when(brandRepository.findByIdForShare(20L)).thenReturn(Optional.of(Fixtures.brand(20L, other, BrandStatus.APPROVED)));

        assertThatThrownBy(() -> productService.create(100L, request(20L, 11L, List.of(new OptionRequest(1L, 3L, 1)))))
                .extracting("errorCode").isEqualTo(ErrorCode.BRAND_NOT_OWNED);
    }

    @Test
    void 승인되지_않은_브랜드로는_등록할_수_없다() {
        when(brandRepository.findByIdForShare(10L)).thenReturn(Optional.of(Fixtures.brand(10L, me, BrandStatus.PENDING)));

        assertThatThrownBy(() -> productService.create(100L, request(10L, 11L, List.of(new OptionRequest(1L, 3L, 1)))))
                .extracting("errorCode").isEqualTo(ErrorCode.BRAND_NOT_APPROVED);
    }

    @Test
    void 대분류에는_상품을_등록할_수_없다() {
        when(brandRepository.findByIdForShare(10L)).thenReturn(Optional.of(myBrand));
        when(categoryService.getCategory(1L)).thenReturn(root);

        assertThatThrownBy(() -> productService.create(100L, request(10L, 1L, List.of(new OptionRequest(1L, 3L, 1)))))
                .extracting("errorCode").isEqualTo(ErrorCode.CATEGORY_NOT_LEAF);
    }

    @Test
    void 판매중인_상품의_정보를_고치면_재승인_대기로_돌아간다() {
        Product onSale = Fixtures.product(50L, myBrand, leaf, ProductStatus.ON_SALE);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(onSale));

        productService.update(100L, 50L, renameTo("새 이름"));

        assertThat(onSale.getName()).isEqualTo("새 이름");
        assertThat(onSale.getStatus()).isEqualTo(ProductStatus.PENDING);
    }

    @Test
    void 바뀐_값이_없으면_승인_상태를_유지한다() {
        Product onSale = Fixtures.product(50L, myBrand, leaf, ProductStatus.ON_SALE);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(onSale));

        productService.update(100L, 50L, renameTo(onSale.getName()));

        assertThat(onSale.getStatus()).isEqualTo(ProductStatus.ON_SALE);
    }

    @Test
    void 타인_상품_수정은_403() {
        Product othersProduct = Fixtures.product(51L, Fixtures.brand(20L, other, BrandStatus.APPROVED), leaf,
                ProductStatus.ON_SALE);
        when(productRepository.findDetailById(51L)).thenReturn(Optional.of(othersProduct));

        assertThatThrownBy(() -> productService.update(100L, 51L, renameTo("변경")))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_OWNED);
    }

    @Test
    void 관리자가_차단한_상품은_수정할_수_없다() {
        Product blocked = Fixtures.product(50L, myBrand, leaf, ProductStatus.BLOCKED);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> productService.update(100L, 50L, renameTo("변경")))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_EDITABLE);
        assertThat(blocked.getStatus()).isEqualTo(ProductStatus.BLOCKED);
    }

    @Test
    void 승인_전_상품은_판매상태를_바꿀_수_없다() {
        Product pending = Fixtures.product(50L, myBrand, leaf, ProductStatus.PENDING);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(pending));

        assertThatThrownBy(() -> productService.changeSaleStatus(100L, 50L, ProductStatus.ON_SALE))
                .extracting("errorCode").isEqualTo(ErrorCode.PRODUCT_NOT_APPROVED);
    }

    @Test
    void 삭제는_승인없이_DELETED로_바뀐다() {
        Product onSale = Fixtures.product(50L, myBrand, leaf, ProductStatus.ON_SALE);
        when(productRepository.findDetailById(50L)).thenReturn(Optional.of(onSale));

        productService.delete(100L, 50L);

        assertThat(onSale.getStatus()).isEqualTo(ProductStatus.DELETED);
    }

    @Test
    void 관리자_승인은_승인대기나_차단_상태에서만_가능하다() {
        when(productRepository.findById(50L)).thenReturn(Optional.of(Fixtures.product(50L,
                Fixtures.brand(10L, Fixtures.seller(1L, Fixtures.user(1L, "s@test.com", Role.SELLER)), BrandStatus.APPROVED),
                leaf, ProductStatus.PENDING)));
        when(productRepository.transitStatus(eq(50L), anyCollection(), eq(ProductStatus.ON_SALE), any()))
                .thenReturn(0);
        when(productRepository.existsById(50L)).thenReturn(true);

        assertThatThrownBy(() -> productService.adminChangeStatus(50L,
                new AdminProductStatusRequest(ProductStatus.ON_SALE, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_STATUS_TRANSITION);
    }

    @Test
    void 브랜드가_승인되기_전에는_상품을_승인할_수_없다() {
        when(productRepository.findById(51L)).thenReturn(Optional.of(Fixtures.product(51L,
                Fixtures.brand(11L, Fixtures.seller(1L, Fixtures.user(1L, "s@test.com", Role.SELLER)), BrandStatus.PENDING),
                leaf, ProductStatus.PENDING)));

        assertThatThrownBy(() -> productService.adminChangeStatus(51L,
                new AdminProductStatusRequest(ProductStatus.ON_SALE, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.BRAND_NOT_APPROVED);
        verify(productRepository, never()).transitStatus(anyLong(), anyCollection(), any(), any());
    }

    @Test
    void 관리자는_DELETED나_PENDING을_지정할_수_없다() {
        assertThatThrownBy(() -> productService.adminChangeStatus(50L,
                new AdminProductStatusRequest(ProductStatus.DELETED, null)))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_INPUT);
        verify(productRepository, never()).transitStatus(anyLong(), anyCollection(), any(), anyString());
    }
}
