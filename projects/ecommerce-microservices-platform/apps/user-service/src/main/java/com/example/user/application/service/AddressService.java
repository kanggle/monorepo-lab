package com.example.user.application.service;

import com.example.user.application.command.CreateAddressCommand;
import com.example.user.application.command.UpdateAddressCommand;
import com.example.user.application.result.AddressResult;
import com.example.user.domain.exception.AddressNotFoundException;
import com.example.user.domain.exception.DefaultAddressCannotBeDeletedException;
import com.example.user.domain.model.Address;
import com.example.user.domain.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addressRepository;

    public List<AddressResult> getAddresses(UUID userId) {
        return addressRepository.findAllByUserId(userId).stream()
                .map(AddressResult::from)
                .toList();
    }

    @Transactional
    public UUID createAddress(CreateAddressCommand command) {
        int currentCount = addressRepository.countByUserId(command.userId());
        Address.validateAddressLimit(currentCount);

        boolean isFirst = currentCount == 0;
        boolean isDefault = isFirst || command.isDefault();

        if (isDefault && !isFirst) {
            addressRepository.unmarkDefaultByUserId(command.userId());
        }

        Address address = Address.create(
                command.userId(),
                command.label(),
                command.recipientName(),
                command.phone(),
                command.zipCode(),
                command.address1(),
                command.address2(),
                isDefault
        );

        Address saved = addressRepository.save(address);
        log.info("Address created: addressId={}, userId={}", saved.getId(), command.userId());
        return saved.getId();
    }

    @Transactional
    public AddressResult updateAddress(UpdateAddressCommand command) {
        Address address = addressRepository.findByIdAndUserId(command.addressId(), command.userId())
                .orElseThrow(() -> new AddressNotFoundException(command.addressId()));

        if (Boolean.TRUE.equals(command.isDefault()) && !address.isDefault()) {
            addressRepository.unmarkDefaultByUserId(command.userId());
        }

        if (Boolean.FALSE.equals(command.isDefault()) && address.isDefault()) {
            List<Address> allAddresses = addressRepository.findAllByUserId(command.userId());
            boolean hasOtherAddresses = allAddresses.stream()
                    .anyMatch(a -> !a.getId().equals(address.getId()));
            if (!hasOtherAddresses) {
                address.update(
                        command.label(), command.recipientName(), command.phone(),
                        command.zipCode(), command.address1(), command.address2(), true
                );
                addressRepository.save(address);
                return AddressResult.from(address);
            }
        }

        address.update(
                command.label(), command.recipientName(), command.phone(),
                command.zipCode(), command.address1(), command.address2(), command.isDefault()
        );

        addressRepository.save(address);
        return AddressResult.from(address);
    }

    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Address address = addressRepository.findByIdAndUserId(addressId, userId)
                .orElseThrow(() -> new AddressNotFoundException(addressId));

        if (address.isDefault()) {
            int addressCount = addressRepository.countByUserId(userId);
            if (addressCount > 1) {
                throw new DefaultAddressCannotBeDeletedException();
            }
        }

        addressRepository.delete(address);
        log.info("Address deleted: addressId={}, userId={}", addressId, userId);
    }

}
