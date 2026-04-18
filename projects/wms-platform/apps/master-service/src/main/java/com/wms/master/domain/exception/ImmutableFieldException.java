package com.wms.master.domain.exception;

public class ImmutableFieldException extends MasterDomainException {

    public ImmutableFieldException(String fieldName) {
        super("IMMUTABLE_FIELD", "Field '" + fieldName + "' is immutable after creation");
    }
}
