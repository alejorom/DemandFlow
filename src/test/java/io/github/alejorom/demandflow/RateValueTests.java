package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;

import io.github.alejorom.demandflow.tariffs.domain.HourlyRate;
import io.github.alejorom.demandflow.tariffs.domain.RequirementType;
import io.github.alejorom.demandflow.tariffs.domain.TariffPeriod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/** T3; HR3/HR4 and the economic comparison of CA4.S1, without application/audit claims. */
class RateValueTests {
    @ParameterizedTest
    @ValueSource(strings = {"0.01", "9999999999999.99", "12.3", "12.30", "12"})
    void validAmountsAreExactAndNormalizedOnlyAfterValidation(String value) {
        BigDecimal actual = HourlyRate.parseAmount(value);
        assertThat(actual).isEqualByComparingTo(new BigDecimal(value));
        assertThat(actual.scale()).isEqualTo(2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"x", "NaN", "Infinity", "0", "-0.01", "12.345", "12.340", "10000000000000.00"})
    void invalidAmountsAreRejectedWithoutRounding(String value) {
        assertThrows(IllegalArgumentException.class, () -> HourlyRate.parseAmount(value));
    }

    @Test
    void correctionKeepsAssociationsAndEqualEconomicValuesDoNotMutate() {
        var period = new TariffPeriod("Periodo", LocalDate.of(2027,1,1), LocalDate.of(2027,12,31));
        var type = mock(RequirementType.class);
        var rate = new HourlyRate(period, type, new BigDecimal("12.3"));
        BigDecimal original = rate.getAmountCop();
        assertThat(rate.correctAmount(new BigDecimal("12.30"))).isFalse();
        assertThat(rate.getAmountCop()).isSameAs(original);
        assertThrows(IllegalArgumentException.class, () -> rate.correctAmount(new BigDecimal("12.340")));
        assertThrows(IllegalArgumentException.class, () -> rate.correctAmount(null));
        assertThat(rate.getAmountCop()).isSameAs(original);
        period.deactivate();
        assertThat(rate.correctAmount(new BigDecimal("13.01"))).isTrue();
        assertThat(rate.getAmountCop()).isEqualTo(new BigDecimal("13.01"));
        assertThat(rate.getPeriod()).isSameAs(period);
        assertThat(rate.getType()).isSameAs(type);
        assertThat(rate.isActive()).isTrue();
        assertThat(period.isActive()).isFalse();
        assertThrows(IllegalArgumentException.class, () -> new HourlyRate(null, type, original));
        assertThrows(IllegalArgumentException.class, () -> new HourlyRate(period, null, original));
        assertThrows(IllegalArgumentException.class, () -> new HourlyRate(period, type, null));
        assertThat(Arrays.stream(HourlyRate.class.getMethods()).map(method -> method.getName()))
                .doesNotContain("setPeriod", "setType");
    }
}
