package com.github.wprusik.notify.smsapi;

public class SmsFailedException extends Exception {

    public SmsFailedException(String message) {
        super(message);
    }

    public SmsFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
