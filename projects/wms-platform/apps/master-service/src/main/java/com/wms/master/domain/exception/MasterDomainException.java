package com.wms.master.domain.exception;

public abstract class MasterDomainException extends RuntimeException {

    private final String code;

    protected MasterDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
