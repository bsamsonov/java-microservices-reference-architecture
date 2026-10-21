package io.github.bsamsonov.catalog.product;

import java.net.URI;
import java.time.ZoneOffset;
import java.util.UUID;

import io.github.bsamsonov.catalog.product.api.ProductsApi;
import io.github.bsamsonov.catalog.product.api.model.CreateProductRequestDto;
import io.github.bsamsonov.catalog.product.api.model.ProductPageDto;
import io.github.bsamsonov.catalog.product.api.model.ProductResponseDto;
import io.github.bsamsonov.catalog.product.api.model.UpdateProductRequestDto;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements the interface generated from api/openapi.yaml; maps DTO ⇄ entity, the entity never leaves this class. */
@RestController
class ProductController implements ProductsApi {

    private final ProductService products;

    ProductController(ProductService products) {
        this.products = products;
    }

    @Override
    public ResponseEntity<ProductResponseDto> createProduct(CreateProductRequestDto request) {
        Product product = products.create(request.getCode(), request.getName(), request.getPrice());
        return ResponseEntity.created(URI.create("/products/" + product.getId())).body(toDto(product));
    }

    @Override
    public ResponseEntity<ProductResponseDto> getProduct(UUID productId) {
        return ResponseEntity.ok(toDto(products.get(productId)));
    }

    @Override
    public ResponseEntity<ProductPageDto> listProducts(Integer page, Integer size, Boolean activeOnly) {
        Page<Product> result = products.list(page, size, activeOnly);
        return ResponseEntity.ok(new ProductPageDto()
                .items(result.map(ProductController::toDto).toList())
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements()));
    }

    @Override
    public ResponseEntity<ProductResponseDto> updateProduct(UUID productId, UpdateProductRequestDto request) {
        return ResponseEntity.ok(toDto(products.update(productId, request.getName(), request.getPrice(),
                request.getActive(), request.getVersion())));
    }

    @Override
    public ResponseEntity<Void> deleteProduct(UUID productId) {
        products.delete(productId);
        return ResponseEntity.noContent().build();
    }

    static ProductResponseDto toDto(Product p) {
        return new ProductResponseDto()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .price(p.getPrice())
                .active(p.isActive())
                .version(p.getVersion())
                .createdAt(p.getCreatedAt().atOffset(ZoneOffset.UTC))
                .updatedAt(p.getUpdatedAt().atOffset(ZoneOffset.UTC));
    }
}
