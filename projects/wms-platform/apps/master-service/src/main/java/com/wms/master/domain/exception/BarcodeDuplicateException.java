package com.wms.master.domain.exception;

public class BarcodeDuplicateException extends MasterDomainException {

    public BarcodeDuplicateException(String barcode) {
        super("BARCODE_DUPLICATE", "barcode is already taken: " + barcode);
    }
}
