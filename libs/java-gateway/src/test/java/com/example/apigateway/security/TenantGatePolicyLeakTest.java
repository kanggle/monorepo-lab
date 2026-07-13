package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.security.oauth2.TenantClaimValidator;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * This suite used to assert <em>where the opening switch isn't</em>.
 *
 * <p>{@code acceptAnyWellFormedTenant} admitted any non-blank {@code tenant_id}, and it was the
 * only switch in the library that opened a gate rather than narrowing one. Exactly one caller had
 * it — ecommerce, the marketplace edge — so this test named the three edges that must never turn
 * it on, on the theory that a flag which opens an edge needs a test saying where it isn't.
 *
 * <p><strong>The switch is gone (TASK-MONO-388).</strong> ecommerce needed to admit a
 * customer-tenant operator whose {@code tenant_id} names their own tenant — but that is
 * {@code trustEntitledDomains}, the same question the other four edges ask. "Any well-formed
 * tenant" answered a weaker one, and let a token entitled only to some other domain through
 * (TASK-BE-506).
 *
 * <p>So the proposition this suite defends gets <em>stronger</em>, and it is restated rather than
 * deleted: <strong>the library has no switch that opens a gate, and no edge admits a stranger.</strong>
 * The first half is asserted against the builder's own API surface, so reintroducing the switch —
 * under any name — turns this red. The second half is asserted as behaviour, against every gate
 * policy in the fleet, ecommerce included.
 */
@DisplayName("테넌트 게이트 — 문을 여는 스위치는 존재하지 않고, 어떤 엣지도 낯선 테넌트를 들이지 않는다")
class TenantGatePolicyLeakTest {

    // The fleet's five gate policies, reconstructed here rather than imported: libs/ may not
    // depend on a service module. Each domain suite asserts that its gateway really wires the
    // policy named below (they build their validator from the production
    // OAuth2ResourceServerConfig#tenantGate()); this test asserts what those policies then mean.
    private static final TenantClaimValidator WMS =
            TenantClaimValidator.forTenant("wms").trustEntitledDomains().build();
    private static final TenantClaimValidator SCM = TenantClaimValidator.forTenant("scm")
            .allowSuperAdminWildcard()
            .trustEntitledDomains()
            .build();
    private static final TenantClaimValidator FAN = TenantClaimValidator.forTenant("fan-platform")
            .allowSuperAdminWildcard()
            .build();
    private static final TenantClaimValidator ECOMMERCE = TenantClaimValidator.forTenant("ecommerce")
            .allowSuperAdminWildcard()
            .trustEntitledDomains()
            .build();

    private static Stream<TenantClaimValidator> everyEdge() {
        return Stream.of(WMS, SCM, FAN, ECOMMERCE);
    }

    /**
     * The structural half: the builder's switch set is <strong>pinned</strong>.
     *
     * <p>Asserted against the API surface rather than a call site, because the failure this guards
     * is "someone adds an opening switch back" — and a call-site assertion cannot see a switch
     * nobody has called yet.
     *
     * <p><strong>It pins the whole set rather than pattern-matching names</strong>, and the first
     * draft of this test is why. That draft flagged any method whose name contained
     * {@code any} / {@code every} / {@code all} — and {@code allowSuperAdminWildcard} contains
     * "all", inside "allow". A guard that goes red on the first honest run gets switched off, and
     * a switched-off job's skip reports green (TASK-MONO-360). A pinned set has no false
     * positives: adding a switch under <em>any</em> name turns this red and makes someone say out
     * loud what it does to the gate.
     */
    @Test
    @DisplayName("빌더의 스위치 집합이 고정돼 있다 — 어떤 이름으로든 새 스위치를 넣으면 빨개진다")
    void theBuilderSwitchSetIsPinned() {
        List<String> actual = Arrays.stream(TenantClaimValidator.Builder.class.getDeclaredMethods())
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .filter(m -> !m.isSynthetic())
                .map(java.lang.reflect.Method::getName)
                .distinct()
                .sorted()
                .toList();

        assertThat(actual)
                .as("the shared tenant gate's switch set changed. Every switch here must NARROW "
                        + "the gate; the one that opened it (acceptAnyWellFormedTenant) was removed "
                        + "in TASK-MONO-388 because the marketplace edge does not need one — it "
                        + "reaches its edge through entitlement, like every other domain. If you "
                        + "are adding a switch, say here what it does to the gate.")
                .containsExactly("allowSuperAdminWildcard", "build", "trustEntitledDomains");
    }

    /**
     * The behavioural half, and the one that would still bite if the switch came back under a name
     * this test does not recognise: a stranger's tenant is refused at <em>every</em> edge.
     */
    @Test
    @DisplayName("낯선 테넌트 토큰 — 마켓플레이스를 포함해 모든 엣지가 거부한다")
    void aStrangersTenantIsRefusedAtEveryEdge() {
        Jwt stranger = jwt("some-other-tenant", null);
        everyEdge().forEach(edge ->
                assertThat(edge.validate(stranger).hasErrors())
                        .as("a token naming an unrelated tenant must not pass any edge")
                        .isTrue());
    }

    /**
     * The stranger that used to get in. ecommerce admitted this token before TASK-MONO-388 —
     * a tenant entitled to some <em>other</em> domain, which is exactly TASK-BE-506's hole.
     */
    @Test
    @DisplayName("다른 도메인에만 구독된 테넌트 — ecommerce 를 포함해 모든 엣지가 거부한다")
    void aTenantEntitledElsewhereIsRefusedAtEveryEdge() {
        Jwt entitledElsewhere = jwt("fan-platform", List.of("fan-platform", "wms"));
        assertThat(ECOMMERCE.validate(entitledElsewhere).hasErrors())
                .as("ecommerce admitted this before TASK-MONO-388 — that was TASK-BE-506's hole")
                .isTrue();
        assertThat(SCM.validate(entitledElsewhere).hasErrors()).as("scm").isTrue();
    }

    /** Entitlement is what opens an edge — for every edge that trusts it, ecommerce included. */
    @Test
    @DisplayName("구독한 테넌트는 통과한다 — 거부만 단언하면 항상-거부 게이트도 초록이다")
    void anEntitledTenantIsAdmitted() {
        assertThat(ECOMMERCE.validate(jwt("acme-corp", List.of("ecommerce"))).hasErrors())
                .as("a customer-tenant operator running their own store (ADR-MONO-030 D1-A)")
                .isFalse();
        assertThat(WMS.validate(jwt("acme-corp", List.of("wms"))).hasErrors()).as("wms").isFalse();
        assertThat(SCM.validate(jwt("acme-corp", List.of("scm"))).hasErrors()).as("scm").isFalse();
        // fan does not trust entitlement (the branch would be dead code) — so it refuses, and
        // that difference is policy, not drift.
        assertThat(FAN.validate(jwt("acme-corp", List.of("fan-platform"))).hasErrors())
                .as("fan sits outside the entitlement plane")
                .isTrue();
    }

    /** A blank / absent / non-string claim never admits anyone, at any edge. */
    @Test
    @DisplayName("blank / 부재 / 비-문자열 tenant_id 는 어떤 엣지도 통과하지 못한다")
    void malformedTenantClaimIsRefusedAtEveryEdge() {
        for (Object malformed : new Object[] {"   ", null, 42}) {
            Jwt bad = jwt(malformed, null);
            everyEdge().forEach(edge ->
                    assertThat(edge.validate(bad).hasErrors())
                            .as("malformed tenant_id: %s", malformed)
                            .isTrue());
        }
    }

    private static Jwt jwt(Object tenantId, List<String> entitledDomains) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (tenantId != null) {
            b.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        if (entitledDomains != null) {
            b.claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, entitledDomains);
        }
        return b.build();
    }
}
