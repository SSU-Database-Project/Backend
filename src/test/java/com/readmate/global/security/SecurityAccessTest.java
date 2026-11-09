package com.readmate.global.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.readmate.domain.product.controller.ProductController;
import com.readmate.domain.product.controller.SellerProductController;
import com.readmate.domain.product.service.ProductService;
import com.readmate.domain.user.controller.AdminUserController;
import com.readmate.domain.user.entity.Role;
import com.readmate.domain.user.entity.User;
import com.readmate.domain.user.entity.UserStatus;
import com.readmate.domain.user.repository.UserRepository;
import com.readmate.domain.user.service.AdminUserService;
import com.readmate.global.common.PageResponse;
import com.readmate.global.config.AppProperties;
import com.readmate.global.config.SecurityConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.readmate.support.Fixtures;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

/** URL 프리픽스 권한 + 401/403 공통 응답 JSON 검증 (DB 없이 실행) */
@WebMvcTest(controllers = {ProductController.class, SellerProductController.class, AdminUserController.class})
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

    private static final PageResponse<Object> EMPTY = new PageResponse<>(List.of(), 0, 20, 0, 0);

    private String tokenFor(long id, Role role, UserStatus status) {
        User user = Fixtures.user(id, "u" + id + "@test.com");
        ReflectionTestUtils.setField(user, "role", role);
        ReflectionTestUtils.setField(user, "status", status);
        when(userRepository.findById(id)).thenReturn(Optional.of(user));
        return "Bearer " + jwtProvider.createAccessToken(id, role);
    }

    @Test
    void 비회원도_상품_목록은_조회할_수_있다() throws Exception {
        when(productService.searchPublic(any(), any(), any(), any(), anyInt(), anyInt())).thenReturn((PageResponse) EMPTY);

        mockMvc.perform(get("/api/products"))
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

    /** 토큰의 role 이 아니라 DB 의 최신 role 로 판단한다: 토큰은 USER 로 발급됐지만 DB 에서 SELLER 로 승인된 경우 */
    @Test
    void 승인_직후_재로그인_없이도_DB의_최신_role이_반영된다() throws Exception {
        User user = Fixtures.user(3L, "u3@test.com");
        String oldToken = "Bearer " + jwtProvider.createAccessToken(3L, Role.USER);
        user.promoteToSeller();
        when(userRepository.findById(3L)).thenReturn(Optional.of(user));
        when(productService.myProducts(eq(3L), any(), anyInt(), anyInt())).thenReturn((PageResponse) EMPTY);

        mockMvc.perform(get("/api/seller/products").header(HttpHeaders.AUTHORIZATION, oldToken))
                .andExpect(status().isOk());
    }

    @Test
    void 정지된_회원의_토큰은_만료_전이라도_401() throws Exception {
        mockMvc.perform(get("/api/seller/products")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(4L, Role.SELLER, UserStatus.SUSPENDED)))
                .andExpect(status().isUnauthorized());
    }
}
