package com.grabit.cba.VendingMachineAlertService.exception;

public class ClientErrorException extends RuntimeException {

    public ClientErrorException(String msg) {
        super(msg);
    }

    public ClientErrorException(String msg, Throwable ex) {
        super(msg, ex);
    }
}
