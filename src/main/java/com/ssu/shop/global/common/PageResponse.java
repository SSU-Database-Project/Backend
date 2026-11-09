package com.ssu.shop.global.common;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/** 목록 응답: { content, page, size, totalElements, totalPages } (page는 0부터 시작) */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return of(page, page.getContent().stream().map(mapper).toList());
    }

    public static <E, T> PageResponse<T> of(Page<E> page, List<T> mappedContent) {
        return new PageResponse<>(mappedContent, page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages());
    }
}
