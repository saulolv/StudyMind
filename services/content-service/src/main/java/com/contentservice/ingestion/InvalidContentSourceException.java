package com.contentservice.ingestion;

public class InvalidContentSourceException extends RuntimeException {

    public InvalidContentSourceException(String message) {
        super(message);
    }
}
