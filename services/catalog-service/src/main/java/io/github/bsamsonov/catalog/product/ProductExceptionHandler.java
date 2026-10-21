package io.github.bsamsonov.catalog.product;

import jakarta.validation.ConstraintViolationException;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Errors as RFC 9457 problem+json; standard Spring MVC errors (validation, 404 path...) handled by the base class. */
@RestControllerAdvice(assignableTypes = ProductController.class)
class ProductExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    ProblemDetail notFound(ProductNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ProductConflictException.class)
    ProblemDetail conflict(ProductConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Query/path parameter constraints (page, size): the generated interface is {@code @Validated}, so they are
     * checked by the method-validation proxy, which throws this instead of an MVC exception; without it they are 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail invalidParameter(ConstraintViolationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** Races the explicit checks cannot see: same code inserted concurrently, update between read and commit. */
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class})
    ProblemDetail concurrentChange(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "concurrent change, re-read and retry");
    }
}
