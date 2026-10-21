package io.github.bsamsonov.catalog.product;

/** Duplicate code or stale version (maps to HTTP 409). */
class ProductConflictException extends RuntimeException {

    ProductConflictException(String message) {
        super(message);
    }
}
