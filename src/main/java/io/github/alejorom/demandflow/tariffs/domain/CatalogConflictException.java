package io.github.alejorom.demandflow.tariffs.domain;

/** Stable business reasons for RF-CON-005; callers never classify localized/raw exception text. */
public final class CatalogConflictException extends IllegalStateException {
    public enum Reason { PERIOD_OVERLAP, RATE_DUPLICATE, PERIOD_INACTIVE, REDUNDANT_STATE }
    private final Reason reason;

    public CatalogConflictException(Reason reason) {
        super(switch (reason) {
            case PERIOD_OVERLAP -> "Period dates overlap another period";
            case RATE_DUPLICATE -> "A rate already exists for the period and type";
            case PERIOD_INACTIVE -> "Rate creation or reactivation requires an active period";
            case REDUNDANT_STATE -> "Requested state is already current";
        });
        this.reason = reason;
    }

    public Reason reason() { return reason; }
}
