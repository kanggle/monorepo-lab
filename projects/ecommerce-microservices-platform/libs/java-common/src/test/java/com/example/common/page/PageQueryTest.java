package com.example.common.page;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryTest {

    @Test
    void validPageQuery_shouldBeCreated() {
        PageQuery query = new PageQuery(0, 10, "createdAt", "DESC");
        assertThat(query.page()).isEqualTo(0);
        assertThat(query.size()).isEqualTo(10);
        assertThat(query.sortBy()).isEqualTo("createdAt");
        assertThat(query.sortDirection()).isEqualTo("DESC");
    }

    @Test
    void pageZero_shouldBeAllowed() {
        PageQuery query = new PageQuery(0, 1, "id", "ASC");
        assertThat(query.page()).isEqualTo(0);
    }

    @Test
    void sizeOne_shouldBeAllowed() {
        PageQuery query = new PageQuery(0, 1, "id", "ASC");
        assertThat(query.size()).isEqualTo(1);
    }

    @Test
    void sizeMaxSize_shouldBeAllowed() {
        PageQuery query = new PageQuery(0, PageQuery.MAX_SIZE, "id", "ASC");
        assertThat(query.size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    void pageNegative_shouldThrow() {
        assertThatThrownBy(() -> new PageQuery(-1, 10, "id", "ASC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page must be >= 0");
    }

    @Test
    void sizeZero_shouldThrow() {
        assertThatThrownBy(() -> new PageQuery(0, 0, "id", "ASC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be >= 1");
    }

    @Test
    void sizeExceedsMax_shouldThrow() {
        assertThatThrownBy(() -> new PageQuery(0, PageQuery.MAX_SIZE + 1, "id", "ASC"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be <=");
    }

    @Test
    void ofFactory_clampsNegativePage_toZero() {
        PageQuery query = PageQuery.of(-5, 10, "id", "ASC");
        assertThat(query.page()).isEqualTo(0);
    }

    @Test
    void ofFactory_clampsZeroSize_toOne() {
        PageQuery query = PageQuery.of(0, 0, "id", "ASC");
        assertThat(query.size()).isEqualTo(1);
    }

    @Test
    void ofFactory_clampsOversizeToMax() {
        PageQuery query = PageQuery.of(0, PageQuery.MAX_SIZE + 50, "id", "ASC");
        assertThat(query.size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50, 100})
    void validSizes_shouldBeAllowed(int size) {
        PageQuery query = new PageQuery(0, size, "id", "ASC");
        assertThat(query.size()).isEqualTo(size);
    }
}
