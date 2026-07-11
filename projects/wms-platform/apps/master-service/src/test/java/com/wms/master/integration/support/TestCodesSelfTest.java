package com.wms.master.integration.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Self-test for {@link TestCodes} that runs in the default {@code test} phase without
 * Docker (TASK-BE-499).
 *
 * <p><strong>This is the actual proof of the fix.</strong> The defect it replaces
 * ({@code Math.random()} over 890 values, against one shared database) is
 * <em>probabilistic</em> — the broken suite passed most runs. So a green CI run proves
 * nothing about the fix; what has to be shown is the property itself: the generator
 * <em>cannot</em> emit a duplicate within its range, and it refuses to wrap when the range
 * runs out.
 */
class TestCodesSelfTest {

    @Test
    @DisplayName("suffixes are unique — the property the old Math.random() generator lacked")
    void suffixesAreUnique() {
        TestCodes codes = new TestCodes(100, 999);

        List<String> emitted = IntStream.range(0, 900).mapToObj(i -> codes.next()).toList();

        assertThat(emitted).doesNotHaveDuplicates();
        assertThat(Set.copyOf(emitted)).hasSize(900);
    }

    @Test
    @DisplayName("every suffix satisfies the domain patterns ^WH\\d{2,3}$ and ^Z-[A-Z0-9]+$")
    void suffixesSatisfyDomainPatterns() {
        TestCodes codes = new TestCodes(100, 999);

        for (int i = 0; i < 900; i++) {
            String suffix = codes.next();
            assertThat("WH" + suffix)
                    .as("warehouseCode must match Warehouse.CODE_PATTERN")
                    .matches("^WH\\d{2,3}$");
            assertThat("Z-" + suffix)
                    .as("zoneCode must match Zone.CODE_PATTERN")
                    .matches("^Z-[A-Z0-9]+$");
        }
    }

    @Test
    @DisplayName("exhaustion throws instead of wrapping — wrapping would restore the collision")
    void exhaustionThrows() {
        TestCodes codes = new TestCodes(100, 102);

        assertThat(codes.next()).isEqualTo("100");
        assertThat(codes.next()).isEqualTo("101");
        assertThat(codes.next()).isEqualTo("102");

        assertThatThrownBy(codes::next)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exhausted");
    }

    @Test
    @DisplayName("concurrent callers never receive the same suffix (AtomicInteger, not int++)")
    void concurrentCallersGetDistinctSuffixes() throws Exception {
        TestCodes codes = new TestCodes(100, 999);
        int threads = 8;
        int perThread = 100;

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<List<String>>> jobs = IntStream.range(0, threads)
                    .mapToObj(t -> (Callable<List<String>>) () ->
                            IntStream.range(0, perThread).mapToObj(i -> codes.next()).toList())
                    .toList();

            Set<String> all = new HashSet<>();
            int total = 0;
            for (Future<List<String>> f : pool.invokeAll(jobs)) {
                List<String> batch = f.get();
                total += batch.size();
                all.addAll(batch);
            }

            assertThat(total).isEqualTo(threads * perThread);
            assertThat(all)
                    .as("every concurrently-issued suffix is distinct")
                    .hasSize(threads * perThread);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("the shared JVM-wide sequence also hands out distinct suffixes")
    void sharedSequenceIsUnique() {
        List<String> emitted =
                IntStream.range(0, 50).mapToObj(i -> TestCodes.uniqueSuffix()).toList();

        assertThat(emitted).doesNotHaveDuplicates();
    }
}
