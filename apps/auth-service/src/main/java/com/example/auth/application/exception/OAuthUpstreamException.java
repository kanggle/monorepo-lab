package com.example.auth.application.exception;

import lombok.Getter;

@Getter
public class OAuthUpstreamException extends RuntimeException {

    private final String callbackUrl;

    public OAuthUpstreamException(String message, String callbackUrl) {
        super(message);
        this.callbackUrl = callbackUrl;
    }

    public OAuthUpstreamException(String message, String callbackUrl, Throwable cause) {
        super(message, cause);
        this.callbackUrl = callbackUrl;
    }
}
