package io.github.alejorom.demandflow;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.ActorOrigin;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.Operation;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.PeriodState;
import io.github.alejorom.demandflow.audit.application.CatalogAuditWriter.RateState;
import io.github.alejorom.demandflow.audit.persistence.CatalogAuditEvent;
import io.github.alejorom.demandflow.audit.persistence.CatalogAuditInsert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/** T1/T5; CA1/CA2: snapshots contain only functional catalog fields. */
class AuditWriterTests {
    private static final Instant NOW = Instant.parse("2026-11-26T15:16:17.123456789Z");
    private final CatalogAuditInsert insert = mock(CatalogAuditInsert.class);
    private final Clock clock = mock(Clock.class);
    private final CatalogAuditWriter writer = new CatalogAuditWriter(insert, clock);

    private void fixedInstant() {
        when(clock.instant()).thenReturn(Clock.fixed(NOW, ZoneId.of("America/Bogota")).instant());
    }

    @ParameterizedTest
    @EnumSource(ActorOrigin.class)
    void periodCreationHasFourInitialFieldsOneServerMutationAndOneUtcInstant(ActorOrigin origin) {
        fixedInstant();
        writer.recordPeriod(7L, Operation.CREATE, null, period("Normalizada", true), origin);
        var events = events(4);
        assertThat(events).extracting(CatalogAuditEvent::getField)
                .containsExactly("label", "start_date", "end_date", "active");
        assertThat(events).extracting(CatalogAuditEvent::getAfterValue)
                .containsExactly("Normalizada", "2027-01-01", "2027-12-31", "true");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getBeforeValue()).isNull();
            assertThat(event.getEntityType()).isEqualTo("TARIFF_PERIOD");
            assertThat(event.getPeriodId()).isEqualTo(7L);
            assertThat(event.getRateId()).isNull();
            assertThat(event.getOperation()).isEqualTo("CREATE");
            assertThat(event.getActorOrigin()).isEqualTo(origin.name());
            assertThat(event.getOccurredAt()).isEqualTo(Instant.parse("2026-11-26T15:16:17.123456Z"));
        });
        assertThat(events).extracting(CatalogAuditEvent::getMutationId).doesNotContainNull().containsOnly(events.getFirst().getMutationId());
        verify(clock, times(1)).instant();
    }

    @ParameterizedTest
    @EnumSource(ActorOrigin.class)
    void rateCreationSerializesOnlyItsFixedFunctionalFields(ActorOrigin origin) {
        fixedInstant();
        writer.recordRate(9L, Operation.CREATE, null, rate("12.3", true), origin);
        var events = events(4);
        assertThat(events).extracting(CatalogAuditEvent::getField)
                .containsExactly("period_id", "type_id", "amount_cop", "active");
        assertThat(events).extracting(CatalogAuditEvent::getAfterValue).containsExactly("7", "1", "12.30", "true");
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getEntityType()).isEqualTo("HOURLY_RATE");
            assertThat(event.getPeriodId()).isNull();
            assertThat(event.getRateId()).isEqualTo(9L);
            assertThat(event.getBeforeValue()).isNull();
            assertThat(event.getActorOrigin()).isEqualTo(origin.name());
        });
    }

    @Test
    void periodEditRecordsOnlyRealDifferencesIncludingIsoDates() {
        fixedInstant();
        writer.recordPeriod(7L, Operation.UPDATE, period("Anterior", true),
                new PeriodState("Nueva", LocalDate.of(2027, 2, 1), LocalDate.of(2027, 11, 30), true), ActorOrigin.LOCAL_USER);
        var events = events(3);
        assertThat(events).extracting(CatalogAuditEvent::getBeforeValue).containsExactly("Anterior", "2027-01-01", "2027-12-31");
        assertThat(events).extracting(CatalogAuditEvent::getAfterValue).containsExactly("Nueva", "2027-02-01", "2027-11-30");
        assertThat(events).extracting(CatalogAuditEvent::getField).doesNotContain("active", "version");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rateCorrectionDoesNotDescribeReactivationOrAssociations(boolean active) {
        fixedInstant();
        writer.recordRate(9L, Operation.UPDATE, rate("0.01", active), rate("9999999999999.99", active), ActorOrigin.LOCAL_USER);
        var event = events(1).getFirst();
        assertThat(event.getField()).isEqualTo("amount_cop");
        assertThat(event.getBeforeValue()).isEqualTo("0.01");
        assertThat(event.getAfterValue()).isEqualTo("9999999999999.99");
        assertThat(event.getOperation()).isEqualTo("UPDATE");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void transitionsDescribeOnlyChangedEntityState(boolean reactivation) {
        fixedInstant();
        var operation = reactivation ? Operation.REACTIVATE : Operation.DEACTIVATE;
        writer.recordPeriod(7L, operation, period("Igual", !reactivation), period("Igual", reactivation), ActorOrigin.LOCAL_USER);
        writer.recordRate(9L, operation, rate("12.30", !reactivation), rate("12.30", reactivation), ActorOrigin.SYSTEM);
        var events = events(2);
        assertThat(events).allSatisfy(event -> {
            assertThat(event.getField()).isEqualTo("active");
            assertThat(event.getBeforeValue()).isEqualTo(Boolean.toString(!reactivation));
            assertThat(event.getAfterValue()).isEqualTo(Boolean.toString(reactivation));
            assertThat(event.getOperation()).isEqualTo(operation.name());
        });
        assertThat(events.get(0).getMutationId()).isNotEqualTo(events.get(1).getMutationId());
    }

    @Test
    void effectiveEqualityProducesNoRowsOrClockReads() {
        writer.recordPeriod(7L, Operation.UPDATE, period("Normalizada", true), period("Normalizada", true), ActorOrigin.LOCAL_USER);
        writer.recordRate(9L, Operation.UPDATE, rate("12.3", true), rate("12.30", true), ActorOrigin.LOCAL_USER);
        verifyNoInteractions(insert, clock);
    }

    @Test
    void rejectsFictitiousOrMisclassifiedChangesBeforeAnyInsertion() {
        assertThrows(IllegalArgumentException.class, () -> writer.recordPeriod(7L, Operation.DEACTIVATE,
                period("Igual", false), period("Igual", false), ActorOrigin.LOCAL_USER));
        assertThrows(IllegalArgumentException.class, () -> writer.recordPeriod(7L, Operation.REACTIVATE,
                period("Igual", true), period("Igual", true), ActorOrigin.LOCAL_USER));
        assertThrows(IllegalArgumentException.class, () -> writer.recordPeriod(7L, Operation.UPDATE,
                period("Igual", true), period("Igual", false), ActorOrigin.LOCAL_USER));
        assertThrows(IllegalArgumentException.class, () -> writer.recordPeriod(7L, Operation.DEACTIVATE,
                period("Anterior", true), period("Nueva", false), ActorOrigin.LOCAL_USER));
        assertThrows(IllegalArgumentException.class, () -> writer.recordRate(9L, Operation.UPDATE,
                rate("12.30", true), new RateState(8L, (short) 1, new BigDecimal("12.30"), true), ActorOrigin.LOCAL_USER));
        assertThrows(IllegalArgumentException.class, () -> writer.recordPeriod(7L, Operation.CREATE,
                period("Igual", true), period("Igual", true), ActorOrigin.LOCAL_USER));
        verifyNoInteractions(insert, clock);
    }

    @Test
    void typedSnapshotsExcludeTechnicalFieldsAndPersistenceExposesOnlyAppend() {
        assertThat(Arrays.stream(PeriodState.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("label", "startDate", "endDate", "active");
        assertThat(Arrays.stream(RateState.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("periodId", "typeId", "amountCop", "active");
        assertThat(CatalogAuditInsert.class.getDeclaredMethods()).extracting(method -> method.getName()).containsExactly("append");
        assertThat(Arrays.stream(CatalogAuditEvent.class.getDeclaredMethods()).map(method -> method.getName()))
                .allMatch(name -> name.startsWith("get"));
        assertThat(Arrays.stream(CatalogAuditWriter.class.getDeclaredMethods())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers())).map(method -> method.getName()))
                .containsExactlyInAnyOrder("recordPeriod", "recordRate");
    }

    private List<CatalogAuditEvent> events(int count) {
        var captured = ArgumentCaptor.forClass(CatalogAuditEvent.class);
        verify(insert, times(count)).append(captured.capture());
        return captured.getAllValues();
    }

    static PeriodState period(String label, boolean active) {
        return new PeriodState(label, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 12, 31), active);
    }

    static RateState rate(String amount, boolean active) {
        return new RateState(7L, (short) 1, new BigDecimal(amount), active);
    }
}
