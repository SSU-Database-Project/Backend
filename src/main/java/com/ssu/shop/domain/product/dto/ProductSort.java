package com.ssu.shop.domain.product.dto;

import com.ssu.shop.global.error.BusinessException;
import com.ssu.shop.global.error.ErrorCode;
import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;

/**
 * 정렬 화이트리스트. 임의의 컬럼명을 받으면 인덱스 없는 정렬(filesort)이나 오류를 유발하므로 허용된 값만 받는다.
 * 기본값은 OLDEST — 먼저 등록한 상품이 앞에 온다(회의 결정: 알고리즘 없이 등록 순서대로).
 * 동률일 때 페이지 간 순서가 흔들리지 않도록 id 를 보조 정렬키로 둔다.
 *
 * 보조키 방향은 주 정렬키와 같게 맞춘다. 인덱스 (created_at) 는 실제로 (created_at, id) 순서이므로
 * "created_at ASC, id ASC" 는 정방향, "created_at DESC, id DESC" 는 역방향 스캔으로 인덱스만으로 정렬된다.
 * 방향이 섞이면 인덱스 순서를 쓸 수 없어 filesort 가 발생한다.
 */
@Getter
@RequiredArgsConstructor
public enum ProductSort {
    OLDEST("oldest", Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"))),
    LATEST("latest", Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))),
    PRICE_ASC("priceAsc", Sort.by(Sort.Order.asc("price"), Sort.Order.asc("id"))),
    PRICE_DESC("priceDesc", Sort.by(Sort.Order.desc("price"), Sort.Order.desc("id")));

    private final String param;
    private final Sort sort;

    public static ProductSort from(String param) {
        if (param == null || param.isBlank()) {
            return OLDEST;
        }
        return Arrays.stream(values())
                .filter(s -> s.param.equalsIgnoreCase(param))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "sort 는 oldest, latest, priceAsc, priceDesc 중 하나여야 합니다."));
    }
}
