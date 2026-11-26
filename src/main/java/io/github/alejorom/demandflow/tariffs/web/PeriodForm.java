package io.github.alejorom.demandflow.tariffs.web;

import java.time.LocalDate;

import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** HU-001/T3/T6: raw inputs survive validation; domain rules remain the single policy. */
public class PeriodForm {
    private String label;
    private String startDate;
    private String endDate;

    @AssertTrue(message = "La etiqueta debe contener entre 1 y 100 caracteres sin espacios exteriores.")
    public boolean isValidLabel() {
        try { TariffPeriod.normalizeLabel(label); return true; }
        catch (IllegalArgumentException failure) { return false; }
    }

    @AssertTrue(message = "Indica fechas ISO válidas y representables, con inicio no posterior al fin.")
    public boolean isValidDates() {
        try { new TariffPeriod("valid", start(), end()); return true; }
        catch (IllegalArgumentException failure) { return false; }
    }

    public String normalizedLabel() { return TariffPeriod.normalizeLabel(label); }
    public LocalDate start() { return TariffPeriod.parseDate(startDate); }
    public LocalDate end() { return TariffPeriod.parseDate(endDate); }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public static class Edit extends PeriodForm {
        @NotNull @PositiveOrZero
        private Long expectedVersion;
        public Long getExpectedVersion() { return expectedVersion; }
        public void setExpectedVersion(Long expectedVersion) { this.expectedVersion = expectedVersion; }
    }
}
