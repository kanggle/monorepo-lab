package com.example.user.application.service;

import com.example.user.application.command.CreateAddressCommand;
import com.example.user.application.command.UpdateAddressCommand;
import com.example.user.application.result.AddressResult;
import com.example.user.domain.exception.AddressNotFoundException;
import com.example.user.domain.exception.DefaultAddressCannotBeDeletedException;
import com.example.user.domain.exception.UserProfileNotFoundException;
import com.example.user.domain.model.Address;
import com.example.user.domain.repository.AddressRepository;
import com.example.user.domain.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddressService {

    private final AddressRepository addressRepository;
    private final UserProfileRepository userProfileRepository;

    public List<AddressResult> getAddresses(UUID userId) {
        return addressRepository.findAllByUserId(userId).stream()
                .map(AddressResult::from)
                .toList();
    }

    /**
     * <b>Precondition, stated (TASK-BE-575).</b> {@code user_addresses.user_id} is an FK to
     * {@code user_profiles.user_id}, so a missing profile used to surface as a
     * {@code DataIntegrityViolationException} at flush — a 500, i.e. "the server is broken",
     * for what is a missing precondition. Its siblings ({@code GET|PATCH /api/users/me},
     * {@code POST /api/wishlists}) answer 404 {@code USER_PROFILE_NOT_FOUND} in the same
     * situation; this now does too.
     *
     * <p>{@link com.example.user.presentation.filter.UserProfileProvisioningFilter} means
     * an HTTP caller carrying a gateway-verified {@code X-User-Id} will not reach this
     * branch — the profile is provisioned before the controller runs. The check is not
     * therefore decoration: it is what any other caller of this service (a consumer, a
     * future internal endpoint, a test) gets instead of an FK stack trace, and it is the
     * reason the "created" log below can only ever describe a row that exists.
     */
    @Transactional
    public UUID createAddress(CreateAddressCommand command) {
        if (!userProfileRepository.existsByUserId(command.userId())) {
            throw new UserProfileNotFoundException(command.userId());
        }

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
        logAfterCommit("Address created: addressId={}, userId={}", saved.getId(), command.userId());
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
        logAfterCommit("Address deleted: addressId={}, userId={}", addressId, userId);
    }

    /**
     * Log a completed mutation only once it is actually durable (TASK-BE-575 AC-3).
     *
     * <p>These lines used to be emitted inside the transaction, so a rollback left
     * {@code INFO Address created: addressId=…} sitting directly above the
     * {@code ERROR … violates foreign key constraint} that undid it. An operator reading
     * the log had every reason to believe the address existed. A statement in the past
     * tense has to be true when it is written; deferring it to {@code afterCommit} is what
     * makes that so.
     *
     * <p>Outside a transaction (a direct service call in a unit test) there is nothing to
     * wait for, so the line is written immediately — it is already as durable as it will get.
     */
    private void logAfterCommit(String format, Object... args) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.info(format, args);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.info(format, args);
            }
        });
    }
}
