package io.github.alejorom.demandflow.tariffs.web;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/** T4: shared missing-reference boundary for reads and form context, scoped to catalog controllers. */
@ControllerAdvice(assignableTypes = {PeriodController.class, RateController.class})
@Order(0)
public class CatalogWebExceptionHandler {
    @ExceptionHandler(EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String missingReference() { return "error"; }
}
