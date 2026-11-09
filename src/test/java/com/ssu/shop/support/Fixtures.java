package com.ssu.shop.support;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.seller.entity.SellerStatus;
import com.ssu.shop.domain.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

/** 단위 테스트용 엔티티 생성 (id 는 리플렉션으로 주입) */
public final class Fixtures {

    private Fixtures() {
    }

    public static User user(long id, String email) {
        User user = User.create(email, "{noop}password", "테스터" + id, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Seller seller(long id, User user, SellerStatus status) {
        Seller seller = Seller.apply(user, String.format("%010d", id), "회사" + id, "대표" + id);
        ReflectionTestUtils.setField(seller, "id", id);
        ReflectionTestUtils.setField(seller, "status", status);
        return seller;
    }

    public static Brand brand(long id, Seller seller, SellerStatus status) {
        Brand brand = Brand.create(seller, "브랜드" + id, null, null);
        ReflectionTestUtils.setField(brand, "id", id);
        ReflectionTestUtils.setField(brand, "status", status);
        return brand;
    }

    public static Category rootCategory(long id) {
        Category c = Category.create(null, "대분류" + id, 0);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    public static Category leafCategory(long id, Category parent) {
        Category c = Category.create(parent, "소분류" + id, 0);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    public static Product product(long id, Seller seller, Brand brand, Category category) {
        Product p = Product.create(seller, brand, category, "상품" + id, null, 10_000, null);
        ReflectionTestUtils.setField(p, "id", id);
        return p;
    }
}
