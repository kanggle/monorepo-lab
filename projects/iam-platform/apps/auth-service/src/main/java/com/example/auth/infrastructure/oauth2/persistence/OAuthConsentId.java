package com.example.auth.infrastructure.oauth2.persistence;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Composite primary key for {@link OAuthConsentEntity}.
 *
 * <p>TASK-BE-252.
 */
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class OAuthConsentId implements Serializable {

    private String principalId;
    private String clientId;
}
