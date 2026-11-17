package com.ssu.shop.support;

import java.sql.PreparedStatement;
import java.sql.Statement;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * 실제 커밋이 필요한 DB 테스트(락·동시성)용 데이터 준비와 정리.
 * 테스트마다 고유한 tag 를 주면 회원 이메일은 "{tag}-..."로, 브랜드 이름은 "{TAG}-..."로 만들어지고,
 * cleanUp() 은 그 tag 로 만든 것만 지운다 → 같은 DB(shop_test)를 쓰는 다른 테스트와 섞이지 않는다.
 */
public class DbFixtures {

    // V4 고정 시드
    public static final long CLOTHING_LEAF = 11L; // 상의 > 반소매 티셔츠
    public static final long BLACK = 1L;
    public static final long WHITE = 2L;
    public static final long SIZE_M = 3L;

    private final JdbcTemplate jdbc;
    private final String tag;

    public DbFixtures(JdbcTemplate jdbc, String tag) {
        this.jdbc = jdbc;
        this.tag = tag;
    }

    /** role: USER / SELLER. 이메일은 {tag}-{name}@test.com */
    public long user(String name, String role) {
        return insert("INSERT INTO users (email, password, name, role) VALUES (?, '{noop}pw', ?, ?)",
                tag + "-" + name + "@test.com", name, role);
    }

    public long seller(long userId, String businessNumber) {
        return insert("""
                INSERT INTO sellers (user_id, business_number, company_name, representative_name)
                VALUES (?, ?, ?, '대표')""", userId, businessNumber, tag + " 상점 " + businessNumber);
    }

    /** 승인된 브랜드. 이름은 {TAG}-{suffix} */
    public long brand(long sellerId, String suffix) {
        return insert("INSERT INTO brands (seller_id, name, status) VALUES (?, ?, 'APPROVED')",
                sellerId, tag.toUpperCase() + "-" + suffix);
    }

    public long product(long brandId, String code, int price, String status) {
        return insert("""
                INSERT INTO products (brand_id, category_id, product_code, name, price, gender, season, ship_days, status)
                VALUES (?, ?, ?, ?, ?, 'UNISEX', '24FW', 3, ?)""",
                brandId, CLOTHING_LEAF, code, "테스트 상품 " + code, price, status);
    }

    /** 사이즈 M 고정, 색상으로 옵션을 구분한다 */
    public long option(long productId, long colorId, int stock) {
        return insert("INSERT INTO product_options (product_id, color_id, size_id, stock_quantity) VALUES (?, ?, ?, ?)",
                productId, colorId, SIZE_M, stock);
    }

    /** 이 tag 로 만든 데이터를 참조 순서의 반대로 모두 지운다 */
    public void cleanUp() {
        String users = tag + "-%";
        String brands = tag.toUpperCase() + "-%";
        String optionsOfTag = """
                 JOIN products p ON p.id = o.product_id
                 JOIN brands b ON b.id = p.brand_id
                WHERE b.name LIKE ?""";
        jdbc.update("DELETE h FROM stock_history h JOIN product_options o ON o.id = h.option_id" + optionsOfTag, brands);
        jdbc.update("DELETE i FROM order_items i JOIN product_options o ON o.id = i.option_id" + optionsOfTag, brands);
        jdbc.update("""
                DELETE s FROM shipments s
                  JOIN orders d ON d.id = s.order_id
                  JOIN users u ON u.id = d.user_id
                 WHERE u.email LIKE ?""", users);
        jdbc.update("DELETE d FROM orders d JOIN users u ON u.id = d.user_id WHERE u.email LIKE ?", users);
        jdbc.update("DELETE i FROM cart_items i JOIN product_options o ON o.id = i.option_id" + optionsOfTag, brands);
        jdbc.update("DELETE c FROM carts c JOIN users u ON u.id = c.user_id WHERE u.email LIKE ?", users);
        jdbc.update("DELETE o FROM product_options o" + optionsOfTag, brands);
        jdbc.update("DELETE p FROM products p JOIN brands b ON b.id = p.brand_id WHERE b.name LIKE ?", brands);
        jdbc.update("DELETE FROM brands WHERE name LIKE ?", brands);
        jdbc.update("DELETE s FROM sellers s JOIN users u ON u.id = s.user_id WHERE u.email LIKE ?", users);
        jdbc.update("DELETE FROM users WHERE email LIKE ?", users);
    }

    /** INSERT 하고 생성된 id 를 돌려준다 (트랜잭션 밖이라 LAST_INSERT_ID() 를 따로 조회하면 다른 연결일 수 있다) */
    public long insert(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keys);
        return keys.getKey().longValue();
    }
}
