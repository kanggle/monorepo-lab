package com.wms.master.integration.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Collision-free codes for the master-service integration + contract suites (TASK-BE-499).
 *
 * <p><strong>Why a counter and not {@code Math.random()}.</strong> Every integration and
 * contract class extends {@code MasterServiceIntegrationBase}, whose Postgres container is
 * {@code static} — so the whole module shares <em>one</em> database, and nothing truncates
 * it between tests (the data is created over real HTTP, so there is no rollback either).
 * Warehouses therefore accumulate for the length of the run.
 *
 * <p>{@code warehouseCode} is capped by the domain at {@code ^WH\d{2,3}$}
 * ({@code Warehouse.CODE_PATTERN}, {@code CreateWarehouseRequest}), so the code space is
 * ~1000 values and cannot be widened to a UUID. The suites used to draw from it randomly
 * ({@code 10 + Math.random() * 890}), which is a birthday problem: with dozens of
 * warehouses accumulating in one database, a repeat is a matter of probability, and a
 * repeated code is rejected as {@code WAREHOUSE_CODE_DUPLICATE} → <strong>409</strong>.
 * That surfaced as an intermittent CI failure inside a <em>seed helper</em>
 * ({@code expected: 201 CREATED but was: 409 CONFLICT}), which reads like a logic bug but
 * is really two tests picking the same name.
 *
 * <p>A monotonic counter cannot repeat within its range, so the collision is removed by
 * construction rather than made less likely. Exhaustion throws instead of wrapping —
 * silently wrapping would reintroduce exactly the defect this class exists to remove.
 *
 * <p>The emitted suffix is three digits, which satisfies both {@code ^WH\d{2,3}$} and
 * {@code ^Z-[A-Z0-9]+$} (zone codes), so one sequence serves warehouse, zone and SKU codes.
 */
public final class TestCodes {

    /** First suffix. Three digits keeps every emitted code inside {@code ^WH\d{2,3}$}. */
    private static final int FIRST = 100;

    /** Last suffix. {@code ^WH\d{2,3}$} admits no more than three digits. */
    private static final int LAST = 999;

    /**
     * One sequence for the whole test JVM. It must be shared across classes: they all
     * write to the same database, so a per-class counter would hand {@code WH100} to
     * {@code LocationIntegrationTest} and {@code WH100} to {@code WarehouseIntegrationTest}
     * and collide anyway.
     */
    private static final TestCodes SHARED = new TestCodes(FIRST, LAST);

    private final AtomicInteger seq;
    private final int last;

    /** Package-private so {@link TestCodesSelfTest} can exercise a small, exhaustible range. */
    TestCodes(int first, int last) {
        this.seq = new AtomicInteger(first);
        this.last = last;
    }

    /**
     * A suffix no other caller in this JVM has been given.
     *
     * @throws IllegalStateException if the code space is exhausted — see the class comment
     *     for why this is not allowed to wrap
     */
    public static String uniqueSuffix() {
        return SHARED.next();
    }

    String next() {
        int n = seq.getAndIncrement();
        if (n > last) {
            throw new IllegalStateException(
                    "Test code space exhausted (> WH" + last + "). The domain caps warehouseCode "
                            + "at ^WH\\d{2,3}$, so the suite cannot create more than "
                            + (last - FIRST + 1) + " coded aggregates in one JVM. Reuse codes "
                            + "deliberately, or shrink the suite — do NOT wrap, that reintroduces "
                            + "the collision this class removes (TASK-BE-499).");
        }
        return String.valueOf(n);
    }
}
