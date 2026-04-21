package com.example.user.domain.exception;

public class DefaultAddressCannotBeDeletedException extends RuntimeException {

    public DefaultAddressCannotBeDeletedException() {
        super("Cannot delete the default address while other addresses exist");
    }
}
