package com.cmacgm.gbs.rst.api.common.paging;

import java.util.List;
import java.util.function.Function;

import org.springframework.data.domain.Page;

/**
 * 1-based page envelope used by list APIs.
 *
 * @param items current page of results
 * @param page current page number (1-based)
 * @param pageSize page size
 * @param total total matching rows
 * @param totalPages total page count, at least 1
 * @param <T> row type
 */
public record PageResponse<T>(
        List<T> items,
        int page,
        int pageSize,
        long total,
        int totalPages) {

    /**
     * Maps a Spring Data page (0-based) onto this 1-based envelope.
     *
     * @param source Spring Data page
     * @param mapper row mapper
     * @param <S> source row type
     * @param <T> response row type
     * @return page envelope
     */
    public static <S, T> PageResponse<T> from(Page<S> source, Function<S, T> mapper) {
        return new PageResponse<>(
                source.getContent().stream().map(mapper).toList(),
                source.getNumber() + 1,
                source.getSize(),
                source.getTotalElements(),
                Math.max(1, source.getTotalPages()));
    }

    /**
     * Pages an already-filtered in-memory list. Page is 1-based; size is clamped to 1–100.
     *
     * @param items filtered rows
     * @param page requested page
     * @param pageSize requested page size
     * @param <T> row type
     * @return page envelope
     */
    public static <T> PageResponse<T> ofList(List<T> items, int page, int pageSize) {
        List<T> source = items == null ? List.of() : items;
        int safePageSize = Math.min(100, Math.max(1, pageSize));
        long total = source.size();
        int totalPages = Math.max(1, (int) Math.ceil(total / (double) safePageSize));
        int safePage = Math.min(Math.max(1, page), totalPages);
        int from = (safePage - 1) * safePageSize;
        List<T> slice = from >= source.size()
                ? List.of()
                : List.copyOf(source.subList(from, Math.min(from + safePageSize, source.size())));
        return new PageResponse<>(slice, safePage, safePageSize, total, totalPages);
    }
}
