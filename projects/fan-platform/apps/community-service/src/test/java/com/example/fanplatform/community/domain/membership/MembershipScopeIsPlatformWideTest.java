package com.example.fanplatform.community.domain.membership;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-003 (ACCEPTED — B) reopen trigger — TASK-FAN-BE-047 AC-4.
 *
 * <p>ADR-003 decided that any author may publish at any visibility tier, including a fan
 * publishing a gated {@code FAN_POST}. That decision is <strong>provisional on exactly one
 * fact</strong>: memberships are <strong>platform-scoped</strong>. {@link MembershipChecker}
 * answers "does this account hold tier T in this tenant" — the <em>author</em> appears nowhere
 * in it. That is why a fan's {@code PREMIUM} post earns its author nothing, and why ADR-003
 * could reasonably call the state odd-but-harmless.
 *
 * <p>The ADR wrote the reopen condition as prose: <em>"if per-artist membership arrives, this
 * ADR is superseded"</em>. Prose does not fire. Nothing in the repository evaluated that
 * sentence, so it could stay technically true forever while the premise underneath it quietly
 * changed — the failure mode this repository has named repeatedly: a condition nobody computes
 * is not a condition. This test is that sentence given something it can fail on.
 *
 * <p><strong>If this test goes RED, it is not a bug — it is the trigger firing.</strong> An
 * artist/author axis on the membership port means a fan's gated post now has a real audience
 * and a real payer, which is precisely the world in which ADR-003's answer changes. Do not
 * "fix" the assertion. Reopen ADR-003 and decide again, then delete or rewrite this test as
 * part of that decision.
 *
 * <p>🔴 This asserts the <em>shape of the port</em>, not a behaviour, and that is deliberate.
 * A behavioural test ("a fan can publish PREMIUM") would stay green through the very change
 * this is watching for — per-artist membership does not stop a fan publishing, it changes what
 * publishing <em>means</em>. The observable that moves is the port's dependence on an author.
 */
class MembershipScopeIsPlatformWideTest {

    /**
     * Parameter names are not retained in bytecode without {@code -parameters}, so the axis is
     * read from the parameter TYPES and the method arity — the two things that must change if
     * an artist/author identity is threaded through the port.
     */
    private static Method theOnlyMethod() {
        List<Method> declared = Arrays.stream(MembershipChecker.class.getDeclaredMethods())
                .filter(m -> !m.isSynthetic())
                .toList();
        assertThat(declared)
                .as("MembershipChecker is a single-method port; a new method is itself a "
                        + "change of shape worth reading ADR-003 before making")
                .hasSize(1);
        return declared.get(0);
    }

    @Test
    @DisplayName("ADR-003 trigger: the membership port takes no artist/author axis — "
            + "RED here means reopen ADR-003, not fix this test")
    void membershipPortHasNoAuthorAxis() {
        Method hasAccess = theOnlyMethod();

        assertThat(hasAccess.getName()).isEqualTo("hasAccess");
        assertThat(hasAccess.getReturnType()).isEqualTo(boolean.class);

        assertThat(hasAccess.getParameterTypes())
                .as("""
                        ADR-003 (ACCEPTED — B) rests on memberships being PLATFORM-scoped: \
                        hasAccess(accountId, tier, tenantId) asks about the READER only. \
                        A fourth parameter — or any parameter carrying an artist/author — \
                        means membership became per-artist, a fan's gated post gained a real \
                        audience, and ADR-003's premise is gone. Reopen the ADR.""")
                .containsExactly(String.class, String.class, String.class);
    }
}
