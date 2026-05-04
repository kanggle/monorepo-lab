package com.example.admin.application;

import com.example.admin.application.exception.InvalidRefreshTokenException;
import com.example.admin.application.exception.RefreshTokenReuseDetectedException;
import com.example.admin.application.port.AdminRefreshTokenPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.example.admin.infrastructure.security.JwtSigner;
import com.example.security.jwt.JwtVerificationException;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-040-fix2 — port-stub unit coverage for
 * {@link AdminRefreshTokenService}. Covers the four decision points in the
 * rotation pipeline (normal rotation, unknown jti, sub/row operator mismatch,
 * rotated-jti reuse detection).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AdminRefreshTokenService (port-stub unit)")
class AdminRefreshTokenServiceTest {

    @Mock JwtVerifier jwtVerifier;
    @Mock AdminRefreshTokenPort tokenPort;
    @Mock OperatorLookupPort operatorLookup;
    @Mock AdminRefreshTokenIssuer refreshIssuer;
    @Mock JwtSigner jwtSigner;

    private AdminJwtProperties props;
    private AdminRefreshTokenService service;

    private static final String OP_UUID = "11111111-2222-3333-4444-555555555555";
    private static final Long OP_PK = 42L;
    private static final String JTI = "jti-abc-1";
    private static final String RAW_JWT = "raw.jwt.value";

    @BeforeEach
    void setUp() {
        props = new AdminJwtProperties();
        // defaults: issuer=admin-service, expectedTokenType=admin,
        //          refreshTokenType=admin_refresh, accessTtl=3600
        service = new AdminRefreshTokenService(
                jwtVerifier, props, tokenPort, operatorLookup, refreshIssuer, jwtSigner);
    }

    @Test
    @DisplayName("정상 rotation — RefreshResult.operatorId 를 검증된 UUID로 담는다")
    void rotation_success_populatesOperatorId() throws JwtVerificationException {
        when(jwtVerifier.verify(RAW_JWT)).thenReturn(claims(OP_UUID, JTI, "admin_refresh"));
        when(tokenPort.findByJti(JTI)).thenReturn(Optional.of(activeRow(JTI, OP_PK)));
        when(operatorLookup.findByOperatorId(OP_UUID))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(OP_PK, OP_UUID)));
        when(refreshIssuer.issue(eq(OP_PK), eq(OP_UUID), eq(JTI)))
                .thenReturn(new AdminRefreshTokenIssuer.Issued(
                        "new.refresh.jwt", "new-jti", 2_592_000L, Instant.now().plusSeconds(2_592_000L)));
        when(jwtSigner.sign(any())).thenReturn("new.access.jwt");

        AdminRefreshTokenService.RefreshResult result = service.refresh(RAW_JWT);

        assertThat(result.operatorId()).isEqualTo(OP_UUID);
        assertThat(result.accessToken()).isEqualTo("new.access.jwt");
        assertThat(result.refreshToken()).isEqualTo("new.refresh.jwt");
        verify(tokenPort).revoke(eq(JTI), any(Instant.class), eq(AdminRefreshTokenPort.REASON_ROTATED));
        verify(tokenPort, never()).revokeAllForOperator(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("jti 미등록 — InvalidRefreshTokenException")
    void unknown_jti_throwsInvalid() throws JwtVerificationException {
        when(jwtVerifier.verify(RAW_JWT)).thenReturn(claims(OP_UUID, JTI, "admin_refresh"));
        when(tokenPort.findByJti(JTI)).thenReturn(Optional.empty());

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(RAW_JWT))
                .withMessageContaining("jti");
        verify(tokenPort, never()).revoke(anyString(), any(), anyString());
        verify(tokenPort, never()).revokeAllForOperator(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("operator sub 가 레지스트리 row 와 불일치 — InvalidRefreshTokenException")
    void operatorMismatch_throwsInvalid() throws JwtVerificationException {
        when(jwtVerifier.verify(RAW_JWT)).thenReturn(claims(OP_UUID, JTI, "admin_refresh"));
        when(tokenPort.findByJti(JTI)).thenReturn(Optional.of(activeRow(JTI, 999L)));
        when(operatorLookup.findByOperatorId(OP_UUID))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(OP_PK, OP_UUID)));

        assertThatExceptionOfType(InvalidRefreshTokenException.class)
                .isThrownBy(() -> service.refresh(RAW_JWT))
                .withMessageContaining("operator mismatch");
        verify(tokenPort, never()).revoke(anyString(), any(), anyString());
        verify(tokenPort, never()).revokeAllForOperator(anyLong(), any(), anyString());
    }

    @Test
    @DisplayName("이미 revoke 된 jti 재사용 — 전체 체인 revoke + RefreshTokenReuseDetectedException(operatorId) throw")
    void reuse_triggersBulkRevokeAndCarriesOperatorId() throws JwtVerificationException {
        when(jwtVerifier.verify(RAW_JWT)).thenReturn(claims(OP_UUID, JTI, "admin_refresh"));
        when(tokenPort.findByJti(JTI)).thenReturn(Optional.of(revokedRow(JTI, OP_PK)));
        when(operatorLookup.findByOperatorId(OP_UUID))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(OP_PK, OP_UUID)));
        when(tokenPort.revokeAllForOperator(eq(OP_PK), any(Instant.class),
                eq(AdminRefreshTokenPort.REASON_REUSE_DETECTED))).thenReturn(2);

        RefreshTokenReuseDetectedException ex = catchThrowableOfType(
                () -> service.refresh(RAW_JWT), RefreshTokenReuseDetectedException.class);
        assertThat(ex).isNotNull();

        assertThat(ex.operatorId()).isEqualTo(OP_UUID);
        verify(tokenPort).revokeAllForOperator(
                eq(OP_PK), any(Instant.class), eq(AdminRefreshTokenPort.REASON_REUSE_DETECTED));
        verify(tokenPort, never()).revoke(anyString(), any(), anyString());
    }

    // --- helpers -----------------------------------------------------------

    private static Map<String, Object> claims(String sub, String jti, String tokenType) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("sub", sub);
        c.put("jti", jti);
        c.put("token_type", tokenType);
        c.put("iss", "admin-service");
        return c;
    }

    private static AdminRefreshTokenPort.TokenRecord activeRow(String jti, Long opPk) {
        Instant now = Instant.now();
        return new AdminRefreshTokenPort.TokenRecord(
                jti, opPk, now.minusSeconds(60), now.plusSeconds(3600), null, null, null);
    }

    private static AdminRefreshTokenPort.TokenRecord revokedRow(String jti, Long opPk) {
        Instant now = Instant.now();
        return new AdminRefreshTokenPort.TokenRecord(
                jti, opPk, now.minusSeconds(600), now.plusSeconds(3000), null,
                now.minusSeconds(30), AdminRefreshTokenPort.REASON_ROTATED);
    }
}
