package com.example.messaging.outbox;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Helper that adapts a Spring Data {@link JpaRepository} on a service-specific
 * outbox entity into the generic {@link OutboxRowRepository} contract consumed
 * by {@link AbstractOutboxPublisher}.
 *
 * <p>Services declare a Spring Data interface on their entity ({@code R extends OutboxRow})
 * with two query methods:
 * <ul>
 *   <li>{@code List<R> findPending(Pageable pageable)} — pending rows ordered
 *       ascending by their natural created-at order</li>
 *   <li>{@code long countByPublishedAtIsNull()} — for the pending-count gauge</li>
 * </ul>
 *
 * <p>To wire it up, services call {@link #wrap} and pass the publisher the
 * resulting {@link OutboxRowRepository}.
 */
public final class SpringDataOutboxRowRepository {

    private SpringDataOutboxRowRepository() {
    }

    /**
     * Wrap a Spring Data backend in the publisher-facing {@link OutboxRowRepository}
     * contract. The {@code finder} and {@code counter} lambdas typically reference
     * the corresponding methods on the Spring Data interface
     * ({@code repository::findPending}, {@code repository::countByPublishedAtIsNull}).
     */
    public static <R extends OutboxRow> OutboxRowRepository<R> wrap(JpaRepository<R, UUID> backend,
                                                                     PendingFinder<R> finder,
                                                                     PendingCounter counter) {
        return new OutboxRowRepository<>() {
            @Override
            public List<R> findPending(int batchSize) {
                return finder.findPending(PageRequest.of(0, batchSize));
            }

            @Override
            public R findById(UUID id) {
                return backend.findById(id).orElse(null);
            }

            @Override
            public void save(R row) {
                backend.save(row);
            }

            @Override
            public long countPending() {
                return counter.countPending();
            }
        };
    }

    /** Adapter for {@code List<R> findPending(Pageable)} on the Spring Data repo. */
    @FunctionalInterface
    public interface PendingFinder<R extends OutboxRow> {
        List<R> findPending(Pageable pageable);
    }

    /** Adapter for {@code long countByPublishedAtIsNull()} on the Spring Data repo. */
    @FunctionalInterface
    public interface PendingCounter {
        long countPending();
    }
}
