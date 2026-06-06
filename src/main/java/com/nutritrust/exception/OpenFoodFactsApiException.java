package com.nutritrust.exception;

public class OpenFoodFactsApiException extends RuntimeException {

    public OpenFoodFactsApiException(String message) {
        super(message);
    }

    public OpenFoodFactsApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
