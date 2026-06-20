package com.example.batch.application;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import com.example.batch.infrastructure.client.ProductServiceClient;
import com.example.batch.infrastructure.client.ProductServiceClient.ProductPageResponse;
import com.example.batch.infrastructure.client.ProductServiceClient.ProductSummary;
import com.example.batch.infrastructure.client.SearchServiceClient;
import com.example.batch.infrastructure.client.SearchServiceClient.SearchResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Job 3 — Elasticsearch index consistency spot-check (TASK-BE-409 / AC-3).
 *
 * <p><b>What it does:</b> Pages through all ON_SALE products from product-service (the
 * authority catalog) and, for each product, issues a name-keyword search against search-service.
 * If the product's UUID is absent from the search results, the product is counted as an
 * inconsistency and the {@code batch_index_inconsistencies_detected_total} Micrometer counter
 * is incremented.
 *
 * <p><b>Heuristic limitation — IMPORTANT:</b> The search-service API ({@code GET
 * /api/search/products?q=}) exposes only keyword search; there is NO full-index enumeration
 * endpoint. This means the check is a product-catalog-driven <em>spot-check heuristic</em>:
 * for each ON_SALE product we query the search index by product name and verify the product UUID
 * appears in the top results. This approach has two well-known false-positive sources:
 * <ol>
 *   <li><b>Relevance ranking:</b> a product may exist in the index but rank below the page
 *       boundary ({@code size=20} default) when its name matches many other products — it would
 *       be falsely counted as missing.</li>
 *   <li><b>Eventual consistency lag:</b> a recently-added product may not yet be indexed; the
 *       counter captures these as "suspected drift" rather than confirmed drift.</li>
 * </ol>
 * The metric should therefore be interpreted as "suspected drift count" rather than a precise
 * inconsistency guarantee. A confirmed full-reconciliation would require a dedicated enumeration
 * endpoint in search-service (not available in v1 — see AC-6 spec note in overview.md).
 *
 * <p><b>Idempotency:</b> read-only — no writes to any store (aside from history + metric).
 * Safe to re-run; repeated runs produce the same observable outcome.
 *
 * <p><b>ShedLock test bypass:</b> tests must call {@link #execute()} directly (not via the
 * scheduler) to avoid the {@code lockAtLeastFor} trap that silently no-ops subsequent invocations
 * within the lock window. The scheduler bean ({@link com.example.batch.scheduling.SearchIndexConsistencyScheduler})
 * is the thin shell; this class holds the logic.
 */
@Slf4j
@Service
public class SearchIndexConsistencyJob {

    static final String JOB_NAME = "searchIndexConsistencyCheckJob";
    static final String INCONSISTENCY_COUNTER_NAME = "batch_index_inconsistencies_detected_total";

    private static final int PAGE_SIZE = 50;

    /**
     * Absolute page-count backstop: if the loop somehow exceeds this many pages (e.g. due to an
     * unexpected API contract change), break and emit a WARN rather than spin forever.
     * 100_000 pages × PAGE_SIZE=50 = 5 million products — well above any realistic catalog size.
     */
    private static final int MAX_PAGES_BACKSTOP = 100_000;

    private final ProductServiceClient productServiceClient;
    private final SearchServiceClient searchServiceClient;
    private final BatchJobExecutionRepository executionRepository;
    private final Counter inconsistencyCounter;

    @Value("${batch.jobs.search-consistency.enabled:true}")
    private boolean enabled;

    public SearchIndexConsistencyJob(
            ProductServiceClient productServiceClient,
            SearchServiceClient searchServiceClient,
            BatchJobExecutionRepository executionRepository,
            MeterRegistry meterRegistry) {
        this.productServiceClient = productServiceClient;
        this.searchServiceClient = searchServiceClient;
        this.executionRepository = executionRepository;
        this.inconsistencyCounter = meterRegistry.counter(INCONSISTENCY_COUNTER_NAME);
    }

    /**
     * Execute one full consistency check pass over all ON_SALE products.
     *
     * <p>History lifecycle: {@code RUNNING → COMPLETED} on success, {@code RUNNING → FAILED}
     * on any unrecoverable exception. Exceptions are swallowed after recording FAILED history
     * so that the scheduler thread is never terminated by a job failure (overview.md invariant 2).
     */
    public void execute() {
        if (!enabled) {
            log.info("searchIndexConsistencyCheckJob is disabled via batch.jobs.search-consistency.enabled=false; skipping.");
            return;
        }

        BatchJobExecution execution = executionRepository.save(BatchJobExecution.start(JOB_NAME));
        log.info("SearchIndexConsistencyJob started (executionId={})", execution.getId());

        try {
            long totalDrift = runCheck();
            execution.complete();
            executionRepository.save(execution);
            log.info("SearchIndexConsistencyJob completed (executionId={} suspectedDrift={})",
                    execution.getId(), totalDrift);
        } catch (Exception e) {
            // B-4 fix: guard for blank message — e.getMessage() may be non-null but "" (blank),
            // which would cause BatchJobExecution.fail() to throw IllegalArgumentException,
            // escaping this catch block and breaking exception isolation (overview.md invariant 2).
            String errorMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getName();
            execution.fail(errorMsg);
            executionRepository.save(execution);
            log.error("SearchIndexConsistencyJob FAILED (executionId={}): {}",
                    execution.getId(), errorMsg, e);
            // Do NOT re-throw — failed jobs must not block the scheduler thread (overview.md invariant 2).
        }
    }

    /**
     * Core pagination + spot-check loop. Returns the total suspected inconsistency count.
     *
     * <p><b>Termination logic (B-2 fix):</b>
     * <ol>
     *   <li>{@code totalPages} is snapshotted from the first page response before the loop starts.
     *       This prevents the "catalog grows mid-run" infinite-loop risk where
     *       {@code consumedSoFar >= totalElements} might never fire if every page returns exactly
     *       PAGE_SIZE items and totalElements keeps growing.</li>
     *   <li>{@code products.isEmpty()} remains as a hard secondary terminator (defensive guard
     *       against a server that returns empty content before page >= totalPages).</li>
     *   <li>An absolute backstop ({@link #MAX_PAGES_BACKSTOP}) breaks the loop and emits WARN
     *       if page count exceeds a sane upper bound, ensuring no input can spin forever.</li>
     * </ol>
     */
    private long runCheck() {
        // Fetch the first page to snapshot totalPages before the loop.
        ProductPageResponse firstResponse = productServiceClient.listOnSale(0, PAGE_SIZE);
        List<ProductSummary> firstProducts = firstResponse.content();

        if (firstProducts.isEmpty()) {
            log.info("SearchIndexConsistencyJob check complete: totalProducts=0 suspectedDrift=0");
            return 0L;
        }

        // Snapshot totalPages once — prevents mid-run catalog growth from blocking termination.
        int totalPages = (int) Math.ceil((double) firstResponse.totalElements() / PAGE_SIZE);
        log.debug("SearchIndexConsistencyJob: totalElements={} totalPages={}",
                firstResponse.totalElements(), totalPages);

        int page = 0;
        long totalProducts = 0;
        long totalDrift = 0;
        List<ProductSummary> products = firstProducts;

        while (true) {
            // Hard secondary terminator: empty page means no more data regardless of page count.
            if (products.isEmpty()) {
                break;
            }

            totalProducts += products.size();

            for (ProductSummary product : products) {
                boolean foundInSearch = isProductFoundInSearch(product);
                if (!foundInSearch) {
                    totalDrift++;
                    inconsistencyCounter.increment();
                    log.warn("SearchIndexConsistencyJob: product not found in search index " +
                            "(productId={} name={}). Note: this is a heuristic spot-check — " +
                            "see class-level javadoc for false-positive sources.",
                            product.id(), product.name());
                }
            }

            page++;

            // Primary terminator: all pages from the snapshotted total consumed.
            if (page >= totalPages) {
                break;
            }

            // Absolute backstop: no input can cause an infinite loop regardless of API behavior.
            if (page >= MAX_PAGES_BACKSTOP) {
                log.warn("SearchIndexConsistencyJob: page count exceeded backstop limit " +
                        "(page={} MAX_PAGES_BACKSTOP={}). Terminating early to prevent runaway loop. " +
                        "This should not happen under normal conditions — investigate API contract.",
                        page, MAX_PAGES_BACKSTOP);
                break;
            }

            products = productServiceClient.listOnSale(page, PAGE_SIZE).content();
        }

        log.info("SearchIndexConsistencyJob check complete: totalProducts={} suspectedDrift={}",
                totalProducts, totalDrift);
        return totalDrift;
    }

    /**
     * Spot-check: query search-service by product name and verify the product UUID appears in
     * the results.
     *
     * <p><b>Heuristic:</b> this uses the name-keyword search endpoint which ranks results by
     * relevance. A product that exists in the index but ranks below position {@code size=20} will
     * be treated as absent. This is an intentional trade-off given that no full-enumeration
     * endpoint exists in v1 search-service (see class-level javadoc and AC-6 spec note).
     */
    private boolean isProductFoundInSearch(ProductSummary product) {
        SearchResponse searchResponse = searchServiceClient.searchByName(product.name());
        return searchResponse.content().stream()
                .anyMatch(hit -> product.id().equals(hit.productId()));
    }
}
