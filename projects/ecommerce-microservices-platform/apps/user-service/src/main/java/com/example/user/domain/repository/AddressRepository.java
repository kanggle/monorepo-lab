package com.example.user.domain.repository;

import com.example.user.domain.model.Address;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository {

    Address save(Address address);

    Optional<Address> findByIdAndUserId(UUID id, UUID userId);

    List<Address> findAllByUserId(UUID userId);

    int countByUserId(UUID userId);

    void delete(Address address);

    void unmarkDefaultByUserId(UUID userId);
}
