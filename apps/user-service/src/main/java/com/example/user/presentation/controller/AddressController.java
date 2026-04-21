package com.example.user.presentation.controller;

import com.example.user.application.command.CreateAddressCommand;
import com.example.user.application.command.UpdateAddressCommand;
import com.example.user.application.result.AddressResult;
import com.example.user.application.service.AddressService;
import com.example.user.presentation.dto.request.CreateAddressRequest;
import com.example.user.presentation.dto.request.UpdateAddressRequest;
import com.example.user.presentation.dto.response.AddressListResponse;
import com.example.user.presentation.dto.response.CreateAddressResponse;
import com.example.user.presentation.dto.response.UpdateAddressResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/users/me/addresses")
public class AddressController {

    private final AddressService addressService;

    @GetMapping
    public ResponseEntity<AddressListResponse> getAddresses(
            @RequestHeader("X-User-Id") UUID userId) {
        List<AddressResult> results = addressService.getAddresses(userId);
        return ResponseEntity.ok(AddressListResponse.from(results));
    }

    @PostMapping
    public ResponseEntity<CreateAddressResponse> createAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody CreateAddressRequest request) {
        var command = new CreateAddressCommand(
                userId,
                request.label(),
                request.recipientName(),
                request.phone(),
                request.zipCode(),
                request.address1(),
                request.address2(),
                request.isDefault()
        );
        UUID addressId = addressService.createAddress(command);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CreateAddressResponse.from(addressId));
    }

    @PatchMapping("/{addressId}")
    public ResponseEntity<UpdateAddressResponse> updateAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId,
            @Valid @RequestBody UpdateAddressRequest request) {
        var command = new UpdateAddressCommand(
                userId,
                addressId,
                request.label(),
                request.recipientName(),
                request.phone(),
                request.zipCode(),
                request.address1(),
                request.address2(),
                request.isDefault()
        );
        AddressResult result = addressService.updateAddress(command);
        return ResponseEntity.ok(UpdateAddressResponse.from(result));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID addressId) {
        addressService.deleteAddress(userId, addressId);
        return ResponseEntity.noContent().build();
    }
}
