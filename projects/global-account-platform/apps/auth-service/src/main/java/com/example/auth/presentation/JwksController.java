package com.example.auth.presentation;

import com.example.auth.infrastructure.jwt.JwksEndpointProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/internal/auth")
@RequiredArgsConstructor
public class JwksController {

    private final JwksEndpointProvider jwksEndpointProvider;

    @GetMapping("/jwks")
    public ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok(jwksEndpointProvider.getJwks());
    }
}
