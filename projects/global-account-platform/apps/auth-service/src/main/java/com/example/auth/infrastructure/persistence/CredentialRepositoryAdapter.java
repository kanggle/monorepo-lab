package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.repository.CredentialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CredentialRepositoryAdapter implements CredentialRepository {

    private final CredentialJpaRepository credentialJpaRepository;

    @Override
    public Optional<Credential> findByAccountId(String accountId) {
        return credentialJpaRepository.findByAccountId(accountId).map(CredentialJpaEntity::toDomain);
    }

    @Override
    public Optional<Credential> findByTenantIdAndEmail(String tenantId, String email) {
        if (tenantId == null || email == null) {
            return Optional.empty();
        }
        String normalized = email.trim().toLowerCase();
        return credentialJpaRepository.findByTenantIdAndEmail(tenantId, normalized)
                .map(CredentialJpaEntity::toDomain);
    }

    @Override
    public List<Credential> findAllByEmail(String email) {
        if (email == null) {
            return List.of();
        }
        String normalized = email.trim().toLowerCase();
        return credentialJpaRepository.findAllByEmail(normalized).stream()
                .map(CredentialJpaEntity::toDomain)
                .collect(Collectors.toList());
    }

    @Override
    @Deprecated
    public Optional<Credential> findByAccountIdEmail(String email) {
        if (email == null) {
            return Optional.empty();
        }
        return credentialJpaRepository.findByEmail(email)
                .map(CredentialJpaEntity::toDomain);
    }

    @Override
    public Credential save(Credential credential) {
        CredentialJpaEntity entity = CredentialJpaEntity.fromDomain(credential);
        return credentialJpaRepository.save(entity).toDomain();
    }

    @Override
    public boolean existsByAccountId(String accountId) {
        return credentialJpaRepository.findByAccountId(accountId).isPresent();
    }
}
