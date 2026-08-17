package com.cmacgm.gbs.rst.api.common.paging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class PageResponseTests {

    @Test
    void ofListSlicesAndReportsTotals() {
        PageResponse<Integer> page = PageResponse.ofList(List.of(1, 2, 3, 4, 5), 2, 2);
        assertThat(page.items()).containsExactly(3, 4);
        assertThat(page.page()).isEqualTo(2);
        assertThat(page.pageSize()).isEqualTo(2);
        assertThat(page.total()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
    }

    @Test
    void ofListClampsPagePastTheEnd() {
        PageResponse<Integer> page = PageResponse.ofList(List.of(1, 2, 3), 9, 10);
        assertThat(page.page()).isEqualTo(1);
        assertThat(page.items()).containsExactly(1, 2, 3);
        assertThat(page.totalPages()).isEqualTo(1);
    }

    @Test
    void ofListClampsPageSize() {
        PageResponse<Integer> empty = PageResponse.ofList(List.of(), 0, 0);
        assertThat(empty.page()).isEqualTo(1);
        assertThat(empty.pageSize()).isEqualTo(1);
        assertThat(empty.total()).isZero();
        assertThat(empty.totalPages()).isEqualTo(1);

        PageResponse<Integer> capped = PageResponse.ofList(List.of(1), 1, 500);
        assertThat(capped.pageSize()).isEqualTo(100);
    }
}
