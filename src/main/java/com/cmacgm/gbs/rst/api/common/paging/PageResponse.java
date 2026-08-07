package com.cmacgm.gbs.rst.api.common.paging;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages) {

    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber() + 1,
                source.getSize(),
                source.getTotalElements(),
                Math.max(1, source.getTotalPages()));
    }
}
