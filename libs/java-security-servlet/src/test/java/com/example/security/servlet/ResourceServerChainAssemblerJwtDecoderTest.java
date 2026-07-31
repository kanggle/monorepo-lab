package com.example.security.servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * The decoder half of the promoted assembly (ADR-MONO-058 § D4), tested as a mechanism.
 *
 * <p>No issuer, tenant id or property key here is any service's real value — every one is a synthetic
 * fixture. What is asserted is the <em>shape</em> of the chain the builder assembles: which validators
 * run, in what order, and what happens when the caller omits the one input that must not be omitted.
 */
@DisplayName("ResourceServerChainAssembler.jwtDecoder — decoder + validator chain assembly")
class ResourceServerChainAssemblerJwtDecoderTest {

    private static final String JWKS_URI = "https://issuer.example/oauth2/jwks";
    private static final String ISSUER = "https://issuer.example";
    private static final String OTHER_ISSUER = "https://elsewhere.example";

    /** The error code {@code JwtTimestampValidator} and {@code JwtValidators.createDefault()} emit. */
    private static final String TIMESTAMP_ERROR_CODE = "invalid_token";

    /** The error code {@code AllowedIssuersValidator} emits. */
    private static final String ISSUER_ERROR_CODE = "invalid_issuer";

    private static Jwt token(String issuer, Instant expiresAt) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .claim("iss", issuer)
                .issuedAt(expiresAt.minusSeconds(600))
                .expiresAt(expiresAt)
                .build();
    }

    private static Jwt validToken() {
        return token(ISSUER, Instant.now().plusSeconds(600));
    }

    /**
     * An expiry far enough in the past to clear {@code JwtTimestampValidator}'s default 60-second
     * clock skew. A first draft used {@code now - 60s} and the timestamp validator passed it, so the
     * two ordering assertions below read "issuer error first" and looked like a wrong chain order
     * rather than a fixture that was not actually expired.
     */
    private static Instant expiredLongAgo() {
        return Instant.now().minusSeconds(3600);
    }

    /** A validator that always fails with the given code — a probe for chain position. */
    private static OAuth2TokenValidator<Jwt> alwaysFailing(String code) {
        return jwt -> OAuth2TokenValidatorResult.failure(new OAuth2Error(code, code + " rejected", null));
    }

    private static List<String> errorCodes(OAuth2TokenValidator<Jwt> chain, Jwt jwt) {
        return chain.validate(jwt).getErrors().stream().map(OAuth2Error::getErrorCode).toList();
    }

    @Nested
    @DisplayName("buildValidator() — the chain")
    class ValidatorChain {

        @Test
        @DisplayName("a token with an allowed issuer and a future expiry passes with no errors")
        void happyPath() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuers(Set.of(ISSUER))
                    .buildValidator();

            assertThat(chain.validate(validToken()).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("an issuer outside the allow-list is rejected by the issuer validator")
        void unknownIssuerRejected() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuers(Set.of(ISSUER))
                    .buildValidator();

            assertThat(errorCodes(chain, token(OTHER_ISSUER, Instant.now().plusSeconds(600))))
                    .containsExactly(ISSUER_ERROR_CODE);
        }

        @Test
        @DisplayName("supplied validators run AFTER the issuer check and in call order")
        void suppliedValidatorsRunAfterIssuerCheckInCallOrder() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuers(Set.of(ISSUER))
                    .validator(alwaysFailing("first_supplied"))
                    .validator(alwaysFailing("second_supplied"))
                    .buildValidator();

            // The token's issuer is wrong too, so the issuer error is present and its POSITION is what
            // this asserts. DelegatingOAuth2TokenValidator accumulates rather than short-circuits, so
            // every failing delegate contributes exactly one entry, in chain order.
            assertThat(errorCodes(chain, token(OTHER_ISSUER, Instant.now().plusSeconds(600))))
                    .containsExactly(ISSUER_ERROR_CODE, "first_supplied", "second_supplied");
        }

        @Test
        @DisplayName("the timestamp check runs FIRST — before the issuer check")
        void timestampRunsFirst() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuers(Set.of(ISSUER))
                    .buildValidator();

            // Expired AND wrong issuer: the first error decides what a caller's entry point reports.
            List<String> codes = errorCodes(chain, token(OTHER_ISSUER, expiredLongAgo()));

            assertThat(codes).first().isEqualTo(TIMESTAMP_ERROR_CODE);
            assertThat(codes).element(1).isEqualTo(ISSUER_ERROR_CODE);
        }

        @Test
        @DisplayName("the Spring-Security defaults run LAST — the duplicate timestamp check is preserved")
        void defaultsRunLastAndTimestampIsDuplicated() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuers(Set.of(ISSUER))
                    .validator(alwaysFailing("supplied"))
                    .buildValidator();

            // Expired, allowed issuer: the explicit JwtTimestampValidator fails, the supplied validator
            // fails, and JwtValidators.createDefault()'s own timestamp validator fails again at the end.
            // Every copy of this assembly carried that duplicate; dropping it would drop one entry from
            // the accumulated error list a caller's entry point walks.
            assertThat(errorCodes(chain, token(ISSUER, expiredLongAgo())))
                    .containsExactly(TIMESTAMP_ERROR_CODE, "supplied", TIMESTAMP_ERROR_CODE);
        }

        @Test
        @DisplayName("with no supplied validators the chain is exactly timestamp + issuer + defaults")
        void chainWithoutSuppliedValidators() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuers(Set.of(ISSUER))
                    .buildValidator();

            assertThat(errorCodes(chain, token(OTHER_ISSUER, expiredLongAgo())))
                    .containsExactly(TIMESTAMP_ERROR_CODE, ISSUER_ERROR_CODE, TIMESTAMP_ERROR_CODE);
        }
    }

    @Nested
    @DisplayName("allowedIssuersCsv — the parseCsv every copy re-typed")
    class CsvParsing {

        @Test
        @DisplayName("splits on comma, trims each part and drops the blanks")
        void splitsTrimsAndDropsBlanks() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuersCsv("  " + ISSUER + " , ,, " + OTHER_ISSUER + "  ")
                    .buildValidator();

            assertThat(chain.validate(validToken()).hasErrors()).isFalse();
            assertThat(chain.validate(token(OTHER_ISSUER, Instant.now().plusSeconds(600))).hasErrors())
                    .isFalse();
            assertThat(errorCodes(chain, token("https://third.example", Instant.now().plusSeconds(600))))
                    .containsExactly(ISSUER_ERROR_CODE);
        }

        @Test
        @DisplayName("a single value with no comma is accepted")
        void singleValue() {
            OAuth2TokenValidator<Jwt> chain = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuersCsv(ISSUER)
                    .buildValidator();

            assertThat(chain.validate(validToken()).hasErrors()).isFalse();
        }

        @Test
        @DisplayName("a null, blank or comma-only string yields no issuer and is rejected at wiring time")
        void emptyCsvRejected() {
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).allowedIssuersCsv(null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allowedIssuers");
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).allowedIssuersCsv("   "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).allowedIssuersCsv(" , , "))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("construction contract — every omission fails loudly, none silently widens the gate")
    class ConstructionContract {

        @Test
        @DisplayName("omitting the issuer allow-list fails the build rather than accepting any issuer")
        void issuerAllowListIsRequired() {
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).buildValidator())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("allowedIssuers");

            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).build())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("allowedIssuers");
        }

        @Test
        @DisplayName("an empty issuer collection is rejected at the call, not silently kept")
        void emptyIssuerCollectionRejected() {
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).allowedIssuers(Set.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allowedIssuers");
        }

        @Test
        @DisplayName("a blank jwkSetUri is rejected, a null one throws NullPointerException")
        void jwkSetUriValidated() {
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder("  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("jwkSetUri");
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("null arguments are rejected")
        void nullArgumentsRejected() {
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).allowedIssuers(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> ResourceServerChainAssembler.jwtDecoder(JWKS_URI).validator(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("build() — the NimbusJwtDecoder")
    class DecoderConstruction {

        @Test
        @DisplayName("returns a NimbusJwtDecoder without contacting the JWKS endpoint")
        void buildsLazily() {
            // withJwkSetUri fetches on first verification, not at construction: this is what lets a
            // service start before its issuer is reachable, and it is why this assertion can name a
            // host that does not resolve.
            NimbusJwtDecoder decoder = ResourceServerChainAssembler.jwtDecoder(JWKS_URI)
                    .allowedIssuers(Set.of(ISSUER))
                    .build();

            assertThat(decoder).isNotNull();
        }

        @Test
        @DisplayName("each build() is an independent decoder — the builder is not a shared singleton")
        void buildsDistinctInstances() {
            ResourceServerChainAssembler.JwtDecoderBuilder builder =
                    ResourceServerChainAssembler.jwtDecoder(JWKS_URI).allowedIssuers(Set.of(ISSUER));

            assertThat(builder.build()).isNotSameAs(builder.build());
        }
    }
}
