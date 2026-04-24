package com.example.common.page;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void pageResult_shouldHoldAllFields() {
        List<String> content = List.of("a", "b", "c");
        PageResult<String> result = new PageResult<>(content, 0, 3, 10L, 4);

        assertThat(result.content()).containsExactly("a", "b", "c");
        assertThat(result.page()).isEqualTo(0);
        assertThat(result.size()).isEqualTo(3);
        assertThat(result.totalElements()).isEqualTo(10L);
        assertThat(result.totalPages()).isEqualTo(4);
    }

    @Test
    void pageResult_withGenericType() {
        List<Integer> content = List.of(1, 2, 3);
        PageResult<Integer> result = new PageResult<>(content, 1, 3, 100L, 34);

        assertThat(result.content()).containsExactly(1, 2, 3);
        assertThat(result.totalElements()).isEqualTo(100L);
    }

    @Test
    void pageResult_emptyContent() {
        PageResult<String> result = new PageResult<>(List.of(), 0, 10, 0L, 0);

        assertThat(result.content()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(0L);
        assertThat(result.totalPages()).isEqualTo(0);
    }

    @Test
    void map_shouldTransformContent() {
        List<String> content = List.of("1", "2", "3");
        PageResult<String> original = new PageResult<>(content, 0, 3, 10L, 4);

        PageResult<Integer> mapped = original.map(Integer::parseInt);

        assertThat(mapped.content()).containsExactly(1, 2, 3);
        assertThat(mapped.page()).isEqualTo(0);
        assertThat(mapped.size()).isEqualTo(3);
        assertThat(mapped.totalElements()).isEqualTo(10L);
        assertThat(mapped.totalPages()).isEqualTo(4);
    }

    @Test
    void map_preservesPaginationMetadata() {
        PageResult<String> original = new PageResult<>(List.of("x"), 5, 20, 150L, 8);

        PageResult<Integer> mapped = original.map(String::length);

        assertThat(mapped.page()).isEqualTo(5);
        assertThat(mapped.size()).isEqualTo(20);
        assertThat(mapped.totalElements()).isEqualTo(150L);
        assertThat(mapped.totalPages()).isEqualTo(8);
    }
}
