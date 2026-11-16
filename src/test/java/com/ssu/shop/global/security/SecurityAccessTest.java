package com.ssu.shop.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssu.shop.domain.cart.controller.CartController;
import com.ssu.shop.domain.cart.service.CartService;
import com.ssu.shop.domain.like.controller.LikeController;
import com.ssu.shop.domain.like.service.LikeService;
import com.ssu.shop.domain.product.controller.ProductController;
import com.ssu.shop.domain.product.controller.SellerProductController;
import com.ssu.shop.domain.product.service.ProductService;
import com.ssu.shop.domain.user.controller.AdminUserController;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import com.ssu.shop.domain.user.entity.UserStatus;
import com.ssu.shop.domain.user.repository.UserRepository;
import com.ssu.shop.domain.user.service.AdminUserService;
import com.ssu.shop.global.common.PageResponse;
import com.ssu.shop.global.config.AppProperties;
import com.ssu.shop.global.config.SecurityConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.ssu.shop.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** URL 프리픽스 권한 + 401/403 공통 응답 JSON 검증 (DB 없이 실행) */
@WebMvcTest(controllers = {ProductController.class, SellerProductController.class, AdminUserController.class,
        LikeController.class, CartController.class})
@Import({SecurityConfig.class, JwtProvider.class, SecurityErrorHandlers.class})
@EnableConfigurationProperties({JwtProperties.class, AppProperties.class})
class SecurityAccessTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JwtProvider jwtProvider;

    @MockitoBean
    UserRepository userRepository;
    @MockitoBean
    ProductService productService;
    @MockitoBean
    AdminUserService adminUserService;
    @MockitoBean
    LikeService likeService;
    @MockitoBean
    CartService cartService;

    private static final PageResponse<Object> EMPTY = new PageResponse<>(List.of(), 0, 20, 0, 0);

    private String tokenFor(long id, Role role, UserStatus status) {
        User user = Fixtures.user(id, "u" + id + "@test.com");
        ReflectionTestUtils.setField(user, "role", role);
        ReflectionTestUtils.setField(user, "status", status);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return "Bearer " + jwtProvider.createAccessToken(id, role);
    }

    @Test
    void 회원제_비회원은_상품_목록도_401() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 로그인한_일반회원은_상품_목록을_조회할_수_있다() throws Exception {
        when(productService.searchPublic(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn((PageResponse) EMPTY);

        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(1L, Role.USER, UserStatus.ACTIVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 토큰_없이_판매자_API를_호출하면_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get("/api/seller/products"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void 위조된_토큰은_401() throws Exception {
        mockMvc.perform(get("/api/seller/products").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 일반_회원이_판매자_API를_호출하면_403_FORBIDDEN() throws Exception {
        mockMvc.perform(get("/api/seller/products")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(1L, Role.USER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void 판매자는_판매자_API를_호출할_수_있다() throws Exception {
        when(productService.myProducts(eq(2L), any(), anyInt(), anyInt())).thenReturn((PageResponse) EMPTY);

        mockMvc.perform(get("/api/seller/products")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(2L, Role.SELLER, UserStatus.ACTIVE)))
                .andExpect(status().isOk());
    }

    @Test
    void 판매자가_관리자_API를_호출하면_403() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(2L, Role.SELLER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
    }

    /** 토큰의 role 이 아니라 DB 의 최신 role 로 판단한다: 토큰에는 SELLER 라고 적혀 있어도 DB 가 USER 면 403 */
    @Test
    void 토큰의_role이_아니라_DB의_최신_role로_판단한다() throws Exception {
        User user = Fixtures.user(3L, "u3@test.com", Role.USER);
        String forgedRoleToken = "Bearer " + jwtProvider.createAccessToken(3L, Role.SELLER);
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/seller/products").header(HttpHeaders.AUTHORIZATION, forgedRoleToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void 정지된_회원의_토큰은_만료_전이라도_401() throws Exception {
        mockMvc.perform(get("/api/seller/products")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(4L, Role.SELLER, UserStatus.SUSPENDED)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 탈퇴한_회원의_토큰도_401() throws Exception {
        mockMvc.perform(get("/api/products")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(5L, Role.USER, UserStatus.WITHDRAWN)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 좋아요는_일반회원만_판매자는_403() throws Exception {
        mockMvc.perform(post("/api/products/1/like")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(2L, Role.SELLER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/users/me/likes")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(6L, Role.SELLER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
    }

    // ===== [BE-2] 장바구니·주문 =====

    @Test
    void 장바구니와_주문은_일반회원만_판매자는_403() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(2L, Role.SELLER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/cart/items")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(2L, Role.SELLER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(2L, Role.SELLER, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/orders/1")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(7L, Role.ADMIN, UserStatus.ACTIVE)))
                .andExpect(status().isForbidden());
    }

    @Test
    void 비회원은_장바구니도_401() throws Exception {
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 일반회원은_장바구니를_조회할_수_있다() throws Exception {
        mockMvc.perform(get("/api/cart")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(1L, Role.USER, UserStatus.ACTIVE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void 장바구니_수량은_1에서_99까지만_받는다() throws Exception {
        for (String body : List.of("{\"optionId\":1,\"quantity\":0}", "{\"optionId\":1,\"quantity\":100}",
                "{\"quantity\":1}")) {
            mockMvc.perform(post("/api/cart/items")
                            .header(HttpHeaders.AUTHORIZATION, tokenFor(1L, Role.USER, UserStatus.ACTIVE))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
        }
    }
}
