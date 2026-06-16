package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.common.page.PageQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docker-free unit tests for {@link PageRequests#toPageable} — the
 * {@code PageQuery -> Spring Data PageRequest} conversion (TASK-SCM-BE-016 L5 sort fix).
 * Moved here from the application-service test when the sort building was relocated out
 * of the (now framework-free) domain port boundary.
 */
class PageRequestsTest {

    @Test
    @DisplayName("sortBy=createdAt asc → PageRequest carries Sort.Direction.ASC")
    void sortAsc() {
        PageRequest pr = PageRequests.toPageable(PageQuery.of(0, 10, "createdAt", "asc"));
        assertThat(pr.getSort().getOrderFor("createdAt"))
                .isNotNull()
                .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC));
    }

    @Test
    @DisplayName("sortBy=createdAt desc → PageRequest carries Sort.Direction.DESC")
    void sortDesc() {
        PageRequest pr = PageRequests.toPageable(PageQuery.of(0, 10, "createdAt", "desc"));
        assertThat(pr.getSort().getOrderFor("createdAt"))
                .isNotNull()
                .satisfies(order -> assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC));
    }

    @Test
    @DisplayName("blank sortBy → unsorted, page/size preserved")
    void unsorted() {
        PageRequest pr = PageRequests.toPageable(PageQuery.of(2, 25, null, null));
        assertThat(pr.getSort().isSorted()).isFalse();
        assertThat(pr.getPageNumber()).isEqualTo(2);
        assertThat(pr.getPageSize()).isEqualTo(25);
    }
}
