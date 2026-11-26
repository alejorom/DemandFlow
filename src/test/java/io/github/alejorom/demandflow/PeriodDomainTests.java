package io.github.alejorom.demandflow;

import java.time.LocalDate;

import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** T3; TP2.S1-S4, TP3.S1-S3 and the domain part of TP5.S3. */
class PeriodDomainTests {
    private TariffPeriod period(String label) {
        return new TariffPeriod(label, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31));
    }

    @Test
    void labelsNormalizeOnceAllowDuplicatesAndCountCodePointsInsteadOfUtf16Units() {
        assertThat(period(" \t Vigencia \n").getLabel()).isEqualTo("Vigencia");
        assertThat(period("Vigencia").getLabel()).isEqualTo(period("Vigencia").getLabel());
        assertThat(period("x".repeat(100)).getLabel()).hasSize(100);
        String supplementary = "\uD83D\uDE00".repeat(100);
        assertThat(period(supplementary).getLabel()).isEqualTo(supplementary);
        assertThrows(IllegalArgumentException.class, () -> period("x".repeat(101)));
        assertThrows(IllegalArgumentException.class, () -> period(supplementary + "x"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void nullOrBlankLabelsAreRejected(String label) {
        assertThrows(IllegalArgumentException.class, () -> period(label));
    }

    @ParameterizedTest
    @CsvSource({"2027-01-01,2027-12-31", "2027-01-01,2027-06-30", "2027-02-01,2027-02-10",
            "2027-01-01,2027-01-01", "-4712-01-01,-4712-01-01", "+5874897-12-30,+5874897-12-30"})
    void inclusiveIntervalsHaveFlexibleDuration(String start, String end) {
        var period = new TariffPeriod("Periodo", TariffPeriod.parseDate(start), TariffPeriod.parseDate(end));
        assertThat(period.getStartDate()).isEqualTo(start);
        assertThat(period.getEndDate()).isEqualTo(end);
        assertThat(period.isActive()).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2027-02-29", "2027-13-01", "01/01/2027", "infinity",
            "-4713-11-23", "-4713-11-24", "-4713-12-31", "+5874898-01-01"})
    void datesMustBeStrictIsoAndPostgresRepresentable(String input) {
        assertThrows(IllegalArgumentException.class, () -> TariffPeriod.parseDate(input));
    }

    @Test
    void editsValidateBeforeMutationAndStateChangesAreExplicit() {
        var period = period("Periodo");
        assertThat(period.edit(" Periodo ", period.getStartDate(), period.getEndDate())).isFalse();
        assertThrows(IllegalArgumentException.class, () -> period.edit("Otro", null, period.getEndDate()));
        assertThrows(IllegalArgumentException.class, () -> period.edit("Otro", period.getStartDate(), null));
        assertThrows(IllegalArgumentException.class, () -> period.edit("Otro", period.getEndDate(), period.getStartDate()));
        assertThrows(IllegalArgumentException.class, () -> period.edit("Otro", LocalDate.MIN, LocalDate.MAX));
        assertThrows(IllegalArgumentException.class, () -> period.edit("Otro", period.getStartDate(), LocalDate.of(5874897,12,31)));
        assertThat(period.getLabel()).isEqualTo("Periodo");
        assertThrows(IllegalStateException.class, period::reactivate);
        period.deactivate();
        assertThat(period.isActive()).isFalse();
        assertThrows(IllegalStateException.class, period::deactivate);
        assertThat(period.edit("Corregido", period.getStartDate(), period.getEndDate())).isTrue();
        assertThat(period.isActive()).isFalse();
        period.reactivate();
        assertThat(period.isActive()).isTrue();
    }
}
