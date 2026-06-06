package com.nutritrust.exception;

public class InvalidBarcodeException extends RuntimeException {

    public InvalidBarcodeException(String message) {
        super(message);
    }
}
