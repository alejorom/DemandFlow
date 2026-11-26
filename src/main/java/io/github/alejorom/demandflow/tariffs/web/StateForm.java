package io.github.alejorom.demandflow.tariffs.web;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** A state action accepts only the version; the target state comes from the route. */
public class StateForm {
    @NotNull @PositiveOrZero
    private Long expectedVersion;
    public Long getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
}
