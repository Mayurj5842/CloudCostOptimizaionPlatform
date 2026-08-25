package com.cloudcostai.aws.exception;

public class AwsConnectionException extends RuntimeException {

    public AwsConnectionException(String message) {
        super(message);
    }

    public AwsConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}