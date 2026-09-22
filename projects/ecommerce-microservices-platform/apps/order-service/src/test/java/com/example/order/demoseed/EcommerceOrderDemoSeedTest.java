package com.example.order.demoseed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.order.domain.model.OrderStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * TASK-MONO-710 — offline sentinel for the ecommerce demo order seed.
 *
 * <p><b>Why this exists.</b> Before this ticket, {@code infra/demo/seed/seed-ecommerce.sh} never
 * created an order. It picked one up with {@code GET /api/orders?size=1} and, finding none, emitted
 * a single {@code seed_warn} and moved on — which also silently skipped the shipping progression
 * and the review seed, because both hang off variables set inside that branch. Every run reported
 * "failures 0" while five console screens rendered as empty lists.
 *
 * <p>The seed now asserts its own post-conditions at runtime (orders &ge; 5, distinct statuses
 * &ge; 4, shipments &ge; 2, all {@code seed_fail}). That runtime gate is the real one — but it can
 * only fire against a running stack, so a PR that guts the seed merges green and the damage is
 * found in the next demo window. This test is the offline half: it goes red in CI with nothing
 * running.
 *
 * <p><b>It reads the artifacts, never a copy.</b> Following {@code FanArtistDemoSeedTest} in
 * iam-platform: the floors, slot numbers, statuses and endpoints below are parsed out of the seed
 * script itself and cross-checked against this module's own source. A test that restated them as
 * its own constants would verify the restatement — the exact failure mode it is here to prevent.
 *
 * <p><b>It is not a duplicate of the runtime gate.</b> The two predicates catch different
 * mutations. Example: if both {@code ship_progress} calls were "simplified" to the same final
 * target {@code DELIVERED}, no order would ever sit at {@code SHIPPED} — yet the runtime floor of
 * 4 distinct statuses would still pass immediately after seeding (CANCELLED + PENDING + CONFIRMED
 * + DELIVERED = 4) and would only start failing ~35 minutes later, once the stuck-detector
 * auto-cancels the PENDING order. {@link #theTwoShipmentsStopAtDifferentStates()} catches that
 * deterministically, with nothing running.
 */
@DisplayName("TASK-MONO-710 ecommerce order demo seed")
class EcommerceOrderDemoSeedTest {

    /**
     * Repo root, four levels up (order-service -&gt; apps -&gt; ecommerce-microservices-platform
     * -&gt; projects). The demo seed is shared infrastructure; this test only reads it.
     *
     * <p>Reading a file outside this module is invisible to Gradle's up-to-date check. This
     * module's {@code build.gradle} declares it as a {@code test} input AND ci.yml's
     * {@code ecommerce} path filter lists it — {@code scripts/check-outside-module-input-filter-
     * coverage.sh} asserts those two agree. Without both, editing only the seed leaves
     * {@code :test} UP-TO-DATE / the lane SKIPPED, and this guard reports green over exactly the
     * drift it exists to catch.
     */
    private static final Path SEED = Path.of("..", "..", "..", "..",
            "infra", "demo", "seed", "seed-ecommerce.sh");

    /** Sibling source in this module — the reason the seed must not use the admin status endpoint. */
    private static final Path ADMIN_STATUS_SERVICE = Path.of("src", "main", "java", "com", "example",
            "order", "application", "service", "AdminOrderStatusService.java");

    /**
     * Sibling source in this module — the order item DTO. It is the reason {@code variantId} is a
     * real key <em>here</em>, and the reason reading the PRODUCT response for that same key is a
     * bug rather than a typo: the two planes name the same concept differently on purpose.
     */
    private static final Path PLACE_ORDER_COMMAND = Path.of("src", "main", "java", "com", "example",
            "order", "application", "dto", "PlaceOrderCommand.java");

    /** {@code order_wait_status "$x" CONFIRMED SHIPPED DELIVERED;} */
    private static final Pattern WAIT_STATUS_CALL = Pattern.compile(
            "^\\s*(?:if\\s+)?order_wait_status\\s+\"[^\"]+\"\\s+([A-Z_ ]+?)\\s*;", Pattern.MULTILINE);

    /** {@code ship_progress "$SHIP_ID" "${SHIP_STATUS:-}" DELIVERED} */
    private static final Pattern SHIP_PROGRESS_CALL = Pattern.compile(
            "^\\s*ship_progress\\s+\"[^\"]+\"\\s+\"[^\"]+\"\\s+([A-Z_]+)\\s*$", Pattern.MULTILINE);

    /** {@code operator_token <tenant>} — every tenant this seed assumes, in file order. */
    private static final Pattern OPERATOR_TOKEN_TENANT = Pattern.compile(
            "operator_token ([a-z0-9-]+)");

    /** The single line that resolves a variant id out of the product-detail body: {@code vid="$(...)"} */
    private static final Pattern VID_EXTRACTION = Pattern.compile("^ *vid=.*$", Pattern.MULTILINE);

    /** {@code ORDER_SLOT_IDS[4]} — only the literal indices; the loop-counter form is a variable. */
    private static final Pattern LITERAL_SLOT = Pattern.compile("ORDER_SLOT_IDS\\[(\\d+)\\]");

    /** {@code for order_slot in 3 4 5;} */
    private static final Pattern SLOT_LOOP = Pattern.compile("for\\s+order_slot\\s+in\\s+([\\d ]+);");

    /** {@code -H "Idempotency-Key: demo-seed-order-$slot"} / {@code -H 'Idempotency-Key: ...'} */
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "Idempotency-Key:\\s*([^\"']+)[\"']");

    /** {@code dbexec --why "<reason>" \<newline> <container> psql ...} */
    private static final Pattern DBEXEC_CONTAINER = Pattern.compile(
            "dbexec\\s+--why\\s+\"[^\"]*\"\\s*\\\\?\\s*([a-z0-9-]+)\\s+(?:psql|mysql)");

    private static String seed() throws IOException {
        assertThat(SEED)
                .as("the ecommerce demo seed must be resolvable from this module (working dir = %s)",
                        Path.of("").toAbsolutePath())
                .exists();
        return Files.readString(SEED, StandardCharsets.UTF_8);
    }

    /** {@code if [ "${order_total:-0}" -lt 5 ]} — a post-condition floor, by shell variable name. */
    private static Matcher floorMatcher(String body, String var) {
        Matcher m = Pattern.compile(Pattern.quote("${" + var + ":-0}\"") + "\\s+-lt\\s+(\\d+)")
                .matcher(body);
        assertThat(m.find())
                .as("the seed's post-condition must still carry a numeric floor for '%s'. If that "
                        + "line vanished the runtime gate vanished with it, and every parse below "
                        + "would otherwise verify nothing.", var)
                .isTrue();
        return m;
    }

    private static int floor(String body, String var) {
        return Integer.parseInt(floorMatcher(body, var).group(1));
    }

    private static List<String> allMatches(Pattern p, String body, int group) {
        Matcher m = p.matcher(body);
        List<String> out = new ArrayList<>();
        while (m.find()) {
            out.add(m.group(group));
        }
        return out;
    }

    @Test
    @DisplayName("the seed places orders through POST /api/orders with a fixed Idempotency-Key")
    void ordersArePlacedThroughTheApiIdempotently() throws IOException {
        String body = seed();

        assertThat(body)
                .as("the seed must CREATE orders, not pick up whatever happens to exist. Deleting "
                        + "this call returns the demo to the state TASK-MONO-710 was filed for: "
                        + "/ecommerce/orders, orders/[id], /shippings and both settlement screens "
                        + "render as empty lists — and the shipping + review seeds go with them, "
                        + "because they hang off variables set only in that branch.")
                .contains("http POST \"$GW/api/orders\"");

        List<String> keys = allMatches(IDEMPOTENCY_KEY, body, 1);
        assertThat(keys)
                .as("zero parsed Idempotency-Key literals would make every assertion below "
                        + "vacuously true")
                .isNotEmpty();
        assertThat(keys)
                .as("order placement must carry a client key: a repeat POST with the same key and "
                        + "the same user returns the ORIGINAL order (order-api.md), which is the "
                        + "contract-native answer to 'the seed runs twice'. Without it, placement "
                        + "falls back to legacy non-idempotent behaviour and every run doubles the "
                        + "demo's orders.")
                .anyMatch(k -> k.startsWith("demo-seed-order-"));
        for (String key : keys) {
            assertThat(key)
                    .as("idempotency key '%s' must be a fixed literal — a per-run value makes the "
                            + "key useless and the seed non-idempotent (the coupon-issue block "
                            + "records that exact mistake in its own comment)", key)
                    .doesNotContain("$(")
                    .doesNotContain("RANDOM");
        }
    }

    @Test
    @DisplayName("the seed drives orders through the API only — no direct-DB order writes")
    void ordersAreNeverWrittenDirectlyToTheDatabase() throws IOException {
        String body = seed();

        List<String> containers = allMatches(DBEXEC_CONTAINER, body, 1);
        assertThat(containers)
                .as("this seed has at least one documented direct-DB block (the user-profile "
                        + "workaround). Parsing zero of them means this regex broke, not that the "
                        + "block is gone.")
                .isNotEmpty();
        assertThat(containers)
                .as("an order is a state machine. Inserting a mid/terminal state fills the screen "
                        + "and leaves NO outbox row, so no OrderPlaced / OrderConfirmed / "
                        + "PaymentCompleted is ever published and shipping, settlement and wms "
                        + "never learn the order exists (the ticket's Failure Scenario 1). lib.sh "
                        + "already encodes the policy — what can go in through the real API goes "
                        + "in through the real API — and guard (y) blocks raw psql outside lib.sh. "
                        + "The only permitted direct-DB target here is the user-profile "
                        + "workaround, and it carries its own --why.")
                .containsOnly("ecommerce-user-postgres");
    }

    @Test
    @DisplayName("the seed never sets order status through the admin endpoint")
    void theSeedDoesNotUseTheAdminOrderStatusEndpoint() throws IOException {
        String body = seed();

        // Match the CALL form this script uses ("$GW/..."), not the bare path: the seed's own
        // prose explains why that endpoint is unusable, and a naive substring check would trip
        // over that explanation instead of over a real call.
        assertThat(body)
                .as("POST /api/admin/orders/{id}/status cannot produce the states this seed needs. "
                        + "SHIPPED and DELIVERED are rejected there outright (400 "
                        + "INVALID_ORDER_REQUEST — the Order reaches them solely via the "
                        + "shipping-driven return leg, ADR-MONO-022 D7), and CONFIRMED there is "
                        + "worse than useless for a demo: see the sibling assertion below. Reading "
                        + "the admin LIST (/api/admin/orders?...) is a different thing and is what "
                        + "the post-condition does.")
                .doesNotContain("\"$GW/api/admin/orders/");

        String adminStatusService = Files.readString(ADMIN_STATUS_SERVICE, StandardCharsets.UTF_8);
        assertThat(adminStatusService)
                .as("AdminOrderStatusService.changeStatus(CONFIRMED) saves the row and publishes "
                        + "NOTHING — no OrderEventPublisher is injected at all. An operator "
                        + "'confirm' therefore creates no shipping record and books no settlement "
                        + "accrual, which is precisely the half-filled demo this ticket exists to "
                        + "avoid. If this assertion goes red because the service gained a "
                        + "publisher, the pin above is stale ON PURPOSE: re-read it and decide. Do "
                        + "not delete it to get green.")
                .doesNotContain("OrderEventPublisher");
    }

    @Test
    @DisplayName("every status the seed waits for is a real OrderStatus constant")
    void waitedForStatusesExistInTheDomain() throws IOException {
        String body = seed();

        Set<String> waited = new LinkedHashSet<>();
        for (String args : allMatches(WAIT_STATUS_CALL, body, 1)) {
            for (String token : args.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    waited.add(token);
                }
            }
        }
        assertThat(waited)
                .as("zero parsed order_wait_status arguments would pass the containment check "
                        + "below against an empty set. The seed must still wait for the "
                        + "payment -> confirm saga, which is asynchronous (two Kafka hops).")
                .isNotEmpty();

        Set<String> domain = new TreeSet<>();
        for (OrderStatus s : OrderStatus.values()) {
            domain.add(s.name());
        }
        assertThat(domain)
                .as("the seed polls for these states by name; a typo makes the poll spin for its "
                        + "whole budget and then fail for a reason that reads like an outage")
                .containsAll(waited);
    }

    @Test
    @DisplayName("the two shipments stop at different states, so one order actually sits at SHIPPED")
    void theTwoShipmentsStopAtDifferentStates() throws IOException {
        String body = seed();

        List<String> finals = allMatches(SHIP_PROGRESS_CALL, body, 1);
        assertThat(finals)
                .as("the shipping progression is one shared function called once per shipment. "
                        + "Parsing a number other than 2 means a shipment was added, dropped, or "
                        + "the call shape changed — either way this test is no longer measuring "
                        + "what it claims.")
                .hasSize(2);
        assertThat(new LinkedHashSet<>(finals))
                .as("both shipments walking to the same terminal is the mutation the RUNTIME "
                        + "post-condition cannot see: right after a seed run the distinct-status "
                        + "floor of 4 is still met (CANCELLED + PENDING + CONFIRMED + DELIVERED) "
                        + "and only breaks ~35 minutes later, when the stuck-detector auto-cancels "
                        + "the PENDING order. One shipment must stop at SHIPPED.")
                .containsExactlyInAnyOrder("SHIPPED", "DELIVERED");
    }

    @Test
    @DisplayName("the post-condition is an assertion, with floors the seed's own slots can satisfy")
    void thePostConditionFloorsAgreeWithTheSeededSlots() throws IOException {
        String body = seed();

        int orderFloor = floor(body, "order_total");
        int statusFloor = floor(body, "order_state_count");
        int shippingFloor = floor(body, "shipping_total");

        Set<Integer> slots = new TreeSet<>();
        for (String s : allMatches(LITERAL_SLOT, body, 1)) {
            slots.add(Integer.parseInt(s));
        }
        for (String loop : allMatches(SLOT_LOOP, body, 1)) {
            for (String token : loop.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    slots.add(Integer.parseInt(token));
                }
            }
        }

        assertThat(slots)
                .as("zero parsed order slots would make the comparison below vacuous")
                .isNotEmpty();
        assertThat(slots.size())
                .as("the seed's order floor is %d but the script only ever addresses %d distinct "
                        + "order slots %s. A floor the seed cannot reach fails every run, and a "
                        + "check that always fails gets switched off.",
                        orderFloor, slots.size(), slots)
                .isGreaterThanOrEqualTo(orderFloor);
        assertThat(orderFloor)
                .as("there cannot be more distinct statuses than there are orders")
                .isGreaterThanOrEqualTo(statusFloor);
        assertThat(statusFloor)
                .as("the floor measures 'not empty', not 'exactly how many'. It is deliberately 4 "
                        + "and not 5 because PENDING is transient BY DESIGN: OrderStuckDetector "
                        + "sweeps PENDING orders with no payment after a 1800s grace and "
                        + "auto-cancels them (PAYMENT_TIMEOUT). Requiring one floor per enum "
                        + "constant would make success break on any volume seeded more than ~35 "
                        + "minutes ago.")
                .isGreaterThanOrEqualTo(2)
                .isLessThan(OrderStatus.values().length);
        assertThat(shippingFloor)
                .as("the seed creates two shipments (one stopped at SHIPPED, one carried to "
                        + "DELIVERED); the floor must not claim more than the seed makes")
                .isBetween(1, 2);
    }

    @Test
    @DisplayName("each post-condition floor fails hard — seed_fail, never seed_warn")
    void eachFloorIsAHardFailure() throws IOException {
        String body = seed();

        for (String var : List.of("order_total", "order_state_count", "shipping_total")) {
            Matcher m = floorMatcher(body, var);
            // The branch body: from the floor comparison to the end of that `if`. Short and
            // bounded, so this cannot drift into the next block and pass on ITS seed_fail.
            int from = m.end();
            int to = body.indexOf("\n    fi\n", from);
            assertThat(to)
                    .as("could not find the end of the '%s' floor branch — the post-condition's "
                            + "shape changed and this parse is measuring the wrong region", var)
                    .isGreaterThan(from);
            String branch = body.substring(from, to);

            assertThat(branch)
                    .as("the '%s' floor must call seed_fail. A warning is exactly why nobody "
                            + "noticed the empty demo for this long: lib.sh's summary line then "
                            + "reads 'failures 0' and a broken run is indistinguishable from a "
                            + "healthy one (the same lesson TASK-MONO-535 already paid for).", var)
                    .contains("seed_fail");
            assertThat(branch)
                    .as("the '%s' floor must not be downgraded to seed_warn", var)
                    .doesNotContain("seed_warn");
        }
    }

    @Test
    @DisplayName("the variant id is read out of the product response's variants[], not a variantId key")
    void theVariantIdIsReadFromTheProductResponseShape() throws IOException {
        String body = seed();

        // The order plane really does call it `variantId` — that half is not in doubt, and this
        // module owns the proof.
        String placeOrder = Files.readString(PLACE_ORDER_COMMAND, StandardCharsets.UTF_8);
        assertThat(placeOrder)
                .as("if the order item DTO stopped carrying 'variantId', the seed's REQUEST body "
                        + "below would be wrong too and this cell would be guarding a dead name")
                .contains("String variantId");
        assertThat(body)
                .as("the seed must still SEND variantId in the order item — that is the order "
                        + "plane's key (PlaceOrderCommand.OrderItemCommand)")
                .contains("\\\"variantId\\\":\\\"$vid\\\"");

        Matcher m = VID_EXTRACTION.matcher(body);
        assertThat(m.find())
                .as("could not find the 'vid=' extraction line at all — this cell would otherwise "
                        + "pass vacuously over a seed that no longer resolves a variant")
                .isTrue();
        String vidLine = m.group(0);

        // 🔴🔴 The measured failure (2026-09-22 demo window). The first version of that line read
        //    grep -oE '"variantId":"[0-9a-f-]{36}"' — against `GET /api/products/{id}`, whose body
        //    names the same concept `variants[].id`. The expression matched nothing on every one
        //    of the five products, so `seed_fail` fired five times, NO order was ever created, and
        //    the shipping + review + settlement seeds silently went with them. The run still
        //    printed "실패 0" for the parts that did run, and five console screens stayed empty.
        //    ⇒ the bug was searching someone else's corpus with my own name for the thing.
        assertThat(vidLine)
                .as("the variant id must NOT be looked up by the order plane's key name in the "
                        + "PRODUCT response: `GET /api/products/{id}` has no 'variantId' key. "
                        + "Extraction line was: %s", vidLine)
                .doesNotContain("variantId");
        assertThat(vidLine)
                .as("the variant id must be taken from the product response's variants[] array — "
                        + "cut at the array so the product's own leading id is not picked up. "
                        + "Extraction line was: %s", vidLine)
                .contains("\"variants\":");
    }

    @Test
    @DisplayName("the post-condition prints a tenant CONTRAST — it must not only re-read what it wrote")
    void thePostConditionContrastsAgainstADifferentTenant() throws IOException {
        String body = seed();

        List<String> tenants = allMatches(OPERATOR_TOKEN_TENANT, body, 1);
        assertThat(tenants)
                .as("zero parsed `operator_token <tenant>` calls would make every assertion "
                        + "below vacuously true")
                .isNotEmpty();

        // 🔴🔴 TASK-MONO-718 AC-2. The floors above are read back with the SAME token the
        //    seed wrote with (`operator_token ecommerce`), so they are true by construction
        //    and say nothing about the tenant an operator actually has selected. The
        //    2026-09-22 window measured the cost of that blind spot — same instant, same
        //    URLs: tenant=ecommerce → orders 5 / products 24 / users 1 / sellers 2, and
        //    tenant=demo-corp → 0 / 0 / 0 / 0. The console was on `demo-corp`, nine of its
        //    fourteen ecommerce screens rendered as empty lists, and this post-condition
        //    was GREEN throughout.
        //
        // 🔵 The fix is not another floor — which tenant is "right" is TASK-MONO-718's owner
        //    decision, and today's 0 is design (TASK-BE-576), not a defect. The seed prints
        //    the contrast so a human reads the split instead of inheriting it.
        assertThat(new java.util.HashSet<>(tenants))
                .as("the seed must assume at least TWO distinct tenants: the one it writes "
                        + "with, and a different one to contrast the post-condition against. "
                        + "Parsed: %s — if they collapse to one, the post-condition is back to "
                        + "re-reading what it wrote and the empty-console split becomes "
                        + "invisible again.", tenants)
                .hasSizeGreaterThan(1);

        assertThat(body)
                .as("the contrast must reach the OPERATOR PLANE the console reads — "
                        + "contrasting on some other endpoint would measure a different thing")
                .contains("테넌트 대조");
    }
}
