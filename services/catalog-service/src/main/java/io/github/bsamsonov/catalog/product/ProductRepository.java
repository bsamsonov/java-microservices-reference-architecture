package io.github.bsamsonov.catalog.product;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;

interface ProductRepository extends ListCrudRepository<Product, UUID>, ListPagingAndSortingRepository<Product, UUID> {

    boolean existsByCode(String code);

    Page<Product> findByActiveTrue(Pageable pageable);
}
