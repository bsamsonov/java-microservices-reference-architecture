package io.github.bsamsonov.notifications.notification.adapter.in.web;

import io.github.bsamsonov.notifications.notification.application.NotificationNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Errors as RFC 9457 problem+json; standard Spring MVC errors (validation, 404 path...) handled by the base class. */
@RestControllerAdvice(assignableTypes = NotificationController.class)
class NotificationExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(NotificationNotFoundException.class)
    ProblemDetail notFound(NotificationNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
