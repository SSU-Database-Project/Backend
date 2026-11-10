package com.ssu.shop.support;

import com.ssu.shop.domain.brand.entity.Brand;
import com.ssu.shop.domain.brand.entity.BrandStatus;
import com.ssu.shop.domain.category.entity.Category;
import com.ssu.shop.domain.product.entity.Color;
import com.ssu.shop.domain.product.entity.Gender;
import com.ssu.shop.domain.product.entity.Product;
import com.ssu.shop.domain.product.entity.ProductStatus;
import com.ssu.shop.domain.product.entity.Size;
import com.ssu.shop.domain.product.entity.SizeGroup;
import com.ssu.shop.domain.seller.entity.Seller;
import com.ssu.shop.domain.user.entity.Role;
import com.ssu.shop.domain.user.entity.User;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** 단위 테스트용 엔티티 생성 (id 등은 리플렉션으로 주입) */
public final class Fixtures {

    private Fixtures() {
    }

    public static User user(long id, String email) {
        return user(id, email, Role.USER);
    }

    public static User user(long id, String email, Role role) {
        User user = role == Role.ADMIN
                ? User.createAdmin(email, "{noop}password", "관리자" + id)
                : User.create(email, "{noop}password", "테스터" + id, null, role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    public static Seller seller(long id, User user) {
        Seller seller = Seller.create(user, String.format("%010d", id), "회사" + id, "대표" + id);
        ReflectionTestUtils.setField(seller, "id", id);
        return seller;
    }

    public static Brand brand(long id, Seller seller, BrandStatus status) {
        Brand brand = Brand.create(seller, "브랜드" + id, null, null);
        ReflectionTestUtils.setField(brand, "id", id);
        ReflectionTestUtils.setField(brand, "status", status);
        return brand;
    }

    public static Category rootCategory(long id, SizeGroup sizeGroup) {
        Category c = Category.create(null, "대분류" + id, sizeGroup, 0);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    public static Category leafCategory(long id, Category parent) {
        Category c = Category.create(parent, "소분류" + id, parent.getSizeGroup(), 0);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }

    public static Color color(long id, String name) {
        Color color = BeanUtils.instantiateClass(Color.class);
        ReflectionTestUtils.setField(color, "id", id);
        ReflectionTestUtils.setField(color, "name", name);
        ReflectionTestUtils.setField(color, "hexCode", "#000000");
        return color;
    }

    public static Size size(long id, SizeGroup group, String label) {
        Size size = BeanUtils.instantiateClass(Size.class);
        ReflectionTestUtils.setField(size, "id", id);
        ReflectionTestUtils.setField(size, "sizeGroup", group);
        ReflectionTestUtils.setField(size, "label", label);
        return size;
    }

    public static Product product(long id, Brand brand, Category category, ProductStatus status) {
        Product p = Product.create(brand, category, "CODE-" + id, "상품" + id, null, 10_000, Gender.UNISEX, "24FW",
                3, null);
        ReflectionTestUtils.setField(p, "id", id);
        ReflectionTestUtils.setField(p, "status", status);
        return p;
    }
}
