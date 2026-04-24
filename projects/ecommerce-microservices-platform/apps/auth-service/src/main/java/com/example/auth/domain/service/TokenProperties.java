package com.example.auth.domain.service;

public interface TokenProperties {
    long refreshTokenTtlSeconds();
    long accessTokenTtlSeconds();
}
