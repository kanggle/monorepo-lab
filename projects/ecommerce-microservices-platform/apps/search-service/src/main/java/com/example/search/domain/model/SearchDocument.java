package com.example.search.domain.model;

/**
 * Elasticsearch index document for a searchable product.
 *
 * <p><b>Multi-tenancy (TASK-BE-404, ADR-MONO-030 Step 4 facet c):</b>
 * {@code tenantId} is a keyword field on every indexed document, populated from
 * the inbound product event's {@code tenant_id} envelope field (set by
 * product-service since TASK-BE-357). Every search query adds a mandatory
 * {@code term} filter on this field (see {@code ElasticsearchQueryAdapter}).
 * A missing {@code tenantId} on a pre-existing document (indexed before this
 * change) is coalesced to the default tenant {@code "ecommerce"} at query time —
 * no destructive reindex required; the document will be restamped on the next
 * product event re-sync.
 */
public record SearchDocument(
        String productId,
        String name,
        String description,
        long price,
        String status,
        String categoryId,
        int totalStock,
        String thumbnailUrl,
        Double score,
        String tenantId
) {
    /**
     * Legacy factory without tenantId — resolves to default tenant.
     * Used by existing test/call-sites that pre-date multi-tenancy.
     */
    public static SearchDocument of(
            String productId,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            int totalStock
    ) {
        return new SearchDocument(productId, name, description, price, status, categoryId, totalStock, null, null, "ecommerce");
    }

    /** Factory without tenantId — resolves to default tenant. */
    public static SearchDocument of(
            String productId,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            int totalStock,
            String thumbnailUrl
    ) {
        return new SearchDocument(productId, name, description, price, status, categoryId, totalStock, thumbnailUrl, null, "ecommerce");
    }

    /** Primary factory — all fields including tenantId. */
    public static SearchDocument of(
            String productId,
            String name,
            String description,
            long price,
            String status,
            String categoryId,
            int totalStock,
            String thumbnailUrl,
            String tenantId
    ) {
        return new SearchDocument(productId, name, description, price, status, categoryId, totalStock, thumbnailUrl, null, tenantId);
    }
}
