package io.github.alejorom.demandflow.tariffs.web;

import java.math.BigDecimal;

import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/** HU-002/T3/T6: retain the original decimal string until domain scale validation. */
public class RateForm {
    private String amountCOP;

    @AssertTrue(message = "Indica un valor COP/h positivo, hasta 9999999999999.99 y con máximo dos decimales.")
    public boolean isValidAmount() {
        try { amount(); return true; }
        catch (IllegalArgumentException failure) { return false; }
    }

    public BigDecimal amount() { return HourlyRate.parseAmount(amountCOP); }
    public String getAmountCOP() { return amountCOP; }
    public void setAmountCOP(String amountCOP) { this.amountCOP = amountCOP; }

    public static class Create extends RateForm {
        @NotNull @Positive
        private Long periodId;
        @NotNull @Min(1) @Max(2)
        private Short typeId;
        public Long getPeriodId() { return periodId; }
        public void setPeriodId(Long periodId) { this.periodId = periodId; }
        public Short getTypeId() { return typeId; }
        public void setTypeId(Short typeId) { this.typeId = typeId; }
    }

    public static class Edit extends RateForm {
        @NotNull @PositiveOrZero
        private Long expectedVersion;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
    }
}
