package io.github.bsamsonov.notifications.notification.adapter.in.kafka;

/** The record can never be processed (bad JSON, missing fields): goes to the DLT without retries. */
class InvalidMessageException extends RuntimeException {

    InvalidMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
