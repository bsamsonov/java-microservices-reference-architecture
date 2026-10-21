package io.github.bsamsonov.catalog.product;

import java.util.UUID;

class ProductNotFoundException extends RuntimeException {

    ProductNotFoundException(UUID id) {
        super("product " + id + " not found");
    }
}
