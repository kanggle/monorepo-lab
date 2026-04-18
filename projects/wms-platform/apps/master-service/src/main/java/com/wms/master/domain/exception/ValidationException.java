package com.wms.master.domain.exception;

public class ValidationException extends MasterDomainException {

    public ValidationException(String message) {
        super("VALIDATION_ERROR", message);
    }
}
