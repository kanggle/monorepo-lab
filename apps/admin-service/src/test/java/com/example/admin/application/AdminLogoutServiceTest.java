package com.example.admin.application;

import com.example.admin.application.port.AdminRefreshTokenPort;
import com.example.admin.application.port.OperatorLookupPort;
import com.example.admin.application.port.TokenBlacklistPort;
import com.example.admin.infrastructure.config.AdminJwtProperties;
import com.gap.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-040-fix2 — port-stub unit coverage for {@link AdminLogoutService}.
 * Focuses on the refresh-token revocation branch (the access-jti blacklist is
 * covered by integration / filter tests).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("AdminLogoutService (port-stub unit)")
class AdminLogoutServiceTest {

    @Mock TokenBlacklistPort blacklist;
    @Mock JwtVerifier jwtVerifier;
    @Mock AdminRefreshTokenPort tokenPort;
    @Mock OperatorLookupPort operatorLookup;

    private AdminJwtProperties props;
    private AdminLogoutService service;

    private static final String OP_UUID = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static final Long OP_PK = 7L;
    private static final String ACCESS_JTI = "access-jti-1";
    private static final String REFRESH_JTI = "refresh-jti-1";
    private static final String REFRESH_JWT = "raw.refresh.jwt";

    @BeforeEach
    void setUp() {
        props = new AdminJwtProperties();
        service = new AdminLogoutService(blacklist, props, jwtVerifier, tokenPort, operatorLookup);
    }

    @Test
    @DisplayName("유효한 refresh JWT + operator 일치 → tokenPort.revoke(LOGOUT) 호출")
    void validRefresh_revokesWithLogoutReason() {
        Instant accessExp = Instant.now().plusSeconds(600);
        when(jwtVerifier.verify(REFRESH_JWT))
                .thenReturn(refreshClaims(OP_UUID, REFRESH_JTI, "admin_refresh"));
        when(tokenPort.findByJti(REFRESH_JTI))
                .thenReturn(Optional.of(activeRow(REFRESH_JTI, OP_PK)));
        when(operatorLookup.findByOperatorId(OP_UUID))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(OP_PK, OP_UUID)));

        service.logout(OP_UUID, ACCESS_JTI, accessExp, REFRESH_JWT);

        verify(blacklist).blacklist(eq(ACCESS_JTI), any(Duration.class));
        verify(tokenPort).revoke(eq(REFRESH_JTI), any(Instant.class),
                eq(AdminRefreshTokenPort.REASON_LOGOUT));
    }

    @Test
    @DisplayName("refresh JWT 가 null 이면 tokenPort.revoke 는 호출되지 않는다")
    void nullRefresh_skipsRevoke() {
        Instant accessExp = Instant.now().plusSeconds(600);

        service.logout(OP_UUID, ACCESS_JTI, accessExp, null);

        verify(blacklist).blacklist(eq(ACCESS_JTI), any(Duration.class));
        verify(tokenPort, never()).revoke(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("refresh row 의 operator 가 access operator 와 불일치 → revoke 미호출")
    void operatorMismatch_skipsRevoke() {
        Instant accessExp = Instant.now().plusSeconds(600);
        when(jwtVerifier.verify(REFRESH_JWT))
                .thenReturn(refreshClaims(OP_UUID, REFRESH_JTI, "admin_refresh"));
        // Registry row belongs to a DIFFERENT operator PK.
        when(tokenPort.findByJti(REFRESH_JTI))
                .thenReturn(Optional.of(activeRow(REFRESH_JTI, 999L)));
        when(operatorLookup.findByOperatorId(OP_UUID))
                .thenReturn(Optional.of(new OperatorLookupPort.OperatorSummary(OP_PK, OP_UUID)));

        service.logout(OP_UUID, ACCESS_JTI, accessExp, REFRESH_JWT);

        verify(blacklist).blacklist(eq(ACCESS_JTI), any(Duration.class));
        verify(tokenPort, never()).revoke(anyString(), any(), anyString());
    }

    private static Map<String, Object> refreshClaims(String sub, String jti, String tokenType) {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("sub", sub);
        c.put("jti", jti);
        c.put("token_type", tokenType);
        return c;
    }

    private static AdminRefreshTokenPort.TokenRecord activeRow(String jti, Long opPk) {
        Instant now = Instant.now();
        return new AdminRefreshTokenPort.TokenRecord(
                jti, opPk, now.minusSeconds(60), now.plusSeconds(3600), null, null, null);
    }
}
